import XCTest
@testable import KeyJawnKit

/// These preferences cross a process boundary, so the interesting cases are the ones
/// where nothing has been written yet — the state every fresh install is in, and the
/// state a keyboard extension without Full Access is permanently in.
final class KeyboardPrefsTests: XCTestCase {

    private var suiteName: String!
    private var suite: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "com.keyjawn.tests.\(UUID().uuidString)"
        suite = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        suite = nil
        suiteName = nil
        super.tearDown()
    }

    // MARK: - Defaults

    func testThemeDefaultsToDark() {
        XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, .dark)
    }

    /// `UserDefaults.bool(forKey:)` returns false for a key that was never written, so
    /// reading this naively would ship every install with feedback silently off.
    func testFeedbackDefaultsToOn() {
        XCTAssertTrue(KeyboardPrefs(defaults: suite).hapticsEnabled)
    }

    /// On by default because the product exists to drive shells over SSH, and because
    /// with it off the up and down arrows have nothing to do.
    func testTerminalArrowKeysDefaultToOn() {
        XCTAssertTrue(KeyboardPrefs(defaults: suite).terminalArrowKeys)
    }

    // MARK: - Round trip

    func testEveryThemeRoundTrips() {
        for theme in KeyboardTheme.allCases {
            let writer = KeyboardPrefs(defaults: suite)
            writer.theme = theme
            XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, theme,
                           "\(theme.rawValue) did not survive a write and re-read")
        }
    }

    func testBooleanPreferencesRoundTripInBothDirections() {
        let prefs = KeyboardPrefs(defaults: suite)
        for value in [false, true, false] {
            prefs.hapticsEnabled = value
            prefs.terminalArrowKeys = value
            let reread = KeyboardPrefs(defaults: suite)
            XCTAssertEqual(reread.hapticsEnabled, value)
            XCTAssertEqual(reread.terminalArrowKeys, value)
        }
    }

    /// A theme removed in a later release, or a value corrupted in the shared
    /// container, must not leave the keyboard with no colours at all.
    func testUnknownThemeFallsBackToDark() {
        suite.set("solarized-mauve", forKey: "keyjawn.theme")
        XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, .dark)
    }

    // MARK: - Migration

    /// The pre-App-Group Settings screen wrote `@AppStorage("theme")` into the app's
    /// own sandbox where the keyboard could never see it. Honour that choice once
    /// rather than resetting the user to Dark on upgrade.
    func testLegacySettingsThemeIsAdopted() {
        let legacyKey = "theme"
        let previous = UserDefaults.standard.string(forKey: legacyKey)
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: legacyKey)
            } else {
                UserDefaults.standard.removeObject(forKey: legacyKey)
            }
        }

        UserDefaults.standard.set("terminal", forKey: legacyKey)
        XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, .terminal)
    }

    /// Migration runs once. A later legacy write must not overwrite a choice the user
    /// has since made in the new Settings screen.
    func testMigrationDoesNotRunTwice() {
        let legacyKey = "theme"
        let previous = UserDefaults.standard.string(forKey: legacyKey)
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: legacyKey)
            } else {
                UserDefaults.standard.removeObject(forKey: legacyKey)
            }
        }

        UserDefaults.standard.set("terminal", forKey: legacyKey)
        _ = KeyboardPrefs(defaults: suite)          // migrates
        KeyboardPrefs(defaults: suite).theme = .oled // user picks something else

        UserDefaults.standard.set("light", forKey: legacyKey)
        XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, .oled)
    }

    /// The upgrade path this migration exists for is a user who opens the *keyboard*
    /// before the app — the ordinary case for this product. The extension cannot see
    /// the app's standard suite, so if it ran the migration it would find nothing,
    /// set the shared completion flag anyway, and the app's next launch would skip the
    /// migration and silently discard the user's saved theme. A non-migrating process
    /// must leave both the values and the flag alone.
    func testANonMigratingProcessLeavesTheFlagUnset() {
        let legacyKey = "theme"
        let previous = UserDefaults.standard.string(forKey: legacyKey)
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: legacyKey)
            } else {
                UserDefaults.standard.removeObject(forKey: legacyKey)
            }
        }
        UserDefaults.standard.set("terminal", forKey: legacyKey)

        // Stands in for the keyboard extension.
        let extensionSide = KeyboardPrefs(defaults: suite, migratesLegacyValues: false)
        XCTAssertEqual(extensionSide.theme, .dark, "extension should not consume app-side keys")
        XCTAssertFalse(suite.bool(forKey: "keyjawn.prefs.migrated.v2"),
                       "extension claimed the migration it could not perform")

        // The app opens later and the choice is still recoverable.
        XCTAssertEqual(KeyboardPrefs(defaults: suite, migratesLegacyValues: true).theme, .terminal)
    }

    func testAnExplicitChoiceIsNotOverwrittenByALegacyValue() {
        let legacyKey = "theme"
        let previous = UserDefaults.standard.string(forKey: legacyKey)
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: legacyKey)
            } else {
                UserDefaults.standard.removeObject(forKey: legacyKey)
            }
        }

        suite.set(KeyboardTheme.light.rawValue, forKey: "keyjawn.theme")
        UserDefaults.standard.set("terminal", forKey: legacyKey)
        XCTAssertEqual(KeyboardPrefs(defaults: suite).theme, .light)
    }
}
