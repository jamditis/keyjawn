import XCTest
@testable import KeyJawnKit

final class HardwareKeyMappingTests: XCTestCase {

    func testEscapeTabAndArrows() {
        XCTAssertEqual(HardwareKeyMapping.bytes(for: .escape, modifiers: []), [0x1b])
        XCTAssertEqual(HardwareKeyMapping.bytes(for: .tab, modifiers: []), [0x09])
        XCTAssertEqual(
            HardwareKeyMapping.bytes(for: .arrowUp, modifiers: []),
            ANSISequence.bytes(for: .arrowUp)
        )
        XCTAssertEqual(
            HardwareKeyMapping.bytes(for: .arrowDown, modifiers: []),
            ANSISequence.bytes(for: .arrowDown)
        )
        XCTAssertEqual(
            HardwareKeyMapping.bytes(for: .arrowLeft, modifiers: []),
            ANSISequence.bytes(for: .arrowLeft)
        )
        XCTAssertEqual(
            HardwareKeyMapping.bytes(for: .arrowRight, modifiers: []),
            ANSISequence.bytes(for: .arrowRight)
        )
    }

    func testCtrlLettersMatchTheExtraRow() {
        XCTAssertEqual(HardwareKeyMapping.bytes(for: .character("c"), modifiers: .control), [0x03])
        XCTAssertEqual(HardwareKeyMapping.bytes(for: .character("d"), modifiers: .control), [0x04])
        XCTAssertEqual(HardwareKeyMapping.bytes(for: .character("z"), modifiers: .control), [0x1a])
    }

    func testCmdCIsNotInterrupt() {
        XCTAssertNil(HardwareKeyMapping.bytes(for: .character("c"), modifiers: .command))
        XCTAssertNil(HardwareKeyMapping.bytes(for: .character("c"), modifiers: [.command, .control]))
        XCTAssertNotEqual(
            HardwareKeyMapping.bytes(for: .character("c"), modifiers: .command),
            [0x03]
        )
    }

    func testPlainCharactersStayOnInsertText() {
        XCTAssertNil(HardwareKeyMapping.bytes(for: .character("x"), modifiers: []))
    }
}
