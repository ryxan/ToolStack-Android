package com.toolstack.io.ui.ratiomix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.RatioMixPreset
import com.toolstack.io.data.repository.RatioMixPresetPart
import com.toolstack.io.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RatioMixViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RatioMixUiState())
    val uiState: StateFlow<RatioMixUiState> = _uiState.asStateFlow()

    init {
        // Reactively wire saved presets from DataStore into UI state.
        preferencesRepository.ratioMixPresets
            .onEach { presets -> _uiState.update { it.copy(savedPresets = presets) } }
            .launchIn(viewModelScope)
    }

    // ── part label edits ──────────────────────────────────────────────────────

    fun onPartLabelChanged(index: Int, label: String) {
        _uiState.update { state ->
            val parts = state.parts.toMutableList()
            parts[index] = parts[index].copy(label = label)
            state.copy(parts = parts).recalculate()
        }
    }

    // ── ratio edits ───────────────────────────────────────────────────────────

    fun onRatioChanged(index: Int, text: String) {
        _uiState.update { state ->
            val parts = state.parts.toMutableList()
            parts[index] = parts[index].copy(ratioText = text)
            state.copy(parts = parts).recalculate()
        }
    }

    // ── add / remove parts ────────────────────────────────────────────────────

    fun onAddPart() {
        _uiState.update { state ->
            if (state.parts.size >= MAX_PARTS) return@update state
            val newLabel = defaultLabel(state.parts.size)
            state.copy(parts = state.parts + RatioPart(label = newLabel, ratioText = "1"))
                .recalculate()
        }
    }

    fun onRemovePart(index: Int) {
        _uiState.update { state ->
            if (state.parts.size <= MIN_PARTS) return@update state
            val parts = state.parts.toMutableList().also { it.removeAt(index) }
            state.copy(parts = parts).recalculate()
        }
    }

    // ── total volume ──────────────────────────────────────────────────────────

    fun onTotalVolumeChanged(text: String) {
        _uiState.update { it.copy(totalVolumeText = text).recalculate() }
    }

    fun onUnitSelected(unit: VolumeUnit) {
        _uiState.update { it.copy(selectedUnit = unit).recalculate() }
    }

    // ── mode toggle ───────────────────────────────────────────────────────────

    /** Toggle between "given total, find each part" and "given one part, find total". */
    fun onModeToggled() {
        _uiState.update { it.copy(mode = if (it.mode == Mode.TOTAL_TO_PARTS) Mode.PART_TO_TOTAL else Mode.TOTAL_TO_PARTS).recalculate() }
    }

    fun onKnownPartIndexChanged(index: Int) {
        _uiState.update { it.copy(knownPartIndex = index).recalculate() }
    }

    // ── preset save/load/delete ───────────────────────────────────────────────

    /** Show or hide the "name your preset" dialog. */
    fun onSavePresetClicked() {
        _uiState.update { it.copy(showSaveDialog = true, saveDialogName = "") }
    }

    fun onSaveDialogNameChanged(name: String) {
        _uiState.update { it.copy(saveDialogName = name) }
    }

    fun onSaveDialogDismissed() {
        _uiState.update { it.copy(showSaveDialog = false, saveDialogName = "") }
    }

    /**
     * Persists the current parts list under the given name.
     * If a preset with that name already exists it is overwritten.
     */
    fun onSaveDialogConfirmed() {
        val state = _uiState.value
        val name = state.saveDialogName.trim()
        if (name.isBlank()) return
        _uiState.update { it.copy(showSaveDialog = false, saveDialogName = "") }
        viewModelScope.launch {
            val preset = RatioMixPreset(
                name = name,
                parts = state.parts.map { RatioMixPresetPart(it.label, it.ratioText) }
            )
            preferencesRepository.saveRatioMixPreset(preset)
        }
    }

    /**
     * Replaces the current parts list with those from the chosen preset.
     * Volume input and mode are intentionally left unchanged — the user
     * may want to reuse the same volume with a different mix.
     */
    fun onLoadPreset(preset: RatioMixPreset) {
        val newParts = preset.parts
            .take(MAX_PARTS)
            .map { RatioPart(label = it.label, ratioText = it.ratioText) }
            .let { parts ->
                // Ensure we always have at least MIN_PARTS
                if (parts.size >= MIN_PARTS) parts
                else parts + List(MIN_PARTS - parts.size) { i ->
                    RatioPart(label = defaultLabel(parts.size + i), ratioText = "1")
                }
            }
        _uiState.update { state ->
            // Preserve the selected known-part index when it falls within the new
            // parts list; otherwise clamp to the last available part.
            val clampedIndex = state.knownPartIndex.coerceAtMost(newParts.lastIndex)
            state.copy(parts = newParts, knownPartIndex = clampedIndex).recalculate()
        }
    }

    /** Permanently removes the named preset from DataStore. */
    fun onDeletePreset(presetName: String) {
        viewModelScope.launch {
            preferencesRepository.deleteRatioMixPreset(presetName)
        }
    }

    companion object {
        const val MAX_PARTS = 6
        const val MIN_PARTS = 2

        private val defaultLabels = listOf("Water", "Fertilizer", "Part C", "Part D", "Part E", "Part F")
        fun defaultLabel(index: Int) = defaultLabels.getOrElse(index) { "Part ${index + 1}" }
    }
}

// ── Domain types ──────────────────────────────────────────────────────────────

enum class VolumeUnit(val label: String, val symbol: String, val toLitres: Double) {
    LITRES("Litres",           "L",      1.0),
    MILLILITRES("Millilitres", "mL",     0.001),
    US_GALLONS("US Gallons",   "gal",    3.785411784),
    IMP_GALLONS("Imp Gallons", "Igal",   4.54609),
    US_QUARTS("US Quarts",     "qt",     0.946352946),
    CUBIC_METRES("Cubic m",    "m³",     1000.0);
}

enum class Mode { TOTAL_TO_PARTS, PART_TO_TOTAL }

data class RatioPart(
    val label: String,
    val ratioText: String,
    /** Computed result volume, null when inputs are invalid. */
    val resultVolume: Double? = null,
    val resultText: String = ""
)

// ── UiState ───────────────────────────────────────────────────────────────────

data class RatioMixUiState(
    val parts: List<RatioPart> = listOf(
        RatioPart(label = "Water",      ratioText = "3"),
        RatioPart(label = "Fertilizer", ratioText = "1")
    ),
    val totalVolumeText: String = "",
    val selectedUnit: VolumeUnit = VolumeUnit.LITRES,
    val mode: Mode = Mode.TOTAL_TO_PARTS,
    val knownPartIndex: Int = 0,
    // Saved presets (from DataStore)
    val savedPresets: List<RatioMixPreset> = emptyList(),
    // Save-dialog state
    val showSaveDialog: Boolean = false,
    val saveDialogName: String = "",
    // Computed
    val results: List<RatioResult> = emptyList(),
    val totalResultText: String = "",
    val ratioSummary: String = "",
    val hasError: Boolean = false
) {
    fun recalculate(): RatioMixUiState {
        val ratios = parts.map { it.ratioText.trim().toDoubleOrNull() }
        if (ratios.any { it == null || it <= 0.0 }) {
            return copy(results = emptyList(), totalResultText = "", ratioSummary = "", hasError = true)
        }

        val ratioValues = ratios.map { it!! }
        val ratioSum = ratioValues.sum()

        // Build simplified ratio string e.g. "3 : 1" or "2 : 1 : 0.5"
        val summary = ratioValues.joinToString(" : ") { formatRatio(it) }

        return when (mode) {
            Mode.TOTAL_TO_PARTS -> {
                val totalLitres = totalVolumeText.trim().toDoubleOrNull()
                if (totalLitres == null || totalLitres <= 0.0) {
                    copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = false)
                } else {
                    val totalInLitres = totalLitres * selectedUnit.toLitres
                    val results = parts.mapIndexed { i, part ->
                        val vol = totalInLitres * (ratioValues[i] / ratioSum)
                        val display = formatVolume(vol / selectedUnit.toLitres)
                        RatioResult(label = part.label, volumeText = "$display ${selectedUnit.symbol}")
                    }
                    copy(results = results, totalResultText = "", ratioSummary = summary, hasError = false)
                }
            }
            Mode.PART_TO_TOTAL -> {
                val knownVolume = totalVolumeText.trim().toDoubleOrNull()
                val idx = knownPartIndex.coerceIn(parts.indices)
                if (knownVolume == null || knownVolume <= 0.0) {
                    copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = false)
                } else {
                    val knownInLitres = knownVolume * selectedUnit.toLitres
                    val litresPerRatioPart = knownInLitres / ratioValues[idx]
                    val totalLitres = litresPerRatioPart * ratioSum
                    val results = parts.mapIndexed { i, part ->
                        val vol = litresPerRatioPart * ratioValues[i]
                        val display = formatVolume(vol / selectedUnit.toLitres)
                        RatioResult(label = part.label, volumeText = "$display ${selectedUnit.symbol}")
                    }
                    val totalDisplay = formatVolume(totalLitres / selectedUnit.toLitres)
                    copy(
                        results = results,
                        totalResultText = "$totalDisplay ${selectedUnit.symbol}",
                        ratioSummary = summary,
                        hasError = false
                    )
                }
            }
        }
    }

    private fun formatRatio(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun formatVolume(v: Double): String {
        if (v <= 0.0) return "0"
        return if (v >= 100.0) {
            String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
        } else {
            String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
        }
    }
}

data class RatioResult(
    val label: String,
    val volumeText: String
)
