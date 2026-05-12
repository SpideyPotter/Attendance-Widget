import Foundation

public enum AppGroup {
    public static let identifier = "group.edu.bmu.attendance"
    public static let snapshotFileName = "bmu_snapshot.json"
    public static let subjectAliasesFileName = "bmu_subject_aliases.json"
    public static let pendingForceRefreshKey = "pendingForceRefresh"
    public static let hasCredentialsKey = "hasCredentials"

    public static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }
}
