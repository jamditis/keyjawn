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

    /// Storage supplied by a test. When nil the suite is looked up per access below.
    private let injectedDefaults: UserDefaults?

    /// Look the suite up on every access rather than holding one wrapper for the
    /// process's lifetime.
    ///
    /// The extension process outlives any single activation, and the main app writes
    /// these values from a different process. A cached `UserDefaults` wrapper can keep
    /// serving its own stale in-process cache until the extension restarts, so a theme
    /// picked in Settings would not appear until the keyboard was killed — the same
    /// staleness `AppGroupHostStore` reopens its suite to avoid, found and reverted for
    /// exactly this reason in #46.
    ///
    /// Cheap enough for the read sites here, which are activation- and
    /// interaction-scoped. It is deliberately *not* cheap enough for per-keystroke
    /// reads: `KeyboardHaptics` caches its flag and refreshes it on appearance rather
    /// than reopening the suite on every key press.
    private var defaults: UserDefaults {
        if let injectedDefaults { return injectedDefaults }
        return UserDefaults(suiteName: AppGroupConfig.suiteName) ?? .standard
    }

    /// - Parameters:
    ///   - defaults: backing store. Defaults to the shared App Group suite, resolved
    ///     per access. Injectable so tests can run against a scratch suite.
    ///   - migratesLegacyValues: whether this process should consume the pre-App-Group
    ///     keys. Defaults to false inside an app extension; see
    ///     ``migrateLegacyValuesIfNeeded()``.
    public init(defaults: UserDefaults? = nil, migratesLegacyValues: Bool? = nil) {
        self.injectedDefaults = defaults
        self.migratesLegacyValues = migratesLegacyValues ?? !Self.isAppExtension
        migrateLegacyValuesIfNeeded()
    }

    private let migratesLegacyValues: Bool

    /// True when this code is running inside an app extension rather than the app.
    /// Extension bundles are `.appex`; the containing app's is `.app`.
    private static var isAppExtension: Bool {
        Bundle.main.bundleURL.pathExtension == "appex"
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
    ///
    /// Only the main app runs this. Both legacy keys live in the *app's* standard
    /// suite, which an extension cannot see — so an extension running it would find
    /// nothing, and then set the shared completion flag, and the main app's next
    /// launch would skip the migration and quietly discard the user's saved theme.
    /// An upgraded user opening the keyboard before the app is the ordinary case for
    /// this product, not a corner of it.
    private func migrateLegacyValuesIfNeeded() {
        guard migratesLegacyValues else { return }

        let store = defaults
        guard !store.bool(forKey: Key.migrated) else { return }

        let legacy = UserDefaults.standard
        // Nothing to carry when the shared suite is unavailable and `store` has already
        // fallen back to the same standard suite the legacy values live in.
        guard legacy != store else { return }
        defer { store.set(true, forKey: Key.migrated) }

        if store.object(forKey: Key.theme) == nil,
           let raw = legacy.string(forKey: Key.theme) ?? legacy.string(forKey: "theme"),
           KeyboardTheme(rawValue: raw) != nil {
            store.set(raw, forKey: Key.theme)
        }

        if store.object(forKey: Key.haptics) == nil,
           let legacyHaptics = legacy.object(forKey: "hapticEnabled") as? Bool {
            store.set(legacyHaptics, forKey: Key.haptics)
        }
    }
}
