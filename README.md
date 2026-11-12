# Sawari: car rental, native Android + FastAPI

Browse cars in a city, pick a time slot, book, and see/cancel your bookings. A native Android app (Kotlin, Jetpack Compose) talking to a real FastAPI + PostgreSQL backend. Double-booking is prevented **inside the database**, not in app code.

| | |
|---|---|
| Live API docs | https://sawari-api-1krd.onrender.com/docs (Render free tier: the first request after idle can take ~30 s) |
| Debug APK | built by CI on every push, attached to the GitHub release on `v*` tags |
| Tests | 95 JVM tests (72 app + 23 shared) + 49 emulator tests + 10 backend tests. The 23 shared tests also run on the iOS simulator in CI. Counts are from the last full run, see Tests |

<p>
<img src="docs/screenshots/01_login.png" width="150"> <img src="docs/screenshots/03_car_list.png" width="150"> <img src="docs/screenshots/05_booking.png" width="150"> <img src="docs/screenshots/07_conflict.png" width="150"> <img src="docs/screenshots/06_my_bookings.png" width="150"> <img src="docs/screenshots/10_offline_cars.png" width="150">
</p>

_Left to right: sign in, car list, booking, double-book conflict, my bookings, offline with cached data. Taken from the Android emulator (API 34)._

## Architecture

```mermaid
flowchart LR
  subgraph UI["ui (Compose, single Activity)"]
    S[Screens] --> VM[ViewModels<br/>StateFlow state]
  end
  subgraph Domain["domain (pure Kotlin)"]
    UC[Use cases] --> RI[Repository interfaces]
    M[Models, AppResult / AppError]
  end
  subgraph Data["data"]
    RP[Repository impls]
    API[Retrofit + OkHttp<br/>auth + GET-only retry]
    DB[(Room<br/>single source of truth)]
  end
  VM --> UC
  RP -. implements .-> RI
  RP --> API
  RP --> DB
  DB -- Flow --> RP
  API --> BE[(FastAPI + PostgreSQL)]
```

- **Unidirectional data flow.** The UI observes `StateFlow<State>`; user actions call ViewModel functions (or, on the booking screen, send `Intent`s). Nothing in the UI mutates state directly.
- **Room is the single source of truth.** Screens never read the network: repositories expose `Flow`s from Room, and `refresh()` fetches and swaps the table in one transaction. Offline, the app opens with the last data and shows a banner ("You're offline. Showing saved cars."). A failed refresh never clears the cache (there is a test for exactly that).
- **Errors are values.** `AppResult<T>` / sealed `AppError` (`Network`, `Unauthorized`, `Conflict` (409), `Validation`, `NotFound`, `Server`). Every screen renders loading, empty, error and content (`UiState`).
- **Networking.** Retrofit + OkHttp + kotlinx.serialization. Timeouts 10s connect / 20s read. An interceptor retries **only GETs** (IO errors, 502/503/504, exponential backoff, max 2). POSTs (login, book, cancel) are never retried. A 401 on an authenticated call clears the session and the UI returns to login.
- **DI:** Hilt. **Reactive:** Coroutines/Flow. **Images:** Coil with explicit size limits, bounded memory/disk caches, RGB_565 on low-RAM devices.

### Booking screen is MVI; the rest are MVVM
1. MVVM screens expose several observable values and many ViewModel methods; MVI collapses that into **one immutable `BookingState`**.
2. The UI never calls ViewModel methods for each action; it sends **`BookingIntent`s** (`PickDate`, `ChangeDuration`, `Submit`) into a single `onIntent` reducer.
3. State is only ever produced by that reducer, so every screen state is reproducible and unit-testable by replaying intents.
4. **One-shot `BookingEffect`s** (`Booked`, `ShowMessage`) go through a `Channel`, so a snackbar or navigation never replays on rotation or recomposition, which `StateFlow` state would do.
5. Cost: more ceremony (contract file, reducer) than a plain ViewModel, so it is only used where there are real races (a booking conflict) and one-shot outcomes.

## The double-booking guarantee
`bookings` has a PostgreSQL **exclusion constraint** (`btree_gist`):

```sql
EXCLUDE USING gist (car_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&) WHERE (status = 'confirmed')
```

The API just `INSERT`s and maps the constraint violation to HTTP 409. There is no check-then-insert window to race through. Back-to-back bookings (end == next start) are allowed; cancelling frees the slot. The test suite fires **20 concurrent requests for the same car and slot and asserts exactly one `201` and nineteen `409`s**.

## Run it
**Backend** (needs Docker for Postgres):
```bash
docker run -d --name sawari-pg -e POSTGRES_USER=sawari -e POSTGRES_PASSWORD=sawari -e POSTGRES_DB=sawari -p 25432:5432 postgres:16-alpine
cd backend && python -m venv .venv && .venv/Scripts/pip install -r requirements-dev.txt   # Linux/macOS: .venv/bin/pip
DATABASE_URL=postgresql+psycopg://sawari:sawari@localhost:25432/sawari .venv/Scripts/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# interactive docs: http://localhost:8000/docs
```
Tests: `docker exec sawari-pg createdb -U sawari sawari_test` once, then `cd backend && pytest`. 24 cars across Bengaluru, Mumbai and Delhi are seeded on first start.

**Android** (JDK 17+, Android SDK 35):
```bash
./gradlew :app:assembleLiteDebug          # app/build/outputs/apk/lite/debug/app-lite-debug.apk
./gradlew :app:testLiteDebugUnitTest      # 44 unit tests (JVM + Robolectric)
./gradlew :app:connectedLiteDebugAndroidTest   # 6 Compose UI tests, needs an emulator/device
./gradlew :app:lintLiteDebug
```

### API base URL setting
The app reads `sawari.baseUrl` at build time (`BuildConfig.BASE_URL`), in this order: `-Psawari.baseUrl=...`, `local.properties`, then `gradle.properties` (default `http://10.0.2.2:8000/`, the emulator's alias for your machine). Release builds use `sawari.releaseBaseUrl` (falls back to `sawari.baseUrl`).
```bash
./gradlew :app:assembleLiteDebug -Psawari.baseUrl=https://YOUR-SERVICE.onrender.com/
```
Cleartext HTTP is allowed only for `10.0.2.2` and `localhost` (`network_security_config.xml`).

### Build flavours
- `lite`: no Firebase at all. What CI builds. Works with no extra files.
- `firebase`: Crashlytics + Analytics. Supply your own `app/google-services.json` (from your Firebase console; **never committed**, listed in `.gitignore`). Without the file the flavour still builds and runs; the analytics wrapper detects that Firebase did not initialise and logs to Logcat instead.

Events sent: `search`, `booking_created`, `booking_conflict`, `booking_cancelled`. Debug builds add a menu on *My bookings* (⋮) with **Debug: record non-fatal** and **Debug: crash app** to prove reports arrive in the console.

## Tests (real counts)
| Suite | Count | Notes |
|---|---|---|
| Backend pytest | 10 | auth, filters, overlap rules, cancel/ownership, validation, **20-way concurrent booking** |
| ViewModel unit tests | 22 | Turbine + hand-written fake repositories; includes the MVI booking conflict path |
| Repository / network tests | 17 | real Retrofit against `MockWebServer`; real Room (Robolectric); retry rules, error mapping, 401 handling |
| Room DAO tests | 5 | filters, atomic `replaceAll`, ordering, embedded car |
| Compose UI tests | 6 | run on the emulator (`androidTest`) |

(That table is the original 60-test baseline. Counts now: **JVM 95** = 72 in `app` + 23 in `shared/commonTest`; **emulator 49** = deep links 4, reminders 8, permission flow 6, licence scan 3, animations 7, Compose/accessibility 11, font scale 5, reading order 5; **backend 10** (not re-run locally for this branch; green in CI). The emulator tests need a fresh install for the permission-denial tests. Lint: 0 errors, 99 warnings.)

### Mutation checks (done once each, both caught)
- **Overlap check:** removed the exclusion-constraint DDL: `test_overlap_rejected_but_adjacent_and_other_car_allowed` and `test_concurrent_double_booking_only_one_wins` failed (2 failed, 8 passed). Restored.
- **Cache fallback:** made `CarRepositoryImpl.refresh()` wipe the table before calling the network: `cache fallback - a failed refresh leaves the cached cars untouched` failed (1 failed of 44). Restored.

## Performance, measured
**Where:** Android Emulator AVD "sawari_lowend": Pixel 2 profile, Android 14 (API 34) x86_64 Google APIs, **1.5 GB RAM, 2 vCPUs, 720x1280 @ 240 dpi**, SwiftShader (software) GPU, running on a Windows 11 laptop with 15.7 GB RAM. I first tried 1 GB, but Android 14 itself thrashed (the low-memory killer killed system apps and the instrumentation process) and the run crashed, so 1.5 GB is the lowest that gave a valid run. Tools: Macrobenchmark (`:benchmark`) with a release-like build (R8 + resource shrinking), Perfetto trace sections, `MemoryUsageMetric`. Raw output: [`docs/bench/`](docs/bench).

**Naive vs optimised car list** (same data, same device; naive is a deliberately bad implementation kept in `NaiveCarList.kt`: non-lazy Column, no keys, unstable model rebuilt per composition, fresh lambdas and `ImageRequest`s, original-size software bitmaps; select it with `am start --ez naive_list true`):

| Metric (median) | Naive | Optimised | Change |
|---|---|---|---|
| Card bodies run during a 4-step city-filter toggle ("recompositions") | 48 | 7 | **-85%** |
| Time inside card composition for that scenario | 58.6 ms | 19.4 ms | -67% |
| App anonymous RSS after scrolling the list | 89.0 MB | 54.4 MB | **-39%** |
| Frame time P50 / P90 / P99 while scrolling (`frameDurationCpuMs`) | 40.7 / 56.7 / 258.7 ms | 40.5 / 56.5 / 187.7 ms | P99 better, P50 no change |
| Frames over 16.7 ms | 95.9% | 96.8% | **no improvement** |

What the optimised list does: `LazyColumn` with `key = { it.id }` and `contentType`, `@Immutable` models so cards are skippable, `remember`ed image requests, Coil `size(540x340)` decoding instead of the original bitmap, bounded Coil caches.

**Honest reading:** the list optimisations clearly cut recomposition work and memory, but **they did not reduce jank on this emulator**: both versions miss the 16.7 ms frame budget on ~96% of frames because the emulated 2-vCPU, software-rendered device is slow at everything. I re-ran the scroll test with the host GPU (naive: P50 32.1 ms, 92.5% janky; optimised after one more tweak, removing a redundant clip and shrinking the decode size: P50 36.3 ms, 95.3% janky) and got the same picture. So: the "jank percentage" here is a property of the emulator, not a verdict on real hardware, and I can't claim a frame-time win. This needs a physical low-end phone.

**Cold start** (6 iterations each, medians; ranges shown because they overlap):

| | No profile | With baseline profile |
|---|---|---|
| Time to initial display | 3186 ms (3025-4120) | 3393 ms (2371-4896) |
| Time to full display (cars on screen) | 5254 ms (4884-6412) | 4531 ms (3600-6086) |

The baseline profile (generated by `BaselineProfileGenerator`, committed as `app/src/main/baseline-prof.txt`) gave a **~14% better median time-to-full-display, but the ranges overlap and initial display was not better**, so treat it as "probably helps a little", not proven. I did not benchmark the unminified debug build against this one, so I have no "R8 before/after" start-up number; the size effect is measurable: **debug APK 19.7 MB vs R8-shrunk release APK 2.0 MB**.

**LeakCanary** is in debug builds. I drove the debug app through login, list, detail, booking, rotation (6 Activity destroys watched), bookings, sign-out and sign-in: **LeakCanary reported no leaks** (0 retained objects). That only covers the paths I exercised.

## Release build and Play Store (not published)
Signing config comes from `keystore.properties` (git-ignored), e.g.:
```properties
storeFile=sawari-upload.jks
storePassword=...
keyAlias=sawari
keyPassword=...
```
Then `./gradlew :app:bundleFirebaseRelease -Psawari.releaseBaseUrl=https://YOUR-SERVICE.onrender.com/`. Without a keystore the release APK builds unsigned (`app-lite-release-unsigned.apk`, verified). Listing text, data-safety answers and the privacy policy are in [`store/`](store). **Publishing needs the owner's Google Play developer account (one-time fee), so I stopped before that step.**

## Deploying the backend (Render)
`render.yaml` is a Blueprint: a Docker web service (`backend/Dockerfile`) plus a free Postgres, with `DATABASE_URL` wired in and a generated `JWT_SECRET`. In Render: *New → Blueprint → pick this repo*. The public docs URL will be `https://<service>.onrender.com/docs`. Deployed at https://sawari-api-1krd.onrender.com. I curl-tested the live service: `/health`, 24 cars in 3 cities, register, book, the same booking again (`409`), cancel.

## Deep links
| Link | Opens |
|---|---|
| `sawari://car/{id}` | that car's detail screen |
| `sawari://bookings` | My bookings tab |
| `https://sawari-api-1krd.onrender.com/app/car/{id}` and `/app/bookings` | same, as plain (unverified) https links |

How it works: `MainActivity` is `singleTask`, so a link reaches an already-open app through `onNewIntent` and a closed app through `onCreate`. `DeepLinkParser` (pure Kotlin) turns the URI into `OpenCar` / `OpenBookings` / `Invalid`; the result waits in `MainViewModel`, so a **logged-out user signs in first and then lands on the target**. A car id that does not exist shows "Car not found" (the detail screen refreshes the cache first, so a cold start on an empty cache does not falsely say "not found"; offline it says it cannot load). A malformed link (`sawari://car/abc`) shows a snackbar and never crashes.

Try it on an emulator (debug build, `adb` on PATH):
```bash
adb shell am start -a android.intent.action.VIEW -d "sawari://car/12" com.hxrsh.sawari
adb shell am start -a android.intent.action.VIEW -d "sawari://bookings" com.hxrsh.sawari
adb shell am start -a android.intent.action.VIEW -d "https://sawari-api-1krd.onrender.com/app/car/12" com.hxrsh.sawari
```
`bash scripts/deeplink_check.sh` runs the whole matrix against a local backend (cold start logged out then login, app already open, unknown id, malformed link, https link, cold start logged in) and prints PASS/FAIL. Last run: 8 of 8 PASS on an API 34 emulator.

Tests: `DeepLinkParserTest` + `DeepLinkFlowTest` (JVM, 10 tests: a 28-row table of valid, broken and foreign URIs, pending link kept across login, unknown / empty-cache / offline car detail) and `DeepLinkIntentTest` (emulator, 4 tests: the manifest routes the custom-scheme and https URIs to `MainActivity`, does not claim foreign URIs, and a cold start with valid / unknown / malformed links does not crash).

**Honest limits:** the https links are *not* verified App Links. That needs `https://sawari-api-1krd.onrender.com/.well-known/assetlinks.json` hosted with the app's signing fingerprint, which is not set up, so on a real phone Android may show a chooser instead of opening the app directly. The `onNewIntent` path (app already open) is verified with adb only; `ActivityScenario` cannot reliably close a `singleTask` activity, so there is no instrumented test for it.

## Booking reminders and the notification permission
A confirmed booking schedules a local notification **1 hour before it starts** (WorkManager, no server, no Firebase). Cancelling the booking cancels the reminder. Tapping the notification opens My bookings through the same deep link as `sawari://bookings`.

- **Rule (`ReminderPlanner`, pure Kotlin):** fire at `start - 1h`. A booking that starts in less than an hour gets **no** reminder (a "starts in 1 hour" message would be false). Tested with fake clocks.
- **Wiring:** `CreateBookingUseCase` / `CancelBookingUseCase` call a `ReminderScheduler` (interface in `domain`, WorkManager implementation in `notifications`). A failed booking or failed cancel changes nothing.
- **Channel:** `booking_reminders`, created lazily (when the user taps through the explanation, or when a reminder is shown), not at app start, so nothing can trigger a notification prompt before the explain-first step. This is a precaution; I did not observe the system doing so. The unsolicited prompt I did see on the emulator came from LeakCanary in the debug build.
- **Android 13+ permission (`POST_NOTIFICATIONS`)** is handled on the booking screen by a reusable `PermissionController` with four states: *not asked* (explain first, in a dialog, then ask), *denied once* ("Ask again"), *denied permanently* ("Open settings", which opens the app's system settings page; the state refreshes when you come back), and *granted*. Android cannot tell "never asked" from "don't ask again", so the app remembers whether it has asked. Booking works in every state; only the reminder depends on the permission. Before Android 13 there is no runtime permission, so only the "blocked in settings" state applies.

Tests: 14 JVM tests (`ReminderPlannerTest`: 7 fake-clock cases; `ReminderUseCaseTest`: 6; `PermissionStatusTest`: every state combination) and 14 emulator tests (`ReminderInstrumentedTest`: real WorkManager enqueue / cancel / replace / skip, the notification posted by the worker, the notification's tap intent delivering `sawari://bookings` to `MainActivity`; `PermissionFlowTest`: the **real system permission dialog** driven with UiAutomator, deny, ask again, deny again, ends at "Open settings" with a quiet no-op when showing a reminder, the granted state, each card state, rationale-before-request, and booking still working with notifications denied).

**Honest limits:** nothing waited a real hour: the clock is faked and the worker is run directly. The "denied" emulator test needs the permission to start un-granted (fresh install; the tests run in name order and skip, not pass, if it is already granted) and needs Android 13+. The system dialog buttons are found by id or by text ("Don't allow"), so another Android version or a localized emulator could need a tweak. Reminders are not re-scheduled after a reboot beyond what WorkManager persists, are not reconciled with bookings cancelled from another device, and are not cancelled on logout. The notification channel's behaviour on OEM skins with aggressive battery savers is not tested.

## Driving licence photo (camera, on-device only)
The booking screen has an optional **Scan driving licence** card: explain, ask for the camera, live CameraX preview, take photo, review, **Retake** or **Use photo**. The confirmed photo's *path* becomes part of the booking *draft*. It is not uploaded, not verified (no OCR, no ID check) and is deleted when you remove it, when the booking succeeds, or when you leave the booking screen. It lives in app-private storage (`filesDir/licence`).

- **Permission:** same reusable controller as the reminders (not asked, denied once, don't-ask-again with an "Open settings" button). If the camera is refused, the **system photo picker** (no permission needed) is offered as a fallback.
- **State machine:** `ScanState.on(event)` is a pure function (Preview, Capturing, Review, Done, Cancelled). A double tap on the shutter, a late camera callback after Retake, or Confirm with no photo are ignored.
- **Camera lifecycle:** CameraX is bound to the lifecycle in a `DisposableEffect` and unbound on leave; the shutter stays disabled until the camera is really bound. Only the back camera is configured (`CameraXConfig` limiter), which also fixes CameraX refusing to start on devices that declare a front camera they do not have (the stock emulator does).
- **Bugs the real-app check found and I fixed:** (1) tapping the shutter before the camera was ready failed the capture; (2) after **Use photo** the app silently re-opened the camera while closing the scanner and kept holding it on the booking screen (a late CameraX bind callback ran after the preview was gone). Both are covered now.

Verified on the emulator (its virtual back camera): `bash scripts/licence_check.sh` drives the real debug app and checks, among others, that the rationale shows before the system dialog, the camera is in use only while previewing, the preview and the review survive rotation, exactly one photo exists after a capture, a retaken photo is deleted, the camera is released while reviewing and after confirming, Back closes the scanner, and that **LeakCanary** (debug build) did not dump the heap or report a leak after repeated open/close of the scanner. Tests: 16 JVM (state-machine table, file deletion on retake / cancel / replace, picker import, draft lifecycle) and 3 emulator tests (the real camera permission dialogs: deny, ask again, deny, "Open settings" + picker fallback; a real capture, retake, confirm with the files checked on disk).

**Honest limits:** the emulator camera is a synthetic scene, so image quality, autofocus and real-device camera quirks are untested. The photo picker fallback is shown but I could not drive the system picker UI, so picking a photo is covered by unit tests of the import path only. LeakCanary only proves no leak on the paths I exercised. The photo file is not encrypted. Debug builds include LeakCanary, which shows its own notification-permission dialog on Android 13+ (the check script pre-grants it); release builds do not.

## Animations (and reduced motion)
- **Car list:** a skeleton with a shimmer replaces the spinner while loading (still announced once as "Loading"); cards fade and slide up the first time they appear (new ids only, not when scrolling back); `animateItem()` slides cards to their new place when a filter reorders the list.
- **Booking summary:** the card animates its size, and the total rolls up or down when the duration changes (`AnimatedContent`).
- **Reduced motion:** if the user turned animations off (Developer options "Animator duration scale: off", which "Remove animations" also sets), `LocalReduceMotion` is true: no shimmer (a static placeholder), no enter transition, and the total is a plain `Text` with no `AnimatedContent`. The app follows the setting live (`ContentObserver`). Tested with the real system setting.
- **Why a wrapper composable and not a `Modifier` for the enter animation:** a modifier created inside the list item would make every `CarCard` recompose when the animation starts. The wrapper keeps the animation state in its own scope and applies progress in the graphics layer (draw phase).

**Accessibility re-checked after the change** (emulator): the automated Compose accessibility checks, the 200% font-scale tests, the reading-order tests and the 48 dp touch-target assertions all pass. One test had to change: the booking screen got taller (licence card), so the confirm button starts below the fold and a partly visible node reports a clipped touch area; the test now scrolls it into view before measuring, as a user would. No layout or size was weakened.

**Recomposition re-measured** with the method from the Performance section (Macrobenchmark `ListBenchmark.filterToggleOptimised`, `CarCard` trace section, same AVD, 5 iterations, baseline = the commit before these changes, built and run the same way):

| | Before | After |
|---|---|---|
| Card bodies run in the 4-step city-filter toggle | 7 (7,7,7,7,7) | **7** (7,7,7,7,7) |
| Summed time in `CarCard` composition, median (range) | 21.0 ms (11.8 to 53.2) | 56.2 ms (30.1 to 71.7) |

The count did not change. The time did look higher, but the ranges overlap and this emulator is noisy, so I do not claim either "no cost" or "a cost". Frame time and jank were **not** re-measured.

Tests: 1 JVM test for the reduce-motion rule and 7 emulator tests (skeleton and its announcement, with and without reduced motion; cards appear and stay clickable; the total ends on the right price and, with reduced motion, has no outgoing copy; the real system animator setting is detected and followed live).

**Honest limits:** I judged the animations by tests and by the screens I looked at on the emulator, not on a real device; smoothness is not measured. Enter animations replay when the list is rebuilt from scratch (new composition).

## Shared Kotlin Multiplatform module (Android + iOS targets)
`shared/` is a Kotlin Multiplatform library (targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`) holding the parts of the domain layer that are genuinely platform-independent: the models, `AppResult` / `AppError`, the use cases, the repository **interfaces**, `ReminderScheduler` / `ReminderPlanner`. The Android app depends on it; its package names did not change.

What stayed in `app/` on purpose: Room, Retrofit/OkHttp, DataStore, WorkManager, CameraX, Compose, Hilt wiring and all ViewModels (they use Android `ViewModel`, `SavedStateHandle`, Compose types).

What had to change to make the move honest:
- **Dates:** `java.time.Instant` is JVM-only, so the models use `kotlinx.datetime.Instant`; the app converts at its edges (mappers, formatters, ViewModels).
- **`@Immutable`:** it comes from Compose, which a shared module must not depend on. The app marks `com.hxrsh.sawari.domain.model.*` stable in `app/compose_stability.conf` instead. Re-measured: the card recomposition count in the filter-toggle benchmark is still 7 (5 of 5 runs).
- **`@Inject`:** `javax.inject` is JVM-only, so the use cases are plain classes and `di/UseCaseModule.kt` builds them for Hilt.
- **`expect/actual` (one real case): `AppLog`.** Android logs with `android.util.Log`, iOS with `NSLog`; neither exists on the other platform. Dates did not need `expect/actual` because kotlinx-datetime already covers them in common code. The first iOS run caught a real bug here: passing Kotlin strings to `NSLog` as `%@` varargs crashed the iOS test process; the actual now passes one escaped format string.

**What is verified (and how):**
- `:shared:testDebugUnitTest` runs the 23 `commonTest` tests on the JVM/Android target (locally and in CI): reminder rule, booking model, `AppResult`, create/cancel/auth use cases with hand-written fakes.
- The CI job **`shared-ios`** (`macos-latest`) runs `./gradlew :shared:iosSimulatorArm64Test` and `:shared:linkDebugFrameworkIosSimulatorArm64`. Its last run on this branch was green: **23 tests ran on the iOS simulator, 0 failed**, and `SawariShared.framework` was produced (the job prints the totals from the test result files). That job is the only evidence for iOS; I have no Mac, so nothing iOS was run locally.

**Honest limits:** there is **no iOS app**: no SwiftUI, no Compose Multiplatform UI, no Xcode project. The framework is built but nothing consumes it, so "the shared module compiles and its tests pass on iOS" is the whole claim. `iosX64` and `iosArm64` are declared but CI only builds and tests `iosSimulatorArm64`. The shared module has no networking or persistence, so an iOS client would still need those. Kotlin/Native compile warnings about expect/actual being in Beta are not suppressed.

## Quality
| Area | Status | Where |
|---|---|---|
| Accessibility | **Done and run**: automated Compose accessibility checks and 48dp touch-target assertions on all 5 screens, 200% font-scale fixes, contrast fixes, screen-reader focus order. TalkBack by ear **not run** | [docs/ACCESSIBILITY.md](docs/ACCESSIBILITY.md) |
| Crash analysis with Crashlytics | **Not run.** Needs a real Firebase project and `google-services.json` | n/a |
| Figma to Compose | **Not done** | n/a |
| Device / OS-version matrix | **Not run.** Only an Android 14 emulator has been used | n/a |

## Future scope
- **iOS client**, not started. What exists: the `shared` KMP module (domain layer) with a green iOS simulator test run in CI (see above). Still needed: networking, persistence (a Room/SQLDelight equivalent) and the UI (SwiftUI or Compose Multiplatform), and a Mac to build and run it.
- Crashlytics end-to-end verification against a real Firebase project, a Figma-to-Compose design pass, and a real-device / multi-OS-version test matrix (see the Quality table above).
- Encrypted token storage, pagination, and real payments.

## Honest limits
- **Free-tier hosting:** the Render service sleeps when idle (slow first request) and the free Postgres is time-limited. The emulator walkthrough, offline test and benchmarks were run against a local backend, not the Render one.
- **No real payments**, no real fleet. Prices are illustrative INR; total = hours x hourly rate.
- **Car photos are stock images reused across the catalogue** (9 photos, 24 cars) and do not match the car names.
- **Firebase Crashlytics/Analytics are integrated and compile, but I never saw an event or crash in a Firebase console** (no Firebase project is available to me). The `firebase` flavour is therefore unverified end to end.
- **All performance numbers are from an emulator**, not a physical device, and the frame-time results are inconclusive (see above). Start-up numbers are noisy (6 iterations).
- Auth token is stored in plain DataStore (not encrypted); fine for a demo, not for production.
- Only local reminder notifications (no push, no server-sent notifications), no pagination (24 cars), no payment/ID verification, English only.
- Play Store: listing assets prepared, **not published**; feature graphic and 512x512 store icon still to make.
