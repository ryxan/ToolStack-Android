package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.BendInput
import com.toolstack.io.domain.model.BendResult
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConduitBendCalculatorTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Corner 90° Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `corner90 - basic calculation with half inch conduit`() {
        val input = BendInput.Corner90(
            distanceToCornerInches = 24.0,
            conduitSize = ConduitSize.HALF
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.Corner90

        assertEquals(24.0, result.distanceToCornerInches, 0.0001)
        assertEquals(5.0, result.takeOffInches, 0.0001)
        assertEquals(19.0, result.markLocationInches, 0.0001)
    }

    @Test
    fun `corner90 - all conduit sizes have correct takeOff`() {
        val testCases = mapOf(
            ConduitSize.HALF to 5.0,
            ConduitSize.THREE_QTR to 6.0,
            ConduitSize.ONE to 8.0,
            ConduitSize.ONE_QTR to 11.0,
            ConduitSize.ONE_HALF to 14.25
        )

        testCases.forEach { (size, expectedTakeOff) ->
            val input = BendInput.Corner90(30.0, size)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Corner90

            assertEquals("Wrong takeOff for $size", expectedTakeOff, result.takeOffInches, 0.0001)
            assertEquals("Wrong mark for $size", 30.0 - expectedTakeOff, result.markLocationInches, 0.0001)
        }
    }

    @Test
    fun `corner90 - rejects distance shorter than takeOff`() {
        val input = BendInput.Corner90(
            distanceToCornerInches = 4.5,
            conduitSize = ConduitSize.HALF  // takeOff is 5.0
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("shorter than the take-off"))
    }

    @Test
    fun `corner90 - rejects distance equal to takeOff`() {
        val input = BendInput.Corner90(
            distanceToCornerInches = 5.0,
            conduitSize = ConduitSize.HALF  // takeOff is 5.0
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("shorter than the take-off"))
    }

    @Test
    fun `corner90 - rejects zero distance`() {
        val input = BendInput.Corner90(0.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `corner90 - rejects negative distance`() {
        val input = BendInput.Corner90(-10.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Stub-Up 90° Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `stubUp90 - basic calculation with 12 inch stub`() {
        val input = BendInput.StubUp90(
            stubLengthInches = 12.0,
            conduitSize = ConduitSize.HALF
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.StubUp90

        assertEquals(5.0, result.takeOffInches, 0.0001)
        assertEquals(7.0, result.markLocationInches, 0.0001)
    }

    @Test
    fun `stubUp90 - all conduit sizes calculate correctly`() {
        val testCases = mapOf(
            ConduitSize.HALF to Pair(5.0, 15.0),        // takeOff, mark for 20" stub
            ConduitSize.THREE_QTR to Pair(6.0, 14.0),
            ConduitSize.ONE to Pair(8.0, 12.0),
            ConduitSize.ONE_QTR to Pair(11.0, 9.0),
            ConduitSize.ONE_HALF to Pair(14.25, 5.75)
        )

        testCases.forEach { (size, expected) ->
            val (expectedTakeOff, expectedMark) = expected
            val input = BendInput.StubUp90(20.0, size)
            val result = ConduitBendCalculator.calculate(input) as BendResult.StubUp90

            assertEquals("Wrong takeOff for $size", expectedTakeOff, result.takeOffInches, 0.0001)
            assertEquals("Wrong mark for $size", expectedMark, result.markLocationInches, 0.0001)
        }
    }

    @Test
    fun `stubUp90 - minimum valid stub height per size`() {
        // Minimum is just above the takeOff value
        val testCases = listOf(
            BendInput.StubUp90(5.1, ConduitSize.HALF),
            BendInput.StubUp90(6.1, ConduitSize.THREE_QTR),
            BendInput.StubUp90(8.1, ConduitSize.ONE),
            BendInput.StubUp90(11.1, ConduitSize.ONE_QTR),
            BendInput.StubUp90(14.3, ConduitSize.ONE_HALF)
        )

        testCases.forEach { input ->
            val result = ConduitBendCalculator.calculate(input) as BendResult.StubUp90
            assert(result.markLocationInches > 0) { "Mark should be positive for ${input.conduitSize}" }
        }
    }

    @Test
    fun `stubUp90 - rejects stub shorter than takeOff`() {
        val input = BendInput.StubUp90(4.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("shorter than the take-off"))
    }

    @Test
    fun `stubUp90 - half inch vs one-and-half inch comparison`() {
        val halfInch = ConduitBendCalculator.calculate(
            BendInput.StubUp90(20.0, ConduitSize.HALF)
        ) as BendResult.StubUp90

        val oneHalfInch = ConduitBendCalculator.calculate(
            BendInput.StubUp90(20.0, ConduitSize.ONE_HALF)
        ) as BendResult.StubUp90

        // Larger conduit has larger takeOff, so mark is closer to end
        assert(halfInch.markLocationInches > oneHalfInch.markLocationInches)
        assertEquals(9.25, halfInch.markLocationInches - oneHalfInch.markLocationInches, 0.0001)
    }

    @Test
    fun `stubUp90 - rejects zero stub length`() {
        val input = BendInput.StubUp90(0.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `stubUp90 - rejects negative stub length`() {
        val input = BendInput.StubUp90(-5.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Offset Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `offset - 45 degree standard case`() {
        val input = BendInput.Offset(
            obstructionHeightInches = 6.0,
            conduitSize = ConduitSize.HALF,
            angle = OffsetAngle.DEG_45
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.Offset

        // 6 × 1.414 = 8.484
        assertEquals(8.484, result.bendSpacingInches, 0.01)
        // 6 × 0.375 = 2.25
        assertEquals(2.25, result.shrinkageInches, 0.01)
        assertEquals(0.0, result.mark1Inches, 0.0001)
        assertEquals(8.484, result.mark2Inches, 0.01)
        assertEquals(OffsetAngle.DEG_45, result.angleUsed)
    }

    @Test
    fun `offset - all angles produce correct multipliers`() {
        val testCases = mapOf(
            OffsetAngle.DEG_10 to 5.76,
            OffsetAngle.DEG_22 to 2.6,
            OffsetAngle.DEG_30 to 2.0,
            OffsetAngle.DEG_45 to 1.414,
            OffsetAngle.DEG_60 to 1.155
        )

        testCases.forEach { (angle, expectedMultiplier) ->
            val input = BendInput.Offset(10.0, ConduitSize.HALF, angle)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Offset

            val expectedSpacing = 10.0 * expectedMultiplier
            assertEquals(
                "Wrong spacing for ${angle.degrees}°",
                expectedSpacing,
                result.bendSpacingInches,
                0.01
            )
        }
    }

    @Test
    fun `offset - shrinkage calculation for each angle`() {
        val testCases = mapOf(
            OffsetAngle.DEG_10 to 0.0625,
            OffsetAngle.DEG_22 to 0.1875,
            OffsetAngle.DEG_30 to 0.25,
            OffsetAngle.DEG_45 to 0.375,
            OffsetAngle.DEG_60 to 0.5
        )

        testCases.forEach { (angle, shrinkPerInch) ->
            val input = BendInput.Offset(8.0, ConduitSize.HALF, angle)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Offset

            val expectedShrinkage = 8.0 * shrinkPerInch
            assertEquals(
                "Wrong shrinkage for ${angle.degrees}°",
                expectedShrinkage,
                result.shrinkageInches,
                0.01
            )
        }
    }

    @Test
    fun `offset - bend spacing increases with height`() {
        val heights = listOf(2.0, 4.0, 6.0, 8.0, 10.0)
        val results = heights.map { height ->
            val input = BendInput.Offset(height, ConduitSize.HALF, OffsetAngle.DEG_45)
            ConduitBendCalculator.calculate(input) as BendResult.Offset
        }

        // Verify spacing increases proportionally
        for (i in 1 until results.size) {
            assert(results[i].bendSpacingInches > results[i - 1].bendSpacingInches) {
                "Spacing should increase with height"
            }
        }
    }

    @Test
    fun `offset - 10 degree vs 60 degree comparison`() {
        val deg10 = ConduitBendCalculator.calculate(
            BendInput.Offset(5.0, ConduitSize.HALF, OffsetAngle.DEG_10)
        ) as BendResult.Offset

        val deg60 = ConduitBendCalculator.calculate(
            BendInput.Offset(5.0, ConduitSize.HALF, OffsetAngle.DEG_60)
        ) as BendResult.Offset

        // Shallow angle (10°) needs wider spacing than steep angle (60°)
        assert(deg10.bendSpacingInches > deg60.bendSpacingInches)
        // Steep angle has more shrinkage
        assert(deg60.shrinkageInches > deg10.shrinkageInches)
    }

    @Test
    fun `offset - rejects zero height`() {
        val input = BendInput.Offset(0.0, ConduitSize.HALF, OffsetAngle.DEG_45)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `offset - rejects negative height`() {
        val input = BendInput.Offset(-3.0, ConduitSize.HALF, OffsetAngle.DEG_45)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3-Point Saddle Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `saddle3Point - basic calculation`() {
        val input = BendInput.Saddle3Point(
            obstructionHeightInches = 4.0,
            conduitSize = ConduitSize.HALF
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle3Point

        assertEquals(0.0, result.centerMarkInches, 0.0001)
        assertEquals(10.0, result.outerMarkOffsetInches, 0.0001)  // 4 × 2.5
        assertEquals(0.75, result.shrinkageInches, 0.0001)       // 4 × 0.1875
    }

    @Test
    fun `saddle3Point - outer spread is height times 2_5`() {
        val heights = listOf(2.0, 4.0, 6.0, 8.0)

        heights.forEach { height ->
            val input = BendInput.Saddle3Point(height, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle3Point

            assertEquals(height * 2.5, result.outerMarkOffsetInches, 0.0001)
        }
    }

    @Test
    fun `saddle3Point - shrinkage is height times 0_1875`() {
        val heights = listOf(2.0, 4.0, 6.0, 8.0)

        heights.forEach { height ->
            val input = BendInput.Saddle3Point(height, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle3Point

            assertEquals(height * 0.1875, result.shrinkageInches, 0.0001)
        }
    }

    @Test
    fun `saddle3Point - various obstruction heights`() {
        val testCases = mapOf(
            2.0 to Pair(5.0, 0.375),   // height to (outerSpread, shrinkage)
            3.0 to Pair(7.5, 0.5625),
            5.0 to Pair(12.5, 0.9375),
            8.0 to Pair(20.0, 1.5)
        )

        testCases.forEach { (height, expected) ->
            val (expectedSpread, expectedShrinkage) = expected
            val input = BendInput.Saddle3Point(height, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle3Point

            assertEquals("Wrong spread for $height\"", expectedSpread, result.outerMarkOffsetInches, 0.0001)
            assertEquals("Wrong shrinkage for $height\"", expectedShrinkage, result.shrinkageInches, 0.0001)
        }
    }

    @Test
    fun `saddle3Point - rejects zero height`() {
        val input = BendInput.Saddle3Point(0.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `saddle3Point - rejects negative height`() {
        val input = BendInput.Saddle3Point(-2.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4-Point Saddle Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `saddle4Point - basic calculation`() {
        val input = BendInput.Saddle4Point(
            obstructionHeightInches = 5.0,
            conduitSize = ConduitSize.HALF
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle4Point

        assertEquals(0.0, result.mark1Inches, 0.0001)
        assertEquals(10.0, result.mark2Inches, 0.0001)   // 5 × 2.0
        assertEquals(20.0, result.mark3Inches, 0.0001)   // 5 × 2.0 × 2
        assertEquals(30.0, result.mark4Inches, 0.0001)   // 5 × 2.0 × 3
        assertEquals(1.25, result.shrinkageInches, 0.0001) // 5 × 0.25
    }

    @Test
    fun `saddle4Point - marks equally spaced at height times 2`() {
        val heights = listOf(3.0, 4.0, 6.0)

        heights.forEach { height ->
            val input = BendInput.Saddle4Point(height, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle4Point

            val expectedSpacing = height * 2.0
            assertEquals(0.0, result.mark1Inches, 0.0001)
            assertEquals(expectedSpacing, result.mark2Inches, 0.0001)
            assertEquals(expectedSpacing * 2, result.mark3Inches, 0.0001)
            assertEquals(expectedSpacing * 3, result.mark4Inches, 0.0001)
        }
    }

    @Test
    fun `saddle4Point - shrinkage is height times 0_25`() {
        val heights = listOf(2.0, 4.0, 6.0, 8.0)

        heights.forEach { height ->
            val input = BendInput.Saddle4Point(height, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle4Point

            assertEquals(height * 0.25, result.shrinkageInches, 0.0001)
        }
    }

    @Test
    fun `saddle4Point - mark progression validation`() {
        val input = BendInput.Saddle4Point(3.0, ConduitSize.HALF)
        val result = ConduitBendCalculator.calculate(input) as BendResult.Saddle4Point

        // Marks should be in ascending order
        assert(result.mark1Inches < result.mark2Inches)
        assert(result.mark2Inches < result.mark3Inches)
        assert(result.mark3Inches < result.mark4Inches)

        // Spacing should be uniform
        val spacing1 = result.mark2Inches - result.mark1Inches
        val spacing2 = result.mark3Inches - result.mark2Inches
        val spacing3 = result.mark4Inches - result.mark3Inches
        assertEquals(spacing1, spacing2, 0.0001)
        assertEquals(spacing2, spacing3, 0.0001)
    }

    @Test
    fun `saddle4Point - rejects zero height`() {
        val input = BendInput.Saddle4Point(0.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `saddle4Point - rejects negative height`() {
        val input = BendInput.Saddle4Point(-1.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Back-to-Back 90° Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `backToBack - basic calculation`() {
        val input = BendInput.BackToBack(
            backDistanceInches = 18.0,
            conduitSize = ConduitSize.HALF
        )
        val result = ConduitBendCalculator.calculate(input) as BendResult.BackToBack

        assertEquals(13.0, result.firstMarkInches, 0.0001)   // 18 - 5
        assertEquals(31.0, result.secondMarkInches, 0.0001)  // 13 + 18
    }

    @Test
    fun `backToBack - second mark is first plus distance`() {
        val distances = listOf(12.0, 15.0, 20.0, 24.0)

        distances.forEach { distance ->
            val input = BendInput.BackToBack(distance, ConduitSize.HALF)
            val result = ConduitBendCalculator.calculate(input) as BendResult.BackToBack

            val expectedSecond = result.firstMarkInches + distance
            assertEquals(
                "Second mark should be first + distance for $distance\"",
                expectedSecond,
                result.secondMarkInches,
                0.0001
            )
        }
    }

    @Test
    fun `backToBack - all conduit sizes`() {
        val testCases = mapOf(
            ConduitSize.HALF to Pair(15.0, 35.0),          // first, second for 20" distance
            ConduitSize.THREE_QTR to Pair(14.0, 34.0),
            ConduitSize.ONE to Pair(12.0, 32.0),
            ConduitSize.ONE_QTR to Pair(9.0, 29.0),
            ConduitSize.ONE_HALF to Pair(5.75, 25.75)
        )

        testCases.forEach { (size, expected) ->
            val (expectedFirst, expectedSecond) = expected
            val input = BendInput.BackToBack(20.0, size)
            val result = ConduitBendCalculator.calculate(input) as BendResult.BackToBack

            assertEquals("Wrong first mark for $size", expectedFirst, result.firstMarkInches, 0.0001)
            assertEquals("Wrong second mark for $size", expectedSecond, result.secondMarkInches, 0.0001)
        }
    }

    @Test
    fun `backToBack - rejects distance shorter than takeOff`() {
        val input = BendInput.BackToBack(4.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("shorter than the take-off"))
    }

    @Test
    fun `backToBack - rejects zero distance`() {
        val input = BendInput.BackToBack(0.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    @Test
    fun `backToBack - rejects negative distance`() {
        val input = BendInput.BackToBack(-10.0, ConduitSize.HALF)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConduitBendCalculator.calculate(input)
        }
        assert(exception.message!!.contains("must be positive"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Formatting Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `formatInches - whole numbers`() {
        assertEquals("0\"", ConduitBendCalculator.formatInches(0.0))
        assertEquals("1\"", ConduitBendCalculator.formatInches(1.0))
        assertEquals("12\"", ConduitBendCalculator.formatInches(12.0))
        assertEquals("100\"", ConduitBendCalculator.formatInches(100.0))
    }

    @Test
    fun `formatInches - common fractions half`() {
        assertEquals("1/2\"", ConduitBendCalculator.formatInches(0.5))
        assertEquals("5 1/2\"", ConduitBendCalculator.formatInches(5.5))
    }

    @Test
    fun `formatInches - common fractions quarter`() {
        assertEquals("1/4\"", ConduitBendCalculator.formatInches(0.25))
        assertEquals("3/4\"", ConduitBendCalculator.formatInches(0.75))
        assertEquals("10 1/4\"", ConduitBendCalculator.formatInches(10.25))
    }

    @Test
    fun `formatInches - common fractions eighth`() {
        assertEquals("1/8\"", ConduitBendCalculator.formatInches(0.125))
        assertEquals("3/8\"", ConduitBendCalculator.formatInches(0.375))
        assertEquals("5/8\"", ConduitBendCalculator.formatInches(0.625))
        assertEquals("7/8\"", ConduitBendCalculator.formatInches(0.875))
    }

    @Test
    fun `formatInches - sixteenth fractions`() {
        assertEquals("1/16\"", ConduitBendCalculator.formatInches(0.0625))
        assertEquals("3/16\"", ConduitBendCalculator.formatInches(0.1875))
        assertEquals("5/16\"", ConduitBendCalculator.formatInches(0.3125))
        assertEquals("13/16\"", ConduitBendCalculator.formatInches(0.8125))
    }

    @Test
    fun `formatInches - mixed numbers with fractions`() {
        assertEquals("13 3/8\"", ConduitBendCalculator.formatInches(13.375))
        assertEquals("7 5/16\"", ConduitBendCalculator.formatInches(7.3125))
        assertEquals("24 7/8\"", ConduitBendCalculator.formatInches(24.875))
    }

    @Test
    fun `formatInches - rounds to nearest 16th`() {
        // 0.06 is closer to 1/16 (0.0625) than to 0
        assertEquals("1/16\"", ConduitBendCalculator.formatInches(0.06))
        // 0.19 is closer to 3/16 (0.1875) than to 1/8 (0.125)
        assertEquals("3/16\"", ConduitBendCalculator.formatInches(0.19))
    }

    @Test
    fun `formatInches - carries overflow when rounding up`() {
        // 5.9999 rounds to 6.0
        assertEquals("6\"", ConduitBendCalculator.formatInches(5.9999))
        // 12.96875 (12 + 15.5/16) rounds to 13
        assertEquals("13\"", ConduitBendCalculator.formatInches(12.96875))
    }

    @Test
    fun `formatInches - reduces fractions to lowest terms`() {
        // 8/16 reduces to 1/2
        assertEquals("1/2\"", ConduitBendCalculator.formatInches(0.5))
        // 4/16 reduces to 1/4
        assertEquals("1/4\"", ConduitBendCalculator.formatInches(0.25))
        // 12/16 reduces to 3/4
        assertEquals("3/4\"", ConduitBendCalculator.formatInches(0.75))
    }

    @Test
    fun `formatInches - professional field measurements`() {
        // Real-world stub-up example: 12" - 5" takeOff
        assertEquals("7\"", ConduitBendCalculator.formatInches(7.0))
        // Offset spacing: 6 × 1.414
        assertEquals("8 1/2\"", ConduitBendCalculator.formatInches(8.484))
        // Back-to-back: 18 - 5
        assertEquals("13\"", ConduitBendCalculator.formatInches(13.0))
    }

    @Test
    fun `formatDecimal - precision to 3 places`() {
        assertEquals("13.375\"", ConduitBendCalculator.formatDecimal(13.375))
        assertEquals("8.484\"", ConduitBendCalculator.formatDecimal(8.484))
        assertEquals("1.414\"", ConduitBendCalculator.formatDecimal(1.414))
    }

    @Test
    fun `formatDecimal - adds inch symbol`() {
        val result = ConduitBendCalculator.formatDecimal(10.5)
        assert(result.endsWith("\""))
    }

    @Test
    fun `formatDecimal - rounds to 3 decimal places`() {
        assertEquals("1.235\"", ConduitBendCalculator.formatDecimal(1.23456789))
    }

    @Test
    fun `formatMillimeters - conversion accuracy`() {
        // 1 inch = 25.4 mm exactly
        assertEquals("25 mm", ConduitBendCalculator.formatMillimeters(1.0))
        // 10 inches = 254 mm
        assertEquals("254 mm", ConduitBendCalculator.formatMillimeters(10.0))
        // 0.5 inch = 12.7 mm
        assertEquals("13 mm", ConduitBendCalculator.formatMillimeters(0.5))
    }

    @Test
    fun `formatMillimeters - rounds large values to integer`() {
        val result = ConduitBendCalculator.formatMillimeters(20.0)
        assert(!result.contains(".")) { "Large values should be rounded to integer" }
        assertEquals("508 mm", result)
    }

    @Test
    fun `formatMillimeters - shows decimal for small values`() {
        val result = ConduitBendCalculator.formatMillimeters(0.25)
        assert(result.contains(".")) { "Small values should show decimal" }
        assertEquals("6.4 mm", result)
    }

    @Test
    fun `formatMillimeters - common conduit measurements`() {
        // 1/2" = 12.7 mm
        assertEquals("13 mm", ConduitBendCalculator.formatMillimeters(0.5))
        // 3/4" = 19.05 mm
        assertEquals("19 mm", ConduitBendCalculator.formatMillimeters(0.75))
        // 1" = 25.4 mm
        assertEquals("25 mm", ConduitBendCalculator.formatMillimeters(1.0))
    }
}
