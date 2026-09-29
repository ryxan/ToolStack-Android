package com.toolstack.io.ui.recipescaler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.domain.calculator.CopyFormat
import com.toolstack.io.domain.calculator.RecipeScalerCalculator
import com.toolstack.io.domain.model.IngredientState

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
            scaledIngredients = uiState.scaledIngredients,
            servings = uiState.desiredServingsText,
            simplifyFractions = uiState.simplifyFractions,
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

    if (uiState.showSaveRecipeDialog) {
        SaveRecipeDialog(
            recipeNameInput = uiState.recipeNameInput,
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
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Section 1: Original Recipe Card ───────────────────────────────
            item {
                OriginalRecipeCard(
                    originalServingsText = uiState.originalServingsText,
                    ingredients = uiState.ingredients,
                    onOriginalServingsChanged = viewModel::onOriginalServingsChanged,
                    onClearAll = viewModel::clearAllIngredients,
                    onIngredientQuantityChanged = viewModel::onIngredientQuantityChanged,
                    onIngredientUnitChanged = viewModel::onIngredientUnitChanged,
                    onIngredientNameChanged = viewModel::onIngredientNameChanged,
                    onRemoveIngredient = viewModel::removeIngredient,
                    onAddIngredient = viewModel::addIngredient,
                    canAddMore = uiState.ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS
                )
            }

            // ── Save/Load Recipe Buttons ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::onShowLoadRecipeDialog,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.savedRecipes.isNotEmpty()
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
                        onClick = viewModel::onShowSaveRecipeDialog,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.ingredients.any { it.name.isNotBlank() }
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

            // ── Section 2: Scaled Recipe Card ─────────────────────────────────
            item {
                ScaledRecipeCard(
                    desiredServingsText = uiState.desiredServingsText,
                    scaledIngredients = uiState.scaledIngredients,
                    multiplierText = uiState.multiplierText,
                    showOriginalValues = uiState.showOriginalValues,
                    simplifyFractions = uiState.simplifyFractions,
                    onDesiredServingsChanged = viewModel::onDesiredServingsChanged,
                    onCopyRecipe = viewModel::onShowCopyFormatDialog,
                    onToggleOriginalValues = viewModel::toggleShowOriginalValues,
                    onToggleSimplifyFractions = viewModel::toggleSimplifyFractions
                )
            }

            // ── Disclaimer ────────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Original Recipe Card ──────────────────────────────────────────────────────

@Composable
private fun OriginalRecipeCard(
    originalServingsText: String,
    ingredients: List<com.toolstack.io.domain.model.IngredientItem>,
    onOriginalServingsChanged: (String) -> Unit,
    onClearAll: () -> Unit,
    onIngredientQuantityChanged: (String, String) -> Unit,
    onIngredientUnitChanged: (String, String) -> Unit,
    onIngredientNameChanged: (String, String) -> Unit,
    onRemoveIngredient: (String) -> Unit,
    onAddIngredient: () -> Unit,
    canAddMore: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with step number and servings input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step number badge
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.recipe_scaler_original_recipe),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Serves input
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recipe_scaler_serves_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = originalServingsText,
                        onValueChange = onOriginalServingsChanged,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(72.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            // Clear all button
            if (ingredients.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.align(Alignment.Start)
                ) {
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

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.recipe_scaler_qty_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_unit_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.recipe_scaler_ingredient_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(2.5f)
                )
            }

            // Ingredient rows
            ingredients.forEach { ingredient ->
                CompactIngredientRow(
                    ingredient = ingredient,
                    onQuantityChanged = { onIngredientQuantityChanged(ingredient.id, it) },
                    onUnitChanged = { onIngredientUnitChanged(ingredient.id, it) },
                    onNameChanged = { onIngredientNameChanged(ingredient.id, it) },
                    onRemove = { onRemoveIngredient(ingredient.id) }
                )
            }

            // Add ingredient button
            if (canAddMore) {
                OutlinedButton(
                    onClick = onAddIngredient,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_add_ingredient))
                }
            }
        }
    }
}

// ── Compact Ingredient Row ────────────────────────────────────────────────────

@Composable
private fun CompactIngredientRow(
    ingredient: com.toolstack.io.domain.model.IngredientItem,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    var showUnitDialog by remember { mutableStateOf(false) }

    if (showUnitDialog) {
        UnitSelectorDialog(
            currentUnit = ingredient.unit,
            onUnitSelected = { selectedUnit ->
                onUnitChanged(selectedUnit)
                showUnitDialog = false
            },
            onDismiss = { showUnitDialog = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quantity
        OutlinedTextField(
            value = ingredient.qtyString,
            onValueChange = onQuantityChanged,
            singleLine = true,
            placeholder = { Text(text = "Qty", style = MaterialTheme.typography.bodySmall) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // Unit selector button
        OutlinedTextField(
            value = RecipeScalerCalculator.ALL_UNITS.find { it.first == ingredient.unit }?.second ?: "-",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .clickable { showUnitDialog = true },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            ),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.primary,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            enabled = false
        )

        // Ingredient name
        OutlinedTextField(
            value = ingredient.name,
            onValueChange = onNameChanged,
            singleLine = true,
            placeholder = { Text(text = "Ingredient", style = MaterialTheme.typography.bodySmall) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(2.5f),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

// ── Unit Selector Dialog ──────────────────────────────────────────────────────

@Composable
private fun UnitSelectorDialog(
    currentUnit: String,
    onUnitSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_select_unit)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Volume section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Volume",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Volume units - two columns
                    val volumeLeftColumn = listOf(
                        Pair("tsp", "tsp"),
                        Pair("Tbsp", "Tbsp"),
                        Pair("cup", "cup"),
                        Pair("fl oz", "fl oz")
                    )
                    val volumeRightColumn = listOf(
                        Pair("mL", "mL"),
                        Pair("L", "L"),
                        Pair("pt", "pt"),
                        Pair("qt", "qt"),
                        Pair("gal", "gal")
                    )
                    
                    val maxVolumeRows = maxOf(volumeLeftColumn.size, volumeRightColumn.size)
                    for (i in 0 until maxVolumeRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Left column item
                            if (i < volumeLeftColumn.size) {
                                val (value, label) = volumeLeftColumn[i]
                                UnitButton(
                                    label = label,
                                    isSelected = currentUnit == value,
                                    onClick = { onUnitSelected(value) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            
                            // Right column item
                            if (i < volumeRightColumn.size) {
                                val (value, label) = volumeRightColumn[i]
                                UnitButton(
                                    label = label,
                                    isSelected = currentUnit == value,
                                    onClick = { onUnitSelected(value) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                
                // Weight section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Weight",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    // Weight units - two columns
                    val weightLeftColumn = listOf(
                        Pair("g", "g"),
                        Pair("oz", "oz")
                    )
                    val weightRightColumn = listOf(
                        Pair("kg", "kg")
                    )
                    
                    val maxWeightRows = maxOf(weightLeftColumn.size, weightRightColumn.size)
                    for (i in 0 until maxWeightRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Left column item
                            if (i < weightLeftColumn.size) {
                                val (value, label) = weightLeftColumn[i]
                                UnitButton(
                                    label = label,
                                    isSelected = currentUnit == value,
                                    onClick = { onUnitSelected(value) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            
                            // Right column item
                            if (i < weightRightColumn.size) {
                                val (value, label) = weightRightColumn[i]
                                UnitButton(
                                    label = label,
                                    isSelected = currentUnit == value,
                                    onClick = { onUnitSelected(value) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun UnitButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ── Scaled Recipe Card ────────────────────────────────────────────────────────

@Composable
private fun ScaledRecipeCard(
    desiredServingsText: String,
    scaledIngredients: List<com.toolstack.io.domain.model.ScaledIngredient>,
    multiplierText: String,
    showOriginalValues: Boolean,
    simplifyFractions: Boolean,
    onDesiredServingsChanged: (String) -> Unit,
    onCopyRecipe: () -> Unit,
    onToggleOriginalValues: () -> Unit,
    onToggleSimplifyFractions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with step number and scale factor badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step number badge
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.recipe_scaler_scaled_recipe),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scale factor badge (highlighted)
                if (desiredServingsText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scale To:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = desiredServingsText,
                                onValueChange = onDesiredServingsChanged,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(64.dp),
                                textStyle = MaterialTheme.typography.labelMedium.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                                    focusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                    focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                } else {
                    // When no servings entered yet, show input field normally
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_scaler_scale_to_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = desiredServingsText,
                            onValueChange = onDesiredServingsChanged,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(72.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }

            // Toggle chips for show original and simplify fractions
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
                                text = if (showOriginalValues) 
                                    stringResource(R.string.recipe_scaler_hide_original)
                                else
                                    stringResource(R.string.recipe_scaler_show_original)
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
            }

            // Scaled ingredients list with dual-column layout
            if (scaledIngredients.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    scaledIngredients.forEach { ingredient ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Ingredient name (left)
                            Text(
                                text = ingredient.name.ifBlank { 
                                    stringResource(R.string.recipe_scaler_unnamed_ingredient) 
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            
                            // Quantity column (right-aligned)
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = ingredient.displayText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.End
                                )
                                
                                // Show original value if toggled
                                if (showOriginalValues && ingredient.originalQty > 0) {
                                    val originalUnit = RecipeScalerCalculator.ALL_UNITS
                                        .find { it.first == ingredient.unit }?.second ?: ""
                                    val originalDisplay = String.format(
                                        java.util.Locale.US,
                                        "%.2f",
                                        ingredient.originalQty
                                    ).trimEnd('0').trimEnd('.')
                                    
                                    val displayText = if (originalUnit.isNotBlank()) {
                                        "$originalDisplay $originalUnit"
                                    } else {
                                        originalDisplay
                                    }
                                    
                                    Text(
                                        text = "was: $displayText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                        
                        // Subtle divider between ingredients
                        if (ingredient != scaledIngredients.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Copy button
                Button(
                    onClick = onCopyRecipe,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recipe_scaler_copy_scaled_recipe))
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.recipe_scaler_empty_scaled_recipe),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Copy Format Dialog ────────────────────────────────────────────────────────

@Composable
private fun CopyFormatDialog(
    scaledIngredients: List<com.toolstack.io.domain.model.ScaledIngredient>,
    servings: String,
    simplifyFractions: Boolean,
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.recipe_scaler_format_plain))
                }
                
                OutlinedButton(
                    onClick = { onFormatSelected(CopyFormat.MARKDOWN) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.recipe_scaler_format_markdown))
                }
                
                OutlinedButton(
                    onClick = { onFormatSelected(CopyFormat.SHOPPING_LIST) },
                    modifier = Modifier.fillMaxWidth()
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

// ── Shopping List Dialog ──────────────────────────────────────────────────────

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

// ── Save Recipe Dialog ────────────────────────────────────────────────────────

@Composable
private fun SaveRecipeDialog(
    recipeNameInput: String,
    onRecipeNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipe_scaler_save_recipe_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.recipe_scaler_save_recipe_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = recipeNameInput,
                    onValueChange = onRecipeNameChanged,
                    label = { Text(text = stringResource(R.string.recipe_scaler_recipe_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = recipeNameInput.trim().isNotBlank()
            ) {
                Text(text = stringResource(R.string.recipe_scaler_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

// ── Load Recipe Dialog ────────────────────────────────────────────────────────

@Composable
private fun LoadRecipeDialog(
    savedRecipes: List<com.toolstack.io.domain.model.SavedRecipe>,
    onLoadRecipe: (com.toolstack.io.domain.model.SavedRecipe) -> Unit,
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
                    itemsIndexed(savedRecipes) { _, recipe ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onLoadRecipe(recipe)
                            }
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

                                IconButton(
                                    onClick = { recipeToDelete = recipe.name }
                                ) {
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

// ── Utility Functions ─────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scaled Recipe", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.recipe_scaler_copied_toast), Toast.LENGTH_SHORT).show()
}
