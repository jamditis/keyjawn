import XCTest
@testable import KeyJawnKit

final class ExtraRowPresetTests: XCTestCase {

    func testAgentRoundTripsToDefaultTenKeyRow() {
        XCTAssertEqual(ExtraRowPreset.decode(ExtraRowPreset.agent.encoded), .agent)
        XCTAssertEqual(
            ExtraRowPreset.agent.keys.map(\.slot),
            ExtraRowKey.defaults.map(\.slot)
        )
        XCTAssertEqual(
            ExtraRowPreset.agent.keys.map(\.label),
            ExtraRowKey.defaults.map(\.label)
        )
    }

    func testConfirmEncodesAnswersAndASubmit() {
        XCTAssertEqual(ExtraRowPreset.decode(ExtraRowPreset.confirm.encoded), .confirm)
        let outputs = ExtraRowPreset.confirm.keys.compactMap(\.output)
        for expected: KeyOutput in [
            .character("y"), .character("n"), .character("a"),
            .character("1"), .character("2"), .character("3"),
            .send, .escape, .ctrlC,
        ] {
            XCTAssertTrue(outputs.contains(expected), "Confirm is missing \(expected)")
        }
        XCTAssertEqual(
            TerminalInputMapping.extraRow(.send, ctrlActive: false),
            .write(TerminalInputMapping.submitBytes)
        )
    }

    func testUnknownAndMissingPresetDecodeAsAgent() {
        XCTAssertEqual(ExtraRowPreset.decode(nil), .agent)
        XCTAssertEqual(ExtraRowPreset.decode("nope"), .agent)
        XCTAssertEqual(ExtraRowPreset.decode(""), .agent)
    }

    func testPrefsPersistThePresetOnAnInjectedSuite() {
        let name = "com.keyjawn.tests.preset.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }

        let writer = KeyboardPrefs(defaults: suite, migratesLegacyValues: false)
        XCTAssertEqual(writer.extraRowPreset, .agent)
        writer.extraRowPreset = .confirm
        let reader = KeyboardPrefs(defaults: suite, migratesLegacyValues: false)
        XCTAssertEqual(reader.extraRowPreset, .confirm)
        XCTAssertEqual(reader.extraRowPreset.keys.map(\.slot), ExtraRowPreset.confirm.keys.map(\.slot))
    }
}
