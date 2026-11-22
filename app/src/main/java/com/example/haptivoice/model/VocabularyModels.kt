package com.example.haptivoice.model

// =============================================================
// MODELO DEL VOCABULARIO (INFORME, PUNTO 10.4)
// =============================================================

/*
 * Estas clases siguen las colecciones del diccionario de datos:
 * - BaseLemma      -> lemmas / dictionary_publications
 * - CustomGesture  -> custom_gestures
 * - LemmaOverride  -> lemma_overrides
 *
 * En la etapa 5 (Room) y la 7 (Firestore) se guardan con estos
 * mismos campos. Aquí son clases simples para las reglas.
 */

// Entrada del diccionario base, publicada por el administrador.
data class BaseLemma(
    val lemmaId: String,
    val glosa: String,
    val isActive: Boolean,
    val version: Int,
    val template: GestureTemplate
)

/*
 * Gesto propio del usuario.
 * order: posición en la lista (decisión del 21-11-2026: tras
 * vencer premium siguen activos los primeros 8 según el orden
 * de la lista). Si es null se ordena por fecha de creación.
 */
data class CustomGesture(
    val gestureId: String,
    val glosa: String,
    val template: GestureTemplate,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val order: Int? = null
)

// Gesto propio que reemplaza el de un lema base (solo premium).
data class LemmaOverride(
    val overrideId: String,
    val lemmaId: String,
    val template: GestureTemplate,
    val isActive: Boolean
)

// =============================================================
// RESULTADO: QUÉ RECONOCE EL CLASIFICADOR
// =============================================================

enum class VocabularySource {
    BASE,
    CUSTOM,
    OVERRIDE
}

data class VocabularyEntry(
    val id: String,
    val glosa: String,
    val template: GestureTemplate,
    val source: VocabularySource
)

data class ResolvedVocabulary(

    // Lo que usa el clasificador y se puede reproducir.
    val recognizable: List<VocabularyEntry>,

    /*
     * Gestos propios visibles y bloqueados: exceden el tope del
     * plan básico. No se reconocen ni se reproducen (HU11, HU16).
     */
    val blockedCustom: List<CustomGesture>,

    // Lemas base cuyo gesto reemplazó el usuario.
    val overriddenLemmaIds: Set<String>
)