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
            category      = category,
            fromUnit      = category.units.first(),
            toUnit        = category.units.getOrElse(1) { category.units.first() },
            fromText      = "",
            toText        = "",
            activeField   = ActiveField.FROM
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

    // ── input ─────────────────────────────────────────────────────────────────

    /** User typed in the "from" field — derive the "to" value. */
    fun onFromTextChanged(text: String) {
        _uiState.update { it.copy(fromText = text, activeField = ActiveField.FROM).recalculate() }
    }

    /** User typed in the "to" field — derive the "from" value. */
    fun onToTextChanged(text: String) {
        _uiState.update { it.copy(toText = text, activeField = ActiveField.TO).recalculate() }
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

// ── Domain types ──────────────────────────────────────────────────────────────

enum class ActiveField { FROM, TO }

// ── UiState ───────────────────────────────────────────────────────────────────

data class UnitConverterUiState(
    val category: UnitCategory,
    val fromUnit: UnitEntry,
    val toUnit: UnitEntry,
    /** Raw text in the top (from) field. */
    val fromText: String,
    /** Raw text in the bottom (to) field. */
    val toText: String,
    /** Which field the user last typed into — drives calculation direction. */
    val activeField: ActiveField
) {
    fun recalculate(): UnitConverterUiState {
        return when (activeField) {
            ActiveField.FROM -> {
                val raw = fromText.trim()
                if (raw.isEmpty()) return copy(toText = "")
                val input = raw.toDoubleOrNull() ?: return copy(toText = "—")
                val result = toUnit.fromBase(fromUnit.toBase(input))
                copy(toText = formatResult(result))
            }
            ActiveField.TO -> {
                val raw = toText.trim()
                if (raw.isEmpty()) return copy(fromText = "")
                val input = raw.toDoubleOrNull() ?: return copy(fromText = "—")
                // Reverse: treat "to" as the input unit, derive "from" value
                val result = fromUnit.fromBase(toUnit.toBase(input))
                copy(fromText = formatResult(result))
            }
        }
    }

    private fun formatResult(value: Double): String {
        if (value.isInfinite() || value.isNaN()) return "—"
        // Fuel-economy units use Double.MAX_VALUE as a sentinel for zero-input
        // (reciprocal of zero). Treat it as invalid rather than displaying a
        // huge finite number.
        if (value == Double.MAX_VALUE || value == -Double.MAX_VALUE) return "—"
        if (value == 0.0) return "0"
        val abs = kotlin.math.abs(value)
        return when {
            abs >= 1e10 || abs < 1e-4 -> {
                // Trim trailing zeros only from the mantissa, not the exponent.
                // e.g. "1.000000e+10" → "1e+10", not "1.e+1"
                val raw = String.format(Locale.US, "%.6e", value)
                val eIdx = raw.indexOf('e')
                val mantissa = raw.substring(0, eIdx).trimEnd('0').trimEnd('.')
                val exponent = raw.substring(eIdx)
                "$mantissa$exponent"
            }
            abs >= 1 -> String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
            else     -> String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        }
    }
}
