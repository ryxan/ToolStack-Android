package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.BendInput
import com.toolstack.io.domain.model.BendResult
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle

/**
 * Pure stateless calculator for conduit bend geometry.
 *
 * All inputs and outputs are in inches. No Android or Room dependencies —
 * this object is cheap to unit test directly.
 *
 * Formula sources:
 *  - 90° Stub-Up: mark = stubLength − takeOff (bender-specific constant per conduit size)
 *  - Offset: spacing = H × (1/sin θ); shrinkage = H × shrinkConstant
 *  - 3-Point Saddle: outer spread = H × 2.5 from centre; shrinkage = H × 3/16
 *  - 4-Point Saddle: equal spacing = H × 2.0 between marks; shrinkage = H × 1/4
 *  - Back-to-Back: first mark = backDistance − takeOff; second = first + backDistance
 */
object ConduitBendCalculator {

    /**
     * Dispatch to the correct formula for the given input type.
     * Throws [IllegalArgumentException] for negative or zero measurements
     * (callers should validate before calling, but this acts as a safety net).
     */
    fun calculate(input: BendInput): BendResult = when (input) {
        is BendInput.Corner90     -> calculateCorner90(input)
        is BendInput.StubUp90     -> calculateStubUp90(input)
        is BendInput.Offset       -> calculateOffset(input)
        is BendInput.Saddle3Point -> calculateSaddle3Point(input)
        is BendInput.Saddle4Point -> calculateSaddle4Point(input)
        is BendInput.BackToBack   -> calculateBackToBack(input)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 90° Corner
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Standard horizontal corner bend — no elevation change.
     *
     * The user measures from the end of the pipe to the outside corner point
     * (where they want the bend to land on the wall). The bender arrow goes at:
     *
     *   markLocation = distanceToCorner − takeOff
     *
     * Place the bender arrow on the mark, heel of the bender toward the long
     * run, and bend to 90°.
     */
    private fun calculateCorner90(input: BendInput.Corner90): BendResult.Corner90 {
        require(input.distanceToCornerInches > 0) { "Distance to corner must be positive" }
        val takeOff = input.conduitSize.takeOff90Inches
        val mark = input.distanceToCornerInches - takeOff
        require(mark > 0) {
            "Distance to corner (${input.distanceToCornerInches}\") is shorter than the " +
                "take-off (${takeOff}\") for ${input.conduitSize.label} conduit."
        }
        return BendResult.Corner90(
            takeOffInches        = takeOff,
            markLocationInches   = mark
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 90° Stub-Up
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The bender "takes off" a fixed amount from the stub measurement because
     * the pipe travels through an arc. The take-off constant varies by conduit
     * size and is stamped on most hand benders.
     *
     * markLocation = stubLength − takeOff
     *
     * Place the bender arrow at markLocation from the end of the pipe.
     * Bend until the bender reads 90° (or the stub stop pin engages).
     */
    private fun calculateStubUp90(input: BendInput.StubUp90): BendResult.StubUp90 {
        require(input.stubLengthInches > 0) { "Stub length must be positive" }
        val takeOff = input.conduitSize.takeOff90Inches
        val markLocation = input.stubLengthInches - takeOff
        require(markLocation > 0) {
            "Stub length (${input.stubLengthInches}\") is shorter than the take-off " +
                "(${takeOff}\") for ${input.conduitSize.label} conduit."
        }
        return BendResult.StubUp90(
            takeOffInches       = takeOff,
            markLocationInches  = markLocation
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Offset
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Two bends of equal angle in opposite directions clear an obstacle of
     * height H.
     *
     *   bendSpacing = H × multiplier          (centre-to-centre between marks)
     *   shrinkage   = H × shrinkPerInch        (pipe is effectively shorter after bending)
     *   mark1       = 0 (reference point from the end of the pipe — caller adjusts)
     *   mark2       = mark1 + bendSpacing
     *
     * The first bend goes "up" (arrow toward the work); the second goes "down"
     * (shoe or star toward the work) to bring the pipe back parallel.
     */
    private fun calculateOffset(input: BendInput.Offset): BendResult.Offset {
        require(input.obstructionHeightInches > 0) { "Obstruction height must be positive" }
        val h       = input.obstructionHeightInches
        val angle   = input.angle
        val spacing = h * angle.multiplier
        val shrink  = h * angle.shrinkPerInch
        return BendResult.Offset(
            bendSpacingInches = spacing,
            shrinkageInches   = shrink,
            mark1Inches       = 0.0,   // relative; user places mark1 where offset starts
            mark2Inches       = spacing,
            angleUsed         = angle
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3-Point Saddle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Centre bend is 45°; two outer bends are 22.5° each (22° on standard benders).
     *
     *   centre mark         = user-positioned at the middle of the obstacle
     *   outer mark offset   = H × 2.5 each side of centre mark
     *   shrinkage           = H × 3/16 (0.1875)
     */
    private fun calculateSaddle3Point(input: BendInput.Saddle3Point): BendResult.Saddle3Point {
        require(input.obstructionHeightInches > 0) { "Obstruction height must be positive" }
        val h = input.obstructionHeightInches
        return BendResult.Saddle3Point(
            centerMarkInches       = 0.0,       // user marks the centre themselves
            outerMarkOffsetInches  = h * 2.5,
            shrinkageInches        = h * 0.1875
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4-Point Saddle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Four 30° bends, equally spaced.
     *
     *   spacing between each bend = H × 2.0
     *   shrinkage                 = H × 0.25
     *
     * mark1 is at 0 (reference), marks 2–4 follow at even spacing intervals.
     */
    private fun calculateSaddle4Point(input: BendInput.Saddle4Point): BendResult.Saddle4Point {
        require(input.obstructionHeightInches > 0) { "Obstruction height must be positive" }
        val h       = input.obstructionHeightInches
        val spacing = h * 2.0
        return BendResult.Saddle4Point(
            mark1Inches     = 0.0,
            mark2Inches     = spacing,
            mark3Inches     = spacing * 2,
            mark4Inches     = spacing * 3,
            shrinkageInches = h * 0.25
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Back-to-Back 90°
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Two 90° bends facing opposite directions (like a U-channel run).
     * [backDistanceInches] is the desired centre-to-centre distance between
     * the two 90° stubs (measured on the finished install).
     *
     *   firstMark  = backDistance − takeOff
     *   secondMark = firstMark + backDistance
     *
     * Make the first bend at firstMark, then measure backDistance along the
     * bent pipe from the heel of the first bend and mark secondMark there.
     */
    private fun calculateBackToBack(input: BendInput.BackToBack): BendResult.BackToBack {
        require(input.backDistanceInches > 0) { "Back distance must be positive" }
        val takeOff    = input.conduitSize.takeOff90Inches
        val firstMark  = input.backDistanceInches - takeOff
        require(firstMark > 0) {
            "Back distance (${input.backDistanceInches}\") is shorter than the take-off " +
                "(${takeOff}\") for ${input.conduitSize.label} conduit."
        }
        val secondMark = firstMark + input.backDistanceInches
        return BendResult.BackToBack(
            firstMarkInches  = firstMark,
            secondMarkInches = secondMark
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Formatting helpers (used by the screen layer via the ViewModel)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Formats an inch value as a mixed-number fraction string, e.g.
     * 13.375 → "13 3/8\"".  Denominator is limited to 16ths for readability.
     * Values that don't land on a 1/16 boundary are rounded to the nearest 16th.
     */
    fun formatInches(value: Double): String {
        val whole       = value.toInt()
        val remainder   = value - whole
        val sixteenths  = (remainder * 16).toInt()  // truncate to nearest 1/16
        return when {
            sixteenths == 0  -> if (whole == 0) "0\"" else "$whole\""
            else             -> {
                val (num, den) = reduceFraction(sixteenths, 16)
                if (whole == 0) "$num/$den\"" else "$whole $num/$den\""
            }
        }
    }

    /** Returns a decimal-inch string rounded to 3 places, e.g. "13.375\"". */
    fun formatDecimal(value: Double): String =
        String.format(java.util.Locale.US, "%.3f\"", value)

    private fun reduceFraction(num: Int, den: Int): Pair<Int, Int> {
        val g = gcd(num, den)
        return Pair(num / g, den / g)
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
