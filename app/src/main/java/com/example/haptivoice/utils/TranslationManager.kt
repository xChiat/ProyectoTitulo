package com.example.haptivoice.utils

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager {

    private val translators = mutableMapOf<String, Translator>()
    private var isClosed = false

    fun translateSpanishToEnglish(
        text: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        ttsManager: TextToSpeechManager? = null
    ) {
        translateText(
            text = text,
            sourceLanguage = TranslateLanguage.SPANISH,
            targetLanguage = TranslateLanguage.ENGLISH,
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
            sourceLanguage = TranslateLanguage.ENGLISH,
            targetLanguage = TranslateLanguage.SPANISH,
            onResult = { translated ->
                ttsManager?.setLanguage(TextToSpeechManager.SupportedLanguage.SPANISH)
                onResult(translated)
            },
            onError = onError
        )
    }

    fun preloadModels(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        ensureTranslator(
            sourceLanguage = TranslateLanguage.SPANISH,
            targetLanguage = TranslateLanguage.ENGLISH,
            onReady = {
                ensureTranslator(
                    sourceLanguage = TranslateLanguage.ENGLISH,
                    targetLanguage = TranslateLanguage.SPANISH,
                    onReady = { onReady() },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun close() {
        isClosed = true
        translators.values.forEach { translator ->
            runCatching { translator.close() }
        }
        translators.clear()
    }

    private fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isClosed) {
            onError("TranslationManager ya fue cerrado")
            return
        }

        if (text.isBlank()) {
            onError("No hay texto para traducir")
            return
        }

        ensureTranslator(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            onReady = { translator ->
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        onResult(translatedText)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al traducir", exception)
                        onError(
                            exception.localizedMessage
                                ?: "Error al realizar la traducción"
                        )
                    }
            },
            onError = onError
        )
    }

    private fun ensureTranslator(
        sourceLanguage: String,
        targetLanguage: String,
        onReady: (Translator) -> Unit,
        onError: (String) -> Unit
    ) {
        val key = "$sourceLanguage->$targetLanguage"
        val cached = translators[key]
        if (cached != null) {
            onReady(cached)
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)
        translators[key] = translator

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                onReady(translator)
            }
            .addOnFailureListener { exception ->
                translators.remove(key)
                runCatching { translator.close() }
                Log.e(TAG, "No se pudo descargar el modelo $key", exception)
                onError(
                    exception.localizedMessage
                        ?: "No se pudo descargar el modelo de traducción. Conéctate a Wi-Fi e inténtalo otra vez."
                )
            }
    }

    companion object {
        private const val TAG = "TranslationManager"
    }
}