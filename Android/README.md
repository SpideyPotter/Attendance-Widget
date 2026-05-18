# BMU Attendance — Android

Native Android port of the attendance tracker, with a Jetpack **Glance**
home-screen widget and a Compose settings screen.

## Layout

```
Android/
├── README.md     ← you are here
├── FLOW.md       ← reverse-engineered Maitri HTTP contract (read this first)
├── probe/        ← standalone Kotlin/JVM Gradle probe used to verify the
│                  HTTP flow before any Android code was written
└── app/          ← Android Studio project (open this folder in Studio)
    ├── settings.gradle.kts
    ├── build.gradle.kts             ← top-level
    ├── gradle/libs.versions.toml    ← single source of truth for versions
    ├── gradlew, gradlew.bat
    └── app/                         ← the actual Android module
        ├── build.gradle.kts
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/                 ← strings, theme, widget info, icon
            └── kotlin/edu/bmu/attendance/
                ├── BmuAttendanceApp.kt
                ├── MainActivity.kt
                ├── data/    ← MaitriClient, Models, CredentialStore,
                │              SnapshotStore, AttendanceRepository
                ├── ui/      ← SettingsScreen + ViewModel + theme
                ├── widget/  ← AttendanceWidget (Glance) + receiver +
                │              RefreshAction click handler
                └── work/    ← RefreshWorker (periodic + one-shot)
```

## Architecture in one paragraph

The widget is a **read-only view of `SnapshotStore`**, never makes a network
call itself. All fetching happens in `RefreshWorker` (WorkManager), which calls
`AttendanceRepository.refresh()`, which in turn delegates to `MaitriClient`
to perform the four-step Maitri flow described in `FLOW.md`. On success, the
worker writes a fresh `AttendanceSnapshot` to `SnapshotStore` and asks Glance
to re-render. The Settings screen uses the same repository to test credentials
and force a refresh after save.

Credentials live in `EncryptedSharedPreferences` (Android Keystore-backed) and
are excluded from auto-backup via `data_extraction_rules.xml`.

## Prerequisites

- **Android Studio Hedgehog or newer** (we use AGP 8.10.0).
  Bundled JBR satisfies the JDK 17 toolchain requirement.
- **Android SDK with platform 36** (Android 16 / Baklava).
  `local.properties` points at `/Volumes/APPS/Library` by default — adjust if
  your SDK lives elsewhere.

## Building from the command line

```bash
cd Android/app
export JAVA_HOME="/Volumes/APPS/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Building from Android Studio

1. Open Android Studio.
2. **File → Open** → navigate to `Android/app` (NOT the outer `Android` folder).
3. Wait for Gradle sync.
4. Pick a device or AVD and hit Run.

## First run

1. The launcher icon opens **Settings**. Enter:
   - Username: **full email** (e.g. `you@bmu.edu.in`) — Maitri rejects the bare
     local part.
   - Password.
2. Tap **Save and refresh**. The status banner tells you whether the login and
   first attendance fetch worked.
3. Long-press the home screen → **Widgets** → **BMU Attendance** → drag onto
   the home screen.
4. The widget refreshes itself every ~15 minutes via WorkManager (constraints:
   network connected). The repository also rate-limits actual network calls to
   one per 10 minutes so multiple triggers don't hammer Maitri.

## Tips

- **Force a refresh now**: tap the ↻ button in the widget header, or hit
  *Save and refresh* in Settings.
- **Forget my credentials**: bottom button on the Settings screen.
- **Adjust the refresh cadence**: edit `RefreshWorker.schedulePeriodic()` and
  the rate limiter in `AttendanceRepository.DEFAULT_MIN_INTERVAL_MILLIS`.

## CI and releases

- **CI**: GitHub Actions workflow [`.github/workflows/android.yml`](../.github/workflows/android.yml) builds the probe and assembles debug + release on every push/PR that touches `Android/`.
- **Releases**: Tag the repo as `v*` (for example `v1.0.1`) to build both Mac zip and `BmuAttendance-android-1.0.1.apk`. Version in `app/build.gradle.kts` should match the tag (without the `v`). For an older release that only has the Mac zip, run **Actions → Release → Run workflow** with that tag and **android_only** enabled. Install the APK via `adb install` or Android Studio; you may need to allow installs from unknown sources.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| "Invalid username or password" right after a save | Username missing the `@bmu.edu.in` part | Re-enter as the full email |
| Widget shows "No attendance yet — tap to refresh" indefinitely | Periodic worker hasn't fired yet, or network was unavailable | Tap the widget; it queues a one-shot worker |
| Build error: "Gradle version too old" | New AGP version requires newer Gradle | Bump `gradle-wrapper.properties` per the error message |
| Build error: "platform-XX not found" | Missing SDK platform | SDK Manager → install the platform AGP requests |
