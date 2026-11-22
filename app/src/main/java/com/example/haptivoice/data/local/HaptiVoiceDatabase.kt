package com.example.haptivoice.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// =============================================================
// BASE DE DATOS LOCAL (ROOM)
// =============================================================

/*
 * Guarda el vocabulario en el almacenamiento privado de la app.
 * Android cifra ese almacenamiento por dispositivo, y el archivo
 * queda excluido de los respaldos (backup_rules.xml y
 * data_extraction_rules.xml), como pide el punto 7.3.
 *
 * Versiones:
 * 1: vocabulario local (etapa 5).
 * 2: tabla pending_deletions para sincronizar borrados (etapa 7).
 *
 * Si se cambian las tablas, subir la versión y agregar una
 * migración: así no se pierden los gestos de los usuarios.
 */
@Database(
    entities = [
        BaseLemmaEntity::class,
        CustomGestureEntity::class,
        LemmaOverrideEntity::class,
        DictionaryMetaEntity::class,
        PendingDeletionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HaptiVoiceDatabase : RoomDatabase() {

    abstract fun baseLemmaDao(): BaseLemmaDao

    abstract fun customGestureDao(): CustomGestureDao

    abstract fun lemmaOverrideDao(): LemmaOverrideDao

    abstract fun dictionaryMetaDao(): DictionaryMetaDao

    abstract fun pendingDeletionDao(): PendingDeletionDao

    companion object {

        const val DATABASE_NAME = "haptivoice.db"

        // 1 -> 2: se agrega la tabla de borrados pendientes.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_deletions` (" +
                            "`docId` TEXT NOT NULL, " +
                            "`collection` TEXT NOT NULL, " +
                            "`userId` TEXT NOT NULL, " +
                            "`deletedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`docId`))"
                )
            }
        }

        @Volatile
        private var instance: HaptiVoiceDatabase? = null

        fun get(context: Context): HaptiVoiceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HaptiVoiceDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}