import Foundation

/// Three-state Ctrl modifier matching the Android implementation exactly.
///
/// OFF  → tap → ARMED  (fires one keypress with Ctrl, then resets to OFF)
/// ARMED → tap → LOCKED (sticky until tapped again)
/// LOCKED → tap → OFF
@MainActor
public final class CtrlState {
    public enum State: Sendable {
        case off, armed, locked
    }

    public private(set) var state: State = .off

    /// Called after the state changes so the UI can update.
    public var onChange: ((State) -> Void)?

    public init() {}

    /// Advance the state on a Ctrl button tap.
    public func toggle() {
        switch state {
        case .off:    state = .armed
        case .armed:  state = .locked
        case .locked: state = .off
        }
        onChange?(state)
    }

    /// Consume a single ARMED press — resets to OFF after one key event.
    /// LOCKED state is unaffected.
    public func consume() {
        if state == .armed {
            state = .off
            onChange?(state)
        }
    }

    public var isActive: Bool { state != .off }
}
