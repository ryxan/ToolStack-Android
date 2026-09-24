package com.toolstack.io.ui.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.BillingRepository
import com.toolstack.io.data.billing.BillingResult
import com.toolstack.io.data.billing.PurchaseState
import com.toolstack.io.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Set to true by [moveModule] the moment the user performs their first reorder.
     * The async preference load in [init] checks this flag before applying the saved
     * order, so a reorder that races with the initial read is never overwritten.
     */
    @Volatile private var userHasMutated = false

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

        // Load the persisted module order once, then apply it to the default list.
        // Skip the update if the user already reordered before the read completed.
        viewModelScope.launch {
            val savedOrder = preferencesRepository.homeModuleOrder.first()
            if (!userHasMutated) {
                _uiState.update { it.copy(modules = applyOrder(DEFAULT_MODULES, savedOrder)) }
            }
        }
    }

    /**
     * Load Play Store product details so prices are available when the upgrade
     * dialog is shown. Safe to call multiple times.
     */
    fun loadProducts() {
        viewModelScope.launch {
            billingRepository.queryProductDetails()
        }
    }

    /**
     * Move the item at [from] to [to] in the module list, then persist the new
     * order. Called on every live drag step so the list animates in real time.
     */
    fun moveModule(from: Int, to: Int) {
        userHasMutated = true
        val current = _uiState.value.modules.toMutableList()
        current.add(to, current.removeAt(from))
        _uiState.update { it.copy(modules = current) }
        viewModelScope.launch {
            preferencesRepository.saveHomeModuleOrder(current.map { it.route })
        }
    }

    fun startPurchaseFlow(activity: Activity, product: BillingProduct) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseError = null) }
            when (val result = billingRepository.launchPurchaseFlow(activity, product)) {
                is BillingResult.Pending -> {}
                is BillingResult.Error -> {
                    _uiState.update { it.copy(purchaseError = result.message) }
                }
                is BillingResult.UserCancelled -> {}
                is BillingResult.Success -> {}
            }
        }
    }

    fun clearPurchaseError() {
        _uiState.update { it.copy(purchaseError = null) }
    }

    companion object {
        /**
         * All implemented modules in their default order. This is the source of truth
         * for which modules exist; [UserPreferencesRepository.homeModuleOrder] only
         * stores a permutation of these routes.
         */
        val DEFAULT_MODULES: List<HomeModule> = listOf(
            HomeModule("sae_metric",      R.string.sae_to_metric,        HomeModuleIcon.SaeMetric),
            HomeModule("wrench_fastener", R.string.wrench_fastener,       HomeModuleIcon.WrenchFastener),
            HomeModule("taps_and_drills", R.string.taps_and_drills,       HomeModuleIcon.TapsAndDrills),
            HomeModule("conduit_bends",   R.string.conduit_bends,         HomeModuleIcon.ConduitBends),
            HomeModule("unit_converter",  R.string.unit_converter,        HomeModuleIcon.UnitConverter),
            HomeModule("ratio_mix",       R.string.ratio_mix_title,       HomeModuleIcon.RatioMix),
            HomeModule("calculator",      R.string.calculator_title,      HomeModuleIcon.Calculator),
            HomeModule("sprayer",         R.string.sprayer_title,         HomeModuleIcon.Sprayer)
        )

        /**
         * Reorder [defaults] according to [savedRoutes]. Any route in [savedRoutes]
         * that is no longer in [defaults] is silently dropped; any new module added
         * to [defaults] that is not in [savedRoutes] is appended at the end.
         */
        fun applyOrder(defaults: List<HomeModule>, savedRoutes: List<String>): List<HomeModule> {
            if (savedRoutes.isEmpty()) return defaults
            val byRoute = defaults.associateBy { it.route }
            val ordered = savedRoutes.mapNotNull { byRoute[it] }
            val missing = defaults.filter { it.route !in savedRoutes }
            return ordered + missing
        }
    }
}

data class HomeUiState(
    val isPremium: Boolean = false,
    val availableProducts: List<BillingProduct> = emptyList(),
    val purchaseError: String? = null,
    /** Currently-ordered list of home screen modules. */
    val modules: List<HomeModule> = HomeViewModel.DEFAULT_MODULES
)

/**
 * Lightweight data class representing a single home screen module card.
 * Replaces the private sealed class that used to live inside [HomeScreen].
 */
data class HomeModule(
    val route: String,
    val titleRes: Int,
    val icon: HomeModuleIcon,
    val isPremium: Boolean = false
)

/**
 * Enum carrying the icon identity for each module.
 * Decoupled from [ImageVector] so the ViewModel stays Android-framework-free
 * (ImageVector is a Compose/UI type). The screen resolves it back to an
 * ImageVector via [HomeModuleIcon.imageVector].
 */
enum class HomeModuleIcon {
    SaeMetric,
    WrenchFastener,
    TapsAndDrills,
    ConduitBends,
    UnitConverter,
    RatioMix,
    Calculator,
    Sprayer
}

/** Maps [PurchaseState] to a simple boolean for the UI gate. */
private val PurchaseState.isActive: Boolean
    get() = this is PurchaseState.ActiveSubscription || this is PurchaseState.LifetimePurchase
