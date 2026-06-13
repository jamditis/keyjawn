import UIKit

/// Horizontal popup showing one button per alt character.
/// The caller is responsible for positioning the popup's frame before adding it to the window.
/// On selection or dismissal the popup removes itself (and the dimmer) from the view hierarchy.
@MainActor
public final class AltKeyPopup: UIView {

    /// Called with the selected alt string, then the popup removes itself from the superview.
    public var onSelect: ((String) -> Void)?

    /// Called when the user taps outside the popup. The popup removes itself from the superview.
    public var onDismiss: (() -> Void)?

    // Full-screen dimmer added to the window so taps outside close the popup.
    private weak var dimmer: UIView?

    // MARK: - Init

    public init(alts: [String]) {
        super.init(frame: .zero)
        backgroundColor = UIColor(red: 0.15, green: 0.15, blue: 0.15, alpha: 1)
        layer.cornerRadius = 8
        layer.masksToBounds = true

        buildButtons(alts: alts)
    }

    required init?(coder: NSCoder) { fatalError("use init(alts:)") }

    // MARK: - Build

    private func buildButtons(alts: [String]) {
        for (index, alt) in alts.enumerated() {
            let btn = UIButton(type: .system)
            btn.setTitle(alt, for: .normal)
            btn.setTitleColor(.white, for: .normal)
            btn.titleLabel?.font = UIFont.monospacedSystemFont(ofSize: 16, weight: .regular)
            btn.tag = index
            btn.addTarget(self, action: #selector(altTapped(_:)), for: .touchUpInside)
            addSubview(btn)
        }
    }

    // MARK: - Layout

    public override func layoutSubviews() {
        super.layoutSubviews()
        let count = subviews.count
        guard count > 0 else { return }
        let btnW = bounds.width / CGFloat(count)
        for (i, btn) in subviews.enumerated() {
            btn.frame = CGRect(x: CGFloat(i) * btnW, y: 0, width: btnW, height: bounds.height)
        }
    }

    // MARK: - Window attachment

    /// Attaches a full-screen dimmer behind this popup on the given window.
    /// Call this after setting the popup's frame and adding it to the window.
    public func attachDimmer(to window: UIWindow) {
        let dim = UIView(frame: window.bounds)
        dim.backgroundColor = .clear
        let tap = UITapGestureRecognizer(target: self, action: #selector(dimmerTapped))
        dim.addGestureRecognizer(tap)
        window.insertSubview(dim, belowSubview: self)
        dimmer = dim
    }

    // MARK: - Actions

    @objc private func altTapped(_ sender: UIButton) {
        guard let title = sender.title(for: .normal) else { return }
        let callback = onSelect
        teardown()
        callback?(title)
    }

    @objc private func dimmerTapped() {
        let callback = onDismiss
        teardown()
        callback?()
    }

    // MARK: - Teardown

    private func teardown() {
        dimmer?.removeFromSuperview()
        dimmer = nil
        removeFromSuperview()
    }
}
