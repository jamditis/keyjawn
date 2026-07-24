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
    /// Remote directory path for SCP uploads. Defaults to /tmp.
    public var uploadPath: String

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
        hostPublicKey: String? = nil,
        uploadPath: String = "/tmp"
    ) {
        self.id = id
        self.label = label
        self.hostname = hostname
        self.port = port
        self.username = username
        self.authMethod = authMethod
        self.hostPublicKey = hostPublicKey
        self.uploadPath = uploadPath
    }

    public var isValid: Bool {
        !hostname.trimmingCharacters(in: .whitespaces).isEmpty &&
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        port > 0
    }

    /// The command that produces the key to paste into ``hostPublicKey``.
    ///
    /// Carries `-p` for a non-default port. Without it the instructions scan port 22
    /// of the same hostname, which is either nothing at all or — worse — a different
    /// service whose key gets pinned here and makes every later connection fail.
    public var hostKeyScanCommand: String {
        Self.hostKeyScanCommand(hostname: hostname, port: port)
    }

    public static func hostKeyScanCommand(hostname: String, port: UInt16) -> String {
        let portFlag = port == 22 ? "" : "-p \(port) "
        return "ssh-keyscan \(portFlag)-t ed25519 \(hostname)"
    }
}
