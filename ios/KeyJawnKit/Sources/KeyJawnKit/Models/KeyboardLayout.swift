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
}

public struct ExtraRowKey: Sendable {
    public let slot: ExtraRowSlot
    public let label: String
    public let output: KeyOutput?   // nil for action keys (clipboard, upload)

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
        case .space:                return [0x20]
        case .character(let s):
            guard let scalar = s.unicodeScalars.first else { return nil }
            let byte = UInt8(scalar.value & 0xFF)
            return ctrlActive ? [byte & 0x1f] : [byte]
        case .slash:                return nil  // handled by slash command popup
        }
    }
}
