
package com.example.haptivoice.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Cliente de reproducción Premium de HaptiVoice.
 *
 * Solicita audio Neural2 a la Cloud Function protegida.
 * La función comprueba Firebase Auth, App Check,
 * la suscripción de Google Play y la cuota diaria.
 *
 * No contiene claves privadas de Google Cloud.
 */
class PremiumCloudVoicePlayer(context: Context) {

    private val appContext = context.applicationContext

    private val functions =
        FirebaseFunctions.getInstance("us-central1")

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private var player: MediaPlayer? = null
    private var temporaryAudio: File? = null

    // Evita reproducir respuestas de solicitudes canceladas.
    private var requestSequence = 0L
    private var closed = false

    fun speak(
        text: String,
        language: String,
        gender: String,
        purchaseToken: String,
        onDone: (() -> Unit)? = null,
        onFailure: (String) -> Unit
    ) {
        if (closed) {
            onFailure("El reproductor Premium está cerrado.")
            return
        }

        if (text.isBlank()) {
            onFailure("No hay texto para reproducir.")
            return
        }

        // Igual que el límite configurado en Firebase.
        if (text.length > MAX_CHARACTERS) {
            onFailure(
                "El texto supera el límite de voz Premium " +
                        "de $MAX_CHARACTERS caracteres."
            )
            return
        }

        stop()

        val currentRequest = requestSequence

        val arguments = hashMapOf(
            "text" to text,
            "language" to language,
            "gender" to gender,
            "purchaseToken" to purchaseToken
        )

        functions
            .getHttpsCallable("synthesizePremiumSpeech")
            .call(arguments)
            .addOnSuccessListener { response ->

                if (!isCurrent(currentRequest)) {
                    return@addOnSuccessListener
                }

                val result = response.data as? Map<*, *>

                val encodedAudio =
                    result?.get("audioBase64") as? String

                if (encodedAudio.isNullOrBlank()) {
                    onFailure(
                        "Firebase no devolvió audio Premium."
                    )
                    return@addOnSuccessListener
                }

                scope.launch {
                    val file = try {
                        withContext(Dispatchers.IO) {
                            val audioBytes = Base64.decode(
                                encodedAudio,
                                Base64.DEFAULT
                            )

                            require(audioBytes.isNotEmpty()) {
                                "Audio vacío"
                            }

                            File.createTempFile(
                                "haptivoice_cloud_",
                                ".mp3",
                                appContext.cacheDir
                            ).also {
                                it.writeBytes(audioBytes)
                            }
                        }
                    } catch (exception: Exception) {

                        if (isCurrent(currentRequest)) {
                            Log.e(
                                TAG,
                                "Error preparando audio Premium",
                                exception
                            )
                            onFailure(
                                "No se pudo preparar el audio."
                            )
                        }

                        return@launch
                    }

                    // Si se canceló mientras se escribía,
                    // eliminamos el archivo temporal.
                    if (!isCurrent(currentRequest)) {
                        withContext(Dispatchers.IO) {
                            file.delete()
                        }
                        return@launch
                    }

                    playAudio(
                        file = file,
                        requestId = currentRequest,
                        onDone = onDone,
                        onFailure = onFailure
                    )
                }
            }
            .addOnFailureListener { exception ->

                if (!isCurrent(currentRequest)) {
                    return@addOnFailureListener
                }

                Log.w(
                    TAG,
                    "Solicitud Premium rechazada: " +
                            exception.javaClass.simpleName
                )

                onFailure(toUserMessage(exception))
            }
    }

    private fun playAudio(
        file: File,
        requestId: Long,
        onDone: (() -> Unit)?,
        onFailure: (String) -> Unit
    ) {
        temporaryAudio = file

        try {
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer

            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            // Se utiliza un descriptor de archivo privado.
            FileInputStream(file).use { input ->
                mediaPlayer.setDataSource(input.fd)
            }

            mediaPlayer.setOnPreparedListener { prepared ->

                if (!isCurrent(requestId)) {
                    releasePlayback()
                    return@setOnPreparedListener
                }

                try {
                    prepared.start()
                } catch (exception: Exception) {
                    releasePlayback()
                    onFailure("No se pudo iniciar la reproducción.")
                }
            }

            mediaPlayer.setOnCompletionListener {

                if (isCurrent(requestId)) {
                    releasePlayback()
                    onDone?.invoke()
                }
            }

            mediaPlayer.setOnErrorListener { _, what, _ ->

                if (isCurrent(requestId)) {
                    Log.e(TAG, "Error MediaPlayer: $what")
                    releasePlayback()
                    onFailure("Falló la reproducción Premium.")
                }

                true
            }

            mediaPlayer.prepareAsync()

        } catch (exception: Exception) {
            Log.e(TAG, "Error iniciando MediaPlayer", exception)
            releasePlayback()

            if (isCurrent(requestId)) {
                onFailure("No se pudo reproducir el MP3.")
            }
        }
    }

    private fun toUserMessage(
        exception: Exception
    ): String {

        val error = exception as? FirebaseFunctionsException

        return when (error?.code) {

            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Debes iniciar sesión en HaptiVoice."

            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "La suscripción no pasó la verificación " +
                        "de Google Play o no está vinculada a tu cuenta."

            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "Alcanzaste el límite diario de voz Premium."

            FirebaseFunctionsException.Code.NOT_FOUND ->
                "La función Premium aún no está desplegada."

            FirebaseFunctionsException.Code.UNAVAILABLE ->
                "El servicio de voz Premium no está disponible."

            else ->
                "No se pudo conectar con Google Cloud TTS."
        }
    }

    private fun isCurrent(id: Long): Boolean {
        return !closed && id == requestSequence
    }

    fun stop() {
        requestSequence++
        releasePlayback()
    }

    private fun releasePlayback() {
        val oldPlayer = player
        player = null

        if (oldPlayer != null) {
            runCatching {
                oldPlayer.release()
            }
        }

        val oldFile = temporaryAudio
        temporaryAudio = null

        if (oldFile != null) {
            runCatching {
                oldFile.delete()
            }
        }
    }

    fun close() {
        closed = true
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "PremiumCloudVoice"
        private const val MAX_CHARACTERS = 400
    }
}