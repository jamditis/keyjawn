import Foundation
import CryptoKit
import KeyJawnKit

/// Manages the app's single Ed25519 SSH identity key.
///
/// The private key is stored once in the shared Keychain access group, pinned to this
/// device, and read there by both the app and keyboard extension. The public key is
/// derived on demand and shown in Settings → SSH keys for the user to copy to servers.
final class SSHKeyStore: @unchecked Sendable {
    static let shared = SSHKeyStore()
    private init() {}

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

    /// Ensures an upgraded install has migrated before the extension can run.
    func prepareSharedIdentity() {
        _ = privateKey
    }

    // MARK: - Keychain

    private func loadKey() -> Curve25519.Signing.PrivateKey? {
        guard let data = SSHIdentityKeyStore.migrateLegacyIdentityKeyData() else {
            return nil
        }
        return try? Curve25519.Signing.PrivateKey(rawRepresentation: data)
    }

    private func store(_ key: Curve25519.Signing.PrivateKey) {
        SSHIdentityKeyStore.store(identityKeyData: key.rawRepresentation)
    }

    private func deleteKey() {
        SSHIdentityKeyStore.deleteIdentityKey()
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
