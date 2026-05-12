import SwiftUI
import AttendanceCore

@main
struct BmuAttendanceApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel = SettingsViewModel()

    var body: some Scene {
        WindowGroup {
            AppRootView(viewModel: viewModel)
                .onOpenURL { url in
                    Task { await viewModel.handleOpenURL(url) }
                }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            guard newPhase == .active else { return }
            let fromBackground = oldPhase == .background
            Task { await viewModel.handleBecameActive(fromBackground: fromBackground) }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BackgroundRefreshScheduler.register()
        BackgroundRefreshScheduler.schedule()
        return true
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }
}
