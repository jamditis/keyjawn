import KeyJawnKit
import SwiftUI

struct HostListView: View {
    @EnvironmentObject private var hostStore: HostStore
    @State private var showingAddHost = false
    @State private var editingHost: HostConfig?

    var body: some View {
        NavigationStack {
            Group {
                if hostStore.hosts.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Hosts")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingAddHost = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add host")
                }
            }
            .sheet(isPresented: $showingAddHost) {
                HostEditView { newHost in
                    hostStore.add(newHost)
                }
            }
            .sheet(item: $editingHost) { host in
                HostEditView(host: host) { updated in
                    hostStore.update(updated)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            AppPromptMark()
            Text("No hosts yet")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(AppTheme.text)
            Text(
                "Connect to a remote SSH server. Commands run on that server. "
                    + "KeyJawn cannot browse files on your iPhone or iPad."
            )
            .foregroundStyle(AppTheme.secondaryText)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 40)
            Button("Add host") {
                showingAddHost = true
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(28)
        .background(AppTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(AppTheme.border, lineWidth: 1)
        }
        .padding(24)
    }

    private var list: some View {
        List {
            ForEach(hostStore.hosts) { host in
                NavigationLink(value: host) {
                    HostRow(host: host)
                }
                // Tapping a row connects, so editing needs its own affordance. Without
                // one there was no way to correct a hostname or add a host key after
                // the fact short of deleting the host and typing it in again.
                .swipeActions(edge: .leading, allowsFullSwipe: true) {
                    Button {
                        editingHost = host
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    .tint(.blue)
                }
                .contextMenu {
                    Button {
                        editingHost = host
                    } label: {
                        Label("Edit host", systemImage: "pencil")
                    }
                }
            }
            .onDelete { indices in
                hostStore.delete(at: indices)
            }
        }
        .appFormBackground()
        .navigationDestination(for: HostConfig.self) { host in
            HostTerminalView(host: host)
        }
    }
}

struct HostRow: View {
    let host: HostConfig

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text(host.label)
                    .fontWeight(.medium)
                // Surface the unverified state before the user connects, rather than
                // only in a toolbar button once the session is already live.
                if !host.hasPinnedHostKey {
                    Image(systemName: "shield.slash")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                        .accessibilityLabel("Host key not verified")
                }
            }
            Text("\(host.username)@\(host.hostname):\(host.port)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fontDesign(.monospaced)
        }
        .padding(.vertical, 2)
    }
}
