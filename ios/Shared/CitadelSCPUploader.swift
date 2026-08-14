import Foundation
import Citadel
import NIOCore
@preconcurrency import NIOSSH
import CryptoKit
import KeyJawnKit

/// Uploads image data to a remote host via SFTP using Citadel.
/// Compiled into both the app and the keyboard extension so the in-app extra
/// row can use the same upload path as the IME. Not in KeyJawnKit because
/// the kit must stay free of the Citadel dependency.
enum CitadelSCPUploader {

    enum UploadError: Error, LocalizedError {
        case noHosts
        case hostKeyNotTrusted
        case invalidHostKey
        case invalidPrivateKey
        case connectionFailed(Error)
        case uploadFailed(Error)

        var errorDescription: String? {
            switch self {
            case .noHosts:             return "No hosts configured"
            case .hostKeyNotTrusted:   return "Open this host in KeyJawn once to verify its SSH host key"
            case .invalidHostKey:      return "The saved SSH host key is invalid"
            case .invalidPrivateKey:   return "SSH key is invalid"
            case .connectionFailed(let e): return "Connection failed: \(e.localizedDescription)"
            case .uploadFailed(let e):     return "Upload failed: \(e.localizedDescription)"
            }
        }
    }

    /// Uploads `imageData` to `host` via SFTP. Returns the remote file path on success.
    static func upload(imageData: Data, to host: HostConfig, privateKeyData: Data) async throws -> String {
        // The extension cannot present a trust prompt. Refuse before parsing the
        // private key or opening a socket, and direct the user to the main app.
        guard host.hasPinnedHostKey else {
            throw UploadError.hostKeyNotTrusted
        }
        guard let hostKeyString = host.hostPublicKey,
              let hostKey = try? NIOSSHPublicKey(openSSHPublicKey: hostKeyString) else {
            throw UploadError.invalidHostKey
        }
        let validator = SSHHostKeyValidator.trustedKeys(Set([hostKey]))

        // Build the remote path. The suffix is there because the timestamp alone has
        // one-second resolution and the write below truncates: two uploads in the same
        // second silently overwrote each other, and the path handed back for the first
        // one then pointed at the second one's image.
        let stamp = Int(Date().timeIntervalSince1970)
        let suffix = UUID().uuidString.prefix(6).lowercased()
        let filename = "keyjawn-\(stamp)-\(suffix).jpg"
        let base = host.uploadPath.hasSuffix("/") ? host.uploadPath : host.uploadPath + "/"
        let remotePath = "\(base)\(filename)"

        // Reconstruct the Curve25519 private key.
        let privateKey: Curve25519.Signing.PrivateKey
        do {
            privateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: privateKeyData)
        } catch {
            throw UploadError.invalidPrivateKey
        }

        let authMethod = SSHAuthenticationMethod.ed25519(username: host.username, privateKey: privateKey)

        // Connect.
        let client: SSHClient
        do {
            client = try await SSHClient.connect(
                host: host.hostname,
                port: Int(host.port),
                authenticationMethod: authMethod,
                hostKeyValidator: validator,
                reconnect: .never
            )
        } catch {
            throw UploadError.connectionFailed(error)
        }

        // Upload via SFTP.
        do {
            try await client.withSFTP { sftp in
                try await sftp.withFile(
                    filePath: remotePath,
                    flags: [.write, .create, .truncate]
                ) { file in
                    try await file.write(ByteBuffer(data: imageData))
                }
            }
        } catch {
            try? await client.close()
            throw UploadError.uploadFailed(error)
        }

        try? await client.close()
        return remotePath
    }
}
