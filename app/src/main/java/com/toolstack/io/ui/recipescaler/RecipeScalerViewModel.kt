package com.toolstack.io.ui.recipescaler

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.RecipeOcrParser
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.SavedRecipe
import com.toolstack.io.domain.model.SavedRecipeIngredient
import com.toolstack.io.domain.model.ScaledIngredient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class RecipeScalerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeScalerUiState())
    val uiState: StateFlow<RecipeScalerUiState> = _uiState.asStateFlow()

    // Holds the File written before the camera is launched so we can delete it
    // after ML Kit finishes (or on cancellation).
    private var pendingScanFile: File? = null

    init {
        addIngredient()

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
                    val unicodeFractionRegex = Regex("[\\u00BC-\\u00BE\\u2150-\\u215E]")
                    val newQuantity = when {
                        current.isEmpty() -> fraction
                        fraction == "/" -> "$current$fraction"
                        unicodeFractionRegex.containsMatchIn(current) -> {
                            val wholePart = current.replace(unicodeFractionRegex, "").trim()
                            if (wholePart.isEmpty()) fraction else "$wholePart $fraction"
                        }
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
                id        = UUID.randomUUID().toString(),
                qtyString = "",
                unit      = "none",
                name      = ""
            )
            state.copy(ingredients = state.ingredients + newIngredient).recalculate()
        }
    }

    fun addIngredientAndFocusName() {
        _uiState.update { state ->
            if (state.ingredients.size >= MAX_INGREDIENTS) return@update state
            val newIngredient = IngredientItem(
                id        = UUID.randomUUID().toString(),
                qtyString = "",
                unit      = "none",
                name      = ""
            )
            state.copy(
                ingredients       = state.ingredients + newIngredient,
                focusedIngredientId = newIngredient.id,
                focusedField      = FocusedIngredientField.QUANTITY
            ).recalculate()
        }
    }

    fun removeIngredient(id: String) {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.filter { it.id != id }).recalculate()
        }
    }

    fun onIngredientQuantityChanged(id: String, text: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { if (it.id == id) it.copy(qtyString = text) else it }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientUnitChanged(id: String, unit: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { if (it.id == id) it.copy(unit = unit) else it }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientNameChanged(id: String, name: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map { if (it.id == id) it.copy(name = name) else it }
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
        addIngredient()
    }

    // ── Recipe Management ─────────────────────────────────────────────────────

    fun onShowSaveRecipeDialog() {
        val hasSaved = _uiState.value.savedRecipes.isNotEmpty()
        if (hasSaved) {
            _uiState.update { it.copy(showSaveListDialog = true) }
        } else {
            _uiState.update { it.copy(showSaveRecipeDialog = true, recipeNameInput = "") }
        }
    }

    fun onDismissSaveRecipeDialog() {
        _uiState.update {
            it.copy(
                showSaveRecipeDialog     = false,
                showSaveListDialog       = false,
                recipeNameInput          = "",
                saveRecipeError          = null,
                selectedRecipeForOverwrite = null
            )
        }
    }

    fun onDismissSaveListDialog() {
        _uiState.update { it.copy(showSaveListDialog = false) }
    }

    fun onSelectOverwriteRecipe(recipe: SavedRecipe) {
        _uiState.update {
            it.copy(
                showSaveListDialog         = false,
                showSaveRecipeDialog       = true,
                recipeNameInput            = recipe.name,
                selectedRecipeForOverwrite = recipe.name,
                saveRecipeError            = null
            )
        }
    }

    fun onChooseSaveAsNew() {
        _uiState.update {
            it.copy(
                showSaveListDialog         = false,
                showSaveRecipeDialog       = true,
                recipeNameInput            = "",
                selectedRecipeForOverwrite = null,
                saveRecipeError            = null
            )
        }
    }

    fun onRecipeNameInputChanged(name: String) {
        _uiState.update { it.copy(recipeNameInput = name) }
    }

    fun onSaveRecipe() {
        val state = _uiState.value
        val recipeName = state.recipeNameInput.trim()
        if (recipeName.isEmpty()) return
        if (state.ingredients.isEmpty()) return

        val originalKey = state.selectedRecipeForOverwrite
        val isRename    = originalKey != null && recipeName != originalKey
        val isSaveAsNew = originalKey == null

        if (isSaveAsNew && state.savedRecipes.any { it.name == recipeName }) {
            _uiState.update {
                it.copy(saveRecipeError = "A recipe named \"$recipeName\" already exists. Choose a different name or select it from the list to overwrite.")
            }
            return
        }

        if (isRename && state.savedRecipes.any { it.name == recipeName }) {
            _uiState.update { it.copy(saveRecipeError = "A recipe named \"$recipeName\" already exists.") }
            return
        }

        val recipe = SavedRecipe(
            name       = recipeName,
            servings   = state.originalServingsText,
            ingredients = state.ingredients.map { ingredient ->
                SavedRecipeIngredient(
                    qtyString = ingredient.qtyString,
                    unit      = ingredient.unit,
                    name      = ingredient.name
                )
            }
        )

        viewModelScope.launch {
            if (isRename) userPreferencesRepository.deleteRecipe(originalKey!!)
            val result = userPreferencesRepository.saveRecipe(recipe)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        showSaveRecipeDialog       = false,
                        recipeNameInput            = "",
                        saveRecipeError            = null,
                        selectedRecipeForOverwrite = null
                    )
                }
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
        val loadedIngredients = recipe.ingredients.map { saved ->
            IngredientItem(
                id        = UUID.randomUUID().toString(),
                qtyString = saved.qtyString,
                unit      = saved.unit,
                name      = saved.name
            )
        }
        _uiState.update {
            it.copy(
                ingredients         = loadedIngredients,
                originalServingsText = recipe.servings,
                showLoadRecipeDialog = false
            ).recalculate()
        }
    }

    fun onDeleteRecipe(recipeName: String) {
        viewModelScope.launch { userPreferencesRepository.deleteRecipe(recipeName) }
    }

    // ── Toggles ───────────────────────────────────────────────────────────────

    fun toggleShowOriginalValues() {
        _uiState.update { it.copy(showOriginalValues = !it.showOriginalValues) }
    }

    /** Keep original units: skip unit-family decomposition, display raw rounded amount. */
    fun toggleKeepOriginalUnits() {
        _uiState.update { it.copy(keepOriginalUnits = !it.keepOriginalUnits).recalculate() }
    }

    fun onShowCopyFormatDialog() {
        _uiState.update { it.copy(showCopyFormatDialog = true) }
    }

    fun onDismissCopyFormatDialog() {
        _uiState.update { it.copy(showCopyFormatDialog = false) }
    }

    // ── OCR / Scan ────────────────────────────────────────────────────────────

    /**
     * Creates a temporary file in the app's external cache directory and returns
     * a [Uri] (via FileProvider) that can be passed to [TakePicture].
     *
     * Stores the [File] reference in [pendingScanFile] so it can be deleted after
     * ML Kit finishes processing (or when the user cancels).
     *
     * Returns `null` if the external cache directory is unavailable (e.g. SD card
     * ejected). The screen should treat `null` as a non-fatal failure and skip
     * launching the camera.
     */
    fun createCameraImageUri(): Uri? {
        return try {
            val dir = File(appContext.externalCacheDir, "recipe_scan").also { it.mkdirs() }
            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
            pendingScanFile = file
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            pendingScanFile = null
            null
        }
    }

    /**
     * Called when the camera or gallery activity returns with a [uri] pointing to
     * the selected/captured image. Runs ML Kit text recognition on the image,
     * parses the result with [RecipeOcrParser], then replaces the current
     * ingredient list with the parsed ingredients.
     *
     * State transitions:
     *   isScanProcessing = true  → ML Kit running
     *   isScanProcessing = false → done (success or error)
     *   scanError                → non-null string on failure, null on success
     */
    fun onImageCaptured(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanProcessing = true, scanError = null) }
            try {
                val image = InputImage.fromFilePath(appContext, uri)
                val rawText = runTextRecognition(image)
                val parsed  = RecipeOcrParser.parse(rawText, MAX_INGREDIENTS)

                if (parsed.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isScanProcessing = false,
                            scanError        = ScanError.NO_INGREDIENTS_FOUND
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            ingredients      = parsed,
                            isScanProcessing = false,
                            scanError        = null
                        ).recalculate()
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanProcessing = false,
                        scanError        = ScanError.RECOGNITION_FAILED
                    )
                }
            } finally {
                deletePendingScanFile()
            }
        }
    }

    /** Called when the user cancels the camera/gallery without selecting an image. */
    fun onScanCancelled() {
        deletePendingScanFile()
        _uiState.update { it.copy(isScanProcessing = false, scanError = null) }
    }

    /** Dismiss a previously-shown scan error banner. */
    fun onDismissScanError() {
        _uiState.update { it.copy(scanError = null) }
    }

    // ── Private scan helpers ──────────────────────────────────────────────────

    /**
     * Wraps the ML Kit [TextRecognizer] callback API in a suspend function.
     * The coroutine is resumed exactly once (success) or with an exception
     * (failure). The recognizer is closed after use to free native resources.
     */
    private suspend fun runTextRecognition(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    recognizer.close()
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    if (cont.isActive) cont.cancel(e)
                }
        }

    private fun deletePendingScanFile() {
        pendingScanFile?.delete()
        pendingScanFile = null
    }

    companion object {
        const val MAX_INGREDIENTS = 50
    }
}

// ── Scan error type ───────────────────────────────────────────────────────────

/** Sealed type for the two distinguishable scan failure modes. */
sealed interface ScanError {
    /** ML Kit ran successfully but the parser found no ingredient lines. */
    data object NO_INGREDIENTS_FOUND : ScanError
    /** ML Kit itself threw an exception (image unreadable, OOM, etc.). */
    data object RECOGNITION_FAILED : ScanError
}

// ── UiState ───────────────────────────────────────────────────────────────────

data class RecipeScalerUiState(
    val originalServingsText: String = "1",
    val desiredServingsText: String = "",
    val ingredients: List<IngredientItem> = emptyList(),
    val scaledIngredients: List<ScaledIngredient> = emptyList(),
    val multiplierText: String = "",
    val shoppingListText: String = "",
    /** True when multiplier > 4×; triggers the recipe-level warning banner. */
    val showScaleWarning: Boolean = false,
    /** When true, unit-family decomposition is skipped; raw rounded amounts are shown. */
    val keepOriginalUnits: Boolean = false,
    val showOriginalValues: Boolean = false,
    val showShoppingListDialog: Boolean = false,
    val savedRecipes: List<SavedRecipe> = emptyList(),
    val showSaveListDialog: Boolean = false,
    val showSaveRecipeDialog: Boolean = false,
    val showLoadRecipeDialog: Boolean = false,
    val recipeNameInput: String = "",
    val selectedRecipeForOverwrite: String? = null,
    val showCopyFormatDialog: Boolean = false,
    val saveRecipeError: String? = null,
    val focusedIngredientId: String? = null,
    val focusedField: FocusedIngredientField = FocusedIngredientField.NONE,
    // ── scan ──────────────────────────────────────────────────────────────────
    /** True while ML Kit is processing the captured image. */
    val isScanProcessing: Boolean = false,
    /** Non-null when the most recent scan attempt ended with an error. */
    val scanError: ScanError? = null
) {
    fun recalculate(): RecipeScalerUiState {
        val originalServings = originalServingsText.toDoubleOrNull() ?: 0.0
        val desiredServings  = desiredServingsText.toDoubleOrNull()  ?: 0.0

        if (originalServings <= 0.0 || desiredServings <= 0.0) {
            return copy(
                scaledIngredients = emptyList(),
                multiplierText    = "",
                shoppingListText  = "",
                showScaleWarning  = false
            )
        }

        val multiplier = RecipeScalerCalculator.calculateMultiplier(originalServings, desiredServings)
        val scaled     = RecipeScalerCalculator.scaleIngredients(ingredients, multiplier, keepOriginalUnits)
        val shoppingList = RecipeScalerCalculator.generateShoppingList(scaled)

        val multiplierFormatted = String.format(Locale.US, "%.2f", multiplier)
            .replace(Regex("\\.?0+$"), "")

        return copy(
            scaledIngredients = scaled,
            multiplierText    = multiplierFormatted,
            shoppingListText  = shoppingList,
            showScaleWarning  = multiplier > 4.0
        )
    }
}

enum class FocusedIngredientField {
    QUANTITY, UNIT, NAME, NONE
}
