# Google Play store listing (draft, not yet published)

**App name:** Sawari: Rent a Car (max 30 chars)
**Package:** com.hxrsh.sawari
**Category:** Auto & Vehicles · **Free** · **Content rating:** Everyone (complete the IARC questionnaire in Play Console)

## Short description (max 80)
Browse cars in your city, pick a time slot and book in seconds.

## Full description
Sawari makes renting a car simple.

- Browse cars available in Bengaluru, Mumbai and Delhi, with photos, seats, fuel type and hourly price
- Search by name and filter by city and fuel
- Pick a date, start time and duration; see the total before you confirm
- Instant conflict detection: if someone else grabs the car first, you are told straight away and shown what is taken
- See and cancel your bookings
- Works offline: the last cars and bookings you saw stay available, and the app tells you when it is showing saved data

This is a demonstration service: no real payments are taken and no real cars are rented out.

## Assets
- Phone screenshots: `docs/screenshots/` (1080x2400, taken from the emulator). Play wants 2 to 8 phone screenshots; use 03, 05, 06, 07, 10.
- Feature graphic (1024x500) and 512x512 icon: still to be produced (the adaptive launcher icon is `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`).
- Privacy policy URL: host `store/privacy-policy.md` (e.g. via GitHub Pages or the repo's raw/blob URL) and paste the URL in Play Console.

## Data safety form answers
- Data collected: Name, Email, User IDs, App activity (in-app events), Crash logs, Diagnostics
- Encrypted in transit: yes (HTTPS) · Users can request deletion: yes (email)
- Shared with third parties: no (Firebase is a service provider/processor)

## Before you can publish (needs you)
1. A Google Play developer account (one-time US$25 fee) and identity verification.
2. An upload keystore: `keytool -genkeypair -v -keystore sawari-upload.jks -alias sawari -keyalg RSA -keysize 2048 -validity 10000`, then create `keystore.properties` (see README). Keep both out of git.
3. Your own Firebase project: add `app/google-services.json`.
4. Build the bundle: `./gradlew :app:bundleFirebaseRelease -Psawari.releaseBaseUrl=https://<your-render-url>/`
5. New personal accounts must run a closed test with at least 12 testers for 14 days before production access. Real install counts only come from real users.
