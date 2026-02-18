# KeyJawn: Custom mobile keyboard for LLM CLI usage

![KeyJawn branding graphic](https://i.imgur.com/c6z2Gl0.jpeg)

[![Build](https://github.com/jamditis/keyjawn/actions/workflows/build.yml/badge.svg)](https://github.com/jamditis/keyjawn/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-6cf2a8?style=flat)](LICENSE)
[![Android](https://img.shields.io/badge/android-8.0%2B-6cf2a8?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-17.0%2B-6cf2a8?style=flat&logo=apple&logoColor=white)](https://developer.apple.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/swift-6.0-F05138?style=flat&logo=swift&logoColor=white)](https://swift.org)
[![GitHub release](https://img.shields.io/github/v/release/jamditis/keyjawn?color=6cf2a8&style=flat)](https://github.com/jamditis/keyjawn/releases)
[![Site](https://img.shields.io/badge/site-keyjawn.amditis.tech-6cf2a8?style=flat)](https://keyjawn.amditis.tech)

A custom keyboard for using LLM CLI agents (Claude Code, OpenClaw, etc.) from your phone. Android keyboard extension + iOS terminal app.

## Install

### Android

**Free version**: Download the lite APK from [GitHub releases](https://github.com/jamditis/keyjawn/releases). Includes voice input, clipboard history, slash commands, swipe gestures, and per-app autocorrect.

**Full version ($4)**: Buy on [the website](https://keyjawn.amditis.tech) via Stripe. After purchase, you'll get an email with a private download link (expires in 7 days). New versions are emailed automatically.

After installing:
1. Go to **Settings > System > Languages & input > On-screen keyboard**
2. Enable **KeyJawn**
3. Set KeyJawn as your default keyboard

### iOS (beta)

The iOS version is a standalone terminal app, not just a keyboard extension. It has a built-in SSH client (SwiftTerm + SwiftNIO SSH) so you can connect directly to your servers. A companion keyboard extension adds Esc, Tab, Ctrl, arrows, and slash commands to any app.

Currently in TestFlight beta. App Store launch coming soon.

To use the keyboard extension in other apps after installing:
1. Go to **Settings > General > Keyboard > Keyboards > Add new keyboard**
2. Select **KeyJawn Keyboard**

## Features

### Free (lite APK)

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

### Full version ($4 one-time purchase)

Everything in free, plus:
- SCP image upload to remote SSH servers
- Multi-host SSH management with encrypted credentials (AES-256)
- Custom slash command sets
- Keyboard color themes (Dark, Light, OLED black, Terminal)
- Menu panel with inline settings
- Clipboard pinning (persistent across sessions)
- Tooltip toggle

## Who it's for

Anyone who SSHs into a server from their phone to use a CLI-based AI assistant. Built for and tested with:
- Claude Code via Cockpit web terminal
- Direct SSH apps (Termux, JuiceSSH, ConnectBot)
- Any LLM CLI that uses slash commands (OpenClaw, etc.)

## Versions

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

# Tests
./gradlew testFullDebugUnitTest
./gradlew testLiteDebugUnitTest
```

Requires JDK 17, Android SDK with compileSdk 35.

### iOS

```bash
cd ios

# Generate Xcode project (after editing project.yml)
xcodegen generate

# Build for simulator
xcodebuild -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Debug build

# Archive for App Store (requires Apple Developer account + provisioning profiles)
bash scripts/build.sh
```

Requires Xcode 26+, Swift 6, iOS 17+ deployment target. Uses XcodeGen to manage the project file — edit `project.yml` instead of the `.xcodeproj` directly.

## Links

- [Website](https://keyjawn.amditis.tech)
- [Privacy policy](https://keyjawn.amditis.tech/privacy)
- [User manual](https://keyjawn.amditis.tech/manual)

## License

MIT
