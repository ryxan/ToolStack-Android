package com.toolstack.io.data.billing

import com.android.billingclient.api.ProductDetails

/**
 * Product IDs registered in Google Play Console.
 * These must match exactly what is configured in the Play Console.
 */
object ProductIds {
    const val MONTHLY_SUBSCRIPTION = "toolstack_pro_monthly_sub"
    const val ONE_TIME_PURCHASE = "toolstack_pro_lifetime"

    /**
     * Consumable AI scan pack — grants [com.toolstack.io.domain.ai.AiImportConfig.SCANS_PER_PACK]
     * bonus credits on purchase. Consumed immediately so it can be repurchased.
     */
    const val AI_SCANS_100 = "ai_scans_100_recreated"

    /**
     * Original scan-pack ID, deactivated in Play Console but the ID stays
     * reserved. Kept so the recovery path still consumes any unconsumed
     * purchases made under it.
     */
    const val LEGACY_AI_SCANS_100 = "ai_scans_100"

    /**
     * Consumable top-up products — purchasable from contextual paywalls (e.g.
     * the AI import dialog) but never shown as Pro plans.
     */
    val CONSUMABLE_PRODUCT_IDS = setOf(AI_SCANS_100, LEGACY_AI_SCANS_100)
}

/**
 * Wrapper around Google Play [ProductDetails] with convenience accessors.
 * Keeps billing library types isolated to the data layer.
 */
data class BillingProduct(
    val productId: String,
    val title: String,
    val description: String,
    val price: String,
    val isSubscription: Boolean,
    val productDetails: ProductDetails,
    val offerToken: String? = null
)

/**
 * Result of a billing operation (query products, purchase flow, etc.).
 */
sealed class BillingResult<out T> {
    data class Success<T>(val data: T) : BillingResult<T>()
    data class Error(val message: String, val responseCode: Int) : BillingResult<Nothing>()
    data object UserCancelled : BillingResult<Nothing>()

    /** Purchase flow launched successfully; actual result arrives asynchronously via listener. */
    data object Pending : BillingResult<Nothing>()
}

/**
 * Current purchase/entitlement state for the Pro feature set.
 */
sealed class PurchaseState {
    /**
     * No active purchase or subscription found after querying Play Store.
     * Only treated as "not premium" once a real query has completed — not on
     * initial app start before any query runs.
     */
    data object None : PurchaseState()

    /** Active monthly subscription. */
    data object ActiveSubscription : PurchaseState()

    /** One-time lifetime purchase confirmed. */
    data object LifetimePurchase : PurchaseState()

    /** Purchase is pending confirmation from Play (e.g. deferred payment). */
    data object Pending : PurchaseState()

    /**
     * User explicitly cancelled the purchase flow.
     * Distinct from [None] — represents a user action, not a query result.
     * Does not clear a previously confirmed entitlement.
     */
    data object Cancelled : PurchaseState()

    /** Purchase failed or was declined. */
    data class Error(val message: String, val responseCode: Int) : PurchaseState()
}
