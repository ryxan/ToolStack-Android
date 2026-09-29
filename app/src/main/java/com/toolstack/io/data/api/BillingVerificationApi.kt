package com.toolstack.io.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Backend API for server-side purchase verification.
 *
 * The backend calls the Google Play Developer API to verify purchase tokens,
 * which is the authoritative source of truth for entitlement. Client-side
 * verification (checking purchase.purchaseState alone) can be spoofed on
 * rooted devices.
 */
interface BillingVerificationApi {

    @POST("verify-purchase")
    suspend fun verifyPurchase(
        @Body request: VerifyPurchaseRequest
    ): VerifyPurchaseResponse
}

/**
 * Request sent to the backend for purchase verification.
 *
 * @param packageName App package name (e.g. com.toolstack.io)
 * @param productId Product SKU (toolstack_pro_lifetime / toolstack_pro_monthly)
 * @param purchaseToken Opaque token from Google Play
 * @param productType "inapp" or "subscription"
 */
data class VerifyPurchaseRequest(
    @SerializedName("packageName")
    val packageName: String,
    @SerializedName("productId")
    val productId: String,
    @SerializedName("purchaseToken")
    val purchaseToken: String,
    @SerializedName("productType")
    val productType: String
)

/**
 * Response from the backend after verifying with Google Play.
 *
 * @param valid true if Google confirmed the purchase is legitimate
 * @param status Human-readable status message for logging/debugging only
 */
data class VerifyPurchaseResponse(
    @SerializedName("valid")
    val valid: Boolean,
    @SerializedName("status")
    val status: String
)
