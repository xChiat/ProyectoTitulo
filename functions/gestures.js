"use strict";

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { requireValidSubscription } = require("./premiumTts");

// =====================================================
// GESTOS PROPIOS (ETAPA 7, PUNTOS 10.4.2 Y 10.4.3)
// =====================================================

// Debe coincidir con SubscriptionManager.FREE_GESTURE_LIMIT.
const FREE_GESTURE_LIMIT = 8;

// =====================================================
// ¿ES PREMIUM SEGÚN EL SERVIDOR?
// =====================================================
// plans/{uid} solo lo escribe refreshPlanStatus.
// Sin documento, o con la fecha vencida, cuenta como básico.

async function isPremiumOnServer(uid) {
    const snapshot = await admin.firestore()
        .collection("plans")
        .doc(uid)
        .get();

    if (!snapshot.exists) {
        return false;
    }

    const plan = snapshot.data();
    const expiration = plan.expirationDate?.toMillis?.() ?? 0;

    return plan.isPremium === true && expiration > Date.now();
}

// =====================================================
// ACTUALIZAR plans/{uid} CON GOOGLE PLAY
// =====================================================
// Android la llama al abrir Home si tiene purchaseToken.

exports.refreshPlanStatus = onCall({
    region: "us-central1",

    // Misma cuenta de servicio que la voz Premium:
    // es la autorizada para consultar Google Play.
    serviceAccount:
        "haptivoice-premium-tts@haptivoice-3f6a6.iam.gserviceaccount.com",

    enforceAppCheck: true,
    timeoutSeconds: 30,
    maxInstances: 3

}, async request => {

    const uid = request.auth?.uid;

    if (!uid) {
        throw new HttpsError(
            "unauthenticated",
            "Debes iniciar sesión."
        );
    }

    const purchaseToken = request.data?.purchaseToken;

    if (
        typeof purchaseToken !== "string" ||
        purchaseToken.length < 10 ||
        purchaseToken.length > 4096
    ) {
        throw new HttpsError(
            "invalid-argument",
            "Token de compra no válido."
        );
    }

    const planRef = admin.firestore().collection("plans").doc(uid);

    try {
        const { expiryMillis } =
            await requireValidSubscription(uid, purchaseToken);

        await planRef.set({
            type: "premium",
            isPremium: true,
            expirationDate:
                admin.firestore.Timestamp.fromMillis(expiryMillis),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        return { isPremium: true, expirationDate: expiryMillis };

    } catch (error) {

        // Google Play dijo que no es válida: pasa a básico.
        // Si Google Play no respondió ("unavailable"), no se toca.
        if (error instanceof HttpsError && error.code === "permission-denied") {
            await planRef.set({
                type: "basic",
                isPremium: false,
                expirationDate: null,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });

            return { isPremium: false };
        }

        throw error;
    }
});

// =====================================================
// TOPE DE 8 GESTOS ACTIVOS EN PLAN GRATUITO
// =====================================================
// Se ejecuta cuando un gesto queda activo (se crea o se
// reactiva). Si el usuario no es Premium y ya tiene más
// de 8 activos, este gesto queda desactivado.
//
// Los gestos que ya estaban activos no se tocan: si vence
// Premium, la app sigue usando los 8 primeros de la lista
// y muestra el resto como bloqueados (decisión 21-11-2026).

exports.enforceGestureQuota = onDocumentWritten({
    document: "custom_gestures/{gestureId}",

    // Debe ser la región de la base de Firestore. Si el deploy
    // reclama por la región, cámbiala por la de la consola
    // (Firestore > Configuración > Ubicación).
    region: "southamerica-west1",
    maxInstances: 3

}, async event => {

    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    // Borrado, o no quedó activo: nada que revisar.
    if (!after || after.isActive !== true) {
        return;
    }

    // Ya estaba activo antes (por ejemplo, solo cambió el orden).
    if (before?.isActive === true) {
        return;
    }

    const uid = after.userId;

    if (typeof uid !== "string" || !uid) {
        return;
    }

    if (await isPremiumOnServer(uid)) {
        return;
    }

    const activeCount = (
        await admin.firestore()
            .collection("custom_gestures")
            .where("userId", "==", uid)
            .where("isActive", "==", true)
            .count()
            .get()
    ).data().count;

    if (activeCount <= FREE_GESTURE_LIMIT) {
        return;
    }

    // updatedAt del servidor: así el teléfono acepta este
    // cambio en la próxima sincronización.
    await event.data.after.ref.update({
        isActive: false,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    logger.info("Gesto desactivado por tope del plan gratuito", {
        gestureId: event.params.gestureId,
        activeCount
    });
});