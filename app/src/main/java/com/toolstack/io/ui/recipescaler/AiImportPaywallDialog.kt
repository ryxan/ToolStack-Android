package com.toolstack.io.ui.recipescaler

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toolstack.io.R
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.ProductIds
import com.toolstack.io.domain.ai.AiImportConfig
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(16.dp)
private val BadgeShape = RoundedCornerShape(50)

/**
 * Paywall shown when a free user has no AI imports left — free allowance and
 * bonus credits both spent. Offers three ways back in: a consumable 100-scan
 * pack, ToolStack Pro (unlimited), or a rewarded ad for a small top-up.
 *
 * The rewarded-ad row is rendered disabled ("Soon") until a real
 * [com.toolstack.io.data.ai.RewardedAdProvider] is bound — the ads SDK isn't
 * integrated yet.
 */
@Composable
fun AiImportPaywallDialog(
    products: List<BillingProduct>,
    rewardedAdAvailable: Boolean,
    isAdLoading: Boolean,
    onDismiss: () -> Unit,
    onUpgrade: (BillingProduct) -> Unit,
    onWatchAd: () -> Unit,
    onSeeDetails: () -> Unit
) {
    val scanPack = products.firstOrNull { it.productId == ProductIds.AI_SCANS_100 }
    val subscription = products.firstOrNull { it.isSubscription }
    val lifetime = products.firstOrNull {
        !it.isSubscription && it.productId != ProductIds.AI_SCANS_100
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Header(onDismiss = onDismiss)

                Spacer(modifier = Modifier.height(20.dp))

                Reveal(delayMillis = 80) {
                    ScanPackCard(
                        product = scanPack,
                        onClick = { scanPack?.let(onUpgrade) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Reveal(delayMillis = 160) {
                    ProCard(
                        subscription = subscription,
                        lifetime = lifetime,
                        onUpgrade = onUpgrade,
                        onSeeDetails = onSeeDetails
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Reveal(delayMillis = 240) {
                    Column {
                        FreeDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        AdOptionCard(
                            available = rewardedAdAvailable,
                            loading = isAdLoading,
                            onClick = onWatchAd
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = stringResource(R.string.premium_dismiss))
                }
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun Header(onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.ai_paywall_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ai_paywall_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.premium_dismiss)
            )
        }
    }
}

// ── Options ─────────────────────────────────────────────────────────────────

/** Highlighted hero option: the 100-scan consumable pack. */
@Composable
private fun ScanPackCard(
    product: BillingProduct?,
    onClick: () -> Unit
) {
    OptionCard(
        onClick = onClick,
        enabled = product != null,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    ) {
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ai_paywall_pack_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                BestValueChip()
            }
            Text(
                text = stringResource(R.string.ai_paywall_pack_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (product == null) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = product.price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BestValueChip() {
    Surface(
        shape = BadgeShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            text = stringResource(R.string.ai_paywall_best_value),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Pro upsell: subscription + lifetime buttons inside one card. */
@Composable
private fun ProCard(
    subscription: BillingProduct?,
    lifetime: BillingProduct?,
    onUpgrade: (BillingProduct) -> Unit,
    onSeeDetails: () -> Unit
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Filled.WorkspacePremium,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_paywall_pro_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.ai_paywall_pro_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (subscription == null && lifetime == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subscription?.let { product ->
                        Button(
                            onClick = { onUpgrade(product) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.premium_product_subscription,
                                    product.price
                                )
                            )
                        }
                    }
                    lifetime?.let { product ->
                        FilledTonalButton(
                            onClick = { onUpgrade(product) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.premium_product_lifetime,
                                    product.price
                                )
                            )
                        }
                    }
                    TextButton(
                        onClick = onSeeDetails,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(text = stringResource(R.string.premium_see_details))
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = stringResource(R.string.ai_paywall_free_option),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** Rewarded-ad option: +5 scans per completed ad. Disabled until ads ship. */
@Composable
private fun AdOptionCard(
    available: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val enabled = available && !loading
    OptionCard(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.alpha(if (available) 1f else 0.55f)
    ) {
        IconBadge(
            icon = Icons.Filled.OndemandVideo,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_paywall_ad_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = pluralStringResource(
                    R.plurals.ai_paywall_ad_reward,
                    AiImportConfig.SCANS_PER_REWARDED_AD,
                    AiImportConfig.SCANS_PER_REWARDED_AD
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            !available -> Surface(
                shape = BadgeShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.premium_coming_soon),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────

@Composable
private fun OptionCard(
    onClick: () -> Unit,
    enabled: Boolean,
    border: BorderStroke,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CardShape,
        color = containerColor,
        border = border,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/** Same circular badge treatment as the Premium screen's feature icons. */
@Composable
private fun IconBadge(
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Fades and lifts content in once — same motion as the Premium screen. */
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
        label = "paywall_reveal"
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
