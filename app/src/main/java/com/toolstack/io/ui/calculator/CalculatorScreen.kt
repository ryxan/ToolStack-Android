package com.toolstack.io.ui.calculator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.ui.theme.CalcColors
import com.toolstack.io.ui.theme.calcColors
import java.util.Locale

/**
 * Formats a numeric string with thousand separators for display.
 * Preserves the input for incomplete/invalid numbers (e.g., "123.", ".", "-").
 * Preserves scientific notation (e.g., "1e+10") and all fractional digits.
 */
private fun formatNumber(value: String): String {
    if (value.isEmpty() || value == "." || value == "-" || value.endsWith(".")) {
        return value
    }
    
    // Don't format scientific notation - pass through as-is
    if (value.contains('e', ignoreCase = true)) {
        return value
    }
    
    return try {
        // Split into integer and fractional parts to preserve exact decimal digits
        val parts = value.split('.')
        val integerPart = parts[0]
        val fractionalPart = if (parts.size > 1) parts[1] else ""
        
        // Format integer part with thousand separators
        val number = integerPart.toLongOrNull() ?: return value
        val formattedInteger = String.format(Locale.US, "%,d", number)
        
        // Reconstruct with original fractional digits
        if (fractionalPart.isNotEmpty()) {
            "$formattedInteger.$fractionalPart"
        } else {
            formattedInteger
        }
    } catch (e: Exception) {
        value
    }
}

/**
 * Formats numbers in an expression string while preserving operators.
 * Uses space-delimited tokens from the engine to avoid breaking scientific notation.
 * Example: "123456 + 789" -> "123,456 + 789"
 *          "1e+10 × 2" -> "1e+10 × 2" (scientific notation preserved)
 */
private fun formatExpression(expression: String): String {
    if (expression.isEmpty()) return expression
    
    // The engine already provides space-delimited tokens
    // Split by spaces and format each token if it's a number
    val tokens = expression.split(" ")
    
    return tokens.joinToString(" ") { token ->
        // Check if it's an operator
        if (token in listOf("+", "−", "×", "÷", "=")) {
            token
        } else {
            // It's a number - format it
            formatNumber(token)
        }
    }
}

/**
 * Main calculator screen.
 *
 * @param onBack                 Navigates back to the home screen.
 * @param onNavigateToUnitConverter  Quick-switch to the Unit Converter screen.
 * @param onAddShortcut          Pins this screen to the launcher (null hides the button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    onNavigateToUnitConverter: () -> Unit,
    onAddShortcut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val calcState = uiState.calculatorState
    val colors = calcColors()
    
    var showMenu by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.calculator_title),
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
                    // Quick-switch to Unit Converter
                    IconButton(onClick = onNavigateToUnitConverter) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.calculator_switch_to_unit_converter)
                        )
                    }
                    if (onAddShortcut != null) {
                        IconButton(onClick = onAddShortcut) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                                contentDescription = stringResource(R.string.content_description_add_shortcut)
                            )
                        }
                    }
                    // More menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.calculator_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calculator_clear_history)) },
                                onClick = {
                                    showMenu = false
                                    showClearHistoryDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null
                                    )
                                },
                                enabled = calcState.history.isNotEmpty()
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
        containerColor = colors.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            // Calculate available space and constrain keypad width based on height
            val availableHeight = maxHeight
            val availableWidth = maxWidth
            
            // Keypad needs 5 rows + 4 gaps (32dp spacing) + some margin
            val keypadMinHeight = 280.dp
            val isLandscape = availableWidth > availableHeight
            val needsScroll = isLandscape && availableHeight < keypadMinHeight
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (needsScroll) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Display ───────────────────────────────────────────────────────
                DisplayPanel(
                    history = calcState.history,
                    expression = calcState.expression,
                    display = calcState.display,
                    liveResult = calcState.liveResult,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!needsScroll) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ── Keypad ────────────────────────────────────────────────────────
                Keypad(
                    onDigit    = viewModel::onDigit,
                    onOperator = viewModel::onOperator,
                    onEquals   = viewModel::onEquals,
                    onClear    = viewModel::onClear,
                    onBackspace = viewModel::onBackspace,
                    onPercent  = viewModel::onPercent,
                    onSignFlip = viewModel::onSignFlip,
                    colors = colors,
                    modifier = if (needsScroll) {
                        // Constrain width in landscape to maintain square keys
                        Modifier.width(availableHeight * 0.8f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
            }
        }
    }
    
    // Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.calculator_clear_history_title)) },
            text = { Text(stringResource(R.string.calculator_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onClearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.calculator_clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

// ── Display panel ─────────────────────────────────────────────────────────────

@Composable
private fun DisplayPanel(
    history: List<String>,
    expression: String,
    display: String,
    liveResult: String,
    colors: CalcColors,
    modifier: Modifier = Modifier
) {
    val historyScrollState = rememberScrollState()
    val expressionScrollState = rememberScrollState()
    
    // Auto-scroll history to the end (right) when history changes
    androidx.compose.runtime.LaunchedEffect(history.size, historyScrollState.maxValue) {
        if (history.isNotEmpty() && 
            historyScrollState.maxValue > 0 && 
            historyScrollState.maxValue != Int.MAX_VALUE
        ) {
            historyScrollState.animateScrollTo(historyScrollState.maxValue)
        }
    }
    
    // Auto-scroll expression to bottom when it grows
    androidx.compose.runtime.LaunchedEffect(expression, expressionScrollState.maxValue) {
        if (expressionScrollState.maxValue > 0) {
            expressionScrollState.animateScrollTo(expressionScrollState.maxValue)
        }
    }
    
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        // History Line (Top) - scrollable past calculations
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .horizontalScroll(historyScrollState),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                history.reversed().forEachIndexed { index, item ->
                    if (index > 0) {
                        Text(
                            text = "  |  ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.display.copy(alpha = 0.4f)
                        )
                    }
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.display.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Main Expression Area - scrollable and multi-line with dynamic sizing
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .verticalScroll(expressionScrollState)
        ) {
            val maxWidth = this.maxWidth
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                // Main Expression Line - dynamic font size based on content length
                val formattedExpression = formatExpression(expression.ifEmpty { " " })
                val baseFontSize = 40.sp
                val dynamicFontSize = when {
                    formattedExpression.length < 12 -> baseFontSize
                    formattedExpression.length < 20 -> 36.sp
                    formattedExpression.length < 30 -> 32.sp
                    else -> 28.sp
                }
                
                Text(
                    text = formattedExpression,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = dynamicFontSize,
                        fontWeight = FontWeight.Normal,
                        lineHeight = dynamicFontSize * 1.2f
                    ),
                    color = colors.display,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Dynamic Result Preview
                if (liveResult.isNotEmpty()) {
                    val formattedResult = formatNumber(liveResult)
                    val resultFontSize = when {
                        formattedResult.length < 12 -> 32.sp
                        formattedResult.length < 20 -> 28.sp
                        else -> 24.sp
                    }
                    
                    Text(
                        text = "= $formattedResult",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = resultFontSize,
                            fontWeight = FontWeight.Normal
                        ),
                        color = colors.display.copy(alpha = 0.8f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Keypad ────────────────────────────────────────────────────────────────────

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onEquals: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onPercent: () -> Unit,
    onSignFlip: () -> Unit,
    colors: CalcColors,
    modifier: Modifier = Modifier
) {
    // Row layout: 4 columns × 5 rows
    // Row 1:  C  ⌫   %  ÷
    // Row 2:  7   8   9  ×
    // Row 3:  4   5   6  −
    // Row 4:  1   2   3  +
    // Row 5:  ±   0   .  =
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1 — function keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcKey(label = stringResource(R.string.calculator_key_clear), containerColor = colors.clear, fontSize = 24.sp, modifier = Modifier.weight(1f), onClick = onClear)
            CalcKey(icon = Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.content_description_backspace), containerColor = colors.function, modifier = Modifier.weight(1f), onClick = onBackspace)
            CalcKey(label = stringResource(R.string.calculator_key_pct), containerColor = colors.function, fontSize = 28.sp, modifier = Modifier.weight(1f), onClick = onPercent)
            CalcKey(label = "÷", containerColor = colors.operator, fontSize = 40.sp, modifier = Modifier.weight(1f)) { onOperator("÷") }
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcKey(label = "7", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("7") }
            CalcKey(label = "8", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("8") }
            CalcKey(label = "9", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("9") }
            CalcKey(label = "×", containerColor = colors.operator, fontSize = 40.sp, modifier = Modifier.weight(1f)) { onOperator("×") }
        }
        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcKey(label = "4", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("4") }
            CalcKey(label = "5", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("5") }
            CalcKey(label = "6", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("6") }
            CalcKey(label = "−", containerColor = colors.operator, fontSize = 40.sp, modifier = Modifier.weight(1f)) { onOperator("−") }
        }
        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcKey(label = "1", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("1") }
            CalcKey(label = "2", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("2") }
            CalcKey(label = "3", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("3") }
            CalcKey(label = "+", containerColor = colors.operator, fontSize = 40.sp, modifier = Modifier.weight(1f)) { onOperator("+") }
        }
        // Row 5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcKey(label = stringResource(R.string.calculator_key_sign), containerColor = colors.function, fontSize = 28.sp, modifier = Modifier.weight(1f), onClick = onSignFlip)
            CalcKey(label = "0", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit("0") }
            CalcKey(label = ".", containerColor = colors.digit, fontSize = 32.sp, modifier = Modifier.weight(1f)) { onDigit(".") }
            CalcKey(label = "=", containerColor = colors.equals, fontSize = 40.sp, modifier = Modifier.weight(1f), onClick = onEquals)
        }
    }
}

// ── Individual key composables ────────────────────────────────────────────────

@Composable
private fun CalcKey(
    label: String,
    containerColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Normal
            )
        )
    }
}

@Composable
private fun CalcKey(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.padding(4.dp)
        )
    }
}
