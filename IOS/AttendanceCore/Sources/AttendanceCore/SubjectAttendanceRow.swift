#if os(iOS)
import SwiftUI

public struct SubjectAttendanceRow: View {
    let label: String
    let details: String
    let labelColumnWidth: CGFloat
    let colonColumnWidth: CGFloat
    let font: Font
    let color: Color

    public init(
        subject: Subject,
        labelColumnWidth: CGFloat,
        colonColumnWidth: CGFloat? = nil,
        font: Font = .system(.subheadline, design: .monospaced),
        color: Color = .primary,
        aliasStore: SubjectAliasStore = .shared
    ) {
        self.label = SubjectRowLayout.label(for: subject, aliasStore: aliasStore)
        self.details = SubjectRowLayout.details(for: subject)
        self.labelColumnWidth = labelColumnWidth
        self.colonColumnWidth = colonColumnWidth ?? SubjectRowLayout.colonColumnWidth(
            fontSize: Self.defaultFontSize(for: font)
        )
        self.font = font
        self.color = color
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            Text(label)
                .frame(width: labelColumnWidth, alignment: .leading)
            Text(":")
                .frame(width: colonColumnWidth, alignment: .leading)
            Text(details)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .font(font)
        .foregroundStyle(color)
    }

    private static func defaultFontSize(for font: Font) -> CGFloat {
        switch font {
        case .body:
            return 17
        case .subheadline:
            return 15
        default:
            return 15
        }
    }
}
#endif
