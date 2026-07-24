import XCTest
@testable import KeyJawnKit

/// Word deletion is N backward deletes, so an off-by-one here eats a character of the
/// previous word or leaves one behind — both of which read as a broken keyboard.
final class WordBoundaryTests: XCTestCase {

    func testDeletesTheWordBeforeTheCursor() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "hello world"), 5)
    }

    func testConsumesTrailingSpacesWithTheWord() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "hello world "), 6)
        XCTAssertEqual(WordBoundary.deleteCount(before: "hello world   "), 8)
    }

    /// A path is one word. Splitting on the slashes would make word delete useless for
    /// the thing this keyboard is mostly used to type.
    func testPathsAreASingleWord() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "cat src/main/App.kt"), 15)
        XCTAssertEqual(WordBoundary.deleteCount(before: "ls ~/.ssh/config"), 13)
    }

    func testFlagsAreASingleWord() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "git commit --no-verify"), 11)
    }

    func testStopsAtALineBreak() {
        // The word on this line only — the previous line survives.
        XCTAssertEqual(WordBoundary.deleteCount(before: "first line\nsecond"), 6)
    }

    /// Sitting on an empty line, the break itself is the next thing to remove, so a
    /// held backspace keeps making progress instead of stalling.
    func testRemovesABareLineBreak() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "text\n"), 1)
    }

    /// Trailing spaces go first, then the break — two steps, not one, so a hold does
    /// not jump a whole line at once.
    func testTrailingSpacesAfterABreakGoFirst() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "text\n   "), 3)
    }

    func testEmptyContextDeletesNothing() {
        XCTAssertEqual(WordBoundary.deleteCount(before: ""), 0)
    }

    func testOnlyWhitespace() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "   "), 3)
        XCTAssertEqual(WordBoundary.deleteCount(before: "\t\t"), 2)
    }

    func testSingleWord() {
        XCTAssertEqual(WordBoundary.deleteCount(before: "npm"), 3)
    }

    /// Never more than there is to delete, whatever the input.
    func testCountNeverExceedsTheContext() {
        let samples = [
            "", " ", "\n", "a", "a ", " a", "\n ", " \n", "a\nb", "--flag=value",
            "echo \"hello world\"", "cd ..", "\ttabbed", "emoji 🎹 here",
        ]
        for sample in samples {
            let count = WordBoundary.deleteCount(before: sample)
            XCTAssertGreaterThanOrEqual(count, 0, "negative count for \(sample.debugDescription)")
            XCTAssertLessThanOrEqual(count, sample.count,
                                     "over-delete for \(sample.debugDescription)")
        }
    }

    /// Repeatedly applying the rule always terminates and always empties the string,
    /// which is what a user holding backspace on a long prompt is doing.
    func testRepeatedApplicationDrainsAnyString() {
        var text = "git commit -m \"fix: the thing\"\nnpm run build -- --watch"
        var steps = 0
        while !text.isEmpty {
            let count = WordBoundary.deleteCount(before: text)
            XCTAssertGreaterThan(count, 0, "stalled with \(text.debugDescription) remaining")
            text.removeLast(count)
            steps += 1
            XCTAssertLessThan(steps, 200, "did not converge")
        }
    }
}
