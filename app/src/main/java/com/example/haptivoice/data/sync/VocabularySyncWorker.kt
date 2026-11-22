package com.example.haptivoice.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

// =============================================================
// TRABAJO EN SEGUNDO PLANO: SINCRONIZAR CUANDO HAYA RED
// =============================================================

/*
 * WorkManager ejecuta la sincronización solo con Internet.
 * Si el teléfono está sin red, el trabajo espera; si falla,
 * se reintenta con espera creciente. La app sigue funcionando
 * igual sin red (RNF-03).
 */
class VocabularySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {

        val userId =
            FirebaseAuth.getInstance().currentUser?.uid
                ?: return Result.success()

        return try {

            DictionarySync(applicationContext).refresh()

            VocabularySync(applicationContext).syncUser(userId)

            Result.success()

        } catch (exception: Exception) {

            Log.w(TAG, "Sincronización fallida (intento $runAttemptCount)", exception)

            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "VocabularySyncWorker"
        private const val MAX_ATTEMPTS = 5
    }
}

object VocabularySyncScheduler {

    private const val UNIQUE_WORK_NAME = "vocabulary-sync"

    /*
     * Pide una sincronización. Se llama al abrir Home y después
     * de cada cambio local (guardar, ordenar, eliminar).
     */
    fun request(context: Context) {

        val request =
            OneTimeWorkRequestBuilder<VocabularySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
    }
}