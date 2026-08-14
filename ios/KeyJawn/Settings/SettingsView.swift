import SwiftUI
import KeyJawnKit

struct SettingsView: View {

    // Backed by KeyboardPrefs, which writes the shared App Group suite. These used to
    // be @AppStorage properties in the app's own sandbox under keys nothing ever read:
    // the theme picker changed nothing, the haptics switch changed nothing, and the
    // autocorrect switch was not wired to any code at all. Every control here now
    // changes what the keyboard does.
    @Binding var showOnboarding: Bool
    @State private var theme = KeyboardPrefs.shared.theme
    @State private var hapticsEnabled = KeyboardPrefs.shared.hapticsEnabled
    @State private var terminalArrowKeys = KeyboardPrefs.shared.terminalArrowKeys

    var body: some View {
        NavigationStack {
            Form {
                Section("SSH") {
                    NavigationLink("SSH keys") {
                        SSHKeysView()
                    }
                }

                Section {
                    Button(OnboardingCopy.reopenTitle) {
                        showOnboarding = true
                    }
                    .accessibilityIdentifier("settings.setupKeyboard")
                } header: {
                    Text("Setup")
                } footer: {
                    Text("How to enable KeyJawn Keyboard and add a host. You can skip it.")
                        .font(.caption)
                }

                Section {
                    Toggle("Key press feedback", isOn: $hapticsEnabled)
                        .onChange(of: hapticsEnabled) { _, newValue in
                            KeyboardPrefs.shared.hapticsEnabled = newValue
                        }
                } header: {
                    Text("Keyboard")
                } footer: {
                    Text("The system's own keyboard feedback settings still apply on top of this.")
                        .font(.caption)
                }

                Section {
                    Toggle("Terminal arrow keys", isOn: $terminalArrowKeys)
                        .onChange(of: terminalArrowKeys) { _, newValue in
                            KeyboardPrefs.shared.terminalArrowKeys = newValue
                        }
                } header: {
                    Text("Extra row")
                } footer: {
                    Text(terminalArrowKeys
                         ? "Arrows send escape codes, so up and down reach shell history in a terminal app."
                         : "Left and right move the text cursor. Up and down have no effect with this off.")
                        .font(.caption)
                }

                Section {
                    Picker("Theme", selection: $theme) {
                        ForEach(KeyboardTheme.allCases, id: \.self) { option in
                            Text(option.displayName).tag(option)
                        }
                    }
                    .onChange(of: theme) { _, newValue in
                        KeyboardPrefs.shared.theme = newValue
                    }
                } header: {
                    Text("Appearance")
                } footer: {
                    Text("Applies to the KeyJawn keyboard. The keyboard needs Full Access to read this setting: Settings → General → Keyboard → Keyboards → KeyJawn.")
                        .font(.caption)
                }

                Section("About") {
                    LabeledContent("Version", value: Self.versionString)
                    Link("Privacy policy", destination: URL(string: "https://keyjawn.amditis.tech/privacy")!)
                    Link("Manual", destination: URL(string: "https://keyjawn.amditis.tech/manual")!)
                }
            }
            .navigationTitle("Settings")
        }
    }

    /// Read from the bundle rather than typed in by hand, so it cannot drift from the
    /// build the user is actually running.
    private static var versionString: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "\(short) (\(build))"
    }
}
