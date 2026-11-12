# Heuristic evaluation (expert review, not user research)

This is a review of three screens against Nielsen's 10 usability heuristics, done by the developer from the emulator
screenshots in `docs/screenshots/` (`03_car_list`, `05_booking`, `07_conflict`). It finds likely problems; it is **not**
user research, nobody else took part, and it has not been checked with real users. The other screens were not
reviewed here. Severity: 1 = cosmetic, 2 = minor, 3 = major, 4 = blocks the task.

| # | Screen | Heuristic | Observation | Severity | Suggested change |
|---|---|---|---|---|---|
| 1 | Car list | Match between system and the real world | The photos do not match the car names (for example "Hyundai i20" shows a different make). The README already says the photos are reused stock images. A user choosing a car by picture would be misled. | 3 | Use correct photos, or a neutral placeholder with the car's name until real photos exist. |
| 2 | Car list | Recognition rather than recall | The two chip rows (cities, then fuel types) have no labels, so a first-time user has to guess what each row filters. | 2 | Add short row labels ("City", "Fuel") or group them under one filter control. |
| 3 | Car list | Flexibility and efficiency of use | Each card is about a quarter of the screen, so only about two cars are visible at once; scanning 24 cars takes many scrolls. | 2 | A more compact card (smaller image) or a list/grid toggle. |
| 4 | Booking | Error prevention | Date and time are chosen with pickers (good), but "Confirm booking" stays enabled for a slot that has already been shown as taken (screen 07), so the user can submit the same conflicting request again. | 2 | After a conflict, disable Confirm until the date, time or duration changes. |
| 5 | Conflict | Help users recognise and recover from errors | The message says "Pick a different time below", but the pickers are above the message, and nothing suggests a free time. | 3 | Say "above", or better, suggest the nearest free slots. |
| 6 | Conflict | Visibility of system status | The "Already booked" list repeats the slot the user just tried; it does not show which hours are free. | 2 | Show free windows, or mark busy hours on the time picker. |
| 7 | Booking | Consistency and standards | The duration stepper (minus, "3 hours", plus) is clear and uses standard icons; the filled round buttons look different from the outlined date and time buttons next to them. | 1 | Keep, or use the same button style for both. |
| 8 | Booking | Visibility of system status | The total price updates with the duration and shows both start and end times. This works well. | 0 (positive) | Keep. |

## What this review cannot tell you

- Whether real users find the same problems, or how long the tasks take. That needs a test with people; see
  `usability-test-kit.md`.
- Anything about the screens that were not reviewed, or about accessibility (see `docs/ACCESSIBILITY.md`).
- Nothing in the app was changed as part of this review; the suggested changes are not implemented.
