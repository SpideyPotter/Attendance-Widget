# BMU Attendance

Attendance for BML Munjal University students from the Maitri portal, with a home-screen widget and a native app for iPhone and Apple Silicon Mac.

## What it does

- Fetches the current term from Maitri and shows overall attendance plus per-subject percentages.
- Stores a cached snapshot on device so the widget and app can read the latest data without opening the portal.
- Lets you set short local course labels that apply in the app and widget on that device only.
- Refreshes on demand from the app or widget, with background refresh on a best-effort schedule.
- Rate-limits network refresh to about once every ten minutes so repeated opens do not hammer the portal.

## Platforms

| Platform | Status | How to get it |
| --- | --- | --- |
| iPhone (iOS 17+) | Supported | Build from source in Xcode. See [IOS/README.md](IOS/README.md). |
| Apple Silicon Mac | Supported | Download the latest **macOS (Apple Silicon)** `.zip` from [GitHub Releases](https://github.com/SpideyPotter/Attendance-Widget/releases). This is a **native macOS** SwiftUI app (`BmuAttendanceMac`); the iPhone app and widget stay on iOS. |
| Android | Supported | Build from source in Android Studio. See [Android/README.md](Android/README.md). |
| CLI and SwiftBar | Legacy | See [docs/legacy-cli-swiftbar.md](docs/legacy-cli-swiftbar.md). |

GitHub Releases ship a **native macOS** `.app` inside the zip (bundle id `edu.bmu.attendance.mac`). iOS is not distributed as an installable package here; build the iPhone app from source in Xcode.

## Mac install from a release

1. Download `BmuAttendance-macOS-apple-silicon.zip` from [Releases](https://github.com/SpideyPotter/Attendance-Widget/releases).
2. Unzip and move `BmuAttendance.app` into Applications.
3. Open the app. If macOS blocks it (quarantine), use **Open** from the context menu, or run `xattr -dr com.apple.quarantine /Applications/BmuAttendance.app`.
4. Sign in with your full Maitri email and refresh attendance.

The **home-screen widget** ships with the **iOS** target only. On Mac you use the desktop app; use the iPhone build for the widget.

To package locally, run `./scripts/package-macos-app.sh` (scheme **BmuAttendanceMac**) with Xcode signed in on the **BmuAttendanceMac** target.

## iPhone install

Open `IOS/BmuAttendance.xcodeproj` in Xcode, enable the App Group `group.edu.bmu.attendance` on the app and widget targets, run on your device, then add the widget. Details are in [IOS/README.md](IOS/README.md).

## Repository layout

```text
IOS/                 Native app, widget, and AttendanceCore package
Android/             Native app and home-screen widgets (Kotlin / Compose)
docs/                Legacy CLI and SwiftBar documentation
MacOS/               SwiftBar plugin script
attendance.py        Legacy terminal script
.github/workflows/   CI and release automation
```

## Development

- Package tests: `cd IOS/AttendanceCore && swift test`
- iOS CI runs on pushes and pull requests that touch `IOS/`
- Android CI runs on pushes and pull requests that touch `Android/` (probe compile + Gradle assemble)
- Tag a release as `v*` (for example `v1.1.0`) to publish the unsigned Mac zip **and** Android APK to GitHub Releases. See [CHANGELOG.md](CHANGELOG.md).
- To add an Android APK to an **existing** release (for example `v1.0.0` created before Android was in the repo): **Actions → Release → Run workflow**, set tag `v1.0.0`, leave **android_only** checked

## Author

[Kota Ravindra Reddy](https://github.com/SpideyPotter)
