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
    @State private var extraRowPreset = KeyboardPrefs.shared.extraRowPreset
    @State private var customTrigger = ""
    @State private var customDescription = ""
    @State private var customError = ""
    @State private var customCommands = SlashCommandStore.load(from: SlashCommandStore.appGroupDefaults())

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
                    Picker("Preset", selection: $extraRowPreset) {
                        ForEach(ExtraRowPreset.allCases, id: \.self) { option in
                            Text(option.displayName).tag(option)
                        }
                    }
                    .onChange(of: extraRowPreset) { _, newValue in
                        KeyboardPrefs.shared.extraRowPreset = newValue
                    }
                    Toggle("Terminal arrow keys", isOn: $terminalArrowKeys)
                        .onChange(of: terminalArrowKeys) { _, newValue in
                            KeyboardPrefs.shared.terminalArrowKeys = newValue
                        }
                } header: {
                    Text("Extra row")
                } footer: {
                    Text(extraRowPreset == .confirm
                         ? "Confirm types y, n, a, 1, 2, 3 and can submit. Agent is the default ten-key row."
                         : terminalArrowKeys
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

                Section {
                    ForEach(customCommands, id: \.id) { record in
                        HStack {
                            Text(record.trigger)
                                .font(.body.monospaced())
                            Spacer()
                            Button("Delete") {
                                SlashCommandStore.remove(id: record.id, from: SlashCommandStore.appGroupDefaults())
                                customCommands = SlashCommandStore.load(from: SlashCommandStore.appGroupDefaults())
                            }
                            .foregroundStyle(.red)
                        }
                    }
                    TextField("Trigger", text: $customTrigger)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Description", text: $customDescription)
                    Button("Add shortcut") {
                        switch SlashCommandStore.add(
                            trigger: customTrigger,
                            description: customDescription,
                            to: SlashCommandStore.appGroupDefaults()
                        ) {
                        case .success:
                            customTrigger = ""
                            customDescription = ""
                            customError = ""
                            customCommands = SlashCommandStore.load(from: SlashCommandStore.appGroupDefaults())
                        case .failure(.duplicateTrigger):
                            customError = "That shortcut already exists"
                        case .failure(.invalidTrigger):
                            customError = "Use a short /name with no spaces"
                        }
                    }
                    if !customError.isEmpty {
                        Text(customError)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                } header: {
                    Text("Custom shortcuts")
                } footer: {
                    Text("Shown in the slash panel. Inserts the trigger as plain text. Full Access is required for the keyboard extension to see them.")
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
