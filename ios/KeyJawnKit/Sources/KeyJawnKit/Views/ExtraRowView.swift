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
}

// MARK: - ExtraRowView

/// Horizontal bar of terminal keys designed for LLM CLI workflows.
///
/// Layout: ^C | Tab | ▲ | ▼ | ◄ | ► | / | Esc | Clip
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

    // MARK: - Colours

    private static let bg       = UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)
    private static let keyBg    = UIColor(red: 0.227, green: 0.227, blue: 0.227, alpha: 1)
    private static let armed    = UIColor(red: 0.267, green: 0.467, blue: 0.800, alpha: 1)
    private static let locked   = UIColor(red: 0.800, green: 0.267, blue: 0.267, alpha: 1)

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
        backgroundColor = Self.bg

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
            let btn = ExtraRowButton(key: key)
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

        default:
            // Tab, slash: single tap
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
            self?.repeatTimer = Timer.scheduledTimer(
                withTimeInterval: 0.08,
                repeats: true
            ) { [weak self, output] _ in
                self?.fire(output, ctrlActive: false) // repeat never carries Ctrl
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

    // MARK: - Helpers

    private func fire(_ output: KeyOutput, ctrlActive: Bool) {
        delegate?.extraRow(self, send: output, ctrlActive: ctrlActive)
    }

    // MARK: - Ctrl visual state

    private func applyCtrlVisual(_ state: CtrlState.State) {
        guard let btn = ctrlCButton else { return }
        UIView.animate(withDuration: 0.15) {
            switch state {
            case .off:
                btn.backgroundColor = Self.keyBg
                btn.layer.shadowOpacity = 0
            case .armed:
                btn.backgroundColor = Self.armed
                btn.layer.shadowColor = Self.armed.withAlphaComponent(0.5).cgColor
                btn.layer.shadowOpacity = 1
                btn.layer.shadowRadius = 8
                btn.layer.shadowOffset = .zero
            case .locked:
                btn.backgroundColor = Self.locked
                btn.layer.shadowColor = Self.locked.withAlphaComponent(0.5).cgColor
                btn.layer.shadowOpacity = 1
                btn.layer.shadowRadius = 8
                btn.layer.shadowOffset = .zero
            }
        }
    }
}

// MARK: - ExtraRowButton

@MainActor
final class ExtraRowButton: UIButton {

    let key: ExtraRowKey

    init(key: ExtraRowKey) {
        self.key = key
        super.init(frame: .zero)
        configure()
    }

    required init?(coder: NSCoder) { fatalError("use init(key:)") }

    private func configure() {
        backgroundColor = UIColor(red: 0.227, green: 0.227, blue: 0.227, alpha: 1)
        setTitleColor(.white, for: .normal)
        setTitleColor(UIColor.white.withAlphaComponent(0.4), for: .highlighted)
        titleLabel?.font = .monospacedSystemFont(ofSize: 13, weight: .semibold)
        setTitle(key.label, for: .normal)
        layer.cornerRadius = 6
        layer.masksToBounds = false
        layer.shadowOpacity = 0
    }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.08) {
                self.alpha = self.isHighlighted ? 0.65 : 1.0
            }
        }
    }
}
