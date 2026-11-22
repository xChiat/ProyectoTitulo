"use strict";

// =====================================================
// ASIGNAR O QUITAR EL ROL DE ADMINISTRADOR (ETAPA 8)
// =====================================================
// Uso (desde la carpeta functions):
//
//   node scripts/setAdmin.js correo@ejemplo.com
//   node scripts/setAdmin.js correo@ejemplo.com --quitar
//
// Necesita la variable GOOGLE_APPLICATION_CREDENTIALS con
// la ruta de una clave de cuenta de servicio del proyecto.
// La clave NUNCA se sube a GitHub ni se deja en el proyecto.

const admin = require("firebase-admin");

admin.initializeApp();

async function main() {
    const email = process.argv[2];
    const remove = process.argv.includes("--quitar");

    if (!email) {
        console.error("Falta el correo. Ej: node scripts/setAdmin.js correo@ejemplo.com");
        process.exit(1);
    }

    const user = await admin.auth().getUserByEmail(email);
    const claims = { ...(user.customClaims || {}) };

    if (remove) {
        delete claims.admin;
    } else {
        claims.admin = true;
    }

    await admin.auth().setCustomUserClaims(user.uid, claims);

    console.log(
        remove
            ? `Rol de administrador quitado a ${email} (${user.uid}).`
            : `${email} (${user.uid}) ahora es administrador.`
    );
    console.log("Vuelve a abrir el Diccionario en la app para que se note el cambio.");
}

main().catch(error => {
    console.error("Error:", error.message);
    process.exit(1);
});