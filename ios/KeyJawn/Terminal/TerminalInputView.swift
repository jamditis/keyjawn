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

        extraRow.frame     = CGRect(x: 0, y: 0, width: 0, height: 52)
        extraRow.delegate  = self
        inputAccessoryView = extraRow
    }

    // MARK: UIKeyInput — intercept before text hits the text view

    override func insertText(_ text: String) {
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
    func extraRowDidTapClipboard(_ view: ExtraRowView) { print("clipboard") }
}
