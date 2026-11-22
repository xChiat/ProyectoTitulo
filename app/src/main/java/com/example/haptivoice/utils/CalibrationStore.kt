package com.example.haptivoice.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.ImuCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// =============================================================
// CALIBRACIÓN GUARDADA EN EL TELÉFONO
// =============================================================

/*
 * Guarda el sesgo del giroscopio de cada mano en
 * SharedPreferences, para no recalibrar cada vez que
 * se abre la app.
 *
 * Es un dato del guante físico, no del usuario, así que
 * no se sube a Firestore. En la etapa 5 puede pasar a Room
 * junto con la calibración de los flexores.
 */
class CalibrationStore(
    context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            "HaptiVoiceCalibration",
            Context.MODE_PRIVATE
        )

    /*
 * Un solo estado para toda la app: si se calibra desde Home,
 * el panel de traducción y la grabación lo ven al tiro.
 */
    private val _calibrations: MutableStateFlow<Map<Hand, ImuCalibration>> =
        synchronized(CalibrationStore) {
            shared ?: MutableStateFlow(loadAll()).also { shared = it }
        }

    val calibrations: StateFlow<Map<Hand, ImuCalibration>> =
        _calibrations.asStateFlow()

    fun get(hand: Hand): ImuCalibration? =
        _calibrations.value[hand]

    fun save(calibration: ImuCalibration) {

        val hand = calibration.hand.code

        prefs.edit()
            .putString(
                "gyro_bias_$hand",
                calibration.gyroBiasRaw.joinToString(",")
            )
            .putLong(
                "calibrated_at_$hand",
                calibration.calibratedAtEpochMillis
            )
            .apply()

        _calibrations.value =
            _calibrations.value + (calibration.hand to calibration)
    }

    fun clear(hand: Hand) {

        prefs.edit()
            .remove("gyro_bias_${hand.code}")
            .remove("calibrated_at_${hand.code}")
            .apply()

        _calibrations.value =
            _calibrations.value - hand
    }

    private fun loadAll(): Map<Hand, ImuCalibration> =
        Hand.entries
            .mapNotNull { hand -> load(hand)?.let { hand to it } }
            .toMap()

    private fun load(hand: Hand): ImuCalibration? {

        val text =
            prefs.getString("gyro_bias_${hand.code}", null)
                ?: return null

        val bias =
            text.split(',').mapNotNull { it.toFloatOrNull() }

        if (bias.size != 3) {
            return null
        }

        return ImuCalibration(
            hand = hand,
            gyroBiasRaw = bias,
            calibratedAtEpochMillis =
                prefs.getLong("calibrated_at_${hand.code}", 0L)
        )
    }
    companion object {

        @Volatile
        private var shared: MutableStateFlow<Map<Hand, ImuCalibration>>? = null
    }
}