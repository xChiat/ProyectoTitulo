package com.example.haptivoice.utils

import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.ImuCalibration
import com.example.haptivoice.model.NormalizedFrame
import com.example.haptivoice.model.SensorFrame
import kotlin.math.sqrt

// =============================================================
// NORMALIZACIÓN DEL MPU6050
// =============================================================

/*
 * El firmware no cambia los rangos del MPU6050, así que usa
 * los de fábrica:
 * - acelerómetro ±2 g     -> 16384 unidades por g
 * - giroscopio ±250 °/s   -> 131 unidades por °/s
 *
 * No depende de Android, así que se prueba con JUnit.
 */
object ImuNormalizer {

    const val ACCEL_LSB_PER_G = 16384f
    const val GYRO_LSB_PER_DPS = 131f

    fun normalize(
        frame: SensorFrame,
        calibration: ImuCalibration?
    ): NormalizedFrame {

        val bias =
            calibration
                ?.takeIf { it.hand == frame.hand }
                ?.gyroBiasRaw
                ?: listOf(0f, 0f, 0f)

        return NormalizedFrame(
            hand = frame.hand,
            seq = frame.seq,
            receivedAtMillis = frame.receivedAtMillis,
            accelG = frame.accel.map { it / ACCEL_LSB_PER_G },
            gyroDps = frame.gyro.mapIndexed { i, raw ->
                (raw - bias[i]) / GYRO_LSB_PER_DPS
            },
            isGyroCalibrated = calibration?.hand == frame.hand
        )
    }
}

// =============================================================
// ESTIMACIÓN DEL SESGO DEL GIROSCOPIO
// =============================================================

sealed class GyroBiasResult {

    data class Ok(val calibration: ImuCalibration) : GyroBiasResult()

    // Muy pocas tramas: el guante no estaba enviando bien.
    data class TooFewSamples(val count: Int) : GyroBiasResult()

    // El MPU mandó ceros: no responde.
    data object ImuMissing : GyroBiasResult()

    // El guante se movió durante la medición.
    data class Moving(val maxStdDps: Float) : GyroBiasResult()

    /*
     * La gravedad medida no da cerca de 1 g: el MPU está mal
     * configurado, dañado o el guante se movió mucho.
     */
    data class AccelOutOfRange(val magnitudeG: Float) : GyroBiasResult()
}

object GyroBiasEstimator {

    // 2 s a 20 tramas por segundo.
    const val MIN_SAMPLES = 40

    // Desviación máxima del giroscopio para considerar el guante quieto.
    const val MAX_STD_DPS = 0.5f

    const val MIN_GRAVITY_G = 0.85f
    const val MAX_GRAVITY_G = 1.15f

    fun estimate(
        hand: Hand,
        frames: List<SensorFrame>,
        nowEpochMillis: Long
    ): GyroBiasResult {

        val samples = frames.filter { it.hand == hand }

        if (samples.size < MIN_SAMPLES) {
            return GyroBiasResult.TooFewSamples(samples.size)
        }

        if (samples.all { it.imuMissing }) {
            return GyroBiasResult.ImuMissing
        }

        val means = FloatArray(3)
        val stds = FloatArray(3)

        for (axis in 0 until 3) {

            val values = samples.map { it.gyro[axis].toDouble() }
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size

            means[axis] = mean.toFloat()
            stds[axis] = (sqrt(variance) / ImuNormalizer.GYRO_LSB_PER_DPS).toFloat()
        }

        val maxStd = stds.max()

        if (maxStd > MAX_STD_DPS) {
            return GyroBiasResult.Moving(maxStd)
        }

        val meanGravity =
            samples
                .map { frame ->
                    sqrt(
                        frame.accel.sumOf { (it.toDouble() * it) }
                    ) / ImuNormalizer.ACCEL_LSB_PER_G
                }
                .average()
                .toFloat()

        if (meanGravity < MIN_GRAVITY_G || meanGravity > MAX_GRAVITY_G) {
            return GyroBiasResult.AccelOutOfRange(meanGravity)
        }

        return GyroBiasResult.Ok(
            ImuCalibration(
                hand = hand,
                gyroBiasRaw = means.toList(),
                calibratedAtEpochMillis = nowEpochMillis
            )
        )
    }
}