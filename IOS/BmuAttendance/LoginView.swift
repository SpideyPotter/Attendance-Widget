import AttendanceCore
import SwiftUI

struct LoginView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Sign in with your full Maitri email to fetch attendance for the home-screen widget.")
                        .foregroundStyle(.secondary)
                }

                Section("Maitri credentials") {
                    TextField("Full email", text: $viewModel.username)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    HStack {
                        if viewModel.showPassword {
                            TextField("Password", text: $viewModel.password)
                                .textContentType(.password)
                        } else {
                            SecureField("Password", text: $viewModel.password)
                                .textContentType(.password)
                        }
                        Button(viewModel.showPassword ? "Hide" : "Show") {
                            viewModel.showPassword.toggle()
                        }
                        .buttonStyle(.borderless)
                    }
                }

                Section {
                    Button("Save and continue") {
                        Task { await viewModel.saveAndRefresh() }
                    }
                    .disabled(!viewModel.username.contains("@") || viewModel.password.isEmpty || viewModel.status == .busy)

                    Button("Test login") {
                        Task { await viewModel.testCredentials() }
                    }
                    .disabled(viewModel.username.isEmpty || viewModel.password.isEmpty || viewModel.status == .busy)
                }

                Section("Status") {
                    Text(viewModel.statusText)
                        .foregroundStyle(statusColor)
                }
            }
            .navigationTitle("Set up")
        }
    }

    private var statusColor: Color {
        switch viewModel.status {
        case .ok:
            return .green
        case .failed:
            return .red
        default:
            return .primary
        }
    }
}
