package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.SaeMetricEntry
import java.util.Locale

/**
 * Generates the SAE to Metric conversion table in 1/64" steps.
 *
 * The base table always runs from 1/64" through 1". Extended ranges append rows
 * beyond 1" up to [maxInches] using mixed-number labels.
 */
object SaeMetricCalculator {

    private const val MM_PER_INCH = 25.4
    private const val STEPS_PER_INCH = 64

    private val COMMON_BELOW_ONE = setOf(
        1, 2, 4, 8, 10, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64
    )

    private val COMMON_ABOVE_ONE = setOf(
        0, 16, 32, 48
    )

    fun generate(maxInches: Int): List<SaeMetricEntry> {
        require(maxInches in listOf(1, 2, 3, 5, 10)) {
            "Supported ranges are 1, 2, 3, 5, and 10 inches."
        }

        val entries = mutableListOf<SaeMetricEntry>()
        val maxSteps = maxInches * STEPS_PER_INCH

        for (step in 1..maxSteps) {
            val decimal = step / STEPS_PER_INCH.toDouble()
            val metric = decimal * MM_PER_INCH
            entries.add(
                SaeMetricEntry(
                    fractionLabel = formatFraction(step, maxInches > 1),
                    decimalInch = decimal,
                    metricMm = metric,
                    isCommon = isCommon(step, maxInches),
                    decimalDisplay = roundMetricForDisplay(decimal, 4),
                    metricDisplay = roundMetricForDisplay(metric, 3)
                )
            )
        }

        return entries
    }

    private fun isCommon(step: Int, maxInches: Int): Boolean {
        if (maxInches == 1) {
            return step in COMMON_BELOW_ONE
        }
        val whole = step / STEPS_PER_INCH
        val remainder = step % STEPS_PER_INCH
        return if (whole >= 1) {
            remainder == 0 || remainder in COMMON_ABOVE_ONE
        } else {
            step in COMMON_BELOW_ONE
        }
    }

    private fun formatFraction(step: Int, canExceedOne: Boolean): String {
        val whole = step / STEPS_PER_INCH
        val remainder = step % STEPS_PER_INCH

        if (remainder == 0) {
            return whole.toString()
        }

        val simplified = simplify(remainder, STEPS_PER_INCH)
        val frac = "${simplified.first}/${simplified.second}"

        return if (canExceedOne && whole > 0) {
            "$whole-$frac"
        } else {
            frac
        }
    }

    private fun simplify(numerator: Int, denominator: Int): Pair<Int, Int> {
        val gcd = greatestCommonDivisor(numerator, denominator)
        return (numerator / gcd) to (denominator / gcd)
    }

    private fun greatestCommonDivisor(a: Int, b: Int): Int {
        return if (b == 0) a else greatestCommonDivisor(b, a % b)
    }

    fun roundMetricForDisplay(mm: Double, decimals: Int = 3): String {
        return String.format(Locale.US, "%.${decimals}f", mm)
    }
}
