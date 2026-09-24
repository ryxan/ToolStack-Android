package com.toolstack.io.ui.conduitbends

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
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

    // Handle system/gesture Back so it follows the same wizard transitions
    // as the toolbar button. Only enabled when inside the wizard (INPUT or
    // RESULT) — on SELECTION the default navigation Back is correct.
    BackHandler(enabled = uiState.step != BendStep.SELECTION) {
        handleBack()
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
        items(BendType.entries.filter { it.isVisible }, key = { it.name }) { bend ->
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
    val markStr     = ConduitBendCalculator.formatInches(result.markLocationInches)
    val takeOff     = ConduitBendCalculator.formatInches(result.takeOffInches)
    val size        = uiState.conduitSize.label
    val cornerStr   = ConduitBendCalculator.formatInches(result.distanceToCornerInches)

    StepCard(stepNumber = 1, title = stringResource(R.string.conduit_bend_step_measure_title)) {
        Text(
            text  = stringResource(R.string.conduit_bend_step_corner_measure, markStr, takeOff, size, cornerStr),
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
 * [showLabels] adds measurement ticks for corner, stub-up, and offset diagrams.
 * [inputInches] and [result] currently do not affect the drawing.
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
    val strokeWidth  = 8f  // Increased from 6f for better visibility

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
 * Draws a floor-level pipe that bends upward at a rounded corner, with a wall/floor
 * reference and an optional mark tick on the horizontal run.
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
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    // Horizontal run along the floor (slightly elevated for visibility)
    val floorY = h - pad - sw * 2
    val kneeX = w * 0.55f

    draw3DPipe(
        start       = Offset(w * 0.15f, floorY),
        end         = Offset(kneeX, floorY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Arc at the corner knee, bending upward
    val radius = h * 0.18f
    val kneePath = Path().apply {
        moveTo(kneeX, floorY)
        cubicTo(
            kneeX + radius * 0.5f, floorY,
            kneeX + radius * 0.5f, floorY - radius * 0.5f,
            kneeX + radius * 0.5f, floorY - radius
        )
    }
    draw3DPipePath(
        path        = kneePath,
        color       = pipeColor,
        strokeWidth = sw
    )

    // Vertical run going up toward ceiling
    draw3DPipe(
        start       = Offset(kneeX + radius * 0.5f, floorY - radius),
        end         = Offset(kneeX + radius * 0.5f, pad * 2),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Mark tick on the horizontal run
    if (showLabels) {
        val markX = w * 0.35f
        drawArrowTick(markX, floorY, accentColor, sw * 0.9f)
    }
}

// ── 90° Stub-Up ───────────────────────────────────────────────────────────────

/**
 * Shows a horizontal pipe run along the floor that bends 90° upward.
 * The reference wall/floor corner helps visualize the vertical orientation.
 */
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
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    // Horizontal run along the bottom floor
    val runEndX   = w * 0.45f
    val floorY    = h - pad - sw * 2

    // Vertical stub going up
    val stubStartX = runEndX
    val stubTopY   = pad * 2

    // Horizontal segment along floor
    draw3DPipe(
        start       = Offset(w * 0.10f, floorY),
        end         = Offset(runEndX, floorY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Curved knee — rises from floor
    val bendRadius = h * 0.22f
    val kneePath = Path().apply {
        moveTo(runEndX, floorY)
        cubicTo(
            runEndX + bendRadius * 0.5f, floorY,
            stubStartX + bendRadius * 0.5f, floorY - bendRadius,
            stubStartX + bendRadius * 0.5f, floorY - bendRadius * 1.5f
        )
    }
    draw3DPipePath(
        path        = kneePath,
        color       = pipeColor,
        strokeWidth = sw
    )

    // Vertical stub rising up
    draw3DPipe(
        start       = Offset(stubStartX + bendRadius * 0.5f, floorY - bendRadius * 1.5f),
        end         = Offset(stubStartX + bendRadius * 0.5f, stubTopY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Mark arrow on the horizontal run
    if (showLabels) {
        val markX = runEndX * 0.55f
        drawArrowTick(markX, floorY, accentColor, sw * 0.9f)
    }
}

// ── Offset ────────────────────────────────────────────────────────────────────

/**
 * Shows a horizontal pipe running along the floor that rises to clear an obstacle,
 * then returns to floor level. The reference wall/floor shows this is a side view.
 */
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
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    val floorLevel  = h - pad - sw * 2      // pipe level along floor
    val raisedLevel = h * 0.35f             // raised level over obstacle
    val riseX1 = w * 0.30f                  // start of first bend
    val riseX2 = w * 0.65f                  // start of second bend

    // Draw pipes FIRST (background)
    // Left horizontal run along floor
    draw3DPipe(
        start       = Offset(w * 0.10f, floorLevel),
        end         = Offset(riseX1, floorLevel),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Diagonal rise over obstacle
    draw3DPipe(
        start       = Offset(riseX1, floorLevel),
        end         = Offset(riseX2, raisedLevel),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Right horizontal run back at floor level
    draw3DPipe(
        start       = Offset(riseX2, raisedLevel),
        end         = Offset(w * 0.90f, floorLevel),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Draw obstacle LAST (foreground) - appears in front of lower pipe sections
    val obstacleLeft  = riseX1 + (riseX2 - riseX1) * 0.25f
    val obstacleWidth = (riseX2 - riseX1) * 0.50f
    val obstacleHeight = floorLevel - raisedLevel - sw * 3
    val obstacleRight = obstacleLeft + obstacleWidth
    
    draw3DObstacle(
        topLeft = Offset(obstacleLeft, floorLevel - obstacleHeight),
        width   = obstacleWidth,
        height  = obstacleHeight,
        color   = accentColor
    )

    // Mark ticks
    if (showLabels) {
        drawArrowTick(riseX1, floorLevel, accentColor, sw * 0.9f)
        drawArrowTick(riseX2, raisedLevel, accentColor, sw * 0.9f)
    }
}

// ── 3-Point Saddle ────────────────────────────────────────────────────────────

/**
 * Shows pipe running along floor that rises over an obstacle in the middle.
 * The reference wall/floor provides spatial context for the vertical clearance.
 */
private fun DrawScope.drawSaddle3Point(pipeColor: Color, accentColor: Color, sw: Float) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    val baseY    = h - pad - sw * 2  // floor level
    val peakY    = h * 0.25f         // peak height over obstacle
    val x1       = w * 0.15f
    val x3       = w * 0.50f         // centre / peak
    val x5       = w * 0.85f

    // Draw pipes FIRST (background)
    draw3DPipe(
        start       = Offset(w * 0.05f, baseY),
        end         = Offset(x1, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x1, baseY),
        end         = Offset(x3, peakY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x3, peakY),
        end         = Offset(x5, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x5, baseY),
        end         = Offset(w * 0.95f, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Draw obstacle LAST (foreground) - appears in front
    val obstacleWidth = (x5 - x1) * 0.55f
    val obstacleLeft = x3 - obstacleWidth / 2
    val obstacleRight = obstacleLeft + obstacleWidth
    val obstacleHeight = baseY - peakY - sw * 4
    
    draw3DObstacle(
        topLeft = Offset(obstacleLeft, baseY - obstacleHeight),
        width   = obstacleWidth,
        height  = obstacleHeight,
        color   = accentColor
    )

    // Centre tick at peak
    drawArrowTick(x3, peakY, accentColor, sw)
}

// ── 4-Point Saddle ────────────────────────────────────────────────────────────

/**
 * Shows pipe running along floor with a flat top section clearing an obstacle.
 * The reference wall/floor shows this is a side view.
 */
private fun DrawScope.drawSaddle4Point(pipeColor: Color, accentColor: Color, sw: Float) {
    val w   = size.width
    val h   = size.height
    val pad = sw * 2
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    val baseY  = h - pad - sw * 2  // floor level
    val topY   = h * 0.30f         // elevated section
    val x1     = w * 0.18f
    val x2     = w * 0.38f
    val x3     = w * 0.62f
    val x4     = w * 0.82f

    // Draw pipes FIRST (background)
    draw3DPipe(
        start       = Offset(w * 0.05f, baseY),
        end         = Offset(x1, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x1, baseY),
        end         = Offset(x2, topY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x2, topY),
        end         = Offset(x3, topY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x3, topY),
        end         = Offset(x4, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )
    draw3DPipe(
        start       = Offset(x4, baseY),
        end         = Offset(w * 0.95f, baseY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Draw obstacle LAST (foreground) - appears in front
    val obstacleWidth = (x4 - x1) * 0.65f
    val obstacleLeft = ((x1 + x4) / 2) - obstacleWidth / 2
    val obstacleRight = obstacleLeft + obstacleWidth
    val obstacleHeight = baseY - topY - sw * 4
    
    draw3DObstacle(
        topLeft = Offset(obstacleLeft, baseY - obstacleHeight),
        width   = obstacleWidth,
        height  = obstacleHeight,
        color   = accentColor
    )
}

// ── Back-to-Back ──────────────────────────────────────────────────────────────

/**
 * Shows two 90° bends in opposite directions with a horizontal run connecting them.
 * The reference wall/floor shows the vertical orientation of the stub sections.
 */
private fun DrawScope.drawBackToBack(pipeColor: Color, accentColor: Color, sw: Float) {
    val w    = size.width
    val h    = size.height
    val pad  = sw * 2
    
    // Draw reference wall/floor for orientation
    drawReferenceWall(pipeColor)

    val bottomY = h - pad - sw * 2  // floor level
    val topY    = h * 0.25f         // elevated horizontal run

    val leftX  = w * 0.25f
    val rightX = w * 0.75f

    // Left vertical stub (up from floor)
    draw3DPipe(
        start       = Offset(leftX, bottomY),
        end         = Offset(leftX, topY),
        color       = pipeColor,
        strokeWidth = sw
    )
    // Horizontal connecting run at elevated level
    draw3DPipe(
        start       = Offset(leftX, topY),
        end         = Offset(rightX, topY),
        color       = pipeColor,
        strokeWidth = sw
    )
    // Right vertical stub (down to floor)
    draw3DPipe(
        start       = Offset(rightX, topY),
        end         = Offset(rightX, bottomY),
        color       = pipeColor,
        strokeWidth = sw
    )

    // Heel marks at the bends
    drawArrowTick(leftX, topY, accentColor, sw * 0.9f)
    drawArrowTick(rightX, topY, accentColor, sw * 0.9f)
}

// ── Shared drawing helpers ────────────────────────────────────────────────────

/**
 * Draws a reference wall/floor corner to provide spatial orientation.
 * The wall runs vertically on the left side, floor runs horizontally at the bottom.
 */
private fun DrawScope.drawReferenceWall(color: Color) {
    val wallThickness = 4f
    val wallColor = color.copy(alpha = 0.25f)
    
    // Vertical wall on the left side
    drawLine(
        color       = wallColor,
        start       = Offset(0f, 0f),
        end         = Offset(0f, size.height),
        strokeWidth = wallThickness,
        cap         = StrokeCap.Square
    )
    
    // Horizontal floor at the bottom
    drawLine(
        color       = wallColor,
        start       = Offset(0f, size.height),
        end         = Offset(size.width, size.height),
        strokeWidth = wallThickness,
        cap         = StrokeCap.Square
    )
}

/**
 * Draws a pipe segment with subtle 3D appearance.
 * Creates a cylindrical look with minimal layering for clean appearance.
 */
private fun DrawScope.draw3DPipe(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    // Shadow for depth
    drawLine(
        color       = Color.Black.copy(alpha = 0.20f),
        start       = Offset(start.x + 1.5f, start.y + 1.5f),
        end         = Offset(end.x + 1.5f, end.y + 1.5f),
        strokeWidth = strokeWidth + 1f,
        cap         = StrokeCap.Round
    )
    
    // Main pipe body
    drawLine(
        color       = color,
        start       = start,
        end         = end,
        strokeWidth = strokeWidth,
        cap         = StrokeCap.Round
    )
}

/**
 * Draws a 3D pipe path with subtle shading for curved sections.
 */
private fun DrawScope.draw3DPipePath(
    path: Path,
    color: Color,
    strokeWidth: Float
) {
    // Shadow
    drawPath(
        path  = path,
        color = Color.Black.copy(alpha = 0.20f),
        style = Stroke(width = strokeWidth + 1f, cap = StrokeCap.Round)
    )
    
    // Main body
    drawPath(
        path  = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * Draws a clean obstacle box with fill and border.
 */
private fun DrawScope.draw3DObstacle(
    topLeft: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    // Shadow
    drawRect(
        color = Color.Black.copy(alpha = 0.15f),
        topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
        size = androidx.compose.ui.geometry.Size(width, height)
    )
    
    // Main fill
    drawRect(
        color = color.copy(alpha = 0.25f),
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(width, height)
    )
    
    // Border
    drawRect(
        color = color.copy(alpha = 0.60f),
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(width, height),
        style = Stroke(width = 2f)
    )
}

/** Clean perpendicular tick mark indicating a measurement mark on the pipe. */
private fun DrawScope.drawArrowTick(x: Float, y: Float, color: Color, sw: Float) {
    val len = sw * 3f
    // Shadow
    drawLine(
        color       = Color.Black.copy(alpha = 0.25f),
        start       = Offset(x + 1f, y - len + 1f),
        end         = Offset(x + 1f, y + len + 1f),
        strokeWidth = sw * 1.1f,
        cap         = StrokeCap.Round
    )
    // Main tick
    drawLine(
        color       = color,
        start       = Offset(x, y - len),
        end         = Offset(x, y + len),
        strokeWidth = sw,
        cap         = StrokeCap.Round
    )
}

// ── Inline pipe diagrams for step cards ──────────────────────────────────────

/** Draws a pipe with a mark at [markFraction] of its width; [labelText] is not rendered. */
@Composable
private fun PipeWithMark(markFraction: Float, labelText: String) {
    val pipeColor   = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2f
        val sw = 8f
        
        // Draw 3D pipe
        draw3DPipe(
            start       = Offset(0f, midY),
            end         = Offset(size.width, midY),
            color       = pipeColor,
            strokeWidth = sw
        )
        
        // Draw mark tick
        val markX = size.width * markFraction
        drawArrowTick(markX, midY, accentColor, sw * 0.9f)
    }
}

/**
 * Draws a pipe with marks at the given fractions of its width.
 * [label1] and [label2] are not rendered.
 */
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
        val sw = 8f
        
        // Draw 3D pipe
        draw3DPipe(
            start       = Offset(0f, midY),
            end         = Offset(size.width, midY),
            color       = pipeColor,
            strokeWidth = sw
        )
        
        // Draw mark ticks
        listOf(mark1Fraction, mark2Fraction).forEach { frac ->
            val x = size.width * frac
            drawArrowTick(x, midY, accentColor, sw * 0.9f)
        }
    }
}
