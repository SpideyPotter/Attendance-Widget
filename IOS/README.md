# BMU Attendance — iOS

Native iOS 17+ port of the attendance tracker with a WidgetKit home-screen widget and a SwiftUI settings screen.

## Layout

```text
iOS/
├── README.md
├── BmuAttendance.xcodeproj
├── AttendanceCore/          Swift package: Maitri client, models, stores, repository
├── BmuAttendance/           Host app (settings + background refresh)
└── AttendanceWidget/        WidgetKit extension
```

The Maitri HTTP contract lives in [`../Android/FLOW.md`](../Android/FLOW.md). Validate changes with the Kotlin probe in [`../Android/probe/`](../Android/probe/).

## Open in Xcode

1. Open `iOS/BmuAttendance.xcodeproj`.
2. Select the `BmuAttendance` scheme and a device or simulator.
3. Run the app, enter your full BMU email and password, then add the widget from the home screen.

## Command line

```bash
cd iOS/AttendanceCore
swift test

cd ../
xcodebuild -project BmuAttendance.xcodeproj -scheme BmuAttendance -destination 'generic/platform=iOS' build
```

## First run

1. The app icon opens **Settings**. Use your full Maitri email (`you@bmu.edu.in`).
2. Tap **Save and refresh** to store credentials in Keychain and fetch attendance into the shared App Group snapshot.
3. Add the **BMU Attendance** widget to the home screen.
4. Background refresh is best-effort via `BGAppRefreshTask` (~15 minutes). The repository rate-limits network calls to once per 10 minutes.

## Custom course labels

Open **Labels** from the home screen toolbar to choose short local abbreviations for each course. Labels stay on this device, survive attendance refresh, and apply in both the app and widget.

## Signing

Both the app and widget extension need the App Group `group.edu.bmu.attendance` on your development team before the widget can read the shared snapshot.
