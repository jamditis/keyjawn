import SwiftUI
import KeyJawnKit

/// Full-screen terminal view for a specific SSH host.
/// Creates its own SSHSession and manages the full connect/disconnect lifecycle.
struct HostTerminalView: View {
    @EnvironmentObject private var hostStore: HostStore
    @State private var host: HostConfig
    @StateObject private var session = SSHSession()
    @State private var showingPasswordPrompt = false

    init(host: HostConfig) {
        _host = State(initialValue: host)
    }

    var body: some View {
        ZStack {
            HostTerminalRepresentable(session: session)
                .ignoresSafeArea()

            switch session.connectionState {
            case .disconnected:
                disconnectedOverlay

            case .connecting:
                VStack(spacing: 12) {
                    ProgressView()
                        .scaleEffect(1.5)
                    Text("Connecting to \(host.hostname)…")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.black.opacity(0.85))

            case .awaitingHostKey:
                VStack(spacing: 12) {
                    Image(systemName: "lock.shield")
                        .font(.system(size: 42))
                    Text("Waiting for host key approval")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.black.opacity(0.85))

            case .connected:
                EmptyView()

            case .failed(let message):
                errorOverlay(message: message)
            }
        }
        .navigationTitle(host.label)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if session.connectionState == .connected {
                    Button("Disconnect") { session.disconnect() }
                }
            }
        }
        .onAppear {
            if session.connectionState == .disconnected {
                startConnection()
            }
        }
        // Session teardown is NOT wired to .onDisappear: this view is a pushed
        // destination inside the Hosts tab of a TabView, and SwiftUI fires
        // .onDisappear on a plain tab switch too, which would drop a live session
        // the user is still using. Teardown lives in TerminalViewController's
        // viewDidDisappear, gated on whether the controller or any ancestor is
        // actually being removed, so it runs on a real pop or dismiss but never
        // on a tab switch.
        .sheet(isPresented: $showingPasswordPrompt) {
            PasswordPromptView(host: host) { password in
                session.connect(to: host, password: password)
            }
        }
        .alert("Trust this host key?", isPresented: hostKeyPromptIsPresented) {
            Button("Cancel", role: .cancel) {
                session.rejectPendingHostKey()
            }
            Button("Trust") {
                trustPendingHostKey()
            }
        } message: {
            if let presentedKey = session.pendingHostKey {
                Text(
                    "\(host.hostname):\(host.port) presented this key:\n\n"
                    + "\(presentedKey.fingerprint)\n\n"
                    + "Trust it only if the fingerprint matches the server. "
                    + "KeyJawn will save it and reject later changes."
                )
            }
        }
    }

    private var hostKeyPromptIsPresented: Binding<Bool> {
        Binding(
            get: { session.pendingHostKey != nil },
            // Both alert buttons explicitly finish the trust decision. Keeping this
            // setter passive avoids a framework-driven dismissal racing the button action.
            set: { _ in }
        )
    }

    private func trustPendingHostKey() {
        guard let presentedKey = session.pendingHostKey else { return }
        var updatedHost = host
        updatedHost.hostPublicKey = presentedKey.openSSHKey
        if hostStore.update(updatedHost) {
            host = updatedHost
            session.connectAfterTrust(to: updatedHost)
        } else {
            session.rejectPendingHostKey(.storageFailed)
        }
    }

    private func startConnection() {
        if host.authMethod == .key {
            session.connectWithKey(to: host)
        } else {
            showingPasswordPrompt = true
        }
    }

    private var disconnectedOverlay: some View {
        VStack(spacing: 16) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("Not connected")
                .font(.title3)
            Button("Connect") { startConnection() }
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.black.opacity(0.85))
    }

    private func errorOverlay(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 48))
                .foregroundStyle(.red)
            Text(message)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("Retry") {
                session.disconnect()
                startConnection()
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.black.opacity(0.85))
    }
}

// MARK: - UIViewControllerRepresentable

struct HostTerminalRepresentable: UIViewControllerRepresentable {
    let session: SSHSession

    func makeUIViewController(context: Context) -> TerminalViewController {
        TerminalViewController(session: session)
    }

    func updateUIViewController(_ uiViewController: TerminalViewController, context: Context) {}
}
