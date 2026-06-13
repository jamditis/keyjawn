import UIKit

// MARK: - Delegate

@MainActor
public protocol QwertyKeyboardDelegate: AnyObject {
    func keyboard(_ keyboard: QwertyKeyboardView, insertText text: String)
    func keyboardDeleteBackward(_ keyboard: QwertyKeyboardView)
    func keyboardAdvanceToNextInputMode(_ keyboard: QwertyKeyboardView)
}

// MARK: - QwertyKeyboardView

/// Full QWERTY keyboard view for use inside a keyboard extension.
/// Manages three layers (lowercase, uppercase, symbols) and shift state.
/// Does NOT include the extra row — compose that separately above this view.
@MainActor
public final class QwertyKeyboardView: UIView {

    public weak var delegate: QwertyKeyboardDelegate?

    // MARK: State

    private var layer_: KeyboardLayerType = .lowercase  // 'layer' shadows UIView.layer
    private var shiftState: ShiftState = .off

    // MARK: Theme

    public var theme: KeyboardTheme = .dark

    private var bg: UIColor      { theme.keyboardBg }
    private var keyBg: UIColor   { theme.keyBg }
    private var specBg: UIColor  { theme.specKeyBg }
    private var shiftOn: UIColor { theme.armed }

    // MARK: Layout constants

    private let spacingH: CGFloat   = 6
    private let spacingV: CGFloat   = 11
    private let sidePad: CGFloat    = 3
    private let topPad: CGFloat     = 8
    private let bottomPad: CGFloat  = 4

    // MARK: Key views

    private var keyButtons: [QwertyKeyButton] = []

    // MARK: - Init

    public override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = bg
        rebuild()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        backgroundColor = bg
        rebuild()
    }

    // MARK: - Rebuild / layout

    private func rebuild() {
        keyButtons.forEach { $0.removeFromSuperview() }
        keyButtons.removeAll()

        let rows = KeyboardLayers.rows(for: layer_, shiftState: shiftState)
        for row in rows {
            for key in row {
                let btn = QwertyKeyButton(key: key, theme: theme)
                btn.backgroundColor = bgColor(for: key)
                btn.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
                if case .character = key {
                    let lp = UILongPressGestureRecognizer(target: self, action: #selector(keyLongPressed(_:)))
                    lp.minimumPressDuration = 0.4
                    btn.addGestureRecognizer(lp)
                }
                addSubview(btn)
                keyButtons.append(btn)
            }
        }
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        positionKeys()
    }

    private func positionKeys() {
        let rows = KeyboardLayers.rows(for: layer_, shiftState: shiftState)
        let usableW = bounds.width - 2 * sidePad

        // Normal key width is based on a full 10-key row
        let normalW = (usableW - 9 * spacingH) / 10

        let usableH  = bounds.height - topPad - bottomPad
        let rowCount = CGFloat(rows.count)
        let keyH     = (usableH - (rowCount - 1) * spacingV) / rowCount

        var btnIdx = 0

        for (rowIdx, row) in rows.enumerated() {
            let y = topPad + CGFloat(rowIdx) * (keyH + spacingV)

            switch rowType(row) {

            case .equal:
                // Full-width equal keys (row 0, symbols row 1)
                let count = CGFloat(row.count)
                let w = (usableW - (count - 1) * spacingH) / count
                var x = sidePad
                for _ in row {
                    keyButtons[btnIdx].frame = CGRect(x: x, y: y, width: w, height: keyH)
                    x += w + spacingH
                    btnIdx += 1
                }

            case .centeredEqual:
                // Fewer keys than 10 — center them using normal key width (ASDF row)
                let count = CGFloat(row.count)
                let rowW  = count * normalW + (count - 1) * spacingH
                var x = (bounds.width - rowW) / 2
                for _ in row {
                    keyButtons[btnIdx].frame = CGRect(x: x, y: y, width: normalW, height: keyH)
                    x += normalW + spacingH
                    btnIdx += 1
                }

            case .wideSides:
                // First and last keys are wider (shift/more | letters | backspace)
                let midCount  = CGFloat(row.count - 2)
                let wideW     = normalW * 1.5
                let letterW   = (usableW - 2 * wideW - (CGFloat(row.count) - 1) * spacingH) / midCount
                var x = sidePad
                for (j, _) in row.enumerated() {
                    let w: CGFloat = (j == 0 || j == row.count - 1) ? wideW : letterW
                    keyButtons[btnIdx].frame = CGRect(x: x, y: y, width: w, height: keyH)
                    x += w + spacingH
                    btnIdx += 1
                }

            case .bottomBar:
                // 123/ABC | Globe | Space (fills) | Return
                let w123    = usableW * 0.13
                let wGlobe  = usableW * 0.10
                let wReturn = usableW * 0.22
                let wSpace  = usableW - w123 - wGlobe - wReturn - 3 * spacingH
                let widths  = [w123, wGlobe, wSpace, wReturn]
                var x = sidePad
                for (j, _) in row.enumerated() {
                    let w = j < widths.count ? widths[j] : normalW
                    keyButtons[btnIdx].frame = CGRect(x: x, y: y, width: w, height: keyH)
                    x += w + spacingH
                    btnIdx += 1
                }
            }
        }
    }

    // MARK: - Row type detection

    private enum RowType { case equal, centeredEqual, wideSides, bottomBar }

    private func rowType(_ row: [QwertyKey]) -> RowType {
        if row.contains(.space)     { return .bottomBar }
        if row.contains(.backspace) && (row.first == .shift || row.first == .more) {
            return .wideSides
        }
        // If fewer keys than a full row, center them; otherwise fill equally
        let fullRowCount = 10
        return row.count < fullRowCount ? .centeredEqual : .equal
    }

    // MARK: - Colours

    private func bgColor(for key: QwertyKey) -> UIColor {
        switch key {
        case .character:    return keyBg
        case .space:        return keyBg
        case .shift:
            switch shiftState {
            case .off:           return specBg
            case .once, .caps:   return shiftOn
            }
        default:            return specBg
        }
    }

    // MARK: - Theme

    public func applyTheme(_ theme: KeyboardTheme) {
        self.theme = theme
        backgroundColor = bg
        rebuild()
        setNeedsLayout()
    }

    // MARK: - Key tap handler

    @objc private func keyTapped(_ sender: QwertyKeyButton) {
        switch sender.key {

        case .character(let s):
            delegate?.keyboard(self, insertText: s)
            if shiftState == .once {
                shiftState = .off
                layer_     = .lowercase
                rebuild()
                setNeedsLayout()
            }

        case .space:
            delegate?.keyboard(self, insertText: " ")

        case .return:
            delegate?.keyboard(self, insertText: "\n")

        case .backspace:
            delegate?.keyboardDeleteBackward(self)

        case .shift:
            switch shiftState {
            case .off:   shiftState = .once;  layer_ = .uppercase
            case .once:  shiftState = .caps;  layer_ = .uppercase
            case .caps:  shiftState = .off;   layer_ = .lowercase
            }
            rebuild()
            setNeedsLayout()

        case .symbolsToggle:
            layer_      = .symbols
            shiftState  = .off
            rebuild()
            setNeedsLayout()

        case .alphabeticToggle:
            layer_      = .lowercase
            shiftState  = .off
            rebuild()
            setNeedsLayout()

        case .more:
            // Cycle symbols → back to symbols for now (extend later)
            rebuild()
            setNeedsLayout()

        case .globe:
            delegate?.keyboardAdvanceToNextInputMode(self)
        }
    }

    // MARK: - Long-press alt characters

    @objc private func keyLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began,
              let btn = gr.view as? QwertyKeyButton,
              case .character(let s) = btn.key
        else { return }

        let alts = AltKeyMappings.alts(for: s)
        if alts.isEmpty { return }

        if alts.count == 1 {
            delegate?.keyboard(self, insertText: alts[0])
            return
        }

        guard let window = gr.view?.window else { return }
        let popup = AltKeyPopup(alts: alts)
        popup.onSelect = { [weak self] alt in
            guard let self else { return }
            self.delegate?.keyboard(self, insertText: alt)
        }

        let btnFrame = btn.convert(btn.bounds, to: window)
        let popupW = min(CGFloat(alts.count) * 52, window.bounds.width - 16)
        let popupX = max(8, min(btnFrame.midX - popupW / 2, window.bounds.width - popupW - 8))
        let popupY = btnFrame.minY - 52
        popup.frame = CGRect(x: popupX, y: popupY, width: popupW, height: 44)
        window.addSubview(popup)
        popup.attachDimmer(to: window)
    }
}

// MARK: - QwertyKeyButton

@MainActor
final class QwertyKeyButton: UIButton {

    let key: QwertyKey
    private let theme: KeyboardTheme

    init(key: QwertyKey, theme: KeyboardTheme) {
        self.key = key
        self.theme = theme
        super.init(frame: .zero)
        configure()
    }

    required init?(coder: NSCoder) { fatalError("use init(key:theme:)") }

    private func configure() {
        let text = theme.keyText
        let textFaded = text.withAlphaComponent(0.4)
        setTitleColor(text, for: .normal)
        setTitleColor(textFaded, for: .highlighted)
        layer.cornerRadius    = 5
        layer.masksToBounds   = false
        layer.shadowColor     = UIColor.black.cgColor
        layer.shadowOffset    = CGSize(width: 0, height: 1)
        layer.shadowOpacity   = 0.35
        layer.shadowRadius    = 0.5

        switch key {
        case .character(let s):
            titleLabel?.font = .systemFont(ofSize: 17, weight: .light)
            setTitle(s, for: .normal)

        case .space:
            titleLabel?.font = .systemFont(ofSize: 15, weight: .regular)
            setTitle("space", for: .normal)

        case .return:
            titleLabel?.font = .systemFont(ofSize: 15, weight: .regular)
            setTitle("return", for: .normal)

        case .backspace:
            setImage(UIImage(systemName: "delete.backward"), for: .normal)
            tintColor = text

        case .shift:
            setImage(UIImage(systemName: "shift"), for: .normal)
            tintColor = text

        case .symbolsToggle:
            titleLabel?.font = .systemFont(ofSize: 14, weight: .regular)
            setTitle("123", for: .normal)

        case .alphabeticToggle:
            titleLabel?.font = .systemFont(ofSize: 14, weight: .regular)
            setTitle("ABC", for: .normal)

        case .globe:
            setImage(UIImage(systemName: "globe"), for: .normal)
            tintColor = text

        case .more:
            titleLabel?.font = .systemFont(ofSize: 13, weight: .regular)
            setTitle("#+=", for: .normal)
        }
    }

    override var isHighlighted: Bool {
        didSet {
            UIView.animate(withDuration: 0.06) {
                self.alpha = self.isHighlighted ? 0.6 : 1.0
            }
        }
    }
}
