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

    private static let bg     = UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)
    private static let keyBg  = UIColor(white: 0.27, alpha: 1)

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
        backgroundColor = Self.bg
        for label in labels {
            let btn = UIButton(type: .custom)
            btn.setTitle(label, for: .normal)
            btn.setTitleColor(.white, for: .normal)
            btn.titleLabel?.font = UIFont.monospacedSystemFont(ofSize: 16, weight: .light)
            btn.backgroundColor = Self.keyBg
            btn.layer.cornerRadius = cornerRadius
            btn.layer.masksToBounds = true
            btn.addTarget(self, action: #selector(digitTapped(_:)), for: .touchUpInside)

            let lp = UILongPressGestureRecognizer(target: self, action: #selector(digitLongPressed(_:)))
            lp.minimumPressDuration = 0.4
            btn.addGestureRecognizer(lp)

            addSubview(btn)
            buttons.append(btn)
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
        delegate?.numberRow(self, insertText: label)
    }

    @objc private func digitLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began,
              let btn = gr.view as? UIButton,
              let label = btn.title(for: .normal),
              let shifted = AltKeyMappings.numberShifts[label]
        else { return }
        delegate?.numberRow(self, insertText: shifted)
    }
}
