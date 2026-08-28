package com.toolstack.io.ui.tapsanddrills

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolstack.io.domain.calculator.TapDrillData
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.domain.model.TapDrillThread
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TapsAndDrillsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TapsAndDrillsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val bodyStyle = MaterialTheme.typography.bodySmall
    val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)

    val columnWidths = remember(textMeasurer, density, bodyStyle, headerStyle) {

        fun measure(text: String, style: TextStyle): Int {
            return textMeasurer.measure(
                text = text,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                constraints = Constraints(maxWidth = 10000, maxHeight = 1000)
            ).size.width
        }

        fun maxOfTexts(texts: Iterable<String>, style: TextStyle): Int {
            return texts.maxOfOrNull { measure(it, style) } ?: 0
        }

        val dotWidthPx = with(density) { 12.dp.roundToPx() }

        val threadBodyMax = maxOfTexts(TapDrillData.threads.map { it.designation }, bodyStyle)
        val sparkThreadMax = maxOfTexts(TapDrillData.threads.filter { it.isSparkPlug }.map { it.designation }, bodyStyle)
        val threadHeader = measure("Thread", headerStyle)
        val threadWidthPx = maxOf(threadBodyMax, threadHeader, sparkThreadMax + dotWidthPx)

        val pitchTexts = TapDrillData.threads.map { pitchText(it) }
        val pitchWidthPx = maxOf(maxOfTexts(pitchTexts, bodyStyle), measure("Pitch", headerStyle))

        val majorTexts = TapDrillData.threads.map { "${formatDecimal(it.majorDiameter)}\"" }
        val majorWidthPx = maxOf(maxOfTexts(majorTexts, bodyStyle), measure("Major Dia", headerStyle))

        val tapTexts = TapDrillData.threads.map { it.tapDrill75 }
        val tapWidthPx = maxOf(maxOfTexts(tapTexts, bodyStyle), measure("Tap Drill", headerStyle))

        val decimalTexts = TapDrillData.threads.map { "${formatDecimal(it.tapDrill75Decimal)}\"" }
        val decimalWidthPx = maxOf(maxOfTexts(decimalTexts, bodyStyle), measure("Decimal", headerStyle))

        val fractionalTexts = TapDrillData.threads.map { it.closestFractional ?: "–" }
        val fractionalWidthPx = maxOf(maxOfTexts(fractionalTexts, bodyStyle), measure("Fractional", headerStyle))

        with(density) {
            mapOf(
                "thread" to threadWidthPx.toDp(),
                "pitch" to pitchWidthPx.toDp(),
                "major" to majorWidthPx.toDp(),
                "tap" to tapWidthPx.toDp(),
                "decimal" to decimalWidthPx.toDp(),
                "fractional" to fractionalWidthPx.toDp()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.taps_and_drills),
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                            tint = MaterialTheme.colorScheme.onPrimary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.tap_drill_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryFilterChip(
                    selectedId = uiState.selectedStandard,
                    counts = uiState.categoryCounts,
                    onCategorySelected = viewModel::onCategorySelected
                )
                ColumnsFilterChip(
                    visibleColumns = uiState.visibleColumns,
                    onColumnToggled = viewModel::onColumnToggled
                )
                SparkPlugChip(
                    selected = uiState.sparkPlugOnly,
                    onClick = { viewModel.onSparkPlugOnlyChanged(!uiState.sparkPlugOnly) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val visibleColumnIds = uiState.visibleColumns
                val visibleColumnWidthList = visibleColumnIds.mapNotNull { columnWidths[it] }
                val contentMinWidth = visibleColumnWidthList.fold(0.dp) { acc, width -> acc + width }
                val tableMinWidth = if (visibleColumnIds.isEmpty()) {
                    0.dp
                } else {
                    contentMinWidth +
                        ((visibleColumnIds.size - 1) * 4).dp +
                        16.dp
                }
                val tableWidth = if (visibleColumnIds.isEmpty()) 0.dp else maxWidth.coerceAtLeast(tableMinWidth)

                val tableContentWidth = tableWidth - 16.dp
                val totalCellWidth = if (visibleColumnIds.isEmpty()) {
                    0.dp
                } else {
                    tableContentWidth - ((visibleColumnIds.size - 1) * 4).dp
                }

                val displayColumnWidths = remember(tableWidth, visibleColumnIds, columnWidths) {
                    if (contentMinWidth == 0.dp) {
                        emptyMap()
                    } else {
                        visibleColumnIds.associateWith { columnId ->
                            val minWidth = columnWidths[columnId] ?: 0.dp
                            totalCellWidth * (minWidth / contentMinWidth)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .width(tableWidth)
                            .fillMaxHeight()
                    ) {
                        stickyHeader {
                            TableHeader(
                                visibleColumns = uiState.visibleColumns,
                                columnWidths = displayColumnWidths
                            )
                        }

                        if (uiState.displayedThreads.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.tap_drill_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.displayedThreads,
                                key = { it.designation }
                            ) { thread ->
                                TableRow(
                                    thread = thread,
                                    visibleColumns = uiState.visibleColumns,
                                    columnWidths = displayColumnWidths
                                )
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
    }
}
}

@Composable
private fun TableHeader(
    visibleColumns: Set<String>,
    columnWidths: Map<String, Dp>
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (visibleColumns.contains("thread")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_thread_header), width = columnWidths["thread"]!!)
            }
            if (visibleColumns.contains("pitch")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_pitch_header), width = columnWidths["pitch"]!!)
            }
            if (visibleColumns.contains("major")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_major_header), width = columnWidths["major"]!!)
            }
            if (visibleColumns.contains("tap")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_tap_header), width = columnWidths["tap"]!!)
            }
            if (visibleColumns.contains("decimal")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_decimal_header), width = columnWidths["decimal"]!!)
            }
            if (visibleColumns.contains("fractional")) {
                TableHeaderCell(text = stringResource(R.string.tap_drill_fractional_header), width = columnWidths["fractional"]!!)
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TableRow(
    thread: TapDrillThread,
    visibleColumns: Set<String>,
    columnWidths: Map<String, Dp>
) {
    val textStyle = MaterialTheme.typography.bodySmall

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visibleColumns.contains("thread")) {
            ThreadCell(
                thread = thread,
                width = columnWidths["thread"]!!,
                style = textStyle
            )
        }
        if (visibleColumns.contains("pitch")) {
            TableCell(
                text = pitchText(thread),
                width = columnWidths["pitch"]!!,
                style = textStyle
            )
        }
        if (visibleColumns.contains("major")) {
            TableCell(
                text = "${formatDecimal(thread.majorDiameter)}\"",
                width = columnWidths["major"]!!,
                style = textStyle
            )
        }
        if (visibleColumns.contains("tap")) {
            TableCell(
                text = thread.tapDrill75,
                width = columnWidths["tap"]!!,
                style = textStyle
            )
        }
        if (visibleColumns.contains("decimal")) {
            TableCell(
                text = "${formatDecimal(thread.tapDrill75Decimal)}\"",
                width = columnWidths["decimal"]!!,
                style = textStyle
            )
        }
        if (visibleColumns.contains("fractional")) {
            TableCell(
                text = thread.closestFractional ?: "–",
                width = columnWidths["fractional"]!!,
                style = textStyle
            )
        }
    }
}

@Composable
private fun ThreadCell(
    thread: TapDrillThread,
    width: Dp,
    style: TextStyle
) {
    Row(
        modifier = Modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = thread.designation,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true)
        )
        if (thread.isSparkPlug) {
            SparkPlugDot()
        }
    }
}

@Composable
private fun SparkPlugDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    style: TextStyle,
    color: Color = Color.Unspecified
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

private fun pitchText(thread: TapDrillThread): String {
    return if (thread.standard == "METRIC") {
        "${String.format(Locale.US, "%.2f", thread.pitch)} mm"
    } else {
        "${thread.pitch.toInt()} TPI"
    }
}

private fun formatDecimal(value: Double): String {
    return String.format(Locale.US, "%.4f", value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterChip(
    selectedId: String?,
    counts: Map<String?, Int>,
    onCategorySelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedId } ?: categories.first()
    val count = counts[selectedCategory.id] ?: 0

    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                Text(
                    text = "${stringResource(selectedCategory.titleRes)} ($count)",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.tap_drill_filter)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                val categoryCount = counts[category.id] ?: 0
                val isSelected = selectedId == category.id
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${stringResource(category.titleRes)} ($categoryCount)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnsFilterChip(
    visibleColumns: Set<String>,
    onColumnToggled: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCount = columnOptions.count { it.id in visibleColumns }

    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                Text(
                    text = "${stringResource(R.string.tap_drill_columns)} ($activeCount)",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ViewColumn,
                    contentDescription = stringResource(R.string.tap_drill_columns)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            columnOptions.forEach { option ->
                val isVisible = option.id in visibleColumns
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.titleRes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { onColumnToggled(option.id) },
                    trailingIcon = {
                        if (isVisible) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkPlugChip(
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = stringResource(R.string.tap_drill_spark_plug),
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
        )
    )
}

private data class Category(
    val id: String?,
    val titleRes: Int
)

private val categories = listOf(
    Category(null, R.string.tap_drill_category_all),
    Category("UNC", R.string.tap_drill_category_unc),
    Category("UNF", R.string.tap_drill_category_unf),
    Category("UNS", R.string.tap_drill_category_uns),
    Category("UNEF", R.string.tap_drill_category_unef),
    Category("METRIC", R.string.tap_drill_category_metric),
    Category("PIPE", R.string.tap_drill_category_pipe)
)

private data class ColumnOption(
    val id: String,
    val titleRes: Int
)

private val columnOptions = listOf(
    ColumnOption("thread", R.string.tap_drill_thread_header),
    ColumnOption("pitch", R.string.tap_drill_pitch_header),
    ColumnOption("major", R.string.tap_drill_major_header),
    ColumnOption("tap", R.string.tap_drill_tap_header),
    ColumnOption("decimal", R.string.tap_drill_decimal_header),
    ColumnOption("fractional", R.string.tap_drill_fractional_header)
)
