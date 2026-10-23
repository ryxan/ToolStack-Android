package com.toolstack.io.domain.ai

import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeMapperTest {

    private fun result(vararg items: AiIngredient) =
        AiRecipeResult(ingredients = items.toList())

    @Test
    fun `maps qty unit and name to an IngredientItem`() {
        val items = AiRecipeMapper.map(result(AiIngredient(qty = "1 1/2", unit = "cup", name = "flour")))

        assertEquals(1, items.size)
        assertEquals("1 1/2", items[0].qtyString)
        assertEquals("cup", items[0].unit)
        assertEquals("flour", items[0].name)
        assertFalse(items[0].lowConfidence)
        assertTrue(items[0].id.isNotBlank())
    }

    @Test
    fun `canonicalizes unit case and plural variants`() {
        val items = AiRecipeMapper.map(
            result(
                AiIngredient(qty = "1", unit = "Tbsp", name = "a"),
                AiIngredient(qty = "2", unit = "Cups", name = "b"),
                AiIngredient(qty = "3", unit = "oz.", name = "c"),
                AiIngredient(qty = "4", unit = "lbs", name = "d")
            )
        )

        assertEquals(listOf("tbsp", "cup", "oz", "lb"), items.map { it.unit })
    }

    @Test
    fun `unknown unit folds to none and keeps original text in the name`() {
        val items = AiRecipeMapper.map(
            result(AiIngredient(qty = "2", unit = "bunches", name = "asparagus"))
        )

        assertEquals("none", items[0].unit)
        assertEquals("bunches asparagus", items[0].name)
    }

    @Test
    fun `blank unit becomes none`() {
        val items = AiRecipeMapper.map(
            result(
                AiIngredient(qty = "3", unit = null, name = "eggs"),
                AiIngredient(qty = "1", unit = "  ", name = "apple")
            )
        )

        assertEquals(listOf("none", "none"), items.map { it.unit })
        assertEquals("eggs", items[0].name)
        assertEquals("apple", items[1].name)
    }

    @Test
    fun `drops fully blank rows`() {
        val items = AiRecipeMapper.map(
            result(
                AiIngredient(qty = null, unit = null, name = null),
                AiIngredient(qty = "", unit = "", name = ""),
                AiIngredient(qty = "1", unit = "cup", name = "flour")
            )
        )

        assertEquals(1, items.size)
        assertEquals("flour", items[0].name)
    }

    @Test
    fun `caps results at MAX_INGREDIENTS`() {
        val many = List(AiImportConfig.MAX_INGREDIENTS + 10) {
            AiIngredient(qty = "1", unit = "cup", name = "ingredient $it")
        }

        assertEquals(AiImportConfig.MAX_INGREDIENTS, AiRecipeMapper.map(result(*many.toTypedArray())).size)
    }

    @Test
    fun `unparseable qty flags lowConfidence`() {
        val items = AiRecipeMapper.map(
            result(
                AiIngredient(qty = "some", unit = "cup", name = "flour"),
                AiIngredient(qty = "1 1/2", unit = "cup", name = "sugar"),
                AiIngredient(qty = "", unit = "none", name = "salt to taste")
            )
        )

        assertTrue(items[0].lowConfidence)
        assertFalse(items[1].lowConfidence)
        // A blank qty is a name-only ingredient, not a confidence problem.
        assertFalse(items[2].lowConfidence)
    }

    @Test
    fun `unicode fraction qty parses without lowConfidence`() {
        val items = AiRecipeMapper.map(
            result(AiIngredient(qty = "½", unit = "cup", name = "milk"))
        )

        assertFalse(items[0].lowConfidence)
    }

    @Test
    fun `trims overlong fields`() {
        val items = AiRecipeMapper.map(
            result(
                AiIngredient(
                    qty = "1".repeat(AiImportConfig.MAX_QTY_LENGTH + 5),
                    unit = "cup",
                    name = "x".repeat(AiImportConfig.MAX_NAME_LENGTH + 10)
                )
            )
        )

        assertEquals(AiImportConfig.MAX_QTY_LENGTH, items[0].qtyString.length)
        assertEquals(AiImportConfig.MAX_NAME_LENGTH, items[0].name.length)
    }

    @Test
    fun `unit enum exposed to the model equals ALL_UNITS keys`() {
        assertEquals(
            RecipeScalerCalculator.ALL_UNITS.map { it.first },
            AiImportConfig.ALLOWED_UNITS
        )
    }
}
