
package com.example.haptivoice

import android.app.Application
import android.util.Log
import com.example.haptivoice.utils.AppCheckInitializer

/**
 * Configuración inicial de HaptiVoice.
 *
 * Inicializa Firebase App Check antes de utilizar
 * los servicios de Firebase.
 *
 * Debug: utiliza el proveedor de depuración.
 * Release: utiliza Google Play Integrity.
 */
class HaptiVoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            AppCheckInitializer.initialize(
                applicationContext
            )

            Log.d(
                TAG,
                "Firebase App Check inicializado correctamente"
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
