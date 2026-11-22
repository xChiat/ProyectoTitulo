package com.example.haptivoice.utils

import com.example.haptivoice.model.BaseLemma
import com.example.haptivoice.model.CustomGesture
import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.LemmaOverride
import com.example.haptivoice.model.VocabularyEntry

// =============================================================
// QUÉ PASA AL GUARDAR UNA PALABRA (CU-013)
// =============================================================

sealed class SavePlan {

    // Falta la palabra.
    data object Empty : SavePlan()

    // Gesto propio nuevo.
    data object NewCustom : SavePlan()

    // Ya existe un gesto propio con esa palabra: se regraba.
    data class ReplaceCustom(val gestureId: String) : SavePlan()

    /*
     * La palabra está en el diccionario base y el plan es premium:
     * se guarda como reasignación (flujo alternativo 1).
     */
    data class Reassign(
        val lemmaId: String,
        val baseGlosa: String,
        val existingOverrideId: String?
    ) : SavePlan()

    // La palabra está en el diccionario base y no hay premium (flujo 4).
    data class NeedsPremium(val baseGlosa: String) : SavePlan()

    // Plan básico con 8 gestos activos (flujo 3).
    data class LimitReached(val limit: Int) : SavePlan()

    val canRecord: Boolean
        get() = this is NewCustom || this is ReplaceCustom || this is Reassign
}

// Otra seña demasiado parecida a la que se está grabando.
data class GestureConflict(
    val glosa: String,
    val distanceDeg: Float
)

// =============================================================
// REGLAS DE LOS GESTOS PERSONALIZADOS (HU13, CU-013)
// =============================================================

/*
 * Decide qué hacer con una palabra antes de grabar y al guardar,
 * y detecta si la seña grabada se parece demasiado a otra.
 *
 * La usan la pantalla (para avisar antes de grabar) y el
 * repositorio (para validar al guardar), así ambos aplican
 * exactamente las mismas reglas.
 *
 * No depende de Android, así que se prueba con JUnit.
 */
object GestureRules {

    /*
     * Dos señas más cercanas que esto se confunden fácil:
     * el clasificador rechaza sobre 25° y exige 5° de margen.
     */
    const val DEFAULT_CONFLICT_DEG = 20f

    fun plan(
        glosa: String,
        isPremium: Boolean,
        baseLemmas: List<BaseLemma>,
        overrides: List<LemmaOverride>,
        customGestures: List<CustomGesture>,
        freeLimit: Int
    ): SavePlan {

        val clean = glosa.trim()

        if (clean.isEmpty()) {
            return SavePlan.Empty
        }

        val baseMatch =
            baseLemmas.firstOrNull {
                it.isActive && it.glosa.equals(clean, ignoreCase = true)
            }

        if (baseMatch != null) {

            if (!isPremium) {
                return SavePlan.NeedsPremium(baseMatch.glosa)
            }

            return SavePlan.Reassign(
                lemmaId = baseMatch.lemmaId,
                baseGlosa = baseMatch.glosa,
                existingOverrideId =
                    overrides.firstOrNull { it.lemmaId == baseMatch.lemmaId }?.overrideId
            )
        }

        val customMatch =
            customGestures.firstOrNull {
                it.glosa.equals(clean, ignoreCase = true)
            }

        if (customMatch != null) {
            return SavePlan.ReplaceCustom(customMatch.gestureId)
        }

        val activeCount = customGestures.count { it.isActive }

        if (!isPremium && activeCount >= freeLimit) {
            return SavePlan.LimitReached(freeLimit)
        }

        return SavePlan.NewCustom
    }

    /*
     * Señas del vocabulario que quedan a menos de thresholdDeg de
     * alguna repetición nueva. Se omite la misma palabra (si se
     * está regrabando) y las plantillas de otras manos.
     */
    fun findConflicts(
        glosa: String,
        hands: List<Hand>,
        newRepetitions: List<List<Float>>,
        vocabulary: List<VocabularyEntry>,
        thresholdDeg: Float = DEFAULT_CONFLICT_DEG
    ): List<GestureConflict> {

        if (newRepetitions.isEmpty() || hands.isEmpty()) {
            return emptyList()
        }

        val clean = glosa.trim()

        return vocabulary
            .filter { entry ->
                !entry.glosa.equals(clean, ignoreCase = true) &&
                        entry.template.hands == hands &&
                        entry.template.featureSet == GestureTemplate.FEATURE_SET_IMU_DIRECTION &&
                        entry.template.repetitions.all { it.size == hands.size * 3 }
            }
            .mapNotNull { entry ->

                val distance =
                    newRepetitions.minOf { repetition ->
                        entry.template.repetitions.minOfOrNull { other ->
                            PoseMath.poseDistanceDeg(repetition, other, hands.size)
                        } ?: Float.MAX_VALUE
                    }

                if (distance < thresholdDeg) {
                    GestureConflict(entry.glosa, distance)
                } else {
                    null
                }
            }
            .sortedBy { it.distanceDeg }
    }
}