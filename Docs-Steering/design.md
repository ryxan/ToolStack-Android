# Design Document
## Industrial Utility — Mobile App

**Version:** 1.0  
**Platforms:** Android (Material Design 3), iOS (future — Human Interface Guidelines)  
**Last updated:** 2026-08-24 (SAE to Metric range + row dividers)

---

## 1. Design Principles

1. **Job-site first** — Large touch targets, high contrast, readable in bright light or dim shops.
2. **Glanceable data** — Tables and numbers dominate; decoration is secondary.
3. **Offline confidence** — UI never implies network dependency for core tools.
4. **Industrial clarity** — Functional, trustworthy, not playful or consumer-gimmicky.
5. **Consistent navigation** — Same patterns across modules so users learn once.

---

## 2. Visual Language

### 2.1 Color
Inspired by the original app’s blue header and industrial imagery, modernized for Material 3 / future iOS.

| Role | Light | Dark | Notes |
|------|-------|------|-------|
| Primary | Deep industrial blue `#1565C0` / `#1976D2` | Lighter blue for emphasis | App bars, key actions |
| On-primary | White | White | |
| Surface | `#FAFAFA` / white | `#121212` / `#1E1E1E` | Cards, tables |
| On-surface | Near-black | Near-white | Body text |
| Outline / dividers | Subtle gray | Subtle light gray | Table rows |
| Emphasis (common sizes) | Slightly stronger weight + optional soft highlight | Same | e.g. 1/4, 1/2, 3/4 in SAE table |
| Error / warning | Standard Material red / amber | Same | Disclaimers, invalid input |

Accent icons can use muted industrial tones (steel gray, safety yellow sparingly).

### 2.2 Typography
- **Titles / app bar:** Medium–Bold, clear hierarchy.
- **Table headers:** Semi-bold, slightly smaller.
- **Body / data cells:** Monospace or highly legible sans for numbers (e.g. Roboto Mono or system tabular figures) so columns align.
- **Minimum readable size:** Prefer 14–16 sp body on phone; avoid tiny legal text without expand.

### 2.3 Iconography
- Simple, filled or outlined industrial glyphs matching the original menu:
  - Calipers / measurement → SAE to Metric  
  - Tap / drill bit → Taps and Drills  
  - Bearing cross-section → Bearings  
  - Circuit / component block → Components Selection  
  - Wire / spool or gear → Wire Calculation  
  - Rope / coil → Ropes  
  - Chain links → Chains  
- Icons should remain recognizable at small sizes and in monochrome.

### 2.4 Imagery
- Home background: optional subtle industrial photo (hard hat, denim, tools) with strong scrim so text stays readable — matching original spirit without competing with the menu.
- Prefer system/list-first if performance or licensing is an issue; background is polish, not required for MVP.

---

## 3. Layout & Screens

### 3.1 Home (Main Menu)
- **Top app bar:** Title “Industrial Utility”, optional overflow (About, Settings, Disclaimer).
- **Body:** Vertical list (or simple grid on tablet) of modules.
  - Leading icon + title text.
  - Full-width rows, generous height (≥ 56–72 dp).
  - Optional divider lines.
- **Background:** Optional themed image with overlay, or clean surface.
- **Behavior:** Tap opens the corresponding tool screen.

### 3.2 Generic Tool Screen Pattern
Most modules follow the same shell:

```
┌─────────────────────────────┐
│ ←  Module Title        ⋮    │  App bar
├─────────────────────────────┤
│ [Search / Filter] (optional)│
├─────────────────────────────┤
│                             │
│   Content (table / form)    │
│                             │
│                             │
└─────────────────────────────┘
```

- Back navigation always available.
- Optional search/filter bar for large tables.
- Sticky table header when scrolling long lists.
- Bottom safe area respected; no critical controls under system nav.

### 3.3 SAE to Metric (reference implementation)

**Layout**
```
┌──────────────────────────────────────────┐
│ ←  SAE to Metric                    ⋮    │
├──────────────────────────────────────────┤
│ Up to:  [1″]  [2″]  [3″]  [5″]  [10″]    │  ← range selector (default 1″)
├──────────────────────────────────────────┤
│ SAE           Decimal       Metric (mm)  │  ← sticky header
├──────────────────────────────────────────┤
│ 1/64          0.016         0.397        │
├──────────────────────────────────────────┤  ← row divider on every row
│ 1/32          0.031         0.794        │
├──────────────────────────────────────────┤
│ …                                        │
├──────────────────────────────────────────┤
│ 1             1.000        25.400        │
├──────────────────────────────────────────┤
│ 1-1/64        1.016        25.797        │  ← only when range > 1″
│ …                                        │
└──────────────────────────────────────────┘
```

**Behavior & visuals**
- Three-column table: **SAE** | **Decimal** | **Metric (mm)**.
- Sticky header while the body scrolls.
- **Horizontal row dividers** (subtle outline color) under every data row so users can track across columns reliably.
- **Range selector** directly under the app bar / above the sticky header:
  - Chips or compact dropdown: 1″ (default) · 2″ · 3″ · 5″ · 10″.
  - Base table is always 0–1″; choosing a larger value appends rows beyond 1″.
  - Selection is persisted.
- Common fractional sizes (1/16, 1/8, 1/4, 5/16, 3/8, 1/2, 5/8, 3/4, 7/8, 1, and major marks above 1″) visually emphasized (bold and/or soft highlight).
- Labels: proper simplified fractions below 1″; mixed numbers above 1″ (e.g. `1-3/8`, `2-1/16`).
- Tabular/monospace figures for clean column alignment.
- Units clearly labeled in the header.
- Optional later: search/jump, reverse convert (mm → nearest SAE).

### 3.4 Taps and Drills
- Filter chips or tabs: UNC | UNF | Metric (or similar).
- Table columns e.g.: Thread | Major Ø | Tap drill (in) | Tap drill (mm) | Notes.
- Sticky headers; horizontal scroll only if absolutely necessary (prefer wrapping or priority columns).

### 3.5 Bearings / Chains / Ropes
- Search field (designation or key dimension).
- Result list or detail card: key dimensions + ratings.
- Empty state: “Enter a bearing number (e.g. 6205)” with example.

### 3.6 Wire Calculation
- Input fields: AWG or mm² (mutual conversion).
- Optional secondary calculators (length, approx. resistance) with units and formula notes.
- Results in large, clear type.
- Disclaimer text near ampacity or load-related outputs.

### 3.7 Components Selection
- Step-by-step or category → size → result flow.
- Keep depth shallow for v1 (e.g. bolt diameter / length / grade lookup tables).

---

## 4. Interaction Patterns

| Pattern | Usage |
|---------|--------|
| List navigation | Home → module |
| Scrollable table | SAE, taps, chains, etc. |
| Search / filter | Large datasets (bearings, full charts) |
| Chips / tabs | Series selection (UNC/UNF/Metric) |
| Form + live result | Wire and other calculators |
| Dialog / bottom sheet | Disclaimers, About, detailed notes |
| Snackbar | Copy-to-clipboard confirmation (optional) |

**Touch:** Minimum 48×48 dp targets. Prefer full-row taps on lists.

**Haptics:** Light feedback on selection (optional, platform default).

**Clipboard:** Long-press or action to copy a row’s values (nice-to-have).

---

## 5. Accessibility

- Support system font scaling.
- Sufficient color contrast (WCAG AA minimum).
- Content descriptions for icons.
- TalkBack / screen-reader friendly table structure (or list alternative if needed).
- No information conveyed by color alone (bold + position for “common” sizes).

---

## 6. Dark Mode & Theming

- Full dark theme from day one (Material 3 dynamic color optional).
- Tables remain high-contrast in both modes.
- Background imagery (if used) adapts or is disabled in dark mode for clarity.

---

## 7. Responsive / Form Factors

- **Phone (primary):** Single-column lists and tables.
- **Tablet / foldable:** Optional two-pane (menu + content) or wider tables; not required for MVP.
- Orientation: Portrait preferred; landscape supported for tables (more columns visible).

---

## 8. Motion

- Subtle, short transitions (Material motion or platform defaults).
- No long decorative animations that slow task completion.
- Shared-element or simple fade/slide between home and tools.

---

## 9. Empty, Loading, Error States

- **Empty search:** Helpful prompt + example query.
- **No network:** Never shown as an error for core offline data.
- **Data missing:** “Data not available for this size” rather than crash.
- **About / Disclaimer:** Always reachable; clear legal language that values must be verified against official sources.

---

## 10. Branding & Store Presence (guidance)

- App icon: Simple industrial symbol (calipers, gear, or hard-hat motif) on solid or gradient blue.
- Feature graphic / screenshots: Show real tables and the home menu; highlight offline use.
- Tone of voice: Direct, professional, no marketing fluff in the UI itself.

---

## 11. Design Handoff Notes

- Use Material Design 3 components on Android (TopAppBar, LazyColumn, FilterChip, OutlinedTextField, etc.).
- Maintain a shared design token set (colors, type scale, spacing) so iOS can map to SwiftUI equivalents later.
- Priority screens for first visual polish: Home, SAE to Metric, Taps and Drills, Wire Calculation.

---

*Design should feel like a reliable tool in a toolbox — not a consumer lifestyle app.*
