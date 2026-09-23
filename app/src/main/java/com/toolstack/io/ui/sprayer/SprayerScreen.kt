package com.toolstack.io.ui.sprayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SprayerScreen(
    onBack: () -> Unit,
    onAddShortcut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SprayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sprayer_title),
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
                actions = {
                    if (onAddShortcut != null) {
                        IconButton(onClick = onAddShortcut) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                                contentDescription = stringResource(R.string.content_description_add_shortcut)
                            )
                        }
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
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Tank volume input ─────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.tankVolumeText,
                    onValueChange = viewModel::onTankVolumeChanged,
                    label = { Text(text = stringResource(R.string.sprayer_tank_volume)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Spray rate input ──────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.sprayRateText,
                    onValueChange = viewModel::onSprayRateChanged,
                    label = { Text(text = stringResource(R.string.sprayer_spray_rate)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Mode toggle ───────────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.mode == SprayerMode.RATE_PER_ACRE,
                        onClick = { if (uiState.mode != SprayerMode.RATE_PER_ACRE) viewModel.onModeToggled() },
                        label = { Text(text = stringResource(R.string.sprayer_mode_rate_per_acre)) }
                    )
                    FilterChip(
                        selected = uiState.mode == SprayerMode.ACRES_PER_JUG,
                        onClick = { if (uiState.mode != SprayerMode.ACRES_PER_JUG) viewModel.onModeToggled() },
                        label = { Text(text = stringResource(R.string.sprayer_mode_acres_per_jug)) }
                    )
                }
            }

            // ── Mode-specific input ───────────────────────────────────────────
            when (uiState.mode) {
                SprayerMode.RATE_PER_ACRE -> {
                    item {
                        OutlinedTextField(
                            value = uiState.chemRateText,
                            onValueChange = viewModel::onChemRateChanged,
                            label = { Text(text = stringResource(R.string.sprayer_chem_rate)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                SprayerMode.ACRES_PER_JUG -> {
                    item {
                        OutlinedTextField(
                            value = uiState.acresPerJugText,
                            onValueChange = viewModel::onAcresPerJugChanged,
                            label = { Text(text = stringResource(R.string.sprayer_acres_per_jug)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Results ───────────────────────────────────────────────────────
            if (uiState.totalAcresText.isNotEmpty()) {
                item {
                    ResultsCard(
                        mode = uiState.mode,
                        totalAcresText = uiState.totalAcresText,
                        totalChemLitersText = uiState.totalChemLitersText,
                        totalChemGallonsText = uiState.totalChemGallonsText,
                        totalJugsText = uiState.totalJugsText
                    )
                }
            }

            // ── Disclaimer ────────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResultsCard(
    mode: SprayerMode,
    totalAcresText: String,
    totalChemLitersText: String,
    totalChemGallonsText: String,
    totalJugsText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sprayer_results_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            // Total acres covered (shown in both modes)
            ResultRow(
                label = stringResource(R.string.sprayer_acres_covered),
                value = totalAcresText
            )

            when (mode) {
                SprayerMode.RATE_PER_ACRE -> {
                    if (totalChemLitersText.isNotEmpty()) {
                        ResultRow(
                            label = stringResource(R.string.sprayer_total_chem_liters),
                            value = totalChemLitersText
                        )
                    }
                    if (totalChemGallonsText.isNotEmpty()) {
                        ResultRow(
                            label = stringResource(R.string.sprayer_total_chem_gallons),
                            value = totalChemGallonsText
                        )
                    }
                }
                SprayerMode.ACRES_PER_JUG -> {
                    if (totalJugsText.isNotEmpty()) {
                        ResultRow(
                            label = stringResource(R.string.sprayer_total_jugs),
                            value = totalJugsText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
