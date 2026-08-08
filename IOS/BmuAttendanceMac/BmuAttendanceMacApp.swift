import AttendanceCore
import SwiftUI

@main
struct BmuAttendanceMacApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var viewModel = SettingsViewModel()

    var body: some Scene {
        WindowGroup {
            AppRootView(viewModel: viewModel)
                .frame(minWidth: 420, minHeight: 520)
                .onOpenURL { url in
                    Task { await viewModel.handleOpenURL(url) }
                }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            guard newPhase == .active else { return }
            let fromBackground = oldPhase == .background
            Task { await viewModel.handleBecameActive(fromBackground: fromBackground) }
        }
        .commands {
            CommandGroup(replacing: .newItem) {}
        }
    }
}
