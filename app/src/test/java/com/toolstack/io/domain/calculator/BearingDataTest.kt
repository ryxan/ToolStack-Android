package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BearingDataTest {

    private val testBearings = BearingData.fromJson(
        """
        [
          {"designation": "6205", "type": "Single-row deep groove ball bearing", "boreMm": 25.0, "odMm": 52.0, "widthMm": 15.0, "series": "6200"},
          {"designation": "6310", "type": "Single-row deep groove ball bearing", "boreMm": 50.0, "odMm": 110.0, "widthMm": 27.0, "series": "6300"},
          {"designation": "6405", "type": "Single-row deep groove ball bearing", "boreMm": 25.0, "odMm": 80.0, "widthMm": 21.0, "series": "6400"},
          {"designation": "6320", "type": "Single-row deep groove ball bearing", "boreMm": 100.0, "odMm": 215.0, "widthMm": 47.0, "series": "6300"},
          {"designation": "6420", "type": "Single-row deep groove ball bearing", "boreMm": 100.0, "odMm": 250.0, "widthMm": 58.0, "series": "6400"},
          {"designation": "4205", "type": "Double-row deep groove ball bearing", "boreMm": 25.0, "odMm": 52.0, "widthMm": 18.0, "series": "4200"},
          {"designation": "4305", "type": "Double-row deep groove ball bearing", "boreMm": 25.0, "odMm": 62.0, "widthMm": 24.0, "series": "4300"}
        ]
        """.trimIndent()
    )

    @Test
    fun `findByDimensions returns exact match for 6205`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = 25.0,
            odMm = 52.0,
            widthMm = 15.0
        )

        assertEquals(1, results.size)
        assertEquals("6205", results.first().designation)
    }

    @Test
    fun `findByDimensions returns empty for unknown dimensions`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = 1.0,
            odMm = 1.0,
            widthMm = 1.0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByDimensions with tolerance matches worn bearing`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = 25.08,
            odMm = 51.92,
            widthMm = 15.05,
            toleranceMm = 0.5
        )

        assertTrue(results.isNotEmpty())
        assertEquals("6205", results.first().designation)
    }

    @Test
    fun `findByDimensions rejects negative dimensions`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = -25.0,
            odMm = 52.0,
            widthMm = 15.0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByDimensions rejects zero dimensions`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = 0.0,
            odMm = 52.0,
            widthMm = 15.0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByDimensions rejects NaN dimensions`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = Double.NaN,
            odMm = 52.0,
            widthMm = 15.0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByDimensions rejects infinite dimensions`() {
        val results = BearingData.findByDimensions(
            bearings = testBearings,
            boreMm = Double.POSITIVE_INFINITY,
            odMm = 52.0,
            widthMm = 15.0
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `areValidDimensions returns false for non positive and non finite values`() {
        assertTrue(BearingData.areValidDimensions(1.0, 2.0, 3.0))
        assertFalse(BearingData.areValidDimensions(-1.0, 2.0, 3.0))
        assertFalse(BearingData.areValidDimensions(0.0, 2.0, 3.0))
        assertFalse(BearingData.areValidDimensions(1.0, Double.NaN, 3.0))
        assertFalse(BearingData.areValidDimensions(1.0, 2.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `fromJson parses the full catalog asset`() {
        val assetFile = java.io.File("src/main/assets/bearings.json")
        val assetJson = if (assetFile.exists()) {
            assetFile.readText()
        } else {
            javaClass.classLoader?.getResource("assets/bearings.json")?.readText()
                ?: error("Could not load bearings.json from test classpath or file path")
        }

        val bearings = BearingData.fromJson(assetJson)

        assertTrue(bearings.isNotEmpty())
        assertTrue(bearings.any { it.designation == "6205" })
        assertTrue(bearings.any { it.designation == "6320" })
        assertTrue(bearings.any { it.designation == "6420" })
        assertTrue(bearings.any { it.designation == "4205" })
        assertTrue(bearings.any { it.designation == "4320" })
        assertTrue(bearings.any { it.type == "Double-row deep groove ball bearing" })
    }

    @Test
    fun `fromJson catalog contains thin-section, narrow, and wide variants`() {
        val assetFile = java.io.File("src/main/assets/bearings.json")
        val assetJson = if (assetFile.exists()) {
            assetFile.readText()
        } else {
            javaClass.classLoader?.getResource("assets/bearings.json")?.readText()
                ?: error("Could not load bearings.json from test classpath or file path")
        }

        val bearings = BearingData.fromJson(assetJson)

        assertTrue(bearings.any { it.designation == "16005" })
        assertTrue(bearings.any { it.designation == "16101" })
        assertTrue(bearings.any { it.designation == "61701" })
        assertTrue(bearings.any { it.designation == "61805" })
        assertTrue(bearings.any { it.designation == "61905" })
        assertTrue(bearings.any { it.designation == "603" })
        assertTrue(bearings.any { it.designation == "62200" })
        assertTrue(bearings.any { it.designation == "62300" })
        assertTrue(bearings.any { it.designation == "62000" })
        assertTrue(bearings.any { it.designation == "63000" })
    }
}
