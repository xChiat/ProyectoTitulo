"use strict";

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const crypto = require("crypto");
const { google } = require("googleapis");
const textToSpeech = require("@google-cloud/text-to-speech");

// =====================================================
// CONFIGURACIÓN DE HAPTIVOICE PREMIUM
// =====================================================

const PACKAGE_NAME = "cl.h1p0c.haptivoice";
const SUBSCRIPTION_ID = "haptivoice_premium";

// Límites iniciales para controlar costos.
const MAX_CHARACTERS_PER_REQUEST = 400;
const DAILY_CHARACTERS = 5000;
const DAILY_REQUESTS = 30;

// Voces autorizadas. El cliente no puede enviar
// nombres arbitrarios de voces a Google Cloud.
const voices = Object.freeze({
    "es-US": {
        FEMALE: "es-US-News-F",
        MALE: "es-US-Neural2-C",
    },
    "en-US": {
        FEMALE: "en-US-Chirp3-HD-Zephyr",
        MALE: "en-US-Neural2-D",
    }
});

// =====================================================
// CLIENTES DE GOOGLE PLAY Y CLOUD TTS
// =====================================================

const publisher = google.androidpublisher({
    version: "v3",
    auth: new google.auth.GoogleAuth({
        scopes: [
            "https://www.googleapis.com/auth/androidpublisher"
        ]
    })
});

const ttsClient = new textToSpeech.TextToSpeechClient();

// =====================================================
// VERIFICAR SUSCRIPCIÓN EN GOOGLE PLAY
// =====================================================

async function requireValidSubscription(uid, purchaseToken) {
    let purchase;

    try {
        const result = await publisher.purchases.subscriptionsv2.get({
            packageName: PACKAGE_NAME,
            token: purchaseToken
        });

        purchase = result.data;

    } catch (error) {
        logger.error("Error verificando Google Play", {
            status: error.code || "unknown"
        });

        throw new HttpsError(
            "unavailable",
            "No se pudo verificar la suscripción."
        );
    }

    // Confirmar que la suscripción sigue vigente.
    const allowedStates = new Set([
        "SUBSCRIPTION_STATE_ACTIVE",
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
        "SUBSCRIPTION_STATE_CANCELED"
    ]);

    const validProduct = (purchase.lineItems || []).some(
        item =>
            item.productId === SUBSCRIPTION_ID &&
            Number.isFinite(Date.parse(item.expiryTime)) &&
            Date.parse(item.expiryTime) > Date.now()
    );

    if (
        !allowedStates.has(purchase.subscriptionState) ||
        !validProduct
    ) {
        throw new HttpsError(
            "permission-denied",
            "No hay una suscripción Premium vigente."
        );
    }

    // Identificador asociado a las compras nuevas.
    // Debe coincidir con el SHA-256 del UID de Firebase.
    const expectedAccountId = crypto
        .createHash("sha256")
        .update(uid, "utf8")
        .digest("hex");

    const registeredAccountId =
        purchase.externalAccountIdentifiers
            ?.obfuscatedExternalAccountId;

    // Solamente permitimos compras asociadas a Firebase.
    // Si la compra no incluye este identificador, se rechaza.
    // No utilizamos listas de autorización ni excepciones
    // para las suscripciones antiguas.
    if (!registeredAccountId) {
        throw new HttpsError(
            "permission-denied",
            "La compra no está vinculada a una cuenta de HaptiVoice. " +
            "Realiza una nueva compra de prueba desde la app actualizada."
        );
    }

    if (registeredAccountId !== expectedAccountId) {
        throw new HttpsError(
            "permission-denied",
            "La compra pertenece a otra cuenta."
        );
    }

    // Cada token de compra queda vinculado a un solo UID.
    // No guardamos el token original, solo su SHA-256.
    const tokenHash = crypto
        .createHash("sha256")
        .update(purchaseToken, "utf8")
        .digest("hex");

    const bindingRef = admin.firestore()
        .collection("premium_tts_purchase_bindings")
        .doc(tokenHash);

    await admin.firestore().runTransaction(async transaction => {
        const existing = await transaction.get(bindingRef);

        if (existing.exists) {
            if (existing.data()?.uid !== uid) {
                throw new HttpsError(
                    "permission-denied",
                    "Esta compra ya está vinculada a otra cuenta."
                );
            }
        } else {
            transaction.create(bindingRef, {
                uid: uid,
                createdAt:
                    admin.firestore.FieldValue.serverTimestamp()
            });
        }
    });

    // Etapa 7: la fecha de vencimiento se guarda en plans/{uid}.
    const expiryMillis = Math.max(
        ...(purchase.lineItems || [])
            .filter(item => item.productId === SUBSCRIPTION_ID)
            .map(item => Date.parse(item.expiryTime))
            .filter(Number.isFinite)
    );

    return { expiryMillis };
}

// =====================================================
// LÍMITE DIARIO POR USUARIO
// =====================================================

async function consumeDailyQuota(uid, characters) {
    const today = new Date().toISOString().slice(0, 10);

    const ref = admin.firestore()
        .collection("premium_tts_usage")
        .doc(uid)
        .collection("days")
        .doc(today);

    await admin.firestore().runTransaction(async transaction => {
        const snapshot = await transaction.get(ref);
        const current = snapshot.exists ? snapshot.data() : {};

        const used = current.characters || 0;
        const requests = current.requests || 0;

        if (
            used + characters > DAILY_CHARACTERS ||
            requests >= DAILY_REQUESTS
        ) {
            throw new HttpsError(
                "resource-exhausted",
                "Llegaste al límite diario de voz Premium."
            );
        }

        transaction.set(ref, {
            characters: used + characters,
            requests: requests + 1,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
    });
}

// =====================================================
// GENERACIÓN DE AUDIO PREMIUM
// =====================================================

exports.synthesizePremiumSpeech = onCall({
    region: "us-central1",

    // Cuenta de servicio autorizada en Google Play.
    serviceAccount:
        "haptivoice-premium-tts@haptivoice-3f6a6.iam.gserviceaccount.com",

    enforceAppCheck: true,
    timeoutSeconds: 60,
    memory: "512MiB",
    maxInstances: 3

}, async request => {

    // Solo usuarios autenticados.
    if (!request.auth?.uid) {
        throw new HttpsError(
            "unauthenticated",
            "Debes iniciar sesión."
        );
    }

    const text = request.data?.text;
    const language = request.data?.language;
    const gender = request.data?.gender;
    const purchaseToken = request.data?.purchaseToken;

    // Selección estricta de una voz autorizada.
    const selectedVoice =
        typeof language === "string" &&
        typeof gender === "string" &&
        Object.hasOwn(voices, language) &&
        Object.hasOwn(voices[language], gender)
            ? voices[language][gender]
            : null;

    // Nunca confiar en los datos enviados por Android.
    if (
        typeof text !== "string" ||
        !text.trim() ||
        text.length > MAX_CHARACTERS_PER_REQUEST ||
        !selectedVoice ||
        typeof purchaseToken !== "string" ||
        purchaseToken.length < 10 ||
        purchaseToken.length > 4096
    ) {
        throw new HttpsError(
            "invalid-argument",
            "Datos de síntesis no válidos."
        );
    }

    // Comprobar compra directamente con Google Play.
    await requireValidSubscription(
        request.auth.uid,
        purchaseToken
    );

    // Reservar cuota antes de generar audio.
    await consumeDailyQuota(
        request.auth.uid,
        text.length
    );

    try {
        const [response] = await ttsClient.synthesizeSpeech({
            input: {
                text: text.trim()
            },
            voice: {
                languageCode: language,
                name: selectedVoice
            },
            audioConfig: {
                audioEncoding: "MP3"
            }
        });

        if (!response.audioContent) {
            throw new Error("La API no devolvió audio");
        }

        const audioBase64 =
            typeof response.audioContent === "string"
                ? response.audioContent
                : Buffer.from(response.audioContent).toString("base64");

        return {
            audioBase64,
            mimeType: "audio/mpeg"
        };

    } catch (error) {
        logger.error("Error de síntesis Cloud TTS", {
            code: error.code
        });

        throw new HttpsError(
            "unavailable",
            "No se pudo generar la voz Premium."
        );
    }
});

// Etapa 7: gestures.js la usa para refreshPlanStatus.
exports.requireValidSubscription = requireValidSubscription;