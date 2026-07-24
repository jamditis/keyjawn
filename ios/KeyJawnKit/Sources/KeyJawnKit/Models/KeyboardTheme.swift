import UIKit

/// Colour roles for every keyboard surface.
///
/// Each case is a complete set: key faces, the extra row, and the overlay panels all
/// resolve their colours from here rather than hardcoding their own. Before this the
/// number row, the extra row's key labels, and all three panels carried literal dark
/// values, so picking Light or Terminal recoloured some of the keyboard and left the
/// rest looking like a rendering fault.
///
/// The text roles are chosen to clear WCAG AA (4.5:1) against the backgrounds they sit
/// on, which `KeyboardThemeContrastTests` enforces for every case.
public enum KeyboardTheme: String, CaseIterable, Sendable {
    case dark     = "dark"
    case light    = "light"
    case oled     = "oled"
    case terminal = "terminal"

    public var displayName: String {
        switch self {
        case .dark:     return "Dark"
        case .light:    return "Light"
        case .oled:     return "OLED black"
        case .terminal: return "Terminal"
        }
    }

    /// True for every theme whose surfaces are dark. Drives the keyboard appearance
    /// reported to the host app and the chrome inside the overlay panels.
    public var isDark: Bool { self != .light }

    // MARK: - Keyboard surfaces

    public var keyboardBg: UIColor {
        switch self {
        case .dark:     return UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)
        case .light:    return UIColor(red: 0.81, green: 0.82, blue: 0.84, alpha: 1)
        case .oled:     return .black
        case .terminal: return UIColor(red: 0.04, green: 0.08, blue: 0.04, alpha: 1)
        }
    }

    public var keyBg: UIColor {
        switch self {
        case .dark:     return UIColor(white: 0.27, alpha: 1)
        case .light:    return .white
        case .oled:     return UIColor(white: 0.15, alpha: 1)
        case .terminal: return UIColor(red: 0.07, green: 0.13, blue: 0.07, alpha: 1)
        }
    }

    public var specKeyBg: UIColor {
        switch self {
        case .dark:     return UIColor(white: 0.17, alpha: 1)
        case .light:    return UIColor(red: 0.71, green: 0.72, blue: 0.74, alpha: 1)
        case .oled:     return UIColor(white: 0.08, alpha: 1)
        // Lifted off keyboardBg on purpose: when the two matched exactly, shift,
        // backspace, 123 and return read as holes in the keyboard rather than as keys.
        case .terminal: return UIColor(red: 0.055, green: 0.105, blue: 0.055, alpha: 1)
        }
    }

    public var keyText: UIColor {
        switch self {
        case .dark:     return .white
        case .light:    return .black
        case .oled:     return .white
        case .terminal: return UIColor(red: 0.2, green: 0.9, blue: 0.2, alpha: 1)
        }
    }

    public var armed: UIColor {
        switch self {
        case .dark, .light, .oled:
            return UIColor(red: 0.267, green: 0.467, blue: 0.800, alpha: 1)
        case .terminal:
            return UIColor(red: 0.1, green: 0.7, blue: 0.3, alpha: 1)
        }
    }

    public var locked: UIColor {
        switch self {
        case .dark, .light, .oled:
            return UIColor(red: 0.800, green: 0.267, blue: 0.267, alpha: 1)
        case .terminal:
            return UIColor(red: 0.8, green: 0.3, blue: 0.1, alpha: 1)
        }
    }

    // MARK: - Extra row

    public var extraRowBg: UIColor {
        switch self {
        case .dark:     return UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)
        case .light:    return UIColor(red: 0.75, green: 0.76, blue: 0.78, alpha: 1)
        case .oled:     return .black
        case .terminal: return UIColor(red: 0.03, green: 0.06, blue: 0.03, alpha: 1)
        }
    }

    public var extraRowKeyBg: UIColor {
        switch self {
        case .dark:     return UIColor(red: 0.227, green: 0.227, blue: 0.227, alpha: 1)
        case .light:    return UIColor(red: 0.55, green: 0.56, blue: 0.58, alpha: 1)
        case .oled:     return UIColor(white: 0.18, alpha: 1)
        case .terminal: return UIColor(red: 0.08, green: 0.15, blue: 0.08, alpha: 1)
        }
    }

    /// Label colour for extra row keys. Shares `keyText` so the terminal green and the
    /// light theme's black carry across; the extra row used to hardcode white, which
    /// on Light gave 3.3:1 against its own key background.
    public var extraRowKeyText: UIColor { keyText }

    // MARK: - Overlay panels

    /// Backing colour for the slash command, clipboard and upload panels. Slightly
    /// off the keyboard background and near-opaque, so a panel reads as sitting above
    /// the keys rather than replacing them.
    public var panelBg: UIColor {
        switch self {
        case .dark:     return UIColor(red: 0.11, green: 0.11, blue: 0.11, alpha: 0.97)
        case .light:    return UIColor(red: 0.96, green: 0.96, blue: 0.97, alpha: 0.97)
        case .oled:     return UIColor(white: 0.04, alpha: 0.98)
        case .terminal: return UIColor(red: 0.02, green: 0.05, blue: 0.02, alpha: 0.97)
        }
    }

    public var panelText: UIColor {
        switch self {
        case .light: return .black
        default:     return .white
        }
    }

    /// Descriptions, empty states and section headers.
    public var panelSecondaryText: UIColor {
        switch self {
        case .light: return UIColor(white: 0.32, alpha: 1)
        default:     return UIColor(white: 0.78, alpha: 1)
        }
    }

    public var panelSeparator: UIColor {
        switch self {
        case .light: return UIColor(white: 0.0, alpha: 0.15)
        default:     return UIColor(white: 1.0, alpha: 0.15)
        }
    }

    /// Row highlight behind a selected panel cell.
    public var panelSelection: UIColor {
        switch self {
        case .light: return UIColor(white: 0.0, alpha: 0.08)
        default:     return UIColor(white: 1.0, alpha: 0.12)
        }
    }

    /// Tint for panel actions (Done, Cancel) and for slash command triggers.
    public var accent: UIColor {
        switch self {
        case .dark, .oled: return UIColor(red: 0.40, green: 0.75, blue: 1.00, alpha: 1)
        case .light:       return UIColor(red: 0.00, green: 0.36, blue: 0.80, alpha: 1)
        case .terminal:    return UIColor(red: 0.30, green: 0.95, blue: 0.40, alpha: 1)
        }
    }
}
