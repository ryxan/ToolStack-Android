@file:OptIn(ExperimentalCoroutinesApi::class)

package com.toolstack.io.ui.conduitbends

import com.toolstack.io.domain.model.BendType
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConduitBendsViewModelTest {

    private lateinit var viewModel: ConduitBendsViewModel

    @Before
    fun setup() {
        viewModel = ConduitBendsViewModel()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Initial State Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is SELECTION step`() = runTest {
        val state = viewModel.uiState.value

        assertEquals(BendStep.SELECTION, state.step)
        assertNull(state.selectedBend)
        assertNull(state.result)
        assertNull(state.inputError)
    }

    @Test
    fun `initial state has sensible defaults`() = runTest {
        val state = viewModel.uiState.value

        assertEquals(ConduitSize.HALF, state.conduitSize)
        assertEquals(OffsetAngle.DEG_45, state.offsetAngle)
        assertEquals("", state.inputValue)
        assertEquals(ResultTab.QUICK, state.resultTab)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Navigation Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onBendSelected transitions to INPUT step`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertEquals(BendType.STUB_UP_90, state.selectedBend)
    }

    @Test
    fun `onBendSelected clears previous input and result`() = runTest {
        // Setup: complete a calculation
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")
        viewModel.onCalculate()

        // Now select a different bend
        viewModel.onBendSelected(BendType.OFFSET)

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertEquals(BendType.OFFSET, state.selectedBend)
        assertEquals("", state.inputValue)
        assertNull(state.result)
        assertNull(state.inputError)
        assertEquals(ResultTab.QUICK, state.resultTab)
    }

    @Test
    fun `onBackFromInput returns to SELECTION`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")

        viewModel.onBackFromInput()

        val state = viewModel.uiState.value
        assertEquals(BendStep.SELECTION, state.step)
        assertNull(state.selectedBend)
        assertEquals("", state.inputValue)
        assertNull(state.inputError)
    }

    @Test
    fun `onBackFromResult returns to INPUT preserving values`() = runTest {
        // Setup: complete a calculation
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onConduitSizeChanged(ConduitSize.THREE_QTR)
        viewModel.onInputValueChanged("15")
        viewModel.onCalculate()

        // Navigate back from result
        viewModel.onBackFromResult()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertEquals(BendType.STUB_UP_90, state.selectedBend)
        assertEquals(ConduitSize.THREE_QTR, state.conduitSize)
        assertEquals("15", state.inputValue)
        assertNull(state.result)  // Result is cleared
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Input Field Change Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onInputValueChanged updates state`() = runTest {
        viewModel.onInputValueChanged("24.5")

        assertEquals("24.5", viewModel.uiState.value.inputValue)
    }

    @Test
    fun `onInputValueChanged clears error`() = runTest {
        // Setup: trigger an error
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onCalculate()  // Empty input triggers error

        // User types something
        viewModel.onInputValueChanged("12")

        assertNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `onConduitSizeChanged updates size`() = runTest {
        viewModel.onConduitSizeChanged(ConduitSize.ONE_HALF)

        assertEquals(ConduitSize.ONE_HALF, viewModel.uiState.value.conduitSize)
    }

    @Test
    fun `onConduitSizeChanged clears error`() = runTest {
        // Setup: trigger an error
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onCalculate()

        // User changes size
        viewModel.onConduitSizeChanged(ConduitSize.ONE)

        assertNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `onOffsetAngleChanged updates angle`() = runTest {
        viewModel.onOffsetAngleChanged(OffsetAngle.DEG_30)

        assertEquals(OffsetAngle.DEG_30, viewModel.uiState.value.offsetAngle)
    }

    @Test
    fun `onOffsetAngleChanged clears error`() = runTest {
        // Setup: trigger an error
        viewModel.onBendSelected(BendType.OFFSET)
        viewModel.onCalculate()

        // User changes angle
        viewModel.onOffsetAngleChanged(OffsetAngle.DEG_60)

        assertNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `onResultTabChanged updates tab`() = runTest {
        viewModel.onResultTabChanged(ResultTab.DETAILED)

        assertEquals(ResultTab.DETAILED, viewModel.uiState.value.resultTab)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onCalculate with empty input shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertNotNull(state.inputError)
        assert(state.inputError!!.contains("valid measurement"))
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with whitespace input shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("   ")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertNotNull(state.inputError)
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with non-numeric input shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("abc")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertNotNull(state.inputError)
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with letters in numeric input shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12a")

        viewModel.onCalculate()

        assertNotNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `onCalculate with zero shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("0")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertNotNull(state.inputError)
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with negative number shows error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("-5")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertNotNull(state.inputError)
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with stub shorter than takeOff shows calculator error`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onConduitSizeChanged(ConduitSize.HALF)  // takeOff = 5"
        viewModel.onInputValueChanged("4")  // Less than takeOff

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.INPUT, state.step)
        assertNotNull(state.inputError)
        assert(state.inputError!!.contains("shorter") || state.inputError!!.contains("take-off"))
        assertNull(state.result)
    }

    @Test
    fun `onCalculate with corner distance equal to takeOff shows error`() = runTest {
        viewModel.onBendSelected(BendType.CORNER_90)
        viewModel.onConduitSizeChanged(ConduitSize.HALF)  // takeOff = 5"
        viewModel.onInputValueChanged("5")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertNotNull(state.inputError)
        assertNull(state.result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Successful Calculation Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onCalculate with valid stubUp90 transitions to RESULT`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.RESULT, state.step)
        assertNotNull(state.result)
        assertNull(state.inputError)
    }

    @Test
    fun `onCalculate with valid offset transitions to RESULT`() = runTest {
        viewModel.onBendSelected(BendType.OFFSET)
        viewModel.onInputValueChanged("6")
        viewModel.onOffsetAngleChanged(OffsetAngle.DEG_45)

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.RESULT, state.step)
        assertNotNull(state.result)
        assertNull(state.inputError)
    }

    @Test
    fun `onCalculate with valid corner90 populates result correctly`() = runTest {
        viewModel.onBendSelected(BendType.CORNER_90)
        viewModel.onConduitSizeChanged(ConduitSize.HALF)
        viewModel.onInputValueChanged("24")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertNotNull(state.result)
        // Result should be Corner90 with mark at 19" (24 - 5 takeOff)
        assert(state.result is com.toolstack.io.domain.model.BendResult.Corner90)
    }

    @Test
    fun `onCalculate clears inputError on success`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onCalculate()  // First attempt with empty input
        assert(viewModel.uiState.value.inputError != null)

        viewModel.onInputValueChanged("12")
        viewModel.onCalculate()  // Second attempt with valid input

        assertNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `onCalculate with decimal input works correctly`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12.5")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.RESULT, state.step)
        assertNotNull(state.result)
    }

    @Test
    fun `onCalculate trims whitespace from input`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("  12.5  ")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.RESULT, state.step)
        assertNotNull(state.result)
    }

    @Test
    fun `onCalculate with all bend types succeeds with valid input`() = runTest {
        val testCases = listOf(
            BendType.CORNER_90 to "24",
            BendType.STUB_UP_90 to "12",
            BendType.OFFSET to "6",
            BendType.SADDLE_3_POINT to "4",
            BendType.SADDLE_4_POINT to "5",
            BendType.BACK_TO_BACK to "18"
        )

        testCases.forEach { (bendType, input) ->
            viewModel.onBendSelected(bendType)
            viewModel.onInputValueChanged(input)
            viewModel.onCalculate()

            val state = viewModel.uiState.value
            assertEquals("$bendType should transition to RESULT", BendStep.RESULT, state.step)
            assertNotNull("$bendType should have result", state.result)
            assertNull("$bendType should have no error", state.inputError)

            // Reset for next test
            viewModel.onBackFromResult()
            viewModel.onBackFromInput()
        }
    }

    @Test
    fun `onCalculate preserves conduit size in result state`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onConduitSizeChanged(ConduitSize.ONE_HALF)
        viewModel.onInputValueChanged("20")

        viewModel.onCalculate()

        assertEquals(ConduitSize.ONE_HALF, viewModel.uiState.value.conduitSize)
    }

    @Test
    fun `onCalculate preserves offset angle in result state`() = runTest {
        viewModel.onBendSelected(BendType.OFFSET)
        viewModel.onOffsetAngleChanged(OffsetAngle.DEG_30)
        viewModel.onInputValueChanged("8")

        viewModel.onCalculate()

        assertEquals(OffsetAngle.DEG_30, viewModel.uiState.value.offsetAngle)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Edge Case Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onCalculate with minimum valid stub height succeeds`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onConduitSizeChanged(ConduitSize.HALF)  // takeOff = 5"
        viewModel.onInputValueChanged("5.1")  // Just above takeOff

        viewModel.onCalculate()

        assertEquals(BendStep.RESULT, viewModel.uiState.value.step)
    }

    @Test
    fun `onCalculate called without selecting bend does nothing`() = runTest {
        viewModel.onInputValueChanged("12")

        viewModel.onCalculate()

        val state = viewModel.uiState.value
        assertEquals(BendStep.SELECTION, state.step)
        assertNull(state.result)
    }

    @Test
    fun `multiple calculations preserve history in state`() = runTest {
        // First calculation
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")
        viewModel.onCalculate()
        val firstResult = viewModel.uiState.value.result

        // Go back and calculate again with different input
        viewModel.onBackFromResult()
        viewModel.onInputValueChanged("15")
        viewModel.onCalculate()
        val secondResult = viewModel.uiState.value.result

        // Results should be different
        assert(firstResult != secondResult)
    }

    @Test
    fun `default result tab is QUICK after calculation`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")

        viewModel.onCalculate()

        assertEquals(ResultTab.QUICK, viewModel.uiState.value.resultTab)
    }

    @Test
    fun `changing result tab does not affect calculation state`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")
        viewModel.onCalculate()

        val resultBefore = viewModel.uiState.value.result
        viewModel.onResultTabChanged(ResultTab.DETAILED)
        val resultAfter = viewModel.uiState.value.result

        assertEquals(resultBefore, resultAfter)
        assertEquals(ResultTab.DETAILED, viewModel.uiState.value.resultTab)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Input Parsing Tests (via calculation behavior)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseInches accepts whole numbers`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12")
        viewModel.onCalculate()

        assertEquals(BendStep.RESULT, viewModel.uiState.value.step)
    }

    @Test
    fun `parseInches accepts decimals`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12.75")
        viewModel.onCalculate()

        assertEquals(BendStep.RESULT, viewModel.uiState.value.step)
    }

    @Test
    fun `parseInches rejects multiple decimal points`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12.5.5")
        viewModel.onCalculate()

        assertNotNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `parseInches rejects special characters`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("12#")
        viewModel.onCalculate()

        assertNotNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun `parseInches accepts leading zeros`() = runTest {
        viewModel.onBendSelected(BendType.STUB_UP_90)
        viewModel.onInputValueChanged("012")
        viewModel.onCalculate()

        assertEquals(BendStep.RESULT, viewModel.uiState.value.step)
    }

    @Test
    fun `parseInches accepts decimal without leading zero`() = runTest {
        viewModel.onBendSelected(BendType.OFFSET)
        viewModel.onInputValueChanged(".5")
        viewModel.onCalculate()

        // .5 is less than any takeOff, so should error for validation, not parsing
        // This tests that parsing succeeded
        val state = viewModel.uiState.value
        // Since Offset has no takeOff check, this should succeed
        assertEquals(BendStep.RESULT, state.step)
    }
}
