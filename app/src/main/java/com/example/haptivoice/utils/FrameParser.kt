package com.example.haptivoice.utils

import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.SensorFrame

// =============================================================
// PARSER DE TRAMAS DEL GUANTE
// =============================================================

/*
 * Convierte una línea del firmware v3 en un SensorFrame.
 *
 * Devuelve null si la línea no tiene exactamente 13 campos,
 * si la mano no es I ni D, si algún número no se puede leer
 * o si un flexor está fuera del rango del ADC (0-4095).
 *
 * No depende de Android, así que se prueba con JUnit.
 */
object FrameParser {

    const val FIELD_COUNT = 13

    private const val ADC_MAX = 4095

    fun parse(
        line: String,
        receivedAtMillis: Long
    ): SensorFrame? {

        val parts = line.trim().split(',')

        if (parts.size != FIELD_COUNT) {
            return null
        }

        val handText = parts[0].trim()

        if (handText.length != 1) {
            return null
        }

        val hand = Hand.fromCode(handText[0]) ?: return null

        val seq = parts[1].trim().toLongOrNull() ?: return null

        if (seq < 0) {
            return null
        }

        val values = IntArray(FIELD_COUNT - 2)

        for (i in values.indices) {
            values[i] = parts[i + 2].trim().toIntOrNull() ?: return null
        }

        val flex = values.copyOfRange(0, 5).toList()

        if (flex.any { it < 0 || it > ADC_MAX }) {
            return null
        }

        return SensorFrame(
            hand = hand,
            seq = seq,
            receivedAtMillis = receivedAtMillis,
            flex = flex,
            accel = values.copyOfRange(5, 8).toList(),
            gyro = values.copyOfRange(8, 11).toList()
        )
    }
}