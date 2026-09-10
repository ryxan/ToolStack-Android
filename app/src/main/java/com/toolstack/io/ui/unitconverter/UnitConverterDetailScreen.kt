package com.toolstack.io.ui.unitconverter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.UnitConverterData
import com.toolstack.io.domain.model.UnitEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterDetailScreen(
    categoryIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = remember(categoryIndex) {
        UnitConverterData.categories.getOrElse(categoryIndex) {
            UnitConverterData.categories.first()
        }
    }

    val viewModel: UnitConverterViewModel = viewModel(
        key = "converter_$categoryIndex",
        factory = UnitConverterViewModel.factory(category)
    )

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.category.name,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UnitInputRow(
                        label = stringResource(R.string.unit_converter_from),
                        value = uiState.fromText,
                        unit = uiState.fromUnit,
                        units = uiState.category.units,
                        onValueChange = viewModel::onFromTextChanged,
                        onUnitSelected = viewModel::onFromUnitSelected,
                        onFocused = {
                            // Clear the computed value when the user taps into this field
                            // so they start with a blank slate rather than editing a result.
                            if (uiState.activeField == ActiveField.TO) {
                                viewModel.onFromTextChanged("")
                            }
                        }
                    )

                    UnitInputRow(
                        label = stringResource(R.string.unit_converter_to),
                        value = uiState.toText,
                        unit = uiState.toUnit,
                        units = uiState.category.units,
                        onValueChange = viewModel::onToTextChanged,
                        onUnitSelected = viewModel::onToUnitSelected,
                        onFocused = {
                            if (uiState.activeField == ActiveField.FROM) {
                                viewModel.onToTextChanged("")
                            }
                        }
                    )
                }
            }

            // Summary line — always shows from → to direction for readability.
            if (uiState.fromText.isNotEmpty() && uiState.toText.isNotEmpty() &&
                uiState.toText != "—" && uiState.fromText != "—"
            ) {
                Text(
                    text = "${uiState.fromText} ${uiState.fromUnit.symbol}  =  ${uiState.toText} ${uiState.toUnit.symbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Shared input row ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitInputRow(
    label: String,
    value: String,
    unit: UnitEntry,
    units: List<UnitEntry>,
    onValueChange: (String) -> Unit,
    onUnitSelected: (UnitEntry) -> Unit,
    onFocused: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        text = stringResource(R.string.unit_converter_input_hint),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState -> if (focusState.isFocused) onFocused() }
            )

            Spacer(modifier = Modifier.width(0.dp))

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.width(150.dp)
            ) {
                OutlinedTextField(
                    value = unit.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    units.forEach { entry ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = entry.symbol,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onUnitSelected(entry)
                                dropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}
