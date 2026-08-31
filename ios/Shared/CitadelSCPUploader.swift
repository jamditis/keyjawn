import Citadel
import CryptoKit
import Foundation
import KeyJawnKit
import NIOCore
@preconcurrency import NIOSSH
import OSLog

/// Uploads image data to a remote host via SFTP using Citadel.
/// Compiled into both the app and the keyboard extension so the in-app extra
/// row can use the same upload path as the IME. Not in KeyJawnKit because
/// the kit must stay free of the Citadel dependency.
enum CitadelSCPUploader {

    enum UploadError: Error, LocalizedError, Equatable {
        case keyAuthenticationRequired
        case hostKeyNotTrusted
        case hostKeyChanged
        case invalidHostKey
        case invalidPrivateKey
        case authenticationFailed
        case connectionFailed
        case uploadTimedOut
        case uploadFailed

        var errorDescription: String? {
            switch self {
            case .keyAuthenticationRequired: return "Copied-image upload requires SSH key authentication"
            case .hostKeyNotTrusted: return "Open this host in KeyJawn once to verify its SSH host key"
            case .hostKeyChanged: return "The SSH server host key changed. Verify the server in KeyJawn"
            case .invalidHostKey: return "The saved SSH host key is invalid"
            case .invalidPrivateKey: return "SSH key is invalid"
            case .authenticationFailed: return "SSH key authentication failed"
            case .connectionFailed: return "Could not connect to the SSH server"
            case .uploadTimedOut: return "Copied-image upload timed out"
            case .uploadFailed: return "Copied-image upload failed"
            }
        }
    }

    private static let logger = Logger(
        subsystem: "com.keyjawn",
        category: "SFTP upload"
    )

    /// Uploads `imageData` to `host` via SFTP. Returns the remote file path on success.
    static func upload(imageData: Data, to host: HostConfig, privateKeyData: Data) async throws -> String {
        // The uploader receives only the shared SSH private key. Password hosts work
        // in the terminal, but this path has no password credential it could use.
        guard host.authMethod == .key else {
            throw UploadError.keyAuthenticationRequired
        }
        // The extension cannot present a trust prompt. Refuse before parsing the
        // private key or opening a socket, and direct the user to the main app.
        guard host.hasPinnedHostKey else {
            throw UploadError.hostKeyNotTrusted
        }
        guard let hostKeyString = host.hostPublicKey,
            let hostKey = try? NIOSSHPublicKey(openSSHPublicKey: hostKeyString)
        else {
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
        let connection: AppleNetworkSSHTransport.Connection
        do {
            connection = try await AppleNetworkSSHTransport.connect(
                host: host.hostname,
                port: Int(host.port),
                useTLS: host.usesTLSTunnel,
                authentication: AppleNetworkSSHAuthentication(authMethod),
                hostKeyValidator: validator
            )
        } catch {
            throw connectionFailure(from: error)
        }
        let client = connection.client
        defer { connection.channel.close(promise: nil) }
        let channelCloser = AppleNetworkChannelCloser(
            channel: connection.channel
        )
        let deadline = AppleNetworkOperationDeadline(
            channel: connection.channel,
            timeout: .seconds(30)
        )

        // Upload via SFTP.
        do {
            try await withTaskCancellationHandler {
                try await client.withSFTP { sftp in
                    try await sftp.withFile(
                        filePath: remotePath,
                        flags: [.write, .create, .truncate]
                    ) { file in
                        try await file.write(ByteBuffer(data: imageData))
                    }
                }
            } onCancel: {
                channelCloser.close()
            }
            if Task.isCancelled {
                deadline.cancel()
                throw CancellationError()
            }
            guard deadline.complete() else {
                throw UploadError.uploadTimedOut
            }
        } catch {
            let completedBeforeDeadline = deadline.complete()
            if Task.isCancelled {
                throw CancellationError()
            }
            if let uploadError = error as? UploadError {
                throw uploadError
            }
            if !completedBeforeDeadline {
                throw UploadError.uploadTimedOut
            }
            throw uploadFailure(
                from: error,
                didTimeOut: false
            )
        }

        return remotePath
    }

    static func connectionFailure(from error: Error) -> Error {
        if error is CancellationError {
            return error
        }
        if error is InvalidHostKey {
            return UploadError.hostKeyChanged
        }
        let presentedError = AppleNetworkSSHTransport.userFacingError(error)
        if presentedError as? AppleNetworkSSHTransportError == .authenticationFailed {
            return UploadError.authenticationFailed
        }
        if presentedError as? AppleNetworkSSHTransportError == .operationTimedOut {
            return UploadError.uploadTimedOut
        }
        return UploadError.connectionFailed
    }

    static func uploadFailure(from error: Error, didTimeOut: Bool) -> Error {
        if error is CancellationError {
            return error
        }
        if didTimeOut {
            return UploadError.uploadTimedOut
        }
        if let sftpError = error as? SFTPError,
            case .missingResponse = sftpError
        {
            return UploadError.uploadTimedOut
        }
        logger.error(
            "Upload failed: \(String(describing: type(of: error)), privacy: .private)"
        )
        return UploadError.uploadFailed
    }
}
