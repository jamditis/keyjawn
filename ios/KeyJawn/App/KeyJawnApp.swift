import SwiftUI

@main
struct KeyJawnApp: App {
    @StateObject private var hostStore = HostStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(hostStore)
        }
    }
}
