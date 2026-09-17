package com.toolstack.io.ui.calculator

import androidx.lifecycle.ViewModel
import com.toolstack.io.domain.calculator.CalculatorEngine
import com.toolstack.io.domain.calculator.CalculatorMode
import com.toolstack.io.domain.calculator.CalculatorState
import com.toolstack.io.domain.calculator.InternalState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Holds calculator display state and delegates all arithmetic logic to
 * [CalculatorEngine]. The ViewModel owns both the public [CalculatorUiState]
 * and the private [InternalState]; the UI only ever sees the public state.
 *
 * To add [CalculatorMode.CONSTRUCTION] later: add a "switch mode" action here
 * that calls [CalculatorEngine.onClear] and sets [CalculatorState.mode].
 * The engine already has stubs for each construction-mode handler.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // Arithmetic context — not exposed to the UI.
    private var internal = InternalState()

    // ── button handlers ───────────────────────────────────────────────────────

    fun onDigit(digit: String) = applyEngine { state ->
        CalculatorEngine.onDigit(state, digit, internal)
    }

    fun onOperator(op: String) = applyEngine { state ->
        CalculatorEngine.onOperator(state, op, internal)
    }

    fun onEquals() = applyEngine { state ->
        CalculatorEngine.onEquals(state, internal)
    }

    fun onClear() {
        val (newCalc, newInternal) = CalculatorEngine.onClear(_uiState.value.calculatorState)
        internal = newInternal
        _uiState.update { it.copy(calculatorState = newCalc) }
    }

    fun onBackspace() = applyEngine { state ->
        CalculatorEngine.onBackspace(state, internal)
    }

    fun onPercent() = applyEngine { state ->
        CalculatorEngine.onPercent(state, internal)
    }

    fun onSignFlip() = applyEngine { state ->
        CalculatorEngine.onSignFlip(state, internal)
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Runs an engine function, updates [internal], and publishes the new
     * [CalculatorState] through [_uiState]. All engine functions are pure and
     * return a (state, internal) pair so this wrapper keeps the call sites tidy.
     */
    private inline fun applyEngine(
        block: (CalculatorState) -> Pair<CalculatorState, InternalState>
    ) {
        val (newCalc, newInternal) = block(_uiState.value.calculatorState)
        internal = newInternal
        _uiState.update { it.copy(calculatorState = newCalc) }
    }
}

/**
 * UI-facing state for [CalculatorScreen].
 *
 * Wraps [CalculatorState] rather than flattening it so the Screen can reach
 * [CalculatorState.mode] directly when the mode-switching UI is added.
 */
data class CalculatorUiState(
    val calculatorState: CalculatorState = CalculatorState()
)
