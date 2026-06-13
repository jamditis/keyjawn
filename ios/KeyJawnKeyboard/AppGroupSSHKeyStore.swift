import Foundation

/// Read-only SSH key accessor for the keyboard extension.
/// Key bytes are mirrored to App Group UserDefaults by SSHKeyStore in the main app.
final class AppGroupSSHKeyStore: @unchecked Sendable {
    static let shared = AppGroupSSHKeyStore()
    private init() {}

    private let key = "keyjawn.ssh-identity-raw"
    private var defaults: UserDefaults? { UserDefaults(suiteName: "group.com.keyjawn") }

    var privateKeyData: Data? {
        defaults?.data(forKey: key)
    }
}
