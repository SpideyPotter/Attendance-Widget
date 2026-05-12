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
| Apple Silicon Mac | Supported | Download the latest **macOS (Apple Silicon)** `.zip` from [GitHub Releases](https://github.com/SpideyPotter/Attendance-Widget/releases). The app is the same iOS build running on Mac. |
| Android | In progress | Not published in this repo yet. |
| CLI and SwiftBar | Legacy | See [docs/legacy-cli-swiftbar.md](docs/legacy-cli-swiftbar.md). |

GitHub Releases ship an unsigned Mac zip built in CI without your signing credentials. Builds from Xcode on your Mac can use your Apple ID personal team for local install on your devices. iOS is not distributed as an installable package here; build from source in Xcode.

## Mac install from a release

1. Download `BmuAttendance-macOS-apple-silicon.zip` from [Releases](https://github.com/SpideyPotter/Attendance-Widget/releases).
2. Unzip and move `BmuAttendance.app` into Applications.
3. CI-built releases are unsigned. macOS may block them on first open; use **Open** from the context menu, or run `xattr -dr com.apple.quarantine /Applications/BmuAttendance.app`.
4. Sign in with your full Maitri email, refresh attendance, then add the **BMU Attendance** widget from the widget gallery.

For a build signed with your Apple ID on your Mac, run `./scripts/package-macos-app.sh` in Xcode with your team selected on the app and widget targets.

## iPhone install

Open `IOS/BmuAttendance.xcodeproj` in Xcode, enable the App Group `group.edu.bmu.attendance` on the app and widget targets, run on your device, then add the widget. Details are in [IOS/README.md](IOS/README.md).

## Repository layout

```text
IOS/                 Native app, widget, and AttendanceCore package
docs/                Legacy CLI and SwiftBar documentation
MacOS/               SwiftBar plugin script
attendance.py        Legacy terminal script
.github/workflows/   CI and release automation
```

## Development

- Package tests: `cd IOS/AttendanceCore && swift test`
- iOS CI runs on pushes and pull requests that touch `IOS/`
- Tag a release as `v*` (for example `v1.0.0`) to build and upload the Mac app zip to GitHub Releases

## Author

[Kota Ravindra Reddy](https://github.com/SpideyPotter)
