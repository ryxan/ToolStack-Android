package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.ScaledIngredient
import kotlin.math.roundToInt

object RecipeScalerCalculator {

    // ── Unit catalogue ────────────────────────────────────────────────────────
    // Used by the ingredient row display ("was X unit") and the accessory bar lookup.
    val ALL_UNITS = listOf(
        Pair("none", "-"),
        // US volume
        Pair("tsp",  "tsp"),
        Pair("tbsp", "tbsp"),
        Pair("cup",  "cup"),
        // Metric volume
        Pair("ml",   "ml"),
        Pair("L",    "L"),
        // Weight / other
        Pair("g",    "g"),
        Pair("oz",   "oz"),
        Pair("lb",   "lb"),
        Pair("each", "each")
    )

    // ── Unit families ─────────────────────────────────────────────────────────

    private val US_VOLUME_UNITS    = setOf("tsp", "tbsp", "cup")
    private val METRIC_VOLUME_UNITS = setOf("ml", "L")

    // ── Base conversions (spec) ───────────────────────────────────────────────
    //   3 tsp  = 1 tbsp
    //   16 tbsp = 1 cup  →  48 tsp = 1 cup

    /** Convert any US-volume unit to teaspoons. */
    fun toTsp(amount: Double, unit: String): Double = when (unit) {
        "tsp"  -> amount
        "tbsp" -> amount * 3.0
        "cup"  -> amount * 48.0
        else   -> amount          // should never happen; guard only
    }

    /**
     * Decompose a whole-tsp total into (cups, tbsp, tsp).
     * Only whole cups roll up; anything under 48 tsp stays as tbsp + tsp.
     */
    fun decompose(totalTsp: Int): Triple<Int, Int, Int> {
        val cups = totalTsp / 48
        val remAfterCups = totalTsp % 48
        val tbsp = remAfterCups / 3
        val tsp  = remAfterCups % 3
        return Triple(cups, tbsp, tsp)
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Format a US-volume result in teaspoons as "X cup Y tbsp Z tsp",
     * omitting zero parts and applying the pinch rule (0 < amount < 1 tsp).
     */
    private fun formatUsVolume(totalTsp: Int): String {
        if (totalTsp <= 0) return "0 tsp"
        val (cups, tbsp, tsp) = decompose(totalTsp)
        val parts = mutableListOf<String>()
        if (cups > 0) parts += "$cups cup"
        if (tbsp > 0) parts += "$tbsp tbsp"
        if (tsp  > 0) parts += "$tsp tsp"
        return parts.joinToString(" ")
    }

    /**
     * Format a metric-volume result in millilitres as "X L Y ml" or "X ml",
     * whole numbers only.
     */
    private fun formatMetricVolume(totalMl: Int): String {
        if (totalMl <= 0) return "0 ml"
        return if (totalMl >= 1000) {
            val litres = totalMl / 1000
            val ml     = totalMl % 1000
            if (ml > 0) "$litres L $ml ml" else "$litres L"
        } else {
            "$totalMl ml"
        }
    }

    /**
     * Format a non-volume unit (g, oz, lb, each, etc.).
     * Formats to two decimal places, strips trailing zeros and a trailing decimal
     * point, then appends the unit label; anything that rounds to 0 shows as 1.
     */
    private fun formatOther(scaledQty: Double, unit: String): String {
        val qty = scaledQty.coerceAtLeast(if (scaledQty > 0.0) 0.01 else 0.0)
        val formatted = String.format(java.util.Locale.US, "%.2f", qty)
            .trimEnd('0')
            .trimEnd('.')
            .ifEmpty { "0" }
        val unitLabel = if (unit == "none" || unit.isBlank()) "" else " $unit"
        return "$formatted$unitLabel"
    }

    // ── Core scale + format ───────────────────────────────────────────────────

    /**
     * Scale [ingredient] by [factor] and format the result according to the v1 spec.
     *
     * @param keepOriginalUnits When true the format is skipped and the raw scaled
     *   amount (rounded whole number) is displayed in the original unit without
     *   decomposition or promotion.
     */
    fun scale(ingredient: IngredientItem, factor: Double, keepOriginalUnits: Boolean): ScaledIngredient {
        val originalQty = parseQuantity(ingredient.qtyString)
        val scaledQty   = originalQty * factor
        val displayText = formatQuantity(scaledQty, ingredient.unit, keepOriginalUnits)
        return ScaledIngredient(
            id          = ingredient.id,
            originalQty = originalQty,
            scaledQty   = scaledQty,
            unit        = ingredient.unit,
            name        = ingredient.name,
            displayText = displayText
        )
    }

    /**
     * Format [scaledQty] in [unit] according to the v1 spec.
     *
     * US volume  : decompose to cups / tbsp / tsp (whole numbers; pinch rule).
     * Metric vol : decompose to L + ml (whole numbers).
     * Other      : round to nearest whole in original unit (min 1 if > 0).
     *
     * When [keepOriginalUnits] is true, skip family decomposition and just round
     * the raw quantity to the nearest whole number in [unit].
     */
    fun formatQuantity(scaledQty: Double, unit: String, keepOriginalUnits: Boolean = false): String {
        if (scaledQty == 0.0) return "0${if (unit.isNotBlank() && unit != "none") " $unit" else ""}"

        // ── Keep-original-units path ──────────────────────────────────────────
        if (keepOriginalUnits) {
            val whole = scaledQty.roundToInt().coerceAtLeast(if (scaledQty > 0.0) 1 else 0)
            val label = if (unit == "none" || unit.isBlank()) "" else " $unit"
            return "$whole$label"
        }

        // ── US volume family ──────────────────────────────────────────────────
        if (unit in US_VOLUME_UNITS) {
            val totalTspDouble = toTsp(scaledQty, unit)
            val totalTsp       = totalTspDouble.roundToInt()

            // Pinch rule: result is non-zero but rounds to 0 tsp
            if (totalTsp == 0) return "pinch"

            return formatUsVolume(totalTsp)
        }

        // ── Metric volume family ──────────────────────────────────────────────
        if (unit in METRIC_VOLUME_UNITS) {
            val totalMlDouble = when (unit) {
                "L"  -> scaledQty * 1000.0
                else -> scaledQty               // ml
            }
            val totalMl = totalMlDouble.roundToInt()
            return formatMetricVolume(totalMl)
        }

        // ── Everything else ───────────────────────────────────────────────────
        return formatOther(scaledQty, unit)
    }

    // ── Batch scaling ─────────────────────────────────────────────────────────

    fun scaleIngredients(
        ingredients: List<IngredientItem>,
        multiplier: Double,
        keepOriginalUnits: Boolean = false
    ): List<ScaledIngredient> = ingredients.map { scale(it, multiplier, keepOriginalUnits) }

    // ── Multiplier ────────────────────────────────────────────────────────────

    fun calculateMultiplier(originalServings: Double, desiredServings: Double): Double {
        if (originalServings <= 0.0) return 1.0
        return desiredServings / originalServings
    }

    // ── Copy helpers ──────────────────────────────────────────────────────────

    /** Plain-text shopping list. */
    fun generateShoppingList(scaledIngredients: List<ScaledIngredient>): String {
        if (scaledIngredients.isEmpty()) return ""
        return scaledIngredients.joinToString("\n") { "• ${it.displayText} ${it.name}" }
    }

    /**
     * Generates a recipe in the chosen copy format.
     * [keepOriginalUnits] is passed through so the copy reflects what the user sees.
     */
    fun generateFormattedRecipe(
        scaledIngredients: List<ScaledIngredient>,
        servings: String,
        format: CopyFormat
    ): String {
        if (scaledIngredients.isEmpty()) return ""
        return when (format) {
            CopyFormat.PLAIN -> {
                "Scaled Recipe (Serves $servings)\n\n" +
                    scaledIngredients.joinToString("\n") { "${it.displayText} ${it.name}" }
            }
            CopyFormat.MARKDOWN -> {
                "## Recipe (Serves $servings)\n\n### Ingredients\n\n" +
                    scaledIngredients.joinToString("\n") { "- **${it.displayText}** ${it.name}" }
            }
            CopyFormat.SHOPPING_LIST -> {
                scaledIngredients.joinToString("\n") { "☐ ${it.displayText} ${it.name}" }
            }
        }
    }

    // ── Quantity parser ───────────────────────────────────────────────────────

    /**
     * Parses a quantity string supporting:
     * - Decimal : "1.5", "0.25"
     * - Fraction: "1/2", "3/4"
     * - Mixed   : "1 1/2", "2-1/4"
     * - Unicode fractions: ½ ⅓ ¾ etc.
     */
    fun parseQuantity(inputStr: String): Double {
        if (inputStr.isBlank()) return 0.0
        var str = inputStr.trim().replace(',', '.')

        val unicodeFractions = mapOf(
            '½' to " 1/2", '⅓' to " 1/3", '⅔' to " 2/3", '¼' to " 1/4", '¾' to " 3/4",
            '⅕' to " 1/5", '⅖' to " 2/5", '⅗' to " 3/5", '⅘' to " 4/5", '⅙' to " 1/6",
            '⅚' to " 5/6", '⅛' to " 1/8", '⅜' to " 3/8", '⅝' to " 5/8", '⅞' to " 7/8"
        )
        unicodeFractions.forEach { (char, replacement) -> str = str.replace(char.toString(), replacement) }
        str = str.trim()

        // Mixed number: "1 1/2" or "2-1/4"
        val mixedRegex = Regex("""^(\d+)[\s-]+(\d+)/(\d+)$""")
        mixedRegex.find(str)?.let { m ->
            val whole = m.groupValues[1].toDouble()
            val num   = m.groupValues[2].toDouble()
            val den   = m.groupValues[3].toDouble()
            if (den != 0.0) return whole + num / den
        }

        // Simple fraction: "3/4"
        val fracRegex = Regex("""^(\d+)/(\d+)$""")
        fracRegex.find(str)?.let { m ->
            val num = m.groupValues[1].toDouble()
            val den = m.groupValues[2].toDouble()
            if (den != 0.0) return num / den
        }

        return str.toDoubleOrNull() ?: 0.0
    }
}

enum class CopyFormat {
    PLAIN,
    MARKDOWN,
    SHOPPING_LIST
}
