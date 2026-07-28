import Foundation
import Security

/// The one system-of-record store for the SSH identity shared by the app and keyboard.
///
/// Both targets carry `$(AppIdentifierPrefix)com.keyjawn.shared` in their signed
/// `keychain-access-groups` entitlement and expose the expanded value through the
/// `KeychainAccessGroup` Info.plist key. Every new item is written directly into that
/// group with `WhenUnlockedThisDeviceOnly` protection.
public enum SSHIdentityKeyStore {
    private static let service = "com.keyjawn"
    private static let account = "ssh-identity-ed25519"
    private static let sharedAccessGroupInfoKey = "KeychainAccessGroup"
    private static let legacyAccessGroupInfoKey = "LegacyKeychainAccessGroup"

    // These names are read-only migration compatibility for builds before #68.
    private static let legacyFileName = "ssh-identity-ed25519.key"
    private static let legacyDefaultsKey = "keyjawn.ssh-identity-raw"

    /// Reads only the shared Keychain item. The keyboard extension deliberately does
    /// not migrate the old file mirror: only the main app can compare that copy with
    /// the authoritative legacy Keychain item before choosing which bytes to preserve.
    public static func identityKeyData() -> Data? {
        guard let sharedAccessGroup else { return nil }
        return keychainData(accessGroup: sharedAccessGroup)
    }

    /// Gives the extension a seamless upgrade path when iOS launches it before
    /// the containing app has had a chance to migrate the legacy mirror.
    ///
    /// The shared item always wins. The old protected App Group copy is read-only
    /// fallback data and disappears after the app verifies the shared write.
    public static func extensionIdentityKeyData() -> Data? {
        SSHIdentityKeyMigration.extensionReadableKey(
            shared: identityKeyData(),
            legacyMirror: legacyIdentityKeyData()
        )
    }

    /// Moves an existing install to the shared Keychain item without rotating its key.
    ///
    /// The app-specific Keychain item wins over the former App Group file if both are
    /// present. Legacy copies are deleted only after the shared write can be read back
    /// byte-for-byte.
    public static func migrateLegacyIdentityKeyData() -> Data? {
        guard let sharedAccessGroup,
              let legacyAccessGroup
        else { return nil }
        return SSHIdentityKeyMigration.resolve(
            loadShared: { keychainData(accessGroup: sharedAccessGroup) },
            loadLegacyKeychain: { keychainData(accessGroup: legacyAccessGroup) },
            loadLegacyFile: { legacyIdentityKeyData() },
            storeShared: { storeShared($0, accessGroup: sharedAccessGroup) },
            deleteLegacyKeychain: {
                deleteKeychainItem(accessGroup: legacyAccessGroup)
            },
            deleteLegacyFile: { deleteLegacyArtifacts() }
        )
    }

    /// Stores new key bytes in the shared Keychain group and verifies the write before
    /// clearing any migration artifacts.
    @discardableResult
    public static func store(identityKeyData data: Data) -> Bool {
        guard !data.isEmpty,
              let sharedAccessGroup,
              let legacyAccessGroup,
              storeShared(data, accessGroup: sharedAccessGroup),
              keychainData(accessGroup: sharedAccessGroup) == data
        else { return false }

        deleteKeychainItem(accessGroup: legacyAccessGroup)
        deleteLegacyArtifacts()
        return true
    }

    /// Removes every location used by current and pre-migration builds.
    public static func deleteIdentityKey() {
        if let sharedAccessGroup {
            deleteKeychainItem(accessGroup: sharedAccessGroup)
        }
        if let legacyAccessGroup {
            deleteKeychainItem(accessGroup: legacyAccessGroup)
        }
        deleteLegacyArtifacts()
    }

    private static var sharedAccessGroup: String? {
        configuredAccessGroup(infoKey: sharedAccessGroupInfoKey)
    }

    private static var legacyAccessGroup: String? {
        configuredAccessGroup(infoKey: legacyAccessGroupInfoKey)
    }

    private static func configuredAccessGroup(infoKey: String) -> String? {
        guard let value = Bundle.main.object(
            forInfoDictionaryKey: infoKey
        ) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func keychainQuery(accessGroup: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: accessGroup,
        ]
    }

    private static func keychainData(accessGroup: String) -> Data? {
        var query = keychainQuery(accessGroup: accessGroup)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              !data.isEmpty
        else { return nil }
        return data
    }

    private static func storeShared(_ data: Data, accessGroup: String) -> Bool {
        let query = keychainQuery(accessGroup: accessGroup)
        let values: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]

        let updateStatus = SecItemUpdate(query as CFDictionary, values as CFDictionary)
        if updateStatus == errSecSuccess {
            return true
        }
        guard updateStatus == errSecItemNotFound else { return false }

        var attributes = query
        attributes.merge(values) { _, new in new }
        return SecItemAdd(attributes as CFDictionary, nil) == errSecSuccess
    }

    private static func deleteKeychainItem(accessGroup: String) {
        SecItemDelete(keychainQuery(accessGroup: accessGroup) as CFDictionary)
    }

    private static var legacyFileURL: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: AppGroupConfig.suiteName)?
            .appendingPathComponent(legacyFileName, isDirectory: false)
    }

    private static func legacyIdentityKeyData() -> Data? {
        if let url = legacyFileURL,
           let data = try? Data(contentsOf: url),
           !data.isEmpty {
            return data
        }
        guard let data = UserDefaults(suiteName: AppGroupConfig.suiteName)?
            .data(forKey: legacyDefaultsKey),
              !data.isEmpty
        else { return nil }
        return data
    }

    private static func deleteLegacyArtifacts() {
        if let url = legacyFileURL {
            try? FileManager.default.removeItem(at: url)
        }
        UserDefaults(suiteName: AppGroupConfig.suiteName)?
            .removeObject(forKey: legacyDefaultsKey)
    }
}

/// Pure migration ordering and durability policy, separated from Security.framework
/// so key precedence and cleanup behavior can be regression-tested.
enum SSHIdentityKeyMigration {
    static func extensionReadableKey(shared: Data?, legacyMirror: Data?) -> Data? {
        nonempty(shared) ?? nonempty(legacyMirror)
    }

    static func resolve(
        loadShared: () -> Data?,
        loadLegacyKeychain: () -> Data?,
        loadLegacyFile: () -> Data?,
        storeShared: (Data) -> Bool,
        deleteLegacyKeychain: () -> Void,
        deleteLegacyFile: () -> Void
    ) -> Data? {
        if let shared = nonempty(loadShared()) {
            deleteLegacyKeychain()
            deleteLegacyFile()
            return shared
        }

        guard let legacy = nonempty(loadLegacyKeychain())
            ?? nonempty(loadLegacyFile()),
              storeShared(legacy),
              nonempty(loadShared()) == legacy
        else { return nil }

        deleteLegacyKeychain()
        deleteLegacyFile()
        return legacy
    }

    private static func nonempty(_ data: Data?) -> Data? {
        guard let data, !data.isEmpty else { return nil }
        return data
    }
}
