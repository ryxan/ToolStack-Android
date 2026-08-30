package com.toolstack.io.ui.bearings

import androidx.lifecycle.ViewModel
import com.toolstack.io.data.repository.BearingRepository
import com.toolstack.io.domain.model.Bearing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BearingsViewModel @Inject constructor(
    private val bearingRepository: BearingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BearingsUiState())
    val uiState: StateFlow<BearingsUiState> = _uiState.asStateFlow()

    fun onBoreChanged(text: String) {
        _uiState.update { it.copy(boreText = text, hasSearched = false, results = emptyList()) }
    }

    fun onOdChanged(text: String) {
        _uiState.update { it.copy(odText = text, hasSearched = false, results = emptyList()) }
    }

    fun onWidthChanged(text: String) {
        _uiState.update { it.copy(widthText = text, hasSearched = false, results = emptyList()) }
    }

    fun onToleranceSelected(toleranceMm: Double) {
        _uiState.update { it.copy(toleranceMm = toleranceMm) }
        if (_uiState.value.hasSearched) {
            search()
        }
    }

    fun search() {
        val state = _uiState.value
        val bore = state.boreText.toDoubleOrNull()
        val od = state.odText.toDoubleOrNull()
        val width = state.widthText.toDoubleOrNull()

        if (bore == null || od == null || width == null) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    hasSearched = true,
                    isInputValid = false
                )
            }
            return
        }

        val results = bearingRepository.findByDimensions(
            boreMm = bore,
            odMm = od,
            widthMm = width,
            toleranceMm = state.toleranceMm
        )

        _uiState.update {
            it.copy(
                results = results,
                hasSearched = true,
                isInputValid = true
            )
        }
    }

    data class BearingsUiState(
        val boreText: String = "",
        val odText: String = "",
        val widthText: String = "",
        val toleranceMm: Double = 0.0,
        val results: List<Bearing> = emptyList(),
        val hasSearched: Boolean = false,
        val isInputValid: Boolean = false
    )

    companion object {
        val TOLERANCES = listOf(0.0, 0.1, 0.5, 1.0)
    }
}
