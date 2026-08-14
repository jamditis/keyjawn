import UIKit
import XCTest
@testable import KeyJawn
@testable import KeyJawnKit

/// Drives the same mapping `TerminalInputView.insertText` and extra-row Send use.
final class TerminalInputMappingTests: XCTestCase {

    func testLoneReturnIsCR() {
        XCTAssertEqual(TerminalInputMapping.bytes(forInsertedText: "\n"), [0x0d])
        XCTAssertEqual(TerminalInputMapping.bytes(forInsertedText: "\r"), [0x0d])
        XCTAssertEqual(TerminalInputMapping.bytes(forInsertedText: "\n"), TerminalInputMapping.submitBytes)
    }

    func testNonSubmitNewlineIsNotASubmit() {
        XCTAssertEqual(TerminalInputMapping.newlineBytes, [0x0a])
        XCTAssertNotEqual(TerminalInputMapping.newlineBytes, TerminalInputMapping.submitBytes)
    }

    func testOrdinaryTextIsUTF8() {
        XCTAssertEqual(TerminalInputMapping.bytes(forInsertedText: "hi"), Array("hi".utf8))
    }

    func testMultilinePasteIsNotCollapsedToSubmit() {
        XCTAssertEqual(TerminalInputMapping.bytes(forInsertedText: "a\nb"), Array("a\nb".utf8))
    }

    func testExtraRowSlashOpensThePanelInsteadOfWritingBytes() {
        XCTAssertEqual(TerminalInputMapping.extraRow(.slash, ctrlActive: false), .openSlash)
        XCTAssertNil(ANSISequence.bytes(for: .slash))
    }

    func testExtraRowReturnIsSubmitCR() {
        XCTAssertEqual(TerminalInputMapping.extraRow(.return, ctrlActive: false), .write([0x0d]))
    }
}

@MainActor
final class TerminalInputViewTests: XCTestCase {

    func testSystemReturnUsesTheShippedSubmitMapping() {
        let sink = TerminalInputView()
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        sink.insertText("\n")
        XCTAssertEqual(got, [TerminalInputMapping.submitBytes])
        XCTAssertEqual(got.first, [0x0d])
    }

    func testInsertNewlineWithoutSubmitIsNotSubmit() {
        let sink = TerminalInputView()
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        sink.insertNewlineWithoutSubmit()
        XCTAssertEqual(got, [TerminalInputMapping.newlineBytes])
        XCTAssertNotEqual(got.first, TerminalInputMapping.submitBytes)
    }

    func testSubmitLineWritesCR() {
        let sink = TerminalInputView()
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        sink.submitLine()
        XCTAssertEqual(got, [[0x0d]])
    }

    func testTypedTextIsUTF8() {
        let sink = TerminalInputView()
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        sink.insertText("ab")
        XCTAssertEqual(got, [Array("ab".utf8)])
    }

    func testArmedCtrlSurvivesAMultilinePaste() {
        let sink = TerminalInputView()
        sink.extraRow.ctrl.toggle()
        XCTAssertTrue(sink.extraRow.ctrl.isActive)
        sink.insertText("ab\ncd")
        XCTAssertTrue(sink.extraRow.ctrl.isActive, "paste must not consume armed Ctrl")
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        sink.insertText("c")
        XCTAssertEqual(got, [[0x03]])
        XCTAssertFalse(sink.extraRow.ctrl.isActive)
    }

    func testArmedCtrlIsConsumedOnSystemReturn() {
        let sink = TerminalInputView()
        sink.extraRow.ctrl.toggle()
        sink.insertText("\n")
        XCTAssertFalse(sink.extraRow.ctrl.isActive, "submit must consume armed Ctrl")
    }

    func testExtraRowSlashOpensPanelAndCompactWritesTrigger() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let sink = TerminalInputView(frame: window.bounds)
        var got: [[UInt8]] = []
        sink.onRawInput = { got.append($0) }
        window.addSubview(sink)
        window.makeKeyAndVisible()
        sink.layoutIfNeeded()

        let extra = sink.extraRow
        extra.frame = CGRect(x: 0, y: 640, width: 390, height: 52)
        window.addSubview(extra)
        extra.layoutIfNeeded()

        tapExtra(extra, accessibility: "Slash commands")

        guard let panel = window.subviews.reversed().first(where: {
            $0.accessibilityIdentifier == "slash-command-panel"
        }) as? SlashCommandPanel else {
            return XCTFail("slash panel did not appear over the terminal")
        }

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
        XCTAssertEqual(got, [Array("/compact".utf8)])
        window.isHidden = true
    }

    func testClipOpensClipboardPanel() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let sink = TerminalInputView(frame: window.bounds)
        window.addSubview(sink)
        window.makeKeyAndVisible()

        let extra = sink.extraRow
        extra.frame = CGRect(x: 0, y: 640, width: 390, height: 52)
        window.addSubview(extra)
        extra.layoutIfNeeded()

        tapExtra(extra, accessibility: "Clipboard history")
        XCTAssertTrue(
            window.subviews.contains { $0.accessibilityIdentifier == "clipboard-panel" },
            "Clip must present ClipboardPanel, not one-shot paste"
        )
        window.isHidden = true
    }

    func testSCPOpensUploadPanel() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let sink = TerminalInputView(frame: window.bounds)
        sink.uploadHosts = { [] }
        window.addSubview(sink)
        window.makeKeyAndVisible()

        let extra = sink.extraRow
        extra.frame = CGRect(x: 0, y: 640, width: 390, height: 52)
        window.addSubview(extra)
        extra.layoutIfNeeded()

        tapExtra(extra, accessibility: "Upload image over SFTP")
        XCTAssertTrue(
            window.subviews.contains { $0.accessibilityIdentifier == "upload-panel" },
            "SCP must present UploadPanel, not a no-op"
        )
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
}
