import SwiftUI
import KeyJawnKit
@preconcurrency import NIOSSH

struct HostEditView: View {
    let onSave: (HostConfig) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var label = ""
    @State private var hostname = ""
    @State private var port = "22"
    @State private var username = ""
    @State private var authMethod = HostConfig.AuthMethod.password
    @State private var hostPublicKey = ""

    private var isHostKeyValid: Bool {
        let trimmed = hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return true }
        return (try? NIOSSHPublicKey(openSSHPublicKey: trimmed)) != nil
    }

    private var isValid: Bool {
        !label.trimmingCharacters(in: .whitespaces).isEmpty &&
        !hostname.trimmingCharacters(in: .whitespaces).isEmpty &&
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        Int(port) != nil &&
        isHostKeyValid
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

                Section {
                    TextField("ssh-ed25519 AAAA...", text: $hostPublicKey, axis: .vertical)
                        .lineLimit(3...)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .font(.caption.monospaced())
                } header: {
                    Text("Host key (optional)")
                } footer: {
                    let trimmed = hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty && !isHostKeyValid {
                        Text("Invalid key format. Paste the full line from: ssh-keyscan -t ed25519 <hostname>")
                            .font(.caption)
                            .foregroundStyle(.red)
                    } else {
                        Text("Paste output of: ssh-keyscan -t ed25519 <hostname>\nIf omitted, the server's key is not verified.")
                            .font(.caption)
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
                        let trimmedKey = hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
                        let host = HostConfig(
                            label: label.trimmingCharacters(in: .whitespaces),
                            hostname: hostname.trimmingCharacters(in: .whitespaces),
                            port: UInt16(port) ?? 22,
                            username: username.trimmingCharacters(in: .whitespaces),
                            authMethod: authMethod,
                            hostPublicKey: trimmedKey.isEmpty ? nil : trimmedKey
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
