import SwiftUI

@main
struct KeyJawnApp: App {
    @StateObject private var hostStore: HostStore

    init() {
        let persistenceMode: HostStore.PersistenceMode
        #if DEBUG
            let isUITesting = ProcessInfo.processInfo.arguments.contains("-ui-testing")
            persistenceMode = isUITesting ? .inMemory : .keychain
        #else
            persistenceMode = .keychain
        #endif
        _hostStore = StateObject(
            wrappedValue: HostStore(persistenceMode: persistenceMode)
        )
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
