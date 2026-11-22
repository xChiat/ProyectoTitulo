package com.example.haptivoice.data.sync

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

// =============================================================
// ESTADO DEL PLAN EN EL SERVIDOR (plans/{uid})
// =============================================================

/*
 * El servidor necesita saber si el usuario es Premium para
 * aplicar el tope de 8 gestos (enforceGestureQuota). Solo una
 * Cloud Function puede escribir plans/{uid}, y lo hace después
 * de verificar el purchaseToken con Google Play.
 *
 * Si no hay token no se llama: el documento conserva su fecha
 * de vencimiento y el servidor lo trata como básico al pasar
 * esa fecha.
 */
object PlanStatusSync {

    private const val TAG = "PlanStatusSync"

    suspend fun refresh(purchaseToken: String?) {

        if (purchaseToken.isNullOrBlank()) return

        try {
            FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("refreshPlanStatus")
                .call(mapOf("purchaseToken" to purchaseToken))
                .await()
        } catch (exception: Exception) {
            // Sin red o sin suscripción vigente: el reconocimiento
            // en el teléfono sigue igual.
            Log.w(TAG, "No se pudo actualizar el plan", exception)
        }
    }
}