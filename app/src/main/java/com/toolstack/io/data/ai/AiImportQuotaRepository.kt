package com.toolstack.io.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.toolstack.io.domain.ai.AiImportConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device quota for AI recipe imports.
 *
 * Free users get [AiImportConfig.FREE_LIFETIME_LIMIT] lifetime imports plus a
 * balance of bonus credits bought in packs ([AiImportConfig.SCANS_PER_PACK])
 * or earned from rewarded ads ([AiImportConfig.SCANS_PER_REWARDED_AD]).
 * Premium users get a hidden daily cap of [AiImportConfig.PREMIUM_DAILY_CAP]
 * and never touch the bonus balance. Callers reserve before the network call
 * and refund on any non-success so a failed import never costs a credit.
 *
 * The counter is local and bypassable by clearing app data — acceptable for
 * metered credits; the real cost guards are App Check, the per-user RPM quota,
 * and budget alerts (see plan, "Known weaknesses").
 */
@Singleton
class AiImportQuotaRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val dayProvider: () -> String
) {
    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(
        dataStore,
        { LocalDate.now().toString() }
    )

    /** Remaining lifetime imports for a free user (0 once exhausted). */
    val remainingFree: Flow<Int> = dataStore.data.map { preferences ->
        (AiImportConfig.FREE_LIFETIME_LIMIT - (preferences[KEY_FREE_USED] ?: 0)).coerceAtLeast(0)
    }

    /** Purchased/ad-earned credits; spent only after the free allowance. */
    val bonusCredits: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_BONUS_CREDITS] ?: 0
    }

    /** Total imports a free user can still run — free allowance plus bonus. */
    val remainingImports: Flow<Int> = combine(remainingFree, bonusCredits) { free, bonus ->
        free + bonus
    }

    /**
     * Atomically reserves one import. Returns false when the user's quota is
     * exhausted — nothing is written in that case.
     */
    suspend fun tryReserve(isPremium: Boolean): Boolean {
        var granted = false
        dataStore.edit { preferences ->
            if (isPremium) {
                val today = dayProvider()
                val dayCount = if (preferences[KEY_DAY] == today) preferences[KEY_DAY_COUNT] ?: 0 else 0
                if (dayCount < AiImportConfig.PREMIUM_DAILY_CAP) {
                    preferences[KEY_DAY] = today
                    preferences[KEY_DAY_COUNT] = dayCount + 1
                    granted = true
                }
            } else {
                val used = preferences[KEY_FREE_USED] ?: 0
                val bonus = preferences[KEY_BONUS_CREDITS] ?: 0
                when {
                    used < AiImportConfig.FREE_LIFETIME_LIMIT -> {
                        preferences[KEY_FREE_USED] = used + 1
                        preferences[KEY_LAST_DEBIT] = DEBIT_FREE
                        granted = true
                    }
                    bonus > 0 -> {
                        preferences[KEY_BONUS_CREDITS] = bonus - 1
                        preferences[KEY_LAST_DEBIT] = DEBIT_BONUS
                        granted = true
                    }
                }
            }
        }
        return granted
    }

    /** Returns a previously reserved credit after a failed import. */
    suspend fun refund(isPremium: Boolean) {
        dataStore.edit { preferences ->
            if (isPremium) {
                if (preferences[KEY_DAY] == dayProvider()) {
                    preferences[KEY_DAY_COUNT] = ((preferences[KEY_DAY_COUNT] ?: 0) - 1).coerceAtLeast(0)
                }
            } else if (preferences[KEY_LAST_DEBIT] == DEBIT_BONUS) {
                preferences[KEY_BONUS_CREDITS] = (preferences[KEY_BONUS_CREDITS] ?: 0) + 1
            } else {
                preferences[KEY_FREE_USED] = ((preferences[KEY_FREE_USED] ?: 0) - 1).coerceAtLeast(0)
            }
        }
    }

    /** Adds [count] purchased or rewarded credits to the bonus balance. */
    suspend fun grantCredits(count: Int) {
        if (count <= 0) return
        dataStore.edit { preferences ->
            preferences[KEY_BONUS_CREDITS] = (preferences[KEY_BONUS_CREDITS] ?: 0) + count
        }
    }

    private companion object {
        val KEY_FREE_USED = intPreferencesKey("ai_import_free_used")
        val KEY_BONUS_CREDITS = intPreferencesKey("ai_import_bonus_credits")
        val KEY_LAST_DEBIT = stringPreferencesKey("ai_import_last_debit")
        val KEY_DAY = stringPreferencesKey("ai_import_day")
        val KEY_DAY_COUNT = intPreferencesKey("ai_import_day_count")

        const val DEBIT_FREE = "free"
        const val DEBIT_BONUS = "bonus"
    }
}
