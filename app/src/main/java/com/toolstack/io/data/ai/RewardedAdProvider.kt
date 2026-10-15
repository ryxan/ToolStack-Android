package com.toolstack.io.data.ai

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of rewarded ads that grant bonus AI scans on completion.
 *
 * The Google Mobile Ads SDK is not integrated yet — [NoopRewardedAdProvider]
 * is bound in production so the paywall renders the option in a "coming soon"
 * state. When AdMob lands (app ID in the manifest, AD_ID permission, UMP
 * consent), bind a real implementation that loads a [com.google.android.gms.ads.rewarded.RewardedAd]
 * behind [isAvailable] and returns the earned credits from
 * [showRewardedForScans].
 */
interface RewardedAdProvider {

    /** True while a rewarded ad is loaded and can be shown. */
    val isAvailable: Flow<Boolean>

    /**
     * Shows a rewarded ad for bonus AI scans.
     *
     * Returns the number of credits earned — 0 when the user skips the ad or
     * no ad is available. Implementations grant the credits themselves
     * (via [AiImportQuotaRepository.grantCredits]); the caller only surfaces
     * the result.
     */
    suspend fun showRewardedForScans(activity: Activity): Int
}

/** No-op provider bound until the Mobile Ads SDK is integrated. */
@Singleton
class NoopRewardedAdProvider @Inject constructor() : RewardedAdProvider {
    override val isAvailable: Flow<Boolean> = flowOf(false)
    override suspend fun showRewardedForScans(activity: Activity): Int = 0
}
