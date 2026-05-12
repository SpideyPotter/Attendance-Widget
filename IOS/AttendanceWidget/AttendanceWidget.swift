import AppIntents
import AttendanceCore
import SwiftUI
import WidgetKit

struct AttendanceEntry: TimelineEntry {
    let date: Date
    let snapshot: AttendanceSnapshot?
    let hasCredentials: Bool
}

struct AttendanceTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> AttendanceEntry {
        AttendanceEntry(date: Date(), snapshot: nil, hasCredentials: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (AttendanceEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AttendanceEntry>) -> Void) {
        let entry = makeEntry()
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date().addingTimeInterval(900)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func makeEntry() -> AttendanceEntry {
        let hasCredentials = UserDefaults(suiteName: AppGroup.identifier)?.bool(forKey: AppGroup.hasCredentialsKey) == true
            || CredentialStore().hasCredentials
        return AttendanceEntry(
            date: Date(),
            snapshot: SnapshotStore().load(),
            hasCredentials: hasCredentials
        )
    }
}

struct AttendanceWidgetEntryView: View {
    var entry: AttendanceEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        content
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(widgetBackground, for: .widget)
    }

    private var widgetBackground: Color {
        colorScheme == .dark ? .black : .white
    }

    private var widgetForeground: Color {
        colorScheme == .dark ? .white : .black
    }

    private var widgetSecondaryForeground: Color {
        colorScheme == .dark ? Color.white.opacity(0.65) : Color.black.opacity(0.55)
    }

    @ViewBuilder
    private var content: some View {
        if !entry.hasCredentials {
            emptyState(
                message: "Tap to set up Maitri credentials",
                intent: OpenSettingsIntent()
            )
        } else if let snapshot = entry.snapshot {
            populated(snapshot)
        } else {
            emptyState(
                message: "No attendance yet — tap to refresh",
                intent: RefreshAttendanceIntent()
            )
        }
    }

    @ViewBuilder
    private func populated(_ snapshot: AttendanceSnapshot) -> some View {
        VStack(alignment: .leading, spacing: family == .systemSmall ? 4 : 6) {
            overallHeader(snapshot)
            Divider()
                .overlay(widgetSecondaryForeground.opacity(0.35))
            subjectList(orderedSubjects(snapshot.subjects))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(widgetForeground)
    }

    private func orderedSubjects(_ subjects: [Subject]) -> [Subject] {
        guard family == .systemSmall else { return subjects }
        return subjects.sorted { lhs, rhs in
            if lhs.percentage != rhs.percentage {
                return lhs.percentage < rhs.percentage
            }
            return lhs.code.localizedCaseInsensitiveCompare(rhs.code) == .orderedAscending
        }
    }

    private func overallHeader(_ snapshot: AttendanceSnapshot) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Text(String(format: "%.2f%%", snapshot.overallPercentage))
                .font(.system(size: overallFontSize, weight: .semibold, design: .monospaced))
                .foregroundStyle(rowColor(for: snapshot.overallPercentage))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Button(intent: RefreshAttendanceIntent()) {
                Text("↻")
                    .font(.system(size: overallFontSize - 4, weight: .medium, design: .monospaced))
                    .foregroundStyle(widgetSecondaryForeground)
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
        }
    }

    private var overallFontSize: CGFloat {
        switch family {
        case .systemSmall:
            return 24
        case .systemMedium:
            return 28
        case .systemLarge, .systemExtraLarge:
            return 32
        default:
            return 28
        }
    }

    private func subjectList(_ subjects: [Subject]) -> some View {
        let limit = subjectLimit(for: family)
        let visible = Array(subjects.prefix(limit))
        let fontSize = rowFontSize
        let rowFont = Font.system(size: fontSize, weight: .regular, design: .monospaced)
        let labelWidth = SubjectRowLayout.labelColumnWidth(for: subjects, fontSize: fontSize)
        let colonWidth = SubjectRowLayout.colonColumnWidth(fontSize: fontSize)
        return VStack(alignment: .leading, spacing: rowSpacing) {
            ForEach(visible) { subject in
                SubjectAttendanceRow(
                    subject: subject,
                    labelColumnWidth: labelWidth,
                    colonColumnWidth: colonWidth,
                    font: rowFont,
                    color: rowColor(for: subject.percentage)
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if subjects.count > limit {
                Text("+\(subjects.count - limit) more")
                    .font(.system(size: fontSize - 2, weight: .regular, design: .monospaced))
                    .foregroundStyle(widgetSecondaryForeground)
            }
        }
    }

    private var rowFontSize: CGFloat {
        switch family {
        case .systemSmall:
            return 13
        case .systemMedium:
            return 16
        case .systemLarge:
            return 17
        case .systemExtraLarge:
            return 18
        default:
            return 16
        }
    }

    private var rowSpacing: CGFloat {
        switch family {
        case .systemSmall:
            return 3
        case .systemMedium:
            return 4
        case .systemLarge, .systemExtraLarge:
            return 5
        default:
            return 4
        }
    }

    private func subjectLimit(for family: WidgetFamily) -> Int {
        switch family {
        case .systemSmall:
            return 4
        case .systemMedium:
            return 8
        case .systemLarge:
            return 14
        case .systemExtraLarge:
            return 22
        default:
            return 8
        }
    }

    private func rowColor(for percentage: Double) -> Color {
        percentage < 70 ? .red : widgetForeground
    }

    private func emptyState<I: AppIntent>(message: String, intent: I) -> some View {
        Button(intent: intent) {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(widgetSecondaryForeground)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .buttonStyle(.plain)
    }
}

@main
struct AttendanceWidgetBundle: WidgetBundle {
    var body: some Widget {
        AttendanceWidget()
    }
}

struct AttendanceWidget: Widget {
    let kind = AttendanceWidgetKind.identifier

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AttendanceTimelineProvider()) { entry in
            AttendanceWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("BMU Attendance")
        .description("Shows your latest Maitri attendance snapshot.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
    }
}
