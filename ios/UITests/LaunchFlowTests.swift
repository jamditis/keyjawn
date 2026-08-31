import XCTest

/// Drives the shipped app the way a first-run user does. No mocks.
final class LaunchFlowTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    private func launchApp() {
        app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
    }

    @MainActor
    func testColdLaunchCanSkipOnboardingAndReachTheHostsAndSettingsTabs() {
        launchApp()
        let skip = app.buttons["onboarding.skip"]
        XCTAssertTrue(skip.waitForExistence(timeout: 5), "first launch must show Skip")
        XCTAssertTrue(
            app.descendants(matching: .any)["brand.prompt"].firstMatch.exists,
            "onboarding must show the KeyJawn prompt mark"
        )
        skip.tap()

        activateTab("Hosts")
        XCTAssertTrue(app.navigationBars["Hosts"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No hosts yet"].exists)
        XCTAssertTrue(
            app.staticTexts[
                "Connect to a remote SSH server. Commands run on that server. "
                    + "KeyJawn cannot browse files on your iPhone or iPad."
            ].exists
        )
        XCTAssertFalse(app.buttons["Preview"].exists)
        XCTAssertTrue(
            app.descendants(matching: .any)["brand.prompt"].firstMatch.exists,
            "empty hosts must show the KeyJawn prompt mark"
        )
        attachScreenshot(named: "03-remote-hosts")
        activateTab("Settings")
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["settings.setupKeyboard"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["settings.brand"].firstMatch.exists)
        attachScreenshot(named: "04-settings")

        activateTab("Hosts")
        XCTAssertTrue(app.navigationBars["Hosts"].waitForExistence(timeout: 3))
    }

    @MainActor
    func testSettingsReopensOnboardingAndSkipReturnsToSettings() {
        launchApp()
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

    @MainActor
    func testOnboardingContinueWalksEveryPageThenDone() {
        launchApp()
        let skip = app.buttons["onboarding.skip"]
        if skip.waitForExistence(timeout: 5) {
            attachScreenshot(named: "01-remote-boundary")
            skip.tap()
        }
        activateTab("Settings")
        app.buttons["settings.setupKeyboard"].tap()

        let continueButton = app.buttons["onboarding.continue"]
        XCTAssertTrue(continueButton.waitForExistence(timeout: 3))
        continueButton.tap()
        XCTAssertTrue(
            app.staticTexts["Turn on the keyboard"].waitForExistence(timeout: 2),
            "the screenshot must wait for the Full Access page"
        )
        attachScreenshot(named: "02-full-access")
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
    @MainActor
    private func activateTab(_ name: String) {
        let tab = app.tabBars.buttons[name]
        if tab.waitForExistence(timeout: 2) {
            tab.tap()
            return
        }

        let identifiers = [
            "Hosts": "server.rack",
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

    @MainActor
    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
