package com.toolstack.io.ui.conduitbends

import com.toolstack.io.domain.model.BendResult
import com.toolstack.io.domain.model.BendType
import com.toolstack.io.domain.model.ConduitSize
import com.toolstack.io.domain.model.OffsetAngle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConduitBendsUiStateTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Data Class Instantiation Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `default state has expected values`() {
        val state = ConduitBendsUiState()

        assertEquals(BendStep.SELECTION, state.step)
        assertNull(state.selectedBend)
        assertEquals(ConduitSize.HALF, state.conduitSize)
        assertEquals(OffsetAngle.DEG_45, state.offsetAngle)
        assertEquals("", state.inputValue)
        assertNull(state.inputError)
        assertNull(state.result)
        assertEquals(ResultTab.QUICK, state.resultTab)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original = ConduitBendsUiState(
            step = BendStep.INPUT,
            selectedBend = BendType.STUB_UP_90,
            conduitSize = ConduitSize.ONE,
            inputValue = "12.5"
        )

        val copied = original.copy(inputValue = "15")

        assertEquals(BendStep.INPUT, copied.step)
        assertEquals(BendType.STUB_UP_90, copied.selectedBend)
        assertEquals(ConduitSize.ONE, copied.conduitSize)
        assertEquals("15", copied.inputValue)
    }

    @Test
    fun `copy can update step`() {
        val original = ConduitBendsUiState(step = BendStep.SELECTION)
        val updated = original.copy(step = BendStep.RESULT)

        assertEquals(BendStep.RESULT, updated.step)
    }

    @Test
    fun `copy can set result`() {
        val original = ConduitBendsUiState()
        val result = BendResult.StubUp90(
            takeOffInches = 5.0,
            markLocationInches = 7.0
        )
        val updated = original.copy(result = result)

        assertEquals(result, updated.result)
    }

    @Test
    fun `copy can clear error`() {
        val original = ConduitBendsUiState(inputError = "Invalid input")
        val cleared = original.copy(inputError = null)

        assertNull(cleared.inputError)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Equality Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `two states with same values are equal`() {
        val state1 = ConduitBendsUiState(
            step = BendStep.INPUT,
            selectedBend = BendType.OFFSET,
            inputValue = "6"
        )
        val state2 = ConduitBendsUiState(
            step = BendStep.INPUT,
            selectedBend = BendType.OFFSET,
            inputValue = "6"
        )

        assertEquals(state1, state2)
    }

    @Test
    fun `two states with different values are not equal`() {
        val state1 = ConduitBendsUiState(inputValue = "12")
        val state2 = ConduitBendsUiState(inputValue = "15")

        assertFalse(state1 == state2)
    }

    @Test
    fun `state with error vs state without error are different`() {
        val state1 = ConduitBendsUiState(inputError = "Error")
        val state2 = ConduitBendsUiState(inputError = null)

        assertFalse(state1 == state2)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // State Transition Validation Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `SELECTION step should have null selectedBend`() {
        val state = ConduitBendsUiState(
            step = BendStep.SELECTION,
            selectedBend = null
        )

        assertEquals(BendStep.SELECTION, state.step)
        assertNull(state.selectedBend)
    }

    @Test
    fun `INPUT step can have selectedBend`() {
        val state = ConduitBendsUiState(
            step = BendStep.INPUT,
            selectedBend = BendType.STUB_UP_90
        )

        assertEquals(BendStep.INPUT, state.step)
        assertEquals(BendType.STUB_UP_90, state.selectedBend)
    }

    @Test
    fun `RESULT step should have both selectedBend and result`() {
        val result = BendResult.StubUp90(5.0, 7.0)
        val state = ConduitBendsUiState(
            step = BendStep.RESULT,
            selectedBend = BendType.STUB_UP_90,
            result = result
        )

        assertEquals(BendStep.RESULT, state.step)
        assertEquals(BendType.STUB_UP_90, state.selectedBend)
        assertEquals(result, state.result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Null Safety Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `nullable fields can be null`() {
        val state = ConduitBendsUiState(
            selectedBend = null,
            inputError = null,
            result = null
        )

        assertNull(state.selectedBend)
        assertNull(state.inputError)
        assertNull(state.result)
    }

    @Test
    fun `nullable fields can be set to values`() {
        val result = BendResult.Offset(
            bendSpacingInches = 8.484,
            shrinkageInches = 2.25,
            mark1Inches = 0.0,
            mark2Inches = 8.484,
            angleUsed = OffsetAngle.DEG_45
        )
        val state = ConduitBendsUiState(
            selectedBend = BendType.OFFSET,
            inputError = "Test error",
            result = result
        )

        assertEquals(BendType.OFFSET, state.selectedBend)
        assertEquals("Test error", state.inputError)
        assertEquals(result, state.result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Computed Property Tests (add these if you add computed properties)
    // ──────────────────────────────────────────────────────────────────────────

    // Note: Currently ConduitBendsUiState has no computed properties.
    // If you add any (e.g., `val hasValidInput: Boolean`), add tests here:
    //
    // @Test
    // fun `hasValidInput returns true when input is valid`() {
    //     val state = ConduitBendsUiState(inputValue = "12")
    //     assertTrue(state.hasValidInput)
    // }
    //
    // @Test
    // fun `hasValidInput returns false when input is empty`() {
    //     val state = ConduitBendsUiState(inputValue = "")
    //     assertFalse(state.hasValidInput)
    // }

    // ──────────────────────────────────────────────────────────────────────────
    // Integration with Enums Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `all BendStep enum values can be assigned`() {
        BendStep.entries.forEach { step ->
            val state = ConduitBendsUiState(step = step)
            assertEquals(step, state.step)
        }
    }

    @Test
    fun `all ResultTab enum values can be assigned`() {
        ResultTab.entries.forEach { tab ->
            val state = ConduitBendsUiState(resultTab = tab)
            assertEquals(tab, state.resultTab)
        }
    }

    @Test
    fun `all ConduitSize enum values can be assigned`() {
        ConduitSize.entries.forEach { size ->
            val state = ConduitBendsUiState(conduitSize = size)
            assertEquals(size, state.conduitSize)
        }
    }

    @Test
    fun `all OffsetAngle enum values can be assigned`() {
        OffsetAngle.entries.forEach { angle ->
            val state = ConduitBendsUiState(offsetAngle = angle)
            assertEquals(angle, state.offsetAngle)
        }
    }

    @Test
    fun `all BendType enum values can be assigned as selectedBend`() {
        BendType.entries.forEach { bendType ->
            val state = ConduitBendsUiState(selectedBend = bendType)
            assertEquals(bendType, state.selectedBend)
        }
    }
}
