import Foundation
import KeyJawnKit

/// Read-only SSH key accessor for the keyboard extension.
///
/// The main app mirrors the key into the App Group container; see
/// ``AppGroupSecretStore`` for why it crosses as a protected, backup-excluded file
/// rather than as a value in the shared `UserDefaults` suite.
final class AppGroupSSHKeyStore: @unchecked Sendable {
    static let shared = AppGroupSSHKeyStore()
    private init() {}

    var privateKeyData: Data? {
        AppGroupSecretStore.identityKeyData()
    }
}
