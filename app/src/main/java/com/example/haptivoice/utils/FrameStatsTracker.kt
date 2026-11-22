package com.example.haptivoice.utils

import com.example.haptivoice.model.GloveStats
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.SensorFrame

// =============================================================
// ESTADÍSTICAS DE RECEPCIÓN
// =============================================================

/*
 * Cuenta las tramas recibidas y perdidas de cada mano
 * usando el número de secuencia del firmware.
 *
 * - Si seq salta (por ejemplo de 10 a 13), se cuentan
 *   2 tramas perdidas.
 * - Si seq vuelve atrás o se repite, el guante se reinició:
 *   las cuentas de esa mano empiezan de nuevo.
 *
 * Los callbacks BLE llegan desde otros hilos, por eso
 * los métodos están sincronizados.
 */
class FrameStatsTracker {

    private val stats = mutableMapOf<Hand, GloveStats>()

    @Synchronized
    fun record(frame: SensorFrame): Map<Hand, GloveStats> {

        val previous = stats[frame.hand]
        val lastSeq = previous?.lastSeq

        val updated =
            if (previous == null || lastSeq == null || frame.seq <= lastSeq) {

                GloveStats(
                    hand = frame.hand,
                    received = 1,
                    lost = 0,
                    lastSeq = frame.seq,
                    lastFrame = frame
                )

            } else {

                val gap = frame.seq - lastSeq - 1

                previous.copy(
                    received = previous.received + 1,
                    lost = previous.lost + gap,
                    lastSeq = frame.seq,
                    lastFrame = frame
                )
            }

        stats[frame.hand] = updated

        return stats.toMap()
    }

    @Synchronized
    fun remove(hand: Hand): Map<Hand, GloveStats> {

        stats.remove(hand)

        return stats.toMap()
    }

    @Synchronized
    fun snapshot(): Map<Hand, GloveStats> = stats.toMap()
}