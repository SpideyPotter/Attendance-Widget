# BMU Attendance — iOS and Mac

Native **iOS 17+** app with a WidgetKit home-screen widget, plus a separate **native macOS 14+** SwiftUI app that shares `AttendanceCore` with iOS.

## Layout

```text
IOS/
├── README.md
├── BmuAttendance.xcodeproj
├── AttendanceCore/          Swift package: Maitri client, models, stores, repository
├── BmuAttendance/           iOS host app (settings + BG refresh) + shared SwiftUI views
├── BmuAttendanceMac/        macOS-only entry + BackgroundRefresh stub
└── AttendanceWidget/        WidgetKit extension (iOS only)
```

The Maitri HTTP contract lives in [`../Android/FLOW.md`](../Android/FLOW.md). Validate changes with the Kotlin probe in [`../Android/probe/`](../Android/probe/).

## Open in Xcode

### iPhone / iPad

1. Open `IOS/BmuAttendance.xcodeproj`.
2. Select the **BmuAttendance** scheme and an iPhone or simulator.
3. Run the app, enter your full BMU email and password, then add the widget from the home screen widget gallery.

### Native Mac

1. Select the **BmuAttendanceMac** scheme and **My Mac**.
2. Run the app. Enable the App Group `group.edu.bmu.attendance` on the **BmuAttendanceMac** target (same as iOS) so snapshots and labels can use the shared container when you use both apps.

## Mac without Xcode

Download the latest Mac build from [GitHub Releases](https://github.com/SpideyPotter/Attendance-Widget/releases). The zip contains a standard **macOS** `BmuAttendance.app` (not an iOS-on-Mac wrapper). To package locally, use `./scripts/package-macos-app.sh` after Xcode is signed in on the **BmuAttendanceMac** target. See the root [README](../README.md) for install notes.

## Command line

```bash
cd IOS/AttendanceCore
swift test

cd ../
xcodebuild -project BmuAttendance.xcodeproj -scheme BmuAttendance -destination 'generic/platform=iOS' build
xcodebuild -project BmuAttendance.xcodeproj -scheme BmuAttendanceMac -destination 'generic/platform=macOS' build
```

## First run

1. The app opens login on first launch. Use your full Maitri email (`you@bmu.edu.in`).
2. Tap **Save and continue** to store credentials in Keychain and fetch attendance into the shared App Group snapshot.
3. **iOS:** add the **BMU Attendance** widget from the widget gallery.
4. **iOS:** background refresh is best-effort via `BGAppRefreshTask` (about every fifteen minutes). **Mac:** there is no `BGAppRefreshTask`; refresh from the window or when the app becomes active. The repository rate-limits network calls to once per ten minutes.

## Custom course labels

Open **Labels** from the home toolbar to choose short local abbreviations for each course. Labels stay on this device, survive attendance refresh, and apply in both the app and widget (**widget is iOS-only**).

## Signing

Sign in to Xcode with your Apple ID and select your personal team on the **BmuAttendance**, **AttendanceWidget**, and **BmuAttendanceMac** targets. The App Group `group.edu.bmu.attendance` must be enabled on **BmuAttendance** and **AttendanceWidget** for the widget; enable it on **BmuAttendanceMac** as well if you use the Mac app.

GitHub Actions release builds use automatic signing with `-allowProvisioningUpdates` on the Mac scheme (CI must be able to create a Mac provisioning profile for `edu.bmu.attendance.mac`).

## Maintainer packaging

```bash
./scripts/package-macos-app.sh
```

Push a tag such as `v1.0.0` to trigger the release workflow that uploads the Mac zip and Android APK to GitHub Releases.
