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
    private var hasUserSelectedMaxInches = false

    init {
        viewModelScope.launch {
            val savedMaxInches = preferencesRepository.saeMetricMaxInches.first()
            val savedShowOnlyCommon = preferencesRepository.saeMetricShowOnlyCommon.first()
            _uiState.update { it.copy(showOnlyCommon = savedShowOnlyCommon) }
            if (!hasUserSelectedMaxInches) {
                loadEntries(savedMaxInches)
            }
        }
    }

    fun onRangeSelected(maxInches: Int) {
        if (maxInches == _uiState.value.maxInches) return
        hasUserSelectedMaxInches = true
        viewModelScope.launch {
            preferencesRepository.saveSaeMetricMaxInches(maxInches)
        }
        loadEntries(maxInches)
    }

    fun onShowOnlyCommonChanged(showOnlyCommon: Boolean) {
        if (showOnlyCommon == _uiState.value.showOnlyCommon) return
        viewModelScope.launch {
            preferencesRepository.saveSaeMetricShowOnlyCommon(showOnlyCommon)
        }
        _uiState.update { it.copy(showOnlyCommon = showOnlyCommon) }
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
    val showOnlyCommon: Boolean = false,
    val entries: List<SaeMetricEntry> = emptyList()
) {
    val displayedEntries: List<SaeMetricEntry>
        get() = if (showOnlyCommon) entries.filter { it.isCommon } else entries
}
