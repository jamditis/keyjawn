import Foundation
import Citadel
import NIOCore
@preconcurrency import NIOSSH
import KeyJawnKit

// Citadel doesn't yet declare Sendable conformances for these types,
// but the underlying NIO Channel, AsyncThrowingStream, and auth delegate are thread-safe.
extension TTYOutput: @unchecked @retroactive Sendable {}
extension TTYStdinWriter: @unchecked @retroactive Sendable {}
extension SSHAuthenticationMethod: @unchecked @retroactive Sendable {}

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
    /// True when the current (or most recent) connection used a pinned host key.
    /// False when `.acceptAnything()` was used — the server's identity is unverified.
    @Published private(set) var isHostKeyVerified: Bool = false

    /// Called on the main actor each time SSH output arrives.
    var onData: (([UInt8]) -> Void)?

    private var sessionTask: Task<Void, Never>?
    private var inputContinuation: AsyncStream<[UInt8]>.Continuation?
    private var resizeContinuation: AsyncStream<(Int, Int)>.Continuation?

    // MARK: - Connect

    /// Connects using password authentication.
    func connect(to host: HostConfig, password: String) {
        let authMethod = SSHAuthenticationMethod.passwordBased(username: host.username, password: password)
        connect(to: host, authenticationMethod: authMethod)
    }

    /// Connects using the app's Ed25519 identity key from the Keychain.
    func connectWithKey(to host: HostConfig) {
        let authMethod = SSHAuthenticationMethod.ed25519(
            username: host.username,
            privateKey: SSHKeyStore.shared.privateKey
        )
        connect(to: host, authenticationMethod: authMethod)
    }

    private func connect(to host: HostConfig, authenticationMethod: SSHAuthenticationMethod) {
        guard connectionState == .disconnected else { return }
        connectionState = .connecting
        isHostKeyVerified = host.hostPublicKey != nil

        // Coalesce SSH output off-main and hand it to the terminal one batch per
        // main-actor hop, instead of one Task and one array copy per network
        // chunk (see SSHOutputCoalescer).
        let coalescer = SSHOutputCoalescer { [weak self] bytes in self?.onData?(bytes) }
        let onStateChange: @Sendable (ConnectionState) -> Void = { [weak self] state in
            Task { @MainActor in self?.connectionState = state }
        }

        let (inputStream, inputCont) = AsyncStream<[UInt8]>.makeStream()
        let (resizeStream, resizeCont) = AsyncStream<(Int, Int)>.makeStream()
        inputContinuation = inputCont
        resizeContinuation = resizeCont

        let validator: SSHHostKeyValidator
        do {
            validator = try hostPublicKey(from: host).map { .trustedKeys(Set([$0])) } ?? .acceptAnything()
        } catch {
            connectionState = .failed("Saved host key is invalid. Edit the host and re-enter the key from ssh-keyscan.")
            return
        }

        sessionTask = Task.detached { [host, authenticationMethod, inputStream, resizeStream, coalescer, onStateChange, validator] in
            do {
                let client = try await SSHClient.connect(
                    host: host.hostname,
                    port: Int(host.port),
                    authenticationMethod: authenticationMethod,
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
                        // Pump SSH output → coalescer → main-actor terminal feed
                        group.addTask {
                            for try await chunk in ttyOutput {
                                // Copy the readable bytes straight into the
                                // coalescer's pending batch; it owns the single
                                // main-actor hop and the array hand-off.
                                switch chunk {
                                case .stdout(let buf): coalescer.append(buf.readableBytesView)
                                case .stderr(let buf): coalescer.append(buf.readableBytesView)
                                }
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
        isHostKeyVerified = false
        connectionState = .disconnected
    }
}

/// Parses a host's stored public key string into an NIOSSHPublicKey for host key pinning.
/// Accepts OpenSSH format: "ssh-ed25519 <base64>" (from ssh-keyscan -t ed25519).
/// Returns nil if no key is configured. Throws if a key is configured but cannot be parsed,
/// so the caller can fail explicitly rather than silently falling back to no verification.
private func hostPublicKey(from host: HostConfig) throws -> NIOSSHPublicKey? {
    guard let keyString = host.hostPublicKey, !keyString.isEmpty else { return nil }
    guard let key = try? NIOSSHPublicKey(openSSHPublicKey: keyString) else {
        throw HostKeyParseError()
    }
    return key
}

private struct HostKeyParseError: Error {}

/// Coalesces SSH output chunks that arrive off the main actor into one batched
/// hand-off per main-actor hop, instead of one unstructured `Task` and one array
/// copy per network chunk.
///
/// Under high-throughput output (an agent streaming tokens, a file dump) NIO SSH
/// delivers many small chunks in a burst. Feeding each through its own
/// `Task { @MainActor }` floods the main thread with hops and array copies at the
/// exact moment it is busiest with key handling and rendering, which the user
/// feels as input lag and choppy scrolling. Here each chunk is appended under a
/// lock, and only the first append after an idle stretch schedules a single
/// main-actor flush; every chunk that lands before that flush runs is folded into
/// the same batch. A burst of N chunks collapses to one hop and one delivered
/// array, with no timer or display link and no added latency beyond the
/// main-actor hop that was already happening. It does not remove the terminal's
/// VT-parse cost, which is per-byte regardless; it removes the per-chunk hop and
/// allocation churn around it.
///
/// Delivery preserves arrival order, and not only within a batch. The lock keeps
/// each batch's bytes in arrival order, and `flushScheduled` keeps at most one
/// flush in flight, so batch N is delivered before batch N+1 is ever scheduled.
/// That makes cross-batch order structural rather than dependent on the order in
/// which unstructured tasks happen to run, which Swift does not guarantee.
///
/// `@unchecked Sendable` because the mutable batch is reached from both the
/// off-main read loop and the main-actor flush; the lock below is what keeps
/// those touches race-free, so do not drop it.
final class SSHOutputCoalescer: @unchecked Sendable {
    private let lock = NSLock()
    private var pending: [UInt8] = []
    private var flushScheduled = false
    private let deliver: @MainActor ([UInt8]) -> Void

    /// - Parameter deliver: called on the main actor with each coalesced batch,
    ///   in arrival order.
    init(deliver: @escaping @MainActor ([UInt8]) -> Void) {
        self.deliver = deliver
    }

    /// Append one chunk's bytes. Called off the main actor from the SSH read
    /// loop. The bytes are copied into the pending batch synchronously, so the
    /// caller's `ByteBuffer` is free to be reused the moment this returns, and a
    /// main-actor flush is scheduled only when one is not already pending.
    func append<Bytes: Sequence>(_ bytes: Bytes) where Bytes.Element == UInt8 {
        lock.lock()
        pending.append(contentsOf: bytes)
        let scheduleFlush = !flushScheduled
        if scheduleFlush { flushScheduled = true }
        lock.unlock()
        guard scheduleFlush else { return }
        Task { @MainActor in self.flush() }
    }

    @MainActor
    private func flush() {
        lock.lock()
        let batch = pending
        pending = []
        flushScheduled = false
        lock.unlock()
        // Deliver outside the lock on purpose: the terminal feed (VT parsing and
        // rendering) can be slow, and holding the lock across it would stall an
        // off-main append that only wants to add bytes to the next batch.
        guard !batch.isEmpty else { return }
        deliver(batch)
    }
}
