import AttendanceCore
import Foundation
import WidgetKit

/// macOS does not support `BGAppRefreshTask`. Widget reloads happen on explicit refresh and window activation.
enum BackgroundRefreshScheduler {
    static let taskIdentifier = "edu.bmu.attendance.refresh"

    static func register() {}

    static func schedule() {
        WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
    }
}
