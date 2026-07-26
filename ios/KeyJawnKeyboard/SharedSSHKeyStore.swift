import Foundation
import KeyJawnKit

/// Read-only shared-Keychain accessor for the keyboard extension.
final class SharedSSHKeyStore: @unchecked Sendable {
    static let shared = SharedSSHKeyStore()
    private init() {}

    var privateKeyData: Data? {
        SSHIdentityKeyStore.identityKeyData()
    }
}
