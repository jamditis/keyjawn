import SwiftUI
import KeyJawnKit

struct ContentView: View {
    var body: some View {
        TabView {
            TerminalViewControllerRepresentable()
                .tabItem { Label("Terminal", systemImage: "terminal") }

            HostListView()
                .tabItem { Label("Hosts", systemImage: "server.rack") }

            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - UIViewControllerRepresentable

struct TerminalViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> TerminalViewController {
        TerminalViewController()
    }
    func updateUIViewController(_ uiViewController: TerminalViewController, context: Context) {}
}
