@file:OptIn(ExperimentalCoroutinesApi::class)
package com.toolstack.io.ui.saemetric

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.toolstack.io.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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

private val saeMetricMaxInchesKey = intPreferencesKey("sae_metric_max_inches")
private val saeMetricShowOnlyCommonKey = booleanPreferencesKey("sae_metric_show_only_common")

class SaeMetricViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `range selected before saved preference is restored does not load outdated range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val viewModel = SaeMetricViewModel(repository)

            // Simulate a user selecting a range while the saved preference read is still in flight.
            viewModel.onRangeSelected(5)

            // Saved preference read resumes after the selection.
            val savedPreferences = preferencesOf(
                saeMetricMaxInchesKey to 10,
                saeMetricShowOnlyCommonKey to true
            )
            dataStore.emit(savedPreferences)
            dataStore.emit(savedPreferences)
            advanceUntilIdle()

            assertEquals(5, viewModel.uiState.value.maxInches)
            assertEquals(true, viewModel.uiState.value.showOnlyCommon)
        }

    @Test
    fun `saved range is restored when no selection occurs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val viewModel = SaeMetricViewModel(repository)

            val savedPreferences = preferencesOf(
                saeMetricMaxInchesKey to 10,
                saeMetricShowOnlyCommonKey to true
            )
            dataStore.emit(savedPreferences)
            dataStore.emit(savedPreferences)
            advanceUntilIdle()

            assertEquals(10, viewModel.uiState.value.maxInches)
            assertEquals(true, viewModel.uiState.value.showOnlyCommon)
        }

    @Test
    fun `first range selection equal to default is persisted and ignores restored range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dataStore = FakeDataStore()
            val repository = UserPreferencesRepository(dataStore)
            val viewModel = SaeMetricViewModel(repository)

            // Select the default range (1) before the saved preference read completes.
            viewModel.onRangeSelected(1)

            val savedPreferences = preferencesOf(
                saeMetricMaxInchesKey to 10,
                saeMetricShowOnlyCommonKey to true
            )
            dataStore.emit(savedPreferences)
            dataStore.emit(savedPreferences)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.maxInches)
            assertEquals(true, viewModel.uiState.value.showOnlyCommon)
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

    private class FakeDataStore : DataStore<Preferences> {
        private val channel = Channel<Preferences>(Channel.UNLIMITED)
        private var currentPreferences: Preferences = emptyPreferences()

        override val data: Flow<Preferences> = channel.receiveAsFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val newPreferences = transform(currentPreferences)
            currentPreferences = newPreferences
            return newPreferences
        }

        fun emit(preferences: Preferences) {
            channel.trySend(preferences).getOrThrow()
        }
    }
}
