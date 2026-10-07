package com.toolstack.io.ui.recipescaler

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.CopyFormat
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.SavedRecipe
import com.toolstack.io.domain.model.ScaledIngredient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RecipeCardShape = RoundedCornerShape(16.dp)
private val LowConfidenceAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScalerScreen(
    onBack: () -> Unit,
    onAddShortcut: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: RecipeScalerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val showAccessory = uiState.focusedIngredientId != null &&
        (uiState.focusedField == FocusedIngredientField.QUANTITY ||
            uiState.focusedField == FocusedIngredientField.UNIT)
    var accessoryBarHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding by animateDpAsState(
        targetValue = if (showAccessory) {
            with(density) { accessoryBarHeightPx.toDp() }
        } else {
            24.dp + navBarBottomDp
        },
        label = "ingredient_list_bottom_padding"
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.recipe_scaler_copied_toast)
    // Lift the snackbar clear of the accessory bar while it's visible.
    val snackbarBottomPadding by animateDpAsState(
        targetValue = if (showAccessory) {
            with(density) { accessoryBarHeightPx.toDp() }
        } else {
            0.dp
        },
        label = "snackbar_bottom_padding"
    )

    // ── Scan: source picker dialog ────────────────────────────────────────────
    var showScanSourceDialog by rememberSaveable { mutableStateOf(false) }

    // ── Clear-all confirmation ────────────────────────────────────────────────
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }

    // ── Scan: pending camera URI (survives recomposition + activity recreation) ─
    // rememberSaveable persists the Parcelable URI across process death so the
    // TakePicture result callback can still retrieve it after the camera app
    // returns to a recreated activity.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // ── Gallery picker launcher (Photo Picker — no storage permission needed) ──
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageCaptured(uri)
        } else {
            viewModel.onScanCancelled()
        }
    }

    // ── Camera launcher ───────────────────────────────────────────────────────
    // TakePicture grants the camera app a URI permission, so no CAMERA runtime
    // permission is needed.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onImageCaptured(uri)
        } else {
            viewModel.onScanCancelled()
        }
        pendingCameraUri = null
    }

    // ── ML Kit Document Scanner launcher ──────────────────────────────────────
    // Play-Services-provided scan flow: live edge detection, auto-capture,
    // perspective correction, cleanup filters, and built-in gallery import.
    // Requires no CAMERA permission and no activity result contract of our own.
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
        } else {
            null
        }
        if (uri != null) {
            viewModel.onImageCaptured(uri, deleteWhenDone = true)
        } else {
            viewModel.onScanCancelled()
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showScanSourceDialog) {
        ScanSourceDialog(
            onTakePhoto = {
                showScanSourceDialog = false
                val uri = viewModel.createCameraImageUri()
                if (uri == null) {
                    viewModel.onCameraUnavailable()
                } else {
                    pendingCameraUri = uri
                    try {
                        cameraLauncher.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        pendingCameraUri = null
                        viewModel.onCameraUnavailable()
                    }
                }
            },
            onChooseGallery = {
                showScanSourceDialog = false
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onDismiss = {
                showScanSourceDialog = false
                viewModel.onScanCancelled()
            }
        )
    }

    // ── Scan import confirmation (existing list isn't blank) ──────────────────
    val pendingScan = uiState.pendingScanResult
    if (pendingScan != null) {
        AlertDialog(
            onDismissRequest = viewModel::onScanResultDismiss,
            title = {
                Text(
                    text = pluralStringResource(
                        R.plurals.recipe_scaler_scan_import_title,
                        pendingScan.size,
                        pendingScan.size
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.recipe_scaler_scan_import_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onScanResultReplace) {
                    Text(text = stringResource(R.string.recipe_scaler_scan_replace_action))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::onScanResultAppend) {
                        Text(text = stringResource(R.string.recipe_scaler_scan_add_to_list_action))
                    }
                    TextButton(onClick = viewModel::onScanResultDismiss) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    if (uiState.showShoppingListDialog) {
        ShoppingListDialog(
            shoppingListText = uiState.shoppingListText,
            onCopy = {
                copyToClipboard(context, uiState.shoppingListText)
                viewModel.onDismissShoppingListDialog()
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
            },
            onDismiss = viewModel::onDismissShoppingListDialog
        )
    }

    if (uiState.showCopyFormatDialog) {
        CopyFormatSheet(
            onFormatSelected = { format ->
                val text = RecipeScalerCalculator.generateFormattedRecipe(
                    uiState.scaledIngredients,
                    uiState.desiredServingsText,
                    format
                )
                copyToClipboard(context, text)
                viewModel.onDismissCopyFormatDialog()
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
            },
            onDismiss = viewModel::onDismissCopyFormatDialog
        )
    }

    if (uiState.showSaveListDialog) {
        SaveListSheet(
            savedRecipes = uiState.savedRecipes,
            onSelectOverwrite = viewModel::onSelectOverwriteRecipe,
            onSaveAsNew = viewModel::onChooseSaveAsNew,
            onDismiss = viewModel::onDismissSaveListDialog
        )
    }

    if (uiState.showSaveRecipeDialog) {
        SaveRecipeDialog(
            recipeNameInput = uiState.recipeNameInput,
            overwritingRecipeName = uiState.selectedRecipeForOverwrite,
            errorMessage = uiState.saveRecipeError,
            onRecipeNameChanged = viewModel::onRecipeNameInputChanged,
            onSave = viewModel::onSaveRecipe,
            onDismiss = viewModel::onDismissSaveRecipeDialog
        )
    }

    if (uiState.showLoadRecipeDialog) {
        LoadRecipeSheet(
            savedRecipes = uiState.savedRecipes,
            onLoadRecipe = viewModel::onLoadRecipe,
            onDeleteRecipe = viewModel::onDeleteRecipe,
            onDismiss = viewModel::onDismissLoadRecipeDialog
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(text = stringResource(R.string.recipe_scaler_clear_all_title)) },
            text = {
                Text(
                    text = stringResource(R.string.recipe_scaler_clear_all_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        viewModel.clearAllIngredients()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.recipe_scaler_clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.recipe_scaler_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back)
                        )
                    }
                },
                actions = {
                    // ── Scan button ──────────────────────────────────────────
                    // Prefer the ML Kit Document Scanner; on failure
                    // (UNSUPPORTED <1.7 GB RAM, missing Play Services) fall
                    // back silently to the camera/gallery source dialog.
                    val startScan: () -> Unit = {
                        val activity = context as? Activity
                        if (activity == null) {
                            showScanSourceDialog = true
                        } else {
                            viewModel.onScanLaunchStarted()
                            scanner.getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender ->
                                    try {
                                        scannerLauncher.launch(
                                            IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    } catch (e: Exception) {
                                        viewModel.onScanCancelled()
                                        showScanSourceDialog = true
                                    }
                                }
                                .addOnFailureListener {
                                    viewModel.onScanCancelled()
                                    showScanSourceDialog = true
                                }
                        }
                    }
                    IconButton(
                        onClick = startScan,
                        enabled = !uiState.isScanProcessing,
                        colors = IconButtonDefaults.iconButtonColors(
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (uiState.isScanProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.recipe_scaler_scan_recipe)
                            )
                        }
                    }
                    if (onAddShortcut != null) {
                        IconButton(onClick = onAddShortcut) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                                contentDescription = stringResource(R.string.content_description_add_shortcut)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(
                        WindowInsets.ime
                            .union(WindowInsets.navigationBars)
                            .asPaddingValues()
                    )
                    .padding(bottom = snackbarBottomPadding)
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = listBottomPadding,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    OriginalRecipeCard(
                        originalServingsText = uiState.originalServingsText,
                        desiredServingsText = uiState.desiredServingsText,
                        ingredients = uiState.ingredients,
                        focusedIngredientId = uiState.focusedIngredientId,
                        focusedField = uiState.focusedField,
                        isScanProcessing = uiState.isScanProcessing,
                        scanError = uiState.scanError,
                        onOriginalServingsChanged = viewModel::onOriginalServingsChanged,
                        onDesiredServingsChanged = viewModel::onDesiredServingsChanged,
                        onClearAll = { showClearAllConfirm = true },
                        ingredientCallbacks = IngredientRowCallbacks(
                            onQuantityChanged = viewModel::onIngredientQuantityChanged,
                            onNameChanged = viewModel::onIngredientNameChanged,
                            onRemove = viewModel::removeIngredient,
                            onFocusChanged = viewModel::setFocusedIngredient,
                            onFocusCleared = { id, field ->
                                viewModel.clearFocusedIngredient(id, field)
                            },
                            onNameDone = { id ->
                                val ingredients = uiState.ingredients
                                val currentIndex = ingredients.indexOfFirst { it.id == id }
                                val nextIngredient = ingredients.getOrNull(currentIndex + 1)
                                if (nextIngredient != null) {
                                    viewModel.setFocusedIngredient(nextIngredient.id, FocusedIngredientField.QUANTITY)
                                } else if (ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS) {
                                    viewModel.addIngredientAndFocusName()
                                }
                            }
                        ),
                        onAddIngredient = viewModel::addIngredientAndFocusName,
                        canAddMore = uiState.ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS,
                        onLoadRecipe = viewModel::onShowLoadRecipeDialog,
                        onSaveRecipe = viewModel::onShowSaveRecipeDialog,
                        canLoad = uiState.savedRecipes.isNotEmpty(),
                        canSave = uiState.ingredients.any { it.name.isNotBlank() },
                        onDismissScanError = viewModel::onDismissScanError
                    )
                }

                item {
                    ScaledRecipeCard(
                        scaledIngredients      = uiState.scaledIngredients,
                        multiplierText         = uiState.multiplierText,
                        showOriginalValues     = uiState.showOriginalValues,
                        keepOriginalUnits      = uiState.keepOriginalUnits,
                        showScaleWarning       = uiState.showScaleWarning,
                        onCopyRecipe           = viewModel::onShowCopyFormatDialog,
                        onToggleOriginalValues = viewModel::toggleShowOriginalValues,
                        onToggleKeepOriginalUnits = viewModel::toggleKeepOriginalUnits
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(
                visible = showAccessory,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                // Retain the last shown target so the bar keeps rendering while it
                // slides out — uiState has already moved on to NAME/NONE by then,
                // which would make IngredientInputAccessoryBar return early.
                var shownIngredientId by remember { mutableStateOf(uiState.focusedIngredientId) }
                var shownField by remember { mutableStateOf(uiState.focusedField) }
                if (showAccessory) {
                    shownIngredientId = uiState.focusedIngredientId
                    shownField = uiState.focusedField
                }
                IngredientInputAccessoryBar(
                    focusedIngredientId = shownIngredientId,
                    focusedField = shownField,
                    currentUnit = uiState.ingredients
                        .find { it.id == shownIngredientId }
                        ?.unit,
                    onFractionClick = { fraction ->
                        shownIngredientId?.let { id ->
                            viewModel.appendFractionToIngredient(id, fraction)
                        }
                    },
                    onUnitClick = { unit ->
                        shownIngredientId?.let { id ->
                            viewModel.onIngredientUnitChanged(id, unit)
                            viewModel.setFocusedIngredient(id, FocusedIngredientField.NAME)
                        }
                    },
                    onNextClick = {
                        val ingredients = uiState.ingredients
                        val currentIndex = ingredients.indexOfFirst { it.id == shownIngredientId }
                        val nextIngredient = ingredients.getOrNull(currentIndex + 1)
                        if (nextIngredient != null) {
                            viewModel.setFocusedIngredient(nextIngredient.id, FocusedIngredientField.QUANTITY)
                        } else if (ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS) {
                            viewModel.addIngredientAndFocusName()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .onSizeChanged { accessoryBarHeightPx = it.height }
                )
            }
        }
    }
}

// ── Scan source picker dialog ─────────────────────────────────────────────────

/**
 * Fallback source picker — shown when the ML Kit Document Scanner is
 * unavailable (unsupported device, missing Play Services, or no Activity
 * context). Lets the user choose between the camera and the gallery.
 *
 * No CAMERA permission is required: TakePicture grants the camera app a scoped
 * URI permission for the photo file. [onTakePhoto] launches the camera
 * directly; any launch failure is handled by the caller.
 */
@Composable
private fun ScanSourceDialog(
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_scan_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.recipe_scaler_scan_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(4.dp))
                // Camera button — opens the camera via TakePicture
                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_scan_take_photo))
                }
                OutlinedButton(
                    onClick = onChooseGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_scan_choose_gallery))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

// ── OriginalRecipeCard ────────────────────────────────────────────────────────

/** Per-row callbacks for an ingredient entry, keyed by ingredient id. */
private data class IngredientRowCallbacks(
    val onQuantityChanged: (String, String) -> Unit,
    val onNameChanged: (String, String) -> Unit,
    val onRemove: (String) -> Unit,
    val onFocusChanged: (String, FocusedIngredientField) -> Unit,
    val onFocusCleared: (String, FocusedIngredientField) -> Unit,
    val onNameDone: (String) -> Unit
)

@Composable
private fun OriginalRecipeCard(
    originalServingsText: String,
    desiredServingsText: String,
    ingredients: List<IngredientItem>,
    focusedIngredientId: String?,
    focusedField: FocusedIngredientField?,
    isScanProcessing: Boolean,
    scanError: ScanError?,
    onOriginalServingsChanged: (String) -> Unit,
    onDesiredServingsChanged: (String) -> Unit,
    onClearAll: () -> Unit,
    ingredientCallbacks: IngredientRowCallbacks,
    onAddIngredient: () -> Unit,
    canAddMore: Boolean,
    onLoadRecipe: () -> Unit,
    onSaveRecipe: () -> Unit,
    canLoad: Boolean,
    canSave: Boolean,
    onDismissScanError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RecipeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StepBadge(step = "1", emphasized = false)
                    Text(
                        text = stringResource(R.string.recipe_scaler_original_recipe),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (ingredients.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.recipe_scaler_clear_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Scan processing indicator ─────────────────────────────────────
            AnimatedVisibility(
                visible = isScanProcessing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.recipe_scaler_scan_processing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ── Scan error banner ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = scanError != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // Retain the last error so the banner stays rendered while it
                // animates out after dismissal.
                var shownError by remember { mutableStateOf(scanError) }
                if (scanError != null) shownError = scanError
                shownError?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    when (error) {
                                        ScanError.NO_INGREDIENTS_FOUND ->
                                            R.string.recipe_scaler_scan_error_no_ingredients
                                        ScanError.RECOGNITION_FAILED ->
                                            R.string.recipe_scaler_scan_error_failed
                                        ScanError.CAMERA_UNAVAILABLE ->
                                            R.string.recipe_scaler_scan_error_camera_unavailable
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissScanError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            val scaleToFocusRequester = remember { FocusRequester() }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_scaler_serves_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ServingsField(
                            value = originalServingsText,
                            onValueChange = onOriginalServingsChanged,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { scaleToFocusRequester.requestFocus() }
                            )
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_scaler_scale_to_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ServingsField(
                            value = desiredServingsText,
                            onValueChange = onDesiredServingsChanged,
                            modifier = Modifier.focusRequester(scaleToFocusRequester)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.recipe_scaler_qty_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_unit_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_ingredient_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(2.2f)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            ingredients.forEach { ingredient ->
                key(ingredient.id) {
                    CompactIngredientRow(
                        ingredient = ingredient,
                        onQuantityChanged = { ingredientCallbacks.onQuantityChanged(ingredient.id, it) },
                        onNameChanged = { ingredientCallbacks.onNameChanged(ingredient.id, it) },
                        onRemove = { ingredientCallbacks.onRemove(ingredient.id) },
                        onDone = { ingredientCallbacks.onNameDone(ingredient.id) },
                        onFocusChanged = { field -> ingredientCallbacks.onFocusChanged(ingredient.id, field) },
                        onFocusCleared = { field -> ingredientCallbacks.onFocusCleared(ingredient.id, field) },
                        focusedField = if (focusedIngredientId == ingredient.id) focusedField else null
                    )
                }
            }

            if (canAddMore) {
                OutlinedButton(
                    onClick = onAddIngredient,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_add_ingredient))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLoadRecipe,
                    modifier = Modifier.weight(1f),
                    enabled = canLoad,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_load_recipe))
                }

                FilledTonalButton(
                    onClick = onSaveRecipe,
                    modifier = Modifier.weight(1f),
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_save_recipe))
                }
            }
        }
    }
}

// ── CompactIngredientRow ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactIngredientRow(
    ingredient: IngredientItem,
    onQuantityChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    onFocusChanged: (FocusedIngredientField) -> Unit,
    onFocusCleared: (FocusedIngredientField) -> Unit,
    focusedField: FocusedIngredientField?
) {
    val quantityFocusRequester = remember { FocusRequester() }
    val unitFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    var qtyFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = ingredient.qtyString,
                selection = TextRange(0)  // cursor at start so leading digits are visible
            )
        )
    }
    LaunchedEffect(ingredient.qtyString) {
        if (ingredient.qtyString != qtyFieldValue.text) {
            qtyFieldValue = TextFieldValue(
                text = ingredient.qtyString,
                selection = TextRange(ingredient.qtyString.length)
            )
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )
    // Amber-outlined variant for qty/name fields on rows the OCR flagged as
    // low-confidence — a subtle "please check this" hint. Cleared on any edit.
    val lowConfidenceFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = LowConfidenceAmber,
        focusedBorderColor = LowConfidenceAmber
    )
    val flaggedFieldColors = if (ingredient.lowConfidence) lowConfidenceFieldColors else fieldColors

    fun bringRowIntoView() {
        scope.launch {
            delay(280)
            bringIntoViewRequester.bringIntoView()
        }
    }

    // The single delayed bringIntoView above can land before the keyboard
    // finishes opening; re-run it as the IME inset animates so a newly
    // focused row isn't left hidden behind the keyboard.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && focusedField != null) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(ingredient.id) {
        if (focusedField == FocusedIngredientField.QUANTITY ||
            focusedField == FocusedIngredientField.NAME) {
            delay(150)
            when (focusedField) {
                FocusedIngredientField.QUANTITY -> quantityFocusRequester.requestFocus()
                FocusedIngredientField.NAME -> nameFocusRequester.requestFocus()
                else -> Unit
            }
            bringRowIntoView()
        }
    }

    LaunchedEffect(focusedField) {
        when (focusedField) {
            FocusedIngredientField.NAME -> {
                delay(50)
                nameFocusRequester.requestFocus()
                bringRowIntoView()
            }
            FocusedIngredientField.QUANTITY -> {
                delay(50)
                quantityFocusRequester.requestFocus()
                bringRowIntoView()
            }
            else -> Unit
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = qtyFieldValue,
            onValueChange = { newValue ->
                qtyFieldValue = newValue
                onQuantityChanged(newValue.text)
            },
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.recipe_scaler_quantity),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    keyboardController?.hide()
                    unitFocusRequester.requestFocus()
                }
            ),
            colors = flaggedFieldColors,
            modifier = Modifier
                .weight(1.1f)
                .focusRequester(quantityFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocusChanged(FocusedIngredientField.QUANTITY)
                        bringRowIntoView()
                    } else {
                        onFocusCleared(FocusedIngredientField.QUANTITY)
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = RecipeScalerCalculator.ALL_UNITS.find { it.first == ingredient.unit }?.second ?: "-",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.recipe_scaler_unit),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = {
                    keyboardController?.hide()
                    nameFocusRequester.requestFocus()
                }
            ),
            colors = fieldColors,
            modifier = Modifier
                .weight(1.1f)
                .focusRequester(unitFocusRequester)
                .clickable {
                    keyboardController?.hide()
                    unitFocusRequ ester.requestFocus()
                }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        keyboardController?.hide()
                        onFocusChanged(FocusedIngredientField.UNIT)
                        bringRowIntoView()
                    } else {
                        onFocusCleared(FocusedIngredientField.UNIT)
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        )

        OutlinedTextField(
            value = ingredient.name,
            onValueChange = onNameChanged,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.recipe_scaler_ingredient_name),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onDone() }
            ),
            colors = flaggedFieldColors,
            trailingIcon = if (ingredient.lowConfidence) {
                {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.recipe_scaler_low_confidence_hint),
                        tint = LowConfidenceAmber,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .weight(2.2f)
                .focusRequester(nameFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocusChanged(FocusedIngredientField.NAME)
                        bringRowIntoView()
                    } else {
                        onFocusCleared(FocusedIngredientField.NAME)
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge
        )

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.recipe_scaler_remove_ingredient),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── ScaledRecipeCard ──────────────────────────────────────────────────────────

@Composable
private fun ScaledRecipeCard(
    scaledIngredients: List<ScaledIngredient>,
    multiplierText: String,
    showOriginalValues: Boolean,
    keepOriginalUnits: Boolean,
    showScaleWarning: Boolean,
    onCopyRecipe: () -> Unit,
    onToggleOriginalValues: () -> Unit,
    onToggleKeepOriginalUnits: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RecipeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepBadge(step = "2", emphasized = true)
                Text(
                    text = stringResource(R.string.recipe_scaler_scaled_recipe),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (multiplierText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_scaler_multiplier, multiplierText),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (scaledIngredients.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showScaleWarning,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.recipe_scaler_scale_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showOriginalValues,
                        onClick = onToggleOriginalValues,
                        label = {
                            Text(
                                text = if (showOriginalValues) {
                                    stringResource(R.string.recipe_scaler_hide_original)
                                } else {
                                    stringResource(R.string.recipe_scaler_show_original)
                                }
                            )
                        }
                    )
                    FilterChip(
                        selected = keepOriginalUnits,
                        onClick = onToggleKeepOriginalUnits,
                        label = {
                            Text(text = stringResource(R.string.recipe_scaler_keep_original_units))
                        }
                    )
                }

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    scaledIngredients.forEachIndexed { index, ingredient ->
                        ScaledIngredientRow(
                            ingredient = ingredient,
                            showOriginalValues = showOriginalValues,
                            keepOriginalUnits = keepOriginalUnits
                        )
                        if (index != scaledIngredients.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }

                Button(
                    onClick = onCopyRecipe,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_copy_scaled_recipe))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = stringResource(R.string.recipe_scaler_empty_scaled_recipe),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.recipe_scaler_empty_scaled_recipe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── ScaledIngredientRow ───────────────────────────────────────────────────────

@Composable
private fun ScaledIngredientRow(
    ingredient: ScaledIngredient,
    showOriginalValues: Boolean,
    keepOriginalUnits: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ingredient.name.ifBlank {
                stringResource(R.string.recipe_scaler_unnamed_ingredient)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = ingredient.displayText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End
            )
            if (showOriginalValues && ingredient.originalQty > 0) {
                val originalText = RecipeScalerCalculator.formatQuantity(
                    ingredient.originalQty, ingredient.unit, keepOriginalUnits
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_was_original, originalText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── StepBadge ─────────────────────────────────────────────────────────────────

@Composable
private fun StepBadge(step: String, emphasized: Boolean) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── ServingsField ─────────────────────────────────────────────────────────────

@Composable
private fun ServingsField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        modifier = modifier.width(96.dp),
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ── CopyFormatSheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyFormatSheet(
    onFormatSelected: (CopyFormat) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.recipe_scaler_select_copy_format),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        CopyFormatItem(
            icon = Icons.Filled.Description,
            label = stringResource(R.string.recipe_scaler_format_plain),
            description = stringResource(R.string.recipe_scaler_format_plain_desc),
            onClick = { onFormatSelected(CopyFormat.PLAIN) }
        )
        CopyFormatItem(
            icon = Icons.Filled.Code,
            label = stringResource(R.string.recipe_scaler_format_markdown),
            description = stringResource(R.string.recipe_scaler_format_markdown_desc),
            onClick = { onFormatSelected(CopyFormat.MARKDOWN) }
        )
        CopyFormatItem(
            icon = Icons.Filled.Checklist,
            label = stringResource(R.string.recipe_scaler_format_shopping_list),
            description = stringResource(R.string.recipe_scaler_format_shopping_list_desc),
            onClick = { onFormatSelected(CopyFormat.SHOPPING_LIST) }
        )
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun CopyFormatItem(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = label) },
        supportingContent = { Text(text = description) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ── ShoppingListDialog ────────────────────────────────────────────────────────

@Composable
private fun ShoppingListDialog(
    shoppingListText: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_shopping_list_title)) },
        text = {
            Text(
                text = shoppingListText.ifBlank { stringResource(R.string.recipe_scaler_no_ingredients) },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text(text = stringResource(R.string.recipe_scaler_copy_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

// ── SaveRecipeDialog ──────────────────────────────────────────────────────────

@Composable
private fun SaveRecipeDialog(
    recipeNameInput: String,
    overwritingRecipeName: String?,
    errorMessage: String?,
    onRecipeNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (overwritingRecipeName != null) {
                    stringResource(R.string.recipe_scaler_overwrite_recipe_title)
                } else {
                    stringResource(R.string.recipe_scaler_save_recipe_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overwritingRecipeName != null) {
                    Text(
                        text = stringResource(
                            R.string.recipe_scaler_overwrite_recipe_message,
                            overwritingRecipeName
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.recipe_scaler_save_recipe_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = recipeNameInput,
                    onValueChange = onRecipeNameChanged,
                    label = { Text(text = stringResource(R.string.recipe_scaler_recipe_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = recipeNameInput.trim().isNotBlank()
            ) {
                Text(
                    text = if (overwritingRecipeName != null) {
                        stringResource(R.string.recipe_scaler_overwrite_action)
                    } else {
                        stringResource(R.string.recipe_scaler_save_action)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

// ── SaveListSheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveListSheet(
    savedRecipes: List<SavedRecipe>,
    onSelectOverwrite: (SavedRecipe) -> Unit,
    onSaveAsNew: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.recipe_scaler_save_list_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                Text(
                    text = stringResource(R.string.recipe_scaler_save_list_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = onSaveAsNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_save_as_new))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = stringResource(R.string.recipe_scaler_or_overwrite),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(savedRecipes, key = { _, recipe -> recipe.name }) { _, recipe ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectOverwrite(recipe) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    R.string.recipe_scaler_recipe_details,
                                    recipe.servings,
                                    recipe.ingredients.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── LoadRecipeSheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadRecipeSheet(
    savedRecipes: List<SavedRecipe>,
    onLoadRecipe: (SavedRecipe) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var recipeToDelete by remember { mutableStateOf<String?>(null) }

    if (recipeToDelete != null) {
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text(text = stringResource(R.string.recipe_scaler_delete_recipe_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.recipe_scaler_delete_recipe_message,
                        recipeToDelete!!
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecipe(recipeToDelete!!)
                        recipeToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.recipe_scaler_delete_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (savedRecipes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.recipe_scaler_load_recipe_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_no_saved_recipes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.recipe_scaler_load_recipe_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                itemsIndexed(savedRecipes, key = { _, recipe -> recipe.name }) { _, recipe ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLoadRecipe(recipe) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recipe.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.recipe_scaler_recipe_details,
                                        recipe.servings,
                                        recipe.ingredients.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { recipeToDelete = recipe.name }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.recipe_scaler_delete_recipe_content_description
                                    ),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Clipboard helper ──────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scaled Recipe", text)
    clipboard.setPrimaryClip(clip)
}
