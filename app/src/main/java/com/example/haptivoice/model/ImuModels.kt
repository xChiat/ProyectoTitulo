package com.example.haptivoice.model

import kotlin.math.atan2
import kotlin.math.sqrt

// =============================================================
// CALIBRACIÓN DEL MPU DE UN GUANTE
// =============================================================

/*
 * Sesgo del giroscopio medido con el guante quieto.
 * Se guarda en unidades crudas del MPU6050 (gx, gy, gz)
 * y se resta antes de convertir a °/s.
 *
 * Se guarda por mano: cada guante tiene su propio MPU.
 */
data class ImuCalibration(
    val hand: Hand,
    val gyroBiasRaw: List<Float>,
    val calibratedAtEpochMillis: Long
)

// =============================================================
// TRAMA NORMALIZADA (SOLO MPU, PRESENTACIÓN DEL LUNES)
// =============================================================

/*
 * La misma trama del guante, convertida a unidades físicas:
 * - accelG:   aceleración en g (1 g = gravedad).
 * - gyroDps:  velocidad de giro en °/s, sin el sesgo de reposo.
 *
 */
data class NormalizedFrame(
    val hand: Hand,
    val seq: Long,
    val receivedAtMillis: Long,
    val accelG: List<Float>,
    val gyroDps: List<Float>,
    val isGyroCalibrated: Boolean
) {

    // Largo del vector de aceleración; quieto debe ser cercano a 1 g.
    val accelMagnitudeG: Float
        get() = sqrt(accelG.sumOf { (it * it).toDouble() }).toFloat()

    /*
     * Inclinación de la mano calculada con la gravedad.
     * Solo es confiable con la mano quieta, que es el caso
     * de las señas estáticas.
     */
    val rollDeg: Float
        get() = Math.toDegrees(
            atan2(accelG[1].toDouble(), accelG[2].toDouble())
        ).toFloat()

    val pitchDeg: Float
        get() = Math.toDegrees(
            atan2(
                -accelG[0].toDouble(),
                sqrt((accelG[1] * accelG[1] + accelG[2] * accelG[2]).toDouble())
            )
        ).toFloat()
}