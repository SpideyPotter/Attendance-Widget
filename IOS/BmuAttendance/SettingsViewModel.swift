import AttendanceCore
import Foundation
import WidgetKit

@MainActor
final class SettingsViewModel: ObservableObject {
    enum Status: Equatable {
        case idle
        case busy
        case ok
        case failed
    }

    @Published var username = ""
    @Published var password = ""
    @Published var showPassword = false
    @Published var status: Status = .idle
    @Published var statusText = "Enter your Maitri credentials to refresh the widget."
    @Published var lastSnapshot: AttendanceSnapshot?
    @Published private(set) var hasSavedCredentials = false

    private let repository = AttendanceRepository()

    init() {
        loadInitial()
    }

    func loadInitial() {
        let store = CredentialStore()
        hasSavedCredentials = store.hasCredentials
        if let creds = store.load() {
            username = creds.username
            password = creds.password
        }
        lastSnapshot = repository.cachedSnapshot
        if hasSavedCredentials, lastSnapshot != nil {
            status = .ok
            statusText = "Attendance is ready. Refresh anytime from the home screen."
        }
    }

    func saveAndRefresh() async {
        let creds = Credentials(username: username.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
        guard creds.isValid else {
            status = .failed
            statusText = "Username must be the full email and password can't be empty."
            return
        }
        status = .busy
        statusText = "Saving and refreshing…"
        do {
            try repository.saveCredentials(creds)
        } catch let error as KeychainError {
            status = .failed
            statusText = error.localizedDescription
            return
        } catch {
            status = .failed
            statusText = "Could not save credentials securely."
            return
        }
        await performRefresh(force: true, successPrefix: "Saved")
        BackgroundRefreshScheduler.schedule()
    }

    func refreshFromHome() async {
        status = .busy
        statusText = "Refreshing…"
        await performRefresh(force: true, successPrefix: "Updated")
    }

    func testCredentials() async {
        status = .busy
        statusText = "Testing…"
        let creds = Credentials(username: username.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
        let result = await repository.testCredentials(creds)
        switch result {
        case .success(let snapshot):
            status = .ok
            statusText = "Login OK · \(snapshot.termName) · \(snapshot.subjects.count) subjects · " +
                String(format: "%.2f%%", snapshot.overallPercentage)
        case .failure(let error):
            status = .failed
            statusText = humanize(error)
        }
    }

    func clearCredentials() async {
        do {
            try repository.clearCredentials()
            try SnapshotStore().clear()
            username = ""
            password = ""
            hasSavedCredentials = false
            status = .idle
            statusText = "Credentials cleared."
            lastSnapshot = nil
            WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
        } catch {
            status = .failed
            statusText = "Could not clear credentials."
        }
    }

    func handleOpenURL(_ url: URL) async {
        guard url.scheme == "bmuattendance" else { return }
        if url.host == "refresh" {
            await performRefresh(force: true)
        }
    }

    func handleBecameActive(fromBackground: Bool = false) async {
        if !fromBackground {
            WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
            return
        }
        if UserDefaults(suiteName: AppGroup.identifier)?.bool(forKey: AppGroup.pendingForceRefreshKey) == true {
            UserDefaults(suiteName: AppGroup.identifier)?.set(false, forKey: AppGroup.pendingForceRefreshKey)
            await performRefresh(force: true)
            return
        }
        if let cached = repository.cachedSnapshot {
            let age = Int64(Date().timeIntervalSince1970 * 1000) - cached.fetchedAtMillis
            if age >= AttendanceRepository.defaultMinIntervalMillis {
                await performRefresh(force: false)
            }
        }
        WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
    }

    func performRefresh(force: Bool, successPrefix: String = "Saved") async {
        let result = force ? await repository.forceRefresh() : await repository.refresh()
        switch result {
        case .success(let snapshot, _):
            status = .ok
            hasSavedCredentials = true
            statusText = "\(successPrefix) · \(snapshot.termName) · \(snapshot.subjects.count) subjects · " +
                String(format: "%.2f%%", snapshot.overallPercentage)
            lastSnapshot = snapshot
            WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
        case .failure(let error):
            status = .failed
            statusText = humanize(error)
            WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
        case .missingCredentials:
            status = .failed
            statusText = "No credentials saved."
        }
    }

    private func humanize(_ error: Error) -> String {
        if let error = error as? KeychainError {
            return error.localizedDescription
        }
        if let error = error as? SnapshotStoreError {
            return error.localizedDescription
        }
        guard let error = error as? MaitriError else {
            return error.localizedDescription
        }
        switch error {
        case .invalidCredentials:
            return "Invalid username or password."
        case .usernameNotEmail:
            return "Username must be the full email (e.g. you@bmu.edu.in)."
        case .noTerms:
            return "Maitri returned no enrolled terms."
        case .network(let message):
            return "Network error: \(message)"
        case .portal(let message):
            return "Portal error: \(message)"
        }
    }
}
