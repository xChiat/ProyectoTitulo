package com.example.haptivoice.utils

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

// =============================================================
// CÁLCULOS CON DIRECCIONES 3D
// =============================================================

/*
 * Utilidades para comparar orientaciones de la mano.
 * Una dirección es un vector unitario (x, y, z).
 */
object PoseMath {

    fun unit(v: List<Float>): List<Float> {

        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()

        return if (norm == 0f) v else v.map { it / norm }
    }

    // Ángulo en grados entre dos direcciones (0 a 180).
    fun angleDeg(a: List<Float>, b: List<Float>): Float {

        val ua = unit(a)
        val ub = unit(b)

        val dot =
            (ua[0] * ub[0] + ua[1] * ub[1] + ua[2] * ub[2])
                .coerceIn(-1f, 1f)

        return Math.toDegrees(acos(dot.toDouble())).toFloat()
    }

    // Dirección promedio de varias direcciones.
    fun meanDirection(directions: List<List<Float>>): List<Float> {

        val sum = FloatArray(3)

        directions.forEach { d ->
            for (i in 0 until 3) sum[i] += d[i]
        }

        return unit(sum.toList())
    }

    /*
     * Inclinación lateral y frontal en grados, calculadas
     * igual que en NormalizedFrame, para mostrarlas en pantalla.
     */
    fun rollDeg(d: List<Float>): Float =
        Math.toDegrees(atan2(d[1].toDouble(), d[2].toDouble())).toFloat()

    fun pitchDeg(d: List<Float>): Float =
        Math.toDegrees(
            atan2(-d[0].toDouble(), sqrt((d[1] * d[1] + d[2] * d[2]).toDouble()))
        ).toFloat()

    /*
     * Distancia entre dos posturas: promedio, mano por mano,
     * del ángulo entre sus direcciones (3 valores por mano).
     * Es la misma medida que usa PoseClassifier.
     */
    fun poseDistanceDeg(
        a: List<Float>,
        b: List<Float>,
        handCount: Int
    ): Float {

        var total = 0f

        for (h in 0 until handCount) {
            total += angleDeg(
                a.subList(h * 3, h * 3 + 3),
                b.subList(h * 3, h * 3 + 3)
            )
        }

        return total / handCount
    }

    /*
     * Mayor desviación, en grados, entre cada repetición y el
     * promedio, revisando mano por mano (3 valores por mano).
     * Sirve para avisar si una seña se grabó de forma inconsistente.
     */
    fun maxDeviationDeg(
        repetitions: List<List<Float>>,
        handCount: Int
    ): Float {

        if (repetitions.size < 2) {
            return 0f
        }

        var maxDeviation = 0f

        for (h in 0 until handCount) {

            val segments =
                repetitions.map { it.subList(h * 3, h * 3 + 3) }

            val mean = meanDirection(segments)

            segments.forEach { segment ->
                maxDeviation = maxOf(maxDeviation, angleDeg(segment, mean))
            }
        }

        return maxDeviation
    }
}