# Sawari: car rental, native Android + FastAPI

Browse cars in a city, pick a time slot, book, and see/cancel your bookings. A native Android app (Kotlin, Jetpack Compose) talking to a real FastAPI + PostgreSQL backend. Double-booking is prevented **inside the database**, not in app code.

| | |
|---|---|
| Live API docs | _pending: see [Deploying the backend](#deploying-the-backend-render); needs the owner's Render account_ |
| Debug APK | built by CI on every push, attached to the GitHub release on `v*` tags |
| Tests | 44 JVM unit tests + 6 emulator UI tests + 10 backend tests (60) |

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

(The JVM rows add up to 44: 22 ViewModel + 17 network/repository + 5 DAO.)

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
`render.yaml` is a Blueprint: a Docker web service (`backend/Dockerfile`) plus a free Postgres, with `DATABASE_URL` wired in and a generated `JWT_SECRET`. In Render: *New → Blueprint → pick this repo*. The public docs URL will be `https://<service>.onrender.com/docs`. I verified the Docker image locally (builds, boots two workers, creates the schema, seeds 24 cars, serves `/docs`, accepts a `postgres://` URL); I have **not** deployed it because that needs your Render account.

## Honest limits
- **Not deployed yet.** There is no live URL, because I have no access to a Render or GitHub account from here. Everything below was verified against the backend running locally.
- **No real payments**, no real fleet. Prices are illustrative INR; total = hours x hourly rate.
- **Car photos are stock images reused across the catalogue** (9 photos, 24 cars) and do not match the car names.
- **Firebase Crashlytics/Analytics are integrated and compile, but I never saw an event or crash in a Firebase console** (no Firebase project is available to me). The `firebase` flavour is therefore unverified end to end.
- **All performance numbers are from an emulator**, not a physical device, and the frame-time results are inconclusive (see above). Start-up numbers are noisy (6 iterations).
- Auth token is stored in plain DataStore (not encrypted); fine for a demo, not for production.
- No push notifications, no pagination (24 cars), no payment/ID verification, English only.
- Play Store: listing assets prepared, **not published**; feature graphic and 512x512 store icon still to make.
