import Foundation

/// Keyboard preferences shared between the main app and the keyboard extension.
///
/// Storage is the `group.com.keyjawn` App Group suite, because that is the only
/// container both processes can see. Previously this read `UserDefaults.standard`,
/// which in an app extension is the *extension's* sandbox and not the app's, while
/// the Settings screen wrote its own unrelated keys into the *app's* sandbox. The
/// result was that nothing the user changed in Settings ever reached the keyboard —
/// the theme picker in particular looked functional and did nothing.
///
/// A keyboard extension can only open the shared suite once the user grants Full
/// Access. Without it `UserDefaults(suiteName:)` returns nil and reads fall back to
/// the extension's own sandbox, so the keyboard runs on defaults. That is a platform
/// rule rather than a bug; `UIInputViewController.hasFullAccess` is what the UI uses
/// to explain it where it matters.
public final class KeyboardPrefs: @unchecked Sendable {

    public static let shared = KeyboardPrefs()

    private enum Key {
        static let theme = "keyjawn.theme"
        static let haptics = "keyjawn.haptics"
        static let terminalArrows = "keyjawn.terminalArrows"
        static let migrated = "keyjawn.prefs.migrated.v2"
    }

    private let defaults: UserDefaults

    /// - Parameter defaults: backing store. Defaults to the shared App Group suite,
    ///   falling back to this process's own `standard` suite when the group is not
    ///   reachable. Injectable so tests can run against a scratch suite.
    public init(defaults: UserDefaults? = nil) {
        self.defaults = defaults
            ?? UserDefaults(suiteName: AppGroupConfig.suiteName)
            ?? .standard
        migrateLegacyValuesIfNeeded()
    }

    // MARK: - Preferences

    /// Colour theme applied to every keyboard surface.
    public var theme: KeyboardTheme {
        get {
            guard let raw = defaults.string(forKey: Key.theme),
                  let theme = KeyboardTheme(rawValue: raw) else { return .dark }
            return theme
        }
        set { defaults.set(newValue.rawValue, forKey: Key.theme) }
    }

    /// Whether key presses produce click/haptic feedback. On by default, matching
    /// the system keyboard.
    public var hapticsEnabled: Bool {
        get { bool(Key.haptics, default: true) }
        set { defaults.set(newValue, forKey: Key.haptics) }
    }

    /// Whether the extra row's arrow keys emit ANSI escape sequences (`ESC [ A` and
    /// friends) instead of moving the text cursor.
    ///
    /// On by default because the product exists to drive CLI agents over SSH, where
    /// escape sequences are the only thing that reaches the shell — and because with
    /// it off the up and down arrows have nothing at all to do. Turning it off gives
    /// left/right plain cursor movement for editing prose in an ordinary text field.
    public var terminalArrowKeys: Bool {
        get { bool(Key.terminalArrows, default: true) }
        set { defaults.set(newValue, forKey: Key.terminalArrows) }
    }

    // MARK: - Helpers

    /// `UserDefaults.bool(forKey:)` reports false for a key that was never set, which
    /// would silently flip any preference that defaults to true. Check for presence
    /// first so an unset key falls through to the stated default.
    private func bool(_ key: String, default defaultValue: Bool) -> Bool {
        defaults.object(forKey: key) as? Bool ?? defaultValue
    }

    // MARK: - Migration

    /// Carries values written by the pre-App-Group builds into the shared suite once.
    ///
    /// Two legacy sources existed: this type's own `keyjawn.theme` in the app's
    /// standard suite, and the Settings screen's `@AppStorage("theme")` /
    /// `@AppStorage("hapticEnabled")`. Neither was ever read by the keyboard, so
    /// migrating them is about honouring a choice the user already made rather than
    /// preserving working behaviour.
    private func migrateLegacyValuesIfNeeded() {
        guard !defaults.bool(forKey: Key.migrated) else { return }
        defer { defaults.set(true, forKey: Key.migrated) }

        let legacy = UserDefaults.standard
        guard legacy != defaults else { return }

        if defaults.object(forKey: Key.theme) == nil,
           let raw = legacy.string(forKey: Key.theme) ?? legacy.string(forKey: "theme"),
           KeyboardTheme(rawValue: raw) != nil {
            defaults.set(raw, forKey: Key.theme)
        }

        if defaults.object(forKey: Key.haptics) == nil,
           let legacyHaptics = legacy.object(forKey: "hapticEnabled") as? Bool {
            defaults.set(legacyHaptics, forKey: Key.haptics)
        }
    }
}
