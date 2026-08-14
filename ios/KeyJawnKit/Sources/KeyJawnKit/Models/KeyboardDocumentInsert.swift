import Foundation

/// What the keyboard extension does with a key, independent of `UITextDocumentProxy`.
///
/// `KeyboardViewController.applyInsert` is the production caller for extra-row
/// and QWERTY keys. Tests drive this same function so a change to Esc, Tab,
/// slash, or backspace cannot silently go inert again.
public enum KeyboardDocumentInsert: Equatable, Sendable {
    case insert(String)
    case deleteBackward
    case adjustPosition(Int)
    case openSlash

    public static func action(
        for output: KeyOutput,
        ctrlActive: Bool,
        terminalArrows: Bool
    ) -> KeyboardDocumentInsert? {
        switch output {
        case .slash:
            return .openSlash
        case .tab, .escape, .ctrlC, .ctrlD:
            return ANSISequence.text(for: output, ctrlActive: ctrlActive).map { .insert($0) }
        case .arrowUp, .arrowDown:
            guard terminalArrows else { return nil }
            return ANSISequence.text(for: output, ctrlActive: ctrlActive).map { .insert($0) }
        case .arrowLeft:
            if terminalArrows, let text = ANSISequence.text(for: output, ctrlActive: ctrlActive) {
                return .insert(text)
            }
            return .adjustPosition(-1)
        case .arrowRight:
            if terminalArrows, let text = ANSISequence.text(for: output, ctrlActive: ctrlActive) {
                return .insert(text)
            }
            return .adjustPosition(1)
        case .character(let s):
            if ctrlActive, s.count == 1,
               let control = ANSISequence.text(for: .character(s), ctrlActive: true) {
                return .insert(control)
            }
            return .insert(s)
        case .backspace:
            return .deleteBackward
        case .return:
            return .insert("\n")
        case .space:
            return .insert(" ")
        }
    }
}
