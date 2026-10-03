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
import androidx.core.content.edit

/**
 * Un solo plan premium (haptivoice_premium).
 * Desbloquea voces de mejor calidad, traducción ES-EN,
 * gestos personalizados ilimitados y escritura a voz.
 *
 * Los IDs deben coincidir con Play Console:
 * suscripción "haptivoice_premium", base plans "monthly" y "yearly".
 * No incluye prueba gratis.
 */
class SubscriptionManager(
    context: Context
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private val _offers = MutableStateFlow<List<PremiumOffer>>(emptyList())
    val offers: StateFlow<List<PremiumOffer>> = _offers.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var isClosed = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (isClosed) return@PurchasesUpdatedListener

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty())
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.e(TAG, "Compra fallida: ${billingResult.debugMessage}")
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isClosed) {
            onError("SubscriptionManager ya fue cerrado")
            return
        }
        if (billingClient.isReady) {
            refresh(onReady, onError)
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingReady.value = true
                    refresh(onReady, onError)
                } else {
                    _billingReady.value = false
                    onError(billingResult.toUserMessage("No se pudo conectar con Google Play"))
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingReady.value = false
            }
        })
    }

    fun refresh(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!billingClient.isReady) {
            start(onReady, onError)
            return
        }

        queryProduct(onError)
        queryActiveSubscription(
            onResult = {
                onReady()
            },
            onError = onError
        )
    }

    fun purchase(
        activity: Activity,
        basePlanId: String = BASE_PLAN_MONTHLY,
        onError: (String) -> Unit
    ) {
        if (!billingClient.isReady) {
            onError("Google Play todavía no está listo")
            return
        }

        val details = productDetails
        if (details == null) {
            onError("No se encontró el plan premium. Revísalo en Play Console.")
            return
        }

        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId }
            ?: details.subscriptionOfferDetails?.firstOrNull()

        if (offer == null) {
            onError("No hay una oferta activa para este plan")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onError(result.toUserMessage("No se pudo abrir el pago"))
        }
    }

    fun canCreateCustomGesture(currentCount: Int): Boolean {
        return _isPremium.value || currentCount < FREE_GESTURE_LIMIT
    }

    fun close() {
        isClosed = true
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
        _billingReady.value = false
    }

    private fun queryProduct(onError: (String) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onError(billingResult.toUserMessage("No se pudo cargar el plan premium"))
                return@queryProductDetailsAsync
            }

            val details = result.productDetailsList.firstOrNull()
            productDetails = details
            _offers.value = details?.subscriptionOfferDetails.orEmpty().map { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                PremiumOffer(
                    basePlanId = offer.basePlanId,
                    formattedPrice = phase?.formattedPrice.orEmpty(),
                    billingPeriod = phase?.billingPeriod.orEmpty()
                )
            }
        }
    }

    private fun queryActiveSubscription(
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onError(billingResult.toUserMessage("No se pudo verificar la suscripción"))
                onResult(_isPremium.value)
                return@queryPurchasesAsync
            }

            handlePurchases(purchases)
            onResult(_isPremium.value)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val premiumPurchases = purchases.filter { purchase ->
            purchase.products.contains(PREMIUM_PRODUCT_ID)
        }

        premiumPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { acknowledge(it) }

        val active = premiumPurchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        setPremium(active)
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "No se pudo confirmar la compra: ${billingResult.debugMessage}")
            }
        }
    }

    private fun setPremium(active: Boolean) {
        _isPremium.value = active
        prefs.edit { putBoolean(KEY_IS_PREMIUM, active) }
    }

    private fun BillingResult.toUserMessage(fallback: String): String {
        return debugMessage.takeIf { it.isNotBlank() } ?: fallback
    }

    data class PremiumOffer(
        val basePlanId: String,
        val formattedPrice: String,
        val billingPeriod: String
    )

    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "haptivoice_subscription"
        private const val KEY_IS_PREMIUM = "is_premium"

        const val PREMIUM_PRODUCT_ID = "haptivoice_premium"
        const val BASE_PLAN_MONTHLY = "monthly"
        const val BASE_PLAN_YEARLY = "yearly"
        const val FREE_GESTURE_LIMIT = 3
    }
}