package com.example.haptivoice.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SessionManager(context: Context) {

    // Archivo de preferencias privado para la app
    private val prefs: SharedPreferences = context.getSharedPreferences("HaptiVoiceSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        // 90 días en milisegundos: 90 días * 24 hrs * 60 min * 60 seg * 1000 ms
        private const val NINETY_DAYS_IN_MS = 90L * 24 * 60 * 60 * 1000
    }

    /**
     * Llama a esta función cuando el usuario inicie sesión o se registre con éxito.
     */
    fun saveSession() {
        prefs.edit { putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis()) }
    }

    /**
     * Comprueba si la sesión fue creada hace menos de 90 días.
     */
    fun isSessionValid(): Boolean {
        val loginTime = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)

        // Si es 0, significa que nunca se guardó la sesión
        if (loginTime == 0L) return false

        val currentTime = System.currentTimeMillis()
        return (currentTime - loginTime) < NINETY_DAYS_IN_MS
    }

    /**
     * Limpia la marca de tiempo de la sesión (usar al cerrar sesión).
     */
    fun clearSession() {
        prefs.edit { remove(KEY_LOGIN_TIMESTAMP) }
    }
}