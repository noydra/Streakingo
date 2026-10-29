# Streakingo – Duolingo Streak Saver

Streakingo keeps your Duolingo streak alive by completing one practice lesson for you every day. It is written with Kotlin Multiplatform and Compose Multiplatform. A single codebase runs on:

- **Desktop:** macOS and Windows
- **Android:** 8.0 and later

## Features

- **Connect your account**
  - **Desktop:** Streakingo opens your installed browser (Chrome, Edge or Firefox) with its own profile and without automation, so Google sign-in works too. When you sign in, the token is read automatically.
  - **Android:**
    - Email or username accounts can sign in inside the app.
    - Google accounts: Google blocks sign-in inside in-app browsers (WebView). Connect on the desktop first, open **Settings → Transfer to phone** to show a QR code, then scan it on your phone with **Transfer from desktop via QR**.
  - On both platforms you can also paste the token manually.
- **Do 1 lesson now**
- **Automatic lessons:** "Every day at 12:00" and "Custom time". Lessons run even when the app is closed:
  - **macOS:** launchd. If the Mac is asleep at that time, the lesson runs when it wakes up.
  - **Windows:** Task Scheduler. Runs only while you are signed in.
  - **Android:** AlarmManager. Running at the exact time requires the "Alarms & reminders" permission; the app warns you if it is missing.
- **Settings (⚙):** Theme (System / Light / Dark), language (English / Türkçe / 中文), About.
- **Adaptive layout:** The interface adjusts to the screen width and system font size, so text never overflows on small phones.
- Preferences, the token and history are stored in a local SQLite database:
  - macOS: `~/Library/Application Support/Streakingo/streakingo.db`
  - Windows: `%APPDATA%\Streakingo\streakingo.db`

## Project structure

```
composeApp/src/
  commonMain/    Shared code: Duolingo API (Ktor), database, translations, Compose UI
  desktopMain/   Desktop: entry point, launchd/schtasks scheduler, browser token fetcher, QR code
  androidMain/   Android: activity, AlarmManager, WebView sign-in, QR scanner
```

## Development

Open the project in Android Studio, or use the command line:

- Run the desktop app: `./gradlew :composeApp:run`
- Do 1 lesson without the UI: `./gradlew :composeApp:run --args="--run"`
- Install on a connected Android device or emulator: `./gradlew :composeApp:installDebug`

## Distribution

- macOS: `./gradlew :composeApp:packageDmg` (output: `composeApp/build/compose/binaries/main/dmg/`)
- Windows: `./gradlew :composeApp:packageMsi` (run it on Windows)
- Android APK: `./gradlew :composeApp:assembleRelease` (requires a signing config)

Notes:
- The JDK needed for packaging (Temurin 21) is downloaded automatically by Gradle.
- Turn on automatic lessons **from the installed app**. A schedule turned on during development (`gradlew run`) uses a script tied to the `build` folder and stops working after a clean.

## GitHub Actions

`.github/workflows/streak.yml` does 1 lesson every day at 09:00 UTC, so it works even when your computer is off.

Setup: Repository → Settings → Secrets → add your token as `DUOLINGO_JWT`.

## Warnings

- The token is as sensitive as your password and is stored unencrypted in the database. The "Transfer to phone" QR code contains this token too; do not share it.
- Streakingo uses an unofficial Duolingo API. If Duolingo changes it, the app may stop working.
- Automation is against Duolingo's terms of service and your account may be flagged. Use it at your own risk.
- Streakingo is not affiliated with Duolingo.
- The Google Sans font is distributed under the SIL Open Font License (`composeApp/src/commonMain/composeResources/files/GoogleSans-OFL.txt`).
