package com.toolstack.io.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.BillingRepository
import com.toolstack.io.data.billing.BillingResult
import com.toolstack.io.data.billing.PurchaseState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        billingRepository.purchaseState
            .onEach { state ->
                _uiState.update {
                    it.copy(
                        entitlement = state.toEntitlement(),
                        isPending = state is PurchaseState.Pending
                    )
                }
            }
            .launchIn(viewModelScope)

        billingRepository.products
            .onEach { products ->
                _uiState.update { current ->
                    // Monthly first, lifetime second; lifetime is preselected.
                    val ordered = products.sortedByDescending { it.isSubscription }
                    val selected = current.selectedProductId
                        ?.takeIf { id -> ordered.any { it.productId == id } }
                        ?: ordered.firstOrNull { !it.isSubscription }?.productId
                        ?: ordered.firstOrNull()?.productId
                    current.copy(
                        products = ordered,
                        selectedProductId = selected,
                        pricesState = if (ordered.isEmpty()) current.pricesState else PricesState.READY
                    )
                }
            }
            .launchIn(viewModelScope)

        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(pricesState = PricesState.LOADING) }
            when (billingRepository.queryProductDetails()) {
                is BillingResult.Error ->
                    _uiState.update {
                        if (it.products.isEmpty()) it.copy(pricesState = PricesState.ERROR) else it
                    }
                else -> Unit
            }
        }
    }

    fun onProductSelected(productId: String) {
        _uiState.update { it.copy(selectedProductId = productId) }
    }

    fun startPurchaseFlow(activity: Activity, product: BillingProduct) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseError = null) }
            when (val result = billingRepository.launchPurchaseFlow(activity, product)) {
                is BillingResult.Error ->
                    _uiState.update { it.copy(purchaseError = result.message) }
                else -> Unit
            }
        }
    }

    fun restorePurchases() {
        if (_uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }
            val querySucceeded = billingRepository.queryActivePurchases()
            if (querySucceeded) {
                val active = billingRepository.purchaseState.value.toEntitlement() != Entitlement.NONE
                _uiState.update { it.copy(isRestoring = false, restoreNotFound = !active) }
            } else {
                // Query failed (billing unavailable etc.) — leave purchaseState
                // untouched and don't claim nothing was found.
                _uiState.update { it.copy(isRestoring = false) }
            }
        }
    }

    fun onRestoreMessageShown() {
        _uiState.update { it.copy(restoreNotFound = false) }
    }

    fun clearPurchaseError() {
        _uiState.update { it.copy(purchaseError = null) }
    }
}

enum class Entitlement { NONE, SUBSCRIBER, LIFETIME }

enum class PricesState { LOADING, READY, ERROR }

data class PremiumUiState(
    val entitlement: Entitlement = Entitlement.NONE,
    val isPending: Boolean = false,
    val products: List<BillingProduct> = emptyList(),
    val selectedProductId: String? = null,
    val pricesState: PricesState = PricesState.LOADING,
    val purchaseError: String? = null,
    val isRestoring: Boolean = false,
    val restoreNotFound: Boolean = false
) {
    val isPremium: Boolean get() = entitlement != Entitlement.NONE
}

private fun PurchaseState.toEntitlement(): Entitlement = when (this) {
    is PurchaseState.ActiveSubscription -> Entitlement.SUBSCRIBER
    is PurchaseState.LifetimePurchase -> Entitlement.LIFETIME
    else -> Entitlement.NONE
}
