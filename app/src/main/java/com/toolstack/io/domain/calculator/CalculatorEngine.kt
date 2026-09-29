package com.toolstack.io.domain.calculator

/**
 * The operating mode of the calculator.
 *
 * [BASIC] is the initial mode — four-operation arithmetic with a display-style
 * input model (digit-by-digit entry, operator queuing, chained operations).
 *
 * [CONSTRUCTION] is reserved for a future mode that will add trade-specific
 * functions (feet-inch-fraction entry, board-feet, concrete volume, etc.).
 * Adding it is a matter of branching on this enum in [CalculatorEngine] and
 * extending [CalculatorState] with mode-specific fields — no other code needs
 * to change.
 */
enum class CalculatorMode { BASIC, CONSTRUCTION }

/**
 * Immutable snapshot of calculator state produced by [CalculatorEngine].
 *
 * @param display         The string shown in the main readout. Always non-empty.
 * @param expression      Secondary line showing the in-progress expression, e.g. "12 + 3 =".
 *                        Empty when idle.
 * @param liveResult      Dynamic preview of the current expression's result. Empty when no valid expression.
 * @param history         List of past calculations (e.g., "12 + 8 = 20"). Most recent first.
 * @param mode            Which operating mode is currently active.
 */
data class CalculatorState(
    val display: String = "0",
    val expression: String = "",
    val liveResult: String = "",
    val history: List<String> = emptyList(),
    val mode: CalculatorMode = CalculatorMode.BASIC
)

/**
 * Pure, stateless calculator engine for [CalculatorMode.BASIC] arithmetic.
 *
 * All methods take the current [CalculatorState] and return a new one;
 * no mutable state is held here. The ViewModel owns the state and calls
 * these functions to advance it.
 *
 * ### Input model
 * - Digits and decimal point build [pendingInput] character by character.
 * - An operator (+, −, ×, ÷) commits [pendingInput] as the left operand and
 *   queues the operator.
 * - A second operator press (before entering right operand) replaces the
 *   queued operator without evaluating.
 * - Equals evaluates the queued operation; a subsequent digit press starts fresh.
 * - Percent converts the current display value to percent (÷ 100).
 * - Sign-flip toggles the sign of the current display value.
 * - Backspace deletes the last character of [pendingInput]; clears to "0" when empty.
 * - Clear (AC) resets everything to initial state.
 *
 * ### Extension point for CONSTRUCTION mode
 * Add a branch in each function that checks `state.mode == CalculatorMode.CONSTRUCTION`
 * and delegates to a separate construction-aware implementation. The ViewModel and UI
 * only need to pass the updated mode down.
 */
object CalculatorEngine {

    // ── internal working state (not exposed in CalculatorState) ──────────────
    // We embed it in a private data class and carry it through the state so the
    // engine remains stateless while preserving the full arithmetic context.

    /**
     * Applies a digit character (0–9), "00", or decimal point to the current state.
     * Enforces a maximum of 15 digits per number (excluding the decimal point).
     */
    fun onDigit(state: CalculatorState, digit: String, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onDigitConstruction(state, digit, internal)

        val newInternal: InternalState
        val newDisplay: String

        if (internal.justEvaluated) {
            // After "=", start a fresh expression
            val initialInput = when {
                digit == "." -> "0."
                digit == "00" -> "0"
                else -> digit
            }
            newInternal = InternalState(pendingInput = initialInput)
            newDisplay = initialInput
        } else {
            val current = internal.pendingInput
            
            // Count digits only (excluding decimal point and minus sign)
            val digitCount = current.count { it.isDigit() }
            val newDigitCount = when (digit) {
                "00" -> 2
                "." -> 0
                else -> digit.count { it.isDigit() }
            }
            
            // Enforce 15-digit limit
            if (digitCount + newDigitCount > 15 && digit != ".") {
                // Reject the input if it would exceed 15 digits
                return state to internal
            }
            
            val newInput = when {
                current.isEmpty() && digit == "." -> "0."  // Normalize initial decimal
                digit == "." && current.contains(".") -> current  // only one decimal point
                digit == "0" && current == "0"        -> current  // leading-zero guard
                digit == "00" && current == "0"       -> current  // don't allow "000..."
                current == "0" && digit != "." && digit != "00" -> digit
                current.isEmpty()                     -> digit
                else                                  -> current + digit
            }
            newInternal = internal.copy(pendingInput = newInput)
            newDisplay = newInput
        }

        // Build full expression string
        val expressionStr = buildExpressionString(newInternal.expressionTokens, newInternal.pendingInput)
        
        // Compute live result from the full expression
        val liveResult = evaluateExpression(newInternal.expressionTokens, newInternal.pendingInput)
        
        return state.copy(
            display = newDisplay, 
            expression = expressionStr,
            liveResult = liveResult
        ) to newInternal
    }

    /**
     * Applies an operator (+, −, ×, ÷).
     */
    fun onOperator(state: CalculatorState, op: String, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onOperatorConstruction(state, op, internal)

        // If we have pending input, add it and the operator to the expression
        val newTokens = if (internal.pendingInput.isNotEmpty()) {
            internal.expressionTokens + internal.pendingInput + op
        } else if (internal.expressionTokens.isEmpty()) {
            // Starting with just an operator after "="? Use the last result
            listOf(state.display, op)
        } else if (internal.expressionTokens.isNotEmpty()) {
            // Replace the last operator if we just pressed an operator
            val last = internal.expressionTokens.last()
            if (isOperator(last)) {
                internal.expressionTokens.dropLast(1) + op
            } else {
                internal.expressionTokens + op
            }
        } else {
            listOf(op)
        }

        val newInternal = internal.copy(
            expressionTokens = newTokens,
            pendingInput = "",
            justEvaluated = false
        )

        val expressionStr = buildExpressionString(newTokens, "")
        
        return state.copy(
            display = state.display,  // Keep showing the last number
            expression = expressionStr,
            liveResult = ""  // Clear live result when operator is added
        ) to newInternal
    }

    /**
     * Evaluates the full expression and shows the result.
     * Appends the completed calculation to history.
     */
    fun onEquals(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onEqualsConstruction(state, internal)

        // Build the complete expression including any pending input
        val completeTokens = if (internal.pendingInput.isNotEmpty()) {
            internal.expressionTokens + internal.pendingInput
        } else {
            internal.expressionTokens
        }

        // Evaluate the full expression
        val result = calculateFromTokens(completeTokens)
        val resultStr = formatNumber(result)

        // Build the history entry
        val exprStr = completeTokens.joinToString(" ")
        val historyEntry = if (completeTokens.isNotEmpty()) "$exprStr = $resultStr" else resultStr

        // Add to history only if there was a complete expression
        val newHistory = if (
            completeTokens.size >= 3 && !isOperator(completeTokens.last())
        ) {
            listOf(historyEntry) + state.history
        } else {
            state.history
        }

        val newInternal = InternalState(
            expressionTokens = emptyList(),
            pendingInput = resultStr,
            justEvaluated = true
        )
        
        return state.copy(
            display = resultStr, 
            expression = resultStr,
            liveResult = "",
            history = newHistory
        ) to newInternal
    }

    /**
     * Clears all state (AC button).
     */
    fun onClear(state: CalculatorState): Pair<CalculatorState, InternalState> =
        state.copy(display = "0", expression = "0", liveResult = "") to InternalState()

    /**
     * Deletes the last character of the current input or last token.
     */
    fun onBackspace(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onBackspaceConstruction(state, internal)
        if (internal.justEvaluated) return onClear(state)

        val newInternal: InternalState
        val newDisplay: String

        if (internal.pendingInput.isNotEmpty()) {
            // Delete from pending input
            val trimmed = internal.pendingInput.dropLast(1)
            newInternal = internal.copy(pendingInput = trimmed)
            newDisplay = if (trimmed.isEmpty() && internal.expressionTokens.isNotEmpty()) {
                // Show the last number from tokens
                internal.expressionTokens.findLast { !isOperator(it) } ?: "0"
            } else if (trimmed.isEmpty()) {
                "0"
            } else {
                trimmed
            }
        } else if (internal.expressionTokens.isNotEmpty()) {
            // Remove the last token
            val newTokens = internal.expressionTokens.dropLast(1)
            newInternal = internal.copy(expressionTokens = newTokens)
            newDisplay = newTokens.findLast { !isOperator(it) } ?: "0"
        } else {
            // Nothing to delete
            return state to internal
        }
        
        val expressionStr = buildExpressionString(newInternal.expressionTokens, newInternal.pendingInput)
        val liveResult = evaluateExpression(newInternal.expressionTokens, newInternal.pendingInput)
        
        return state.copy(
            display = newDisplay,
            expression = if (expressionStr.isEmpty()) "0" else expressionStr,
            liveResult = liveResult
        ) to newInternal
    }

    /**
     * Applies percentage in context of the current operation.
     */
    fun onPercent(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onPercentConstruction(state, internal)
        
        val currentValue = state.display.toDoubleOrNull() ?: 0.0
        val percentValue = currentValue / 100.0
        
        val str = formatNumber(percentValue)
        val newInternal = internal.copy(pendingInput = str, justEvaluated = false)
        
        val expressionStr = buildExpressionString(newInternal.expressionTokens, str)
        val liveResult = evaluateExpression(newInternal.expressionTokens, str)
        
        return state.copy(
            display = str,
            expression = expressionStr,
            liveResult = liveResult
        ) to newInternal
    }

    /**
     * Flips the sign of the current display value.
     */
    fun onSignFlip(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onSignFlipConstruction(state, internal)
        val value = (state.display.toDoubleOrNull() ?: 0.0) * -1.0
        val str = formatNumber(value)
        val newInternal = internal.copy(pendingInput = str, justEvaluated = false)
        
        val expressionStr = buildExpressionString(newInternal.expressionTokens, str)
        val liveResult = evaluateExpression(newInternal.expressionTokens, str)
        
        return state.copy(
            display = str,
            expression = expressionStr,
            liveResult = liveResult
        ) to newInternal
    }

    // ── formatting ────────────────────────────────────────────────────────────

    /**
     * Builds a displayable expression string from tokens and pending input.
     */
    private fun buildExpressionString(tokens: List<String>, pendingInput: String): String {
        val parts = tokens + if (pendingInput.isNotEmpty()) listOf(pendingInput) else emptyList()
        return if (parts.isEmpty()) "0" else parts.joinToString(" ")
    }

    /**
     * Checks if a token is an operator.
     */
    private fun isOperator(token: String): Boolean =
        token in listOf("+", "−", "×", "÷")

    /**
     * Evaluates an expression from tokens and pending input, returns formatted result or empty string.
     */
    private fun evaluateExpression(tokens: List<String>, pendingInput: String): String {
        val completeTokens = if (pendingInput.isNotEmpty()) {
            tokens + pendingInput
        } else {
            tokens
        }

        // Only show live result if we have a complete operation (at least: number operator number)
        if (completeTokens.size < 3) return ""
        
        // Don't show if the last token is an operator
        if (completeTokens.isNotEmpty() && isOperator(completeTokens.last())) return ""

        val result = calculateFromTokens(completeTokens)
        return formatNumber(result)
    }

    /**
     * Calculates the result from a list of tokens (numbers and operators).
     * Respects operator precedence (× and ÷ before + and −).
     */
    private fun calculateFromTokens(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        if (tokens.size == 1) return tokens[0].toDoubleOrNull() ?: 0.0

        // First pass: handle × and ÷ (higher precedence)
        val afterMultDiv = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token == "×" && i > 0 && i < tokens.size - 1 -> {
                    val left = afterMultDiv.removeLastOrNull()?.toDoubleOrNull() ?: 0.0
                    val right = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    afterMultDiv.add(formatNumber(left * right))
                    i += 2
                }
                token == "÷" && i > 0 && i < tokens.size - 1 -> {
                    val left = afterMultDiv.removeLastOrNull()?.toDoubleOrNull() ?: 0.0
                    val right = tokens[i + 1].toDoubleOrNull() ?: 1.0
                    afterMultDiv.add(formatNumber(if (right == 0.0) Double.NaN else left / right))
                    i += 2
                }
                else -> {
                    afterMultDiv.add(token)
                    i++
                }
            }
        }

        // Second pass: handle + and − (lower precedence)
        var result = afterMultDiv[0].toDoubleOrNull() ?: 0.0
        i = 1
        while (i < afterMultDiv.size) {
            val operator = afterMultDiv[i]
            val nextValue = afterMultDiv.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0
            result = when (operator) {
                "+" -> result + nextValue
                "−" -> result - nextValue
                else -> result
            }
            i += 2
        }

        return result
    }

    /**
     * Computes the live result preview for the current expression.
     * Returns empty string if the expression is incomplete or invalid.
     * @deprecated Use evaluateExpression instead
     */
    private fun computeLiveResult(internal: InternalState, currentDisplay: String): String {
        // Legacy function - redirect to new implementation
        return evaluateExpression(internal.expressionTokens, currentDisplay)
    }

    /**
     * Formats a [Double] for display: strips trailing zeros from decimals,
     * caps at 10 significant digits, and falls back to "Error" for NaN/Infinity.
     */
    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        // If the value is a whole number, show without decimal.
        return if (value == kotlin.math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e10) {
            String.format(java.util.Locale.US, "%.0f", value)
        } else {
            // Up to 10 significant digits
            val raw = String.format(java.util.Locale.US, "%.10g", value)
            // Preserve scientific notation (e.g., "1.000000000e+10")
            // Only trim zeros from the mantissa (before 'e'), not the exponent
            if (raw.contains('e', ignoreCase = true)) {
                val parts = raw.split('e', 'E', limit = 2)
                val mantissa = parts[0].trimEnd('0').trimEnd('.')
                val exponent = parts[1]
                "$mantissa${if (raw.contains('e')) 'e' else 'E'}$exponent"
            } else {
                raw.trimEnd('0').trimEnd('.')
            }
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private fun evaluate(left: Double?, op: String?, right: Double): Double {
        val l = left ?: right
        return when (op) {
            "+"  -> l + right
            "−"  -> l - right
            "×"  -> l * right
            "÷"  -> if (right == 0.0) Double.NaN else l / right
            else -> right
        }
    }

    // ── construction mode stubs (ready to implement) ─────────────────────────

    private fun onDigitConstruction(state: CalculatorState, digit: String, internal: InternalState) =
        onDigit(state.copy(mode = CalculatorMode.BASIC), digit, internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }

    private fun onOperatorConstruction(state: CalculatorState, op: String, internal: InternalState) =
        onOperator(state.copy(mode = CalculatorMode.BASIC), op, internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }

    private fun onEqualsConstruction(state: CalculatorState, internal: InternalState) =
        onEquals(state.copy(mode = CalculatorMode.BASIC), internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }

    private fun onBackspaceConstruction(state: CalculatorState, internal: InternalState) =
        onBackspace(state.copy(mode = CalculatorMode.BASIC), internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }

    private fun onPercentConstruction(state: CalculatorState, internal: InternalState) =
        onPercent(state.copy(mode = CalculatorMode.BASIC), internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }

    private fun onSignFlipConstruction(state: CalculatorState, internal: InternalState) =
        onSignFlip(state.copy(mode = CalculatorMode.BASIC), internal)
            .let { (s, i) -> s.copy(mode = CalculatorMode.CONSTRUCTION) to i }
}

/**
 * Mutable arithmetic context that travels alongside [CalculatorState].
 *
 * Kept separate from [CalculatorState] because it is not meaningful to the UI
 * on its own — the UI only reads [CalculatorState.display] and
 * [CalculatorState.expression]. The ViewModel holds both objects together.
 */
data class InternalState(
    /** The full expression being built as a list of tokens (numbers and operators). */
    val expressionTokens: List<String> = emptyList(),
    /** Characters being typed for the current number. */
    val pendingInput: String = "",
    /** True immediately after "=" so the next digit starts a fresh expression. */
    val justEvaluated: Boolean = false,
    /** Legacy fields for backward compatibility - to be removed */
    val leftOperand: Double? = null,
    val pendingOperator: String? = null
)
