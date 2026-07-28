import Foundation
import CryptoKit

public struct HostConfig: Sendable, Identifiable, Codable, Hashable {
    public let id: UUID
    public var label: String
    public var hostname: String
    public var port: UInt16
    public var username: String
    public var authMethod: AuthMethod
    /// Server public key in OpenSSH authorized_keys format (e.g. "ssh-ed25519 AAAA...").
    /// Obtain with: ssh-keyscan -t ed25519 <hostname>
    /// If nil, the main app asks the user to trust the first key it receives.
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

    /// Whether a host key has been stored for strict verification.
    ///
    /// The keyboard extension cannot display the trust-on-first-use prompt, so it
    /// uses this as its first gate and refuses to open a network connection until
    /// the main app has pinned a key.
    public var hasPinnedHostKey: Bool {
        guard let hostPublicKey else { return false }
        return !hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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

/// The canonical OpenSSH key and SHA-256 fingerprint shown by the first-use prompt.
public struct PresentedHostKey: Sendable, Equatable, Identifiable {
    public let openSSHKey: String
    public let fingerprint: String

    public var id: String { openSSHKey }

    public init(openSSHKey: String) throws {
        let components = openSSHKey.split(
            maxSplits: 2,
            omittingEmptySubsequences: true,
            whereSeparator: { $0.isWhitespace }
        )
        guard components.count >= 2,
              let keyData = Data(base64Encoded: String(components[1])) else {
            throw PresentedHostKeyError.invalidOpenSSHKey
        }

        self.openSSHKey = "\(components[0]) \(components[1])"
        let digest = Data(SHA256.hash(data: keyData))
        self.fingerprint = "SHA256:" + digest.base64EncodedString()
            .trimmingCharacters(in: CharacterSet(charactersIn: "="))
    }

    /// Compares key material while ignoring an optional OpenSSH comment.
    public func matches(openSSHKey otherKey: String) throws -> Bool {
        try self.openSSHKey == PresentedHostKey(openSSHKey: otherKey).openSSHKey
    }
}

public enum PresentedHostKeyError: Error, Equatable {
    case invalidOpenSSHKey
}
