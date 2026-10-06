package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Targeted tests for [RecipeOcrParser] fraction-handling edge cases,
 * specifically the ML Kit mis-reads that produce "14" instead of "1¼".
 */
class RecipeOcrParserTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseLine(line: String) = RecipeOcrParser.parse(line).firstOrNull()

    private fun assertQty(line: String, expected: Double) {
        val item = parseLine(line)
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty for '$line'", expected, qty, 0.002)
    }

    // ── Full recipe — real Unicode glyphs ─────────────────────────────────────

    @Test
    fun `full recipe image parses all 6 lines correctly`() {
        val raw = """
            4 cups all-purpose flour
            1¼ cups warm water
            3 tbsp sugar
            2 tbsp butter
            1 tbsp yeast
            1¼ tsp salt
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 6 ingredients", 6, items.size)
        assertEquals("4",    items[0].qtyString); assertEquals("cup",  items[0].unit)
        assertEquals("cup",  items[1].unit)
        val qty1 = RecipeScalerCalculator.parseQuantity(items[1].qtyString)
        assertEquals("1¼ cups: qty=1.25", 1.25, qty1, 0.001)
        assertEquals("3",    items[2].qtyString); assertEquals("tbsp", items[2].unit)
        assertEquals("2",    items[3].qtyString); assertEquals("tbsp", items[3].unit)
        assertEquals("1",    items[4].qtyString); assertEquals("tbsp", items[4].unit)
        val qty5 = RecipeScalerCalculator.parseQuantity(items[5].qtyString)
        assertEquals("1¼ tsp: qty=1.25", 1.25, qty5, 0.001)
        assertEquals("tsp",  items[5].unit)
    }

    // ── Full recipe — ML Kit digit-collapse (exactly what logcat showed) ──────

    @Test
    fun `full recipe ML Kit digit-collapse variant parses all 6 lines correctly`() {
        val raw = """
            4 cups all-purpose flour
            14 cups warm water
            3 tbsp sugar
            2 tbsp butter
            1 tbsp yeast
            14 tsp salt
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 6 ingredients", 6, items.size)
        assertEquals("4",   items[0].qtyString); assertEquals("cup",  items[0].unit)
        val qty1 = RecipeScalerCalculator.parseQuantity(items[1].qtyString)
        assertEquals("14 cups → 1.25", 1.25, qty1, 0.001)
        assertEquals("cup",  items[1].unit)
        assertEquals("Warm water", items[1].name)
        assertEquals("3",    items[2].qtyString); assertEquals("tbsp", items[2].unit)
        assertEquals("2",    items[3].qtyString); assertEquals("tbsp", items[3].unit)
        assertEquals("1",    items[4].qtyString); assertEquals("tbsp", items[4].unit)
        val qty5 = RecipeScalerCalculator.parseQuantity(items[5].qtyString)
        assertEquals("14 tsp → 1.25", 1.25, qty5, 0.001)
        assertEquals("tsp",  items[5].unit)
        assertEquals("Salt", items[5].name)
    }

    // ── Unicode glyph path ────────────────────────────────────────────────────

    @Test fun `unicode half cup`()              { assertQty("1½ cup flour",  1.5) }
    @Test fun `unicode quarter cup`()           { assertQty("1¼ cup flour",  1.25) }
    @Test fun `unicode three-quarters cup`()    { assertQty("1¾ cup flour",  1.75) }
    @Test fun `unicode third cup`()             { assertQty("1⅓ cup flour",  1.333) }
    @Test fun `unicode two-thirds cup`()        { assertQty("1⅔ cup flour",  1.667) }

    @Test fun `unicode quarter full line`() {
        val item = parseLine("1¼ cups warm water")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 1.25", 1.25, qty, 0.001)
        assertEquals("cup",        item?.unit)
        assertEquals("Warm water", item?.name)
    }

    // ── ML Kit digit-collapse path ────────────────────────────────────────────

    @Test fun `ml-kit 12 cup → 1 half`()          { assertQty("12 cup flour",  1.5) }
    @Test fun `ml-kit 14 cup → 1 quarter`()        { assertQty("14 cup flour",  1.25) }
    @Test fun `ml-kit 134 cup → 1 three-quarter`() { assertQty("134 cup flour", 1.75) }
    @Test fun `ml-kit 13 cup → 1 third`()          { assertQty("13 cup flour",  1.333) }
    @Test fun `ml-kit 14 tsp → 1 quarter`()        { assertQty("14 tsp salt",   1.25) }
    @Test fun `ml-kit 12 tbsp → 1 half`()          { assertQty("12 tbsp butter", 1.5) }

    // ── False-positive guards — must NOT be rewritten ─────────────────────────

    @Test fun `plain 3 tbsp stays 3`()  { assertQty("3 tbsp sugar",  3.0) }
    @Test fun `plain 4 cups stays 4`()  { assertQty("4 cups flour",  4.0) }

    @Test fun `plain 12 eggs stays 12`() {
        // "eggs" is not a recognised unit word — digit-collapse must NOT fire.
        val item = parseLine("12 eggs")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("12 eggs must not become 1.5", 12.0, qty, 0.001)
    }

    // ── Digit-1 mis-read as "L" fused to unit (Cheesy Bread regression) ──────
    //
    // These three patterns were observed in a real ML Kit scan of a Cheesy Bread
    // recipe image. "1 cup" was read as "Lcup" (fused), "1 cup" as "L cup"
    // (spaced), and "1 Tbsp" as "V1Tbsp" (leading-V artefact + fused digit).

    @Test fun `ml-kit Lcup fused → 1 cup`() {
        val item = parseLine("Lcup shredded cheddar cheese")
        assertEquals("qty should be '1'",            "1",                      item?.qtyString)
        assertEquals("unit should be cup",            "cup",                    item?.unit)
        assertEquals("name should be ingredient",     "Shredded cheddar cheese", item?.name)
    }

    @Test fun `ml-kit L cup spaced → 1 cup`() {
        val item = parseLine("L cup milk")
        assertEquals("qty should be '1'",  "1",    item?.qtyString)
        assertEquals("unit should be cup", "cup",  item?.unit)
        assertEquals("name should be milk", "Milk", item?.name)
    }

    @Test fun `ml-kit V1Tbsp fused → 1 tbsp`() {
        val item = parseLine("V1Tbsp chopped parsley")
        assertEquals("qty should be '1'",       "1",              item?.qtyString)
        assertEquals("unit should be tbsp",     "tbsp",           item?.unit)
        assertEquals("name should be parsley",  "Chopped parsley", item?.name)
    }

    @Test fun `ml-kit 1cup fused no letter prefix → 1 cup`() {
        // "1cup" (digit directly fused to unit, no L prefix)
        val item = parseLine("1cup shredded cheddar")
        assertEquals("qty should be '1'",  "1",               item?.qtyString)
        assertEquals("unit should be cup", "cup",             item?.unit)
        assertEquals("name",               "Shredded cheddar", item?.name)
    }

    @Test fun `full cheesy bread recipe parses all 9 lines correctly`() {
        // Simulates what ML Kit might return for the Cheesy Bread recipe photo,
        // mixing normal and mis-read tokens.
        val raw = """
            2 cups all-purpose flour
            1 Tbsp baking powder
            1/2 tsp salt
            1/2 tsp garlic powder
            Lcup shredded cheddar cheese
            L cup milk
            1/4 cup melted butter
            1 egg
            V1Tbsp chopped parsley
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 9 ingredients", 9, items.size)

        assertEquals("2",     items[0].qtyString); assertEquals("cup",  items[0].unit)
        assertEquals("1",     items[1].qtyString); assertEquals("tbsp", items[1].unit)
        assertEquals("1/2",   items[2].qtyString); assertEquals("tsp",  items[2].unit)
        assertEquals("1/2",   items[3].qtyString); assertEquals("tsp",  items[3].unit)

        // "Lcup shredded cheddar cheese" → qty=1, unit=cup
        assertEquals("cup",   items[4].unit)
        val qty4 = RecipeScalerCalculator.parseQuantity(items[4].qtyString)
        assertEquals("Lcup → qty 1", 1.0, qty4, 0.001)

        // "L cup milk" → qty=1, unit=cup
        assertEquals("cup",   items[5].unit)
        val qty5 = RecipeScalerCalculator.parseQuantity(items[5].qtyString)
        assertEquals("L cup → qty 1", 1.0, qty5, 0.001)

        assertEquals("1/4",   items[6].qtyString); assertEquals("cup",  items[6].unit)
        assertEquals("1",     items[7].qtyString)

        // "V1Tbsp chopped parsley" → qty=1, unit=tbsp
        assertEquals("tbsp",  items[8].unit)
        val qty8 = RecipeScalerCalculator.parseQuantity(items[8].qtyString)
        assertEquals("V1Tbsp → qty 1", 1.0, qty8, 0.001)
    }
}
