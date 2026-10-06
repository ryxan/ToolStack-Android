package com.toolstack.io.ui.recipescaler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolstack.io.R

/**
 * Input accessory bar that appears at the bottom of the screen, changing content based
 * on which ingredient field is focused (fraction chips for QUANTITY, unit picker for UNIT).
 *
 * IME inset handling is owned by the parent [Box] in RecipeScalerScreen via
 * [Modifier.imePadding], so this composable does not apply any window-inset padding itself,
 * preventing double-counting.
 */
@Composable
fun IngredientInputAccessoryBar(
    focusedIngredientId: String?,
    focusedField: FocusedIngredientField,
    currentUnit: String?,
    onFractionClick: (String) -> Unit,
    onUnitClick: (String) -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusedIngredientId == null) return
    when (focusedField) {
        FocusedIngredientField.QUANTITY,
        FocusedIngredientField.UNIT -> Unit
        FocusedIngredientField.NAME,
        FocusedIngredientField.NONE -> return
    }

    // surfaceContainerHigh gives clear visual separation from the card content below
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        when (focusedField) {
            FocusedIngredientField.QUANTITY -> QuantityAccessoryContent(onFractionClick)
            FocusedIngredientField.UNIT -> UnitAccessoryContent(
                currentUnit = currentUnit,
                onUnitClick = onUnitClick,
                onNextClick = onNextClick
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val fractions = listOf("½", "⅓", "¼", "¾", "⅛", "⅔", "⅜", "/")
        items(fractions.size) { index ->
            FractionChip(fractions[index]) { onFractionClick(fractions[index]) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitAccessoryContent(
    currentUnit: String?,
    onUnitClick: (String) -> Unit,
    onNextClick: () -> Unit
) {
    val volumeUnits = listOf(
        "tsp" to "tsp", "tbsp" to "Tbsp", "cup" to "cup",
        "pt" to "pt", "qt" to "qt", "gal" to "gal",
        "ml" to "mL", "L" to "L", "fl oz" to "fl oz"
    )
    val weightUnits = listOf(
        "oz" to "oz", "lb" to "lb", "g" to "g", "kg" to "kg", "mg" to "mg"
    )
    val otherUnits = listOf(
        "pinch" to "pinch", "dash" to "dash", "whole" to "whole",
        "clove" to "clove", "can" to "can", "pkg" to "pkg", "none" to "-"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InlineUnitSection(
            label = stringResource(R.string.recipe_scaler_volume),
            units = volumeUnits,
            currentUnit = currentUnit,
            onUnitClick = onUnitClick
        )
        InlineUnitSection(
            label = stringResource(R.string.recipe_scaler_weight),
            units = weightUnits,
            currentUnit = currentUnit,
            onUnitClick = onUnitClick
        )
        InlineUnitSection(
            label = stringResource(R.string.recipe_scaler_other_units),
            units = otherUnits,
            currentUnit = currentUnit,
            onUnitClick = onUnitClick
        )

        // Next button — always visible so user can advance without picking a unit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onNextClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.recipe_scaler_next),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineUnitSection(
    label: String,
    units: List<Pair<String, String>>,
    currentUnit: String?,
    onUnitClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            units.forEach { (value, display) ->
                UnitChip(
                    label = display,
                    selected = currentUnit == value,
                    onClick = { onUnitClick(value) }
                )
            }
        }
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
