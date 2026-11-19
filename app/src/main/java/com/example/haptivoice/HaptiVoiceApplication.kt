
package com.example.haptivoice

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Configuración inicial de HaptiVoice.
 *
 * Inicializa Firebase App Check con Google Play Integrity
 * antes de utilizar los servicios de Firebase.
 *
 * Protege las solicitudes dirigidas a las funciones
 * Premium mediante tokens de verificación.
 */
class HaptiVoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // Inicializar Firebase.
            FirebaseApp.initializeApp(this)

            // Configurar Firebase App Check.
            val firebaseAppCheck = FirebaseAppCheck.getInstance()

            // Utilizar Google Play Integrity.
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )

            Log.d(
                TAG,
                "Firebase App Check inicializado con Play Integrity"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error al inicializar Firebase App Check",
                e
            )
        }
    }

    companion object {
        private const val TAG = "HaptiVoiceAppCheck"
    }
}