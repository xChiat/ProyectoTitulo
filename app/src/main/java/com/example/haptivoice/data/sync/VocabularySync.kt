package com.example.haptivoice.data.sync

import android.content.Context
import android.util.Log
import com.example.haptivoice.data.local.HaptiVoiceDatabase
import com.example.haptivoice.data.local.PendingDeletionEntity
import com.example.haptivoice.data.local.SYNC_SYNCED
import com.example.haptivoice.data.sync.FirestoreVocabularyCodec.CUSTOM_GESTURES
import com.example.haptivoice.data.sync.FirestoreVocabularyCodec.LEMMA_OVERRIDES
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// =============================================================
// SINCRONIZACIÓN ROOM <-> FIRESTORE (ETAPA 7)
// =============================================================

/*
 * El teléfono es donde se reconoce; Firestore es la réplica
 * (punto 7.3 y 10.4.2). Orden de cada sincronización:
 *
 * 1. Subir los borrados hechos sin red.
 * 2. Subir los gestos y reasignaciones pendientes.
 * 3. Bajar lo que hay en Firestore para este usuario:
 *    - lo que no existe en el teléfono se agrega
 *      (así se reconstruye la partición personal en un
 *      teléfono nuevo);
 *    - si el mismo documento cambió en ambos lados,
 *      gana el updatedAt más reciente;
 *    - lo que ya estaba subido y desapareció de Firestore
 *      se borra del teléfono.
 *
 * Nunca se sube la serie cruda del guante: solo plantillas.
 */
class VocabularySync(
    context: Context
) {

    private val database = HaptiVoiceDatabase.get(context)
    private val customDao = database.customGestureDao()
    private val overrideDao = database.lemmaOverrideDao()
    private val deletionDao = database.pendingDeletionDao()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncUser(userId: String) {

        pushDeletions(userId)
        pushPending(userId)
        pullCustomGestures(userId)
        pullOverrides(userId)
    }

    // =========================================================
    // SUBIR
    // =========================================================

    private suspend fun pushDeletions(userId: String) {

        deletionDao.getForUser(userId).forEach { deletion ->

            firestore.collection(deletion.collection)
                .document(deletion.docId)
                .delete()
                .await()

            deletionDao.delete(deletion.docId)
        }
    }

    private suspend fun pushPending(userId: String) {

        customDao.getPending(userId).forEach { entity ->

            firestore.collection(CUSTOM_GESTURES)
                .document(entity.gestureId)
                .set(FirestoreVocabularyCodec.customToMap(entity))
                .await()

            customDao.markSynced(entity.gestureId, entity.updatedAt)
        }

        overrideDao.getPending(userId).forEach { entity ->

            firestore.collection(LEMMA_OVERRIDES)
                .document(entity.overrideId)
                .set(FirestoreVocabularyCodec.overrideToMap(entity))
                .await()

            overrideDao.markSynced(entity.overrideId, entity.updatedAt)
        }
    }

    // =========================================================
    // BAJAR
    // =========================================================

    private suspend fun pullCustomGestures(userId: String) {

        val snapshot =
            firestore.collection(CUSTOM_GESTURES)
                .whereEqualTo("userId", userId)
                .get()
                .await()

        val pendingDeletes =
            deletionDao.getForUser(userId).map { it.docId }.toSet()

        val remoteIds = mutableSetOf<String>()

        snapshot.documents.forEach { doc ->

            remoteIds += doc.id

            if (doc.id in pendingDeletes) return@forEach

            val remote =
                FirestoreVocabularyCodec.customFromDocument(doc) ?: run {
                    Log.w(TAG, "Gesto remoto ilegible: ${doc.id}")
                    return@forEach
                }

            val local = customDao.get(userId, doc.id)

            val remoteWins =
                local == null ||
                        (local.syncState == SYNC_SYNCED && remote.updatedAt != local.updatedAt) ||
                        remote.updatedAt > local.updatedAt

            if (remoteWins) {
                customDao.upsert(remote)
            }
        }

        // Subidos antes y ya no están en Firestore: se borraron en otro teléfono.
        customDao.getForUser(userId)
            .filter { it.syncState == SYNC_SYNCED && it.gestureId !in remoteIds }
            .forEach { customDao.delete(userId, it.gestureId) }
    }

    private suspend fun pullOverrides(userId: String) {

        val snapshot =
            firestore.collection(LEMMA_OVERRIDES)
                .whereEqualTo("userId", userId)
                .get()
                .await()

        val pendingDeletes =
            deletionDao.getForUser(userId).map { it.docId }.toSet()

        val remoteIds = mutableSetOf<String>()

        snapshot.documents.forEach { doc ->

            remoteIds += doc.id

            if (doc.id in pendingDeletes) return@forEach

            val remote =
                FirestoreVocabularyCodec.overrideFromDocument(doc) ?: run {
                    Log.w(TAG, "Reasignación remota ilegible: ${doc.id}")
                    return@forEach
                }

            val local = overrideDao.getForUser(userId).firstOrNull { it.overrideId == doc.id }

            val remoteWins =
                local == null ||
                        (local.syncState == SYNC_SYNCED && remote.updatedAt != local.updatedAt) ||
                        remote.updatedAt > local.updatedAt

            if (!remoteWins) return@forEach

            /*
             * Si el mismo lema se reasignó en dos teléfonos sin red,
             * quedan dos documentos. Se conserva el más reciente y
             * el otro se anota para borrarlo en Firestore.
             */
            val sameLemma = overrideDao.findForLemma(userId, remote.lemmaId)

            if (sameLemma != null && sameLemma.overrideId != remote.overrideId) {

                if (sameLemma.updatedAt > remote.updatedAt) {
                    deletionDao.upsert(
                        PendingDeletionEntity(
                            docId = remote.overrideId,
                            collection = LEMMA_OVERRIDES,
                            userId = userId,
                            deletedAt = System.currentTimeMillis()
                        )
                    )
                    return@forEach
                }

                overrideDao.delete(userId, sameLemma.overrideId)
                deletionDao.upsert(
                    PendingDeletionEntity(
                        docId = sameLemma.overrideId,
                        collection = LEMMA_OVERRIDES,
                        userId = userId,
                        deletedAt = System.currentTimeMillis()
                    )
                )
            }

            overrideDao.upsert(remote)
        }

        overrideDao.getForUser(userId)
            .filter { it.syncState == SYNC_SYNCED && it.overrideId !in remoteIds }
            .forEach { overrideDao.delete(userId, it.overrideId) }
    }

    // =========================================================
    // ELIMINAR CUENTA (HU07, CU-006)
    // =========================================================

    /*
     * Borra los gestos y reasignaciones del usuario en Firestore
     * y en el teléfono. Requiere Internet, como la eliminación
     * de la cuenta.
     */
    suspend fun deleteAllForUser(userId: String) {

        listOf(CUSTOM_GESTURES, LEMMA_OVERRIDES).forEach { collection ->

            val snapshot =
                firestore.collection(collection)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

            // Un lote admite hasta 500 operaciones.
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }

        customDao.deleteAllForUser(userId)
        overrideDao.deleteAllForUser(userId)
        deletionDao.deleteAllForUser(userId)
    }

    companion object {
        private const val TAG = "VocabularySync"
    }
}