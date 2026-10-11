# Recipe Scaler: Standalone Android App Plan

How to pull the Recipe Scaler out of ToolStack-Android and turn it into its own native Android app (Kotlin, Jetpack Compose, Material 3). iOS will be a separate native rewrite later, so this plan has no cross-platform frameworks. It does keep the domain logic pure Kotlin with a full test suite, so those tests can serve as the spec for the Swift port.

> Audit baseline: ToolStack-Android working tree as of 2026-10-11 (`compileSdk/targetSdk 36`, `minSdk 26`, Hilt, Compose BOM 2025.09.01, DataStore, ML Kit, Firebase AI, Play Billing 8).

---

## 0. TL;DR: Where to Start

1. **Don't start with UI.** Start by creating a pure-Kotlin `:core:domain` module and porting `RecipeScalerCalculator`, `RecipeOcrParser`, `IngredientNameCorrector`, the recipe models, and their tests (about 1,100 lines of tests already exist).
2. **Before changing anything, freeze current behavior** with characterization tests, then fix the accuracy bugs in [§2.3](#23-accuracy-bugs-found-in-the-current-engine-fix-in-phase-1). Several of them change what a cook would actually measure.
3. **Keep the cup / Tbsp / tsp breakdown display, but make it precise:** ½ tsp steps (¼ and ⅛ below 1 tsp), cup fractions (¼ ⅓ ½ ⅔ ¾), and real precision for all other units ([§3.3](#33-measurement-display-kitchenformatter)).
4. Only then add Room persistence, and after that the Compose UI.

---

## 1. How to Share Code for Analysis

The codebase is already in this workspace, and §2 below is the audit. For later rounds, or when you work in the new repo:

| What you want reviewed | Best way to share |
| --- | --- |
| Whole feature | Open both repos in the same workspace (as now) and name the package path, e.g. `com.toolstack.io.ui.recipescaler`. |
| One behavior or bug | The function plus its test file, and the input and the output you expected. |
| Scan mis-parses | Logcat tag `RecipeScaler` (debug builds log raw ML Kit text with per-line confidence), plus the photo. |
| Design and UX | Screenshots or a screen recording plus the composable file name. |
| History questions | Commit SHAs; the git history here is detailed and useful (see §2.4). |

**Moving code into the new repo:**

- **Simple (recommended):** copy the files, then commit `"Import domain logic from ToolStack @ <sha>"` so you can trace where they came from.
- **History-preserving:** `git filter-repo --paths-from-file recipe-paths.txt` on a throwaway clone of ToolStack, then pull that into the new repo. This is only worth it if you want `git blame` on the parser heuristics.

---

## 2. Codebase Audit

### 2.1 Inventory and Verdicts

Paths are relative to `app/src/main/java/com/toolstack/io/`.

| File | Lines | Layer | Android/3rd-party deps | Verdict |
| --- | ---: | --- | --- | --- |
| `domain/calculator/RecipeScalerCalculator.kt` | 285 | Domain | None (pure Kotlin) | **Carry over, then refactor.** Keep `parseQuantity`, `toTsp`, `decompose` and the copy/shopping-list generators. Replace the formatter (§2.3) and the stringly-typed unit catalogue. |
| `domain/model/RecipeScalerModels.kt` | 35 | Domain | None | **Carry over, then reshape.** Split the editable draft from the parsed/scan result (`lowConfidence` is a scan concern). |
| `domain/calculator/RecipeOcrParser.kt` | 622 | Domain | None (`java.util.UUID` only) | **Carry over as-is.** Pure, heavily tested, and already isolated from ML Kit through the `OcrLine` adapter. Also powers a free **"Paste recipe text"** feature through `parse(rawText)`. |
| `domain/calculator/IngredientNameCorrector.kt` | 200 | Domain | None | **Carry over as-is** (OCR typo fixer, edit-distance based). |
| `domain/ai/AiRecipeResult.kt`, `AiRecipeMapper.kt` | 84 | Domain | kotlinx.serialization | **Carry over** if AI import ships (Phase 5). |
| `domain/ai/AiImportConfig.kt` | 60 | Domain | None | **Carry over, then decouple.** `ALLOWED_UNITS` reads from `RecipeScalerCalculator.ALL_UNITS`; point it at the new unit enum. |
| `data/ai/AiRecipeImporter.kt` (interface) | 37 | Data | None | **Carry over.** This is already the right seam. |
| `data/ai/FirebaseAiRecipeImporter.kt` | 238 | Data | Firebase AI, App Check | **Carry over in Phase 5.** Needs a new Firebase project/app registration. |
| `data/ai/AiImportQuotaRepository.kt` | 87 | Data | DataStore | **Carry over in Phase 5** together with billing. |
| `data/repository/UserPreferencesRepository.kt` (saved-recipe part) | ~200 of it | Data | DataStore | **Replace with Room.** Today recipes are stored as one Base64, tab-and-`\uFFFE`-delimited string in a single preference key. There's no ID, so name is the key; a rename is delete-then-insert; there's no ordering or query support. |
| `ui/recipescaler/RecipeScalerViewModel.kt` | 1,174 | UI | Android `Context`, ML Kit, Bitmap/EXIF, Billing, Firebase via repos | **Rewrite (split).** It's a god object: ingredient editing, servings, save/load dialogs, camera file management, image decoding/EXIF/JPEG encoding, ML Kit, AI import, quota, paywall and purchase flow. Keep the *behaviors*, not the class. |
| `RecipeScalerUiState` (same file) | ~80 | UI | None | **Rewrite.** 30+ fields, about 10 of them `show*Dialog` booleans. Keep `recalculate()` logic as a pure derivation. |
| `ui/recipescaler/RecipeScalerScreen.kt` | 2,210 | UI | Compose | **Rewrite.** Mine it for UX decisions (below), not code. |
| `ui/recipescaler/IngredientInputAccessoryBar.kt` | 229 | UI | Compose | **Carry over the idea, rebuild the composable.** The fraction and unit chips above the IME solve a real problem: numeric keyboards can't type `/` or `½`. |
| `ui/recipescaler/AiImportPaywallDialog.kt` | 86 | UI | Compose, Billing | Phase 5 only. |

**Tests** (`app/src/test/java/com/toolstack/io/`):

| Test | Size | Verdict |
| --- | --- | --- |
| `ui/recipescaler/RecipeScalerUiStateTest.kt` | 44 tests | Despite the name, most of these are **calculator spec tests**. Port them into `:core:domain` and split them into `QuantityParserTest`, `KitchenFormatterTest`, `ScaleRecipeTest`. Tests that encode the buggy behavior get **rewritten** to the new spec (see §2.3). |
| `domain/calculator/RecipeOcrParserTest.kt` | 894 lines | **Port as-is.** |
| `domain/calculator/IngredientNameCorrectorTest.kt` | 142 lines | **Port as-is.** |
| `domain/ai/AiRecipeMapperTest.kt` | 135 lines | Port with AI (Phase 5). |
| `ui/recipescaler/RecipeScalerViewModelAiTest.kt` | 329 lines | **Rewrite** against the new import ViewModel/use case. |

**UX decisions to carry into the rewrite** (from the existing screen and VM):

- A fraction/unit accessory bar tied to the focused field (`FocusedIngredientField`), with IME inset handled once by the parent `imePadding()`.
- "Next" on the accessory bar adds a row and focuses its quantity (`addIngredientAndFocusName`).
- Scan results replace silently only if the list is blank; otherwise the user picks Replace / Append / Cancel.
- A `MAX_INGREDIENTS = 50` cap.
- A warning banner when the multiplier is over 4× (`showScaleWarning`). Baking chemistry and pan sizes don't scale linearly.
- A "Show original values" caption under each scaled row.
- Copy formats: Plain / Markdown / Shopping list (checkbox glyphs).
- The camera URI held in `rememberSaveable`, so it survives process death.

### 2.2 Reusable Domain Logic vs. Rewrite: Summary

```
CARRY OVER (pure Kotlin)            REFACTOR (pure, but wrong shape)       REWRITE (Compose/Android)
─────────────────────────           ──────────────────────────────         ─────────────────────────
RecipeOcrParser + OcrLine           RecipeScalerCalculator.formatQuantity  RecipeScalerScreen
IngredientNameCorrector             ALL_UNITS (Pair<String,String>)        RecipeScalerViewModel
parseQuantity (with fixes)          IngredientItem / SavedRecipe           IngredientInputAccessoryBar
toTsp / decompose                   calculateMultiplier (servings only)    Saved-recipe persistence
generateFormattedRecipe             RecipeScalerUiState.recalculate        Image decode / EXIF helpers*
AiRecipeMapper / AiRecipeResult     AiImportConfig.ALLOWED_UNITS
```

\* The image helpers (`prepareJpegForAi`, `decodeViaImageDecoder`, `applyExifRotation`, `scaleToMaxEdge`, `loadScanImage`) are correct and worth keeping. Move them out of the ViewModel into an `ImagePreprocessor` class in the data layer (Phase 5).

### 2.3 Accuracy Bugs Found in the Current Engine (fix in Phase 1)

All of these come from `RecipeScalerCalculator.formatQuantity` / `formatOther`, which round to **whole numbers**. Several are locked in by existing tests, so those tests need to change along with the fix.

| # | Input | Current output | Why it's wrong | Root cause |
| --- | --- | --- | --- | --- |
| B1 | `½ tsp` baking soda, 1× | **`1 tsp`** | Doubles a leavener | US volume rounds to whole tsp (`roundToInt(0.5) == 1`) |
| B2 | `¼ tsp` or `⅓ tsp`, 1× | **`pinch`** | Any amount < 0.5 tsp becomes "pinch" | Same whole-tsp rounding + pinch rule |
| B3 | `1½ tsp`, 1× | `2 tsp` | 33% over | Same |
| B4 | `⅓ cup`, 1× | `5 Tbsp 1 tsp` | Correct volume but not how cooks measure. The test `one third cup formats as 5 Tbsp 1 tsp` encodes this. | No fraction output at all |
| B5 | `1.5 qt`, `0.5 gal`, `1.5 pt`, `fl oz` | `2 qt`, `1 gal`, … | Up to 100% error | `pt/qt/gal/fl oz` are in `ALL_UNITS` but **not** in `US_VOLUME_UNITS`, so they fall through to `formatOther` (whole-number rounding) |
| B6 | `1.5 kg`, `1.5 lb`, `0.75 oz` | `2 kg`, `2 lb`, `1 oz` | Large weight errors | `formatOther` rounds every non-volume unit to whole, minimum 1 |
| B7 | `¼ cup` with **Keep original units** on | **`1 cup`** | 4× too much | Keep-original path does `roundToInt().coerceAtLeast(1)` |
| B8 | `"1,000 g"` | parses as `1.0` | 1000× too little | `parseQuantity` replaces `,` with `.` unconditionally |
| B9 | Unparseable qty (`"2-3"`, `"a few"`) | `0.0` | Can't tell "unparsed" from "zero" | `parseQuantity` returns `0.0` on failure |
| B10 | Servings `"½"` or `"4-6"` | No scaling | Valid recipe yields | Servings parsed with `toDoubleOrNull()`, not `parseQuantity` |

**Missing features** compared with the new requirements: scaling by a key ingredient, scaling by a direct factor, fractional kitchen display, weight conversions (g↔oz↔lb), and a user preference for measurement system.

### 2.4 Recoverable Code in Git History

Commit `32a9d67` ("v1 scaling engine…") **removed** a fraction formatter with per-unit allowed-fraction sets (`toFraction`, `tryConvertLargerUnits`, `formatQuantitySimplified`, a `simplifyFractions` toggle). To read it:

```bash
git show 32a9d67^:app/src/main/java/com/toolstack/io/domain/calculator/RecipeScalerCalculator.kt
```

Use it as a reference for the new `KitchenFormatter` (§3.3), not as a drop-in. It depended on the removed `IngredientState` (DRY/LIQUID).

---

## 3. Core Feature Requirements and Logic

### 3.1 Domain Model (new)

```kotlin
enum class UnitSystem { US, METRIC, NONE }
enum class UnitFamily { VOLUME, WEIGHT, COUNT }

/** Replaces ALL_UNITS Pair<String,String>. `toBase` = mL for volume, g for weight, null for count. */
enum class KitchenUnit(val key: String, val label: String, val family: UnitFamily,
                       val system: UnitSystem, val toBase: Double?) {
    TSP("tsp", "tsp", VOLUME, US, 4.92892), TBSP("tbsp", "Tbsp", VOLUME, US, 14.7868),
    FL_OZ("fl oz", "fl oz", VOLUME, US, 29.5735), CUP("cup", "cup", VOLUME, US, 236.588),
    PINT("pt", "pt", VOLUME, US, 473.176), QUART("qt", "qt", VOLUME, US, 946.353),
    GALLON("gal", "gal", VOLUME, US, 3785.41),
    ML("ml", "mL", VOLUME, METRIC, 1.0), L("L", "L", VOLUME, METRIC, 1000.0),
    MG("mg", "mg", WEIGHT, METRIC, 0.001), G("g", "g", WEIGHT, METRIC, 1.0),
    KG("kg", "kg", WEIGHT, METRIC, 1000.0),
    OZ("oz", "oz", WEIGHT, US, 28.3495), LB("lb", "lb", WEIGHT, US, 453.592),
    EACH(...), WHOLE(...), PINCH(...), DASH(...), CLOVE(...), CAN(...), PKG(...), NONE(...);
    companion object { fun fromKey(raw: String): KitchenUnit? /* case-insensitive + synonyms */ }
}

data class Ingredient(
    val id: Long,
    val rawQuantity: String,      // what the user typed: source of truth, round-trips exactly
    val quantity: Double?,        // parsed; null = unparseable (fixes B9)
    val unit: KitchenUnit,
    val name: String,
    val note: String? = null,     // "chopped", "room temp"
)

data class Recipe(val id: Long, val title: String, val yieldAmount: Double,
                  val yieldLabel: String /* "servings", "cookies" */, val ingredients: List<Ingredient>)

sealed interface ScaleMode {
    data class ByYield(val target: Double) : ScaleMode
    data class ByIngredient(val ingredientId: Long, val targetAmount: Double, val targetUnit: KitchenUnit) : ScaleMode
    data class ByFactor(val factor: Double) : ScaleMode
}

enum class DisplayMode { KITCHEN /* cup/Tbsp/tsp breakdown, ½ tsp precision */, KEEP_UNITS, DECIMAL }
```

Keep US customary values (`fl oz`, `cup` = 236.588 mL) as the default. If a UK/AU audience matters, add a "Metric cup (250 mL)" preference later.

### 3.2 Scaling Engine

```kotlin
class ScaleRecipe(private val formatter: KitchenFormatter) {
    fun factor(recipe: Recipe, mode: ScaleMode): Result<Double>
    operator fun invoke(recipe: Recipe, mode: ScaleMode, prefs: DisplayPrefs): ScaledRecipe
}
```

- **By yield:** `factor = target / recipe.yieldAmount`. Error if `yieldAmount <= 0`.
- **By key ingredient:** `factor = convert(targetAmount, targetUnit → ingredient.unit) / ingredient.quantity`.
  - Error if the ingredient quantity is null/0, or if the units are in **different families** (e.g. the user types grams for an ingredient entered in cups; you can't convert without density, so show "Can't convert cups to grams for this ingredient").
  - Same-family conversion (g↔oz, cup↔mL) is allowed.
- **By factor:** quick chips `½× 1× 2× 3×` plus a custom value.
- After scaling: also show the derived yield ("Makes 18 cookies") and the multiplier. Warn above 4× and below ¼×.
- Unparseable rows pass through unscaled and get a visual flag (never silently zero).
- Keep `generateFormattedRecipe` / `generateShoppingList`; feed them the new display text.

### 3.3 Measurement Display (`KitchenFormatter`)

**Keep the current breakdown style.** Results are split into the measures you actually scoop (`1 tsp × 20 → 6 Tbsp 2 tsp`, `1 cup × 20 → 20 cup`), not shown as decimals. Three things change:

- **Precision goes from whole tsp to ½ tsp**, with ¼ and ⅛ tsp allowed below 1 tsp. Fixes B1–B3.
- **Cup fractions** ¼ ⅓ ½ ⅔ ¾ are used when they're the simplest way to measure. Fixes B4.
- **Every unit gets real precision** instead of whole-number rounding. Fixes B5–B7.

#### US volume (tsp / Tbsp / cup) algorithm

1. Convert to total teaspoons `T` (`1 Tbsp = 3 tsp`, `1 cup = 48 tsp`).
2. **Snap to a measurable amount:**
   - `T < 1/16` → `"pinch"`.
   - `T < 1` → nearest of `⅛, ¼, ½, ¾, 1` tsp.
   - `T ≥ 1` → nearest **½ tsp**. Maximum error is ¼ tsp, which matters less as amounts grow.
3. **Build candidate renderings** from these parts:
   - **cup part:** whole cups + one of `0, ¼, ⅓, ½, ⅔, ¾` (in tsp: 0, 12, 16, 24, 32, 36)
   - **Tbsp part:** whole Tbsp, **at most 3 when there's a cup part**. A remainder of 4 Tbsp or more means a larger cup fraction fits, which blocks `⅓ cup 8 Tbsp` and `⅔ cup 5 Tbsp`.
   - **tsp part:** `½, 1, 1½, 2, 2½` (or the sub-1 tsp values above when it's the only part)
4. **Pick the rendering with the fewest parts**, then the fewest scoops as a tie-break (`½ cup` = 1 scoop, `8 Tbsp` = 8).
5. **Tbsp-only renderings (no cup part) are only allowed below ½ cup** (`T < 24`). This prevents `13 Tbsp 1 tsp` when `¾ cup 1 Tbsp 1 tsp` exists.
6. Output parts in large-to-small order, space-separated, matching today's format: `¾ cup 1 Tbsp 1 tsp`.

Worked examples of the selection rule:

- `T = 20` → `6 Tbsp 2 tsp` (2 parts) beats `⅓ cup 1 Tbsp 1 tsp` (3 parts). This is today's output, unchanged.
- `T = 19` → `⅓ cup 1 Tbsp` (2 parts, 2 scoops) beats `6 Tbsp 1 tsp` (2 parts, 7 scoops).
- `T = 12` → `¼ cup` (1 scoop) beats `4 Tbsp` (4 scoops).
- `T = 40` → over ½ cup, so a cup part is required. `¾ cup 1 Tbsp 1 tsp` (3 scoops) beats `⅔ cup 2 Tbsp 2 tsp` (5 scoops).
- `T = 47` → `¾ cup 3 Tbsp 2 tsp`. `⅔ cup 5 Tbsp` is blocked by the 3-Tbsp cap.

#### Other units

| Unit | Display rule | Example |
| --- | --- | --- |
| `pt`, `qt`, `gal` | Stay in the source unit, nearest ¼ | `1.5 qt → 1½ qt` |
| `fl oz` | Nearest ½ fl oz | `2.3 fl oz → 2½ fl oz` |
| `mL`, `L` | Keep today's whole-mL format | `1500 mL → 1 L 500 mL` |
| `g`, `kg`, `mg` | Below 10 g → 1 decimal; otherwise whole g; `≥ 1 kg` → `1 kg 500 g` | `1.5 kg → 1 kg 500 g` |
| `oz`, `lb` | oz to nearest ¼; `≥ 1 lb` → `lb + oz` | `1.5 lb → 1 lb 8 oz` |
| Count (`each`, `egg`, `clove`, …) | Nearest ½; non-whole eggs get a hint | `2 eggs × 1.25 → 2½` |
| `pinch`, `dash` | Scale, then nearest whole (minimum 1) | `1 pinch × 3 → 3 pinch` |

#### Modes and rendering

- **KITCHEN** (default): the rules above.
- **KEEP_UNITS:** the same snapping, but never changes the unit. `¼ cup` stays `¼ cup`, `2.5 Tbsp` becomes `2½ Tbsp`. Fixes B7.
- **DECIMAL:** at most 2 decimals.
- Use Unicode fractions (`½ ⅓ ⅔ ¼ ¾ ⅛`) and a spoken `contentDescription` ("three quarters cup, one tablespoon") for TalkBack.

**Arithmetic:** `Double` with snapping is enough. Parse `⅓` exactly as `1.0/3`, so `⅓ cup × 3` snaps back to `1 cup`. A `Rational` type isn't needed.

**Spec table** (becomes `KitchenFormatterTest`, and later the Swift port's test vectors):

| Input | Factor | Expected (US, Kitchen) | Today |
| --- | --- | --- | --- |
| 1 tsp | 20 | 6 Tbsp 2 tsp | same |
| 1 cup | 20 | 20 cup | same |
| 3½ cup | 20 | 70 cup | same |
| 2 tsp | 2.5 | 1 Tbsp 2 tsp | same |
| 50 tsp | 1 | 1 cup 2 tsp | same |
| ½ tsp | 1 | ½ tsp | 1 tsp |
| ¼ tsp | 1 | ¼ tsp | pinch |
| ¼ tsp | 0.5 | ⅛ tsp | pinch |
| ⅛ tsp | 0.25 | pinch | pinch |
| 1 tsp | 1.5 | 1½ tsp | 2 tsp |
| 1 Tbsp | 0.5 | 1½ tsp | 2 tsp |
| ⅓ cup | 1 | ⅓ cup | 5 Tbsp 1 tsp |
| 1 cup | 0.5 | ½ cup | 8 Tbsp |
| ¼ cup | 3 | ¾ cup | 12 Tbsp |
| 1 cup | 0.4 | ⅓ cup 1 Tbsp | 6 Tbsp 1 tsp |
| 2 tsp | 20 | ¾ cup 1 Tbsp 1 tsp | 13 Tbsp 1 tsp |
| 47 tsp | 1 | ¾ cup 3 Tbsp 2 tsp | 15 Tbsp 2 tsp |
| 1 cup | 1.5 | 1½ cup | 1 cup 8 Tbsp |
| 1.5 qt | 1 | 1½ qt | 2 qt |
| 1.5 lb | 1 | 1 lb 8 oz | 2 lb |
| ¼ cup (Keep units) | 1 | ¼ cup | 1 cup |
| 250 mL | 6 | 1 L 500 mL | same |

These existing tests in `RecipeScalerUiStateTest` encode the old behavior. Rewrite them to the table above when you port them: `one third cup formats as 5 Tbsp 1 tsp`, `quarter cup times 3 is 12 Tbsp not 1 cup`, `47 tsp does not roll into a cup`, `amount just above 0 5 tsp rounds to 1 tsp not pinch`, `oz rounds to whole`, `lb rounds to whole`, `keep original units rounds 2·5 tbsp to 3 Tbsp`.

### 3.4 Quick Recipe Input

- Row = `[qty] [unit chip] [name]`. The qty field uses `KeyboardType.Decimal` **plus** the accessory bar for `½ ⅓ ¼ ¾ ⅔ ⅛` and `/`.
- `parseQuantity` accepts decimals, `a/b`, mixed `1 1/2` / `1-1/2`, Unicode fractions, and `1½`. Fixes: thousands separators (B8), ranges → use the low end and flag, failures → `null` (B9).
- **Paste recipe text**: a multi-line paste goes through `RecipeOcrParser.parse(text)` and opens the same Replace/Append confirm. This reuses about 600 lines of tested parsing for free.
- Unit inference while typing: "2 cups flour" typed into the name field gets split automatically (the OCR parser's line logic already does this).

---

## 4. UI / UX Blueprint (Compose and Material 3)

### 4.1 Screen Flow

```
┌──────────────┐  tap / FAB   ┌──────────────────┐  "Scale"   ┌──────────────────┐
│ Recipe List  │ ───────────▶ │ Recipe Editor    │ ─────────▶ │ Scale View       │
│ (home)       │ ◀─────────── │ (input)          │ ◀───────── │ (live results)   │
└──────┬───────┘              └──────────────────┘            └──────────────────┘
       │ overflow                                                   │ share/copy
       ▼                                                            ▼
┌──────────────┐                                              system share sheet
│ Settings     │
└──────────────┘
```

| Screen | Contents |
| --- | --- |
| **Recipe List** | Saved recipes (title, yield, ingredient count), search, FAB "New recipe", empty state with "Quick scale" (unsaved scratch recipe). Swipe to delete with Undo snackbar. |
| **Recipe Editor** | Title, yield amount + label, ingredient rows (drag to reorder), accessory bar, overflow: Paste text / Scan (Phase 5) / Clear. Autosave a draft (no Save dialog dance). |
| **Scale View** | Single-choice `SegmentedButton`: **Servings · Ingredient · ×Factor**. Big target input plus quick chips. Live scaled list (original value caption optional). Warning banner over 4×. Actions: Copy / Share / Checklist mode (tap rows to strike through, keep screen on). |
| **Settings** | Measurement system (US / Metric / Match recipe), display (Fractions / Decimals), theme (System / Light / Dark), Dynamic color toggle, about/licenses/privacy. |

On **expanded widths** (tablets, foldables, landscape) use `NavigableListDetailPaneScaffold` (`material3-adaptive`). List and Editor/Scale go side by side, and Editor plus Scale View can share one screen (input left, live results right).

### 4.2 Material 3 Patterns

- **Dynamic color** on Android 12+ (`dynamicLightColorScheme`/`dynamicDarkColorScheme`), with a brand fallback scheme. Generate it with Material Theme Builder.
- **Edge-to-edge** (`enableEdgeToEdge()`), `Scaffold` insets, `imePadding()` applied once at the root of the editor.
- **Numbers:** `fontFeatureSettings = "tnum"` for aligned quantities; scaled amounts in `titleMedium`, names in `bodyLarge`.
- Input fields: `OutlinedTextField` with clear errors ("Can't read '2-3'"). Unit picker as a `ModalBottomSheet` grid grouped by family, with the accessory bar chips for the common ones.
- Motion: `animateItem()` on list rows, `AnimatedContent` for value changes on scale.
- Accessibility: 48dp targets, TalkBack-friendly fraction descriptions, font-scale testing up to 200%, contrast check in dark mode.
- Predictive back (Navigation Compose handles it; opt in through `android:enableOnBackInvokedCallback`).

---

## 5. Architecture and Tech Stack

### 5.1 Modules

Start lean. Split features into modules later only if build times hurt.

```
recipe-scaler/
├── app/                      # Application, MainActivity, NavHost, Hilt entry, theme wiring
├── core/
│   ├── domain/               # kotlin("jvm") — NO Android. Models, KitchenUnit, QuantityParser,
│   │                         #   KitchenFormatter, ScaleRecipe, RecipeOcrParser, NameCorrector
│   ├── data/                 # Room DB + DAOs, DataStore prefs, repositories (interfaces live in domain)
│   └── designsystem/         # Theme, typography, shared components (AccessoryBar, QuantityText)
└── feature/                  # Optional: packages inside :app at first
    ├── recipes/              # list + editor
    ├── scale/
    ├── settings/
    └── importer/             # Phase 5: ML Kit + AI
```

Making `:core:domain` a **plain JVM module** means the compiler enforces "no Android in business logic". It also gives you fast tests and the cleanest source for the iOS port.

### 5.2 Layers and State

```
Compose Screen ──(events)──▶ ViewModel ──▶ UseCase / pure domain ──▶ Repository ──▶ Room / DataStore
      ▲                          │
      └──── StateFlow<UiState> ──┘   (derived with combine(...).stateIn(WhileSubscribed(5_000)))
```

- **One `UiState` data class per screen.** Scaling is a pure function, so derive scaled output instead of calling `recalculate()` imperatively:

  ```kotlin
  val uiState = combine(recipeRepo.observe(id), scaleMode, prefsRepo.prefs) { recipe, mode, prefs ->
      ScaleUiState(recipe = recipe, mode = mode, result = scaleRecipe(recipe, mode, prefs))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScaleUiState.Loading)
  ```

- Replace the 10 `show*Dialog` booleans with `val dialog: EditorDialog?` (sealed interface).
- One-off effects (snackbar, share intent): a `Channel<Effect>` collected with `LaunchedEffect`, or a state field that's consumed and cleared.
- **Use cases only where they earn their keep:** `ScaleRecipe`, `ParseIngredientLine`, `ImportRecipeText`, `SaveRecipe` (validation + repo). Don't wrap simple repository reads.
- **DI:** Hilt (same as ToolStack). Navigation: Navigation Compose with type-safe `@Serializable` routes.

### 5.3 Persistence

| Data | Store | Why |
| --- | --- | --- |
| Recipes and ingredients | **Room** | Relational (recipe 1→N ingredients), ordering, search, stable IDs, rename without delete/insert, migrations. |
| Settings (unit system, display mode, theme) | **DataStore Preferences** | A few scalar values. |
| Editor draft | Room (a `isDraft` flag or separate table) | Survives process death without a save dialog. |

```kotlin
@Entity data class RecipeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val yieldAmount: Double, val yieldLabel: String,
    val createdAt: Long, val updatedAt: Long)

@Entity(foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = CASCADE)],
        indices = [Index("recipeId")])
data class IngredientEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long, val position: Int, val rawQuantity: String,
    val unitKey: String, val name: String, val note: String?)
```

- Turn on `exportSchema = true` and commit `schemas/`, then test migrations with `MigrationTestHelper`.
- Store `rawQuantity` text (what the user typed) and re-parse on read. Don't store the scaled value.
- **No data migration from ToolStack** is possible (different `applicationId`). If users need it, add "Export recipes (JSON)" to ToolStack and "Import JSON" to the new app. That's optional.

### 5.4 Tech Stack

Kotlin 2.x + KSP · Compose BOM + Material 3 + `material3-adaptive` · Hilt · Navigation Compose · Room · DataStore · kotlinx.serialization · Coroutines/Flow · JUnit4 (matches existing tests) + Turbine for Flow tests + Compose UI tests · optional Roborazzi screenshot tests · R8 + Baseline Profiles · version catalog (`libs.versions.toml`). Pick the latest **stable** versions at project creation, ideally ones published at least 7 days earlier.

Phase 5 (in v1): ML Kit Text Recognition + Document Scanner, Firebase AI + App Check, Play Billing, Google Mobile Ads SDK + User Messaging Platform (consent).

---

## 6. Phased Roadmap

Each phase has exit criteria. Don't start the next phase until they're met.

### Phase 0: Project Setup
- [ ] Decide app name, `applicationId` (permanent once published), and package namespace.
- [ ] New repo; Android Studio "Empty Compose Activity"; version catalog; `minSdk 26` (matches ToolStack), `compileSdk/targetSdk 36`.
- [ ] Add `:core:domain` (JVM), `:core:data`, `:core:designsystem` modules; Hilt; CI (GitHub Actions: `./gradlew test lint assembleDebug`).
- [ ] Add `AGENTS.md` with build/test commands.
- **Exit:** empty app builds in CI; `:core:domain` has one passing test.

### Phase 1: Extract and Refactor Core Logic (most important)
- [ ] Copy `RecipeScalerCalculator`, `RecipeOcrParser`, `IngredientNameCorrector`, models, and their tests into `:core:domain` (new package). Get them green **unchanged**.
- [ ] Add characterization tests for current behavior on the B1–B10 inputs, marked as "documents old behavior".
- [ ] Introduce `KitchenUnit` enum; migrate `ALL_UNITS`/`canonicalUnit` callers (OCR parser unit lookup, AI mapper).
- [ ] `QuantityParser`: return `Double?`, handle thousands separators and ranges (B8–B10).
- [ ] `KitchenFormatter` per §3.3, driven by the spec table. Start from today's `toTsp`/`decompose`/`formatUsVolume`, then add ½ tsp snapping, cup fractions, and the fewest-parts selection. Fixes B1–B7. Rewrite the old-behavior tests listed at the end of §3.3.
- [ ] `ScaleRecipe` with `ByYield / ByIngredient / ByFactor` and family-mismatch errors.
- [ ] Split `lowConfidence` out of the editable model (`ParsedIngredient(ingredient, confidence)`).
- **Exit:** `:core:domain` has zero Android imports; all ported tests plus the new spec tests pass; B1–B10 have tests asserting the *new* behavior.

### Phase 2: Data Layer
- [ ] Room DB, entities, DAOs, `RecipeRepository` (interface in domain, impl in data).
- [ ] DataStore `SettingsRepository`.
- [ ] DAO tests (Robolectric or instrumented) and repository tests with fakes.
- **Exit:** CRUD plus observe-by-id Flow tested; schema exported.

### Phase 3: MVP UI
- [ ] Theme (dynamic color + fallback), typography, `QuantityText` component.
- [ ] Recipe List → Editor → Scale View → Settings with type-safe navigation.
- [ ] Accessory bar (fraction + unit chips), autosave draft, Paste-text import.
- [ ] Copy/Share (reuse `generateFormattedRecipe`).
- [ ] ViewModel tests with fake repos + Turbine; one Compose UI test per screen's happy path.
- **Exit:** you can enter a recipe, scale it all three ways, save it, and reopen it on a real device.

### Phase 4: Polish
- [ ] Adaptive list-detail layout; landscape and foldable checks.
- [ ] Accessibility pass (TalkBack fraction speech, 200% font scale, contrast).
- [ ] Checklist/cook mode (keep screen on), Undo on delete, empty states, scale-warning copy.
- [ ] App icon (adaptive + themed monochrome), splash screen.
- **Exit:** no a11y scanner warnings; screenshots look right on phone, tablet, and dark mode.

### Phase 5: Import, Monetization and Ads (required for v1)
- [ ] Ads: Google Mobile Ads SDK (AdMob) + **UMP consent** (GDPR/US-states consent form before the first ad request). Prefer banner placement on list/scale screens; no interstitials mid-cooking. Hide ads for Pro if Pro removes them.
- [ ] Move image helpers out of the old VM into `ImagePreprocessor` (data layer).
- [ ] ML Kit scan behind an `OcrEngine` interface → `RecipeOcrParser.parseLines`.
- [ ] AI import: `AiRecipeImporter` + `FirebaseAiRecipeImporter` + quota repo; new Firebase project and App Check.
- [ ] Billing/paywall if AI import is paid. Port `RecipeScalerViewModelAiTest` scenarios to the new import VM.
- **Exit:** scan and AI paths tested with fakes; credits refunded on every non-success (same rule as today).

### Phase 6: Play Store Readiness
- [ ] Release signing (Play App Signing), R8 rules verified on a release build, Baseline Profile.
- [ ] Meet Play's current **target API level** requirement (check Play Console; `targetSdk 36` today).
- [ ] Privacy policy URL + **Data safety** form. Declare ads (advertising ID, device identifiers via AdMob), the Firebase AI image upload for AI import, and purchase history (Billing). Answer "Contains ads: Yes" in Play Console.
- [ ] `AD_ID` permission declared (AdMob adds it automatically; verify it's in the merged manifest).
- [ ] Store listing: short/full description, phone + tablet screenshots, feature graphic, content rating questionnaire.
- [ ] Crash reporting choice (Firebase Crashlytics, or rely on Play Console vitals).
- [ ] **Closed testing:** new *personal* developer accounts must run a closed test with a minimum number of testers for a minimum period before production access. Check current numbers in Play Console (they've been 12 testers / 14 days) and plan for it early.
- [ ] Internal testing → closed testing → production staged rollout.
- **Exit:** production release approved.

---

## 7. Decisions

Decided on 2026-10-11:

1. **Name (working):** launcher label `Rescale`, Play title `Rescale – Recipe Scaler`. Still open: the `applicationId`. Pick a brand-neutral one (e.g. `com.ryxan.rescale` or `com.ryxan.recipescaler`) so a later rename of the app doesn't matter. The ID is permanent once published.
2. **Monetization:** free with ads. **Pro** unlocks AI recipe import (accurate handwriting import). Ads, billing, and AI import are all **in v1**. Still open: does Pro also remove ads?
3. **Measurement system:** user-selectable in Settings (US / Metric / Match recipe). First-run default follows the device locale (US locale → US, else Metric).
4. **v1 scope:** a complete app at launch, with no rush to production. Photo scan (ML Kit) and AI import ship in v1, so Phase 5 is required.
5. **Repo:** <https://github.com/ryxan/RecipeScaler-Android>, local at `C:\Users\ryxan\RecipeScaler-Android`. Code was extracted from ToolStack at tag `recipe-scaler-extract-base`.

Still open, not blocking: **should ToolStack keep its Recipe Scaler** after the standalone launches? If yes, consider pointing it at the new `:core:domain` later (published as a local Maven artifact or a git submodule) so the two never drift.

---

## 8. First Week Checklist

1. Pick the `applicationId` (§7 #1).
2. Phase 0 skeleton with `:core:domain` as a JVM module.
3. Copy the domain files and tests; get them green unchanged.
4. Write the §3.3 spec table as failing tests in `KitchenFormatterTest`.
5. Implement `KitchenUnit` + `QuantityParser` fixes, then `KitchenFormatter` until the spec table passes.
