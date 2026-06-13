import Foundation
import KeyJawnKit

/// Read-only host config reader for the keyboard extension.
/// The main app HostStore writes host JSON to the App Group container on every save.
final class AppGroupHostStore: @unchecked Sendable {
    static let shared = AppGroupHostStore()
    private init() {}

    private let key = "keyjawn.hosts"
    private var defaults: UserDefaults? { UserDefaults(suiteName: AppGroupConfig.suiteName) }

    var hosts: [HostConfig] {
        guard let data = defaults?.data(forKey: key),
              let decoded = try? JSONDecoder().decode([HostConfig].self, from: data) else {
            return []
        }
        return decoded
    }
}
