package com.toolstack.io.ui.recipescaler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.IngredientState
import com.toolstack.io.domain.model.SavedRecipe
import com.toolstack.io.domain.model.SavedRecipeIngredient
import com.toolstack.io.domain.model.ScaledIngredient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecipeScalerViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeScalerUiState())
    val uiState: StateFlow<RecipeScalerUiState> = _uiState.asStateFlow()

    init {
        // Start with one empty ingredient
        addIngredient()
        
        // Observe saved recipes
        userPreferencesRepository.savedRecipes
            .onEach { recipes ->
                _uiState.update { it.copy(savedRecipes = recipes) }
            }
            .launchIn(viewModelScope)
    }

    // ── Focus Management ──────────────────────────────────────────────────────

    fun setFocusedIngredient(ingredientId: String?, field: FocusedIngredientField) {
        _uiState.update { it.copy(focusedIngredientId = ingredientId, focusedField = field) }
    }

    fun clearFocusedIngredient(ingredientId: String, field: FocusedIngredientField) {
        _uiState.update { state ->
            if (state.focusedIngredientId == ingredientId && state.focusedField == field) {
                state.copy(focusedIngredientId = null, focusedField = FocusedIngredientField.NONE)
            } else {
                state
            }
        }
    }

    fun appendFractionToIngredient(ingredientId: String, fraction: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { ingredient ->
                if (ingredient.id == ingredientId) {
                    val current = ingredient.qtyString.trim()
                    val newQuantity = when {
                        current.isEmpty() -> fraction
                        current.last().isDigit() && !current.contains(" ") -> "$current$fraction"
                        current.contains("½") || current.contains("⅓") ||
                        current.contains("¼") || current.contains("¾") ||
                        current.contains("⅛") || current.contains("⅔") ||
                        current.contains("⅜") -> fraction // Replace existing fraction
                        else -> "$current $fraction"
                    }
                    ingredient.copy(qtyString = newQuantity.trim())
                } else {
                    ingredient
                }
            }
            state.copy(ingredients = updated).recalculate()
        }
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
                if (ingredient.id == id) {
                    // Auto-detect ingredient state from unit
                    val detectedState = RecipeScalerCalculator.detectIngredientState(unit)
                    ingredient.copy(unit = unit, state = detectedState)
                } else {
                    ingredient
                }
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

    fun clearAllIngredients() {
        _uiState.update { it.copy(ingredients = emptyList()).recalculate() }
        // Add one empty ingredient back
        addIngredient()
    }

    // ── Recipe Management ─────────────────────────────────────────────────────

    fun onShowSaveRecipeDialog() {
        _uiState.update { it.copy(showSaveRecipeDialog = true, recipeNameInput = "") }
    }

    fun onDismissSaveRecipeDialog() {
        _uiState.update { it.copy(showSaveRecipeDialog = false, recipeNameInput = "", saveRecipeError = null) }
    }

    fun onRecipeNameInputChanged(name: String) {
        _uiState.update { it.copy(recipeNameInput = name) }
    }

    fun onSaveRecipe() {
        val state = _uiState.value
        val recipeName = state.recipeNameInput.trim()
        if (recipeName.isEmpty()) return
        if (state.ingredients.isEmpty()) return

        val recipe = SavedRecipe(
            name = recipeName,
            servings = state.originalServingsText,
            ingredients = state.ingredients.map { ingredient ->
                SavedRecipeIngredient(
                    qtyString = ingredient.qtyString,
                    unit = ingredient.unit,
                    state = ingredient.state,
                    name = ingredient.name
                )
            }
        )

        viewModelScope.launch {
            val result = userPreferencesRepository.saveRecipe(recipe)
            if (result.isSuccess) {
                _uiState.update { it.copy(showSaveRecipeDialog = false, recipeNameInput = "", saveRecipeError = null) }
            } else {
                _uiState.update { it.copy(saveRecipeError = "Failed to save recipe. Please try again.") }
            }
        }
    }

    fun onShowLoadRecipeDialog() {
        _uiState.update { it.copy(showLoadRecipeDialog = true) }
    }

    fun onDismissLoadRecipeDialog() {
        _uiState.update { it.copy(showLoadRecipeDialog = false) }
    }

    fun onLoadRecipe(recipe: SavedRecipe) {
        val loadedIngredients = recipe.ingredients.map { savedIngredient ->
            IngredientItem(
                id = UUID.randomUUID().toString(),
                qtyString = savedIngredient.qtyString,
                unit = savedIngredient.unit,
                state = savedIngredient.state,
                name = savedIngredient.name
            )
        }

        _uiState.update {
            it.copy(
                ingredients = loadedIngredients,
                originalServingsText = recipe.servings,
                showLoadRecipeDialog = false
            ).recalculate()
        }
    }

    fun onDeleteRecipe(recipeName: String) {
        viewModelScope.launch {
            userPreferencesRepository.deleteRecipe(recipeName)
        }
    }

    // ── Enhanced Features ─────────────────────────────────────────────────────

    fun toggleShowOriginalValues() {
        _uiState.update { it.copy(showOriginalValues = !it.showOriginalValues) }
    }

    fun toggleSimplifyFractions() {
        _uiState.update { it.copy(simplifyFractions = !it.simplifyFractions).recalculate() }
    }

    fun onShowCopyFormatDialog() {
        _uiState.update { it.copy(showCopyFormatDialog = true) }
    }

    fun onDismissCopyFormatDialog() {
        _uiState.update { it.copy(showCopyFormatDialog = false) }
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
    val showShoppingListDialog: Boolean = false,
    val savedRecipes: List<SavedRecipe> = emptyList(),
    val showSaveRecipeDialog: Boolean = false,
    val showLoadRecipeDialog: Boolean = false,
    val recipeNameInput: String = "",
    val showOriginalValues: Boolean = false,
    val simplifyFractions: Boolean = false,
    val showCopyFormatDialog: Boolean = false,
    val saveRecipeError: String? = null,
    val focusedIngredientId: String? = null,
    val focusedField: FocusedIngredientField = FocusedIngredientField.NONE
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
        
        // Apply simplified format if enabled
        val finalScaled = if (simplifyFractions) {
            scaled.map { ingredient ->
                ingredient.copy(
                    displayText = RecipeScalerCalculator.formatQuantitySimplified(
                        ingredient.scaledQty,
                        ingredient.unit,
                        ingredient.state
                    )
                )
            }
        } else {
            scaled
        }
        
        val shoppingList = RecipeScalerCalculator.generateShoppingList(finalScaled)

        val multiplierFormatted = String.format(Locale.US, "%.2f", multiplier)
            .replace(Regex("\\.?0+$"), "")

        return copy(
            scaledIngredients = finalScaled,
            multiplierText = multiplierFormatted,
            shoppingListText = shoppingList
        )
    }
}

enum class FocusedIngredientField {
    QUANTITY, UNIT, NAME, NONE
}
