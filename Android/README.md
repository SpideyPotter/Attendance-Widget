# BMU Attendance — Android

Native Android app for BMU Maitri attendance **and weekly timetable**, with
Jetpack Glance home-screen widgets and a Compose UI (Today · Weekly · Attendance).

## What you get

- Sign in with your full Maitri email; credentials live in EncryptedSharedPreferences.
- **Today / Weekly / Attendance** tabs; venue overrides when Maitri leaves room as `-`.
- Home-screen widgets: full list, compact, and transparent overall %.
- Background refresh ~every 15 minutes (WorkManager; network required).
- PROJECT subjects are excluded from overall % (same as iOS).
- Optional morning notification listing today’s classes (Account → Notifications).

## Layout

```
Android/
├── README.md     ← you are here
├── FLOW.md       ← Maitri HTTP contract
├── probe/        ← JVM probe for the HTTP flow
└── app/          ← open THIS folder in Android Studio
```

## Share with peers (sideload APK)

From a machine with the Android SDK / Android Studio JDK:

```bash
cd Android/app
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Install with `adb install -r app/build/outputs/apk/release/app-release.apk`, or send the
APK and allow “Install unknown apps” on the phone.

**Signing:** release uses an upload keystore when configured (`keystore.properties` or
`ANDROID_UPLOAD_*` env vars). Otherwise it is **debug-signed** so the APK is always
installable for campus sharing — not for Play Store. To keep one installable identity
across rebuilds for peers, either always use the same debug keystore machine, or add a
dedicated upload keystore under `Android/app/keystore.properties` (gitignored).

Current version: **1.1.1** (`versionCode` 4).

## Building (debug)

```bash
cd Android/app
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio: **File → Open → `Android/app`**, sync, Run.

## First run

1. Open the app → enter full email (`you@bmu.edu.in`) + password → **Save and continue**.
   Credentials are only kept if login + attendance fetch succeed.
2. Use Today / Weekly / Attendance; set classroom venues by tapping TBA/room on Weekly.
3. Long-press home → Widgets → **Attendance**, **Attendance Compact**, or **Attendance %**.

## Architecture (short)

Widgets only read cached snapshots. Network work runs in `RefreshWorker` /
`AttendanceRepository` → `MaitriClient` (see `FLOW.md`). Timetable is fetched on the
same login as attendance; if the timetable call fails, attendance still updates and the
previous week cache is kept.

## CI and releases

- [`.github/workflows/android.yml`](../.github/workflows/android.yml) builds probe + debug/release APKs.
- Tag `v*` (e.g. `v1.1.0`) for the [Release workflow](../.github/workflows/release.yml).

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Invalid username/password | Use the full `@bmu.edu.in` email |
| Stuck on login after bad password | Expected — failed refresh does not unlock Home |
| Widget empty | Open the app once, or tap refresh on the widget |
| Secure storage unavailable | Rare OEM/Keystore issue; restart phone or update Android |
