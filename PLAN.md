# Unit Converter — Review & Remediation Plan

Scope: `domain/calculator/UnitConverterData.kt`, `domain/model/UnitConverterModels.kt`,
`ui/unitconverter/*`, nav wiring in `MainActivity.kt`, `UserPreferencesRepository`
converter prefs, and unit tests.

Ranked by severity. Check the box when an item is fixed and verified.

---

## Critical

- [x] **C1 — Locale decimal separator breaks input.** ✅ DONE
  `parseLocalizedNumber` in `UnitConverterUiState` companion: locale decimal
  separator, validated 3-digit grouping, whitespace/NBSP/NNBSP stripping,
  Arabic-Indic digit translation, ambiguity rejection (`1,5` in en-US / `1.5`
  in de-DE → `—` rather than silently 15). `formatResult` now takes a
  `Locale` param (default `Locale.getDefault()`) with grouping + localized
  sci mantissa.

- [ ] **C2 — Focus behaviour destroys input.** ⏸ NOT PLANNED (deferred by user)
  `autoFocus` re-requests focus on every composition, including after config change:
  if `activeField == TO`, rotation fires `onFromTextChanged("")` and wipes both fields.
  Also, tapping the inactive field clears *both* fields, so a user can never tweak a
  result. Fix: gate auto-focus on a `rememberSaveable` first-focus flag; tapping the
  inactive field should flip `activeField` without clearing.
  `UnitConverterDetailScreen.kt:125-131, 201-205, 228-231`

- [ ] **C3 — Zero tests for conversion math / formatting.** ✅ DONE
  Added `UnitConverterDataTest` (round-trip for every unit in every category +
  ~60 hand-checked spot conversions) and `UnitConverterUiStateTest`
  (`recalculate`/`formatResult` behaviour). Exposed and fixed the H1 factor errors.

## High

- [x] **H1 — Wrong liquid-rate factors.** `fl oz/acre (US)` was `0.07300`,
  correct is `0.0730778` (9.35396/128). `Imp gal/acre` was `11.2333`,
  correct is `11.2336`. Fixed in `UnitConverterData.kt`.

- [x] **H2 — Truncated/inconsistent factor precision.** ✅ DONE
  Shared exact constants (`KG_PER_LB`, `L_PER_US_GAL`, `HA_PER_ACRE`,
  `J_PER_BTU`, `J_PER_FT_LBF`, `M_PER_FT`); all derived factors now written as
  expressions (`1852.0 / 3600.0`, `L_PER_US_GAL / 128`, `KG_PER_LB / 16`).
  `Year (Julian)` → `Year (avg)` (Gregorian 31,556,952 s) so 12 mo = 1 yr exactly.

- [x] **H3 — No domain bounds; stale sentinel code.** ✅ DONE
  `UnitEntry.minValue` added; set for temperature (absolute zero per scale) and
  all fuel units (0). `recalculate` rejects out-of-bounds input AND results
  (`-300 °C → —`, `-10 mpg → —`); 1e-9 epsilon protects the absolute-zero
  boundary from float noise. Unbounded units still accept negatives
  (depth/offsets are legitimate). Dead `MAX_VALUE` branch removed during C1.
  Remaining polish (proper error UI vs `—`) tracked under M2.

- [x] **H4 — Category identity is fragile.** ✅ DONE
  `UnitCategory.id` added; nav route is now `unit_converter_detail/{categoryId}`
  and prefs store ids. Single normalization table `UnitConverterData.idFor`
  accepts id / current name / legacy name — replaced the two duplicated maps.
  Lookup precedence: id > current name > legacy. `findByName` removed in favor
  of `findById`; unused `categoryIndex` param dropped from the detail screen.

## Medium

- [x] **M1 — Accessibility.** ✅ DONE — value fields show
  `unit_converter_from`/`unit_converter_to` labels + `input_hint` placeholder;
  unit pickers labeled "From unit"/"To unit" (new `unit_converter_unit`
  string). Labels follow *role*, not position: the active (typed) field is
  always "From" since both fields are editable.

- [ ] **M2 — Partial-input flicker.** `-`, `.`, `1e` transiently show `—` as if
  invalid. Treat numeric prefixes as "pending"; reserve `—` (+ `isError`) for
  genuinely invalid text.

- [x] **M3 — `toDoubleOrNull` too permissive.** ✅ DONE — folded into
  `parseLocalizedNumber`: `NaN`, `Infinity`, `1d`, `5f`, `0x1p3` all rejected.

- [ ] **M4 — Inconsistent output formatting.** Magic thresholds `1e10`/`1e-4`;
  `1 mm→km` → `1e-06` but `1 GB→B` → `1000000000` (no grouping); small values get
  8 decimals vs 6 for ≥1. Format to fixed significant digits with locale grouping.

- [x] **M5 — Backspace icon.** ✅ DONE — removed entirely (IME already has
  backspace); dropped `onBackspace` from screen + ViewModel and the now-unused
  `isActive` param.

- [ ] **M6 — No swap button.** ⏸ SKIPPED (deferred by user)
  Standard converter affordance missing; requires two dropdown trips to
  reverse direction.

- [ ] **M7 — Dropdown clipping.** Hard-coded `150.dp` + `singleLine` + default
  `Clip` truncates `bushel/acre (wheat 60 lb)` etc. Use `TextOverflow.Ellipsis`.

- [ ] **M8 — Concentration duplicates/ambiguity.** `mg/L (ppm)` vs `ppm` and
  `g/L` vs `kg/m³` are the same unit twice. `ppt = 1e-6 mg/L` means parts-per-
  *trillion*, but agri/water contexts read `ppt` as parts-per-*thousand*
  (1000× off). Rename explicitly or drop.

- [x] **M9 — `IME Done` is a no-op.** ✅ DONE — `KeyboardActions(onDone)`
  clears focus via `LocalFocusManager`.

## Low / Code quality

- [ ] **L1 — Dead code:** `UnitConverterViewModel.factory`, `UnitConverterData.findByName`
  (KDoc claims detail screen uses it — false), `UnitCategory.description` (never
  rendered), `categoryIndex` param of `UnitConverterDetailScreen` (ignored),
  `UnitConverterListViewModel.moveCategory` + `applyOrder` (no reorder UI exists;
  9 tests cover unreachable code), 3 unused strings (M1),
  `Spacer(width = 0.dp)`.
- [ ] **L2 — Layering:** domain model holds Compose `ImageVector`; domain
  calculator imports `material.icons`. Move icons to a UI-side map by category id.
- [ ] **L3 — `UnitEntry` is a `data class` with lambda fields** — equality is
  reference-based; works only because entries are singletons. Model linear units
  as `(factor, offset)` data or drop `data`.
- [ ] **L4 — Unexplained magic numbers:** fuel constants `235.214583`,
  `282.480936` → write as `100 * 3.785411784 / 1.609344`, `100 * 4.54609 / 1.609344`.
- [ ] **L5 — Mixed conventions:** `cal = 4.184` (thermochemical) beside
  `BTU = 1055.05585` (IT). Document which calorie.
- [ ] **L6 — Input text not process-death safe:** `fromText`/`toText` live only in
  `MutableStateFlow`; unit selections persist, values don't. Keep in
  `SavedStateHandle`.
- [ ] **L7 — `"—"` sentinel compared in both VM and UI** (`toText != "—"`).
  Model result as sealed type `Empty | Invalid | Value(text)`.
- [ ] **L8 — Summary line echoes raw input** (`"  5 "`, `"1e3"` verbatim).
- [x] **L9 — Singular vs plural labels.** ✅ DONE — plural per user preference:
  `Feet`, `Metres`, `Pounds`, `US gallons`, `Acres`, `Kilobytes`, etc.
  Invariant/scale/compound units keep singular or symbol forms (`Stone`,
  `Horsepower`, `Celsius`, `Kelvin`, `ft·lbf`, `kgf·m`, `mg/L`, `ppm`).
  Summary line unchanged — symbols are invariant (`5 ft = 1.524 m`).
  Note: prefs store unit *labels*, so this resets saved unit selections to
  defaults on upgrade (graceful fallback, no crash).
- [x] **L10 — Label style is inconsistent.** ✅ DONE — folded into L9: all
  abbreviations and symbol-labels spelled out (`Nautical miles`,
  `Square millimetres`, `Kilometres per hour`, `US gallons per minute`,
  `Pounds per square inch`, `Litres per hectare`, `Miles per gallon (US)`).
  Symbols still shown as dropdown subtitle.

## Fix order

1. ~~C3 tests~~ ✅ (landed; caught H1)
2. ~~C1 + M3~~ ✅ — strict locale-aware parsing + locale output
3. C2 — focus/clear behaviour (⏸ deferred); ~~M1/M9~~ ✅; M6 swap (⏸ skipped)
4. ~~H4 — stable category ids~~ ✅; ~~H2/H3~~ ✅
5. M8 — concentration unit cleanup
6. M2–M7 — input/UX polish
7. L1–L8 — dead code + structure sweep
