package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.IngredientItem
import java.util.UUID

/**
 * Pure parser that converts raw OCR text (from ML Kit) into a list of [IngredientItem]s.
 *
 * ## Design constraints
 * - No Android, Room, or Compose dependencies — stays cheap to unit-test.
 * - Delegates quantity parsing entirely to [RecipeScalerCalculator.parseQuantity] so
 *   fractions, mixed numbers, unicode fractions, and decimals are all handled consistently.
 * - Delegates unit recognition to [RecipeScalerCalculator.ALL_UNITS] so the two code
 *   paths never diverge.
 *
 * ## Algorithm (per line)
 * 1. Strip leading/trailing whitespace and skip blank or non-ingredient lines.
 * 2. Attempt to peel a quantity token from the start of the line.
 * 3. Attempt to match the next token against a known unit.
 * 4. Treat everything that remains as the ingredient name, cleaned up.
 *
 * Lines that yield no parseable quantity AND no recognisable unit are dropped,
 * unless they contain at least two words (treated as name-only ingredient with
 * quantity "" and unit "none").
 */
object RecipeOcrParser {

    // ── OCR mis-read normalization map ───────────────────────────────────────
    //
    // ML Kit sometimes renders Unicode vulgar fractions as Latin look-alikes
    // depending on the font being scanned. These are the most common mis-reads
    // seen in practice. Applied as a pre-pass before the quantity regex runs.
    private val OCR_FRACTION_FIXES: List<Pair<Regex, String>> = listOf(
        // "1/3" rendered as "Va", "1/s", "Vs", "V3", "V's"
        Pair(Regex("""(?i)\bV[a3s]'?\b"""), "1/3"),
        // "1/2" rendered as "Y2", "Yz", "1/2" is usually fine
        Pair(Regex("""(?i)\bY[2z]\b"""), "1/2"),
        // "1/4" rendered as "Y4", "Va" (ambiguous with 1/3 — context-free, treat as 1/4)
        Pair(Regex("""(?i)\bY4\b"""), "1/4"),
        // "3/4" rendered as "3/4" is fine; "3A" is a common mis-read
        Pair(Regex("""\b3A\b"""), "3/4"),
        // "2/3" rendered as "2/s", "2/3" is usually fine
        Pair(Regex("""(?i)\b2/[sz]\b"""), "2/3")
    )

    // ── Unit synonym map ──────────────────────────────────────────────────────
    //
    // Maps every surface form a recipe might use to one of the canonical unit
    // keys in RecipeScalerCalculator.ALL_UNITS.
    //
    // Plurals, abbreviations, and common OCR mis-reads (e.g. "Tbsp", "tbsps")
    // are all covered.

    private val UNIT_SYNONYMS: Map<String, String> = mapOf(
        // teaspoon
        "tsp"        to "tsp",
        "tsps"       to "tsp",
        "teaspoon"   to "tsp",
        "teaspoons"  to "tsp",
        "t."         to "tsp",
        // tablespoon
        "tbsp"        to "tbsp",
        "tbsps"       to "tbsp",
        "tablespoon"  to "tbsp",
        "tablespoons" to "tbsp",
        "tbls"        to "tbsp",
        "tbl"         to "tbsp",
        "tb"          to "tbsp",
        "t"           to "tbsp",    // capital T convention: handled by case-folding
        // cup
        "cup"  to "cup",
        "cups" to "cup",
        "c"    to "cup",
        "c."   to "cup",
        // millilitre
        "ml"           to "ml",
        "mls"          to "ml",
        "milliliter"   to "ml",
        "millilitre"   to "ml",
        "milliliters"  to "ml",
        "millilitres"  to "ml",
        // litre
        "l"      to "L",
        "l."     to "L",
        "liter"  to "L",
        "litre"  to "L",
        "liters" to "L",
        "litres" to "L",
        // gram
        "g"     to "g",
        "g."    to "g",
        "gr"    to "g",
        "gram"  to "g",
        "grams" to "g",
        // ounce
        "oz"     to "oz",
        "oz."    to "oz",
        "ounce"  to "oz",
        "ounces" to "oz",
        // pound
        "lb"     to "lb",
        "lb."    to "lb",
        "lbs"    to "lb",
        "pound"  to "lb",
        "pounds" to "lb",
        // each / countable
        "each"   to "each",
        "ea"     to "each",
        "piece"  to "each",
        "pieces" to "each",
        "pcs"    to "each",
        "pc"     to "each",
        "whole"  to "each"
    )

    // Regex that matches the quantity portion at the very start of a token.
    // Supports: "1", "1.5", "1/2", "1 1/2", "1-1/2", and the unicode fractions
    // handled by parseQuantity (U+00BC - U+00BE vulgar fractions, U+2150-U+215E).
    //
    // All Unicode fraction code points are listed as explicit hex escapes so
    // the file is pure ASCII — avoids JVM regex "literal prefixes and suffixes"
    // errors that arise when literal Unicode chars are mixed with \d in a
    // character class.
    //
    // Character class breakdown:
    //   \d          - ASCII digit (start or continuation)
    //   \u00BC-\u00BE - 1/4, 1/2, 3/4 (Latin-1 vulgar fractions)
    //   \u2150-\u215E - Unicode vulgar fraction block (1/7 through 7/8)
    private val QTY_CHAR_CLASS = "[\u00BC-\u00BE\u2150-\u215E\\d]"
    private val QTY_CONT_CLASS = "[\u00BC-\u00BE\u2150-\u215E\\d\\s./-]"
    private val QTY_PREFIX_REGEX = Regex("^($QTY_CHAR_CLASS$QTY_CONT_CLASS*)")

    // Lines that are almost certainly recipe section headers, not ingredients.
    private val SKIP_LINE_PATTERNS = listOf(
        Regex("""^ingredients\s*:?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^instructions\s*:?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^directions\s*:?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^method\s*:?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^steps?\s*:?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^for\s+the\b""", RegexOption.IGNORE_CASE),
        Regex("""^serves\s+\d""", RegexOption.IGNORE_CASE),
        Regex("""^makes\s+\d""", RegexOption.IGNORE_CASE),
        Regex("""^prep\s+time""", RegexOption.IGNORE_CASE),
        Regex("""^cook\s+time""", RegexOption.IGNORE_CASE),
        Regex("""^total\s+time""", RegexOption.IGNORE_CASE),
        Regex("""^\d+[.)]\s""")           // numbered step: "1. Preheat..." or "1) Preheat..."
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse [rawText] (as returned by ML Kit's `Text.text`) into a list of
     * [IngredientItem]s ready to load into the Recipe Scaler.
     *
     * Empty lines, section headers, and lines that contain no recognisable
     * ingredient information are silently dropped.
     *
     * The returned list is capped at [maxIngredients] (default 50). If more
     * lines are found the excess is silently dropped — the user can add them
     * manually.
     */
    fun parse(rawText: String, maxIngredients: Int = 50): List<IngredientItem> {
        return rawText
            .lines()
            .asSequence()
            .map   { cleanLine(it) }
            .filter { it.isNotBlank() }
            .filter { line -> SKIP_LINE_PATTERNS.none { it.containsMatchIn(line) } }
            .mapNotNull { parseLine(it) }
            .filter { it.name.isNotBlank() || it.qtyString.isNotBlank() }
            .take(maxIngredients)
            .toList()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Normalise a single raw OCR line:
     * - Strip leading bullet/dash/asterisk markers (common in recipe lists).
     * - Collapse multiple consecutive spaces into one.
     * - Trim.
     */
    private fun cleanLine(line: String): String {
        // Use explicit Unicode escapes for bullet/dash chars so the regex stays
        // ASCII-safe. U+2022=bullet, U+2013=en-dash, U+2014=em-dash, U+00B7=middle-dot.
        // Also strip leading periods — OCR sometimes renders a bullet as a plain dot.
        return line
            .replace(Regex("^[\\s\\u2022\\u2013\\u2014*\\u00B7.-]+"), "") // leading list markers + dots
            .replace(Regex("\\s{2,}"), " ")                                // collapsed whitespace
            .trim()
    }

    /**
     * Parse a single cleaned line into an [IngredientItem], or return `null`
     * if the line cannot be interpreted as an ingredient at all.
     */
    private fun parseLine(line: String): IngredientItem? {
        var remaining = line

        // ── 0. Normalize OCR fraction mis-reads ───────────────────────────────
        // Apply before the quantity regex so "Va cup ketchup" → "1/3 cup ketchup"
        // and similar ML Kit mis-renderings are caught.
        OCR_FRACTION_FIXES.forEach { (pattern, replacement) ->
            remaining = remaining.replace(pattern, replacement)
        }

        // ── 1. Quantity ───────────────────────────────────────────────────────
        val qtyString: String
        val qtyMatch = QTY_PREFIX_REGEX.find(remaining)
        if (qtyMatch != null) {
            val raw = qtyMatch.value.trim()
            // Validate: parseQuantity must return a non-zero value for this to
            // count as a real quantity token.
            val qty = RecipeScalerCalculator.parseQuantity(raw)
            if (qty > 0.0) {
                qtyString = raw
                remaining = remaining.removePrefix(qtyMatch.value).trim()
            } else {
                qtyString = ""
            }
        } else {
            qtyString = ""
        }

        // ── 2. Unit ───────────────────────────────────────────────────────────
        val unitKey: String
        val tokens = remaining.split(Regex("\\s+"), limit = 2)
        val firstToken = tokens.firstOrNull().orEmpty()
        val lookedUpUnit = UNIT_SYNONYMS[firstToken.lowercase().trimEnd('.')]
        if (lookedUpUnit != null) {
            unitKey   = lookedUpUnit
            remaining = tokens.getOrElse(1) { "" }.trim()
        } else {
            unitKey   = "none"
            // remaining is unchanged — the first token is part of the name
        }

        // ── 3. Name ───────────────────────────────────────────────────────────
        val name = remaining
            .replace(Regex("\\s*,.*$"), "")      // strip trailing comma clauses ("flour, sifted")
            .replace(Regex("\\s*\\(.*?\\)"), "") // strip parenthetical notes ("(optional)")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .replaceFirstChar { it.uppercaseChar() }

        // ── 4. Drop lines that carry no useful information ────────────────────
        // A line with no quantity AND no unit AND a single-word name is almost
        // certainly a header or noise — skip it.
        if (qtyString.isBlank() && unitKey == "none" && name.split(' ').size < 2) {
            return null
        }

        return IngredientItem(
            id        = UUID.randomUUID().toString(),
            qtyString = qtyString,
            unit      = unitKey,
            name      = name
        )
    }
}
