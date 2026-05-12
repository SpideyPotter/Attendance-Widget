import Foundation

public final class SnapshotStore: @unchecked Sendable {
    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    public func load() -> AttendanceSnapshot? {
        for url in candidateURLs() {
            guard let data = try? Data(contentsOf: url) else { continue }
            if let snapshot = try? decoder.decode(AttendanceSnapshot.self, from: data) {
                return snapshot
            }
        }
        return nil
    }

    public func save(_ snapshot: AttendanceSnapshot) throws {
        var lastError: Error = SnapshotStoreError.missingContainer
        for url in candidateURLs() {
            do {
                try write(snapshot, to: url)
                return
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    public func clear() throws {
        for url in candidateURLs() where fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
    }

    private func candidateURLs() -> [URL] {
        var urls: [URL] = []
        if let groupURL = AppGroup.containerURL?.appendingPathComponent(AppGroup.snapshotFileName) {
            urls.append(groupURL)
        }
        if let appURL = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
            .appendingPathComponent(AppGroup.snapshotFileName) {
            urls.append(appURL)
        }
        return urls
    }

    private func write(_ snapshot: AttendanceSnapshot, to url: URL) throws {
        let directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try encoder.encode(snapshot)
        try data.write(to: url, options: .atomic)
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        var mutableURL = url
        try mutableURL.setResourceValues(resourceValues)
    }
}

public enum SnapshotStoreError: Error, Equatable, Sendable {
    case missingContainer

    public var localizedDescription: String {
        switch self {
        case .missingContainer:
            return "Shared storage is unavailable. Enable the App Group capability for the app and widget targets."
        }
    }
}
