# BMU Attendance — iOS and Mac

Native iOS 17+ app with a WidgetKit home-screen widget and a SwiftUI settings screen. The same build runs on Apple Silicon Mac as an iOS app on Mac.

## Layout

```text
IOS/
├── README.md
├── BmuAttendance.xcodeproj
├── AttendanceCore/          Swift package: Maitri client, models, stores, repository
├── BmuAttendance/           Host app (settings + background refresh)
└── AttendanceWidget/        WidgetKit extension
```

The Maitri HTTP contract lives in [`../Android/FLOW.md`](../Android/FLOW.md). Validate changes with the Kotlin probe in [`../Android/probe/`](../Android/probe/).

## Open in Xcode

1. Open `IOS/BmuAttendance.xcodeproj`.
2. Select the `BmuAttendance` scheme and an iPhone, simulator, or **My Mac (Designed for iPad)**.
3. Run the app, enter your full BMU email and password, then add the widget from the home screen or desktop widget gallery.

## Mac without Xcode

Download the latest unsigned Mac build from [GitHub Releases](https://github.com/SpideyPotter/Attendance-Widget/releases). See the root [README](../README.md) for install notes.

## Command line

```bash
cd IOS/AttendanceCore
swift test

cd ../
xcodebuild -project BmuAttendance.xcodeproj -scheme BmuAttendance -destination 'generic/platform=iOS' build
```

## First run

1. The app opens login on first launch. Use your full Maitri email (`you@bmu.edu.in`).
2. Tap **Save and continue** to store credentials in Keychain and fetch attendance into the shared App Group snapshot.
3. Add the **BMU Attendance** widget.
4. Background refresh is best-effort via `BGAppRefreshTask` (about every fifteen minutes). The repository rate-limits network calls to once per ten minutes.

## Custom course labels

Open **Labels** from the home toolbar to choose short local abbreviations for each course. Labels stay on this device, survive attendance refresh, and apply in both the app and widget.

## Signing

The app and widget extension need the App Group `group.edu.bmu.attendance` on your development team before the widget can read the shared snapshot.

## Maintainer packaging

```bash
./scripts/package-macos-app.sh
```

Push a tag such as `v1.0.0` to trigger the release workflow that uploads the same zip to GitHub Releases.
