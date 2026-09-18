# Calculator Full Expression Evaluation Feature

## Overview

The ToolStack calculator now supports **full expression building** with live evaluation. Users can type complete multi-operation expressions like `5 + 5 + 5 - 3`, see the result update in real-time, and only commit the calculation to history when pressing `=`.

This matches modern calculator UX where you can build complex expressions without intermediate evaluations.

## Display Layout (Top to Bottom)

### 1. History Line (Top)
- **Purpose**: Shows past completed calculations
- **Behavior**: 
  - Horizontally scrollable
  - **Auto-scrolls to the right** when new calculations are added
  - Displays calculations separated by ` | ` delimiter
  - Most recent calculations appear on the right
  - Shown with muted color (60% opacity)
  - **Persisted to DataStore** - survives app restarts
  - Can be cleared via "Clear History" menu option
  - Example: `5 + 5 = 10  |  12 × 3 = 36  |  5 + 5 + 5 - 3 = 12`

### 2. Main Expression Line (Middle)
- **Purpose**: Shows the complete active expression being built
- **Behavior**:
  - Updates in real-time as digits and operators are entered
  - Shows full expression like `5 + 5 + 5 - 3`
  - After pressing `=`, displays only the result
  - Uses larger font (32sp) with normal weight
  - Example flow:
    - Typing: `5` → `5 +` → `5 + 5` → `5 + 5 +` → `5 + 5 + 5` → `5 + 5 + 5 -` → `5 + 5 + 5 - 3`
    - After `=`: `12`

### 3. Dynamic Result Preview (Bottom)
- **Purpose**: Shows live evaluation of the current expression
- **Behavior**:
  - Updates continuously as the expression changes
  - Prefixed with `= ` to indicate it's a preview
  - Only appears when there's a valid complete operation
  - Respects operator precedence (× ÷ before + −)
  - Clears when operator is pressed (awaiting next number)
  - Uses medium font (24sp) with 80% opacity
  - Example: `= 12` (while typing `5 + 5 + 5 - 3`)

## Key Behavioral Changes

### Expression Building (NEW!)
Users can now build **full multi-operation expressions** without intermediate evaluations:

**Example: `5 + 5 + 5 - 3`**

| Action | Expression | Display | Live Result |
|--------|-----------|---------|-------------|
| Type `5` | `5` | `5` | ` ` |
| Press `+` | `5 +` | `5` | ` ` |
| Type `5` | `5 + 5` | `5` | `= 10` |
| Press `+` | `5 + 5 +` | `5` | ` ` |
| Type `5` | `5 + 5 + 5` | `5` | `= 15` |
| Press `-` | `5 + 5 + 5 -` | `5` | ` ` |
| Type `3` | `5 + 5 + 5 - 3` | `3` | `= 12` |
| Press `=` | `12` | `12` | ` ` |

### Operator Precedence
The calculator respects standard mathematical precedence:
- **High precedence**: `×` (multiplication), `÷` (division)
- **Low precedence**: `+` (addition), `−` (subtraction)

**Example: `5 + 3 × 2`**
- Expression: `5 + 3 × 2`
- Live Result: `= 11` (not 16!)
- Calculation: `5 + (3 × 2) = 5 + 6 = 11`

### When Typing Digits
1. Main expression updates to show digit being added
2. Display shows current number
3. Live result recalculates entire expression (if complete)

### When Pressing Operators
1. Current number is added to expression tokens
2. Operator is appended to expression
3. Expression updates: `5 + 5 +`
4. Live result clears (awaiting next operand)
5. If operator pressed twice, replaces last operator

### When Pressing Equals (`=`)
1. Full expression is evaluated
2. Formatted equation added to history: `5 + 5 + 5 - 3 = 12`
3. Expression shows only result: `12`
4. Display shows result: `12`
5. Live result clears
6. Next digit starts fresh expression

### When Pressing Clear (AC)
1. Display resets to `0`
2. Expression resets to `0`
3. Live result clears
4. Expression tokens cleared
5. **History preserved**

## Implementation Details

### History Persistence

Calculator history is automatically saved to DataStore and persists across app restarts:

**UserPreferencesRepository.kt**:
- `calculatorHistory: Flow<List<String>>` - Exposes persisted history
- `saveCalculatorHistory(List<String>)` - Saves history to DataStore
- `clearCalculatorHistory()` - Clears all persisted history

**CalculatorViewModel.kt**:
- Loads history from DataStore on initialization
- Automatically saves history after each `=` press
- Provides `onClearHistory()` action for clearing

**CalculatorScreen.kt**:
- History auto-scrolls to the right when new items are added
- "Clear History" menu option in app bar (three-dot menu)
- Confirmation dialog before clearing history
- History scroll state remembered with `rememberScrollState()`
- `LaunchedEffect(history.size)` triggers auto-scroll animation

### Data Model Changes

**InternalState** (`CalculatorEngine.kt`):
```kotlin
data class InternalState(
    val expressionTokens: List<String> = emptyList(),  // NEW: Token list
    val pendingInput: String = "",
    val justEvaluated: Boolean = false
)
```

The calculator now maintains a list of **tokens** (numbers and operators) rather than just left/right operands. This allows building expressions of arbitrary length.

### Engine Functions

**Key New Functions**:
- `buildExpressionString()` - Converts token list to displayable string
- `evaluateExpression()` - Evaluates full expression with live preview
- `calculateFromTokens()` - Performs two-pass evaluation (× ÷ first, then + −)
- `isOperator()` - Checks if token is an operator

**Modified Functions**:
- `onDigit()` - Appends to pending input, updates expression and live result
- `onOperator()` - Adds pending input and operator to token list
- `onEquals()` - Evaluates complete token list, adds to history
- `onBackspace()` - Removes last character or last token
- `onPercent()` - Simplified to just divide by 100
- `onSignFlip()` - Flips current number sign

### Evaluation Algorithm

The calculator uses a **two-pass evaluation** to respect operator precedence:

**Pass 1**: Process `×` and `÷` (left to right)
```
5 + 3 × 2  →  5 + 6
```

**Pass 2**: Process `+` and `−` (left to right)
```
5 + 6  →  11
```

## Test Coverage

Enhanced test suite with **15 total tests**:

### New Tests
1. `supports multi-operation expressions` - Full `5+5+5-3` workflow
2. `respects operator precedence` - Validates `5+3×2=11`

### Updated Tests
- All percentage tests simplified (context-aware % removed)
- Expression building tests updated for multi-operation support

### All Tests Passing ✅
- 15/15 tests passing
- Zero regressions
- Full coverage of new expression-building feature

## Examples

### Basic Multi-Operation
```
Input:  5 + 5 + 5
Live:   = 15
Result: 15
History: 5 + 5 + 5 = 15
```

### With Precedence
```
Input:  2 + 3 × 4
Live:   = 14  (not 20)
Result: 14
History: 2 + 3 × 4 = 14
```

### Mixed Operations
```
Input:  10 - 2 × 3 + 5
Live:   = 11  (10 - 6 + 5)
Result: 11
History: 10 - 2 × 3 + 5 = 11
```

### Long Expression
```
Input:  5 + 5 + 5 + 5 - 3
Live:   = 17
Result: 17
History: 5 + 5 + 5 + 5 - 3 = 17
```

## Benefits

1. **Natural Expression Building**: Type complete calculations like on paper
2. **No Intermediate Evaluations**: Build complex expressions without breaking flow
3. **Operator Precedence**: Correct mathematical evaluation
4. **Real-time Feedback**: See results update as you type
5. **Persistent History**: Review past calculations even after restarting the app
6. **Auto-Scrolling History**: Newest calculations automatically scroll into view
7. **Manageable History**: Clear history when needed via menu option
8. **Error Prevention**: Spot mistakes before committing with `=`

## Technical Notes

- Expressions stored as token lists (alternating numbers and operators)
- Live result only shows when expression is complete (no trailing operator)
- History preserved across clear operations
- Backspace removes characters from current number, then removes tokens
- Sign flip and percent only affect current pending number
- Maximum expression length limited only by display width
