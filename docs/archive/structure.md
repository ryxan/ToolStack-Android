# App Structure
## Industrial Utility — Mobile App

**Version:** 1.0  
**Focus:** Android first, shared core for future iOS  
**Last updated:** 2026-08-24 (SAE to Metric range + row dividers)

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Presentation                         │
│  (Compose UI / future SwiftUI)                          │
│  Screens • ViewModels • Navigation                      │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                   Domain / Use Cases                     │
│  Convert SAE↔Metric • Lookup tap drill • Search bearing │
│  Wire calc • Rope/Chain reference                       │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                      Data Layer                          │
│  Local repositories • Offline datasets (JSON/DB)        │
│  Calculators (pure functions)                           │
└─────────────────────────────────────────────────────────┘
```

- **Offline-first:** All v1 data lives on device.
- **Unidirectional data flow:** UI → ViewModel → Use case / Repository → data.
- **Shared kernel:** Domain models, pure calculation functions, and static datasets can be extracted to a multiplatform module (Kotlin Multiplatform) when iOS work begins.

---

## 2. Module / Package Layout (Android)

Suggested Gradle modules:

```
app/                          # Application entry, DI, navigation host
core/
  designsystem/               # Theme, colors, typography, common composables
  common/                     # Utilities, Result types, dispatchers
feature/
  home/
  sae_metric/
  taps_drills/
  bearings/
  components/
  wire/
  ropes/
  chains/
data/
  local/                      # Assets, Room (if used), file loaders
domain/                       # Models + use cases (or keep inside features for MVP)
```

For a smaller MVP, a single-module app with clear packages is acceptable:

```
com.toolstack.io/
  ui/
    theme/
    components/          # Reusable table, search bar, disclaimer
    home/
    saemetric/
    tapsdrills/
    bearings/
    componentsselection/
    wire/
    ropes/
    chains/
  domain/
    model/
    usecase/
    calculator/
  data/
    repository/
    source/              # JSON assets, embedded tables
  di/
  MainActivity.kt
  IndustrialUtilityApp.kt
```

---

## 3. Navigation Structure

```
Home
├── SAE to Metric
├── Taps and Drills
│     └── (optional detail / notes)
├── Bearings
│     └── Bearing Detail
├── Components Selection
│     └── Category → Result
├── Wire Calculation
├── Ropes
│     └── (optional detail)
└── Chains
      └── (optional detail)

Global (from overflow / settings):
├── About
├── Disclaimer / Legal
└── Settings (theme, units preference)
```

- Single-activity architecture with Navigation Compose (Android).
- Deep links optional later (e.g. `industrialutility://sae/0.312`).

---

## 4. Screen Inventory

| Screen | Route / ID | Primary content | Notes |
|--------|------------|-----------------|-------|
| Home | `home` | Module list | Entry point |
| SAE to Metric | `sae_metric` | Conversion table + range selector | Sticky header, row dividers, 1–10″ range |
| Taps and Drills | `taps_drills` | Filterable chart | Series tabs/chips |
| Bearings | `bearings` | Search + results | Detail on select |
| Bearing Detail | `bearings/{id}` | Dimensions & ratings | |
| Components | `components` | Category / wizard | Scope limited in MVP |
| Wire Calculation | `wire` | Inputs + results | Live conversion |
| Ropes | `ropes` | Reference table / search | Disclaimer |
| Chains | `chains` | Reference table / search | Disclaimer |
| About | `about` | Version, credits | |
| Disclaimer | `disclaimer` | Legal text | Accessible from all tools |

---

## 5. Data Model (conceptual)

```
SaeMetricEntry
  - fractionLabel: String          // "5/16", "1-3/8", ...
  - decimalInch: Double            // 0.3125
  - metricMm: Double               // 7.9375
  - isCommon: Boolean
  - stepIndex: Int                 // n in n/64 (optional, for generation)

// Range preference (persisted)
SaeMetricRange
  - maxInches: Float               // 1f | 2f | 3f | 5f | 10f (default 1f)

TapDrillEntry
  - designation: String            // "1/4-20 UNC"
  - series: Enum (UNC, UNF, Metric, ...)
  - majorDiameterIn: Double
  - majorDiameterMm: Double
  - recommendedDrillIn: String / Double
  - recommendedDrillMm: Double
  - notes: String?

Bearing
  - designation: String            // "6205"
  - type: String
  - boreMm: Double
  - odMm: Double
  - widthMm: Double
  - dynamicLoad?: Double
  - staticLoad?: Double
  - ...

WireSize
  - awg: Int?
  - mm2: Double
  - diameterMm?: Double
  - ...

Rope / Chain entries similarly: designation, dimensions, strength ratings (with source notes).
```

Calculators (wire resistance, etc.) should be pure functions taking typed inputs and returning typed results + any assumption flags.

---

## 6. Data Sources & Storage

| Data | Format (recommended) | Location |
|------|----------------------|----------|
| SAE ↔ Metric | Generated at runtime (1/64″ steps) from pure functions; no large static file required | domain/calculator |
| Tap & drill charts | JSON or CSV → parsed at build or runtime | assets |
| Bearings | JSON or Room pre-populated DB | assets / Room |
| Wire AWG table | Constants or JSON | assets |
| Ropes / Chains | JSON | assets |

- Prefer **immutable offline assets** for v1.
- Room is optional: useful if search/filter becomes complex or datasets grow large.
- Version datasets; show data version in About.

---

## 7. Key Components (UI)

- `IndustrialTopBar`
- `ModuleListItem` (icon + title)
- `StickyHeaderTable` / `DataTable` (with horizontal row dividers)
- `SaeRangeSelector` (chips or dropdown: 1″ / 2″ / 3″ / 5″ / 10″)
- `SearchField`
- `FilterChipRow`
- `DisclaimerBanner` or footer
- `EmptyState`
- `NumericResultDisplay`

Shared across features to keep visual consistency.

---

## 8. State Management

- **ViewModel per screen** (or per feature) holding UI state.
- UI state as immutable data class / sealed interface (loading, content, empty, error).
- User events → ViewModel → update state.
- No heavy business logic in Composables.

Example (SAE):

```
data class SaeMetricUiState(
  val maxInches: Float = 1f,                    // 1, 2, 3, 5, or 10
  val entries: List<SaeMetricEntry> = emptyList(), // generated for current maxInches
  val query: String = "",
  val filtered: List<SaeMetricEntry> = entries
)
```

Generation (domain):
- For `n` in `1 .. (maxInches * 64)` produce an entry with simplified/mixed fraction label, `n/64.0` decimal, and `decimal * 25.4` mm.
- `isCommon` marks standard shop sizes for emphasis.

---

## 9. Cross-Cutting Concerns

- **Theming:** Single `Theme` with light/dark.
- **Units preference:** Optional global “prefer mm / prefer inch” that secondary displays respect.
- **Analytics (optional):** Screen views only; no PII.
- **Crash reporting:** Standard (Firebase Crashlytics or similar) once ready for release.
- **Localization:** English first; structure strings for future translation (industrial terms may stay English).

---

## 10. Future iOS Alignment

- Extract pure Kotlin (or shared) models + calculators + JSON datasets into a KMP module.
- Keep UI fully native (Jetpack Compose ↔ SwiftUI).
- Mirror navigation graph and feature boundaries so product behavior stays parallel.
- Same offline datasets shipped on both platforms.

---

## 11. Testing Structure

```
test/
  domain/          # Pure calculator & conversion tests (critical)
  data/            # Parsing / repository tests
androidTest/
  ui/              # Critical flows: open SAE table, convert, open taps
```

Priority: mathematical correctness of conversions and chart lookups.

---

## 12. Build & Delivery

- Product flavors optional (e.g. `demo` with limited data).
- Min SDK chosen for broad device coverage while allowing modern Compose.
- App Bundle for Play Store.
- Clear versioning: `versionName` visible in About; data version separate if needed.

---

*Structure prioritizes clear feature boundaries, offline data, and a path to shared logic for iOS without over-engineering the Android MVP.*
