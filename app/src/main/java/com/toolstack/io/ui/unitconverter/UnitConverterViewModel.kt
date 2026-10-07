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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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

    private val categoryId: String? = savedStateHandle.get<String>(ARG_CATEGORY_ID)

    val category: UnitCategory =
        categoryId?.let { UnitConverterData.findById(it) }
            ?: UnitConverterData.categories.first()

    /** Secondary constructor for direct instantiation in tests. */
    constructor(
        category: UnitCategory,
        preferencesRepository: UserPreferencesRepository
    ) : this(
        savedStateHandle = SavedStateHandle(mapOf(ARG_CATEGORY_ID to category.id)),
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
            preferencesRepository.saveLastConverterCategory(category.id)
        }

        // Restore persisted units for this category if user hasn't already made a selection
        viewModelScope.launch {
            val savedMap = preferencesRepository.converterUnits.first()
            // Stored keys may be ids, current names, or legacy names — prefer the
            // newest form (id), then name, then any legacy-normalized key.
            val savedUnits = savedMap[category.id]
                ?: savedMap[category.name]
                ?: savedMap.entries
                    .firstOrNull { UnitConverterData.idFor(it.key) == category.id }
                    ?.value
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
                categoryName = category.id,
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
                categoryName = category.id,
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
        const val ARG_CATEGORY_ID = "categoryId"

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
                val input = parseLocalizedNumber(raw) ?: return copy(toText = "—")
                if (!input.withinBounds(fromUnit)) return copy(toText = "—")
                val result = toUnit.fromBase(fromUnit.toBase(input))
                copy(toText = if (result.withinBounds(toUnit)) formatResult(result) else "—")
            }
            ActiveField.TO -> {
                val raw = toText.trim()
                if (raw.isEmpty()) return copy(fromText = "")
                val input = parseLocalizedNumber(raw) ?: return copy(fromText = "—")
                if (!input.withinBounds(toUnit)) return copy(fromText = "—")
                // Reverse: treat "to" as the input unit, derive "from" value
                val result = fromUnit.fromBase(toUnit.toBase(input))
                copy(fromText = if (result.withinBounds(fromUnit)) formatResult(result) else "—")
            }
        }
    }

    /**
     * True if [this] respects [unit]'s physical lower bound (e.g. absolute zero,
     * or 0 for fuel-economy reciprocals). NaN fails the check; a small epsilon
     * absorbs float noise at the boundary (-459.67 °F converts to -273.15 °C
     * ± ~1e-11).
     */
    private fun Double.withinBounds(unit: UnitEntry): Boolean =
        isFinite() && (unit.minValue == null || this >= unit.minValue - 1e-9)

    companion object {
        /**
         * Locale-aware strict number parser.
         *
         * Rules:
         * - Whitespace (incl. NBSP/NNBSP used as grouping in fr) is always ignored.
         * - Arabic-Indic digits are translated to ASCII.
         * - The locale's decimal separator always counts as decimal. If it is
         *   absent and the *other* common separator appears exactly once and is
         *   not the locale's grouping char, it is treated as the decimal —
         *   e.g. "1.5" parses in a comma locale where '.' isn't the grouping
         *   char. Ambiguous input ("1,5" in en-US, "1.5" in de-DE, where that
         *   char IS the grouping separator but doesn't form valid 3-digit
         *   groups) is rejected rather than silently producing 15.
         * - Grouping separators must form valid 3-digit groups ("1,000.5" ok,
         *   "12,34" rejected).
         * - Exponents pass through ("1e-3"); "NaN", "Infinity", "1d", "0x1p3"
         *   and other Java-float grammar are rejected.
         */
        internal fun parseLocalizedNumber(
            raw: String,
            locale: Locale = Locale.getDefault()
        ): Double? {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val dec = symbols.decimalSeparator
            val grp = symbols.groupingSeparator
            var s = raw.trim()
            if (s.isEmpty()) return null
            s = s.replace("\u00A0", "").replace("\u202F", "").replace(" ", "")
            s = s.map { c ->
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            }.joinToString("")
            val eIdx = s.indexOfAny(charArrayOf('e', 'E'))
            val mantissa = if (eIdx >= 0) s.substring(0, eIdx) else s
            val exponent = if (eIdx >= 0) s.substring(eIdx) else ""
            if (mantissa.count { it == dec } > 1) return null
            val alt = if (dec == '.') ',' else '.'
            val decUsed =
                if (dec !in mantissa && mantissa.count { it == alt } == 1 && alt != grp) alt
                else dec
            val parts = mantissa.split(decUsed)
            if (parts.size > 2) return null
            val intPart = parts[0]
            val fracPart = parts.getOrElse(1) { "" }
            if (grp in fracPart || dec in fracPart) return null
            val groupRe = Regex(
                "^[+-]?(\\d+|\\d{1,3}(${Regex.escape(grp.toString())}\\d{3})+)$"
            )
            if (intPart.isEmpty()) {
                if (parts.size < 2 || fracPart.isEmpty()) return null
            } else if (!groupRe.matches(intPart)) {
                return null
            }
            val normalized = buildString {
                append(intPart.replace(grp.toString(), ""))
                if (parts.size == 2) {
                    append('.')
                    append(fracPart)
                }
                append(exponent)
            }
            return normalized.toDoubleOrNull()
        }

        /**
         * Formats a conversion result in [locale]: grouping separators for
         * readability, locale decimal separator, scientific notation outside
         * [1e-4, 1e10), trailing zeros stripped.
         */
        internal fun formatResult(value: Double, locale: Locale = Locale.getDefault()): String {
            if (value.isInfinite() || value.isNaN()) return "—"
            if (value == 0.0) return "0"
            val abs = kotlin.math.abs(value)
            return when {
                abs >= 1e10 || abs < 1e-4 -> scientificFormat(value, locale)
                abs >= 1 -> decimalFormat(value, locale, 6)
                else -> decimalFormat(value, locale, 8)
            }
        }

        private fun scientificFormat(value: Double, locale: Locale): String {
            // Trim trailing zeros only from the mantissa, not the exponent.
            // e.g. "1.000000e+10" → "1e+10", not "1.e+1"
            val dec = DecimalFormatSymbols.getInstance(locale).decimalSeparator
            val raw = String.format(locale, "%.6e", value)
            val eIdx = raw.indexOf('e')
            val mantissa = raw.substring(0, eIdx).trimEnd('0').trimEnd(dec)
            return mantissa + raw.substring(eIdx)
        }

        private fun decimalFormat(value: Double, locale: Locale, maxFraction: Int): String =
            DecimalFormat(
                "#,##0." + "#".repeat(maxFraction),
                DecimalFormatSymbols.getInstance(locale)
            ).format(value)
    }
}
