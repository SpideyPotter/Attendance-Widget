import Foundation
import Security

public final class CredentialStore: Sendable {
    private let service = "edu.bmu.attendance.credentials"
    private let account = "maitri"
    private let fallbackFileName = "bmu_credentials.json"
    private let fileManager: FileManager

    public init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    public func load() -> Credentials? {
        if let data = loadKeychainData() ?? loadFallbackData() {
            return decodeCredentials(from: data)
        }
        return nil
    }

    public func save(_ creds: Credentials) throws {
        let payload = StoredCredentials(
            username: creds.username.trimmingCharacters(in: .whitespacesAndNewlines),
            password: creds.password
        )
        let data = try JSONEncoder().encode(payload)

        do {
            try saveToKeychain(data)
        } catch {
            try saveToFallbackFile(data)
        }
        syncCredentialFlag(hasCredentials: true)
    }

    public func clear() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let status = SecItemDelete(query as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            throw KeychainError.unhandled(status)
        }

        if let url = fallbackURL, fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        syncCredentialFlag(hasCredentials: false)
    }

    public var hasCredentials: Bool {
        load() != nil
    }

    private func decodeCredentials(from data: Data) -> Credentials? {
        guard let payload = try? JSONDecoder().decode(StoredCredentials.self, from: data) else { return nil }
        let creds = Credentials(username: payload.username, password: payload.password)
        guard !creds.username.isEmpty, !creds.password.isEmpty else { return nil }
        return creds
    }

    private func loadKeychainData() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    private func saveToKeychain(_ data: Data) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)

        var addQuery = query
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(addQuery as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.unhandled(status)
        }
    }

    private var fallbackURL: URL? {
        fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
            .appendingPathComponent(fallbackFileName)
    }

    private func loadFallbackData() -> Data? {
        guard let url = fallbackURL else { return nil }
        return try? Data(contentsOf: url)
    }

    private func saveToFallbackFile(_ data: Data) throws {
        guard let url = fallbackURL else {
            throw KeychainError.storageUnavailable
        }
        let directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }

    private func syncCredentialFlag(hasCredentials: Bool) {
        if let defaults = UserDefaults(suiteName: AppGroup.identifier) {
            defaults.set(hasCredentials, forKey: AppGroup.hasCredentialsKey)
        }
    }

    private struct StoredCredentials: Codable {
        let username: String
        let password: String
    }
}

public enum KeychainError: Error, Equatable, Sendable {
    case unhandled(OSStatus)
    case storageUnavailable

    public var localizedDescription: String {
        switch self {
        case .storageUnavailable:
            return "Secure storage is unavailable on this device."
        case .unhandled(let status):
            return "Keychain error \(status) (\(Self.message(for: status)))."
        }
    }

    private static func message(for status: OSStatus) -> String {
        if let message = SecCopyErrorMessageString(status, nil) as String? {
            return message
        }
        return "unknown"
    }
}
