package com.toolstack.io.ui.recipescaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.OcrLine
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

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

    // One recognizer for the ViewModel's lifetime — creating a client per scan
    // pays native init cost each time. `recognizerInitialized` tracks whether
    // the lazy was ever triggered so onCleared() doesn't instantiate it just to
    // close it.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var recognizerInitialized = false

    override fun onCleared() {
        if (recognizerInitialized) recognizer.close()
        super.onCleared()
    }

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
            val updated = state.ingredients.map {
                if (it.id == id) it.copy(qtyString = text, lowConfidence = false) else it
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientUnitChanged(id: String, unit: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map {
                if (it.id == id) it.copy(
                    unit = RecipeScalerCalculator.canonicalUnit(unit),
                    lowConfidence = false
                ) else it
            }
            state.copy(ingredients = updated).recalculate()
        }
    }

    fun onIngredientNameChanged(id: String, name: String) {
        _uiState.update { state ->
            val updated = state.ingredients.map {
                if (it.id == id) it.copy(name = name, lowConfidence = false) else it
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
                unit      = RecipeScalerCalculator.canonicalUnit(saved.unit),
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
     * ejected). The screen should report this via [onCameraUnavailable].
     */
    fun createCameraImageUri(): Uri? {
        return try {
            val dir = File(appContext.externalCacheDir, "recipe_scan").also { it.mkdirs() }
            // Clear leftovers from previous scans so cancelled/failed attempts
            // don't accumulate in the cache directory.
            dir.listFiles()?.forEach { it.delete() }
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
     * Called when the camera, gallery, or ML Kit Document Scanner activity
     * returns with a [uri] pointing to the selected/captured image. Runs ML Kit
     * text recognition on the image, parses the result with [RecipeOcrParser],
     * then either replaces the current ingredient list (when it's entirely
     * blank) or stores the result in [RecipeScalerUiState.pendingScanResult]
     * for the user to confirm.
     *
     * When [deleteWhenDone] is true and [uri] is a `file://` URI (the Document
     * Scanner's output JPEG), the file is recorded in [pendingScanFile] so the
     * existing `finally` cleanup deletes it after processing.
     *
     * State transitions:
     *   isScanProcessing = true  → ML Kit running
     *   isScanProcessing = false → done (success or error)
     *   scanError                → non-null on failure, null on success
     *   pendingScanResult        → non-null when confirmation is needed
     */
    fun onImageCaptured(uri: Uri, deleteWhenDone: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanProcessing = true, scanError = null) }
            if (deleteWhenDone && uri.scheme == "file") {
                pendingScanFile = uri.path?.let(::File)
            }
            var scanBitmap: Bitmap? = null
            try {
                val (image, bitmap) = loadScanImage(uri)
                scanBitmap = bitmap

                val visionText = runTextRecognition(image)

                // Parsing is pure CPU work — keep it off the main thread.
                val parsed = withContext(Dispatchers.Default) {
                    parseVisionText(visionText)
                }

                if (parsed.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isScanProcessing = false,
                            scanError        = ScanError.NO_INGREDIENTS_FOUND
                        )
                    }
                } else {
                    _uiState.update { state ->
                        // Only replace silently when the current list is entirely
                        // blank; otherwise ask the user (replace / append / cancel).
                        if (state.ingredients.all { it.name.isBlank() && it.qtyString.isBlank() }) {
                            state.copy(
                                ingredients         = parsed,
                                isScanProcessing    = false,
                                scanError           = null,
                                focusedIngredientId = null,
                                focusedField        = FocusedIngredientField.NONE
                            ).recalculate()
                        } else {
                            state.copy(
                                pendingScanResult = parsed,
                                isScanProcessing  = false,
                                scanError         = null
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanProcessing = false,
                        scanError        = ScanError.RECOGNITION_FAILED
                    )
                }
            } finally {
                scanBitmap?.recycle()
                deletePendingScanFile()
            }
        }
    }

    /**
     * Called while `getStartScanIntent` is pending — the Document Scanner flow
     * is downloaded by Play Services on first use, which can take a while.
     * Cleared by [onScanCancelled], [onCameraUnavailable], or [onImageCaptured].
     */
    fun onScanLaunchStarted() {
        _uiState.update { it.copy(isScanProcessing = true, scanError = null) }
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

    /** Called when the camera cannot be launched (no camera app, dead cache dir). */
    fun onCameraUnavailable() {
        deletePendingScanFile()
        _uiState.update {
            it.copy(isScanProcessing = false, scanError = ScanError.CAMERA_UNAVAILABLE)
        }
    }

    /** Confirm a pending scan import: replace the current ingredient list. */
    fun onScanResultReplace() {
        _uiState.update { state ->
            val pending = state.pendingScanResult ?: return@update state
            state.copy(
                ingredients         = pending,
                pendingScanResult   = null,
                focusedIngredientId = null,
                focusedField        = FocusedIngredientField.NONE
            ).recalculate()
        }
    }

    /** Confirm a pending scan import: append scanned items after non-blank ones. */
    fun onScanResultAppend() {
        _uiState.update { state ->
            val pending = state.pendingScanResult ?: return@update state
            val kept    = state.ingredients.filter { it.name.isNotBlank() || it.qtyString.isNotBlank() }
            state.copy(
                ingredients         = (kept + pending).take(MAX_INGREDIENTS),
                pendingScanResult   = null,
                focusedIngredientId = null,
                focusedField        = FocusedIngredientField.NONE
            ).recalculate()
        }
    }

    /** Dismiss the pending scan import dialog without changing ingredients. */
    fun onScanResultDismiss() {
        _uiState.update { it.copy(pendingScanResult = null) }
    }

    // ── Private scan helpers ──────────────────────────────────────────────────

    /**
     * Runs ML Kit text recognition on [image] using the shared [recognizer].
     * `kotlinx-coroutines-play-services`' await() bridges the Task API.
     */
    private suspend fun runTextRecognition(image: InputImage): Text {
        recognizerInitialized = true
        return recognizer.process(image).await()
    }

    /**
     * Parses recognised text into ingredients, preferring the layout-aware path
     * (column detection + wrapped-line merge) when every OCR line has a
     * bounding box; falls back to plain text parsing otherwise.
     */
    private fun parseVisionText(visionText: Text): List<IngredientItem> {
        // Raw text goes to logcat so mis-parse reports can be diagnosed against
        // what ML Kit actually returned rather than the cleaned-up result.
        if (com.toolstack.io.BuildConfig.DEBUG) {
            Log.d(TAG, "OCR raw text:\n${visionText.text}")
        }
        val lines = visionText.textBlocks.flatMap { it.lines }
        if (com.toolstack.io.BuildConfig.DEBUG) {
            // One line per OCR line: "<confidence>  <text>" — used to tune
            // RecipeOcrParser.LOW_CONFIDENCE_THRESHOLD from real scans.
            lines.forEach { line ->
                Log.d(TAG, "%.2f  %s".format(line.confidence, line.text))
            }
        }
        if (lines.isNotEmpty() && lines.all { it.boundingBox != null }) {
            return RecipeOcrParser.parseLines(
                lines.map { line ->
                    val box = line.boundingBox!!
                    OcrLine(
                        text   = line.text,
                        left   = box.left,
                        top    = box.top,
                        right  = box.right,
                        bottom = box.bottom,
                        confidence = line.confidence
                    )
                },
                MAX_INGREDIENTS
            )
        }
        return RecipeOcrParser.parse(visionText.text, MAX_INGREDIENTS)
    }

    /**
     * Decodes [uri] into an [InputImage], downscaling so the longest edge is at
     * most [MAX_SCAN_EDGE_PX]. Image decoding runs on [Dispatchers.IO].
     *
     * On API 28+ [ImageDecoder] applies EXIF orientation itself, so the bitmap
     * is already upright and [InputImage] gets rotation 0. On API 26–27 we fall
     * back to [InputImage.fromFilePath], which also handles EXIF.
     *
     * Returns the [InputImage] paired with the decoded [Bitmap] (non-null only
     * on the ImageDecoder path) so the caller can recycle it after recognition.
     */
    private suspend fun loadScanImage(uri: Uri): Pair<InputImage, Bitmap?> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(appContext.contentResolver, uri)
            ) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > MAX_SCAN_EDGE_PX) {
                    val scale = MAX_SCAN_EDGE_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            InputImage.fromBitmap(bitmap, 0) to bitmap
        } else {
            InputImage.fromFilePath(appContext, uri) to null
        }
    }

    private fun deletePendingScanFile() {
        pendingScanFile?.delete()
        pendingScanFile = null
    }

    companion object {
        private const val TAG = "RecipeScaler"
        const val MAX_INGREDIENTS = 50
        /** Longest-edge pixel cap for scan images; large photos waste memory/OCR time. */
        const val MAX_SCAN_EDGE_PX = 2048
    }
}

// ── Scan error type ───────────────────────────────────────────────────────────

/** Sealed type for the distinguishable scan failure modes. */
sealed interface ScanError {
    /** ML Kit ran successfully but the parser found no ingredient lines. */
    data object NO_INGREDIENTS_FOUND : ScanError
    /** ML Kit itself threw an exception (image unreadable, OOM, etc.). */
    data object RECOGNITION_FAILED : ScanError
    /** The camera could not be launched (no camera app or temp-file failure). */
    data object CAMERA_UNAVAILABLE : ScanError
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
    val scanError: ScanError? = null,
    /**
     * Parsed scan result awaiting the user's replace/append/cancel choice.
     * Non-null only when a scan succeeded but the current list isn't blank.
     */
    val pendingScanResult: List<IngredientItem>? = null
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
