package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.SensorFrame
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.CalibrationStore
import com.example.haptivoice.utils.GyroBiasEstimator
import com.example.haptivoice.utils.GyroBiasResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Tiempo quieto que se mide para calibrar.
private const val HOME_CALIBRATION_MILLIS = 3_000L

// =============================================================
// TARJETA "CALIBRAR SENSORES" EN HOME
// =============================================================

/*
 * Reemplaza el mensaje crudo del ESP32.
 *
 * - "Calibrar sensores" mide 3 s con los guantes quietos y guarda
 *   el sesgo del giroscopio de cada guante conectado.
 * - Si todos los guantes conectados están calibrados, se muestra
 *   un ✓ verde "Sensores calibrados".
 * - Si algo falla (se movió, el MPU no responde…), se explica
 *   debajo del botón.
 *
 * La calibración queda guardada en el teléfono: no hay que
 * repetirla cada vez que se abre la app, solo si cambia el
 * guante o las señas se reconocen mal.
 */
@Composable
fun CalibrationCard(
    bluetoothManager: BluetoothManager,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val calibrationStore = remember {
        CalibrationStore(context.applicationContext)
    }

    val calibrations by calibrationStore.calibrations.collectAsState()
    val handsByAddress by bluetoothManager.handsByAddress.collectAsState()

    val connectedHands =
        Hand.entries.filter { it in handsByAddress.values }

    val uncalibrated =
        connectedHands.filterNot { it in calibrations.keys }

    val isCalibrated =
        connectedHands.isNotEmpty() && uncalibrated.isEmpty()

    var isCalibrating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun calibrate() {

        scope.launch {

            isCalibrating = true
            errorMessage = null

            val collected = mutableListOf<SensorFrame>()

            // La colección no termina sola: se corta a los 3 s.
            withTimeoutOrNull(HOME_CALIBRATION_MILLIS) {
                bluetoothManager.sensorFrames.collect { frame ->
                    collected.add(frame)
                }
            }

            val now = System.currentTimeMillis()

            val problems =
                connectedHands.mapNotNull { hand ->

                    when (val result = GyroBiasEstimator.estimate(hand, collected, now)) {

                        is GyroBiasResult.Ok -> {
                            calibrationStore.save(result.calibration)
                            null
                        }

                        is GyroBiasResult.TooFewSamples ->
                            "Mano ${hand.label.lowercase()}: llegaron pocos datos. Repite."

                        GyroBiasResult.ImuMissing ->
                            "Mano ${hand.label.lowercase()}: el sensor de movimiento no responde."

                        is GyroBiasResult.Moving ->
                            "Mano ${hand.label.lowercase()}: se movió. " +
                                    "Deja el guante quieto sobre la mesa y repite."

                        is GyroBiasResult.AccelOutOfRange ->
                            "Mano ${hand.label.lowercase()}: lectura fuera de rango. " +
                                    "Revisa el sensor y repite."
                    }
                }

            errorMessage =
                when {
                    connectedHands.isEmpty() -> "Conecta al menos un guante."
                    problems.isEmpty() -> null
                    else -> problems.joinToString("\n")
                }

            isCalibrating = false
        }
    }

    OutlinedCard(modifier = modifier) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (isCalibrated && !isCalibrating) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Sensores calibrados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isCalibrating) {

                Text(
                    text = "Calibrando… deja los guantes quietos 3 segundos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Ya calibrado: botón secundario para repetir.
            if (isCalibrated) {

                OutlinedButton(
                    onClick = { calibrate() },
                    enabled = !isCalibrating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Calibrar de nuevo")
                }

            } else {

                Button(
                    onClick = { calibrate() },
                    enabled = !isCalibrating && connectedHands.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Calibrar sensores")
                }
            }

            errorMessage?.let {

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}