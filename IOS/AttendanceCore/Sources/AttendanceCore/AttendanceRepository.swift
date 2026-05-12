import Foundation

public final class AttendanceRepository: @unchecked Sendable {
    public static let defaultMinIntervalMillis: Int64 = 10 * 60 * 1000

    public enum RefreshResult: Equatable, Sendable {
        case success(AttendanceSnapshot, networkCallMade: Bool)
        case failure(MaitriError)
        case missingCredentials
    }

    private let credentialStore: CredentialStore
    private let snapshotStore: SnapshotStore
    private let client: MaitriClient
    private let minIntervalMillis: Int64
    private let now: () -> Int64

    public init(
        credentialStore: CredentialStore = CredentialStore(),
        snapshotStore: SnapshotStore = SnapshotStore(),
        client: MaitriClient = MaitriClient(),
        minIntervalMillis: Int64 = AttendanceRepository.defaultMinIntervalMillis,
        now: @escaping () -> Int64 = {
            Int64(Date().timeIntervalSince1970 * 1000)
        }
    ) {
        self.credentialStore = credentialStore
        self.snapshotStore = snapshotStore
        self.client = client
        self.minIntervalMillis = minIntervalMillis
        self.now = now
    }

    public var cachedSnapshot: AttendanceSnapshot? {
        snapshotStore.load()
    }

    public func refresh() async -> RefreshResult {
        if let cached = snapshotStore.load(),
           now() - cached.fetchedAtMillis < minIntervalMillis {
            return .success(cached, networkCallMade: false)
        }
        return await forceRefresh()
    }

    public func forceRefresh() async -> RefreshResult {
        guard let creds = credentialStore.load() else {
            return .missingCredentials
        }
        do {
            let fresh = try await client.fetchAttendance(creds: creds)
            try snapshotStore.save(fresh)
            return .success(fresh, networkCallMade: true)
        } catch let error as MaitriError {
            return .failure(error)
        } catch {
            return .failure(.portal(error.localizedDescription))
        }
    }

    public func testCredentials(_ creds: Credentials) async -> Result<AttendanceSnapshot, Error> {
        do {
            let snapshot = try await client.fetchAttendance(creds: creds)
            return .success(snapshot)
        } catch {
            return .failure(error)
        }
    }

    public func saveCredentials(_ creds: Credentials) throws {
        try credentialStore.save(creds)
    }

    public func clearCredentials() throws {
        try credentialStore.clear()
    }

    public var hasCredentials: Bool {
        credentialStore.hasCredentials
    }
}
