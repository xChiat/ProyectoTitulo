package com.example.haptivoice.utils

import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.NormalizedFrame
import com.example.haptivoice.model.PoseSample
import kotlin.math.abs
import kotlin.math.sqrt

// =============================================================
// ESTADO DEL SEGMENTADOR (PARA MOSTRAR EN PANTALLA)
// =============================================================

sealed class SegmenterStatus {

    // Falta recibir tramas recientes de alguna mano requerida.
    data class WaitingForHands(val missing: Set<Hand>) : SegmenterStatus()

    // Una mano manda ceros en el MPU.
    data class ImuMissing(val hand: Hand) : SegmenterStatus()

    // Las manos se están moviendo.
    data object Moving : SegmenterStatus()

    // Todas quietas; heldMillis = cuánto llevan quietas juntas.
    data class Holding(val heldMillis: Long) : SegmenterStatus()

    /*
     * Ya se capturó la postura actual. Para capturar otra hay
     * que mover las manos y volver a quedarse quieto.
     */
    data object WaitingForMovement : SegmenterStatus()
}

// =============================================================
// SEGMENTADOR DE POSTURAS ESTÁTICAS
// =============================================================

/*
 * Detecta cuándo las manos quedan quietas en una postura y
 * entrega UNA PoseSample por cada postura sostenida.
 *
 * Reglas:
 * - Una mano está quieta si gira a menos de moveThresholdDps
 *   y su aceleración está cerca de 1 g (solo la gravedad).
 * - Cuando todas las manos requeridas llevan holdMillis quietas
 *   a la vez, se captura la postura: la dirección promedio de
 *   la gravedad de cada mano durante ese tiempo.
 * - Después de capturar, no se captura de nuevo hasta que
 *   alguna mano se mueva. Así una seña sostenida produce un
 *   solo resultado.
 *
 * No depende de Android, así que se prueba con JUnit.
 */
class StaticPoseSegmenter(
    private val requiredHands: Set<Hand>,
    private val holdMillis: Long = 400,
    private val moveThresholdDps: Float = 15f,
    private val gravityToleranceG: Float = 0.15f,
    private val staleMillis: Long = 300
) {

    private class HandState {
        var lastFrameAt: Long = Long.MIN_VALUE
        var stillSince: Long? = null
        val stillFrames = ArrayDeque<NormalizedFrame>()
    }

    private val states = mutableMapOf<Hand, HandState>()

    // Se necesita movimiento antes de capturar otra postura.
    private var armed = true

    var status: SegmenterStatus =
        SegmenterStatus.WaitingForHands(requiredHands)
        private set

    fun reset() {
        states.clear()
        armed = true
        status = SegmenterStatus.WaitingForHands(requiredHands)
    }

    fun process(frame: NormalizedFrame): PoseSample? {

        if (frame.hand !in requiredHands) {
            return null
        }

        val now = frame.receivedAtMillis
        val state = states.getOrPut(frame.hand) { HandState() }

        state.lastFrameAt = now

        val accelMagnitude = frame.accelMagnitudeG

        // MPU sin datos: la aceleración queda en 0.
        if (accelMagnitude < 0.1f) {
            state.stillSince = null
            state.stillFrames.clear()
            status = SegmenterStatus.ImuMissing(frame.hand)
            return null
        }

        val gyroMagnitude =
            sqrt(frame.gyroDps.sumOf { (it * it).toDouble() }).toFloat()

        val moving =
            gyroMagnitude > moveThresholdDps ||
                    abs(accelMagnitude - 1f) > gravityToleranceG

        if (moving) {
            state.stillSince = null
            state.stillFrames.clear()
            armed = true
            status = SegmenterStatus.Moving
            return null
        }

        if (state.stillSince == null) {
            state.stillSince = now
        }

        state.stillFrames.addLast(frame)

        while (state.stillFrames.size > MAX_BUFFERED_FRAMES) {
            state.stillFrames.removeFirst()
        }

        // ¿Todas las manos requeridas están enviando y quietas?
        val missing =
            requiredHands.filter { hand ->
                val s = states[hand]
                s == null || now - s.lastFrameAt > staleMillis
            }.toSet()

        if (missing.isNotEmpty()) {
            status = SegmenterStatus.WaitingForHands(missing)
            return null
        }

        val stillSinceAll =
            requiredHands.map { states.getValue(it).stillSince }

        if (stillSinceAll.any { it == null }) {
            status = SegmenterStatus.Moving
            return null
        }

        if (!armed) {
            status = SegmenterStatus.WaitingForMovement
            return null
        }

        val holdStart = stillSinceAll.maxOf { it!! }
        val held = now - holdStart

        if (held < holdMillis) {
            status = SegmenterStatus.Holding(held)
            return null
        }

        // Captura: dirección promedio de cada mano desde holdStart.
        val directions =
            requiredHands.associateWith { hand ->

                val frames =
                    states.getValue(hand).stillFrames
                        .filter { it.receivedAtMillis >= holdStart }
                        .ifEmpty { listOf(states.getValue(hand).stillFrames.last()) }

                PoseMath.meanDirection(
                    frames.map { PoseMath.unit(it.accelG) }
                )
            }

        armed = false
        status = SegmenterStatus.WaitingForMovement

        return PoseSample(
            directions = directions,
            holdMillis = held,
            capturedAtMillis = now
        )
    }

    companion object {
        // 5 s de tramas a 20 por segundo.
        private const val MAX_BUFFERED_FRAMES = 100
    }
}