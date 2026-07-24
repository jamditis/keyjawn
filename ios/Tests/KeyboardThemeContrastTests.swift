import XCTest
import UIKit
@testable import KeyJawnKit

/// Legibility as a test rather than as a thing someone notices on a device.
///
/// This is the check that would have caught the extra row's hardcoded white labels:
/// they survived every theme change, and on Light they sat on a mid-grey key at 3.3:1
/// — readable enough in a screenshot, tiring on a phone in daylight.
///
/// Ratios are WCAG 2.1 relative luminance. The panel backgrounds are near-opaque and
/// compared as their own colour rather than composited, which is the conservative
/// reading: what shows through is the keyboard behind them, in the same theme.
final class KeyboardThemeContrastTests: XCTestCase {

    /// WCAG AA for normal text. Key glyphs are small and used one-handed in bad light,
    /// so this is a floor rather than a target.
    private let minimumRatio = 4.5

    func testKeyLabelsAreLegibleOnKeyFaces() {
        for theme in KeyboardTheme.allCases {
            assertContrast(theme.keyText, on: theme.keyBg, theme: theme, label: "keyText on keyBg")
        }
    }

    func testKeyLabelsAreLegibleOnModifierKeys() {
        for theme in KeyboardTheme.allCases {
            assertContrast(theme.keyText, on: theme.specKeyBg, theme: theme, label: "keyText on specKeyBg")
        }
    }

    func testExtraRowLabelsAreLegible() {
        for theme in KeyboardTheme.allCases {
            assertContrast(theme.extraRowKeyText, on: theme.extraRowKeyBg,
                           theme: theme, label: "extraRowKeyText on extraRowKeyBg")
        }
    }

    func testPanelTextIsLegible() {
        for theme in KeyboardTheme.allCases {
            assertContrast(theme.panelText, on: theme.panelBg, theme: theme, label: "panelText")
            assertContrast(theme.panelSecondaryText, on: theme.panelBg, theme: theme, label: "panelSecondaryText")
            assertContrast(theme.accent, on: theme.panelBg, theme: theme, label: "accent")
        }
    }

    /// A modifier key whose face matches the keyboard behind it reads as a hole rather
    /// than as a key. The Terminal theme used to have exactly that.
    func testModifierKeysAreDistinguishableFromTheKeyboardBackground() {
        for theme in KeyboardTheme.allCases {
            XCTAssertNotEqual(components(theme.specKeyBg), components(theme.keyboardBg),
                              "\(theme.rawValue): special keys are invisible against the keyboard")
            XCTAssertNotEqual(components(theme.keyBg), components(theme.keyboardBg),
                              "\(theme.rawValue): keys are invisible against the keyboard")
        }
    }

    /// The armed and locked Ctrl states have to be told apart — one means the next key
    /// carries Ctrl, the other means every key does until it is turned off.
    ///
    /// Asserted as a colour difference, not a luminance ratio: the two are separated by
    /// hue (blue against red), which sits at roughly 1.06:1 in luminance terms. That is
    /// legible to most people and is the palette the Android build already uses, but it
    /// does mean the distinction is carried by hue alone.
    func testArmedAndLockedAreDistinct() {
        for theme in KeyboardTheme.allCases {
            XCTAssertNotEqual(components(theme.armed), components(theme.locked),
                              "\(theme.rawValue): armed and locked are the same colour")
            XCTAssertNotEqual(components(theme.armed), components(theme.extraRowKeyBg),
                              "\(theme.rawValue): an armed Ctrl looks the same as an idle key")
        }
    }

    func testIsDarkMatchesTheActualBackground() {
        for theme in KeyboardTheme.allCases {
            let bright = relativeLuminance(theme.keyboardBg) > 0.5
            XCTAssertEqual(theme.isDark, !bright, "\(theme.rawValue) misreports its own brightness")
        }
    }

    func testEveryThemeIsNamed() {
        for theme in KeyboardTheme.allCases {
            XCTAssertFalse(theme.displayName.isEmpty)
            XCTAssertFalse(theme.rawValue.isEmpty)
        }
        XCTAssertEqual(Set(KeyboardTheme.allCases.map(\.displayName)).count,
                       KeyboardTheme.allCases.count,
                       "two themes share a name in the picker")
    }

    // MARK: - Helpers

    private func assertContrast(_ foreground: UIColor,
                                on background: UIColor,
                                theme: KeyboardTheme,
                                label: String,
                                file: StaticString = #filePath,
                                line: UInt = #line) {
        let ratio = contrastRatio(foreground, background)
        XCTAssertGreaterThanOrEqual(
            ratio, minimumRatio,
            "\(theme.rawValue): \(label) is \(rounded(ratio)):1, below the \(minimumRatio):1 floor",
            file: file, line: line
        )
    }

    private func contrastRatio(_ a: UIColor, _ b: UIColor) -> Double {
        let la = relativeLuminance(a)
        let lb = relativeLuminance(b)
        let lighter = max(la, lb)
        let darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private func relativeLuminance(_ color: UIColor) -> Double {
        let rgb = components(color)
        func linear(_ channel: Double) -> Double {
            channel <= 0.03928 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue)
    }

    private struct RGB: Equatable {
        let red: Double
        let green: Double
        let blue: Double
    }

    private func components(_ color: UIColor) -> RGB {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        // Handles the grayscale colour space too, which UIColor(white:alpha:) and the
        // .white / .black constants use.
        guard color.getRed(&r, green: &g, blue: &b, alpha: &a) else {
            XCTFail("could not read components from \(color)")
            return RGB(red: 0, green: 0, blue: 0)
        }
        return RGB(red: Double(r), green: Double(g), blue: Double(b))
    }

    private func rounded(_ value: Double) -> String {
        String(format: "%.2f", value)
    }
}
