package com.example.haptivoice.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// =============================================================
// CONSULTAS DE LAS TABLAS DEL VOCABULARIO
// =============================================================

@Dao
interface BaseLemmaDao {

    @Query("SELECT * FROM base_lemmas ORDER BY glosa COLLATE NOCASE")
    fun observeAll(): Flow<List<BaseLemmaEntity>>

    @Query(
        "SELECT * FROM base_lemmas " +
                "WHERE isActive = 1 AND glosa = :glosa COLLATE NOCASE LIMIT 1"
    )
    suspend fun findActiveByGlosa(glosa: String): BaseLemmaEntity?

    @Query("SELECT * FROM base_lemmas")
    suspend fun getAll(): List<BaseLemmaEntity>

    @Upsert
    suspend fun upsertAll(items: List<BaseLemmaEntity>)

    @Query("DELETE FROM base_lemmas")
    suspend fun deleteAll()
}

@Dao
interface CustomGestureDao {

    @Query(
        "SELECT * FROM custom_gestures " +
                "WHERE userId = :userId ORDER BY sortOrder, createdAt"
    )
    fun observeForUser(userId: String): Flow<List<CustomGestureEntity>>

    @Query(
        "SELECT * FROM custom_gestures " +
                "WHERE userId = :userId ORDER BY sortOrder, createdAt"
    )
    suspend fun getForUser(userId: String): List<CustomGestureEntity>

    @Query(
        "SELECT * FROM custom_gestures " +
                "WHERE userId = :userId AND gestureId = :gestureId LIMIT 1"
    )
    suspend fun get(userId: String, gestureId: String): CustomGestureEntity?

    @Query(
        "SELECT * FROM custom_gestures " +
                "WHERE userId = :userId AND glosa = :glosa COLLATE NOCASE LIMIT 1"
    )
    suspend fun findByGlosa(userId: String, glosa: String): CustomGestureEntity?

    @Query(
        "SELECT COUNT(*) FROM custom_gestures " +
                "WHERE userId = :userId AND isActive = 1"
    )
    suspend fun countActive(userId: String): Int

    @Query("SELECT MAX(sortOrder) FROM custom_gestures WHERE userId = :userId")
    suspend fun maxSortOrder(userId: String): Int?

    @Upsert
    suspend fun upsert(item: CustomGestureEntity)

    // Etapa 7: cambios que falta subir a Firestore.
    @Query(
        "SELECT * FROM custom_gestures " +
                "WHERE userId = :userId AND syncState = 'pending'"
    )
    suspend fun getPending(userId: String): List<CustomGestureEntity>

    /*
     * Marca como subido solo si no cambió mientras se subía
     * (mismo updatedAt); si cambió, queda pendiente.
     */
    @Query(
        "UPDATE custom_gestures SET syncState = 'synced' " +
                "WHERE gestureId = :gestureId AND updatedAt = :updatedAt"
    )
    suspend fun markSynced(gestureId: String, updatedAt: Long)

    @Query(
        "DELETE FROM custom_gestures " +
                "WHERE userId = :userId AND gestureId = :gestureId"
    )
    suspend fun delete(userId: String, gestureId: String)

    // Para HU07: al eliminar la cuenta se borran sus gestos.
    @Query("DELETE FROM custom_gestures WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface LemmaOverrideDao {

    @Query("SELECT * FROM lemma_overrides WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<LemmaOverrideEntity>>

    @Query("SELECT * FROM lemma_overrides WHERE userId = :userId")
    suspend fun getForUser(userId: String): List<LemmaOverrideEntity>

    @Query(
        "SELECT * FROM lemma_overrides " +
                "WHERE userId = :userId AND lemmaId = :lemmaId LIMIT 1"
    )
    suspend fun findForLemma(userId: String, lemmaId: String): LemmaOverrideEntity?

    @Upsert
    suspend fun upsert(item: LemmaOverrideEntity)

    @Query(
        "SELECT * FROM lemma_overrides " +
                "WHERE userId = :userId AND syncState = 'pending'"
    )
    suspend fun getPending(userId: String): List<LemmaOverrideEntity>

    @Query(
        "UPDATE lemma_overrides SET syncState = 'synced' " +
                "WHERE overrideId = :overrideId AND updatedAt = :updatedAt"
    )
    suspend fun markSynced(overrideId: String, updatedAt: Long)

    @Query(
        "DELETE FROM lemma_overrides " +
                "WHERE userId = :userId AND overrideId = :overrideId"
    )
    suspend fun delete(userId: String, overrideId: String)

    @Query("DELETE FROM lemma_overrides WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface DictionaryMetaDao {

    @Query("SELECT * FROM dictionary_meta WHERE id = 0")
    fun observe(): Flow<DictionaryMetaEntity?>


    @Query("SELECT * FROM dictionary_meta WHERE id = 0")
    suspend fun get(): DictionaryMetaEntity?

    @Upsert
    suspend fun upsert(item: DictionaryMetaEntity)
}

@Dao
interface PendingDeletionDao {

    @Query("SELECT * FROM pending_deletions WHERE userId = :userId")
    suspend fun getForUser(userId: String): List<PendingDeletionEntity>

    @Upsert
    suspend fun upsert(item: PendingDeletionEntity)

    @Query("DELETE FROM pending_deletions WHERE docId = :docId")
    suspend fun delete(docId: String)

    @Query("DELETE FROM pending_deletions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}