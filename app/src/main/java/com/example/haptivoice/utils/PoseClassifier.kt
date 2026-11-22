package com.example.haptivoice.utils

import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.NormalizedFrame
import com.example.haptivoice.model.PoseSample

// =============================================================
// RESULTADO DE CLASIFICAR UNA POSTURA
// =============================================================

sealed class Classification {

    // Seña reconocida: se muestra y se lee.
    data class Recognized(
        val template: GestureTemplate,
        val distanceDeg: Float,
        val marginDeg: Float
    ) : Classification()

    // Postura de reposo: no se muestra nada.
    data class Rest(
        val template: GestureTemplate,
        val distanceDeg: Float
    ) : Classification()

    /*
     * Seña no reconocida (RF-15): demasiado lejos de todas las
     * plantillas, o muy parecida a dos señas distintas.
     * Se muestra un aviso, sin voz.
     */
    data class Unrecognized(
        val bestDistanceDeg: Float?,
        val ambiguousWith: List<String> = emptyList()
    ) : Classification()

    // No hay plantillas para las manos en uso.
    data object NoTemplates : Classification()
}

// =============================================================
// CLASIFICADOR DE POSTURAS ESTÁTICAS (SOLO MPU)
// =============================================================

/*
 * Compara la postura capturada con cada repetición guardada
 * (vecino más cercano):
 *
 * - Distancia entre dos posturas = promedio, mano por mano,
 *   del ángulo entre las direcciones de la gravedad.
 * - Distancia a una seña = la de su repetición más cercana.
 * - Si la mejor seña está a más de rejectDistanceDeg,
 *   o la segunda está a menos de minMarginDeg de diferencia,
 *   el resultado es "no reconocida".
 *
 * Solo usa plantillas grabadas con las mismas manos.
 * No depende de Android, así que se prueba con JUnit.
 */
class PoseClassifier(
    templates: List<GestureTemplate>,
    val hands: List<Hand>,
    private val rejectDistanceDeg: Float = 25f,
    private val minMarginDeg: Float = 5f
) {

    private val usable: List<GestureTemplate> =
        templates.filter { template ->
            template.hands == hands &&
                    template.featureSet == GestureTemplate.FEATURE_SET_IMU_DIRECTION &&
                    template.repetitions.isNotEmpty() &&
                    template.repetitions.all { it.size == hands.size * 3 }
        }

    val templateCount: Int
        get() = usable.size

    fun classify(sample: PoseSample): Classification {

        if (usable.isEmpty()) {
            return Classification.NoTemplates
        }

        val features = sample.featureVector(hands)

        val ranked =
            usable
                .map { template ->
                    template to template.repetitions.minOf { distanceDeg(features, it) }
                }
                .sortedBy { it.second }

        val (best, bestDistance) = ranked.first()

        if (bestDistance > rejectDistanceDeg) {
            return Classification.Unrecognized(bestDistance)
        }

        if (best.isRest) {
            return Classification.Rest(best, bestDistance)
        }

        val second = ranked.getOrNull(1)

        if (second != null && second.second - bestDistance < minMarginDeg) {
            return Classification.Unrecognized(
                bestDistanceDeg = bestDistance,
                ambiguousWith = listOf(best.glosa, second.first.glosa)
            )
        }

        return Classification.Recognized(
            template = best,
            distanceDeg = bestDistance,
            marginDeg = (second?.second ?: Float.MAX_VALUE) - bestDistance
        )
    }

    // Promedio, mano por mano, del ángulo entre direcciones.
    private fun distanceDeg(a: List<Float>, b: List<Float>): Float {

        var total = 0f

        for (h in hands.indices) {
            total += PoseMath.angleDeg(
                a.subList(h * 3, h * 3 + 3),
                b.subList(h * 3, h * 3 + 3)
            )
        }

        return total / hands.size
    }

    companion object {

        /*
         * Manos que usa el vocabulario: la combinación más común
         * entre las señas guardadas que no son de reposo.
         * null si no hay señas.
         */
        fun handsFor(templates: List<GestureTemplate>): List<Hand>? =
            templates
                .filterNot { it.isRest }
                .groupingBy { it.hands }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

        /*
         * Igual que handsFor, pero prefiere las combinaciones de
         * manos que están conectadas ahora. Así, con un solo
         * guante se traducen las señas grabadas con esa mano
         * aunque también existan señas de dos manos.
         * Si ninguna combinación está conectada, devuelve la más
         * común (para avisar qué mano falta).
         */
        fun handsFor(
            templates: List<GestureTemplate>,
            connected: Set<Hand>
        ): List<Hand>? {

            val counts =
                templates
                    .filterNot { it.isRest }
                    .groupingBy { it.hands }
                    .eachCount()

            return counts
                .filterKeys { hands -> hands.isNotEmpty() && connected.containsAll(hands) }
                .maxWithOrNull(
                    compareBy<Map.Entry<List<Hand>, Int>> { it.key.size }
                        .thenBy { it.value }
                )
                ?.key
                ?: counts.maxByOrNull { it.value }?.key
        }
    }
}

// =============================================================
// MOTOR DE RECONOCIMIENTO: SEGMENTADOR + CLASIFICADOR
// =============================================================

data class RecognitionEvent(
    val classification: Classification,
    val sample: PoseSample
)

/*
 * Recibe las tramas normalizadas y entrega un evento cada vez
 * que las manos se quedan quietas en una postura.
 */
class RecognitionEngine(
    templates: List<GestureTemplate>,
    hands: List<Hand>
) {

    private val segmenter = StaticPoseSegmenter(hands.toSet())

    val classifier = PoseClassifier(templates, hands)

    val status: SegmenterStatus
        get() = segmenter.status

    fun process(frame: NormalizedFrame): RecognitionEvent? {

        val sample = segmenter.process(frame) ?: return null

        return RecognitionEvent(
            classification = classifier.classify(sample),
            sample = sample
        )
    }
}