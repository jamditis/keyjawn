import UIKit

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
        case .terminal: return UIColor(red: 0.04, green: 0.08, blue: 0.04, alpha: 1)
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
}
