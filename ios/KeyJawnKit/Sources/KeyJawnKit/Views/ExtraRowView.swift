import UIKit

// MARK: - Delegate

@MainActor
public protocol ExtraRowDelegate: AnyObject {
    /// A key in the extra row was tapped.
    /// - Parameters:
    ///   - output: the logical key output
    ///   - ctrlActive: whether the Ctrl modifier is currently armed or locked
    func extraRow(_ view: ExtraRowView, send output: KeyOutput, ctrlActive: Bool)
    func extraRowDidTapClipboard(_ view: ExtraRowView)
    func extraRowDidTapUpload(_ view: ExtraRowView)
    func extraRowDidTapMic(_ view: ExtraRowView)
    func extraRowDidCancelMic(_ view: ExtraRowView)
}

// MARK: - ExtraRowView

/// Horizontal bar of terminal keys designed for LLM CLI workflows.
///
/// Layout: ^C | Tab | ▲ | ▼ | ◄ | ► | / | Esc | Clip | SCP
///
/// Use as `inputAccessoryView` in the terminal app, or as the top row of the
/// keyboard extension view.
@MainActor
public final class ExtraRowView: UIView {

    public weak var delegate: ExtraRowDelegate?

    /// Ctrl modifier state — can be inspected by the parent to drive visual
    /// indicators outside this view (e.g. a status bar label).
    public let ctrl = CtrlState()

    // MARK: Private

    private let stack = UIStackView()
    private var ctrlCButton: ExtraRowButton?
    private var micButton: ExtraRowButton?
    private var repeatTimer: Timer?

    // MARK: - Theme

    public var theme: KeyboardTheme = .dark

    private var bg: UIColor { theme.extraRowBg }
    private var keyBg: UIColor { theme.extraRowKeyBg }
    private var armed: UIColor { theme.armed }
    private var locked: UIColor { theme.locked }

    // MARK: - Init

    public override init(frame: CGRect) {
        super.init(frame: frame)
        build()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        build()
    }

    // MARK: - Layout

    public override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 52)
    }

    // MARK: - Setup

    private func build() {
        backgroundColor = bg

        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6),
        ])

        setKeys(ExtraRowKey.defaults)

        ctrl.onChange = { [weak self] state in
            self?.applyCtrlVisual(state)
        }
    }

    /// Replace the row's keys. Used by the SSH accessory to add Send and by
    /// remappable presets later. Rebuilds buttons; Ctrl visual state is reapplied.
    public func setKeys(_ keys: [ExtraRowKey]) {
        stack.arrangedSubviews.forEach {
            stack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        ctrlCButton = nil
        micButton = nil
        for key in keys {
            let btn = ExtraRowButton(key: key, bgColor: keyBg, textColor: theme.extraRowKeyText)
            wire(btn)
            stack.addArrangedSubview(btn)
            if key.slot == .ctrlC { ctrlCButton = btn }
            if key.slot == .mic { micButton = btn }
        }
        applyCtrlVisual(ctrl.state)
    }

    public func setMicListening(_ listening: Bool) {
        guard let micButton else { return }
        micButton.backgroundColor = listening ? armed : keyBg
    }

    /// Stop auto-repeat when the row leaves the hierarchy.
    ///
    /// The repeat timer is normally cancelled on touch-up, but a panel appearing over
    /// the row, or the keyboard being dismissed mid-hold, tears the view down without
    /// one — and a repeating `Timer` left on the run loop keeps firing arrow keys into
    /// whatever gains focus next. Handled here rather than in `deinit`, which cannot
    /// touch main-actor state under Swift 6 strict concurrency.
    public override func willMove(toWindow newWindow: UIWindow?) {
        super.willMove(toWindow: newWindow)
        if newWindow == nil {
            repeatTimer?.invalidate()
            repeatTimer = nil
        }
    }

    private func wire(_ btn: ExtraRowButton) {
        switch btn.key.slot {

        case .ctrlC:
            // Tap = send ^C immediately; long-press = arm Ctrl modifier
            btn.addTarget(self, action: #selector(ctrlCTapped), for: .touchUpInside)
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(ctrlCLongPressed(_:)))
            lp.minimumPressDuration = 0.45
            btn.addGestureRecognizer(lp)

        case .arrowUp, .arrowDown, .arrowLeft, .arrowRight:
            // Fire immediately on touch down, then repeat while held
            btn.addTarget(self, action: #selector(arrowTouchDown(_:)), for: .touchDown)
            btn.addTarget(self, action: #selector(arrowTouchEnd(_:)),
                          for: [.touchUpInside, .touchUpOutside, .touchCancel])

        case .clipboard:
            btn.addTarget(self, action: #selector(clipTapped), for: .touchUpInside)

        case .upload:
            btn.addTarget(self, action: #selector(uploadTapped), for: .touchUpInside)

        case .mic:
            btn.addTarget(self, action: #selector(micTapped), for: .touchUpInside)
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(micLongPressed(_:)))
            lp.minimumPressDuration = 0.45
            btn.addGestureRecognizer(lp)

        case .send:
            btn.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(sendLongPressed(_:)))
            lp.minimumPressDuration = 0.45
            btn.addGestureRecognizer(lp)

        default:
            // Tab, slash, escape: single tap
            btn.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
        }
    }

    // MARK: - Actions

    @objc private func ctrlCTapped() {
        // Always sends ^C regardless of current Ctrl modifier state.
        // Long-press is the only way to arm the modifier.
        KeyboardHaptics.keyPress()
        fire(.ctrlC, ctrlActive: false)
    }

    @objc private func ctrlCLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        KeyboardHaptics.keyPress()
        ctrl.toggle()
    }

    @objc private func keyTapped(_ sender: ExtraRowButton) {
        guard let output = sender.key.output else { return }
        KeyboardHaptics.keyPress()
        fire(output, ctrlActive: ctrl.isActive)
        ctrl.consume()
    }

    @objc private func arrowTouchDown(_ sender: ExtraRowButton) {
        guard let output = sender.key.output else { return }
        KeyboardHaptics.keyPress()
        fire(output, ctrlActive: ctrl.isActive)
        ctrl.consume()

        repeatTimer?.invalidate()
        repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: false) { [weak self, output] _ in
            MainActor.assumeIsolated {
                self?.repeatTimer = Timer.scheduledTimer(
                    withTimeInterval: 0.08,
                    repeats: true
                ) { [weak self, output] _ in
                    MainActor.assumeIsolated {
                        self?.fire(output, ctrlActive: false) // repeat never carries Ctrl
                    }
                }
            }
        }
    }

    @objc private func arrowTouchEnd(_ sender: ExtraRowButton) {
        repeatTimer?.invalidate()
        repeatTimer = nil
    }

    @objc private func clipTapped() {
        KeyboardHaptics.keyPress()
        delegate?.extraRowDidTapClipboard(self)
    }

    @objc private func uploadTapped() {
        KeyboardHaptics.keyPress()
        delegate?.extraRowDidTapUpload(self)
    }

    @objc private func micTapped() {
        KeyboardHaptics.keyPress()
        delegate?.extraRowDidTapMic(self)
    }

    @objc private func micLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        KeyboardHaptics.keyPress()
        delegate?.extraRowDidCancelMic(self)
    }

    @objc private func sendTapped() {
        KeyboardHaptics.keyPress()
        fire(.send, ctrlActive: false)
    }

    @objc private func sendLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        KeyboardHaptics.keyPress()
        fire(.newline, ctrlActive: false)
    }

    // MARK: - Helpers

    private func fire(_ output: KeyOutput, ctrlActive: Bool) {
        delegate?.extraRow(self, send: output, ctrlActive: ctrlActive)
    }

    // MARK: - Ctrl visual state

    private func applyCtrlVisual(_ state: CtrlState.State) {
        guard let btn = ctrlCButton else { return }
        UIView.animate(withDuration: 0.15) { [self] in
            switch state {
            case .off:
                btn.backgroundColor = keyBg
                btn.layer.shadowOpacity = 0
            case .armed:
                btn.backgroundColor = armed
                btn.layer.shadowColor = armed.withAlphaComponent(0.5).cgColor
                btn.layer.shadowOpacity = 1
                btn.layer.shadowRadius = 8
                btn.layer.shadowOffset = .zero
            case .locked:
                btn.backgroundColor = locked
                btn.layer.shadowColor = locked.withAlphaComponent(0.5).cgColor
                btn.layer.shadowOpacity = 1
                btn.layer.shadowRadius = 8
                btn.layer.shadowOffset = .zero
            }
        }
    }

    // MARK: - Theme application

    public func applyTheme(_ theme: KeyboardTheme) {
        self.theme = theme
        backgroundColor = bg
        for subview in stack.arrangedSubviews {
            if let btn = subview as? ExtraRowButton {
                btn.applyColors(background: keyBg, text: theme.extraRowKeyText)
            }
        }
        applyCtrlVisual(ctrl.state)
    }
}

// MARK: - ExtraRowButton

@MainActor
final class ExtraRowButton: UIButton {

    let key: ExtraRowKey

    init(key: ExtraRowKey, bgColor: UIColor, textColor: UIColor) {
        self.key = key
        super.init(frame: .zero)
        titleLabel?.font = .monospacedSystemFont(ofSize: 13, weight: .semibold)
        setTitle(key.label, for: .normal)
        accessibilityLabel = key.accessibilityLabel
        accessibilityIdentifier = key.accessibilityIdentifier
        layer.cornerRadius = 6
        layer.masksToBounds = false
        layer.shadowOpacity = 0
        applyColors(background: bgColor, text: textColor)
    }

    required init?(coder: NSCoder) { fatalError("use init(key:bgColor:textColor:)") }

    /// The label colour used to be a hardcoded white, which survived every theme
    /// change: on Light that put white glyphs on a mid-grey key at 3.3:1, below the
    /// 4.5:1 floor, and on Terminal it broke the green-on-green treatment the rest of
    /// the keyboard uses.
    func applyColors(background: UIColor, text: UIColor) {
        backgroundColor = background
        setTitleColor(text, for: .normal)
        setTitleColor(text.withAlphaComponent(0.4), for: .highlighted)
    }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.08) {
                self.alpha = self.isHighlighted ? 0.65 : 1.0
            }
        }
    }
}
