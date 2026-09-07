# Billing Integration — Status & Remaining Work

## What was built

The full billing data layer and UI wiring is in place. The app compiles and
runs. No Play Console products exist yet, so the purchase flow is functional
code waiting on external setup.

### Files added

| File | Purpose |
|---|---|
| `app/src/main/java/com/toolstack/io/data/billing/BillingModels.kt` | `ProductIds` constants, `BillingProduct`, `BillingResult<T>`, `PurchaseState` sealed classes |
| `app/src/main/java/com/toolstack/io/data/billing/BillingRepository.kt` | `BillingClient` lifecycle, `queryActivePurchases`, `queryProductDetails`, `launchPurchaseFlow`, server-side verification with fail-open network fallback |
| `app/src/main/java/com/toolstack/io/data/api/BillingVerificationApi.kt` | Retrofit interface `POST verify-purchase` + `VerifyPurchaseRequest` / `VerifyPurchaseResponse` DTOs |
| `app/src/main/java/com/toolstack/io/di/BillingNetworkModule.kt` | `@BillingRetrofit` qualifier, OkHttp + Retrofit + `BillingVerificationApi` Hilt providers |
| `app/src/main/java/com/toolstack/io/ui/home/HomeViewModel.kt` | `@HiltViewModel`, observes `purchaseState` → `isPremium`, `startPurchaseFlow(activity, product)` |

### Files modified

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Added `billing-ktx 8.0.0`, `retrofit 2.11.0`, `okhttp 4.12.0`, `gson 2.11.0`, `lifecycle-process 2.8.7` |
| `app/build.gradle.kts` | Added `implementation` for all 6 new deps |
| `app/src/main/java/com/toolstack/io/ui/home/HomeScreen.kt` | Injects `HomeViewModel`; `isPremium` is now live from billing state (not hardcoded); upgrade dialog shows real Play Store prices; lock badge disappears once user is entitled |
| `app/src/main/res/values/strings.xml` | Added `premium_product_subscription`, `premium_product_lifetime`, `purchase_error_title`, `purchase_error_dismiss` |
| `app/src/main/AndroidManifest.xml` | Added `INTERNET` permission |

### Current backend config

`BillingNetworkModule.BILLING_VERIFICATION_URL` points at `https://api.riverwatch.cc/`
(shared with RiverWatch while ToolStack is in development). The RiverWatch backend
receives `packageName` and `productId` in every request so it can distinguish apps.

---

## What still needs to be done before shipping

### 1. Play Console — create products

In the ToolStack Play Console entry, create these two products:

| Type | Product ID | Notes |
|---|---|---|
| Subscription | `toolstack_pro_monthly` | Monthly recurring |
| One-time | `toolstack_pro_lifetime` | Non-consumable |

These IDs are hardcoded in `BillingModels.ProductIds`. If you use different IDs
in the Play Console, update the constants there.

### 2. Backend — set up a dedicated ToolStack instance

When ready to ship:

1. Register a domain (e.g. `api.toolstack.io`).
2. Deploy a backend — same code as RiverWatch's verify endpoint, different env config.
3. Grant the service account access to `com.toolstack.io` in Play Console
   (Play Console → Setup → API access).
4. Update `BILLING_VERIFICATION_URL` in `BillingNetworkModule.kt` to the new URL.

Until then, the shared RiverWatch backend works **but** requires one change on
the RiverWatch backend side before any real ToolStack purchases go live:

> Add `"com.toolstack.io"` to whatever package-name allowlist the backend uses,
> so it doesn't reject ToolStack verification requests as unknown apps.

### 3. Entitlement persistence (optional but recommended)

Currently `isPremium` is derived entirely from `BillingRepository.purchaseState`,
which is populated by querying Google Play on startup and every resume. This means:

- The user briefly sees the locked state on cold start until `queryActivePurchases`
  completes (usually < 1 s, but noticeable on slow devices).
- There is no offline cache of the entitlement.

**Fix:** persist the last known `isPremium` value to DataStore (same pattern as
`SettingsRepository.isAdFree` in RiverWatch), and seed `HomeUiState.isPremium`
from that cached value so the UI is correct immediately on launch.

### 4. Restore Purchases UI (optional)

`BillingRepository.queryActivePurchases()` is already the restore mechanism —
it re-queries Play for all active purchases. Adding a "Restore Purchases" button
(e.g. in a future Settings screen) is just calling `viewModel.loadProducts()` +
`billingRepository.queryActivePurchases()`. No new backend work required.

### 5. Decide on Pro feature set

Right now only `ConduitBends` is flagged `isPremium = true`. Additional modules
flagged as Pro just need `isPremium = true` added to their `Module` entry in
`HomeScreen.kt` — the gate logic is already generic.

---

## Architecture notes

- `isPremium` flows: `BillingRepository.purchaseState` → `HomeViewModel.uiState.isPremium` → `HomeScreen`
- `BillingRepository` is `@Singleton` and self-connects on first use. No manual
  lifecycle management needed.
- Verification policy: fail-**open** on `IOException` / `UnknownHostException`
  (trusts Play's local `PURCHASED` state when backend is unreachable); fail-**closed**
  on HTTP 4xx/5xx (explicit backend rejection is honoured).
- The shared backend URL is the only thing that needs to change at ship time.
  Everything else — product IDs, verification logic, UI gate — is already wired.
