package com.toolstack.io.ui.recipescaler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
            // ── Servings input ────────────────────────────────────────────────
            item {
                ServingsInputSection(
                    originalServingsText = uiState.originalServingsText,
                    desiredServingsText = uiState.desiredServingsText,
                    multiplierText = uiState.multiplierText,
                    onOriginalServingsChanged = viewModel::onOriginalServingsChanged,
                    onDesiredServingsChanged = viewModel::onDesiredServingsChanged
                )
            }

            // ── Ingredients section ───────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.recipe_scaler_ingredients_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(uiState.ingredients, key = { _, ingredient -> ingredient.id }) { index, ingredient ->
                IngredientRow(
                    ingredient = ingredient,
                    canRemove = uiState.ingredients.size > 1,
                    onQuantityChanged = { viewModel.onIngredientQuantityChanged(ingredient.id, it) },
                    onUnitChanged = { viewModel.onIngredientUnitChanged(ingredient.id, it) },
                    onStateChanged = { viewModel.onIngredientStateChanged(ingredient.id, it) },
                    onNameChanged = { viewModel.onIngredientNameChanged(ingredient.id, it) },
                    onRemove = { viewModel.removeIngredient(ingredient.id) }
                )
            }

            // ── Add ingredient button ─────────────────────────────────────────
            item {
                if (uiState.ingredients.size < RecipeScalerViewModel.MAX_INGREDIENTS) {
                    Button(
                        onClick = viewModel::addIngredient,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.recipe_scaler_add_ingredient))
                    }
                }
            }

            // ── Scaled results ────────────────────────────────────────────────
            if (uiState.scaledIngredients.isNotEmpty()) {
                item {
                    ScaledResultsCard(
                        scaledIngredients = uiState.scaledIngredients,
                        onShowShoppingList = viewModel::onShowShoppingListDialog
                    )
                }
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

// ── Servings Input Section ────────────────────────────────────────────────────

@Composable
private fun ServingsInputSection(
    originalServingsText: String,
    desiredServingsText: String,
    multiplierText: String,
    onOriginalServingsChanged: (String) -> Unit,
    onDesiredServingsChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.recipe_scaler_servings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = originalServingsText,
                    onValueChange = onOriginalServingsChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.recipe_scaler_original_servings)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "→",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = desiredServingsText,
                    onValueChange = onDesiredServingsChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.recipe_scaler_desired_servings)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            if (multiplierText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.recipe_scaler_multiplier, multiplierText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Ingredient Row ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientRow(
    ingredient: com.toolstack.io.domain.model.IngredientItem,
    canRemove: Boolean,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onStateChanged: (IngredientState) -> Unit,
    onNameChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    var unitExpanded by remember { mutableStateOf(false) }

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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First row: Quantity, Unit, State toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ingredient.qtyString,
                    onValueChange = onQuantityChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.recipe_scaler_quantity)) },
                    placeholder = { Text(text = stringResource(R.string.recipe_scaler_quantity_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )

                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = RecipeScalerCalculator.ALL_UNITS.find { it.first == ingredient.unit }?.second ?: "-",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.recipe_scaler_unit)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                        },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        RecipeScalerCalculator.ALL_UNITS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                onClick = {
                                    onUnitChanged(value)
                                    unitExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }

            // Second row: State toggle chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ingredient.state == IngredientState.DRY,
                    onClick = { onStateChanged(IngredientState.DRY) },
                    label = { Text(text = IngredientState.DRY.displayName) }
                )
                FilterChip(
                    selected = ingredient.state == IngredientState.LIQUID,
                    onClick = { onStateChanged(IngredientState.LIQUID) },
                    label = { Text(text = IngredientState.LIQUID.displayName) }
                )
            }

            // Third row: Name and delete button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = onNameChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.recipe_scaler_ingredient_name)) },
                    placeholder = { Text(text = stringResource(R.string.recipe_scaler_ingredient_name_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )

                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.recipe_scaler_remove_ingredient),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ── Scaled Results Card ───────────────────────────────────────────────────────

@Composable
private fun ScaledResultsCard(
    scaledIngredients: List<com.toolstack.io.domain.model.ScaledIngredient>,
    onShowShoppingList: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.recipe_scaler_results_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            scaledIngredients.forEach { ingredient ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ingredient.name.ifBlank { stringResource(R.string.recipe_scaler_unnamed_ingredient) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ingredient.displayText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedButton(
                onClick = onShowShoppingList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.recipe_scaler_copy_shopping_list))
            }
        }
    }
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

// ── Clipboard Helper ──────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Shopping List", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.recipe_scaler_copied_toast), Toast.LENGTH_SHORT).show()
}
