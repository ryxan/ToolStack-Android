@file:OptIn(ExperimentalCoroutinesApi::class)
package com.toolstack.io.ui.unitconverter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.UnitConverterData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class UnitConverterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val weightCategory = UnitConverterData.categories.first { it.name == "Mass" }
    private val ounceUnit = weightCategory.units.first { it.label == "Ounce" }
    private val poundUnit = weightCategory.units.first { it.label == "Pound" }

    @Test
    fun `default units loaded when no saved preference exists`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val viewModel = UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            assertEquals("Pound", viewModel.uiState.value.fromUnit.label)
            assertEquals("Kilogram", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `saved units restored on launch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            repository.saveConverterUnits(weightCategory.name, "Ounce", "Pound")

            val viewModel = UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            assertEquals("Ounce", viewModel.uiState.value.fromUnit.label)
            assertEquals("Pound", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `legacy category name Weight slash Mass migrates to Mass`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            // Save with legacy name
            repository.saveConverterUnits("Weight / Mass", "Ounce", "Pound")

            val viewModel = UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            assertEquals("Ounce", viewModel.uiState.value.fromUnit.label)
            assertEquals("Pound", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `legacy category name Fuel Economy migrates to Fuel`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val fuelCategory = UnitConverterData.categories.first { it.name == "Fuel" }
            // Save with legacy name
            repository.saveConverterUnits("Fuel Economy", "mpg (US)", "L/100km")

            val viewModel = UnitConverterViewModel(fuelCategory, repository)
            advanceUntilIdle()

            assertEquals("mpg (US)", viewModel.uiState.value.fromUnit.label)
            assertEquals("L/100km", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `legacy category name Data Size migrates to Data`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val dataCategory = UnitConverterData.categories.first { it.name == "Data" }
            // Save with legacy name
            repository.saveConverterUnits("Data Size", "Byte", "Kilobyte")

            val viewModel = UnitConverterViewModel(dataCategory, repository)
            advanceUntilIdle()

            assertEquals("Byte", viewModel.uiState.value.fromUnit.label)
            assertEquals("Kilobyte", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `current category name takes precedence over legacy name`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            // Save with both legacy and current names (current should win)
            repository.saveConverterUnits("Weight / Mass", "Ounce", "Pound")
            repository.saveConverterUnits("Mass", "Gram", "Kilogram")

            val viewModel = UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            // Should use the current name's values
            assertEquals("Gram", viewModel.uiState.value.fromUnit.label)
            assertEquals("Kilogram", viewModel.uiState.value.toUnit.label)
        }

    @Test
    fun `selecting units persists to repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val viewModel = UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            viewModel.onFromUnitSelected(ounceUnit)
            viewModel.onToUnitSelected(poundUnit)
            advanceUntilIdle()

            val savedUnits = repository.converterUnits.first()[weightCategory.name]
            assertEquals(Pair("Ounce", "Pound"), savedUnits)
        }

    @Test
    fun `lastConverterCategory recorded on init`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            UnitConverterViewModel(weightCategory, repository)
            advanceUntilIdle()

            assertEquals("Mass", repository.lastConverterCategory.first())
        }

    @Test
    fun `user selection before saved preference read completes is preserved`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            repository.saveConverterUnits(weightCategory.name, "Kilogram", "Tonne (metric)")

            val viewModel = UnitConverterViewModel(weightCategory, repository)
            // User immediately selects Ounce before advanceUntilIdle() processes the read
            viewModel.onFromUnitSelected(ounceUnit)
            advanceUntilIdle()

            assertEquals("Ounce", viewModel.uiState.value.fromUnit.label)
        }

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val _data = MutableStateFlow(initial)
        override val data: Flow<Preferences> = _data.asStateFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val newPreferences = transform(_data.value)
            _data.value = newPreferences
            return newPreferences
        }
    }
}

