import SwiftUI
import KeyJawnKit

/// Full-screen terminal view for a specific SSH host.
/// Creates its own SSHSession and manages the full connect/disconnect lifecycle.
struct HostTerminalView: View {
    let host: HostConfig

    @StateObject private var session = SSHSession()
    @State private var showingPasswordPrompt = false

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
                showingPasswordPrompt = true
            }
        }
        .sheet(isPresented: $showingPasswordPrompt) {
            PasswordPromptView(host: host) { password in
                session.connect(to: host, password: password)
            }
        }
    }

    private var disconnectedOverlay: some View {
        VStack(spacing: 16) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("Not connected")
                .font(.title3)
            Button("Connect") { showingPasswordPrompt = true }
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
                showingPasswordPrompt = true
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
