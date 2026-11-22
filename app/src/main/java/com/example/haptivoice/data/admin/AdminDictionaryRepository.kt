package com.example.haptivoice.data.admin

import com.example.haptivoice.data.sync.FirestoreVocabularyCodec
import com.example.haptivoice.model.GestureTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

// =============================================================
// ROL DE ADMINISTRADOR (CUSTOM CLAIM admin: true)
// =============================================================

/*
 * El rol se asigna con functions/scripts/setAdmin.js. Viaja
 * dentro del token de Firebase Auth, así que las reglas y las
 * Cloud Functions lo revisan sin consultar otra colección.
 *
 * forceRefresh = true pide un token nuevo: sirve justo después
 * de asignar el rol, sin cerrar sesión.
 */
object AdminRole {

    suspend fun isAdmin(forceRefresh: Boolean = false): Boolean =
        try {
            FirebaseAuth.getInstance().currentUser
                ?.getIdToken(forceRefresh)
                ?.await()
                ?.claims
                ?.get("admin") == true
        } catch (exception: Exception) {
            false
        }
}

// =============================================================
// LEMA DEL DICCIONARIO BASE, VISTO POR EL ADMINISTRADOR
// =============================================================

data class AdminLemma(
    val lemmaId: String,
    val glosa: String,
    val template: GestureTemplate,
    val isActive: Boolean,
    val updatedAtMillis: Long
)

data class PublishedVersion(
    val version: Int,
    val versionId: String,
    val publishedAtMillis: Long
)

// =============================================================
// ADMINISTRACIÓN DEL DICCIONARIO BASE (CU-012, RF-22, RF-26)
// =============================================================

/*
 * El administrador edita la colección lemmas (borrador) y
 * después publica: publishDictionary copia los lemas activos
 * a dictionary_publications con una versión nueva. Los
 * teléfonos solo descargan publicaciones, nunca el borrador.
 *
 * Los lemas no se borran: se desactivan (isActive = false),
 * para no romper las reasignaciones premium que apuntan a ellos.
 *
 * Las reglas de Firestore exigen admin == true para escribir.
 */
class AdminDictionaryRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val lemmas = firestore.collection(LEMMAS)

    // Lista en vivo de todos los lemas, activos e inactivos.
    fun observeLemmas(): Flow<List<AdminLemma>> = callbackFlow {

        val registration =
            lemmas.addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                val items =
                    snapshot.documents.mapNotNull { doc ->

                        val glosa = doc.getString("glosa") ?: return@mapNotNull null
                        val gesto = doc.get("gesto") as? Map<*, *> ?: return@mapNotNull null
                        val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L

                        AdminLemma(
                            lemmaId = doc.id,
                            glosa = glosa,
                            template = FirestoreVocabularyCodec.templateFromMap(
                                doc.id, glosa, updatedAt, gesto
                            ),
                            isActive = doc.getBoolean("isActive") ?: true,
                            updatedAtMillis = updatedAt
                        )
                    }
                        .sortedBy { it.glosa.lowercase() }

                trySend(items)
            }

        awaitClose { registration.remove() }
    }

    // Última versión publicada (config/dictionary), en vivo.
    fun observePublishedVersion(): Flow<PublishedVersion?> = callbackFlow {

        val registration =
            firestore.collection(CONFIG).document(DICTIONARY_CONFIG)
                .addSnapshotListener { doc, _ ->

                    val version = doc?.getLong("latestVersion")?.toInt()
                    val versionId = doc?.getString("versionId")

                    trySend(
                        if (version != null && versionId != null) {
                            PublishedVersion(
                                version = version,
                                versionId = versionId,
                                publishedAtMillis =
                                    doc.getTimestamp("publishedAt")?.toDate()?.time ?: 0L
                            )
                        } else {
                            null
                        }
                    )
                }

        awaitClose { registration.remove() }
    }

    /*
     * Guarda un lema grabado. Si ya existe uno con esa glosa
     * (sin importar mayúsculas), se regraba ese mismo lema y
     * se reactiva; si no, se crea uno nuevo.
     * Devuelve el mensaje para mostrar en pantalla.
     */
    suspend fun saveLemma(
        glosa: String,
        template: GestureTemplate
    ): String {

        val clean = glosa.trim()

        if (clean.isEmpty()) {
            return "Escribe la palabra de la seña."
        }

        val existing =
            lemmas.get().await().documents.firstOrNull {
                it.getString("glosa")?.equals(clean, ignoreCase = true) == true
            }

        val lemmaId = existing?.id ?: UUID.randomUUID().toString()
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        val data =
            mutableMapOf(
                "lemmaId" to lemmaId,
                "glosa" to (existing?.getString("glosa") ?: clean),
                "gesto" to FirestoreVocabularyCodec.templateToMap(template),
                "isActive" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid
            )

        if (existing == null) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }

        lemmas.document(lemmaId)
            .set(data, SetOptions.merge())
            .await()

        return if (existing == null) {
            "\"$clean\" agregada al borrador del diccionario. Publica para enviarla a los teléfonos."
        } else {
            "\"$clean\" regrabada en el borrador. Publica para enviarla a los teléfonos."
        }
    }

    // Activar o desactivar sin borrar (CU-012).
    suspend fun setActive(lemmaId: String, active: Boolean) {

        lemmas.document(lemmaId)
            .update(
                mapOf(
                    "isActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                )
            )
            .await()
    }

    /*
     * Publica los lemas activos como una versión nueva.
     * Devuelve el número de versión y cuántos lemas lleva.
     */
    suspend fun publish(): Pair<Int, Int> {

        val result =
            FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("publishDictionary")
                .call()
                .await()

        val data = result.data as? Map<*, *>

        val version = (data?.get("version") as? Number)?.toInt() ?: 0
        val count = (data?.get("lemmaCount") as? Number)?.toInt() ?: 0

        return version to count
    }

    companion object {
        const val LEMMAS = "lemmas"
        const val CONFIG = "config"
        const val DICTIONARY_CONFIG = "dictionary"
        const val PUBLICATIONS = "dictionary_publications"
    }
}