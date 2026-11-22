package com.example.haptivoice.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.haptivoice.data.local.BaseLemmaEntity
import com.example.haptivoice.data.local.DictionaryMetaEntity
import com.example.haptivoice.data.local.HaptiVoiceDatabase
import com.example.haptivoice.utils.TemplateJson
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// =============================================================
// DESCARGA DEL DICCIONARIO BASE PUBLICADO (ETAPA 8)
// =============================================================

/*
 * config/dictionary dice cuál es la última versión publicada.
 * Si es distinta de la que tiene el teléfono, se descarga
 * dictionary_publications/{versionId} y se reemplaza entera la
 * partición base en una sola transacción: o queda la versión
 * nueva completa, o sigue la anterior (punto 7.3).
 *
 * Sin red, el trabajo espera y el teléfono sigue reconociendo
 * con la última versión descargada (RNF-03).
 */
class DictionarySync(
    context: Context
) {

    private val database = HaptiVoiceDatabase.get(context)
    private val baseDao = database.baseLemmaDao()
    private val metaDao = database.dictionaryMetaDao()
    private val firestore = FirebaseFirestore.getInstance()

    // Devuelve true si descargó una versión nueva.
    suspend fun refresh(): Boolean {

        val config =
            firestore.collection("config")
                .document("dictionary")
                .get()
                .await()

        val versionId = config.getString("versionId") ?: return false
        val current = metaDao.get()?.versionId

        if (versionId == current) {
            return false
        }

        val publication =
            firestore.collection("dictionary_publications")
                .document(versionId)
                .get()
                .await()

        if (!publication.exists()) {
            Log.w(TAG, "Falta la publicación $versionId")
            return false
        }

        val version = publication.getLong("version")?.toInt() ?: 0

        val lemmas =
            (publication.get("lemmas") as? List<*>).orEmpty()
                .mapNotNull { item ->

                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val lemmaId = map["lemmaId"] as? String ?: return@mapNotNull null
                    val glosa = map["glosa"] as? String ?: return@mapNotNull null
                    val gesto = map["gesto"] as? Map<*, *> ?: return@mapNotNull null

                    val template =
                        FirestoreVocabularyCodec.templateFromMap(
                            id = lemmaId,
                            glosa = glosa,
                            createdAtMillis = 0L,
                            map = gesto
                        )

                    BaseLemmaEntity(
                        lemmaId = lemmaId,
                        glosa = glosa,
                        isActive = true,
                        version = version,
                        templateJson = TemplateJson.encode(template)
                    )
                }

        database.withTransaction {
            baseDao.deleteAll()
            baseDao.upsertAll(lemmas)
            metaDao.upsert(
                DictionaryMetaEntity(
                    versionId = versionId,
                    downloadedAt = System.currentTimeMillis()
                )
            )
        }

        Log.i(TAG, "Diccionario base $versionId descargado: ${lemmas.size} lemas")

        return true
    }

    companion object {
        private const val TAG = "DictionarySync"
    }
}