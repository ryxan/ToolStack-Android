package com.toolstack.io.domain.model

/** Measurement system used for user-facing input/output in the Conduit Bends tool.
 *  Internal calculations always use inches; only formatting and parsing are affected. */
enum class UnitMode { IMPERIAL, METRIC }

/**
 * All bend types the Conduit Bends tool supports.
 * [isImplemented] gates whether the selection card is tappable or shown as "coming soon".
 */
enum class BendType(val displayName: String, val subtitle: String, val isImplemented: Boolean, val isVisible: Boolean = true) {
    CORNER_90(
        displayName   = "90° Corner",
        subtitle      = "Standard 90° bend around a corner",
        isImplemented = true,
        isVisible     = false   // same math as STUB_UP_90; hidden to avoid duplication
    ),
    STUB_UP_90(
        displayName    = "90° Bend (Stub-Up)",
        subtitle       = "Single 90° bend rising from the floor",
        isImplemented  = true
    ),
    OFFSET(
        displayName    = "True Offset",
        subtitle       = "Two equal bends to clear an obstruction",
        isImplemented  = true
    ),
    SADDLE_3_POINT(
        displayName    = "3-Point Saddle",
        subtitle       = "45° center, two 22½° outer bends",
        isImplemented  = false
    ),
    SADDLE_4_POINT(
        displayName    = "4-Point Saddle",
        subtitle       = "Four 30° bends over an obstacle",
        isImplemented  = false
    ),
    BACK_TO_BACK(
        displayName    = "Back-to-Back 90°",
        subtitle       = "Two opposing 90° bends",
        isImplemented  = false
    )
}

/**
 * EMT conduit sizes with their bender take-up constants.
 * Take-up values are for standard hand benders (Klein, Ideal, Greenlee).
 * These represent how much conduit the bend arc "uses up" from your measured stub height.
 */
enum class ConduitSize(val label: String, val takeOff90Inches: Double) {
    HALF("½\"",   5.0),
    THREE_QTR("¾\"",  6.0),
    ONE("1\"",    8.0),
    ONE_QTR("1¼\"", 11.0),
    ONE_HALF("1½\"", 14.25)
}

/**
 * Offset bend angles with their geometric multipliers and shrinkage constants.
 *
 * [multiplier]        = 1/sin(θ) — multiply by obstruction height to get center-to-center spacing.
 * [shrinkPerInch]     = how many inches of overall conduit length are lost per inch of rise height.
 */
enum class OffsetAngle(
    val degrees: Int,
    val multiplier: Double,
    val shrinkPerInch: Double
) {
    DEG_10(10, 5.76,  0.0625),
    DEG_22(22, 2.6,   0.1875),
    DEG_30(30, 2.0,   0.25),
    DEG_45(45, 1.414, 0.375),
    DEG_60(60, 1.155, 0.5)
}

// ──────────────────────────────────────────────────────────────────────────────
// Input models
// ──────────────────────────────────────────────────────────────────────────────

/** Validated, ready-to-calculate inputs for each bend type. */
sealed class BendInput {
    /**
     * Standard 90° corner bend (around a wall corner, no elevation change).
     * [distanceToCornerInches] is measured from the end of the pipe to the
     * desired outside corner point on the finished run.
     */
    data class Corner90(
        val distanceToCornerInches: Double,
        val conduitSize: ConduitSize
    ) : BendInput()

    data class StubUp90(
        val stubLengthInches: Double,
        val conduitSize: ConduitSize
    ) : BendInput()

    data class Offset(
        val obstructionHeightInches: Double,
        val conduitSize: ConduitSize,
        val angle: OffsetAngle
    ) : BendInput()

    data class Saddle3Point(
        val obstructionHeightInches: Double,
        val conduitSize: ConduitSize
    ) : BendInput()

    data class Saddle4Point(
        val obstructionHeightInches: Double,
        val conduitSize: ConduitSize
    ) : BendInput()

    data class BackToBack(
        val backDistanceInches: Double,
        val conduitSize: ConduitSize
    ) : BendInput()
}

// ──────────────────────────────────────────────────────────────────────────────
// Result models
// ──────────────────────────────────────────────────────────────────────────────

/** Calculated output for each bend type. All values are in inches. */
sealed class BendResult {
    /**
     * Standard 90° corner.
     * @param distanceToCornerInches The original user-entered distance from pipe end to outside corner.
     * @param takeOffInches          The bender's take-up for this conduit size.
     * @param markLocationInches     Where to place the bender arrow, measured from the
     *                               reference end of the pipe (distanceToCorner − takeUp).
     */
    data class Corner90(
        val distanceToCornerInches: Double,
        val takeOffInches: Double,
        val markLocationInches: Double
    ) : BendResult()

    /**
     * @param takeOffInches     The take-up amount subtracted from stub height.
     * @param markLocationInches Distance from the end of the pipe to place the bender arrow.
     */
    data class StubUp90(
        val takeOffInches: Double,
        val markLocationInches: Double
    ) : BendResult()

    /**
     * @param bendSpacingInches   Center-to-center distance between the two bend marks.
     * @param shrinkageInches     Amount to cut extra from the pipe (or compensate) due to shrinkage.
     * @param mark1Inches         First bend mark from the reference end (caller-supplied offset + 0).
     * @param mark2Inches         Second bend mark = mark1 + bendSpacing.
     * @param angleUsed           Angle that was selected, for display purposes.
     */
    data class Offset(
        val bendSpacingInches: Double,
        val shrinkageInches: Double,
        val mark1Inches: Double,
        val mark2Inches: Double,
        val angleUsed: OffsetAngle
    ) : BendResult()

    data class Saddle3Point(
        val centerMarkInches: Double,
        val outerMarkOffsetInches: Double,
        val shrinkageInches: Double
    ) : BendResult()

    data class Saddle4Point(
        val mark1Inches: Double,
        val mark2Inches: Double,
        val mark3Inches: Double,
        val mark4Inches: Double,
        val shrinkageInches: Double
    ) : BendResult()

    data class BackToBack(
        val firstMarkInches: Double,
        val secondMarkInches: Double
    ) : BendResult()
}
