package com.example.haptivoice.utils

import com.google.firebase.functions.FirebaseFunctions

class TranslationManager {

    // Proveedor de traducción usado por el momento.
    // Actualmente, se invoca mediante Firebase Cloud Functions,
    // pero puede reemplazarse por otro proveedor más adelante.
    private val functions = FirebaseFunctions.getInstance()

    fun translateSpanishToEnglish(
        text: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        ttsManager: TextToSpeechManager? = null
    ) {
        translateText(
            text = text,
            sourceLanguage = "es",
            targetLanguage = "en",
            onResult = { translated ->
                ttsManager?.setLanguage(TextToSpeechManager.SupportedLanguage.ENGLISH)
                onResult(translated)
            },
            onError = onError
        )
    }

    fun translateEnglishToSpanish(
        text: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        ttsManager: TextToSpeechManager? = null
    ) {
        translateText(
            text = text,
            sourceLanguage = "en",
            targetLanguage = "es",
            onResult = { translated ->
                ttsManager?.setLanguage(TextToSpeechManager.SupportedLanguage.SPANISH)
                onResult(translated)
            },
            onError = onError
        )
    }

    private fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (text.isBlank()) {
            onError("No hay texto para traducir")
            return
        }

        val data = hashMapOf(
            "text" to text,
            "sourceLanguage" to sourceLanguage,
            "targetLanguage" to targetLanguage
        )

        // Firebase se utiliza por el momento para ejecutar la traducción.
        // Esta llamada puede adaptarse en el futuro si se cambia de proveedor.
        functions
            .getHttpsCallable("translateText")
            .call(data)
            .addOnSuccessListener { result ->

                val response = result.data as? Map<*, *>

                val translatedText =
                    response?.get("translatedText") as? String

                if (translatedText != null) {
                    onResult(translatedText)
                } else {
                    onError("No se pudo obtener la traducción")
                }
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.localizedMessage
                        ?: "Error al realizar la traducción"
                )
            }
    }
}