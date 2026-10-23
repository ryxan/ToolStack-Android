package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.SaeMetricEntry
import java.util.Locale
import kotlin.math.abs

/**
 * Generates the SAE to Metric conversion table in 1/64" steps.
 *
 * The base table always runs from 1/64" through 1". Extended ranges append rows
 * beyond 1" up to [maxInches] using mixed-number labels.
 */
object SaeMetricCalculator {

    private const val MM_PER_INCH = 25.4
    private const val STEPS_PER_INCH = 64

    // Common SAE wrench/drill sizes in 1/16" increments (4/64 steps).
    private val COMMON_BELOW_ONE = (4..64 step 4).toSet()

    // Same 1/16" increments as remainders after a whole number of inches.
    private val COMMON_ABOVE_ONE = (0..60 step 4).toSet()

    // Standard metric wrench sizes (across flats, mm), ISO 272 preferred series.
    private val WRENCH_SIZES = doubleArrayOf(
        3.2, 4.0, 5.0, 5.5, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0,
        15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0,
        27.0, 28.0, 29.0, 30.0, 32.0, 34.0, 36.0, 38.0, 41.0, 46.0, 50.0, 55.0,
        60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0, 105.0, 110.0,
        115.0, 120.0, 125.0, 130.0, 135.0, 140.0, 145.0, 150.0, 155.0, 160.0,
        165.0, 170.0, 175.0, 180.0, 185.0, 190.0, 195.0, 200.0, 210.0, 220.0,
        230.0, 240.0, 250.0
    )

    // Wrench sizes found in most common metric wrench sets.
    val COMMON_WRENCH_SIZES = setOf(
        6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0,
        18.0, 19.0, 21.0, 22.0, 24.0, 27.0, 30.0, 32.0
    )

    // A wrench is suggested only when within half a millimetre or 5% of the bolt.
    private const val WRENCH_ABS_TOLERANCE = 0.5
    private const val WRENCH_REL_TOLERANCE = 0.05

    fun generate(maxInches: Int): List<SaeMetricEntry> {
        require(maxInches in listOf(1, 2, 3, 5, 10)) {
            "Supported ranges are 1, 2, 3, 5, and 10 inches."
        }

        val entries = mutableListOf<SaeMetricEntry>()
        val maxSteps = maxInches * STEPS_PER_INCH

        for (step in 1..maxSteps) {
            val decimal = step / STEPS_PER_INCH.toDouble()
            val metric = decimal * MM_PER_INCH
            val nearestWrench = nearestWrenchSize(metric)
            entries.add(
                SaeMetricEntry(
                    fractionLabel = formatFraction(step, maxInches > 1),
                    decimalInch = decimal,
                    metricMm = metric,
                    isCommon = isCommon(step, maxInches),
                    decimalDisplay = roundMetricForDisplay(decimal, 4),
                    metricDisplay = roundMetricForDisplay(metric, 3),
                    nearestWrenchMm = nearestWrench,
                    nearestWrenchDisplay = nearestWrench?.let { formatWrenchSize(it) } ?: "—"
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

    /**
     * Closest standard metric wrench size for [mm], or null when every real
     * wrench is too far off to sensibly suggest (tiny fasteners, or the gaps
     * between large wrench sizes).
     */
    fun nearestWrenchSize(mm: Double): Double? {
        val nearest = WRENCH_SIZES.minByOrNull { abs(it - mm) } ?: return null
        val tolerance = maxOf(WRENCH_ABS_TOLERANCE, mm * WRENCH_REL_TOLERANCE)
        return if (abs(nearest - mm) <= tolerance) nearest else null
    }

    fun isCommonWrench(mm: Double?): Boolean =
        mm != null && mm in COMMON_WRENCH_SIZES

    private fun formatWrenchSize(mm: Double): String =
        if (mm % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", mm)
        } else {
            String.format(Locale.US, "%.1f", mm)
        }
}
