import Foundation

/// Portal date labels for `stu_getBetweenDatesTimetableForStudentSP.json`.
/// Matches the gems date filter format (`Aug 2, 2026`), Sunday–Saturday weeks.
public struct TimetableDateRange: Equatable, Sendable {
    public let start: Date
    public let end: Date
    public let startLabel: String
    public let endLabel: String

    public init(start: Date, end: Date) {
        self.start = start
        self.end = end
        self.startLabel = Self.formatPortalDate(start)
        self.endLabel = Self.formatPortalDate(end)
    }

    /// Current week (Sunday…Saturday). Optional overrides replace either bound.
    public static func currentWeek(start: Date? = nil, end: Date? = nil, now: Date = Date()) -> TimetableDateRange {
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: now)
        let weekday = calendar.component(.weekday, from: today) // 1 = Sunday
        let sunday = calendar.date(byAdding: .day, value: -(weekday - 1), to: today) ?? today
        let saturday = calendar.date(byAdding: .day, value: 6, to: sunday) ?? sunday
        return TimetableDateRange(start: start ?? sunday, end: end ?? saturday)
    }

    /// Formats like the Maitri date filter: `Aug 2, 2026` (no zero-padded day).
    public static func formatPortalDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: date)
    }
}
