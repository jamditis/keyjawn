import Foundation

/// Persists keyboard user preferences using UserDefaults.
/// In the keyboard extension this is the extension's own sandbox.
/// In the main app this is the app's sandbox.
/// Both share the same UserDefaults.standard because they don't share an App Group.
public final class KeyboardPrefs: @unchecked Sendable {
    public static let shared = KeyboardPrefs()
    private init() {}

    private let defaults = UserDefaults.standard

    public var theme: KeyboardTheme {
        get {
            let raw = defaults.string(forKey: "keyjawn.theme") ?? KeyboardTheme.dark.rawValue
            return KeyboardTheme(rawValue: raw) ?? .dark
        }
        set {
            defaults.set(newValue.rawValue, forKey: "keyjawn.theme")
        }
    }
}
