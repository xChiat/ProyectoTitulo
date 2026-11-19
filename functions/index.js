const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require('crypto');

admin.initializeApp();

const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASS
    }
});

function hashSHA256(text) {
    return crypto.createHash('sha256').update(text).digest('hex');
}

exports.sendRecoveryOtp = onCall(async (request) => {
    const email = request.data.email;

    if (!email) {
        throw new HttpsError('invalid-argument', 'El correo es obligatorio.');
    }

    const otp = Math.floor(10000 + Math.random() * 90000).toString();
    const hashedOtp = hashSHA256(otp);

    try {
        await admin.firestore().collection('password_resets').doc(email).set({
            otp: hashedOtp,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            expiresAt: new Date(Date.now() + 15 * 60 * 1000)
        });

        const mailOptions = {
            from: '"HaptiVoice Soporte" <infinity.creations.xx@gmail.com>',
            to: email,
            subject: 'Código de recuperación de contraseña',
            text: `Tu código de recuperación es: ${otp}`,
            html: `
                <h2>Recuperación de contraseña</h2>
                <p>Tu código de seguridad de 5 dígitos es: <b>${otp}</b></p>
                <p>Este código expirará en 15 minutos.</p>
                <p>Si no solicitaste este cambio, ignora este mensaje.</p>
            `
        };

        await transporter.sendMail(mailOptions);
        logger.info(`OTP enviado exitosamente a ${email}`, { structuredData: true });

        return { success: true, message: 'Correo enviado exitosamente.' };

    } catch (error) {
        logger.error('Error al procesar la solicitud de OTP', error);
        throw new HttpsError('internal', 'No se pudo enviar el correo de recuperación.');
    }
});

exports.verifyRecoveryOtp = onCall(async (request) => {
    const email = request.data.email;
    const otp = request.data.otp;

    if (!email || !otp) {
        throw new HttpsError('invalid-argument', 'Faltan datos para la verificación.');
    }

    const resetDocRef = admin.firestore().collection('password_resets').doc(email);
    const doc = await resetDocRef.get();

    if (!doc.exists) {
        throw new HttpsError('not-found', 'No hay solicitud pendiente para este correo.');
    }

    const data = doc.data();
    const hashedInputOtp = hashSHA256(otp);

    if (data.otp !== hashedInputOtp) {
        throw new HttpsError('permission-denied', 'El código ingresado es incorrecto.');
    }

    if (data.expiresAt.toDate() < new Date()) {
        throw new HttpsError('deadline-exceeded', 'El código ha expirado. Solicita uno nuevo.');
    }

    return { success: true };
});

exports.resetPasswordWithOtp = onCall(async (request) => {
    const email = request.data.email;
    const otp = request.data.otp;
    const newPassword = request.data.newPassword;

    if (!email || !otp || !newPassword) {
        throw new HttpsError('invalid-argument', 'Faltan datos para restablecer la contraseña.');
    }

    const resetDocRef = admin.firestore().collection('password_resets').doc(email);
    const doc = await resetDocRef.get();

    if (!doc.exists) {
        throw new HttpsError('not-found', 'No se solicitó ningún cambio para este correo.');
    }

    const data = doc.data();
    const hashedInputOtp = hashSHA256(otp);

    if (data.otp !== hashedInputOtp) {
        throw new HttpsError('permission-denied', 'El código ingresado es incorrecto.');
    }

    if (data.expiresAt.toDate() < new Date()) {
        throw new HttpsError('deadline-exceeded', 'El código ha expirado. Solicita uno nuevo.');
    }

    try {
        const userRecord = await admin.auth().getUserByEmail(email);
        await admin.auth().updateUser(userRecord.uid, { password: newPassword });

        await resetDocRef.delete();

        return { success: true, message: 'Contraseña actualizada correctamente.' };
    } catch (error) {
        logger.error('Error al cambiar contraseña', error);
        throw new HttpsError('internal', 'Ocurrió un error al intentar cambiar la contraseña.');
    }
});

//
// =====================================================
// HAPTIVOICE PREMIUM - GOOGLE CLOUD TEXT TO SPEECH
// =====================================================
//
exports.synthesizePremiumSpeech =
    require("./premiumTts").synthesizePremiumSpeech;
