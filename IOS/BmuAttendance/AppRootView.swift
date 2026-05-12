import AttendanceCore
import SwiftUI

struct AppRootView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        Group {
            if viewModel.hasSavedCredentials {
                HomeView(viewModel: viewModel)
            } else {
                LoginView(viewModel: viewModel)
            }
        }
    }
}
