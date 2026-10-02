package com.toolstack.io.ui.recipescaler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolstack.io.R

/**
 * Input accessory bar that appears above the keyboard, changing content based on
 * which ingredient field is focused (Quantity or Unit).
 */
@Composable
fun IngredientInputAccessoryBar(
    focusedIngredientId: String?,
    focusedField: FocusedIngredientField,
    currentUnit: String?,
    onFractionClick: (String) -> Unit,
    onUnitClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusedIngredientId == null) {
        return
    }
    when (focusedField) {
        FocusedIngredientField.QUANTITY,
        FocusedIngredientField.UNIT -> Unit
        FocusedIngredientField.NAME,
        FocusedIngredientField.NONE -> return
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        when (focusedField) {
            FocusedIngredientField.QUANTITY -> QuantityAccessoryContent(onFractionClick)
            FocusedIngredientField.UNIT -> UnitAccessoryContent(
                currentUnit = currentUnit,
                onUnitClick = onUnitClick
            )
            else -> Unit
        }
    }
}

@Composable
private fun QuantityAccessoryContent(
    onFractionClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val fractions = listOf("½", "⅓", "¼", "¾", "⅛", "⅔", "⅜", "/")
        items(fractions.size) { index ->
            FractionChip(fractions[index]) { onFractionClick(fractions[index]) }
        }
    }
}

@Composable
private fun UnitAccessoryContent(
    currentUnit: String?,
    onUnitClick: (String) -> Unit
) {
    var showMoreUnits by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val primaryUnits = listOf(
            "tsp" to "tsp",
            "Tbsp" to "Tbsp",
            "cup" to "cup",
            "oz" to "oz",
            "g" to "g",
            "mL" to "mL",
            "lb" to "lb"
        )

        items(primaryUnits.size) { index ->
            val (value, label) = primaryUnits[index]
            UnitChip(
                label = label,
                selected = currentUnit == value,
                onClick = { onUnitClick(value) }
            )
        }

        item {
            FilterChip(
                selected = showMoreUnits,
                onClick = { showMoreUnits = !showMoreUnits },
                label = { Text(stringResource(R.string.recipe_scaler_more_units)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (showMoreUnits) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }

    if (showMoreUnits) {
        MoreUnitsBottomSheet(
            currentUnit = currentUnit,
            onUnitSelect = {
                onUnitClick(it)
                showMoreUnits = false
            },
            onDismiss = { showMoreUnits = false }
        )
    }
}

@Composable
private fun FractionChip(
    label: String,
    onClick: () -> Unit
) {
    val description = stringResource(R.string.recipe_scaler_insert_fraction, label)
    FilterChip(
        selected = false,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@Composable
private fun UnitChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoreUnitsBottomSheet(
    currentUnit: String?,
    onUnitSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.recipe_scaler_select_unit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val volumeUnits = listOf(
                "tsp" to "tsp", "Tbsp" to "Tbsp", "cup" to "cup",
                "pt" to "pt", "qt" to "qt", "gal" to "gal",
                "mL" to "mL", "L" to "L", "fl oz" to "fl oz"
            )
            val weightUnits = listOf(
                "oz" to "oz", "lb" to "lb", "g" to "g", "kg" to "kg", "mg" to "mg"
            )
            val otherUnits = listOf(
                "pinch" to "pinch", "dash" to "dash", "whole" to "whole",
                "clove" to "clove", "can" to "can", "pkg" to "pkg", "none" to "-"
            )

            UnitCategorySection(
                category = stringResource(R.string.recipe_scaler_volume),
                units = volumeUnits,
                currentUnit = currentUnit,
                onUnitSelect = onUnitSelect
            )
            Spacer(modifier = Modifier.height(16.dp))
            UnitCategorySection(
                category = stringResource(R.string.recipe_scaler_weight),
                units = weightUnits,
                currentUnit = currentUnit,
                onUnitSelect = onUnitSelect
            )
            Spacer(modifier = Modifier.height(16.dp))
            UnitCategorySection(
                category = stringResource(R.string.recipe_scaler_other_units),
                units = otherUnits,
                currentUnit = currentUnit,
                onUnitSelect = onUnitSelect
            )

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitCategorySection(
    category: String,
    units: List<Pair<String, String>>,
    currentUnit: String?,
    onUnitSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            units.forEach { (value, label) ->
                FilterChip(
                    selected = currentUnit == value,
                    onClick = { onUnitSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}
