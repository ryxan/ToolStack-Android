package com.toolstack.io.ui.sprayer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SprayerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SprayerUiState())
    val uiState: StateFlow<SprayerUiState> = _uiState.asStateFlow()

    fun onTankVolumeChanged(value: String) {
        _uiState.update { it.copy(tankVolumeText = value) }
        recalculate()
    }

    fun onSprayRateChanged(value: String) {
        _uiState.update { it.copy(sprayRateText = value) }
        recalculate()
    }

    fun onChemRateChanged(value: String) {
        _uiState.update { it.copy(chemRateText = value) }
        recalculate()
    }

    fun onAcresPerJugChanged(value: String) {
        _uiState.update { it.copy(acresPerJugText = value) }
        recalculate()
    }

    fun onModeToggled() {
        _uiState.update { current ->
            val newMode = if (current.mode == SprayerMode.RATE_PER_ACRE) {
                SprayerMode.ACRES_PER_JUG
            } else {
                SprayerMode.RATE_PER_ACRE
            }
            current.copy(mode = newMode)
        }
        recalculate()
    }

    private fun recalculate() {
        val current = _uiState.value
        val tankVolume = current.tankVolumeText.toDoubleOrNull() ?: 0.0
        val sprayRate = current.sprayRateText.toDoubleOrNull() ?: 0.0

        val totalAcres = if (sprayRate > 0) tankVolume / sprayRate else 0.0

        when (current.mode) {
            SprayerMode.RATE_PER_ACRE -> {
                val chemRate = current.chemRateText.toDoubleOrNull() ?: 0.0
                val totalLiters = totalAcres * chemRate
                val totalGallons = totalLiters * LITERS_TO_GALLONS

                _uiState.update { it.copy(
                    totalAcresText = if (totalAcres > 0) String.format(Locale.US, "%.2f", totalAcres) else "",
                    totalChemLitersText = if (totalLiters > 0) String.format(Locale.US, "%.2f", totalLiters) else "",
                    totalChemGallonsText = if (totalGallons > 0) String.format(Locale.US, "%.2f", totalGallons) else "",
                    totalJugsText = ""
                )}
            }
            SprayerMode.ACRES_PER_JUG -> {
                val acresPerJug = current.acresPerJugText.toDoubleOrNull() ?: 0.0
                val totalJugs = if (acresPerJug > 0) totalAcres / acresPerJug else 0.0

                _uiState.update { it.copy(
                    totalAcresText = if (totalAcres > 0) String.format(Locale.US, "%.2f", totalAcres) else "",
                    totalJugsText = if (totalJugs > 0) String.format(Locale.US, "%.2f", totalJugs) else "",
                    totalChemLitersText = "",
                    totalChemGallonsText = ""
                )}
            }
        }
    }

    companion object {
        private const val LITERS_TO_GALLONS = 0.264172
    }
}

data class SprayerUiState(
    val mode: SprayerMode = SprayerMode.RATE_PER_ACRE,
    val tankVolumeText: String = "",
    val sprayRateText: String = "",
    val chemRateText: String = "",
    val acresPerJugText: String = "",
    val totalAcresText: String = "",
    val totalChemLitersText: String = "",
    val totalChemGallonsText: String = "",
    val totalJugsText: String = ""
)

enum class SprayerMode {
    RATE_PER_ACRE,
    ACRES_PER_JUG
}
