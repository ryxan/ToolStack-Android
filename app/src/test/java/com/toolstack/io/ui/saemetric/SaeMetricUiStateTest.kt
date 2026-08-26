package com.toolstack.io.ui.saemetric

import com.toolstack.io.domain.calculator.SaeMetricCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaeMetricUiStateTest {

    @Test
    fun `displayed entries include all entries when show only common is off`() {
        val entries = SaeMetricCalculator.generate(1)
        val uiState = SaeMetricUiState(entries = entries, showOnlyCommon = false)

        assertEquals(entries.size, uiState.displayedEntries.size)
    }

    @Test
    fun `displayed entries filter to common sizes when show only common is on`() {
        val entries = SaeMetricCalculator.generate(1)
        val uiState = SaeMetricUiState(entries = entries, showOnlyCommon = true)
        val expected = entries.filter { it.isCommon }

        assertEquals(expected.size, uiState.displayedEntries.size)
        assertTrue(uiState.displayedEntries.all { it.isCommon })
    }
}
