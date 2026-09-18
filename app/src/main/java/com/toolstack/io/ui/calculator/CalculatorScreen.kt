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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R

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
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear History") },
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
        containerColor = com.toolstack.io.ui.theme.CalcBackground,
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
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all calculation history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onClearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Auto-scroll to the end (right) when history changes
    androidx.compose.runtime.LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
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
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                history.reversed().forEachIndexed { index, item ->
                    if (index > 0) {
                        Text(
                            text = "  |  ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = com.toolstack.io.ui.theme.CalcDisplayDark.copy(alpha = 0.4f)
                        )
                    }
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = com.toolstack.io.ui.theme.CalcDisplayDark.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Main Expression Line (Middle) - active input
        Text(
            text = expression.ifEmpty { " " },
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal
            ),
            color = com.toolstack.io.ui.theme.CalcDisplayDark,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Dynamic Result Preview (Bottom) - live evaluation
        Text(
            text = if (liveResult.isNotEmpty()) "= $liveResult" else " ",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            ),
            color = com.toolstack.io.ui.theme.CalcDisplayDark.copy(alpha = 0.8f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
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
    modifier: Modifier = Modifier
) {
    // Row layout: 4 columns × 5 rows
    // Row 1:  C  ⌫   %  ÷
    // Row 2:  7   8   9  ×
    // Row 3:  4   5   6  −
    // Row 4:  1   2   3  +
    // Row 5:  0  00   .  =
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1 — function keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcClearKey(label = stringResource(R.string.calculator_key_clear), modifier = Modifier.weight(1f), onClick = onClear)
            CalcBackspaceKey(modifier = Modifier.weight(1f), onClick = onBackspace)
            CalcFunctionKey(label = stringResource(R.string.calculator_key_pct),   modifier = Modifier.weight(1f), onClick = onPercent)
            CalcOperatorKey(label = "÷", modifier = Modifier.weight(1f)) { onOperator("÷") }
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcDigitKey(label = "7", modifier = Modifier.weight(1f)) { onDigit("7") }
            CalcDigitKey(label = "8", modifier = Modifier.weight(1f)) { onDigit("8") }
            CalcDigitKey(label = "9", modifier = Modifier.weight(1f)) { onDigit("9") }
            CalcOperatorKey(label = "×", modifier = Modifier.weight(1f)) { onOperator("×") }
        }
        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcDigitKey(label = "4", modifier = Modifier.weight(1f)) { onDigit("4") }
            CalcDigitKey(label = "5", modifier = Modifier.weight(1f)) { onDigit("5") }
            CalcDigitKey(label = "6", modifier = Modifier.weight(1f)) { onDigit("6") }
            CalcOperatorKey(label = "−", modifier = Modifier.weight(1f)) { onOperator("−") }
        }
        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcDigitKey(label = "1", modifier = Modifier.weight(1f)) { onDigit("1") }
            CalcDigitKey(label = "2", modifier = Modifier.weight(1f)) { onDigit("2") }
            CalcDigitKey(label = "3", modifier = Modifier.weight(1f)) { onDigit("3") }
            CalcOperatorKey(label = "+", modifier = Modifier.weight(1f)) { onOperator("+") }
        }
        // Row 5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcDigitKey(label = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
            CalcDigitKey(label = "00", modifier = Modifier.weight(1f)) { onDigit("00") }
            CalcDigitKey(label = ".", modifier = Modifier.weight(1f)) { onDigit(".") }
            CalcEqualsKey(modifier = Modifier.weight(1f), onClick = onEquals)
        }
    }
}

// ── Individual key composables ────────────────────────────────────────────────

@Composable
private fun CalcDigitKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcDigitButton,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CalcOperatorKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcOperatorOrange,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CalcClearKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcClearRed,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CalcFunctionKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcFunctionDark,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CalcEqualsKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcEqualsGreen,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(
            text = "=",
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CalcBackspaceKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.toolstack.io.ui.theme.CalcFunctionDark,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.content_description_backspace),
            tint = androidx.compose.ui.graphics.Color.White
        )
    }
}
