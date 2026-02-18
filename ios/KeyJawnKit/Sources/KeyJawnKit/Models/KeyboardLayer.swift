import Foundation

// MARK: - Key types

public enum QwertyKey: Sendable, Equatable {
    case character(String)
    case space
    case `return`
    case backspace
    case shift
    case symbolsToggle      // 123
    case alphabeticToggle   // ABC (from symbols layer)
    case globe
    case more               // #+= (extended symbols — cycles back for now)
}

public enum ShiftState: Sendable {
    case off    // lowercase
    case once   // next char uppercase, then auto-revert
    case caps   // all caps until tapped again
}

// MARK: - Layer type

public enum KeyboardLayerType: Sendable {
    case lowercase
    case uppercase
    case symbols
}

// MARK: - Layout data

public enum KeyboardLayers {

    /// Returns the four key rows for the given layer/shift combination.
    public static func rows(for layer: KeyboardLayerType,
                            shiftState: ShiftState = .off) -> [[QwertyKey]] {
        switch layer {

        case .lowercase:
            return [
                chars("qwertyuiop"),
                chars("asdfghjkl"),
                [.shift] + chars("zxcvbnm") + [.backspace],
                [.symbolsToggle, .globe, .space, .return],
            ]

        case .uppercase:
            return [
                chars("QWERTYUIOP"),
                chars("ASDFGHJKL"),
                [.shift] + chars("ZXCVBNM") + [.backspace],
                [.symbolsToggle, .globe, .space, .return],
            ]

        case .symbols:
            return [
                chars("1234567890"),
                chars("-/:;()$&@\""),
                [.more] + chars(".,?!'") + [.backspace],
                [.alphabeticToggle, .globe, .space, .return],
            ]
        }
    }

    private static func chars(_ s: String) -> [QwertyKey] {
        s.map { .character(String($0)) }
    }
}
