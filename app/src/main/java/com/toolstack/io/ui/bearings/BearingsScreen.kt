package com.toolstack.io.ui.bearings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.domain.model.Bearing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BearingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BearingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bearings),
                        style = MaterialTheme.typography.titleLarge,
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

            Text(
                text = stringResource(R.string.bearings_search_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.boreText,
                onValueChange = viewModel::onBoreChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.bearings_bore_label)) },
                suffix = { Text(text = stringResource(R.string.bearings_mm_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.odText,
                onValueChange = viewModel::onOdChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.bearings_od_label)) },
                suffix = { Text(text = stringResource(R.string.bearings_mm_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.widthText,
                onValueChange = viewModel::onWidthChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.bearings_width_label)) },
                suffix = { Text(text = stringResource(R.string.bearings_mm_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BearingsViewModel.TOLERANCES.forEach { value ->
                    FilterChip(
                        selected = uiState.toleranceMm == value,
                        onClick = { viewModel.onToleranceSelected(value) },
                        label = { Text(text = toleranceLabel(value)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.boreText.isNotBlank() &&
                    uiState.odText.isNotBlank() &&
                    uiState.widthText.isNotBlank()
            ) {
                Text(text = stringResource(R.string.bearings_search_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    !uiState.hasSearched -> {
                        PromptText(text = stringResource(R.string.bearings_input_prompt))
                    }

                    !uiState.isInputValid -> {
                        PromptText(text = stringResource(R.string.bearings_input_invalid))
                    }

                    uiState.results.isEmpty() -> {
                        PromptText(text = stringResource(R.string.bearings_no_results))
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = uiState.results,
                                key = { it.designation }
                            ) { bearing ->
                                BearingResultCard(bearing = bearing)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun BearingResultCard(bearing: Bearing) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = bearing.designation,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = bearing.type,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            DimensionRow(label = stringResource(R.string.bearings_bore_label), value = bearing.boreMm)
            DimensionRow(label = stringResource(R.string.bearings_od_label), value = bearing.odMm)
            DimensionRow(label = stringResource(R.string.bearings_width_label), value = bearing.widthMm)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.bearings_suffix_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DimensionRow(label: String, value: Double) {
    RowWithLabel(
        label = label,
        value = "${formatMm(value)} ${stringResource(R.string.bearings_mm_suffix)}"
    )
}

@Composable
private fun RowWithLabel(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun toleranceLabel(value: Double): String {
    return when (value) {
        0.0 -> stringResource(R.string.bearings_tolerance_exact)
        0.1 -> stringResource(R.string.bearings_tolerance_0_1)
        0.5 -> stringResource(R.string.bearings_tolerance_0_5)
        1.0 -> stringResource(R.string.bearings_tolerance_1_0)
        else -> stringResource(R.string.bearings_tolerance_exact)
    }
}

private fun formatMm(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}
