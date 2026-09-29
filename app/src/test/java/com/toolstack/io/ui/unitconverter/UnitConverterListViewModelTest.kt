package com.toolstack.io.ui.unitconverter

import com.toolstack.io.domain.calculator.UnitConverterData
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterListViewModelTest {

    private val allCategories = UnitConverterData.categories

    @Test
    fun `applyOrder returns defaults when savedNames is empty`() {
        val result = UnitConverterListViewModel.applyOrder(allCategories, emptyList())
        assertEquals(allCategories, result)
    }

    @Test
    fun `applyOrder preserves saved order`() {
        val savedNames = listOf("Data", "Mass", "Length")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        assertEquals("Data", result[0].name)
        assertEquals("Mass", result[1].name)
        assertEquals("Length", result[2].name)
    }

    @Test
    fun `applyOrder appends new categories not in saved order`() {
        val savedNames = listOf("Length", "Mass")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // First two match saved order
        assertEquals("Length", result[0].name)
        assertEquals("Mass", result[1].name)
        // Remaining categories are appended in default order
        assertEquals(allCategories.size, result.size)
    }

    @Test
    fun `applyOrder migrates legacy Weight slash Mass to Mass`() {
        val savedNames = listOf("Weight / Mass", "Length", "Area")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // Legacy name should map to current "Mass"
        assertEquals("Mass", result[0].name)
        assertEquals("Length", result[1].name)
        assertEquals("Area", result[2].name)
    }

    @Test
    fun `applyOrder migrates legacy Fuel Economy to Fuel`() {
        val savedNames = listOf("Fuel Economy", "Temperature", "Speed")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // Legacy name should map to current "Fuel"
        assertEquals("Fuel", result[0].name)
        assertEquals("Temperature", result[1].name)
        assertEquals("Speed", result[2].name)
    }

    @Test
    fun `applyOrder migrates legacy Data Size to Data`() {
        val savedNames = listOf("Data Size", "Time", "Power")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // Legacy name should map to current "Data"
        assertEquals("Data", result[0].name)
        assertEquals("Time", result[1].name)
        assertEquals("Power", result[2].name)
    }

    @Test
    fun `applyOrder migrates all three legacy names`() {
        val savedNames = listOf("Weight / Mass", "Fuel Economy", "Data Size", "Length")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        assertEquals("Mass", result[0].name)
        assertEquals("Fuel", result[1].name)
        assertEquals("Data", result[2].name)
        assertEquals("Length", result[3].name)
    }

    @Test
    fun `applyOrder drops categories that no longer exist`() {
        val savedNames = listOf("NonExistentCategory", "Mass", "Length")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // Should only contain valid categories
        assertEquals("Mass", result[0].name)
        assertEquals("Length", result[1].name)
        assertEquals(allCategories.size, result.size)
    }

    @Test
    fun `applyOrder deduplicates when both legacy and current name exist`() {
        // Edge case: both legacy and current name in saved list
        val savedNames = listOf("Mass", "Weight / Mass", "Length")
        val result = UnitConverterListViewModel.applyOrder(allCategories, savedNames)

        // Should only include Mass once (first occurrence after normalization)
        assertEquals("Mass", result[0].name)
        assertEquals("Length", result[1].name)
        // Mass should not appear twice
        assertEquals(allCategories.size, result.size)
    }
}
