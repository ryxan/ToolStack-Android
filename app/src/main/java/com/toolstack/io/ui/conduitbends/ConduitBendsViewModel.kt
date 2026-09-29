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

/**
 * Represents the three steps in the conduit bend calculation wizard.
 * SELECTION: user chooses a bend type
 * INPUT: user provides measurements and parameters
 * RESULT: calculated values and instructions are shown
 */
enum class BendStep { SELECTION, INPUT, RESULT }

/**
 * Tab selection for the result display screen.
 * QUICK: shows key measurements in a simple format
 * DETAILED: shows step-by-step instructions with diagrams
 */
enum class ResultTab { QUICK, DETAILED }

/**
 * UI state for the conduit bends feature.
 * Tracks the wizard navigation, user inputs, and calculation results.
 */
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

/**
 * ViewModel for the conduit bends calculator feature.
 * 
 * Manages a three-step wizard flow:
 * 1. User selects a bend type (90° corner, stub-up, offset, saddle, back-to-back)
 * 2. User provides measurements and parameters (conduit size, angle if applicable)
 * 3. Results are calculated and displayed with quick reference or detailed instructions
 * 
 * All calculations are delegated to [ConduitBendCalculator]. This ViewModel handles
 * only UI state, validation, and navigation between wizard steps.
 */
@HiltViewModel
class ConduitBendsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ConduitBendsUiState())
    val uiState: StateFlow<ConduitBendsUiState> = _uiState.asStateFlow()

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * User tapped a bend card on the selection screen.
     * Transitions to the INPUT step and resets any previous input/result state.
     */
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

    /**
     * Back pressed on the input screen — return to bend selection.
     * Clears the selected bend and any input values.
     */
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

    /**
     * Back pressed on the result screen — return to input so the user can tweak values.
     * Preserves the input values and selected bend, only clears the result.
     */
    fun onBackFromResult() {
        _uiState.update {
            it.copy(
                step   = BendStep.INPUT,
                result = null
            )
        }
    }

    // ── Input field changes ───────────────────────────────────────────────────

    /**
     * Called when the user types in the measurement input field.
     * Clears any previous validation error to allow re-submission.
     */
    fun onInputValueChanged(value: String) {
        _uiState.update { it.copy(inputValue = value, inputError = null) }
    }

    /**
     * User selected a different conduit size chip.
     * Clears any previous validation error.
     */
    fun onConduitSizeChanged(size: ConduitSize) {
        _uiState.update { it.copy(conduitSize = size, inputError = null) }
    }

    /**
     * User selected a different offset angle chip (only visible for offset bends).
     * Clears any previous validation error.
     */
    fun onOffsetAngleChanged(angle: OffsetAngle) {
        _uiState.update { it.copy(offsetAngle = angle, inputError = null) }
    }

    /**
     * User switched between Quick and Detailed tabs on the result screen.
     */
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
