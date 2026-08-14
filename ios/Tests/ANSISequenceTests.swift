import XCTest
@testable import KeyJawnKit

/// The byte sequences here are the entire contract between a key press and a remote
/// shell. A wrong byte is not a visual glitch — it is an interrupt that does not
/// interrupt, or history that does not scroll.
final class ANSISequenceTests: XCTestCase {

    func testControlCharacters() {
        XCTAssertEqual(ANSISequence.bytes(for: .ctrlC), [0x03])
        XCTAssertEqual(ANSISequence.bytes(for: .ctrlD), [0x04])
        XCTAssertEqual(ANSISequence.bytes(for: .escape), [0x1b])
        XCTAssertEqual(ANSISequence.bytes(for: .tab), [0x09])
        XCTAssertEqual(ANSISequence.bytes(for: .backspace), [0x7f])
        XCTAssertEqual(ANSISequence.bytes(for: .return), [0x0d])
        XCTAssertEqual(ANSISequence.bytes(for: .send), [0x0d])
        XCTAssertEqual(ANSISequence.bytes(for: .newline), [0x0a])
        XCTAssertEqual(ANSISequence.bytes(for: .space), [0x20])
    }

    func testArrowsAreCSISequences() {
        XCTAssertEqual(ANSISequence.bytes(for: .arrowUp), [0x1b, 0x5b, 0x41])
        XCTAssertEqual(ANSISequence.bytes(for: .arrowDown), [0x1b, 0x5b, 0x42])
        XCTAssertEqual(ANSISequence.bytes(for: .arrowRight), [0x1b, 0x5b, 0x43])
        XCTAssertEqual(ANSISequence.bytes(for: .arrowLeft), [0x1b, 0x5b, 0x44])
    }

    func testCtrlModifiedArrowsUseTheModifierForm() {
        XCTAssertEqual(ANSISequence.bytes(for: .arrowLeft, ctrlActive: true),
                       [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x44])
        XCTAssertEqual(ANSISequence.bytes(for: .arrowRight, ctrlActive: true),
                       [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x43])
    }

    /// Ctrl+letter is the combination the three-state modifier exists to produce.
    func testCtrlMasksCharactersIntoControlCodes() {
        XCTAssertEqual(ANSISequence.bytes(for: .character("c"), ctrlActive: true), [0x03])
        XCTAssertEqual(ANSISequence.bytes(for: .character("d"), ctrlActive: true), [0x04])
        XCTAssertEqual(ANSISequence.bytes(for: .character("z"), ctrlActive: true), [0x1a])
        XCTAssertEqual(ANSISequence.bytes(for: .character("l"), ctrlActive: true), [0x0c])
        XCTAssertEqual(ANSISequence.bytes(for: .character("a"), ctrlActive: true), [0x01])
        // Case does not change the control code, matching every terminal emulator.
        XCTAssertEqual(ANSISequence.bytes(for: .character("C"), ctrlActive: true), [0x03])
    }

    func testPlainCharacterPassesThrough() {
        XCTAssertEqual(ANSISequence.bytes(for: .character("x")), [0x78])
    }

    func testSlashHasNoBytesBecauseThePanelHandlesIt() {
        XCTAssertNil(ANSISequence.bytes(for: .slash))
        XCTAssertNil(ANSISequence.text(for: .slash))
    }

    /// What the keyboard extension actually inserts, since it has no byte channel.
    func testTextFormMatchesTheByteForm() {
        XCTAssertEqual(ANSISequence.text(for: .arrowUp), "\u{1b}[A")
        XCTAssertEqual(ANSISequence.text(for: .arrowDown), "\u{1b}[B")
        XCTAssertEqual(ANSISequence.text(for: .escape), "\u{1b}")
        XCTAssertEqual(ANSISequence.text(for: .ctrlC), "\u{03}")
        XCTAssertEqual(ANSISequence.text(for: .tab), "\t")
    }

    /// Every key the extra row can emit has to produce something, or it is a key that
    /// looks live and does nothing — which is exactly what ^C and the vertical arrows
    /// were before they were routed through here.
    func testEveryExtraRowKeyProducesOutput() {
        for key in ExtraRowKey.defaults {
            guard let output = key.output else { continue }   // clipboard, upload
            if output == .slash { continue }                  // opens the panel instead
            XCTAssertNotNil(ANSISequence.bytes(for: output),
                            "\(key.label) produces no bytes")
            XCTAssertNotNil(ANSISequence.text(for: output),
                            "\(key.label) produces no insertable text")
        }
    }

    /// The visible labels are glyphs and abbreviations; VoiceOver needs words.
    func testEveryExtraRowKeyHasASpokenName() {
        for key in ExtraRowKey.defaults {
            XCTAssertFalse(key.accessibilityLabel.isEmpty, "\(key.label) is unnamed for VoiceOver")
        }
        // The arrow glyphs in particular read as nothing useful on their own.
        let arrows: [ExtraRowSlot] = [.arrowUp, .arrowDown, .arrowLeft, .arrowRight]
        for key in ExtraRowKey.defaults where arrows.contains(key.slot) {
            XCTAssertNotEqual(key.accessibilityLabel, key.label,
                              "\(key.label) falls back to its glyph for VoiceOver")
        }
    }

    func testExtraRowIdentifiersAreStableAndNotTheVisibleGlyph() throws {
        let ctrl = try XCTUnwrap(ExtraRowKey.defaults.first { $0.slot == .ctrlC })
        XCTAssertEqual(ctrl.accessibilityIdentifier, "extra.ctrlC")
        XCTAssertNotEqual(ctrl.accessibilityIdentifier, ctrl.label)
        for key in ExtraRowKey.defaults {
            XCTAssertEqual(key.accessibilityIdentifier, "extra.\(key.slot)")
        }
    }
}
