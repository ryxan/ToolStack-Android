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
    // ML Kit sometimes reads "1¼" as "14" (the ¼ glyph collapses into its
    // denominator digit). We can only safely rewrite when the next token is a
    // recognised unit word — "14 cups" fires but "14 eggs" does not.
    //
    // Unit-anchored lookahead: the pattern is kept as a plain String and
    // concatenated (not interpolated) into each Regex constructor call so the
    // regex $ anchors inside it are never mistaken for Kotlin string templates.
    private val UNIT_LOOKAHEAD: String = buildString {
        // Case-insensitive lookahead that matches " <unit>" at the end of the
        // quantity token. The unit list mirrors UNIT_SYNONYMS (longest forms first
        // so the alternation matches greedily). The trailing (?:\s|$) anchor
        // ensures we match a full unit word, not a prefix.
        append("(?i)(?=\\s+(?:")
        append("tablespoons|tablespoon|teaspoons|teaspoon|")
        append("milliliters|millilitres|milliliter|millilitre|")
        append("litres|liters|pounds|pieces|ounces|ounce|")
        append("litre|liter|grams|gram|tbsps|tbsp|tbls|tsps|")
        append("cups|cup|each|lbs|mls|tsp|pcs|")
        append("oz\\.|oz|lb\\.|lb|g\\.|g|c\\.|c|ea|pc|l\\.|l|T|t")
        append(")(?:\\s|\$))")
    }

    private val OCR_FRACTION_FIXES: List<Pair<Regex, String>> = listOf(
        // ── Unicode vulgar fraction glyph collapsed into surrounding digits ──
        //
        // ML Kit reads "1¼" as "14", "1½" as "12", "1¾" as "134", etc.
        // Ordered longest suffix first so "134" (→ 1¾) is tried before "14" (→ 1¼).
        Pair(Regex("(?<!\\d)(\\d+)34$UNIT_LOOKAHEAD"), "$1 3/4"),  // "134 cups" → "1 3/4 cups"
        Pair(Regex("(?<!\\d)(\\d+)23$UNIT_LOOKAHEAD"), "$1 2/3"),  // "123 ml"   → "1 2/3 ml"
        Pair(Regex("(?<!\\d)(\\d+)38$UNIT_LOOKAHEAD"), "$1 3/8"),  // "138 oz"   → "1 3/8 oz"
        Pair(Regex("(?<!\\d)(\\d+)58$UNIT_LOOKAHEAD"), "$1 5/8"),  // "158 oz"   → "1 5/8 oz"
        Pair(Regex("(?<!\\d)(\\d+)78$UNIT_LOOKAHEAD"), "$1 7/8"),  // "178 oz"   → "1 7/8 oz"
        Pair(Regex("(?<!\\d)(\\d+)2$UNIT_LOOKAHEAD"),  "$1 1/2"),  // "12 cups"  → "1 1/2 cups"
        Pair(Regex("(?<!\\d)(\\d+)3$UNIT_LOOKAHEAD"),  "$1 1/3"),  // "13 cups"  → "1 1/3 cups"
        Pair(Regex("(?<!\\d)(\\d+)4$UNIT_LOOKAHEAD"),  "$1 1/4"),  // "14 cups"  → "1 1/4 cups"
        Pair(Regex("(?<!\\d)(\\d+)8$UNIT_LOOKAHEAD"),  "$1 1/8"),  // "18 tsp"   → "1 1/8 tsp"
        // ── Latin look-alike mis-reads ────────────────────────────────────────
        // "1/3" rendered as "Va", "Vs", "V3"
        Pair(Regex("""(?im)(?:^|(?<=\s))V[a3s]'?(?=\s|$)"""), "1/3"),
        // "1/2" rendered as "Y2", "Yz"
        Pair(Regex("""(?i)\bY[2z]\b"""), "1/2"),
        // "1/4" rendered as "Y4"
        Pair(Regex("""(?i)\bY4\b"""), "1/4"),
        // "3/4" rendered as "3A"
        Pair(Regex("""\b3A\b"""), "3/4"),
        // "2/3" rendered as "2/s"
        Pair(Regex("""(?i)\b2/[sz]\b"""), "2/3"),

        // ── Digit-1 read as standalone "L" or "I" before a unit word ────────
        //
        // ML Kit sometimes reads the digit "1" as the capital letter "L" or "I"
        // and leaves it as a separate space-separated token before the unit word.
        // Examples seen in production:
        //
        //   "1 cup shredded cheddar" → "L cup shredded cheddar"
        //   "1 cup milk"             → "L cup milk"
        //
        // Pattern: at line start (or after whitespace), a bare [LI] followed by
        // exactly one space and then a known unit word. Must not match common
        // English words — the lookahead on both sides is tight enough to prevent
        // false-positives.
        Pair(
            Regex("""(?:(?<=^)|(?<=\s))[LI]\s(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c)(?=\s|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            "1 $1"
        ),

        // ── Digit-1 fused directly to unit word (no space) ───────────────────
        //
        // ML Kit sometimes reads the digit "1" as the capital letter "L" (or "I")
        // and fuses it directly onto the following unit word when no space was
        // detected between them. Examples from the Cheesy Bread recipe:
        //
        //   "1 cup shredded cheddar" → "Lcup shredded cheddar"
        //   "1 cup milk"             → "Lcup milk"
        //   "1 Tbsp chopped parsley" → "V1Tbsp chopped parsley"  (leading V artefact)
        //
        // Pattern: at line start (or after whitespace), an [LI] immediately followed
        // (no space) by a known unit word. The unit word boundary is enforced by a
        // lookahead so the regex matches the full "Lcup" token but not a word like
        // "Lemon" that merely starts with L.
        //
        // The unit alternation mirrors UNIT_SYNONYMS (longest first for greedy match).
        // "Lcup" → "1 cup", "Ltbsp" → "1 tbsp", "Itsp" → "1 tsp", etc.
        Pair(
            Regex("""(?:(?<=^)|(?<=\s))[LI](tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c)(?=\s|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            "1 $1"
        ),

        // ── "V<digit>Unit" leading-V artefact fused with digit + unit ─────────
        //
        // ML Kit occasionally prepends a spurious "V" before a digit-unit token,
        // e.g. "V1Tbsp" or "V1cup".  The existing Va/Vs/V3 rule only covers
        // fraction forms; this handles the fused-quantity variant.
        //
        //   "V1Tbsp chopped parsley" → "1 Tbsp chopped parsley"
        //   "V2cup flour"            → "2 cup flour"
        Pair(
            Regex("""(?:(?<=^)|(?<=\s))V(\d+)(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c)(?=\s|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            "$1 $2"
        ),

        // ── Digit fused directly to unit word (no space, starts with digit) ──
        //
        // On some fonts/images ML Kit reads "1cup" (no space) or "2tbsp".
        // This inserts the missing space so the unit-recognition step finds it.
        //
        //   "1cup shredded cheddar" → "1 cup shredded cheddar"
        //   "2tbsp butter"          → "2 tbsp butter"
        Pair(
            Regex("""(?<=\d)(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c)(?=\s|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            " $1"
        )
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
        // Note: bare "t" and "t." are intentionally absent — lowercase "t" is the
        // teaspoon convention and capital "T" is tablespoon. These are resolved
        // case-sensitively in the unit lookup below before any lowercasing occurs.
        // tablespoon
        "tbsp"        to "tbsp",
        "tbsps"       to "tbsp",
        "tablespoon"  to "tbsp",
        "tablespoons" to "tbsp",
        "tbls"        to "tbsp",
        "tbl"         to "tbsp",
        "tb"          to "tbsp",
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

    // Regex that matches the quantity portion at the very start of a line.
    // Supports: "1", "1.5", "1/2", "1 1/2", "1-1/2", "1¼", and the Unicode
    // vulgar fractions in U+00BC-U+00BE and U+2150-U+215E.
    //
    // All Unicode code points are explicit hex escapes so the file stays ASCII-safe.
    //
    // The pattern is structured to match exactly the quantity token and no more:
    //
    //   ^                        – start of (cleaned) line
    //   (FRAC_CHAR+)             – one or more fraction/digit chars  → whole number OR standalone fraction glyph
    //   (                        – optionally followed by ONE of:
    //     [\s-]+FRAC_CHAR+/FRAC_CHAR+   – mixed number: "1 1/4" or "1-1/4"
    //   | [./]FRAC_CHAR+                – decimal "1.5" or slash-fraction continuation "1/2"
    //   )?
    //
    // This deliberately excludes a bare space followed by a letter, so "1¼ cups"
    // does NOT extend the match into "cups". The [\s-]+ is only followed by more
    // digit/fraction chars and a slash (the mixed-number form), never bare words.
    private val FRAC_CHAR = "[\u00BC-\u00BE\u2150-\u215E\\d]"
    private val QTY_PREFIX_REGEX = Regex(
        """^($FRAC_CHAR+(?:[\s-]+$FRAC_CHAR+/$FRAC_CHAR+|[./]$FRAC_CHAR+)?)"""
    )

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
        // Resolve bare "T" (tablespoon) and "t" / "t." (teaspoon) case-sensitively
        // BEFORE lowercasing, so punctuation trimming cannot collapse the distinction.
        val lookedUpUnit = when (firstToken.trimEnd('.')) {
            "T"  -> "tbsp"
            "t"  -> "tsp"
            else -> UNIT_SYNONYMS[firstToken.lowercase().trimEnd('.')]
        }
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
