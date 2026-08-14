import UIKit
import XCTest
@testable import KeyJawnKit

/// Load and churn the types the keyboard actually uses. These are not mocks:
/// they call the same Ctrl machine, layouts, ANSI encoder, prefs, clipboard,
/// and UIKit views the app ships.
@MainActor
final class KeyboardStressTests: XCTestCase {

    func testCtrlToggleConsumeStormStaysInTheDocumentedMachine() {
        let ctrl = CtrlState()
        for i in 0..<5_000 {
            switch i % 5 {
            case 0, 1, 2:
                ctrl.toggle()
            default:
                ctrl.consume()
            }
        }
        // 5000 steps: toggle, toggle, toggle, consume, consume — period 5.
        // After a full period from off: armed, locked, off, consume-noop, consume-noop.
        XCTAssertEqual(ctrl.state, .off)
        XCTAssertFalse(ctrl.isActive)
    }

    func testArmedConsumesExactlyOnceUnderRepeatedKeys() {
        let ctrl = CtrlState()
        ctrl.toggle()
        XCTAssertEqual(ctrl.state, .armed)
        for _ in 0..<200 {
            if ctrl.isActive { ctrl.consume() }
        }
        XCTAssertEqual(ctrl.state, .off)
    }

    func testLockedSurvivesAThousandConsumes() {
        let ctrl = CtrlState()
        ctrl.toggle()
        ctrl.toggle()
        for _ in 0..<1_000 { ctrl.consume() }
        XCTAssertEqual(ctrl.state, .locked)
        XCTAssertTrue(ctrl.isActive)
    }

    func testEveryPrintableASCIIHasAStableCtrlMask() throws {
        for scalar in 32...126 {
            let ch = String(UnicodeScalar(scalar)!)
            let plain = try XCTUnwrap(ANSISequence.bytes(for: .character(ch)))
            let masked = try XCTUnwrap(ANSISequence.bytes(for: .character(ch), ctrlActive: true))
            XCTAssertEqual(plain.count, 1)
            XCTAssertEqual(masked, [UInt8(scalar) & 0x1f], "ctrl+\(ch)")
            XCTAssertEqual(
                ANSISequence.text(for: .character(ch), ctrlActive: true),
                String(decoding: masked, as: UTF8.self)
            )
        }
    }

    func testLayerRowShapesAreStableAcrossRepeatedLookups() {
        let expected = KeyboardLayers.rows(for: .lowercase, shiftState: .off).map(\.count)
        for _ in 0..<1_000 {
            for layer in [KeyboardLayerType.lowercase, .uppercase, .symbols, .symbols2] {
                let rows = KeyboardLayers.rows(for: layer, shiftState: .off)
                XCTAssertEqual(rows.count, 4, "\(layer)")
            }
            XCTAssertEqual(
                KeyboardLayers.rows(for: .lowercase, shiftState: .off).map(\.count),
                expected
            )
        }
    }

    func testSlashGroupingIsIdempotentAndTriggerUnique() {
        var first: [(SlashCommand.Category, [String])] = []
        for i in 0..<200 {
            let grouped = SlashCommand.grouped
            let snapshot = grouped.map { ($0.category, $0.commands.map(\.trigger)) }
            if i == 0 { first = snapshot }
            XCTAssertEqual(snapshot.map(\.0), first.map(\.0))
            XCTAssertEqual(snapshot.map(\.1), first.map(\.1))
            let triggers = grouped.flatMap { $0.commands.map(\.trigger) }
            XCTAssertEqual(triggers.count, Set(triggers).count)
        }
    }

    func testHostConfigArrayRoundTripsFiveHundredHosts() throws {
        let hosts = (0..<500).map { i in
            HostConfig(
                label: "host-\(i)",
                hostname: "h\(i).example",
                port: i % 2 == 0 ? 22 : 2222,
                username: "user\(i)",
                authMethod: i % 2 == 0 ? .key : .password,
                hostPublicKey: i % 3 == 0 ? nil : "ssh-ed25519 AAAA\(i)",
                uploadPath: "/tmp/u\(i)"
            )
        }
        let data = try JSONEncoder().encode(hosts)
        let decoded = try JSONDecoder().decode([HostConfig].self, from: data)
        XCTAssertEqual(decoded, hosts)
        XCTAssertEqual(Set(decoded.map(\.id)).count, 500)
    }

    func testPrefsSurviveAWriteStormOnAnInjectedSuite() {
        let name = "com.keyjawn.tests.stress.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }

        let prefs = KeyboardPrefs(defaults: suite)
        for i in 0..<1_000 {
            prefs.theme = KeyboardTheme.allCases[i % KeyboardTheme.allCases.count]
            prefs.hapticsEnabled = i % 2 == 0
            prefs.terminalArrowKeys = i % 3 == 0
            prefs.hasCompletedOnboarding = i % 4 == 0
        }
        let last = 999
        let reread = KeyboardPrefs(defaults: suite)
        XCTAssertEqual(reread.theme, KeyboardTheme.allCases[last % KeyboardTheme.allCases.count])
        XCTAssertEqual(reread.hapticsEnabled, last % 2 == 0)
        XCTAssertEqual(reread.terminalArrowKeys, last % 3 == 0)
        XCTAssertEqual(reread.hasCompletedOnboarding, last % 4 == 0)
    }

    func testClipboardHistoryCapsAtThirtyAndDropsOldest() {
        let name = "com.keyjawn.tests.clipboard.\(UUID().uuidString)"
        let suite = UserDefaults(suiteName: name)!
        defer { UserDefaults.standard.removePersistentDomain(forName: name) }
        let history = ClipboardHistory(defaults: suite)

        for i in 0..<80 {
            history.add("item-\(i)")
        }
        XCTAssertEqual(history.items.count, 30)
        XCTAssertEqual(history.items.first, "item-79")
        XCTAssertEqual(history.items.last, "item-50")
        XCTAssertFalse(history.items.contains("item-0"))
    }

    func testKeyboardViewsSurviveRepeatedThemeRebuilds() {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 400))
        let extra = ExtraRowView()
        extra.frame = CGRect(x: 0, y: 0, width: 390, height: 52)
        let numbers = NumberRowView()
        numbers.frame = CGRect(x: 0, y: 56, width: 390, height: 42)
        let qwerty = QwertyKeyboardView()
        qwerty.frame = CGRect(x: 0, y: 102, width: 390, height: 220)
        let root = UIView(frame: window.bounds)
        root.addSubview(extra)
        root.addSubview(numbers)
        root.addSubview(qwerty)
        window.addSubview(root)
        window.makeKeyAndVisible()

        for _ in 0..<80 {
            for theme in KeyboardTheme.allCases {
                extra.applyTheme(theme)
                numbers.applyTheme(theme)
                qwerty.applyTheme(theme)
            }
            extra.layoutIfNeeded()
            numbers.layoutIfNeeded()
            qwerty.layoutIfNeeded()
        }

        XCTAssertFalse(extra.subviews.isEmpty)
        XCTAssertFalse(qwerty.subviews.isEmpty)
        window.isHidden = true
    }

    func testSlashPanelAndExtraRowCanBeCreatedAndTornDownRepeatedly() {
        for _ in 0..<40 {
            autoreleasepool {
                let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 500))
                let extra = ExtraRowView()
                extra.frame = CGRect(x: 0, y: 0, width: 390, height: 52)
                extra.applyTheme(.dark)
                let panel = SlashCommandPanel(theme: .oled)
                panel.frame = CGRect(x: 0, y: 60, width: 390, height: 320)
                window.addSubview(extra)
                window.addSubview(panel)
                window.makeKeyAndVisible()
                extra.layoutIfNeeded()
                panel.layoutIfNeeded()
                XCTAssertGreaterThan(panel.subviews.count, 0)
                window.isHidden = true
            }
        }
    }
}
