import UIKit

/// Stores recent clipboard items in UserDefaults.
/// Works in both the main app and the keyboard extension (each has its own sandbox).
/// Items are added manually when the user taps the Clip button — no background monitoring.
@MainActor
public final class ClipboardHistory {
    public static let shared = ClipboardHistory()
    private init() {}

    private let maxItems = 30
    private let maxPinned = 10
    private let historyKey = "keyjawn.clipboard.history"
    private let pinnedKey  = "keyjawn.clipboard.pinned"
    private let defaults   = UserDefaults.standard

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
