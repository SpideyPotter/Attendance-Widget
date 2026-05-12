import AttendanceCore
import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if let snapshot = viewModel.lastSnapshot {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .center, spacing: 10) {
                                Text(String(format: "%.2f%%", snapshot.overallPercentage))
                                    .font(.system(size: 40, weight: .semibold, design: .monospaced))
                                    .foregroundStyle(color(for: snapshot.overallPercentage))
                                Button {
                                    Task { await viewModel.refreshFromHome() }
                                } label: {
                                    Text("↻")
                                        .font(.system(size: 28, weight: .medium, design: .monospaced))
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.borderless)
                                .disabled(viewModel.status == .busy)
                                Spacer(minLength: 0)
                            }
                            Text(
                                "\(snapshot.totalPresent) / \(snapshot.totalDelivered) lectures · " +
                                "\(snapshot.termName) · \(RelativeTimeFormatter.label(for: snapshot.fetchedAtMillis))"
                            )
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    } else {
                        Text("No attendance cached yet. Refresh to fetch your latest Maitri snapshot.")
                            .foregroundStyle(.secondary)
                    }
                }

                if let snapshot = viewModel.lastSnapshot {
                    Section("Subjects") {
                        VStack(alignment: .leading, spacing: 8) {
                            let labelWidth = SubjectRowLayout.labelColumnWidth(
                                for: snapshot.subjects,
                                fontSize: 17
                            )
                            let colonWidth = SubjectRowLayout.colonColumnWidth(fontSize: 17)
                            ForEach(snapshot.subjects) { subject in
                                SubjectAttendanceRow(
                                    subject: subject,
                                    labelColumnWidth: labelWidth,
                                    colonColumnWidth: colonWidth,
                                    font: .system(.body, design: .monospaced),
                                    color: color(for: subject.percentage)
                                )
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    Button("Refresh attendance") {
                        Task { await viewModel.refreshFromHome() }
                    }
                    .disabled(viewModel.status == .busy)
                }

                Section("Status") {
                    Text(viewModel.statusText)
                        .foregroundStyle(statusColor)
                }
            }
            .navigationTitle("BMU Attendance")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        LabelsView(settingsViewModel: viewModel)
                    } label: {
                        Image(systemName: "textformat.abc")
                    }
                    .accessibilityLabel("Labels")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        AccountView(viewModel: viewModel)
                    } label: {
                        Image(systemName: "person.crop.circle")
                    }
                    .accessibilityLabel("Account")
                }
            }
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

    private func color(for percentage: Double) -> Color {
        percentage < 75 ? .red : .primary
    }
}

private enum RelativeTimeFormatter {
    static func label(for millis: Int64) -> String {
        guard millis > 0 else { return "—" }
        let delta = Int64(Date().timeIntervalSince1970 * 1000) - millis
        if delta < 60_000 { return "just now" }
        if delta < 3_600_000 { return "\(delta / 60_000)m ago" }
        if delta < 86_400_000 { return "\(delta / 3_600_000)h ago" }
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
}
