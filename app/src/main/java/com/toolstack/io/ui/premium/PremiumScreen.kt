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
import androidx.compose.ui.semantics.contentDescription
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
                    modifier = Modifier.semantics { contentDescription = description }
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
