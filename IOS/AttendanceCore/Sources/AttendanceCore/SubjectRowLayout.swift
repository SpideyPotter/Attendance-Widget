import CoreGraphics
import Foundation

public enum SubjectRowLayout {
    public static let colonColumnWidthFactor: CGFloat = 0.45
    public static let monospacedCharacterWidthFactor: CGFloat = 0.62

    public static func label(for subject: Subject, aliasStore: SubjectAliasStore = .shared) -> String {
        aliasStore.displayLabel(for: subject)
    }

    public static func details(for subject: Subject) -> String {
        String(
            format: "%6.2f%% (%2d/%2d)",
            subject.percentage,
            subject.present,
            subject.total
        )
    }

    public static func labelColumnWidth(
        for subjects: [Subject],
        fontSize: CGFloat,
        aliasStore: SubjectAliasStore = .shared
    ) -> CGFloat {
        let maxLength = subjects.map { label(for: $0, aliasStore: aliasStore).count }.max() ?? 1
        return CGFloat(maxLength) * fontSize * monospacedCharacterWidthFactor
    }

    public static func colonColumnWidth(fontSize: CGFloat) -> CGFloat {
        fontSize * colonColumnWidthFactor
    }
}
