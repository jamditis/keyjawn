import XCTest
@testable import KeyJawnKit

@MainActor
final class ClipboardHistoryTests: XCTestCase {

    func testPinIsVisibleToAnotherInstanceOnTheSameSuite() {
        let name = "com.keyjawn.tests.clip-pin.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }

        let writer = ClipboardHistory(defaults: suite)
        writer.pin("pinned-path")
        let reader = ClipboardHistory(defaults: suite)
        XCTAssertTrue(reader.isPinned("pinned-path"))
        XCTAssertEqual(reader.pinned, ["pinned-path"])
    }

    func testLegacyStandardSuitePinsMoveToTheAppGroup() {
        let suiteName = "com.keyjawn.tests.clip-migrate.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: suiteName)!
        defer { UserDefaults.standard.removePersistentDomain(forName: suiteName) }

        let previous = UserDefaults.standard.stringArray(forKey: "keyjawn.clipboard.pinned")
        UserDefaults.standard.set(["legacy-pin"], forKey: "keyjawn.clipboard.pinned")
        defer {
            if let previous {
                UserDefaults.standard.set(previous, forKey: "keyjawn.clipboard.pinned")
            } else {
                UserDefaults.standard.removeObject(forKey: "keyjawn.clipboard.pinned")
            }
        }

        let history = ClipboardHistory(defaults: suite, migratesLegacyValues: true)
        XCTAssertTrue(history.isPinned("legacy-pin"))
        XCTAssertTrue(ClipboardHistory(defaults: suite, migratesLegacyValues: false).isPinned("legacy-pin"))
    }

    func testUninjectedHistoryUsesAppGroupSuite() {
        let suite = UserDefaults(suiteName: AppGroupConfig.suiteName)
        XCTAssertNotNil(suite, "group.com.keyjawn suite must resolve in tests")
        let sentinel = "suite-sentinel-\(UUID().uuidString)"
        let key = "keyjawn.clipboard.history"
        let previous = suite?.stringArray(forKey: key)
        suite?.set([sentinel], forKey: key)
        defer {
            if let previous {
                suite?.set(previous, forKey: key)
            } else {
                suite?.removeObject(forKey: key)
            }
        }

        let history = ClipboardHistory()
        XCTAssertTrue(history.items.contains(sentinel),
                      "uninjected ClipboardHistory must read group.com.keyjawn")
    }
}
