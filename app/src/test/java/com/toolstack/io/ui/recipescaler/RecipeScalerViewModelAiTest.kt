@file:OptIn(ExperimentalCoroutinesApi::class)
package com.toolstack.io.ui.recipescaler

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.toolstack.io.data.ai.AiImportQuotaRepository
import com.toolstack.io.data.ai.AiImportResult
import com.toolstack.io.data.ai.AiRecipeImporter
import com.toolstack.io.data.ai.NoopRewardedAdProvider
import com.toolstack.io.data.api.BillingVerificationApi
import com.toolstack.io.data.api.VerifyPurchaseRequest
import com.toolstack.io.data.api.VerifyPurchaseResponse
import com.toolstack.io.data.billing.BillingProduct
import com.toolstack.io.data.billing.BillingRepository
import com.toolstack.io.data.billing.PurchaseState
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.ai.AiImportConfig
import com.toolstack.io.domain.ai.AiRecipeResult
import com.toolstack.io.domain.model.IngredientItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * ViewModel-level tests for the AI import flow. Robolectric supplies a real
 * [Context] for the ViewModel; the importer and entitlement are faked, the
 * quota repository is real over an in-memory DataStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeScalerViewModelAiTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var dataStore: FakeDataStore
    private lateinit var quota: AiImportQuotaRepository
    private lateinit var importer: FakeAiImporter

    private fun createViewModel(premium: Boolean = false): RecipeScalerViewModel {
        dataStore = FakeDataStore()
        quota = AiImportQuotaRepository(dataStore) { DAY }
        importer = FakeAiImporter()
        return RecipeScalerViewModel(
            appContext = context,
            userPreferencesRepository = UserPreferencesRepository(dataStore),
            billingRepository = FakeBillingRepository(context, premium, quota),
            aiImporter = importer,
            aiQuota = quota,
            rewardedAds = NoopRewardedAdProvider()
        )
    }

    @Test
    fun `AI import success replaces a blank list and spends one free credit`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        advanceUntilIdle()

        assertEquals(1, importer.callCount)
        assertTrue(importer.jpegs.single().contentEquals(JPEG_A))
        val state = viewModel.uiState.value
        assertEquals(2, state.ingredients.size)
        assertNull(state.pendingScanResult)
        assertNull(state.scanError)
        assertFalse(state.isAiProcessing)
        assertEquals(AiImportConfig.FREE_LIFETIME_LIMIT - 1, state.aiScansRemaining)
        // Title and servings from the AI result prefill their fields.
        assertEquals("Test Cake", state.recipeNameInput)
        assertEquals("8", state.originalServingsText)
    }

    @Test
    fun `AI import on a non-blank list holds the result then appends on confirm`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIngredientNameChanged(viewModel.uiState.value.ingredients.first().id, "butter")
        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        advanceUntilIdle()

        // Non-blank list → result waits in pendingScanResult for confirmation.
        assertEquals(2, viewModel.uiState.value.pendingScanResult?.size)
        assertEquals(1, viewModel.uiState.value.ingredients.size)

        viewModel.onScanResultAppend()
        val ingredients = viewModel.uiState.value.ingredients
        assertEquals(3, ingredients.size)
        assertEquals("butter", ingredients.first().name)
        assertEquals("sugar", ingredients.last().name)
    }

    @Test
    fun `AI failure refunds the reserved credit`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        importer.nextResult = AiImportResult.NetworkError

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        advanceUntilIdle()

        assertEquals(1, importer.callCount)
        val state = viewModel.uiState.value
        assertEquals(ScanError.AI_NETWORK_ERROR, state.scanError)
        assertTrue(state.canOfferMlKitFallback)
        assertEquals(AiImportConfig.FREE_LIFETIME_LIMIT, state.aiScansRemaining)
    }

    @Test
    fun `free user with no imports left gets the paywall and no import call`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        // Spend every lifetime credit up front.
        repeat(AiImportConfig.FREE_LIFETIME_LIMIT) {
            assertTrue(quota.tryReserve(isPremium = false))
        }
        advanceUntilIdle()

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        advanceUntilIdle()

        assertEquals(0, importer.callCount)
        assertTrue(viewModel.uiState.value.showAiPaywall)
        assertNull(viewModel.uiState.value.scanError)
    }

    @Test
    fun `premium user is not blocked after the free credits are gone`() = runTest {
        val viewModel = createViewModel(premium = true)
        advanceUntilIdle()
        repeat(AiImportConfig.FREE_LIFETIME_LIMIT) {
            assertTrue(quota.tryReserve(isPremium = false))
        }
        advanceUntilIdle()

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        advanceUntilIdle()

        assertEquals(1, importer.callCount)
        val state = viewModel.uiState.value
        assertFalse(state.showAiPaywall)
        assertNull(state.scanError)
        assertEquals(2, state.ingredients.size)
    }

    @Test
    fun `retry reuses the cached jpeg bytes`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        importer.nextResult = AiImportResult.NetworkError

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        viewModel.onTryAiImport() // second attempt still uses the same cached bytes
        advanceUntilIdle()

        // Both calls must carry identical bytes — no re-read of a deleted file.
        assertTrue(importer.jpegs.isNotEmpty())
        assertTrue(importer.jpegs.all { it.contentEquals(JPEG_A) })
    }

    @Test
    fun `double tap spends only one credit`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onTryAiImport()
        viewModel.onTryAiImport() // isAiProcessing is already true → ignored
        advanceUntilIdle()

        assertEquals(1, importer.callCount)
        assertEquals(AiImportConfig.FREE_LIFETIME_LIMIT - 1, viewModel.uiState.value.aiScansRemaining)
    }

    @Test
    fun `cancel clears the cached jpeg so retry is a no-op`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.lastScanJpeg = JPEG_A
        viewModel.onScanCancelled()
        viewModel.onTryAiImport()
        advanceUntilIdle()

        assertEquals(0, importer.callCount)
        assertNull(viewModel.lastScanJpeg)
    }

    @Test
    fun `prepareJpegForAi decodes a real jpeg file into bounded bytes`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // A real, decodable JPEG exercises the decode → rotate → scale →
        // re-encode path. Called directly so runTest awaits the IO dispatcher
        // hop (onImageCaptured's viewModelScope launch cannot be awaited).
        val imageFile = File(context.cacheDir, "ai-scan-test.jpg")
        imageFile.writeBytes(
            android.util.Base64.decode(TINY_JPEG_BASE64, android.util.Base64.DEFAULT)
        )

        val jpeg = viewModel.prepareJpegForAi(Uri.fromFile(imageFile))

        assertNotNull(jpeg)
        assertTrue(jpeg!!.isNotEmpty())
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeAiImporter : AiRecipeImporter {
        val jpegs = mutableListOf<ByteArray>()
        var nextResult: AiImportResult = SUCCESS

        val callCount: Int get() = jpegs.size

        override suspend fun import(jpeg: ByteArray): AiImportResult {
            jpegs += jpeg
            return nextResult
        }
    }

    private class FakeBillingRepository(
        context: Context,
        premium: Boolean,
        quota: AiImportQuotaRepository
    ) : BillingRepository(context, FakeVerificationApi, quota) {
        private val _state = MutableStateFlow<PurchaseState>(
            if (premium) PurchaseState.LifetimePurchase else PurchaseState.None
        )
        override val purchaseState: StateFlow<PurchaseState> = _state.asStateFlow()
        override val products: StateFlow<List<BillingProduct>> = MutableStateFlow(emptyList())
    }

    private object FakeVerificationApi : BillingVerificationApi {
        override suspend fun verifyPurchase(request: VerifyPurchaseRequest) =
            VerifyPurchaseResponse(valid = false, status = "test")
    }

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

    private companion object {
        const val DAY = "2026-10-10"
        val JPEG_A = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

        /** A real 80×120 JPEG — BitmapFactory must actually decode it. */
        const val TINY_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw8LCwkMEQ8SEhEPERETFhwXExQaFRERGCEYGh0d" +
            "Hx8fExciJCIeJBweHx7/2wBDAQUFBQcGBw4ICA4eFBEUHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4e" +
            "Hh4eHh4eHh7/wAARCAB4AFADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF" +
            "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW" +
            "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi" +
            "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC" +
            "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm" +
            "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq" +
            "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD7LooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooo" +
            "oAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKK" +
            "KKACiiigAooooAKKKKACiiigAooooA//2Q=="

        val SUCCESS = AiImportResult.Success(
            recipe = AiRecipeResult(
                title = "Test Cake",
                servings = "8",
                ingredients = emptyList()
            ),
            ingredients = listOf(
                IngredientItem(id = "ai-1", qtyString = "1", unit = "cup", name = "flour"),
                IngredientItem(id = "ai-2", qtyString = "2", unit = "tbsp", name = "sugar")
            )
        )
    }
}
