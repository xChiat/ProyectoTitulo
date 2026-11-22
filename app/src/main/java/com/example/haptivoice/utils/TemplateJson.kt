package com.example.haptivoice.utils

import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.Hand
import org.json.JSONArray
import org.json.JSONObject

// =============================================================
// PLANTILLA <-> JSON
// =============================================================

/*
 * Convierte una plantilla a texto JSON y de vuelta.
 * Room la guarda así, y en la etapa 7 el mismo formato
 * irá al campo `gesto` de Firestore.
 */
object TemplateJson {

    fun encode(template: GestureTemplate): String {

        val repetitions = JSONArray()

        template.repetitions.forEach { repetition ->
            val values = JSONArray()
            repetition.forEach { values.put(it.toDouble()) }
            repetitions.put(values)
        }

        val hands = JSONArray()
        template.hands.forEach { hands.put(it.code.toString()) }

        return JSONObject()
            .put("id", template.id)
            .put("glosa", template.glosa)
            .put("hands", hands)
            .put("isRest", template.isRest)
            .put("createdAt", template.createdAtEpochMillis)
            .put("templateVersion", template.templateVersion)
            .put("featureSet", template.featureSet)
            .put("kind", template.kind)
            .put("repetitions", repetitions)
            .toString()
    }

    fun decode(text: String): GestureTemplate {

        val json = JSONObject(text)

        val handsArray = json.getJSONArray("hands")

        val hands =
            (0 until handsArray.length()).mapNotNull { i ->
                Hand.fromCode(handsArray.getString(i).first())
            }

        val repetitionsArray = json.getJSONArray("repetitions")

        val repetitions =
            (0 until repetitionsArray.length()).map { i ->
                val values = repetitionsArray.getJSONArray(i)
                (0 until values.length()).map { j ->
                    values.getDouble(j).toFloat()
                }
            }

        return GestureTemplate(
            id = json.getString("id"),
            glosa = json.getString("glosa"),
            hands = hands,
            isRest = json.optBoolean("isRest", false),
            repetitions = repetitions,
            createdAtEpochMillis = json.optLong("createdAt", 0L),
            templateVersion = json.optInt("templateVersion", GestureTemplate.TEMPLATE_VERSION),
            featureSet = json.optString("featureSet", GestureTemplate.FEATURE_SET_IMU_DIRECTION),
            kind = json.optString("kind", GestureTemplate.KIND_STATIC)
        )
    }
}