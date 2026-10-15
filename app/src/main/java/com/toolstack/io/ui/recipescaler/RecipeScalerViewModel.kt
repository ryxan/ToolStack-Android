package com.toolstack.io.ui.recipescaler

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.toolstack.io.data.ai.AiImportQuotaRepository
import com.toolstack.io.data.ai.AiImportResult
import com.toolstack.io.data.ai.AiRecipeImporter
import com.toolstack.io.data.ai.RewardedAdProvider
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.BillingRepository
import com.toolstack.io.data.billing.BillingResult
import com.toolstack.io.data.billing.PurchaseState
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.ai.AiImportConfig
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecipeScalerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val billingRepository: BillingRepository,
    private val aiImporter: AiRecipeImporter,
    private val aiQuota: AiImportQuotaRepository,
    private val rewardedAds: RewardedAdProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeScalerUiState())
    val uiState: StateFlow<RecipeScalerUiState> = _uiState.asStateFlow()

    // Holds the File written before the camera is launched so we can delete it
    // after ML Kit finishes (or on cancellation).
    private var pendingScanFile: File? = null

    // JPEG prepared for the AI importer, cached so "Try AI import" retries and
    // the on-device fallback work after the source file is deleted. Internal
    // for unit-test seeding; cleared on each new scan, on cancel, onCleared.
    internal var lastScanJpeg: ByteArray? = null

    // One recognizer for the ViewModel's lifetime — creating a client per scan
    // pays native init cost each time. `recognizerInitialized` tracks whether
    // the lazy was ever triggered so onCleared() doesn't instantiate it just to
    // close it.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var recognizerInitialized = false

    override fun onCleared() {
        if (recognizerInitialized) recognizer.close()
        lastScanJpeg = null
        super.onCleared()
    }

    init {
        addIngredient()

        userPreferencesRepository.savedRecipes
            .onEach { recipes ->
                _uiState.update { it.copy(savedRecipes = recipes) }
            }
            .launchIn(viewModelScope)

        billingRepository.purchaseState
            .onEach { state ->
                _uiState.update { it.copy(isPremium = state.isActive) }
            }
            .launchIn(viewModelScope)

        billingRepository.products
            .onEach { products ->
                _uiState.update { it.copy(availableProducts = products) }
            }
            .launchIn(viewModelScope)

        aiQuota.remainingImports
            .onEach { remaining ->
                _uiState.update { it.copy(aiScansRemaining = remaining) }
            }
            .launchIn(viewModelScope)

        billingRepository.creditsGranted
            .onEach { credits ->
                _uiState.update { it.copy(creditsGranted = credits, showAiPaywall = false) }
            }
            .launchIn(viewModelScope)

        rewardedAds.isAvailable
            .onEach { available ->
                _uiState.update { it.copy(rewardedAdAvailable = available) }
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
     *
     * When [RecipeScalerUiState.useAiImport] is on, the image goes to the AI
     * importer instead of ML Kit (see [runAiImport]).
     */
    fun onImageCaptured(uri: Uri, deleteWhenDone: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanProcessing      = true,
                    scanError             = null,
                    canOfferAiRetry       = false,
                    canOfferMlKitFallback = false
                )
            }
            if (deleteWhenDone && uri.scheme == "file") {
                pendingScanFile = uri.path?.let(::File)
            }
            lastScanJpeg = null
            if (_uiState.value.useAiImport) {
                runAiImport(uri)
            } else {
                runMlKitScan(uri)
            }
        }
    }

    /**
     * ML Kit path: decode → text recognition → parse → replace/append.
     * On an empty or poor result the image is re-encoded for the AI importer
     * and cached, so the error banner can offer "Try AI import".
     */
    private suspend fun runMlKitScan(uri: Uri) {
        var scanBitmap: Bitmap? = null
        try {
            val (image, bitmap) = loadScanImage(uri)
            scanBitmap = bitmap
            runMlKitRecognition(image, uri)
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

    /**
     * Recognition → parse → apply. Shared by the normal scan path and the
     * on-device fallback after an AI network error ([uri] is null there —
     * nothing left to re-encode, the JPEG is already cached).
     */
    private suspend fun runMlKitRecognition(image: InputImage, uri: Uri?) {
        val visionText = runTextRecognition(image)

        // Parsing is pure CPU work — keep it off the main thread.
        val parsed = withContext(Dispatchers.Default) {
            parseVisionText(visionText)
        }

        if (parsed.isEmpty()) {
            if (uri != null) cacheJpegForAiRetry(uri)
            _uiState.update {
                it.copy(
                    isScanProcessing = false,
                    scanError        = ScanError.NO_INGREDIENTS_FOUND,
                    canOfferAiRetry  = lastScanJpeg != null
                )
            }
        } else {
            // Poor result: the parse ran but probably missed lines. Apply it as
            // usual (the low-confidence hints still fire) and offer AI retry.
            val poorResult = parsed.size < POOR_RESULT_MIN_ITEMS ||
                parsed.count { it.lowConfidence } * 2 > parsed.size
            if (poorResult && uri != null) cacheJpegForAiRetry(uri)
            _uiState.update { state ->
                // Only replace silently when the current list is entirely
                // blank; otherwise ask the user (replace / append / cancel).
                if (state.ingredients.all { it.name.isBlank() && it.qtyString.isBlank() }) {
                    state.copy(
                        ingredients         = parsed,
                        isScanProcessing    = false,
                        scanError           = null,
                        canOfferAiRetry     = poorResult && lastScanJpeg != null,
                        focusedIngredientId = null,
                        focusedField        = FocusedIngredientField.NONE
                    ).recalculate()
                } else {
                    state.copy(
                        pendingScanResult = parsed,
                        isScanProcessing  = false,
                        scanError         = null,
                        canOfferAiRetry   = poorResult && lastScanJpeg != null
                    )
                }
            }
        }
    }

    // ── AI import ─────────────────────────────────────────────────────────────

    /** ScanSourceDialog toggle: route the next scan through the AI importer. */
    fun setUseAiImport(enabled: Boolean) {
        _uiState.update { it.copy(useAiImport = enabled) }
    }

    /**
     * AI path: prepare the JPEG, cache it for retries, reserve a credit, then
     * call the importer. A credit is spent only on a successful parse — every
     * non-success is refunded.
     */
    private suspend fun runAiImport(uri: Uri) {
        val totalStartedAt = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(isScanProcessing = false, isAiProcessing = true) }
        try {
            val preparationStartedAt = SystemClock.elapsedRealtime()
            val jpeg = prepareJpegForAi(uri)
            if (com.toolstack.io.BuildConfig.DEBUG) {
                Log.d(TAG, "AI timing: prepare=${SystemClock.elapsedRealtime() - preparationStartedAt}ms")
            }
            if (jpeg == null) {
                _uiState.update {
                    it.copy(isAiProcessing = false, scanError = ScanError.AI_FAILED)
                }
                return
            }
            lastScanJpeg = jpeg
            runAiImportBytes(jpeg)
        } finally {
            if (com.toolstack.io.BuildConfig.DEBUG) {
                Log.d(TAG, "AI timing: capture-to-result=${SystemClock.elapsedRealtime() - totalStartedAt}ms")
            }
            deletePendingScanFile()
        }
    }

    /**
     * Reserve → import → apply for an already-prepared JPEG. Shared by the
     * toggle-on scan path and the "Try AI import" retry.
     */
    private suspend fun runAiImportBytes(jpeg: ByteArray) {
        val totalStartedAt = SystemClock.elapsedRealtime()
        val isPremium = _uiState.value.isPremium
        val quotaStartedAt = SystemClock.elapsedRealtime()
        val reserved = aiQuota.tryReserve(isPremium)
        if (com.toolstack.io.BuildConfig.DEBUG) {
            Log.d(TAG, "AI timing: quota=${SystemClock.elapsedRealtime() - quotaStartedAt}ms reserved=$reserved bytes=${jpeg.size}")
        }
        if (!reserved) {
            _uiState.update {
                it.copy(
                    isAiProcessing = false,
                    showAiPaywall  = !isPremium,
                    scanError      = if (isPremium) ScanError.AI_LIMIT_REACHED else null
                )
            }
            if (!isPremium) viewModelScope.launch { billingRepository.queryProductDetails() }
            return
        }
        val importStartedAt = SystemClock.elapsedRealtime()
        val result = aiImporter.import(jpeg)
        if (com.toolstack.io.BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "AI timing: importer=${SystemClock.elapsedRealtime() - importStartedAt}ms " +
                    "prepared-to-result=${SystemClock.elapsedRealtime() - totalStartedAt}ms " +
                    "result=${result.javaClass.simpleName}"
            )
        }
        when (result) {
            is AiImportResult.Success -> applyAiSuccess(result)
            else -> {
                aiQuota.refund(isPremium)
                _uiState.update {
                    it.copy(
                        isAiProcessing        = false,
                        scanError             = result.toScanError(),
                        canOfferMlKitFallback = result == AiImportResult.NetworkError
                    )
                }
            }
        }
    }

    /**
     * Apply a successful import using the same replace/append flow as ML Kit:
     * silently replace when the list is blank, otherwise store in
     * pendingScanResult for confirmation. Servings and title prefill the
     * servings field and save-name input when returned.
     */
    private fun applyAiSuccess(result: AiImportResult.Success) {
        _uiState.update { state ->
            val base = state.copy(
                isAiProcessing       = false,
                scanError            = null,
                originalServingsText = result.recipe.servings
                    ?.takeIf { it.isNotBlank() } ?: state.originalServingsText,
                recipeNameInput      = result.recipe.title
                    ?.takeIf { it.isNotBlank() } ?: state.recipeNameInput
            )
            if (state.ingredients.all { it.name.isBlank() && it.qtyString.isBlank() }) {
                base.copy(
                    ingredients         = result.ingredients,
                    focusedIngredientId = null,
                    focusedField        = FocusedIngredientField.NONE
                ).recalculate()
            } else {
                base.copy(pendingScanResult = result.ingredients)
            }
        }
    }

    /**
     * "Try AI import" after a failed or poor ML Kit scan — reuses the JPEG
     * cached during the original scan so no new photo is needed.
     */
    fun onTryAiImport() {
        val jpeg = lastScanJpeg ?: return
        if (_uiState.value.isAiProcessing || _uiState.value.isScanProcessing) return
        _uiState.update {
            it.copy(
                isAiProcessing    = true,
                scanError         = null,
                pendingScanResult = null,
                canOfferAiRetry   = false
            )
        }
        viewModelScope.launch { runAiImportBytes(jpeg) }
    }

    /**
     * "Scan on-device" after an AI network error — runs the normal ML Kit
     * pipeline on the cached JPEG; no new photo needed.
     */
    fun onAiFallbackToMlKit() {
        val jpeg = lastScanJpeg ?: return
        if (_uiState.value.isAiProcessing || _uiState.value.isScanProcessing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isScanProcessing = true, scanError = null, canOfferMlKitFallback = false)
            }
            var bitmap: Bitmap? = null
            try {
                bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    ?: error("Cached JPEG undecodable")
                runMlKitRecognition(InputImage.fromBitmap(bitmap, 0), uri = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isScanProcessing = false, scanError = ScanError.RECOGNITION_FAILED)
                }
            } finally {
                bitmap?.recycle()
            }
        }
    }

    fun onDismissAiPaywall() {
        _uiState.update { it.copy(showAiPaywall = false) }
    }

    /** Launch the Google Play purchase flow from the AI paywall dialog. */
    fun startPurchaseFlow(activity: Activity, product: BillingProduct) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseError = null) }
            when (val result = billingRepository.launchPurchaseFlow(activity, product)) {
                is BillingResult.Error -> {
                    _uiState.update { it.copy(purchaseError = result.message) }
                }
                else -> {}
            }
        }
    }

    fun clearPurchaseError() {
        _uiState.update { it.copy(purchaseError = null) }
    }

    /**
     * "Watch ad for +5 scans" on the AI paywall. The provider grants the
     * credits; when it reports a reward the paywall closes and the snackbar
     * shows the new balance.
     */
    fun onWatchAdRequested(activity: Activity) {
        if (_uiState.value.isAdLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAdLoading = true) }
            val earned = rewardedAds.showRewardedForScans(activity)
            _uiState.update {
                it.copy(
                    isAdLoading = false,
                    showAiPaywall = if (earned > 0) false else it.showAiPaywall,
                    creditsGranted = if (earned > 0) earned else it.creditsGranted
                )
            }
        }
    }

    /** Called after the "scans added" snackbar has been shown. */
    fun onCreditsGrantedShown() {
        _uiState.update { it.copy(creditsGranted = null) }
    }

    private fun AiImportResult.toScanError(): ScanError = when (this) {
        AiImportResult.NothingFound -> ScanError.NO_INGREDIENTS_FOUND
        AiImportResult.NetworkError -> ScanError.AI_NETWORK_ERROR
        AiImportResult.Blocked      -> ScanError.AI_BLOCKED
        AiImportResult.RateLimited  -> ScanError.AI_RATE_LIMITED
        is AiImportResult.Failed    -> ScanError.AI_FAILED
        is AiImportResult.Success   -> ScanError.AI_FAILED // unreachable — handled earlier
    }

    /** Re-encode [uri] for the AI importer once per scan, for the retry cache. */
    private suspend fun cacheJpegForAiRetry(uri: Uri) {
        if (lastScanJpeg == null) {
            lastScanJpeg = prepareJpegForAi(uri)
        }
    }

    /**
     * Decode [uri] and re-encode as a bounded JPEG for the AI importer:
     * [inSampleSize] bounds decode → EXIF rotation → scale so the longest edge
     * is at most [AiImportConfig.MAX_IMAGE_EDGE_PX] → JPEG at
     * [AiImportConfig.JPEG_QUALITY] quality. Runs on all API levels (unlike
     * [loadScanImage], which needs API 28 for EXIF). Returns null when the
     * source can't be decoded.
     */
    internal suspend fun prepareJpegForAi(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val totalStartedAt = SystemClock.elapsedRealtime()
        try {
            // ImageDecoder (API 28+) handles EXIF rotation and formats
            // BitmapFactory can't decode from non-seekable streams (HEIF).
            // BitmapFactory + ExifInterface is the fallback below API 28 and
            // for sources ImageDecoder rejects.
            val decodeStartedAt = SystemClock.elapsedRealtime()
            val raw = decodeViaImageDecoder(uri)
                ?: decodeViaBitmapFactory(uri)?.let { applyExifRotation(uri, it) }
            val decodeMs = SystemClock.elapsedRealtime() - decodeStartedAt
            if (raw == null) {
                if (com.toolstack.io.BuildConfig.DEBUG) {
                    Log.w(TAG, "prepareJpegForAi: undecodable image $uri")
                }
                return@withContext null
            }

            val scaleStartedAt = SystemClock.elapsedRealtime()
            val sourceWidth = raw.width
            val sourceHeight = raw.height
            val scaled = scaleToMaxEdge(raw, AiImportConfig.MAX_IMAGE_EDGE_PX)
            val scaleMs = SystemClock.elapsedRealtime() - scaleStartedAt
            val compressStartedAt = SystemClock.elapsedRealtime()
            ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, AiImportConfig.JPEG_QUALITY, out)) {
                    if (com.toolstack.io.BuildConfig.DEBUG) {
                        Log.w(TAG, "prepareJpegForAi: JPEG compress failed $uri")
                    }
                    return@withContext null
                }
                val jpeg = out.toByteArray()
                if (com.toolstack.io.BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "AI timing: image decode=${decodeMs}ms scale=${scaleMs}ms " +
                            "compress=${SystemClock.elapsedRealtime() - compressStartedAt}ms " +
                            "total=${SystemClock.elapsedRealtime() - totalStartedAt}ms " +
                            "source=${sourceWidth}x$sourceHeight output=${scaled.width}x${scaled.height} bytes=${jpeg.size}"
                    )
                }
                jpeg
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (com.toolstack.io.BuildConfig.DEBUG) Log.w(TAG, "prepareJpegForAi failed", e)
            null
        }
    }

    /**
     * Decode via [ImageDecoder] (API 28+). Applies EXIF orientation itself —
     * do not run [applyExifRotation] on the result.
     */
    private fun decodeViaImageDecoder(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            if (com.toolstack.io.BuildConfig.DEBUG) {
                Log.w(TAG, "ImageDecoder failed for $uri", e)
            }
            null
        }
    }

    /**
     * Decode via [BitmapFactory] with bounds pass + [inSampleSize]. EXIF is
     * NOT applied — callers must run [applyExifRotation] on the result.
     */
    private suspend fun decodeViaBitmapFactory(uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = appContext.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    ?: return@withContext null
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AiImportConfig.MAX_IMAGE_EDGE_PX) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (com.toolstack.io.BuildConfig.DEBUG) {
                    Log.w(TAG, "BitmapFactory decode failed for $uri", e)
                }
                null
            }
        }

    private suspend fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap =
        withContext(Dispatchers.IO) {
            val orientation = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bitmap
            } else {
                Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(degrees) }, true
                )
            }
        }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
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
        lastScanJpeg = null
        _uiState.update {
            it.copy(isScanProcessing = false, isAiProcessing = false, scanError = null)
        }
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
        /**
         * An ML Kit parse with fewer rows than this — or one where over half
         * the rows are low-confidence — is treated as a poor result and gets
         * a "Try AI import" offer.
         */
        private const val POOR_RESULT_MIN_ITEMS = 2
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
    /** AI import failed because the device has no usable network. */
    data object AI_NETWORK_ERROR : ScanError
    /** The image was rejected by the model's safety filters. */
    data object AI_BLOCKED : ScanError
    /** AI import failed for another reason (timeout, quota, malformed response). */
    data object AI_FAILED : ScanError
    /** Pro user hit the hidden daily AI cap — resets tomorrow. */
    data object AI_LIMIT_REACHED : ScanError
    /** The model's request quota is exhausted right now — retry shortly. */
    data object AI_RATE_LIMITED : ScanError
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
    val pendingScanResult: List<IngredientItem>? = null,
    // ── AI import ─────────────────────────────────────────────────────────────
    /** True once the user holds an active Pro entitlement. */
    val isPremium: Boolean = false,
    /** Imports a free user can still run — free allowance plus bonus credits. */
    val aiScansRemaining: Int = AiImportConfig.FREE_LIFETIME_LIMIT,
    /** True while a rewarded ad can be shown (see [RewardedAdProvider]). */
    val rewardedAdAvailable: Boolean = false,
    /** True while a rewarded ad is being loaded/shown. */
    val isAdLoading: Boolean = false,
    /** Bonus scans just granted (purchase or ad) — surfaced as a snackbar, then cleared. */
    val creditsGranted: Int? = null,
    /** ScanSourceDialog toggle: route scans through the AI importer. */
    val useAiImport: Boolean = false,
    /** True while an AI import request is in flight. */
    val isAiProcessing: Boolean = false,
    /** Free user exhausted imports — show the AI paywall dialog. */
    val showAiPaywall: Boolean = false,
    /** Products listed in the AI paywall dialog (mirrors BillingRepository). */
    val availableProducts: List<BillingProduct> = emptyList(),
    /** Purchase-flow error surfaced by the AI paywall host. */
    val purchaseError: String? = null,
    /** A poor/failed ML Kit scan left a cached JPEG that AI can retry on. */
    val canOfferAiRetry: Boolean = false,
    /** An AI network failure left a cached JPEG that ML Kit can fall back to. */
    val canOfferMlKitFallback: Boolean = false
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

/** Maps [PurchaseState] to a simple boolean. Mirrors the same extension in HomeViewModel. */
private val PurchaseState.isActive: Boolean
    get() = this is PurchaseState.ActiveSubscription || this is PurchaseState.LifetimePurchase
