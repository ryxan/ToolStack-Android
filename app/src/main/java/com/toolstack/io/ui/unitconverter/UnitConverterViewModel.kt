package com.toolstack.io.ui.unitconverter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.UnitConverterData
import com.toolstack.io.domain.model.UnitCategory
import com.toolstack.io.domain.model.UnitEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for a single converter category.
 *
 * Scoped to [NavBackStackEntry] via Hilt or instantiated via [factory] for testing.
 * Restores and persists the user's selected From and To units per category.
 */
@HiltViewModel
class UnitConverterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val categoryIndex: Int =
        savedStateHandle.get<String>(ARG_CATEGORY_INDEX)?.toIntOrNull()
            ?: savedStateHandle.get<Int>(ARG_CATEGORY_INDEX)
            ?: 0

    val category: UnitCategory =
        UnitConverterData.categories.getOrElse(categoryIndex) {
            UnitConverterData.categories.first()
        }

    /** Secondary constructor for direct instantiation in tests. */
    constructor(
        category: UnitCategory,
        preferencesRepository: UserPreferencesRepository
    ) : this(
        savedStateHandle = SavedStateHandle(
            mapOf(ARG_CATEGORY_INDEX to UnitConverterData.categories.indexOf(category).toString())
        ),
        preferencesRepository = preferencesRepository
    )

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

    @Volatile private var fromUnitSelected = false
    @Volatile private var toUnitSelected = false

    init {
        // Record this category as the last-used category
        viewModelScope.launch {
            preferencesRepository.saveLastConverterCategory(category.name)
        }

        // Restore persisted units for this category if user hasn't already made a selection
        viewModelScope.launch {
            val savedMap = preferencesRepository.converterUnits.first()
            val savedUnits = savedMap[category.name]
            if (savedUnits != null) {
                val (fromLabel, toLabel) = savedUnits
                val from = if (!fromUnitSelected) {
                    category.units.find { it.label == fromLabel } ?: category.units.first()
                } else null
                val to = if (!toUnitSelected) {
                    category.units.find { it.label == toLabel }
                        ?: category.units.getOrElse(1) { category.units.first() }
                } else null
                if (from != null || to != null) {
                    _uiState.update { current ->
                        current.copy(
                            fromUnit = from ?: current.fromUnit,
                            toUnit = to ?: current.toUnit
                        ).recalculate()
                    }
                }
            }
        }
    }

    // ── unit selection ────────────────────────────────────────────────────────

    fun onFromUnitSelected(unit: UnitEntry) {
        fromUnitSelected = true
        _uiState.update { it.copy(fromUnit = unit).recalculate() }
        viewModelScope.launch {
            preferencesRepository.saveConverterUnits(
                categoryName = category.name,
                fromUnit = unit.label,
                toUnit = _uiState.value.toUnit.label
            )
        }
    }

    fun onToUnitSelected(unit: UnitEntry) {
        toUnitSelected = true
        _uiState.update { it.copy(toUnit = unit).recalculate() }
        viewModelScope.launch {
            preferencesRepository.saveConverterUnits(
                categoryName = category.name,
                fromUnit = _uiState.value.fromUnit.label,
                toUnit = unit.label
            )
        }
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

    /** Deletes the last character from the active field. */
    fun onBackspace() {
        _uiState.update { state ->
            when (state.activeField) {
                ActiveField.FROM -> {
                    val trimmed = state.fromText.dropLast(1)
                    state.copy(fromText = trimmed).recalculate()
                }
                ActiveField.TO -> {
                    val trimmed = state.toText.dropLast(1)
                    state.copy(toText = trimmed).recalculate()
                }
            }
        }
    }

    // ── factory ───────────────────────────────────────────────────────────────

    companion object {
        /** Nav argument key — used in the route definition in MainActivity. */
        const val ARG_CATEGORY_INDEX = "categoryIndex"

        fun factory(
            category: UnitCategory,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UnitConverterViewModel(category, preferencesRepository) as T
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
