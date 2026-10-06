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

    // ── Leading-decimal quantities ────────────────────────────────────────────
    //
    // A ".5" written without a leading zero must survive cleanLine's
    // leading-marker strip (a '.' followed by a digit is a decimal point, not a
    // bullet) and parse as 0.5 — not 5.

    @Test fun `leading dot decimal point 5 cup → half cup`() {
        val item = parseLine(".5 cup milk")
        assertEquals("0.5",  item?.qtyString)
        assertEquals("cup",  item?.unit)
        assertEquals("Milk", item?.name)
    }

    @Test fun `leading dot decimal after bullet marker → half cup`() {
        val item = parseLine("* .5 cup milk")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.5", 0.5, qty, 0.001)
        assertEquals("cup", item?.unit)
    }

    @Test fun `leading period followed by space is still stripped as bullet`() {
        // ". chopped parsley" — '.' before a space is a bullet artefact, not a decimal.
        val item = parseLine(". chopped parsley")
        assertEquals("Chopped parsley", item?.name)
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

    // ── Handwritten-card: t/T/c unit conventions ─────────────────────────────
    //
    // Bare "t" = teaspoon, "T" = tablespoon, "c"/"C." = cup — the convention
    // used on handwritten index-card recipes.

    @Test fun `handwritten T period → tbsp`() {
        val item = parseLine("1 T. oil")
        assertEquals("1",    item?.qtyString)
        assertEquals("tbsp", item?.unit)
        assertEquals("Oil",  item?.name)
    }

    @Test fun `handwritten bare T → tbsp`() {
        val item = parseLine("1 T olive oil")
        assertEquals("1",         item?.qtyString)
        assertEquals("tbsp",      item?.unit)
        assertEquals("Olive oil", item?.name)
    }

    @Test fun `handwritten bare t → tsp`() {
        val item = parseLine("1 t salt")
        assertEquals("1",     item?.qtyString)
        assertEquals("tsp",   item?.unit)
        assertEquals("Salt",  item?.name)
    }

    @Test fun `handwritten bare C no period → cup`() {
        val item = parseLine("2 C sugar")
        assertEquals("2",     item?.qtyString)
        assertEquals("cup",   item?.unit)
        assertEquals("Sugar", item?.name)
    }

    @Test fun `cent sign mis-read of c → cup`() {
        // Handwritten "c." is often OCR'd as a cent sign.
        val item = parseLine("1 ¢ sugar")
        assertEquals("1",     item?.qtyString)
        assertEquals("cup",   item?.unit)
        assertEquals("Sugar", item?.name)
    }

    // ── Handwritten-card: unit punctuation mis-reads ─────────────────────────
    //
    // The dot after a handwritten unit letter frequently OCRs as a comma,
    // colon, or semicolon instead of a period.

    @Test fun `handwritten C comma → cup`() {
        val item = parseLine("3/4 C, butter")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.75", 0.75, qty, 0.001)
        assertEquals("cup",    item?.unit)
        assertEquals("Butter", item?.name)
    }

    @Test fun `handwritten t colon → tsp`() {
        val item = parseLine("1 t: vanilla extract")
        assertEquals("1",               item?.qtyString)
        assertEquals("tsp",             item?.unit)
        assertEquals("Vanilla extract", item?.name)
    }

    @Test fun `handwritten T comma → tbsp`() {
        val item = parseLine("2 T, sugar")
        assertEquals("tbsp", item?.unit)
    }

    // ── Handwritten-card: margin artefacts ───────────────────────────────────
    //
    // Ruled index cards with a printed checkbox column can produce stray
    // glyphs at the start of every line.

    @Test fun `leading pipe artefact stripped`() {
        val item = parseLine("| 1/2 C. honey")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.5", 0.5, qty, 0.001)
        assertEquals("cup",   item?.unit)
        assertEquals("Honey", item?.name)
    }

    @Test fun `leading checkbox glyph stripped`() {
        val item = parseLine("☐ 1 t. vanilla extract")
        assertEquals("1",               item?.qtyString)
        assertEquals("tsp",             item?.unit)
        assertEquals("Vanilla extract", item?.name)
    }

    @Test fun `leading bracket pair stripped`() {
        val item = parseLine("[ ] 2 tbsp sugar")
        assertEquals("2",     item?.qtyString)
        assertEquals("tbsp",  item?.unit)
        assertEquals("Sugar", item?.name)
    }

    @Test fun `leading check mark stripped`() {
        val item = parseLine("✓ 1 C. chocolate chips")
        assertEquals("1",               item?.qtyString)
        assertEquals("cup",             item?.unit)
        assertEquals("Chocolate chips", item?.name)
    }

    // ── Real-scan mis-reads from a handwritten index card ────────────────────
    //
    // Tokens below are drawn from an actual ML Kit scan of the "3/4 C. butter"
    // style card: "Lt Vanilla extra" (= "1 t. vanilla extract"),
    // "4 YaColled ts" (= "4 ½ C. rolled oats").

    @Test fun `ml-kit Lt fused at line start → 1 tsp`() {
        val item = parseLine("Lt Vanilla extra")
        assertEquals("1",              item?.qtyString)
        assertEquals("tsp",            item?.unit)
        assertEquals("Vanilla extra",  item?.name)
    }

    @Test fun `ml-kit lt period fused at line start → 1 tsp`() {
        val item = parseLine("lt. baking soda")
        assertEquals("tsp",         item?.unit)
        assertEquals("Baking soda", item?.name)
    }

    @Test fun `ml-kit l space t period → 1 tsp`() {
        val item = parseLine("l t. vanilla extract")
        assertEquals("1",               item?.qtyString)
        assertEquals("tsp",             item?.unit)
        assertEquals("Vanilla extract", item?.name)
    }

    @Test fun `ml-kit 1t period fused → 1 tsp`() {
        val item = parseLine("1t. vanilla")
        assertEquals("1",       item?.qtyString)
        assertEquals("tsp",     item?.unit)
        assertEquals("Vanilla", item?.name)
    }

    @Test fun `ml-kit V1T period fused → 1 tbsp`() {
        val item = parseLine("V1T. oil")
        assertEquals("1",    item?.qtyString)
        assertEquals("tbsp", item?.unit)
        assertEquals("Oil",  item?.name)
    }

    @Test fun `ml-kit YaColled → 4 and a half cup`() {
        // "4 YaColled ts" — handwritten "4 ½ C. rolled oats" mis-read with the
        // fraction fused to the capital C. The fused C confirms the cup token,
        // so the fix re-emits it: unit is recovered, name keeps the fused C.
        val item = parseLine("4 YaColled ts")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 4.5", 4.5, qty, 0.001)
        assertEquals("cup",        item?.unit)
        // Post-OCR correction turns "Colled" into "Rolled"; "ts" is ≤3 chars
        // and is left alone.
        assertEquals("Rolled ts",  item?.name)
    }

    @Test fun `ml-kit Jy fused → three quarter cup`() {
        // "Jy buter, meltd (2 sthcks)" — "¾ C. butter, melted (1½ sticks)" with
        // "3/4 C." compressed into "Jy".
        val item = parseLine("Jy buter, meltd (2 sthcks)")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 0.75", 0.75, qty, 0.001)
        assertEquals("cup",   item?.unit)
        assertEquals("Butter", item?.name)
    }

    @Test fun `ml-kit Vaci fused → third cup blank name`() {
        // A whole line collapsed to "Vaci" — "⅓ C." fused; the ingredient name
        // was lost by OCR entirely. Keeping the qty+unit row lets the user
        // fill the name in instead of silently dropping the line.
        val item = parseLine("Vaci")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be ~0.333", 1.0 / 3.0, qty, 0.002)
        assertEquals("cup", item?.unit)
    }

    @Test fun `leading pipe fused to letter → 1`() {
        // "|C whole wheat flocor" — "|" is the digit 1 mis-read, fused to "C".
        val item = parseLine("|C whole wheat flocor")
        assertEquals("1",                  item?.qtyString)
        assertEquals("cup",                item?.unit)
        assertEquals("Whole wheat flour",  item?.name)
    }

    @Test fun `ml-kit 1t fused no period → 1 tsp`() {
        val item = parseLine("1t bakina sOda")
        assertEquals("1",           item?.qtyString)
        assertEquals("tsp",         item?.unit)
        // "bakina" → "baking"; "sOda" lowercases to a dictionary word so its
        // odd capitalization is left alone.
        assertEquals("Baking sOda", item?.name)
    }

    @Test fun `paren or-note mis-read as Cor is stripped`() {
        // "chips Cor any exas" = "chips (or any extras)" with '(' → 'C'.
        val item = parseLine("lc chocolat chps Cor any exas")
        assertEquals("1",               item?.qtyString)
        assertEquals("cup",             item?.unit)
        assertEquals("Chocolate chips", item?.name)
    }

    @Test fun `real ml-kit scan of handwritten card parses all lines`() {
        // Exact logcat output from a real scan of the "¾ C. butter" index card.
        val raw = """
            Jy buter, meltd (2 sthcks)
            Vaci
            Lt Vanilla extract
            |C whole wheat flocor
            1t bakina sOda
            4 YaColled ts
            lc chocolat chps Cor any exas
        """.trimIndent()
        val items = RecipeOcrParser.parse(raw)
        assertEquals("should parse 7 rows", 7, items.size)

        val q0 = RecipeScalerCalculator.parseQuantity(items[0].qtyString)
        assertEquals(0.75, q0, 0.001); assertEquals("cup", items[0].unit)

        val q1 = RecipeScalerCalculator.parseQuantity(items[1].qtyString)
        assertEquals(1.0 / 3.0, q1, 0.002); assertEquals("cup", items[1].unit)

        assertEquals("1",   items[2].qtyString); assertEquals("tsp", items[2].unit)
        assertEquals("Vanilla extract", items[2].name)

        assertEquals("1",   items[3].qtyString); assertEquals("cup", items[3].unit)

        assertEquals("1",   items[4].qtyString); assertEquals("tsp", items[4].unit)

        val q5 = RecipeScalerCalculator.parseQuantity(items[5].qtyString)
        assertEquals(4.5, q5, 0.001); assertEquals("cup", items[5].unit)

        assertEquals("1",   items[6].qtyString); assertEquals("cup", items[6].unit)
        assertEquals("Chocolate chips", items[6].name)
    }

    @Test fun `mid-line it is NOT rewritten to 1 tsp`() {
        // Guard: the fused [LI]t rule is line-start only — a real "it" mid-line
        // must survive untouched.
        val item = parseLine("add it to taste")
        assertEquals("",                  item?.qtyString)
        assertEquals("none",              item?.unit)
        assertEquals("Add it to taste",   item?.name)
    }

    @Test fun `chicken not split into cup hicken`() {
        // Guard: a capital C fused to lowercase text is a real word, not "C.".
        val item = parseLine("2 Chicken breasts")
        assertEquals("2",               item?.qtyString)
        assertEquals("Chicken breasts", item?.name)
    }

    @Test fun `ml-kit 14 C comma → 1 quarter cup`() {
        // Digit-collapse now anchors through trailing punctuation too.
        val item = parseLine("14 C, sugar")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 1.25", 1.25, qty, 0.001)
        assertEquals("cup",   item?.unit)
        assertEquals("Sugar", item?.name)
    }

    @Test fun `ml-kit 12 t period → 1 and a half tsp`() {
        // "t." is a valid volume anchor for digit-collapse.
        val item = parseLine("12 t. salt")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 1.5", 1.5, qty, 0.001)
        assertEquals("tsp",  item?.unit)
        assertEquals("Salt", item?.name)
    }

    @Test fun `ml-kit Lcup comma → 1 cup`() {
        val item = parseLine("Lcup, shredded cheddar")
        assertEquals("1",                item?.qtyString)
        assertEquals("cup",              item?.unit)
        assertEquals("Shredded cheddar", item?.name)
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

    @Test fun `parseLines flags rows below the confidence threshold`() {
        val lines = listOf(
            OcrLine("1 cup flour",         left = 0, top = 0,  right = 300, bottom = 40,  confidence = 0.95f),
            OcrLine("1 tsp bakina soda",   left = 0, top = 50, right = 300, bottom = 90,  confidence = 0.3f)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(2, items.size)
        assertEquals(false, items[0].lowConfidence)
        assertEquals(true,  items[1].lowConfidence)
    }

    @Test fun `parseLines leaves flag false when confidence is null`() {
        val lines = listOf(
            OcrLine("1 cup flour", left = 0, top = 0, right = 300, bottom = 40)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(1, items.size)
        assertEquals(false, items[0].lowConfidence)
    }

    @Test fun `parseLines merged wrapped line takes the lower confidence`() {
        val lines = listOf(
            OcrLine("1 cup cheddar and", left = 0, top = 0,  right = 390, bottom = 40, confidence = 0.9f),
            OcrLine("mozzarella",        left = 0, top = 45, right = 200, bottom = 85, confidence = 0.2f)
        )
        val items = RecipeOcrParser.parseLines(lines)
        assertEquals(1, items.size)
        assertEquals(true, items[0].lowConfidence)
    }

    @Test fun `parse from raw text never flags rows`() {
        val item = RecipeOcrParser.parse("1 cup flour").firstOrNull()
        assertEquals(false, item?.lowConfidence)
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

    // ── Post-OCR ingredient name correction (plan item 5) ───────────────────
    //
    // Garbled name words are corrected against the bounded ingredient
    // dictionary after name extraction, before capitalization.

    @Test fun `ocr garbled name buter corrected to Butter`() {
        val item = parseLine("1 cup buter")
        assertEquals("1",       item?.qtyString)
        assertEquals("cup",     item?.unit)
        assertEquals("Butter",  item?.name)
    }

    @Test fun `ocr garbled multi-word name corrected`() {
        // "lc." = fused "1 c." mis-read; "Cor any extras" is the existing
        // "(or …" paren-note rule. Corrector fixes "chcolate chps".
        val item = parseLine("lc. chcolate chps Cor any extras")
        assertEquals("1",               item?.qtyString)
        assertEquals("cup",             item?.unit)
        assertEquals("Chocolate chips", item?.name)
    }

    @Test fun `ocr YaColled name corrected to Rolled`() {
        // "4 YaColled ts" — the Ya(?=C) rule recovers qty 4½ + cup; the
        // corrector then fixes "Colled" → "Rolled". "ts" is ≤3 chars and
        // is left untouched.
        val item = parseLine("4 YaColled ts")
        val qty  = RecipeScalerCalculator.parseQuantity(item?.qtyString ?: "")
        assertEquals("qty should be 4.5", 4.5, qty, 0.001)
        assertEquals("cup",       item?.unit)
        assertEquals("Rolled ts", item?.name)
    }
}

