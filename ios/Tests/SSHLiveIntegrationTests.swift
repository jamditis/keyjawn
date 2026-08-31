import CryptoKit
import Foundation
import KeyJawnKit
import XCTest

@testable import KeyJawn

/// Opt-in checks against a real SSH server.
///
/// Normal test runs skip the network test. Set the `KEYJAWN_LIVE_SSH_*`
/// environment variables described in `docs/ios-app-review.md` to enable it.
@MainActor
final class SSHLiveIntegrationTests: XCTestCase {

    func testLiveConfigurationRequiresAnExplicitTLSChoice() throws {
        let environment = liveEnvironment()

        XCTAssertThrowsError(
            try LiveSSHConfiguration.fromEnvironment(environment)
        ) { error in
            guard case LiveSSHTestError.incompleteConfiguration = error else {
                return XCTFail("Expected incompleteConfiguration, got \(error)")
            }
        }
    }

    func testLiveConfigurationRejectsAnInvalidTLSChoice() throws {
        var environment = liveEnvironment()
        environment["KEYJAWN_LIVE_SSH_TLS"] = "true"

        XCTAssertThrowsError(
            try LiveSSHConfiguration.fromEnvironment(environment)
        ) { error in
            guard case LiveSSHTestError.invalidTLSValue("true") = error else {
                return XCTFail("Expected invalidTLSValue, got \(error)")
            }
        }
    }

    func testLiveConfigurationEnablesTLSOnlyForOne() throws {
        var environment = liveEnvironment()
        environment["KEYJAWN_LIVE_SSH_TLS"] = "1"

        XCTAssertTrue(
            try XCTUnwrap(
                LiveSSHConfiguration.fromEnvironment(environment)
            ).usesTLSTunnel
        )

        environment["KEYJAWN_LIVE_SSH_TLS"] = "0"
        XCTAssertFalse(
            try XCTUnwrap(
                LiveSSHConfiguration.fromEnvironment(environment)
            ).usesTLSTunnel
        )
    }

    func testShellSingleQuoteEscapesApostrophes() {
        XCTAssertEqual(
            shellSingleQuoted("/tmp/reviewer's image.jpg"),
            "'/tmp/reviewer'\\''s image.jpg'"
        )
    }

    func testIdentityKeyUsesAuthorizedKeysFormat() throws {
        let publicKey = SSHKeyStore.shared.publicKeyOpenSSHString
        let parts = publicKey.split(separator: " ")

        XCTAssertEqual(parts.count, 3)
        guard parts.count == 3 else { return }
        XCTAssertEqual(parts.first, "ssh-ed25519")
        XCTAssertNotNil(Data(base64Encoded: String(parts[1])))
        XCTAssertEqual(parts.last, "keyjawn")

        if ProcessInfo.processInfo.environment["KEYJAWN_EXPORT_PUBLIC_KEY"] == "1" {
            print("KEYJAWN_LIVE_PUBLIC_KEY=\(publicKey)")
        }
    }

    func testHostTrustRemotePTYAndSFTPUpload() async throws {
        guard let configuration = try LiveSSHConfiguration.fromEnvironment() else {
            throw XCTSkip(
                "Set KEYJAWN_LIVE_SSH_HOST, USER, HOST_KEY, and TLS to run the live SSH test"
            )
        }

        var host = HostConfig(
            label: "Live integration",
            hostname: configuration.host,
            port: configuration.port,
            username: configuration.user,
            authMethod: .key,
            usesTLSTunnel: configuration.usesTLSTunnel,
            uploadPath: configuration.uploadPath
        )
        let session = SSHSession()
        var output: [UInt8] = []
        session.onData = { output.append(contentsOf: $0) }

        session.connectWithKey(to: host)
        try await waitForState(.awaitingHostKey, in: session)

        let presentedKey = try XCTUnwrap(session.pendingHostKey)
        XCTAssertTrue(try presentedKey.matches(openSSHKey: configuration.hostKey))
        host.hostPublicKey = presentedKey.openSSHKey

        session.connectAfterTrust(to: host)
        try await waitForState(.connected, in: session)

        let token = UUID().uuidString.lowercased()
        let expectedOutput = "KEYJAWN_\(token)"
        session.send(Array("printf 'KEYJAWN_%s\\n' '\(token)'\n".utf8))
        try await waitUntil("remote PTY output") {
            String(decoding: output, as: UTF8.self).contains(expectedOutput)
        }
        session.resize(cols: 100, rows: 30)
        session.disconnect()

        let payload = Data("keyjawn-sftp-\(token)\n".utf8)
        let remotePath = try await CitadelSCPUploader.upload(
            imageData: payload,
            to: host,
            privateKeyData: SSHKeyStore.shared.privateKey.rawRepresentation
        )
        XCTAssertTrue(remotePath.hasPrefix(configuration.uploadPath + "/keyjawn-"))

        let cleanupMarker = "KEYJAWN_LIVE_CLEANED_\(token)"
        let failureMarker = "KEYJAWN_LIVE_FAILED_\(token)"
        let quotedPath = shellSingleQuoted(remotePath)
        output.removeAll()
        session.connectWithKey(to: host)
        try await waitForState(.connected, in: session)
        let cleanupCommand =
            "actual=$(cat -- \(quotedPath)); "
            + "if [ \"$actual\" = \"keyjawn-sftp-\(token)\" ] "
            + "&& [ \"$(wc -c < \(quotedPath))\" -eq \(payload.count) ] "
            + "&& rm -f -- \(quotedPath) && [ ! -e \(quotedPath) ]; "
            + "then printf 'KEYJAWN_LIVE_%s_%s\\n' 'CLEANED' '\(token)'; "
            + "else printf 'KEYJAWN_LIVE_%s_%s\\n' 'FAILED' '\(token)'; fi\n"
        session.send(Array(cleanupCommand.utf8))
        try await waitUntil("remote upload verification and cleanup") {
            let text = String(decoding: output, as: UTF8.self)
            return text.contains(cleanupMarker) || text.contains(failureMarker)
        }
        XCTAssertTrue(
            String(decoding: output, as: UTF8.self).contains(cleanupMarker),
            "remote payload must match and its file must be removed before the live test passes"
        )
        session.disconnect()
    }

    private func waitForState(
        _ expected: SSHSession.ConnectionState,
        in session: SSHSession
    ) async throws {
        try await waitUntil("SSH state \(expected)") {
            if case .failed = session.connectionState { return true }
            return session.connectionState == expected
        }
        if case .failed(let message) = session.connectionState {
            throw LiveSSHTestError.connectionFailed(message)
        }
    }

    private func waitUntil(
        _ operation: String,
        timeout: TimeInterval = 30,
        condition: () -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return }
            try await Task.sleep(for: .milliseconds(100))
        }
        throw LiveSSHTestError.timedOut(operation)
    }

    private func liveEnvironment() -> [String: String] {
        [
            "KEYJAWN_LIVE_SSH_HOST": "review.example",
            "KEYJAWN_LIVE_SSH_USER": "reviewer",
            "KEYJAWN_LIVE_SSH_HOST_KEY": "ssh-ed25519 AAAAexample",
        ]
    }
}

private struct LiveSSHConfiguration {
    let host: String
    let user: String
    let port: UInt16
    let hostKey: String
    let uploadPath: String
    let usesTLSTunnel: Bool

    static func fromEnvironment(
        _ environment: [String: String] = ProcessInfo.processInfo.environment
    ) throws -> Self? {
        let names = [
            "KEYJAWN_LIVE_SSH_HOST",
            "KEYJAWN_LIVE_SSH_USER",
            "KEYJAWN_LIVE_SSH_HOST_KEY",
            "KEYJAWN_LIVE_SSH_TLS",
        ]
        let present = names.filter { environment[$0]?.isEmpty == false }
        if present.isEmpty { return nil }
        guard present.count == names.count else {
            throw LiveSSHTestError.incompleteConfiguration
        }

        let portText = environment["KEYJAWN_LIVE_SSH_PORT"] ?? "22"
        guard let port = UInt16(portText), port > 0 else {
            throw LiveSSHTestError.invalidPort(portText)
        }
        let uploadPath = environment["KEYJAWN_LIVE_SSH_UPLOAD_PATH"] ?? "/tmp"
        guard uploadPath.hasPrefix("/"), !uploadPath.contains("\n") else {
            throw LiveSSHTestError.invalidUploadPath(uploadPath)
        }
        let tlsText = environment["KEYJAWN_LIVE_SSH_TLS"]!
        guard tlsText == "0" || tlsText == "1" else {
            throw LiveSSHTestError.invalidTLSValue(tlsText)
        }

        return Self(
            host: environment["KEYJAWN_LIVE_SSH_HOST"]!,
            user: environment["KEYJAWN_LIVE_SSH_USER"]!,
            port: port,
            hostKey: environment["KEYJAWN_LIVE_SSH_HOST_KEY"]!,
            uploadPath: uploadPath,
            usesTLSTunnel: tlsText == "1"
        )
    }
}

private enum LiveSSHTestError: Error, LocalizedError {
    case incompleteConfiguration
    case invalidPort(String)
    case invalidUploadPath(String)
    case invalidTLSValue(String)
    case connectionFailed(String)
    case timedOut(String)

    var errorDescription: String? {
        switch self {
        case .incompleteConfiguration:
            return "The live SSH environment is incomplete"
        case .invalidPort(let value):
            return "Invalid live SSH port: \(value)"
        case .invalidUploadPath(let value):
            return "Invalid live SSH upload path: \(value)"
        case .invalidTLSValue(let value):
            return "Invalid live SSH TLS value: \(value). Use 0 or 1"
        case .connectionFailed(let message):
            return "SSH connection failed: \(message)"
        case .timedOut(let operation):
            return "Timed out waiting for \(operation)"
        }
    }
}

private func shellSingleQuoted(_ value: String) -> String {
    "'" + value.replacingOccurrences(of: "'", with: "'\\''") + "'"
}
