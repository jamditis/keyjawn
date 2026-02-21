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

        for key in ExtraRowKey.defaults {
            let btn = ExtraRowButton(key: key, bgColor: keyBg)
            wire(btn)
            stack.addArrangedSubview(btn)
            if key.slot == .ctrlC { ctrlCButton = btn }
        }

        ctrl.onChange = { [weak self] state in
            self?.applyCtrlVisual(state)
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

        default:
            // Tab, slash, escape: single tap
            btn.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
        }
    }

    // MARK: - Actions

    @objc private func ctrlCTapped() {
        // Always sends ^C regardless of current Ctrl modifier state.
        // Long-press is the only way to arm the modifier.
        fire(.ctrlC, ctrlActive: false)
    }

    @objc private func ctrlCLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        ctrl.toggle()
    }

    @objc private func keyTapped(_ sender: ExtraRowButton) {
        guard let output = sender.key.output else { return }
        fire(output, ctrlActive: ctrl.isActive)
        ctrl.consume()
    }

    @objc private func arrowTouchDown(_ sender: ExtraRowButton) {
        guard let output = sender.key.output else { return }
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
        delegate?.extraRowDidTapClipboard(self)
    }

    @objc private func uploadTapped() {
        delegate?.extraRowDidTapUpload(self)
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
                btn.applyBgColor(keyBg)
            }
        }
        applyCtrlVisual(ctrl.state)
    }
}

// MARK: - ExtraRowButton

@MainActor
final class ExtraRowButton: UIButton {

    let key: ExtraRowKey

    init(key: ExtraRowKey, bgColor: UIColor) {
        self.key = key
        super.init(frame: .zero)
        configure(bgColor: bgColor)
    }

    required init?(coder: NSCoder) { fatalError("use init(key:bgColor:)") }

    private func configure(bgColor: UIColor) {
        backgroundColor = bgColor
        setTitleColor(.white, for: .normal)
        setTitleColor(UIColor.white.withAlphaComponent(0.4), for: .highlighted)
        titleLabel?.font = .monospacedSystemFont(ofSize: 13, weight: .semibold)
        setTitle(key.label, for: .normal)
        layer.cornerRadius = 6
        layer.masksToBounds = false
        layer.shadowOpacity = 0
    }

    func applyBgColor(_ color: UIColor) {
        backgroundColor = color
    }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.08) {
                self.alpha = self.isHighlighted ? 0.65 : 1.0
            }
        }
    }
}
