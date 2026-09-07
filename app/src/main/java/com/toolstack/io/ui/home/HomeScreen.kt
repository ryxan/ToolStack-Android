package com.toolstack.io.ui.home

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.ui.components.draggedItem
import com.toolstack.io.ui.components.rememberDragDropState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadProducts()
    }

    var pendingPremiumModule by remember { mutableStateOf<HomeModule?>(null) }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.moveModule(from, to)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(uiState.modules, key = { _, module -> module.route }) { index, module ->
                ModuleCard(
                    module = module,
                    isPremiumUnlocked = uiState.isPremium,
                    isDragging = dragDropState.draggingItemIndex == index,
                    modifier = Modifier.draggedItem(dragDropState, index, module.route),
                    onClick = {
                        if (module.isPremium && !uiState.isPremium) {
                            pendingPremiumModule = module
                        } else {
                            onNavigate(module.route)
                        }
                    }
                )
            }
        }
    }

    // Purchase error dialog
    uiState.purchaseError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPurchaseError() },
            title = { Text(text = stringResource(R.string.purchase_error_title)) },
            text = { Text(text = error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPurchaseError() }) {
                    Text(text = stringResource(R.string.purchase_error_dismiss))
                }
            }
        )
    }

    // Premium gate dialog
    pendingPremiumModule?.let { module ->
        PremiumGateDialog(
            moduleName = stringResource(module.titleRes),
            products = uiState.availableProducts,
            onDismiss = { pendingPremiumModule = null },
            onUpgrade = { product ->
                pendingPremiumModule = null
                val activity = context as? Activity ?: return@PremiumGateDialog
                viewModel.startPurchaseFlow(activity, product)
            }
        )
    }
}

@Composable
private fun PremiumGateDialog(
    moduleName: String,
    products: List<BillingProduct>,
    onDismiss: () -> Unit,
    onUpgrade: (BillingProduct) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.premium_required_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.premium_required_body))
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.premium_dismiss))
            }
        }
    )
}

@Composable
private fun ModuleCard(
    module: HomeModule,
    isPremiumUnlocked: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val showLock = module.isPremium && !isPremiumUnlocked

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 6.dp else 2.dp
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(module.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            leadingContent = {
                ModuleIcon(icon = module.icon.imageVector, showProBadge = showLock)
            },
            trailingContent = {
                if (showLock) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.content_description_premium_lock),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (isDragging) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.content_description_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleIcon(icon: ImageVector, showProBadge: Boolean) {
    BadgedBox(
        badge = {
            if (showProBadge) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = stringResource(R.string.premium_badge),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    ) {
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Resolves each [HomeModuleIcon] enum value to its Compose [ImageVector]. */
private val HomeModuleIcon.imageVector: ImageVector
    get() = when (this) {
        HomeModuleIcon.SaeMetric      -> Icons.Filled.Straighten
        HomeModuleIcon.WrenchFastener -> Icons.Filled.Handyman
        HomeModuleIcon.TapsAndDrills  -> Icons.Filled.Build
        HomeModuleIcon.ConduitBends   -> Icons.Filled.Construction
        HomeModuleIcon.UnitConverter  -> Icons.Filled.SwapHoriz
        HomeModuleIcon.RatioMix       -> Icons.Filled.WaterDrop
    }
