import UIKit

/// Opts the keyboard's input view into system key click feedback.
///
/// `UIDevice.playInputClick()` is the only feedback channel available to a keyboard
/// extension — `UIImpactFeedbackGenerator` is inert there — and it is ignored unless
/// the enclosing input view declares that it wants clicks. Declaring it on
/// `UIInputView` covers the view `UIInputViewController` vends without having to
/// replace the controller's view hierarchy.
///
/// The system still respects the user's own Keyboard Feedback settings on top of
/// this, and `KeyboardHaptics` gates it on the app's own preference, so returning
/// true unconditionally here does not force feedback on anyone.
extension UIInputView: @retroactive UIInputViewAudioFeedback {
    public var enableInputClicksWhenVisible: Bool { true }
}
