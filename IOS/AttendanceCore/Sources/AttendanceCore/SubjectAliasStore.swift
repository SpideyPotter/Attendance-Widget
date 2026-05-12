import Foundation

public final class SubjectAliasStore: @unchecked Sendable {
    public static let shared = SubjectAliasStore()
    public static let maxAliasLength = 12

    private struct Payload: Codable, Equatable {
        var aliases: [String: String]
    }

    private let fileManager: FileManager
    private let fileURL: URL?
    private let lock = NSLock()

    public init(fileManager: FileManager = .default, fileURL: URL? = nil) {
        self.fileManager = fileManager
        if let fileURL {
            self.fileURL = fileURL
        } else {
            self.fileURL = AppGroup.containerURL?.appendingPathComponent(AppGroup.subjectAliasesFileName)
        }
    }

    public func displayLabel(for subject: Subject) -> String {
        let aliases = loadPayload().aliases
        if let custom = aliases[subject.code]?.trimmingCharacters(in: .whitespacesAndNewlines), !custom.isEmpty {
            return custom
        }
        return Self.defaultLabel(for: subject)
    }

    public static func defaultLabel(for subject: Subject) -> String {
        subject.abbreviation.isEmpty ? subject.code : subject.abbreviation
    }

    public func alias(for code: String) -> String? {
        loadPayload().aliases[code]
    }

    public func allAliases() -> [String: String] {
        loadPayload().aliases
    }

    @discardableResult
    public func setAlias(code: String, label: String) throws -> Bool {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return try clearAlias(code: code)
        }
        guard trimmed.count <= Self.maxAliasLength else {
            throw SubjectAliasStoreError.labelTooLong
        }
        var payload = loadPayload()
        payload.aliases[code] = trimmed
        try savePayload(payload)
        return true
    }

    @discardableResult
    public func clearAlias(code: String) throws -> Bool {
        var payload = loadPayload()
        guard payload.aliases.removeValue(forKey: code) != nil else { return false }
        try savePayload(payload)
        return true
    }

    public func resetAll() throws {
        try savePayload(Payload(aliases: [:]))
    }

    private func migrateLegacyAliasFileIfNeeded() {
        guard let fileURL, !fileManager.fileExists(atPath: fileURL.path) else { return }
        guard let support = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return
        }
        let legacy = support.appendingPathComponent(AppGroup.subjectAliasesFileName)
        guard fileManager.fileExists(atPath: legacy.path) else { return }
        do {
            try fileManager.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try fileManager.copyItem(at: legacy, to: fileURL)
        } catch {
            return
        }
    }

    private func loadPayload() -> Payload {
        migrateLegacyAliasFileIfNeeded()
        lock.lock()
        defer { lock.unlock() }
        guard let fileURL, let data = try? Data(contentsOf: fileURL) else {
            return Payload(aliases: [:])
        }
        return (try? JSONDecoder().decode(Payload.self, from: data)) ?? Payload(aliases: [:])
    }

    private func savePayload(_ payload: Payload) throws {
        guard let fileURL else {
            throw SubjectAliasStoreError.storageUnavailable
        }
        let directory = fileURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(payload)
        try data.write(to: fileURL, options: .atomic)
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        var mutableURL = fileURL
        try mutableURL.setResourceValues(resourceValues)
    }
}

public enum SubjectAliasStoreError: Error, Equatable, Sendable {
    case labelTooLong
    case storageUnavailable
}
