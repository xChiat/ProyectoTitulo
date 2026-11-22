package com.example.haptivoice.utils

import com.example.haptivoice.model.BaseLemma
import com.example.haptivoice.model.CustomGesture
import com.example.haptivoice.model.LemmaOverride
import com.example.haptivoice.model.ResolvedVocabulary
import com.example.haptivoice.model.VocabularyEntry
import com.example.haptivoice.model.VocabularySource

// =============================================================
// REGLAS DEL VOCABULARIO ACTIVO
// =============================================================

/*
 * Arma el conjunto de señas que el clasificador debe reconocer,
 * según el informe:
 *
 * 1. Lemas base: solo los activos (HU11, HU12).
 * 2. Reasignaciones: solo con plan premium vigente, y solo
 *    sobre lemas base activos. Reemplazan el gesto del lema,
 *    no su glosa. Sin premium rige el gesto base original (HU13).
 * 3. Gestos propios activos, en el orden de la lista:
 *    - premium: todos;
 *    - básico: los primeros `freeLimit` (8, RNF-09); el resto
 *      queda visible y bloqueado (HU16, RST-15).
 *
 * freeLimit debe coincidir con SubscriptionManager.FREE_GESTURE_LIMIT.
 * No depende de Android, así que se prueba con JUnit.
 */
object VocabularyResolver {

    const val DEFAULT_FREE_LIMIT = 8

    fun resolve(
        baseLemmas: List<BaseLemma>,
        customGestures: List<CustomGesture>,
        overrides: List<LemmaOverride>,
        isPremium: Boolean,
        freeLimit: Int = DEFAULT_FREE_LIMIT
    ): ResolvedVocabulary {

        val activeBase =
            baseLemmas.filter { it.isActive }

        val activeLemmaIds =
            activeBase.map { it.lemmaId }.toSet()

        // Una reasignación por lema; si hubiera varias, rige la última.
        val overrideByLemma: Map<String, LemmaOverride> =
            if (isPremium) {
                overrides
                    .filter { it.isActive && it.lemmaId in activeLemmaIds }
                    .associateBy { it.lemmaId }
            } else {
                emptyMap()
            }

        val baseEntries =
            activeBase.map { lemma ->

                val override = overrideByLemma[lemma.lemmaId]

                if (override != null) {
                    VocabularyEntry(
                        id = override.overrideId,
                        glosa = lemma.glosa,
                        template = override.template,
                        source = VocabularySource.OVERRIDE
                    )
                } else {
                    VocabularyEntry(
                        id = lemma.lemmaId,
                        glosa = lemma.glosa,
                        template = lemma.template,
                        source = VocabularySource.BASE
                    )
                }
            }

        val orderedCustom =
            customGestures
                .filter { it.isActive }
                .sortedWith(
                    compareBy<CustomGesture> { it.order ?: Int.MAX_VALUE }
                        .thenBy { it.createdAtEpochMillis }
                )

        val usableCustom =
            if (isPremium) orderedCustom else orderedCustom.take(freeLimit)

        val blockedCustom =
            if (isPremium) emptyList() else orderedCustom.drop(freeLimit)

        val customEntries =
            usableCustom.map { gesture ->
                VocabularyEntry(
                    id = gesture.gestureId,
                    glosa = gesture.glosa,
                    template = gesture.template,
                    source = VocabularySource.CUSTOM
                )
            }

        return ResolvedVocabulary(
            recognizable = baseEntries + customEntries,
            blockedCustom = blockedCustom,
            overriddenLemmaIds = overrideByLemma.keys
        )
    }
}