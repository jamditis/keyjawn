import UIKit
import KeyJawnKit

public class KeyboardViewController: UIInputViewController, ExtraRowDelegate {

    private var extraRow: ExtraRowView!

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)

        extraRow = ExtraRowView()
        extraRow.delegate = self
        extraRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(extraRow)

        // Globe button — required by App Store guideline 4.4.1
        let globe = UIButton(type: .system)
        globe.setImage(UIImage(systemName: "globe"), for: .normal)
        globe.tintColor = .white
        globe.translatesAutoresizingMaskIntoConstraints = false
        globe.addTarget(self, action: #selector(advanceToNextInputMode), for: .touchUpInside)
        view.addSubview(globe)

        // Placeholder for the QWERTY grid (next milestone)
        let placeholder = UILabel()
        placeholder.text = "QWERTY keyboard coming soon"
        placeholder.textColor = UIColor.white.withAlphaComponent(0.4)
        placeholder.font = .systemFont(ofSize: 13)
        placeholder.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(placeholder)

        NSLayoutConstraint.activate([
            // Extra row sits at the top of the keyboard view
            extraRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            extraRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            extraRow.topAnchor.constraint(equalTo: view.topAnchor),

            // Placeholder below the extra row
            placeholder.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            placeholder.topAnchor.constraint(equalTo: extraRow.bottomAnchor, constant: 20),

            // Globe bottom-left
            globe.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            globe.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
        ])
    }

    // MARK: - ExtraRowDelegate

    public func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        guard let proxy = textDocumentProxy as? UITextDocumentProxy else { return }

        switch output {
        case .tab:
            proxy.insertText("\t")

        case .arrowLeft:
            proxy.adjustTextPosition(byCharacterOffset: -1)

        case .arrowRight:
            proxy.adjustTextPosition(byCharacterOffset: 1)

        case .slash:
            proxy.insertText("/")
            // TODO: open slash command popup

        case .character(let s):
            proxy.insertText(s)

        default:
            // Esc, Ctrl+C, arrows up/down — insertText("\u{1b}") is ignored
            // by most apps. Document this limitation clearly in the UI.
            break
        }
    }

    public func extraRowDidTapClipboard(_ view: ExtraRowView) {
        // TODO: open clipboard history panel
    }
}
