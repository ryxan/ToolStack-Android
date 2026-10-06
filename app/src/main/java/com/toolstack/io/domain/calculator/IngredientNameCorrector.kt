package com.toolstack.io.domain.calculator

/**
 * Post-OCR ingredient-name correction (plan item 5 in
 * docs/recipe-scaler-ocr-accuracy-plan.md).
 *
 * ML Kit frequently garbles single characters on handwritten cards:
 * "buter" for butter, "flocor" for flour, "chps" for chips. Those mis-reads
 * land in the ingredient *name*, where no regex can safely fix them, so this
 * corrector does a bounded dictionary match instead: a token is replaced only
 * when exactly ONE dictionary word sits at the minimum edit distance (≤ 2).
 * Ambiguous tokens ("bitter" is distance 1 from both "batter" and "butter")
 * are left untouched — a wrong guess is worse than a garbled word the user
 * can see and fix.
 *
 * Pure Kotlin, no Android dependencies, so it stays unit-testable.
 */
object IngredientNameCorrector {

    /**
     * Common recipe-ingredient words, all lowercase `[a-z]+` and ≥ 4 chars.
     * Words ≤ 3 chars are deliberately absent — [correctWords] skips tokens
     * that short anyway ("oil", "egg", "rye"), so they would be dead weight.
     * Both singular and plural forms are listed so a correct plural is never
     * "corrected" to the singular (or vice versa).
     *
     * A few plausible-looking words are deliberately EXCLUDED because they
     * would create ambiguity against required corrections or false positives:
     * "chops"/"chaps" (distance 1 from "chps" — would block "chps"→"chips"),
     * "stacks"/"stocks" (distance 1 from "sthcks" → "sticks"), "flavor"/
     * "flower" (distance 2 from "flocor" → "flour"), and "mace"/"lace"/
     * "taco"/"pack" (distance 2 from "vaci", which must stay untouched).
     * "taste" and "twice" ARE included so real words in names
     * ("salt and pepper to taste", "sifted twice") are not mangled.
     */
    val INGREDIENT_WORDS: Set<String> = setOf(
        // ── Baking staples & dairy ──────────────────────────────────────────
        "flour", "sugar", "brown", "granulated", "powdered", "confectioners",
        "baking", "soda", "powder", "yeast", "salt", "kosher",
        "butter", "margarine", "shortening", "olive", "vegetable", "canola",
        "ghee", "eggs", "milk", "buttermilk", "cream", "sour", "whipped",
        "whipping", "heavy", "cheese", "cheddar", "mozzarella", "parmesan",
        "ricotta", "cottage", "feta", "provolone", "yogurt", "vanilla",
        "extract", "almond", "lemon", "lemons", "orange", "oranges", "zest",
        "juice", "honey", "maple", "syrup", "molasses", "agave", "turbinado",
        "demerara", "muscovado", "tartar", "cornstarch", "espresso", "coffee",
        "instant",

        // ── Grains, starches & baked goods ─────────────────────────────────
        "oats", "rolled", "quick", "oatmeal", "granola", "cereal", "rice",
        "quinoa", "barley", "cornmeal", "breadcrumbs", "breadcrumb", "panko",
        "pasta", "noodle", "noodles", "spaghetti", "macaroni", "bread",
        "breads", "roll", "rolls", "buns", "loaf", "bagel", "biscuit",
        "biscuits", "muffin", "muffins", "pancake", "pancakes", "waffle",
        "waffles", "tortilla", "tortillas", "crust", "dough", "batter",
        "frosting", "icing", "crumb", "crumbs", "cracker", "crackers",
        "cake", "cakes", "cookie", "cookies", "brownie", "brownies",
        "purpose", "pastry", "whole", "wheat", "grain", "grains", "bran",
        "berry", "berries",

        // ── Chocolate, nuts, seeds & mix-ins ───────────────────────────────
        "chocolate", "cocoa", "cacao", "chip", "chips", "chunk", "chunks",
        "nuts", "walnut", "walnuts", "pecan", "pecans", "almonds", "peanut",
        "peanuts", "cashew", "cashews", "hazelnut", "hazelnuts", "pistachio",
        "pistachios", "raisin", "raisins", "cranberry", "cranberries",
        "cherry", "cherries", "date", "dates", "figs", "apricot", "apricots",
        "shredded", "flake", "flakes", "seed", "seeds", "sesame", "sunflower",
        "pumpkin", "flax", "chia", "poppy", "marshmallow", "marshmallows",
        "caramel", "sprinkles",

        // ── Spices, herbs & condiments ─────────────────────────────────────
        "cinnamon", "nutmeg", "ginger", "clove", "cloves", "allspice",
        "cardamom", "paprika", "cumin", "coriander", "turmeric", "cayenne",
        "chili", "chile", "chipotle", "jalapeno", "pepper", "peppers",
        "peppercorn", "black", "white", "green", "oregano", "basil", "thyme",
        "rosemary", "sage", "parsley", "cilantro", "dill", "mint", "leaves",
        "leaf", "tarragon", "marjoram", "curry", "saffron", "mustard",
        "ketchup", "mayonnaise", "mayo", "vinegar", "balsamic", "cider",
        "wine", "beer", "worcestershire", "tabasco", "sriracha",
        "horseradish", "salsa", "pesto", "tahini", "relish", "pickle",
        "pickles", "olives", "caper", "capers", "marinade", "gravy",
        "garlic", "onion", "onions", "shallot", "shallots", "scallion",
        "scallions", "chive", "chives", "seasoning",

        // ── Produce ────────────────────────────────────────────────────────
        "apple", "apples", "banana", "bananas", "strawberry", "strawberries",
        "blueberry", "blueberries", "raspberry", "raspberries", "blackberry",
        "blackberries", "peach", "peaches", "pear", "pears", "plum", "plums",
        "mango", "mangoes", "pineapple", "grape", "grapes", "melon",
        "watermelon", "lime", "limes", "nectarine", "papaya", "kiwi",
        "tomato", "tomatoes", "potato", "potatoes", "sweet", "yams",
        "carrot", "carrots", "celery", "bell", "zucchini", "squash",
        "butternut", "spinach", "kale", "lettuce", "broccoli", "cauliflower",
        "cabbage", "mushroom", "mushrooms", "corn", "peas", "bean", "beans",
        "kidney", "pinto", "chickpea", "chickpeas", "lentil", "lentils",
        "cucumber", "avocado", "radish", "beet", "beets", "eggplant",
        "asparagus", "artichoke", "artichokes",

        // ── Proteins ───────────────────────────────────────────────────────
        "chicken", "beef", "ground", "pork", "bacon", "sausage", "sausages",
        "turkey", "lamb", "veal", "duck", "boneless", "skinless", "steak",
        "steaks", "thigh", "thighs", "wing", "wings", "breast", "breasts",
        "drumstick", "drumsticks", "chop", "prosciutto", "chorizo",
        "pepperoni", "salami", "salmon", "tuna", "shrimp", "prawns", "crab",
        "lobster", "scallop", "scallops", "clams", "mussels", "oysters",
        "tofu", "tempeh", "tilapia", "trout", "anchovies",

        // ── Pantry & liquids ───────────────────────────────────────────────
        "water", "broth", "stock", "bouillon", "soup", "soups", "stewed",
        "applesauce", "marmalade", "jelly", "preserves", "condensed",
        "evaporated", "sauce", "paste", "puree",

        // ── Descriptors common in ingredient names ─────────────────────────
        "melted", "softened", "unsalted", "salted", "cold", "warm", "large",
        "medium", "small", "room", "temperature", "sifted", "chopped",
        "minced", "diced", "sliced", "grated", "packed", "lightly",
        "firmly", "divided", "optional", "fresh", "frozen", "canned",
        "dried", "ripe", "mashed", "pureed", "toasted", "roasted", "cooked",
        "thawed", "drained", "rinsed", "peeled", "seeded", "halved",
        "quartered", "beaten", "chilled", "browned", "crumbled", "homemade",
        "taste", "twice", "extra", "extras", "stick", "sticks", "piece",
        "pieces"
    )

    /**
     * Levenshtein edit distance between [a] and [b], or -1 when the true
     * distance exceeds [max]. Classic two-row dynamic program with two
     * prunes: a length difference greater than [max] short-circuits, and a
     * row whose minimum cell already exceeds [max] exits early (every later
     * row can only be as large, so the result is guaranteed > [max]).
     */
    fun boundedEditDistance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return -1
        var prev = IntArray(b.length + 1) { it }          // row i=0: insertions only
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i                                    // j=0: deletions only
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,                           // delete a[i-1]
                    curr[j - 1] + 1,                       // insert b[j-1]
                    prev[j - 1] + cost                     // substitute / match
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return -1                    // row all > max: bail early
            val tmp = prev; prev = curr; curr = tmp
        }
        return if (prev[b.length] <= max) prev[b.length] else -1
    }

    /**
     * Correct each whitespace-separated token of [name] against
     * [INGREDIENT_WORDS]. A token is replaced only when exactly one
     * dictionary word sits at the minimum edit distance and that distance is
     * 1 or 2; ties and non-matches are left untouched. Tokens ≤ 3 chars,
     * tokens containing anything but ASCII letters, and tokens already in
     * the dictionary are never touched. Tokens are rejoined with single
     * spaces — none are dropped or reordered.
     */
    fun correctWords(name: String): String {
        return name.split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { correctToken(it) }
    }

    private val WHITESPACE = Regex("\\s+")

    private fun correctToken(token: String): String {
        if (token.length <= 3) return token
        if (!token.all { it in 'a'..'z' || it in 'A'..'Z' }) return token
        val lower = token.lowercase()
        if (lower in INGREDIENT_WORDS) return token

        var best = Int.MAX_VALUE
        var bestWord: String? = null
        var bestCount = 0
        for (word in INGREDIENT_WORDS) {
            val d = boundedEditDistance(lower, word, 2)
            if (d < 0) continue
            if (d < best) {
                best = d
                bestWord = word
                bestCount = 1
            } else if (d == best) {
                bestCount++
            }
        }
        // d == 0 is unreachable (dictionary members were returned above), so
        // a unique minimum in 1..2 is the only case that triggers a rewrite.
        if (bestWord == null || bestCount != 1 || best !in 1..2) return token
        return if (token[0].isUpperCase()) {
            bestWord.replaceFirstChar { it.uppercaseChar() }
        } else {
            bestWord
        }
    }
}
