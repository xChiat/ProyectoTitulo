package com.example.haptivoice.model

// =============================================================
// POSTURA CAPTURADA (SEÑA ESTÁTICA, SOLO MPU)
// =============================================================

/*
 * Una postura sostenida de las manos, detectada por el
 * segmentador. Para cada mano guarda la dirección de la
 * gravedad vista por su MPU (vector unitario x, y, z):
 * eso describe cómo está orientada la mano.
 */
data class PoseSample(
    val directions: Map<Hand, List<Float>>,
    val holdMillis: Long,
    val capturedAtMillis: Long
) {

    /*
     * Vector de características en un orden fijo de manos:
     * 3 valores por mano (I primero, luego D).
     */
    fun featureVector(hands: List<Hand>): List<Float> =
        hands.flatMap { directions.getValue(it) }
}

// =============================================================
// PLANTILLA DE UNA SEÑA
// =============================================================

/*
 * Lo que se guarda de una seña grabada: su glosa y varias
 * repeticiones del vector de características.
 *
 * Coincide con el formato del campo `gesto` de la hoja de ruta
 * (templateVersion, featureSet, kind, repetitions), con un
 * featureSet propio de esta versión solo MPU.
 *
 * isRest = postura de reposo (manos abajo o sobre la mesa):
 * el clasificador la reconoce para NO mostrar nada.
 */
data class GestureTemplate(
    val id: String,
    val glosa: String,
    val hands: List<Hand>,
    val isRest: Boolean,
    val repetitions: List<List<Float>>,
    val createdAtEpochMillis: Long,
    val templateVersion: Int = TEMPLATE_VERSION,
    val featureSet: String = FEATURE_SET_IMU_DIRECTION,
    val kind: String = KIND_STATIC
) {

    companion object {
        const val TEMPLATE_VERSION = 1
        const val FEATURE_SET_IMU_DIRECTION = "imu_gravity_dir_v1"
        const val KIND_STATIC = "static"
    }
}