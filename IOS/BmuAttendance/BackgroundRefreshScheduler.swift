import AttendanceCore
import BackgroundTasks
import Foundation
import WidgetKit

enum BackgroundRefreshScheduler {
    static let taskIdentifier = "edu.bmu.attendance.refresh"
    private static var isRegistered = false

    static func register() {
        guard !isRegistered else { return }
        isRegistered = BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refreshTask)
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // The system may reject duplicate submissions; ignore.
        }
    }

    private static func handle(_ task: BGAppRefreshTask) {
        schedule()
        let operation = Task {
            let repository = AttendanceRepository()
            let result = await repository.refresh()
            switch result {
            case .success:
                WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
                task.setTaskCompleted(success: true)
            case .failure(let error):
                WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
                if case .network = error {
                    task.setTaskCompleted(success: false)
                } else {
                    task.setTaskCompleted(success: true)
                }
            case .missingCredentials:
                WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
                task.setTaskCompleted(success: true)
            }
        }
        task.expirationHandler = {
            operation.cancel()
        }
    }
}
