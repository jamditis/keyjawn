import SwiftUI
import KeyJawnKit

struct HostListView: View {
    @State private var hosts: [HostConfig] = []
    @State private var showingAddHost = false
    @State private var selectedHost: HostConfig?

    var body: some View {
        NavigationStack {
            Group {
                if hosts.isEmpty {
                    emptyState
                } else {
                    list
                }
            }
            .navigationTitle("Hosts")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingAddHost = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingAddHost) {
                HostEditView { newHost in
                    hosts.append(newHost)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No hosts yet")
                .font(.title3)
                .fontWeight(.semibold)
            Text("Add an SSH host to start a terminal session.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button("Add host") {
                showingAddHost = true
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private var list: some View {
        List {
            ForEach(hosts) { host in
                NavigationLink(value: host) {
                    HostRow(host: host)
                }
            }
            .onDelete { indices in
                hosts.remove(atOffsets: indices)
            }
        }
        .navigationDestination(for: HostConfig.self) { host in
            // TODO: navigate to terminal for this host
            Text("Terminal for \(host.label)")
        }
    }
}

struct HostRow: View {
    let host: HostConfig

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(host.label)
                .fontWeight(.medium)
            Text("\(host.username)@\(host.hostname):\(host.port)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fontDesign(.monospaced)
        }
        .padding(.vertical, 2)
    }
}
