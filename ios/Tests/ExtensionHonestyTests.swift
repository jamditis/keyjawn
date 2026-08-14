import XCTest
@testable import KeyJawnKit

/// Structural checks that the keyboard extension does not grow a dead mic.
final class ExtensionHonestyTests: XCTestCase {

    func testExtensionSourcesDoNotImportSpeechOrRequestTheMic() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let extensionDir = root.appendingPathComponent("KeyJawnKeyboard")
        let files = try FileManager.default.contentsOfDirectory(
            at: extensionDir,
            includingPropertiesForKeys: nil
        ).filter { $0.pathExtension == "swift" }
        XCTAssertFalse(files.isEmpty, "missing keyboard extension sources")
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            XCTAssertFalse(text.contains("import Speech"), "\(file.lastPathComponent) imports Speech")
            XCTAssertFalse(text.contains("SFSpeechRecognizer"), "\(file.lastPathComponent) uses SFSpeechRecognizer")
            XCTAssertFalse(text.contains("AVAudioEngine"), "\(file.lastPathComponent) opens the audio engine")
            XCTAssertFalse(text.contains("requestRecordPermission"), "\(file.lastPathComponent) requests the mic")
        }
    }

    func testExtensionDefaultRowHasNoMic() {
        XCTAssertFalse(
            ExtraRowKey.defaults.contains { $0.slot == .mic },
            "the IME extra row must not show a dead mic"
        )
        XCTAssertTrue(
            ExtraRowKey.terminalKeys.contains { $0.slot == .mic },
            "the SSH extra row is where the mic lives"
        )
    }
}
