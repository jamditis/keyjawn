import UIKit
import XCTest

/// Enables the KeyJawn keyboard extension in Settings, selects an explicit Full
/// Access state, and types into an active text field through the system IME.
@MainActor
final class KeyboardIMETests: XCTestCase {

    private var keyboardWasInstalled: Bool?
    private var initialFullAccess: Bool?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSystemKeyboardTypesWithFullAccessOff() throws {
        defer { restoreKeyboardState() }
        installContainingApp()
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        try setFullAccess(false, in: settings)
        typeHello(
            in: settings,
            screenshotName: "05-companion-keyboard",
            verifyBuiltInShortcut: true
        )
    }

    func testSystemKeyboardTypesWithFullAccessOn() throws {
        defer { restoreKeyboardState() }
        installContainingApp()
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        try setFullAccess(true, in: settings)
        typeHello(in: settings)
    }

    private func installContainingApp() {
        // Installing the host app also installs the keyboard appex.
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        if app.buttons["onboarding.skip"].waitForExistence(timeout: 4) {
            app.buttons["onboarding.skip"].tap()
        }
        app.terminate()
    }

    private func setFullAccess(_ enabled: Bool, in settings: XCUIApplication) throws {
        settings.launch()
        try openKeyboardList(in: settings)
        keyboardWasInstalled = settings.cells["com.keyjawn.keyboard"].exists
        installKeyboardIfNeeded(in: settings)
        try openInstalledKeyboard(in: settings)
        let toggle = settings.switches["Allow Full Access"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), "Allow Full Access switch")
        if keyboardWasInstalled == true {
            initialFullAccess = toggle.value as? String == "1"
        }
        setFullAccessToggle(enabled, in: settings)
        settings.terminate()
    }

    private func restoreKeyboardState() {
        guard let keyboardWasInstalled else { return }

        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        settings.launch()
        defer { settings.terminate() }

        do {
            try openKeyboardList(in: settings)
            if keyboardWasInstalled {
                guard let initialFullAccess else { return }
                try openInstalledKeyboard(in: settings)
                setFullAccessToggle(initialFullAccess, in: settings)
            } else {
                removeInstalledKeyboard(in: settings)
            }
        } catch {
            XCTFail("Restore the pre-test keyboard state: \(error)")
        }
    }

    private func openInstalledKeyboard(in settings: XCUIApplication) throws {
        let installedKeyboard = settings.cells["com.keyjawn.keyboard"]
        XCTAssertTrue(
            installedKeyboard.waitForExistence(timeout: 5),
            "KeyJawn must appear in the installed keyboard list"
        )
        installedKeyboard.tap()
    }

    private func setFullAccessToggle(_ enabled: Bool, in settings: XCUIApplication) {
        let toggle = settings.switches["Allow Full Access"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), "Allow Full Access switch")
        let expectedValue = enabled ? "1" : "0"
        if toggle.value as? String != expectedValue {
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
            if enabled {
                confirmFullAccessIfNeeded(in: settings)
            }
        }

        let valueExpectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == %@", expectedValue),
            object: toggle
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [valueExpectation], timeout: 5),
            .completed,
            "Full Access must be \(enabled ? "on" : "off")"
        )
    }

    private func confirmFullAccessIfNeeded(in settings: XCUIApplication) {
        let alert = settings.alerts.firstMatch
        guard alert.waitForExistence(timeout: 5) else { return }

        let allow = alert.buttons["Allow"]
        let allowFullAccess = alert.buttons["Allow Full Access"]
        let confirmation = allow.exists ? allow : allowFullAccess
        XCTAssertTrue(confirmation.waitForExistence(timeout: 2), "Full Access confirmation")
        confirmation.tap()

        let dismissed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: alert
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [dismissed], timeout: 5),
            .completed,
            "Full Access confirmation must close"
        )
    }

    private func openKeyboardList(in settings: XCUIApplication) throws {
        if settings.cells["com.keyjawn.keyboard"].exists
            || settings.cells["AddNewKeyboard"].exists
        {
            return
        }

        if settings.tables.staticTexts["General"].waitForExistence(timeout: 6) {
            settings.tables.staticTexts["General"].tap()
        } else if settings.buttons["General"].waitForExistence(timeout: 2) {
            settings.buttons["General"].tap()
        } else if settings.staticTexts["General"].firstMatch.waitForExistence(timeout: 2) {
            settings.staticTexts["General"].firstMatch.tap()
        } else {
            XCTFail("Settings General row not found on this supported OS")
            return
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

        XCTAssertTrue(
            settings.cells["com.keyjawn.keyboard"].waitForExistence(timeout: 2)
                || settings.cells["AddNewKeyboard"].waitForExistence(timeout: 2),
            "installed keyboard list"
        )
    }

    private func installKeyboardIfNeeded(in settings: XCUIApplication) {
        let installedKeyboard = settings.cells["com.keyjawn.keyboard"]
        if installedKeyboard.exists { return }

        let addByIdentifier = settings.cells["AddNewKeyboard"]
        let add =
            addByIdentifier.exists
            ? addByIdentifier
            : firstMatch(
                in: settings,
                labels: [
                    "Add New Keyboard",
                    "Add New Keyboard…",
                    "Add New Keyboard...",
                    "Add Keyboard",
                ])
        XCTAssertTrue(add.waitForExistence(timeout: 5), "Add New Keyboard row")
        add.tap()

        let keyjawnByIdentifier = settings.staticTexts["com.keyjawn"]
        let keyjawn =
            keyjawnByIdentifier.exists
            ? keyjawnByIdentifier
            : firstMatch(in: settings, labels: ["KeyJawn Keyboard", "KeyJawn"])
        XCTAssertTrue(keyjawn.waitForExistence(timeout: 6), "KeyJawn in the add-keyboard list")
        keyjawn.tap()

        let addKeyboard = firstMatch(in: settings, labels: ["Add Keyboard", "Allow"])
        if addKeyboard.waitForExistence(timeout: 2) {
            addKeyboard.tap()
        }
        if !installedKeyboard.waitForExistence(timeout: 5),
            settings.navigationBars.buttons["Keyboards"].exists
        {
            settings.navigationBars.buttons["Keyboards"].tap()
        }
        XCTAssertTrue(installedKeyboard.waitForExistence(timeout: 5), "installed KeyJawn keyboard")
    }

    private func removeInstalledKeyboard(in settings: XCUIApplication) {
        let installedKeyboard = settings.cells["com.keyjawn.keyboard"]
        guard installedKeyboard.waitForExistence(timeout: 3) else { return }

        installedKeyboard.swipeLeft()
        let delete = settings.buttons["Delete"].firstMatch
        XCTAssertTrue(delete.waitForExistence(timeout: 3), "Delete installed keyboard button")
        delete.tap()

        let removed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: installedKeyboard
        )
        XCTAssertEqual(
            XCTWaiter.wait(for: [removed], timeout: 5),
            .completed,
            "KeyJawn keyboard must return to its pre-test uninstalled state"
        )
    }

    private func typeHello(
        in settings: XCUIApplication,
        screenshotName: String? = nil,
        verifyBuiltInShortcut: Bool = false
    ) {
        settings.terminate()
        typeHelloInHostLabel(
            screenshotName: screenshotName,
            verifyBuiltInShortcut: verifyBuiltInShortcut
        )
    }

    private func typeHelloInHostLabel(
        screenshotName: String?,
        verifyBuiltInShortcut: Bool
    ) {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        if app.buttons["onboarding.skip"].waitForExistence(timeout: 4) {
            app.buttons["onboarding.skip"].tap()
        }

        let addHost = app.buttons["Add host"].firstMatch
        XCTAssertTrue(addHost.waitForExistence(timeout: 5), "Add host button")
        addHost.tap()

        let field = app.textFields["e.g. remote server"]
        XCTAssertTrue(field.waitForExistence(timeout: 5), "host label field")
        XCTAssertTrue(
            app.switches["TLS tunnel"].waitForExistence(timeout: 3),
            "the screenshot host form must include the current TLS tunnel control"
        )
        typeHello(
            in: field,
            app: app,
            screenshotName: screenshotName,
            verifyBuiltInShortcut: verifyBuiltInShortcut
        )
    }

    private func typeHello(
        in field: XCUIElement,
        app: XCUIApplication,
        screenshotName: String?,
        verifyBuiltInShortcut: Bool
    ) {
        field.tap()
        clear(field)

        XCTAssertTrue(
            switchToKeyJawn(in: app),
            "KeyJawn must be the selected system keyboard before letter taps"
        )
        assertLeadingExtraRowKeysAreHittable(in: app)
        if let screenshotName {
            attachScreenshot(named: screenshotName)
        }
        tapKey(in: app, "h")
        tapKey(in: app, "i")
        let typedValue = (field.value as? String)?.lowercased()
        XCTAssertEqual(
            typedValue,
            "hi",
            "typed hi through the KeyJawn system keyboard"
        )
        var expectedValue = "hi"
        if UIDevice.current.userInterfaceIdiom == .pad {
            tapKey(in: app, "Shift")
            let systemKeyboards = app.otherElements.matching(identifier: Self.systemKeyboardId)
            let shiftedQ = systemKeyboards.buttons["Q"].firstMatch
            XCTAssertTrue(shiftedQ.waitForExistence(timeout: 2), "shifted Q key")
            let flickStart = shiftedQ.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15))
            let flickEnd = shiftedQ.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9))
            flickStart.press(forDuration: 0.01, thenDragTo: flickEnd)
            expectedValue += "1"
            XCTAssertEqual(
                (field.value as? String)?.lowercased(),
                expectedValue,
                "downward Q flick inserts its secondary character once"
            )

            let lowercaseQ = systemKeyboards.buttons["q"].firstMatch
            XCTAssertTrue(
                lowercaseQ.waitForExistence(timeout: 2),
                "a flick must consume one-shot Shift"
            )
            lowercaseQ.tap()
            expectedValue += "q"
            XCTAssertEqual(
                (field.value as? String)?.lowercased(),
                expectedValue,
                "a normal tap still inserts the primary character once"
            )
        }
        if verifyBuiltInShortcut {
            let slash = app.buttons["extra.slash"].firstMatch
            XCTAssertTrue(slash.waitForExistence(timeout: 3), "built-in slash key")
            slash.tap()

            let compact = app.staticTexts["/compact"].firstMatch
            XCTAssertTrue(compact.waitForExistence(timeout: 3), "built-in /compact shortcut")
            compact.tap()
            XCTAssertEqual(
                (field.value as? String)?.lowercased(),
                expectedValue + "/compact",
                "built-in shortcut must work with Full Access off"
            )
        }
        app.terminate()
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func clear(_ field: XCUIElement) {
        guard let value = field.value as? String,
            value != field.placeholderValue,
            !value.isEmpty
        else { return }

        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
    }

    /// ExtraRowButton exposes identifier `extra.ctrlC` and the spoken Control-C
    /// label, not the visible `^C` title. XCUIElement queries match those.
    private static let extraRowCtrlCId = "extra.ctrlC"
    private static let extraRowTabId = "extra.tab"
    private static let systemKeyboardId = "keyjawn.systemKeyboard"
    private static let systemAssistantButtonIds = [
        "assistantUndo",
        "assistantRedo",
        "assistantPaste:forEvent:",
    ]

    /// iPadOS can place its undo, redo, and paste assistant controls over the
    /// keyboard's leading edge. Existence alone did not catch that regression:
    /// the covered KeyJawn buttons remained in the accessibility tree but could
    /// not receive a tap.
    private func assertLeadingExtraRowKeysAreHittable(in app: XCUIApplication) {
        let systemKeyboards = app.otherElements.matching(identifier: Self.systemKeyboardId)
        let controls = [
            systemKeyboards.buttons[Self.extraRowCtrlCId].firstMatch,
            systemKeyboards.buttons[Self.extraRowTabId].firstMatch,
        ]

        let assistantButtons = Self.systemAssistantButtonIds
            .map { app.buttons[$0].firstMatch }
            .filter(\.exists)
        if let assistantTrailingEdge = assistantButtons.map(\.frame.maxX).max() {
            for control in controls {
                XCTAssertGreaterThanOrEqual(
                    control.frame.minX,
                    assistantTrailingEdge,
                    "KeyJawn's leading extra-row controls must start after the iPad system assistant group"
                )
            }
        }

        for control in controls {
            XCTAssertTrue(control.waitForExistence(timeout: 2), "leading extra-row control")
            XCTAssertTrue(
                control.isHittable,
                "iPad system assistant controls must not cover KeyJawn's leading extra-row controls"
            )
        }
    }

    private func extraRowIsVisible(in app: XCUIApplication, wait: TimeInterval = 0) -> Bool {
        let systemKeyboards = app.otherElements.matching(identifier: Self.systemKeyboardId)
        let extraRow = systemKeyboards.buttons[Self.extraRowCtrlCId].firstMatch
        return wait > 0 ? extraRow.waitForExistence(timeout: wait) : extraRow.exists
    }

    private func switchToKeyJawn(in app: XCUIApplication) -> Bool {
        if extraRowIsVisible(in: app) { return true }
        let selector = app.buttons["emoji"]
        if selector.exists, selector.isHittable {
            selector.press(forDuration: 1)
            if dismissKeyboardPickerEducationIfNeeded(in: app) {
                selector.tap()
                if extraRowIsVisible(in: app, wait: 5) { return true }
                selector.press(forDuration: 1)
            }
            if selectKeyJawnFromPicker(in: app) {
                return extraRowIsVisible(in: app, wait: 5)
            }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()
        }

        for _ in 0..<6 {
            if extraRowIsVisible(in: app) { return true }
            let byIdentifier = app.buttons["emoji"]
            if byIdentifier.exists, byIdentifier.isHittable {
                byIdentifier.tap()
            } else {
                let byLabel = firstMatch(
                    in: app,
                    labels: ["Next keyboard", "Next Keyboard", "Globe", "Emoji"]
                )
                guard byLabel.exists, byLabel.isHittable else { return false }
                byLabel.tap()
            }
            if dismissKeyboardPickerEducationIfNeeded(in: app) { continue }
            if extraRowIsVisible(in: app, wait: 3) { return true }
        }
        return false
    }

    private func dismissKeyboardPickerEducationIfNeeded(in app: XCUIApplication) -> Bool {
        let title = app.staticTexts["Quickly Change Keyboards"]
        guard title.waitForExistence(timeout: 2) else { return false }

        let continueButton = app.buttons["Continue"]
        XCTAssertTrue(continueButton.waitForExistence(timeout: 2), "keyboard picker education")
        continueButton.tap()
        return true
    }

    private func selectKeyJawnFromPicker(in app: XCUIApplication) -> Bool {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", "KeyJawn Keyboard")
        let candidates = [
            app.buttons.matching(predicate).firstMatch,
            app.menuItems.matching(predicate).firstMatch,
            app.otherElements.matching(predicate).firstMatch,
        ]
        for candidate in candidates where candidate.waitForExistence(timeout: 1) {
            candidate.tap()
            return true
        }
        return false
    }

    private func tapKey(in app: XCUIApplication, _ label: String) {
        let systemKeyboards = app.otherElements.matching(identifier: Self.systemKeyboardId)
        let key = systemKeyboards.buttons[label].firstMatch
        XCTAssertTrue(key.waitForExistence(timeout: 2), "KeyJawn key \(label)")
        key.tap()
    }

    private func firstMatch(in app: XCUIApplication, labels: [String]) -> XCUIElement {
        for label in labels {
            let button = app.buttons[label].firstMatch
            if button.exists { return button }
            let text = app.staticTexts[label].firstMatch
            if text.exists { return text }
            let cell = app.cells[label].firstMatch
            if cell.exists { return cell }
        }
        return app.descendants(matching: .any)[labels[0]]
    }
}
