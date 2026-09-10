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

    /**
     * Persisted ratio-mix presets. Each preset is a named list of (label, ratioText) pairs
     * defined by the user. Stored as newline-separated records; each record has the format:
     *
     *   presetName\tLabel1\uFFFEratioText1\tLabel2\uFFFEratioText2...
     *
     * '\t' separates the preset name from the parts, and parts from each other.
     * '\uFFFE' separates a part's label from its ratio value. The separator U+FFFE
     * (non-character) is unlikely to appear in user-entered text and avoids collisions
     * with common punctuation.
     *
     * An empty/missing value means no presets have been saved.
     */
    val ratioMixPresets: Flow<List<RatioMixPreset>> = dataStore.data.map { preferences ->
        preferences[KEY_RATIO_MIX_PRESETS]
            ?.let { decodeRatioMixPresets(it) }
            ?: emptyList()
    }

    suspend fun saveRatioMixPreset(preset: RatioMixPreset) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_RATIO_MIX_PRESETS]
                ?.let { decodeRatioMixPresets(it) }
                ?.toMutableList()
                ?: mutableListOf()
            // Replace an existing preset with the same name, otherwise append.
            val existingIndex = current.indexOfFirst { it.name == preset.name }
            if (existingIndex >= 0) current[existingIndex] = preset else current.add(preset)
            preferences[KEY_RATIO_MIX_PRESETS] = encodeRatioMixPresets(current)
        }
    }

    suspend fun deleteRatioMixPreset(presetName: String) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_RATIO_MIX_PRESETS]
                ?.let { decodeRatioMixPresets(it) }
                ?.toMutableList()
                ?: return@edit
            current.removeAll { it.name == presetName }
            preferences[KEY_RATIO_MIX_PRESETS] = encodeRatioMixPresets(current)
        }
    }

    companion object {
        private val KEY_SAE_METRIC_MAX_INCHES = intPreferencesKey("sae_metric_max_inches")
        private const val DEFAULT_MAX_INCHES = 1

        private val KEY_SAE_METRIC_SHOW_ONLY_COMMON = booleanPreferencesKey("sae_metric_show_only_common")
        private const val DEFAULT_SHOW_ONLY_COMMON = false

        private val KEY_HOME_MODULE_ORDER = stringPreferencesKey("home_module_order")
        private val KEY_CONVERTER_CATEGORY_ORDER = stringPreferencesKey("converter_category_order")
        private val KEY_RATIO_MIX_PRESETS = stringPreferencesKey("ratio_mix_presets")

        /** Separator between a part's label and its ratio value. U+FFFE is a non-character. */
        private const val PART_SEP = "\uFFFE"

        private fun encodeRatioMixPresets(presets: List<RatioMixPreset>): String =
            presets.joinToString("\n") { preset ->
                val partsEncoded = preset.parts.joinToString("\t") { (label, ratio) ->
                    "${label.replace(PART_SEP, "")}$PART_SEP${ratio.replace(PART_SEP, "")}"
                }
                "${preset.name.replace("\n", " ").replace("\t", " ")}\t$partsEncoded"
            }

        private fun decodeRatioMixPresets(encoded: String): List<RatioMixPreset> =
            encoded.split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { record ->
                    val tokens = record.split("\t")
                    if (tokens.size < 2) return@mapNotNull null
                    val name = tokens[0]
                    val parts = tokens.drop(1).mapNotNull { partToken ->
                        val idx = partToken.indexOf(PART_SEP)
                        if (idx < 0) null
                        else RatioMixPresetPart(
                            label = partToken.substring(0, idx),
                            ratioText = partToken.substring(idx + PART_SEP.length)
                        )
                    }
                    if (parts.isEmpty()) null
                    else RatioMixPreset(name = name, parts = parts)
                }
    }
}

/** A named ratio-mix preset the user has saved. */
data class RatioMixPreset(
    val name: String,
    val parts: List<RatioMixPresetPart>
)

/** One ingredient entry within a saved preset. */
data class RatioMixPresetPart(
    val label: String,
    val ratioText: String
)
