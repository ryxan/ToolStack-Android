package com.toolstack.io.domain.ai

import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import java.util.UUID

/**
 * Maps an [AiRecipeResult] to [IngredientItem] rows for the Recipe Scaler.
 *
 * Pure function with no Android or Firebase dependencies — same testability
 * as [com.toolstack.io.domain.calculator.RecipeOcrParser].
 *
 * Rules:
 * - Units are resolved to canonical [RecipeScalerCalculator.ALL_UNITS] keys.
 *   An unrecognised unit folds to "none" with the original unit text preserved
 *   at the start of the name so no information is lost.
 * - Fully blank rows are dropped; results are capped at [AiImportConfig.MAX_INGREDIENTS].
 * - A non-blank quantity that [RecipeScalerCalculator.parseQuantity] cannot
 *   parse flags the row `lowConfidence` so the existing amber hint fires.
 */
object AiRecipeMapper {

    private val KNOWN_UNIT_KEYS = RecipeScalerCalculator.ALL_UNITS.map { it.first }.toSet()

    fun map(result: AiRecipeResult): List<IngredientItem> =
        result.ingredients
            .mapNotNull { mapIngredient(it) }
            .take(AiImportConfig.MAX_INGREDIENTS)

    private fun mapIngredient(raw: AiIngredient): IngredientItem? {
        val qty = raw.qty?.trim()?.take(AiImportConfig.MAX_QTY_LENGTH).orEmpty()
        val name = raw.name?.trim()?.take(AiImportConfig.MAX_NAME_LENGTH).orEmpty()
        if (qty.isEmpty() && name.isEmpty() && raw.unit.isNullOrBlank()) return null

        val (unit, finalName) = resolveUnit(raw.unit, name)
        val lowConfidence = qty.isNotEmpty() && RecipeScalerCalculator.parseQuantity(qty) == 0.0

        return IngredientItem(
            id = UUID.randomUUID().toString(),
            qtyString = qty,
            unit = unit,
            name = finalName,
            lowConfidence = lowConfidence
        )
    }

    private fun resolveUnit(rawUnit: String?, name: String): Pair<String, String> {
        val unit = rawUnit?.trim().orEmpty()
        if (unit.isEmpty()) return "none" to name

        RecipeScalerCalculator.canonicalUnit(unit).takeIf { it in KNOWN_UNIT_KEYS }
            ?.let { return it to name }

        // Surface forms the model may emit despite the enum constraint:
        // plurals and dotted abbreviations ("cups", "oz.", "lbs").
        val folded = unit.lowercase().trimEnd('.', 's')
        RecipeScalerCalculator.canonicalUnit(folded).takeIf { it in KNOWN_UNIT_KEYS }
            ?.let { return it to name }

        return "none" to "$unit $name".trim()
    }
}
