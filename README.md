# KeyJawn: Custom mobile keyboard for LLM CLI usage

![KeyJawn branding graphic](https://i.imgur.com/c6z2Gl0.jpeg)

[![Build](https://github.com/jamditis/keyjawn/actions/workflows/build.yml/badge.svg)](https://github.com/jamditis/keyjawn/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-6cf2a8?style=flat)](LICENSE)
[![Android](https://img.shields.io/badge/android-8.0%2B-6cf2a8?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![GitHub release](https://img.shields.io/github/v/release/jamditis/keyjawn?color=6cf2a8&style=flat)](https://github.com/jamditis/keyjawn/releases)
[![Site](https://img.shields.io/badge/site-keyjawn.amditis.tech-6cf2a8?style=flat)](https://keyjawn.amditis.tech)

A custom Android keyboard designed for using LLM CLI agents (Claude Code, OpenClaw, etc.) from your phone.

## Install

**Google Play** (recommended): Search for "KeyJawn" or use the link above. Free download with optional $3.99 in-app purchase for premium features.

**GitHub releases**: Download the APK from [releases](https://github.com/jamditis/keyjawn/releases). The lite flavor (`com.keyjawn.lite`) is a standalone sideload build with no billing or network features.

After installing:
1. Go to **Settings > System > Languages & input > On-screen keyboard**
2. Enable **KeyJawn**
3. Set KeyJawn as your default keyboard

## Features

### Free (included with download)

- QWERTY keyboard with three layers (lowercase, uppercase, symbols)
- Terminal key row: Esc, Tab, Ctrl (three-state toggle), arrow keys
- Number row with shift-symbol hints (long-press for shifted symbols)
- Alt character popups on long-press (accented letters, punctuation variants)
- Configurable quick key (bottom row, defaults to `/`)
- Color-coded extra row keys for quick identification
- Shift / caps lock with visual state indicator
- Per-app autocorrect toggle (long-press spacebar)

### Premium ($3.99 one-time unlock)

- Voice input with streaming transcription
- Clipboard history manager
- SCP image upload to remote SSH servers
- Multi-host SSH management with encrypted credentials (AES-256)
- Slash command shortcuts for LLM CLIs
- Custom slash command sets
- Keyboard color themes (Dark, Light, OLED black, Terminal)
- Swipe gestures (delete word, space, layer switching)

## Who it's for

Anyone who SSHs into a server from their phone to use a CLI-based AI assistant. Built for and tested with:
- Claude Code via Cockpit web terminal
- Direct SSH apps (Termux, JuiceSSH, ConnectBot)
- Any LLM CLI that uses slash commands (OpenClaw, etc.)

## Versions

| | Play Store | Lite (sideload) |
|---|---|---|
| Package | `com.keyjawn` | `com.keyjawn.lite` |
| Free features | All | All |
| Premium unlock | $3.99 in-app purchase | Not available |
| SCP upload | Yes (premium) | No |
| Permissions | INTERNET, READ_MEDIA_IMAGES, RECORD_AUDIO | RECORD_AUDIO only |
| Distribution | Google Play | GitHub releases |

## Build

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

## Links

- [Website](https://keyjawn.amditis.tech)
- [Privacy policy](https://keyjawn.amditis.tech/privacy)
- [User manual](https://keyjawn.amditis.tech/manual)

## License

MIT
