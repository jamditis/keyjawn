import Foundation

// MARK: - Key output

public enum KeyOutput: Sendable, Equatable {
    case character(String)
    case ctrlC                  // dedicated Ctrl+C — most-used LLM CLI key
    case ctrlD
    case escape
    case tab
    case arrowUp
    case arrowDown
    case arrowLeft
    case arrowRight
    case slash                  // triggers slash command popup
    case backspace
    case `return`
    case space
    case send                   // submit in the SSH app; honest newline in the IME
    case newline                // non-submit line break
}

// MARK: - Key definition

public struct Key: Sendable {
    public let label: String
    public let output: KeyOutput
    public let widthWeight: CGFloat  // relative width in its row (1.0 = normal key)

    public init(_ label: String, _ output: KeyOutput, width: CGFloat = 1.0) {
        self.label = label
        self.output = output
        self.widthWeight = width
    }
}

// MARK: - Extra row layout (LLM CLI focused)
//
// Slot order: Ctrl+C | Tab | ▲ | ▼ | ◄ | ► | / | Esc | Clip | SCP
//
// Ctrl+C is first because it's by far the most-used key during LLM sessions
// (interrupt generation). The full Ctrl modifier lives on long-press of Ctrl+C.
// Esc replaces Mic — the system keyboard already has a built-in dictation mic,
// and Esc is absent from the iOS keyboard despite being critical in terminal work.

public enum ExtraRowSlot: Int, CaseIterable, Sendable {
    case ctrlC = 0
    case tab
    case arrowUp
    case arrowDown
    case arrowLeft
    case arrowRight
    case slash
    case escape
    case clipboard
    case upload
    case send
    case letterY
    case letterN
    case letterA
    case digit1
    case digit2
    case digit3
}

public struct ExtraRowKey: Sendable {
    public let slot: ExtraRowSlot
    public let label: String
    public let output: KeyOutput?   // nil for action keys (clipboard, upload)

    /// What VoiceOver announces for this key.
    ///
    /// The visible labels are glyphs and abbreviations chosen to fit a ten-key row —
    /// "▲", "^C", "SCP". Read literally they are useless, so each one gets a spoken
    /// name here.
    public var accessibilityLabel: String {
        switch slot {
        case .ctrlC:      return "Control C. Double tap and hold to lock the Control modifier."
        case .tab:        return "Tab"
        case .arrowUp:    return "Up arrow"
        case .arrowDown:  return "Down arrow"
        case .arrowLeft:  return "Left arrow"
        case .arrowRight: return "Right arrow"
        case .slash:      return "Slash commands"
        case .escape:     return "Escape"
        case .clipboard:  return "Clipboard history"
        case .upload:     return "Upload image over SFTP"
        case .send:       return "Send"
        case .letterY:    return "y"
        case .letterN:    return "n"
        case .letterA:    return "a"
        case .digit1:     return "1"
        case .digit2:     return "2"
        case .digit3:     return "3"
        }
    }

    /// Stable XCUITest identifier. VoiceOver still uses `accessibilityLabel`;
    /// queries must not use the visible glyph (`^C`, `▲`).
    public var accessibilityIdentifier: String { "extra.\(slot)" }

    public static let defaults: [ExtraRowKey] = [
        ExtraRowKey(slot: .ctrlC,      label: "^C",  output: .ctrlC),
        ExtraRowKey(slot: .tab,        label: "Tab", output: .tab),
        ExtraRowKey(slot: .arrowUp,    label: "▲",   output: .arrowUp),
        ExtraRowKey(slot: .arrowDown,  label: "▼",   output: .arrowDown),
        ExtraRowKey(slot: .arrowLeft,  label: "◄",   output: .arrowLeft),
        ExtraRowKey(slot: .arrowRight, label: "►",   output: .arrowRight),
        ExtraRowKey(slot: .slash,      label: "/",   output: .slash),
        ExtraRowKey(slot: .escape,     label: "Esc", output: .escape),
        ExtraRowKey(slot: .clipboard,  label: "Clip",output: nil),
        ExtraRowKey(slot: .upload,     label: "SCP", output: nil),
    ]

    /// Terminal accessory: the default ten keys plus Send. The extension keeps
    /// `defaults` until remappable presets can place Send without crowding
    /// the phone IME row.
    public static let terminalKeys: [ExtraRowKey] = defaults + [
        ExtraRowKey(slot: .send, label: "Send", output: .send),
    ]

    public init(slot: ExtraRowSlot, label: String, output: KeyOutput?) {
        self.slot = slot
        self.label = label
        self.output = output
    }
}

// MARK: - ANSI byte sequences

public enum ANSISequence {
    /// Raw bytes to write into the SSH stream for a given key output.
    /// Returns nil for action keys (mic, clipboard) handled by the UI layer.
    public static func bytes(for output: KeyOutput, ctrlActive: Bool = false) -> [UInt8]? {
        switch output {
        case .ctrlC:                return [0x03]
        case .ctrlD:                return [0x04]
        case .escape:               return [0x1b]
        case .tab:                  return [0x09]
        case .arrowUp:              return ctrlActive ? [0x1b,0x5b,0x31,0x3b,0x35,0x41]
                                                      : [0x1b,0x5b,0x41]
        case .arrowDown:            return ctrlActive ? [0x1b,0x5b,0x31,0x3b,0x35,0x42]
                                                      : [0x1b,0x5b,0x42]
        case .arrowRight:           return ctrlActive ? [0x1b,0x5b,0x31,0x3b,0x35,0x43]
                                                      : [0x1b,0x5b,0x43]
        case .arrowLeft:            return ctrlActive ? [0x1b,0x5b,0x31,0x3b,0x35,0x44]
                                                      : [0x1b,0x5b,0x44]
        case .backspace:            return [0x7f]
        case .return:               return [0x0d]
        case .send:                 return [0x0d]
        case .newline:              return [0x0a]
        case .space:                return [0x20]
        case .character(let s):
            guard let scalar = s.unicodeScalars.first else { return nil }
            let byte = UInt8(scalar.value & 0xFF)
            return ctrlActive ? [byte & 0x1f] : [byte]
        case .slash:                return nil  // handled by slash command popup
        }
    }

    /// The same sequence as a string, for the keyboard extension.
    ///
    /// The extension has no socket to write bytes to — its only channel to the host
    /// app is `UITextDocumentProxy.insertText`. Terminal apps forward inserted text
    /// straight to the pty, which is why Esc has always worked there as a literal
    /// `\u{1b}`; this lets Ctrl+C and the arrow keys reach the shell the same way
    /// instead of being dropped on the floor.
    public static func text(for output: KeyOutput, ctrlActive: Bool = false) -> String? {
        bytes(for: output, ctrlActive: ctrlActive)
            .map { String(decoding: $0, as: UTF8.self) }
    }
}
