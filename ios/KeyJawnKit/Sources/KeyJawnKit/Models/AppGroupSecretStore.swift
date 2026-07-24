import Foundation

/// The SSH identity key as the keyboard extension sees it.
///
/// The extension needs the raw private key bytes to authenticate an SFTP upload, and
/// the Keychain item the main app holds is not reachable from the extension's process
/// without a shared keychain access group. The bridge is therefore the App Group
/// container — but *how* it crosses matters.
///
/// It used to cross as a plain `UserDefaults` value in the shared suite. That suite is
/// a property list inside the group container, and group containers are included in
/// device backups, so the raw Ed25519 private key was copied into every iTunes and
/// iCloud backup — defeating the `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` class
/// deliberately chosen for the Keychain copy, which exists precisely to keep the key
/// on the device it was generated on.
///
/// It now crosses as a file with `.completeFileProtectionUntilUserAuthentication` and
/// `isExcludedFromBackup`, which restores that property: the bytes stay encrypted at
/// rest until first unlock and never leave the device in a backup. The Keychain
/// remains the system of record; this is a mirror the extension can read.
public enum AppGroupSecretStore {

    private static let fileName = "ssh-identity-ed25519.key"
    private static let legacyDefaultsKey = "keyjawn.ssh-identity-raw"

    private static var fileURL: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: AppGroupConfig.suiteName)?
            .appendingPathComponent(fileName, isDirectory: false)
    }

    // MARK: - Read

    /// The mirrored private key bytes, or nil when none has been written yet — which
    /// is also what an extension without Full Access sees, since the group container
    /// is closed to it entirely.
    public static func identityKeyData() -> Data? {
        if let url = fileURL, let data = try? Data(contentsOf: url), !data.isEmpty {
            return data
        }
        return migrateLegacyValue()
    }

    /// Moves a key left behind by the `UserDefaults` mirror into the protected file
    /// and clears the old copy, so an existing install stops carrying the key in its
    /// backups without the user having to regenerate and redistribute a new one.
    ///
    /// Runs from whichever process reads first. Both have write access to the group
    /// container, and the write is atomic, so a concurrent migration is a redundant
    /// write of identical bytes rather than a race.
    private static func migrateLegacyValue() -> Data? {
        guard let defaults = UserDefaults(suiteName: AppGroupConfig.suiteName),
              let legacy = defaults.data(forKey: legacyDefaultsKey),
              !legacy.isEmpty
        else { return nil }

        write(identityKeyData: legacy)
        defaults.removeObject(forKey: legacyDefaultsKey)
        return legacy
    }

    // MARK: - Write

    public static func write(identityKeyData data: Data) {
        guard var url = fileURL else { return }
        do {
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilUserAuthentication])
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? url.setResourceValues(values)
        } catch {
            // A failed mirror is not fatal: the main app still holds the key in the
            // Keychain and the terminal keeps working. Only the extension's SFTP
            // upload is affected, and it reports "SSH key not found" for this.
        }
    }

    public static func deleteIdentityKey() {
        if let url = fileURL {
            try? FileManager.default.removeItem(at: url)
        }
        UserDefaults(suiteName: AppGroupConfig.suiteName)?.removeObject(forKey: legacyDefaultsKey)
    }
}
