import SwiftUI

@main
struct KeyJawnApp: App {
    @StateObject private var hostStore = HostStore()

    init() {
        // Reconcile the extension-visible key mirror with the Keychain at launch.
        // Without this, an install whose mirror is missing or stale — a storage format
        // change, a reset group container — leaves SFTP upload from the keyboard
        // reporting "SSH key not found" with nothing the user can do about it.
        SSHKeyStore.shared.syncAppGroupMirror()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(hostStore)
        }
    }
}
