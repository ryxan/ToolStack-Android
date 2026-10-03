package com.toolstack.io.ui.recipescaler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.CopyFormat
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientItem
import com.toolstack.io.domain.model.SavedRecipe
import com.toolstack.io.domain.model.ScaledIngredient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RecipeCardShape = RoundedCornerShape(16.dp)

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

    if (uiState.showShoppingListDialog) {
        ShoppingListDialog(
            shoppingListText = uiState.shoppingListText,
            onCopy = {
                copyToClipboard(context, uiState.shoppingListText)
                viewModel.onDismissShoppingListDialog()
            },
            onDismiss = viewModel::onDismissShoppingListDialog
        )
    }

    if (uiState.showCopyFormatDialog) {
        CopyFormatDialog(
            onFormatSelected = { format ->
                val text = RecipeScalerCalculator.generateFormattedRecipe(
                    uiState.scaledIngredients,
                    uiState.desiredServingsText,
                    format,
                    uiState.simplifyFractions
                )
                copyToClipboard(context, text)
                viewModel.onDismissCopyFormatDialog()
            },
            onDismiss = viewModel::onDismissCopyFormatDialog
        )
    }

    if (uiState.showSaveListDialog) {
        SaveListDialog(
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
        LoadRecipeDialog(
            savedRecipes = uiState.savedRecipes,
            onLoadRecipe = viewModel::onLoadRecipe,
            onDeleteRecipe = viewModel::onDeleteRecipe,
            onDismiss = viewModel::onDismissLoadRecipeDialog
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
                    bottom = if (showAccessory) {
                        with(density) { accessoryBarHeightPx.toDp() }
                    } else {
                        24.dp + navBarBottomDp
                    },
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
                        onOriginalServingsChanged = viewModel::onOriginalServingsChanged,
                        onDesiredServingsChanged = viewModel::onDesiredServingsChanged,
                        onClearAll = viewModel::clearAllIngredients,
                        onIngredientQuantityChanged = viewModel::onIngredientQuantityChanged,
                        onIngredientNameChanged = viewModel::onIngredientNameChanged,
                        onRemoveIngredient = viewModel::removeIngredient,
                        onAddIngredient = viewModel::addIngredient,
                        canAddMore = uiState.ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS,
                        onLoadRecipe = viewModel::onShowLoadRecipeDialog,
                        onSaveRecipe = viewModel::onShowSaveRecipeDialog,
                        canLoad = uiState.savedRecipes.isNotEmpty(),
                        canSave = uiState.ingredients.any { it.name.isNotBlank() },
                        onIngredientFocusChanged = viewModel::setFocusedIngredient,
                        onIngredientFocusCleared = { id, field ->
                            viewModel.clearFocusedIngredient(id, field)
                        },
                        onIngredientNameDone = { id ->
                            val ingredients = uiState.ingredients
                            val currentIndex = ingredients.indexOfFirst { it.id == id }
                            val nextIngredient = ingredients.getOrNull(currentIndex + 1)
                            if (nextIngredient != null) {
                                viewModel.setFocusedIngredient(nextIngredient.id, FocusedIngredientField.QUANTITY)
                            } else if (ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS) {
                                viewModel.addIngredientAndFocusName()
                            }
                        }
                    )
                }

                item {
                    ScaledRecipeCard(
                        scaledIngredients = uiState.scaledIngredients,
                        multiplierText = uiState.multiplierText,
                        showOriginalValues = uiState.showOriginalValues,
                        simplifyFractions = uiState.simplifyFractions,
                        onCopyRecipe = viewModel::onShowCopyFormatDialog,
                        onToggleOriginalValues = viewModel::toggleShowOriginalValues,
                        onToggleSimplifyFractions = viewModel::toggleSimplifyFractions
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.disclaimer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (showAccessory) {
                IngredientInputAccessoryBar(
                    focusedIngredientId = uiState.focusedIngredientId,
                    focusedField = uiState.focusedField,
                    currentUnit = uiState.ingredients
                        .find { it.id == uiState.focusedIngredientId }
                        ?.unit,
                    onFractionClick = { fraction ->
                        uiState.focusedIngredientId?.let { id ->
                            viewModel.appendFractionToIngredient(id, fraction)
                        }
                    },
                    onUnitClick = { unit ->
                        uiState.focusedIngredientId?.let { id ->
                            viewModel.onIngredientUnitChanged(id, unit)
                            // After unit is selected, advance to name field
                            viewModel.setFocusedIngredient(id, FocusedIngredientField.NAME)
                        }
                    },
                    onNextClick = {
                        val ingredients = uiState.ingredients
                        val currentId = uiState.focusedIngredientId
                        val currentIndex = ingredients.indexOfFirst { it.id == currentId }
                        val nextIngredient = ingredients.getOrNull(currentIndex + 1)
                        if (nextIngredient != null) {
                            // Advance focus to the next ingredient's quantity field.
                            viewModel.setFocusedIngredient(nextIngredient.id, FocusedIngredientField.QUANTITY)
                        } else if (ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS) {
                            // No next ingredient — add a new row and focus its quantity field.
                            viewModel.addIngredientAndFocusName()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .onSizeChanged { accessoryBarHeightPx = it.height }
                )
            }
        }
    }
}

@Composable
private fun OriginalRecipeCard(
    originalServingsText: String,
    desiredServingsText: String,
    ingredients: List<IngredientItem>,
    focusedIngredientId: String?,
    focusedField: FocusedIngredientField?,
    onOriginalServingsChanged: (String) -> Unit,
    onDesiredServingsChanged: (String) -> Unit,
    onClearAll: () -> Unit,
    onIngredientQuantityChanged: (String, String) -> Unit,
    onIngredientNameChanged: (String, String) -> Unit,
    onRemoveIngredient: (String) -> Unit,
    onAddIngredient: () -> Unit,
    canAddMore: Boolean,
    onLoadRecipe: () -> Unit,
    onSaveRecipe: () -> Unit,
    canLoad: Boolean,
    canSave: Boolean,
    onIngredientFocusChanged: (String, FocusedIngredientField) -> Unit,
    onIngredientFocusCleared: (String, FocusedIngredientField) -> Unit,
    onIngredientNameDone: (String) -> Unit
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Serves" column
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
                            onValueChange = onOriginalServingsChanged
                        )
                    }

                    // Arrow divider
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                            // mirror to point right
                            .then(
                                Modifier.graphicsLayer { rotationZ = 180f }
                            )
                    )

                    // "Scale to" column
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
                            onValueChange = onDesiredServingsChanged
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            ingredients.forEach { ingredient ->
                key(ingredient.id) {
                    CompactIngredientRow(
                        ingredient = ingredient,
                        onQuantityChanged = { onIngredientQuantityChanged(ingredient.id, it) },
                        onNameChanged = { onIngredientNameChanged(ingredient.id, it) },
                        onRemove = { onRemoveIngredient(ingredient.id) },
                        onDone = { onIngredientNameDone(ingredient.id) },
                        onFocusChanged = { field -> onIngredientFocusChanged(ingredient.id, field) },
                        onFocusCleared = { field -> onIngredientFocusCleared(ingredient.id, field) },
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

                Button(
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

    // Local TextFieldValue for qty so we control cursor position.
    // Initialized once; kept stable so local edits preserve the cursor.
    // When qtyString changes externally (e.g. fraction insert), we detect the
    // divergence in a LaunchedEffect and reinitialize with the cursor at end-of-text.
    var qtyFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = ingredient.qtyString,
                selection = TextRange(ingredient.qtyString.length)
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
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )

    fun bringRowIntoView() {
        scope.launch {
            delay(280)
            bringIntoViewRequester.bringIntoView()
        }
    }

    // Case 1 — NEW row: focusedField is already set to the desired value on first
    // composition (the ViewModel sets it atomically with the new ingredient). Keyed on
    // ingredient.id so it runs exactly once per row lifetime, after the first layout
    // pass, giving the FocusRequester time to attach to the layout tree. A 150 ms delay
    // covers both the layout pass and the soft-keyboard animation settling.
    // Keying on id (not Unit) means the effect is also cancelled if the row is ever
    // removed and re-added with a different id.
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

    // Case 2 — EXISTING row: focusedField changes after the row is already fully laid
    // out (e.g. advancing from a previous row via the IME Next action). The row's
    // FocusRequester is already attached, so a short delay is sufficient.
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
            colors = fieldColors,
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
                    unitFocusRequester.requestFocus()
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
            colors = fieldColors,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScaledRecipeCard(
    scaledIngredients: List<ScaledIngredient>,
    multiplierText: String,
    showOriginalValues: Boolean,
    simplifyFractions: Boolean,
    onCopyRecipe: () -> Unit,
    onToggleOriginalValues: () -> Unit,
    onToggleSimplifyFractions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RecipeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                        selected = simplifyFractions,
                        onClick = onToggleSimplifyFractions,
                        label = {
                            Text(text = stringResource(R.string.recipe_scaler_simplify_fractions))
                        }
                    )
                }

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        scaledIngredients.forEachIndexed { index, ingredient ->
                            ScaledIngredientRow(
                                ingredient = ingredient,
                                showOriginalValues = showOriginalValues
                            )
                            if (index != scaledIngredients.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                }
            }
        }
    }
}

@Composable
private fun ScaledIngredientRow(
    ingredient: ScaledIngredient,
    showOriginalValues: Boolean
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
                val originalUnit = RecipeScalerCalculator.ALL_UNITS
                    .find { it.first == ingredient.unit }?.second.orEmpty()
                val originalDisplay = String.format(
                    java.util.Locale.US,
                    "%.2f",
                    ingredient.originalQty
                ).trimEnd('0').trimEnd('.')
                val originalText = if (originalUnit.isNotBlank()) {
                    "$originalDisplay $originalUnit"
                } else {
                    originalDisplay
                }
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

@Composable
private fun StepBadge(step: String, emphasized: Boolean) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
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

@Composable
private fun ServingsField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(80.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun CopyFormatDialog(
    onFormatSelected: (CopyFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_select_copy_format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onFormatSelected(CopyFormat.PLAIN) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.recipe_scaler_format_plain))
                }
                OutlinedButton(
                    onClick = { onFormatSelected(CopyFormat.MARKDOWN) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.recipe_scaler_format_markdown))
                }
                OutlinedButton(
                    onClick = { onFormatSelected(CopyFormat.SHOPPING_LIST) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.recipe_scaler_format_shopping_list))
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

@Composable
private fun SaveListDialog(
    savedRecipes: List<SavedRecipe>,
    onSelectOverwrite: (SavedRecipe) -> Unit,
    onSaveAsNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_save_list_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.recipe_scaler_save_list_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // "Save as New" always at the top
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
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_or_overwrite),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(savedRecipes, key = { _, recipe -> recipe.name }) { _, recipe ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectOverwrite(recipe) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun LoadRecipeDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_load_recipe_title)) },
        text = {
            if (savedRecipes.isEmpty()) {
                Text(
                    text = stringResource(R.string.recipe_scaler_no_saved_recipes),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.recipe_scaler_close_action))
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scaled Recipe", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.recipe_scaler_copied_toast), Toast.LENGTH_SHORT).show()
}
