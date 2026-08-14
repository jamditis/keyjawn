import XCTest
@testable import KeyJawnKit

final class SlashCommandStoreTests: XCTestCase {

    func testAddFooAndRejectCompact() {
        let name = "com.keyjawn.tests.slash.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }

        let added = SlashCommandStore.add(trigger: "/foo", description: "mine", to: suite)
        guard case .success(let record) = added else {
            return XCTFail("expected /foo to be accepted")
        }
        XCTAssertEqual(record.trigger, "/foo")
        XCTAssertEqual(SlashCommandStore.commands(from: suite).map(\.trigger), ["/foo"])

        let duplicate = SlashCommandStore.add(trigger: "/compact", description: "nope", to: suite)
        XCTAssertEqual(duplicate, .failure(.duplicateTrigger))

        let again = SlashCommandStore.add(trigger: "foo", description: "again", to: suite)
        XCTAssertEqual(again, .failure(.duplicateTrigger))
    }

    func testPanelUnionIncludesCustomTrigger() {
        let name = "com.keyjawn.tests.slash-panel.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }
        _ = SlashCommandStore.add(trigger: "/foo", description: "mine", to: suite)
        let commands = SlashCommand.all + SlashCommandStore.commands(from: suite)
        XCTAssertTrue(commands.contains { $0.trigger == "/foo" })
        XCTAssertTrue(commands.contains { $0.trigger == "/compact" })
        XCTAssertEqual(Set(commands.map(\.trigger)).count, commands.count)
    }
}
