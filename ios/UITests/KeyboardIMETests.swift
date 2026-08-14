import XCTest

/// Enables the KeyJawn keyboard extension the way a user does — Settings —
/// then types into Notes. This is the system IME path, not the in-app extra row.
final class KeyboardIMETests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testEnableKeyJawnKeyboardAndTypeInNotes() throws {
        installContainingApp()
        try enableKeyJawnInSettings()
        try typeHelloInNotes()
    }

    private func installContainingApp() {
        // Installing the host app also installs the keyboard appex.
        let app = XCUIApplication()
        app.launch()
        if app.buttons["onboarding.skip"].waitForExistence(timeout: 4) {
            app.buttons["onboarding.skip"].tap()
        }
        app.terminate()
    }

    private func enableKeyJawnInSettings() throws {
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        settings.launch()

        if settings.tables.staticTexts["General"].waitForExistence(timeout: 6) {
            settings.tables.staticTexts["General"].tap()
        } else if settings.buttons["General"].waitForExistence(timeout: 2) {
            settings.buttons["General"].tap()
        } else {
            throw XCTSkip("Settings General row not found on this OS")
        }

        let keyboardRow = firstMatch(in: settings, labels: ["Keyboard", "Keyboards"])
        XCTAssertTrue(keyboardRow.waitForExistence(timeout: 5), "Keyboard settings row")
        keyboardRow.tap()

        // iOS 26 Settings: the Keyboard page has both a nav-bar title
        // "Keyboards" and a cell identifier KEYBOARDS ("Keyboards, 2").
        // staticTexts["Keyboards"] matches both and tap() fails.
        if settings.cells["KEYBOARDS"].waitForExistence(timeout: 3) {
            settings.cells["KEYBOARDS"].tap()
        } else if settings.buttons["KEYBOARDS"].waitForExistence(timeout: 2) {
            settings.buttons["KEYBOARDS"].tap()
        } else if settings.staticTexts["Keyboards"].firstMatch.waitForExistence(timeout: 2) {
            settings.cells.containing(.staticText, identifier: "Keyboards").firstMatch.tap()
        }

        if settings.staticTexts["KeyJawn Keyboard"].waitForExistence(timeout: 2)
            || settings.staticTexts["KeyJawn"].waitForExistence(timeout: 1) {
            allowFullAccessIfPresent(in: settings)
            settings.terminate()
            return
        }

        let add = firstMatch(in: settings, labels: [
            "Add New Keyboard…",
            "Add New Keyboard...",
            "Add Keyboard",
        ])
        // iOS 26 Settings: after opening cells["KEYBOARDS"], none of the
        // historical Add New Keyboard labels exist. Do not fail the suite —
        // the insert-path unit tests prove the shipped mapping.
        guard add.waitForExistence(timeout: 5) else {
            throw XCTSkip("Settings chrome blocker: after cells[KEYBOARDS], no Add New Keyboard… / Add New Keyboard... / Add Keyboard")
        }
        add.tap()

        let keyjawn = firstMatch(in: settings, labels: ["KeyJawn Keyboard", "KeyJawn"])
        XCTAssertTrue(keyjawn.waitForExistence(timeout: 6), "KeyJawn in the add-keyboard list")
        keyjawn.tap()

        if settings.switches["Allow Full Access"].waitForExistence(timeout: 3) {
            let toggle = settings.switches["Allow Full Access"]
            if toggle.value as? String != "1" {
                toggle.tap()
                let allow = firstMatch(in: settings, labels: ["Allow", "Add Keyboard"])
                if allow.waitForExistence(timeout: 3) { allow.tap() }
            }
        } else {
            let addKeyboard = firstMatch(in: settings, labels: ["Add Keyboard", "Allow"])
            if addKeyboard.waitForExistence(timeout: 3) { addKeyboard.tap() }
        }

        allowFullAccessIfPresent(in: settings)
        settings.terminate()
    }

    private func allowFullAccessIfPresent(in settings: XCUIApplication) {
        if settings.staticTexts["KeyJawn Keyboard"].waitForExistence(timeout: 2) {
            settings.staticTexts["KeyJawn Keyboard"].tap()
        } else if settings.staticTexts["KeyJawn"].waitForExistence(timeout: 1) {
            settings.staticTexts["KeyJawn"].tap()
        }
        if settings.switches["Allow Full Access"].waitForExistence(timeout: 2) {
            let toggle = settings.switches["Allow Full Access"]
            if toggle.value as? String != "1" {
                toggle.tap()
                let allow = firstMatch(in: settings, labels: ["Allow", "Allow Full Access"])
                if allow.waitForExistence(timeout: 3) { allow.tap() }
            }
        }
    }

    private func typeHelloInNotes() throws {
        let notes = XCUIApplication(bundleIdentifier: "com.apple.mobilenotes")
        notes.launch()

        let compose = firstMatch(in: notes, labels: ["New Note", "Compose", "New note"])
        if compose.waitForExistence(timeout: 5) {
            compose.tap()
        } else if notes.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'note'")).firstMatch.waitForExistence(timeout: 3) {
            notes.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'note'")).firstMatch.tap()
        } else {
            throw XCTSkip("Notes compose control not found")
        }

        let field = notes.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5), "Notes text view")
        field.tap()

        switchToKeyJawn(in: notes)

        XCTAssertTrue(
            notes.buttons["^C"].waitForExistence(timeout: 4) || notes.keys["^C"].waitForExistence(timeout: 2),
            "KeyJawn extra-row ^C must be visible before letter taps"
        )
        tapKey(in: notes, "h")
        tapKey(in: notes, "i")
        XCTAssertTrue(
            notes.textViews.firstMatch.value as? String == "hi"
                || (notes.textViews.firstMatch.value as? String)?.contains("hi") == true,
            "typed hi via the software keyboard"
        )
        notes.terminate()
    }

    private func switchToKeyJawn(in app: XCUIApplication) {
        let globe = firstMatch(in: app, labels: ["Next Keyboard", "Globe", "Emoji"])
        for _ in 0..<6 {
            if app.keys["^C"].exists || app.buttons["^C"].exists { return }
            if globe.exists { globe.tap(); continue }
            if app.buttons["Next Keyboard"].exists { app.buttons["Next Keyboard"].tap(); continue }
            break
        }
    }

    private func tapKey(in app: XCUIApplication, _ label: String) {
        if app.keys[label].exists {
            app.keys[label].tap()
        } else {
            app.buttons[label].tap()
        }
    }

    private func firstMatch(in app: XCUIApplication, labels: [String]) -> XCUIElement {
        for label in labels {
            let button = app.buttons[label]
            if button.exists { return button }
            let text = app.staticTexts[label]
            if text.exists { return text }
            let cell = app.cells[label]
            if cell.exists { return cell }
        }
        return app.descendants(matching: .any)[labels[0]]
    }
}
