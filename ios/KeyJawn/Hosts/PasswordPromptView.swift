import SwiftUI
import KeyJawnKit

struct PasswordPromptView: View {
    let host: HostConfig
    let onConnect: (String) -> Void

    @State private var password = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Server") {
                    LabeledContent("Host") { Text(host.hostname).foregroundStyle(.secondary) }
                    LabeledContent("User") { Text(host.username).foregroundStyle(.secondary) }
                    LabeledContent("Port") { Text("\(host.port)").foregroundStyle(.secondary) }
                }
                Section("Authentication") {
                    SecureField("Password", text: $password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle("Connect to \(host.label)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Connect") {
                        onConnect(password)
                        dismiss()
                    }
                    .disabled(password.isEmpty)
                }
            }
        }
    }
}
