import Foundation

enum SubjectAbbreviation {
    private static let romanNumeral = try! NSRegularExpression(
        pattern: "^(I{1,3}|IV|V)$",
        options: [.caseInsensitive]
    )

    static func make(from courseName: String) -> String {
        guard !courseName.isEmpty else { return "" }
        var output = ""
        for word in courseName.split(separator: " ", omittingEmptySubsequences: true) {
            let token = String(word)
            if token.contains("-") {
                let parts = token.split(separator: "-")
                    .compactMap { abbreviatePart(String($0).trimmingCharacters(in: .whitespaces)) }
                if !parts.isEmpty {
                    output += parts.joined(separator: "-")
                }
            } else if let part = abbreviatePart(token.trimmingCharacters(in: .whitespaces)) {
                output += part
            }
        }
        return output
    }

    private static func abbreviatePart(_ part: String) -> String? {
        guard !part.isEmpty else { return nil }
        let range = NSRange(part.startIndex..<part.endIndex, in: part)
        if romanNumeral.firstMatch(in: part, options: [], range: range) != nil {
            return part.uppercased()
        }
        guard let first = part.first, first.isUppercase else { return nil }
        return String(first)
    }
}
