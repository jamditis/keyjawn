import Foundation
import KeyJawnKit

/// Persists the SSH host list across app launches.
@MainActor
final class HostStore: ObservableObject {

    @Published private(set) var hosts: [HostConfig] = []

    private let defaults = UserDefaults.standard
    private let storageKey = "keyjawn.hosts"

    init() {
        load()
    }

    // MARK: - Mutations

    func add(_ host: HostConfig) {
        hosts.append(host)
        save()
    }

    func delete(at offsets: IndexSet) {
        hosts.remove(atOffsets: offsets)
        save()
    }

    func update(_ host: HostConfig) {
        guard let index = hosts.firstIndex(where: { $0.id == host.id }) else { return }
        hosts[index] = host
        save()
    }

    // MARK: - Persistence

    private func load() {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([HostConfig].self, from: data) else { return }
        hosts = decoded
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(hosts) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
