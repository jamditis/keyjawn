import SwiftUI
import KeyJawnKit

struct HostEditView: View {
    let onSave: (HostConfig) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var label = ""
    @State private var hostname = ""
    @State private var port = "22"
    @State private var username = ""
    @State private var authMethod = HostConfig.AuthMethod.key

    private var isValid: Bool {
        !label.trimmingCharacters(in: .whitespaces).isEmpty &&
        !hostname.trimmingCharacters(in: .whitespaces).isEmpty &&
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        Int(port) != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Connection") {
                    LabeledContent("Label") {
                        TextField("e.g. houseofjawn", text: $label)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled()
                    }
                    LabeledContent("Hostname") {
                        TextField("hostname or IP", text: $hostname)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled()
                            .autocapitalization(.none)
                            .keyboardType(.URL)
                    }
                    LabeledContent("Port") {
                        TextField("22", text: $port)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.numberPad)
                    }
                    LabeledContent("Username") {
                        TextField("username", text: $username)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled()
                            .autocapitalization(.none)
                    }
                }

                Section("Auth") {
                    Picker("Method", selection: $authMethod) {
                        Text("SSH key").tag(HostConfig.AuthMethod.key)
                        Text("Password").tag(HostConfig.AuthMethod.password)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)

                    if authMethod == .key {
                        Text("Keys are managed in Settings → SSH Keys.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Add host")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let host = HostConfig(
                            label: label.trimmingCharacters(in: .whitespaces),
                            hostname: hostname.trimmingCharacters(in: .whitespaces),
                            port: UInt16(port) ?? 22,
                            username: username.trimmingCharacters(in: .whitespaces),
                            authMethod: authMethod
                        )
                        onSave(host)
                        dismiss()
                    }
                    .disabled(!isValid)
                }
            }
        }
    }
}
