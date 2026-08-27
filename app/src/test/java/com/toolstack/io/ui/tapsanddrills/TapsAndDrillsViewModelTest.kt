package com.toolstack.io.ui.tapsanddrills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapsAndDrillsViewModelTest {

    @Test
    fun `initial state shows all threads and correct category counts`() {
        val viewModel = TapsAndDrillsViewModel()
        val state = viewModel.uiState.value

        assertEquals(111, state.displayedThreads.size)
        assertEquals(111, state.categoryCounts[null])
        assertEquals(23, state.categoryCounts["UNC"])
        assertEquals(24, state.categoryCounts["UNF"])
        assertEquals(16, state.categoryCounts["UNS"])
        assertEquals(3, state.categoryCounts["UNEF"])
        assertEquals(31, state.categoryCounts["METRIC"])
        assertEquals(14, state.categoryCounts["PIPE"])
    }

    @Test
    fun `selecting metric category shows only metric threads`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onCategorySelected("METRIC")

        val state = viewModel.uiState.value
        assertEquals(31, state.displayedThreads.size)
        assertTrue(state.displayedThreads.all { it.standard == "METRIC" })
    }

    @Test
    fun `searching by designation filters threads`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onSearchQueryChanged("M6")

        val state = viewModel.uiState.value
        assertTrue(state.displayedThreads.all { it.designation.contains("M6") })
        assertEquals(1, state.displayedThreads.size)
    }

    @Test
    fun `searching by tap drill size filters threads`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onSearchQueryChanged("5mm")

        val state = viewModel.uiState.value
        assertTrue(state.displayedThreads.all { it.tapDrill75.contains("5mm") })
        assertTrue(state.displayedThreads.isNotEmpty())
    }

    @Test
    fun `spark plug only filter shows only spark plug threads`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onSparkPlugOnlyChanged(true)

        val state = viewModel.uiState.value
        assertEquals(5, state.displayedThreads.size)
        assertTrue(state.displayedThreads.all { it.isSparkPlug })
    }

    @Test
    fun `combined filters apply correctly`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onCategorySelected("METRIC")
        viewModel.onSparkPlugOnlyChanged(true)

        val state = viewModel.uiState.value
        assertEquals(4, state.displayedThreads.size)
        assertTrue(state.displayedThreads.all { it.standard == "METRIC" && it.isSparkPlug })
        assertFalse(state.displayedThreads.any { it.designation == "7/8-18" })
    }

    @Test
    fun `clearing search restores all threads in selected category`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onCategorySelected("UNC")
        viewModel.onSearchQueryChanged("1/4")
        viewModel.onSearchQueryChanged("")

        val state = viewModel.uiState.value
        assertEquals(23, state.displayedThreads.size)
        assertTrue(state.displayedThreads.all { it.standard == "UNC" })
    }

    @Test
    fun `initial visible columns include all six columns`() {
        val viewModel = TapsAndDrillsViewModel()

        val state = viewModel.uiState.value
        assertEquals(6, state.visibleColumns.size)
        assertTrue(state.visibleColumns.containsAll(
            listOf("thread", "pitch", "major", "tap", "decimal", "fractional")
        ))
    }

    @Test
    fun `toggling a column hides it`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onColumnToggled("major")

        val state = viewModel.uiState.value
        assertFalse(state.visibleColumns.contains("major"))
        assertEquals(5, state.visibleColumns.size)
        assertEquals(111, state.displayedThreads.size)
    }

    @Test
    fun `toggling a hidden column shows it`() {
        val viewModel = TapsAndDrillsViewModel()

        viewModel.onColumnToggled("major")
        viewModel.onColumnToggled("major")

        val state = viewModel.uiState.value
        assertTrue(state.visibleColumns.contains("major"))
        assertEquals(6, state.visibleColumns.size)
    }

    @Test
    fun `toggling the last visible column is ignored`() {
        val viewModel = TapsAndDrillsViewModel()

        listOf("pitch", "major", "tap", "decimal", "fractional").forEach {
            viewModel.onColumnToggled(it)
        }
        viewModel.onColumnToggled("thread")

        val state = viewModel.uiState.value
        assertEquals(setOf("thread"), state.visibleColumns)
    }
}
