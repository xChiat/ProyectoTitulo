
package com.example.haptivoice.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

/**
 * Un solo plan premium (haptivoice_premium).
 * Desbloquea voces de mejor calidad, traducción ES-EN,
 * gestos personalizados ilimitados y escritura a voz.
 *
 * Los IDs deben coincidir con Play Console:
 * suscripción "haptivoice_premium", base plans "monthly" y "yearly".
 * No incluye prueba gratis.
 *
 * Por el momento se utiliza Google Play Billing desde Android.
 *
 * PENDIENTE ANTES DE PRODUCCIÓN:
 * Verificar las compras mediante un backend seguro y asociarlas
 * a la cuenta Firebase del usuario.
 */
class SubscriptionManager(
    context: Context
) {

    private val appContext = context.applicationContext

    // =========================================================
    // ESTADO DE LA SUSCRIPCIÓN
    // =========================================================

    /*
     * Premium comienza desactivado.
     *
     * No recuperamos un Boolean de SharedPreferences porque
     * podría quedar desactualizado si la suscripción vence,
     * se cancela o cambia la cuenta.
     *
     * El estado se consulta nuevamente a Google Play.
     */
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /*
     * Token de la compra Premium actual.
     *
     * Solo se conserva en memoria.
     * Nunca debe incluirse en Logcat ni compartirse
     * con servicios distintos del backend autorizado.
     *
     * El backend verificará este token con Google Play.
     */
    private val _purchaseToken = MutableStateFlow<String?>(null)

    val purchaseToken: StateFlow<String?> =
        _purchaseToken.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _offers =
        MutableStateFlow<List<PremiumOffer>>(emptyList())

    val offers: StateFlow<List<PremiumOffer>> =
        _offers.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var isClosed = false

    // =========================================================
    // RESULTADOS DE COMPRAS
    // =========================================================

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->

            if (isClosed) {
                return@PurchasesUpdatedListener
            }

            when (billingResult.responseCode) {

                BillingClient.BillingResponseCode.OK -> {

                    if (purchases.isNullOrEmpty()) {

                        Log.w(
                            TAG,
                            "Google Play no devolvió compras en este evento"
                        )

                        return@PurchasesUpdatedListener
                    }

                    /*
                     * Una compra PENDING todavía no concede Premium.
                     */
                    purchases.forEach { purchase ->

                        if (
                            purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                            purchase.purchaseState ==
                            Purchase.PurchaseState.PENDING
                        ) {

                            Log.d(
                                TAG,
                                "Suscripción pendiente de pago"
                            )
                        }
                    }

                    /*
                     * Consultamos nuevamente las compras actuales.
                     *
                     * No reemplazamos el estado Premium con una lista
                     * parcial proveniente de este evento.
                     */
                    queryActiveSubscription(
                        onResult = { active ->

                            Log.d(
                                TAG,
                                "Estado Premium actualizado: $active"
                            )
                        },
                        onError = { message ->

                            Log.e(TAG, message)
                        }
                    )
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {

                    Log.d(
                        TAG,
                        "El usuario canceló el proceso de compra"
                    )
                }

                else -> {

                    Log.e(
                        TAG,
                        "Compra fallida: ${billingResult.debugMessage}"
                    )
                }
            }
        }

    // =========================================================
    // CLIENTE DE GOOGLE PLAY BILLING
    // =========================================================

    private val billingClient: BillingClient =
        BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

    // =========================================================
    // INICIAR GOOGLE PLAY BILLING
    // =========================================================

    fun start(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        if (isClosed) {

            onError("SubscriptionManager ya fue cerrado")
            return
        }

        if (billingClient.isReady) {

            _billingReady.value = true
            refresh(onReady, onError)
            return
        }

        billingClient.startConnection(
            object : BillingClientStateListener {

                override fun onBillingSetupFinished(
                    billingResult: BillingResult
                ) {

                    if (isClosed) {
                        return
                    }

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {

                        _billingReady.value = true

                        Log.d(
                            TAG,
                            "Conexión con Google Play Billing establecida"
                        )

                        refresh(onReady, onError)

                    } else {

                        _billingReady.value = false

                        onError(
                            billingResult.toUserMessage(
                                "No se pudo conectar con Google Play"
                            )
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {

                    _billingReady.value = false

                    Log.w(
                        TAG,
                        "Se perdió la conexión con Google Play Billing"
                    )
                }
            }
        )
    }

    // =========================================================
    // ACTUALIZAR PRODUCTOS Y SUSCRIPCIONES
    // =========================================================

    fun refresh(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        if (isClosed) {

            onError("SubscriptionManager ya fue cerrado")
            return
        }

        if (!billingClient.isReady) {

            start(onReady, onError)
            return
        }

        _billingReady.value = true

        /*
         * Primero consultamos los productos disponibles.
         *
         * Después consultamos las suscripciones existentes,
         * aunque el producto todavía no esté creado en Play Console.
         */
        queryProduct(
            onComplete = {

                queryActiveSubscription(
                    onResult = {

                        onReady()
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }


// =========================================================
// IDENTIFICADOR SEGURO PARA GOOGLE PLAY
// =========================================================

    /*
     * Asociamos las compras nuevas con el UID de Firebase.
     *
     * No enviamos el correo ni datos personales en texto claro.
     * El backend debe calcular el mismo SHA-256 para verificar
     * que la compra corresponde a la cuenta autenticada.
     */
    private fun getObfuscatedAccountId(): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return null

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
    }

    // =========================================================
    // COMPRAR SUSCRIPCIÓN PREMIUM
    // =========================================================

    fun purchase(
        activity: Activity,
        basePlanId: String = BASE_PLAN_MONTHLY,
        onError: (String) -> Unit
    ) {

        if (isClosed) {

            onError("El sistema de suscripciones está cerrado")
            return
        }

        if (!billingClient.isReady) {

            onError("Google Play todavía no está listo")
            return
        }

        if (_isPremium.value) {

            onError("Tu suscripción Premium ya está activa")
            return
        }

        val details = productDetails

        if (details == null) {

            onError(
                "La suscripción Premium todavía no está disponible en Google Play."
            )

            return
        }

        /*
         * Seleccionamos exactamente el plan solicitado.
         *
         * offerId == null corresponde al plan base regular.
         * Así no seleccionamos por accidente una oferta
         * promocional o una prueba gratuita.
         */
        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { availableOffer ->

                availableOffer.basePlanId == basePlanId &&
                        availableOffer.offerId == null
            }

        if (offer == null) {

            onError(
                "El plan seleccionado todavía no está disponible."
            )

            return
        }

        val productDetailsParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build()


        val obfuscatedAccountId = getObfuscatedAccountId()

        if (obfuscatedAccountId == null) {
            onError(
                "Debes iniciar sesión en HaptiVoice para comprar Premium."
            )
            return
        }

        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(productDetailsParams)
                )
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build()

        val result = billingClient.launchBillingFlow(
            activity,
            flowParams
        )

        if (
            result.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {

            onError(
                result.toUserMessage(
                    "No se pudo abrir el pago"
                )
            )
        }
    }

    // =========================================================
    // LÍMITE DE GESTOS PERSONALIZADOS
    // =========================================================

    fun canCreateCustomGesture(currentCount: Int): Boolean {

        return _isPremium.value ||
                currentCount < FREE_GESTURE_LIMIT
    }

    // =========================================================
    // CERRAR GOOGLE PLAY BILLING
    // =========================================================

    fun close() {

        _purchaseToken.value = null

        if (isClosed) {
            return
        }

        isClosed = true

        billingClient.endConnection()

        _billingReady.value = false
        productDetails = null
        _offers.value = emptyList()

        Log.d(
            TAG,
            "SubscriptionManager cerrado"
        )
    }

    // =========================================================
    // CONSULTAR PRODUCTO PREMIUM
    // =========================================================

    private fun queryProduct(
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {

        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PREMIUM_PRODUCT_ID)
                            .setProductType(
                                BillingClient.ProductType.SUBS
                            )
                            .build()
                    )
                )
                .build()

        billingClient.queryProductDetailsAsync(params) {
                billingResult, result ->

            if (isClosed) {
                return@queryProductDetailsAsync
            }

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {

                productDetails = null
                _offers.value = emptyList()

                onError(
                    billingResult.toUserMessage(
                        "No se pudo cargar el plan Premium"
                    )
                )

                /*
                 * Incluso si falla la consulta del producto,
                 * intentamos recuperar una suscripción existente.
                 */
                onComplete()

                return@queryProductDetailsAsync
            }

            val details = result.productDetailsList
                .firstOrNull { product ->

                    product.productId == PREMIUM_PRODUCT_ID
                }

            productDetails = details

            if (details == null) {

                _offers.value = emptyList()

                Log.d(
                    TAG,
                    "Premium todavía no está configurado en Play Console"
                )

                onComplete()
                return@queryProductDetailsAsync
            }

            /*
             * Mostramos solamente los planes base regulares.
             * No agregamos ofertas promocionales ni pruebas gratis.
             */
            _offers.value =
                details.subscriptionOfferDetails
                    .orEmpty()
                    .filter { offer ->

                        offer.offerId == null &&
                                (
                                        offer.basePlanId == BASE_PLAN_MONTHLY ||
                                                offer.basePlanId == BASE_PLAN_YEARLY
                                        )
                    }
                    .map { offer ->

                        val phase =
                            offer.pricingPhases.pricingPhaseList
                                .lastOrNull()

                        PremiumOffer(
                            basePlanId = offer.basePlanId,
                            formattedPrice =
                                phase?.formattedPrice.orEmpty(),
                            billingPeriod =
                                phase?.billingPeriod.orEmpty()
                        )
                    }

            Log.d(
                TAG,
                "Planes Premium encontrados: ${_offers.value.size}"
            )

            onComplete()
        }
    }

    // =========================================================
    // CONSULTAR SUSCRIPCIONES ACTIVAS
    // =========================================================

    private fun queryActiveSubscription(
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {

        if (isClosed) {
            return
        }

        if (!billingClient.isReady) {

            onError(
                "Google Play no está conectado para verificar la suscripción"
            )

            return
        }

        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(
                    BillingClient.ProductType.SUBS
                )
                .build()

        billingClient.queryPurchasesAsync(params) {
                billingResult, purchases ->

            if (isClosed) {
                return@queryPurchasesAsync
            }

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {

                onError(
                    billingResult.toUserMessage(
                        "No se pudo verificar la suscripción"
                    )
                )

                return@queryPurchasesAsync
            }

            handlePurchases(purchases)

            onResult(_isPremium.value)
        }
    }

    // =========================================================
    // PROCESAR COMPRAS
    // =========================================================

    private fun handlePurchases(
        purchases: List<Purchase>
    ) {

        val premiumPurchases = purchases.filter { purchase ->

            purchase.products.contains(PREMIUM_PRODUCT_ID)
        }

        val completedPurchases = premiumPurchases.filter { purchase ->

            purchase.purchaseState ==
                    Purchase.PurchaseState.PURCHASED
        }

        val pendingPurchases = premiumPurchases.filter { purchase ->

            purchase.purchaseState ==
                    Purchase.PurchaseState.PENDING
        }

        if (pendingPurchases.isNotEmpty()) {

            Log.d(
                TAG,
                "Hay compras Premium pendientes de confirmación"
            )
        }

        /*
         * INTEGRACIÓN PROVISIONAL PARA PRUEBAS INTERNAS:
         *
         * El estado se calcula a partir de compras devueltas
         * por Google Play.
         *
         * ANTES DE PRODUCCIÓN:
         * Verificar purchaseToken en un backend seguro usando
         * Google Play Developer API y vincularlo al usuario Firebase.
         *
         * No usar este resultado local como validación definitiva
         * para cobros reales.
         */

        val activePurchase = completedPurchases.firstOrNull()

        val active = activePurchase != null

        _purchaseToken.value = activePurchase?.purchaseToken

        setPremium(active)

        /*
         * Confirmación del lado Android para pruebas.
         *
         * Antes de producción, realizar la confirmación después
         * de verificar la compra en el backend.
         */
        completedPurchases
            .filter { purchase ->
                !purchase.isAcknowledged
            }
            .forEach { purchase ->
                acknowledge(purchase)
            }
    }

    // =========================================================
    // CONFIRMAR COMPRA
    // =========================================================

    private fun acknowledge(
        purchase: Purchase
    ) {

        val params =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

        billingClient.acknowledgePurchase(params) { billingResult ->

            if (isClosed) {
                return@acknowledgePurchase
            }

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {

                Log.d(
                    TAG,
                    "Compra Premium confirmada"
                )

            } else {

                Log.e(
                    TAG,
                    "No se pudo confirmar la compra: ${billingResult.debugMessage}"
                )
            }
        }
    }

    // =========================================================
    // ACTUALIZAR ESTADO PREMIUM
    // =========================================================

    private fun setPremium(
        active: Boolean
    ) {

        _isPremium.value = active

        Log.d(
            TAG,
            "Estado Premium: $active"
        )
    }

    // =========================================================
    // MENSAJES DE ERROR
    // =========================================================

    private fun BillingResult.toUserMessage(
        fallback: String
    ): String {

        return debugMessage.takeIf {
            it.isNotBlank()
        } ?: fallback
    }

    // =========================================================
    // MODELO DE OFERTAS
    // =========================================================

    data class PremiumOffer(
        val basePlanId: String,
        val formattedPrice: String,
        val billingPeriod: String
    )

    // =========================================================
    // CONSTANTES
    // =========================================================

    companion object {

        private const val TAG = "SubscriptionManager"

        const val PREMIUM_PRODUCT_ID = "haptivoice_premium"

        const val BASE_PLAN_MONTHLY = "monthly"
        const val BASE_PLAN_YEARLY = "yearly"

        const val FREE_GESTURE_LIMIT = 8
    }
}
