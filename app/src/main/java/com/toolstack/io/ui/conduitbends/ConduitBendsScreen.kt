package com.toolstack.io.ui.conduitbends

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.ConduitBendCalculator
import com.toolstack.io.domain.model.BendResult
import com.toolstack.io.domain.model.BendType
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Public entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConduitBendsScreen(
    onBack: () -> Unit,
    onAddShortcut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ConduitBendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Intercept the system/toolbar back press at each wizard step
    val handleBack: () -> Unit = when (uiState.step) {
        BendStep.SELECTION -> onBack
        BendStep.INPUT     -> viewModel::onBackFromInput
        BendStep.RESULT    -> viewModel::onBackFromResult
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.step) {
                            BendStep.SELECTION -> stringResource(R.string.conduit_bends)
                            BendStep.INPUT     -> uiState.selectedBend?.displayName
                                ?: stringResource(R.string.conduit_bends)
                            BendStep.RESULT    -> stringResource(R.string.conduit_bend_results)
                        },
                        style    = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                            tint               = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    if (uiState.step == BendStep.SELECTION && onAddShortcut != null) {
                        IconButton(onClick = onAddShortcut) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.AddToHomeScreen,
                                contentDescription = stringResource(R.string.content_description_add_shortcut),
                                tint               = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    titleContentColor      = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "bend_step_transition"
        ) { step ->
            when (step) {
                BendStep.SELECTION -> SelectionStep(
                    onBendSelected = viewModel::onBendSelected,
                    modifier       = Modifier.padding(padding)
                )
                BendStep.INPUT -> InputStep(
                    uiState    = uiState,
                    onValueChange       = viewModel::onInputValueChanged,
                    onConduitSizeChange = viewModel::onConduitSizeChanged,
                    onAngleChange       = viewModel::onOffsetAngleChanged,
                    onCalculate         = viewModel::onCalculate,
                    modifier            = Modifier.padding(padding)
                )
                BendStep.RESULT -> ResultStep(
                    uiState          = uiState,
                    onTabChange      = viewModel::onResultTabChanged,
                    modifier         = Modifier.padding(padding)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Selection
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionStep(
    onBendSelected: (BendType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns            = GridCells.Fixed(2),
        contentPadding     = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        modifier           = modifier.fillMaxSize()
    ) {
        items(BendType.entries, key = { it.name }) { bend ->
            BendTypeCard(
                bend     = bend,
                onClick  = { if (bend.isImplemented) onBendSelected(bend) }
            )
        }
    }
}

@Composable
private fun BendTypeCard(
    bend: BendType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (bend.isImplemented) 1f else 0.45f

    Card(
        onClick   = onClick,
        enabled   = bend.isImplemented,
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Diagram thumbnail
            Box(
                modifier            = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                contentAlignment    = Alignment.Center
            ) {
                BendDiagram(
                    bendType = bend,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text      = bend.displayName,
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines  = 2
            )
            Text(
                text      = if (bend.isImplemented) bend.subtitle
                             else stringResource(R.string.conduit_bend_coming_soon),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines  = 2
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Input
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputStep(
    uiState: ConduitBendsUiState,
    onValueChange: (String) -> Unit,
    onConduitSizeChange: (ConduitSize) -> Unit,
    onAngleChange: (OffsetAngle) -> Unit,
    onCalculate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bend = uiState.selectedBend ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Diagram preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                BendDiagram(
                    bendType     = bend,
                    showLabels   = true,
                    inputInches  = uiState.inputValue.toDoubleOrNull(),
                    modifier     = Modifier.fillMaxSize()
                )
            }
        }

        // Conduit size
        SectionLabel(text = stringResource(R.string.conduit_bend_conduit_size))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            ConduitSize.entries.forEach { size ->
                FilterChip(
                    selected  = uiState.conduitSize == size,
                    onClick   = { onConduitSizeChange(size) },
                    label     = { Text(size.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Offset angle (only for offset bend)
        if (bend == BendType.OFFSET) {
            SectionLabel(text = stringResource(R.string.conduit_bend_offset_angle))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                OffsetAngle.entries.forEach { angle ->
                    FilterChip(
                        selected  = uiState.offsetAngle == angle,
                        onClick   = { onAngleChange(angle) },
                        label     = {
                            Text(
                                "${angle.degrees}°",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        // Measurement input
        val measurementLabel = when (bend) {
            BendType.CORNER_90    -> stringResource(R.string.conduit_bend_input_corner_distance)
            BendType.STUB_UP_90   -> stringResource(R.string.conduit_bend_input_stub_length)
            BendType.OFFSET       -> stringResource(R.string.conduit_bend_input_obstruction_height)
            BendType.SADDLE_3_POINT,
            BendType.SADDLE_4_POINT -> stringResource(R.string.conduit_bend_input_obstruction_height)
            BendType.BACK_TO_BACK -> stringResource(R.string.conduit_bend_input_back_distance)
        }

        SectionLabel(text = measurementLabel)

        OutlinedTextField(
            value         = uiState.inputValue,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text(stringResource(R.string.conduit_bend_input_placeholder)) },
            suffix        = { Text(stringResource(R.string.conduit_bend_inches_suffix)) },
            isError       = uiState.inputError != null,
            supportingText = uiState.inputError?.let { err ->
                { Text(text = err, color = MaterialTheme.colorScheme.error) }
            },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape         = MaterialTheme.shapes.medium
        )

        Button(
            onClick  = onCalculate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text  = stringResource(R.string.conduit_bend_calculate),
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Takeoff reference for this size
        TakeOffReferenceCard(conduitSize = uiState.conduitSize)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TakeOffReferenceCard(conduitSize: ConduitSize) {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text  = stringResource(R.string.conduit_bend_takoff_reference_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            ConduitSize.entries.forEach { size ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = size.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (size == conduitSize) FontWeight.Bold else FontWeight.Normal,
                        color = if (size == conduitSize)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = ConduitBendCalculator.formatInches(size.takeOff90Inches),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (size == conduitSize) FontWeight.Bold else FontWeight.Normal,
                        color = if (size == conduitSize)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Result
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultStep(
    uiState: ConduitBendsUiState,
    onTabChange: (ResultTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.result ?: return
    val bend   = uiState.selectedBend ?: return

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = uiState.resultTab.ordinal) {
            Tab(
                selected = uiState.resultTab == ResultTab.QUICK,
                onClick  = { onTabChange(ResultTab.QUICK) },
                text     = { Text(stringResource(R.string.conduit_bend_tab_quick)) }
            )
            Tab(
                selected = uiState.resultTab == ResultTab.DETAILED,
                onClick  = { onTabChange(ResultTab.DETAILED) },
                text     = { Text(stringResource(R.string.conduit_bend_tab_detailed)) }
            )
        }

        AnimatedContent(
            targetState  = uiState.resultTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label        = "result_tab"
        ) { tab ->
            when (tab) {
                ResultTab.QUICK    -> QuickResultContent(
                    result   = result,
                    bend     = bend,
                    uiState  = uiState,
                    modifier = Modifier.fillMaxSize()
                )
                ResultTab.DETAILED -> DetailedResultContent(
                    result   = result,
                    bend     = bend,
                    uiState  = uiState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── Quick tab ─────────────────────────────────────────────────────────────────

@Composable
private fun QuickResultContent(
    result: BendResult,
    bend: BendType,
    uiState: ConduitBendsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Diagram with dimension labels
        Card(
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                BendDiagram(
                    bendType    = bend,
                    showLabels  = true,
                    result      = result,
                    modifier    = Modifier.fillMaxSize()
                )
            }
        }

        // Key values grid
        when (result) {
            is BendResult.Corner90 -> Corner90QuickValues(result)
            is BendResult.StubUp90 -> StubUp90QuickValues(result)
            is BendResult.Offset   -> OffsetQuickValues(result)
            is BendResult.Saddle3Point -> Saddle3QuickValues(result)
            is BendResult.Saddle4Point -> Saddle4QuickValues(result)
            is BendResult.BackToBack   -> BackToBackQuickValues(result)
        }

        // Take-off reference — tucked away so beginners aren't confused
        val takeOffInches = when (result) {
            is BendResult.Corner90 -> result.takeOffInches
            is BendResult.StubUp90 -> result.takeOffInches
            else                   -> null
        }
        if (takeOffInches != null) {
            TakeOffExplainerCard(
                conduitSize  = uiState.conduitSize,
                takeOff      = takeOffInches
            )
        }

        DisclaimerCard()
    }
}

@Composable
private fun Corner90QuickValues(result: BendResult.Corner90) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_mark_at),
            value = ConduitBendCalculator.formatInches(result.markLocationInches),
            hint  = stringResource(R.string.conduit_bend_hint_mark_corner),
            highlight = true
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_arrow_placement),
            value = stringResource(R.string.conduit_bend_result_corner_arrow_tip),
            hint  = stringResource(R.string.conduit_bend_hint_arrow_corner)
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_bend_to),
            value = "90°",
            hint  = stringResource(R.string.conduit_bend_hint_bend_to_90)
        )
    }
}

@Composable
private fun StubUp90QuickValues(result: BendResult.StubUp90) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_mark_at),
            value = ConduitBendCalculator.formatInches(result.markLocationInches),
            hint  = stringResource(R.string.conduit_bend_hint_mark_stub),
            highlight = true
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_arrow_placement),
            value = stringResource(R.string.conduit_bend_result_arrow_on_mark),
            hint  = stringResource(R.string.conduit_bend_hint_arrow_stub)
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_bend_to),
            value = "90°",
            hint  = stringResource(R.string.conduit_bend_hint_bend_to_90)
        )
    }
}

@Composable
private fun OffsetQuickValues(result: BendResult.Offset) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_bend_angle),
            value = "${result.angleUsed.degrees}°"
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_multiplier),
            value = String.format(Locale.US, "%.3f", result.angleUsed.multiplier)
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_bend_spacing),
            value = ConduitBendCalculator.formatInches(result.bendSpacingInches),
            highlight = true
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_shrinkage),
            value = ConduitBendCalculator.formatInches(result.shrinkageInches)
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_mark_1),
            value = stringResource(R.string.conduit_bend_result_reference_end)
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_mark_2),
            value = ConduitBendCalculator.formatInches(result.bendSpacingInches),
            highlight = true
        )
    }
}

@Composable
private fun Saddle3QuickValues(result: BendResult.Saddle3Point) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_center_bend),
            value = "45°"
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_outer_bends),
            value = "22½°"
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_outer_spread),
            value = ConduitBendCalculator.formatInches(result.outerMarkOffsetInches),
            highlight = true
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_shrinkage),
            value = ConduitBendCalculator.formatInches(result.shrinkageInches)
        )
    }
}

@Composable
private fun Saddle4QuickValues(result: BendResult.Saddle4Point) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_all_bends),
            value = "30°"
        )
        ResultDivider()
        ResultRow(label = "Mark 1", value = ConduitBendCalculator.formatInches(result.mark1Inches))
        ResultDivider()
        ResultRow(label = "Mark 2", value = ConduitBendCalculator.formatInches(result.mark2Inches), highlight = true)
        ResultDivider()
        ResultRow(label = "Mark 3", value = ConduitBendCalculator.formatInches(result.mark3Inches), highlight = true)
        ResultDivider()
        ResultRow(label = "Mark 4", value = ConduitBendCalculator.formatInches(result.mark4Inches))
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_shrinkage),
            value = ConduitBendCalculator.formatInches(result.shrinkageInches)
        )
    }
}

@Composable
private fun BackToBackQuickValues(result: BendResult.BackToBack) {
    ResultCard(title = stringResource(R.string.conduit_bend_key_values)) {
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_first_mark),
            value = ConduitBendCalculator.formatInches(result.firstMarkInches),
            highlight = true
        )
        ResultDivider()
        ResultRow(
            label = stringResource(R.string.conduit_bend_result_second_mark),
            value = ConduitBendCalculator.formatInches(result.secondMarkInches),
            highlight = true
        )
    }
}

// ── Detailed tab ──────────────────────────────────────────────────────────────

@Composable
private fun DetailedResultContent(
    result: BendResult,
    bend: BendType,
    uiState: ConduitBendsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (result) {
            is BendResult.Corner90 -> Corner90DetailedSteps(result, uiState)
            is BendResult.StubUp90 -> StubUp90DetailedSteps(result, uiState)
            is BendResult.Offset   -> OffsetDetailedSteps(result, uiState)
            is BendResult.Saddle3Point -> GenericStubMessage(bend.displayName)
            is BendResult.Saddle4Point -> GenericStubMessage(bend.displayName)
            is BendResult.BackToBack   -> GenericStubMessage(bend.displayName)
        }
        DisclaimerCard()
    }
}

@Composable
private fun Corner90DetailedSteps(result: BendResult.Corner90, uiState: ConduitBendsUiState) {
    val markStr = ConduitBendCalculator.formatInches(result.markLocationInches)
    val takeOff = ConduitBendCalculator.formatInches(result.takeOffInches)
    val size    = uiState.conduitSize.label

    StepCard(stepNumber = 1, title = stringResource(R.string.conduit_bend_step_measure_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_measure, markStr, takeOff, size),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 2, title = stringResource(R.string.conduit_bend_step_mark_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_mark, markStr),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            PipeWithMark(markFraction = 0.4f, labelText = markStr)
        }
    }
    StepCard(stepNumber = 3, title = stringResource(R.string.conduit_bend_step_place_bender_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_place),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 4, title = stringResource(R.string.conduit_bend_step_bend_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_bend),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 5, title = stringResource(R.string.conduit_bend_step_verify_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_verify),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StubUp90DetailedSteps(result: BendResult.StubUp90, uiState: ConduitBendsUiState) {
    val markStr   = ConduitBendCalculator.formatInches(result.markLocationInches)
    val takeOff   = ConduitBendCalculator.formatInches(result.takeOffInches)
    val size      = uiState.conduitSize.label

    StepCard(stepNumber = 1, title = stringResource(R.string.conduit_bend_step_measure_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_90_measure, markStr, takeOff, size),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 2, title = stringResource(R.string.conduit_bend_step_mark_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_90_mark, markStr),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            PipeWithMark(
                markFraction = 0.35f,
                labelText    = markStr
            )
        }
    }
    StepCard(stepNumber = 3, title = stringResource(R.string.conduit_bend_step_place_bender_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_90_place),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 4, title = stringResource(R.string.conduit_bend_step_bend_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_90_bend),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 5, title = stringResource(R.string.conduit_bend_step_verify_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_90_verify),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OffsetDetailedSteps(result: BendResult.Offset, uiState: ConduitBendsUiState) {
    val spacingStr = ConduitBendCalculator.formatInches(result.bendSpacingInches)
    val shrinkStr  = ConduitBendCalculator.formatInches(result.shrinkageInches)
    val angle      = result.angleUsed.degrees

    StepCard(stepNumber = 1, title = stringResource(R.string.conduit_bend_step_measure_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_offset_measure, angle),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 2, title = stringResource(R.string.conduit_bend_step_mark_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_offset_mark, spacingStr),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            PipeWithTwoMarks(
                mark1Fraction = 0.25f,
                mark2Fraction = 0.65f,
                label1        = "Mark 1",
                label2        = "Mark 2 (+$spacingStr)"
            )
        }
    }
    StepCard(stepNumber = 3, title = stringResource(R.string.conduit_bend_step_first_bend_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_offset_first_bend, angle),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 4, title = stringResource(R.string.conduit_bend_step_second_bend_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_offset_second_bend, angle),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StepCard(stepNumber = 5, title = stringResource(R.string.conduit_bend_step_shrinkage_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_offset_shrinkage, shrinkStr),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun GenericStubMessage(bendName: String) {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text      = "Detailed steps for $bendName coming soon.",
            style     = MaterialTheme.typography.bodyMedium,
            modifier  = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared result UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    hint: String? = null,
    highlight: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text       = value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color      = if (highlight) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.End,
                softWrap   = true
            )
        }
        if (hint != null) {
            Text(
                text     = hint,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ResultDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment  = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(28.dp)
                        .background(
                            color  = MaterialTheme.colorScheme.primary,
                            shape  = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "$stepNumber",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TakeOffExplainerCard(conduitSize: ConduitSize, takeOff: Double) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick  = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = stringResource(R.string.conduit_bend_takeoff_explainer_title),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text  = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(
                        R.string.conduit_bend_takeoff_explainer_body,
                        ConduitBendCalculator.formatInches(takeOff),
                        conduitSize.label
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.conduit_bend_takoff_reference_title),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                ConduitSize.entries.forEach { size ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text  = size.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (size == conduitSize) FontWeight.Bold else FontWeight.Normal,
                            color = if (size == conduitSize)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = ConduitBendCalculator.formatInches(size.takeOff90Inches),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (size == conduitSize) FontWeight.Bold else FontWeight.Normal,
                            color = if (size == conduitSize)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text     = stringResource(R.string.disclaimer),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas diagram composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dispatches to the correct schematic drawing for each bend type.
 * [showLabels] — show dimension annotations.
 * [inputInches] — if provided, the user's raw measurement is live-annotated.
 * [result] — if provided, calculated values are shown on the diagram.
 */
@Composable
private fun BendDiagram(
    bendType: BendType,
    showLabels: Boolean  = false,
    inputInches: Double? = null,
    result: BendResult?  = null,
    modifier: Modifier   = Modifier
) {
    val pipeColor    = MaterialTheme.colorScheme.primary
    val accentColor  = MaterialTheme.colorScheme.error
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant
    val strokeWidth  = 6f

    Canvas(modifier = modifier) {
        when (bendType) {
            BendType.CORNER_90     -> drawCorner90(pipeColor, accentColor, strokeWidth, showLabels,
                                          result as? BendResult.Corner90)
            BendType.STUB_UP_90    -> drawStubUp90(pipeColor, accentColor, strokeWidth, showLabels,
                                          result as? BendResult.StubUp90)
            BendType.OFFSET        -> drawOffset(pipeColor, accentColor, strokeWidth, showLabels,
                                          result as? BendResult.Offset)
            BendType.SADDLE_3_POINT -> drawSaddle3Point(pipeColor, accentColor, strokeWidth)
            BendType.SADDLE_4_POINT -> drawSaddle4Point(pipeColor, accentColor, strokeWidth)
            BendType.BACK_TO_BACK  -> drawBackToBack(pipeColor, accentColor, strokeWidth)
        }
    }
}

// ── 90° Corner ────────────────────────────────────────────────────────────────

/**
 * Flat L-shaped corner — two straight runs meeting at a right angle,
 * with a small radius arc at the knee. No elevation change.
 */
private fun DrawScope.drawCorner90(
    pipeColor: Color,
    accentColor: Color,
    sw: Float,
    showLabels: Boolean,
    result: BendResult.Corner90?
) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2

    // Long horizontal run coming from the left
    val kneeX = w * 0.55f
    val kneeY = h * 0.55f

    drawLine(
        color       = pipeColor,
        start       = Offset(pad, kneeY),
        end         = Offset(kneeX, kneeY),
        strokeWidth = sw,
        cap         = StrokeCap.Round
    )

    // Short arc at the corner knee
    val radius = h * 0.18f
    val kneePath = Path().apply {
        moveTo(kneeX, kneeY)
        cubicTo(
            kneeX + radius * 0.8f, kneeY,
            kneeX + radius,        kneeY - radius * 0.8f,
            kneeX + radius,        kneeY - radius
        )
    }
    drawPath(
        path  = kneePath,
        color = pipeColor,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )

    // Short vertical run going up
    drawLine(
        color       = pipeColor,
        start       = Offset(kneeX + radius, kneeY - radius),
        end         = Offset(kneeX + radius, pad),
        strokeWidth = sw,
        cap         = StrokeCap.Round
    )

    // Mark tick on the horizontal run
    if (showLabels) {
        val markX = kneeX * 0.45f
        drawArrowTick(markX, kneeY, accentColor, sw)
    }
}

// ── 90° Stub-Up ───────────────────────────────────────────────────────────────

private fun DrawScope.drawStubUp90(
    pipeColor: Color,
    accentColor: Color,
    sw: Float,
    showLabels: Boolean,
    result: BendResult.StubUp90?
) {
    val w = size.width
    val h = size.height
    val pad = sw * 2

    // Horizontal run along the bottom (left side)
    val runEndX   = w * 0.45f
    val floorY    = h - pad - sw

    // Vertical stub going up (right side)
    val stubStartX = runEndX
    val stubTopY   = pad + sw

    // Horizontal segment
    drawLine(
        color       = pipeColor,
        start       = Offset(pad, floorY),
        end         = Offset(runEndX, floorY),
        strokeWidth = sw,
        cap         = StrokeCap.Round
    )

    // Curved knee — simple arc approximation using a quadratic bezier path
    val bendRadius = h * 0.22f
    val kneePath = Path().apply {
        moveTo(runEndX, floorY)
        cubicTo(
            runEndX + bendRadius * 0.5f, floorY,
            stubStartX + bendRadius, floorY - bendRadius,
            stubStartX + bendRadius, floorY - bendRadius * 1.5f
        )
    }
    drawPath(
        path        = kneePath,
        color       = pipeColor,
        style       = Stroke(width = sw, cap = StrokeCap.Round)
    )

    // Vertical stub
    drawLine(
        color       = pipeColor,
        start       = Offset(stubStartX + bendRadius, floorY - bendRadius * 1.5f),
        end         = Offset(stubStartX + bendRadius, stubTopY),
        strokeWidth = sw,
        cap         = StrokeCap.Round
    )

    // Mark arrow on the horizontal run
    if (showLabels) {
        val markX = runEndX * 0.42f
        drawArrowTick(markX, floorY, accentColor, sw)
    }
}

// ── Offset ────────────────────────────────────────────────────────────────────

private fun DrawScope.drawOffset(
    pipeColor: Color,
    accentColor: Color,
    sw: Float,
    showLabels: Boolean,
    result: BendResult.Offset?
) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2

    val leftY  = h - pad - sw           // low run
    val rightY = pad + sw               // high run (over the obstacle)
    val riseX1 = w * 0.32f             // start of first bend
    val riseX2 = w * 0.65f             // start of second bend

    // Left horizontal run
    drawLine(pipeColor, Offset(pad, leftY), Offset(riseX1, leftY), sw, cap = StrokeCap.Round)

    // Diagonal crossing section
    drawLine(pipeColor, Offset(riseX1, leftY), Offset(riseX2, rightY), sw, cap = StrokeCap.Round)

    // Right horizontal run
    drawLine(pipeColor, Offset(riseX2, rightY), Offset(w - pad, rightY), sw, cap = StrokeCap.Round)

    // Obstacle box
    val obstacleLeft  = riseX1 + (riseX2 - riseX1) * 0.15f
    val obstacleRight = riseX1 + (riseX2 - riseX1) * 0.85f
    val obstacleTop   = leftY - (leftY - rightY) * 0.7f
    drawLine(
        color       = accentColor.copy(alpha = 0.4f),
        start       = Offset(obstacleLeft, leftY),
        end         = Offset(obstacleLeft, obstacleTop),
        strokeWidth = sw * 0.7f
    )
    drawLine(
        color       = accentColor.copy(alpha = 0.4f),
        start       = Offset(obstacleRight, leftY),
        end         = Offset(obstacleRight, obstacleTop),
        strokeWidth = sw * 0.7f
    )
    drawLine(
        color       = accentColor.copy(alpha = 0.4f),
        start       = Offset(obstacleLeft, obstacleTop),
        end         = Offset(obstacleRight, obstacleTop),
        strokeWidth = sw * 0.7f
    )

    // Mark ticks
    if (showLabels) {
        drawArrowTick(riseX1, leftY, accentColor, sw)
        drawArrowTick(riseX2, rightY, accentColor, sw)
    }
}

// ── 3-Point Saddle ────────────────────────────────────────────────────────────

private fun DrawScope.drawSaddle3Point(pipeColor: Color, accentColor: Color, sw: Float) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2

    val baseY    = h * 0.70f
    val peakY    = h * 0.20f
    val x1       = w * 0.15f
    val x2       = w * 0.32f
    val x3       = w * 0.50f   // centre / peak
    val x4       = w * 0.68f
    val x5       = w * 0.85f

    drawLine(pipeColor, Offset(pad, baseY), Offset(x1, baseY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x1, baseY), Offset(x3, peakY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x3, peakY), Offset(x5, baseY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x5, baseY), Offset(w - pad, baseY), sw, cap = StrokeCap.Round)

    // Centre tick
    drawArrowTick(x3, peakY, accentColor, sw)
}

// ── 4-Point Saddle ────────────────────────────────────────────────────────────

private fun DrawScope.drawSaddle4Point(pipeColor: Color, accentColor: Color, sw: Float) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2

    val baseY  = h * 0.72f
    val topY   = h * 0.22f
    val x1     = w * 0.15f
    val x2     = w * 0.35f
    val x3     = w * 0.65f
    val x4     = w * 0.85f

    drawLine(pipeColor, Offset(pad, baseY), Offset(x1, baseY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x1, baseY), Offset(x2, topY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x2, topY), Offset(x3, topY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x3, topY), Offset(x4, baseY), sw, cap = StrokeCap.Round)
    drawLine(pipeColor, Offset(x4, baseY), Offset(w - pad, baseY), sw, cap = StrokeCap.Round)
}

// ── Back-to-Back ──────────────────────────────────────────────────────────────

private fun DrawScope.drawBackToBack(pipeColor: Color, accentColor: Color, sw: Float) {
    val w    = size.width
    val h    = size.height
    val pad  = sw * 2
    val midX = w * 0.50f

    val bottomY = h - pad - sw
    val topY    = pad + sw

    // Left vertical stub (up)
    drawLine(pipeColor, Offset(w * 0.18f, bottomY), Offset(w * 0.18f, topY), sw, cap = StrokeCap.Round)
    // Horizontal connecting run
    drawLine(pipeColor, Offset(w * 0.18f, topY), Offset(w * 0.82f, topY), sw, cap = StrokeCap.Round)
    // Right vertical stub (down)
    drawLine(pipeColor, Offset(w * 0.82f, topY), Offset(w * 0.82f, bottomY), sw, cap = StrokeCap.Round)

    // Heel marks
    drawArrowTick(w * 0.18f, topY, accentColor, sw)
    drawArrowTick(w * 0.82f, topY, accentColor, sw)
}

// ── Shared drawing helpers ────────────────────────────────────────────────────

/** Clean perpendicular tick mark indicating a measurement mark on the pipe. */
private fun DrawScope.drawArrowTick(x: Float, y: Float, color: Color, sw: Float) {
    val len = sw * 3f
    drawLine(
        color       = color,
        start       = Offset(x, y - len),
        end         = Offset(x, y + len),
        strokeWidth = sw * 0.9f,
        cap         = StrokeCap.Round
    )
}

// ── Inline pipe diagrams for step cards ──────────────────────────────────────

@Composable
private fun PipeWithMark(markFraction: Float, labelText: String) {
    val pipeColor   = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2f
        drawLine(pipeColor, Offset(0f, midY), Offset(size.width, midY), 6f, cap = StrokeCap.Round)
        val markX = size.width * markFraction
        drawLine(accentColor, Offset(markX, midY - 16f), Offset(markX, midY + 16f), 4f, cap = StrokeCap.Round)
    }
}

@Composable
private fun PipeWithTwoMarks(
    mark1Fraction: Float,
    mark2Fraction: Float,
    label1: String,
    label2: String
) {
    val pipeColor   = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2f
        drawLine(pipeColor, Offset(0f, midY), Offset(size.width, midY), 6f, cap = StrokeCap.Round)
        listOf(mark1Fraction, mark2Fraction).forEach { frac ->
            val x = size.width * frac
            drawLine(accentColor, Offset(x, midY - 16f), Offset(x, midY + 16f), 4f, cap = StrokeCap.Round)
        }
    }
}
