package com.toolstack.io.ui.saemetric

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.SaeMetricCalculator
import com.toolstack.io.domain.model.SaeMetricEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaeMetricViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaeMetricUiState())
    val uiState: StateFlow<SaeMetricUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            val savedMaxInches = preferencesRepository.saeMetricMaxInches.first()
            loadEntries(savedMaxInches)
        }
    }

    fun onRangeSelected(maxInches: Int) {
        if (maxInches == _uiState.value.maxInches) return
        viewModelScope.launch {
            preferencesRepository.saveSaeMetricMaxInches(maxInches)
        }
        loadEntries(maxInches)
    }

    private fun loadEntries(maxInches: Int) {
        _uiState.update { it.copy(maxInches = maxInches) }
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
