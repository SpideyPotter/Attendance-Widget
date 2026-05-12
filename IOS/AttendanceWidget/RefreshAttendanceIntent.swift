import AppIntents
import AttendanceCore
import Foundation

struct RefreshAttendanceIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh attendance"
    static var openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        UserDefaults(suiteName: AppGroup.identifier)?.set(true, forKey: AppGroup.pendingForceRefreshKey)
        return .result()
    }
}

struct OpenSettingsIntent: AppIntent {
    static var title: LocalizedStringResource = "Open settings"
    static var openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}
