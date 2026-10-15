# Accessibility

Scope: the five Compose screens (`AuthScreen`, `CarListScreen`/`CarCard`, `CarDetail`, `BookingScreen`, `MyBookingsScreen`) and the shared `Components.kt`.
Everything below was run on this repo; where something was **not run** it says so.

## How it was checked
1. **Static audit** of every `ui/` file (labels, click targets, fixed heights, colours, headings, live regions).
2. **Automated Compose accessibility checks** (`enableAccessibilityChecks()` + `tryPerformAccessibilityChecks()`, from `ui-test-junit4-accessibility`, which runs Google's Accessibility Test Framework: touch-target size, contrast, missing labels, ...) on each screen.
3. **Explicit assertions** in `ComposeUiTest.kt`: content descriptions, headings, and a real **48x48dp touch-target** check (it measures `touchBoundsInRoot`, because Material widgets enlarge their touch area beyond their layout bounds, so `assertHeightIsAtLeast` under-reports).
4. **Colour contrast** computed by hand (WCAG formula) for every text/background pair the theme uses.
5. **200% font scale** rendering of every screen (`FontScaleTest`, density `fontScale = 2.0`), screenshots in [`docs/a11y/`](a11y), judged by eye.
6. **Screen-reader focus order** per screen (`ReadingOrderTest`), written to [`docs/a11y/reading_order_*.txt`](a11y).

## Before / after
Measured by running the new test suite against the code as it was before this branch, then again after the fixes.

| | Before | After |
|---|---|---|
| New accessibility tests (5 screens) passing | **0 / 5** | **5 / 5** |
| Whole instrumented suite | n/a | **21 / 21** (6 original UI + 5 accessibility + 5 font-scale + 5 reading-order) |
| JVM unit tests / lint | 44 / clean | 44 / clean |
| `heading()` semantics (call sites) | 0 | 9 |
| Live regions (conflict banner, auth error, duration, offline banner) | 0 | 4 |
| Controls announced as buttons that do nothing (chips with `onClick = {}`) | 4 per screen set (3 on detail + 1 per booking card) | 0 (read-only `InfoChip`) |
| Focus stops for one car card | 6 (image, name, city, seats, fuel, price) | 1 |
| Colour pairs below their WCAG threshold (4.5:1 text, 3:1 icons) | 2 (teal price text 4.34:1; gray icons 2.75:1) | 0 |
| Layouts that break at 200% font scale | 3 | 0 |

The "before" test failures were, per screen: no `heading()` on the car-name / section titles (detail, booking, my bookings), no merged description on cards (car list, my bookings), and the status not exposed on bookings. Compose's own automated checks did not flag anything on the unfixed screens; the explicit assertions are what failed. I did not record a separate "before" count for touch targets (the assertion that failed first stopped the test); after the fixes every listed control passes the 48x48dp check.

## Issues found and fixed
- **Cards were five separate focus stops** (image + name + place + seats + fuel + price) and the photo was announced as just the car name. Now one stop: *"Maruti Swift, Bengaluru, 5 seats, Petrol, ₹180 per hour"*. Booking cards likewise read *"... , ₹540, Confirmed"* with `stateDescription = Confirmed/Cancelled`. The Cancel button inside the card remains its own stop ("Cancel booking for Maruti Swift").
- **No-op chips announced as buttons**: the city/seats/fuel chips on the detail screen and the status chip on bookings were `AssistChip(onClick = {})`. Replaced with a non-interactive `InfoChip`.
- **No headings**: screen titles and section titles ("When do you need it?", "Duration", "Already booked", car name, "My bookings", "Sawari") now carry `heading()` so TalkBack's heading navigation works.
- **Dynamic changes were silent**: conflict/error banner (assertive), auth error (assertive), offline banner and duration counter (polite) are live regions.
- **Search field only had a placeholder** (not announced as a label). Now a real `label`.
- **Date/time buttons** read "Start date, Fri, 16 Oct 2026. Double tap to change" instead of an icon plus text fragments. The "Menu" icon button is now "Account menu". The submit button exposes "Booking in progress".
- **Detail photo** duplicated the heading's text for screen readers; marked decorative.
- **Contrast**: fact icons used a hard-coded `Color.Gray` (2.75:1 on the card surface), now `onSurfaceVariant` (7.24:1). The teal primary `#00796B` gave only **4.34:1** for price text on card surfaces; changed to `#00695C` (5.40:1 on cards, 6.30:1 on the page, white-on-primary buttons 6.61:1).
- **Fixed 48dp button heights** would clip large text; now `heightIn(min = 48.dp)`.
- **200% font scale** (screenshots `docs/a11y/*_x2.0.png`):
  - My bookings: the "Confirmed" chip was squeezed into **one letter per line** next to a long car name. Name now takes the remaining width, chip never wraps.
  - Booking: top-bar title "Book Toyota Innova Crysta Hybrid" was cut off ("Hybrid" clipped). Title is now "Book a car" and the car name is a heading in the content.
  - Car list: a long car name was truncated to one line with the price jammed against it. Name now allows two lines, price keeps its width.

## Contrast table (WCAG 2.x, computed)
| Pair | Ratio |
|---|---|
| onSurfaceVariant on card / page | 7.24 / 8.90 |
| Primary teal on page / on card (after fix) | 6.30 / 5.40 |
| White on primary (buttons) | 6.61 |
| Offline banner text on its container | 13.22 |
| Error text on page | 6.22 |
| Dark theme: primary on background / banner text | 7.60 / 7.18 |

## Screen-reader focus order
`docs/a11y/reading_order_*.txt` lists, per screen, the merged semantics tree in top-to-bottom, left-to-right order (one line per thing a screen reader can land on, with role, heading, state and live-region flags). **This is a model of what TalkBack reads, not a recording of TalkBack.** Two caveats: it sorts by position approximately (items on one row can swap), and it cannot tell what TalkBack actually speaks.

## Not run / not fixed
- **TalkBack by ear: not run.** TalkBack is installed on the emulator, but the emulator was run without audio, an agent cannot listen, and the slow emulator ANR'd under typing while I tried to drive the booking flow. I therefore did **not** complete a booking with TalkBack and make no claim about how it sounds. The reading-order files above are the substitute. Someone should do this pass on a real phone.
- **Real device: not run.** All checks ran on an Android 14 x86_64 emulator.
- The Material **date picker / time picker dialogs** and the dropdown menu are stock Material components and were **not audited** beyond their wrapper buttons.
- **Dark theme** was checked for contrast numbers only, not rendered at 200% or run through the automated checks.
- Switch Access, keyboard navigation and Reduce Motion were **not tested**. The app has no custom animations.
- `NaiveCarList` (benchmark-only, deliberately bad) is not accessible and is not reachable in normal use.
- The accessibility checks run on stateless screen content with fake data, not on the full app with real navigation.
