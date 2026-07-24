import UIKit

// MARK: - Delegate

@MainActor
public protocol NumberRowDelegate: AnyObject {
    func numberRow(_ view: NumberRowView, insertText text: String)
}

// MARK: - NumberRowView

/// A row of 10 equal-width digit keys (1–9, 0) with long-press shifted symbols.
@MainActor
public final class NumberRowView: UIView {

    public weak var delegate: NumberRowDelegate?

    /// Colours for the row. This used to be two hardcoded dark greys, which left a
    /// band of the wrong colour across the middle of the keyboard under every theme
    /// but Dark. Set it through ``applyTheme(_:)`` so the existing keys recolour.
    public private(set) var theme: KeyboardTheme = .dark

    private let labels = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
    private var buttons: [UIButton] = []

    private let sidePad: CGFloat  = 4
    private let spacing: CGFloat  = 4
    private let cornerRadius: CGFloat = 5

    // MARK: - Init

    public override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    // MARK: - Setup

    private func setup() {
        backgroundColor = theme.keyboardBg
        for label in labels {
            let btn = UIButton(type: .custom)
            btn.setTitle(label, for: .normal)
            btn.titleLabel?.font = UIFont.monospacedSystemFont(ofSize: 16, weight: .light)
            btn.layer.cornerRadius = cornerRadius
            btn.layer.masksToBounds = true
            btn.addTarget(self, action: #selector(digitTapped(_:)), for: .touchUpInside)

            let lp = UILongPressGestureRecognizer(target: self, action: #selector(digitLongPressed(_:)))
            lp.minimumPressDuration = 0.4
            btn.addGestureRecognizer(lp)

            addSubview(btn)
            buttons.append(btn)
        }
        applyThemeColors()
    }

    // MARK: - Theme

    public func applyTheme(_ theme: KeyboardTheme) {
        self.theme = theme
        applyThemeColors()
    }

    private func applyThemeColors() {
        backgroundColor = theme.keyboardBg
        for btn in buttons {
            btn.backgroundColor = theme.keyBg
            btn.setTitleColor(theme.keyText, for: .normal)
            btn.setTitleColor(theme.keyText.withAlphaComponent(0.4), for: .highlighted)
        }
    }

    // MARK: - Intrinsic size and layout

    public override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 42)
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, !buttons.isEmpty else { return }

        let count = CGFloat(buttons.count)
        let totalSpacing = (count - 1) * spacing + 2 * sidePad
        let keyW = (bounds.width - totalSpacing) / count
        let keyH = bounds.height

        for (i, btn) in buttons.enumerated() {
            let x = sidePad + CGFloat(i) * (keyW + spacing)
            btn.frame = CGRect(x: x, y: 0, width: keyW, height: keyH)
        }
    }

    // MARK: - Actions

    @objc private func digitTapped(_ sender: UIButton) {
        guard let label = sender.title(for: .normal) else { return }
        KeyboardHaptics.keyPress()
        delegate?.numberRow(self, insertText: label)
    }

    @objc private func digitLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began,
              let btn = gr.view as? UIButton,
              let label = btn.title(for: .normal),
              let shifted = AltKeyMappings.numberShifts[label]
        else { return }
        KeyboardHaptics.keyPress()
        delegate?.numberRow(self, insertText: shifted)
    }
}
