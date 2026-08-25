package com.industrialutility.ui.saemetric

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.industrialutility.domain.calculator.SaeMetricCalculator
import com.industrialutility.domain.model.SaeMetricEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class SaeMetricViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SaeMetricUiState())
    val uiState: StateFlow<SaeMetricUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null

    init {
        loadEntries(_uiState.value.maxInches)
    }

    fun onRangeSelected(maxInches: Int) {
        if (maxInches == _uiState.value.maxInches) return
        loadEntries(maxInches)
    }

    private fun loadEntries(maxInches: Int) {
        generateJob?.cancel()
        generateJob = viewModelScope.launch(Dispatchers.Default) {
            val entries = SaeMetricCalculator.generate(maxInches)
            if (isActive) {
                _uiState.update { current ->
                    current.copy(maxInches = maxInches, entries = entries)
                }
            }
        }
    }
}

data class SaeMetricUiState(
    val maxInches: Int = 1,
    val entries: List<SaeMetricEntry> = emptyList()
)
