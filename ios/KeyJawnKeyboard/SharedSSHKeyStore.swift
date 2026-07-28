import Foundation
import KeyJawnKit

/// Read-only SSH identity accessor for the keyboard extension.
///
/// The shared Keychain item is authoritative. A protected legacy mirror remains
/// readable only until the containing app completes the one-time migration.
final class SharedSSHKeyStore: @unchecked Sendable {
    static let shared = SharedSSHKeyStore()
    private init() {}

    var privateKeyData: Data? {
        SSHIdentityKeyStore.extensionIdentityKeyData()
    }
}
