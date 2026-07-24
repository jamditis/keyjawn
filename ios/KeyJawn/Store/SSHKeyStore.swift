import Foundation
import CryptoKit
import Security
import KeyJawnKit

/// Manages the app's single Ed25519 SSH identity key.
///
/// The private key is stored in the Keychain, pinned to this device, and mirrored into
/// the App Group container so the keyboard extension can authenticate SFTP uploads.
/// The public key is derived on demand and shown in Settings → SSH keys for the user
/// to copy to servers.
final class SSHKeyStore: @unchecked Sendable {
    static let shared = SSHKeyStore()
    private init() {}

    private let service = "com.keyjawn"
    private let account = "ssh-identity-ed25519"

    // MARK: - Key access

    var privateKey: Curve25519.Signing.PrivateKey {
        if let existing = loadKey() { return existing }
        let key = Curve25519.Signing.PrivateKey()
        store(key)
        return key
    }

    /// OpenSSH authorized_keys format: "ssh-ed25519 <base64> keyjawn"
    var publicKeyOpenSSHString: String {
        let rawPub = privateKey.publicKey.rawRepresentation
        var blob = Data()
        blob.appendSSHString(Data("ssh-ed25519".utf8))
        blob.appendSSHString(rawPub)
        return "ssh-ed25519 \(blob.base64EncodedString()) keyjawn"
    }

    func regenerate() {
        deleteKey()
        _ = privateKey
    }

    /// Brings the extension-visible mirror back in line with the Keychain.
    ///
    /// The mirror is only written when a key is generated, so it can be missing or
    /// stale on an install that predates it, after the storage format changed, or if
    /// the group container was reset — and the extension's only symptom is
    /// "SSH key not found" on an upload the user has every reason to expect to work.
    /// The Keychain is the system of record, so reconcile towards it at launch.
    func syncAppGroupMirror() {
        guard let existing = loadKey() else { return }
        let raw = existing.rawRepresentation
        guard AppGroupSecretStore.identityKeyData() != raw else { return }
        AppGroupSecretStore.write(identityKeyData: raw)
    }

    // MARK: - Keychain

    private func loadKey() -> Curve25519.Signing.PrivateKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? Curve25519.Signing.PrivateKey(rawRepresentation: data)
    }

    private func store(_ key: Curve25519.Signing.PrivateKey) {
        let attrs: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: key.rawRepresentation,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        SecItemDelete(attrs as CFDictionary)
        SecItemAdd(attrs as CFDictionary, nil)

        // Mirror raw key bytes to the App Group so the keyboard extension can
        // authenticate. Goes through AppGroupSecretStore, which keeps the mirror
        // encrypted at rest and out of device backups — a plain UserDefaults value
        // would have been backed up, undoing the ThisDeviceOnly class above.
        AppGroupSecretStore.write(identityKeyData: key.rawRepresentation)
    }

    private func deleteKey() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)

        // Remove mirror too, including any value left by the pre-file layout.
        AppGroupSecretStore.deleteIdentityKey()
    }
}

private extension Data {
    mutating func appendSSHString(_ data: Data) {
        var len = UInt32(data.count).bigEndian
        let lenData = Swift.withUnsafeBytes(of: &len) { Data($0) }
        append(lenData)
        append(data)
    }
}
