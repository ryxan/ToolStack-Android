New files

ui/premium/PremiumViewModel.kt

kotlin
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
            billingRepository.queryActivePurchases()
            val active = billingRepository.purchaseState.value.toEntitlement() != Entitlement.NONE
            _uiState.update { it.copy(isRestoring = false, restoreNotFound = !active) }
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

ui/premium/PremiumScreen.kt

kotlin
package com.toolstack.io.ui.premium

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.ProductIds
import kotlinx.coroutines.delay

private val PremiumCardShape = RoundedCornerShape(16.dp)
private val PremiumButtonShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val restoreNotFoundMessage = stringResource(R.string.premium_restore_not_found)

    LaunchedEffect(uiState.restoreNotFound) {
        if (uiState.restoreNotFound) {
            snackbarHostState.showSnackbar(restoreNotFoundMessage)
            viewModel.onRestoreMessageShown()
        }
    }

    uiState.purchaseError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearPurchaseError,
            title = { Text(text = stringResource(R.string.purchase_error_title)) },
            text = { Text(text = error) },
            confirmButton = {
                TextButton(onClick = viewModel::clearPurchaseError) {
                    Text(text = stringResource(R.string.purchase_error_dismiss))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.premium_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                item {
                    Reveal(delayMillis = 0) {
                        if (uiState.isPremium) {
                            ThankYouCard(
                                entitlement = uiState.entitlement,
                                onManageSubscription = { openSubscriptionManagement(context) }
                            )
                        } else {
                            Hero()
                        }
                    }
                }

                item { Reveal(delayMillis = 80) { HighlightsCard() } }

                item { Reveal(delayMillis = 160) { ComparisonCard() } }

                if (!uiState.isPremium) {
                    item {
                        PricingSection(
                            uiState = uiState,
                            onSelect = viewModel::onProductSelected,
                            onRetry = viewModel::loadProducts,
                            onRestore = viewModel::restorePurchases,
                            onPurchase = {
                                val product = uiState.products
                                    .firstOrNull { it.productId == uiState.selectedProductId }
                                val activity = context as? Activity
                                if (product != null && activity != null) {
                                    viewModel.startPurchaseFlow(activity, product)
                                }
                            }
                        )
                    }
                    item { TrustSection() }
                }

                item { FaqSection() }
            }
        }
    }
}

// ── Motion ────────────────────────────────────────────────────────────────────

/** Fades and lifts content in once. Layout size is stable, so nothing jumps. */
@Composable
private fun Reveal(delayMillis: Int, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(delayMillis.toLong())
            visible = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "premium_reveal"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        }
    ) {
        content()
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
private fun Hero() {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.premium_eyebrow),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.premium_headline),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.premium_subhead),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThankYouCard(
    entitlement: Entitlement,
    onManageSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PremiumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.premium_pro_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(
                    if (entitlement == Entitlement.SUBSCRIBER) {
                        R.string.premium_pro_body_subscription
                    } else {
                        R.string.premium_pro_body_lifetime
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entitlement == Entitlement.SUBSCRIBER) {
                OutlinedButton(
                    onClick = onManageSubscription,
                    shape = PremiumButtonShape
                ) {
                    Text(text = stringResource(R.string.premium_manage_subscription))
                }
            }
        }
    }
}

// ── Highlights ────────────────────────────────────────────────────────────────

@Composable
private fun HighlightsCard() {
    SectionCard(title = stringResource(R.string.premium_highlights_title)) {
        HighlightRow(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_conduit_bender),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = stringResource(R.string.premium_feature_conduit_title),
            body = stringResource(R.string.premium_feature_conduit_body)
        )
        HighlightDivider()
        HighlightRow(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = stringResource(R.string.premium_feature_shortcuts_title),
            body = stringResource(R.string.premium_feature_shortcuts_body)
        )
        HighlightDivider()
        HighlightRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = stringResource(R.string.premium_feature_ad_free_title),
            body = stringResource(R.string.premium_feature_ad_free_body)
        )
        HighlightDivider()
        HighlightRow(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_chef_hat),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = stringResource(R.string.premium_feature_ai_title),
            body = stringResource(R.string.premium_feature_ai_body),
            soon = true
        )
    }
}

@Composable
private fun HighlightRow(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    soon: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(content = icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (soon) {
                    Text(
                        text = stringResource(R.string.premium_coming_soon),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HighlightDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Same circular badge treatment as the Home screen's module icons. */
@Composable
private fun IconBadge(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

// ── Comparison ────────────────────────────────────────────────────────────────

private enum class Availability { INCLUDED, NOT_INCLUDED, SOON }

private data class ComparisonRow(
    @StringRes val label: Int,
    val free: Availability,
    val premium: Availability
)

private val comparisonRows = listOf(
    ComparisonRow(R.string.premium_compare_tools, Availability.INCLUDED, Availability.INCLUDED),
    ComparisonRow(R.string.premium_compare_recipes, Availability.INCLUDED, Availability.INCLUDED),
    ComparisonRow(R.string.premium_compare_conduit, Availability.NOT_INCLUDED, Availability.INCLUDED),
    ComparisonRow(R.string.premium_compare_shortcuts, Availability.NOT_INCLUDED, Availability.INCLUDED),
    ComparisonRow(R.string.premium_compare_ad_free, Availability.NOT_INCLUDED, Availability.INCLUDED),
    ComparisonRow(R.string.premium_compare_ai, Availability.NOT_INCLUDED, Availability.SOON)
)

@Composable
private fun ComparisonCard() {
    SectionCard(title = stringResource(R.string.premium_compare_title)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.premium_compare_free),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = stringResource(R.string.premium_compare_premium),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
        }
        comparisonRows.forEach { row ->
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(row.label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                AvailabilityCell(row.free, Modifier.width(64.dp))
                AvailabilityCell(row.premium, Modifier.width(72.dp))
            }
        }
    }
}

@Composable
private fun AvailabilityCell(availability: Availability, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (availability) {
            Availability.INCLUDED -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.premium_included),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Availability.NOT_INCLUDED -> {
                val description = stringResource(R.string.premium_not_included)
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.semantics { contentDescriptionCompat(description) }
                )
            }
            Availability.SOON -> Text(
                text = stringResource(R.string.premium_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.contentDescriptionCompat(value: String) {
    this.contentDescription = value
}

// ── Pricing ───────────────────────────────────────────────────────────────────

@Composable
private fun PricingSection(
    uiState: PremiumUiState,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onPurchase: () -> Unit
) {
    val selectedProduct = uiState.products.firstOrNull { it.productId == uiState.selectedProductId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.premium_plans_title),
            style = MaterialTheme.typography.titleLarge
        )

        when (uiState.pricesState) {
            PricesState.READY -> {
                uiState.products.forEach { product ->
                    PlanCard(
                        product = product,
                        selected = product.productId == uiState.selectedProductId,
                        onClick = { onSelect(product.productId) }
                    )
                }
            }
            PricesState.LOADING -> PlansPlaceholder {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            PricesState.ERROR -> PlansPlaceholder {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.premium_prices_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = onRetry, shape = PremiumButtonShape) {
                        Text(text = stringResource(R.string.premium_retry))
                    }
                }
            }
        }

        if (uiState.isPending) {
            Text(
                text = stringResource(R.string.premium_pending_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onPurchase,
            enabled = selectedProduct != null,
            shape = PremiumButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = stringResource(R.string.premium_cta),
                style = MaterialTheme.typography.titleMedium
            )
        }

        selectedProduct?.let { product ->
            Text(
                text = stringResource(
                    if (product.isSubscription) {
                        R.string.premium_legal_subscription
                    } else {
                        R.string.premium_legal_lifetime
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(
            onClick = onRestore,
            enabled = !uiState.isRestoring,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (uiState.isRestoring) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.premium_restore))
        }
    }
}

@Composable
private fun PlansPlaceholder(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PremiumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun PlanCard(
    product: BillingProduct,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "plan_border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        label = "plan_border_width"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "plan_container_color"
    )

    Card(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        shape = PremiumCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        if (product.isSubscription) R.string.premium_plan_monthly
                        else R.string.premium_plan_lifetime
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        if (product.isSubscription) R.string.premium_plan_monthly_desc
                        else R.string.premium_plan_lifetime_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
                Text(
                    text = stringResource(
                        if (product.isSubscription) R.string.premium_plan_monthly_period
                        else R.string.premium_plan_lifetime_period
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Trust ─────────────────────────────────────────────────────────────────────

@Composable
private fun TrustSection() {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TrustRow(Icons.Filled.Lock, stringResource(R.string.premium_trust_secure))
        TrustRow(Icons.Filled.Check, stringResource(R.string.premium_trust_cancel))
        TrustRow(Icons.Filled.Sync, stringResource(R.string.premium_trust_restore))
    }
}

@Composable
private fun TrustRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── FAQ ───────────────────────────────────────────────────────────────────────

private val faqItems = listOf(
    R.string.premium_faq_cancel_q to R.string.premium_faq_cancel_a,
    R.string.premium_faq_lifetime_q to R.string.premium_faq_lifetime_a,
    R.string.premium_faq_devices_q to R.string.premium_faq_devices_a,
    R.string.premium_faq_data_q to R.string.premium_faq_data_a,
    R.string.premium_faq_refund_q to R.string.premium_faq_refund_a,
    R.string.premium_faq_next_q to R.string.premium_faq_next_a
)

@Composable
private fun FaqSection() {
    SectionCard(title = stringResource(R.string.premium_faq_title)) {
        faqItems.forEachIndexed { index, (question, answer) ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            FaqItem(question = stringResource(question), answer = stringResource(answer))
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by rememberSaveable(question) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "faq_chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ── Shared ────────────────────────────────────────────────────────────────────

/** Section header above a bordered, flat card (Recipe Scaler's emphasized-card style). */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = PremiumCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

private fun openSubscriptionManagement(context: Context) {
    val uri = Uri.parse(
        "https://play.google.com/store/account/subscriptions" +
            "?sku=${ProductIds.MONTHLY_SUBSCRIPTION}&package=${context.packageName}"
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No browser or Play Store available; nothing sensible to fall back to.
    }
}

Two compile notes on the file above:

PricingSection uses Modifier.align inside a Column. That works because it's inside the Column scope.
The contentDescriptionCompat helper is a convenience. If you'd rather, swap it for contentDescription = description with the androidx.compose.ui.semantics.contentDescription import.

ui/shortcuts/ShortcutPaywallDialog.kt (full file, now with a details link)

kotlin
package com.toolstack.io.ui.shortcuts

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct

/**
 * Paywall dialog shown when a non-premium user taps "Add to Home Screen".
 *
 * Reuses the same visual pattern as [HomeScreen]'s PremiumGateDialog so the
 * upgrade experience is consistent across the app.
 */
@Composable
fun ShortcutPaywallDialog(
    products: List<BillingProduct>,
    onDismiss: () -> Unit,
    onUpgrade: (BillingProduct) -> Unit,
    onSeeDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.premium_required_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.shortcut_premium_required_body))
                if (products.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    products.forEach { product ->
                        TextButton(
                            onClick = { onUpgrade(product) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (product.isSubscription) {
                                    stringResource(
                                        R.string.premium_product_subscription,
                                        product.price
                                    )
                                } else {
                                    stringResource(
                                        R.string.premium_product_lifetime,
                                        product.price
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSeeDetails) {
                Text(text = stringResource(R.string.premium_see_details))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.premium_dismiss))
            }
        }
    )
}

/**
 * Convenience composable that observes [uiState] and renders both the paywall
 * dialog and purchase-error dialog when needed.
 *
 * [shortcutViewModel] must be the same instance used to call
 * [ShortcutViewModel.onAddShortcutClicked] — scoped to [MainActivity] and shared
 * across all screens.
 */
@Composable
fun ShortcutPaywallHost(
    uiState: ShortcutUiState,
    shortcutViewModel: ShortcutViewModel,
    onSeeDetails: () -> Unit
) {
    val context = LocalContext.current

    if (uiState.showPaywall) {
        ShortcutPaywallDialog(
            products = uiState.availableProducts,
            onDismiss = shortcutViewModel::dismissPaywall,
            onUpgrade = { product ->
                shortcutViewModel.dismissPaywall()
                val activity = context as? Activity ?: return@ShortcutPaywallDialog
                shortcutViewModel.startPurchaseFlow(activity, product)
            },
            onSeeDetails = {
                shortcutViewModel.dismissPaywall()
                onSeeDetails()
            }
        )
    }

    uiState.purchaseError?.let { error ->
        AlertDialog(
            onDismissRequest = shortcutViewModel::clearPurchaseError,
            title = { Text(text = stringResource(R.string.purchase_error_title)) },
            text = { Text(text = error) },
            confirmButton = {
                TextButton(onClick = shortcutViewModel::clearPurchaseError) {
                    Text(text = stringResource(R.string.purchase_error_dismiss))
                }
            }
        )
    }
}

res/values/strings.xml (add)

xml
<!-- Premium page -->
<string name="premium_title">Premium</string>
<string name="content_description_premium">Premium</string>
<string name="premium_see_details">See what\'s included</string>
<string name="premium_eyebrow">TOOLSTACK PREMIUM</string>
<string name="premium_headline">The full toolkit, without the noise.</string>
<string name="premium_subhead">Unlock the specialist tools, go ad-free, and put any tool on your home screen.</string>

<string name="premium_highlights_title">What Premium adds</string>
<string name="premium_feature_conduit_title">Conduit Bends</string>
<string name="premium_feature_conduit_body">Bend marks, offsets and step-by-step instructions for EMT conduit.</string>
<string name="premium_feature_shortcuts_title">Home screen shortcuts</string>
<string name="premium_feature_shortcuts_body">Pin any tool to your home screen and open it in one tap.</string>
<string name="premium_feature_ad_free_title">Ad-free</string>
<string name="premium_feature_ad_free_body">Use every tool without ads, now and as the app grows.</string>
<string name="premium_feature_ai_title">AI Recipe Importer</string>
<string name="premium_feature_ai_body">Import recipes into the Recipe Scaler with AI.</string>
<string name="premium_coming_soon">Soon</string>

<string name="premium_compare_title">Free and Premium</string>
<string name="premium_compare_free">Free</string>
<string name="premium_compare_premium">Premium</string>
<string name="premium_compare_tools">Reference tables and calculators</string>
<string name="premium_compare_recipes">Recipe Scaler, saved recipes and scanning</string>
<string name="premium_compare_conduit">Conduit Bends</string>
<string name="premium_compare_shortcuts">Home screen shortcuts</string>
<string name="premium_compare_ad_free">Ad-free experience</string>
<string name="premium_compare_ai">AI Recipe Importer</string>
<string name="premium_included">Included</string>
<string name="premium_not_included">Not included</string>

<string name="premium_plans_title">Choose your plan</string>
<string name="premium_plan_monthly">Monthly</string>
<string name="premium_plan_monthly_desc">Cancel anytime in Google Play</string>
<string name="premium_plan_monthly_period">per month</string>
<string name="premium_plan_lifetime">Lifetime</string>
<string name="premium_plan_lifetime_desc">Pay once, keep it for good</string>
<string name="premium_plan_lifetime_period">one-time</string>
<string name="premium_cta">Unlock Premium</string>
<string name="premium_legal_subscription">Billed monthly to your Google Play account until you cancel. Manage or cancel anytime in Google Play.</string>
<string name="premium_legal_lifetime">A single payment charged to your Google Play account. No renewals.</string>
<string name="premium_pending_note">Your purchase is pending approval from Google Play. Premium unlocks as soon as it completes.</string>
<string name="premium_prices_error">We couldn\'t load prices from Google Play. Check your connection and try again.</string>
<string name="premium_retry">Try again</string>
<string name="premium_restore">Restore purchases</string>
<string name="premium_restore_not_found">No active purchase found. Check you\'re signed in to the Google account you bought with.</string>

<string name="premium_trust_secure">Secure checkout through Google Play</string>
<string name="premium_trust_cancel">Cancel a subscription anytime</string>
<string name="premium_trust_restore">Your purchase follows your Google account</string>

<string name="premium_faq_title">Questions</string>
<string name="premium_faq_cancel_q">What happens if I cancel?</string>
<string name="premium_faq_cancel_a">You keep Premium until the end of the period you\'ve paid for. After that, Premium tools lock again. Your saved data stays on your device.</string>
<string name="premium_faq_lifetime_q">Is Lifetime really a single payment?</string>
<string name="premium_faq_lifetime_a">Yes. Lifetime is a one-time purchase tied to your Google account, with no renewals.</string>
<string name="premium_faq_devices_q">Will Premium work on my other devices?</string>
<string name="premium_faq_devices_a">Premium follows your Google account. Install ToolStack on another Android device signed in with the same account and it unlocks automatically, or tap Restore purchases.</string>
<string name="premium_faq_data_q">Do I lose my recipes and presets if I don\'t subscribe?</string>
<string name="premium_faq_data_a">No. They\'re stored on your device and aren\'t affected by your plan.</string>
<string name="premium_faq_refund_q">How do refunds work?</string>
<string name="premium_faq_refund_a">Purchases are handled by Google Play, so refund requests go through Google Play\'s standard refund process.</string>
<string name="premium_faq_next_q">What\'s coming next?</string>
<string name="premium_faq_next_a">An AI recipe importer for the Recipe Scaler is in development, with more tools to follow.</string>

<string name="premium_pro_title">Thank you for your support</string>
<string name="premium_pro_body_subscription">Your Premium subscription is active. Every Premium feature is unlocked.</string>
<string name="premium_pro_body_lifetime">Your lifetime purchase is active. Every Premium feature is unlocked, for good.</string>
<string name="premium_manage_subscription">Manage subscription</string>

If any of these names already exist in your strings.xml, the build will flag a duplicate. I've only used names I couldn't see in your code.

Edits to existing files

I'm giving exact replacements for these because each change is only a few lines. I can paste the full files if you'd rather.

MainActivity.kt

kotlin
// import
import com.toolstack.io.ui.premium.PremiumScreen

// ShortcutPaywallHost call
ShortcutPaywallHost(
    uiState = shortcutUiState,
    shortcutViewModel = shortcutViewModel,
    onSeeDetails = {
        navController.navigate(Screen.Premium.route) { launchSingleTop = true }
    }
)

// HomeScreen composable
composable(Screen.Home.route) {
    HomeScreen(
        onNavigate = { route ->
            if (navController.graph.findNode(route) != null) {
                navController.navigate(route)
            }
        },
        onOpenPremium = {
            navController.navigate(Screen.Premium.route) { launchSingleTop = true }
        }
    )
}

// new destination, next to the others
composable(Screen.Premium.route) {
    PremiumScreen(onBack = { navController.popBackStack() })
}

// Screen sealed class
data object Premium : Screen("premium")

HomeViewModel.kt

kotlin
HomeModule("conduit_bends",   R.string.conduit_bends,         HomeModuleIcon.ConduitBends, isPremium = true),

HomeScreen.kt

kotlin
// imports
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.IconButton

// signature
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenPremium: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
)

// top bar actions: add before the FilledTonalIconButton
IconButton(onClick = onOpenPremium) {
    Icon(
        imageVector = Icons.Filled.WorkspacePremium,
        contentDescription = stringResource(R.string.content_description_premium)
    )
}

// call site
PremiumGateDialog(
    moduleName = stringResource(module.titleRes),
    products = uiState.availableProducts,
    onDismiss = { pendingPremiumModule = null },
    onSeeDetails = {
        pendingPremiumModule = null
        onOpenPremium()
    },
    onUpgrade = { product -> /* unchanged */ }
)

// PremiumGateDialog: add the parameter and replace `confirmButton = {}`
onSeeDetails: () -> Unit,
...
confirmButton = {
    TextButton(onClick = onSeeDetails) {
        Text(text = stringResource(R.string.premium_see_details))
    }
},

ShortcutViewModel.kt: this removes the bypass.

kotlin
fun onAddShortcutClicked(...) {
    if (_uiState.value.isPremium) {
        ShortcutUtil.requestPinShortcut(context, route, label, iconResId)
    } else {
        viewModelScope.launch { billingRepository.queryProductDetails() }
        _uiState.update { it.copy(showPaywall = true) }
    }
}
How it fits in
Navigation: Screen.Premium("premium") is registered in the existing NavHost and uses popBackStack() for back. It is not in the deep-link allowlist, so it can't be pinned as a shortcut. There are three entry points: a Home top-bar icon, "See what's included" in the module gate dialog, and the same link in the shortcut paywall.
Entitlement: there's no account system. PremiumViewModel reads BillingRepository.purchaseState and products, and purchases go through launchPurchaseFlow, the same as HomeViewModel and ShortcutViewModel.
Pro state: shows a thank-you card instead of the hero, hides pricing and trust, and shows "Manage subscription" (a Play deep link) for subscribers only. Lifetime owners just get the thank-you.
Restore: it calls queryActivePurchases() and shows a snackbar if nothing is found. Billing already re-queries on app resume.
Style: it's built from existing patterns only: M3 components, the Recipe Scaler's flat bordered cards with 16dp corners, 12dp buttons, and the Home screen's circular icon badges. Prose uses bodyLarge, and prices use monospace to match the app's utility feel.
Known gaps
The conduit gate is only at the Home card. A deep-link shortcut, or a shortcut pinned before a subscription lapsed, still opens conduit_bends directly. A route-level check in MainActivity would close that, but billing state starts as None on a cold launch, so it needs a "billing ready" signal to avoid bouncing real Pro users. Want me to add that?
A cancelled purchase overwrites a Pro user's state. In BillingRepository, USER_CANCELED sets PurchaseState.Cancelled, which would briefly look non-Pro even though the doc comment says it doesn't clear entitlement. It's an existing issue, not caused by this page, but worth fixing.
Trial or intro pricing isn't shown, as agreed. BillingProduct would need a small extension when you want it.

how can i share this with my code agent?

Figuring out how to hand the work to a code agent.

The quickest way is to hand the agent a task brief plus the code, since it already has your repo.

Option 1: paste into the agent (fastest)

Copy the two new files (PremiumViewModel.kt, PremiumScreen.kt) and the new ShortcutPaywallDialog.kt into the repo.
Paste the strings block into strings.xml.
Give the agent this prompt, with the edit snippets from my last message below it:
Implement a Premium page in ToolStack. The new files are already added:
ui/premium/PremiumViewModel.kt, ui/premium/PremiumScreen.kt,
ui/shortcuts/ShortcutPaywallDialog.kt (replaced), plus new strings in strings.xml.

Apply these remaining edits:
1. MainActivity: add Screen.Premium("premium"), register composable(Screen.Premium.route),
   pass onSeeDetails to ShortcutPaywallHost, pass onOpenPremium to HomeScreen.
2. HomeScreen: add onOpenPremium param, a WorkspacePremium top-bar IconButton,
   and onSeeDetails in PremiumGateDialog (confirmButton).
3. HomeViewModel: set conduit_bends isPremium = true.
4. ShortcutViewModel: remove the effectivelyPremium = true bypass, use _uiState.value.isPremium.

Then build, fix compile errors (duplicate string names, icon imports), and verify
the page in light and dark themes. Do not change the design system.

Option 2: have me generate a file
I can bundle everything into one PREMIUM_IMPLEMENTATION.md (all code, all edits, the prompt). You then drop it in the repo root and tell the agent "implement PREMIUM_IMPLEMENTATION.md". If you want it, say so and I'll produce it as a download.

Option 3: patch file
Once the agent has applied everything, it can run git diff > premium.patch. That's useful for review.

If your agent is Claude Code, Option 2 plus one line in CLAUDE.md pointing at the file works well.