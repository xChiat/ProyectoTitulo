package com.example.haptivoice.data

import android.content.Context
import android.util.Log
import com.example.haptivoice.data.local.BaseLemmaEntity
import com.example.haptivoice.data.local.CustomGestureEntity
import com.example.haptivoice.data.local.HaptiVoiceDatabase
import com.example.haptivoice.data.local.LemmaOverrideEntity
import com.example.haptivoice.data.local.PendingDeletionEntity
import com.example.haptivoice.data.local.SYNC_PENDING
import com.example.haptivoice.data.sync.FirestoreVocabularyCodec
import com.example.haptivoice.data.sync.VocabularySyncScheduler
import com.example.haptivoice.model.BaseLemma
import com.example.haptivoice.model.CustomGesture
import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.LemmaOverride
import com.example.haptivoice.model.ResolvedVocabulary
import com.example.haptivoice.utils.GestureRules
import com.example.haptivoice.utils.SavePlan
import com.example.haptivoice.utils.SubscriptionManager
import com.example.haptivoice.utils.TemplateJson
import com.example.haptivoice.utils.VocabularyResolver
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

// =============================================================
// ESTADO DEL VOCABULARIO PARA LAS PANTALLAS
// =============================================================

data class VocabularyState(

    // Lo que reconoce el clasificador, ya con las reglas del plan.
    val resolved: ResolvedVocabulary,

    // Listas completas, para el diccionario.
    val baseLemmas: List<BaseLemma>,
    val customGestures: List<CustomGesture>,
    val overrides: List<LemmaOverride>,

    val isPremium: Boolean
) {

    // Gestos propios que el plan básico no permite usar.
    val blockedIds: Set<String>
        get() = resolved.blockedCustom.map { it.gestureId }.toSet()
}

sealed class SaveGestureResult {

    data class Saved(
        val glosa: String,
        val isOverride: Boolean,
        val replacedExisting: Boolean
    ) : SaveGestureResult()

    data class Rejected(val reason: String) : SaveGestureResult()
}

// =============================================================
// REPOSITORIO DEL VOCABULARIO (ROOM, OFFLINE)
// =============================================================

/*
 * Única puerta de las pantallas al vocabulario:
 * - observe(): vocabulario resuelto según el plan, en vivo.
 * - saveRecordedGesture(): guarda un gesto grabado con las
 *   validaciones de HU13 (tope de 8, reasignación premium).
 * - moveCustomGesture(): cambia el orden de la lista.
 * - delete*(): borra gestos propios y reasignaciones (HU14).
 *
 * Todo ocurre en el teléfono, sin Internet (RNF-03). Cada
 * cambio queda con syncState = "pending" y se pide una
 * sincronización; WorkManager la hace cuando haya red (etapa 7).
 */
class VocabularyRepository(
    context: Context
) {

    private val appContext = context.applicationContext
    private val database = HaptiVoiceDatabase.get(appContext)
    private val baseDao = database.baseLemmaDao()
    private val customDao = database.customGestureDao()
    private val overrideDao = database.lemmaOverrideDao()
    private val deletionDao = database.pendingDeletionDao()

    private fun requestSync() {
        VocabularySyncScheduler.request(appContext)
    }

    /*
     * Anota un borrado para aplicarlo en Firestore. La partición
     * "local" (sin sesión) nunca se sube, así que no se anota.
     */
    private suspend fun rememberDeletion(
        userId: String,
        collection: String,
        docId: String
    ) {
        if (userId == LOCAL_USER_ID) return

        deletionDao.upsert(
            PendingDeletionEntity(
                docId = docId,
                collection = collection,
                userId = userId,
                deletedAt = System.currentTimeMillis()
            )
        )
    }

    // Sin sesión (no debería pasar en Home) se usa una partición local.
    private fun currentUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: LOCAL_USER_ID

    fun observe(isPremium: Flow<Boolean>): Flow<VocabularyState> {

        val userId = currentUserId()

        return combine(
            baseDao.observeAll(),
            customDao.observeForUser(userId),
            overrideDao.observeForUser(userId),
            isPremium
        ) { baseRows, customRows, overrideRows, premium ->

            val base = baseRows.mapNotNull { it.toModel() }
            val custom = customRows.mapNotNull { it.toModel() }
            val overrides = overrideRows.mapNotNull { it.toModel() }

            VocabularyState(
                resolved = VocabularyResolver.resolve(
                    baseLemmas = base,
                    customGestures = custom,
                    overrides = overrides,
                    isPremium = premium,
                    freeLimit = SubscriptionManager.FREE_GESTURE_LIMIT
                ),
                baseLemmas = base,
                customGestures = custom,
                overrides = overrides,
                isPremium = premium
            )
        }
    }

    // =========================================================
    // GUARDAR UN GESTO GRABADO (HU13, CU-013)
    // =========================================================

    /*
     * Aplica GestureRules.plan con los datos actuales de Room,
     * las mismas reglas que la pantalla muestra antes de grabar.
     */
    suspend fun saveRecordedGesture(
        glosa: String,
        template: GestureTemplate,
        isPremium: Boolean
    ): SaveGestureResult = withContext(Dispatchers.IO) {

        val userId = currentUserId()
        val cleanGlosa = glosa.trim()
        val now = System.currentTimeMillis()

        val plan =
            GestureRules.plan(
                glosa = cleanGlosa,
                isPremium = isPremium,
                baseLemmas = baseDao.getAll().mapNotNull { it.toModel() },
                overrides = overrideDao.getForUser(userId).mapNotNull { it.toModel() },
                customGestures = customDao.getForUser(userId).mapNotNull { it.toModel() },
                freeLimit = SubscriptionManager.FREE_GESTURE_LIMIT
            )

        val result = when (plan) {

            SavePlan.Empty ->
                SaveGestureResult.Rejected("Escribe una palabra o frase.")

            is SavePlan.NeedsPremium ->
                SaveGestureResult.Rejected(
                    "\"${plan.baseGlosa}\" ya está en el diccionario base. " +
                            "Cambiar su gesto requiere HaptiVoice Premium."
                )

            is SavePlan.LimitReached ->
                SaveGestureResult.Rejected(
                    "El plan gratuito permite ${plan.limit} gestos propios. " +
                            "Elimina uno o activa Premium."
                )

            is SavePlan.Reassign -> {

                overrideDao.upsert(
                    LemmaOverrideEntity(
                        overrideId = plan.existingOverrideId ?: UUID.randomUUID().toString(),
                        userId = userId,
                        lemmaId = plan.lemmaId,
                        templateJson = TemplateJson.encode(
                            template.copy(glosa = plan.baseGlosa)
                        ),
                        isActive = true,
                        updatedAt = now,
                        syncState = SYNC_PENDING
                    )
                )

                SaveGestureResult.Saved(
                    glosa = plan.baseGlosa,
                    isOverride = true,
                    replacedExisting = plan.existingOverrideId != null
                )
            }

            is SavePlan.ReplaceCustom -> {

                val existing = customDao.get(userId, plan.gestureId)

                customDao.upsert(
                    CustomGestureEntity(
                        gestureId = plan.gestureId,
                        userId = userId,
                        glosa = cleanGlosa,
                        templateJson = TemplateJson.encode(
                            template.copy(glosa = cleanGlosa)
                        ),
                        isActive = true,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        sortOrder = existing?.sortOrder
                            ?: ((customDao.maxSortOrder(userId) ?: -1) + 1),
                        syncState = SYNC_PENDING
                    )
                )

                SaveGestureResult.Saved(
                    glosa = cleanGlosa,
                    isOverride = false,
                    replacedExisting = true
                )
            }

            SavePlan.NewCustom -> {

                customDao.upsert(
                    CustomGestureEntity(
                        gestureId = UUID.randomUUID().toString(),
                        userId = userId,
                        glosa = cleanGlosa,
                        templateJson = TemplateJson.encode(
                            template.copy(glosa = cleanGlosa)
                        ),
                        isActive = true,
                        createdAt = now,
                        updatedAt = now,
                        sortOrder = (customDao.maxSortOrder(userId) ?: -1) + 1,
                        syncState = SYNC_PENDING
                    )
                )

                SaveGestureResult.Saved(
                    glosa = cleanGlosa,
                    isOverride = false,
                    replacedExisting = false
                )
            }
        }

        if (result is SaveGestureResult.Saved) {
            requestSync()
        }

        result
    }

    // =========================================================
    // ORDEN DE LA LISTA
    // =========================================================

    /*
     * Sube o baja un gesto propio una posición. Con plan básico,
     * el orden decide cuáles 8 siguen activos (decisión del
     * 21-11-2026). Se renumera toda la lista para evitar empates.
     */
    suspend fun moveCustomGesture(
        gestureId: String,
        moveUp: Boolean
    ) = withContext(Dispatchers.IO) {

        val userId = currentUserId()
        val now = System.currentTimeMillis()

        database.withTransaction {

            val list = customDao.getForUser(userId).toMutableList()
            val from = list.indexOfFirst { it.gestureId == gestureId }
            val to = if (moveUp) from - 1 else from + 1

            if (from < 0 || to !in list.indices) {
                return@withTransaction
            }

            val moved = list[from]
            list[from] = list[to]
            list[to] = moved

            list.forEachIndexed { index, entity ->
                if (entity.sortOrder != index) {
                    customDao.upsert(
                        entity.copy(
                            sortOrder = index,
                            updatedAt = now,
                            syncState = SYNC_PENDING
                        )
                    )
                }
            }
        }

        requestSync()
    }

    // =========================================================
    // ELIMINAR (HU14, CU-014)
    // =========================================================

    suspend fun deleteCustomGesture(gestureId: String) =
        withContext(Dispatchers.IO) {

            val userId = currentUserId()

            database.withTransaction {
                customDao.delete(userId, gestureId)
                rememberDeletion(
                    userId,
                    FirestoreVocabularyCodec.CUSTOM_GESTURES,
                    gestureId
                )
            }

            requestSync()
        }

    // Al borrar una reasignación vuelve a regir el gesto base.
    suspend fun deleteOverride(overrideId: String) =
        withContext(Dispatchers.IO) {

            val userId = currentUserId()

            database.withTransaction {
                overrideDao.delete(userId, overrideId)
                rememberDeletion(
                    userId,
                    FirestoreVocabularyCodec.LEMMA_OVERRIDES,
                    overrideId
                )
            }

            requestSync()
        }

    // =========================================================
    // CONVERSIONES TABLA -> MODELO
    // =========================================================

    private fun decodeTemplate(json: String): GestureTemplate? =
        try {
            TemplateJson.decode(json)
        } catch (exception: Exception) {
            Log.e(TAG, "Plantilla ilegible, se omite", exception)
            null
        }

    private fun BaseLemmaEntity.toModel(): BaseLemma? =
        decodeTemplate(templateJson)?.let {
            BaseLemma(lemmaId, glosa, isActive, version, it)
        }

    private fun CustomGestureEntity.toModel(): CustomGesture? =
        decodeTemplate(templateJson)?.let {
            CustomGesture(gestureId, glosa, it, isActive, createdAt, sortOrder)
        }

    private fun LemmaOverrideEntity.toModel(): LemmaOverride? =
        decodeTemplate(templateJson)?.let {
            LemmaOverride(overrideId, lemmaId, it, isActive)
        }

    companion object {
        private const val TAG = "VocabularyRepository"
        const val LOCAL_USER_ID = "local"
    }
}