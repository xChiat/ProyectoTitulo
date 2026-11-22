package com.example.haptivoice.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.haptivoice.model.GloveStats
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.ImuCalibration
import com.example.haptivoice.model.SensorFrame
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.CalibrationStore
import com.example.haptivoice.utils.GyroBiasEstimator
import com.example.haptivoice.utils.GyroBiasResult
import com.example.haptivoice.utils.ImuNormalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Duración de la medición de reposo: unas 60 tramas por mano.
private const val CALIBRATION_MILLIS = 3000L

// =============================================================
// PANTALLA DE DEPURACIÓN DE SENSORES (ETAPAS 1 Y 2)
// =============================================================

/*
 * Muestra en vivo lo que llega de cada guante:
 * - si el par está completo (una mano I y una D),
 * - tramas recibidas, perdidas y frecuencia por mano,
 * - el MPU normalizado: aceleración en g, giro en °/s
 *   e inclinación de la mano,
 * - los valores crudos de la última trama.
 *
 * También calibra el giroscopio: con los guantes quietos
 * mide 3 s y guarda el sesgo de cada mano.
 */
@Composable
fun SensorDebugScreen(
    bluetoothManager: BluetoothManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    val calibrationStore = remember {
        CalibrationStore(context.applicationContext)
    }

    val gloveStats by bluetoothManager.gloveStats.collectAsState()
    val handsByAddress by bluetoothManager.handsByAddress.collectAsState()
    val isPairReady by bluetoothManager.isPairReady.collectAsState()
    val invalidLines by bluetoothManager.invalidLines.collectAsState()
    val connectedCount by bluetoothManager.connectedGlovesCount.collectAsState()
    val calibrations by calibrationStore.calibrations.collectAsState()

    val scope = rememberCoroutineScope()

    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onNavigateBack)

    // =========================================================
    // FRECUENCIA: TRAMAS POR SEGUNDO, MEDIDAS CADA 1 s
    // =========================================================

    var ratesByHand by remember {
        mutableStateOf<Map<Hand, Double>>(emptyMap())
    }

    LaunchedEffect(Unit) {

        var previousCounts =
            bluetoothManager.gloveStats.value.mapValues { it.value.received }

        var previousTime = SystemClock.elapsedRealtime()

        while (true) {

            delay(1000)

            val now = SystemClock.elapsedRealtime()
            val seconds = (now - previousTime) / 1000.0
            val current = bluetoothManager.gloveStats.value

            ratesByHand = current.mapValues { (hand, stats) ->
                val before = previousCounts[hand] ?: stats.received
                (stats.received - before).coerceAtLeast(0L) / seconds
            }

            previousCounts = current.mapValues { it.value.received }
            previousTime = now
        }
    }

    // =========================================================
    // CALIBRACIÓN DEL GIROSCOPIO
    // =========================================================

    fun startCalibration() {

        scope.launch {

            isCalibrating = true
            calibrationMessage = "Midiendo 3 s… no muevas los guantes."

            val collected = mutableListOf<SensorFrame>()

            // La colección nunca termina sola: se corta a los 3 s.
            withTimeoutOrNull(CALIBRATION_MILLIS) {
                bluetoothManager.sensorFrames.collect { frame ->
                    collected.add(frame)
                }
            }

            val now = System.currentTimeMillis()

            val messages =
                Hand.entries
                    .filter { hand -> collected.any { it.hand == hand } }
                    .map { hand ->

                        when (val result = GyroBiasEstimator.estimate(hand, collected, now)) {

                            is GyroBiasResult.Ok -> {
                                calibrationStore.save(result.calibration)
                                "${hand.label}: calibrada."
                            }

                            is GyroBiasResult.TooFewSamples -> {
                                "${hand.label}: solo llegaron ${result.count} tramas; repite."
                            }

                            GyroBiasResult.ImuMissing -> {
                                "${hand.label}: el MPU no responde."
                            }

                            is GyroBiasResult.Moving -> {
                                "${hand.label}: se movió " +
                                        "(${"%.2f".format(result.maxStdDps)} °/s); " +
                                        "déjala sobre la mesa y repite."
                            }

                            is GyroBiasResult.AccelOutOfRange -> {
                                "${hand.label}: la gravedad mide " +
                                        "${"%.2f".format(result.magnitudeG)} g " +
                                        "y debería ser cercana a 1 g; revisa el MPU."
                            }
                        }
                    }

            calibrationMessage =
                if (messages.isEmpty()) {
                    "No llegaron tramas: conecta los guantes y repite."
                } else {
                    messages.joinToString("\n")
                }

            isCalibrating = false
        }
    }

    // Dos guantes con el mismo valor de MANO en el firmware.
    val duplicatedHands =
        handsByAddress.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Text(
                text = "Sensores en vivo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================
        // ESTADO DEL PAR
        // =====================================================

        val pairText = when {

            duplicatedHands.isNotEmpty() -> {
                val hand = duplicatedHands.first()
                "Los dos guantes se identifican como ${hand.label} (${hand.code}). " +
                        "Sube el firmware con un valor de MANO distinto en cada uno."
            }

            isPairReady -> {
                "Par listo: izquierda y derecha conectadas."
            }

            else -> {
                "Par incompleto: $connectedCount de 2 guantes conectados, " +
                        "${handsByAddress.size} con mano identificada. " +
                        "Un guante conectado sin mano tiene el firmware antiguo."
            }
        }

        Text(
            text = pairText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPairReady) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Líneas inválidas recibidas: $invalidLines",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // =====================================================
        // CALIBRACIÓN
        // =====================================================

        Text(
            text = "Deja los dos guantes quietos sobre la mesa y presiona calibrar. " +
                    "Repite si cambias de guante o el giro en reposo no queda cerca de 0 °/s.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { startCalibration() },
            enabled = !isCalibrating && gloveStats.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isCalibrating) "Calibrando…" else "Calibrar giroscopio (3 s quieto)"
            )
        }

        calibrationMessage?.let { message ->

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Hand.entries.forEach { hand ->

            HandStatsCard(
                hand = hand,
                stats = gloveStats[hand],
                framesPerSecond = ratesByHand[hand],
                calibration = calibrations[hand]
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// =============================================================
// TARJETA DE UNA MANO
// =============================================================

@Composable
private fun HandStatsCard(
    hand: Hand,
    stats: GloveStats?,
    framesPerSecond: Double?,
    calibration: ImuCalibration?
) {

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "Mano ${hand.label} (${hand.code})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val frame = stats?.lastFrame

            if (stats == null || frame == null) {

                Text(
                    text = "Sin tramas de esta mano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                return@Column
            }

            Text(
                text = "Recibidas: ${stats.received} · " +
                        "perdidas: ${stats.lost} " +
                        "(${"%.1f".format(stats.lossPercent)} %)",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Frecuencia: ${"%.1f".format(framesPerSecond ?: 0.0)} tramas/s · " +
                        "última seq: ${stats.lastSeq}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (frame.imuMissing) {

                Text(
                    text = "El MPU manda ceros: revisa SDA, SCL y su alimentación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // =================================================
            // MPU NORMALIZADO
            // =================================================

            val normalized = ImuNormalizer.normalize(frame, calibration)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "MPU normalizado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (normalized.isGyroCalibrated) {
                    "Giroscopio calibrado."
                } else {
                    "Giroscopio sin calibrar: el giro en reposo no será 0."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (normalized.isGyroCalibrated) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            SensorValueRow("ax (g)", "%.2f".format(normalized.accelG[0]))
            SensorValueRow("ay (g)", "%.2f".format(normalized.accelG[1]))
            SensorValueRow("az (g)", "%.2f".format(normalized.accelG[2]))
            SensorValueRow("|a| (g)", "%.2f".format(normalized.accelMagnitudeG))
            SensorValueRow("gx (°/s)", "%.1f".format(normalized.gyroDps[0]))
            SensorValueRow("gy (°/s)", "%.1f".format(normalized.gyroDps[1]))
            SensorValueRow("gz (°/s)", "%.1f".format(normalized.gyroDps[2]))
            SensorValueRow("Inclinación lateral (°)", "%.0f".format(normalized.rollDeg))
            SensorValueRow("Inclinación frontal (°)", "%.0f".format(normalized.pitchDeg))

            // =================================================
            // VALORES CRUDOS
            // =================================================

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Valores crudos (los flexores aún no se usan)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            val labels = listOf(
                "Pulgar", "Índice", "Medio", "Anular", "Meñique",
                "ax", "ay", "az",
                "gx", "gy", "gz"
            )

            val values = frame.flex + frame.accel + frame.gyro

            labels.zip(values).forEach { (label, value) ->
                SensorValueRow(label = label, value = value.toString())
            }
        }
    }
}

@Composable
private fun SensorValueRow(
    label: String,
    value: String
) {

    Row(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}