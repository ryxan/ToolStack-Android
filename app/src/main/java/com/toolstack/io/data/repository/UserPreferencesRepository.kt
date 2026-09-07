package com.toolstack.io.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    val saeMetricShowOnlyCommon: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SAE_METRIC_SHOW_ONLY_COMMON] ?: DEFAULT_SHOW_ONLY_COMMON
    }

    suspend fun saveSaeMetricShowOnlyCommon(showOnlyCommon: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SAE_METRIC_SHOW_ONLY_COMMON] = showOnlyCommon
        }
    }

    /**
     * Persisted order for the home screen module list. Stored as a comma-separated
     * string of route names (e.g. "sae_metric,wrench_fastener,unit_converter,…").
     * An empty/missing value means use the default declaration order.
     */
    val homeModuleOrder: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[KEY_HOME_MODULE_ORDER]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun saveHomeModuleOrder(orderedRoutes: List<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_HOME_MODULE_ORDER] = orderedRoutes.joinToString(",")
        }
    }

    /**
     * Persisted order for the unit-converter category list. Stored as a
     * comma-separated string of category names.
     * An empty/missing value means use the default declaration order.
     */
    val converterCategoryOrder: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[KEY_CONVERTER_CATEGORY_ORDER]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun saveConverterCategoryOrder(orderedNames: List<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_CONVERTER_CATEGORY_ORDER] = orderedNames.joinToString(",")
        }
    }

    companion object {
        private val KEY_SAE_METRIC_MAX_INCHES = intPreferencesKey("sae_metric_max_inches")
        private const val DEFAULT_MAX_INCHES = 1

        private val KEY_SAE_METRIC_SHOW_ONLY_COMMON = booleanPreferencesKey("sae_metric_show_only_common")
        private const val DEFAULT_SHOW_ONLY_COMMON = false

        private val KEY_HOME_MODULE_ORDER = stringPreferencesKey("home_module_order")
        private val KEY_CONVERTER_CATEGORY_ORDER = stringPreferencesKey("converter_category_order")
    }
}
