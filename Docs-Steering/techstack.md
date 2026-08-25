# Tech Stack
## Industrial Utility — Mobile App

**Version:** 1.0  
**Primary platform:** Android  
**Future:** iOS (shared core)  
**Last updated:** 2026-08-24

---

## 1. Recommended Stack (Android MVP)

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | **Kotlin** | Standard for modern Android; null safety; coroutines |
| UI | **Jetpack Compose** + Material 3 | Declarative, fast iteration, excellent lists/tables |
| Architecture | **MVVM** + unidirectional data flow | Clear separation; testable ViewModels |
| Navigation | **Navigation Compose** | Type-safe routes, single-activity |
| Async | **Kotlin Coroutines** + Flow | Lightweight; natural with Compose |
| DI | **Hilt** (or manual / Koin for smaller scope) | Scalable; official recommendation |
| Local data | **JSON assets** (+ optional **Room**) | Simple offline tables; Room if search/query grows |
| Serialization | **Kotlinx Serialization** | Multiplatform-friendly; works with KMP later |
| Images | Coil (if any remote/assets beyond vectors) | Efficient; optional for v1 |
| Min SDK | **26** (or 24 if broader reach needed) | Balance modern APIs vs device coverage |
| Target / Compile SDK | Latest stable | Play requirements + Material 3 |

---

## 2. Core Libraries (suggested)

```
// UI & platform
androidx.compose.ui / material3 / material-icons
androidx.navigation:navigation-compose
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.activity:activity-compose

// DI
dagger.hilt.android / hilt-navigation-compose

// Data
org.jetbrains.kotlinx:kotlinx-serialization-json
androidx.room (optional)
androidx.datastore (preferences: theme, units)

// Testing
junit, androidx.test, compose.ui.test, turbine (Flow), mockk or similar
```

Avoid unnecessary SDKs in MVP (no maps, no heavy analytics, no social).

---

## 3. Data & Calculation Strategy

- **Static reference data** (SAE table, tap charts, AWG, chain/rope basics):  
  - Ship as `assets/*.json` or generate Kotlin constants from CSV at build time.  
  - Parse once (or lazy) into memory; expose via Repository.

- **Calculations** (decimal ↔ mm, wire cross-section, simple resistance):  
  - Pure Kotlin functions in `domain` / `calculator` package.  
  - Unit-tested heavily.  
  - Use `Double` carefully; document rounding (e.g. display 3 decimal places for mm where appropriate).  
  - Prefer exact fraction representation for SAE labels (`1/64`, `5/16`) rather than only floats.

- **1 inch = 25.4 mm** exactly (SI definition). All metric values derived from that.

---

## 4. Project Tooling

| Tool | Purpose |
|------|---------|
| Android Studio (latest stable) | IDE |
| Gradle (Kotlin DSL) | Build |
| Version Catalog (libs.versions.toml) | Dependency management |
| ktlint / detekt (optional) | Style & static analysis |
| Git | Source control |

CI suggestion: GitHub Actions or similar — assemble, unit tests, lint on PR.

---

## 5. Architecture Pattern Detail

```
Screen (Composable)
    ↓ events
ViewModel
    ↓ calls
UseCase / Repository
    ↓
Data source (assets / Room / pure calc)
    ↑ Flow / StateFlow
ViewModel exposes UiState
    ↑
Composable collects and renders
```

- No business logic in Composables.
- Repositories are the only place that knows about JSON/Room.
- Domain models are plain data classes independent of Android framework.

---

## 6. Offline & Performance

- Zero network required for core features.
- Tables: `LazyColumn` with sticky headers; key items properly.
- Large lists: consider in-memory filter rather than re-querying DB every keystroke.
- App size: keep datasets lean; compress JSON if needed; avoid large unused assets.

---

## 7. Future iOS Path (Kotlin Multiplatform)

When ready for iOS:

| Shared | Platform-specific |
|--------|-------------------|
| Domain models | UI (SwiftUI) |
| Pure calculators | Navigation & platform widgets |
| JSON datasets + parsing | App icons, store assets |
| Business rules / validation | Push / platform services |

**Suggested evolution:**
1. Move `domain` + `data` (non-Android) into a KMP module.
2. Android app depends on shared module; iOS consumes via XCFramework or SPM integration.
3. Keep feature list and data identical so product behavior matches.

Alternative (if team prefers): separate native iOS rewrite that reuses the same JSON specs and calculation formulas documented in this repo — still viable if KMP is deferred.

---

## 8. Security & Privacy

- No accounts, no personal data collection in MVP.
- No sensitive permissions required (no location, camera, contacts, etc.).
- Optional: internet permission only if crash reporting or future remote data is added (declare clearly).
- Disclaimers in-app for engineering/safety-critical use of numbers.

---

## 9. Testing Priorities

1. **Conversion accuracy** — SAE fractions ↔ mm (golden values from standards).
2. **Tap drill lookups** — known designations return correct drills.
3. **Wire AWG ↔ mm²** — standard table values.
4. **UI smoke** — Home → each module opens; tables scroll; search filters.
5. **Edge cases** — empty search, unknown bearing number, extreme inputs.

---

## 10. What Not to Use (for this app)

- Backend / custom server for v1
- Firebase Auth / Firestore as core dependency
- Heavy ML / on-device models
- WebView wrappers for the main experience
- Cross-platform UI frameworks (Flutter/React Native) if the goal is native Android quality + later native iOS — Compose + eventual KMP/SwiftUI is the intended path

(If the team strongly prefers a single codebase UI, Flutter is a reasonable alternative stack; document that decision separately and adapt design/structure docs accordingly.)

---

## 11. Summary Recommendation

**Ship Android with:**
- Kotlin + Jetpack Compose + Material 3  
- MVVM + Navigation Compose + Coroutines/Flow  
- Hilt (or lightweight DI)  
- Offline JSON (and optional Room)  
- Pure, well-tested calculation and conversion logic  

**Prepare for iOS by:**
- Keeping domain and data clean and framework-agnostic  
- Preferring Kotlin Multiplatform-friendly libraries (Serialization, etc.)  
- Documenting all formulas and data sources  

This stack is modern, maintainable, and aligned with Google’s current Android direction while leaving a clear door to iOS.

---

*Tech choices should favor reliability and offline speed over novelty.*
