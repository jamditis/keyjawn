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
        if skip.waitForExistence(timeout: 5) {
            skip.tap()
        }

        XCTAssertTrue(app.tabBars.buttons["Hosts"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Preview"].tap()
        XCTAssertTrue(app.navigationBars["Preview"].waitForExistence(timeout: 3))
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["settings.setupKeyboard"].exists)

        app.tabBars.buttons["Hosts"].tap()
        XCTAssertTrue(app.navigationBars["Hosts"].waitForExistence(timeout: 3))
    }

    func testSettingsReopensOnboardingAndSkipReturnsToSettings() {
        let skip = app.buttons["onboarding.skip"]
        if skip.waitForExistence(timeout: 5) {
            skip.tap()
        }

        app.tabBars.buttons["Settings"].tap()
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
        app.tabBars.buttons["Settings"].tap()
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
}
