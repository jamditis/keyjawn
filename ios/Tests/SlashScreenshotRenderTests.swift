import UIKit
import XCTest
@testable import KeyJawnKit

/// Renders the real extra row, number row, QWERTY, and slash panel onto a
/// 1290×2796 image with a generic prompt behind it. No third-party tool chrome.
///
/// Set `SLASH_SCREENSHOT_PATH` to write the PNG. The test always renders and
/// checks the pixel size so a missing write path still exercises the layout.
@MainActor
final class SlashScreenshotRenderTests: XCTestCase {

    func testRendersUnbrandedStoreSizedSlashScreenshot() throws {
        let image = try Self.render()
        XCTAssertEqual(image.size.width * image.scale, 1290, accuracy: 0.5)
        XCTAssertEqual(image.size.height * image.scale, 2796, accuracy: 0.5)

        let data = try XCTUnwrap(image.pngData())
        XCTAssertGreaterThan(data.count, 50_000)

        // Opt-in only. Ordinary local `xcodebuild test` must not rewrite the
        // tracked listing PNG.
        if let dest = ProcessInfo.processInfo.environment["SLASH_SCREENSHOT_PATH"],
           !dest.isEmpty {
            try data.write(to: URL(fileURLWithPath: dest))
        }
    }

    @MainActor
    static func render() throws -> UIImage {
        let pointSize = CGSize(width: 430, height: 932)
        let theme = KeyboardTheme.dark

        let window = UIWindow(frame: CGRect(origin: .zero, size: pointSize))
        let root = UIView(frame: CGRect(origin: .zero, size: pointSize))
        root.backgroundColor = UIColor(red: 0.07, green: 0.07, blue: 0.08, alpha: 1)
        window.addSubview(root)
        window.makeKeyAndVisible()

        let prompt = UILabel()
        prompt.numberOfLines = 0
        prompt.font = .monospacedSystemFont(ofSize: 15, weight: .regular)
        prompt.textColor = UIColor(white: 0.78, alpha: 1)
        prompt.text = """
        user@host:~$ ls
        docs  src  README.md
        user@host:~$
        """
        prompt.frame = CGRect(x: 16, y: 64, width: pointSize.width - 32, height: 200)
        root.addSubview(prompt)

        let extra = ExtraRowView()
        extra.applyTheme(theme)
        extra.frame = CGRect(x: 0, y: pointSize.height - 52 - 42 - 4 - 220, width: pointSize.width, height: 52)
        root.addSubview(extra)

        let numbers = NumberRowView()
        numbers.applyTheme(theme)
        numbers.frame = CGRect(x: 0, y: extra.frame.maxY + 4, width: pointSize.width, height: 42)
        root.addSubview(numbers)

        let qwerty = QwertyKeyboardView()
        qwerty.applyTheme(theme)
        qwerty.frame = CGRect(x: 0, y: numbers.frame.maxY + 4, width: pointSize.width, height: 220)
        root.addSubview(qwerty)

        let panel = SlashCommandPanel(theme: theme)
        // Cover the QWERTY so the panel — the 3.2.2 artifact — is the subject.
        panel.frame = CGRect(x: 8, y: extra.frame.minY - 280, width: pointSize.width - 16, height: 360)
        panel.layer.cornerRadius = 10
        panel.clipsToBounds = true
        root.addSubview(panel)

        root.layoutIfNeeded()
        extra.layoutIfNeeded()
        numbers.layoutIfNeeded()
        qwerty.layoutIfNeeded()
        panel.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: pointSize, format: format)
        return renderer.image { _ in
            root.drawHierarchy(in: root.bounds, afterScreenUpdates: true)
        }
    }
}
