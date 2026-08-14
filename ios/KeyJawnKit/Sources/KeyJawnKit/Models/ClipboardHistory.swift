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
    private let injectedDefaults: UserDefaults?

    /// - Parameter defaults: backing store. Defaults to the shared App Group
    ///   suite, resolved per access. Injectable so tests can use a scratch suite.
    public init(defaults: UserDefaults? = nil) {
        self.injectedDefaults = defaults
    }

    private var defaults: UserDefaults {
        if let injectedDefaults { return injectedDefaults }
        return UserDefaults(suiteName: AppGroupConfig.suiteName) ?? .standard
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
