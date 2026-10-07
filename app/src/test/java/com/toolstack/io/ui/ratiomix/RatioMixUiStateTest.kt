package com.toolstack.io.ui.ratiomix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatioMixUiStateTest {

    private fun state(
        vararg ratioTexts: String,
        volume: String = "",
        mode: Mode = Mode.TOTAL_TO_PARTS,
        knownPartIndex: Int = 0,
        unit: VolumeUnit = VolumeUnit.LITRES
    ) = RatioMixUiState(
        parts = ratioTexts.map { RatioPart(label = "P", ratioText = it) },
        totalVolumeText = volume,
        mode = mode,
        knownPartIndex = knownPartIndex,
        selectedUnit = unit
    ).recalculate()

    @Test
    fun `total to parts splits volume by ratio`() {
        val s = state("3", "1", volume = "40")
        assertEquals(listOf("30 L", "10 L"), s.results.map { it.volumeText })
        assertEquals("3 : 1", s.ratioSummary)
        assertFalse(s.hasError)
    }

    @Test
    fun `part to total derives total from known part`() {
        val s = state("3", "1", volume = "30", mode = Mode.PART_TO_TOTAL)
        assertEquals(listOf("30 L", "10 L"), s.results.map { it.volumeText })
        assertEquals("40 L", s.totalResultText)
    }

    @Test
    fun `part to total honours known part index`() {
        // known 10 on the "1" part of a 3:1 mix → 30/10, total 40
        val s = state("3", "1", volume = "10", mode = Mode.PART_TO_TOTAL, knownPartIndex = 1)
        assertEquals(listOf("30 L", "10 L"), s.results.map { it.volumeText })
        assertEquals("40 L", s.totalResultText)
    }

    @Test
    fun `out of range known part index is clamped`() {
        val s = state("3", "1", volume = "10", mode = Mode.PART_TO_TOTAL, knownPartIndex = 5)
        // clamped to index 1 → same as previous test
        assertEquals(listOf("30 L", "10 L"), s.results.map { it.volumeText })
    }

    @Test
    fun `invalid ratio flags the field and clears results`() {
        val s = state("3", "abc", volume = "40")
        assertTrue(s.hasError)
        assertTrue(s.results.isEmpty())
        assertFalse(s.parts[0].ratioError)
        assertTrue(s.parts[1].ratioError)
    }

    @Test
    fun `zero negative and blank ratios are errors`() {
        assertTrue(state("0", "1", volume = "40").parts[0].ratioError)
        assertTrue(state("-2", "1", volume = "40").parts[0].ratioError)
        assertTrue(state("", "1", volume = "40").parts[0].ratioError)
    }

    @Test
    fun `comma decimal ratios parse`() {
        val s = state("3,5", "0,5", volume = "40")
        assertFalse(s.hasError)
        assertEquals(listOf("35 L", "5 L"), s.results.map { it.volumeText })
    }

    @Test
    fun `ratio summary echoes input without rounding`() {
        val s = state("0.001", "1", volume = "40")
        assertEquals("0.001 : 1", s.ratioSummary)
    }

    @Test
    fun `non-blank invalid volume sets volumeError`() {
        val s = state("3", "1", volume = "-5")
        assertTrue(s.volumeError)
        assertTrue(s.results.isEmpty())
        assertFalse(s.hasError)
    }

    @Test
    fun `blank volume is not an error`() {
        val s = state("3", "1", volume = "")
        assertFalse(s.volumeError)
        assertTrue(s.results.isEmpty())
    }

    @Test
    fun `tiny result volumes show floor marker instead of zero`() {
        // 0.0000002 L total at 1:1 → 0.0001 mL per part after downshifting
        val s = state("1", "1", volume = "0.0000002")
        assertEquals(listOf("<0.001 mL", "<0.001 mL"), s.results.map { it.volumeText })
    }

    @Test
    fun `sub-litre results downshift to millilitres`() {
        // 0.5 L total at 1:1 → 0.25 L per part → shown as 250 mL
        val s = state("1", "1", volume = "0.5")
        assertEquals(listOf("250 mL", "250 mL"), s.results.map { it.volumeText })
    }

    @Test
    fun `sub-gallon results downshift through US units`() {
        // 1 gal at 3:1 → 0.75 gal = 3 qt and 0.25 gal = 1 qt
        val s = state("3", "1", volume = "1", unit = VolumeUnit.US_GALLONS)
        assertEquals(listOf("3 qt", "1 qt"), s.results.map { it.volumeText })
    }

    @Test
    fun `very small US results reach fl oz`() {
        // 1 gal at 127:1 → the small part is 1/128 gal = exactly 1 fl oz
        val s = state("127", "1", volume = "1", unit = VolumeUnit.US_GALLONS)
        assertEquals("1 fl oz", s.results[1].volumeText)
    }

    @Test
    fun `results show per-part percentage`() {
        val s = state("3", "1", volume = "40")
        assertEquals(listOf("75%", "25%"), s.results.map { it.percentText })
    }

    @Test
    fun `fractional percentages keep one decimal`() {
        val s = state("199", "1", volume = "200")
        assertEquals("99.5%", s.results[0].percentText)
        assertEquals("0.5%", s.results[1].percentText)
    }

    @Test
    fun `results respect selected unit`() {
        val s = state("3", "1", volume = "4", unit = VolumeUnit.US_QUARTS)
        // 4 qt = 3.78541 L; 3:1 → 2.83906/0.94635 L → back in qt: 3/1
        assertEquals(listOf("3 qt", "1 qt"), s.results.map { it.volumeText })
    }
}
