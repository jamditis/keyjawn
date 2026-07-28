import Foundation
import Security
import KeyJawnKit

/// Persists the SSH host list across app launches using the Keychain.
///
/// Host configs include sensitive metadata (hostnames, usernames, pinned host keys),
/// so they live in the Keychain rather than UserDefaults. On first launch after
/// upgrading from the UserDefaults version, data is migrated automatically and
/// removed from UserDefaults.
@MainActor
final class HostStore: ObservableObject {

    @Published private(set) var hosts: [HostConfig] = []

    private let service = "com.keyjawn"
    private let account = "keyjawn.hosts"
    private let legacyDefaultsKey = "keyjawn.hosts"

    init() {
        load()
    }

    // MARK: - Mutations

    func add(_ host: HostConfig) {
        hosts.append(host)
        save()
    }

    func delete(at offsets: IndexSet) {
        hosts.remove(atOffsets: offsets)
        save()
    }

    @discardableResult
    func update(_ host: HostConfig) -> Bool {
        guard let index = hosts.firstIndex(where: { $0.id == host.id }) else { return false }
        let previousHost = hosts[index]
        hosts[index] = host
        guard save() else {
            hosts[index] = previousHost
            return false
        }
        return true
    }

    // MARK: - Persistence

    private func load() {
        // Try Keychain first.
        if let data = keychainLoad(),
           let decoded = try? JSONDecoder().decode([HostConfig].self, from: data) {
            hosts = decoded
            return
        }

        // Migrate from UserDefaults if present (one-time upgrade path).
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: legacyDefaultsKey),
           let decoded = try? JSONDecoder().decode([HostConfig].self, from: data) {
            hosts = decoded
            save()
            defaults.removeObject(forKey: legacyDefaultsKey)
        }
    }

    @discardableResult
    private func save() -> Bool {
        guard let data = try? JSONEncoder().encode(hosts),
              keychainSave(data) else { return false }
        // Mirror to App Group so the keyboard extension can read host configs.
        UserDefaults(suiteName: AppGroupConfig.suiteName)?.set(data, forKey: account)
        return true
    }

    // MARK: - Keychain

    private var keychainQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func keychainLoad() -> Data? {
        var query = keychainQuery
        query[kSecReturnData as String] = true
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    private func keychainSave(_ data: Data) -> Bool {
        var addAttrs = keychainQuery
        addAttrs[kSecValueData as String] = data
        addAttrs[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        let status = SecItemAdd(addAttrs as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let update: [String: Any] = [kSecValueData as String: data]
            return SecItemUpdate(keychainQuery as CFDictionary, update as CFDictionary) == errSecSuccess
        }
        return status == errSecSuccess
    }
}
