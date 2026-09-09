package com.toolstack.io.ui.shortcuts

import android.app.Activity
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.BillingRepository
import com.toolstack.io.data.billing.BillingResult
import com.toolstack.io.data.billing.PurchaseState
import com.toolstack.io.util.ShortcutUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel that backs the "Add to Home Screen" button on every tool
 * screen.  Lives in [MainActivity] scope (via [hiltViewModel] called from the
 * activity composable) so all tool screens share the same premium/billing state
 * without each screen needing its own billing wiring.
 *
 * Responsibilities:
 * - Tracks whether the user has an active Pro entitlement.
 * - Calls [ShortcutUtil.requestPinShortcut] when the user is premium.
 * - Surfaces a paywall dialog when the user is not premium, reusing the same
 *   [BillingProduct] list and [BillingRepository.launchPurchaseFlow] already
 *   used by [HomeViewModel].
 */
@HiltViewModel
class ShortcutViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutUiState())
    val uiState: StateFlow<ShortcutUiState> = _uiState.asStateFlow()

    init {
        billingRepository.purchaseState
            .onEach { state ->
                _uiState.update { it.copy(isPremium = state.isActive) }
            }
            .launchIn(viewModelScope)

        billingRepository.products
            .onEach { products ->
                _uiState.update { it.copy(availableProducts = products) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Called when the user taps "Add to Home Screen" on a tool screen.
     *
     * - If the user is premium: delegates to [ShortcutUtil.requestPinShortcut],
     *   which triggers the system "Add to Home Screen?" dialog.
     * - If the user is not premium: sets [ShortcutUiState.showPaywall] so the
     *   caller composable can display the upgrade prompt.
     */
    fun onAddShortcutClicked(
        context: Context,
        route: String,
        label: String,
        @DrawableRes iconResId: Int
    ) {
        // TODO: remove override before release — bypasses paywall for testing
        val effectivelyPremium = true // _uiState.value.isPremium
        if (effectivelyPremium) {
            ShortcutUtil.requestPinShortcut(context, route, label, iconResId)
        } else {
            viewModelScope.launch { billingRepository.queryProductDetails() }
            _uiState.update { it.copy(showPaywall = true) }
        }
    }

    /** Launch the Google Play purchase flow for [product]. */
    fun startPurchaseFlow(activity: Activity, product: BillingProduct) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseError = null) }
            when (val result = billingRepository.launchPurchaseFlow(activity, product)) {
                is BillingResult.Error -> {
                    _uiState.update { it.copy(purchaseError = result.message) }
                }
                else -> {}
            }
        }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    fun clearPurchaseError() {
        _uiState.update { it.copy(purchaseError = null) }
    }
}

data class ShortcutUiState(
    val isPremium: Boolean = false,
    val showPaywall: Boolean = false,
    val availableProducts: List<BillingProduct> = emptyList(),
    val purchaseError: String? = null
)

/** Maps [PurchaseState] to a simple boolean. Mirrors the same extension in HomeViewModel. */
private val PurchaseState.isActive: Boolean
    get() = this is PurchaseState.ActiveSubscription || this is PurchaseState.LifetimePurchase

/**
 * Maps each tool screen route to the icon resource that will appear on the
 * launcher shortcut.  Falls back to the app launcher icon if the route is
 * unrecognised so [ShortcutUtil.requestPinShortcut] never receives an invalid
 * resource id.
 */
@DrawableRes
fun shortcutIconForRoute(route: String): Int = when (route) {
    "sae_metric"      -> R.mipmap.ic_launcher
    "wrench_fastener" -> R.mipmap.ic_launcher
    "taps_and_drills" -> R.mipmap.ic_launcher
    "bearings"        -> R.mipmap.ic_launcher
    "conduit_bends"   -> R.mipmap.ic_launcher
    "unit_converter"  -> R.mipmap.ic_launcher
    "ratio_mix"       -> R.mipmap.ic_launcher
    else              -> R.mipmap.ic_launcher
}

/**
 * Returns the string resource id for the shortcut label of a given route.
 * Used by the composable layer to resolve a [String] before calling
 * [ShortcutViewModel.onAddShortcutClicked].
 */
@StringRes
fun shortcutLabelResForRoute(route: String): Int = when (route) {
    "sae_metric"      -> R.string.shortcut_label_sae_metric
    "wrench_fastener" -> R.string.shortcut_label_wrench_fastener
    "taps_and_drills" -> R.string.shortcut_label_taps_and_drills
    "bearings"        -> R.string.shortcut_label_bearings
    "conduit_bends"   -> R.string.shortcut_label_conduit_bends
    "unit_converter"  -> R.string.shortcut_label_unit_converter
    "ratio_mix"       -> R.string.shortcut_label_ratio_mix
    else              -> R.string.app_name
}
