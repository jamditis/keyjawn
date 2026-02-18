import SwiftUI
import UIKit

struct SSHKeysView: View {
    @State private var publicKey = ""
    @State private var copied = false
    @State private var showingRegenerateConfirmation = false

    var body: some View {
        Form {
            Section {
                Text(publicKey)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)

                Button {
                    UIPasteboard.general.string = publicKey
                    copied = true
                    Task {
                        try? await Task.sleep(for: .seconds(2))
                        copied = false
                    }
                } label: {
                    Label(
                        copied ? "Copied" : "Copy public key",
                        systemImage: copied ? "checkmark" : "doc.on.doc"
                    )
                }
            } header: {
                Text("Public key")
            } footer: {
                Text("Add this to ~/.ssh/authorized_keys on each server, then select \"SSH key\" auth when adding the host.")
                    .font(.caption)
            }

            Section {
                Button("Regenerate key pair", role: .destructive) {
                    showingRegenerateConfirmation = true
                }
            } footer: {
                Text("Generating a new key invalidates the current public key on all servers. You will need to update authorized_keys everywhere.")
                    .font(.caption)
            }
        }
        .navigationTitle("SSH keys")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            publicKey = SSHKeyStore.shared.publicKeyOpenSSHString
        }
        .confirmationDialog(
            "Regenerate key pair?",
            isPresented: $showingRegenerateConfirmation,
            titleVisibility: .visible
        ) {
            Button("Regenerate", role: .destructive) {
                SSHKeyStore.shared.regenerate()
                publicKey = SSHKeyStore.shared.publicKeyOpenSSHString
            }
        } message: {
            Text("The current public key will stop working on all servers until you update authorized_keys.")
        }
    }
}
