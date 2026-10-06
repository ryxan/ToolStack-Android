# Recipe Scaler — OCR Accuracy Plan

How to improve photo-import recognition, especially for handwritten recipe cards.

## Context

The on-device pipeline is `TakePicture`/`PickVisualMedia` → `loadScanImage` (2048px downscale)
→ ML Kit `text-recognition:16.0.1` → `RecipeOcrParser`. Printed recipes parse well. Handwritten
cards do not, and the root cause is upstream of the parser: ML Kit's Latin recognizer is trained
on printed text and has no handwriting model.

Real scan of the "¾ C. butter" index card (logcat tag `RecipeScaler`):

```
Jy buter, meltd (2 sthcks)        ← 3/4 C. butter, melted (1½ sticks)
Vaci                              ← 1/3 C. brown sugar  (name lost entirely)
Lt Vanilla extract                ← 1 t. vanilla extract
|C whole wheat flocor             ← 1 C. whole wheat flour
1t bakina sOda                    ← 1 t. baking soda
4 YaColled ts                     ← 4 1/2 C. rolled oats
lc chocolat chps Cor any exas     ← 1 C. chocolate chips (or any extras)
```

(The "½ C. honey" line was never emitted by ML Kit at all.)

Parser-side rules now recover qty + unit for all 7 emitted lines, but the ingredient names are
garbage-in and no regex fixes that. **Further mis-read regexes are explicitly out of scope** —
each one overfits a single card and risks false positives elsewhere.

Suggested order: **2+3 via ML Kit Document Scanner** (one dependency, covers crop/deskew/capture
UI), then **4**, then decide on **1**. **5** is independent and can slot in anywhere.

---

## 1. Cloud fallback for handwriting (biggest lever)

ML Kit stays the free, instant, offline default. Add an opt-in "Improve scan" path that sends
the image to a cloud model when the on-device result is weak.

| Option | Handwriting | Cost | Notes |
|---|---|---|---|
| Google Cloud Vision `DOCUMENT_TEXT_DETECTION` | Good | ~$1.50 / 1k images | Returns text; still needs our parser |
| Gemini (multimodal) | Excellent | Per-token | Can return structured `{qty, unit, name}` JSON directly, understands "t = tsp" from context, corrects "buter" → "butter" |
| ML Kit Digital Ink | — | Free | Stylus strokes only, not photos — **not applicable** |

- [ ] Decide: Gemini vs Cloud Vision. Gemini is strongly preferred — it collapses OCR + parsing + spell-correction into one call and returns rows we can load straight into `IngredientItem`.
- [ ] Backend proxy to hold the API key (never ship it in the APK). `BillingNetworkModule` is the precedent for a networked module.
- [ ] Define the response schema: `[{ "qty": "3/4", "unit": "cup", "name": "butter", "note": "melted (1 1/2 sticks)" }]`. Map `unit` through `RecipeScalerCalculator.canonicalUnit`.
- [ ] Trigger: manual button on the scan result, plus automatic when item 4's confidence signal is below threshold.
- [ ] UI: clear "uses network / sends photo" disclosure; respect offline state; show the same replace/append/cancel dialog as today.
- [ ] Privacy: strip EXIF/GPS before upload; don't retain images server-side.
- [ ] Cost guard: rate-limit per user; consider tying to the existing billing tier.

Where: `RecipeScalerViewModel.onImageCaptured`, new `data/remote` module, `RecipeScalerScreen.kt` scan result dialog.

---

## 2+3. Capture + preprocessing — use ML Kit Document Scanner (recommended route)

Photo quality is the single largest variable and we currently have zero control over it —
`TakePicture` hands off to the system camera, and the bitmap goes straight from `ImageDecoder`
to `InputImage.fromBitmap`. The ML Kit guide's own input guidance (≥16px per character, good
focus, recapture if poor) is exactly what the sample scan violates.

Rather than hand-rolling a CameraX overlay plus our own crop/deskew, the **ML Kit Document
Scanner** API ships all of it as a Play-Services-provided flow:

| | Details |
|---|---|
| Dependency | `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0` |
| Size | ~300 KB (models + UI downloaded by Play Services on first use) |
| Provides | Viewfinder with live edge detection, auto-capture, perspective correction, crop handles, shadow/glare cleanup, optional gallery import |
| Output | JPEG `Uri`(s) via `GmsDocumentScanningResult.getPages()` — drops straight into the existing `onImageCaptured(uri)` path |
| Permissions | None — the scanner activity owns the camera, so the current "no CAMERA permission" design is preserved |
| Requirements | API 21+ (we're minSdk 26) and ≥1.7 GB device RAM; otherwise `getStartScanIntent` fails with `MlKitException` code `UNSUPPORTED` |

### Document Scanner integration
- [x] Add the dependency to `libs.versions.toml` + `app/build.gradle.kts`.
- [x] Options: `GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(1).setResultFormats(RESULT_FORMAT_JPEG).setScannerMode(SCANNER_MODE_FULL)`. `SCANNER_MODE_FULL` enables the cleanup filters; evaluate `SCANNER_MODE_BASE_WITH_FILTER` if the full flow feels heavy for a one-card scan.
- [x] Launch via `rememberLauncherForActivityResult(StartIntentSenderForResult())` in `RecipeScalerScreen`, replacing the current `TakePicture` + `PickVisualMedia` pair (gallery import is built into the scanner).
- [x] On result: `GmsDocumentScanningResult.fromActivityResultIntent(data).pages?.firstOrNull()?.imageUri` → `viewModel.onImageCaptured(uri)`. No ViewModel changes needed for the happy path.
- [x] Fallback: if `getStartScanIntent` fails (`UNSUPPORTED` / Play Services missing), fall back to the existing `TakePicture` flow rather than erroring. Keep `createCameraImageUri` for this.
- [x] Cleanup: the scanner writes its own output file; delete it in the existing `finally` alongside `deletePendingScanFile`.
- [x] First-launch latency: the flow is downloaded on first use. Reuse `isScanProcessing` to show a spinner while `getStartScanIntent` is pending.

Where: `RecipeScalerScreen.kt` ~L178–L235 (launchers), ~L600 (camera button), `RecipeScalerViewModel.createCameraImageUri` / `deletePendingScanFile`.

### Residual preprocessing (only if still needed after the scanner is in)
- [ ] **Raise `MAX_SCAN_EDGE_PX`** — 2048 is fine for print; thin pen strokes lose detail. The scanner output is already cropped to the card so the same pixel budget now covers far more text, but try 3072 if characters still fall under ~16px (already using `ALLOCATOR_SOFTWARE`; check memory on low-end devices).
- [ ] **Grayscale + contrast stretch** — `ColorMatrix` saturation 0 plus a contrast boost. The scanner's own filters likely make this redundant; measure before adding.
- [ ] Measure: re-scan the sample card before/after and diff the raw `RecipeScaler` logcat text. Don't keep a step that doesn't change the output.

Where: `RecipeScalerViewModel.loadScanImage` (~L564). Keep the processed bitmap recycled in the existing `finally`.

### Not needed once the scanner is in
- Manual crop UI, auto-crop, deskew — all provided by the scanner flow.
- CameraX dependency + CAMERA runtime permission.
- A text hint on the camera button is still cheap and worth keeping ("Lay the card flat, avoid shadows").

---

## 4. Use ML Kit confidence signals (small, targeted)

`Text.Element.confidence` is available and currently discarded.

- [x] Carry per-line confidence through `OcrLine` (add `confidence: Float?`) and onto the parsed `IngredientItem` (or a parallel list — avoid persisting it in `SavedRecipeIngredient`).
- [x] UI: subtle amber outline / icon on low-confidence rows so the user checks "Bakina sOda" without hunting. Clear the flag once the row is edited.
- [ ] Use the average confidence as the automatic trigger for item 1's cloud fallback.
- [ ] Pick the threshold empirically from a handful of real scans (printed vs handwritten).

Where: `RecipeScalerViewModel.parseVisionText` (~L527), `OcrLine`, ingredient row composable.

---

## 5. Post-OCR ingredient name correction (small, bounded)

A tiny dictionary (~300 common ingredient words) with edit-distance ≤ 2 would fix the most
common garbling: `buter` → `butter`, `flocor` → `flour`, `chocolat chps` → `chocolate chips`.

- [x] Word list as a Kotlin `Set` in `domain/calculator` (no asset loading; keeps it unit-testable).
- [x] Correct per word, only when a unique match within distance 2 exists; never touch words ≤ 3 chars or words already in the dictionary.
- [x] Apply after `parseLine` name extraction, before `replaceFirstChar`.
- [x] Won't help `Vaci` — that's expected; item 1 covers it. (`Colled` turned out to be fixable: distance 1 from `rolled`, so it corrects to `Rolled`.)

Where: `RecipeOcrParser.parseLine` step 3 (name).

---

## Not doing

- **More per-card mis-read regexes** in `OCR_FRACTION_FIXES`. Today's `Jy` / `Vaci` / `Ya(?=C)` / `Lt` rules were justified because they recover real quantities, but that's the stopping point.
- **Changes to `ALL_UNITS` / `UNIT_SYNONYMS` / `t`-`T`-`c` handling** — that layer is correct; the problem is upstream.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests "com.toolstack.io.domain.calculator.RecipeOcrParserTest"` for anything touching the parser.
- For items 1–3, the real test is re-scanning the sample card on a debug build and comparing the `RecipeScaler` logcat output and the resulting rows against the table above.
