package com.toolstack.io.ui.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.toolstack.io.domain.model.UnitCategory
import com.toolstack.io.domain.model.UnitEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * ViewModel for a single converter category.
 *
 * Deliberately NOT a @HiltViewModel — Hilt scopes ViewModels to NavBackStackEntry,
 * which means navigating back and tapping a different category reuses the same
 * ViewModel instance. Passing [category] via a factory sidesteps that entirely:
 * each composable invocation gets a fresh instance scoped to the call site.
 */
class UnitConverterViewModel(category: UnitCategory) : ViewModel() {
    private val _uiState = MutableStateFlow(
        UnitConverterUiState(
            category   = category,
            fromUnit   = category.units.first(),
            toUnit     = category.units.getOrElse(1) { category.units.first() },
            inputText  = "",
            resultText = ""
        )
    )
    val uiState: StateFlow<UnitConverterUiState> = _uiState.asStateFlow()

    // ── unit selection ────────────────────────────────────────────────────────

    fun onFromUnitSelected(unit: UnitEntry) {
        _uiState.update { it.copy(fromUnit = unit).recalculate() }
    }

    fun onToUnitSelected(unit: UnitEntry) {
        _uiState.update { it.copy(toUnit = unit).recalculate() }
    }

    // ── swap ──────────────────────────────────────────────────────────────────

    fun onSwap() {
        _uiState.update { state ->
            state.copy(
                fromUnit   = state.toUnit,
                toUnit     = state.fromUnit,
                inputText  = state.resultText,
                resultText = state.inputText
            )
        }
    }

    // ── input ─────────────────────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text).recalculate() }
    }

    // ── factory ───────────────────────────────────────────────────────────────

    companion object {
        /** Nav argument key — used in the route definition in MainActivity. */
        const val ARG_CATEGORY_INDEX = "categoryIndex"

        fun factory(category: UnitCategory): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UnitConverterViewModel(category) as T
            }
    }
}

// ── UiState ───────────────────────────────────────────────────────────────────

data class UnitConverterUiState(
    val category: UnitCategory,
    val fromUnit: UnitEntry,
    val toUnit: UnitEntry,
    val inputText: String,
    val resultText: String
) {
    fun recalculate(): UnitConverterUiState {
        val raw = inputText.trim()
        if (raw.isEmpty()) return copy(resultText = "")

        val input = raw.toDoubleOrNull()
            ?: return copy(resultText = "—")

        val base   = fromUnit.toBase(input)
        val output = toUnit.fromBase(base)

        val formatted = when {
            output.isInfinite() || output.isNaN() -> "—"
            else                                  -> formatResult(output)
        }
        return copy(resultText = formatted)
    }

    private fun formatResult(value: Double): String {
        if (value == 0.0) return "0"
        val abs = kotlin.math.abs(value)
        return when {
            abs >= 1e10 || abs < 1e-4 -> String.format(Locale.US, "%.6e", value)
                .trimEnd('0').trimEnd('.')
            abs >= 1 -> String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
            else     -> String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        }
    }
}
