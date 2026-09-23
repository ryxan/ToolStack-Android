package com.toolstack.io.ui.sprayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SprayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SprayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SprayerViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val uiState = viewModel.uiState.value
        assertEquals(SprayerMode.RATE_PER_ACRE, uiState.mode)
        assertEquals("", uiState.tankVolumeText)
        assertEquals("", uiState.sprayRateText)
        assertEquals("", uiState.chemRateText)
        assertEquals("", uiState.acresPerJugText)
        assertEquals("", uiState.totalAcresText)
    }

    @Test
    fun `rate per acre mode calculates total acres and chemical amounts`() {
        viewModel.onTankVolumeChanged("100")
        viewModel.onSprayRateChanged("10")
        viewModel.onChemRateChanged("2.5")

        val uiState = viewModel.uiState.value
        assertEquals("10.00", uiState.totalAcresText)
        assertEquals("25.00", uiState.totalChemLitersText)
        assertEquals("6.60", uiState.totalChemGallonsText)
        assertEquals("", uiState.totalJugsText)
    }

    @Test
    fun `acres per jug mode calculates total jugs`() {
        viewModel.onTankVolumeChanged("100")
        viewModel.onSprayRateChanged("10")
        viewModel.onModeToggled()
        viewModel.onAcresPerJugChanged("2.5")

        val uiState = viewModel.uiState.value
        assertEquals(SprayerMode.ACRES_PER_JUG, uiState.mode)
        assertEquals("10.00", uiState.totalAcresText)
        assertEquals("4.00", uiState.totalJugsText)
        assertEquals("", uiState.totalChemLitersText)
        assertEquals("", uiState.totalChemGallonsText)
    }

    @Test
    fun `mode toggle switches between modes`() {
        assertEquals(SprayerMode.RATE_PER_ACRE, viewModel.uiState.value.mode)

        viewModel.onModeToggled()
        assertEquals(SprayerMode.ACRES_PER_JUG, viewModel.uiState.value.mode)

        viewModel.onModeToggled()
        assertEquals(SprayerMode.RATE_PER_ACRE, viewModel.uiState.value.mode)
    }

    @Test
    fun `zero spray rate produces no results`() {
        viewModel.onTankVolumeChanged("100")
        viewModel.onSprayRateChanged("0")
        viewModel.onChemRateChanged("2.5")

        val uiState = viewModel.uiState.value
        assertEquals("", uiState.totalAcresText)
        assertEquals("", uiState.totalChemLitersText)
    }

    @Test
    fun `invalid inputs produce no results`() {
        viewModel.onTankVolumeChanged("invalid")
        viewModel.onSprayRateChanged("10")
        viewModel.onChemRateChanged("2.5")

        val uiState = viewModel.uiState.value
        assertEquals("", uiState.totalAcresText)
    }

    @Test
    fun `liters to gallons conversion is accurate`() {
        viewModel.onTankVolumeChanged("100")
        viewModel.onSprayRateChanged("10")
        viewModel.onChemRateChanged("10")

        val uiState = viewModel.uiState.value
        // 10 acres * 10 L/acre = 100 L
        // 100 L * 0.264172 = 26.4172 gallons
        assertEquals("100.00", uiState.totalChemLitersText)
        assertEquals("26.42", uiState.totalChemGallonsText)
    }

    @Test
    fun `results update when switching modes`() {
        // Start in rate per acre mode
        viewModel.onTankVolumeChanged("100")
        viewModel.onSprayRateChanged("10")
        viewModel.onChemRateChanged("2.5")

        val initialState = viewModel.uiState.value
        assertTrue(initialState.totalChemLitersText.isNotEmpty())
        assertEquals("", initialState.totalJugsText)

        // Switch to acres per jug mode
        viewModel.onModeToggled()
        viewModel.onAcresPerJugChanged("5")

        val switchedState = viewModel.uiState.value
        assertEquals("", switchedState.totalChemLitersText)
        assertEquals("", switchedState.totalChemGallonsText)
        assertTrue(switchedState.totalJugsText.isNotEmpty())
    }
}
