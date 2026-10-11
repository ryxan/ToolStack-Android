@file:OptIn(ExperimentalCoroutinesApi::class)
package com.toolstack.io.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.toolstack.io.domain.ai.AiImportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImportQuotaRepositoryTest {

    @Test
    fun `fresh install reports the full free limit`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }

        assertEquals(AiImportConfig.FREE_LIFETIME_LIMIT, repo.remainingFree.first())
    }

    @Test
    fun `free user reserves until the limit then fails`() = runTest {
        val dataStore = FakeDataStore()
        val repo = AiImportQuotaRepository(dataStore) { DAY }

        repeat(AiImportConfig.FREE_LIFETIME_LIMIT) {
            assertTrue(repo.tryReserve(isPremium = false))
        }
        assertFalse(repo.tryReserve(isPremium = false))
        assertEquals(0, repo.remainingFree.first())
    }

    @Test
    fun `refund restores a free credit`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }

        assertTrue(repo.tryReserve(isPremium = false))
        assertTrue(repo.tryReserve(isPremium = false))
        repo.refund(isPremium = false)

        assertEquals(1, repo.remainingFree.first())
        assertTrue(repo.tryReserve(isPremium = false))
        assertFalse(repo.tryReserve(isPremium = false))
    }

    @Test
    fun `refund never drops below zero`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }

        repo.refund(isPremium = false)

        assertEquals(AiImportConfig.FREE_LIFETIME_LIMIT, repo.remainingFree.first())
    }

    @Test
    fun `concurrent reserves cannot overspend the last free credit`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }
        assertTrue(repo.tryReserve(isPremium = false)) // one credit left

        val results = (1..4)
            .map { async(Dispatchers.Default) { repo.tryReserve(isPremium = false) } }
            .awaitAll()

        assertEquals(1, results.count { it })
        assertEquals(0, repo.remainingFree.first())
    }

    @Test
    fun `premium user reserves up to the daily cap`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }

        repeat(AiImportConfig.PREMIUM_DAILY_CAP) {
            assertTrue(repo.tryReserve(isPremium = true))
        }
        assertFalse(repo.tryReserve(isPremium = true))
    }

    @Test
    fun `premium cap rolls over on a new day`() = runTest {
        var day = "2026-10-10"
        val repo = AiImportQuotaRepository(FakeDataStore()) { day }

        repeat(AiImportConfig.PREMIUM_DAILY_CAP) {
            assertTrue(repo.tryReserve(isPremium = true))
        }
        assertFalse(repo.tryReserve(isPremium = true))

        day = "2026-10-11"
        assertTrue(repo.tryReserve(isPremium = true))
    }

    @Test
    fun `premium refund frees a slot for the same day`() = runTest {
        val repo = AiImportQuotaRepository(FakeDataStore()) { DAY }

        repeat(AiImportConfig.PREMIUM_DAILY_CAP) {
            assertTrue(repo.tryReserve(isPremium = true))
        }
        repo.refund(isPremium = true)

        assertTrue(repo.tryReserve(isPremium = true))
        assertFalse(repo.tryReserve(isPremium = true))
    }

    private companion object {
        const val DAY = "2026-10-10"
    }

    /**
     * In-memory DataStore with serialized writes, mirroring the real DataStore's
     * atomic-edit semantics so reservation races behave like production.
     */
    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val mutex = Mutex()
        private val _data = MutableStateFlow(initial)
        override val data: Flow<Preferences> = _data.asStateFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences = mutex.withLock {
            val newPreferences = transform(_data.value)
            _data.value = newPreferences
            newPreferences
        }
    }
}
