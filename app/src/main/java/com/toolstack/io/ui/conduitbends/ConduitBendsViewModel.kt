package com.toolstack.io.ui.conduitbends

import androidx.lifecycle.ViewModel
import com.toolstack.io.domain.calculator.ConduitBendCalculator
import com.toolstack.io.domain.model.BendInput
import com.toolstack.io.domain.model.BendResult
import com.toolstack.io.domain.model.BendType
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

enum class BendStep { SELECTION, INPUT, RESULT }
enum class ResultTab { QUICK, DETAILED }

data class ConduitBendsUiState(
    val step: BendStep = BendStep.SELECTION,

    // Selection
    val selectedBend: BendType? = null,

    // Input
    val conduitSize: ConduitSize     = ConduitSize.HALF,
    val offsetAngle: OffsetAngle     = OffsetAngle.DEG_45,
    /** Primary measurement string from the user — stub height, obstruction height, or back distance. */
    val inputValue: String           = "",
    val inputError: String?          = null,

    // Result
    val result: BendResult?          = null,
    val resultTab: ResultTab         = ResultTab.QUICK
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ConduitBendsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ConduitBendsUiState())
    val uiState: StateFlow<ConduitBendsUiState> = _uiState.asStateFlow()

    // ── Navigation ────────────────────────────────────────────────────────────

    /** User tapped a bend card on the selection screen. */
    fun onBendSelected(bend: BendType) {
        _uiState.update {
            it.copy(
                selectedBend = bend,
                step         = BendStep.INPUT,
                inputValue   = "",
                inputError   = null,
                result       = null,
                resultTab    = ResultTab.QUICK
            )
        }
    }

    /** Back pressed on the input screen — return to bend selection. */
    fun onBackFromInput() {
        _uiState.update {
            it.copy(
                step         = BendStep.SELECTION,
                selectedBend = null,
                inputValue   = "",
                inputError   = null
            )
        }
    }

    /** Back pressed on the result screen — return to input so the user can tweak values. */
    fun onBackFromResult() {
        _uiState.update {
            it.copy(
                step   = BendStep.INPUT,
                result = null
            )
        }
    }

    // ── Input field changes ───────────────────────────────────────────────────

    fun onInputValueChanged(value: String) {
        _uiState.update { it.copy(inputValue = value, inputError = null) }
    }

    fun onConduitSizeChanged(size: ConduitSize) {
        _uiState.update { it.copy(conduitSize = size, inputError = null) }
    }

    fun onOffsetAngleChanged(angle: OffsetAngle) {
        _uiState.update { it.copy(offsetAngle = angle, inputError = null) }
    }

    fun onResultTabChanged(tab: ResultTab) {
        _uiState.update { it.copy(resultTab = tab) }
    }

    // ── Calculate ─────────────────────────────────────────────────────────────

    /**
     * Validates the user's input, builds the appropriate [BendInput], delegates
     * to [ConduitBendCalculator], and transitions to the RESULT step.
     * On validation or calculation failure the error is surfaced via [inputError]
     * and the step stays at INPUT.
     */
    fun onCalculate() {
        val state = _uiState.value
        val bend  = state.selectedBend ?: return

        val inches = parseInches(state.inputValue)
        if (inches == null) {
            _uiState.update { it.copy(inputError = "Enter a valid measurement in inches (e.g. 12 or 12.5)") }
            return
        }

        val input: BendInput = when (bend) {
            BendType.CORNER_90    -> BendInput.Corner90(
                distanceToCornerInches = inches,
                conduitSize            = state.conduitSize
            )
            BendType.STUB_UP_90   -> BendInput.StubUp90(
                stubLengthInches = inches,
                conduitSize      = state.conduitSize
            )
            BendType.OFFSET       -> BendInput.Offset(
                obstructionHeightInches = inches,
                conduitSize             = state.conduitSize,
                angle                   = state.offsetAngle
            )
            BendType.SADDLE_3_POINT -> BendInput.Saddle3Point(
                obstructionHeightInches = inches,
                conduitSize             = state.conduitSize
            )
            BendType.SADDLE_4_POINT -> BendInput.Saddle4Point(
                obstructionHeightInches = inches,
                conduitSize             = state.conduitSize
            )
            BendType.BACK_TO_BACK -> BendInput.BackToBack(
                backDistanceInches = inches,
                conduitSize        = state.conduitSize
            )
        }

        val result = try {
            ConduitBendCalculator.calculate(input)
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(inputError = e.message) }
            return
        }

        _uiState.update {
            it.copy(
                step       = BendStep.RESULT,
                result     = result,
                inputError = null
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Accepts plain decimal inch strings ("12", "12.5", "12.375").
     * Returns null for empty, non-numeric, or negative/zero values.
     */
    private fun parseInches(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }
}
