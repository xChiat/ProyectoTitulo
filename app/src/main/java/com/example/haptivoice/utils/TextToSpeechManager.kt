package com.example.haptivoice.utils

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

@Suppress("DEPRECATION")
class TextToSpeechManager(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var currentLanguage: Locale = DEFAULT_LANGUAGE
    private var selectedGender: VoiceGender = VoiceGender.NEUTRAL
    private var selectedTier: VoiceQualityTier = VoiceQualityTier.NORMAL

    var onInitResult: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isReady = false
            Log.e(TAG, "Fallo al inicializar TextToSpeech: $status")
            onInitResult?.invoke(false)
            return
        }

        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        isReady = applyLanguage(DEFAULT_LANGUAGE)
        if (isReady) {
            applySelectedVoice()
        }
        onInitResult?.invoke(isReady)
    }

    fun setLanguage(language: SupportedLanguage): Boolean {
        if (!isReady) return false
        return applyLanguage(language.locale)
    }

    fun speak(
        text: String,
        flushQueue: Boolean = true,
        utteranceId: String = "haptivoice-tts",
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val engine = tts
        if (!isReady || engine == null) {
            onError?.invoke("TextToSpeech no está inicializado")
            return
        }
        if (text.isBlank()) {
            onError?.invoke("No hay texto para sintetizar")
            return
        }

        if (onDone != null || onError != null) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    onDone?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onError?.invoke("Error al sintetizar el texto")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError?.invoke("Error TTS ($errorCode)")
                }
            })
        }

        val queueMode =
            if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(text, queueMode, Bundle(), utteranceId)
        if (result == TextToSpeech.ERROR) {
            onError?.invoke("No se pudo encolar la síntesis")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun isReady(): Boolean = isReady

    fun getAllVoices(): List<Voice> {
        val engine = tts
        if (!isReady || engine == null) return emptyList()
        return engine.voices.orEmpty().sortedBy { it.name }
    }

    fun getVoicesForCurrentLanguage(): List<Voice> {
        val language = currentLanguage.language
        return getAllVoices().filter {
            it.locale.language.equals(language, ignoreCase = true)
        }
    }

    fun setVoice(voice: Voice): Boolean {
        val engine = tts ?: return false
        if (!isReady) return false
        return engine.setVoice(voice) == TextToSpeech.SUCCESS
    }

    fun setVoiceByName(voiceName: String): Boolean {
        val voice = tts?.voices?.firstOrNull { it.name == voiceName }
        if (voice == null) {
            Log.e(TAG, "No se encontró la voz: $voiceName")
            return false
        }
        return setVoice(voice)
    }

    fun setVoiceGender(gender: VoiceGender): Boolean {
        selectedGender = gender
        return applySelectedVoice()
    }

    fun setVoiceFromProfile(voiceType: String): Boolean {
        selectedGender = when (voiceType.lowercase()) {
            "femenina", "femenino" -> VoiceGender.FEMALE
            "masculina", "masculino" -> VoiceGender.MALE
            else -> VoiceGender.NEUTRAL
        }
        return applySelectedVoice()
    }

    fun applySelectedVoice(): Boolean {
        if (!isReady) return false

        val voiceName = resolveVoiceName(
            language = currentLanguage,
            gender = selectedGender,
            tier = selectedTier
        )

        if (voiceName == null) {
            Log.w(TAG, "Aún no hay voz HIGH configurada (Cloud TTS)")
            return false
        }

        return setVoiceByName(voiceName)
    }

    fun getVoicesQualityNormal(): List<Voice> {
        return filterVoicesByExactQuality(Voice.QUALITY_NORMAL)
    }

    fun getVoicesQualityVeryHigh(): List<Voice> {
        val veryHigh = filterVoicesByExactQuality(Voice.QUALITY_VERY_HIGH)
        if (veryHigh.isNotEmpty()) return veryHigh
        return filterVoicesByExactQuality(Voice.QUALITY_HIGH)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun applyLanguage(locale: Locale): Boolean {
        val engine = tts ?: return false
        val availability = engine.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(TAG, "Idioma no disponible: $locale ($availability)")
            return false
        }

        val result = engine.setLanguage(locale)
        val ok = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

        if (ok) {
            currentLanguage = locale
            applySelectedVoice()
        }
        return ok
    }

    private fun resolveVoiceName(
        language: Locale,
        gender: VoiceGender,
        tier: VoiceQualityTier
    ): String? {
        if (tier == VoiceQualityTier.HIGH) {
            return null
        }

        val isSpanish = language.language.equals("es", ignoreCase = true)
        return when {
            isSpanish && gender == VoiceGender.FEMALE -> ES_FEMALE_NORMAL
            isSpanish && gender == VoiceGender.MALE -> ES_MALE_NORMAL
            isSpanish -> ES_NEUTRAL_NORMAL
            gender == VoiceGender.FEMALE -> EN_FEMALE_NORMAL
            gender == VoiceGender.MALE -> EN_MALE_NORMAL
            else -> EN_NEUTRAL_NORMAL
        }
    }

    private fun filterVoicesByExactQuality(quality: Int): List<Voice> {
        val language = currentLanguage.language
        return getAllVoices().filter {
            it.quality == quality &&
                    it.locale.language.equals(language, ignoreCase = true)
        }
    }

    enum class SupportedLanguage(val locale: Locale) {
        SPANISH(Locale("es", "ES")),
        ENGLISH(Locale("en", "US"))
    }

    enum class VoiceGender {
        FEMALE,
        MALE,
        NEUTRAL
    }

    enum class VoiceQualityTier {
        NORMAL,
        HIGH
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
        private val DEFAULT_LANGUAGE: Locale = SupportedLanguage.SPANISH.locale

        const val ES_FEMALE_NORMAL = "es-ES-language"
        const val ES_MALE_NORMAL = "es-us-x-esf-local"
        const val ES_NEUTRAL_NORMAL = "es-es-x-eea-local"

        const val EN_FEMALE_NORMAL = "en-AU-language"
        const val EN_MALE_NORMAL = "en-us-x-iom-local"
        const val EN_NEUTRAL_NORMAL = "en-us-x-sfg-local"
    }
}