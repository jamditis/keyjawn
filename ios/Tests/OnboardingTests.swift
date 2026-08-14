import XCTest
@testable import KeyJawnKit

final class OnboardingTests: XCTestCase {

    private var suiteName: String!
    private var suite: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "com.keyjawn.tests.onboarding.\(UUID().uuidString)"
        suite = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        suite = nil
        suiteName = nil
        super.tearDown()
    }

    // MARK: - Flag

    func testFreshSuiteHasNotCompletedOnboarding() {
        XCTAssertFalse(KeyboardPrefs(defaults: suite).hasCompletedOnboarding)
    }

    func testCompletingOnboardingPersistsAcrossInstances() {
        KeyboardPrefs(defaults: suite).hasCompletedOnboarding = true
        XCTAssertTrue(KeyboardPrefs(defaults: suite).hasCompletedOnboarding)
    }

    func testSkippingOnboardingUsesTheSameCompletionFlag() {
        // Skip and Done are the same write. The wizard must not come back
        // after either gesture, including a process restart against the suite.
        let prefs = KeyboardPrefs(defaults: suite)
        prefs.hasCompletedOnboarding = true
        XCTAssertTrue(KeyboardPrefs(defaults: suite).hasCompletedOnboarding)
        prefs.hasCompletedOnboarding = false
        XCTAssertFalse(KeyboardPrefs(defaults: suite).hasCompletedOnboarding)
    }

    func testOnboardingFlagLivesInTheInjectedAppGroupSuite() {
        KeyboardPrefs(defaults: suite).hasCompletedOnboarding = true
        XCTAssertEqual(suite.object(forKey: "keyjawn.onboarding.completed") as? Bool, true)
    }

    // MARK: - Copy

    func testCopyCoversWhatTheAppIs() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("keyboard"), "must say it is a keyboard")
        XCTAssertTrue(text.contains("esc"), "must name a terminal key")
        XCTAssertTrue(text.contains("plain text"), "must say slash inserts text")
    }

    func testCopyCoversEnablingTheKeyboardAndFullAccess() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("keyjawn keyboard"))
        XCTAssertTrue(text.contains("add new keyboard"))
        XCTAssertTrue(text.contains("full access"))
        XCTAssertTrue(text.contains("without full access") || text.contains("works without full access"))
        XCTAssertTrue(OnboardingCopy.openSettingsTitle == "Open Settings")
    }

    func testCopyCoversAddingAHostAndCopyingThePublicKey() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("hosts"))
        XCTAssertTrue(text.contains("public key"))
        XCTAssertTrue(text.contains("authorized_keys"))
    }

    func testSkipAndDoneAreAvailable() {
        XCTAssertEqual(OnboardingCopy.skipTitle, "Skip")
        XCTAssertEqual(OnboardingCopy.doneTitle, "Done")
        XCTAssertEqual(OnboardingCopy.continueTitle, "Continue")
        XCTAssertEqual(OnboardingCopy.reopenTitle, "Set up keyboard")
    }

    func testCopyHasNoThirdPartyToolNames() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        for token in OnboardingCopy.forbiddenTokens {
            XCTAssertFalse(text.contains(token), "onboarding copy contains \(token)")
        }
    }

    func testPagesAreTheShippedSourceOfOnboardingCopy() {
        XCTAssertEqual(OnboardingCopy.pages.count, 3)
        XCTAssertEqual(OnboardingCopy.pages[0], OnboardingCopy.whatItIs)
        XCTAssertEqual(OnboardingCopy.pages[1], OnboardingCopy.enableKeyboard)
        XCTAssertEqual(OnboardingCopy.pages[2], OnboardingCopy.addAHost)
    }
}
