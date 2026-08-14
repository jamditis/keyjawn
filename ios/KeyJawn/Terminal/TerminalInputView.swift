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
    private let voice = TerminalVoiceInput()
    private var lastHardwareEmit: (bytes: [UInt8], at: CFTimeInterval)?

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
        extraRow.setKeys(KeyboardPrefs.shared.extraRowPreset.terminalKeys)
        extraRow.delegate  = self
        // Same theme the keyboard extension uses, so the row looks like one component
        // wherever it appears rather than defaulting to dark here and themed there.
        extraRow.applyTheme(KeyboardPrefs.shared.theme)
        KeyboardHaptics.refresh()
        inputAccessoryView = extraRow
        voice.onCommit = { [weak self] text in
            self?.onRawInput?(Array(text.utf8))
        }
        voice.onListeningChange = { [weak self] listening in
            self?.extraRow.setMicListening(listening)
        }
    }

    /// Re-read the App Group preset. The accessory lives across tab switches.
    func applyExtraRowPreset() {
        extraRow.setKeys(KeyboardPrefs.shared.extraRowPreset.terminalKeys)
        extraRow.applyTheme(KeyboardPrefs.shared.theme)
    }

    func cancelVoice() {
        voice.cancel()
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
        if text == "\n" || text == "\r" {
            extraRow.ctrl.consume()
        }
        // Intentionally no super call — keeps UITextView text empty.
        // Armed Ctrl is only consumed for a control mapping or a submit, so a
        // paste or dictation result does not eat the next Ctrl+letter.
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

    // MARK: Hardware keyboard

    override var keyCommands: [UIKeyCommand]? {
        [
            UIKeyCommand(input: UIKeyCommand.inputEscape, modifierFlags: [], action: #selector(hardwareEscape)),
            UIKeyCommand(input: "\t", modifierFlags: [], action: #selector(hardwareTab)),
            UIKeyCommand(input: UIKeyCommand.inputUpArrow, modifierFlags: [], action: #selector(hardwareUp)),
            UIKeyCommand(input: UIKeyCommand.inputDownArrow, modifierFlags: [], action: #selector(hardwareDown)),
            UIKeyCommand(input: UIKeyCommand.inputLeftArrow, modifierFlags: [], action: #selector(hardwareLeft)),
            UIKeyCommand(input: UIKeyCommand.inputRightArrow, modifierFlags: [], action: #selector(hardwareRight)),
            UIKeyCommand(input: "c", modifierFlags: .control, action: #selector(hardwareCtrlC)),
            UIKeyCommand(input: "d", modifierFlags: .control, action: #selector(hardwareCtrlD)),
            UIKeyCommand(input: "z", modifierFlags: .control, action: #selector(hardwareCtrlZ)),
        ]
    }

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        var handled = false
        for press in presses {
            guard let key = press.key,
                  let mapped = hardwareKey(from: key) else { continue }
            let modifiers = hardwareModifiers(from: key)
            if emitHardware(mapped, modifiers: modifiers) {
                handled = true
            }
        }
        if !handled {
            super.pressesBegan(presses, with: event)
        }
    }

    @discardableResult
    private func emitHardware(_ key: HardwareKey, modifiers: HardwareModifiers) -> Bool {
        guard let bytes = HardwareKeyMapping.bytes(for: key, modifiers: modifiers) else {
            return false
        }
        let now = CACurrentMediaTime()
        if let last = lastHardwareEmit, last.bytes == bytes, now - last.at < 0.03 {
            return true
        }
        lastHardwareEmit = (bytes, now)
        onRawInput?(bytes)
        return true
    }

    private func hardwareKey(from key: UIKey) -> HardwareKey? {
        switch key.keyCode {
        case .keyboardEscape: return .escape
        case .keyboardTab: return .tab
        case .keyboardUpArrow: return .arrowUp
        case .keyboardDownArrow: return .arrowDown
        case .keyboardLeftArrow: return .arrowLeft
        case .keyboardRightArrow: return .arrowRight
        default:
            let chars = key.charactersIgnoringModifiers
            guard chars.count == 1 else { return nil }
            return .character(chars)
        }
    }

    private func hardwareModifiers(from key: UIKey) -> HardwareModifiers {
        var mods = HardwareModifiers()
        if key.modifierFlags.contains(.control) { mods.insert(.control) }
        if key.modifierFlags.contains(.command) { mods.insert(.command) }
        if key.modifierFlags.contains(.alternate) { mods.insert(.alternate) }
        if key.modifierFlags.contains(.shift) { mods.insert(.shift) }
        return mods
    }

    @objc private func hardwareEscape() { emitHardware(.escape, modifiers: []) }
    @objc private func hardwareTab() { emitHardware(.tab, modifiers: []) }
    @objc private func hardwareUp() { emitHardware(.arrowUp, modifiers: []) }
    @objc private func hardwareDown() { emitHardware(.arrowDown, modifiers: []) }
    @objc private func hardwareLeft() { emitHardware(.arrowLeft, modifiers: []) }
    @objc private func hardwareRight() { emitHardware(.arrowRight, modifiers: []) }
    @objc private func hardwareCtrlC() { emitHardware(.character("c"), modifiers: .control) }
    @objc private func hardwareCtrlD() { emitHardware(.character("d"), modifiers: .control) }
    @objc private func hardwareCtrlZ() { emitHardware(.character("z"), modifiers: .control) }
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

    func extraRowDidTapMic(_ view: ExtraRowView) {
        voice.toggle()
    }

    func extraRowDidCancelMic(_ view: ExtraRowView) {
        voice.cancel()
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

        let customs = SlashCommandStore.commands(from: SlashCommandStore.appGroupDefaults())
        let panel = SlashCommandPanel(
            commands: SlashCommand.all + customs,
            theme: KeyboardPrefs.shared.theme
        )
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
