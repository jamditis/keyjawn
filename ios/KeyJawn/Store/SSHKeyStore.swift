import Foundation
import CryptoKit
import Security

/// Manages the app's single Ed25519 SSH identity key.
/// The private key is stored in the Keychain and survives reinstalls (if iCloud Keychain is on).
/// The public key is derived on demand and shown in Settings → SSH Keys for the user to copy to servers.
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
    }

    private func deleteKey() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
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
