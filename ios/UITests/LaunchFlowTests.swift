import XCTest

/// Drives the shipped app the way a first-run user does. No mocks.
final class LaunchFlowTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
    }

    func testColdLaunchCanSkipOnboardingAndReachTheThreeTabs() {
        let skip = app.buttons["onboarding.skip"]
        XCTAssertTrue(skip.waitForExistence(timeout: 5), "first launch must show Skip")
        skip.tap()

        activateTab("Hosts")
        XCTAssertTrue(app.navigationBars["Hosts"].waitForExistence(timeout: 5))
        activateTab("Preview")
        XCTAssertTrue(app.navigationBars["Preview"].waitForExistence(timeout: 3))
        activateTab("Settings")
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["settings.setupKeyboard"].exists)

        activateTab("Hosts")
        XCTAssertTrue(app.navigationBars["Hosts"].waitForExistence(timeout: 3))
    }

    func testSettingsReopensOnboardingAndSkipReturnsToSettings() {
        let skip = app.buttons["onboarding.skip"]
        if skip.waitForExistence(timeout: 5) {
            skip.tap()
        }

        activateTab("Settings")
        let setup = app.buttons["settings.setupKeyboard"]
        XCTAssertTrue(setup.waitForExistence(timeout: 3))
        setup.tap()
        XCTAssertTrue(app.buttons["onboarding.skip"].waitForExistence(timeout: 3))
        app.buttons["onboarding.skip"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 3))
    }

    func testOnboardingContinueWalksEveryPageThenDone() {
        let skip = app.buttons["onboarding.skip"]
        if skip.waitForExistence(timeout: 5) {
            skip.tap()
        }
        activateTab("Settings")
        app.buttons["settings.setupKeyboard"].tap()

        let continueButton = app.buttons["onboarding.continue"]
        XCTAssertTrue(continueButton.waitForExistence(timeout: 3))
        continueButton.tap()
        XCTAssertTrue(continueButton.waitForExistence(timeout: 2))
        continueButton.tap()
        let done = app.buttons["onboarding.done"]
        XCTAssertTrue(done.waitForExistence(timeout: 2))
        done.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 3))
    }

    /// iPhone uses a standard tab bar. iPad iOS 26 uses a floating tab bar
    /// that is not an XCUI tabBars query, and `buttons[name]` matches both the
    /// outer item and a nested `_UIFloatingTabBarItemView`. Prefer the tab
    /// bar, then the SF Symbol identifier (one match), then the label with
    /// `firstMatch`.
    private func activateTab(_ name: String) {
        let tab = app.tabBars.buttons[name]
        if tab.waitForExistence(timeout: 2) {
            tab.tap()
            return
        }

        let identifiers = [
            "Hosts": "server.rack",
            "Preview": "terminal",
            "Settings": "gearshape",
        ]
        if let id = identifiers[name] {
            let byId = app.buttons[id].firstMatch
            if byId.waitForExistence(timeout: 2) {
                byId.tap()
                return
            }
        }

        let button = app.buttons[name].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 3), "tab \(name)")
        button.tap()
    }
}
