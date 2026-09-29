package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.IngredientState
import com.toolstack.io.domain.model.ScaledIngredient
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object RecipeScalerCalculator {

    val ALL_UNITS = listOf(
        Pair("none", "-"),
        // Liquid units
        Pair("tsp", "tsp"),
        Pair("Tbsp", "Tbsp"),
        Pair("fl oz", "fl oz"),
        Pair("cup", "cup"),
        Pair("pt", "pt"),
        Pair("qt", "qt"),
        Pair("gal", "gal"),
        Pair("mL", "mL"),
        Pair("L", "L"),
        // Dry/weight units
        Pair("g", "g"),
        Pair("kg", "kg"),
        Pair("oz", "oz"),
        Pair("lb", "lb")
    )

    // Categorized units for display
    val LIQUID_UNITS = listOf(
        Pair("tsp", "tsp"),
        Pair("Tbsp", "Tbsp"),
        Pair("fl oz", "fl oz"),
        Pair("cup", "cup"),
        Pair("pt", "pt"),
        Pair("qt", "qt"),
        Pair("gal", "gal"),
        Pair("mL", "mL"),
        Pair("L", "L")
    )

    val DRY_UNITS = listOf(
        Pair("g", "g"),
        Pair("kg", "kg"),
        Pair("oz", "oz"),
        Pair("lb", "lb")
    )

    /**
     * Automatically detects whether a unit is for liquid or dry ingredients.
     * Volume units (tsp, Tbsp, cup, fl oz, etc.) default to LIQUID.
     * Weight units (g, kg, oz, lb) default to DRY.
     * Exception: cup/tsp/Tbsp can be used for both, but we default to LIQUID
     * and let the conversion logic handle appropriately.
     */
    fun detectIngredientState(unit: String): IngredientState {
        return when (unit) {
            // Weight units are always DRY
            "g", "kg", "oz", "lb" -> IngredientState.DRY
            // Volume units default to LIQUID
            "tsp", "Tbsp", "fl oz", "cup", "pt", "qt", "gal", "mL", "L" -> IngredientState.LIQUID
            // No unit selected
            else -> IngredientState.DRY
        }
    }

    private val VOL_MAP = mapOf(
        "tsp" to 1.0, "Tbsp" to 3.0, "fl oz" to 6.0, "cup" to 48.0,
        "pt" to 96.0, "qt" to 192.0, "gal" to 768.0
    )

    private val SOLID_VOL_UNITS = setOf("tsp", "Tbsp", "cup")
    private val IMP_WEIGHT_MAP = mapOf("oz" to 1.0, "lb" to 16.0)
    private val MET_SOLID_MAP = mapOf("g" to 1.0, "kg" to 1000.0)
    private val MET_LIQUID_MAP = mapOf("mL" to 1.0, "L" to 1000.0)

    /**
     * Parses a quantity string supporting:
     * - Decimal: "1.5", "0.25"
     * - Fractions: "1/2", "3/4"
     * - Mixed numbers: "1 1/2", "2-1/4"
     * - Unicode fractions: ½, ⅓, ¾, etc.
     */
    fun parseQuantity(inputStr: String): Double {
        if (inputStr.isBlank()) return 0.0
        var str = inputStr.trim().replace(',', '.')

        val unicodeFractions = mapOf(
            '½' to " 1/2", '⅓' to " 1/3", '⅔' to " 2/3", '¼' to " 1/4", '¾' to " 3/4",
            '⅕' to " 1/5", '⅖' to " 2/5", '⅗' to " 3/5", '⅘' to " 4/5", '⅙' to " 1/6",
            '⅚' to " 5/6", '⅛' to " 1/8", '⅜' to " 3/8", '⅝' to " 5/8", '⅞' to " 7/8"
        )

        unicodeFractions.forEach { (char, replacement) ->
            str = str.replace(char.toString(), replacement)
        }
        str = str.trim()

        // Match mixed numbers "1 1/2" or "2-1/4"
        val mixedRegex = Regex("""^(\d+)[\s-]+(\d+)/(\d+)$""")
        mixedRegex.find(str)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val num = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val den = match.groupValues[3].toDoubleOrNull() ?: 1.0
            if (den != 0.0) return whole + (num / den)
        }

        // Simple fraction "3/4"
        val fracRegex = Regex("""^(\d+)/(\d+)$""")
        fracRegex.find(str)?.let { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val den = match.groupValues[2].toDoubleOrNull() ?: 1.0
            if (den != 0.0) return num / den
        }

        return str.toDoubleOrNull() ?: 0.0
    }

    /**
     * Scales a list of ingredients by a given multiplier and optionally converts units.
     */
    fun scaleIngredients(
        ingredients: List<IngredientItem>,
        multiplier: Double
    ): List<ScaledIngredient> {
        return ingredients.map { ingredient ->
            val originalQty = parseQuantity(ingredient.qtyString)
            val scaledQty = originalQty * multiplier
            val displayText = formatQuantity(scaledQty, ingredient.unit, ingredient.state)

            ScaledIngredient(
                id = ingredient.id,
                originalQty = originalQty,
                scaledQty = scaledQty,
                unit = ingredient.unit,
                state = ingredient.state,
                name = ingredient.name,
                displayText = displayText
            )
        }
    }

    /**
     * Formats a scaled quantity with optional unit conversion and fractional display.
     */
    private fun formatQuantity(qty: Double, unit: String, state: IngredientState): String {
        if (qty == 0.0) return "0"

        val converted = tryConvertLargerUnits(qty, unit, state)
        val value = converted.first
        val finalUnit = converted.second

        val frac = toFraction(value)
        val unitLabel = if (finalUnit == "none") "" else " $finalUnit"

        return if (frac != null) {
            "$frac$unitLabel"
        } else {
            String.format(Locale.US, "%.2f%s", value, unitLabel).replace(Regex("\\.?0+$"), "")
        }
    }

    /**
     * Attempts to convert a quantity to a more appropriate unit based on:
     * - Ingredient state (DRY vs LIQUID)
     * - Magnitude (prefer readable quantities)
     * - Culinary conventions (cups for dry, not gallons)
     * 
     * Returns (convertedValue, newUnit) or (originalValue, originalUnit) if no conversion.
     */
    private fun tryConvertLargerUnits(qty: Double, unit: String, state: IngredientState): Pair<Double, String> {
        // ── DRY INGREDIENTS using volume units (tsp, Tbsp, cup) ────────────────
        // For dry ingredients, we STOP at cups — nobody measures dry ingredients in gallons.
        if (VOL_MAP.containsKey(unit) && state == IngredientState.DRY && unit in SOLID_VOL_UNITS) {
            val tspValue = qty * (VOL_MAP[unit] ?: 1.0)
            return when {
                // Stop at cups for dry ingredients (48 tsp = 1 cup)
                tspValue >= 48.0 -> Pair(tspValue / 48.0, "cup")
                tspValue >= 3.0 -> Pair(tspValue / 3.0, "Tbsp")
                else -> Pair(qty, unit)
            }
        }

        // ── LIQUID INGREDIENTS using volume units ──────────────────────────────
        // For liquids, convert through the full scale, but with sensible thresholds.
        // Most recipes don't exceed quarts, so gallons only for very large batches.
        if (VOL_MAP.containsKey(unit) && state == IngredientState.LIQUID) {
            val tspValue = qty * (VOL_MAP[unit] ?: 1.0)
            return when {
                // Use quarts/gallons only for very large quantities
                tspValue >= 1536.0 -> Pair(tspValue / 768.0, "gal")  // 2+ gallons
                tspValue >= 384.0 -> Pair(tspValue / 192.0, "qt")     // 2+ quarts
                tspValue >= 192.0 -> Pair(tspValue / 96.0, "pt")      // 2+ pints
                tspValue >= 96.0 -> Pair(tspValue / 48.0, "cup")      // 2+ cups
                tspValue >= 12.0 -> Pair(tspValue / 6.0, "fl oz")     // 2+ fl oz
                tspValue >= 3.0 -> Pair(tspValue / 3.0, "Tbsp")
                else -> Pair(qty, unit)
            }
        }

        // ── Imperial weight (oz, lb) ────────────────────────────────────────────
        // Convert to pounds only when we have at least 1 lb
        if (IMP_WEIGHT_MAP.containsKey(unit)) {
            val ozValue = qty * (IMP_WEIGHT_MAP[unit] ?: 1.0)
            return if (ozValue >= 16.0) Pair(ozValue / 16.0, "lb") else Pair(qty, unit)
        }

        // ── Metric weight for DRY ingredients (g, kg) ───────────────────────────
        if (state == IngredientState.DRY && MET_SOLID_MAP.containsKey(unit)) {
            val gValue = qty * (MET_SOLID_MAP[unit] ?: 1.0)
            return if (gValue >= 1000.0) Pair(gValue / 1000.0, "kg") else Pair(qty, unit)
        }

        // ── Metric volume for LIQUID ingredients (mL, L) ────────────────────────
        if (state == IngredientState.LIQUID && MET_LIQUID_MAP.containsKey(unit)) {
            val mlValue = qty * (MET_LIQUID_MAP[unit] ?: 1.0)
            return if (mlValue >= 1000.0) Pair(mlValue / 1000.0, "L") else Pair(qty, unit)
        }

        return Pair(qty, unit)
    }

    /**
     * Converts a decimal to a fractional string if it's close to a common fraction.
     * Returns null if no suitable fraction is found.
     */
    private fun toFraction(value: Double): String? {
        if (value < 0.0) return null
        val whole = floor(value).toInt()
        val frac = value - whole

        if (frac < 0.001) return if (whole == 0) null else whole.toString()

        val commonFractions = listOf(
            Pair(1, 2), Pair(1, 3), Pair(2, 3), Pair(1, 4), Pair(3, 4),
            Pair(1, 8), Pair(3, 8), Pair(5, 8), Pair(7, 8),
            Pair(1, 16), Pair(3, 16), Pair(5, 16), Pair(7, 16),
            Pair(9, 16), Pair(11, 16), Pair(13, 16), Pair(15, 16)
        )

        for ((num, den) in commonFractions) {
            val testVal = num.toDouble() / den.toDouble()
            if (abs(frac - testVal) < 0.02) {
                return if (whole > 0) "$whole $num/$den" else "$num/$den"
            }
        }

        return null
    }

    /**
     * Generates a plain-text shopping list from scaled ingredients.
     */
    fun generateShoppingList(scaledIngredients: List<ScaledIngredient>): String {
        if (scaledIngredients.isEmpty()) return ""
        return scaledIngredients.joinToString("\n") { ingredient ->
            "• ${ingredient.displayText} ${ingredient.name}"
        }
    }

    /**
     * Formats quantity as a simplified decimal (no fractions).
     */
    fun formatQuantitySimplified(qty: Double, unit: String, state: IngredientState): String {
        if (qty == 0.0) return "0"

        val converted = tryConvertLargerUnits(qty, unit, state)
        val value = converted.first
        val finalUnit = converted.second

        val unitLabel = if (finalUnit == "none") "" else " $finalUnit"
        
        // Format with up to 2 decimal places, but strip unnecessary trailing zeros
        val formatted = String.format(Locale.US, "%.2f", value)
            .trimEnd('0')  // Remove trailing zeros
            .trimEnd('.')  // Remove trailing decimal point if no decimals left
        
        return "$formatted$unitLabel"
    }

    /**
     * Generates a markdown-formatted recipe.
     */
    fun generateMarkdownRecipe(scaledIngredients: List<ScaledIngredient>, servings: String): String {
        if (scaledIngredients.isEmpty()) return ""
        val header = "## Recipe (Serves $servings)\n\n### Ingredients\n\n"
        val items = scaledIngredients.joinToString("\n") { ingredient ->
            "- **${ingredient.displayText}** ${ingredient.name}"
        }
        return header + items
    }

    /**
     * Generates a recipe with selected format (plain, markdown, shopping list).
     */
    fun generateFormattedRecipe(
        scaledIngredients: List<ScaledIngredient>,
        servings: String,
        format: CopyFormat,
        simplifyFractions: Boolean
    ): String {
        if (scaledIngredients.isEmpty()) return ""
        
        val ingredientsWithFormat = if (simplifyFractions) {
            scaledIngredients.map { ingredient ->
                ingredient.copy(
                    displayText = formatQuantitySimplified(
                        ingredient.scaledQty,
                        ingredient.unit,
                        ingredient.state
                    )
                )
            }
        } else {
            scaledIngredients
        }

        return when (format) {
            CopyFormat.PLAIN -> {
                "Scaled Recipe (Serves $servings)\n\n" +
                ingredientsWithFormat.joinToString("\n") { ingredient ->
                    "${ingredient.displayText} ${ingredient.name}"
                }
            }
            CopyFormat.MARKDOWN -> {
                "## Recipe (Serves $servings)\n\n### Ingredients\n\n" +
                ingredientsWithFormat.joinToString("\n") { ingredient ->
                    "- **${ingredient.displayText}** ${ingredient.name}"
                }
            }
            CopyFormat.SHOPPING_LIST -> {
                ingredientsWithFormat.joinToString("\n") { ingredient ->
                    "☐ ${ingredient.displayText} ${ingredient.name}"
                }
            }
        }
    }

    /**
     * Calculates the multiplier needed to scale a recipe.
     */
    fun calculateMultiplier(originalServings: Double, desiredServings: Double): Double {
        if (originalServings <= 0.0) return 1.0
        return desiredServings / originalServings
    }
}

enum class CopyFormat {
    PLAIN,
    MARKDOWN,
    SHOPPING_LIST
}
