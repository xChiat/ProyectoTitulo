
const { onCall, HttpsError } = require("firebase-functions/v2/https");

//permite ejecutar la limpieza automáticamente.
const { onSchedule } = require("firebase-functions/v2/scheduler");

const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

admin.initializeApp();

const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASS
    }
});

function hashSHA256(text) {
    return crypto.createHash("sha256").update(text).digest("hex");
}

// =====================================================
// ELIMINACIÓN SEGURA DE DOCUMENTOS OTP
// =====================================================
// Evita borrar un OTP nuevo si otro proceso actualizó
// el documento después de que lo leímos.

async function deleteOtpIfUnchanged(snapshot) {
    try {
        await snapshot.ref.delete({
            lastUpdateTime: snapshot.updateTime
        });

        return true;

    } catch (error) {
        // El documento fue eliminado o actualizado
        // por otro proceso. No tocar la versión nueva.
        if (
            error.code === 5 ||
            error.code === 9 ||
            error.code === "not-found" ||
            error.code === "failed-precondition"
        ) {
            return false;
        }

        throw error;
    }
}

// =====================================================
// ENVIAR CÓDIGO OTP
// =====================================================

exports.sendRecoveryOtp = onCall(async (request) => {
    const email = request.data.email;

    if (!email) {
        throw new HttpsError(
            "invalid-argument",
            "El correo es obligatorio."
        );
    }

    const otp = Math.floor(
        10000 + Math.random() * 90000
    ).toString();

    const hashedOtp = hashSHA256(otp);

    try {
        await admin.firestore()
            .collection("password_resets")
            .doc(email)
            .set({
                otp: hashedOtp,
                createdAt:
                    admin.firestore.FieldValue.serverTimestamp(),
                expiresAt: new Date(
                    Date.now() + 15 * 60 * 1000
                )
            });

        const mailOptions = {
            from: '"HaptiVoice Soporte" <infinity.creations.xx@gmail.com>',
            to: email,
            subject: "Código de recuperación de contraseña",
            text: `Tu código de recuperación es: ${otp}`,
            html: `
                <h2>Recuperación de contraseña</h2>
                <p>Tu código de seguridad de 5 dígitos es:
                <b>${otp}</b></p>
                <p>Este código expirará en 15 minutos.</p>
                <p>Si no solicitaste este cambio,
                ignora este mensaje.</p>
            `
        };

        await transporter.sendMail(mailOptions);

        logger.info(
            `OTP enviado exitosamente a ${email}`,
            { structuredData: true }
        );

        return {
            success: true,
            message: "Correo enviado exitosamente."
        };

    } catch (error) {
        logger.error(
            "Error al procesar la solicitud de OTP",
            error
        );

        throw new HttpsError(
            "internal",
            "No se pudo enviar el correo de recuperación."
        );
    }
});

// =====================================================
// VERIFICAR CÓDIGO OTP
// =====================================================

exports.verifyRecoveryOtp = onCall(async (request) => {
    const email = request.data.email;
    const otp = request.data.otp;

    if (!email || !otp) {
        throw new HttpsError(
            "invalid-argument",
            "Faltan datos para la verificación."
        );
    }

    const resetDocRef = admin.firestore()
        .collection("password_resets")
        .doc(email);

    const doc = await resetDocRef.get();

    if (!doc.exists) {
        throw new HttpsError(
            "not-found",
            "No hay solicitud pendiente para este correo."
        );
    }

    const data = doc.data();

    // si ya venció, eliminar el documento
    // siempre que no haya sido reemplazado.
    if (data.expiresAt.toDate() <= new Date()) {
        await deleteOtpIfUnchanged(doc);

        throw new HttpsError(
            "deadline-exceeded",
            "El código ha expirado. Solicita uno nuevo."
        );
    }

    const hashedInputOtp = hashSHA256(otp);

    if (data.otp !== hashedInputOtp) {
        throw new HttpsError(
            "permission-denied",
            "El código ingresado es incorrecto."
        );
    }

    return { success: true };
});

// =====================================================
// RESTABLECER CONTRASEÑA CON OTP
// =====================================================

exports.resetPasswordWithOtp = onCall(async (request) => {
    const email = request.data.email;
    const otp = request.data.otp;
    const newPassword = request.data.newPassword;

    if (!email || !otp || !newPassword) {
        throw new HttpsError(
            "invalid-argument",
            "Faltan datos para restablecer la contraseña."
        );
    }

    const resetDocRef = admin.firestore()
        .collection("password_resets")
        .doc(email);

    const doc = await resetDocRef.get();

    if (!doc.exists) {
        throw new HttpsError(
            "not-found",
            "No se solicitó ningún cambio para este correo."
        );
    }

    const data = doc.data();

    // limpiar un OTP vencido si sigue
    // siendo el mismo documento.
    if (data.expiresAt.toDate() <= new Date()) {
        await deleteOtpIfUnchanged(doc);

        throw new HttpsError(
            "deadline-exceeded",
            "El código ha expirado. Solicita uno nuevo."
        );
    }

    const hashedInputOtp = hashSHA256(otp);

    if (data.otp !== hashedInputOtp) {
        throw new HttpsError(
            "permission-denied",
            "El código ingresado es incorrecto."
        );
    }

    try {
        const userRecord = await admin.auth()
            .getUserByEmail(email);

        await admin.auth().updateUser(
            userRecord.uid,
            { password: newPassword }
        );

        await resetDocRef.delete();

        return {
            success: true,
            message: "Contraseña actualizada correctamente."
        };

    } catch (error) {
        logger.error(
            "Error al cambiar contraseña",
            error
        );

        throw new HttpsError(
            "internal",
            "Ocurrió un error al intentar cambiar la contraseña."
        );
    }
});

// =====================================================
// HAPTIVOICE PREMIUM - GOOGLE CLOUD TEXT TO SPEECH
// =====================================================

exports.synthesizePremiumSpeech =
    require("./premiumTts").synthesizePremiumSpeech;

    // =====================================================
    // GESTOS PROPIOS: PLAN Y TOPE DEL PLAN GRATUITO (ETAPA 7)
    // =====================================================

    const gestures = require("./gestures");

    exports.refreshPlanStatus = gestures.refreshPlanStatus;
    exports.enforceGestureQuota = gestures.enforceGestureQuota;

exports.publishDictionary = require("./dictionary").publishDictionary;

// =====================================================
// LIMPIEZA AUTOMÁTICA DE OTP EXPIRADOS
// =====================================================
// Revisa Firestore una vez por minuto.
// Elimina hasta 100 OTP vencidos por ejecución.
// No necesita que un usuario abra la aplicación.

exports.cleanupExpiredRecoveryOtps = onSchedule(
    {
        schedule: "* * * * *",
        region: "us-central1",
        timeoutSeconds: 120
    },
    async () => {
        const db = admin.firestore();
        const now = admin.firestore.Timestamp.now();

        const expiredOtps = await db
            .collection("password_resets")
            .where("expiresAt", "<=", now)
            .limit(100)
            .get();

        let deleted = 0;
        let skipped = 0;

        for (const doc of expiredOtps.docs) {
            const wasDeleted =
                await deleteOtpIfUnchanged(doc);

            if (wasDeleted) {
                deleted++;
            } else {
                skipped++;
            }
        }

        logger.info(
            "Limpieza automática de OTP finalizada",
            {
                encontrados: expiredOtps.size,
                eliminados: deleted,
                omitidos: skipped
            }
        );
    }
);
