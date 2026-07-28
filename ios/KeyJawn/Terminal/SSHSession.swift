import Foundation
import Citadel
import NIOCore
import NIOPosix
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
        case awaitingHostKey
        case connected
        case failed(String)
    }

    @Published private(set) var connectionState: ConnectionState = .disconnected
    /// The key awaiting an explicit first-use decision in the main app.
    @Published private(set) var pendingHostKey: PresentedHostKey?

    /// Called on the main actor each time SSH output arrives.
    var onData: (([UInt8]) -> Void)?

    private var sessionTask: Task<Void, Never>?
    private var inputContinuation: AsyncStream<[UInt8]>.Continuation?
    private var resizeContinuation: AsyncStream<(Int, Int)>.Continuation?
    private var pendingAuthenticationMethod: SSHAuthenticationMethod?
    private var connectionGeneration = UUID()

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
        // Guard on what actually conflicts — a connection in flight or established —
        // rather than on being exactly `.disconnected`. The old check also rejected
        // `.failed`, so retrying after an error silently did nothing unless the caller
        // happened to call `disconnect()` first.
        switch connectionState {
        case .connecting, .awaitingHostKey, .connected: return
        case .disconnected, .failed: break
        }
        let generation = UUID()
        connectionGeneration = generation
        pendingAuthenticationMethod = authenticationMethod
        pendingHostKey = nil
        connectionState = .connecting

        // Coalesce SSH output off-main and hand it to the terminal one batch per
        // main-actor hop, instead of one Task and one array copy per network
        // chunk (see SSHOutputCoalescer).
        let coalescer = SSHOutputCoalescer { [weak self] bytes in self?.onData?(bytes) }
        let onStateChange: @Sendable (ConnectionState) -> Void = { [weak self] state in
            Task { @MainActor in
                guard let self, self.connectionGeneration == generation else { return }
                self.connectionState = state
                switch state {
                case .connected, .disconnected, .failed:
                    self.pendingAuthenticationMethod = nil
                case .connecting, .awaitingHostKey:
                    break
                }
            }
        }
        let captureHostKey: TOFUHostKeyValidator.CaptureHostKey = {
            [weak self] presentedKey in
            Task { @MainActor in
                guard let self, self.connectionGeneration == generation else { return }
                self.pendingHostKey = presentedKey
            }
        }

        let pinnedHostKey: NIOSSHPublicKey?
        do {
            pinnedHostKey = try hostPublicKey(from: host)
        } catch {
            pendingAuthenticationMethod = nil
            connectionState = .failed("Saved host key is invalid. Edit the host and re-enter the key from ssh-keyscan.")
            return
        }

        guard let pinnedHostKey else {
            sessionTask = Task.detached {
                [host, authenticationMethod, captureHostKey, onStateChange] in
                do {
                    try await probeFirstUseHostKey(
                        host: host,
                        authenticationMethod: authenticationMethod,
                        captureHostKey: captureHostKey
                    )
                    onStateChange(.awaitingHostKey)
                } catch is CancellationError {
                    onStateChange(.disconnected)
                } catch {
                    if Task.isCancelled {
                        onStateChange(.disconnected)
                    } else {
                        onStateChange(.failed(error.localizedDescription))
                    }
                }
            }
            return
        }

        let validator = SSHHostKeyValidator.trustedKeys(Set([pinnedHostKey]))
        let (inputStream, inputCont) = AsyncStream<[UInt8]>.makeStream()
        let (resizeStream, resizeCont) = AsyncStream<(Int, Int)>.makeStream()
        inputContinuation = inputCont
        resizeContinuation = resizeCont

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
                if Task.isCancelled {
                    onStateChange(.disconnected)
                } else if error is InvalidHostKey {
                    onStateChange(.failed(
                        "REMOTE HOST IDENTIFICATION HAS CHANGED for \(host.hostname). "
                        + "Verify the server before replacing the saved host key."
                    ))
                } else {
                    onStateChange(.failed(error.localizedDescription))
                }
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

    /// Starts a fresh, pinned connection after the caller has durably stored the key.
    func connectAfterTrust(to host: HostConfig) {
        guard let presentedKey = pendingHostKey,
              let storedKey = host.hostPublicKey,
              (try? presentedKey.matches(openSSHKey: storedKey)) == true,
              let authenticationMethod = pendingAuthenticationMethod else {
            rejectPendingHostKey(.invalidPresentedKey)
            return
        }

        invalidateCurrentConnection()
        pendingHostKey = nil
        connectionState = .disconnected
        connect(to: host, authenticationMethod: authenticationMethod)
    }

    func rejectPendingHostKey(_ error: HostKeyTrustError = .rejected) {
        guard pendingHostKey != nil || connectionState == .awaitingHostKey else { return }
        invalidateCurrentConnection()
        pendingHostKey = nil
        pendingAuthenticationMethod = nil
        connectionState = .failed(error.localizedDescription)
    }

    func disconnect() {
        invalidateCurrentConnection()
        pendingHostKey = nil
        pendingAuthenticationMethod = nil
        connectionState = .disconnected
    }

    private func invalidateCurrentConnection() {
        connectionGeneration = UUID()
        inputContinuation?.finish()
        resizeContinuation?.finish()
        inputContinuation = nil
        resizeContinuation = nil
        sessionTask?.cancel()
        sessionTask = nil
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

enum HostKeyTrustError: Error, LocalizedError, Sendable, Equatable {
    case trustRequired
    case rejected
    case storageFailed
    case invalidPresentedKey
    case probeTimedOut

    var errorDescription: String? {
        switch self {
        case .trustRequired:
            return "Host key confirmation is required."
        case .rejected:
            return "Host key was not trusted."
        case .storageFailed:
            return "The host key could not be saved, so the connection was stopped."
        case .invalidPresentedKey:
            return "The server presented an invalid host key."
        case .probeTimedOut:
            return "Timed out waiting for the server's SSH host key."
        }
    }
}

/// Captures a first-use key and rejects the probe before SSH authentication starts.
///
/// Holding validation open while a user verifies a fingerprint leaves an SSH
/// transport pending indefinitely. This delegate always closes and fails the owned
/// probe after capture. Acceptance is handled by saving the key and opening a fresh
/// Citadel connection with `.trustedKeys`.
final class TOFUHostKeyValidator: NIOSSHClientServerAuthenticationDelegate, @unchecked Sendable {
    typealias CaptureHostKey = @Sendable (PresentedHostKey) -> Void

    private let captureHostKey: CaptureHostKey
    private let closeProbe: @Sendable () -> Void

    init(
        captureHostKey: @escaping CaptureHostKey,
        closeProbe: @escaping @Sendable () -> Void
    ) {
        self.captureHostKey = captureHostKey
        self.closeProbe = closeProbe
    }

    func captureAndReject(openSSHKey: String) -> HostKeyTrustError {
        defer { closeProbe() }
        do {
            captureHostKey(try PresentedHostKey(openSSHKey: openSSHKey))
            return .trustRequired
        } catch {
            return .invalidPresentedKey
        }
    }

    func validateHostKey(
        hostKey: NIOSSHPublicKey,
        validationCompletePromise: EventLoopPromise<Void>
    ) {
        validationCompletePromise.fail(
            captureAndReject(openSSHKey: String(openSSHPublicKey: hostKey))
        )
    }
}

/// Opens an SSH transport only long enough to capture its first host key.
///
/// The probe owns the NIO channel directly because Citadel does not expose a channel
/// when host-key validation fails during `SSHClient.connect`. Owning it here lets the
/// validator close the socket before returning its mandatory rejection, so no
/// unauthenticated connection remains open while the user reviews the fingerprint.
private func probeFirstUseHostKey(
    host: HostConfig,
    authenticationMethod: SSHAuthenticationMethod,
    captureHostKey: @escaping TOFUHostKeyValidator.CaptureHostKey
) async throws {
    let channelBox = HostKeyProbeChannelBox()
    let (presentedKeys, presentedKeyContinuation) =
        AsyncThrowingStream<PresentedHostKey, Error>.makeStream()
    let validator = TOFUHostKeyValidator(
        captureHostKey: { presentedKey in
            captureHostKey(presentedKey)
            presentedKeyContinuation.yield(presentedKey)
            presentedKeyContinuation.finish()
        },
        closeProbe: {
            channelBox.close()
        }
    )
    let errorHandler = HostKeyProbeErrorHandler { error in
        presentedKeyContinuation.finish(throwing: error)
    }

    try await withTaskCancellationHandler {
        defer {
            presentedKeyContinuation.finish()
            channelBox.close()
        }

        let bootstrap = ClientBootstrap(group: MultiThreadedEventLoopGroup.singleton)
            .channelInitializer { channel in
                channelBox.store(channel)
                let configuration = SSHClientConfiguration(
                    // Host-key validation runs before user authentication. The probe
                    // rejects and closes at that boundary, so this delegate is retained
                    // for the later pinned connection but never offers a credential here.
                    userAuthDelegate: authenticationMethod,
                    serverAuthDelegate: validator
                )
                return channel.pipeline.addHandlers(
                    NIOSSHHandler(
                        role: .client(configuration),
                        allocator: channel.allocator,
                        inboundChildChannelInitializer: nil
                    ),
                    errorHandler
                )
            }
            .connectTimeout(.seconds(30))

        _ = try await bootstrap.connect(
            host: host.hostname,
            port: Int(host.port)
        ).get()

        _ = try await nextPresentedHostKey(
            from: presentedKeys,
            timeout: .seconds(30),
            onTimeout: {
                presentedKeyContinuation.finish(
                    throwing: HostKeyTrustError.probeTimedOut
                )
                channelBox.close()
            }
        )
    } onCancel: {
        presentedKeyContinuation.finish(throwing: CancellationError())
        channelBox.close()
    }
}

func nextPresentedHostKey(
    from presentedKeys: AsyncThrowingStream<PresentedHostKey, Error>,
    timeout: Duration,
    onTimeout: @escaping @Sendable () -> Void
) async throws -> PresentedHostKey {
    try await withThrowingTaskGroup(of: PresentedHostKey?.self) { group in
        group.addTask {
            var iterator = presentedKeys.makeAsyncIterator()
            return try await iterator.next()
        }
        group.addTask {
            try await Task.sleep(for: timeout)
            try Task.checkCancellation()
            onTimeout()
            throw HostKeyTrustError.probeTimedOut
        }

        defer { group.cancelAll() }
        guard let result = try await group.next(), let presentedKey = result else {
            throw HostKeyTrustError.invalidPresentedKey
        }
        return presentedKey
    }
}

/// Retains the probe channel across task cancellation and closes it at most once.
private final class HostKeyProbeChannelBox: @unchecked Sendable {
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

/// Converts every probe pipeline failure or early disconnect into stream completion
/// and closes the owned socket. This is a backup for failures that occur before a
/// well-formed host key reaches the validator.
private final class HostKeyProbeErrorHandler: ChannelInboundHandler, @unchecked Sendable {
    typealias InboundIn = Any

    private let finish: @Sendable (Error) -> Void

    init(finish: @escaping @Sendable (Error) -> Void) {
        self.finish = finish
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        finish(error)
        context.close(promise: nil)
    }

    func channelInactive(context: ChannelHandlerContext) {
        finish(ChannelError.eof)
        context.fireChannelInactive()
    }
}

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
