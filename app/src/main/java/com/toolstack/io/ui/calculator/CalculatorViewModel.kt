package com.toolstack.io.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.CalculatorEngine
import com.toolstack.io.domain.calculator.CalculatorMode
import com.toolstack.io.domain.calculator.CalculatorState
import com.toolstack.io.domain.calculator.InternalState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
class CalculatorViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // Arithmetic context — not exposed to the UI.
    private var internal = InternalState()
    
    // Track if history has been loaded to prevent saving before initial load
    private var historyLoaded = false

    init {
        // One-shot load: a permanent collector would echo our own writes back and
        // overwrite entries added before the first emission. Merging instead keeps
        // an "=" press that races the initial load.
        viewModelScope.launch {
            val stored = userPreferencesRepository.calculatorHistory.first()
            val acCommits = userPreferencesRepository.calculatorClearCommitsHistory.first()
            var merged: List<String> = emptyList()
            _uiState.update { ui ->
                merged = (ui.calculatorState.history + stored)
                    .distinct()
                    .take(CalculatorEngine.HISTORY_LIMIT)
                ui.copy(
                    calculatorState = ui.calculatorState.copy(history = merged),
                    acCommitsHistory = acCommits
                )
            }
            historyLoaded = true
            if (merged != stored) {
                userPreferencesRepository.saveCalculatorHistory(merged)
            }
        }
    }

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

    fun onClear() = applyEngine { state ->
        CalculatorEngine.onClear(
            state, internal,
            commitPending = _uiState.value.acCommitsHistory
        )
    }

    /** Toggles whether AC soft-commits the in-progress expression to history. */
    fun setAcCommitsHistory(enabled: Boolean) {
        _uiState.update { it.copy(acCommitsHistory = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.saveCalculatorClearCommitsHistory(enabled)
        }
    }

    /**
     * Soft history: records the in-progress expression if it is a complete
     * calculation the user never finished with "=". Called when the
     * calculator screen stops or leaves composition — no-op otherwise.
     */
    fun commitPendingExpression() = applyEngine { state ->
        CalculatorEngine.commitPendingExpression(state, internal)
    }

    fun onClearHistory() {
        _uiState.update {
            it.copy(calculatorState = it.calculatorState.copy(history = emptyList()))
        }
        viewModelScope.launch {
            userPreferencesRepository.clearCalculatorHistory()
        }
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

    fun onHistoryRecall(entry: String) = applyEngine { state ->
        CalculatorEngine.onHistoryRecall(state, entry, internal)
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
        val prev = _uiState.value.calculatorState
        val (newCalc, newInternal) = block(prev)
        internal = newInternal
        _uiState.update { it.copy(calculatorState = newCalc) }
        // Persist whenever history changes — covers "=" as well as soft
        // commits (AC, history recall, leaving the screen). Only persist
        // after the initial load has been merged in.
        if (historyLoaded && newCalc.history != prev.history) {
            viewModelScope.launch {
                userPreferencesRepository.saveCalculatorHistory(newCalc.history)
                    .onFailure { /* Silently ignore save failures for now */ }
            }
        }
    }
}

/**
 * UI-facing state for [CalculatorScreen].
 *
 * Wraps [CalculatorState] rather than flattening it so the Screen can reach
 * [CalculatorState.mode] directly when the mode-switching UI is added.
 */
data class CalculatorUiState(
    val calculatorState: CalculatorState = CalculatorState(),
    /** Whether pressing AC soft-commits the in-progress expression to history. */
    val acCommitsHistory: Boolean = true
)
