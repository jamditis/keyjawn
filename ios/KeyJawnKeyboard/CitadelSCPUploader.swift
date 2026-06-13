import Foundation
import Citadel
import NIOCore
@preconcurrency import NIOSSH
import CryptoKit
import KeyJawnKit

/// Uploads image data to a remote host via SFTP using Citadel.
/// Lives in the keyboard extension (not KeyJawnKit) so it can import Citadel freely.
enum CitadelSCPUploader {

    enum UploadError: Error, LocalizedError {
        case noHosts
        case invalidPrivateKey
        case connectionFailed(Error)
        case uploadFailed(Error)

        var errorDescription: String? {
            switch self {
            case .noHosts:             return "No hosts configured"
            case .invalidPrivateKey:   return "SSH key is invalid"
            case .connectionFailed(let e): return "Connection failed: \(e.localizedDescription)"
            case .uploadFailed(let e):     return "Upload failed: \(e.localizedDescription)"
            }
        }
    }

    /// Uploads `imageData` to `host` via SFTP. Returns the remote file path on success.
    static func upload(imageData: Data, to host: HostConfig, privateKeyData: Data) async throws -> String {
        // Build the remote path.
        let filename = "keyjawn-\(Int(Date().timeIntervalSince1970)).jpg"
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

        // Resolve host key validator.
        let validator: SSHHostKeyValidator
        if let hkString = host.hostPublicKey,
           !hkString.isEmpty,
           let hk = try? NIOSSHPublicKey(openSSHPublicKey: hkString) {
            validator = .trustedKeys(Set([hk]))
        } else {
            validator = .acceptAnything()
        }

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
