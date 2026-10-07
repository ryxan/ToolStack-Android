package com.toolstack.io.ui.unitconverter

import com.toolstack.io.domain.calculator.UnitConverterData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [UnitConverterUiState.recalculate] and its result formatting —
 * conversion direction, empty/invalid input, and number formatting rules.
 */
class UnitConverterUiStateTest {

    private val length = UnitConverterData.categories.first { it.name == "Length" }
    private val temperature = UnitConverterData.categories.first { it.name == "Temperature" }
    private val fuel = UnitConverterData.categories.first { it.name == "Fuel" }
    private val data = UnitConverterData.categories.first { it.name == "Data" }

    private fun state(
        category: com.toolstack.io.domain.model.UnitCategory = length,
        fromLabel: String,
        toLabel: String,
        fromText: String = "",
        toText: String = "",
        activeField: ActiveField = ActiveField.FROM
    ) = UnitConverterUiState(
        category = category,
        fromUnit = category.units.first { it.label == fromLabel },
        toUnit = category.units.first { it.label == toLabel },
        fromText = fromText,
        toText = toText,
        activeField = activeField
    ).recalculate()

    // ── forward direction ───────────────────────────────────────────────────

    @Test
    fun `typing in from field produces converted result`() {
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "1")
        assertEquals("0.3048", result.toText)
    }

    @Test
    fun `fractional input converts and trims trailing zeros`() {
        val result = state(fromLabel = "Metres", toLabel = "Feet", fromText = "1.5")
        assertEquals("4.92126", result.toText)
    }

    @Test
    fun `zero result renders as plain 0`() {
        val result = state(
            category = temperature,
            fromLabel = "Fahrenheit", toLabel = "Celsius",
            fromText = "32"
        )
        assertEquals("0", result.toText)
    }

    @Test
    fun `negative temperature offset converts correctly`() {
        val result = state(
            category = temperature,
            fromLabel = "Fahrenheit", toLabel = "Celsius",
            fromText = "-40"
        )
        assertEquals("-40", result.toText)
    }

    // ── reverse direction ───────────────────────────────────────────────────

    @Test
    fun `typing in to field computes reverse conversion`() {
        val result = state(
            category = temperature,
            fromLabel = "Fahrenheit", toLabel = "Celsius",
            toText = "100", activeField = ActiveField.TO
        )
        assertEquals("212", result.fromText)
    }

    @Test
    fun `invalid input in to field shows dash in from field`() {
        val result = state(
            fromLabel = "Feet", toLabel = "Metres",
            toText = "abc", activeField = ActiveField.TO
        )
        assertEquals("—", result.fromText)
    }

    // ── empty / invalid input ───────────────────────────────────────────────

    @Test
    fun `empty from input clears to field`() {
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "")
        assertEquals("", result.toText)
    }

    @Test
    fun `whitespace-only input is treated as empty`() {
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "   ")
        assertEquals("", result.toText)
    }

    @Test
    fun `non-numeric input shows dash`() {
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "abc")
        assertEquals("—", result.toText)
    }

    @Test
    fun `overflow exponent shows dash`() {
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "1e309")
        assertEquals("—", result.toText)
    }

    @Test
    fun `zero input on reciprocal fuel unit shows dash`() {
        val result = state(
            category = fuel,
            fromLabel = "Miles per gallon (US)", toLabel = "Litres per 100 km",
            fromText = "0"
        )
        assertEquals("—", result.toText)
    }

    // ── physical bounds ─────────────────────────────────────────────────────

    @Test
    fun `negative fuel economy shows dash`() {
        val result = state(
            category = fuel,
            fromLabel = "Miles per gallon (US)", toLabel = "Litres per 100 km",
            fromText = "-10"
        )
        assertEquals("—", result.toText)
    }

    @Test
    fun `below absolute zero celsius shows dash`() {
        val result = state(
            category = temperature,
            fromLabel = "Celsius", toLabel = "Fahrenheit",
            fromText = "-300"
        )
        assertEquals("—", result.toText)
    }

    @Test
    fun `negative kelvin shows dash`() {
        val result = state(
            category = temperature,
            fromLabel = "Kelvin", toLabel = "Celsius",
            fromText = "-1"
        )
        assertEquals("—", result.toText)
    }

    @Test
    fun `below absolute zero fahrenheit shows dash`() {
        val result = state(
            category = temperature,
            fromLabel = "Fahrenheit", toLabel = "Celsius",
            fromText = "-460"
        )
        assertEquals("—", result.toText)
    }

    @Test
    fun `exactly absolute zero fahrenheit converts`() {
        // -459.67 °F = -273.15 °C ± float noise — boundary must not reject
        val result = state(
            category = temperature,
            fromLabel = "Fahrenheit", toLabel = "Celsius",
            fromText = "-459.67"
        )
        assertEquals("-273.15", result.toText)
    }

    @Test
    fun `below bound input in to field shows dash`() {
        val result = state(
            category = temperature,
            fromLabel = "Celsius", toLabel = "Fahrenheit",
            toText = "-500", activeField = ActiveField.TO
        )
        assertEquals("—", result.fromText)
    }

    @Test
    fun `negative values still convert for unbounded units`() {
        // e.g. depth below a reference, debt, position offsets
        val result = state(fromLabel = "Feet", toLabel = "Metres", fromText = "-1")
        assertEquals("-0.3048", result.toText)
    }

    // ── formatting rules ────────────────────────────────────────────────────

    @Test
    fun `large results switch to scientific notation`() {
        val result = state(
            category = data,
            fromLabel = "Terabytes", toLabel = "Bits",
            fromText = "2"
        )
        assertEquals("1.6e+13", result.toText)
    }

    @Test
    fun `scientific notation mantissa strips trailing zeros`() {
        val result = state(
            category = data,
            fromLabel = "Terabytes", toLabel = "Bits",
            fromText = "1.25"
        )
        assertEquals("1e+13", result.toText)
    }

    @Test
    fun `small results switch to scientific notation`() {
        val result = state(
            fromLabel = "Millimetres", toLabel = "Kilometres",
            fromText = "1"
        )
        assertEquals("1e-06", result.toText)
    }

    @Test
    fun `mid-range result uses fixed decimal with grouping`() {
        // 1 mile = 1609.344 m — exercises the abs >= 1 branch (en-US grouping)
        val result = state(fromLabel = "Miles", toLabel = "Metres", fromText = "1")
        assertEquals("1,609.344", result.toText)
    }

    // ── localized parsing ───────────────────────────────────────────────────

    private fun parse(raw: String, locale: java.util.Locale = java.util.Locale.US) =
        UnitConverterUiState.parseLocalizedNumber(raw, locale)

    private fun fmt(value: Double, locale: java.util.Locale = java.util.Locale.US) =
        UnitConverterUiState.formatResult(value, locale)

    @Test
    fun `parses US grouping separators`() {
        assertEquals(1000.0, parse("1,000")!!, 0.0)
        assertEquals(1000.5, parse("1,000.5")!!, 0.0)
        assertEquals(1234567.89, parse("1,234,567.89")!!, 0.0)
    }

    @Test
    fun `parses German decimal comma`() {
        val de = java.util.Locale.GERMANY
        assertEquals(1.5, parse("1,5", de)!!, 0.0)
        assertEquals(1000.0, parse("1.000", de)!!, 0.0)
        assertEquals(1000.5, parse("1.000,5", de)!!, 0.0)
    }

    @Test
    fun `rejects ambiguous separator usage`() {
        // "1,5" in en-US: comma is the grouping char but not a valid group → reject
        // rather than silently parsing as 15
        assertEquals(null, parse("1,5"))
        // "1.5" in de-DE: same problem, reversed
        assertEquals(null, parse("1.5", java.util.Locale.GERMANY))
        // malformed grouping
        assertEquals(null, parse("12,34"))
        assertEquals(null, parse("1,23,456"))
    }

    @Test
    fun `accepts dot as decimal in comma locale where dot is not grouping`() {
        // fr-FR uses NBSP grouping, so '.' is unambiguous
        assertEquals(1.5, parse("1.5", java.util.Locale.FRANCE)!!, 0.0)
    }

    @Test
    fun `strips whitespace grouping`() {
        assertEquals(1234.5, parse("1 234.5")!!, 0.0)
        assertEquals(1234.5, parse("1 234.5")!!, 0.0) // NBSP
        assertEquals(1234.5, parse("1 234.5")!!, 0.0) // NNBSP
    }

    @Test
    fun `translates Arabic-Indic digits`() {
        assertEquals(1.5, parse("١.٥")!!, 0.0)
    }

    @Test
    fun `rejects non-numeric grammar that toDoubleOrNull accepted`() {
        assertEquals(null, parse("NaN"))
        assertEquals(null, parse("Infinity"))
        assertEquals(null, parse("1d"))
        assertEquals(null, parse("5f"))
        assertEquals(null, parse("0x1p3"))
        assertEquals(null, parse("abc"))
        assertEquals(null, parse("-"))
        assertEquals(null, parse(""))
    }

    @Test
    fun `parses exponent and partial forms`() {
        assertEquals(1000.0, parse("1e3")!!, 0.0)
        assertEquals(0.0015, parse("1.5e-3")!!, 0.0)
        assertEquals(1.0, parse("1.")!!, 0.0)
        assertEquals(0.5, parse(".5")!!, 0.0)
        assertEquals(-1.5, parse("-1.5")!!, 0.0)
    }

    // ── localized formatting ────────────────────────────────────────────────

    @Test
    fun `formats with locale decimal separator and grouping`() {
        assertEquals("1,609.344", fmt(1609.344))
        assertEquals("1.609,344", fmt(1609.344, java.util.Locale.GERMANY))
        assertEquals("0,3048", fmt(0.3048, java.util.Locale.GERMANY))
    }

    @Test
    fun `scientific notation uses locale decimal in mantissa`() {
        assertEquals("1,6e+13", fmt(1.6e13, java.util.Locale.GERMANY))
        assertEquals("1.6e+13", fmt(1.6e13))
    }

    @Test
    fun `round-trips formatted output back through the parser`() {
        // en-US: formatted "1,609.344" must parse back
        assertEquals(1609.344, parse(fmt(1609.344))!!, 1e-9)
        // de-DE: formatted "1.609,344" must parse back
        assertEquals(1609.344, parse(fmt(1609.344, java.util.Locale.GERMANY), java.util.Locale.GERMANY)!!, 1e-9)
    }
}
