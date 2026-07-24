import UIKit
import KeyJawnKit

/// Transparent UITextView used as a keyboard sink on top of SwiftTerm's
/// TerminalView. Intercepts insertText/deleteBackward to route raw bytes
/// rather than accumulating invisible text in the UITextView.
@MainActor
final class TerminalInputView: UITextView {

    let extraRow = ExtraRowView()

    /// Called for every raw byte sequence produced by keyboard or extra row.
    var onRawInput: (([UInt8]) -> Void)?

    override init(frame: CGRect, textContainer: NSTextContainer?) {
        super.init(frame: frame, textContainer: textContainer)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor          = .clear
        textColor                = .clear
        tintColor                = .clear
        autocorrectionType       = .no
        autocapitalizationType   = .none
        spellCheckingType        = .no
        // Smart substitution turns a typed "--flag" into an en dash and quotes into
        // curly ones, which reach the shell as characters it does not understand.
        smartQuotesType          = .no
        smartDashesType          = .no
        smartInsertDeleteType    = .no

        extraRow.frame     = CGRect(x: 0, y: 0, width: 0, height: 52)
        extraRow.delegate  = self
        // Same theme the keyboard extension uses, so the row looks like one component
        // wherever it appears rather than defaulting to dark here and themed there.
        extraRow.applyTheme(KeyboardPrefs.shared.theme)
        inputAccessoryView = extraRow
    }

    // MARK: UIKeyInput — intercept before text hits the text view

    override func insertText(_ text: String) {
        // Apply an armed or locked Ctrl modifier to ordinary typed characters.
        //
        // The modifier only ever reached the extra row's own keys, so the three-state
        // Ctrl machine could arm but the combination it exists for — Ctrl and a letter
        // — never happened: no Ctrl+D to close stdin, no Ctrl+Z to suspend, no Ctrl+L
        // to clear, no Ctrl+A or Ctrl+E to jump the line. Restricted to single
        // characters so a multi-character insertion (a paste, a dictation result) is
        // not silently collapsed into one control byte.
        if extraRow.ctrl.isActive,
           text.count == 1,
           let bytes = ANSISequence.bytes(for: .character(text), ctrlActive: true) {
            onRawInput?(bytes)
            extraRow.ctrl.consume()
            return
        }
        onRawInput?(Array(text.utf8))
        // Intentionally no super call — keeps UITextView text empty.
    }

    override func deleteBackward() {
        onRawInput?([0x7f]) // DEL
    }
}

extension TerminalInputView: ExtraRowDelegate {
    func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        if let bytes = ANSISequence.bytes(for: output, ctrlActive: ctrlActive) {
            onRawInput?(bytes)
        }
    }

    func extraRowDidTapClipboard(_ view: ExtraRowView) {
        guard let string = UIPasteboard.general.string else { return }
        onRawInput?(Array(string.utf8))
    }

    /// SCP upload is not available from the in-app terminal extra row.
    /// It is only active in the keyboard extension.
    func extraRowDidTapUpload(_ view: ExtraRowView) {
        // no-op in terminal context
    }
}
