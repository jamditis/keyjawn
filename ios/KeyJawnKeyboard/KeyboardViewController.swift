import UIKit
import KeyJawnKit

public class KeyboardViewController: UIInputViewController {

    private var extraRow: ExtraRowView!
    private var qwerty: QwertyKeyboardView!

    // Total height: 52 (extra row) + 8 (gap) + 216 (4 key rows) = 276
    private static let keyboardHeight: CGFloat = 276

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)

        setupExtraRow()
        setupQwerty()
        setupHeightConstraint()
    }

    // MARK: - Setup

    private func setupExtraRow() {
        extraRow = ExtraRowView()
        extraRow.delegate = self
        extraRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(extraRow)

        NSLayoutConstraint.activate([
            extraRow.topAnchor.constraint(equalTo: view.topAnchor),
            extraRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            extraRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            extraRow.heightAnchor.constraint(equalToConstant: 52),
        ])
    }

    private func setupQwerty() {
        qwerty = QwertyKeyboardView()
        qwerty.delegate = self
        qwerty.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(qwerty)

        NSLayoutConstraint.activate([
            qwerty.topAnchor.constraint(equalTo: extraRow.bottomAnchor, constant: 4),
            qwerty.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            qwerty.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            qwerty.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupHeightConstraint() {
        // Priority 999 avoids conflicting with the system's own height constraint during
        // the animation when the keyboard appears.
        let h = view.heightAnchor.constraint(equalToConstant: Self.keyboardHeight)
        h.priority = UILayoutPriority(999)
        h.isActive = true
    }
}

// MARK: - ExtraRowDelegate

extension KeyboardViewController: ExtraRowDelegate {

    public func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        let proxy = textDocumentProxy

        switch output {
        case .tab:
            proxy.insertText("\t")

        case .escape:
            // Esc is ignored by most text inputs; insert the escape character
            // so apps that handle raw input (like terminal emulators) receive it.
            proxy.insertText("\u{1b}")

        case .arrowLeft:
            proxy.adjustTextPosition(byCharacterOffset: -1)

        case .arrowRight:
            proxy.adjustTextPosition(byCharacterOffset: 1)

        case .slash:
            proxy.insertText("/")
            // TODO: show slash command popup

        case .character(let s):
            proxy.insertText(s)

        default:
            // Ctrl+C, arrowUp/Down — not supported via UITextDocumentProxy.
            // Document this limitation in the extension's help text.
            break
        }
    }

    public func extraRowDidTapClipboard(_ view: ExtraRowView) {
        // TODO: clipboard history panel
    }
}

// MARK: - QwertyKeyboardDelegate

extension KeyboardViewController: QwertyKeyboardDelegate {

    public func keyboard(_ keyboard: QwertyKeyboardView, insertText text: String) {
        textDocumentProxy.insertText(text)
    }

    public func keyboardDeleteBackward(_ keyboard: QwertyKeyboardView) {
        textDocumentProxy.deleteBackward()
    }

    public func keyboardAdvanceToNextInputMode(_ keyboard: QwertyKeyboardView) {
        advanceToNextInputMode()
    }
}
