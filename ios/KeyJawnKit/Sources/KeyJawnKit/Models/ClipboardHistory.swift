import UIKit

/// Stores recent clipboard items in the `group.com.keyjawn` App Group suite.
///
/// The app and the keyboard extension are separate processes. `UserDefaults.standard`
/// is a per-process sandbox, so pins written in one never appeared in the other —
/// the same class of bug `KeyboardPrefs` already fixed. Items are added when the
/// user taps Clip; there is no background monitoring.
///
/// A keyboard extension can only open the shared suite with Full Access. Without
/// it the suite lookup falls back to the extension sandbox and pins stay local.
@MainActor
public final class ClipboardHistory {
    public static let shared = ClipboardHistory()

    private let maxItems = 30
    private let maxPinned = 10
    private let historyKey = "keyjawn.clipboard.history"
    private let pinnedKey  = "keyjawn.clipboard.pinned"
    private let migratedKey = "keyjawn.clipboard.migrated.v1"
    private let injectedDefaults: UserDefaults?
    private let migratesLegacyValues: Bool

    /// - Parameters:
    ///   - defaults: backing store. Defaults to the shared App Group suite.
    ///   - migratesLegacyValues: copy pins/history from this process's
    ///     `UserDefaults.standard` once. The extension was the production
    ///     caller, so its sandbox holds the legacy pins; the app may have a
    ///     separate set. Each process copies its own standard suite into the
    ///     App Group when the shared keys are empty. Off for injected tests.
    public init(defaults: UserDefaults? = nil, migratesLegacyValues: Bool? = nil) {
        self.injectedDefaults = defaults
        self.migratesLegacyValues = migratesLegacyValues ?? (defaults == nil)
        migrateLegacyValuesIfNeeded()
    }

    private var defaults: UserDefaults {
        if let injectedDefaults { return injectedDefaults }
        return UserDefaults(suiteName: AppGroupConfig.suiteName) ?? .standard
    }

    private func migrateLegacyValuesIfNeeded() {
        guard migratesLegacyValues else { return }
        let store = defaults
        guard !store.bool(forKey: migratedKey) else { return }
        let legacy = UserDefaults.standard
        guard legacy != store else { return }
        defer { store.set(true, forKey: migratedKey) }
        if store.object(forKey: historyKey) == nil,
           let items = legacy.stringArray(forKey: historyKey) {
            store.set(items, forKey: historyKey)
        }
        if store.object(forKey: pinnedKey) == nil,
           let pinned = legacy.stringArray(forKey: pinnedKey) {
            store.set(pinned, forKey: pinnedKey)
        }
    }

    // MARK: - Recent items

    public var items: [String] {
        defaults.stringArray(forKey: historyKey) ?? []
    }

    /// Add current clipboard string to history (call when Clip button tapped).
    /// Deduplicates and trims to maxItems.
    public func addCurrent() {
        guard let string = UIPasteboard.general.string, !string.isEmpty else { return }
        add(string)
    }

    public func add(_ string: String) {
        var current = items
        current.removeAll { $0 == string }
        current.insert(string, at: 0)
        if current.count > maxItems { current = Array(current.prefix(maxItems)) }
        defaults.set(current, forKey: historyKey)
    }

    public func remove(at index: Int) {
        var current = items
        guard current.indices.contains(index) else { return }
        current.remove(at: index)
        defaults.set(current, forKey: historyKey)
    }

    public func clear() {
        defaults.removeObject(forKey: historyKey)
    }

    // MARK: - Pinned items

    public var pinned: [String] {
        defaults.stringArray(forKey: pinnedKey) ?? []
    }

    public func pin(_ string: String) {
        var current = pinned
        guard !current.contains(string) else { return }
        current.insert(string, at: 0)
        if current.count > maxPinned { current = Array(current.prefix(maxPinned)) }
        defaults.set(current, forKey: pinnedKey)
    }

    public func unpin(_ string: String) {
        var current = pinned
        current.removeAll { $0 == string }
        defaults.set(current, forKey: pinnedKey)
    }

    public func isPinned(_ string: String) -> Bool {
        pinned.contains(string)
    }
}
