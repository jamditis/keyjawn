import Foundation

/// User-visible first-launch copy for the containing app.
///
/// KeyJawn is a mobile keyboard. The screens explain how to turn that keyboard on.
/// The built-in SSH terminal is mentioned only as the app you use after the
/// keyboard is enabled, not as a second product.
///
/// Kept as data so tests can assert the required topics and the absence of
/// third-party tool names without instantiating SwiftUI.
public enum OnboardingCopy: Sendable {

    public struct Page: Sendable, Equatable {
        public let title: String
        public let body: String

        public init(title: String, body: String) {
            self.title = title
            self.body = body
        }
    }

    public static let pages: [Page] = [whatItIs, enableKeyboard, addAHost]

    public static let whatItIs = Page(
        title: "A keyboard for CLI agents",
        body: """
        KeyJawn is a phone keyboard with the extra keys a CLI agent session needs: Esc, Tab, Ctrl, arrows, and text shortcuts.

        Slash shortcuts insert plain text into the field that has focus. They are not links to other apps.

        This app also has a built-in SSH terminal so those keys can reach a shell on iOS, where a keyboard extension cannot send them as real key events.
        """
    )

    public static let enableKeyboard = Page(
        title: "Turn on the keyboard",
        body: """
        1. Open Settings, then General, then Keyboard, then Keyboards.
        2. Tap Add new keyboard and choose KeyJawn Keyboard.
        3. Basic typing works without Full Access.
        4. Grant Full Access only if you want themes, clipboard history, and image upload to read shared settings.

        The keyboard cannot open that screen for you. Use Open Settings below, then follow the steps. Full Access is a system permission, not something KeyJawn can grant itself.
        """
    )

    public static let addAHost = Page(
        title: "Add a host and copy your key",
        body: """
        After this screen, use the Hosts tab to add an SSH server.

        Settings, then SSH keys, shows the public key to paste into authorized_keys on that server. The first connection asks you to pin the host key fingerprint.
        """
    )

    public static let skipTitle = "Skip"
    public static let continueTitle = "Continue"
    public static let doneTitle = "Done"
    public static let openSettingsTitle = "Open Settings"
    public static let reopenTitle = "Set up keyboard"

    /// Tokens that must not appear in user-visible onboarding copy.
    /// Lowercased for comparison. Kept next to the pages so a new page cannot
    /// silently reintroduce a 3.2.2-style tool name.
    public static let forbiddenTokens: [String] = [
        "claude",
        "gemini",
        "codex",
        "aider",
        "anthropic",
        "openai",
        "moshi",
        "blink",
        "termius",
    ]

    public static var allUserVisibleText: String {
        let pageText = pages.map { $0.title + "\n" + $0.body }.joined(separator: "\n")
        return [
            pageText,
            skipTitle,
            continueTitle,
            doneTitle,
            openSettingsTitle,
            reopenTitle,
        ].joined(separator: "\n")
    }
}
