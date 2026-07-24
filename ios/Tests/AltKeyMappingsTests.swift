import XCTest
@testable import KeyJawnKit

final class AltKeyMappingsTests: XCTestCase {

    func testAccentedVowels() {
        XCTAssertEqual(AltKeyMappings.alts(for: "e"), ["é", "è", "ê", "ë"])
        XCTAssertEqual(AltKeyMappings.alts(for: "n"), ["ñ"])
    }

    /// The table only carries lowercase; uppercase is derived so it cannot drift.
    func testUppercaseIsDerivedFromLowercase() {
        XCTAssertEqual(AltKeyMappings.alts(for: "E"), ["É", "È", "Ê", "Ë"])
        XCTAssertEqual(AltKeyMappings.alts(for: "N"), ["Ñ"])
    }

    func testKeysWithoutAlternatesReturnEmpty() {
        XCTAssertTrue(AltKeyMappings.alts(for: "q").isEmpty)
        XCTAssertTrue(AltKeyMappings.alts(for: "Q").isEmpty)
        XCTAssertTrue(AltKeyMappings.alts(for: "&").isEmpty)
    }

    // MARK: - Shell-oriented alternates

    /// Underscore is the single most-typed alternate on this keyboard, so it leads —
    /// the em dash it displaced belongs to prose, not to `my_file`.
    func testHyphenLeadsWithUnderscore() {
        XCTAssertEqual(AltKeyMappings.alts(for: "-").first, "_")
    }

    func testSlashOffersBackslashAndPipe() {
        XCTAssertEqual(AltKeyMappings.alts(for: "/"), ["\\", "|"])
    }

    /// Brackets one press from the first symbols page, so `[]`, `{}` and `<>` never
    /// require a detour to the second.
    func testParenthesesOfferBracketsAndBraces() {
        XCTAssertEqual(AltKeyMappings.alts(for: "("), ["[", "{", "<"])
        XCTAssertEqual(AltKeyMappings.alts(for: ")"), ["]", "}", ">"])
    }

    func testQuotesOfferBacktick() {
        XCTAssertEqual(AltKeyMappings.alts(for: "'").first, "`")
        XCTAssertEqual(AltKeyMappings.alts(for: "\"").first, "`")
    }

    // MARK: - Number row

    func testNumberRowShiftsCoverEveryDigit() {
        for digit in "0123456789" {
            XCTAssertNotNil(AltKeyMappings.numberShifts[String(digit)], "\(digit) has no shifted symbol")
        }
        XCTAssertEqual(AltKeyMappings.numberShifts.count, 10)
    }

    func testNumberRowShiftsMatchTheStandardLayout() {
        XCTAssertEqual(AltKeyMappings.numberShifts["1"], "!")
        XCTAssertEqual(AltKeyMappings.numberShifts["7"], "&")
        XCTAssertEqual(AltKeyMappings.numberShifts["0"], ")")
    }

    // MARK: - Invariants

    func testNoAlternateIsEmptyOrDuplicated() {
        for (key, alts) in AltKeyMappings.table {
            XCTAssertFalse(alts.isEmpty, "\(key) maps to an empty list, which reads as a dead long-press")
            XCTAssertEqual(Set(alts).count, alts.count, "\(key) repeats an alternate")
            XCTAssertFalse(alts.contains(key), "\(key) lists itself as its own alternate")
            for alt in alts {
                XCTAssertFalse(alt.isEmpty, "\(key) has an empty alternate")
            }
        }
    }

    /// The popup is sized at 52pt per alternate against the window width, so a very
    /// long list would be squeezed to unusable targets on a small phone.
    func testAlternateListsStayTappable() {
        for (key, alts) in AltKeyMappings.table {
            XCTAssertLessThanOrEqual(alts.count, 6, "\(key) has too many alternates to tap comfortably")
        }
    }
}
