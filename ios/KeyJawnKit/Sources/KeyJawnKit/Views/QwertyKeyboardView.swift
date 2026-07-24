import UIKit

// MARK: - Delegate

@MainActor
public protocol QwertyKeyboardDelegate: AnyObject {
    func keyboard(_ keyboard: QwertyKeyboardView, insertText text: String)
    func keyboardDeleteBackward(_ keyboard: QwertyKeyboardView)
    /// Delete the whole word before the cursor. Sent once per repeat tick after a
    /// held backspace has escalated past character-at-a-time deletion.
    func keyboardDeleteWordBackward(_ keyboard: QwertyKeyboardView)
    func keyboardAdvanceToNextInputMode(_ keyboard: QwertyKeyboardView)
}

public extension QwertyKeyboardDelegate {
    /// Falls back to a single character delete, so a host that has not implemented
    /// word deletion degrades to the old behaviour instead of stalling on a hold.
    func keyboardDeleteWordBackward(_ keyboard: QwertyKeyboardView) {
        keyboardDeleteBackward(keyboard)
    }
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
    // The theme the current buttons were built under. Each QwertyKeyButton captures
    // its theme at init for title and tint colours, so the in-place fast path is only
    // valid while this matches the view's theme. It diverges when theme is set
    // directly (the keyboard extension does qwerty.theme = ... rather than going
    // through applyTheme), and the fast path must fall back to a full rebuild then.
    private var builtTheme: KeyboardTheme?

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

    // Rebuild the key set for the current layer/shift. Reuses the existing buttons in
    // place when the new layer is position-identical in kind and they were built under
    // the current theme; otherwise tears down and reallocates.
    private func rebuild() {
        let rows = KeyboardLayers.rows(for: layer_, shiftState: shiftState)
        let newKeys = rows.flatMap { $0 }

        // Fast path: every lowercase<->uppercase transition (shift off/once/caps and
        // the one-shot-shift after a capital letter) keeps the same key count and the
        // same kind at each position, differing only in per-key title, image, and
        // colour. Relabel the buttons already on screen instead of destroying and
        // reallocating ~35 UIButtons with fresh shadow layers, gesture recognisers,
        // and SF Symbol lookups on every shift -- the churn issue #45 set out to kill.
        // The tap target and long-press recogniser stay attached for the button's
        // lifetime, and because the kind is unchanged a character button stays a
        // character button, so the recogniser stays valid. Skip the fast path when the
        // structure changed (to or from the symbols layer, or the first build) or when
        // the buttons were built under a different theme -- each button keeps the theme
        // it was created with for its title and tint colours, so reusing them after a
        // theme change would leave the old text colour on the new background. Either
        // case degrades to the full rebuild below. Mirrors the Android applyShiftCase
        // fast path (#28).
        if builtTheme == theme,
           keyButtons.count == newKeys.count,
           zip(keyButtons, newKeys).allSatisfy({ $0.key.structuralKind == $1.structuralKind }) {
            for (btn, key) in zip(keyButtons, newKeys) {
                btn.apply(key: key)
                btn.backgroundColor = bgColor(for: key)
            }
            return
        }

        stopBackspaceRepeat()
        keyButtons.forEach { $0.removeFromSuperview() }
        keyButtons.removeAll()
        for key in newKeys {
            let btn = QwertyKeyButton(key: key, theme: theme)
            btn.backgroundColor = bgColor(for: key)
            btn.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
            switch key {
            case .character:
                let lp = UILongPressGestureRecognizer(target: self, action: #selector(keyLongPressed(_:)))
                lp.minimumPressDuration = 0.4
                btn.addGestureRecognizer(lp)
            case .backspace:
                // Holding backspace did nothing at all before this — every deletion
                // cost a separate tap, which is punishing on a keyboard whose whole
                // job is editing long prompts. The recogniser cancels the button's
                // own touch tracking once it fires, so a tap still emits exactly one
                // delete and a hold emits the repeat below instead of both.
                let lp = UILongPressGestureRecognizer(target: self, action: #selector(backspaceLongPressed(_:)))
                lp.minimumPressDuration = 0.35
                btn.addGestureRecognizer(lp)
            default:
                break
            }
            addSubview(btn)
            keyButtons.append(btn)
        }
        builtTheme = theme
    }

    /// Cancel auto-repeat when the keyboard leaves the hierarchy mid-hold, which
    /// touch-up would otherwise never report. Handled here rather than in `deinit`,
    /// which cannot reach main-actor state under Swift 6 strict concurrency.
    public override func willMove(toWindow newWindow: UIWindow?) {
        super.willMove(toWindow: newWindow)
        if newWindow == nil { stopBackspaceRepeat() }
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
        // The third row is always a wide leading modifier, the letters or punctuation,
        // and a wide backspace. `.symbolsToggle` joins the list because it is the key
        // that leads that row on the second symbols page; without it that page's row
        // fell through to the centred branch and rendered a narrow, floating backspace.
        if row.contains(.backspace),
           let first = row.first,
           first == .shift || first == .more || first == .symbolsToggle {
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
        // rebuild() sees builtTheme != theme and does a full reallocation, so the
        // buttons pick up the new title and tint colours rather than being reused.
        rebuild()
        setNeedsLayout()
    }

    // MARK: - Key tap handler

    @objc private func keyTapped(_ sender: QwertyKeyButton) {
        KeyboardHaptics.keyPress()

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
            // Reached from a letter layer and from the second symbols page, and it
            // means the same thing in both places: go to the first symbols page.
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
            // Used to rebuild the layer it was already on, so #+= looked like a
            // broken key and the shell symbols behind it were unreachable.
            layer_      = .symbols2
            shiftState  = .off
            rebuild()
            setNeedsLayout()

        case .globe:
            delegate?.keyboardAdvanceToNextInputMode(self)
        }
    }

    // MARK: - Backspace auto-repeat

    private var backspaceTimer: Timer?
    private var backspaceTicks = 0

    /// Ticks of character-at-a-time deletion before a held backspace switches to whole
    /// words. At the interval below that is a little over a second of holding, which
    /// is long enough to be a decision rather than an accident.
    private static let backspaceWordThreshold = 12
    private static let backspaceRepeatInterval: TimeInterval = 0.09

    @objc private func backspaceLongPressed(_ gr: UILongPressGestureRecognizer) {
        switch gr.state {
        case .began:
            // The recogniser cancelled the button's touch, so the press that started
            // the hold has not deleted anything yet. Emit it here before repeating.
            backspaceTicks = 0
            delegate?.keyboardDeleteBackward(self)
            startBackspaceRepeat()
        case .ended, .cancelled, .failed:
            stopBackspaceRepeat()
        default:
            break
        }
    }

    private func startBackspaceRepeat() {
        backspaceTimer?.invalidate()
        backspaceTimer = Timer.scheduledTimer(
            withTimeInterval: Self.backspaceRepeatInterval,
            repeats: true
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.backspaceTick() }
        }
    }

    private func backspaceTick() {
        backspaceTicks += 1
        // No haptic per tick on purpose: eleven pulses a second reads as a fault.
        if backspaceTicks > Self.backspaceWordThreshold {
            delegate?.keyboardDeleteWordBackward(self)
        } else {
            delegate?.keyboardDeleteBackward(self)
        }
    }

    private func stopBackspaceRepeat() {
        backspaceTimer?.invalidate()
        backspaceTimer = nil
        backspaceTicks = 0
    }

    // MARK: - Long-press alt characters

    @objc private func keyLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began,
              let btn = gr.view as? QwertyKeyButton,
              case .character(let s) = btn.key
        else { return }

        let alts = AltKeyMappings.alts(for: s)
        if alts.isEmpty { return }

        KeyboardHaptics.keyPress()

        if alts.count == 1 {
            delegate?.keyboard(self, insertText: alts[0])
            return
        }

        guard let window = gr.view?.window else { return }
        let popup = AltKeyPopup(alts: alts, theme: theme)
        popup.onSelect = { [weak self] alt in
            guard let self else { return }
            self.delegate?.keyboard(self, insertText: alt)
        }

        let btnFrame = btn.convert(btn.bounds, to: window)
        let popupW = min(CGFloat(alts.count) * 52, window.bounds.width - 16)
        let popupH: CGFloat = 44
        let popupX = max(8, min(btnFrame.midX - popupW / 2, window.bounds.width - popupW - 8))

        // Preferred position is above the key. Clamp it into the window, and flip
        // below the key when there is no room above, so a long-press on the top row
        // (or in landscape, where the keyboard sits close to the top of a short
        // window) does not push the popup off screen. Mirrors the Android fix in #62.
        let minY = window.safeAreaInsets.top + 4
        let maxY = window.bounds.height - window.safeAreaInsets.bottom - popupH - 4
        var popupY = btnFrame.minY - popupH - 8
        if popupY < minY {
            popupY = btnFrame.maxY + 8
        }
        popupY = min(max(popupY, minY), max(minY, maxY))

        popup.frame = CGRect(x: popupX, y: popupY, width: popupW, height: popupH)
        window.addSubview(popup)
        popup.attachDimmer(to: window)
    }
}

// MARK: - Structural kind

extension QwertyKey {
    /// What VoiceOver announces for this key.
    var spokenName: String? {
        switch self {
        case .character(let s):  return s
        case .space:             return "Space"
        case .return:            return "Return"
        case .backspace:         return "Delete. Hold to repeat, keep holding to delete whole words."
        case .shift:             return "Shift"
        case .symbolsToggle:     return "Numbers and symbols"
        case .alphabeticToggle:  return "Letters"
        case .globe:             return "Next keyboard"
        case .more:              return "More symbols"
        }
    }
}

private extension QwertyKey {
    // A discriminator that ignores a character key's specific value, so two layers
    // built from the same sequence of kinds (lowercase and uppercase) compare equal
    // and their buttons can be relabelled in place. Distinct from Equatable, which
    // treats .character("a") and .character("A") as different.
    var structuralKind: Int {
        switch self {
        case .character:        return 0
        case .space:            return 1
        case .return:           return 2
        case .backspace:        return 3
        case .shift:            return 4
        case .symbolsToggle:    return 5
        case .alphabeticToggle: return 6
        case .globe:            return 7
        case .more:             return 8
        }
    }
}

// MARK: - QwertyKeyButton

@MainActor
final class QwertyKeyButton: UIButton {

    private(set) var key: QwertyKey
    private let theme: KeyboardTheme

    // Resolve the three SF Symbols once per process rather than on every rebuild:
    // UIImage(systemName:) is a non-trivial lookup and these images never change.
    private static let backspaceImage = UIImage(systemName: "delete.backward")
    private static let shiftImage = UIImage(systemName: "shift")
    private static let globeImage = UIImage(systemName: "globe")

    init(key: QwertyKey, theme: KeyboardTheme) {
        self.key = key
        self.theme = theme
        super.init(frame: .zero)
        configureChrome()
        apply(key: key)
    }

    required init?(coder: NSCoder) { fatalError("use init(key:theme:)") }

    // One-time visual setup that does not depend on which key this is: title colours
    // and the rounded-rect drop shadow. Set once at init and never touched again, so
    // an in-place relabel does not re-run the four CALayer shadow writes.
    private func configureChrome() {
        let text = theme.keyText
        setTitleColor(text, for: .normal)
        setTitleColor(text.withAlphaComponent(0.4), for: .highlighted)
        layer.cornerRadius    = 5
        layer.masksToBounds   = false
        layer.shadowColor     = UIColor.black.cgColor
        layer.shadowOffset    = CGSize(width: 0, height: 1)
        layer.shadowOpacity   = 0.35
        layer.shadowRadius    = 0.5
    }

    // Per-key appearance: font, title, and image. Safe to call repeatedly to relabel
    // a reused button, which QwertyKeyboardView does on a lowercase<->uppercase
    // toggle. Both the title and the image are cleared first so no stale glyph
    // survives even if a caller ever reuses a button across kinds.
    func apply(key: QwertyKey) {
        self.key = key
        setTitle(nil, for: .normal)
        setImage(nil, for: .normal)
        // The image keys carry no title for VoiceOver to read, and "#+=" and "123"
        // are announced character by character. Name each one instead.
        accessibilityLabel = key.spokenName
        let text = theme.keyText
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
            setImage(Self.backspaceImage, for: .normal)
            tintColor = text

        case .shift:
            setImage(Self.shiftImage, for: .normal)
            tintColor = text

        case .symbolsToggle:
            titleLabel?.font = .systemFont(ofSize: 14, weight: .regular)
            setTitle("123", for: .normal)

        case .alphabeticToggle:
            titleLabel?.font = .systemFont(ofSize: 14, weight: .regular)
            setTitle("ABC", for: .normal)

        case .globe:
            setImage(Self.globeImage, for: .normal)
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
