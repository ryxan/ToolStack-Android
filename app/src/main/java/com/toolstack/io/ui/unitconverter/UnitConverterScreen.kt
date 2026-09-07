package com.toolstack.io.ui.unitconverter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toolstack.io.R
import com.toolstack.io.domain.model.UnitCategory
import com.toolstack.io.ui.components.dragContainer
import com.toolstack.io.ui.components.draggedItem
import com.toolstack.io.ui.components.rememberDragDropState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    onBack: () -> Unit,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnitConverterListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.moveCategory(from, to)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.unit_converter),
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
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .dragContainer(dragDropState)
        ) {
            itemsIndexed(uiState.categories, key = { _, category -> category.name }) { index, category ->
                CategoryCard(
                    category = category,
                    isDragging = dragDropState.draggingItemIndex == index,
                    modifier = Modifier.draggedItem(dragDropState, index),
                    // Navigate by stable original index, not display-order index.
                    onClick = { onCategorySelected(viewModel.originalIndexOf(category)) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: UnitCategory,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 6.dp else 2.dp
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            trailingContent = {
                if (isDragging) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.content_description_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
