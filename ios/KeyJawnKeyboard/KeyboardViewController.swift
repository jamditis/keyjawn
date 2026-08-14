import UIKit
import KeyJawnKit

public final class KeyboardViewController: UIInputViewController {

    private var extraRow: ExtraRowView!
    private var numberRow: NumberRowView!
    private var qwerty: QwertyKeyboardView!
    private var slashPanel: SlashCommandPanel?
    private var clipboardPanel: ClipboardPanel?
    private var uploadPanel: UploadPanel?

    private var theme: KeyboardTheme = .dark

    // Height constraints are held so the layout can follow the device and orientation
    // rather than being pinned to one phone-portrait number.
    private var extraRowHeight: NSLayoutConstraint!
    private var numberRowTop: NSLayoutConstraint!
    private var numberRowHeight: NSLayoutConstraint!
    private var qwertyTop: NSLayoutConstraint!
    private var keyboardHeight: NSLayoutConstraint!
    private var appliedMetrics: KeyboardMetrics?

    // MARK: - Lifecycle

    public override func viewDidLoad() {
        super.viewDidLoad()
        theme = KeyboardPrefs.shared.theme
        view.backgroundColor = theme.keyboardBg
        setupExtraRow()
        setupNumberRow()
        setupQwerty()
        setupHeightConstraint()
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // The extension process survives between activations, so a theme picked in the
        // main app has to be re-read here rather than only at first load.
        let current = KeyboardPrefs.shared.theme
        if current != theme {
            theme = current
            applyTheme()
        }
        KeyboardHaptics.refresh()
        extraRow.setKeys(KeyboardPrefs.shared.extraRowPreset.keys)
        extraRow.applyTheme(theme)
    }

    public override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        applyMetrics()
    }

    /// Recompute row heights for the current traits.
    ///
    /// Safe to call from layout because it is idempotent: the guard returns without
    /// touching a constraint once the metrics for the current traits are applied, so
    /// it cannot drive a layout loop.
    private func applyMetrics() {
        let metrics = KeyboardMetrics.current(for: traitCollection)
        guard metrics != appliedMetrics else { return }
        appliedMetrics = metrics

        extraRowHeight.constant = metrics.extraRow
        numberRowTop.constant = metrics.gap
        numberRowHeight.constant = metrics.numberRow
        qwertyTop.constant = metrics.gap
        keyboardHeight.constant = metrics.total
    }

    // MARK: - Setup

    private func setupExtraRow() {
        extraRow = ExtraRowView()
        extraRow.delegate = self
        extraRow.applyTheme(theme)
        extraRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(extraRow)

        extraRowHeight = extraRow.heightAnchor.constraint(equalToConstant: KeyboardMetrics.phonePortrait.extraRow)
        NSLayoutConstraint.activate([
            extraRow.topAnchor.constraint(equalTo: view.topAnchor),
            extraRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            extraRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            extraRowHeight,
        ])
    }

    private func setupNumberRow() {
        numberRow = NumberRowView()
        numberRow.delegate = self
        numberRow.applyTheme(theme)
        numberRow.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(numberRow)

        numberRowTop = numberRow.topAnchor.constraint(equalTo: extraRow.bottomAnchor,
                                                      constant: KeyboardMetrics.phonePortrait.gap)
        numberRowHeight = numberRow.heightAnchor.constraint(equalToConstant: KeyboardMetrics.phonePortrait.numberRow)
        NSLayoutConstraint.activate([
            numberRowTop,
            numberRow.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            numberRow.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            numberRowHeight,
        ])
    }

    private func setupQwerty() {
        qwerty = QwertyKeyboardView()
        qwerty.applyTheme(theme)
        qwerty.delegate = self
        qwerty.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(qwerty)

        qwertyTop = qwerty.topAnchor.constraint(equalTo: numberRow.bottomAnchor,
                                                constant: KeyboardMetrics.phonePortrait.gap)
        NSLayoutConstraint.activate([
            qwertyTop,
            qwerty.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            qwerty.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            qwerty.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupHeightConstraint() {
        // Priority 999 avoids conflicting with the system's own height constraint during
        // the animation when the keyboard appears.
        keyboardHeight = view.heightAnchor.constraint(equalToConstant: KeyboardMetrics.phonePortrait.total)
        keyboardHeight.priority = UILayoutPriority(999)
        keyboardHeight.isActive = true
    }

    private func applyTheme() {
        view.backgroundColor = theme.keyboardBg
        extraRow.applyTheme(theme)
        numberRow.applyTheme(theme)
        qwerty.applyTheme(theme)
        // Any panel on screen was built with the old colours; drop it rather than
        // leave a half-recoloured overlay.
        hideSlashPanel()
        hideClipboardPanel()
        hideUploadPanel()
    }

    // MARK: - Panel presentation

    /// Panels cover the keyboard rather than replacing it — a keyboard extension
    /// cannot `present()`, so every overlay is a subview sized to the root view.
    private func showPanel(_ panel: UIView) {
        panel.frame = view.bounds
        panel.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(panel)
    }
}

// MARK: - ExtraRowDelegate

extension KeyboardViewController: ExtraRowDelegate {

    public func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool) {
        applyInsert(output, ctrlActive: ctrlActive)
    }

    /// Shared with QWERTY taps so extra-row and letter keys hit the same mapping.
    func applyInsert(_ output: KeyOutput, ctrlActive: Bool) {
        let proxy = textDocumentProxy
        switch KeyboardDocumentInsert.action(
            for: output,
            ctrlActive: ctrlActive,
            terminalArrows: KeyboardPrefs.shared.terminalArrowKeys
        ) {
        case .openSlash:
            showSlashPanel()
        case .insert(let text):
            proxy.insertText(text)
        case .deleteBackward:
            proxy.deleteBackward()
        case .adjustPosition(let offset):
            proxy.adjustTextPosition(byCharacterOffset: offset)
        case nil:
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

        let panel = SlashCommandPanel(theme: theme)
        panel.onSelect = { [weak self] command in
            self?.textDocumentProxy.insertText(command.trigger)
            self?.hideSlashPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideSlashPanel()
        }
        showPanel(panel)
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

        let panel = ClipboardPanel(theme: theme)
        panel.refresh()
        panel.onSelect = { [weak self] text in
            self?.textDocumentProxy.insertText(text)
            self?.hideClipboardPanel()
        }
        panel.onDismiss = { [weak self] in
            self?.hideClipboardPanel()
        }
        showPanel(panel)
        clipboardPanel = panel
    }

    private func hideClipboardPanel() {
        clipboardPanel?.removeFromSuperview()
        clipboardPanel = nil
    }

    // MARK: - Upload panel

    private func showUploadPanel() {
        guard uploadPanel == nil else { return }

        let panel = UploadPanel(theme: theme)
        panel.hosts = hasFullAccess ? AppGroupHostStore.shared.hosts : []
        // Without Full Access the shared container is closed to the extension, so the
        // host list reads back empty and SFTP has no network. Name that instead of
        // telling the user to add hosts they have already added.
        panel.emptyReason = hasFullAccess ? .noHostsConfigured : .fullAccessRequired
        panel.onDismiss = { [weak self] in self?.hideUploadPanel() }

        guard hasFullAccess else {
            panel.statusMessage = "Full Access is required to upload"
            panel.isUploadEnabled = false
            panel.onUpload = { _ in }
            showPanel(panel)
            uploadPanel = panel
            return
        }

        // Grab the raw encoded image bytes on the main actor (cheap: no decode),
        // so the heavy decode/downsample/encode can run off it. Reading
        // UIPasteboard.image here instead would decode the full bitmap on the
        // keyboard's main thread and freeze key input the moment SCP is tapped (#46).
        guard let rawImageData = UIPasteboard.general.firstImageData else {
            // hasImages true here means an image is present but exposes no
            // readable data representation, so say that instead of implying the
            // user copied nothing.
            panel.statusMessage = UIPasteboard.general.hasImages
                ? "Couldn't read that image. Try copying it again."
                : "Copy an image first, then tap SCP"
            panel.isUploadEnabled = false
            panel.onUpload = { _ in }
            showPanel(panel)
            uploadPanel = panel
            return
        }

        // Show the panel immediately in a preparing state with upload gated off,
        // then downsample and JPEG-encode off the main actor. A host tap before
        // the data is ready is a no-op (UploadPanel.isUploadEnabled).
        panel.statusMessage = "Preparing image..."
        panel.isUploadEnabled = false
        panel.onUpload = { _ in }
        showPanel(panel)
        uploadPanel = panel

        Task { [weak self] in
            let imageData = await Task.detached(priority: .userInitiated) {
                PasteboardImagePreparer.downsampledJPEGData(from: rawImageData)
            }.value

            // Bail if the panel was dismissed (or replaced) while preparing.
            guard let self, self.uploadPanel === panel else { return }
            guard let imageData else {
                panel.statusMessage = "Couldn't read that image. Try copying it again."
                return
            }
            panel.statusMessage = "Select a host to upload"
            panel.onUpload = { [weak self] host in
                self?.performUpload(imageData: imageData, to: host)
            }
            panel.isUploadEnabled = true
        }
    }

    private func hideUploadPanel() {
        uploadPanel?.removeFromSuperview()
        uploadPanel = nil
    }

    private func performUpload(imageData: Data, to host: HostConfig) {
        guard let keyData = SharedSSHKeyStore.shared.privateKeyData else {
            uploadPanel?.statusMessage = "SSH key not found. Open the main app first."
            return
        }
        uploadPanel?.statusMessage = "Uploading to \(host.label)..."
        // A second tap while a transfer is in flight would start a second connection
        // from a memory-constrained extension; hold the list closed until this returns.
        uploadPanel?.isUploadEnabled = false

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
                self.uploadPanel?.isUploadEnabled = true
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
        let output = KeyOutput.character(text)
        let ctrlActive = extraRow.ctrl.isActive
        applyInsert(output, ctrlActive: ctrlActive)
        if KeyboardDocumentInsert.consumesCtrl(for: output, ctrlActive: ctrlActive) {
            extraRow.ctrl.consume()
        }
    }

    public func keyboardDeleteBackward(_ keyboard: QwertyKeyboardView) {
        applyInsert(.backspace, ctrlActive: false)
    }

    /// `UITextDocumentProxy` has no word-delete primitive, so this is N backward
    /// deletes with N derived from the text before the cursor. The boundary rule keeps
    /// paths and flags whole; see `WordBoundary`.
    public func keyboardDeleteWordBackward(_ keyboard: QwertyKeyboardView) {
        let context = textDocumentProxy.documentContextBeforeInput ?? ""
        let count = WordBoundary.deleteCount(before: context)
        guard count > 0 else {
            // No context available (some hosts withhold it) — fall back to one
            // character so a held backspace still makes progress.
            textDocumentProxy.deleteBackward()
            return
        }
        for _ in 0..<count {
            textDocumentProxy.deleteBackward()
        }
    }

    public func keyboardAdvanceToNextInputMode(_ keyboard: QwertyKeyboardView) {
        advanceToNextInputMode()
    }
}


