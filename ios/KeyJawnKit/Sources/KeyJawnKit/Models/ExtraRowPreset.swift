import Foundation

/// Named extra-row layouts stored in the App Group suite.
///
/// Agent is today's default ten-key row. Confirm is the one-tap answer row
/// for agent prompts (y/n/a/1/2/3 plus submit and Esc). Per-slot remapping
/// is a later issue.
public enum ExtraRowPreset: String, Sendable, CaseIterable, Equatable {
    case agent
    case confirm

    public var displayName: String {
        switch self {
        case .agent: return "Agent"
        case .confirm: return "Confirm"
        }
    }

    /// Keys this preset shows. Agent round-trips to `ExtraRowKey.defaults`.
    public var keys: [ExtraRowKey] {
        switch self {
        case .agent:
            return ExtraRowKey.defaults
        case .confirm:
            return [
                ExtraRowKey(slot: .letterY, label: "y", output: .character("y")),
                ExtraRowKey(slot: .letterN, label: "n", output: .character("n")),
                ExtraRowKey(slot: .letterA, label: "a", output: .character("a")),
                ExtraRowKey(slot: .digit1, label: "1", output: .character("1")),
                ExtraRowKey(slot: .digit2, label: "2", output: .character("2")),
                ExtraRowKey(slot: .digit3, label: "3", output: .character("3")),
                ExtraRowKey(slot: .send, label: "Send", output: .send),
                ExtraRowKey(slot: .escape, label: "Esc", output: .escape),
                ExtraRowKey(slot: .ctrlC, label: "^C", output: .ctrlC),
            ]
        }
    }

    /// Terminal accessory keeps Send on Agent so a connected session can still
    /// submit without hunting the system Return. The encoded preset is still
    /// `agent` and `keys` stays `ExtraRowKey.defaults`.
    public var terminalKeys: [ExtraRowKey] {
        switch self {
        case .agent:
            return ExtraRowKey.terminalKeys
        case .confirm:
            return keys
        }
    }

    public var encoded: String { rawValue }

    public static func decode(_ raw: String?) -> ExtraRowPreset {
        raw.flatMap(ExtraRowPreset.init(rawValue:)) ?? .agent
    }
}
