package com.toolstack.io.ui.ratiomix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatioMixScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RatioMixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ratio_mix_title),
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

            // ── mode toggle ───────────────────────────────────────────────────
            item {
                ModeToggleRow(
                    mode = uiState.mode,
                    parts = uiState.parts,
                    knownPartIndex = uiState.knownPartIndex,
                    onModeToggled = viewModel::onModeToggled,
                    onKnownPartIndexChanged = viewModel::onKnownPartIndexChanged
                )
            }

            // ── ratio parts ───────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.ratio_mix_ratio_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(uiState.parts, key = { i, _ -> i }) { index, part ->
                RatioPartRow(
                    index = index,
                    part = part,
                    canRemove = uiState.parts.size > RatioMixViewModel.MIN_PARTS,
                    onLabelChanged = { viewModel.onPartLabelChanged(index, it) },
                    onRatioChanged = { viewModel.onRatioChanged(index, it) },
                    onRemove = { viewModel.onRemovePart(index) }
                )
            }

            // Add part button
            if (uiState.parts.size < RatioMixViewModel.MAX_PARTS) {
                item {
                    Button(
                        onClick = viewModel::onAddPart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.ratio_mix_add_part))
                    }
                }
            }

            // ── volume input ──────────────────────────────────────────────────
            item {
                VolumeInputRow(
                    mode = uiState.mode,
                    parts = uiState.parts,
                    knownPartIndex = uiState.knownPartIndex,
                    volumeText = uiState.totalVolumeText,
                    selectedUnit = uiState.selectedUnit,
                    onVolumeChanged = viewModel::onTotalVolumeChanged,
                    onUnitSelected = viewModel::onUnitSelected
                )
            }

            // ── results ───────────────────────────────────────────────────────
            if (uiState.results.isNotEmpty()) {
                item {
                    ResultsCard(
                        results = uiState.results,
                        ratioSummary = uiState.ratioSummary,
                        totalResultText = uiState.totalResultText,
                        mode = uiState.mode
                    )
                }
            }

            // ── disclaimer ────────────────────────────────────────────────────
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

// ── Mode toggle ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggleRow(
    mode: Mode,
    parts: List<RatioPart>,
    knownPartIndex: Int,
    onModeToggled: () -> Unit,
    onKnownPartIndexChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == Mode.TOTAL_TO_PARTS,
                onClick = { if (mode != Mode.TOTAL_TO_PARTS) onModeToggled() },
                label = { Text(text = stringResource(R.string.ratio_mix_mode_total)) }
            )
            FilterChip(
                selected = mode == Mode.PART_TO_TOTAL,
                onClick = { if (mode != Mode.PART_TO_TOTAL) onModeToggled() },
                label = { Text(text = stringResource(R.string.ratio_mix_mode_part)) }
            )
        }

        if (mode == Mode.PART_TO_TOTAL) {
            var expanded by remember { mutableStateOf(false) }
            val knownLabel = parts.getOrNull(knownPartIndex)?.label
                ?: parts.firstOrNull()?.label ?: ""

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = stringResource(R.string.ratio_mix_known_part_prefix, knownLabel),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    parts.forEachIndexed { index, part ->
                        DropdownMenuItem(
                            text = { Text(text = part.label.ifBlank { "Part ${index + 1}" }) },
                            onClick = {
                                onKnownPartIndexChanged(index)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}

// ── Single ratio part row ─────────────────────────────────────────────────────

@Composable
private fun RatioPartRow(
    index: Int,
    part: RatioPart,
    canRemove: Boolean,
    onLabelChanged: (String) -> Unit,
    onRatioChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label field
        OutlinedTextField(
            value = part.label,
            onValueChange = onLabelChanged,
            singleLine = true,
            label = { Text(text = stringResource(R.string.ratio_mix_part_label)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.weight(1.6f)
        )

        // Ratio value field
        OutlinedTextField(
            value = part.ratioText,
            onValueChange = onRatioChanged,
            singleLine = true,
            label = { Text(text = stringResource(R.string.ratio_mix_ratio_value)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.weight(1f)
        )

        // Remove button
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.ratio_mix_remove_part),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // Placeholder so layout doesn't shift
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

// ── Volume input ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeInputRow(
    mode: Mode,
    parts: List<RatioPart>,
    knownPartIndex: Int,
    volumeText: String,
    selectedUnit: VolumeUnit,
    onVolumeChanged: (String) -> Unit,
    onUnitSelected: (VolumeUnit) -> Unit
) {
    var unitExpanded by remember { mutableStateOf(false) }

    val fieldLabel = if (mode == Mode.TOTAL_TO_PARTS) {
        stringResource(R.string.ratio_mix_total_volume)
    } else {
        val partLabel = parts.getOrNull(knownPartIndex)?.label?.ifBlank { null }
            ?: stringResource(R.string.ratio_mix_part_fallback, knownPartIndex + 1)
        stringResource(R.string.ratio_mix_known_volume, partLabel)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = volumeText,
                onValueChange = onVolumeChanged,
                singleLine = true,
                label = { Text(text = fieldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = it },
                modifier = Modifier.width(130.dp)
            ) {
                OutlinedTextField(
                    value = selectedUnit.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    VolumeUnit.entries.forEach { unit ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = unit.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = unit.symbol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                onUnitSelected(unit)
                                unitExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}

// ── Results card ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsCard(
    results: List<RatioResult>,
    ratioSummary: String,
    totalResultText: String,
    mode: Mode
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
                text = stringResource(R.string.ratio_mix_results_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (ratioSummary.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ratio_mix_ratio_summary, ratioSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            results.forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.label.ifBlank { stringResource(R.string.ratio_mix_part_fallback, results.indexOf(result) + 1) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = result.volumeText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (mode == Mode.PART_TO_TOTAL && totalResultText.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ratio_mix_total_label),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = totalResultText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
