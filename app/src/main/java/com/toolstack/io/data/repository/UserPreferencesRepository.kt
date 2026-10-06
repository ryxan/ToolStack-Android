package com.toolstack.io.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.toolstack.io.domain.calculator.CalculatorEngine
import com.toolstack.io.domain.model.SavedRecipe
import com.toolstack.io.domain.model.SavedRecipeIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Base64
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
     * Persisted hidden home screen modules. Stored as a comma-separated
     * string of route names (e.g. "calculator,sprayer").
     * An empty/missing value means no modules are hidden.
     */
    val hiddenHomeModules: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[KEY_HIDDEN_HOME_MODULES]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun saveHiddenHomeModules(hiddenRoutes: List<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_HIDDEN_HOME_MODULES] = hiddenRoutes.joinToString(",")
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
     *   B64(presetName)\tB64(Label1)\uFFFEB64(ratioText1)\tB64(Label2)\uFFFEB64(ratioText2)...
     *
     * Every field is Base64-encoded (URL-safe, no padding) so that tabs, newlines, and any
     * other characters in user-entered text cannot collide with the structural delimiters.
     * '\t' separates the preset name from the parts, and parts from each other.
     * '\uFFFE' separates a part's Base64-label from its Base64-ratio value.
     *
     * An empty/missing value means no presets have been saved.
     */
    val ratioMixPresets: Flow<List<RatioMixPreset>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
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

    /**
     * Last unit-converter category accessed by the user (e.g. "Weight / Mass").
     * Null if the user has not used the converter yet.
     */
    val lastConverterCategory: Flow<String?> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            preferences[KEY_LAST_CONVERTER_CATEGORY]
        }

    suspend fun saveLastConverterCategory(categoryName: String) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_LAST_CONVERTER_CATEGORY] = categoryName
            }
        } catch (e: IOException) {
            // Log error, could use Timber if available
        }
    }

    /**
     * Persisted unit selections for each converter category.
     * Maps category name -> Pair(fromUnitLabel, toUnitLabel).
     */
    val converterUnits: Flow<Map<String, Pair<String, String>>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            preferences[KEY_CONVERTER_UNITS]
                ?.let { decodeConverterUnits(it) }
                ?: emptyMap()
        }

    suspend fun saveConverterUnits(categoryName: String, fromUnit: String, toUnit: String) {
        try {
            dataStore.edit { preferences ->
                val current = preferences[KEY_CONVERTER_UNITS]
                    ?.let { decodeConverterUnits(it) }
                    ?.toMutableMap()
                    ?: mutableMapOf()
                current[categoryName] = Pair(fromUnit, toUnit)
                preferences[KEY_CONVERTER_UNITS] = encodeConverterUnits(current)
            }
        } catch (e: IOException) {
            // Log error if needed
        }
    }

    /**
     * Persisted calculator history. Returns a list of calculation strings
     * (e.g., "5 + 5 + 5 - 3 = 12"). Most recent first.
     */
    val calculatorHistory: Flow<List<String>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            preferences[KEY_CALCULATOR_HISTORY]
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.take(CalculatorEngine.HISTORY_LIMIT)
                ?: emptyList()
        }

    suspend fun saveCalculatorHistory(history: List<String>): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                preferences[KEY_CALCULATOR_HISTORY] = history.joinToString("\n")
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun clearCalculatorHistory(): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                preferences.remove(KEY_CALCULATOR_HISTORY)
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /**
     * Persisted saved recipes. Each recipe is stored with its name, servings, and ingredient list.
     * Stored as newline-separated records; each record has the format:
     *
     *   B64(recipeName)\tB64(servings)\tB64(qty1)\uFFFEB64(unit1)\uFFFEB64(state1)\uFFFEB64(name1)\t...
     *
     * Every field is Base64-encoded (URL-safe, no padding) so that special characters
     * cannot collide with the structural delimiters.
     * '\t' separates the recipe metadata from ingredients, and ingredients from each other.
     * '\uFFFE' separates an ingredient's fields.
     *
     * An empty/missing value means no recipes have been saved.
     */
    val savedRecipes: Flow<List<SavedRecipe>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { preferences ->
            preferences[KEY_SAVED_RECIPES]
                ?.let { decodeSavedRecipes(it) }
                ?: emptyList()
        }

    suspend fun saveRecipe(recipe: SavedRecipe): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                val current = preferences[KEY_SAVED_RECIPES]
                    ?.let { decodeSavedRecipes(it) }
                    ?.toMutableList()
                    ?: mutableListOf()
                // Replace an existing recipe with the same name, otherwise append.
                val existingIndex = current.indexOfFirst { it.name == recipe.name }
                if (existingIndex >= 0) current[existingIndex] = recipe else current.add(recipe)
                preferences[KEY_SAVED_RECIPES] = encodeSavedRecipes(current)
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeName: String): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                val current = preferences[KEY_SAVED_RECIPES]
                    ?.let { decodeSavedRecipes(it) }
                    ?.toMutableList()
                    ?: return@edit
                current.removeAll { it.name == recipeName }
                preferences[KEY_SAVED_RECIPES] = encodeSavedRecipes(current)
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    companion object {
        private val KEY_SAE_METRIC_MAX_INCHES = intPreferencesKey("sae_metric_max_inches")
        private const val DEFAULT_MAX_INCHES = 1

        private val KEY_SAE_METRIC_SHOW_ONLY_COMMON = booleanPreferencesKey("sae_metric_show_only_common")
        private const val DEFAULT_SHOW_ONLY_COMMON = false

        private val KEY_HOME_MODULE_ORDER = stringPreferencesKey("home_module_order")
        private val KEY_HIDDEN_HOME_MODULES = stringPreferencesKey("hidden_home_modules")
        private val KEY_CONVERTER_CATEGORY_ORDER = stringPreferencesKey("converter_category_order")
        private val KEY_RATIO_MIX_PRESETS = stringPreferencesKey("ratio_mix_presets")
        private val KEY_LAST_CONVERTER_CATEGORY = stringPreferencesKey("last_converter_category")
        private val KEY_CONVERTER_UNITS = stringPreferencesKey("converter_units")
        private val KEY_CALCULATOR_HISTORY = stringPreferencesKey("calculator_history")
        private val KEY_SAVED_RECIPES = stringPreferencesKey("saved_recipes")

        /** Separator between a part's label and its ratio value. U+FFFE is a non-character. */
        private const val PART_SEP = "\uFFFE"

        /** Encode a single field value to URL-safe Base64 (no padding). */
        private fun b64enc(s: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

        /** Decode a single URL-safe Base64 field, returning null on malformed input. */
        private fun b64dec(s: String): String? = try {
            String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) { null }

        private fun encodeRatioMixPresets(presets: List<RatioMixPreset>): String =
            presets.joinToString("\n") { preset ->
                val partsEncoded = preset.parts.joinToString("\t") { (label, ratio) ->
                    "${b64enc(label)}$PART_SEP${b64enc(ratio)}"
                }
                "${b64enc(preset.name)}\t$partsEncoded"
            }

        private fun decodeRatioMixPresets(encoded: String): List<RatioMixPreset> =
            encoded.split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { record ->
                    val tokens = record.split("\t")
                    if (tokens.size < 2) return@mapNotNull null
                    val name = b64dec(tokens[0]) ?: return@mapNotNull null
                    val parts = tokens.drop(1).mapNotNull { partToken ->
                        val idx = partToken.indexOf(PART_SEP)
                        if (idx < 0) null
                        else {
                            val label = b64dec(partToken.substring(0, idx)) ?: return@mapNotNull null
                            val ratio = b64dec(partToken.substring(idx + PART_SEP.length)) ?: return@mapNotNull null
                            RatioMixPresetPart(label = label, ratioText = ratio)
                        }
                    }
                    if (parts.isEmpty()) null
                    else RatioMixPreset(name = name, parts = parts)
                }

        private fun encodeConverterUnits(unitsMap: Map<String, Pair<String, String>>): String =
            unitsMap.entries.joinToString("\n") { (category, units) ->
                "${b64enc(category)}\t${b64enc(units.first)}\t${b64enc(units.second)}"
            }

        private fun decodeConverterUnits(encoded: String): Map<String, Pair<String, String>> =
            encoded.split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { record ->
                    val tokens = record.split("\t")
                    if (tokens.size < 3) return@mapNotNull null
                    val category = b64dec(tokens[0]) ?: return@mapNotNull null
                    val fromUnit = b64dec(tokens[1]) ?: return@mapNotNull null
                    val toUnit = b64dec(tokens[2]) ?: return@mapNotNull null
                    category to Pair(fromUnit, toUnit)
                }
                .toMap()

        private fun encodeSavedRecipes(recipes: List<SavedRecipe>): String =
            recipes.joinToString("\n") { recipe ->
                // Slot [2] is kept as an empty placeholder for backwards-compat with
                // older data that stored IngredientState.name there.
                val ingredientsEncoded = recipe.ingredients.joinToString("\t") { ingredient ->
                    "${b64enc(ingredient.qtyString)}$PART_SEP${b64enc(ingredient.unit)}$PART_SEP${b64enc("")}$PART_SEP${b64enc(ingredient.name)}"
                }
                "${b64enc(recipe.name)}\t${b64enc(recipe.servings)}\t$ingredientsEncoded"
            }

        private fun decodeSavedRecipes(encoded: String): List<SavedRecipe> =
            encoded.split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { record ->
                    val tokens = record.split("\t")
                    if (tokens.size < 2) return@mapNotNull null
                    val name = b64dec(tokens[0]) ?: return@mapNotNull null
                    val servings = b64dec(tokens[1]) ?: return@mapNotNull null
                    val ingredients = tokens.drop(2).mapNotNull { ingredientToken ->
                        val parts = ingredientToken.split(PART_SEP)
                        // parts[2] was IngredientState; we ignore it now but still require
                        // 4 slots so old records (which always had 4) continue to decode.
                        if (parts.size < 4) null
                        else {
                            val qty     = b64dec(parts[0]) ?: return@mapNotNull null
                            val unit    = b64dec(parts[1]) ?: return@mapNotNull null
                            // parts[2] intentionally ignored (legacy IngredientState slot)
                            val ingName = b64dec(parts[3]) ?: return@mapNotNull null
                            SavedRecipeIngredient(
                                qtyString = qty,
                                unit      = unit,
                                name      = ingName
                            )
                        }
                    }
                    if (ingredients.isEmpty()) null
                    else SavedRecipe(name = name, servings = servings, ingredients = ingredients)
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
