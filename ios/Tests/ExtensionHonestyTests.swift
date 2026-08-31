import XCTest

@testable import KeyJawnKit

/// Structural checks that the keyboard extension does not grow a dead mic.
final class ExtensionHonestyTests: XCTestCase {

    func testExtensionSourcesDoNotImportSpeechOrRequestTheMic() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let projectYAML = try String(
            contentsOf: root.appendingPathComponent("project.yml"),
            encoding: .utf8
        )
        let keyboardTarget = try XCTUnwrap(
            projectYAML.components(separatedBy: "  KeyJawnKeyboard:\n").last?
                .components(separatedBy: "\n  KeyJawnKitTests:\n").first
        )
        let declaredSources = try XCTUnwrap(
            keyboardTarget.components(separatedBy: "    sources:\n").last?
                .components(separatedBy: "    dependencies:\n").first
        ).trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertEqual(
            declaredSources,
            "- path: KeyJawnKeyboard\n      - path: Shared",
            "update the extension safety scan when target sources change"
        )
        let declaredDependencies = try XCTUnwrap(
            keyboardTarget.components(separatedBy: "    dependencies:\n").last?
                .components(separatedBy: "    settings:\n").first
        ).trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertEqual(
            declaredDependencies,
            """
            - package: KeyJawnKit
                    product: KeyJawnKit
                  - package: Citadel
                  - package: SwiftNIO
                    product: NIOCore
                  - package: SwiftNIOSSH
                    product: NIOSSH
                  - package: SwiftNIOTransportServices
                    product: NIOTransportServices
            """,
            "update the extension safety scan when target dependencies change"
        )

        let sourceDirectories = [
            "KeyJawnKeyboard",
            "Shared",
            "KeyJawnKit/Sources/KeyJawnKit",
        ]
        var files: [URL] = []
        for directoryName in sourceDirectories {
            let directory = root.appendingPathComponent(directoryName)
            guard
                let enumerator = FileManager.default.enumerator(
                    at: directory,
                    includingPropertiesForKeys: nil,
                    options: [.skipsHiddenFiles]
                )
            else {
                XCTFail("cannot enumerate \(directoryName) sources")
                continue
            }
            for case let file as URL in enumerator where file.pathExtension == "swift" {
                files.append(file)
            }
        }

        let relativePaths = Set(
            files.map { file in
                String(file.path.dropFirst(root.path.count + 1))
            })
        XCTAssertFalse(files.isEmpty, "missing keyboard extension sources")
        XCTAssertTrue(
            relativePaths.contains("KeyJawnKeyboard/KeyboardViewController.swift"),
            "missing keyboard controller from extension source scan"
        )
        XCTAssertTrue(
            relativePaths.contains("Shared/CitadelSCPUploader.swift"),
            "missing shared uploader from extension source scan"
        )
        XCTAssertTrue(
            relativePaths.contains("KeyJawnKit/Sources/KeyJawnKit/Views/ExtraRowView.swift"),
            "missing linked keyboard views from extension source scan"
        )
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            XCTAssertFalse(text.contains("import Speech"), "\(file.lastPathComponent) imports Speech")
            XCTAssertFalse(text.contains("SFSpeechRecognizer"), "\(file.lastPathComponent) uses SFSpeechRecognizer")
            XCTAssertFalse(text.contains("import AVFoundation"), "\(file.lastPathComponent) imports AVFoundation")
            XCTAssertFalse(text.contains("AVAudioEngine"), "\(file.lastPathComponent) opens the audio engine")
            XCTAssertFalse(text.contains("AVAudioSession"), "\(file.lastPathComponent) configures an audio session")
            XCTAssertFalse(text.contains("AVAudioRecorder"), "\(file.lastPathComponent) records audio")
            XCTAssertFalse(text.contains("AVCaptureDevice"), "\(file.lastPathComponent) accesses a capture device")
            XCTAssertFalse(text.contains("requestRecordPermission"), "\(file.lastPathComponent) requests the mic")
        }
    }

    func testExtensionPresetsHaveNoMic() throws {
        for preset in ExtraRowPreset.allCases {
            XCTAssertFalse(
                preset.keys.contains { $0.slot == .mic },
                "the IME \(preset.displayName) row must not show a dead mic"
            )
        }
        XCTAssertTrue(
            ExtraRowKey.terminalKeys.contains { $0.slot == .mic },
            "the SSH extra row is where the mic lives"
        )

        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let controller = root.appendingPathComponent(
            "KeyJawnKeyboard/KeyboardViewController.swift"
        )
        let source = try String(contentsOf: controller, encoding: .utf8)
        let setKeysCallCount = source.components(separatedBy: "extraRow.setKeys(").count - 1
        XCTAssertEqual(
            setKeysCallCount,
            1,
            "the extension must have one audited extra-row key source"
        )
        XCTAssertTrue(
            source.contains("extraRow.setKeys(KeyboardPrefs.shared.extraRowPreset.keys)"),
            "the extension must use mic-free preset keys"
        )
    }
}
