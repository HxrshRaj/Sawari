# Sawari Privacy Policy

_Last updated: 14 October 2026_

Sawari ("the app") is a demo car-rental application. This policy explains what data it handles.

## Data we collect
- **Account data you give us:** name, email address and a password. The password is stored only as a salted bcrypt hash on our server; we cannot read it.
- **Booking data:** which car you booked and the start/end time.
- **Diagnostics (only in builds that include Firebase):** anonymous usage events (search, booking created, booking cancelled, booking conflict) via Firebase Analytics, and crash reports via Firebase Crashlytics. Crash reports can include device model, OS version and a stack trace. We associate them with an internal numeric user id, never with your email or name.

## What we do not collect
Location, contacts, photos, microphone, camera, advertising ID, or payment details. Sawari does not process real payments.

## How data is used
Account and booking data is used only to run the service (sign you in, show and cancel your bookings). Diagnostics are used only to find and fix bugs.

## Storage and sharing
- Data on your device (cached cars and bookings, your sign-in token) stays on your device and is removed when you sign out of the app (bookings and token) or uninstall it.
- Server data is kept in a PostgreSQL database. We do not sell data or share it with third parties, except Google Firebase as a processor for the diagnostics above.

## Your choices
You can ask for your account and bookings to be deleted by emailing the contact below. Firebase diagnostics are absent from the "lite" build.

## Children
Sawari is not directed at children under 13.

## Contact
Harsh Raj, hraj15709@gmail.com
