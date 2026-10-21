# Garage

Personal Android app for tracking maintenance of cars and motorcycles. All data stays on the phone.

**Install:** download [Garage.apk](https://github.com/matanjerbi/Garage-App/releases/latest/download/Garage.apk) and open it on the phone.
Every push to `main` builds a new APK automatically (GitHub Actions) and replaces it on the `latest` release.
The APK is signed with a fixed key (`app/debug.keystore`), so a new build installs as an update and keeps your data.

## What it does
- Several vehicles, each with its own maintenance plan (built-in starter templates for car / motorcycle)
- Intervals by km and/or months, whichever comes first; status overdue / due soon / OK, with an estimated due date from your riding pace
- Service history (self or garage), repairs, costs, photo/PDF attachments
- Optional fuel tracking with km/L
- Test, insurance and license expiry reminders
- Daily push notifications
- Costs per month / year / km
- Backup export / import (single .zip)

## Build locally
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Stack: Kotlin, Jetpack Compose (Material 3), Room, WorkManager. Spec: `docs/spec.md`.
