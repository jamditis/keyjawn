import Citadel
import Foundation
import NIOCore
import NIOEmbedded
import XCTest

@testable import KeyJawn

final class SSHErrorPresentationTests: XCTestCase {
    func testTransportFailureHasActionableUserMessage() {
        XCTAssertEqual(
            AppleNetworkSSHTransportError.connectionFailed.localizedDescription,
            "Could not connect to the SSH server. Check the host, port, TLS tunnel setting, and network connection."
        )
    }

    func testAuthenticationFailureHasActionableUserMessage() {
        XCTAssertEqual(
            AppleNetworkSSHTransport.userFacingError(
                SSHClientError.allAuthenticationOptionsFailed
            ) as? AppleNetworkSSHTransportError,
            .authenticationFailed
        )
    }

    func testEarlyChannelCloseDoesNotExposeLibraryError() {
        XCTAssertEqual(
            AppleNetworkSSHTransport.userFacingError(ChannelError.eof)
                as? AppleNetworkSSHTransportError,
            .sshHandshakeFailed
        )
    }

    func testUnsupportedServerAuthenticationMethodsAreAuthenticationFailures() {
        let errors: [SSHClientError] = [
            .unsupportedPasswordAuthentication,
            .unsupportedPrivateKeyAuthentication,
            .unsupportedHostBasedAuthentication,
        ]

        for error in errors {
            XCTAssertEqual(
                AppleNetworkSSHTransport.userFacingError(error)
                    as? AppleNetworkSSHTransportError,
                .authenticationFailed
            )
        }
    }

    func testCitadelAuthenticationTimeoutUsesTimeoutMessage() {
        XCTAssertEqual(
            AppleNetworkSSHTransport.userFacingError(
                ChannelError.connectTimeout(.seconds(10))
            ) as? AppleNetworkSSHTransportError,
            .operationTimedOut
        )
    }

    func testBootstrapConnectionTimeoutUsesTimeoutMessage() {
        XCTAssertEqual(
            AppleNetworkSSHTransport.connectionError(
                ChannelError.connectTimeout(.seconds(30))
            ),
            .operationTimedOut
        )
    }

    func testUploaderPreservesCancellationDuringConnection() {
        XCTAssertTrue(
            CitadelSCPUploader.connectionFailure(
                from: CancellationError()
            ) is CancellationError
        )
    }

    func testUploaderMapsConnectionAuthenticationAndTimeoutFailures() {
        let cases: [(Error, CitadelSCPUploader.UploadError)] = [
            (
                AppleNetworkSSHTransportError.connectionFailed,
                .connectionFailed
            ),
            (
                AppleNetworkSSHTransportError.authenticationFailed,
                .authenticationFailed
            ),
            (
                AppleNetworkSSHTransportError.operationTimedOut,
                .uploadTimedOut
            ),
        ]

        for (error, expected) in cases {
            XCTAssertEqual(
                CitadelSCPUploader.connectionFailure(from: error)
                    as? CitadelSCPUploader.UploadError,
                expected
            )
        }
    }

    func testUploaderPreservesCancellationDuringUpload() {
        XCTAssertTrue(
            CitadelSCPUploader.uploadFailure(
                from: CancellationError(),
                didTimeOut: false
            ) is CancellationError
        )
    }

    func testSFTPSetupTimeoutUsesUploadTimeoutMessage() {
        XCTAssertEqual(
            CitadelSCPUploader.uploadFailure(
                from: SFTPError.missingResponse,
                didTimeOut: false
            ) as? CitadelSCPUploader.UploadError,
            .uploadTimedOut
        )
    }

    func testDeadlineFlagUsesUploadTimeoutMessage() {
        XCTAssertEqual(
            CitadelSCPUploader.uploadFailure(
                from: ChannelError.eof,
                didTimeOut: true
            ) as? CitadelSCPUploader.UploadError,
            .uploadTimedOut
        )
    }

    func testOperationDeadlineClosesAStalledChannel() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let deadline = AppleNetworkOperationDeadline(
            channel: channel,
            timeout: .seconds(1)
        )

        loop.advanceTime(by: .seconds(1))

        XCTAssertTrue(deadline.didTimeOut)
        XCTAssertFalse(channel.isActive)
        XCTAssertNoThrow(try channel.finish(acceptAlreadyClosed: true))
    }

    func testCancelledOperationDeadlineKeepsTheChannelOpen() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let deadline = AppleNetworkOperationDeadline(
            channel: channel,
            timeout: .seconds(1)
        )

        deadline.cancel()
        loop.advanceTime(by: .seconds(1))

        XCTAssertFalse(deadline.didTimeOut)
        XCTAssertTrue(channel.isActive)
        XCTAssertNoThrow(try channel.finish())
    }

    func testCompletingAnOperationWinsBeforeItsDeadline() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let deadline = AppleNetworkOperationDeadline(
            channel: channel,
            timeout: .seconds(1)
        )

        XCTAssertTrue(deadline.complete())
        loop.advanceTime(by: .seconds(1))

        XCTAssertFalse(deadline.didTimeOut)
        XCTAssertTrue(channel.isActive)
        XCTAssertNoThrow(try channel.finish())
    }

    func testCompletingAnOperationLosesAfterItsDeadline() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let deadline = AppleNetworkOperationDeadline(
            channel: channel,
            timeout: .seconds(1)
        )

        loop.advanceTime(by: .seconds(1))

        XCTAssertFalse(deadline.complete())
        XCTAssertTrue(deadline.didTimeOut)
        XCTAssertNoThrow(try channel.finish(acceptAlreadyClosed: true))
    }

    func testChannelCloserClosesAnActiveChannel() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()

        AppleNetworkChannelCloser(channel: channel).close()

        XCTAssertFalse(channel.isActive)
        XCTAssertNoThrow(try channel.finish(acceptAlreadyClosed: true))
    }

    func testChannelBoxClosesAChannelStoredAfterCancellation() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let box = AppleNetworkChannelBox()

        box.close()
        box.store(channel)

        XCTAssertFalse(channel.isActive)
        XCTAssertNoThrow(try channel.finish(acceptAlreadyClosed: true))
    }

    func testChannelBoxClosesAnAlreadyStoredChannel() throws {
        let loop = EmbeddedEventLoop()
        let channel = EmbeddedChannel(loop: loop)
        try channel.connect(
            to: SocketAddress(ipAddress: "127.0.0.1", port: 22)
        ).wait()
        let box = AppleNetworkChannelBox()

        box.store(channel)
        box.close()

        XCTAssertFalse(channel.isActive)
        XCTAssertNoThrow(try channel.finish(acceptAlreadyClosed: true))
    }
}
