# iOS App Review evidence

This file records the code boundary for the guideline 2.5.2 remediation. It is
evidence for maintainers and a source for the next review note. It does not
authorize a build-number change, archive, upload, reply, or resubmission.

## Current review state

- App Store Connect app ID: `6759345867`.
- Review submission ID: `83b2805c-650c-4bf6-91ff-0c6339f36324`.
- Apple sent the current guideline 2.5.2 rejection on June 12, 2026.
- The review message identifies build 2 and an iPad Air 11-inch (M3).
- The checked-in source identifies as build 9. A fresh signed build 9 archive
  was created and verified from the corrected source on August 31, 2026. The
  exact archive was uploaded, and Apple processed build 9 as valid. Build 9 is
  selected for version 1.0, manual release is enabled, and review submission
  `83b2805c-650c-4bf6-91ff-0c6339f36324` is waiting for review. The reviewer
  response and test steps were included in the review notes. The app is not
  approved and not publicly released.
- Before upload, the live App Store Connect API listed builds 1, 2, and 8. The
  user approved unused build number 9, and both targets were updated together.

Apple's rule says apps must be self-contained and must not read or write data
outside their designated container, or download, install, or execute code that
changes app behavior. See [App Review guideline 2.5.2](https://developer.apple.com/app-store/review/guidelines/#software-requirements).
The container restriction applies to local device storage. Copied-image upload
is user-directed network output to a remote host.

## Shipped code boundary

### Remote terminal

- `SSHSession` connects to a host that the user configures.
- The app and keyboard extension use Apple's Network.framework transport through
  SwiftNIO Transport Services. This avoids the physical-iOS connection stall
  reproduced with the POSIX bootstrap.
- Plain SSH remains the default. A per-host TLS tunnel option supports public
  TLS-terminated TCP relays. SSH authentication and host-key verification still
  occur inside the TLS connection. The configured hostname or IP address must
  match the system-trusted TLS certificate. Self-signed certificates are not
  supported for this path. Plain `ssh-keyscan` cannot cross the TLS relay, so
  users review the SSH fingerprint on first connection instead.
- `TerminalInputView` maps text and terminal keys to bytes.
- `TerminalViewController` sends those bytes through the active `SSHSession`.
- The remote SSH server executes commands. KeyJawn displays bytes returned by
  that server.
- The app has no local shell, process API, command interpreter, WebView runtime,
  document picker, document browser, file provider, or security-scoped URL path.
- The old local-echo Preview tab and its session-free terminal branch are not in
  the shipped app.

### Copied-image upload

- The app and keyboard read one image from `UIPasteboard`.
- `PasteboardImagePreparer` downsamples and encodes the image in memory.
- `CitadelSCPUploader` opens an SFTP file with write, create, and truncate flags
  at the configured remote directory. It writes the prepared bytes and closes
  the connection.
- Copied-image upload lists and accepts only hosts configured for SSH key
  authentication. Password authentication remains available for terminal
  connections, but the uploader does not receive or store a password.
- This flow does not list, browse, read, or download remote files.
- This flow does not request Photos access. The user copies an image before
  opening the upload panel.

### Voice input in the built-in terminal

- The main app shows a Mic action in its built-in SSH terminal. The keyboard
  extension does not use the microphone.
- KeyJawn requests speech-recognition and microphone permission only after the
  user taps Mic.
- The main app sends captured audio to Apple's Speech framework for live
  transcription. Apple's speech-recognition process can send voice audio to
  Apple for processing. See Apple's
  [speech-recognition permission documentation](https://developer.apple.com/documentation/speech/asking-permission-to-use-speech-recognition).
- KeyJawn does not store audio recordings or send them to a server operated by
  the developer. It inserts the returned transcription into the active remote
  terminal input.

### Local storage

- Current SSH identity data is in the Keychain access group declared in both
  signed targets.
- Settings, host records, and clipboard history use the entitled App Group suite
  where sharing between the app and keyboard is required.
- The only direct production file read is migration compatibility for the fixed
  file `ssh-identity-ed25519.key` in the entitled `group.com.keyjawn` container.
- The main app reads that fixed file only to migrate an older install to the
  shared Keychain. It deletes the legacy file only after a verified Keychain
  write.
- Production code does not enumerate local directories or accept a local file
  path from the user.

### Keyboard full access and availability

- Basic keyboard input uses `textDocumentProxy` and works without Allow Full
  Access.
- Built-in slash shortcuts insert plain text into the field that has focus and
  work without Full Access. User-created shortcuts use the shared App Group and
  require Full Access in the extension.
- Other extension controls insert text or control sequences through the focused
  field. The receiving app decides how to interpret them. The built-in SSH
  terminal sends terminal bytes directly.
- Full Access is optional. KeyJawn uses it only for optional extension features
  that upload a copied image or read shared keyboard settings, user-created
  shortcuts, and clipboard history.
- `RequestsOpenAccess` remains enabled for those optional extension features.
  See Apple's
  [custom keyboard open-access documentation](https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard).
- `IsASCIICapable` is `true` because the QWERTY layout generates standard ASCII
  characters, as required by Apple's
  [custom keyboard configuration documentation](https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard).
- The keyboard appears only in apps and text fields that allow third-party
  keyboards. iOS uses the system keyboard for passcodes and secure text
  fields, and for fields that use the `phonePad` or `namePhonePad` keyboard
  type. An app can also block custom keyboards. See
  Apple's [custom keyboard documentation](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface).

## Regression evidence

`AppReviewBoundaryTests` is a conservative source-text check tied to the local
checkout. It scans comments and string literals as well as code, so it can reject
a forbidden API name even when the name is not executable. It checks that shipping
source does not add local execution, document access, file-provider,
security-scoped URL, or runtime-loader APIs. It also limits direct filesystem APIs
to the exact legacy migration path, checks that the app has no Photos purpose
key, and checks that target metadata declares no custom URL scheme.

`OnboardingTests`, `TerminalInputMappingTests`, and `LaunchFlowTests` check the
visible remote-execution, local-file, copied-image, and remote-host wording.

Run the focused boundary tests from `ios/`:

```bash
xcodebuild test \
  -project KeyJawn.xcodeproj \
  -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:KeyJawnKitTests/AppReviewBoundaryTests \
  -only-testing:KeyJawnKitTests/OnboardingTests \
  -only-testing:KeyJawnKitTests/TerminalInputMappingTests \
  -only-testing:KeyJawnKitTests/TerminalInputViewTests
```

Run the system-keyboard and launch UI tests on the iPhone and the iPad review
class:

```bash
xcodebuild test \
  -project KeyJawn.xcodeproj \
  -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:KeyJawnUITests
```

The UI suite passed all five tests on iPad Air 11-inch (M3)/iOS 18.5 and iPhone
17 Pro/iOS 26.2. The final screenshot-device run also passed all five tests on
iPad Pro 13-inch (M4)/iOS 17.5 and iPhone 15 Pro Max/iOS 17.5. The keyboard
tests install KeyJawn in Settings, set Full Access explicitly off and on, select
the extension, require its extra row, and insert `hi`. If the iPhone simulator
hides its software keyboard, use Simulator > I/O > Keyboard > Toggle Software
Keyboard or Command-K before the run.

`SSHLiveIntegrationTests` is opt-in. A normal suite checks the KeyJawn identity
format and skips the network test. To enable the network test, provide these
variables to the simulator test process:

- `KEYJAWN_LIVE_SSH_HOST`
- `KEYJAWN_LIVE_SSH_USER`
- `KEYJAWN_LIVE_SSH_PORT`, or omit it for port 22
- `KEYJAWN_LIVE_SSH_HOST_KEY`, in OpenSSH format
- `KEYJAWN_LIVE_SSH_UPLOAD_PATH`, or omit it for `/tmp`
- `KEYJAWN_LIVE_SSH_TLS=1` for a TLS-terminated SSH relay, or `0` for plain SSH

Authorize the simulator's KeyJawn public key on an isolated test account first.
The live test starts without a saved host key, verifies the presented key, opens
a pinned Ed25519-authenticated PTY, checks remote command output, sends a resize,
and uploads a generated payload through `CitadelSCPUploader`. It prints the
success marker only after it reads the exact payload, removes the remote file,
and verifies that the file is absent. It does not print the remote path or
payload token. Remove the temporary authorized key after the run.

The August 27, 2026 live run passed against an isolated review host. The remote payload
matched byte-for-byte. The uploaded file and marked temporary authorized key
were removed after verification. No server credentials or keys are stored in
this repository.

Run the source-text tests in a simulator from the same checkout. Inspect the
built app and extension plists and entitlements after every project or signing
change; the source-text tests do not replace built-product inspection.

Apple says developers do not report data collected only by Apple through Apple
frameworks or services. It also defines Audio Data as voice or sound recordings.
Before submission, compare the current App Privacy answers with the signed voice
implementation and Apple's current
[App Privacy guidance](https://developer.apple.com/app-store/app-privacy-details/).
Do not infer a developer Audio Data collection claim from Apple-only processing,
and do not omit Audio Data if the developer or an integrated third party stores
readable audio beyond the live request.

## Draft App Store copy

The exact product-page copy, review notes, reviewer response, test steps, and
credential-free placeholders are in
[`app-store-v1.0-metadata.md`](app-store-v1.0-metadata.md).

That file is a draft, not a record of an App Store Connect change. Do not use its
reviewer response until an approved revised build is uploaded and selected. Put
review credentials only in protected App Store Connect fields.

## Release gates

Before any resubmission:

- Finish the unit, UI, analysis, formatting, metadata, entitlement, architecture,
  size, and independent-review checks in `tasks/todo.md`.
- Use an isolated SSH account with a restricted writable directory. Verify the
  password login, fresh-install public-key authorization, switch to SSH key
  authentication, voice permission and transcription flow, copied-image upload,
  and cleanup steps without storing credentials in this repository.
- If the review endpoint uses a TLS-terminated TCP relay, enable the per-host
  TLS tunnel option and test from a physical device outside any private VPN path.
- Select an unused build number and change both targets together only after user
  approval.
- Get separate user approval for each archive, upload, App Review reply, metadata
  change, submission cancellation, and resubmission action.
- Website publication is separate from App Store work. A push to `main` that
  changes `website/**` triggers `.github/workflows/deploy-site.yml` and publishes
  the site. Get explicit approval before that push.

### Export compliance and App Privacy publication

The August 28, 2026 source audit found that source evidence alone did not
support the earlier `usesNonExemptEncryption: false` build value:

- Citadel 0.12.0 is linked into the app and keyboard extension.
- KeyJawn does not enable Citadel's optional `SSHAlgorithms.all` set. Its live
  terminal and SFTP connections use the SwiftNIO SSH defaults, which use
  CryptoKit on Apple platforms.
- Citadel still contains BoringSSL-backed AES-128-CTR, Diffie-Hellman group 14,
  and RSA implementations. The optimized Release simulator app and keyboard
  extension binaries contain those symbols after linking.
- The algorithms and the SSH protocol are published industry standards. The
  source audit found no proprietary or unpublished cryptography.

The final signed archive, Apple territory selection, and current Apple guidance
now support `usesNonExemptEncryption: false` for build 9. The final plists keep
`ITSAppUsesNonExemptEncryption` absent, so the answer was saved on the processed
build instead. App Store Connect accepted the value and a read-back confirmed
it. This is the recorded App Store Connect export-compliance determination. No
`ITSEncryptionExportComplianceCode` or encryption declaration is attached.

Apple's current documentation says that an app with industry-standard
encryption not supplied by the operating system needs a French encryption
declaration only if the app is distributed in France. Build 9 is unavailable in
France and available in the other 174 current territories. Apple processed the
uploaded build as valid, and the processed bundle matches the expected app and
extension identifiers, arm64 architecture, iOS 17.0 minimum, distribution
entitlements, App Group, and Keychain group. The App Store Connect encryption
value is false. Confirm the separate US Bureau of Industry and Security
classification or reporting duty with qualified counsel or the Apple
export-compliance team before release.

Sources: [Apple export compliance overview](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance),
[Apple encryption documentation table](https://developer.apple.com/help/app-store-connect/reference/app-information/export-compliance-documentation-for-encryption),
[Apple property-list key reference](https://developer.apple.com/documentation/bundleresources/information-property-list/itsappusesnonexemptencryption),
and [BIS mass-market guidance](https://www.bis.gov/learn-support/encryption-controls/mass-market).

Inspect the current App Privacy answers in the App Store Connect web interface.
Reading the answers does not publish a change. Do not edit the answers or select
Publish without explicit user approval at action time.
