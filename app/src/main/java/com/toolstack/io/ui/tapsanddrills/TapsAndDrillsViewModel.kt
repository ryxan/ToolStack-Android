package com.toolstack.io.ui.tapsanddrills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.domain.calculator.TapDrillData
import com.toolstack.io.domain.model.TapDrillThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TapsAndDrillsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TapsAndDrillsUiState())
    val uiState: StateFlow<TapsAndDrillsUiState> = _uiState.asStateFlow()

    init {
        applyFilters()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onCategorySelected(standard: String?) {
        _uiState.update { it.copy(selectedStandard = standard) }
        applyFilters()
    }

    fun onSparkPlugOnlyChanged(sparkPlugOnly: Boolean) {
        _uiState.update { it.copy(sparkPlugOnly = sparkPlugOnly) }
        applyFilters()
    }

    fun onColumnToggled(columnId: String) {
        _uiState.update { current ->
            val currentColumns = current.visibleColumns
            val next = if (columnId in currentColumns) {
                if (currentColumns.size == 1) currentColumns else currentColumns - columnId
            } else {
                currentColumns + columnId
            }
            current.copy(visibleColumns = next)
        }
    }

    private fun applyFilters() {
        val state = _uiState.value

        val displayedThreads = TapDrillData.threads.filter { thread ->
            matches(thread, state)
        }

        val categoryCounts = STANDARD_CATEGORIES.associateWith { standard ->
            TapDrillData.threads.count { thread ->
                matches(thread, state.copy(selectedStandard = standard))
            }
        }

        _uiState.update {
            it.copy(
                displayedThreads = displayedThreads,
                categoryCounts = categoryCounts
            )
        }
    }

    private fun matches(thread: TapDrillThread, state: TapsAndDrillsUiState): Boolean {
        val matchesCategory = state.selectedStandard == null || thread.standard == state.selectedStandard
        val matchesSparkPlug = !state.sparkPlugOnly || thread.isSparkPlug

        val matchesSearch = if (state.searchQuery.isBlank()) {
            true
        } else {
            val query = state.searchQuery.lowercase()
            thread.designation.lowercase().contains(query) ||
                thread.tapDrill75.lowercase().contains(query)
        }

        return matchesCategory && matchesSparkPlug && matchesSearch
    }

    data class TapsAndDrillsUiState(
        val searchQuery: String = "",
        val selectedStandard: String? = null,
        val sparkPlugOnly: Boolean = false,
        val visibleColumns: Set<String> = DEFAULT_COLUMNS,
        val displayedThreads: List<TapDrillThread> = emptyList(),
        val categoryCounts: Map<String?, Int> = emptyMap()
    )

    companion object {
        val STANDARD_CATEGORIES = listOf(null, "UNC", "UNF", "UNS", "UNEF", "METRIC", "PIPE")

        val DEFAULT_COLUMNS = setOf(
            "thread",
            "pitch",
            "major",
            "tap",
            "decimal",
            "fractional"
        )
    }
}
