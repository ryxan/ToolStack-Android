package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WrenchFastenerDataTest {

    @Test
    fun `sae entries contain common wrench to fastener mappings`() {
        val entries = WrenchFastenerData.saeEntries()

        val half = entries.first { it.wrenchLabel == "1/2\"" }
        assertEquals("5/16\"", half.fastenerLabel)

        val threeQuarters = entries.first { it.wrenchLabel == "3/4\"" }
        assertEquals("1/2\"", threeQuarters.fastenerLabel)

        val oneAndFiveSixteenths = entries.first { it.wrenchLabel == "1-5/16\"" }
        assertEquals("7/8\"", oneAndFiveSixteenths.fastenerLabel)
    }

    @Test
    fun `metric entries contain common wrench to fastener mappings`() {
        val entries = WrenchFastenerData.metricEntries()

        val thirteenMm = entries.first { it.wrenchLabel == "13 mm" }
        assertEquals("M8", thirteenMm.fastenerLabel)

        val sixteenMm = entries.first { it.wrenchLabel == "16 mm" }
        assertEquals("M10", sixteenMm.fastenerLabel)

        val thirtyMm = entries.first { it.wrenchLabel == "30 mm" }
        assertEquals("M20", thirtyMm.fastenerLabel)
    }

    @Test
    fun `sae and metric lists are ordered`() {
        assertTrue(WrenchFastenerData.saeEntries().isNotEmpty())
        assertTrue(WrenchFastenerData.metricEntries().isNotEmpty())
    }
}
