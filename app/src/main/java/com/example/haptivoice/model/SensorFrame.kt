package com.example.haptivoice.model

// =============================================================
// MANO DEL GUANTE
// =============================================================

/*
 * El firmware v3 escribe la mano como primer campo
 * de la trama: 'I' (izquierda) o 'D' (derecha).
 */
enum class Hand(val code: Char, val label: String) {

    LEFT('I', "Izquierda"),
    RIGHT('D', "Derecha");

    companion object {

        fun fromCode(code: Char): Hand? =
            entries.firstOrNull { it.code == code.uppercaseChar() }
    }
}

// =============================================================
// UNA TRAMA DEL GUANTE
// =============================================================

/*
 * Una lectura completa de un guante, ya separada en campos.
 *
 * Formato de la trama (firmware v3):
 * mano,seq,pulgar,indice,medio,anular,menique,ax,ay,az,gx,gy,gz
 *
 * - flex:  5 valores crudos del ADC (0-4095), de pulgar a meñique.
 * - accel: ax, ay, az crudos del MPU6050.
 * - gyro:  gx, gy, gz crudos del MPU6050.
 * - receivedAtMillis: momento de llegada al teléfono, con
 *   SystemClock.elapsedRealtime() (no cambia si se ajusta la hora).
 */
data class SensorFrame(
    val hand: Hand,
    val seq: Long,
    val receivedAtMillis: Long,
    val flex: List<Int>,
    val accel: List<Int>,
    val gyro: List<Int>
) {

    /*
     * true cuando el MPU no respondió y el firmware
     * mandó ceros en los 6 valores.
     */
    val imuMissing: Boolean
        get() = accel.all { it == 0 } && gyro.all { it == 0 }
}

// =============================================================
// ESTADÍSTICAS DE RECEPCIÓN POR GUANTE
// =============================================================

data class GloveStats(
    val hand: Hand,
    val received: Long = 0,
    val lost: Long = 0,
    val lastSeq: Long? = null,
    val lastFrame: SensorFrame? = null
) {

    // Porcentaje de tramas perdidas, calculado con seq.
    val lossPercent: Double
        get() {
            val expected = received + lost
            return if (expected == 0L) 0.0 else lost * 100.0 / expected
        }
}