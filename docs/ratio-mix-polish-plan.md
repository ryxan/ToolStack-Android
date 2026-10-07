# Ratio Mix Calculator — Polish Plan

Review of `ui/ratiomix/RatioMixViewModel.kt` (includes `RatioMixUiState.recalculate`),
`ui/ratiomix/RatioMixScreen.kt`, and preset persistence in `UserPreferencesRepository`.

Nothing produces wrong answers, but items 1–5 are real bugs (a crash path, dead error
state, rejected input, broken autofocus, lossy formatting). The rest are polish.

Suggested order: **1 → 4** (bugs, small and safe), **6** (highest user-impact feature),
then the polish batch. Add `RatioMixUiStateTest` alongside item 1 — `recalculate()`
is pure and trivially testable.

---

## Bugs

### 1. `ratioMixPresets` flow has no `IOException` guard
Every other flow in the repository does `.catch { if (e is IOException) emit(emptyPreferences()) else throw e }`.
This one doesn't, and the ViewModel collects it via `launchIn(viewModelScope)` — a
DataStore read failure crashes the screen instead of showing "no presets".

- [x] Add the same `.catch` used by `lastConverterCategory` at ~L146–L149.

Where: `UserPreferencesRepository.kt` ~L112–L116.

### 2. `hasError` is computed but never rendered
Typing `0`, `-1`, `abc`, or clearing a ratio field silently removes the results card —
no error text, no red field, no hint why. Same for the volume field.

- [x] Add per-part `isValid` to `RatioPart` (or compute in the composable) and set
  `isError` on the offending `OutlinedTextField`, ideally with `supportingText`.
  Done as `RatioPart.ratioError` + `supportingText` (string `ratio_mix_error_positive`).
- [x] Same treatment for the volume field when non-blank but unparseable / ≤ 0.
  Done via `RatioMixUiState.volumeError`.
- [ ] Alternatively drop `hasError` if a design decision is made to keep silent removal.
  *(not taken — error state implemented)*

Where: `RatioMixUiState.recalculate` ~L219–L278; `RatioPartRow` ~L465–L488;
`VolumeInputRow` ~L535–L545.

### 3. Comma decimals fail silently
`toDoubleOrNull()` rejects `3,5`. Users on comma-locale keyboards (the `Decimal`
keyboard type offers a comma there) get no results.

- [x] Normalise `replace(',', '.')` before parsing, matching
  `RecipeScalerCalculator.parseQuantity` (~L250). Done via `parseDecimal` helper.

Where: `recalculate` ~L220, ~L236, ~L251.

### 4. Save dialog never focuses the name field
A `FocusRequester` is created and attached to the `OutlinedTextField` but
`requestFocus()` is never called — the keyboard doesn't open.

- [x] `LaunchedEffect(Unit) { focusRequester.requestFocus() }` inside `SavePresetDialog`.

Where: `RatioMixScreen.kt` ~L249, ~L271.

### 5. Small values collapse to "0"
`formatRatio` uses `%.2f`: a ratio of `0.001` summarises as `0`; `0.125` as `0.13`.
`formatVolume` similarly shows `0 L` below 0.0005 L.

- [x] Echo the trimmed ratio text in the summary (it's already validated) instead of
  reformatting, or use ~4 significant decimals. *(input echo chosen; `formatRatio` deleted)*
- [x] For volumes below the unit's smallest sensible step show `<0.001 mL`-style text —
  or prefer auto-downshifting units (item 6). *(`<0.001` floor marker chosen)*

Where: `formatRatio` ~L280, `formatVolume` ~L284.

---

## Functional improvements

### 6. Results always display in the input unit
Enter *1 gal* at *100 : 1* and the fertilizer line reads `0.01 gal` — unmeasurable.
This is the biggest usability gap in the module.

Note: the litres round-trip in `recalculate` (`× toLitres … ÷ toLitres`) is currently
a no-op since ratio splitting is unit-agnostic — it only adds floating-point noise.
Keep it if auto-scaling picks units by litres magnitude; otherwise simplify.

- [x] Auto-downshift small results (e.g. < 0.1 of the selected unit) to mL / fl oz,
  or show a secondary smaller-unit value in parentheses.
  *(`VolumeUnit.smaller` chain; `formatVolumeAuto` downshifts while value < 1)*
- [x] ~~Alternative: per-row unit picker on results (heavier; probably unnecessary).~~
  *(not taken — auto-downshift chosen)*

Where: `recalculate` ~L240–L267.

### 7. Missing practical units
No US fl oz, cups, tbsp, tsp, or pints — what fertilizer/concentrate labels actually use.

- [x] Add at least `US_FL_OZ` (0.0295735 L) and `US_CUPS` (0.236588 L);
  tbsp/tsp optional (0.0147868 / 0.00492892 L). *(all four added)*
- [x] Consider `PINTS` (0.473176 L). *(added)*
- [x] Rename `Igal` symbol → `imp gal`; "Cubic m" label → "Cubic metres" for consistency.

Where: `VolumeUnit` enum ~L178–L185.

### 8. Percentage per part in results
`Water 75% · 7.5 L` is a cheap, useful addition and lets users sanity-check the ratio.

- [x] Add `ratio / ratioSum` percentage to each `RatioResult` row.
  *(`RatioResult.percentText`, one-decimal trimmed, `<0.1%` floor)*

Where: `recalculate` ~L241, `ResultsCard` ~L626–L643.

### 9. Preset delete is one tap, no confirm/undo — and sits next to Load
The trash `IconButton` is immediately adjacent to the Load `TextButton` on every card;
a mis-tap deletes a preset with no recovery.

- [x] "Deleted X — Undo" snackbar (Recipe Scaler already has a `SnackbarHost` pattern,
  `RecipeScalerScreen.kt` ~L483), or a confirmation AlertDialog.
  *(snackbar + `onRestorePreset` re-save chosen)*
- [x] Save dialog: when the name matches an existing preset, note "Replaces existing
  preset" (currently a silent overwrite — `saveRatioMixPreset` ~L124–L127).
  *(`supportingText` on the name field)*

Where: `PresetRow` ~L366–L378, `SavePresetDialog` ~L251–L289.

### 10. Load preset gives no feedback
Presets render at the bottom while the parts they replace are at the top — tapping
Load changes nothing in the current viewport.

- [x] Scroll-to-top (`LazyListState.animateScrollToItem(0)`) or a "Loaded X" snackbar.
  *(scroll-to-top chosen)*

Where: `RatioMixScreen` ~L124–L236, `PresetRow` ~L366.

### 11. No persistence of last mix
On process death the user is back to `Water 3 : Fertilizer 1`. The converter module
persists its last category/units; this screen could persist last parts + unit + mode
using the same Base64-record format already used for presets.

- [x] Persist `parts`, `selectedUnit`, `mode` (and arguably `knownPartIndex`) to
  DataStore on change; restore in `init`.
  *(`lastRatioMixState`/`saveRatioMixState`, Base64 record incl. `volumeText`,
  400 ms debounced; persist starts only after restore read)*

---

## Cleanup / polish

### 12. Dead code & unused strings
- [ ] `RatioPart.resultVolume` / `resultText` are never written — results go in the
  separate `RatioResult` list. Remove both fields.
- [ ] Unused string resources: `ratio_mix_load_preset`, `ratio_mix_preset_part_summary`.

Where: `RatioPart` ~L189–L195; `strings.xml` ~L162, ~L165.

### 13. Hardcoded strings
- [ ] Known-part dropdown uses `"Part ${index + 1}"` while the rest of the screen uses
  `R.string.ratio_mix_part_fallback`.
- [ ] `VolumeUnit.label` strings are hardcoded English — move to resources if the app
  is localised elsewhere (check convention in other modules first).

Where: `RatioMixScreen.kt` ~L434; `VolumeUnit` ~L178–L185.

### 14. LazyColumn keys + IME action
- [ ] `itemsIndexed(key = { i, _ -> i })` — index keys defeat the purpose; removing a
  middle row leaves stale text-field internal state on the row that slides up. Give
  parts a stable `id` or drop the key.
- [ ] Last ratio field uses `ImeAction.Next` with nothing after it — `Done` (or jump to
  the volume field) is cleaner.

Where: ~L155, ~L485.

### 15. Unit dropdown width
Fixed `130.dp`; "Millilitres" truncates at larger font scales.

- [ ] Show the symbol (`mL`) in the collapsed field, or `weight()` the box.

Where: `VolumeInputRow` ~L550.

### 16. Clamp in `onKnownPartIndexChanged`
- [x] UI only passes valid indices, but `recalculate` already clamps — make the setter clamp
too so state can never hold an out-of-range index.

Where: `RatioMixViewModel.kt` ~L99–L101.

---

## Tests to add

`recalculate()` is pure — every other UiState in the app already has a `*UiStateTest`.

- [x] Total→Parts: `3:1` over `40 L` → `30 / 10`
- [x] Part→Total: `3:1` with known `30` on part 0 → results `30/10`, total `40`
- [x] Invalid ratio (`0`, `-1`, `abc`, blank) → `hasError`, empty results (#2)
- [x] Comma decimal `3,5` parses (#3)
- [x] Sub-`%.2f` ratios don't collapse to `0` in the summary (#5)
- [ ] `onRemovePart` shifts/clamps `knownPartIndex` correctly (all three branches)
- [ ] `onLoadPreset` clamps `knownPartIndex` to `newParts.lastIndex`
- [ ] Preset decode: fewer than `MIN_PARTS` pads with defaults; more than `MAX_PARTS` truncates

Test file: `app/src/test/java/com/toolstack/io/ui/ratiomix/RatioMixUiStateTest.kt`
Verify with `.\gradlew.bat :app:test` — baseline is 395 tests, 0 failures.

---

## Suggested batches

| Batch | Items | Notes |
|-------|-------|-------|
| 1 | 1, 3, 4, 16 | Tiny, safe fixes. |
| 2 | 2 + tests | Error-state UI + `RatioMixUiStateTest` scaffolding. |
| 3 | 5, 6, 7 | Value formatting + units — one pass over `recalculate`. |
| 4 | 8–11 | Results/preset UX. |
| 5 | 12–15 | Cleanup, any order. |
