package com.toolstack.io.ui.ratiomix

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.data.repository.RatioMixPreset
import kotlinx.coroutines.launch

// Fixed per-part colors — index-based, so a part's input dot, bar segment and
// result row all share one color. Mid-tones stay readable on light surfaces,
// dark surfaces and the blue results card.
private val PartColors = listOf(
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
    Color(0xFFFFA726),
    Color(0xFFAB47BC),
    Color(0xFFEF5350),
    Color(0xFF26C6DA)
)

private fun partColor(index: Int) = PartColors[index % PartColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatioMixScreen(
    onBack: () -> Unit,
    onAddShortcut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: RatioMixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)

    // Save-preset dialog — rendered outside the LazyColumn so it floats above everything.
    if (uiState.showSaveDialog) {
        SavePresetDialog(
            name = uiState.saveDialogName,
            replacesExisting = uiState.savedPresets.any { it.name == uiState.saveDialogName.trim() },
            onNameChanged = viewModel::onSaveDialogNameChanged,
            onConfirm = viewModel::onSaveDialogConfirmed,
            onDismiss = viewModel::onSaveDialogDismissed
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── mode selector ─────────────────────────────────────────────────
            item {
                ModeSelector(
                    mode = uiState.mode,
                    onModeToggled = viewModel::onModeToggled
                )
            }

            // ── ratio parts card ──────────────────────────────────────────────
            item {
                MixPartsCard(
                    parts = uiState.parts,
                    ratioSummary = uiState.ratioSummary,
                    canAddPart = uiState.parts.size < RatioMixViewModel.MAX_PARTS,
                    canRemovePart = uiState.parts.size > RatioMixViewModel.MIN_PARTS,
                    onPartLabelChanged = viewModel::onPartLabelChanged,
                    onRatioChanged = viewModel::onRatioChanged,
                    onRemovePart = viewModel::onRemovePart,
                    onAddPart = viewModel::onAddPart,
                    onSavePreset = viewModel::onSavePresetClicked
                )
            }

            // ── volume card ───────────────────────────────────────────────────
            item {
                VolumeCard(
                    mode = uiState.mode,
                    parts = uiState.parts,
                    knownPartIndex = uiState.knownPartIndex,
                    volumeText = uiState.totalVolumeText,
                    volumeError = uiState.volumeError,
                    selectedUnit = uiState.selectedUnit,
                    onVolumeChanged = viewModel::onTotalVolumeChanged,
                    onUnitSelected = viewModel::onUnitSelected,
                    onKnownPartIndexChanged = viewModel::onKnownPartIndexChanged
                )
            }

            // ── results / hint ────────────────────────────────────────────────
            if (uiState.results.isNotEmpty()) {
                item {
                    ResultsCard(
                        results = uiState.results,
                        ratioSummary = uiState.ratioSummary,
                        totalResultText = uiState.totalResultText,
                        mode = uiState.mode,
                        modifier = Modifier.animateItem()
                    )
                }
            } else if (!uiState.hasError && uiState.totalVolumeText.isBlank()) {
                item { ResultsHintCard(modifier = Modifier.animateItem()) }
            }

            // ── saved presets ─────────────────────────────────────────────────
            item {
                SavedPresetsSection(
                    presets = uiState.savedPresets,
                    onLoad = { preset ->
                        viewModel.onLoadPreset(preset)
                        // Parts live at the top; scroll there so the change is visible.
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onDelete = { preset ->
                        viewModel.onDeletePreset(preset.name)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(
                                    R.string.ratio_mix_preset_deleted, preset.name
                                ),
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onRestorePreset(preset)
                            }
                        }
                    }
                )
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

// ── Save-preset dialog ────────────────────────────────────────────────────────

@Composable
private fun SavePresetDialog(
    name: String,
    replacesExisting: Boolean,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ratio_mix_save_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ratio_mix_save_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.ratio_mix_preset_name_hint)) },
                    supportingText = if (replacesExisting) {
                        { Text(text = stringResource(R.string.ratio_mix_save_replaces_existing)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank()
            ) {
                Text(text = stringResource(R.string.ratio_mix_save_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

// ── Saved presets section ─────────────────────────────────────────────────────

@Composable
private fun SavedPresetsSection(
    presets: List<RatioMixPreset>,
    onLoad: (RatioMixPreset) -> Unit,
    onDelete: (RatioMixPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.ratio_mix_saved_presets),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (presets.isEmpty()) {
            Text(
                text = stringResource(R.string.ratio_mix_no_saved_presets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            presets.forEach { preset ->
                PresetRow(
                    preset = preset,
                    onLoad = { onLoad(preset) },
                    onDelete = { onDelete(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: RatioMixPreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    // Compact summary of the parts, e.g. "Water 3 · Fertilizer 1"
    val partsSummary = preset.parts.joinToString(" · ") {
        "${it.label.ifBlank { "?" }} ${it.ratioText}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = partsSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Load button
            TextButton(onClick = onLoad) {
                Text(text = stringResource(R.string.ratio_mix_load_action))
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.ratio_mix_delete_preset, preset.name),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Mode selector ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: Mode,
    onModeToggled: () -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == Mode.TOTAL_TO_PARTS,
            onClick = { if (mode != Mode.TOTAL_TO_PARTS) onModeToggled() },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.ratio_mix_mode_total),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SegmentedButton(
            selected = mode == Mode.PART_TO_TOTAL,
            onClick = { if (mode != Mode.PART_TO_TOTAL) onModeToggled() },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.ratio_mix_mode_part),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Mix parts card ────────────────────────────────────────────────────────────

@Composable
private fun MixPartsCard(
    parts: List<RatioPart>,
    ratioSummary: String,
    canAddPart: Boolean,
    canRemovePart: Boolean,
    onPartLabelChanged: (Int, String) -> Unit,
    onRatioChanged: (Int, String) -> Unit,
    onRemovePart: (Int) -> Unit,
    onAddPart: () -> Unit,
    onSavePreset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ratio_mix_ratio_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (ratioSummary.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = ratioSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            RatioBar(parts = parts)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEachIndexed { index, part ->
                    RatioPartRow(
                        index = index,
                        part = part,
                        canRemove = canRemovePart,
                        isLast = index == parts.lastIndex,
                        onLabelChanged = { onPartLabelChanged(index, it) },
                        onRatioChanged = { onRatioChanged(index, it) },
                        onRemove = { onRemovePart(index) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (canAddPart) {
                    OutlinedButton(
                        onClick = onAddPart,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.ratio_mix_add_part))
                    }
                }
                OutlinedButton(
                    onClick = onSavePreset,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.Bookmark, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.ratio_mix_save_preset))
                }
            }
        }
    }
}

// ── Proportional ratio bar ────────────────────────────────────────────────────

@Composable
private fun RatioBar(
    parts: List<RatioPart>,
    modifier: Modifier = Modifier
) {
    val fractions = partFractions(parts)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        fractions.forEachIndexed { index, fraction ->
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "ratioSegment$index"
            )
            Box(
                modifier = Modifier
                    // weight must be > 0 — the epsilon keeps invalid parts invisible.
                    .weight(animatedFraction.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(partColor(index))
            )
        }
    }
}

/** Each part's share of the mix as a fraction; equal shares when nothing parses. */
private fun partFractions(parts: List<RatioPart>): List<Float> {
    // Mirror of RatioMixUiState.parseDecimal — kept here so the bar works even
    // before a volume produces results.
    val values = parts.map { part ->
        part.ratioText.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    }
    val sum = values.sum()
    return if (sum <= 0.0) List(parts.size) { 1f / parts.size }
    else values.map { (it / sum).toFloat() }
}

// ── Single ratio part row ─────────────────────────────────────────────────────

@Composable
private fun RatioPartRow(
    index: Int,
    part: RatioPart,
    canRemove: Boolean,
    isLast: Boolean,
    onLabelChanged: (String) -> Unit,
    onRatioChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Color key tying this row to its segment in the bar above.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(partColor(index))
        )

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
            isError = part.ratioError,
            supportingText = if (part.ratioError) {
                { Text(text = stringResource(R.string.ratio_mix_error_positive)) }
            } else null,
            label = { Text(text = stringResource(R.string.ratio_mix_ratio_value)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next
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

// ── Volume card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeCard(
    mode: Mode,
    parts: List<RatioPart>,
    knownPartIndex: Int,
    volumeText: String,
    volumeError: Boolean,
    selectedUnit: VolumeUnit,
    onVolumeChanged: (String) -> Unit,
    onUnitSelected: (VolumeUnit) -> Unit,
    onKnownPartIndexChanged: (Int) -> Unit
) {
    var unitExpanded by remember { mutableStateOf(false) }

    val fieldLabel = if (mode == Mode.TOTAL_TO_PARTS) {
        stringResource(R.string.ratio_mix_total_volume)
    } else {
        val partLabel = parts.getOrNull(knownPartIndex)?.label?.ifBlank { null }
            ?: stringResource(R.string.ratio_mix_part_fallback, knownPartIndex + 1)
        stringResource(R.string.ratio_mix_known_volume, partLabel)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.ratio_mix_volume_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (mode == Mode.PART_TO_TOTAL) {
                var knownExpanded by remember { mutableStateOf(false) }
                val knownLabel = parts.getOrNull(knownPartIndex)?.label?.ifBlank { null }
                    ?: stringResource(R.string.ratio_mix_part_fallback, knownPartIndex + 1)

                ExposedDropdownMenuBox(
                    expanded = knownExpanded,
                    onExpandedChange = { knownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(R.string.ratio_mix_known_part_prefix, knownLabel),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = knownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = knownExpanded,
                        onDismissRequest = { knownExpanded = false }
                    ) {
                        parts.forEachIndexed { index, part ->
                            DropdownMenuItem(
                                text = { Text(text = part.label.ifBlank { stringResource(R.string.ratio_mix_part_fallback, index + 1) }) },
                                onClick = {
                                    onKnownPartIndexChanged(index)
                                    knownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = onVolumeChanged,
                    singleLine = true,
                    isError = volumeError,
                    supportingText = if (volumeError) {
                        { Text(text = stringResource(R.string.ratio_mix_error_positive)) }
                    } else null,
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
                    modifier = Modifier.width(120.dp)
                ) {
                    OutlinedTextField(
                        value = selectedUnit.symbol,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.ratio_mix_unit_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                        },
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
}

// ── Results hint ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsHintCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.ratio_mix_results_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Results card ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsCard(
    results: List<RatioResult>,
    ratioSummary: String,
    totalResultText: String,
    mode: Mode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ratio_mix_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                if (ratioSummary.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.ratio_mix_ratio_summary, ratioSummary),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            results.forEachIndexed { index, result ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(partColor(index))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = result.label.ifBlank { stringResource(R.string.ratio_mix_part_fallback, index + 1) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (result.percentText.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = result.percentText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
