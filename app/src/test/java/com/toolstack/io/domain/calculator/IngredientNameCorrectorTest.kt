package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [IngredientNameCorrector] — the bounded edit-distance function
 * and the per-word dictionary correction used for post-OCR name cleanup
 * (plan item 5 in docs/recipe-scaler-ocr-accuracy-plan.md).
 */
class IngredientNameCorrectorTest {

    // ── boundedEditDistance ─────────────────────────────────────────────────

    @Test fun `equal strings return 0`() {
        assertEquals(0, IngredientNameCorrector.boundedEditDistance("butter", "butter", 2))
    }

    @Test fun `single substitution returns 1`() {
        assertEquals(1, IngredientNameCorrector.boundedEditDistance("sugar", "suger", 2))
    }

    @Test fun `single insertion returns 1`() {
        assertEquals(1, IngredientNameCorrector.boundedEditDistance("chips", "chps", 2))
    }

    @Test fun `single deletion returns 1`() {
        assertEquals(1, IngredientNameCorrector.boundedEditDistance("butter", "buttter", 2))
    }

    @Test fun `two edits returns 2`() {
        // "flocor" → "flour": delete 'c' + substitute 'o'→'u' = 2
        assertEquals(2, IngredientNameCorrector.boundedEditDistance("flocor", "flour", 2))
    }

    @Test fun `three edits returns -1 when max is 2`() {
        assertEquals(-1, IngredientNameCorrector.boundedEditDistance("abcdef", "abdxyz", 2))
    }

    @Test fun `length difference greater than max returns -1`() {
        assertEquals(-1, IngredientNameCorrector.boundedEditDistance("salt", "cauliflower", 2))
    }

    @Test fun `max 0 behaves like equality`() {
        assertEquals(0,  IngredientNameCorrector.boundedEditDistance("salt", "salt", 0))
        assertEquals(-1, IngredientNameCorrector.boundedEditDistance("salt", "malt", 0))
    }

    @Test fun `true distance equal to max is still returned`() {
        // transposition costs 2 in Levenshtein (two substitutions)
        assertEquals(2, IngredientNameCorrector.boundedEditDistance("abcd", "abdc", 2))
    }

    // ── Dictionary sanity ───────────────────────────────────────────────────

    @Test fun `dictionary entries are all lowercase alpha and at least 4 chars`() {
        val pattern = Regex("^[a-z]{4,}$")
        val bad = IngredientNameCorrector.INGREDIENT_WORDS.filter { !it.matches(pattern) }
        assertTrue("non-conforming entries: $bad", bad.isEmpty())
    }

    // ── correctWords: corrections ───────────────────────────────────────────

    @Test fun `buter corrects to butter`() {
        assertEquals("butter", IngredientNameCorrector.correctWords("buter"))
    }

    @Test fun `suger corrects to sugar`() {
        assertEquals("sugar", IngredientNameCorrector.correctWords("suger"))
    }

    @Test fun `broun corrects to brown`() {
        assertEquals("brown", IngredientNameCorrector.correctWords("broun"))
    }

    @Test fun `capitalization is preserved on correction`() {
        assertEquals("Sugar", IngredientNameCorrector.correctWords("SUqar"))
    }

    @Test fun `flocor corrects to flour at distance 2`() {
        assertEquals("flour", IngredientNameCorrector.correctWords("flocor"))
    }

    @Test fun `chcolate chps corrects both tokens`() {
        assertEquals("chocolate chips", IngredientNameCorrector.correctWords("chcolate chps"))
    }

    @Test fun `bakina corrects to baking`() {
        assertEquals("baking", IngredientNameCorrector.correctWords("bakina"))
    }

    @Test fun `meltd corrects to melted`() {
        assertEquals("melted", IngredientNameCorrector.correctWords("meltd"))
    }

    @Test fun `sthcks corrects to sticks`() {
        assertEquals("sticks", IngredientNameCorrector.correctWords("sthcks"))
    }

    @Test fun `Colled corrects to Rolled`() {
        // c→r substitution is distance 1 with a unique match — the plan called
        // this unfixable, but "rolled" in the dictionary catches it anyway.
        assertEquals("Rolled", IngredientNameCorrector.correctWords("Colled"))
    }

    // ── correctWords: non-corrections ───────────────────────────────────────

    @Test fun `Vaci stays untouched - no near dictionary match`() {
        assertEquals("Vaci", IngredientNameCorrector.correctWords("Vaci"))
    }

    @Test fun `short tokens of 3 chars or fewer are never touched`() {
        assertEquals("rye ham oil", IngredientNameCorrector.correctWords("rye ham oil"))
    }

    @Test fun `dictionary words pass through unchanged`() {
        assertEquals("butter", IngredientNameCorrector.correctWords("butter"))
    }

    @Test fun `ambiguous token stays - bitter is distance 1 from batter and butter`() {
        assertEquals("bitter", IngredientNameCorrector.correctWords("bitter"))
    }

    @Test fun `cacoa corrects to cocoa - cacao is distance 2 not 1`() {
        // "cacoa"↔"cacao" is a transposition (distance 2), so "cocoa" is the
        // unique distance-1 match and the correction fires.
        assertEquals("cocoa", IngredientNameCorrector.correctWords("cacoa"))
    }

    @Test fun `nonsense token stays untouched`() {
        assertEquals("zzqwp", IngredientNameCorrector.correctWords("zzqwp"))
    }

    @Test fun `tokens with digits or punctuation are skipped`() {
        assertEquals("s0da all-purpose 2x", IngredientNameCorrector.correctWords("s0da all-purpose 2x"))
    }

    @Test fun `mixed name corrects only the garbled word`() {
        assertEquals("brown sugar", IngredientNameCorrector.correctWords("brown suqar"))
    }
}
