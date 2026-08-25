package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaeMetricCalculatorTest {

    @Test
    fun `base 1 inch range has 64 entries`() {
        val entries = SaeMetricCalculator.generate(1)
        assertEquals(64, entries.size)
    }

    @Test
    fun `10 inch range has 640 entries`() {
        val entries = SaeMetricCalculator.generate(10)
        assertEquals(640, entries.size)
    }

    @Test
    fun `common sizes are marked and less common sizes are not`() {
        val entries = SaeMetricCalculator.generate(1)
        val quarter = entries.first { it.fractionLabel == "1/4" }
        val half = entries.first { it.fractionLabel == "1/2" }
        val threeQuarter = entries.first { it.fractionLabel == "3/4" }

        assertTrue("1/4 should be common", quarter.isCommon)
        assertTrue("1/2 should be common", half.isCommon)
        assertTrue("3/4 should be common", threeQuarter.isCommon)

        val fiveSixtyFourth = entries.first { it.fractionLabel == "5/64" }
        assertTrue("5/64 should not be common", !fiveSixtyFourth.isCommon)
    }

    @Test
    fun `conversion uses exact 25_4 mm per inch`() {
        val entries = SaeMetricCalculator.generate(1)
        val one = entries.last()
        assertEquals("1", one.fractionLabel)
        assertEquals(1.0, one.decimalInch, 0.0000001)
        assertEquals(25.4, one.metricMm, 0.0000001)
    }

    @Test
    fun `5 sixteenths converts to 7_9375 mm`() {
        val entries = SaeMetricCalculator.generate(1)
        val entry = entries.first { it.fractionLabel == "5/16" }
        assertEquals(0.3125, entry.decimalInch, 0.0000001)
        assertEquals(7.9375, entry.metricMm, 0.0000001)
    }

    @Test
    fun `above one inch uses mixed number labels`() {
        val entries = SaeMetricCalculator.generate(2)
        val oneAndQuarter = entries.first { it.fractionLabel == "1-1/4" }
        assertEquals(1.25, oneAndQuarter.decimalInch, 0.0000001)
        assertEquals(31.75, oneAndQuarter.metricMm, 0.0000001)
    }

    @Test
    fun `display formatting rounds to 3 decimal places`() {
        val oneMm = 25.4
        assertEquals("25.400", SaeMetricCalculator.roundMetricForDisplay(oneMm, 3))
        val fiveSixteenthsMm = 7.9375
        assertEquals("7.938", SaeMetricCalculator.roundMetricForDisplay(fiveSixteenthsMm, 3))
    }

    @Test
    fun `entries have precomputed display strings`() {
        val entries = SaeMetricCalculator.generate(1)

        val oneSixtyFourth = entries.first()
        assertEquals("1/64", oneSixtyFourth.fractionLabel)
        assertEquals("0.0156", oneSixtyFourth.decimalDisplay)
        assertEquals("0.397", oneSixtyFourth.metricDisplay)

        val one = entries.last()
        assertEquals("1.0000", one.decimalDisplay)
        assertEquals("25.400", one.metricDisplay)

        val fiveSixteenths = entries.first { it.fractionLabel == "5/16" }
        assertEquals("0.3125", fiveSixteenths.decimalDisplay)
        assertEquals("7.938", fiveSixteenths.metricDisplay)
    }
}
