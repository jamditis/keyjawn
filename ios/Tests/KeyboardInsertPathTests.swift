import UIKit
import XCTest
@testable import KeyJawnKit

/// Drives the same insert mapping the keyboard extension uses, plus the
/// ExtraRow / QWERTY / slash views that feed it. Not a reimplementation of
/// `insertText` — the expected strings come from `KeyboardDocumentInsert`.
@MainActor
final class KeyboardInsertPathTests: XCTestCase {

    func testLettersSpaceBackspaceEscTabAndSlashTrigger() {
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .character("h"), ctrlActive: false, terminalArrows: true),
            .insert("h")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .character("i"), ctrlActive: false, terminalArrows: true),
            .insert("i")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .space, ctrlActive: false, terminalArrows: true),
            .insert(" ")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .backspace, ctrlActive: false, terminalArrows: true),
            .deleteBackward
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .escape, ctrlActive: false, terminalArrows: true),
            .insert("\u{1b}")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .tab, ctrlActive: false, terminalArrows: true),
            .insert("\t")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .slash, ctrlActive: false, terminalArrows: true),
            .openSlash
        )
        XCTAssertEqual(SlashCommand.all.first { $0.id == "compact" }?.trigger, "/compact")
    }

    func testCtrlLetterUsesTheSameMaskTheExtensionInserts() {
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .character("c"), ctrlActive: true, terminalArrows: true),
            .insert("\u{03}")
        )
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .character("d"), ctrlActive: true, terminalArrows: true),
            .insert("\u{04}")
        )
    }

    func testArmedCtrlSurvivesAMultiCharacterAlternate() {
        XCTAssertTrue(
            KeyboardDocumentInsert.consumesCtrl(for: .character("c"), ctrlActive: true)
        )
        XCTAssertFalse(
            KeyboardDocumentInsert.consumesCtrl(for: .character(".."), ctrlActive: true)
        )
        XCTAssertFalse(
            KeyboardDocumentInsert.consumesCtrl(for: .character("c"), ctrlActive: false)
        )
    }

    func testExtraRowButtonsEmitEscTabAndSlash() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 80))
        let extra = ExtraRowView()
        extra.frame = window.bounds
        extra.applyTheme(.dark)
        let recorder = ExtraRecorder()
        extra.delegate = recorder
        window.addSubview(extra)
        window.makeKeyAndVisible()
        extra.layoutIfNeeded()

        tapExtra(extra, accessibility: "Escape")
        tapExtra(extra, accessibility: "Tab")
        tapExtra(extra, accessibility: "Slash commands")

        XCTAssertEqual(recorder.outputs, [.escape, .tab, .slash])

        var inserted: [String] = []
        var openedSlash = false
        for output in recorder.outputs {
            switch KeyboardDocumentInsert.action(for: output, ctrlActive: false, terminalArrows: true) {
            case .insert(let text):
                inserted.append(text)
            case .openSlash:
                openedSlash = true
            default:
                XCTFail("unexpected action for \(output)")
            }
        }
        XCTAssertEqual(inserted, ["\u{1b}", "\t"])
        XCTAssertTrue(openedSlash)
        window.isHidden = true
    }

    func testQwertyButtonsInsertLettersSpaceAndBackspace() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 240))
        let qwerty = QwertyKeyboardView()
        qwerty.frame = window.bounds
        qwerty.applyTheme(.dark)
        let recorder = QwertyRecorder()
        qwerty.delegate = recorder
        window.addSubview(qwerty)
        window.makeKeyAndVisible()
        qwerty.layoutIfNeeded()

        tapQwerty(qwerty, title: "h")
        tapQwerty(qwerty, title: "i")
        tapQwerty(qwerty, title: "space")
        sendBackspace(qwerty)

        XCTAssertEqual(recorder.inserted, ["h", "i", " "])
        XCTAssertEqual(recorder.deletes, 1)
        XCTAssertEqual(
            KeyboardDocumentInsert.action(for: .character("h"), ctrlActive: false, terminalArrows: true),
            .insert("h")
        )
        window.isHidden = true
    }

    func testSlashPanelSelectsCompactTrigger() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 400))
        let panel = SlashCommandPanel(theme: .dark)
        var selected: String?
        panel.onSelect = { selected = $0.trigger }
        panel.frame = window.bounds
        window.addSubview(panel)
        window.makeKeyAndVisible()
        panel.layoutIfNeeded()

        guard let table = panel.subviews.compactMap({ $0 as? UITableView }).first else {
            return XCTFail("SlashCommandPanel has no table")
        }
        table.reloadData()
        table.layoutIfNeeded()

        var compactPath: IndexPath?
        for section in 0..<table.numberOfSections {
            for row in 0..<table.numberOfRows(inSection: section) {
                let path = IndexPath(row: row, section: section)
                let cell = table.dataSource?.tableView(table, cellForRowAt: path)
                if cell?.accessibilityLabel?.contains("/compact") == true {
                    compactPath = path
                    break
                }
            }
        }
        guard let compactPath else {
            return XCTFail("table has no /compact row")
        }

        table.selectRow(at: compactPath, animated: false, scrollPosition: .none)
        table.delegate?.tableView?(table, didSelectRowAt: compactPath)
        XCTAssertEqual(selected, "/compact")
        window.isHidden = true
    }

    private func tapExtra(_ extra: ExtraRowView, accessibility: String) {
        let button = extra.subviews
            .flatMap(\.subviews)
            .compactMap { $0 as? UIButton }
            .first { $0.accessibilityLabel?.contains(accessibility) == true
                || $0.currentTitle == accessibility }
        XCTAssertNotNil(button, "missing extra-row control \(accessibility)")
        button?.sendActions(for: .touchUpInside)
    }

    private func tapQwerty(_ qwerty: QwertyKeyboardView, title: String) {
        let button = qwerty.subviews
            .compactMap { $0 as? UIButton }
            .first { $0.currentTitle == title || $0.accessibilityLabel?.lowercased() == title }
        XCTAssertNotNil(button, "missing QWERTY key \(title)")
        button?.sendActions(for: .touchUpInside)
    }

    private func sendBackspace(_ qwerty: QwertyKeyboardView) {
        let button = qwerty.subviews
            .compactMap { $0 as? UIButton }
            .first { $0.accessibilityLabel?.localizedCaseInsensitiveContains("delete") == true
                || $0.accessibilityLabel?.localizedCaseInsensitiveContains("backspace") == true }
        XCTAssertNotNil(button, "missing backspace")
        button?.sendActions(for: .touchUpInside)
    }
}

@MainActor
private final class ExtraRecorder: ExtraRowDelegate {
    var outputs: [KeyOutput] = []
    func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        outputs.append(output)
    }
    func extraRowDidTapClipboard(_ view: ExtraRowView) {}
    func extraRowDidTapUpload(_ view: ExtraRowView) {}
    func extraRowDidTapMic(_ view: ExtraRowView) {}
    func extraRowDidCancelMic(_ view: ExtraRowView) {}
}

@MainActor
private final class QwertyRecorder: QwertyKeyboardDelegate {
    var inserted: [String] = []
    var deletes = 0
    func keyboard(_ keyboard: QwertyKeyboardView, insertText text: String) {
        inserted.append(text)
    }
    func keyboardDeleteBackward(_ keyboard: QwertyKeyboardView) {
        deletes += 1
    }
    func keyboardAdvanceToNextInputMode(_ keyboard: QwertyKeyboardView) {}
}
