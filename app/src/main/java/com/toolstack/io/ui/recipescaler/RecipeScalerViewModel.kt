package com.toolstack.io.ui.recipescaler

import androidx.lifecycle.ViewModel
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.IngredientState
import com.toolstack.io.domain.model.ScaledIngredient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecipeScalerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeScalerUiState())
    val uiState: StateFlow<RecipeScalerUiState> = _uiState.asStateFlow()

    init {
        // Start with one empty ingredient
        addIngredient()
    }

    // ── Servings ──────────────────────────────────────────────────────────────

    fun onOriginalServingsChanged(text: String) {
        _uiState.update { it.copy(originalServingsText = text).recalculate() }
    }

    fun onDesiredServingsChanged(text: String) {
        _uiState.update { it.copy(desiredServingsText = text).recalculate() }
    }

    // ── Ingredients ───────────────────────────────────────────────────────────

    fun addIngredient() {
        _uiState.update { state ->
            val newIngredient = IngredientItem(
                id = UUID.randomUUID().toString(),
                qtyString = "",
                unit = "none",
                state = IngredientState.DRY,
                name = ""
            )
            state.copy(ingredients = state.ingredients + newIngredient).recalculate()
        }
    }

    fun removeIngredient(id: String) {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.filter { it.id != id }).recalculate()
        }
    }

    fun onIngredientQuantityChanged(id: String, text: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { ingredient ->
                if (ingredient.id == id) ingredient.copy(qtyString = text) else ingredient
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientUnitChanged(id: String, unit: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { ingredient ->
                if (ingredient.id == id) ingredient.copy(unit = unit) else ingredient
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientStateChanged(id: String, ingredientState: IngredientState) {
        _uiState.update { state ->
            val updated = state.ingredients.map { ingredient ->
                if (ingredient.id == id) ingredient.copy(state = ingredientState) else ingredient
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientNameChanged(id: String, name: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { ingredient ->
                if (ingredient.id == id) ingredient.copy(name = name) else ingredient
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    // ── Shopping list ─────────────────────────────────────────────────────────

    fun onShowShoppingListDialog() {
        _uiState.update { it.copy(showShoppingListDialog = true) }
    }

    fun onDismissShoppingListDialog() {
        _uiState.update { it.copy(showShoppingListDialog = false) }
    }

    companion object {
        const val MAX_INGREDIENTS = 50
    }
}

data class RecipeScalerUiState(
    val originalServingsText: String = "",
    val desiredServingsText: String = "",
    val ingredients: List<IngredientItem> = emptyList(),
    val scaledIngredients: List<ScaledIngredient> = emptyList(),
    val multiplierText: String = "",
    val shoppingListText: String = "",
    val showShoppingListDialog: Boolean = false
) {
    fun recalculate(): RecipeScalerUiState {
        val originalServings = originalServingsText.toDoubleOrNull() ?: 0.0
        val desiredServings = desiredServingsText.toDoubleOrNull() ?: 0.0

        if (originalServings <= 0.0 || desiredServings <= 0.0) {
            return copy(
                scaledIngredients = emptyList(),
                multiplierText = "",
                shoppingListText = ""
            )
        }

        val multiplier = RecipeScalerCalculator.calculateMultiplier(originalServings, desiredServings)
        val scaled = RecipeScalerCalculator.scaleIngredients(ingredients, multiplier)
        val shoppingList = RecipeScalerCalculator.generateShoppingList(scaled)

        val multiplierFormatted = String.format(Locale.US, "%.2f", multiplier)
            .replace(Regex("\\.?0+$"), "")

        return copy(
            scaledIngredients = scaled,
            multiplierText = multiplierFormatted,
            shoppingListText = shoppingList
        )
    }
}
