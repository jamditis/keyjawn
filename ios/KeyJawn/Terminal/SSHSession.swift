import Foundation
import Citadel
import NIOCore
@preconcurrency import NIOSSH
import KeyJawnKit

// Citadel 0.12.0 doesn't yet declare Sendable conformances for these types,
// but the underlying NIO Channel and AsyncThrowingStream are both thread-safe.
extension TTYOutput: @unchecked @retroactive Sendable {}
extension TTYStdinWriter: @unchecked @retroactive Sendable {}

/// Manages a single interactive SSH session via Citadel/NIO.
///
/// All public APIs are @MainActor isolated. To stay clean under Swift 6's
/// region-based isolation rules, the actual SSH work runs in a Task.detached
/// with no direct reference to `self`. Instead, two @Sendable callbacks
/// (created on the main actor before the task launches) are passed in — they
/// close over `self` weakly and hop back to the main actor via
/// `Task { @MainActor in }` when state or output needs updating.
@MainActor
final class SSHSession: ObservableObject {

    enum ConnectionState: Equatable {
        case disconnected
        case connecting
        case connected
        case failed(String)
    }

    @Published private(set) var connectionState: ConnectionState = .disconnected

    /// Called on the main actor each time SSH output arrives.
    var onData: (([UInt8]) -> Void)?

    private var sessionTask: Task<Void, Never>?
    private var inputContinuation: AsyncStream<[UInt8]>.Continuation?

    // MARK: - Connect

    func connect(to host: HostConfig, password: String) {
        guard connectionState == .disconnected else { return }
        connectionState = .connecting

        // Capture callbacks as @Sendable closures here, in @MainActor scope,
        // so 'self' never crosses a region boundary inside the detached task.
        let onReceive: @Sendable ([UInt8]) -> Void = { [weak self] bytes in
            Task { @MainActor in self?.onData?(bytes) }
        }
        let onStateChange: @Sendable (ConnectionState) -> Void = { [weak self] state in
            Task { @MainActor in self?.connectionState = state }
        }

        let (inputStream, continuation) = AsyncStream<[UInt8]>.makeStream()
        inputContinuation = continuation

        sessionTask = Task.detached { [host, password, inputStream, onReceive, onStateChange] in
            do {
                let client = try await SSHClient.connect(
                    host: host.hostname,
                    port: Int(host.port),
                    authenticationMethod: .passwordBased(username: host.username, password: password),
                    hostKeyValidator: .acceptAnything(),
                    reconnect: .never
                )
                onStateChange(.connected)

                let ptyRequest = SSHChannelRequestEvent.PseudoTerminalRequest(
                    wantReply: true,
                    term: "xterm-256color",
                    terminalCharacterWidth: 80,
                    terminalRowHeight: 24,
                    terminalPixelWidth: 0,
                    terminalPixelHeight: 0,
                    terminalModes: .init([:])
                )

                try await client.withPTY(ptyRequest) { ttyOutput, stdinWriter in
                    await withThrowingTaskGroup(of: Void.self) { group in
                        // Pump SSH output → onReceive callback
                        group.addTask {
                            for try await chunk in ttyOutput {
                                let bytes: [UInt8]
                                switch chunk {
                                case .stdout(let buf): bytes = Array(buf.readableBytesView)
                                case .stderr(let buf): bytes = Array(buf.readableBytesView)
                                }
                                onReceive(bytes)
                            }
                        }
                        // Pump keyboard input → SSH channel
                        group.addTask {
                            for await bytes in inputStream {
                                var buf = ByteBufferAllocator().buffer(capacity: bytes.count)
                                buf.writeBytes(bytes)
                                try await stdinWriter.write(buf)
                            }
                        }
                        // Stop when either stream closes
                        _ = try? await group.next()
                        group.cancelAll()
                    }
                }

                onStateChange(.disconnected)
            } catch is CancellationError {
                onStateChange(.disconnected)
            } catch {
                onStateChange(.failed(error.localizedDescription))
            }
        }
    }

    // MARK: - Send / Disconnect

    /// Write raw bytes to the SSH shell (e.g. a keypress or ANSI sequence).
    func send(_ bytes: [UInt8]) {
        inputContinuation?.yield(bytes)
    }

    func disconnect() {
        inputContinuation?.finish()
        inputContinuation = nil
        sessionTask?.cancel()
        sessionTask = nil
        connectionState = .disconnected
    }
}
