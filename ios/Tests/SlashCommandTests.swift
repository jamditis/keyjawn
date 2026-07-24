import XCTest
@testable import KeyJawnKit

final class SlashCommandTests: XCTestCase {

    /// The list used to be two sets concatenated, which put `/help` and `/clear` in
    /// the panel twice with identical descriptions and no way to tell the rows apart.
    func testTriggersAreUnique() {
        let triggers = SlashCommand.all.map(\.trigger)
        XCTAssertEqual(Set(triggers).count, triggers.count,
                       "duplicate triggers: \(duplicates(in: triggers))")
    }

    func testIdentifiersAreUnique() {
        let ids = SlashCommand.all.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count, "duplicate ids: \(duplicates(in: ids))")
    }

    func testEveryTriggerIsASlashCommand() {
        for command in SlashCommand.all {
            XCTAssertTrue(command.trigger.hasPrefix("/"), "\(command.trigger) is not a slash command")
            XCTAssertFalse(command.trigger.contains(" "), "\(command.trigger) contains a space")
            XCTAssertGreaterThan(command.trigger.count, 1, "\(command.id) has an empty trigger")
        }
    }

    func testEveryCommandIsDescribed() {
        for command in SlashCommand.all {
            XCTAssertFalse(command.description.isEmpty, "\(command.trigger) has no description")
        }
    }

    // MARK: - Grouping

    /// Grouping is what the panel renders, so it has to account for every command
    /// exactly once — a command in no group would silently vanish from the list.
    func testGroupingIsATotalPartition() {
        // Closures rather than key paths: Swift has no key paths into tuple elements.
        let grouped = SlashCommand.grouped.flatMap { $0.commands }
        XCTAssertEqual(grouped.count, SlashCommand.all.count)
        XCTAssertEqual(Set(grouped.map(\.id)), Set(SlashCommand.all.map(\.id)))
    }

    func testGroupsAreNeverEmpty() {
        for group in SlashCommand.grouped {
            XCTAssertFalse(group.commands.isEmpty, "\(group.category) renders an empty section header")
        }
    }

    func testGroupsFollowDeclaredCategoryOrder() {
        let order = SlashCommand.Category.allCases
        let produced = SlashCommand.grouped.map { $0.category }
        let expected = order.filter { category in produced.contains(category) }
        XCTAssertEqual(produced, expected)
    }

    func testEveryCommandInAGroupCarriesThatCategory() {
        for group in SlashCommand.grouped {
            for command in group.commands {
                XCTAssertEqual(command.category, group.category, "\(command.trigger) is filed wrong")
            }
        }
    }

    // MARK: - Helpers

    private func duplicates(in values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { !seen.insert($0).inserted }
    }
}
