# Recipe Scaler — UI Polish Plan

Review of `ui/recipescaler/RecipeScalerScreen.kt` and `IngredientInputAccessoryBar.kt`.
Nothing here is broken; these are the changes that take the screen from "works" to "feels finished".

Suggested order: **1 → 4 → 3** (noticed in the first 30 seconds), then **5 / 6 / 7** as one pass
over colours and sizes, then the rest.

> Item 2 (unit picker redesign) is intentionally deferred — see "Deferred" at the bottom.

---

## High impact

### 1. Animate the accessory bar and banners
- [x] Wrap `IngredientInputAccessoryBar` in `AnimatedVisibility(enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut())` instead of a bare `if (showAccessory)`.
- [x] Animate the `LazyColumn` bottom `contentPadding` with `animateDpAsState` so the list doesn't snap when the bar appears/disappears.
- [x] Wrap the scan-processing banner, scan-error banner, and scale-warning banner in `AnimatedVisibility` (or add `Modifier.animateContentSize()` to the card `Column`).

Where: `RecipeScalerScreen.kt` ~L356–L367 (padding), ~L432–L467 (bar), ~L613–L688 (scan banners), ~L1110–L1136 (scale warning).
Reference: `ConduitBendsScreen.kt` is the only screen already using animation.

### 3. Fix Qty / Unit / Ingredient header alignment
- [x] Header row uses `spacedBy(4.dp)` + `Spacer(40.dp)`; ingredient rows use `spacedBy(6.dp)` + a 48dp `IconButton`. Labels drift ~8dp off the fields. Match the header to `spacedBy(6.dp)` + `Spacer(48.dp)`.
- [ ] Alternatively drop the header entirely — the field placeholders ("Qty", "Unit", "Ingredient name") already carry this information. *(not taken — header kept and aligned instead)*
- [x] If keeping it, remove the header's own tinted `Surface`; it's a third surface tint stacked on the card and the servings box.

Where: ~L746–L777 (header), ~L923–L1051 (row).

### 4. Servings row
- [x] Replace the 180°-rotated `ArrowBack` with `Icons.AutoMirrored.Filled.ArrowForward` and drop the `graphicsLayer`.
- [x] Make the two servings fields the visual focal point: larger text (`headlineSmall`, centered), slightly wider than 80dp.
- [x] Add `imeAction = ImeAction.Next` on "Serves" so it hops to "Scale to".

Where: ~L690–L744 (row), ~L1299–L1317 (`ServingsField`).

---

## Medium impact

### 5. Consolidate surface tints
Currently in use: `surfaceVariant @ 0.35`, `@ 0.45`, `@ 0.5`, `surface @ 0.7`, `primaryContainer @ 0.08`.
- [x] Replace alpha-blended tints with M3 tonal surfaces (`surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh`). Pick two levels and use them consistently. *(two levels picked: `surfaceContainerLow` for inner fills/dialog item cards/scaled card, `surfaceContainerHigh` for the servings panel — matches the accessory bar)*
- [ ] Verify in dark mode — alpha tints render differently against the two card backgrounds. *(tonal surfaces are theme-aware, but still worth a manual look)*

### 6. Card hierarchy (Step 1 vs Step 2)
- [x] Original card has `2.dp` elevation on `surface`; Scaled card has `0.dp` elevation and a near-invisible `primaryContainer @ 8%`. The result card should read as the more important one.
- [x] Option A: Scaled card → `surfaceContainerLow` + 1dp `outlineVariant` border.
- [ ] Option B: full-opacity `primaryContainer` on the Scaled card header row only. *(not taken — Option A chosen)*
- [x] `StepBadge(emphasized = true)` renders the *lighter* colour — rename or invert so the parameter matches what it draws. *(inverted: emphasized now draws solid `primary`)*

Where: ~L564–L571, ~L1067–L1074, ~L1269–L1295.

### 7. Ingredient row density
- [x] Fields are `heightIn(max = 48.dp)` but `OutlinedTextField` wants 56dp; placeholders are cramped. Either accept 56dp or move to `BasicTextField` + custom `DecorationBox` for a genuinely compact row. *(accepted 56dp — removed the `heightIn` clamps)*
- [x] Remove-row `Close` icon is full-strength `onSurfaceVariant` on every row. Options: lower alpha, show only on the focused row, or swipe-to-dismiss. *(lowered alpha to 0.6)*

Where: ~L923–L1051.

### 8. Clear-all confirmation
- [x] Destructive action with no confirmation, sitting in the card header. Add a confirm `AlertDialog` (pattern already exists in `LoadRecipeDialog`), or move it to an overflow menu in the `TopAppBar`. *(confirm `AlertDialog` added — `showClearAllConfirm` state gates `viewModel.clearAllIngredients()`)*

Where: ~L595–L609.

### 9. "was X" original-value formatting
- [x] `String.format("%.2f")` shows `0.33` while the scaled value shows `⅓`. Run the original through the same fraction formatter the calculator uses so both halves of the row match. *(caption now uses `RecipeScalerCalculator.formatQuantity(originalQty, unit, keepOriginalUnits)` — same formatter as `displayText`)*

Where: ~L1244–L1261.

### 10. List-picker dialogs → bottom sheets
- [x] `CopyFormatDialog`, `SaveListDialog`, `LoadRecipeDialog` are tap-to-pick lists. Move to `ModalBottomSheet`; this also fixes the unbounded `LazyColumn`-inside-`AlertDialog` height problem with many saved recipes. *(converted to `CopyFormatSheet` / `SaveListSheet` / `LoadRecipeSheet`; recipe lists scroll inside the sheet)*
- [x] `CopyFormatDialog`: replace three stacked `OutlinedButton`s with `ListItem`s (leading icon + one-line description per format).

Where: ~L1321–L1361, ~L1473–L1566, ~L1570–L1676.

---

## Low impact / nits

- [x] Replace `Toast` for "Copied" with a `SnackbarHost` in the `Scaffold` so it's themed and sits above the accessory bar. *(snackbar host padded by `ime ∪ navigationBars` + animated accessory-bar height)*
- [x] Scan spinner in the `TopAppBar` is dimmed because the `IconButton` is `enabled = false`. Set `colors = IconButtonDefaults.iconButtonColors(disabledContentColor = onPrimary)`.
- [x] Scaled-card empty state: add a second `bodySmall` / `onSurfaceVariant` line telling the user what to do. *(added `recipe_scaler_empty_scaled_recipe_hint`: "Scaled quantities appear here automatically as you type" — describes the mechanic instead of repeating the headline)*
- [x] Multiplier pill (`×1.5`) is solid `primary` — heaviest element on screen. Try `secondaryContainer` / `onSecondaryContainer`.
- [x] Disclaimer footer: center it and use `bodySmall`.
- [x] Two filled primary CTAs on one screen (Save + Copy). Make Save a `FilledTonalButton`.
- [x] `OriginalRecipeCard` takes 23 parameters. Bundle the ingredient callbacks into a small `IngredientRowCallbacks` data class before the next round of UI changes. *(6 id-scoped callbacks bundled; card now takes 18 params)*

---

## Deferred

### 2. Unit picker redesign (skipped for now)
The unit accessory bar (3 labelled `FlowRow` sections + Next button row) takes roughly half a phone screen.
Ideas when revisited:
- Collapse section labels into a leading non-clickable chip in a single `LazyRow` per group.
- Move Next to an `IconButton` in the bar's top-right corner so it doesn't need its own row.
- Show the 6–8 most common units first with a "More" chip for the rest.
- Unify chip styling: `FractionChip` uses `titleMedium` text, `UnitChip` uses the default.

---

## Verification

- `.\gradlew.bat :app:test` — baseline 296 tests, 0 failures.
- Manual: check both light and dark mode, keyboard open/closed transitions, and a recipe with 10+ ingredients.
