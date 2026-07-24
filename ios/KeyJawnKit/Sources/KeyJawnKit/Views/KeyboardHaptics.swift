import UIKit

/// Key press feedback, shared by every key surface.
///
/// Two mechanisms are fired because neither one works in both contexts:
///
/// - `UIDevice.playInputClick()` is the only feedback route a keyboard extension
///   has. It produces the system keyboard click, and on iOS 16+ the system keyboard
///   haptic when the user has that enabled. It is silently ignored unless the
///   surrounding input view opts in via `UIInputViewAudioFeedback`, which the
///   extension does; outside an input view it is a no-op.
/// - `UIImpactFeedbackGenerator` is what works in the main app's terminal, where the
///   extra row is an `inputAccessoryView` rather than an input view.
///
/// Each is inert in the other's context, so calling both is safe and keeps callers
/// from having to know which one they are running in.
@MainActor
public enum KeyboardHaptics {

    private static let generator = UIImpactFeedbackGenerator(style: .light)

    /// Warms the Taptic Engine so the first press of a session is not late.
    /// Cheap to call repeatedly; safe to call when feedback is disabled.
    public static func prepare() {
        guard KeyboardPrefs.shared.hapticsEnabled else { return }
        generator.prepare()
    }

    /// Feedback for a single deliberate key press.
    ///
    /// Deliberately not called from auto-repeat ticks — a held backspace firing this
    /// eleven times a second reads as a malfunction rather than as feedback.
    public static func keyPress() {
        guard KeyboardPrefs.shared.hapticsEnabled else { return }
        UIDevice.current.playInputClick()
        generator.impactOccurred(intensity: 0.55)
    }
}
