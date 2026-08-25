# Product Requirements Document (PRD)
## Industrial Utility — Mobile App

**Version:** 1.0  
**Status:** Rebuild of abandoned app  
**Platforms:** Android (primary), iOS (future)  
**Last updated:** 2026-08-24 (SAE to Metric range + row dividers)

---

## 1. Overview

**Industrial Utility** is a reference and calculation utility for industrial trades, machining, maintenance, and field engineering. It provides quick offline access to common conversion tables, tap/drill charts, bearing data, component selection helpers, and calculations for wire, rope, and chain.

The original app appears abandoned. This PRD defines a clean rebuild with modern UX, offline-first behavior, and a shared core that can later ship on iOS.

### Goals
- Give tradespeople fast, offline lookup and simple calculations on the job site.
- Cover the feature set visible in the original app (and logical extensions).
- Ship a maintainable Android app first; keep architecture ready for iOS.
- Prefer accuracy, clarity, and speed over flashy UI.

### Non-goals (v1)
- Account systems, cloud sync, or social features.
- AR/measurement camera features.
- Full CAD or complex engineering simulation.
- In-app purchases or heavy monetization (optional later).

---

## 2. Target Users

| Persona | Needs |
|---------|--------|
| Machinist / CNC operator | SAE↔metric, taps & drills, tolerances |
| Maintenance technician | Bearings, chains, ropes, wire |
| Electrician / panel builder | Wire calculation, component selection |
| Field engineer / supervisor | Quick reference without internet |
| Apprentice / student | Clear tables and explanations |

**Context of use:** Workshop, plant floor, outdoor site — often gloves, bright/dark light, limited connectivity.

---

## 3. Core Features (from original app + necessary completeness)

### 3.1 Home / Main Menu
- Vertical list of modules with icon + title.
- Background imagery consistent with industrial theme (optional, subtle).
- Clear navigation into each tool.
- Optional: search/filter across modules (nice-to-have).

**Modules (v1):**
1. **SAE to Metric**
2. **Taps and Drills**
3. **Bearings**
4. **Components Selection**
5. **Wire Calculation**
6. **Ropes**
7. **Chains**

### 3.2 SAE to Metric
- Conversion table: SAE fraction → Decimal (inch) → Metric (mm).
- **Base range:** Always shows 1/64″ through 1″ (64 rows at 1/64″ steps), matching the original app. Common sizes emphasized (bold / subtle highlight).
- **Extended range (user-selectable):** Control to extend the table beyond 1″ up to a maximum of **10″**.
  - Options: 1″ (default) · 2″ · 3″ · 5″ · 10″
  - When extended, rows continue with mixed-number labels (e.g. 1-1/64, 1-1/8, 2, …).
  - Preference is persisted.
- **Row tracking:** Subtle horizontal divider under every row so the eye can follow SAE ↔ Decimal ↔ Metric without guessing.
- Sticky column header while scrolling.
- Step size: 1/64″ throughout.
- Values use exact conversion: **1 inch = 25.4 mm**.
- Display: decimal inches ~3–4 places; mm ~3 places (tabular figures).
- Optional (enhancement): search / jump to size; reverse lookup (mm or decimal → nearest SAE).

### 3.3 Taps and Drills
- Standard tap drill charts (UNC, UNF, metric coarse/fine).
- Show: thread designation, major diameter, recommended drill size (inch & mm), percentage of thread if useful.
- Filter by series (UNC / UNF / Metric).
- Offline data only.

### 3.4 Bearings
- Common bearing types and series (e.g. deep groove ball, tapered, needle — scope TBD by data availability).
- Lookup by designation (e.g. 6205) → dimensions (bore, OD, width), load ratings if available, seals.
- Simple search by number or dimensions.
- Data source: curated offline dataset (ISO / manufacturer standard sizes).

### 3.5 Components Selection
- Guided helpers for common industrial components (e.g. fasteners, fittings, basic hardware).
- Start with a focused subset: bolts/screws size selection, or similar high-value charts.
- Avoid open-ended “everything” catalog; keep it reference-oriented.

### 3.6 Wire Calculation
- Cross-section / AWG ↔ mm² conversion.
- Optional: simple current-carrying capacity estimates (with clear disclaimers and standards reference, e.g. NEC / IEC notes).
- Length / resistance calculators if data is reliable.
- All offline; show formulas and assumptions.

### 3.7 Ropes
- Reference data: common rope diameters, materials (wire rope, synthetic), approximate breaking strengths or working load limits where standard.
- Conversion and comparison helpers.
- Safety disclaimer required.

### 3.8 Chains
- Roller chain / industrial chain size charts (ANSI / ISO).
- Pitch, roller diameter, width, strength ratings where available.
- Lookup by designation or dimensions.

### 3.9 Cross-cutting requirements
- **Offline-first:** All core data and calculations work without network.
- **Accuracy:** Document data sources and rounding rules; prefer exact fractions where applicable.
- **Units:** Clear labeling (inch, mm, AWG, etc.). Toggle or dual display preferred.
- **Accessibility:** Readable fonts, sufficient contrast, large touch targets.
- **Performance:** Instant navigation and table scrolling on mid-range Android devices.
- **Safety & liability:** Disclaimers that the app is a reference tool; users must verify critical values against official standards and manufacturer data.

---

## 4. User Stories (selected)

1. As a machinist, I want to convert 5/16" to mm quickly so I can match a metric part.
2. As a machinist, I want to extend the SAE table past 1″ (up to 10″) when I need larger sizes, without leaving the screen.
3. As a user scanning a dense table, I want a line under each row so I can track across columns without losing my place.
4. As a maintenance tech, I want to look up the recommended drill for a 1/4-20 tap on site.
5. As an electrician, I want AWG ↔ mm² conversion without opening a browser.
6. As a field worker, I want the app to work with no signal.
7. As a supervisor, I want clear tables I can trust and show to apprentices.

---

## 5. Success Metrics (rebuild)

- App installs and retention (30-day).
- Crash-free sessions > 99.5%.
- Core screens load and scroll smoothly on target devices.
- User feedback on accuracy and usefulness (in-app or store reviews).
- Feature parity with original modules + measurable improvements (search, reverse convert, modern UI).

---

## 6. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Incomplete or inaccurate industrial data | Curate from standards (ISO, ANSI, ASME); document sources; allow community corrections in future |
| Scope creep on “Components” and calculators | Strict MVP scope; data-driven tables first |
| Legal/liability for load ratings | Strong disclaimers; no claims of engineering certification |
| Platform fragmentation (Android) | Target reasonable min SDK; test on mid-range devices |
| Future iOS parity | Shared business logic / data layer from day one |

---

## 7. Release Plan (high level)

**MVP (Android)**  
- Home menu  
- SAE to Metric (full table + polish)  
- Taps and Drills (UNC/UNF + common metric)  
- Basic Wire Calculation (AWG ↔ mm²)  
- Placeholders or minimal data for Bearings, Ropes, Chains, Components  
- Offline, disclaimers, dark/light support  

**v1.1+**  
- Complete Bearings, Ropes, Chains datasets  
- Components Selection helpers  
- Search across modules  
- Favorites / recent  
- iOS shared core + native UI  

---

## 8. Open Questions

1. Exact data sources and licensing for bearing, chain, rope, and tap charts.
2. Depth of “Components Selection” — fasteners only or broader?
3. Whether wire ampacity tables are in scope (and which standard).
4. Monetization (none / ads / paid / pro unlock) — deferred.
5. Branding, app name, and store listing assets for the rebuild.

---

*This PRD is the source of truth for the product scope of the Industrial Utility rebuild.*
