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

    // ── Handwritten recipe card: vertical bar / backslash as fraction slash ───
    //
    // Handwritten fractions on index-card style recipes are sometimes read by
    // ML Kit as a vertical bar "|" or backslash "\" instead of a forward slash.

    @Test fun `handwritten 3|4 c → 3 quarter cup`() {
        val item = parseLine("3|4 c. butter")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.75", 0.75, qty, 0.001)
        assertEquals("unit should be cup", "cup", item?.unit)
        assertEquals("name should be Butter", "Butter", item?.name)
    }

    @Test fun `handwritten 1|2 c → half cup`() {
        val item = parseLine("1|2 c. honey")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.5", 0.5, qty, 0.001)
        assertEquals("cup", item?.unit)
        assertEquals("Honey", item?.name)
    }

    @Test fun `handwritten 1 1|2 c → one and a half cups`() {
        val item = parseLine("1 1|2 c. rolled oats")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 1.5", 1.5, qty, 0.001)
        assertEquals("cup", item?.unit)
        assertEquals("Rolled oats", item?.name)
    }

    @Test fun `handwritten backslash fraction 3-slash-4 c → three quarters`() {
        val item = parseLine("3\\4 c. brown sugar")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.75", 0.75, qty, 0.001)
        assertEquals("cup", item?.unit)
    }

    @Test fun `handwritten 1|3 c → one third`() {
        val item = parseLine("1|3 c. brown sugar")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.333", 1.0 / 3.0, qty, 0.002)
        assertEquals("cup", item?.unit)
    }

    // ── Handwritten-card: digit-collapse producing compound mixed-number ──────
    //
    // "4 1/2 C. rolled oats" handwritten — if ML Kit reads the quantity tokens as
    // "4" and "12" (space-separated, with "12" being a collapsed "½"), the
    // digit-collapse rule turns "12 c." into "1 1/2 c.", leaving "4 1 1/2 c."
    // The compound mixed-number rule then folds that back to "4 1/2 c." (4.5).

    @Test fun `ml-kit digit-collapse compound 4 12 c → 4 and a half cups`() {
        // "4 12 c." simulates ML Kit reading "4" and "½" as separate tokens,
        // with "½" collapsed to "12".
        val item = parseLine("4 12 c. rolled oats")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 4.5", 4.5, qty, 0.001)
        assertEquals("unit should be cup", "cup", item?.unit)
        assertEquals("name", "Rolled oats", item?.name)
    }

    @Test fun `ml-kit digit-collapse compound 2 12 tsp → 2 and a half tsp`() {
        val item = parseLine("2 12 tsp baking powder")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 2.5", 2.5, qty, 0.001)
        assertEquals("tsp", item?.unit)
    }

    @Test fun `ml-kit digit-collapse standalone 12 tsp → 1 and a half tsp`() {
        // Standard single-ingredient case — exercises the "12" rule standalone.
        val item = parseLine("12 tsp baking powder")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 1.5", 1.5, qty, 0.001)
        assertEquals("tsp", item?.unit)
    }

    // ── Full handwritten recipe card (the index-card style) ──────────────────
    //
    // Simulates ML Kit output for a handwritten card like the one in the image:
    //   3/4 C. butter, melted (1 1/2 sticks)
    //   1/2 C. honey
    //   1/3 C. brown sugar
    //   1 t. vanilla extract
    //   1 C. whole wheat flour
    //   1 t. baking soda
    //   4 1/2 C. rolled oats
    //   1 C. chocolate chips (or any extras)

    @Test fun `full handwritten index-card recipe parses correctly`() {
        val raw = """
            Ingredients:
            3/4 C. butter, melted (1 1/2 sticks)
            1/2 C. honey
            1/3 C. brown sugar
            1 t. vanilla extract
            1 C. whole wheat flour
            1 t. baking soda
            4 1/2 C. rolled oats
            1 C. chocolate chips (or any extras)
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 8 ingredients", 8, items.size)

        // 3/4 C. butter (parenthetical stripped)
        val qty0 = RecipeScalerCalculator.parseQuantity(items[0].qtyString)
        assertEquals("3/4 C. butter qty", 0.75, qty0, 0.001)
        assertEquals("cup", items[0].unit)
        assertEquals("Butter", items[0].name)

        // 1/2 C. honey
        val qty1 = RecipeScalerCalculator.parseQuantity(items[1].qtyString)
        assertEquals("1/2 C. honey qty", 0.5, qty1, 0.001)
        assertEquals("cup", items[1].unit)
        assertEquals("Honey", items[1].name)

        // 1/3 C. brown sugar
        val qty2 = RecipeScalerCalculator.parseQuantity(items[2].qtyString)
        assertEquals("1/3 C. brown sugar qty", 1.0 / 3.0, qty2, 0.002)
        assertEquals("cup", items[2].unit)

        // 1 t. vanilla extract  — "t." = teaspoon
        assertEquals("1", items[3].qtyString)
        assertEquals("tsp", items[3].unit)
        assertEquals("Vanilla extract", items[3].name)

        // 1 C. whole wheat flour
        assertEquals("1", items[4].qtyString)
        assertEquals("cup", items[4].unit)
        assertEquals("Whole wheat flour", items[4].name)

        // 1 t. baking soda
        assertEquals("1", items[5].qtyString)
        assertEquals("tsp", items[5].unit)
        assertEquals("Baking soda", items[5].name)

        // 4 1/2 C. rolled oats
        val qty6 = RecipeScalerCalculator.parseQuantity(items[6].qtyString)
        assertEquals("4 1/2 C. rolled oats qty", 4.5, qty6, 0.001)
        assertEquals("cup", items[6].unit)
        assertEquals("Rolled oats", items[6].name)

        // 1 C. chocolate chips (parenthetical stripped)
        assertEquals("1", items[7].qtyString)
        assertEquals("cup", items[7].unit)
        assertEquals("Chocolate chips", items[7].name)
    }

    // ── Full handwritten card with vertical-bar fraction slashes ─────────────
    //
    // Same card, but ML Kit reads the handwritten slashes as "|".

    @Test fun `full handwritten index-card with bar slashes parses correctly`() {
        val raw = """
            Ingredients:
            3|4 C. butter, melted (1 1|2 sticks)
            1|2 C. honey
            1|3 C. brown sugar
            1 t. vanilla extract
            1 C. whole wheat flour
            1 t. baking soda
            4 1|2 C. rolled oats
            1 C. chocolate chips (or any extras)
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 8 ingredients", 8, items.size)

        val qty0 = RecipeScalerCalculator.parseQuantity(items[0].qtyString)
        assertEquals("3|4 → 0.75", 0.75, qty0, 0.001)
        assertEquals("cup", items[0].unit)

        val qty1 = RecipeScalerCalculator.parseQuantity(items[1].qtyString)
        assertEquals("1|2 → 0.5", 0.5, qty1, 0.001)

        val qty6 = RecipeScalerCalculator.parseQuantity(items[6].qtyString)
        assertEquals("4 1|2 → 4.5", 4.5, qty6, 0.001)
        assertEquals("cup", items[6].unit)
        assertEquals("Rolled oats", items[6].name)
    }

    // ── Digit-collapse only fires on volume units ─────────────────────────────
    //
    // Weight/count units legitimately take large integer quantities — "14 oz",
    // "454 g" must NOT be rewritten as collapsed fraction glyphs.

    @Test fun `14 oz stays 14 oz`() {
        val item = parseLine("14 oz can tomatoes")
        assertEquals("14", item?.qtyString)
        assertEquals("oz", item?.unit)
    }

    @Test fun `28 oz stays 28 oz`() {
        val item = parseLine("28 oz crushed tomatoes")
        assertEquals("28", item?.qtyString)
        assertEquals("oz", item?.unit)
    }

    @Test fun `32 oz stays 32 oz`() {
        val item = parseLine("32 oz broth")
        assertEquals("32", item?.qtyString)
        assertEquals("oz", item?.unit)
    }

    @Test fun `12 oz stays 12 oz`() {
        val item = parseLine("12 oz cheese")
        assertEquals("12", item?.qtyString)
        assertEquals("oz", item?.unit)
    }

    @Test fun `454 g stays 454 g`() {
        val item = parseLine("454 g flour")
        assertEquals("454", item?.qtyString)
        assertEquals("g",   item?.unit)
    }

    @Test fun `18 g stays 18 g`() {
        val item = parseLine("18 g salt")
        assertEquals("18", item?.qtyString)
        assertEquals("g",  item?.unit)
    }

    @Test fun `12 ml stays 12 ml`() {
        val item = parseLine("12 ml vanilla")
        assertEquals("12", item?.qtyString)
        assertEquals("ml", item?.unit)
    }

    @Test fun `14 lb stays 14 lb`() {
        val item = parseLine("14 lb turkey")
        assertEquals("14", item?.qtyString)
        assertEquals("lb", item?.unit)
    }

    // ── Layout-aware parsing (parseLines) ─────────────────────────────────────
    //
    // OcrLine coordinates simulate a ~40px line height. Column detection sweeps
    // left-to-right; wrapped-line merge joins a lowercase continuation line to
    // the line above it.

    @Test fun `parseLines orders two-column layout column-by-column`() {
        val lines = listOf(
            // Left column (x 0-400)
            OcrLine("1 cup flour",   left = 0,   top = 0,   right = 400,  bottom = 40),
            OcrLine("2 tbsp sugar",  left = 0,   top = 200, right = 400,  bottom = 240),
            OcrLine("1 tsp salt",    left = 0,   top = 400, right = 400,  bottom = 440),
            // Right column (x 600-1000), interleaved vertically
            OcrLine("1 egg",         left = 600, top = 100, right = 1000, bottom = 140),
            OcrLine("2 eggs",        left = 600, top = 300, right = 1000, bottom = 340),
            OcrLine("3 eggs",        left = 600, top = 500, right = 1000, bottom = 540)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals("should parse 6 ingredients", 6, items.size)
        // All left-column ingredients precede all right-column ones.
        assertEquals("Flour", items[0].name)
        assertEquals("Sugar", items[1].name)
        assertEquals("Salt",  items[2].name)
        assertEquals("Egg",   items[3].name)
        assertEquals("Eggs",  items[4].name)
        assertEquals("Eggs",  items[5].name)
    }

    @Test fun `parseLines merges lowercase wrapped continuation line`() {
        val lines = listOf(
            OcrLine("2 cups all-purpose flour,", left = 0, top = 0,  right = 390, bottom = 40),
            OcrLine("sifted twice",              left = 0, top = 50, right = 200, bottom = 90)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals("wrapped line should merge into one ingredient", 1, items.size)
        // The comma clause is stripped, leaving the base name.
        assertEquals("All-purpose flour", items[0].name)
        assertEquals("cup", items[0].unit)
    }

    @Test fun `parseLines does not merge lowercase standalone ingredient line`() {
        // "salt and pepper to taste" is lowercase with a small gap, but the
        // previous line looks complete — it must remain its own ingredient.
        val lines = listOf(
            OcrLine("1 cup flour",               left = 0, top = 0,  right = 300, bottom = 40),
            OcrLine("salt and pepper to taste",  left = 0, top = 50, right = 300, bottom = 90)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(2, items.size)
        assertEquals("Flour", items[0].name)
        assertEquals("Salt and pepper to taste", items[1].name)
    }

    @Test fun `parseLines merges line ending with conjunction`() {
        val lines = listOf(
            OcrLine("1 cup shredded cheddar and", left = 0, top = 0,  right = 390, bottom = 40),
            OcrLine("mozzarella",                 left = 0, top = 50, right = 200, bottom = 90)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(1, items.size)
        assertEquals("Shredded cheddar and mozzarella", items[0].name)
    }

    @Test fun `parseLines merges indented lowercase continuation`() {
        val lines = listOf(
            OcrLine("1 cup flour", left = 0,  top = 0,  right = 300, bottom = 40),
            OcrLine("sifted",      left = 30, top = 50, right = 200, bottom = 90)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(1, items.size)
        assertEquals("Flour sifted", items[0].name)
    }

    @Test fun `parseLines does not merge a new ingredient line`() {
        val lines = listOf(
            OcrLine("2 cups flour", left = 0, top = 0,  right = 300, bottom = 40),
            OcrLine("1 tsp salt",   left = 0, top = 50, right = 300, bottom = 90)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(2, items.size)
        assertEquals("Flour", items[0].name)
        assertEquals("Salt",  items[1].name)
    }

    @Test fun `parseLines does not merge uppercase line across large gap`() {
        val lines = listOf(
            OcrLine("2 cups flour", left = 0, top = 0,   right = 300, bottom = 40),
            OcrLine("Butter or margarine", left = 0, top = 200, right = 300, bottom = 240)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(2, items.size)
        assertEquals("Flour",              items[0].name)
        assertEquals("Butter or margarine", items[1].name)
    }

    @Test fun `parseLines preserves single-column ordering`() {
        val lines = listOf(
            OcrLine("2 cups flour",  left = 0, top = 400, right = 400, bottom = 440),
            OcrLine("1 cup sugar",   left = 0, top = 0,   right = 400, bottom = 40),
            OcrLine("1 tsp vanilla", left = 0, top = 200, right = 400, bottom = 240)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(3, items.size)
        assertEquals("Sugar",   items[0].name)
        assertEquals("Vanilla", items[1].name)
        assertEquals("Flour",   items[2].name)
    }
}

