# Sian OS Wearable Sync

A small Android app that reads today's steps, active calories, and last night's sleep from
Android Health Connect and pushes them to Sian OS via `POST /api/wearable-metrics`. Built to
replace Tasker for syncing a Xiaomi Smart Band (or any device that writes into Health Connect
through Mi Fitness).

This project was written without a local Android build environment available (no JDK/Android
SDK/Gradle in the authoring environment), so it has not been compiled. Build and test it in
Android Studio before relying on it.

## Setup

1. Install [Mi Fitness](https://play.google.com/store/apps/details?id=com.xiaomi.wearable) and
   [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata)
   from the Play Store (Health Connect ships built into the OS on Android 14+, otherwise install
   it manually). Pair your Xiaomi Smart Band in Mi Fitness and enable its sync to Health Connect
   in Mi Fitness's settings.
2. Open this folder (`sian-os-wearable-sync/`) in Android Studio: **File → Open**, pick this
   directory. Let Gradle sync; if it asks about the Gradle wrapper, let it generate/download one.
3. Connect your phone over USB with USB debugging enabled (Settings → About phone → tap Build
   number 7 times → Developer options → USB debugging), then click **Run** in Android Studio to
   install the app directly.
4. In the app:
   - Base URL is prefilled with the production Sian OS URL. Change it only if testing locally.
   - Enter the `MCP_API_KEY` value if one is set in production (`wrangler secret put MCP_API_KEY`
     in the `sian-os` repo). Leave blank if that secret is unset.
   - Tap **Save settings**.
   - Tap **Grant Health Connect access** and approve the three permission requests (steps, active
     calories, sleep).
   - Toggle **Sync automatically in the background** to enable hourly syncing via WorkManager.
   - Tap **Sync now** any time to sync immediately and see the result on screen.

## How it works

- `HealthConnectRepository` reads today's cumulative steps and active calories (midnight to now),
  and the most recently completed sleep session (searched from yesterday noon onward, since sleep
  spans midnight).
- `SianOsApiClient` POSTs whatever fields have data to `/api/wearable-metrics` with
  `Authorization: Bearer <api key>` if one is set. That endpoint is a partial upsert — it only
  updates the fields present in the request, so it's safe to call repeatedly through the day
  without touching manually-entered check-in fields.
- `WearableSyncWorker` (a `CoroutineWorker`) does the actual read-and-post; `SyncScheduler`
  enqueues it hourly via `WorkManager` when auto-sync is enabled, or once immediately for
  "Sync now".
- Settings (base URL, API key, auto-sync flag) are stored in plain Jetpack DataStore, not
  encrypted storage — this is a single-owner app on your own device handling your own data, so
  that trade-off was made to avoid extra dependency/version risk in an unbuilt/untested project.

## If the build fails

This was written targeting a known-stable toolchain (AGP 8.4.2, Kotlin 1.9.24, compileSdk/targetSdk
34, minSdk 26, Health Connect `connect-client:1.0.0`), but hasn't been compiled. If Android Studio
reports a version conflict, the most likely fix is bumping the AGP/Kotlin versions in the root
`build.gradle.kts` to whatever Android Studio's Upgrade Assistant suggests — the app code itself
doesn't depend on anything version-specific beyond the Health Connect API shapes used in
`HealthConnectRepository.kt`.
