package com.toolstack.io.ui.recipescaler

import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the v1 recipe-scaler spec.
 *
 * All examples come directly from the spec document.
 */
class RecipeScalerUiStateTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ingredient(qty: String, unit: String, name: String = "item") =
        IngredientItem(id = "1", qtyString = qty, unit = unit, name = name)

    private fun format(qty: Double, unit: String, keepOriginal: Boolean = false) =
        RecipeScalerCalculator.formatQuantity(qty, unit, keepOriginal)

    private fun scale(qty: String, unit: String, factor: Double, keepOriginal: Boolean = false): String {
        val parsed  = RecipeScalerCalculator.parseQuantity(qty)
        val scaled  = parsed * factor
        return format(scaled, unit, keepOriginal)
    }

    // ── toTsp ─────────────────────────────────────────────────────────────────

    @Test fun `toTsp tsp stays as-is`()          = assertEquals(3.0,  RecipeScalerCalculator.toTsp(3.0,  "tsp"),  0.001)
    @Test fun `toTsp tbsp multiplied by 3`()      = assertEquals(9.0,  RecipeScalerCalculator.toTsp(3.0,  "tbsp"), 0.001)
    @Test fun `toTsp cup multiplied by 48`()      = assertEquals(48.0, RecipeScalerCalculator.toTsp(1.0,  "cup"),  0.001)
    @Test fun `toTsp quarter cup is 12 tsp`()     = assertEquals(12.0, RecipeScalerCalculator.toTsp(0.25, "cup"),  0.001)

    // ── decompose ─────────────────────────────────────────────────────────────

    @Test fun `decompose 48 tsp is exactly 1 cup`() {
        val (cups, tbsp, tsp) = RecipeScalerCalculator.decompose(48)
        assertEquals(1, cups); assertEquals(0, tbsp); assertEquals(0, tsp)
    }

    @Test fun `decompose 47 tsp stays under 1 cup`() {
        val (cups, tbsp, tsp) = RecipeScalerCalculator.decompose(47)
        assertEquals(0, cups); assertEquals(15, tbsp); assertEquals(2, tsp)
    }

    @Test fun `decompose 50 tsp is 1 cup 2 tsp`() {
        val (cups, tbsp, tsp) = RecipeScalerCalculator.decompose(50)
        assertEquals(1, cups); assertEquals(0, tbsp); assertEquals(2, tsp)
    }

    @Test fun `decompose 3 tsp is 1 tbsp`() {
        val (cups, tbsp, tsp) = RecipeScalerCalculator.decompose(3)
        assertEquals(0, cups); assertEquals(1, tbsp); assertEquals(0, tsp)
    }

    @Test fun `decompose 5 tsp is 1 tbsp 2 tsp`() {
        val (cups, tbsp, tsp) = RecipeScalerCalculator.decompose(5)
        assertEquals(0, cups); assertEquals(1, tbsp); assertEquals(2, tsp)
    }

    // ── US volume spec examples ───────────────────────────────────────────────

    /** 50 tsp → 1 cup 2 tsp */
    @Test fun `50 tsp formats as 1 cup 2 tsp`() =
        assertEquals("1 cup 2 tsp", format(50.0, "tsp"))

    /** 1/3 cup → 5 Tbsp 1 tsp  (1/3 cup = 16 tsp; 16/3=5 tbsp r1) */
    @Test fun `one third cup formats as 5 Tbsp 1 tsp`() =
        assertEquals("5 Tbsp 1 tsp", scale("1/3", "cup", 1.0))

    /** 1/4 cup × 3 → 12 Tbsp  (36 tsp total — under 48, no cup rollup) */
    @Test fun `quarter cup times 3 is 12 Tbsp not 1 cup`() =
        assertEquals("12 Tbsp", scale("1/4", "cup", 3.0))

    /** 2 tsp × 2.5 → 5 tsp → 1 Tbsp 2 tsp */
    @Test fun `2 tsp scaled by 2·5 is 1 Tbsp 2 tsp`() =
        assertEquals("1 Tbsp 2 tsp", scale("2", "tsp", 2.5))

    /** 12 cups flour → 12 cups */
    @Test fun `12 cups stays as 12 cups`() =
        assertEquals("12 cup", format(12.0, "cup"))

    // ── 47 vs 48 tsp boundary ─────────────────────────────────────────────────

    @Test fun `47 tsp does not roll into a cup`() =
        assertEquals("15 Tbsp 2 tsp", format(47.0, "tsp"))

    @Test fun `48 tsp is exactly 1 cup`() =
        assertEquals("1 cup", format(48.0, "tsp"))

    // ── Pinch rule ────────────────────────────────────────────────────────────

    @Test fun `tiny amount under 1 tsp shows pinch`() =
        assertEquals("pinch", format(0.4, "tsp"))

    @Test fun `amount just above 0 5 tsp rounds to 1 tsp not pinch`() =
        assertEquals("1 tsp", format(0.6, "tsp"))

    @Test fun `exactly 0 tsp is 0 tsp not pinch`() =
        assertEquals("0 tsp", format(0.0, "tsp"))

    // ── Metric volume spec examples ───────────────────────────────────────────

    /** 250 ml × 2 → 500 mL */
    @Test fun `250 ml times 2 is 500 mL`() =
        assertEquals("500 mL", scale("250", "ml", 2.0))

    /** 250 ml × 6 → 1 L 500 mL */
    @Test fun `250 ml times 6 is 1 L 500 mL`() =
        assertEquals("1 L 500 mL", scale("250", "ml", 6.0))

    /** 900 ml × 1.1 → 990 mL */
    @Test fun `900 ml times 1·1 is 990 mL`() =
        assertEquals("990 mL", scale("900", "ml", 1.1))

    // ── 999 vs 1000 ml boundary ───────────────────────────────────────────────

    @Test fun `999 ml stays as mL`() =
        assertEquals("999 mL", format(999.0, "ml"))

    @Test fun `1000 ml becomes 1 L`() =
        assertEquals("1 L", format(1000.0, "ml"))

    /** 1500 ml → 1 L 500 mL */
    @Test fun `1500 ml becomes 1 L 500 mL`() =
        assertEquals("1 L 500 mL", format(1500.0, "ml"))

    /** 2000 ml → 2 L */
    @Test fun `2000 ml becomes 2 L`() =
        assertEquals("2 L", format(2000.0, "ml"))

    /** L unit input: 0.25 L → 250 mL */
    @Test fun `quarter litre formats as 250 mL`() =
        assertEquals("250 mL", format(0.25, "L"))

    /** L unit input: 1.5 L → 1 L 500 mL */
    @Test fun `1·5 L formats as 1 L 500 mL`() =
        assertEquals("1 L 500 mL", format(1.5, "L"))

    // ── Other units ───────────────────────────────────────────────────────────

    /** 2.5 eggs → 3 */
    @Test fun `2·5 each rounds up to 3`() =
        assertEquals("3 each", format(2.5, "each"))

    /** Nonzero that rounds to 0 → 1 */
    @Test fun `tiny nonzero other unit shows 1 not 0`() =
        assertEquals("1 g", format(0.3, "g"))

    @Test fun `integer g quantity`() =
        assertEquals("5 g", format(5.0, "g"))

    @Test fun `oz rounds to whole`() =
        assertEquals("3 oz", format(3.4, "oz"))

    @Test fun `lb rounds to whole`() =
        assertEquals("2 lb", format(2.0, "lb"))

    // ── keepOriginalUnits toggle ──────────────────────────────────────────────

    /** With keepOriginalUnits=true, 48 tsp stays "48 tsp", not "1 cup" */
    @Test fun `keep original units skips cup decomposition`() =
        assertEquals("48 tsp", format(48.0, "tsp", keepOriginal = true))

    /** With keepOriginalUnits=true, 1000 ml stays "1000 mL", not "1 L" */
    @Test fun `keep original units skips L promotion`() =
        assertEquals("1000 mL", format(1000.0, "ml", keepOriginal = true))

    /** keepOriginalUnits still rounds to a whole number */
    @Test fun `keep original units rounds 2·5 tbsp to 3 Tbsp`() =
        assertEquals("3 Tbsp", format(2.5, "tbsp", keepOriginal = true))

    // ── Unit key normalisation ────────────────────────────────────────────────

    @Test fun `canonicalUnit maps accessory-bar labels to keys`() {
        assertEquals("tbsp", RecipeScalerCalculator.canonicalUnit("Tbsp"))
        assertEquals("ml",   RecipeScalerCalculator.canonicalUnit("mL"))
        assertEquals("L",    RecipeScalerCalculator.canonicalUnit("l"))
        assertEquals("pt",   RecipeScalerCalculator.canonicalUnit("pt"))
    }

    @Test fun `legacy Tbsp unit key still scales as US volume`() =
        assertEquals("2 Tbsp 2 tsp", format(2.5, "Tbsp"))

    // ── UiState recalculate ───────────────────────────────────────────────────

    @Test fun `recalculate produces scaled ingredients and multiplier`() {
        val state = RecipeScalerUiState(
            originalServingsText = "2",
            desiredServingsText  = "4",
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "1", unit = "cup", name = "flour")
            )
        ).recalculate()

        assertEquals("2", state.multiplierText)
        assertEquals(1, state.scaledIngredients.size)
        assertEquals("flour", state.scaledIngredients.first().name)
        assertTrue(state.scaledIngredients.first().displayText.isNotBlank())
    }

    @Test fun `recalculate stays empty without both serving counts`() {
        val state = RecipeScalerUiState(
            originalServingsText = "4",
            desiredServingsText  = "",
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "1", unit = "cup", name = "flour")
            )
        ).recalculate()

        assertTrue(state.scaledIngredients.isEmpty())
        assertEquals("", state.multiplierText)
    }

    // ── >4× warning ───────────────────────────────────────────────────────────

    @Test fun `multiplier above 4 triggers scale warning`() {
        val state = RecipeScalerUiState(
            originalServingsText = "1",
            desiredServingsText  = "5",
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "1", unit = "cup", name = "flour")
            )
        ).recalculate()

        assertTrue(state.showScaleWarning)
    }

    @Test fun `multiplier exactly 4 does not trigger scale warning`() {
        val state = RecipeScalerUiState(
            originalServingsText = "1",
            desiredServingsText  = "4",
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "1", unit = "cup", name = "flour")
            )
        ).recalculate()

        assertFalse(state.showScaleWarning)
    }

    @Test fun `multiplier below 4 does not trigger scale warning`() {
        val state = RecipeScalerUiState(
            originalServingsText = "2",
            desiredServingsText  = "4",
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "1", unit = "cup", name = "flour")
            )
        ).recalculate()

        assertFalse(state.showScaleWarning)
    }

    // ── keepOriginalUnits in recalculate ─────────────────────────────────────

    @Test fun `keepOriginalUnits flag propagates through recalculate`() {
        val state = RecipeScalerUiState(
            originalServingsText = "1",
            desiredServingsText  = "1",
            keepOriginalUnits    = true,
            ingredients = listOf(
                IngredientItem(id = "1", qtyString = "48", unit = "tsp", name = "sugar")
            )
        ).recalculate()

        assertEquals("48 tsp", state.scaledIngredients.first().displayText)
    }
}
