package com.toolstack.io.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val saeMetricMaxInches: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_SAE_METRIC_MAX_INCHES] ?: DEFAULT_MAX_INCHES
    }

    suspend fun saveSaeMetricMaxInches(maxInches: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_SAE_METRIC_MAX_INCHES] = maxInches
        }
    }

    companion object {
        private val KEY_SAE_METRIC_MAX_INCHES = intPreferencesKey("sae_metric_max_inches")
        private const val DEFAULT_MAX_INCHES = 1
    }
}
