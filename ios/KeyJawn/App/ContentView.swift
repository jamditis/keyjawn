import SwiftUI
import KeyJawnKit

struct ContentView: View {

    private enum Tab: Hashable {
        case hosts, preview, settings
    }

    // Hosts, not the terminal. The terminal tab has no session behind it, so opening
    // the app onto it presented a prompt that echoes keystrokes and connects to
    // nothing — a first launch that looks broken. Hosts is where the app actually
    // starts: add a server, tap it, get a shell.
    @State private var selection: Tab = .hosts

    var body: some View {
        TabView(selection: $selection) {
            HostListView()
                .tabItem { Label("Hosts", systemImage: "server.rack") }
                .tag(Tab.hosts)

            TerminalPreviewView()
                .tabItem { Label("Preview", systemImage: "terminal") }
                .tag(Tab.preview)

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Terminal preview

/// A local-echo terminal for trying the extra row without a server.
///
/// It used to be the app's first tab, labelled simply "Terminal", which made a scratch
/// pad look like a broken SSH session. Named and titled for what it is instead.
struct TerminalPreviewView: View {
    var body: some View {
        NavigationStack {
            TerminalViewControllerRepresentable()
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("Preview")
                .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - UIViewControllerRepresentable

struct TerminalViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> TerminalViewController {
        TerminalViewController()
    }
    func updateUIViewController(_ uiViewController: TerminalViewController, context: Context) {}
}
