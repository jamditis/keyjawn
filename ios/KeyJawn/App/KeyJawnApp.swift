import SwiftUI

@main
struct KeyJawnApp: App {
    @StateObject private var hostStore = HostStore()

    init() {
        // Migrate the pre-shared-keychain item before the extension can request it.
        // This preserves every existing authorized_keys entry instead of rotating.
        SSHKeyStore.shared.prepareSharedIdentity()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(hostStore)
        }
    }
}
