@preconcurrency import Citadel
import Foundation
import NIOCore
import NIOTransportServices
import Network
import OSLog

/// Carries Citadel's immutable authentication delegate into one detached task
/// without changing the third-party type's global concurrency contract.
struct AppleNetworkSSHAuthentication: @unchecked Sendable {
    let method: SSHAuthenticationMethod

    init(_ method: SSHAuthenticationMethod) {
        self.method = method
    }
}

enum AppleNetworkSSHTransportError: Error, LocalizedError, Sendable, Equatable {
    case connectionFailed
    case authenticationFailed
    case sshHandshakeFailed
    case operationTimedOut

    var errorDescription: String? {
        switch self {
        case .connectionFailed:
            return
                "Could not connect to the SSH server. Check the host, port, TLS tunnel setting, and network connection."
        case .authenticationFailed:
            return "SSH authentication failed. Check the username and password or SSH key."
        case .sshHandshakeFailed:
            return "The SSH server closed the connection before setup finished. Check the server and host key."
        case .operationTimedOut:
            return "The SSH operation timed out. Check the server and network connection, then try again."
        }
    }
}

/// Opens Citadel SSH clients through Apple's Network.framework transport.
///
/// NIO's POSIX bootstrap can remain pending on a physical iOS device even when
/// Network.framework can reach the same endpoint. NIOTS preserves Citadel's NIO
/// channel pipeline while using the system network path for DNS, VPN, and TCP.
enum AppleNetworkSSHTransport {
    struct Connection {
        let client: SSHClient
        let channel: Channel
    }

    private static let group = NIOTSEventLoopGroup.singleton
    private static let logger = Logger(
        subsystem: "com.keyjawn",
        category: "SSH transport"
    )

    static func bootstrap(
        useTLS: Bool,
        connectTimeout: TimeAmount = .seconds(30)
    ) -> NIOTSConnectionBootstrap {
        let bootstrap = NIOTSConnectionBootstrap(group: group)
            .connectTimeout(connectTimeout)
        if useTLS {
            return bootstrap.tlsOptions(NWProtocolTLS.Options())
        }
        return bootstrap
    }

    static func connect(
        host: String,
        port: Int,
        useTLS: Bool,
        authentication: AppleNetworkSSHAuthentication,
        hostKeyValidator: SSHHostKeyValidator
    ) async throws -> Connection {
        let channelBox = AppleNetworkChannelBox()

        return try await withTaskCancellationHandler {
            do {
                let channel: Channel
                do {
                    channel = try await bootstrap(useTLS: useTLS)
                        .channelInitializer { channel in
                            // Capture the channel before DNS, TCP, or TLS setup
                            // finishes so task cancellation can close pending work.
                            channelBox.store(channel)
                            return channel.eventLoop.makeSucceededVoidFuture()
                        }
                        .connect(host: host, port: port)
                        .get()
                } catch {
                    try Task.checkCancellation()
                    throw connectionError(error)
                }
                try Task.checkCancellation()

                let settings = SSHClientSettings(
                    host: host,
                    port: port,
                    authenticationMethod: { authentication.method },
                    hostKeyValidator: hostKeyValidator
                )
                // Citadel 0.12 does not expose a reconnect setting for an adopted
                // channel. Its adopted-channel client defaults to no reconnect.
                // Keep this path because its host-and-port overload creates a POSIX
                // bootstrap and would bypass Network.framework and optional TLS.
                let deadline = AppleNetworkOperationDeadline(
                    channel: channel,
                    timeout: .seconds(30)
                )

                let client: SSHClient
                do {
                    client = try await SSHClient.connect(on: channel, settings: settings)
                } catch {
                    if Task.isCancelled {
                        deadline.cancel()
                        throw CancellationError()
                    }
                    guard deadline.complete() else {
                        throw AppleNetworkSSHTransportError.operationTimedOut
                    }
                    throw userFacingError(error)
                }
                if Task.isCancelled {
                    deadline.cancel()
                    throw CancellationError()
                }
                guard deadline.complete() else {
                    throw AppleNetworkSSHTransportError.operationTimedOut
                }
                return Connection(client: client, channel: channel)
            } catch {
                channelBox.close()
                throw error
            }
        } onCancel: {
            channelBox.close()
        }
    }

    static func connectionError(_ error: Error) -> AppleNetworkSSHTransportError {
        if let channelError = error as? ChannelError,
            case .connectTimeout = channelError
        {
            return .operationTimedOut
        }
        logPrivateFailure(error, stage: "TCP or TLS connection")
        return .connectionFailed
    }

    /// Preserves security and cancellation errors while hiding library details
    /// that do not help a user repair a connection.
    static func userFacingError(_ error: Error) -> Error {
        if error is InvalidHostKey || error is CancellationError {
            return error
        }
        if let transportError = error as? AppleNetworkSSHTransportError {
            return transportError
        }
        if error is AuthenticationFailed {
            return AppleNetworkSSHTransportError.authenticationFailed
        }
        if let clientError = error as? SSHClientError {
            switch clientError {
            case .allAuthenticationOptionsFailed,
                .unsupportedPasswordAuthentication,
                .unsupportedPrivateKeyAuthentication,
                .unsupportedHostBasedAuthentication:
                return AppleNetworkSSHTransportError.authenticationFailed
            case .channelCreationFailed:
                break
            }
        }
        if let channelError = error as? ChannelError,
            case .connectTimeout = channelError
        {
            return AppleNetworkSSHTransportError.operationTimedOut
        }

        logPrivateFailure(error, stage: "SSH setup")
        return AppleNetworkSSHTransportError.sshHandshakeFailed
    }

    private static func logPrivateFailure(_ error: Error, stage: StaticString) {
        logger.error(
            "\(stage): \(String(describing: type(of: error)), privacy: .private)"
        )
    }
}

/// Closes a channel if one bounded SSH operation does not finish in time.
///
/// Closing the channel cancels the underlying NIO operation. This is more direct
/// than racing a Swift task against a timer because Citadel's client is not
/// Sendable and must remain in the task that owns it.
final class AppleNetworkOperationDeadline: @unchecked Sendable {
    private let state: State
    private let scheduled: Scheduled<Void>

    init(channel: Channel, timeout: TimeAmount) {
        let state = State()
        self.state = state
        scheduled = channel.eventLoop.scheduleTask(in: timeout) {
            guard state.markTimedOut() else { return }
            channel.close(promise: nil)
        }
    }

    var didTimeOut: Bool {
        state.didTimeOut
    }

    func cancel() {
        _ = state.complete()
        scheduled.cancel()
    }

    /// Atomically marks the operation complete before the timer wins.
    /// Returns false when the timeout already closed the channel.
    @discardableResult
    func complete() -> Bool {
        let completedBeforeTimeout = state.complete()
        scheduled.cancel()
        return completedBeforeTimeout
    }

    private final class State: @unchecked Sendable {
        private let lock = NSLock()
        private var isActive = true
        private var timedOut = false

        var didTimeOut: Bool {
            lock.lock()
            defer { lock.unlock() }
            return timedOut
        }

        func markTimedOut() -> Bool {
            lock.lock()
            defer { lock.unlock() }
            guard isActive else { return false }
            isActive = false
            timedOut = true
            return true
        }

        func complete() -> Bool {
            lock.lock()
            defer { lock.unlock() }
            guard isActive else { return false }
            isActive = false
            return true
        }
    }
}

/// Moves channel closure into `@Sendable` cancellation handlers without
/// changing NIO's global concurrency contract.
final class AppleNetworkChannelCloser: @unchecked Sendable {
    private let channel: Channel

    init(channel: Channel) {
        self.channel = channel
    }

    func close() {
        channel.close(promise: nil)
    }
}

/// Retains a channel while an async connection is in progress. Cancellation
/// can race channel creation, so close requests are remembered.
final class AppleNetworkChannelBox: @unchecked Sendable {
    private let lock = NSLock()
    private var channel: Channel?
    private var closeRequested = false

    func store(_ channel: Channel) {
        lock.lock()
        if closeRequested {
            lock.unlock()
            channel.close(promise: nil)
        } else {
            self.channel = channel
            lock.unlock()
        }
    }

    func close() {
        lock.lock()
        closeRequested = true
        let channel = channel
        self.channel = nil
        lock.unlock()
        channel?.close(promise: nil)
    }
}
