# KeyJawn: custom mobile keyboard for LLM CLI use

![KeyJawn branding graphic](https://i.imgur.com/c6z2Gl0.jpeg)

[![Build](https://github.com/jamditis/keyjawn/actions/workflows/build.yml/badge.svg)](https://github.com/jamditis/keyjawn/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-6cf2a8?style=flat)](LICENSE)
[![Android](https://img.shields.io/badge/android-8.0%2B-6cf2a8?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-17.0%2B-6cf2a8?style=flat&logo=apple&logoColor=white)](https://developer.apple.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/swift-6.0-F05138?style=flat&logo=swift&logoColor=white)](https://swift.org)
[![GitHub release](https://img.shields.io/github/v/release/jamditis/keyjawn?color=6cf2a8&style=flat)](https://github.com/jamditis/keyjawn/releases)
[![Site](https://img.shields.io/badge/site-keyjawn.amditis.tech-6cf2a8?style=flat)](https://keyjawn.amditis.tech)

A mobile keyboard for using LLM CLI agents from a phone. KeyJawn includes an
Android keyboard and an iOS remote SSH terminal with a companion keyboard.

## Install

### Android

**Free version**: Download the lite APK from [GitHub releases](https://github.com/jamditis/keyjawn/releases). Includes voice input, clipboard history, slash commands, swipe gestures, and per-app autocorrect.

KeyJawn Lite also has active internal and closed Google Play test tracks.
Google Play production is not active.

**Full version ($4)**: Buy on [the website](https://keyjawn.amditis.tech) via Stripe. After purchase, you'll get an email with a private download link (expires in 7 days). New versions are emailed automatically.

After installing:

1. Go to **Settings > System > Languages & input > On-screen keyboard**
2. Enable **KeyJawn**
3. Set KeyJawn as your default keyboard

### iOS

The iOS app connects to remote SSH servers. Commands run on the configured server,
and the app displays the returned output. It does not run a local shell or browse
files on the iPhone or iPad. Copied-image upload writes the prepared image to a
configured path on a remote host that uses SSH key authentication. The upload
reads only the image that the user copied to the system pasteboard. It does not
open Photos or Files.

Normal hosts use plain SSH. A host that exposes SSH through a TLS-terminated TCP
tunnel can enable **TLS tunnel** in its host settings. The app still verifies the
SSH host key inside that TLS connection. Use a DNS hostname that matches the TLS
certificate, or an IP address that matches an IP-address certificate entry.
Self-signed certificates are not supported for TLS tunnels. Review the SSH
fingerprint on first connection because plain `ssh-keyscan` cannot cross a TLS
tunnel by itself.

A companion keyboard extension provides QWERTY input, terminal-oriented controls,
and text shortcuts in apps and text fields that support third-party keyboards.
The extension inserts text or control sequences into the focused field. The
receiving app decides how to interpret them. The built-in SSH terminal sends
terminal bytes directly. Basic typing works without Allow Full Access. Full
Access is optional. KeyJawn uses it only for copied-image upload and shared
keyboard settings, user-created shortcuts, or clipboard history.

iOS uses the system keyboard for passcodes and secure text fields, and for
fields that use the `phonePad` or `namePhonePad` keyboard type. Apps can also
block third-party keyboards.

Apple approved version 1.0 on September 17, 2026. Build 9 is selected, valid,
and configured for manual release. App Store Connect reports pending developer
release. The app is not publicly available until the developer release occurs
and Apple publishes the listing.

To use the keyboard extension in other apps after installing:

1. Go to **Settings > General > Keyboard > Keyboards > Add new keyboard**
2. Select **KeyJawn Keyboard**

## Android features

The features in this section are Android-only unless the text says otherwise.

### Android free version

- QWERTY keyboard with three layers (lowercase, uppercase, symbols)
- Terminal key row: Esc, Tab, Ctrl (three-state toggle), arrow keys
- Number row with shift-symbol hints (long-press for shifted symbols)
- Alt character popups on long-press (accented letters, punctuation variants)
- Voice input with streaming transcription
- Clipboard history (30 items)
- Slash command shortcuts (built-in sets)
- Swipe gestures (delete word, space, layer switching)
- Per-app autocorrect toggle (long-press spacebar)
- Configurable quick key (bottom row, defaults to `/`)
- Color-coded extra row keys for quick identification
- Shift / caps lock with visual state indicator

### Android full version ($4 one-time purchase)

Everything in free, plus:
- SCP image upload to remote SSH servers
- Multi-host SSH management with encrypted credentials (AES-256)
- Custom slash command sets
- Keyboard color themes (Dark, Light, OLED black, Terminal)
- Menu panel with inline settings
- Clipboard pinning (persistent across sessions)
- Tooltip toggle

## Who it is for

Anyone who uses a remote server from a phone to work with a CLI-based AI
assistant. These integration examples are Android-only:
- Claude Code via Cockpit web terminal
- Direct SSH apps (Termux, JuiceSSH, ConnectBot)
- Any LLM CLI that uses slash commands (OpenClaw, etc.)

## Android versions

| | Full | Lite |
|---|---|---|
| Package | `com.keyjawn` | `com.keyjawn.lite` |
| Price | $4 (website) | Free |
| SCP upload | Yes | No |
| Color themes | Yes | No |
| Clipboard pinning | Yes | No |
| Custom slash commands | Yes | No |
| Permissions | INTERNET, READ_MEDIA_IMAGES, RECORD_AUDIO | RECORD_AUDIO only |
| Distribution | Email after purchase | GitHub releases |

## Build

### Android

```bash
# Debug builds
./gradlew assembleFullDebug
./gradlew assembleLiteDebug

# Release builds (requires signing config)
./gradlew assembleFullRelease bundleFullRelease
./gradlew bundleLiteRelease

# Tests
./gradlew testFullDebugUnitTest
./gradlew testLiteDebugUnitTest

# Static checks
./gradlew lintFullDebug lintLiteDebug
```

Requires JDK 17+ and Android SDK 36. The Gradle wrapper provisions its pinned JDK 21 daemon toolchain.

The current App Store screenshot inventory and validation rules are in
[`ios/AppStore/Screenshots/README.md`](ios/AppStore/Screenshots/README.md).

### iOS

```bash
cd ios

# Generate Xcode project (after editing project.yml)
xcodegen generate

# Build for simulator
xcodebuild -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Debug build

# Run unit tests
xcodebuild test -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

Requires Xcode 26+, Swift 6, and an iOS 17+ deployment target. XcodeGen manages
the project file. Edit `project.yml`, not the `.xcodeproj` directly. Archive,
upload, metadata, reviewer reply, and resubmission actions require separate user
approval.

## Links

- [Website](https://keyjawn.amditis.tech)
- [iOS release status](https://keyjawn.amditis.tech/changelog)
- [Privacy policy](https://keyjawn.amditis.tech/privacy)
- [User manual](https://keyjawn.amditis.tech/manual)
- [App Review evidence](docs/ios-app-review.md)
- [Changelog](CHANGELOG.md)

## License

MIT
