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

    /// Hosts for the in-app SCP panel. Defaults to a live `HostStore` load so
    /// the accessory sees the same list as Settings. Tests inject a snapshot.
    var uploadHosts: () -> [HostConfig] = { HostStore().hosts }

    /// Private key bytes for the in-app SCP upload. Defaults to the shared
    /// identity. Tests inject a stub.
    var uploadPrivateKeyData: () -> Data? = { SSHKeyStore.shared.privateKey.rawRepresentation }

    private var slashPanel: SlashCommandPanel?
    private var clipboardPanel: ClipboardPanel?
    private var uploadPanel: UploadPanel?

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
        KeyboardHaptics.refresh()
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
           text != "\n",
           text != "\r",
           let bytes = ANSISequence.bytes(for: .character(text), ctrlActive: true) {
            onRawInput?(bytes)
            extraRow.ctrl.consume()
            return
        }
        onRawInput?(TerminalInputMapping.bytes(forInsertedText: text))
        // Intentionally no super call — keeps UITextView text empty.
        // Armed Ctrl is only consumed when a single-character control mapping
        // actually fired, so a paste or dictation result does not eat the next
        // Ctrl+letter.
    }

    override func deleteBackward() {
        onRawInput?([0x7f]) // DEL
    }

    /// Inserts a non-submit newline (LF). Used by long-press Send so a
    /// multiline prompt can gain a line without submitting to the agent.
    func insertNewlineWithoutSubmit() {
        onRawInput?(TerminalInputMapping.newlineBytes)
    }

    /// Writes the submit byte (CR) the same way a system Return does.
    func submitLine() {
        onRawInput?(TerminalInputMapping.submitBytes)
    }
}

extension TerminalInputView: ExtraRowDelegate {
    func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        switch TerminalInputMapping.extraRow(output, ctrlActive: ctrlActive) {
        case .write(let bytes):
            onRawInput?(bytes)
        case .openSlash:
            showSlashPanel()
        case .none:
            break
        }
    }

    func extraRowDidTapClipboard(_ view: ExtraRowView) {
        if clipboardPanel != nil {
            hideClipboardPanel()
        } else {
            showClipboardPanel()
        }
    }

    func extraRowDidTapUpload(_ view: ExtraRowView) {
        if uploadPanel != nil {
            hideUploadPanel()
        } else {
            showUploadPanel()
        }
    }
}

// MARK: - Overlays

extension TerminalInputView {
    private func overlayHost() -> UIView {
        superview ?? self
    }

    private func showOverlay(_ panel: UIView) {
        let host = overlayHost()
        panel.translatesAutoresizingMaskIntoConstraints = false
        host.addSubview(panel)
        NSLayoutConstraint.activate([
            panel.topAnchor.constraint(equalTo: host.topAnchor),
            panel.leadingAnchor.constraint(equalTo: host.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: host.trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: host.keyboardLayoutGuide.topAnchor),
        ])
    }

    // MARK: Slash

    private func showSlashPanel() {
        guard slashPanel == nil else { return }
        hideClipboardPanel()
        hideUploadPanel()

        let panel = SlashCommandPanel(theme: KeyboardPrefs.shared.theme)
        panel.onSelect = { [weak self] command in
            self?.onRawInput?(Array(command.trigger.utf8))
            self?.hideSlashPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideSlashPanel()
        }
        showOverlay(panel)
        slashPanel = panel
    }

    private func hideSlashPanel() {
        slashPanel?.removeFromSuperview()
        slashPanel = nil
    }

    // MARK: Clipboard

    private func showClipboardPanel() {
        guard clipboardPanel == nil else { return }
        hideSlashPanel()
        hideUploadPanel()
        ClipboardHistory.shared.addCurrent()

        let panel = ClipboardPanel(theme: KeyboardPrefs.shared.theme)
        panel.refresh()
        panel.onSelect = { [weak self] text in
            self?.onRawInput?(Array(text.utf8))
            self?.hideClipboardPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideClipboardPanel()
        }
        showOverlay(panel)
        clipboardPanel = panel
    }

    private func hideClipboardPanel() {
        clipboardPanel?.removeFromSuperview()
        clipboardPanel = nil
    }

    // MARK: Upload

    private func showUploadPanel() {
        guard uploadPanel == nil else { return }
        hideSlashPanel()
        hideClipboardPanel()

        let theme = KeyboardPrefs.shared.theme
        let panel = UploadPanel(theme: theme)
        let hosts = uploadHosts()
        panel.hosts = hosts
        panel.emptyReason = .noHostsConfigured
        panel.onDismiss = { [weak self] in self?.hideUploadPanel() }

        guard let rawImageData = UIPasteboard.general.firstImageData else {
            panel.statusMessage = UIPasteboard.general.hasImages
                ? "Couldn't read that image. Try copying it again."
                : "Copy an image first, then tap SCP"
            panel.isUploadEnabled = false
            panel.onUpload = { _ in }
            showOverlay(panel)
            uploadPanel = panel
            return
        }

        panel.statusMessage = "Preparing image..."
        panel.isUploadEnabled = false
        panel.onUpload = { _ in }
        showOverlay(panel)
        uploadPanel = panel

        Task { [weak self] in
            let imageData = await Task.detached(priority: .userInitiated) {
                PasteboardImagePreparer.downsampledJPEGData(from: rawImageData)
            }.value

            guard let self, self.uploadPanel === panel else { return }
            guard let imageData else {
                panel.statusMessage = "Couldn't read that image. Try copying it again."
                return
            }
            panel.statusMessage = hosts.isEmpty
                ? "No hosts configured. Add one in Settings."
                : "Select a host to upload"
            panel.onUpload = { [weak self] host in
                self?.performUpload(imageData: imageData, to: host)
            }
            panel.isUploadEnabled = !hosts.isEmpty
        }
    }

    private func hideUploadPanel() {
        uploadPanel?.removeFromSuperview()
        uploadPanel = nil
    }

    private func performUpload(imageData: Data, to host: HostConfig) {
        guard let keyData = uploadPrivateKeyData() else {
            uploadPanel?.statusMessage = "SSH key not found. Open Settings → SSH keys first."
            return
        }
        uploadPanel?.statusMessage = "Uploading to \(host.label)..."
        uploadPanel?.isUploadEnabled = false

        Task { @MainActor in
            do {
                let path = try await CitadelSCPUploader.upload(
                    imageData: imageData,
                    to: host,
                    privateKeyData: keyData
                )
                self.onRawInput?(Array(path.utf8))
                self.hideUploadPanel()
            } catch {
                self.uploadPanel?.statusMessage = "Upload failed: \(error.localizedDescription)"
                self.uploadPanel?.isUploadEnabled = true
            }
        }
    }
}
