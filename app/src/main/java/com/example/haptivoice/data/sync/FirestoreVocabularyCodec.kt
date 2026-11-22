package com.example.haptivoice.data.sync

import com.example.haptivoice.data.local.CustomGestureEntity
import com.example.haptivoice.data.local.LemmaOverrideEntity
import com.example.haptivoice.data.local.SYNC_SYNCED
import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.Hand
import com.example.haptivoice.utils.TemplateJson
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

// =============================================================
// ROOM <-> FIRESTORE (DICCIONARIO DE DATOS, PUNTO 10.4.3)
// =============================================================

/*
 * Documentos de primer nivel con userId, como fija 10.4:
 *
 * custom_gestures/{gestureId}
 *   gestureId, userId, glosa, gesto (map), isActive,
 *   createdAt, updatedAt (timestamp), sortOrder
 *
 * lemma_overrides/{reasignacionId}
 *   reasignacionId, userId, lemmaId, gesto (map), isActive,
 *   updatedAt (timestamp)
 *
 * El campo `gesto` lleva la plantilla. Firestore no acepta
 * arreglos dentro de arreglos, por eso cada repetición va
 * como un map { data: [...] }.
 */
object FirestoreVocabularyCodec {

    const val CUSTOM_GESTURES = "custom_gestures"
    const val LEMMA_OVERRIDES = "lemma_overrides"

    // =========================================================
    // PLANTILLA
    // =========================================================

    fun templateToMap(template: GestureTemplate): Map<String, Any> =
        mapOf(
            "templateVersion" to template.templateVersion,
            "featureSet" to template.featureSet,
            "kind" to template.kind,
            "hands" to template.hands.map { it.code.toString() },
            "isRest" to template.isRest,
            "repetitions" to template.repetitions.map { repetition ->
                mapOf("data" to repetition.map { it.toDouble() })
            }
        )

    fun templateFromMap(
        id: String,
        glosa: String,
        createdAtMillis: Long,
        map: Map<*, *>
    ): GestureTemplate {

        val hands =
            (map["hands"] as? List<*>).orEmpty()
                .mapNotNull { (it as? String)?.firstOrNull()?.let(Hand::fromCode) }

        val repetitions =
            (map["repetitions"] as? List<*>).orEmpty()
                .mapNotNull { item ->
                    ((item as? Map<*, *>)?.get("data") as? List<*>)
                        ?.mapNotNull { (it as? Number)?.toFloat() }
                }

        return GestureTemplate(
            id = id,
            glosa = glosa,
            hands = hands,
            isRest = map["isRest"] as? Boolean ?: false,
            repetitions = repetitions,
            createdAtEpochMillis = createdAtMillis,
            templateVersion = (map["templateVersion"] as? Number)?.toInt()
                ?: GestureTemplate.TEMPLATE_VERSION,
            featureSet = map["featureSet"] as? String
                ?: GestureTemplate.FEATURE_SET_IMU_DIRECTION,
            kind = map["kind"] as? String ?: GestureTemplate.KIND_STATIC
        )
    }

    // =========================================================
    // GESTOS PROPIOS
    // =========================================================

    fun customToMap(entity: CustomGestureEntity): Map<String, Any> =
        mapOf(
            "gestureId" to entity.gestureId,
            "userId" to entity.userId,
            "glosa" to entity.glosa,
            "gesto" to templateToMap(TemplateJson.decode(entity.templateJson)),
            "isActive" to entity.isActive,
            "createdAt" to timestamp(entity.createdAt),
            "updatedAt" to timestamp(entity.updatedAt),
            "sortOrder" to entity.sortOrder
        )

    fun customFromDocument(doc: DocumentSnapshot): CustomGestureEntity? {

        val userId = doc.getString("userId") ?: return null
        val glosa = doc.getString("glosa") ?: return null
        val gesto = doc.get("gesto") as? Map<*, *> ?: return null
        val createdAt = millis(doc.getTimestamp("createdAt"))
        val updatedAt = millis(doc.getTimestamp("updatedAt"))

        val template = templateFromMap(doc.id, glosa, createdAt, gesto)

        return CustomGestureEntity(
            gestureId = doc.id,
            userId = userId,
            glosa = glosa,
            templateJson = TemplateJson.encode(template),
            isActive = doc.getBoolean("isActive") ?: true,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sortOrder = doc.getLong("sortOrder")?.toInt() ?: Int.MAX_VALUE,
            syncState = SYNC_SYNCED
        )
    }

    // =========================================================
    // REASIGNACIONES
    // =========================================================

    fun overrideToMap(entity: LemmaOverrideEntity): Map<String, Any> =
        mapOf(
            "reasignacionId" to entity.overrideId,
            "userId" to entity.userId,
            "lemmaId" to entity.lemmaId,
            "gesto" to templateToMap(TemplateJson.decode(entity.templateJson)),
            "isActive" to entity.isActive,
            "updatedAt" to timestamp(entity.updatedAt)
        )

    fun overrideFromDocument(doc: DocumentSnapshot): LemmaOverrideEntity? {

        val userId = doc.getString("userId") ?: return null
        val lemmaId = doc.getString("lemmaId") ?: return null
        val gesto = doc.get("gesto") as? Map<*, *> ?: return null
        val updatedAt = millis(doc.getTimestamp("updatedAt"))

        val template = templateFromMap(doc.id, "", updatedAt, gesto)

        return LemmaOverrideEntity(
            overrideId = doc.id,
            userId = userId,
            lemmaId = lemmaId,
            templateJson = TemplateJson.encode(template),
            isActive = doc.getBoolean("isActive") ?: true,
            updatedAt = updatedAt,
            syncState = SYNC_SYNCED
        )
    }

    // =========================================================
    // FECHAS
    // =========================================================

    private fun timestamp(millis: Long): Timestamp =
        Timestamp(Date(millis))

    private fun millis(timestamp: Timestamp?): Long =
        timestamp?.toDate()?.time ?: 0L
}