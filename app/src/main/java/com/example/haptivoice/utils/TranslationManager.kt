
package com.example.haptivoice.utils

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager {

    private val translators = mutableMapOf<String, Translator>()
    private val readyTranslators = mutableSetOf<String>()

    // Solicitudes que esperan la descarga del mismo modelo.
    private data class PendingRequest(
        val onReady: (Translator) -> Unit,
        val onError: (String) -> Unit
    )

    private val pendingRequests =
        mutableMapOf<String, MutableList<PendingRequest>>()

    private var isClosed = false

    // =====================================================
    // TRADUCCION ESPAÑOL A INGLES
    // =====================================================

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
                ttsManager?.setLanguage(
                    TextToSpeechManager.SupportedLanguage.ENGLISH
                )
                onResult(translated)
            },
            onError = onError
        )
    }

    // =====================================================
    // TRADUCCION INGLES A ESPAÑOL
    // =====================================================

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
                ttsManager?.setLanguage(
                    TextToSpeechManager.SupportedLanguage.SPANISH
                )
                onResult(translated)
            },
            onError = onError
        )
    }

    // =====================================================
    // DESCARGA ANTICIPADA DE MODELOS
    // =====================================================

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
                    onReady = {
                        onReady()
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    // =====================================================
    // LIBERAR RECURSOS
    // =====================================================

    fun close() {
        if (isClosed) return

        isClosed = true

        pendingRequests.clear()
        readyTranslators.clear()

        translators.values.forEach { translator ->
            runCatching {
                translator.close()
            }
        }

        translators.clear()
    }

    // =====================================================
    // REALIZAR TRADUCCION
    // =====================================================

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

        val cleanText = text.trim()

        if (cleanText.isBlank()) {
            onError("No hay texto para traducir")
            return
        }

        ensureTranslator(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            onReady = { translator ->

                if (!isClosed) {
                    translator.translate(cleanText)
                        .addOnSuccessListener { translatedText ->
                            if (!isClosed) {
                                onResult(translatedText)
                            }
                        }
                        .addOnFailureListener { exception ->
                            if (!isClosed) {
                                Log.e(
                                    TAG,
                                    "Error al traducir",
                                    exception
                                )

                                onError(
                                    exception.localizedMessage
                                        ?: "Error al realizar la traducción"
                                )
                            }
                        }
                }
            },
            onError = onError
        )
    }

    // =====================================================
    // PREPARAR TRADUCTOR Y COMPROBAR MODELO
    // =====================================================

    private fun ensureTranslator(
        sourceLanguage: String,
        targetLanguage: String,
        onReady: (Translator) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isClosed) {
            onError("TranslationManager ya fue cerrado")
            return
        }

        val key = "$sourceLanguage->$targetLanguage"

        val cached = translators[key]

        // Un traductor solamente está listo cuando la descarga
        // del modelo terminó satisfactoriamente.
        if (cached != null && key in readyTranslators) {
            onReady(cached)
            return
        }

        // Si hay una descarga en curso, encolamos esta solicitud
        // para evitar tratar el modelo como disponible antes de tiempo.
        pendingRequests.getOrPut(key) {
            mutableListOf()
        }.add(
            PendingRequest(
                onReady = onReady,
                onError = onError
            )
        )

        if (cached != null) {
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)

        translators[key] = translator

        // Los modelos solo se descargan mediante Wi-Fi.
        // Si ya están descargados pueden utilizarse sin Internet.
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {

                if (isClosed) {
                    return@addOnSuccessListener
                }

                readyTranslators.add(key)

                Log.d(TAG, "Modelo listo: $key")

                val requests = pendingRequests.remove(key)
                    .orEmpty()

                requests.forEach { request ->
                    request.onReady(translator)
                }
            }
            .addOnFailureListener { exception ->

                if (isClosed) {
                    return@addOnFailureListener
                }

                translators.remove(key)
                readyTranslators.remove(key)

                runCatching {
                    translator.close()
                }

                Log.e(
                    TAG,
                    "No se pudo descargar el modelo $key",
                    exception
                )

                val message =
                    "No se pudo preparar el modelo de traducción. " +
                            "Comprueba tu conexión Wi-Fi e inténtalo nuevamente. " +
                            (exception.localizedMessage ?: "")

                val requests = pendingRequests.remove(key)
                    .orEmpty()

                requests.forEach { request ->
                    request.onError(message)
                }
            }
    }

    companion object {
        private const val TAG = "TranslationManager"
    }
}
