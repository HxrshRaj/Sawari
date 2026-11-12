# Usability test kit (ready to run, no results yet)

A small moderated test: 5 people, about 20 minutes each, on a phone with the debug APK. No results are recorded in this
file. **Fill in the results table only with sessions that really happened.**

## Goals
1. Can a first-time user find a car in their city and book a slot without help?
2. Do they understand what to do after a booking conflict?
3. Can they find and cancel a booking?

## Participants
Five adults who have rented a cab or car online before and have not seen the app. Note age range and phone model only;
do not record names with results.

## Setup
Install the debug APK from the latest CI run. Run the backend locally, or use the hosted API (first request can take
about 30 s). Create one test account and one pre-booked slot so a conflict can be reached. Record the screen if the
participant agrees.

## Consent (read aloud)
"I am testing the app, not you. There are no wrong answers. You can stop at any time. I will take notes and, if you
agree, record the screen. Nothing you do is shared with your name on it. Do you agree to take part?"

## Tasks (read one at a time; do not give hints)
| # | Task | Success means |
|---|---|---|
| T1 | "Find a petrol car in Bengaluru under 250 rupees an hour." | Applies city and fuel filter and opens a matching car |
| T2 | "Book it for 3 hours this evening." | Booking confirmed |
| T3 | "Now try to book the car I have marked as already taken for 8 PM." (conflict) | Understands the conflict and chooses another time |
| T4 | "Find your bookings and cancel the first one." | Cancelled and sees it in the list |

Per task record: success (yes, yes with help, no), time in seconds, number of wrong taps, and one quote.

## Think-aloud prompts
"What are you looking at?", "What do you expect to happen if you tap that?", "Is anything confusing?" Do not explain the
screen; if stuck for 60 seconds, note "no" and move on.

## After the tasks: System Usability Scale (SUS)
Ask each statement, answer 1 (strongly disagree) to 5 (strongly agree):
1. I think that I would like to use this app frequently.
2. I found the app unnecessarily complex.
3. I thought the app was easy to use.
4. I think I would need the help of a technical person to use this app.
5. I found the various functions in this app were well integrated.
6. I thought there was too much inconsistency in this app.
7. I imagine most people would learn to use this app very quickly.
8. I found the app very cumbersome to use.
9. I felt very confident using the app.
10. I needed to learn a lot of things before I could get going with this app.

Score: for odd items subtract 1 from the answer; for even items subtract the answer from 5; add the ten numbers and
multiply by 2.5 (0 to 100). With 5 people, treat the average as a rough signal only.

## Results (empty until sessions are run)
| Participant | T1 | T2 | T3 | T4 | SUS | Main problem seen |
|---|---|---|---|---|---|---|
| P1 | | | | | | |
| P2 | | | | | | |
| P3 | | | | | | |
| P4 | | | | | | |
| P5 | | | | | | |

## After the sessions
List each problem with how many participants hit it, rank by severity (see the heuristic evaluation), fix the top three,
and re-run the failed task with two new people.
