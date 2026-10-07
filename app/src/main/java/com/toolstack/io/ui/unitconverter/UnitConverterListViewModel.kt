package com.toolstack.io.ui.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.UnitConverterData
import com.toolstack.io.domain.model.UnitCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnitConverterListViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnitConverterListUiState())
    val uiState: StateFlow<UnitConverterListUiState> = _uiState.asStateFlow()

    /**
     * Set to true by [moveCategory] the moment the user performs their first reorder.
     * The async preference load in [init] checks this flag before applying the saved
     * order, so a reorder that races with the initial read is never overwritten.
     */
    @Volatile private var userHasMutated = false

    init {
        // Load the persisted category order once, then apply it to the default list.
        // Skip the update if the user already reordered before the read completed.
        viewModelScope.launch {
            val savedOrder = preferencesRepository.converterCategoryOrder.first()
            if (!userHasMutated) {
                _uiState.update { it.copy(categories = applyOrder(DEFAULT_CATEGORIES, savedOrder)) }
            }
        }
    }

    /**
     * Move the category at [from] to [to] in the list, then persist the new order.
     * Called on every live drag step so the list animates in real time.
     */
    fun moveCategory(from: Int, to: Int) {
        userHasMutated = true
        val current = _uiState.value.categories.toMutableList()
        current.add(to, current.removeAt(from))
        _uiState.update { it.copy(categories = current) }
        viewModelScope.launch {
            preferencesRepository.saveConverterCategoryOrder(current.map { it.id })
        }
    }

    companion object {
        private val DEFAULT_CATEGORIES: List<UnitCategory> = UnitConverterData.categories

        /**
         * Reorder [defaults] according to [savedKeys]. Persisted keys are stable
         * category ids; keys written by older versions (current or legacy display
         * names) are normalized via [UnitConverterData.idFor]. Unknown keys are
         * dropped; categories absent from [savedKeys] are appended at the end.
         */
        fun applyOrder(defaults: List<UnitCategory>, savedKeys: List<String>): List<UnitCategory> {
            if (savedKeys.isEmpty()) return defaults
            val byId = defaults.associateBy { it.id }
            val seenIds = mutableSetOf<String>()
            val ordered = savedKeys.mapNotNull { savedKey ->
                val id = UnitConverterData.idFor(savedKey)
                if (id == null || id in seenIds) {
                    null  // Unknown key or duplicate after normalization
                } else {
                    seenIds.add(id)
                    byId[id]
                }
            }
            val missing = defaults.filter { it.id !in seenIds }
            return ordered + missing
        }
    }
}

data class UnitConverterListUiState(
    val categories: List<UnitCategory> = UnitConverterData.categories
)
