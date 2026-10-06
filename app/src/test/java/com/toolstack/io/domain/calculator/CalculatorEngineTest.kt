package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {

    @Test
    fun `formatNumber preserves scientific notation exponents`() {
        // Large numbers that trigger scientific notation
        assertEquals("1e+10", CalculatorEngine.formatNumber(1e10))
        assertEquals("1.23e+15", CalculatorEngine.formatNumber(1.23e15))
        assertEquals("5e+20", CalculatorEngine.formatNumber(5e20))
        
        // Small numbers in scientific notation
        // Note: Java %.10g format adds leading zeros in exponents, e.g. "e-08" not "e-8"
        assertEquals("1e-10", CalculatorEngine.formatNumber(1e-10))
        assertEquals("2.5e-08", CalculatorEngine.formatNumber(2.5e-8))
    }

    @Test
    fun `formatNumber strips trailing zeros from regular decimals`() {
        assertEquals("1.5", CalculatorEngine.formatNumber(1.5000000))
        assertEquals("3.14", CalculatorEngine.formatNumber(3.14000))
        assertEquals("0.1", CalculatorEngine.formatNumber(0.10000))
    }

    @Test
    fun `formatNumber shows whole numbers without decimal point`() {
        assertEquals("0", CalculatorEngine.formatNumber(0.0))
        assertEquals("5", CalculatorEngine.formatNumber(5.0))
        assertEquals("42", CalculatorEngine.formatNumber(42.0))
        assertEquals("1000", CalculatorEngine.formatNumber(1000.0))
    }

    @Test
    fun `formatNumber handles error cases`() {
        assertEquals("Error", CalculatorEngine.formatNumber(Double.NaN))
        assertEquals("Error", CalculatorEngine.formatNumber(Double.POSITIVE_INFINITY))
        assertEquals("Error", CalculatorEngine.formatNumber(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `formatNumber handles negative numbers`() {
        assertEquals("-5", CalculatorEngine.formatNumber(-5.0))
        assertEquals("-3.14", CalculatorEngine.formatNumber(-3.14))
        assertEquals("-1e+10", CalculatorEngine.formatNumber(-1e10))
    }

    @Test
    fun `formatNumber preserves significant zeros in mantissa`() {
        // Should preserve zeros that are significant
        assertEquals("1.05", CalculatorEngine.formatNumber(1.05))
        assertEquals("10.01", CalculatorEngine.formatNumber(10.01))
    }

    @Test
    fun `onPercent without operator divides by 100`() {
        val state = CalculatorState(display = "50")
        val internal = InternalState(pendingInput = "50")
        
        val (newState, _) = CalculatorEngine.onPercent(state, internal)
        
        assertEquals("0.5", newState.display)
    }

    @Test
    fun `onPercent with operator still divides by 100`() {
        // After refactor, percent just divides by 100 regardless of context
        val state = CalculatorState(display = "50")
        val internal = InternalState(
            expressionTokens = listOf("50", "×"),
            pendingInput = "50"
        )
        
        val (newState, _) = CalculatorEngine.onPercent(state, internal)
        
        assertEquals("0.5", newState.display)
    }

    @Test
    fun `expression line persists when typing digits after operator`() {
        // User enters: 5 + 5 + 5
        // Start with 5
        var state = CalculatorState(display = "5")
        var internal = InternalState(pendingInput = "5")
        
        // Press +
        val (state2, internal2) = CalculatorEngine.onOperator(state, "+", internal)
        assertEquals("5 +", state2.expression)
        
        // Type 5 - now expression shows complete equation
        val (state3, internal3) = CalculatorEngine.onDigit(state2, "5", internal2)
        assertEquals("5 + 5", state3.expression)
        assertEquals("5", state3.display)
        assertEquals("10", state3.liveResult) // Live result shows 10
        
        // Press + (should add to expression, not evaluate)
        val (state4, internal4) = CalculatorEngine.onOperator(state3, "+", internal3)
        assertEquals("5 + 5 +", state4.expression)
        
        // Type 5 again
        val (state5, internal5) = CalculatorEngine.onDigit(state4, "5", internal4)
        assertEquals("5 + 5 + 5", state5.expression)
        assertEquals("5", state5.display)
        assertEquals("15", state5.liveResult) // Live result shows 15
        
        // Press = (final result should be 15)
        val (finalState, _) = CalculatorEngine.onEquals(state5, internal5)
        assertEquals("15", finalState.expression)
        assertEquals("15", finalState.display)
        assertEquals(1, finalState.history.size)
        assertEquals("5 + 5 + 5 = 15", finalState.history[0])
    }

    @Test
    fun `live result updates as digits are typed`() {
        // Start with 12
        var state = CalculatorState(display = "12")
        var internal = InternalState(pendingInput = "12")
        
        // Press +
        val (state2, internal2) = CalculatorEngine.onOperator(state, "+", internal)
        assertEquals("12 +", state2.expression)
        assertEquals("", state2.liveResult)  // No live result yet
        
        // Type 8
        val (state3, internal3) = CalculatorEngine.onDigit(state2, "8", internal2)
        assertEquals("12 + 8", state3.expression)
        assertEquals("8", state3.display)
        assertEquals("20", state3.liveResult)  // Live result shows 20
        
        // Type another digit to make 88
        val (state4, internal4) = CalculatorEngine.onDigit(state3, "8", internal3)
        assertEquals("12 + 88", state4.expression)
        assertEquals("88", state4.display)
        assertEquals("100", state4.liveResult)  // Live result updates to 100
    }

    @Test
    fun `live result clears when operator is pressed`() {
        // Build up an expression with live result
        var state = CalculatorState(display = "5")
        var internal = InternalState(pendingInput = "5")
        
        val (state2, internal2) = CalculatorEngine.onOperator(state, "+", internal)
        val (state3, internal3) = CalculatorEngine.onDigit(state2, "3", internal2)
        
        // Should have live result
        assertEquals("8", state3.liveResult)
        
        // Press another operator - live result should clear
        val (state4, internal4) = CalculatorEngine.onOperator(state3, "×", internal3)
        assertEquals("", state4.liveResult)
    }

    @Test
    fun `history accumulates multiple calculations`() {
        var state = CalculatorState()
        var internal = InternalState()
        
        // First calculation: 5 + 5 = 10
        val (s1, i1) = CalculatorEngine.onDigit(state, "5", internal)
        val (s2, i2) = CalculatorEngine.onOperator(s1, "+", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "5", i2)
        val (s4, i4) = CalculatorEngine.onEquals(s3, i3)
        
        assertEquals(1, s4.history.size)
        assertEquals("5 + 5 = 10", s4.history[0])
        
        // Second calculation: 10 × 2 = 20
        val (s5, i5) = CalculatorEngine.onOperator(s4, "×", i4)
        val (s6, i6) = CalculatorEngine.onDigit(s5, "2", i5)
        val (s7, i7) = CalculatorEngine.onEquals(s6, i6)
        
        assertEquals(2, s7.history.size)
        assertEquals("10 × 2 = 20", s7.history[0])  // Most recent first
        assertEquals("5 + 5 = 10", s7.history[1])
    }

    @Test
    fun `clear resets to initial state but preserves history`() {
        // Build up some state
        var state = CalculatorState(display = "12", expression = "5 + 12", liveResult = "17", history = listOf("10 + 5 = 15"))
        
        val (newState, newInternal) = CalculatorEngine.onClear(state)
        
        assertEquals("0", newState.display)
        assertEquals("0", newState.expression)
        assertEquals("", newState.liveResult)
        assertEquals(listOf("10 + 5 = 15"), newState.history)  // History preserved
    }

    @Test
    fun `supports multi-operation expressions`() {
        // Test the desired behavior: 5 + 5 + 5 - 3 = 12
        var state = CalculatorState()
        var internal = InternalState()
        
        // Type 5
        val (s1, i1) = CalculatorEngine.onDigit(state, "5", internal)
        assertEquals("5", s1.expression)
        
        // Press +
        val (s2, i2) = CalculatorEngine.onOperator(s1, "+", i1)
        assertEquals("5 +", s2.expression)
        
        // Type 5
        val (s3, i3) = CalculatorEngine.onDigit(s2, "5", i2)
        assertEquals("5 + 5", s3.expression)
        assertEquals("10", s3.liveResult)  // Shows 10
        
        // Press + (adds to expression)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "+", i3)
        assertEquals("5 + 5 +", s4.expression)
        
        // Type 5
        val (s5, i5) = CalculatorEngine.onDigit(s4, "5", i4)
        assertEquals("5 + 5 + 5", s5.expression)
        assertEquals("15", s5.liveResult)  // Shows 15
        
        // Press -
        val (s6, i6) = CalculatorEngine.onOperator(s5, "−", i5)
        assertEquals("5 + 5 + 5 −", s6.expression)
        
        // Type 3
        val (s7, i7) = CalculatorEngine.onDigit(s6, "3", i6)
        assertEquals("5 + 5 + 5 − 3", s7.expression)
        assertEquals("12", s7.liveResult)  // Shows 12
        
        // Press =
        val (s8, i8) = CalculatorEngine.onEquals(s7, i7)
        assertEquals("12", s8.display)
        assertEquals("12", s8.expression)
        assertEquals(1, s8.history.size)
        assertEquals("5 + 5 + 5 − 3 = 12", s8.history[0])
    }

    @Test
    fun `respects operator precedence`() {
        // Test: 5 + 3 × 2 = 11 (not 16)
        var state = CalculatorState()
        var internal = InternalState()
        
        val (s1, i1) = CalculatorEngine.onDigit(state, "5", internal)
        val (s2, i2) = CalculatorEngine.onOperator(s1, "+", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "3", i2)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "×", i3)
        val (s5, i5) = CalculatorEngine.onDigit(s4, "2", i4)
        
        assertEquals("5 + 3 × 2", s5.expression)
        assertEquals("11", s5.liveResult)  // 5 + (3 × 2) = 11
        
        val (s6, i6) = CalculatorEngine.onEquals(s5, i5)
        assertEquals("11", s6.display)
    }

    @Test
    fun `divide by zero mid-expression propagates error`() {
        // 5 ÷ 0 + 3 must be Error, not 3 (NaN must not collapse to 0 on the second pass)
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "5", InternalState())
        val (s2, i2) = CalculatorEngine.onOperator(s1, "÷", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "0", i2)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "+", i3)
        val (s5, i5) = CalculatorEngine.onDigit(s4, "3", i4)
        val (s6, _) = CalculatorEngine.onEquals(s5, i5)

        assertEquals("Error", s6.display)
        assertEquals("5 ÷ 0 + 3 = Error", s6.history[0])
    }

    @Test
    fun `chained operations keep full precision`() {
        // 1 ÷ 3 × 3 = 1 — intermediates must not round-trip through a 10-digit string
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "1", InternalState())
        val (s2, i2) = CalculatorEngine.onOperator(s1, "÷", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "3", i2)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "×", i3)
        val (s5, i5) = CalculatorEngine.onDigit(s4, "3", i4)
        val (s6, _) = CalculatorEngine.onEquals(s5, i5)

        assertEquals("1", s6.display)
    }

    @Test
    fun `operator after error resets instead of chaining`() {
        // 5 ÷ 0 = → Error; pressing + must start fresh, not produce "Error +"
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "5", InternalState())
        val (s2, i2) = CalculatorEngine.onOperator(s1, "÷", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "0", i2)
        val (s4, i4) = CalculatorEngine.onEquals(s3, i3)
        assertEquals("Error", s4.display)

        val (s5, _) = CalculatorEngine.onOperator(s4, "+", i4)
        assertEquals("0", s5.display)
        assertEquals("0 +", s5.expression)
    }

    @Test
    fun `equals after error just clears`() {
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "5", InternalState())
        val (s2, i2) = CalculatorEngine.onOperator(s1, "÷", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "0", i2)
        val (s4, i4) = CalculatorEngine.onEquals(s3, i3)

        val (s5, _) = CalculatorEngine.onEquals(s4, i4)
        assertEquals("0", s5.display)
    }

    @Test
    fun `sign flip preserves typed decimal input`() {
        // Round-tripping through Double would lose the trailing "." and "0"
        val (s1, i1) = CalculatorEngine.onSignFlip(
            CalculatorState(display = "5."),
            InternalState(pendingInput = "5.")
        )
        assertEquals("-5.", s1.display)
        assertEquals("-5.", i1.pendingInput)

        val (s2, i2) = CalculatorEngine.onSignFlip(
            CalculatorState(display = "5.10"),
            InternalState(pendingInput = "5.10")
        )
        assertEquals("-5.10", s2.display)

        // Flipping again restores the original string
        val (s3, _) = CalculatorEngine.onSignFlip(s2, i2)
        assertEquals("5.10", s3.display)
    }

    @Test
    fun `digit after negative zero replaces it`() {
        // 0 → ± → -0 → 5 must give "-5", not "-05"
        val (s1, i1) = CalculatorEngine.onSignFlip(
            CalculatorState(display = "0"),
            InternalState(pendingInput = "0")
        )
        assertEquals("-0", s1.display)

        val (s2, i2) = CalculatorEngine.onDigit(s1, "5", i1)
        assertEquals("-5", s2.display)
    }

    @Test
    fun `percent with pending addition uses left operand`() {
        // 200 + 10 % → operand becomes 20 (10% of 200), result 220
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "2", InternalState())
        val (s2, i2) = CalculatorEngine.onDigit(s1, "0", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "0", i2)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "+", i3)
        val (s5, i5) = CalculatorEngine.onDigit(s4, "1", i4)
        val (s6, i6) = CalculatorEngine.onDigit(s5, "0", i5)
        val (s7, i7) = CalculatorEngine.onPercent(s6, i6)

        assertEquals("20", s7.display)
        assertEquals("200 + 20", s7.expression)
        assertEquals("220", s7.liveResult)

        val (s8, _) = CalculatorEngine.onEquals(s7, i7)
        assertEquals("220", s8.display)
    }

    @Test
    fun `percent with pending subtraction uses left operand`() {
        // 200 − 10 % → 200 − 20 → 180
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "2", InternalState())
        val (s2, i2) = CalculatorEngine.onDigit(s1, "0", i1)
        val (s3, i3) = CalculatorEngine.onDigit(s2, "0", i2)
        val (s4, i4) = CalculatorEngine.onOperator(s3, "−", i3)
        val (s5, i5) = CalculatorEngine.onDigit(s4, "1", i4)
        val (s6, i6) = CalculatorEngine.onDigit(s5, "0", i5)
        val (s7, _) = CalculatorEngine.onPercent(s6, i6)

        assertEquals("20", s7.display)
        assertEquals("180", s7.liveResult)
    }

    @Test
    fun `digit after percent replaces the result`() {
        // 50 % → 0.5 (committed); typing 3 must give "3", not "0.53"
        val (s1, i1) = CalculatorEngine.onDigit(CalculatorState(), "5", InternalState())
        val (s2, i2) = CalculatorEngine.onDigit(s1, "0", i1)
        val (s3, i3) = CalculatorEngine.onPercent(s2, i2)
        assertEquals("0.5", s3.display)

        val (s4, _) = CalculatorEngine.onDigit(s3, "3", i3)
        assertEquals("3", s4.display)
    }
}
