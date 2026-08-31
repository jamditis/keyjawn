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

    func testArmedCtrlSurvivesAnUnmappedAlternate() {
        XCTAssertTrue(
            KeyboardDocumentInsert.consumesCtrl(for: .character("c"), ctrlActive: true)
        )
        XCTAssertFalse(
            KeyboardDocumentInsert.consumesCtrl(for: .character("é"), ctrlActive: true)
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

    func testQwertyUsesDenseRowsWithoutShrinkingPortraitTouchTargets() {
        let pad = QwertyKeyboardView(frame: CGRect(x: 0, y: 0, width: 660, height: 300))
        assertDenseLayout(pad)
        tapQwerty(pad, title: "shift")
        assertDenseLayout(pad)
        tapQwerty(pad, title: "123")
        assertDenseLayout(pad)
        tapQwerty(pad, title: "#+=")
        assertDenseLayout(pad)

        let phone = QwertyKeyboardView(frame: CGRect(x: 0, y: 0, width: 390, height: 220))
        assertDenseLayout(phone)
    }

    func testLetterKeysMatchTheDeviceFlickCapability() throws {
        let qwerty = QwertyKeyboardView(frame: CGRect(x: 0, y: 0, width: 660, height: 300))
        let recorder = QwertyRecorder()
        qwerty.delegate = recorder
        qwerty.layoutIfNeeded()

        let qKey = try XCTUnwrap(
            qwerty.subviews.compactMap { $0 as? UIButton }.first { $0.currentTitle == "q" }
        )
        let downwardFlick = qKey.gestureRecognizers?
            .compactMap { $0 as? UISwipeGestureRecognizer }
            .first { $0.direction == .down }
        let visibleLabels = qKey.subviews.compactMap { ($0 as? UILabel)?.text }

        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertNotNil(downwardFlick)
            XCTAssertEqual(downwardFlick?.cancelsTouchesInView, true)
            XCTAssertTrue(visibleLabels.contains("1"))
            XCTAssertEqual(qKey.accessibilityHint, "Swipe down for 1")
            let action = try XCTUnwrap(qKey.accessibilityCustomActions?.first)
            XCTAssertEqual(action.name, "Insert 1")
            XCTAssertTrue(action.actionHandler?(action) == true)
            XCTAssertEqual(recorder.inserted, ["1"])

            for theme in KeyboardTheme.allCases {
                qwerty.applyTheme(theme)
                let rebuiltQ = try XCTUnwrap(
                    qwerty.subviews.compactMap { $0 as? UIButton }.first { $0.currentTitle == "q" }
                )
                let flicks = rebuiltQ.gestureRecognizers?
                    .compactMap { $0 as? UISwipeGestureRecognizer }
                    .filter { $0.direction == .down }
                let labels = rebuiltQ.subviews.compactMap { ($0 as? UILabel)?.text }
                XCTAssertEqual(flicks?.count, 1)
                XCTAssertEqual(labels.filter { $0 == "1" }.count, 1)
            }
        } else {
            XCTAssertNil(downwardFlick)
            XCTAssertFalse(visibleLabels.contains("1"))
            XCTAssertNil(qKey.accessibilityHint)
        }
    }

    func testCtrlStateHasSpokenAndNonColorIndicators() throws {
        let extra = ExtraRowView(frame: CGRect(x: 0, y: 0, width: 640, height: 62))
        extra.layoutIfNeeded()
        let button = try XCTUnwrap(
            extra.subviews
                .flatMap(\.subviews)
                .compactMap { $0 as? UIButton }
                .first { $0.accessibilityIdentifier == "extra.ctrlC" }
        )

        XCTAssertEqual(button.accessibilityValue, "Off")
        XCTAssertEqual(button.currentTitle, "^C")

        extra.ctrl.toggle()
        XCTAssertEqual(button.accessibilityValue, "Armed for next key")
        XCTAssertEqual(button.currentTitle, "^C·")

        extra.ctrl.toggle()
        XCTAssertEqual(button.accessibilityValue, "Locked")
        XCTAssertEqual(button.currentTitle, "^C∞")

        extra.ctrl.toggle()
        XCTAssertEqual(button.accessibilityValue, "Off")
        XCTAssertEqual(button.currentTitle, "^C")
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
            .first {
                $0.accessibilityLabel?.contains(accessibility) == true
                    || $0.currentTitle == accessibility
            }
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
            .first {
                $0.accessibilityLabel?.localizedCaseInsensitiveContains("delete") == true
                    || $0.accessibilityLabel?.localizedCaseInsensitiveContains("backspace") == true
            }
        XCTAssertNotNil(button, "missing backspace")
        button?.sendActions(for: .touchUpInside)
    }

    private func assertDenseLayout(
        _ qwerty: QwertyKeyboardView,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        qwerty.layoutIfNeeded()
        let frames = qwerty.subviews.compactMap { ($0 as? UIButton)?.frame }
        let rowMinYs = Array(Set(frames.map(\.minY))).sorted()
        let rowMaxYs = rowMinYs.map { minY in
            frames.filter { $0.minY == minY }.map(\.maxY).max() ?? minY
        }
        let verticalGaps = zip(rowMaxYs, rowMinYs.dropFirst()).map { currentMaxY, nextMinY in
            nextMinY - currentMaxY
        }

        XCTAssertEqual(rowMinYs.count, 4, file: file, line: line)
        for gap in verticalGaps {
            XCTAssertEqual(gap, 7, accuracy: 0.01, file: file, line: line)
        }
        XCTAssertGreaterThanOrEqual(frames.map(\.height).min() ?? 0, 44, file: file, line: line)
        for frame in frames {
            XCTAssertTrue(qwerty.bounds.contains(frame), "key outside QWERTY bounds", file: file, line: line)
        }
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
