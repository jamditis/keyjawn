import KeyJawnKit
import SwiftUI

struct ContentView: View {

    private enum Tab: Hashable {
        case hosts, settings
    }

    // Hosts is the useful starting point: add a remote server, then open its terminal.
    @State private var selection: Tab = .hosts
    @State private var showOnboarding = Self.shouldShowOnboarding()

    /// UI tests pass `-ui-testing` so a reused simulator still presents first
    /// launch. The flag is not stored; production launches keep the prefs value.
    private static func shouldShowOnboarding() -> Bool {
        if ProcessInfo.processInfo.arguments.contains("-ui-testing") {
            return true
        }
        return !KeyboardPrefs.shared.hasCompletedOnboarding
    }

    var body: some View {
        TabView(selection: $selection) {
            HostListView()
                .tabItem { Label("Hosts", systemImage: "server.rack") }
                .tag(Tab.hosts)

            SettingsView(showOnboarding: $showOnboarding)
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .tint(AppTheme.mint)
        .background(AppTheme.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .fullScreenCover(isPresented: $showOnboarding) {
            OnboardingView {
                showOnboarding = false
            }
        }
    }
}
