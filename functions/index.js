const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

// ============================================================================
// CONFIGURACIÓN DEL SERVIDOR DE CORREO (SMTP)
// ============================================================================
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASS
    }
});

exports.sendRecoveryOtp = onCall(async (request) => {
    // Obtenemos el correo enviado desde la app de Android
    const email = request.data.email;

    if (!email) {
        throw new HttpsError('invalid-argument', 'El correo es obligatorio.');
    }

    // 1. Generar un código de 5 dígitos (entre 10000 y 99999)
    const otp = Math.floor(10000 + Math.random() * 90000).toString();

    try {
        // 2. Guardar el código en Firestore para validarlo después
        // Creamos una colección llamada 'password_resets'
        await admin.firestore().collection('password_resets').doc(email).set({
            otp: otp,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            expiresAt: new Date(Date.now() + 15 * 60 * 1000)
        });

        // 3. Configurar el formato del correo
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

        // 4. Enviar el correo
        await transporter.sendMail(mailOptions);
        logger.info(`OTP enviado exitosamente a ${email}`, { structuredData: true });

        // Retornar éxito a la app de Android
        return { success: true, message: 'Correo enviado exitosamente.' };

    } catch (error) {
        logger.error('Error al procesar la solicitud de OTP', error);
        throw new HttpsError('internal', 'No se pudo enviar el correo de recuperación.');
    }
});