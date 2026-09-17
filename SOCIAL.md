# KeyJawn social context

This file is the active source for KeyJawn social posts. Update it when a
feature ships or distribution state changes. Do not turn planned, source-only,
or rejected work into a release claim.

## Product

KeyJawn helps people work with terminal-based LLM tools from a phone.

- Android provides a custom keyboard for terminal and agent workflows.
- iOS provides a remote SSH terminal and a companion custom keyboard.
- KeyJawn does not run a local shell or execute downloaded code on iPhone or
  iPad. Commands execute on the remote SSH server that the user configures.

## Distribution state

### Android

- KeyJawn Lite is free through the website and GitHub releases.
- KeyJawn Full costs $4 as a one-time website purchase.
- KeyJawn Lite has active internal and closed Google Play test tracks.
- Google Play production is inactive. Do not call the app publicly available on
  Google Play.

### iOS

- The public TestFlight invitation is not accepting new testers as of August
  28, 2026. Do not publish the invitation URL as an active download path.
- A fresh signed build 9 archive was created and verified from the corrected
  source on August 31, 2026. The exact archive was uploaded, and Apple processed
  build 9 as valid. Build 9 is selected for version 1.0.
- Apple approved version 1.0 on September 17, 2026. App Store Connect reports
  pending developer release because manual release is enabled.
- The app is not publicly available. Do not publish an App Store URL or tell
  people to download it until Apple's public listing is verified.

## Platform features

### Android-only features

- A permanent Esc, Tab, Ctrl, and arrow-key row.
- A three-state Ctrl key with one-shot and locked states.
- Voice input from the keyboard.
- A 30-item clipboard history. The full version adds pinning.
- Slash-command shortcuts.
- Swipe gestures, spacebar cursor movement, backspace acceleration, and
  per-app autocorrect control.
- An adaptive Enter key, double-space period insertion, automatic sentence
  capitalization, and key-specific haptics.
- Full-version SCP image upload, custom command sets, themes, and menu settings.

Do not describe these Android features as iOS keyboard features. In particular,
the iOS microphone flow is in the main app, not the keyboard extension.

### iOS-only features

- A built-in SSH terminal connects to a host that the user configures.
- Terminal input travels over SSH. The remote host executes commands and returns
  output for display.
- The app does not provide a local iOS shell, local command interpreter, device
  file browser, document picker, or file provider.
- Copied-image upload reads one image from the system pasteboard, prepares it in
  memory, and writes a new file to a configured remote directory through SFTP.
  The selected host must use SSH key authentication. The feature does not
  browse local files or list, read, or download remote files.
- The companion keyboard provides terminal-oriented controls and text shortcuts
  in apps and text fields that support third-party keyboards. It inserts text or
  control sequences into the focused field. The receiving app decides how to
  interpret them. The built-in SSH terminal sends terminal bytes directly.
- Basic typing and built-in text shortcuts work without Allow Full Access. Full
  Access is optional for copied-image upload, shared keyboard settings,
  user-created shortcuts, and clipboard history.

iOS controls where a custom keyboard can appear. The system keyboard appears in
passcode and secure text fields, and in fields that use the `phonePad` or
`namePhonePad` keyboard type. Apps can also block third-party keyboards. Do not
say “any app,” “every app,” “system-wide,” or “anywhere.”

## Product angle

Traditional terminal keyboards focus on shell navigation. KeyJawn focuses on
the repeated work of using terminal-based agents: writing prompts, inserting
text shortcuts, reviewing output, moving copied context, and interrupting a
running command.

Useful Android post topics include:

- Insert a common slash command without typing it each time.
- Use one-shot Ctrl for the prompt, interrupt, and re-prompt cycle.
- Dictate a long prompt through the Android keyboard.
- Move the cursor by holding and dragging the Android spacebar.
- Upload an image from Android without leaving the current text workflow.

Useful iOS post topics include:

- Explain the remote SSH boundary: input leaves the device, the server executes
  the command, and output returns over the connection.
- Show the built-in remote terminal.
- Show terminal-oriented controls in a normal text field that supports third-party
  keyboards, and explain that the receiving app decides how to interpret them.
- Explain what works without Allow Full Access.
- Explain copied-image upload without calling it a local file browser.

## Writing rules

- Write as one developer talking to another.
- Use short, direct sentences and active voice.
- State the platform for every platform-specific feature.
- Describe shipped behavior. Do not convert plans or source builds into launch
  claims.
- Do not say that KeyJawn executes code on iOS. The configured remote host
  executes commands.
- Do not say that the iOS app can browse Files, browse device storage, or select
  an upload from Files.
- Do not say that copied-image upload works with a password-authenticated host.
- Do not describe iOS extension controls as hardware key events.
- Do not say that the iOS keyboard appears in every app or field.
- Do not say that Full Access is required for basic typing.
- Do not use hype, fake emotion, rhetorical questions, competitor attacks, or
  hashtag walls.
- Keep posts below 240 characters when the idea fits. Use at most one relevant
  hashtag.
- Put a link at the end when it helps the reader.

## Links

- Website: https://keyjawn.amditis.tech
- GitHub: https://github.com/jamditis/keyjawn
- iOS status: https://keyjawn.amditis.tech/changelog
- Twitter: @keyjawn
