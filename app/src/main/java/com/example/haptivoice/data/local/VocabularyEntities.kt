package com.example.haptivoice.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// =============================================================
// TABLAS LOCALES DEL VOCABULARIO (ROOM)
// =============================================================

/*
 * Copia local del vocabulario, según el punto 7.3 del informe:
 * - partición base:     base_lemmas (+ dictionary_meta)
 * - partición personal: custom_gestures y lemma_overrides
 *
 * La plantilla se guarda como texto JSON (TemplateJson), con el
 * mismo formato que irá al campo `gesto` en Firestore.
 *
 * syncState prepara la etapa 7: "pending" = falta subirlo.
 */

const val SYNC_PENDING = "pending"
const val SYNC_SYNCED = "synced"

// Copia de la publicación vigente del diccionario base.
@Entity(tableName = "base_lemmas")
data class BaseLemmaEntity(
    @PrimaryKey val lemmaId: String,
    val glosa: String,
    val isActive: Boolean,
    val version: Int,
    val templateJson: String
)

// Gestos propios de cada usuario del teléfono.
@Entity(
    tableName = "custom_gestures",
    indices = [Index(value = ["userId"])]
)
data class CustomGestureEntity(
    @PrimaryKey val gestureId: String,
    val userId: String,
    val glosa: String,
    val templateJson: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    // Posición en la lista: decide los 8 activos en plan básico.
    val sortOrder: Int,
    val syncState: String
)

// Reasignaciones premium de un lema base.
@Entity(
    tableName = "lemma_overrides",
    indices = [Index(value = ["userId", "lemmaId"], unique = true)]
)
data class LemmaOverrideEntity(
    @PrimaryKey val overrideId: String,
    val userId: String,
    val lemmaId: String,
    val templateJson: String,
    val isActive: Boolean,
    val updatedAt: Long,
    val syncState: String
)

// Versión descargada del diccionario base (una sola fila, id = 0).
@Entity(tableName = "dictionary_meta")
data class DictionaryMetaEntity(
    @PrimaryKey val id: Int = 0,
    val versionId: String?,
    val downloadedAt: Long
)

/*
 * Borrados hechos sin red que falta aplicar en Firestore
 * (etapa 7). collection = "custom_gestures" o "lemma_overrides".
 */
@Entity(tableName = "pending_deletions")
data class PendingDeletionEntity(
    @PrimaryKey val docId: String,
    val collection: String,
    val userId: String,
    val deletedAt: Long
)