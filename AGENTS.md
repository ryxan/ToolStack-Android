# Agent Notes

## Verification

- Run unit tests: `.\gradlew.bat :app:test`
- Current test baseline: **395 tests, 0 failures, 0 skipped** across 17 test files
- To diagnose scan mis-parses, check logcat tag `RecipeScaler` — debug builds log the raw ML Kit text before parsing.
- The project uses Hilt for dependency injection and Jetpack Compose with Material3.
- SAE/Metric common-size highlighting is controlled by `SaeMetricCalculator.COMMON_BELOW_ONE` and `COMMON_ABOVE_ONE`.
