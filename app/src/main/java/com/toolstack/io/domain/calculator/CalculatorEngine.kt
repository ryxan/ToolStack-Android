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
 * @param display     The string shown in the main readout. Always non-empty.
 * @param expression  Secondary line showing the in-progress expression, e.g. "12 + 3 =".
 *                    Empty when idle.
 * @param mode        Which operating mode is currently active.
 */
data class CalculatorState(
    val display: String = "0",
    val expression: String = "",
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
     * Applies a digit character (0–9) or decimal point to the current state.
     */
    fun onDigit(state: CalculatorState, digit: String, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onDigitConstruction(state, digit, internal)

        val newInternal: InternalState
        val newDisplay: String

        if (internal.justEvaluated) {
            // After "=" a digit press starts a fresh expression.
            newInternal = InternalState().copy(pendingInput = if (digit == ".") "0." else digit)
            newDisplay = newInternal.pendingInput
        } else {
            val current = internal.pendingInput
            newInternal = when {
                digit == "." && current.contains(".") -> internal  // only one decimal point
                digit == "0" && current == "0"        -> internal  // leading-zero guard
                current == "0" && digit != "."        -> internal.copy(pendingInput = digit)
                else                                  -> internal.copy(pendingInput = current + digit)
            }
            newDisplay = newInternal.pendingInput
        }

        return state.copy(display = newDisplay, expression = internal.expressionLine(newInternal)) to newInternal
    }

    /**
     * Applies an operator (+, −, ×, ÷).
     */
    fun onOperator(state: CalculatorState, op: String, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onOperatorConstruction(state, op, internal)

        val currentValue = state.display.toDoubleOrNull() ?: 0.0

        val newInternal = if (internal.leftOperand != null && !internal.justEvaluated && internal.pendingInput.isEmpty()) {
            // Replace queued operator without evaluating (e.g. "5 + ×" → use ×).
            internal.copy(pendingOperator = op)
        } else {
            val left = if (internal.leftOperand != null && !internal.justEvaluated) {
                evaluate(internal.leftOperand, internal.pendingOperator, currentValue)
            } else {
                currentValue
            }
            InternalState(leftOperand = left, pendingOperator = op, pendingInput = "")
        }

        val exprDisplay = formatNumber(newInternal.leftOperand ?: currentValue) + " " + op
        return state.copy(
            display = formatNumber(newInternal.leftOperand ?: currentValue),
            expression = exprDisplay
        ) to newInternal
    }

    /**
     * Evaluates the queued operation and shows the result.
     */
    fun onEquals(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onEqualsConstruction(state, internal)

        val right = if (internal.pendingInput.isNotEmpty()) {
            internal.pendingInput.toDoubleOrNull() ?: 0.0
        } else {
            internal.leftOperand ?: 0.0
        }
        val left = internal.leftOperand ?: right

        val result = evaluate(left, internal.pendingOperator, right)
        val resultStr = formatNumber(result)

        val exprLine = "${formatNumber(left)} ${internal.pendingOperator ?: ""} ${formatNumber(right)} ="

        val newInternal = InternalState(
            leftOperand = result,
            pendingOperator = null,
            pendingInput = resultStr,
            justEvaluated = true
        )
        return state.copy(display = resultStr, expression = exprLine) to newInternal
    }

    /**
     * Clears all state (AC button).
     */
    fun onClear(state: CalculatorState): Pair<CalculatorState, InternalState> =
        state.copy(display = "0", expression = "") to InternalState()

    /**
     * Deletes the last character of the current input.
     */
    fun onBackspace(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onBackspaceConstruction(state, internal)
        if (internal.justEvaluated) return onClear(state)

        val trimmed = internal.pendingInput.dropLast(1)
        val newPending = if (trimmed.isEmpty() || trimmed == "-") "0" else trimmed
        val newInternal = internal.copy(pendingInput = newPending)
        return state.copy(display = newPending) to newInternal
    }

    /**
     * Converts the current display value to its percentage (÷ 100).
     */
    fun onPercent(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onPercentConstruction(state, internal)
        val value = (state.display.toDoubleOrNull() ?: 0.0) / 100.0
        val str = formatNumber(value)
        return state.copy(display = str) to internal.copy(pendingInput = str, justEvaluated = false)
    }

    /**
     * Flips the sign of the current display value.
     */
    fun onSignFlip(state: CalculatorState, internal: InternalState): Pair<CalculatorState, InternalState> {
        if (state.mode == CalculatorMode.CONSTRUCTION) return onSignFlipConstruction(state, internal)
        val value = (state.display.toDoubleOrNull() ?: 0.0) * -1.0
        val str = formatNumber(value)
        return state.copy(display = str) to internal.copy(pendingInput = str, justEvaluated = false)
    }

    // ── formatting ────────────────────────────────────────────────────────────

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
            // Up to 10 significant digits, strip trailing zeros.
            val raw = String.format(java.util.Locale.US, "%.10g", value)
            raw.trimEnd('0').trimEnd('.')
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

    // Private helper for building expression display line.
    private fun InternalState.expressionLine(updated: InternalState): String {
        val op = pendingOperator ?: return ""
        val left = leftOperand ?: return ""
        return "${formatNumber(left)} $op"
    }
}

/**
 * Mutable arithmetic context that travels alongside [CalculatorState].
 *
 * Kept separate from [CalculatorState] because it is not meaningful to the UI
 * on its own — the UI only reads [CalculatorState.display] and
 * [CalculatorState.expression]. The ViewModel holds both objects together.
 */
data class InternalState(
    /** The accumulated left operand, null when starting fresh. */
    val leftOperand: Double? = null,
    /** The queued operator symbol ("+" / "−" / "×" / "÷"), null when none queued. */
    val pendingOperator: String? = null,
    /** Characters typed for the current (right) operand. Empty means use [leftOperand]. */
    val pendingInput: String = "",
    /** True immediately after "=" so the next digit starts a fresh expression. */
    val justEvaluated: Boolean = false
)
