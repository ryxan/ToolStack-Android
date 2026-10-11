package com.toolstack.io.ui.recipescaler

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct

/**
 * Paywall dialog shown when a free user has exhausted their lifetime AI
 * recipe imports. AI import is metered independently of the premium tool
 * routes — Recipe Scaler itself stays free.
 *
 * Mirrors [com.toolstack.io.ui.shortcuts.ShortcutPaywallDialog] so the
 * upgrade experience is consistent across the app.
 */
@Composable
fun AiImportPaywallDialog(
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
                Text(text = stringResource(R.string.recipe_scaler_ai_paywall_body))
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
