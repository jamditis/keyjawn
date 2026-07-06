import Foundation
import KeyJawnKit

/// Read-only host config reader for the keyboard extension.
/// The main app HostStore writes host JSON to the App Group container on every save.
///
/// Reads happen once per deliberate SCP-panel open. In a memory-constrained keyboard
/// extension the main-thread JSON decode on that interactive path is worth avoiding,
/// so the decoded host list is cached against the raw bytes it came from and a repeat
/// open with an unchanged list skips the decode.
final class AppGroupHostStore: @unchecked Sendable {
    static let shared = AppGroupHostStore()
    private init() {}

    private let key = "keyjawn.hosts"

    /// Look the suite up per read rather than caching the instance. The main app
    /// writes host edits from a different process, and a cached `UserDefaults`
    /// wrapper can keep serving its stale in-process cache until the extension
    /// restarts, so the next SCP-panel open would show the old host list. Caching
    /// the instance was tried and reverted for exactly this reason in #46. Only the
    /// decode is cached (below); the read stays as fresh as it was before.
    private var defaults: UserDefaults? { UserDefaults(suiteName: AppGroupConfig.suiteName) }

    /// Guards the decode cache below. This shared singleton declares
    /// `@unchecked Sendable`, so the lock is what makes that promise honest for the
    /// mutable cache state: a read of `hosts` can touch it from any thread, and the
    /// lock keeps those touches race-free. Do not drop it because reads look
    /// main-thread-only today.
    private let lock = NSLock()
    private var cachedData: Data?
    private var cachedHosts: [HostConfig] = []

    /// The host list the main app mirrored into the App Group.
    ///
    /// Each read fetches the current bytes through a fresh suite lookup, so it is as
    /// fresh as before, and re-decodes only when those bytes changed since the last
    /// read. A host added, edited, or removed in the main app rewrites the bytes, so
    /// the cache misses and re-decodes; when nothing changed, the cached decode is
    /// returned and the JSON decode is skipped.
    var hosts: [HostConfig] {
        let data = defaults?.data(forKey: key)
        lock.lock()
        defer { lock.unlock() }
        guard let data else {
            cachedData = nil
            cachedHosts = []
            return []
        }
        if data == cachedData {
            return cachedHosts
        }
        let decoded = (try? JSONDecoder().decode([HostConfig].self, from: data)) ?? []
        cachedData = data
        cachedHosts = decoded
        return decoded
    }
}
