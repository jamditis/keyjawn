import UIKit
import KeyJawnKit

public final class KeyboardViewController: UIInputViewController {

    private var extraRow: ExtraRowView!
    private var numberRow: NumberRowView!
    private var qwerty: QwertyKeyboardView!
    private var slashPanel: SlashCommandPanel?
    private var clipboardPanel: ClipboardPanel?
    private var uploadPanel: UploadPanel?

    // Total height: 52 (extra row) + 4 (gap) + 42 (number row) + 4 (gap) + 220 (4 key rows) = 322
    private static let keyboardHeight: CGFloat = 322

    public override func viewDidLoad() {
        super.viewDidLoad()
        let theme = KeyboardPrefs.shared.theme
        view.backgroundColor = theme.keyboardBg
        setupExtraRow(theme: theme)
        setupNumberRow()
        setupQwerty(theme: theme)
        setupHeightConstraint()
    }

    // MARK: - Setup

    private func setupExtraRow(theme: KeyboardTheme) {
        extraRow = ExtraRowView()
        extraRow.delegate = self
        extraRow.applyTheme(theme)
        extraRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(extraRow)

        NSLayoutConstraint.activate([
            extraRow.topAnchor.constraint(equalTo: view.topAnchor),
            extraRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            extraRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            extraRow.heightAnchor.constraint(equalToConstant: 52),
        ])
    }

    private func setupNumberRow() {
        numberRow = NumberRowView()
        numberRow.delegate = self
        numberRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(numberRow)

        NSLayoutConstraint.activate([
            numberRow.topAnchor.constraint(equalTo: extraRow.bottomAnchor, constant: 4),
            numberRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            numberRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            numberRow.heightAnchor.constraint(equalToConstant: 42),
        ])
    }

    private func setupQwerty(theme: KeyboardTheme) {
        qwerty = QwertyKeyboardView()
        qwerty.theme = theme
        qwerty.delegate = self
        qwerty.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(qwerty)

        NSLayoutConstraint.activate([
            qwerty.topAnchor.constraint(equalTo: numberRow.bottomAnchor, constant: 4),
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
            showSlashPanel()

        case .character(let s):
            proxy.insertText(s)

        default:
            // Ctrl+C, arrowUp/Down — not supported via UITextDocumentProxy.
            break
        }
    }

    public func extraRowDidTapClipboard(_ view: ExtraRowView) {
        if clipboardPanel != nil {
            hideClipboardPanel()
        } else {
            showClipboardPanel()
        }
    }

    public func extraRowDidTapUpload(_ view: ExtraRowView) {
        if uploadPanel != nil {
            hideUploadPanel()
        } else {
            showUploadPanel()
        }
    }

    // MARK: - Slash command panel

    private func showSlashPanel() {
        guard slashPanel == nil else { return }

        let panel = SlashCommandPanel()
        panel.frame = view.bounds
        panel.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        panel.onSelect = { [weak self] command in
            self?.textDocumentProxy.insertText(command.trigger)
            self?.hideSlashPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideSlashPanel()
        }
        view.addSubview(panel)
        slashPanel = panel
    }

    private func hideSlashPanel() {
        slashPanel?.removeFromSuperview()
        slashPanel = nil
    }

    // MARK: - Clipboard panel

    private func showClipboardPanel() {
        guard clipboardPanel == nil else { return }
        // Snapshot current clipboard into history before showing.
        ClipboardHistory.shared.addCurrent()

        let panel = ClipboardPanel()
        panel.frame = view.bounds
        panel.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        panel.refresh()
        panel.onSelect = { [weak self] text in
            self?.textDocumentProxy.insertText(text)
            self?.hideClipboardPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideClipboardPanel()
        }
        view.addSubview(panel)
        clipboardPanel = panel
    }

    private func hideClipboardPanel() {
        clipboardPanel?.removeFromSuperview()
        clipboardPanel = nil
    }

    // MARK: - Upload panel

    private func showUploadPanel() {
        guard uploadPanel == nil else { return }

        let panel = UploadPanel()
        panel.frame = view.bounds
        panel.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        panel.hosts = AppGroupHostStore.shared.hosts

        guard let imageData = UIPasteboard.general.image?.jpegData(compressionQuality: 0.85) else {
            panel.statusMessage = "Copy an image first, then tap SCP"
            panel.onDismiss = { [weak self] in self?.hideUploadPanel() }
            panel.onUpload = { _ in }
            view.addSubview(panel)
            uploadPanel = panel
            return
        }

        panel.statusMessage = "Select a host to upload"
        panel.onDismiss = { [weak self] in self?.hideUploadPanel() }
        panel.onUpload = { [weak self] host in
            self?.performUpload(imageData: imageData, to: host)
        }
        view.addSubview(panel)
        uploadPanel = panel
    }

    private func hideUploadPanel() {
        uploadPanel?.removeFromSuperview()
        uploadPanel = nil
    }

    private func performUpload(imageData: Data, to host: HostConfig) {
        guard let keyData = AppGroupSSHKeyStore.shared.privateKeyData else {
            uploadPanel?.statusMessage = "SSH key not found. Open the main app first."
            return
        }
        uploadPanel?.statusMessage = "Uploading..."

        Task { @MainActor in
            do {
                let path = try await CitadelSCPUploader.upload(
                    imageData: imageData,
                    to: host,
                    privateKeyData: keyData
                )
                self.textDocumentProxy.insertText(path)
                self.hideUploadPanel()
            } catch {
                self.uploadPanel?.statusMessage = "Upload failed: \(error.localizedDescription)"
            }
        }
    }
}

// MARK: - NumberRowDelegate

extension KeyboardViewController: NumberRowDelegate {
    public func numberRow(_ view: NumberRowView, insertText text: String) {
        textDocumentProxy.insertText(text)
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
