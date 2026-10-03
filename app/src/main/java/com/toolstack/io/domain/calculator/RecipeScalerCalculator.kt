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

    // US volume family conversion factors (base: tsp)
    private val VOL_MAP = mapOf(
        "tsp" to 1.0, "Tbsp" to 3.0, "cup" to 48.0
    )

    // Metric volume family conversion factors (base: mL)
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
     *
     * Rounding by unit family (spec):
     * - tsp / Tbsp  → kitchen fractions (1/8, 1/4, 1/2, 3/4, …)
     * - cup         → kitchen fractions (1/4, 1/3, 1/2, 2/3, 3/4, …)
     * - mL          → nearest 5 (nearest 1 when < 100 mL)
     * - L           → nearest 0.05
     * - everything else → fractions where possible, else 2 dp
     */
    private fun formatQuantity(qty: Double, unit: String, state: IngredientState): String {
        if (qty == 0.0) return "0"

        val (value, finalUnit) = tryConvertLargerUnits(qty, unit, state)

        val unitLabel = if (finalUnit == "none") "" else " $finalUnit"

        return when (finalUnit) {
            "mL" -> {
                val rounded = roundMl(value)
                "$rounded$unitLabel"
            }
            "L" -> {
                val rounded = roundL(value)
                // Show at most 2 decimal places, strip trailing zeros
                val formatted = String.format(Locale.US, "%.2f", rounded)
                    .trimEnd('0').trimEnd('.')
                "$formatted$unitLabel"
            }
            else -> {
                val frac = toFraction(value, finalUnit)
                if (frac != null) {
                    "$frac$unitLabel"
                } else {
                    String.format(Locale.US, "%.2f%s", value, unitLabel)
                        .replace(Regex("\\.?0+$"), "")
                }
            }
        }
    }

    /** Round millilitres: nearest 1 when < 100, nearest 5 otherwise. */
    private fun roundMl(ml: Double): Int {
        return if (ml < 100.0) {
            ml.roundToInt()
        } else {
            (ml / 5.0).roundToInt() * 5
        }
    }

    /** Round litres to nearest 0.05. */
    private fun roundL(litres: Double): Double {
        return (litres / 0.05).roundToInt() * 0.05
    }

    /**
     * Promotes a quantity to the most readable unit within its family.
     *
     * Unit families (spec):
     * - US volume   : tsp / Tbsp / cup only
     *     < 3 tsp          → tsp
     *     3 tsp – < 12 tsp → Tbsp
     *     ≥ 12 tsp (4 Tbsp)→ cup (+ remainder shown via fractions)
     * - Metric volume: mL / L
     *     < 1000 mL        → mL
     *     ≥ 1000 mL        → L
     * - Everything else (fl oz, pt, qt, gal, oz, lb, g, kg, each, none, …):
     *     no promotion — scale and display in the original unit.
     */
    private fun tryConvertLargerUnits(qty: Double, unit: String, state: IngredientState): Pair<Double, String> {
        // ── US volume family: tsp / Tbsp / cup ────────────────────────────────
        if (unit == "tsp" || unit == "Tbsp" || unit == "cup") {
            val tsp = qty * (VOL_MAP[unit] ?: 1.0)   // VOL_MAP: tsp=1, Tbsp=3, cup=48
            return when {
                tsp >= 12.0 -> Pair(tsp / 48.0, "cup")   // ≥ 4 Tbsp → cups
                tsp >= 3.0  -> Pair(tsp / 3.0,  "Tbsp")  // ≥ 3 tsp  → Tbsp
                else        -> Pair(tsp,         "tsp")
            }
        }

        // ── Metric volume family: mL / L ──────────────────────────────────────
        if (unit == "mL" || unit == "L") {
            val ml = qty * (MET_LIQUID_MAP[unit] ?: 1.0)  // MET_LIQUID_MAP: mL=1, L=1000
            return if (ml >= 1000.0) Pair(ml / 1000.0, "L") else Pair(ml, "mL")
        }

        // ── All other units: no auto-promotion ───────────────────────────────
        // fl oz, pt, qt, gal, oz, lb, g, kg, each, none, etc.
        // Scale as-is; future spec can add promotion for these.
        return Pair(qty, unit)
    }

    /**
     * Converts a decimal to a fractional string using the allowed fractions for the given unit.
     *
     * Fraction sets (spec):
     * - tsp / Tbsp : 1/8, 1/4, 1/2, 3/4  (eighth-steps — practical kitchen measures)
     * - cup        : 1/4, 1/3, 1/2, 2/3, 3/4
     * - everything else: same as tsp/Tbsp (1/8 steps)
     *
     * Returns null if no fraction is close enough (falls back to decimal in the caller).
     */
    private fun toFraction(value: Double, unit: String = ""): String? {
        if (value < 0.0) return null
        val whole = floor(value).toInt()
        val frac = value - whole

        if (frac < 0.001) return if (whole == 0) null else whole.toString()

        val fractions = when (unit) {
            "cup" -> listOf(
                Pair(1, 4), Pair(1, 3), Pair(1, 2), Pair(2, 3), Pair(3, 4)
            )
            else -> listOf(
                // tsp, Tbsp, and all other units: eighth-steps
                Pair(1, 8), Pair(1, 4), Pair(3, 8), Pair(1, 2),
                Pair(5, 8), Pair(3, 4), Pair(7, 8)
            )
        }

        for ((num, den) in fractions) {
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

        val (value, finalUnit) = tryConvertLargerUnits(qty, unit, state)
        val unitLabel = if (finalUnit == "none") "" else " $finalUnit"

        // Apply the same ml/L rounding as the fraction path for consistency.
        return when (finalUnit) {
            "mL" -> "${roundMl(value)}$unitLabel"
            "L"  -> {
                val rounded = roundL(value)
                val formatted = String.format(Locale.US, "%.2f", rounded)
                    .trimEnd('0').trimEnd('.')
                "$formatted$unitLabel"
            }
            else -> {
                val formatted = String.format(Locale.US, "%.2f", value)
                    .trimEnd('0').trimEnd('.')
                "$formatted$unitLabel"
            }
        }
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
