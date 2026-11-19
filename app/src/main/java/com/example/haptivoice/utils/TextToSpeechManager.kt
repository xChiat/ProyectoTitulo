
package com.example.haptivoice.utils

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // =====================================================
    // MOTOR LOCAL DE ANDROID
    // =====================================================

    private var tts: TextToSpeech? = null
    private var isReady = false

    private var currentLanguage: Locale = DEFAULT_LANGUAGE

    private var selectedGender: VoiceGender =
        VoiceGender.NEUTRAL

    // =====================================================
    // MOTOR PREMIUM DE GOOGLE CLOUD
    // =====================================================

    private val cloudPlayer =
        PremiumCloudVoicePlayer(appContext)

    private var selectedTier =
        VoiceQualityTier.NORMAL

    private var premiumAccess = false
    private var premiumPurchaseToken: String? = null

    private val mainHandler =
        Handler(Looper.getMainLooper())

    var onInitResult: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    // =====================================================
    // INICIALIZACIÓN DE ANDROID TTS
    // =====================================================

    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            isReady = false
            Log.e(TAG, "No se pudo inicializar Android TTS")
            onInitResult?.invoke(false)
            return
        }

        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(
                    AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SPEECH
                )
                .build()
        )

        isReady = applyLanguage(DEFAULT_LANGUAGE)

        if (isReady) {
            applySelectedVoice()
        }

        onInitResult?.invoke(isReady)
    }

    // =====================================================
    // ACTUALIZAR ACCESO PREMIUM
    // =====================================================

    /**
     * Recibe el estado del SubscriptionManager.
     *
     * Esta comprobación local solo habilita la interfaz.
     * La Cloud Function valida la compra con Google Play.
     */
    fun setPremiumAccess(
        active: Boolean,
        purchaseToken: String?
    ) {
        val available =
            active && !purchaseToken.isNullOrBlank()

        premiumAccess = available

        premiumPurchaseToken =
            if (available) purchaseToken else null

        if (!available) {
            selectedTier = VoiceQualityTier.NORMAL
            cloudPlayer.stop()
        }
    }

    fun hasPremiumAccess(): Boolean {
        return premiumAccess &&
                !premiumPurchaseToken.isNullOrBlank()
    }

    // =====================================================
    // SELECCIÓN DE CALIDAD DE VOZ
    // =====================================================

    fun setVoiceQualityTier(
        tier: VoiceQualityTier
    ): Boolean {

        if (
            tier == VoiceQualityTier.HIGH &&
            !hasPremiumAccess()
        ) {
            Log.w(
                TAG,
                "No hay suscripción Premium disponible"
            )
            return false
        }

        if (selectedTier != tier) {
            stop()
        }

        selectedTier = tier

        // Las voces locales continúan disponibles
        // como alternativa de reproducción.
        if (tier == VoiceQualityTier.NORMAL) {
            applySelectedVoice()
        }

        return true
    }

    fun getVoiceQualityTier(): VoiceQualityTier {
        return selectedTier
    }

    // =====================================================
    // IDIOMAS
    // =====================================================

    fun setLanguage(
        language: SupportedLanguage
    ): Boolean {

        if (!isReady) return false

        return applyLanguage(language.locale)
    }

    private fun applyLanguage(
        locale: Locale
    ): Boolean {

        val engine = tts ?: return false

        val availability =
            engine.isLanguageAvailable(locale)

        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(
                TAG,
                "Idioma no disponible: $locale"
            )
            return false
        }

        val result = engine.setLanguage(locale)

        val success =
            result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

        if (success) {
            currentLanguage = locale

            if (isReady) {
                applySelectedVoice()
            }
        }

        return success
    }

    // =====================================================
    // MÉTODO CENTRAL DE REPRODUCCIÓN
    // =====================================================

    /**
     * NORMAL:
     *   Reproduce utilizando Android TTS.
     *
     * HIGH:
     *   Solicita audio Neural2 a Google Cloud.
     *
     * Si Google Cloud falla, intenta la voz local.
     *
     * onFallback permite informar en la interfaz que
     * la reproducción pasó a utilizar Android TTS.
     */
    fun speak(
        text: String,
        flushQueue: Boolean = true,
        utteranceId: String = "haptivoice-tts",
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onFallback: ((String) -> Unit)? = null
    ) {

        if (text.isBlank()) {
            onError?.invoke(
                "No hay texto para sintetizar."
            )
            return
        }

        if (!isReady || tts == null) {
            onError?.invoke(
                "El sistema de voz todavía no está listo."
            )
            return
        }

        if (flushQueue) {
            stop()
        }

        val token = premiumPurchaseToken

        val useCloud =
            selectedTier == VoiceQualityTier.HIGH &&
                    premiumAccess &&
                    !token.isNullOrBlank() &&
                    flushQueue

        // =================================================
        // VOZ PREMIUM
        // =================================================

        if (useCloud) {

            val cloudLanguage =
                if (
                    currentLanguage.language.equals(
                        "es",
                        ignoreCase = true
                    )
                ) {
                    "es-US"
                } else {
                    "en-US"
                }

            val cloudGender = when (selectedGender) {
                VoiceGender.MALE -> "MALE"
                VoiceGender.FEMALE -> "FEMALE"
                VoiceGender.NEUTRAL -> "NEUTRAL"
            }

            cloudPlayer.speak(
                text = text.trim(),
                language = cloudLanguage,
                gender = cloudGender,
                purchaseToken = token!!,

                onDone = onDone,

                onFailure = { message ->

                    Log.w(
                        TAG,
                        "Usando voz local como alternativa"
                    )

                    onFallback?.invoke(
                        "$message Se utilizará la voz básica."
                    )

                    speakWithAndroid(
                        text = text,
                        flushQueue = true,
                        utteranceId = utteranceId,
                        onDone = onDone,
                        onError = onError
                    )
                }
            )

            return
        }

        // =================================================
        // VOZ NORMAL
        // =================================================

        speakWithAndroid(
            text = text,
            flushQueue = flushQueue,
            utteranceId = utteranceId,
            onDone = onDone,
            onError = onError
        )
    }

    // =====================================================
    // REPRODUCCIÓN ANDROID TTS
    // =====================================================

    private fun speakWithAndroid(
        text: String,
        flushQueue: Boolean,
        utteranceId: String,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {

        val engine = tts

        if (!isReady || engine == null) {
            onError?.invoke(
                "Android Text-to-Speech no está disponible."
            )
            return
        }

        val currentUtteranceId =
            "$utteranceId-${System.nanoTime()}"

        if (onDone != null || onError != null) {

            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(
                        utteranceId: String?
                    ) = Unit

                    override fun onDone(
                        utteranceId: String?
                    ) {
                        if (
                            utteranceId == currentUtteranceId
                        ) {
                            mainHandler.post {
                                onDone?.invoke()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(
                        utteranceId: String?
                    ) {
                        if (
                            utteranceId == currentUtteranceId
                        ) {
                            mainHandler.post {
                                onError?.invoke(
                                    "Error de síntesis local."
                                )
                            }
                        }
                    }

                    override fun onError(
                        utteranceId: String?,
                        errorCode: Int
                    ) {
                        if (
                            utteranceId == currentUtteranceId
                        ) {
                            mainHandler.post {
                                onError?.invoke(
                                    "Error Android TTS ($errorCode)"
                                )
                            }
                        }
                    }
                }
            )
        }

        val queueMode =
            if (flushQueue) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }

        val result = engine.speak(
            text,
            queueMode,
            Bundle(),
            currentUtteranceId
        )

        if (result == TextToSpeech.ERROR) {
            onError?.invoke(
                "No se pudo iniciar la reproducción local."
            )
        }
    }

    // =====================================================
    // CONTROL DE REPRODUCCIÓN
    // =====================================================

    fun stop() {
        cloudPlayer.stop()
        tts?.stop()
    }

    fun isReady(): Boolean = isReady

    // =====================================================
    // OBTENER VOCES LOCALES
    // =====================================================

    fun getAllVoices(): List<Voice> {

        val engine = tts

        if (!isReady || engine == null) {
            return emptyList()
        }

        return engine.voices
            .orEmpty()
            .sortedBy { it.name }
    }

    fun getVoicesForCurrentLanguage(): List<Voice> {

        val language = currentLanguage.language

        return getAllVoices().filter {
            it.locale.language.equals(
                language,
                ignoreCase = true
            )
        }
    }

    fun getVoicesQualityNormal(): List<Voice> {
        return filterVoicesByExactQuality(
            Voice.QUALITY_NORMAL
        )
    }

    fun getVoicesQualityVeryHigh(): List<Voice> {

        // Estas son las voces locales de Android.
        // Las voces Neural2 no se enumeran aquí.

        val veryHigh = filterVoicesByExactQuality(
            Voice.QUALITY_VERY_HIGH
        )

        if (veryHigh.isNotEmpty()) {
            return veryHigh
        }

        return filterVoicesByExactQuality(
            Voice.QUALITY_HIGH
        )
    }

    private fun filterVoicesByExactQuality(
        quality: Int
    ): List<Voice> {

        val language = currentLanguage.language

        return getAllVoices().filter {
            it.quality == quality &&
                    it.locale.language.equals(
                        language,
                        ignoreCase = true
                    )
        }
    }

    // =====================================================
    // SELECCIÓN DE VOZ LOCAL
    // =====================================================

    fun setVoice(voice: Voice): Boolean {

        val engine = tts ?: return false

        if (!isReady) return false

        return engine.setVoice(voice) ==
                TextToSpeech.SUCCESS
    }

    fun setVoiceByName(
        voiceName: String
    ): Boolean {

        val voice = tts?.voices?.firstOrNull {
            it.name == voiceName
        }

        if (voice == null) {
            Log.w(
                TAG,
                "Voz local no encontrada: $voiceName"
            )
            return false
        }

        return setVoice(voice)
    }

    fun setVoiceGender(
        gender: VoiceGender
    ): Boolean {

        selectedGender = gender

        return applySelectedVoice()
    }

    fun setVoiceFromProfile(
        voiceType: String
    ): Boolean {

        selectedGender = when (
            voiceType.lowercase()
        ) {

            "femenina", "femenino" ->
                VoiceGender.FEMALE

            "masculina", "masculino" ->
                VoiceGender.MALE

            else ->
                VoiceGender.NEUTRAL
        }

        return applySelectedVoice()
    }

    fun applySelectedVoice(): Boolean {

        if (!isReady) return false

        // Siempre seleccionamos una voz Android para que
        // exista una alternativa cuando falle Google Cloud.
        val voiceName = resolveNormalVoiceName(
            language = currentLanguage,
            gender = selectedGender
        )

        // Los nombres de las voces dependen del motor TTS
        // instalado en el dispositivo.
        // Si no existe, se mantiene la voz por defecto.
        val applied = setVoiceByName(voiceName)

        if (!applied) {
            Log.w(
                TAG,
                "Se mantiene la voz predeterminada de Android"
            )
        }

        return applied
    }

    private fun resolveNormalVoiceName(
        language: Locale,
        gender: VoiceGender
    ): String {

        val isSpanish = language.language.equals(
            "es",
            ignoreCase = true
        )

        return when {

            isSpanish && gender == VoiceGender.FEMALE ->
                ES_FEMALE_NORMAL

            isSpanish && gender == VoiceGender.MALE ->
                ES_MALE_NORMAL

            isSpanish ->
                ES_NEUTRAL_NORMAL

            gender == VoiceGender.FEMALE ->
                EN_FEMALE_NORMAL

            gender == VoiceGender.MALE ->
                EN_MALE_NORMAL

            else ->
                EN_NEUTRAL_NORMAL
        }
    }

    // =====================================================
    // LIBERACIÓN DE RECURSOS
    // =====================================================

    fun destroy() {

        cloudPlayer.close()

        tts?.stop()
        tts?.shutdown()
        tts = null

        isReady = false
        premiumAccess = false
        premiumPurchaseToken = null

        selectedTier = VoiceQualityTier.NORMAL
    }

    // =====================================================
    // ENUMERACIONES
    // =====================================================

    enum class SupportedLanguage(
        val locale: Locale
    ) {
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

    // =====================================================
    // CONSTANTES
    // =====================================================

    companion object {

        private const val TAG = "TextToSpeechManager"

        private val DEFAULT_LANGUAGE: Locale =
            SupportedLanguage.SPANISH.locale

        // Voces normales de Android.
        const val ES_FEMALE_NORMAL = "es-ES-language"
        const val ES_MALE_NORMAL = "es-us-x-esf-local"
        const val ES_NEUTRAL_NORMAL = "es-es-x-eea-local"

        const val EN_FEMALE_NORMAL = "en-AU-language"
        const val EN_MALE_NORMAL = "en-us-x-iom-local"
        const val EN_NEUTRAL_NORMAL = "en-us-x-sfg-local"
    }
}
