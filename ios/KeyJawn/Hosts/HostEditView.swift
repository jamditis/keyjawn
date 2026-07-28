import SwiftUI
import KeyJawnKit
@preconcurrency import NIOSSH

/// Add or edit a host.
///
/// This was add-only: `HostStore.update` had no caller, a typo in a hostname meant
/// deleting the host and retyping it, and the "host key not verified" warning told
/// users to add a key in host settings — a screen that did not exist. Passing an
/// existing host here reuses its `id`, so the store updates in place instead of
/// appending a duplicate.
struct HostEditView: View {
    private let existing: HostConfig?
    private let onSave: (HostConfig) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var label: String
    @State private var hostname: String
    @State private var port: String
    @State private var username: String
    @State private var authMethod: HostConfig.AuthMethod
    @State private var hostPublicKey: String
    @State private var uploadPath: String

    init(host: HostConfig? = nil, onSave: @escaping (HostConfig) -> Void) {
        self.existing = host
        self.onSave = onSave
        _label = State(initialValue: host?.label ?? "")
        _hostname = State(initialValue: host?.hostname ?? "")
        _port = State(initialValue: host.map { String($0.port) } ?? "22")
        _username = State(initialValue: host?.username ?? "")
        _authMethod = State(initialValue: host?.authMethod ?? .key)
        _hostPublicKey = State(initialValue: host?.hostPublicKey ?? "")
        _uploadPath = State(initialValue: host?.uploadPath ?? "/tmp")
    }

    private var isHostKeyValid: Bool {
        let trimmed = hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return true }
        return (try? NIOSSHPublicKey(openSSHPublicKey: trimmed)) != nil
    }

    /// A port has to fit in the `UInt16` the config stores. Validating against `Int`
    /// let 70000 through, and it was silently saved as 22.
    private var parsedPort: UInt16? {
        UInt16(port.trimmingCharacters(in: .whitespaces)).flatMap { $0 > 0 ? $0 : nil }
    }

    /// Built from what has been typed so far, so a non-default port shows up as `-p`
    /// in the instructions rather than sending the user to scan port 22.
    private var scanCommand: String {
        let trimmedHost = hostname.trimmingCharacters(in: .whitespaces)
        return HostConfig.hostKeyScanCommand(
            hostname: trimmedHost.isEmpty ? "<hostname>" : trimmedHost,
            port: parsedPort ?? 22
        )
    }

    private var isValid: Bool {
        !label.trimmingCharacters(in: .whitespaces).isEmpty &&
        !hostname.trimmingCharacters(in: .whitespaces).isEmpty &&
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        parsedPort != nil &&
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
                            .textInputAutocapitalization(.never)
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
                            .textInputAutocapitalization(.never)
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
                        Text("Invalid key format. Paste the key type and key data from the output of: \(scanCommand)")
                            .font(.caption)
                            .foregroundStyle(.red)
                    } else {
                        Text(
                            "Leave blank to review and save the server fingerprint on first connection. "
                            + "Or paste the key type and key data from the output of: \(scanCommand)"
                        )
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
                        Text("Keys are managed in Settings → SSH keys.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    LabeledContent("Upload path") {
                        TextField("/tmp", text: $uploadPath)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                } header: {
                    Text("SCP upload")
                } footer: {
                    Text("Remote directory where the keyboard extension uploads images via SFTP.")
                        .font(.caption)
                }
            }
            .navigationTitle(existing == nil ? "Add host" : "Edit host")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(!isValid)
                }
            }
        }
    }

    private func save() {
        let trimmedKey = hostPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPath = uploadPath.trimmingCharacters(in: .whitespaces)
        let host = HostConfig(
            // Carrying the id forward is what makes this an edit rather than a new
            // host: HostStore.update matches on it.
            id: existing?.id ?? UUID(),
            label: label.trimmingCharacters(in: .whitespaces),
            hostname: hostname.trimmingCharacters(in: .whitespaces),
            port: parsedPort ?? 22,
            username: username.trimmingCharacters(in: .whitespaces),
            authMethod: authMethod,
            hostPublicKey: trimmedKey.isEmpty ? nil : trimmedKey,
            uploadPath: trimmedPath.isEmpty ? "/tmp" : trimmedPath
        )
        onSave(host)
        dismiss()
    }
}
