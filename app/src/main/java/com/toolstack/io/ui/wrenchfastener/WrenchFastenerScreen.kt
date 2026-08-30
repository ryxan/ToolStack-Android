package com.toolstack.io.ui.wrenchfastener

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.WrenchFastenerData
import com.toolstack.io.domain.model.WrenchFastenerEntry

private enum class FastenerSystem { SAE, METRIC }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WrenchFastenerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSystem by rememberSaveable { mutableStateOf(FastenerSystem.SAE) }
    val entries = when (selectedSystem) {
        FastenerSystem.SAE -> WrenchFastenerData.saeEntries()
        FastenerSystem.METRIC -> WrenchFastenerData.metricEntries()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.wrench_fastener),
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedSystem == FastenerSystem.SAE,
                    onClick = { selectedSystem = FastenerSystem.SAE },
                    label = { Text(text = stringResource(R.string.wrench_fastener_sae)) }
                )
                FilterChip(
                    selected = selectedSystem == FastenerSystem.METRIC,
                    onClick = { selectedSystem = FastenerSystem.METRIC },
                    label = { Text(text = stringResource(R.string.wrench_fastener_metric)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                stickyHeader {
                    WrenchFastenerTableHeader(selectedSystem)
                }

                items(
                    items = entries,
                    key = { it.wrenchLabel }
                ) { entry ->
                    WrenchFastenerTableRow(entry = entry)
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WrenchFastenerTableHeader(selectedSystem: FastenerSystem) {
    val wrenchHeader = when (selectedSystem) {
        FastenerSystem.SAE -> stringResource(R.string.sae_wrench_header)
        FastenerSystem.METRIC -> stringResource(R.string.metric_wrench_header)
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WrenchColumnText(text = wrenchHeader, weight = 1f)
            WrenchColumnText(text = stringResource(R.string.fastener_header), weight = 1f)
        }
    }
}

@Composable
private fun WrenchFastenerTableRow(entry: WrenchFastenerEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WrenchColumnText(text = entry.wrenchLabel, weight = 1f)
        WrenchColumnText(text = entry.fastenerLabel, weight = 1f)
    }
}

@Composable
private fun RowScope.WrenchColumnText(
    text: String,
    weight: Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.weight(weight, fill = true)
    )
}
