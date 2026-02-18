import Foundation

public struct HostConfig: Sendable, Identifiable, Codable, Hashable {
    public let id: UUID
    public var label: String
    public var hostname: String
    public var port: UInt16
    public var username: String
    public var authMethod: AuthMethod
    /// Server public key in OpenSSH authorized_keys format (e.g. "ssh-ed25519 AAAA...").
    /// Obtain with: ssh-keyscan -t ed25519 <hostname>
    /// If nil, host key verification is skipped — vulnerable to MitM attacks.
    public var hostPublicKey: String?

    public enum AuthMethod: String, Sendable, Codable, CaseIterable {
        case password   = "password"
        case key        = "key"     // Ed25519/RSA key stored in Keychain
    }

    public init(
        id: UUID = UUID(),
        label: String,
        hostname: String,
        port: UInt16 = 22,
        username: String,
        authMethod: AuthMethod = .key,
        hostPublicKey: String? = nil
    ) {
        self.id = id
        self.label = label
        self.hostname = hostname
        self.port = port
        self.username = username
        self.authMethod = authMethod
        self.hostPublicKey = hostPublicKey
    }

    public var isValid: Bool {
        !hostname.trimmingCharacters(in: .whitespaces).isEmpty &&
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        port > 0
    }
}
