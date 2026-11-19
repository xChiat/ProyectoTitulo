
package com.example.haptivoice.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import com.example.haptivoice.utils.SpeechToTextManager
import com.example.haptivoice.utils.TextToSpeechManager
import com.example.haptivoice.utils.TranslationManager

@Composable
fun AyudasAuditivasScreen(
    ttsManager: TextToSpeechManager,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    val context = LocalContext.current

    // =====================================================
    // ADMINISTRADORES EXISTENTES
    // =====================================================

    val speechManager = remember(context) {
        SpeechToTextManager(context.applicationContext)
    }

    // Google ML Kit: traducción de español a inglés
    // y de inglés a español.
    val translationManager = remember {
        TranslationManager()
    }

    // =====================================================
    // TEXTO A VOZ
    // =====================================================

    // Texto escrito para reproducir.
    var writtenText by remember {
        mutableStateOf("")
    }

    // Traducción del texto escrito al inglés.
    var translatedSpeechText by remember {
        mutableStateOf("")
    }

    var speechMessage by remember {
        mutableStateOf("")
    }

    var isTranslatingSpeech by remember {
        mutableStateOf(false)
    }

    // =====================================================
    // VOZ A TEXTO
    // =====================================================

    // El texto mostrado siempre será español.
    var recognizedText by remember {
        mutableStateOf("")
    }

    var isListening by remember {
        mutableStateOf(false)
    }

    var recognitionMessage by remember {
        mutableStateOf("")
    }

    var isTranslatingRecognition by remember {
        mutableStateOf(false)
    }

    // Idioma que el usuario hablará al micrófono.
    // es-CL = Español (Chile)
    // en-US = Inglés (Estados Unidos)
    var recognitionLanguage by remember {
        mutableStateOf("es-CL")
    }

    var languageMenuExpanded by remember {
        mutableStateOf(false)
    }

    // Si Premium deja de estar activo,
    // el idioma disponible vuelve a español.
    LaunchedEffect(isPremium) {
        if (!isPremium && recognitionLanguage == "en-US") {
            recognitionLanguage = "es-CL"
        }
    }

    // =====================================================
    // REPRODUCIR TEXTO ORIGINAL EN ESPAÑOL
    // =====================================================

    fun reproduceSpanishText() {

        if (writtenText.isBlank()) {
            return
        }

        ttsManager.setLanguage(
            TextToSpeechManager.SupportedLanguage.SPANISH
        )


        ttsManager.speak(
            text = writtenText.trim(),

            onFallback = { message ->
                speechMessage = message
            },

            onError = { message ->
                speechMessage = message
            }
        )

        speechMessage = "Reproduciendo texto en español."
    }

    // =====================================================
    // TRADUCIR TEXTO Y REPRODUCIR EN INGLÉS
    // =====================================================

    fun translateAndSpeakEnglish() {

        if (!isPremium) {
            speechMessage =
                "Necesitas HaptiVoice Premium para traducir."
            return
        }

        if (writtenText.isBlank() || isTranslatingSpeech) {
            return
        }

        val originalText = writtenText.trim()

        isTranslatingSpeech = true
        translatedSpeechText = ""

        speechMessage =
            "Preparando traducción. La primera vez " +
                    "puede necesitar descargar el modelo por Wi-Fi."

        translationManager.translateSpanishToEnglish(
            text = originalText,

            onResult = { translated ->

                translatedSpeechText = translated
                isTranslatingSpeech = false

                // Pronunciar el resultado en inglés.
                ttsManager.setLanguage(
                    TextToSpeechManager.SupportedLanguage.ENGLISH
                )


                ttsManager.speak(
                    text = translated,

                    onFallback = { message ->
                        speechMessage = message
                    },

                    onError = { message ->
                        speechMessage = message
                    }
                )

                speechMessage =
                    "Traducción completada. Reproduciendo en inglés."
            },

            onError = { error ->

                isTranslatingSpeech = false
                speechMessage = error
            }
        )
    }

    // =====================================================
    // INICIAR RECONOCIMIENTO DE VOZ
    // =====================================================

    fun startRecognition() {

        if (isListening ||
            isTranslatingRecognition ||
            isTranslatingSpeech
        ) {
            return
        }

        // El reconocimiento en inglés requiere Premium.
        if (recognitionLanguage == "en-US" && !isPremium) {
            recognitionMessage =
                "El reconocimiento con traducción requiere Premium."
            return
        }

        recognizedText = ""
        recognitionMessage = "Escuchando..."
        isListening = true

        // Utilizamos el idioma elegido por el usuario.
        val selectedLanguage = recognitionLanguage

        speechManager.startListening(
            language = selectedLanguage,

            onResult = { text ->

                isListening = false

                if (text.isBlank()) {
                    recognitionMessage =
                        "No se reconoció ningún texto."
                    return@startListening
                }

                // =========================================
                // RECONOCIMIENTO EN ESPAÑOL
                // =========================================

                if (selectedLanguage == "es-CL") {

                    // El resultado ya está en español.
                    recognizedText = text

                    recognitionMessage = ""
                } else {

                    // =====================================
                    // RECONOCIMIENTO EN INGLÉS
                    // =====================================

                    // No mostramos directamente el texto
                    // inglés: primero lo traducimos.
                    recognizedText = ""

                    isTranslatingRecognition = true

                    recognitionMessage =
                        "Inglés reconocido. Traduciendo a español..."

                    translationManager.translateEnglishToSpanish(
                        text = text,

                        onResult = { translated ->

                            // Solo se muestra el resultado
                            // traducido al español.
                            recognizedText = translated

                            isTranslatingRecognition = false

                            recognitionMessage =
                                "Traducción al español completada."
                        },

                        onError = { error ->

                            isTranslatingRecognition = false

                            // No mostramos inglés si falla
                            // la traducción.
                            recognizedText = ""

                            recognitionMessage =
                                "No se pudo traducir el reconocimiento: $error"
                        }
                    )
                }
            },

            onError = { error ->

                isListening = false

                recognitionMessage = error
            }
        )
    }

    // =====================================================
    // DETENER RECONOCIMIENTO
    // =====================================================

    fun stopRecognition() {

        speechManager.stopListening()

        isListening = false

        recognitionMessage =
            "Procesando reconocimiento..."
    }

    // =====================================================
    // PERMISO DEL MICRÓFONO
    // =====================================================

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                startRecognition()
            } else {

                recognitionMessage =
                    "Se necesita permiso para utilizar el micrófono."
            }
        }
//
// =====================================================
// ESTADO DE VOCES PREMIUM
// =====================================================
//

    var useHighQualityVoice by remember {
        mutableStateOf(
            ttsManager.getVoiceQualityTier() ==
                    TextToSpeechManager.VoiceQualityTier.HIGH
        )
    }

// Si termina la suscripción, regresar a voz normal.
    LaunchedEffect(isPremium) {
        if (!isPremium) {
            useHighQualityVoice = false

            ttsManager.setVoiceQualityTier(
                TextToSpeechManager.VoiceQualityTier.NORMAL
            )
        }
    }

    // =====================================================
    // LIBERAR RECURSOS
    // =====================================================

    DisposableEffect(
        speechManager,
        translationManager,
        ttsManager
    ) {

        onDispose {

            speechManager.destroy()

            translationManager.close()

            ttsManager.stop()
        }
    }

    // =====================================================
    // INTERFAZ PRINCIPAL
    // =====================================================

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Ayudas Auditivas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // =====================================================
        // SECCIÓN 1: TEXTO A VOZ
        // =====================================================

        Text(
            text = "Texto a voz",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

//
// =====================================================
// SELECTOR DE VOCES DE ALTA CALIDAD
// =====================================================
//

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Voces de alta calidad",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isPremium) {
                        "Google Cloud Neural2 · requiere Internet"
                    } else {
                        "Disponible con HaptiVoice Premium"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = useHighQualityVoice,
                enabled = isPremium,

                onCheckedChange = { enabled ->

                    val requestedTier =
                        if (enabled) {
                            TextToSpeechManager.VoiceQualityTier.HIGH
                        } else {
                            TextToSpeechManager.VoiceQualityTier.NORMAL
                        }

                    val accepted =
                        ttsManager.setVoiceQualityTier(requestedTier)

                    useHighQualityVoice =
                        enabled && accepted

                    speechMessage =
                        if (enabled && accepted) {
                            "Voces Premium seleccionadas."
                        } else if (enabled) {
                            "Esperando la verificación de Premium."
                        } else {
                            "Voces normales seleccionadas."
                        }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = writtenText,

            onValueChange = {

                writtenText = it

                translatedSpeechText = ""
                speechMessage = ""
            },

            label = {
                Text("Escribe un mensaje")
            },

            placeholder = {
                Text("Escribe el mensaje que quieres reproducir...")
            },

            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),

            minLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================
        // BOTONES: REPRODUCIR Y TRADUCIR A INGLÉS
        // =====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Reproducción normal, disponible gratis.
            Button(
                onClick = {
                    reproduceSpanishText()
                },

                enabled = writtenText.isNotBlank() &&
                        !isTranslatingSpeech,

                modifier = Modifier.weight(1f)
            ) {

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Reproducir",
                    maxLines = 1
                )
            }

            // Traducción ES -> EN con reproducción.
            OutlinedButton(
                onClick = {
                    translateAndSpeakEnglish()
                },

                enabled = isPremium &&
                        writtenText.isNotBlank() &&
                        !isTranslatingSpeech,

                modifier = Modifier.weight(1f)
            ) {

                if (isTranslatingSpeech) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {

                    Text(
                        text = "Traducir a inglés",
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        if (!isPremium) {

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "La traducción a inglés requiere HaptiVoice Premium.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Mostrar el resultado que se está pronunciando.
        if (translatedSpeechText.isNotBlank()) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Texto en inglés: $translatedSpeechText",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (speechMessage.isNotBlank()) {

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = speechMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(32.dp))

        // =====================================================
        // SECCIÓN 2: VOZ A TEXTO
        // =====================================================

        Text(
            text = "Voz a texto",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Idioma que vas a hablar",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================
        // ESCUCHAR + SELECTOR DE IDIOMA
        // =====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {

                    if (isListening) {

                        stopRecognition()
                    } else {

                        val hasPermission =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {

                            startRecognition()
                        } else {

                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                },

                enabled = !isTranslatingRecognition &&
                        !isTranslatingSpeech,

                modifier = Modifier.weight(1f)
            ) {

                Icon(
                    imageVector = if (isListening) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.Mic
                    },
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (isListening) {
                        "Detener"
                    } else {
                        "Escuchar"
                    }
                )
            }

            // Selector del idioma de entrada.
            Box(
                modifier = Modifier.weight(1f)
            ) {

                OutlinedButton(
                    onClick = {
                        languageMenuExpanded = true
                    },

                    enabled = !isListening &&
                            !isTranslatingRecognition &&
                            !isTranslatingSpeech,

                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = if (recognitionLanguage == "es-CL") {
                            "Español"
                        } else {
                            "Inglés"
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Elegir idioma"
                    )
                }

                DropdownMenu(
                    expanded = languageMenuExpanded,

                    onDismissRequest = {
                        languageMenuExpanded = false
                    }
                ) {

                    DropdownMenuItem(
                        text = {
                            Text("Español (Chile)")
                        },

                        onClick = {

                            recognitionLanguage = "es-CL"
                            languageMenuExpanded = false

                            recognizedText = ""
                            recognitionMessage = ""
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Inglés (Premium)")
                        },

                        enabled = isPremium,

                        onClick = {

                            recognitionLanguage = "en-US"
                            languageMenuExpanded = false

                            recognizedText = ""
                            recognitionMessage = ""
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (recognitionLanguage == "en-US") {
                "Escuchando inglés. El texto se traducirá automáticamente al español."
            } else {
                "Escuchando español. El resultado se mostrará en español."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        // =====================================================
        // MENSAJES DEL RECONOCIMIENTO
        // =====================================================

        if (recognitionMessage.isNotBlank()) {

            Spacer(modifier = Modifier.height(12.dp))

            if (isTranslatingRecognition) {

                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = recognitionMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // =====================================================
        // TEXTO RECONOCIDO: SIEMPRE ESPAÑOL
        // =====================================================

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text = "Texto reconocido (español)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = recognizedText.ifBlank {
                        if (isTranslatingRecognition) {
                            "Traduciendo reconocimiento al español..."
                        } else {
                            "Aquí aparecerá el texto reconocido en español."
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
