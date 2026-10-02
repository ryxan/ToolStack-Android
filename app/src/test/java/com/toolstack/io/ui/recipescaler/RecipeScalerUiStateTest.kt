package com.toolstack.io.ui.recipescaler

import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.IngredientState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeScalerUiStateTest {

    @Test
    fun `recalculate produces scaled ingredients and multiplier`() {
        val state = RecipeScalerUiState(
            originalServingsText = "2",
            desiredServingsText = "4",
            ingredients = listOf(
                IngredientItem(
                    id = "1",
                    qtyString = "1",
                    unit = "cup",
                    state = IngredientState.DRY,
                    name = "flour"
                )
            )
        ).recalculate()

        assertEquals("2", state.multiplierText)
        assertEquals(1, state.scaledIngredients.size)
        assertEquals("flour", state.scaledIngredients.first().name)
        assertTrue(state.scaledIngredients.first().displayText.isNotBlank())
    }

    @Test
    fun `recalculate stays empty without both serving counts`() {
        val state = RecipeScalerUiState(
            originalServingsText = "4",
            desiredServingsText = "",
            ingredients = listOf(
                IngredientItem(
                    id = "1",
                    qtyString = "1",
                    unit = "cup",
                    state = IngredientState.DRY,
                    name = "flour"
                )
            )
        ).recalculate()

        assertTrue(state.scaledIngredients.isEmpty())
        assertEquals("", state.multiplierText)
    }
}
