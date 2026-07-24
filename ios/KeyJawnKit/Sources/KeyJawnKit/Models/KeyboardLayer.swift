import Foundation

// MARK: - Key types

public enum QwertyKey: Sendable, Equatable {
    case character(String)
    case space
    case `return`
    case backspace
    case shift
    case symbolsToggle      // 123
    case alphabeticToggle   // ABC (from a symbols layer)
    case globe
    case more               // #+= (second symbols page)
}

public enum ShiftState: Sendable {
    case off    // lowercase
    case once   // next char uppercase, then auto-revert
    case caps   // all caps until tapped again
}

// MARK: - Layer type

public enum KeyboardLayerType: Sendable, CaseIterable {
    case lowercase
    case uppercase
    case symbols
    case symbols2
}

// MARK: - Layout data

public enum KeyboardLayers {

    /// Every symbol a shell prompt needs that is not on the letter layers.
    ///
    /// The first symbols page carries what prose needs; this set is what a command
    /// line needs, and until the `#+=` key was wired up none of it could be typed at
    /// all — no pipe, no underscore, no tilde, no brackets or braces. Asserted as
    /// reachable by `KeyboardLayersTests` so a future layout edit cannot quietly drop
    /// one again.
    public static let shellSymbols: Set<String> = [
        "[", "]", "{", "}", "#", "%", "^", "*", "+", "=",
        "_", "\\", "|", "~", "<", ">", "`",
    ]

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

        // Row 0 matches the system keyboard's own #+= page, so the muscle memory
        // carries over. Row 1 drops the currency symbols the system puts there for
        // the characters this keyboard's users actually reach for, backtick included
        // — the system keyboard has no backtick anywhere, which makes fenced code and
        // command substitution untypeable on a stock iPhone.
        case .symbols2:
            return [
                chars("[]{}#%^*+="),
                chars("_\\|~<>`\"'"),
                [.symbolsToggle] + chars(".,?!;") + [.backspace],
                [.alphabeticToggle, .globe, .space, .return],
            ]
        }
    }

    private static func chars(_ s: String) -> [QwertyKey] {
        s.map { .character(String($0)) }
    }
}
