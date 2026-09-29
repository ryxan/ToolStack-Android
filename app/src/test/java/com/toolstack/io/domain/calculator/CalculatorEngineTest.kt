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
}
