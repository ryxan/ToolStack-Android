package com.toolstack.io.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single convertible unit.
 *
 * @param label    Display name shown in the dropdown (e.g. "Metres", "Feet").
 * @param symbol   Short symbol shown next to the value (e.g. "m", "ft").
 * @param toBase   Converts a value in this unit to the category's base unit.
 *                 For linear units this is a simple multiplier;
 *                 temperature uses a lambda for offset conversions.
 * @param fromBase Converts a value from the base unit to this unit.
 * @param minValue Physical lower bound for a value expressed in this unit,
 *                 or null if unbounded. Used to reject impossible inputs and
 *                 results (e.g. 0 for fuel-economy reciprocals, -273.15 for
 *                 Celsius, -459.67 for Fahrenheit, 0 for Kelvin/Rankine).
 */
data class UnitEntry(
    val label: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double,
    val minValue: Double? = null
)

/**
 * A group of related [UnitEntry] items.
 *
 * @param id          Stable identifier used by navigation routes and persisted
 *                    preferences. Never changes across releases.
 * @param name        Category display name (e.g. "Length").
 * @param icon        Icon shown on the category list card.
 * @param description Short subtitle shown under the name on the list card.
 * @param units       All units in this category.
 */
data class UnitCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val description: String,
    val units: List<UnitEntry>
)
