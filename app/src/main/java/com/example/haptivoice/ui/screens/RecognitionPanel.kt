package com.example.haptivoice.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.haptivoice.data.VocabularyRepository
import com.example.haptivoice.data.metrics.LatencyLogStore
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.CalibrationStore
import com.example.haptivoice.utils.Classification
import com.example.haptivoice.utils.ImuNormalizer
import com.example.haptivoice.utils.LatencyRecord
import com.example.haptivoice.utils.PoseClassifier
import com.example.haptivoice.utils.RecognitionEngine
import com.example.haptivoice.utils.SubscriptionManager
import com.example.haptivoice.utils.TextToSpeechManager
import com.example.haptivoice.utils.TranslationManager
import kotlinx.coroutines.launch

private const val UNRECOGNIZED_ES = "Seña no reconocida"
private const val UNRECOGNIZED_EN = "Sign not recognized"

// Idioma del texto y la voz.
private enum class OutputLanguage(val code: String) {
    SPANISH("es"),
    ENGLISH("en")
}

// =============================================================
// PANEL DE TRADUCCIÓN (ETAPAS 4, 5 Y 9, SOLO MPU)
// =============================================================

/*
 * Traduce las señas estáticas grabadas a texto y voz:
 *
 * - Usa el vocabulario de Room ya resuelto según el plan:
 *   lemas base activos, reasignaciones premium y los gestos
 *   propios permitidos (etapa 5).
 * - Solo funciona si están conectadas las manos con las que
 *   se grabaron las señas; si falta una, se detiene (HU08).
 * - Seña reconocida: se muestra y se lee con TTS (HU09, HU10).
 *   La voz es la del perfil (MainActivity llama a
 *   setVoiceFromProfile) y, con Premium, la voz de Google Cloud
 *   (setPremiumAccess); si falla, usa la voz del teléfono.
 * - Seña no reconocida: se muestra el aviso, sin voz (RF-15).
 * - Postura de reposo: no cambia nada en pantalla.
 * - Ocultar el texto no detiene la traducción; silenciar la
 *   voz no detiene el texto.
 *
 * Etapa 9:
 * - Inglés solo con Premium: se traduce la palabra ya
 *   reconocida con ML Kit en el teléfono (sin Internet una vez
 *   descargado el modelo por Wi-Fi). Cada palabra se traduce
 *   una vez y queda en memoria, para no sumar latencia.
 * - Cada seña queda en el registro de latencia (KPI del
 *   capítulo 11). No se muestra en pantalla; el archivo queda
 *   en el teléfono (LatencyLogStore) para revisarlo después.
 */
@Composable
fun RecognitionPanel(
    bluetoothManager: BluetoothManager,
    ttsManager: TextToSpeechManager,
    subscriptionManager: SubscriptionManager,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val calibrationStore = remember {
        CalibrationStore(context.applicationContext)
    }

    val repository = remember {
        VocabularyRepository(context.applicationContext)
    }

    val vocabularyFlow = remember(repository) {
        repository.observe(subscriptionManager.isPremium)
    }

    val vocabulary by vocabularyFlow.collectAsState(initial = null)
    val isPremium by subscriptionManager.isPremium.collectAsState()

    // Plantillas que puede reconocer el clasificador, con la glosa
    // final (una reasignación usa la palabra del lema base).
    val templates = remember(vocabulary) {
        vocabulary?.resolved?.recognizable.orEmpty().map { entry ->
            entry.template.copy(id = entry.id, glosa = entry.glosa)
        }
    }
    val handsByAddress by bluetoothManager.handsByAddress.collectAsState()

    val connectedHands = handsByAddress.values.toSet()

    /*
     * Manos con las que se traduce: entre las combinaciones con que
     * se grabaron las señas, la que está conectada ahora (prefiere
     * dos manos si están las dos). Con un solo guante se traducen
     * las señas grabadas con esa mano.
     */
    val hands = remember(templates, connectedHands) {
        PoseClassifier.handsFor(templates, connectedHands)
    }

    val missingHands =
        hands.orEmpty().filterNot { it in connectedHands }

    val isActive = hands != null && missingHands.isEmpty()

    var displayText by remember { mutableStateOf<String?>(null) }
    var originalText by remember { mutableStateOf<String?>(null) }
    var isUnrecognized by remember { mutableStateOf(false) }
    var isTextVisible by remember { mutableStateOf(true) }
    var isVoiceMuted by remember { mutableStateOf(false) }
    var lastLatencyMillis by remember { mutableStateOf<Long?>(null) }
    var lastDetail by remember { mutableStateOf<String?>(null) }

    // =========================================================
    // IDIOMA (ETAPA 9)
    // =========================================================

    var language by remember { mutableStateOf(OutputLanguage.SPANISH) }
    var translationNote by remember { mutableStateOf<String?>(null) }

    val translationManager = remember { TranslationManager() }
    val translationCache = remember { mutableMapOf<String, String>() }

    // Evita que una traducción tardía pise una seña más nueva.
    var eventCounter by remember { mutableIntStateOf(0) }

    DisposableEffect(translationManager) {
        onDispose { translationManager.close() }
    }

    // Sin Premium vuelve a español.
    LaunchedEffect(isPremium) {
        if (!isPremium) {
            language = OutputLanguage.SPANISH
            translationNote = null
        }
    }

    // Prepara el modelo de inglés al elegirlo (descarga por Wi-Fi).
    LaunchedEffect(language) {

        if (language != OutputLanguage.ENGLISH) return@LaunchedEffect

        translationNote = "Preparando traducción al inglés…"

        translationManager.preloadModels(
            onReady = { translationNote = null },
            onError = {
                translationNote =
                    "El modelo de inglés se descarga con Wi-Fi. " +
                            "Mientras tanto se muestra en español."
            }
        )
    }

    // =========================================================
    // MÉTRICAS DE LATENCIA (ETAPA 9)
    // =========================================================

    // Se registran en segundo plano, sin mostrarse en pantalla.

    val latencyLog = remember { LatencyLogStore.get(context) }

    LaunchedEffect(latencyLog) {
        latencyLog.load()
    }

    /*
     * Muestra el resultado, lo lee (si corresponde) y lo registra.
     * La latencia se mide justo al mostrar el texto, así incluye
     * la traducción cuando la hay.
     */
    fun present(
        text: String,
        spanishGlosa: String,
        outputLanguage: OutputLanguage,
        recognized: Boolean,
        capturedAtMillis: Long,
        distanceDeg: Float?
    ) {

        val latency = SystemClock.elapsedRealtime() - capturedAtMillis

        displayText = text
        originalText =
            if (recognized && outputLanguage == OutputLanguage.ENGLISH) spanishGlosa else null
        isUnrecognized = !recognized
        lastLatencyMillis = latency

        if (recognized && !isVoiceMuted) {

            ttsManager.setLanguage(
                if (outputLanguage == OutputLanguage.ENGLISH) {
                    TextToSpeechManager.SupportedLanguage.ENGLISH
                } else {
                    TextToSpeechManager.SupportedLanguage.SPANISH
                }
            )

            ttsManager.speak(text = text)
        }

        scope.launch {
            latencyLog.add(
                LatencyRecord(
                    timestampMillis = System.currentTimeMillis(),
                    result = if (recognized) {
                        LatencyRecord.RESULT_RECOGNIZED
                    } else {
                        LatencyRecord.RESULT_UNRECOGNIZED
                    },
                    glosa = if (recognized) spanishGlosa else "",
                    latencyMillis = latency,
                    distanceDeg = distanceDeg,
                    language = outputLanguage.code
                )
            )
        }
    }

    // =========================================================
    // RECONOCIMIENTO EN VIVO
    // =========================================================

    LaunchedEffect(templates, hands, isActive) {

        if (!isActive || hands == null) {
            return@LaunchedEffect
        }

        val engine = RecognitionEngine(templates, hands)

        bluetoothManager.sensorFrames.collect { frame ->

            val normalized =
                ImuNormalizer.normalize(
                    frame,
                    calibrationStore.get(frame.hand)
                )

            val event = engine.process(normalized) ?: return@collect
            val capturedAt = event.sample.capturedAtMillis

            when (val result = event.classification) {

                is Classification.Recognized -> {

                    val glosa = result.template.glosa
                    val myEvent = ++eventCounter

                    lastDetail =
                        "A ${"%.0f".format(result.distanceDeg)}° de la plantilla."

                    if (language != OutputLanguage.ENGLISH) {

                        present(
                            glosa, glosa, OutputLanguage.SPANISH,
                            recognized = true,
                            capturedAtMillis = capturedAt,
                            distanceDeg = result.distanceDeg
                        )

                        return@collect
                    }

                    val cached = translationCache[glosa]

                    if (cached != null) {

                        present(
                            cached, glosa, OutputLanguage.ENGLISH,
                            recognized = true,
                            capturedAtMillis = capturedAt,
                            distanceDeg = result.distanceDeg
                        )

                        return@collect
                    }

                    translationManager.translateSpanishToEnglish(
                        text = glosa,
                        onResult = { translated ->

                            translationCache[glosa] = translated

                            if (myEvent == eventCounter) {
                                present(
                                    translated, glosa, OutputLanguage.ENGLISH,
                                    recognized = true,
                                    capturedAtMillis = capturedAt,
                                    distanceDeg = result.distanceDeg
                                )
                            }
                        },
                        onError = {

                            // Sin modelo: se muestra en español.
                            translationNote =
                                "No se pudo traducir. Se muestra en español."

                            if (myEvent == eventCounter) {
                                present(
                                    glosa, glosa, OutputLanguage.SPANISH,
                                    recognized = true,
                                    capturedAtMillis = capturedAt,
                                    distanceDeg = result.distanceDeg
                                )
                            }
                        }
                    )
                }

                is Classification.Unrecognized -> {

                    eventCounter++

                    lastDetail =
                        if (result.ambiguousWith.isNotEmpty()) {
                            "Se parece a: ${result.ambiguousWith.joinToString(" y ")}."
                        } else {
                            result.bestDistanceDeg?.let {
                                "La seña más cercana está a ${"%.0f".format(it)}°."
                            }
                        }

                    present(
                        text = if (language == OutputLanguage.ENGLISH) {
                            UNRECOGNIZED_EN
                        } else {
                            UNRECOGNIZED_ES
                        },
                        spanishGlosa = "",
                        outputLanguage = language,
                        recognized = false,
                        capturedAtMillis = capturedAt,
                        distanceDeg = result.bestDistanceDeg
                    )
                }

                is Classification.Rest -> {
                    lastDetail = "Reposo."
                }

                Classification.NoTemplates -> {
                    lastDetail = "No hay señas para las manos conectadas."
                }
            }
        }
    }

    // =========================================================
    // INTERFAZ
    // =========================================================

    OutlinedCard(modifier = modifier) {

        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Traducción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {

                    hands == null ->
                        "Graba al menos una seña en \"Grabar señas\" o en el Diccionario para empezar."

                    missingHands.isNotEmpty() ->
                        "Traducción detenida: conecta la mano " +
                                missingHands.joinToString(" y ") { it.label.lowercase() } + "."

                    else ->
                        "Traduciendo con mano " +
                                hands.joinToString(" y ") { it.label.lowercase() } +
                                " · ${templates.count { !it.isRest && it.hands == hands }} señas."
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            // =================================================
            // IDIOMA
            // =================================================

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {

                LanguageButton(
                    text = "Español",
                    selected = language == OutputLanguage.SPANISH,
                    enabled = true,
                    onClick = {
                        language = OutputLanguage.SPANISH
                        translationNote = null
                    },
                    modifier = Modifier.weight(1f)
                )

                LanguageButton(
                    text = if (isPremium) "English" else "English (Premium)",
                    selected = language == OutputLanguage.ENGLISH,
                    enabled = isPremium,
                    onClick = { language = OutputLanguage.ENGLISH },
                    modifier = Modifier.weight(1f)
                )
            }

            translationNote?.let {

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // =================================================
            // TEXTO TRADUCIDO
            // =================================================

            if (isTextVisible) {

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = displayText ?: "Haz una seña…",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = when {
                        displayText == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        isUnrecognized -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                originalText?.let {

                    Text(
                        text = "($it)",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val detailLine =
                    listOfNotNull(
                        lastLatencyMillis?.let { "Procesado en $it ms." },
                        lastDetail
                    ).joinToString(" ")

                if (detailLine.isNotEmpty()) {

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = { isTextVisible = !isTextVisible },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isTextVisible) "Ocultar texto" else "Mostrar texto")
                }

                OutlinedButton(
                    onClick = {
                        isVoiceMuted = !isVoiceMuted
                        if (isVoiceMuted) {
                            ttsManager.stop()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isVoiceMuted) "Activar voz" else "Silenciar voz")
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    }
}