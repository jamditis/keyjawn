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

    /// Cached rather than read per press.
    ///
    /// `KeyboardPrefs` reopens the App Group suite on every access so a preference
    /// changed in the main app is not served stale to a long-lived extension process.
    /// That is the right trade at activation scope and the wrong one on the keystroke
    /// path, where it would put a `UserDefaults` construction between the finger and
    /// the character. Refreshed on appearance instead, which is the only moment the
    /// value can have changed — the Settings screen is in the other process, so it
    /// cannot be reached without leaving the keyboard.
    private static var isEnabled = true

    /// Re-read the preference and warm the Taptic Engine so the first press of a
    /// session is not late. Call when the keyboard or the terminal appears.
    public static func refresh() {
        isEnabled = KeyboardPrefs.shared.hapticsEnabled
        if isEnabled { generator.prepare() }
    }

    /// Feedback for a single deliberate key press.
    ///
    /// Deliberately not called from auto-repeat ticks — a held backspace firing this
    /// eleven times a second reads as a malfunction rather than as feedback.
    public static func keyPress() {
        guard isEnabled else { return }
        UIDevice.current.playInputClick()
        generator.impactOccurred(intensity: 0.55)
    }
}
