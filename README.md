# Sian OS Wearable Sync

An Android app that reads today's steps, active calories, and last night's sleep from Android
Health Connect and pushes them to [Sian OS](https://github.com/SianAliMalik2418/sian-os) via
`POST /api/wearable-metrics`. Built to sync a Xiaomi Smart Band (or any device that writes into
Health Connect through its companion app, e.g. Mi Fitness).

Confirmed working against a Xiaomi Smart Band 10 on Android 15.

## Download and install

1. Grab the latest APK from the [Releases page](https://github.com/SianAliMalik2418/sian-os-wearable-sync/releases/latest).
2. Open the downloaded `.apk` on your phone (from Downloads, or wherever your browser saved it).
   Android will ask to allow installing from this source the first time — allow it, then install.
3. Install [Mi Fitness](https://play.google.com/store/apps/details?id=com.xiaomi.wearable) and
   [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata)
   from the Play Store if you don't have them (Health Connect ships built into the OS on
   Android 14+, but installing the Play Store listing works too). Pair your band in Mi Fitness
   and enable its sync to Health Connect in Mi Fitness's settings.
4. Open **Sian OS Sync**:
   - Base URL is prefilled with the production Sian OS URL — leave it as-is unless you're
     pointing at a different deployment.
   - Enter the `MCP_API_KEY` value if one is set in Sian OS's production secrets. Leave blank if
     that secret is unset.
   - Tap **Save settings**.
   - Tap **Grant Health Connect access** and approve the permission screen that opens (steps,
     active calories, sleep). If tapping the button appears to do nothing, see the manifest note
     below — a missing permission-rationale declaration makes Health Connect silently reject the
     request instead of showing it.
   - Toggle **Sync automatically in the background** for hourly syncing via WorkManager, and/or
     tap **Sync now** any time for an immediate one-off sync.

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
  encrypted storage — this is a single-owner app handling your own data, so that trade-off was
  made to avoid extra dependency complexity.

## Manifest gotcha: permission button doing nothing

Health Connect checks that the app declares a resolvable "permissions rationale" activity before
it will show the permission screen at all — if that check fails, it silently returns with nothing
granted in under a tenth of a second, which looks exactly like the button doing nothing. Android
versions differ on which intent action they check for, so `AndroidManifest.xml`'s
`ViewPermissionUsageActivity` alias declares **both** the legacy `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
action and the newer platform `android.intent.action.VIEW_PERMISSION_USAGE` /
`HEALTH_PERMISSIONS` category pair. Don't remove either one.

## Building from source

Toolchain: AGP 8.9.1, Gradle 8.11.1, Kotlin 1.9.24, compileSdk/targetSdk 36, minSdk 26, Health
Connect `connect-client:1.1.0` (compileSdk 34/AGP 8.4.2 and connect-client 1.0.0 do **not** work —
1.1.0 requires compileSdk 36 and AGP 8.9.1+).

**With Android Studio**: open this folder directly (File → Open), let Gradle sync, connect a
phone over USB with USB debugging enabled, and click Run.

**From the command line** (no Android Studio needed — this is how it's actually been built and
tested):
```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:/path/to/gradle-8.11.1/bin:$PATH"
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
gradle assembleDebug --no-daemon
```
Requires a JDK 17, Gradle 8.11.1+, and an Android SDK with `platforms;android-36` and
`build-tools;36.0.0` installed (via `sdkmanager`). The output APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

To install straight to a connected/paired device without going through a file download at all:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(works over USB or wireless debugging — Settings → Developer options → Wireless debugging → pair,
then `adb pair <ip:port>` and `adb connect <ip:port>`).

## Roadmap

A remote sync button on the Sian OS web dashboard (push-triggered via Firebase Cloud Messaging,
rather than waiting for the hourly background job) is planned — see `NEXT_STEPS.md`.
