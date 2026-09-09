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
import androidx.compose.material3.MaterialTheme
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
    onUpgrade: (BillingProduct) -> Unit
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.premium_dismiss))
            }
        }
    )
}

/**
 * Convenience composable that observes [uiState] and renders both the paywall
 * dialog and purchase-error dialog when needed.  Drop this into any tool screen
 * composable alongside the "Add to Home Screen" button.
 *
 * [shortcutViewModel] must be the same instance used to call
 * [ShortcutViewModel.onAddShortcutClicked] — typically obtained via
 * `hiltViewModel<ShortcutViewModel>(LocalActivity.current as ViewModelStoreOwner)`
 * so it is scoped to [MainActivity] and shared across all screens.
 */
@Composable
fun ShortcutPaywallHost(
    uiState: ShortcutUiState,
    shortcutViewModel: ShortcutViewModel
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
