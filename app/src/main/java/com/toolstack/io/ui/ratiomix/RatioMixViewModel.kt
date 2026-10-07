package com.toolstack.io.ui.ratiomix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.RatioMixPreset
import com.toolstack.io.data.repository.RatioMixPresetPart
import com.toolstack.io.data.repository.RatioMixStateSnapshot
import com.toolstack.io.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@OptIn(FlowPreview::class)
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

        // Restore the last-used mix once, then keep persisting changes.
        // Persistence only starts after the stored snapshot is read, so the
        // default state can't clobber it on startup.
        viewModelScope.launch {
            preferencesRepository.lastRatioMixState.first()?.let { restoreSnapshot(it) }
            _uiState
                .debounce(PERSIST_DEBOUNCE_MS)
                .collect { preferencesRepository.saveRatioMixState(it.toSnapshot()) }
        }
    }

    private fun restoreSnapshot(snapshot: RatioMixStateSnapshot) {
        val restoredParts = toRatioParts(snapshot.parts)
        _uiState.update { state ->
            state.copy(
                parts = restoredParts,
                totalVolumeText = snapshot.volumeText,
                selectedUnit = VolumeUnit.entries.firstOrNull { it.name == snapshot.unit }
                    ?: state.selectedUnit,
                mode = Mode.entries.firstOrNull { it.name == snapshot.mode } ?: state.mode,
                knownPartIndex = snapshot.knownPartIndex.coerceIn(restoredParts.indices)
            ).recalculate()
        }
    }

    private fun RatioMixUiState.toSnapshot() = RatioMixStateSnapshot(
        mode = mode.name,
        unit = selectedUnit.name,
        knownPartIndex = knownPartIndex,
        volumeText = totalVolumeText,
        parts = parts.map { RatioMixPresetPart(it.label, it.ratioText) }
    )

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
            // Keep knownPartIndex pointing at the same logical part after removal:
            //   - removed part was before known → shift index down by 1
            //   - removed part WAS the known → clamp to last valid index
            //   - removed part was after known → no change needed
            val newKnown = when {
                index < state.knownPartIndex  -> state.knownPartIndex - 1
                index == state.knownPartIndex -> (state.knownPartIndex - 1).coerceAtLeast(0)
                else                          -> state.knownPartIndex
            }
            state.copy(parts = parts, knownPartIndex = newKnown).recalculate()
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
        _uiState.update { it.copy(knownPartIndex = index.coerceIn(it.parts.indices)).recalculate() }
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
        val newParts = toRatioParts(preset.parts)
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

    /** Re-saves a deleted preset — backs the snackbar's Undo action. */
    fun onRestorePreset(preset: RatioMixPreset) {
        viewModelScope.launch {
            preferencesRepository.saveRatioMixPreset(preset)
        }
    }

    /**
     * Converts stored preset/snapshot parts into UI parts: truncates to
     * [MAX_PARTS] and pads up to [MIN_PARTS] with defaults.
     */
    private fun toRatioParts(stored: List<RatioMixPresetPart>): List<RatioPart> =
        stored.take(MAX_PARTS)
            .map { RatioPart(label = it.label, ratioText = it.ratioText) }
            .let { parts ->
                if (parts.size >= MIN_PARTS) parts
                else parts + List(MIN_PARTS - parts.size) { i ->
                    RatioPart(label = defaultLabel(parts.size + i), ratioText = "1")
                }
            }

    companion object {
        const val MAX_PARTS = 6
        const val MIN_PARTS = 2
        private const val PERSIST_DEBOUNCE_MS = 400L

        private val defaultLabels = listOf("Water", "Fertilizer", "Part C", "Part D", "Part E", "Part F")
        fun defaultLabel(index: Int) = defaultLabels.getOrElse(index) { "Part ${index + 1}" }
    }
}

// ── Domain types ──────────────────────────────────────────────────────────────

enum class VolumeUnit(val label: String, val symbol: String, val toLitres: Double) {
    MILLILITRES("Millilitres",    "mL",      0.001),
    LITRES("Litres",              "L",       1.0),
    CUBIC_METRES("Cubic metres",  "m³",      1000.0),
    US_TSP("US Teaspoons",        "tsp",     0.00492892159375),
    US_TBSP("US Tablespoons",     "tbsp",    0.01478676478125),
    US_FL_OZ("US Fluid Ounces",   "fl oz",   0.0295735295625),
    US_CUPS("US Cups",            "cup",     0.2365882365),
    US_PINTS("US Pints",          "pt",      0.473176473),
    US_QUARTS("US Quarts",        "qt",      0.946352946),
    US_GALLONS("US Gallons",      "gal",     3.785411784),
    IMP_GALLONS("Imp Gallons",    "imp gal", 4.54609);

    /**
     * Next-smaller unit used when a result is too small to read in this unit.
     * Follows the unit's own family (metric / US customary); imperial gallons
     * fall back to litres since no smaller imperial unit is offered.
     */
    val smaller: VolumeUnit?
        get() = when (this) {
            CUBIC_METRES -> LITRES
            LITRES -> MILLILITRES
            US_GALLONS -> US_QUARTS
            US_QUARTS -> US_PINTS
            US_PINTS -> US_CUPS
            US_CUPS -> US_FL_OZ
            US_FL_OZ -> US_TBSP
            US_TBSP -> US_TSP
            IMP_GALLONS -> LITRES
            else -> null
        }
}

enum class Mode { TOTAL_TO_PARTS, PART_TO_TOTAL }

data class RatioPart(
    val label: String,
    val ratioText: String,
    /** True when ratioText is not a positive finite number — drives field error UI. */
    val ratioError: Boolean = false
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
    val hasError: Boolean = false,
    /** True when the volume field holds non-blank text that isn't a positive number. */
    val volumeError: Boolean = false
) {
    fun recalculate(): RatioMixUiState {
        val ratios = parts.map { parseDecimal(it.ratioText) }
        val ratioOk = ratios.map { it != null && it.isFinite() && it > 0.0 }
        // Flag each invalid ratio field so the UI can highlight the offender.
        val markedParts = parts.mapIndexed { i, part -> part.copy(ratioError = !ratioOk[i]) }

        val volume = parseDecimal(totalVolumeText)
        // Blank volume is just "no input yet"; non-blank-but-invalid is an error.
        val volumeError = totalVolumeText.isNotBlank() &&
            (volume == null || !volume.isFinite() || volume <= 0.0)

        val withFlags = copy(parts = markedParts, volumeError = volumeError)

        if (ratioOk.any { !it }) {
            return withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = "", hasError = true)
        }

        val ratioValues = ratios.map { it!! }
        val ratioSum = ratioValues.sum()
        if (!ratioSum.isFinite() || ratioSum <= 0.0) {
            return withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = "", hasError = true)
        }

        // Echo the user's own (normalised) text so tiny ratios like "0.001" aren't
        // rounded to "0" by display formatting.
        val summary = markedParts.joinToString(" : ") { it.ratioText.trim().replace(',', '.') }

        return when (mode) {
            Mode.TOTAL_TO_PARTS -> {
                if (volume == null || !volume.isFinite() || volume <= 0.0) {
                    withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = false)
                } else {
                    val totalInLitres = volume * selectedUnit.toLitres
                    val results = markedParts.mapIndexed { i, part ->
                        val vol = totalInLitres * (ratioValues[i] / ratioSum)
                        if (!vol.isFinite()) return withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = true)
                        RatioResult(
                            label = part.label,
                            volumeText = formatVolumeAuto(vol, selectedUnit),
                            percentText = formatPercent(ratioValues[i] / ratioSum)
                        )
                    }
                    withFlags.copy(results = results, totalResultText = "", ratioSummary = summary, hasError = false)
                }
            }
            Mode.PART_TO_TOTAL -> {
                val idx = knownPartIndex.coerceIn(markedParts.indices)
                if (volume == null || !volume.isFinite() || volume <= 0.0) {
                    withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = false)
                } else {
                    val knownInLitres = volume * selectedUnit.toLitres
                    val litresPerRatioPart = knownInLitres / ratioValues[idx]
                    val totalLitres = litresPerRatioPart * ratioSum
                    if (!litresPerRatioPart.isFinite() || !totalLitres.isFinite()) {
                        return withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = true)
                    }
                    val results = markedParts.mapIndexed { i, part ->
                        val vol = litresPerRatioPart * ratioValues[i]
                        if (!vol.isFinite()) return withFlags.copy(results = emptyList(), totalResultText = "", ratioSummary = summary, hasError = true)
                        RatioResult(
                            label = part.label,
                            volumeText = formatVolumeAuto(vol, selectedUnit),
                            percentText = formatPercent(ratioValues[i] / ratioSum)
                        )
                    }
                    withFlags.copy(
                        results = results,
                        totalResultText = formatVolumeAuto(totalLitres, selectedUnit),
                        ratioSummary = summary,
                        hasError = false
                    )
                }
            }
        }
    }

    private fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    /**
     * Format a litres amount for display, downshifting to a smaller unit
     * (see [VolumeUnit.smaller]) while the value is below 1 — so a
     * "0.01 gal" result shows as e.g. "1.28 fl oz" instead.
     */
    private fun formatVolumeAuto(litres: Double, preferred: VolumeUnit): String {
        var unit = preferred
        while (litres / unit.toLitres < 1.0 && unit.smaller != null) {
            unit = unit.smaller!!
        }
        return "${formatVolume(litres / unit.toLitres)} ${unit.symbol}"
    }

    private fun formatVolume(v: Double): String {
        if (v <= 0.0) return "0"
        return if (v >= 100.0) {
            String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
        } else {
            val s = String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
            // Results below the unit's smallest step would format as "0"; show a floor.
            if (s == "0") "<0.001" else s
        }
    }

    private fun formatPercent(fraction: Double): String {
        val pct = fraction * 100
        return when {
            pct <= 0.0 -> "0%"
            pct < 0.05 -> "<0.1%"
            else -> String.format(Locale.US, "%.1f", pct)
                .trimEnd('0').trimEnd('.') + "%"
        }
    }
}

data class RatioResult(
    val label: String,
    val volumeText: String,
    /** Share of the mix this part represents, e.g. "75%", "33.3%", "<0.1%". */
    val percentText: String = ""
)
