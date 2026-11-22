"use strict";

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

// =====================================================
// PUBLICAR EL DICCIONARIO BASE (ETAPA 8, CU-012)
// =====================================================
// Copia los lemas activos del borrador (lemmas) a una
// publicación nueva: dictionary_publications/v{n}.
// config/dictionary apunta a la última versión; los
// teléfonos la comparan con la suya y descargan solo
// si cambió.
//
// Las publicaciones antiguas no se borran: quedan como
// historial de versiones (RF-22).

// Firestore admite documentos de hasta 1 MiB.
const MAX_PUBLICATION_BYTES = 900 * 1024;

exports.publishDictionary = onCall({
    region: "us-central1",
    enforceAppCheck: true,
    timeoutSeconds: 60,
    maxInstances: 2

}, async request => {

    if (!request.auth?.uid) {
        throw new HttpsError(
            "unauthenticated",
            "Debes iniciar sesión."
        );
    }

    // Solo administradores (custom claim admin: true).
    if (request.auth.token?.admin !== true) {
        throw new HttpsError(
            "permission-denied",
            "Solo un administrador puede publicar el diccionario."
        );
    }

    const db = admin.firestore();
    const configRef = db.collection("config").doc("dictionary");

    const result = await db.runTransaction(async transaction => {

        const config = await transaction.get(configRef);

        const activeLemmas = await transaction.get(
            db.collection("lemmas").where("isActive", "==", true)
        );

        const lemmas = activeLemmas.docs
            .map(doc => {
                const data = doc.data();
                return {
                    lemmaId: doc.id,
                    glosa: data.glosa,
                    gesto: data.gesto
                };
            })
            .filter(lemma =>
                typeof lemma.glosa === "string" &&
                lemma.glosa.length > 0 &&
                lemma.gesto &&
                typeof lemma.gesto === "object"
            )
            .sort((a, b) => a.glosa.localeCompare(b.glosa, "es"));

        const approxBytes = Buffer.byteLength(JSON.stringify(lemmas), "utf8");

        if (approxBytes > MAX_PUBLICATION_BYTES) {
            throw new HttpsError(
                "failed-precondition",
                "El diccionario es demasiado grande para una sola publicación."
            );
        }

        const previous = config.exists ? (config.data().latestVersion || 0) : 0;
        const version = previous + 1;
        const versionId = `v${version}`;
        const now = admin.firestore.FieldValue.serverTimestamp();

        transaction.create(
            db.collection("dictionary_publications").doc(versionId),
            {
                versionId,
                version,
                publishedAt: now,
                publishedBy: request.auth.uid,
                lemmaCount: lemmas.length,
                lemmas
            }
        );

        transaction.set(configRef, {
            latestVersion: version,
            versionId,
            publishedAt: now
        });

        return { version, versionId, lemmaCount: lemmas.length };
    });

    logger.info("Diccionario publicado", result);

    return result;
});