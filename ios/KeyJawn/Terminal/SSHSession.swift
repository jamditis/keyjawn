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
/// with no direct reference to `self`. Instead, @Sendable callbacks created
/// on the main actor before the task launches close over `self` weakly and
/// hop back to the main actor via `Task { @MainActor in }`.
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
    private var resizeContinuation: AsyncStream<(Int, Int)>.Continuation?

    // MARK: - Connect

    func connect(to host: HostConfig, password: String) {
        guard connectionState == .disconnected else { return }
        connectionState = .connecting

        let onReceive: @Sendable ([UInt8]) -> Void = { [weak self] bytes in
            Task { @MainActor in self?.onData?(bytes) }
        }
        let onStateChange: @Sendable (ConnectionState) -> Void = { [weak self] state in
            Task { @MainActor in self?.connectionState = state }
        }

        let (inputStream, inputCont) = AsyncStream<[UInt8]>.makeStream()
        let (resizeStream, resizeCont) = AsyncStream<(Int, Int)>.makeStream()
        inputContinuation = inputCont
        resizeContinuation = resizeCont

        let validator: SSHHostKeyValidator = hostPublicKey(from: host).map { .trustedKeys(Set([$0])) }
            ?? .acceptAnything()

        sessionTask = Task.detached { [host, password, inputStream, resizeStream, onReceive, onStateChange, validator] in
            do {
                let client = try await SSHClient.connect(
                    host: host.hostname,
                    port: Int(host.port),
                    authenticationMethod: .passwordBased(username: host.username, password: password),
                    hostKeyValidator: validator,
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
                        // Handle terminal resize requests
                        group.addTask {
                            for await (cols, rows) in resizeStream {
                                try await stdinWriter.changeSize(
                                    cols: cols, rows: rows,
                                    pixelWidth: 0, pixelHeight: 0
                                )
                            }
                        }
                        // Stop when any stream closes
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

    // MARK: - Send / Resize / Disconnect

    func send(_ bytes: [UInt8]) {
        inputContinuation?.yield(bytes)
    }

    /// Notify the remote PTY of a terminal dimension change.
    func resize(cols: Int, rows: Int) {
        resizeContinuation?.yield((cols, rows))
    }

    func disconnect() {
        inputContinuation?.finish()
        resizeContinuation?.finish()
        inputContinuation = nil
        resizeContinuation = nil
        sessionTask?.cancel()
        sessionTask = nil
        connectionState = .disconnected
    }
}

/// Parses a host's stored public key string into an NIOSSHPublicKey for host key pinning.
/// Accepts OpenSSH format: "ssh-ed25519 <base64>" (from ssh-keyscan -t ed25519).
/// Returns nil if no key is configured or the key cannot be parsed.
private func hostPublicKey(from host: HostConfig) -> NIOSSHPublicKey? {
    guard let keyString = host.hostPublicKey else { return nil }
    return try? NIOSSHPublicKey(openSSHPublicKey: keyString)
}
