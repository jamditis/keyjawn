import Foundation

/// Byte mapping the SSH sink uses for system Return, Send, and a non-submit newline.
///
/// `TerminalInputView.insertText` and the extra-row Send key both call through
/// here so a test of this type is a test of the shipped submit path, not a copy
/// of it. A lone Return is CR (`0x0d`) because that is what a PTY treats as
/// submit. A dedicated newline helper writes LF (`0x0a`) so a multiline prompt
/// can gain a line without submitting.
public enum TerminalInputMapping: Sendable {
    public static let submitBytes: [UInt8] = [0x0d]
    public static let newlineBytes: [UInt8] = [0x0a]

    /// What the sink writes for a `UITextView.insertText` payload.
    ///
    /// Only a lone `"\n"` or `"\r"` is a submit. A multiline paste keeps its
    /// UTF-8 bytes so a copied block is not collapsed into one CR.
    public static func bytes(forInsertedText text: String) -> [UInt8] {
        if text == "\n" || text == "\r" {
            return submitBytes
        }
        return Array(text.utf8)
    }

    public enum ExtraRowAction: Equatable, Sendable {
        case write([UInt8])
        case openSlash
        case none
    }

    /// Extra-row key → terminal action. Slash opens the panel; everything else
    /// that has ANSI bytes is written. Clipboard and upload stay on the
    /// delegate methods because they are not `KeyOutput`s.
    public static func extraRow(_ output: KeyOutput, ctrlActive: Bool) -> ExtraRowAction {
        if output == .slash { return .openSlash }
        if let bytes = ANSISequence.bytes(for: output, ctrlActive: ctrlActive) {
            return .write(bytes)
        }
        return .none
    }
}
