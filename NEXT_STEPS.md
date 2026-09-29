# Status and next steps for a new agent session

## What's already working (confirmed live, 2026-09-29)

**Sian OS (main repo, `/Users/app/side-projects/sian-os`)**: fully built, committed, and deployed to production.
- `POST /api/wearable-metrics` accepts partial upserts of `steps`, `active_calories`, `sleep_hours` by date.
- Dashboard and Reports page show these fields.
- Docs are current: `AGENTS.md` (Write endpoints, Product invariants) and `docs/FITNESS_COACHING_CONTEXT.md` (Wearable data section, decision log) both describe this feature accurately.
- Read `AGENTS.md` first — it's the canonical operating doc for that repo.

**Sian OS Sync (this repo, `sian-os-wearable-sync`)**: a Kotlin/Android app that reads Health Connect (steps, active calories, last night's sleep) and pushes to `/api/wearable-metrics`. Built and installed on the owner's Xiaomi-band-paired phone (an Infinix X6885, Android 15). Confirmed working: synced 1384 real steps + active calories into production today.

- **This project is not yet a git repo.** No history, no `.git`. Worth initializing if you want an audit trail, but not required for it to work.
- The only real bug hit so far: Health Connect silently rejects (no UI, no error) permission requests if the app's rationale `activity-alias` in `AndroidManifest.xml` doesn't declare a resolvable intent-filter. Fixed by adding both the legacy `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (with `DEFAULT` category) and the newer platform `android.intent.action.VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS` category variants. See the manifest for the comment explaining this — don't remove either intent-filter.

## Local build environment (already set up on this Mac, don't redo)

- JDK 17 (Temurin): `/Users/app/dev-tools/jdk-17.0.20.1+1`
- Gradle 8.11.1: `/Users/app/dev-tools/gradle-8.11.1` (an unused 8.7 is also present, ignore it)
- Android SDK: `/Users/app/android-sdk` (platforms 34/36, build-tools 34/35/36, platform-tools incl. `adb`)
- Build command:
  ```bash
  export JAVA_HOME="/Users/app/dev-tools/jdk-17.0.20.1+1/Contents/Home"
  export PATH="$JAVA_HOME/bin:/Users/app/dev-tools/gradle-8.11.1/bin:$PATH"
  export ANDROID_HOME="/Users/app/android-sdk"
  export ANDROID_SDK_ROOT="/Users/app/android-sdk"
  cd /Users/app/side-projects/sian-os-wearable-sync
  gradle assembleDebug --no-daemon
  ```
- **Do not try `brew install` for Java/Android tooling** — on this machine/OS it fell back to building dozens of transitive deps from source (gobject-introspection, glib, etc.) and was impractically slow. Direct binary downloads (Temurin tarball, Gradle zip, Android cmdline-tools zip) worked in minutes instead.
- adb is paired to the phone wirelessly. If the pairing has expired (happens if phone/Mac reconnect to different networks, or after enough time), redo it: Settings → Developer options → Wireless debugging → "Pair device with pairing code" on the phone, then `adb pair <ip>:<port>` and `adb connect <ip>:<port>` (the main port shown, not the pairing one — they differ) on the Mac. Once connected, `adb install -r app/build/outputs/apk/debug/app-debug.apk` pushes updates straight to the phone, no more email/Drive download dance needed.
- Built APK is also copied to `dist/sian-os-sync.apk` for manual installs if adb isn't connected.

## In progress: remote sync trigger from the web dashboard

**Goal**: a sync icon on the Sian OS dashboard's Steps card that remotely tells the phone to sync right now, instead of waiting for the app's hourly WorkManager job or opening the app manually.

**Full implementation plan already written**: `/Users/app/.claude/plans/velvety-munching-dongarra.md` — read this in full before starting. Summary:
1. New D1 table `device_push_tokens` + `POST /api/device-tokens` (Android app registers its FCM token, MCP_API_KEY-gated).
2. New `src/lib/fcm.ts` in the Sian OS Worker: signs a JWT with a Firebase service-account key, exchanges for an OAuth token, calls FCM v1 API to send a silent data-only push.
3. New public `POST /api/sync-request` endpoint the dashboard button calls (no auth, consistent with the rest of the intentionally-public app).
4. New `SyncFirebaseMessagingService.kt` in this Android app: registers the FCM token on `onNewToken`, and on receiving the "sync_now" push, calls the existing `SyncScheduler.syncOnce(context)`.
5. Dashboard UI: sync icon on the Steps card in `sian-os/src/routes/_app/index.tsx`.

**Blocker — needs the owner to do this first, cannot be done by an agent alone:**
1. Create (or confirm) a Firebase project at console.firebase.google.com.
2. Add an Android app to it with package name exactly `com.sianalimalik.wearablesync`; download `google-services.json` into `sian-os-wearable-sync/app/` (gitignore it, never commit).
3. Firebase console → Project settings → Service accounts → "Generate new private key". This gives `project_id`, `client_email`, `private_key`.
4. Owner runs these themselves (never paste secret values into chat):
   ```bash
   cd /Users/app/side-projects/sian-os
   npx wrangler secret put FCM_PROJECT_ID
   npx wrangler secret put FCM_CLIENT_EMAIL
   npx wrangler secret put FCM_PRIVATE_KEY
   ```

The owner just installed the official Firebase CLI MCP server (`firebase-tools mcp`) via:
```bash
claude mcp add -s user firebase -- npx -y firebase-tools@latest mcp
```
**Check first thing in a new session whether Firebase tools are available** (search for "firebase" tools). If so, use it to create the project/Android app registration directly instead of walking the owner through the console by hand — but service-account private key generation may still require the owner to click through the console UI themselves, since that's a sensitive action Google may not expose via MCP.
