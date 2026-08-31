import UIKit
import XCTest
@testable import KeyJawn
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
        XCTAssertFalse(text.contains("phone keyboard"), "iPad copy must not call it phone-only")
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

    func testCopyExplainsTheOptionalFullAccessFeatures() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("network access"))
        XCTAssertTrue(text.contains("remote image upload"))
        XCTAssertTrue(text.contains("user-created shortcuts"))
        XCTAssertTrue(text.contains("shared settings and clipboard history"))
    }

    func testCopyCoversAddingAHostAndCopyingThePublicKey() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("hosts"))
        XCTAssertTrue(text.contains("public key"))
        XCTAssertTrue(text.contains("authorized_keys"))
    }

    func testCopyExplainsTheRemoteExecutionAndLocalFileBoundary() {
        let text = OnboardingCopy.allUserVisibleText.lowercased()
        XCTAssertTrue(text.contains("remote ssh server"))
        XCTAssertTrue(text.contains("commands run on that server"))
        XCTAssertTrue(text.contains("cannot browse files on your iphone or ipad"))
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

    // MARK: - Host app theme

    func testHostAppThemeMatchesTheKeyJawnPalette() {
        XCTAssertEqual(rgb(AppTheme.backgroundColor), RGB(red: 10, green: 10, blue: 15))
        XCTAssertEqual(rgb(AppTheme.surfaceColor), RGB(red: 20, green: 20, blue: 28))
        XCTAssertEqual(rgb(AppTheme.textColor), RGB(red: 228, green: 228, blue: 236))
        XCTAssertEqual(rgb(AppTheme.mintColor), RGB(red: 108, green: 242, blue: 168))
        XCTAssertEqual(rgb(AppTheme.warmColor), RGB(red: 242, green: 108, blue: 138))
    }

    func testHostAppTextAndActionsMeetTheContrastFloor() {
        XCTAssertGreaterThanOrEqual(contrast(AppTheme.textColor, AppTheme.backgroundColor), 4.5)
        XCTAssertGreaterThanOrEqual(contrast(AppTheme.textColor, AppTheme.surfaceColor), 4.5)
        XCTAssertGreaterThanOrEqual(contrast(AppTheme.mintColor, AppTheme.backgroundColor), 4.5)
        XCTAssertGreaterThanOrEqual(contrast(AppTheme.warmColor, AppTheme.backgroundColor), 4.5)
    }

    private struct RGB: Equatable {
        let red: Int
        let green: Int
        let blue: Int
    }

    private func rgb(_ color: UIColor) -> RGB {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        XCTAssertTrue(color.getRed(&red, green: &green, blue: &blue, alpha: &alpha))
        return RGB(
            red: Int(round(red * 255)),
            green: Int(round(green * 255)),
            blue: Int(round(blue * 255))
        )
    }

    private func contrast(_ foreground: UIColor, _ background: UIColor) -> Double {
        let lighter = max(luminance(foreground), luminance(background))
        let darker = min(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private func luminance(_ color: UIColor) -> Double {
        let value = rgb(color)
        func linear(_ channel: Int) -> Double {
            let component = Double(channel) / 255
            return component <= 0.03928
                ? component / 12.92
                : pow((component + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(value.red) + 0.7152 * linear(value.green) + 0.0722 * linear(value.blue)
    }
}
