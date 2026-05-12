import AttendanceCore
import SwiftUI

struct LabelsView: View {
    @ObservedObject var settingsViewModel: SettingsViewModel
    @StateObject private var viewModel = LabelsViewModel()

    var body: some View {
        List {
            Section {
                Text("Choose short labels for your courses. These apply in the app and widget on this device only.")
                    .foregroundStyle(.secondary)
            }

            if viewModel.rows.isEmpty {
                Section {
                    Text("Refresh attendance first to load subjects.")
                        .foregroundStyle(.secondary)
                }
            } else {
                Section("Courses") {
                    ForEach(viewModel.rows) { row in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(row.subject.name)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Default: \(row.defaultLabel)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("Current: \(row.currentLabel)")
                                .font(.caption)
                            TextField("Custom label", text: draftBinding(for: row.id))
                                .textInputAutocapitalization(.characters)
                                .autocorrectionDisabled()
                            HStack {
                                Button("Save") {
                                    viewModel.save(rowID: row.id)
                                }
                                Button("Reset to default") {
                                    viewModel.reset(rowID: row.id)
                                }
                            }
                            .buttonStyle(.borderless)
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    Button("Reset all custom labels", role: .destructive) {
                        viewModel.resetAll()
                    }
                }
            }

            Section {
                Text(viewModel.statusText)
                    .foregroundStyle(viewModel.statusIsError ? .red : .secondary)
                Text("Clearing your Maitri login does not remove custom labels.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Labels")
        .onAppear {
            viewModel.load(from: settingsViewModel.lastSnapshot)
        }
        .onChange(of: settingsViewModel.lastSnapshot) { _, snapshot in
            viewModel.load(from: snapshot)
        }
    }

    private func draftBinding(for rowID: String) -> Binding<String> {
        Binding(
            get: { viewModel.rows.first(where: { $0.id == rowID })?.draft ?? "" },
            set: { viewModel.updateDraft(rowID: rowID, draft: $0) }
        )
    }
}
