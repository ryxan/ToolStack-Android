package com.toolstack.io.data.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult as GoogleBillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.toolstack.io.data.api.BillingVerificationApi
import com.toolstack.io.data.api.VerifyPurchaseRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.jvm.Volatile

/**
 * Repository managing Google Play Billing for the ToolStack Pro entitlement.
 *
 * Responsibilities:
 * - BillingClient lifecycle (connect, reconnect on disconnect)
 * - Product details queries (subscription + lifetime one-time purchase)
 * - Purchase flow launch
 * - Server-side purchase verification and acknowledgment
 * - Active entitlement state via [purchaseState]
 *
 * [purchaseState] is the single source of truth for whether the user has Pro
 * access. Consumers should observe it rather than calling Google Play directly.
 *
 * Connection is serialised through [connectionMutex] so concurrent callers
 * share a single [BillingClient.startConnection] attempt rather than racing.
 */
@OptIn(DelicateCoroutinesApi::class)
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verificationApi: BillingVerificationApi
) : PurchasesUpdatedListener {

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.None)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _products = MutableStateFlow<List<BillingProduct>>(emptyList())
    val products: StateFlow<List<BillingProduct>> = _products.asStateFlow()

    @Volatile
    private var isConnected = false

    private val connectionMutex = Mutex()

    init {
        // Query active purchases as soon as the singleton is created so the
        // rest of the app sees the correct entitlement state at startup.
        GlobalScope.launch {
            try {
                queryActivePurchases()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to query active purchases on init", e)
            }
        }

        // Re-query on every app resume to pick up purchases completed on another
        // device or while the app was backgrounded.
        //
        // addObserver must run on the main thread; this @Singleton may be
        // constructed on a background thread (Hilt lazy resolution), so post
        // to the main looper to be safe.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    GlobalScope.launch {
                        try {
                            queryActivePurchases()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e(TAG, "Failed to query active purchases on resume", e)
                        }
                    }
                }
            })
        }
    }

    // -------------------------------------------------------------------------
    // PurchasesUpdatedListener
    // -------------------------------------------------------------------------

    override fun onPurchasesUpdated(
        billingResult: GoogleBillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
                _purchaseState.value = PurchaseState.Cancelled
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned, re-querying active purchases")
                GlobalScope.launch {
                    try {
                        queryActivePurchases()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Failed to re-query after ITEM_ALREADY_OWNED", e)
                    }
                }
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage} (code: ${billingResult.responseCode})")
                _purchaseState.value = PurchaseState.Error(
                    message = mapBillingErrorToUserMessage(billingResult.responseCode),
                    responseCode = billingResult.responseCode
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    /**
     * Ensure the BillingClient is connected before calling any Play Billing API.
     * Serialised with [connectionMutex] to prevent concurrent startConnection races.
     */
    private suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        if (isConnected) return@withLock true

        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: GoogleBillingResult) {
                    isConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    if (!continuation.isCompleted) continuation.resume(isConnected)
                }

                override fun onBillingServiceDisconnected() {
                    isConnected = false
                    if (!continuation.isCompleted) continuation.resume(false)
                }
            })
        }
    }

    // -------------------------------------------------------------------------
    // Product details
    // -------------------------------------------------------------------------

    /**
     * Query Play Store for both the monthly subscription and lifetime SKUs.
     * Subscriptions and in-app products must be queried separately — the API
     * rejects mixed-type queries.
     *
     * Results are published to [products] and also returned as a [BillingResult].
     */
    suspend fun queryProductDetails(): BillingResult<List<BillingProduct>> {
        if (!ensureConnected()) {
            return BillingResult.Error("Billing service unavailable", -1)
        }

        val allProducts = mutableListOf<BillingProduct>()

        // Subscription
        try {
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(ProductIds.MONTHLY_SUBSCRIPTION)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()
            val subsResult = billingClient.queryProductDetails(subsParams)
            if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsResult.productDetailsList?.mapNotNull { mapProductDetails(it) }
                    ?.let { allProducts.addAll(it) }
            } else {
                Log.e(TAG, "Subscription query failed: ${subsResult.billingResult.debugMessage}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Exception querying subscription", e)
        }

        // One-time purchase
        try {
            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(ProductIds.ONE_TIME_PURCHASE)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            val inAppResult = billingClient.queryProductDetails(inAppParams)
            if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppResult.productDetailsList?.mapNotNull { mapProductDetails(it) }
                    ?.let { allProducts.addAll(it) }
            } else {
                Log.e(TAG, "In-app query failed: ${inAppResult.billingResult.debugMessage}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Exception querying in-app product", e)
        }

        _products.value = allProducts
        Log.d(TAG, "Queried ${allProducts.size} products")

        return if (allProducts.isNotEmpty()) {
            BillingResult.Success(allProducts)
        } else {
            BillingResult.Error("No products available", -1)
        }
    }

    // -------------------------------------------------------------------------
    // Purchase flow
    // -------------------------------------------------------------------------

    /**
     * Launch the Google Play purchase UI for [product].
     *
     * Returns [BillingResult.Pending] immediately on a successful launch — the
     * real result arrives asynchronously via [onPurchasesUpdated].
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        product: BillingProduct
    ): BillingResult<Purchase> {
        if (!ensureConnected()) {
            return BillingResult.Error("Billing service unavailable", -1)
        }

        val productDetailsParamsList = if (product.isSubscription) {
            val offerToken = product.productDetails.subscriptionOfferDetails
                ?.firstOrNull()?.offerToken
                ?: return BillingResult.Error("No subscription offer available", -1)
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product.productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            val offerToken = product.offerToken
                ?: return BillingResult.Error("No offer token available for one-time purchase", -1)
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product.productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> BillingResult.Pending
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingResult.UserCancelled
            else -> {
                Log.e(TAG, "Failed to launch purchase flow: ${result.debugMessage} (code: ${result.responseCode})")
                BillingResult.Error(
                    mapBillingErrorToUserMessage(result.responseCode),
                    result.responseCode
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Active purchase query
    // -------------------------------------------------------------------------

    /**
     * Query Play Store for any active subscriptions or lifetime purchases and
     * update [purchaseState]. Safe to call at any time; callers can await it
     * rather than relying on a timer.
     *
     * Query order matters: subscription is checked first so that a user with
     * both a lifetime purchase and a lapsed subscription is not incorrectly
     * downgraded on the subscription query alone.
     */
    suspend fun queryActivePurchases() {
        if (!ensureConnected()) return

        try {
            // --- subscriptions ---
            val subsResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            if (subsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Subscription query failed with code ${subsResult.billingResult.responseCode}")
                return
            }

            val activeSubscription = subsResult.purchasesList.orEmpty().any { purchase ->
                purchase.products.contains(ProductIds.MONTHLY_SUBSCRIPTION) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (activeSubscription) {
                _purchaseState.value = PurchaseState.ActiveSubscription
                subsResult.purchasesList.orEmpty().forEach { acknowledgePurchaseIfNeeded(it) }
                return
            }

            // --- one-time purchases ---
            val inAppResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            if (inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "INAPP query failed with code ${inAppResult.billingResult.responseCode}")
                return
            }

            val lifetimePurchase = inAppResult.purchasesList.orEmpty().any { purchase ->
                purchase.products.contains(ProductIds.ONE_TIME_PURCHASE) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (lifetimePurchase) {
                _purchaseState.value = PurchaseState.LifetimePurchase
                inAppResult.purchasesList.orEmpty().forEach { acknowledgePurchaseIfNeeded(it) }
                return
            }

            // Both queries succeeded with no active entitlement
            val hasPending = (subsResult.purchasesList.orEmpty() + inAppResult.purchasesList.orEmpty())
                .any { it.purchaseState == Purchase.PurchaseState.PENDING }
            _purchaseState.value = if (hasPending) PurchaseState.Pending else PurchaseState.None

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Exception querying active purchases", e)
        }
    }

    // -------------------------------------------------------------------------
    // Purchase handling and verification
    // -------------------------------------------------------------------------

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Verification is async; launching a coroutine here is pragmatic
                // because BillingRepository is a @Singleton tied to ProcessLifecycleOwner.
                GlobalScope.launch(Dispatchers.IO) {
                    verifyAndHandlePurchase(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                _purchaseState.value = PurchaseState.Pending
                Log.d(TAG, "Purchase pending")
            }
            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Log.w(TAG, "Purchase in unspecified state")
            }
        }
    }

    private suspend fun verifyAndHandlePurchase(purchase: Purchase) {
        val isVerified = verifyPurchase(purchase)
        if (isVerified) {
            acknowledgePurchaseIfNeeded(purchase)
            when {
                purchase.products.contains(ProductIds.MONTHLY_SUBSCRIPTION) -> {
                    _purchaseState.value = PurchaseState.ActiveSubscription
                    Log.d(TAG, "Subscription activated")
                }
                purchase.products.contains(ProductIds.ONE_TIME_PURCHASE) -> {
                    _purchaseState.value = PurchaseState.LifetimePurchase
                    Log.d(TAG, "Lifetime purchase activated")
                }
            }
        } else {
            Log.e(TAG, "Purchase verification failed")
            _purchaseState.value = PurchaseState.Error(
                message = "Purchase verification failed",
                responseCode = -1
            )
        }
    }

    /**
     * Verify [purchase] with the backend server.
     *
     * The backend calls the Google Play Developer API, which is the authoritative
     * source of truth. Client-side state can be spoofed on rooted devices.
     *
     * Failure modes:
     * - HTTP 4xx/5xx → fail closed (do not grant access, do not acknowledge)
     * - Network/IO error → fail open on the client-side PURCHASED state
     * - Any other exception → fail closed
     */
    private suspend fun verifyPurchase(purchase: Purchase): Boolean {
        val isClientValid = purchase.purchaseState == Purchase.PurchaseState.PURCHASED

        return try {
            withContext(Dispatchers.IO) {
                val productId = purchase.products.firstOrNull()
                    ?: return@withContext false.also { Log.e(TAG, "Purchase has no product ID") }

                val productType = if (productId == ProductIds.MONTHLY_SUBSCRIPTION) {
                    "subscription"
                } else {
                    "inapp"
                }

                val request = VerifyPurchaseRequest(
                    packageName = context.packageName,
                    productId = productId,
                    purchaseToken = purchase.purchaseToken,
                    productType = productType
                )

                val response = verificationApi.verifyPurchase(request)
                Log.d(TAG, "Verification response: valid=${response.valid}, status=${response.status}")
                response.valid
            }
        } catch (e: retrofit2.HttpException) {
            // Backend explicitly rejected — fail closed
            Log.e(TAG, "Backend verification rejected with HTTP ${e.code()}: ${e.message()}")
            false
        } catch (e: java.net.UnknownHostException) {
            // DNS failure — backend unreachable; fall back to client-side state
            Log.w(TAG, "Backend unreachable (DNS), falling back to client-side: isClientValid=$isClientValid")
            isClientValid
        } catch (e: java.io.IOException) {
            // Network I/O failure — same fallback
            Log.w(TAG, "Backend unreachable (I/O), falling back to client-side: isClientValid=$isClientValid")
            isClientValid
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Verification exception, failing closed", e)
            false
        }
    }

    private fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged: ${purchase.products}")
                } else {
                    Log.e(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun mapProductDetails(details: ProductDetails): BillingProduct? {
        return when (details.productType) {
            BillingClient.ProductType.SUBS -> {
                val offer = details.subscriptionOfferDetails?.firstOrNull()
                val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "—"
                BillingProduct(
                    productId = details.productId,
                    title = details.title,
                    description = details.description,
                    price = price,
                    isSubscription = true,
                    productDetails = details
                )
            }
            BillingClient.ProductType.INAPP -> {
                val offerDetails = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: details.oneTimePurchaseOfferDetails
                if (offerDetails == null) {
                    Log.w(TAG, "No eligible offer for one-time purchase: ${details.productId}")
                    return null
                }
                BillingProduct(
                    productId = details.productId,
                    title = details.title,
                    description = details.description,
                    price = offerDetails.formattedPrice,
                    isSubscription = false,
                    productDetails = details,
                    offerToken = offerDetails.offerToken
                )
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "BillingRepository"

        fun mapBillingErrorToUserMessage(responseCode: Int): String = when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase cancelled"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Play Store is unavailable. Please try again later."
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing is not available on this device"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This item is not available for purchase"
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Configuration error. Please contact support."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already own this item"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "Purchase not found"
            else -> "An error occurred. Please try again."
        }
    }
}
