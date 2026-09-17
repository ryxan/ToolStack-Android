@file:OptIn(ExperimentalCoroutinesApi::class)
package com.toolstack.io.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPreferencesRepositoryConverterTest {

    @Test
    fun `lastConverterCategory returns null by default`() = runTest {
        val dataStore = FakeDataStore()
        val repository = UserPreferencesRepository(dataStore)

        assertNull(repository.lastConverterCategory.first())
    }

    @Test
    fun `lastConverterCategory persists and retrieves category name`() = runTest {
        val dataStore = FakeDataStore()
        val repository = UserPreferencesRepository(dataStore)

        repository.saveLastConverterCategory("Mass")
        assertEquals("Mass", repository.lastConverterCategory.first())

        repository.saveLastConverterCategory("Length")
        assertEquals("Length", repository.lastConverterCategory.first())
    }

    @Test
    fun `converterUnits returns empty map by default`() = runTest {
        val dataStore = FakeDataStore()
        val repository = UserPreferencesRepository(dataStore)

        assertEquals(emptyMap<String, Pair<String, String>>(), repository.converterUnits.first())
    }

    @Test
    fun `converterUnits saves and retrieves multiple categories independently`() = runTest {
        val dataStore = FakeDataStore()
        val repository = UserPreferencesRepository(dataStore)

        repository.saveConverterUnits("Mass", "Ounce", "Pound")
        repository.saveConverterUnits("Length", "Foot", "Metre")

        val units = repository.converterUnits.first()
        assertEquals(Pair("Ounce", "Pound"), units["Mass"])
        assertEquals(Pair("Foot", "Metre"), units["Length"])

        // Update Mass only
        repository.saveConverterUnits("Mass", "Gram", "Kilogram")
        val updatedUnits = repository.converterUnits.first()
        assertEquals(Pair("Gram", "Kilogram"), updatedUnits["Mass"])
        assertEquals(Pair("Foot", "Metre"), updatedUnits["Length"])
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

