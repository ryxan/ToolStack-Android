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
    fun `onPercent with multiplication computes percentage of left operand`() {
        // User entered: 50 × 50 %
        val state = CalculatorState(display = "50")
        val internal = InternalState(
            leftOperand = 50.0,
            pendingOperator = "×",
            pendingInput = "50"
        )
        
        val (newState, newInternal) = CalculatorEngine.onPercent(state, internal)
        
        // 50 × 50% should show 25 (which is 50 × 50 ÷ 100)
        assertEquals("25", newState.display)
        assertEquals("25", newInternal.pendingInput)
    }

    @Test
    fun `onPercent with addition computes percentage of left operand`() {
        // User entered: 72 + 5 %
        // Should compute 72 + (72 × 5 ÷ 100) = 72 + 3.6
        val state = CalculatorState(display = "5")
        val internal = InternalState(
            leftOperand = 72.0,
            pendingOperator = "+",
            pendingInput = "5"
        )
        
        val (newState, newInternal) = CalculatorEngine.onPercent(state, internal)
        
        assertEquals("3.6", newState.display)
        assertEquals("3.6", newInternal.pendingInput)
    }

    @Test
    fun `onPercent with subtraction computes discount amount`() {
        // User entered: 72 − 20 %
        // Should compute 72 − (72 × 20 ÷ 100) = 72 − 14.4
        val state = CalculatorState(display = "20")
        val internal = InternalState(
            leftOperand = 72.0,
            pendingOperator = "−",
            pendingInput = "20"
        )
        
        val (newState, _) = CalculatorEngine.onPercent(state, internal)
        
        assertEquals("14.4", newState.display)
    }

    @Test
    fun `expression line persists when typing digits after operator`() {
        // User enters: 5 + 5 - 5
        // Start with 5
        var state = CalculatorState(display = "5")
        var internal = InternalState(pendingInput = "5")
        
        // Press +
        val (state2, internal2) = CalculatorEngine.onOperator(state, "+", internal)
        assertEquals("5 +", state2.expression)
        
        // Type 5
        val (state3, internal3) = CalculatorEngine.onDigit(state2, "5", internal2)
        assertEquals("5 +", state3.expression) // Expression line should stay as "5 +"
        assertEquals("5", state3.display)
        
        // Press - (evaluates to 10, then sets up subtraction)
        val (state4, internal4) = CalculatorEngine.onOperator(state3, "−", internal3)
        assertEquals("10 −", state4.expression)
        assertEquals("10", state4.display)
        
        // Type 5
        val (state5, internal5) = CalculatorEngine.onDigit(state4, "5", internal4)
        assertEquals("10 −", state5.expression) // Expression line should stay as "10 −"
        assertEquals("5", state5.display)
        
        // Press = (final result should be 5)
        val (finalState, _) = CalculatorEngine.onEquals(state5, internal5)
        assertEquals("10 − 5 =", finalState.expression)
        assertEquals("5", finalState.display)
    }
}
