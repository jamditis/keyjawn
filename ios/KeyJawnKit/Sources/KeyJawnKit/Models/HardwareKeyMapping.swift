import Foundation

/// Hardware keys the SSH sink can turn into PTY bytes.
public enum HardwareKey: Sendable, Equatable {
    case escape
    case tab
    case arrowUp
    case arrowDown
    case arrowLeft
    case arrowRight
    case character(String)
}

/// Modifier bits the mapping understands. Command is tracked so Cmd+C is
/// never turned into `0x03`.
public struct HardwareModifiers: OptionSet, Sendable {
    public let rawValue: Int
    public init(rawValue: Int) { self.rawValue = rawValue }

    public static let control = HardwareModifiers(rawValue: 1 << 0)
    public static let command = HardwareModifiers(rawValue: 1 << 1)
    public static let alternate = HardwareModifiers(rawValue: 1 << 2)
    public static let shift = HardwareModifiers(rawValue: 1 << 3)
}

/// Press → byte table used by `TerminalInputView.pressesBegan` and
/// `UIKeyCommand`. Tests drive this type so Cmd+C cannot silently become
/// interrupt.
public enum HardwareKeyMapping {
    public static func bytes(for key: HardwareKey, modifiers: HardwareModifiers) -> [UInt8]? {
        // Cmd+C / Cmd+V / Cmd+W stay system. Never emit a control byte.
        if modifiers.contains(.command) { return nil }

        switch key {
        case .escape:
            return [0x1b]
        case .tab:
            return [0x09]
        case .arrowUp:
            return ANSISequence.bytes(for: .arrowUp, ctrlActive: modifiers.contains(.control))
        case .arrowDown:
            return ANSISequence.bytes(for: .arrowDown, ctrlActive: modifiers.contains(.control))
        case .arrowLeft:
            return ANSISequence.bytes(for: .arrowLeft, ctrlActive: modifiers.contains(.control))
        case .arrowRight:
            return ANSISequence.bytes(for: .arrowRight, ctrlActive: modifiers.contains(.control))
        case .character(let s):
            guard modifiers.contains(.control) else { return nil }
            return ANSISequence.bytes(for: .character(s), ctrlActive: true)
        }
    }
}
