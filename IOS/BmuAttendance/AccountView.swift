import AttendanceCore
import SwiftUI

struct AccountView: View {
    @ObservedObject var viewModel: SettingsViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section {
                Text("Update your Maitri login or remove saved credentials from this device.")
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
                Button("Save and refresh") {
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

            Section {
                Button("Forget credentials", role: .destructive) {
                    Task {
                        await viewModel.clearCredentials()
                        dismiss()
                    }
                }
                .disabled(viewModel.status == .busy)
            }
        }
        .navigationTitle("Account")
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
