# Calculator — Polish Plan

Review of `domain/calculator/CalculatorEngine.kt`, `ui/calculator/CalculatorViewModel.kt`,
`ui/calculator/CalculatorScreen.kt`, and history persistence in `UserPreferencesRepository`.

Unlike the recipe-scaler plan, **items 1–5 are real correctness bugs** — they produce wrong
answers or corrupt state. The rest are polish.

Suggested order: **1 → 5** (one engine pass + tests), **6 → 9** (engine/state nits),
**10 → 11** (keypad changes), then the polish batch.

---

## Correctness bugs

### 1. Divide-by-zero mid-expression silently becomes 0
`calculateFromTokens` stores intermediates as *formatted strings* and re-parses them.
A `NaN` becomes `"Error"`, which `toDoubleOrNull()` coerces to `0.0` on the next pass.

- [x] Keep intermediates as `Double` (`MutableList<Any>` or a sealed token type); format only at the end.
- [x] Repro: `5 ÷ 0 + 3 =` currently shows `= 3` and records `5 ÷ 0 + 3 = 3` in history. Should be `Error`.

Where: `CalculatorEngine.kt` `calculateFromTokens` ~L340–L384.

### 2. Intermediate rounding to 10 significant digits
Same root cause as #1: `formatNumber(left * right)` truncates via `%.10g` mid-calculation.

- [x] Fixed by #1 (no string round-trip between passes).
- [x] Repro: `1 ÷ 3 × 3 =` currently yields `0.9999999999` instead of `1`.

### 3. "Error" leaks into the next calculation
After `5 ÷ 0 =`, `pendingInput = "Error"`. Pressing `+ 5 =` produces history `Error + 5 = 5`
(digits correctly reset, operators don't).

- [x] Treat any operator/percent/sign-flip press while the display is `"Error"` as a fresh start (same as a digit press).

Where: `onOperator` ~L130, `onPercent` ~L262, `onSignFlip` ~L284.

### 4. Sign flip corrupts the typed string
`onSignFlip` round-trips through `Double` + `formatNumber`: `5.` → `-5` (decimal point lost),
`5.10` → `-5.1`, `0` → `-0` (then typing `5` yields `-05` — leading-zero guard only checks `current == "0"`).

- [x] Toggle a `-` prefix on `pendingInput` directly instead of reformatting the value.

Note: currently unreachable — no ± key exists on the keypad (see #10). Fix together.

Where: `onSignFlip` ~L284–L298.

### 5. Percent result stays editable as text
After `%`, `pendingInput` holds a formatted number; typing `3` appends (`0.05` → `0.053`).
Small values format as `1e-05` and appending produces `1e-053`.

- [x] Commit the percent result like `=` does — next digit starts a fresh number.
- [x] **Decide semantics deliberately:** `200 + 10 %` currently gives `200 + 0.1`. Phone-calculator
  convention is: for pending `+`/`−`, percent of the left operand (`200 + 20`); for `×`/`÷`,
  plain `÷100`. *(implemented: contextual/iOS semantics)*

Where: `onPercent` ~L262–L279; test `onPercent with operator still divides by 100` in `CalculatorEngineTest.kt`.

### 6. Result formatting cliff at 1e10
Input allows 15 digits, but `formatNumber` switches to scientific notation at `1e10`.
`12345678901234 + 0 =` displays `1.23456789e+13`.

- [x] Raise the plain-format threshold to ~1e15 (matching the 15-digit input cap); only use `e` notation beyond that.

Where: `formatNumber` ~L400–L419.

---

## State / UI inconsistencies

### 7. `display` is never rendered
`DisplayPanel` takes `display` but only draws `expression` and `liveResult`. Initial `expression`
is `""` (blank screen) while after AC it's `"0"`.

- [x] Either default `expression = "0"` or render `display` when `expression` is empty; drop the unused param.
  *(implemented: `expression.ifEmpty { display }`)*

Where: `CalculatorState` ~L29, `DisplayPanel` ~L308.

### 8. History is unbounded
Every `=` appends to a single DataStore string with no cap.

- [x] Cap at ~50 entries in `onEquals` (and in the repository on load, for existing users).
  *(implemented: `CalculatorEngine.HISTORY_LIMIT = 50`, applied in `onEquals` and the repository flow)*

Where: `onEquals` ~L189–L195; `UserPreferencesRepository.calculatorHistory` ~L197–L228.

### 9. History race on cold start
If `=` is pressed before the DataStore flow emits, the entry isn't saved and the subsequent
emission overwrites in-memory history, dropping it.

- [x] Make the collector merge rather than replace, or use `first()` for the initial load instead
  of a permanent collector that echoes your own writes back.
  *(implemented: `first()` + merge, with a one-time re-persist when the merge differs;
  `onClearHistory` now clears in-memory state directly)*

Where: `CalculatorViewModel` init ~L41–L51, `onEquals` ~L63–L73.

### 10. Dead code
- [x] Remove unreachable `else { listOf(op) }` in `onOperator`.
- [x] Remove unused `computeLiveResult` and `evaluate`.
- [x] `onClear` ignores `mode` while every other handler branches on it — intentional or not, worth noting.
  *(verified: `state.copy` preserves `mode`, so the branch isn't needed — noted, no change)*

---

## Keypad & screen

### 11. No ± key; `00` is low-value
Engine supports `onSignFlip` but there's no key for it.

- [x] Swap `00` for `±` (common layout: `±  0  .  =`); fix #4 in the same change.

Where: `Keypad` row 5 ~L496–L505.

### 12. Hardcoded strings
"More options", "Clear History", "Clear History?", "This will permanently delete…", "Clear",
"Cancel" bypass `strings.xml` while the rest of the screen uses resources.

- [x] Move all to `strings.xml`.

Where: `CalculatorScreen.kt` ~L181–L189, ~L281–L302.

### 13. Dark theme
App has `darkColorScheme`, but the calculator hard-codes `CalcBackground` (light pastel),
`CalcDisplayDark`, and `Color.White` content — glares in dark mode.

- [x] Add dark variants of the `Calc*` palette or derive from `MaterialTheme.colorScheme`.

Where: `ui/theme/Color.kt` L10–L16, `CalculatorScreen.kt` throughout.

### 14. Consolidate key composables
Five near-identical copies (`CalcDigitKey`, `CalcOperatorKey`, `CalcClearKey`, `CalcFunctionKey`,
`CalcEqualsKey`) differ only in colour and font size.

- [x] One `CalcKey(label, container, fontSize)` would cut ~100 lines.

Where: ~L511–L628.

---

## Polish

### 15. Tap history to recall
- [x] Tapping a history item loads its result into the display (or long-press to copy).
  *(implemented both: tap recalls via `onHistoryRecall`, long-press copies)*

### 16. Full-history view
The horizontal `|`-separated strip gets unreadable past ~5 items.

- [x] Bottom-sheet or full "History" view instead of scrolling the strip.
  *(implemented: `HistorySheet` `ModalBottomSheet` from the overflow menu)*

### 17. Haptics
Nothing in the app uses `LocalHapticFeedback`.

- [x] Light `performHapticFeedback` on key press makes the keypad feel much better.

### 18. Standard affordances
- [x] Long-press ⌫ to clear.
- [x] Long-press result to copy to clipboard.

### 19. Accessibility
- [x] `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the live-result/expression text.
- [x] "More options" `contentDescription` is untranslated (covered by #12).

---

## Tests to add alongside the fixes

- [x] `5 ÷ 0 + 3 =` → `Error` (#1)
- [x] `1 ÷ 3 × 3 =` → `1` (#2)
- [x] error then operator resets instead of chaining (#3)
- [x] sign flip preserves `5.` / `5.10`; `0 ± 5` → `-5` not `-05` (#4)
- [x] digit after `%` starts a fresh number (#5)
- [x] large whole-number result formats without `e` notation below 1e15 (#6)
- [x] history capped at N entries (#8)

Test file: `app/src/test/java/com/toolstack/io/domain/calculator/CalculatorEngineTest.kt`
(currently 15 tests). Verify with `.\gradlew.bat :app:test`.

---

## Suggested batches

| Batch | Items | Notes |
|-------|-------|-------|
| 1 | 1, 2 | One change — keep intermediates as Doubles. Real wrong answers. |
| 2 | 3, 5, 6, 7 | Small engine fixes + display cleanup. |
| 3 | 10, 4 | Add ± key, fix sign flip — same change. |
| 4 | 8, 9 | Persistence. |
| 5 | 12–14 | Strings, dark theme, key consolidation. |
| 6 | 15–19 | Polish, any order. |
