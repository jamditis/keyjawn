import Foundation

/// Commit/cancel machine for in-app speech. The recognizer lives in the main
/// app; this type is what tests drive so cancel cannot accidentally insert.
public struct VoiceSession: Sendable, Equatable {
    public private(set) var isListening = false
    public private(set) var transcript = ""

    public init() {}

    public mutating func start() {
        isListening = true
        transcript = ""
    }

    public mutating func updatePartial(_ text: String) {
        guard isListening else { return }
        transcript = text
    }

    /// Final text to write to the PTY, or `nil` when there is nothing to send.
    public mutating func commit() -> String? {
        guard isListening else { return nil }
        isListening = false
        let text = transcript
        transcript = ""
        return text.isEmpty ? nil : text
    }

    /// Stop without returning text.
    public mutating func cancel() {
        isListening = false
        transcript = ""
    }
}
