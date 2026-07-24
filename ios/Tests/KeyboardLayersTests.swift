import XCTest
@testable import KeyJawnKit

final class KeyboardLayersTests: XCTestCase {

    // MARK: - Shape

    /// The positioning code derives key widths from row shape, so these counts are
    /// load-bearing rather than cosmetic.
    func testRowShapes() {
        XCTAssertEqual(shape(of: .lowercase), [10, 9, 9, 4])
        XCTAssertEqual(shape(of: .uppercase), [10, 9, 9, 4])
        XCTAssertEqual(shape(of: .symbols),   [10, 10, 7, 4])
        XCTAssertEqual(shape(of: .symbols2),  [10, 9, 7, 4])
    }

    func testEveryLayerHasFourRows() {
        for layer in KeyboardLayerType.allCases {
            XCTAssertEqual(KeyboardLayers.rows(for: layer).count, 4, "\(layer)")
        }
    }

    /// The in-place relabel fast path in QwertyKeyboardView reuses buttons across a
    /// shift only while the two layers agree position by position on the kind of key.
    func testLowercaseAndUppercaseArePositionIdentical() {
        let lower = KeyboardLayers.rows(for: .lowercase).flatMap { $0 }
        let upper = KeyboardLayers.rows(for: .uppercase).flatMap { $0 }
        XCTAssertEqual(lower.count, upper.count)
        for (l, u) in zip(lower, upper) {
            switch (l, u) {
            case (.character(let a), .character(let b)):
                XCTAssertEqual(a.uppercased(), b, "\(a) should shift to \(b)")
            default:
                XCTAssertEqual(l, u, "non-character keys must sit in the same slots")
            }
        }
    }

    func testEveryLayerEndsWithTheSameBottomBar() {
        for layer in KeyboardLayerType.allCases {
            let bottom = KeyboardLayers.rows(for: layer)[3]
            XCTAssertEqual(bottom.count, 4, "\(layer)")
            XCTAssertEqual(bottom[1], .globe, "\(layer)")
            XCTAssertEqual(bottom[2], .space, "\(layer)")
            XCTAssertEqual(bottom[3], .return, "\(layer)")
        }
    }

    /// Row two is a wide modifier, the middle keys, then a wide backspace.
    /// `QwertyKeyboardView.rowType` keys off exactly this.
    func testThirdRowLeadsWithAModifierAndEndsWithBackspace() {
        let expectedLead: [KeyboardLayerType: QwertyKey] = [
            .lowercase: .shift,
            .uppercase: .shift,
            .symbols: .more,
            .symbols2: .symbolsToggle,
        ]
        for layer in KeyboardLayerType.allCases {
            let row = KeyboardLayers.rows(for: layer)[2]
            XCTAssertEqual(row.first, expectedLead[layer], "\(layer)")
            XCTAssertEqual(row.last, .backspace, "\(layer)")
        }
    }

    // MARK: - Reachability

    /// Before the second symbols page existed, none of these could be typed on this
    /// keyboard at all — no pipe, no underscore, no tilde, no brackets or braces on a
    /// keyboard whose entire purpose is driving a shell.
    func testShellSymbolsAreAllReachable() {
        let typeable = allCharacters()
        for symbol in KeyboardLayers.shellSymbols.sorted() {
            XCTAssertTrue(typeable.contains(symbol), "\(symbol) cannot be typed on any layer")
        }
    }

    func testSecondSymbolsPageCarriesTheShellSymbols() {
        let page2 = characters(in: .symbols2)
        for symbol in KeyboardLayers.shellSymbols.sorted() {
            XCTAssertTrue(page2.contains(symbol), "\(symbol) is missing from the #+= page")
        }
    }

    /// Long-press alternates and the number row's shifted symbols are the other two
    /// routes to a character. Together with the layers they have to cover everything a
    /// command line needs.
    func testCommonShellCharactersAreReachableSomehow() {
        var reachable = allCharacters()
        reachable.formUnion(AltKeyMappings.numberShifts.values)
        for label in reachable.union(["-", "/", "(", ")", ".", "'", "\"", "$", ";", ":"]) {
            reachable.formUnion(AltKeyMappings.alts(for: label))
        }

        let needed = ["|", "&", ";", "$", "*", "?", "~", "_", "-", "/", "\\", "`",
                      "\"", "'", "(", ")", "[", "]", "{", "}", "<", ">", "=", "+",
                      "#", "%", "^", "@", ":", ".", ","]
        for character in needed {
            XCTAssertTrue(reachable.contains(character), "\(character) is unreachable")
        }
    }

    func testDigitsAreOnTheSymbolsPage() {
        let page1 = characters(in: .symbols)
        for digit in "0123456789" {
            XCTAssertTrue(page1.contains(String(digit)), "\(digit) missing from symbols")
        }
    }

    /// The `#+=` key is the only route to the second page, and it used to rebuild the
    /// page it was already on.
    func testMoreKeyExistsOnlyOnTheFirstSymbolsPage() {
        for layer in KeyboardLayerType.allCases {
            let hasMore = KeyboardLayers.rows(for: layer).flatMap { $0 }.contains(.more)
            XCTAssertEqual(hasMore, layer == .symbols, "\(layer)")
        }
    }

    /// And the second page needs a way back that is not the letters key.
    func testSecondPageOffersARouteBackToTheFirst() {
        let keys = KeyboardLayers.rows(for: .symbols2).flatMap { $0 }
        XCTAssertTrue(keys.contains(.symbolsToggle))
        XCTAssertTrue(keys.contains(.alphabeticToggle))
    }

    // MARK: - Helpers

    private func shape(of layer: KeyboardLayerType) -> [Int] {
        KeyboardLayers.rows(for: layer).map(\.count)
    }

    private func characters(in layer: KeyboardLayerType) -> Set<String> {
        Set(KeyboardLayers.rows(for: layer).flatMap { $0 }.compactMap { key in
            if case .character(let s) = key { return s }
            return nil
        })
    }

    private func allCharacters() -> Set<String> {
        KeyboardLayerType.allCases.reduce(into: Set<String>()) { result, layer in
            result.formUnion(characters(in: layer))
        }
    }
}
