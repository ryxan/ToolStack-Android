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

    /**
     * Lines whose ML Kit recognition confidence falls below this are surfaced
     * as `lowConfidence` on the parsed [IngredientItem]. Starting point only —
     * tune from real scans (see plan item 4 in
     * docs/recipe-scaler-ocr-accuracy-plan.md).
     */
    const val LOW_CONFIDENCE_THRESHOLD = 0.6f

    // ── OCR mis-read normalization map ───────────────────────────────────────
    //
    // ML Kit sometimes reads "1¼" as "14" (the ¼ glyph collapses into its
    // denominator digit). We can only safely rewrite when the next token is a
    // recognised VOLUME unit word — "14 cups" fires but "14 eggs" and
    // "14 oz" (a real weight quantity) do not.
    //
    // Volume-unit-anchored lookahead: the pattern is kept as a plain String and
    // concatenated (not interpolated) into each Regex constructor call so the
    // regex $ anchors inside it are never mistaken for Kotlin string templates.
    //
    // Restricted to VOLUME units (cup / tbsp / tsp family): weight and count
    // units like "oz", "g", "lb" legitimately take large integer quantities
    // ("14 oz can tomatoes", "454 g flour"), so rewriting them as collapsed
    // fractions would corrupt real quantities.
    private val VOLUME_UNIT_LOOKAHEAD: String = buildString {
        // Case-insensitive lookahead that matches " <volume unit>" at the end of
        // the quantity token. The unit list is the volume subset of UNIT_SYNONYMS
        // (longest forms first so the alternation matches greedily). The
        // trailing (?:\s|$) anchor ensures we match a full unit word, not a prefix.
        append("(?i)(?=\\s+(?:")
        append("tablespoons|tablespoon|teaspoons|teaspoon|")
        append("tbsps|tbsp|tbls|tsps|tsp|cups|cup|c\\.|c|t\\.|T|t")
        append(")(?:[\\s.,:;]|\$))")
    }

    private val OCR_FRACTION_FIXES: List<Pair<Regex, String>> = listOf(
        // ── Unicode vulgar fraction glyph collapsed into surrounding digits ──
        //
        // ML Kit reads "1¼" as "14", "1½" as "12", "1¾" as "134", etc.
        // Ordered longest suffix first so "134" (→ 1¾) is tried before "14" (→ 1¼).
        // Anchored to volume units only — "14 oz" / "454 g" are real quantities.
        Pair(Regex("(?<!\\d)(\\d+)34$VOLUME_UNIT_LOOKAHEAD"), "$1 3/4"),  // "134 cups" → "1 3/4 cups"
        Pair(Regex("(?<!\\d)(\\d+)23$VOLUME_UNIT_LOOKAHEAD"), "$1 2/3"),  // "123 cups" → "1 2/3 cups"
        Pair(Regex("(?<!\\d)(\\d+)38$VOLUME_UNIT_LOOKAHEAD"), "$1 3/8"),  // "138 tsp"  → "1 3/8 tsp"
        Pair(Regex("(?<!\\d)(\\d+)58$VOLUME_UNIT_LOOKAHEAD"), "$1 5/8"),  // "158 tsp"  → "1 5/8 tsp"
        Pair(Regex("(?<!\\d)(\\d+)78$VOLUME_UNIT_LOOKAHEAD"), "$1 7/8"),  // "178 tsp"  → "1 7/8 tsp"
        Pair(Regex("(?<!\\d)(\\d+)2$VOLUME_UNIT_LOOKAHEAD"),  "$1 1/2"),  // "12 cups"  → "1 1/2 cups"
        Pair(Regex("(?<!\\d)(\\d+)3$VOLUME_UNIT_LOOKAHEAD"),  "$1 1/3"),  // "13 cups"  → "1 1/3 cups"
        Pair(Regex("(?<!\\d)(\\d+)4$VOLUME_UNIT_LOOKAHEAD"),  "$1 1/4"),  // "14 cups"  → "1 1/4 cups"
        Pair(Regex("(?<!\\d)(\\d+)8$VOLUME_UNIT_LOOKAHEAD"),  "$1 1/8"),  // "18 tsp"   → "1 1/8 tsp"
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
        // "½ C." fused mis-read — a handwritten "1/2" before a capital "C" can
        // come back as "Ya" fused to it ("4 YaColled" = "4 ½ C. rolled"). The
        // fused C is a confirmed cup token, so the replacement re-emits it as
        // the unit. Scoped to a capital C immediately after "Ya" so real words
        // like "Yams" or "yacon" cannot false-positive.
        Pair(Regex("""\bYa(?=C)"""), "1/2 c "),
        // "¾ C." fused — same compression pattern: cursive "3/4 C" read as
        // "Jy" (J≈3, y≈4) with the unit letter swallowed into the token.
        // Observed: "Jy buter, meltd (2 sthcks)" = "¾ C. butter, melted".
        Pair(Regex("""\bJy\b"""), "3/4 c "),
        // "⅓ C." fused — "Va" (the 1/3 mis-read above) + "ci" ("C." with the
        // period read as "i"). The ingredient name is typically lost entirely;
        // emitting qty+unit preserves the row so the user can fill it in.
        // Observed: a bare "Vaci" line on the same handwritten card.
        Pair(Regex("""\bVaci?\b"""), "1/3 c "),

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
            Regex("""(?:(?<=^)|(?<=\s))[LI]\s(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c|t\.?)(?=[\s.,:;]|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
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
        // Bare "t" is deliberately NOT in this alternation — a mid-line "it"/"lt"
        // could be a real word ("add it to taste"); see the line-start rule below.
        Pair(
            Regex("""(?:(?<=^)|(?<=\s))[LI](tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c)(?=[\s.,:;]|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            "1 $1"
        ),

        // ── "Lt"/"It"/"lt" at line start — fused "1 t" (teaspoon) ─────────────
        //
        // On handwritten cards "1 t." is frequently read with the digit fused to
        // the unit letter and the period dropped: "Lt Vanilla extra". Anchored
        // to line start only, where "[LI]t" cannot be a real word.
        //
        //   "Lt Vanilla extra"  → "1 t Vanilla extra"
        //   "lt. baking soda"   → "1 t. baking soda"
        Pair(
            Regex("""(?<=^)[LI](t\.?)(?=[\s.,:;]|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
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
            Regex("""(?:(?<=^)|(?<=\s))V(\d+)(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c|t\.?)(?=[\s.,:;]|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            "$1 $2"
        ),

        // ── Digit fused directly to unit word (no space, starts with digit) ──
        //
        // On some fonts/images ML Kit reads "1cup" (no space) or "2tbsp".
        // This inserts the missing space so the unit-recognition step finds it.
        //
        //   "1cup shredded cheddar" → "1 cup shredded cheddar"
        //   "2tbsp butter"          → "2 tbsp butter"
        //   "1t. vanilla"           → "1 t. vanilla"  (digit fused to bare "t")
        Pair(
            Regex("""(?<=\d)(tablespoons|tablespoon|teaspoons|teaspoon|tbsps|tbsp|tbls|tsps|tsp|cups|cup|grams|gram|ounces|ounce|pounds|pound|litres|liters|litre|liter|milliliters|millilitres|milliliter|millilitre|pieces|piece|lbs|mls|oz|lb|ml|g|c|t\.?)(?=[\s.,:;]|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            " $1"
        ),

        // ── Handwritten-card: vertical bar or backslash used as fraction slash ─
        //
        // Handwritten recipes frequently use a narrow vertical stroke for the
        // fraction separator.  ML Kit reads it as "|" or occasionally "\".
        //
        //   "3|4 C. butter"  → "3/4 C. butter"
        //   "1|2 tsp salt"   → "1/2 tsp salt"
        //   "1 1|2 cups"     → "1 1/2 cups"
        //   "3\4 C."         → "3/4 C."
        Pair(
            Regex("""(\d+)\s*[|\\]\s*(\d+)"""),
            "$1/$2"
        ),

        // ── Handwritten-card: digit-collapse compound mixed-number artifact ───
        //
        // When the digit-collapse rules above convert "N12 unit" → "N 1 1/2 unit",
        // the result is a three-token quantity "N 1 1/2" where the middle "1" is
        // an artifact from splitting the collapsed glyph.  QTY_PREFIX_REGEX only
        // handles two-part mixed numbers ("N M/D"), so "N 1 1/2" ends up with only
        // "N" parsed as the quantity and "1 1/2 unit" misread as the name.
        //
        // This rule absorbs the artifact "1" and folds the fraction into the
        // leading whole number:
        //
        //   "4 1 1/2 cups"  → "4 1/2 cups"    (4 + artifact "1" → 4½)
        //   "2 1 1/3 tsp"   → "2 1/3 tsp"
        //
        // The match is anchored to start-of-string (after all previous fixes have
        // run) so it only fires on the quantity portion, not mid-name text.
        Pair(
            Regex("""^(\d+)\s+1\s+(\d+/\d+)""", setOf(RegexOption.MULTILINE)),
            "$1 $2"
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
        // "¢" is a common OCR mis-read of a handwritten "c." (the stroke through
        // the letter reads as a cent sign). Only safe as a bare unit token.
        "¢"    to "cup",
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
    // Supports: "1", "1.5", ".5", "1/2", "1 1/2", "1-1/2", "1¼", and the Unicode
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
    // | \.\d+                    – or a leading-decimal with no whole part: ".5"
    //
    // This deliberately excludes a bare space followed by a letter, so "1¼ cups"
    // does NOT extend the match into "cups". The [\s-]+ is only followed by more
    // digit/fraction chars and a slash (the mixed-number form), never bare words.
    private val FRAC_CHAR = "[\u00BC-\u00BE\u2150-\u215E\\d]"
    private val QTY_PREFIX_REGEX = Regex(
        """^($FRAC_CHAR+(?:[\s-]+$FRAC_CHAR+/$FRAC_CHAR+|[./]$FRAC_CHAR+)?|\.\d+)"""
    )

    // Per-line cleanup regexes, hoisted so they are compiled once rather than
    // on every line of every scan.
    // Explicit Unicode escapes keep the patterns ASCII-safe: U+2022=bullet,
    // U+2013=en-dash, U+2014=em-dash, U+00B7=middle-dot, U+25A1/25A2=empty
    // squares, U+25CB/25CF=circles, U+2610=ballot box, U+2751=lower-right
    // shadowed square, U+2713/2714=check marks. "|", "[", "]", "_", "~" cover
    // margin artefacts from ruled index cards and printed checkbox columns.
    // Leading periods are stripped too — OCR sometimes renders a bullet as a
    // plain dot — except a period immediately followed by a digit, which is a
    // leading-decimal quantity (".5 cup" → 0.5), not a marker.
    private val LEADING_MARKERS_REGEX = Regex(
        "^(?:[\\s\\u2022\\u2013\\u2014*\\u00B7|\\[\\]_~\\u25A1\\u25A2\\u25CB\\u25CF\\u2610\\u2751\\u2713\\u2714-]|\\.(?!\\d))+"
    )
    private val MULTI_SPACE_REGEX     = Regex("\\s{2,}")
    private val WHITESPACE_SPLIT      = Regex("\\s+")
    private val TRAILING_COMMA_REGEX  = Regex("\\s*,.*$")      // "flour, sifted" → "flour"
    private val PAREN_NOTE_REGEX      = Regex("\\s*\\(.*?\\)") // "(optional)" → ""
    // "(or ...)" note with the open-paren mis-read as a capital C — common on
    // handwritten cards ("chips Cor any exas" = "chips (or any extras)").
    // Case-sensitive on purpose: a capital C is what the paren reads as.
    private val PAREN_COR_REGEX       = Regex("\\s+Cor\\b.*$")
    // A leading "|" fused directly to a letter is a mis-read digit 1
    // ("|C whole wheat" = "1 C. whole wheat"). Converted in cleanLine BEFORE
    // the marker strip would otherwise delete the pipe. A spaced "|" remains
    // a bullet artefact and is stripped normally.
    private val LEADING_PIPE_DIGIT_REGEX = Regex("^\\|(?=[A-Za-z])")

    // Conjunctions/prepositions that signal a wrapped continuation line:
    // an ingredient line ending in one of these almost certainly continues on
    // the next visual line ("1 cup cheddar and" → "mozzarella").
    private val WRAP_WORDS = setOf("and", "or", "of", "with", "for", "to", "in", "plus")

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
        return parseRawLines(rawText.lines().asSequence().map { RawLine(it, null) }, maxIngredients)
    }

    /**
     * Layout-aware variant of [parse] that consumes positioned OCR lines instead
     * of raw text. ML Kit supplies each recognised line with a bounding box;
     * using the geometry lets us:
     *
     * 1. Detect multi-column layouts (common on recipe cards where ingredients
     *    are set in two columns) and emit ingredients column-by-column,
     *    left-to-right, instead of interleaving them by vertical position.
     * 2. Rejoin wrapped lines — a long ingredient name that overflows onto the
     *    next visual line is merged back into one ingredient.
     *
     * Lines with degenerate geometry should be filtered out by the caller; if
     * any line lacks a bounding box the caller should fall back to [parse].
     */
    fun parseLines(lines: List<OcrLine>, maxIngredients: Int = 50): List<IngredientItem> {
        if (lines.isEmpty()) return emptyList()

        // ── a. Column detection ──────────────────────────────────────────────
        // Median line height doubles as the "gutter" tolerance: two lines belong
        // to different columns when the second starts further right than the
        // first column's right edge plus one median line height.
        val heights = lines.map { it.bottom - it.top }.sorted()
        val medianLineHeight = heights[heights.size / 2]

        // Sweep left-to-right, keeping the running right edge of the current
        // column. Normal single-column text overlaps horizontally, so all lines
        // land in one column.
        val columns = mutableListOf<MutableList<OcrLine>>()
        var maxRight = Int.MIN_VALUE
        for (line in lines.sortedBy { it.left }) {
            if (columns.isEmpty() || line.left > maxRight + medianLineHeight) {
                columns.add(mutableListOf(line))
                maxRight = line.right
            } else {
                columns.last().add(line)
                if (line.right > maxRight) maxRight = line.right
            }
        }

        // ── b. Wrapped-line merge (within each column, top-to-bottom) ────────
        // Line B is a visual continuation of line A when ALL of these hold:
        //   - B's text starts with a lowercase letter (a new ingredient line
        //     starts uppercase/digit)
        //   - the vertical gap B.top - A.bottom is < 0.6 * A's height
        //   - B is not indented left of A by more than one line height
        // AND at least one wrap signal is present:
        //   - A looks unfinished: it ends with ',', '-' or '&', has more '('
        //     than ')', or its last word is a conjunction/preposition
        //     (WRAP_WORDS) — e.g. "1 cup cheddar and" → "mozzarella"
        //   - B is indented right of A by more than half a line height
        // The wrap signal guards against swallowing standalone lowercase
        // ingredient lines ("salt and pepper to taste") at normal line spacing.
        val orderedTexts = mutableListOf<RawLine>()
        for (column in columns) {
            var previous: OcrLine? = null
            for (line in column.sortedBy { it.top }) {
                val prev = previous
                val prevHeight = if (prev != null) prev.bottom - prev.top else 0
                val prevText = prev?.text?.trim().orEmpty()
                val prevLooksUnfinished = prev != null && (
                    prevText.lastOrNull() in setOf(',', '-', '&', '(') ||
                        prevText.count { it == '(' } > prevText.count { it == ')' } ||
                        prevText.substringAfterLast(' ')
                            .lowercase()
                            .trim { !it.isLetterOrDigit() } in WRAP_WORDS
                    )
                val isIndented = prev != null &&
                    line.left > prev.left + 0.5 * prevHeight
                val isContinuation = prev != null &&
                    line.text.trim().firstOrNull()?.isLowerCase() == true &&
                    (line.top - prev.bottom) < 0.6 * prevHeight &&
                    line.left >= prev.left - prevHeight &&
                    (prevLooksUnfinished || isIndented)
                if (prev != null && isContinuation) {
                    // Expand A's bounds and append B's text with a single space.
                    // The merged line keeps the lower of the two confidences —
                    // a poorly-recognised continuation shouldn't be masked by a
                    // confident first line. Null counts as "unknown", so a lone
                    // null doesn't drag the merge down to null.
                    val mergedConfidence = when {
                        prev.confidence == null  -> line.confidence
                        line.confidence == null  -> prev.confidence
                        else -> minOf(prev.confidence, line.confidence)
                    }
                    val merged = prev.copy(
                        text  = prev.text.trimEnd() + " " + line.text.trim(),
                        right = maxOf(prev.right, line.right),
                        bottom = maxOf(prev.bottom, line.bottom),
                        confidence = mergedConfidence
                    )
                    orderedTexts[orderedTexts.lastIndex] = RawLine(merged.text, merged.confidence)
                    previous = merged
                } else {
                    orderedTexts.add(RawLine(line.text, line.confidence))
                    previous = line
                }
            }
        }

        // ── c. Same per-line pipeline as parse(String) ───────────────────────
        return parseRawLines(orderedTexts.asSequence(), maxIngredients)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Shared pipeline for [parse] and [parseLines]: cleans each raw line, drops
     * blanks and section headers, parses what remains, and caps the result.
     */
    private fun parseRawLines(rawLines: Sequence<RawLine>, maxIngredients: Int): List<IngredientItem> {
        return rawLines
            .map   { it.copy(text = cleanLine(it.text)) }
            .filter { it.text.isNotBlank() }
            .filter { line -> SKIP_LINE_PATTERNS.none { it.containsMatchIn(line.text) } }
            .mapNotNull { parseLine(it.text, it.confidence) }
            .filter { it.name.isNotBlank() || it.qtyString.isNotBlank() }
            .take(maxIngredients)
            .toList()
    }

    /**
     * Normalise a single raw OCR line:
     * - Strip leading bullet/dash/asterisk markers (common in recipe lists).
     * - Collapse multiple consecutive spaces into one.
     * - Trim.
     */
    private fun cleanLine(line: String): String {
        return line
            .replace(LEADING_PIPE_DIGIT_REGEX, "1 ") // "|C" → "1 C" (| mis-read as 1)
            .replace(LEADING_MARKERS_REGEX, "")      // leading list markers + dots
            .replace(MULTI_SPACE_REGEX, " ")    // collapsed whitespace
            .trim()
    }

    /**
     * Parse a single cleaned line into an [IngredientItem], or return `null`
     * if the line cannot be interpreted as an ingredient at all.
     *
     * [confidence] is ML Kit's recognition confidence for the line (null when
     * unavailable, e.g. the plain-text [parse] path); below
     * [LOW_CONFIDENCE_THRESHOLD] the item is flagged `lowConfidence`.
     */
    private fun parseLine(line: String, confidence: Float?): IngredientItem? {
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
            // Normalise a bare leading decimal (".5") to "0.5" for display.
            val raw = qtyMatch.value.trim()
                .let { if (it.startsWith('.')) "0$it" else it }
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
        val tokens = remaining.split(WHITESPACE_SPLIT, limit = 2)
        val firstToken = tokens.firstOrNull().orEmpty()
        // Resolve bare "T" (tablespoon) and "t" / "t." (teaspoon) case-sensitively
        // BEFORE lowercasing, so punctuation trimming cannot collapse the
        // distinction. Trailing ',', ':', ';' are trimmed alongside '.' because
        // handwritten unit dots frequently OCR as those characters
        // ("3/4 C, butter", "1 t: vanilla").
        val lookedUpUnit = when (firstToken.trimEnd('.', ',', ':', ';')) {
            "T"  -> "tbsp"
            "t"  -> "tsp"
            else -> UNIT_SYNONYMS[firstToken.lowercase().trimEnd('.', ',', ':', ';')]
        }
        if (lookedUpUnit != null) {
            unitKey   = lookedUpUnit
            remaining = tokens.getOrElse(1) { "" }.trim()
        } else {
            unitKey   = "none"
            // remaining is unchanged — the first token is part of the name
        }

        // ── 3. Name ───────────────────────────────────────────────────────────
        val cleaned = remaining
            .replace(TRAILING_COMMA_REGEX, "") // strip trailing comma clauses ("flour, sifted")
            .replace(PAREN_NOTE_REGEX, "")     // strip parenthetical notes ("(optional)")
            .replace(PAREN_COR_REGEX, "")      // strip "(or …" notes mis-read as "Cor …"
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        // Plan item 5: bounded-dictionary correction of garbled name words
        // ("buter" → "butter") — applied after cleanup, before capitalization.
        val name = IngredientNameCorrector.correctWords(cleaned)
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
            name      = name,
            lowConfidence = confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD
        )
    }

    /** A raw OCR text line paired with its recognition confidence, if known. */
    private data class RawLine(val text: String, val confidence: Float?)
}

/**
 * One OCR-recognised line of text with its pixel-space bounding box, as supplied
 * by ML Kit's `Text.Line`. Consumed by [RecipeOcrParser.parseLines] for
 * layout-aware (column detection + wrapped-line merge) parsing.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float? = null
)
