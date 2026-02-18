import SwiftUI

struct SettingsView: View {
    @AppStorage("theme") private var theme = "dark"
    @AppStorage("hapticEnabled") private var hapticEnabled = true
    @AppStorage("autocorrectEnabled") private var autocorrectEnabled = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Keyboard") {
                    Toggle("Haptic feedback", isOn: $hapticEnabled)
                    Toggle("Autocorrect", isOn: $autocorrectEnabled)
                }

                Section("Appearance") {
                    Picker("Theme", selection: $theme) {
                        Text("Dark").tag("dark")
                        Text("Light").tag("light")
                        Text("OLED").tag("oled")
                        Text("Terminal").tag("terminal")
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: "1.0.0")
                    Link("Privacy policy", destination: URL(string: "https://keyjawn.amditis.tech/privacy")!)
                    Link("Manual", destination: URL(string: "https://keyjawn.amditis.tech/manual")!)
                }
            }
            .navigationTitle("Settings")
        }
    }
}
