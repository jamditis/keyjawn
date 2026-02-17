# KeyJawn iOS Development Research

Comprehensive analysis of every viable (and non-viable) approach to bringing KeyJawn to iOS. Research conducted February 2026.

## Table of contents

- [Executive summary](#executive-summary)
- [The fundamental problem](#the-fundamental-problem)
- [Approach 0: Web terminal with custom keyboard (fastest path)](#approach-0-web-terminal-with-custom-keyboard-fastest-path)
- [Approach 1: iOS keyboard extension (native Swift)](#approach-1-ios-keyboard-extension-native-swift)
- [Approach 2: Terminal app with built-in keyboard](#approach-2-terminal-app-with-built-in-keyboard)
- [Approach 3: Hybrid -- keyboard extension + terminal app](#approach-3-hybrid----keyboard-extension--terminal-app)
- [Approach 4: Bluetooth HID hardware bridge](#approach-4-bluetooth-hid-hardware-bridge)
- [Cross-platform framework evaluation](#cross-platform-framework-evaluation)
- [SSH library comparison](#ssh-library-comparison)
- [Code sharing strategies](#code-sharing-strategies)
- [Code portability analysis](#code-portability-analysis)
- [Distribution and business model](#distribution-and-business-model)
- [App Store considerations](#app-store-considerations)
- [Competitive landscape](#competitive-landscape)
- [Recommendations](#recommendations)

---

## Executive summary

**KeyJawn's core value proposition -- terminal keys (Esc, Tab, Ctrl, arrows) for CLI agents -- is fundamentally incompatible with iOS's keyboard extension model.** iOS keyboard extensions can only insert text via `UITextDocumentProxy.insertText()`. There is no API to send raw key events like Escape, Ctrl+C, or arrow key signals. This is not a bug or limitation to work around -- it is an intentional architectural decision by Apple.

Every successful iOS terminal app (Blink Shell, Termius, a-Shell, iSH, Moshi) solves this by building special key handling **inside the app itself**, using `inputAccessoryView` toolbars and `UIKeyCommand` for hardware keyboard support. None use system-wide keyboard extensions for terminal keys.

**The viable paths forward are:**

| Approach | Terminal keys work? | System-wide? | Effort | Recommended? |
|----------|-------------------|--------------|--------|-------------|
| Web terminal with custom keyboard | Full (in browser) | No (web only) | Days-weeks | **Fastest proof of concept** |
| Keyboard extension (native Swift) | Partial (Tab, slash commands, text features) | Yes | Medium | Secondary |
| Terminal app with built-in keyboard | Full (Esc, Ctrl, arrows, everything) | No (in-app only) | Medium-High | **Primary native product** |
| Hybrid (both) | Full within app, partial system-wide | Both | High | Best long-term |
| Bluetooth HID hardware bridge | Full (any device) | Yes (hardware) | Medium | Niche/accessory |

**Bottom line:** The fastest path is a **web terminal** (WebSSH relay + custom HTML keyboard) that can ship in days at zero cost and validates the iOS use case. The most impactful native product is a **terminal app** (SSH/Mosh client with KeyJawn's keyboard built in), optionally paired with a **keyboard extension** for the text-only features that do work system-wide. The closest existing competitor is **Moshi** (getmoshi.app), an iOS terminal app specifically designed for running Claude Code from iPhone.

---

## The fundamental problem

### Android vs iOS keyboard architecture

| Capability | Android `InputMethodService` | iOS `UIInputViewController` |
|-----------|------------------------------|------------------------------|
| Send arbitrary key events (Esc, Ctrl, F-keys) | `InputConnection.sendKeyEvent()` | **Not possible** |
| Insert text | `InputConnection.commitText()` | `UITextDocumentProxy.insertText()` |
| Delete text | `InputConnection.deleteSurroundingText()` | `UITextDocumentProxy.deleteBackward()` |
| Move cursor (left/right) | `sendKeyEvent(DPAD_LEFT/RIGHT)` | `adjustTextPosition(byCharacterOffset:)` |
| Move cursor (up/down) | `sendKeyEvent(DPAD_UP/DOWN)` | **Not possible** (no line-level API) |
| Send Ctrl+key combinations | `sendKeyEvent(KeyEvent(KEYCODE_C, META_CTRL_ON))` | **Not possible** |
| Send Escape | `sendKeyEvent(KEYCODE_ESCAPE)` | **Not possible** (`insertText("\u{1b}")` ignored by most apps) |
| Voice input | Direct `SpeechRecognizer` access | **Microphone blocked** in extensions |
| Clipboard | Direct `ClipboardManager` access | Requires "Full Access" permission |
| Memory limit | Hundreds of MB | ~30-66 MB (device-dependent) |
| Network access | With `INTERNET` permission | Only with "Full Access" |
| Image picker | Via `Intent` system | **Cannot present pickers** from extension |
| Detect target app | `EditorInfo.packageName` | **Not available** (privacy restriction) |

### What the `insertText` escape character trick can do

Calling `textDocumentProxy.insertText("\u{1b}")` (ASCII 27 / Escape) from a keyboard extension:
- **Standard text fields (UITextField/UITextView):** Silently ignored or stripped
- **Terminal apps with custom UITextInput:** Could theoretically work if the app explicitly handles raw control characters
- **Arrow keys as ANSI sequences:** `insertText("\u{1b}[A")` for arrow up -- only works in cooperating terminal apps

This means terminal key features could work with specific apps that opt in, but would not work system-wide.

---

## Approach 0: Web terminal with custom keyboard (fastest path)

The fastest route to iOS users requires zero Apple involvement. Run a WebSocket-to-SSH relay server and serve a web terminal with KeyJawn's custom keyboard rendered entirely in HTML/CSS/JS. Users visit a URL in Safari and bookmark it to their Home Screen.

### How it works

```
iPhone Safari  <--HTTPS/WSS-->  Relay Server  <--SSH-->  Target Server
                                (WebSocket)              (where Claude Code runs)
```

The relay accepts WebSocket connections from the browser, establishes an SSH connection to the target, and pipes data between them. The browser never speaks SSH directly. The terminal and keyboard are a single web page -- the keyboard IS the page, not a system extension.

### Relay implementations

| Tool | Language | Notes |
|------|----------|-------|
| **Sshwifty** | Go + Vue.js | Self-contained binary, SSH + Telnet, preset host configs, TLS built-in, AGPL. Already has mobile key shortcuts. Most promising base for customization. |
| **webssh2** | Node.js | Express + Socket.io + ssh2 + xterm.js. Well-maintained (3.1k stars). SSH-aware. |
| **Cloudflare browser SSH** | Hosted | Free tier, renders xterm.js in browser, Zero Trust auth. Cannot customize the keyboard UI. |

### Terminal rendering

[xterm.js](https://github.com/xtermjs/xterm.js) is the standard web terminal library. GPU-accelerated canvas rendering, zero dependencies, used by VS Code's terminal. Confirmed working in iOS WebKit (used by the Pisth iOS SSH app). Known mobile limitations: predictive text from the native keyboard interferes with terminal input, and there are open issues for touch event handling ([#5377](https://github.com/xtermjs/xterm.js/issues/5377), [#1101](https://github.com/xtermjs/xterm.js/issues/1101)).

### Custom keyboard in HTML

The native iOS keyboard must be suppressed. The most reliable approach on iOS Safari is to avoid standard `<input>` or `<textarea>` elements entirely -- xterm.js already renders to a `<canvas>`. KeyJawn's extra row (Esc, Tab, Ctrl, arrows, mic) would be a fixed HTML bar between the terminal and a custom HTML keyboard. Key presses inject directly into xterm.js via `terminal.write()`.

Virtual keyboard libraries that could help:
- [simple-keyboard](https://virtual-keyboard.js.org/) -- lightweight, responsive, fully customizable layouts
- [teclado.js](https://www.cssscript.com/mobile-virtual-keyboard-teclado/) -- supports long-press character variants (like `AltKeyMappings`)
- [KioskBoard](https://furcan.github.io/KioskBoard/) -- has `allowRealKeyboard: false` option

### Voice input on web

The Web Speech API (`SpeechRecognition`) works in iOS Safari with microphone permission. This enables voice-to-text directly in the browser without any native app.

### Security considerations

The relay server sees all SSH traffic in plaintext between the WebSocket and SSH legs. Mitigations:
- Run the relay on the same machine as the SSH target (localhost:22), eliminating the relay-to-target network leg
- Use Cloudflare Tunnel for encrypted browser-to-relay transport
- Use SSH key authentication with keys stored on the relay
- Restrict the relay to preset hosts only (Sshwifty supports this)

### Self-hosted on houseofjawn

The keyjawn-store backend already runs on houseofjawn (port 5060). A WebSSH relay could run alongside it at zero additional hosting cost. If the relay targets localhost, the SSH target and relay share the same machine with no network exposure.

### Advantages

- **Ship in days, not weeks.** Deploy Sshwifty or webssh2 behind Cloudflare Tunnel and it works immediately from any phone.
- **$0 cost.** No Apple Developer Program, no App Store, no hosting fees (uses existing server).
- **Works on any phone.** Not just iOS -- Android, desktop browsers, tablets.
- **No App Store review.** Update the keyboard layout or add features without waiting for Apple.

### Disadvantages

- No haptic feedback (Web Vibration API not supported in iOS Safari)
- ~40ms input latency vs ~15ms native
- No Mosh support (Mosh uses UDP, WebSocket is TCP)
- Page reload loses the session (mitigate with tmux/mosh on the server side)
- No native clipboard integration with other apps
- Relay security tradeoff (SSH traffic passes through the relay in plaintext)
- Cannot work offline

### When to use this approach

This is the right starting point if you want to validate the iOS use case before investing in a native app. Deploy it in a day, use it for a week, and see if the workflow holds up. If it does, proceed to a native terminal app (Approach 2). The web version can continue to serve as a free fallback.

---

## Approach 1: iOS keyboard extension (native Swift)

### What works

| KeyJawn feature | iOS feasibility | Notes |
|----------------|----------------|-------|
| QWERTY keyboard layout | Fully possible | Standard keyboard extension |
| Number row | Fully possible | Standard UI layout |
| Shift/symbols layer switching | Fully possible | Same state machine |
| Slash command shortcuts | Fully possible | `insertText()` for arbitrary text |
| Alt key long-press characters | Fully possible | `UILongPressGestureRecognizer` + popup |
| Tab key | Mostly works | `insertText("\t")` works in most text views |
| Arrow keys (left/right) | Fully possible | `adjustTextPosition(byCharacterOffset: +/-1)` |
| Themes | Fully possible | Full control over keyboard styling |
| Key repeat (backspace, arrows) | Fully possible | Timer-based, same pattern as Android |
| Spacebar cursor control | Fully possible | `UIPanGestureRecognizer` + `adjustTextPosition` |
| Double-tap space to period | Fully possible | `deleteBackward()` then `insertText(". ")` |
| Auto-capitalize after sentence | Fully possible | Read `documentContextBeforeInput` |
| Clipboard history | With Full Access | `UIPasteboard` + SQLite storage |
| SCP upload | With Full Access | SSH library (Citadel/Shout), but memory-constrained |
| Haptic feedback | With Full Access | `UIImpactFeedbackGenerator` |

### What does NOT work

| KeyJawn feature | iOS reality | Workaround |
|----------------|-------------|------------|
| Escape key | `insertText("\u{1b}")` ignored by standard apps | Only in cooperating terminal apps |
| Ctrl modifier + key | No modifier key API exists | Only in cooperating terminal apps |
| Arrow keys as events | `adjustTextPosition` moves cursor but doesn't send key events | Works for text editing, not terminal navigation |
| Up/down arrows | No line-movement API | Unreliable heuristic only |
| Voice-to-text | Microphone blocked in extensions | Deep-link to containing app for voice capture |
| Per-app detection | `EditorInfo.packageName` equivalent doesn't exist | Cannot detect which app is active |
| Image picker for SCP | Cannot present system pickers from extension | Pre-select in containing app, share via App Group |

### Technical details

**UI framework:** UIKit primary (5-10% faster than SwiftUI, 13% less memory). SwiftUI acceptable for settings panels and non-performance-critical overlays.

**Memory budget:** ~30-66 MB depending on device. Target sustained usage under 30 MB for safety. Use lazy initialization, avoid WebViews, keep SSH connections short-lived, use SQLite over JSON for data storage.

**Architecture:**
```
KeyJawnKeyboard/ (extension target)
  KeyboardViewController.swift  -- UIInputViewController subclass
  QwertyKeyboardView.swift      -- UIKit buttons in UIStackView rows
  ExtraRowView.swift            -- Terminal key row
  NumberRowView.swift           -- Number row
  AltKeyPopup.swift             -- Long-press character popup
  SlashCommandPopup.swift       -- Slash command popup
  ClipboardPanel.swift          -- Clipboard history overlay
  MenuPanel.swift               -- Settings overlay
```

**Key libraries:**
- [KeyboardKit](https://github.com/KeyboardKit/KeyboardKit) (1,700+ stars) -- Leading open-source keyboard framework. Provides layout engine, gestures, haptics, themes, autocomplete. Free tier covers basics; Pro adds localization, dictation, emoji.
- [Citadel](https://github.com/orlandos-nl/Citadel) -- Pure Swift SFTP (SwiftNIO-based)
- [Shout](https://github.com/jakeheis/Shout) -- Swift SCP wrapper (libssh2-based)

**Open-source references:**
- [tasty-imitation-keyboard](https://github.com/archagon/tasty-imitation-keyboard) -- Apple keyboard clone, good for learning key rendering
- [Scribe-iOS](https://github.com/scribe-org/Scribe-iOS) -- Language keyboards, good SQLite-in-extension patterns
- [azooKey](https://github.com/ensan-hcl/azooKey) -- SwiftUI-based keyboard extension

---

## Approach 2: Terminal app with built-in keyboard

This is how every successful iOS terminal app works. Build an SSH/Mosh client with KeyJawn's keyboard as a custom `inputAccessoryView` or `inputView`.

### How terminal apps handle special keys on iOS

**All follow the same pattern:**

1. The terminal view becomes **first responder** and implements `UIKeyInput`/`UITextInput`
2. **Hardware keyboard** events are intercepted via `UIKeyCommand` overrides on the responder chain
3. **On-screen special keys** are provided as an `inputAccessoryView` (toolbar above the system keyboard)
4. Control characters and escape sequences are generated **within the app process** and sent directly to the terminal engine

**Blink Shell** (open source, github.com/blinksh/blink):
- SmartKeys bar with Ctrl, Alt, Esc, Tab, arrows as `inputAccessoryView`
- Ctrl/Alt support "continuous presses" (hold modifier, chain key presses)
- Hardware keyboard remapping: Caps Lock to Ctrl or Esc
- Terminal rendered via WKWebView + hterm

**Moshi** (getmoshi.app) -- most relevant competitor:
- Purpose-built for running Claude Code from iPhone
- Custom toolbar with Ctrl, Alt, Esc, Tab, arrows, special characters
- Long-press Ctrl opens shortcuts panel (tmux, Claude Code commands, Ctrl combos)
- Double-tap Ctrl locks it for continuous use
- Local Whisper model for voice input (no network round-trip)
- Mosh connections for resilient mobile usage
- Built with SwiftUI

**Other terminal apps:** Termius (customizable strip panel, spacebar-drag for arrows), iSH (4-way rocker button for arrows), a-Shell (Caps Lock as Escape setting)

### Architecture for a KeyJawn terminal app

```
KeyJawnTerminal/
  TerminalView.swift           -- UIView implementing UIKeyInput, wraps SwiftTerm
  KeyJawnAccessoryView.swift   -- inputAccessoryView with Esc, Tab, Ctrl, arrows
  CtrlState.swift              -- Three-state Ctrl machine (OFF/ARMED/LOCKED)
  SSHManager.swift             -- SSH/Mosh connection management
  VoiceInputHandler.swift      -- SFSpeechRecognizer (full access in main app)
  SlashCommandPopup.swift      -- Slash command quick-insert
  HostConfigView.swift         -- SSH host management (SwiftUI)
```

**Terminal engine:** [SwiftTerm](https://github.com/migueldeicaza/SwiftTerm) (MIT licensed, pure Swift VT100/Xterm emulator by Miguel de Icaza). Used in La Terminal and Secure Shellfish.

**Advantages over keyboard extension:**
- All terminal keys work (Esc, Ctrl, arrows, function keys)
- Full microphone access for voice-to-text
- No memory constraints (full app allocation)
- Can present image picker for SCP
- Can run background operations
- No "Full Access" friction

**Disadvantages:**
- Not system-wide -- only works within the KeyJawn app
- Users need a separate terminal app instead of using their preferred one
- Competes with established apps (Blink, Termius, Moshi, Prompt)

---

## Approach 3: Hybrid -- keyboard extension + terminal app

Ship both products:

1. **KeyJawn Keyboard** (keyboard extension): System-wide keyboard with slash commands, text shortcuts, clipboard history, themes, voice-to-text (via containing app), QWERTY layout, alt-key long-press. Tab works. Esc/Ctrl/arrows work only in cooperating terminal apps.

2. **KeyJawn Terminal** (or integrate into the containing app): Full terminal app with SSH/Mosh, all terminal keys via `inputAccessoryView`, voice input, SCP upload.

The containing app for the keyboard extension IS the terminal app. Users get both from one App Store download.

This is the most complete approach but also the highest effort.

---

## Approach 4: Bluetooth HID hardware bridge

The most creative unconventional approach. An ESP32-S3 microcontroller board (~$5-10) connects to iPhone via BLE and presents as a USB HID keyboard to a target computer.

**How it works:**
1. iOS companion app provides KeyJawn's full terminal key UI
2. Key presses are sent via BLE to the ESP32-S3 dongle
3. Dongle translates to USB HID keyboard events for the connected computer
4. Target computer sees a standard USB keyboard -- zero configuration needed

**Existing implementations:**
- [ESPRemoteControl](https://github.com/KoStard/ESPRemoteControl) -- ESP32-S3, BLE to USB HID
- [KeyCatcher](https://github.com/escuber/KeyCatcher_pub) -- ESP32, BLE/UDP to USB/BLE HID
- [Blue Keyboard](https://github.com/larrylart/blue_keyboard) -- ESP32-S3, BLE to USB HID

**Interesting note:** [Bluetouch](https://apps.apple.com/us/app/bluetouch/id1622635358) is somehow approved on the App Store and functions as a BLE HID keyboard directly from an iPhone, despite Apple blocking the BLE HID service UUID (0x1812) in CoreBluetooth. How it bypasses this restriction is unknown.

**Opportunity:** A KeyJawn-branded ESP32 dongle could be a hardware accessory product. Works with any target device (servers, Smart TVs, embedded systems, BIOS setup). Unique in the market.

---

## Cross-platform framework evaluation

### Frameworks ruled out for iOS keyboard extensions

| Framework | Why it fails | Details |
|-----------|-------------|---------|
| **Flutter** | Memory: 60-80 MB minimum runtime vs 30-48 MB limit | Flutter's own docs warn against keyboard extensions. No one has shipped a Flutter keyboard. |
| **React Native** | Memory: ~48 MB+ baseline, exceeds limit | [GitHub issue #31910](https://github.com/facebook/react-native/issues/31910) documents crashes. Zero production examples. |
| **Compose Multiplatform** | Memory: ~16 MB overhead eats the budget | Cannot run `ComposeUIViewController` inside an app extension. No documented examples. |
| **.NET MAUI** | No extension support | No first-class iOS keyboard extension support. Visual Studio for Mac discontinued. No real-world examples. |
| **Capacitor/Ionic** | WKWebView too heavy (~100 MB+) | Web view runtime exceeds memory limit by 2-3x. |
| **Go mobile** | Go runtime overhead, limited type support | Low maintenance, not recommended for memory-constrained environments. |

**The 48 MB memory ceiling kills every cross-platform UI framework.** The keyboard extension UI must be native Swift/UIKit.

### Frameworks viable for shared logic only

| Framework | Viability | Notes |
|-----------|-----------|-------|
| **Kotlin Multiplatform (KMP)** | Viable | Already Kotlin codebase. Compiles to native iOS binary via LLVM. No VM overhead. But: no one has built an iOS keyboard extension with KMP. Memory risk from Kotlin/Native runtime. |
| **Rust + UniFFI** | Viable | Production-proven at Mozilla. Generates both Swift and Kotlin bindings. Best memory characteristics (no GC). Learning curve. |
| **C++ + Djinni** | Viable | Proven at Snap, Slack. Higher friction than Rust/KMP. |

---

## SSH library comparison

For the terminal app approach (Approach 2), choosing the right SSH library is critical. Here is every viable option for iOS/Swift.

### Citadel (recommended)

[Citadel](https://github.com/orlandos-nl/Citadel) is a pure-Swift SSH client and server framework built on Apple's [SwiftNIO SSH](https://github.com/apple/swift-nio-ssh). MIT licensed, actively maintained (v0.11.1 in early 2025, continued commits into 2026).

Capabilities: SSH client with password and public key auth, full SFTP client (replaces SCP), PTY allocation for interactive sessions, TCP-IP channel forwarding, jump host support, command execution with separate stdout/stderr.

Why it wins: Pure Swift (no C dependencies to cross-compile), uses Apple's own SwiftNIO async networking framework, SFTP support is native (more reliable than SCP for file transfers), MIT license allows any use, supports iOS/macOS/visionOS/watchOS/tvOS/Linux/Android.

### SwiftNIO SSH (underlying layer)

[SwiftNIO SSH](https://github.com/apple/swift-nio-ssh) is Apple's own SSHv2 protocol implementation. Provides the `NIOSSHHandler` as a SwiftNIO `ChannelHandler` supporting shell requests, exec requests, PTY allocation, and environment variables. Does not ship a ready-made client -- it provides the building blocks. Citadel wraps this into a usable API.

### NMSSH (not recommended)

[NMSSH](https://github.com/NMSSH/NMSSH) is an Objective-C wrapper around libssh2. 683 stars but effectively unmaintained -- no major releases in over a year, 79 open issues. Requires a bridging header for Swift use. Given Citadel exists, there is no reason to use NMSSH for a new project.

### SwiftSH / Shout (limited)

[SwiftSH](https://github.com/Frugghi/SwiftSH) wraps libssh2 in Swift. The original project appears inactive; Miguel de Icaza maintains a fork. [Shout](https://github.com/jakeheis/Shout) provides SCP file send. Both require libssh2 as a C dependency, adding cross-compilation complexity. Neither is as complete or maintained as Citadel.

### libssh2 (C, compilable for iOS)

[libssh2](https://github.com/libssh2/libssh2) is the mature C SSH library. [libssh2-iosx](https://github.com/apotocki/libssh2-iosx) provides build scripts that compile it as an xcframework for iOS. [Libssh2Prebuild](https://github.com/DimaRU/Libssh2Prebuild) provides prebuilt binaries as a Swift Package. Viable fallback if Citadel proves insufficient, but wrapping C code is more maintenance overhead.

### What commercial apps use

- **Blink Shell:** libssh2 for SSH, custom Mosh C++ library, curl for SCP/SFTP. Codebase is 70% Swift, 25% Objective-C.
- **La Terminal / Secure Shellfish:** SwiftTerm engine, likely SwiftNIO SSH or Citadel (same maintainer ecosystem).
- **Termius:** Proprietary cross-platform SSH stack.
- **Prompt 3 (Panic):** Proprietary SSH implementation.

### Mosh compilation for iOS

Mosh uses UDP (ports 60000-61000) for its State Synchronization Protocol. Blink Shell has cross-compiled Mosh for iOS since 2016. The [blinksh/build-mosh](https://github.com/blinksh/build-mosh) repository contains the build scripts. The [blinksh/mosh](https://github.com/blinksh/mosh) fork has iOS-specific changes. Mosh requires protobuf and OpenSSL as build dependencies.

iOS background restriction: when the app is suspended, iOS kills UDP sockets. Mosh handles this gracefully -- the server keeps state while the client is disconnected, and reconnects when the app is foregrounded. Users see a brief "reconnecting" flash and then the current terminal state. This is Mosh's core design advantage over SSH for mobile use.

### Recommendation

Use **Citadel** for SSH and SFTP. It is pure Swift, MIT licensed, built on Apple's own networking stack, and provides everything KeyJawn needs. For Mosh, cross-compile using the Blink Shell build scripts as a reference. Support both SSH and Mosh -- SSH is the baseline, Mosh provides resilient mobile connections.

---

## Code sharing strategies

### Option A: Kotlin Multiplatform (best fit for existing codebase)

```
shared/
  commonMain/    -- CtrlState, KeyboardLayout, AltKeyMappings, SlashCommandRegistry
  androidMain/   -- InputConnection wrapper
  iosMain/       -- UITextDocumentProxy wrapper (calls into Swift)
androidApp/      -- Existing KeyJawn (uses shared module)
iosApp/          -- Xcode project importing shared KMP framework
```

**Pros:** KeyJawn is already Kotlin. `CtrlState`, `KeyboardLayout`, `AltKeyMappings` move to `commonMain` with zero changes. `expect`/`actual` pattern maps well to platform-specific I/O. Compiles to native code (no VM).

**Cons:** No community precedent for KMP in keyboard extensions. Kotlin/Native runtime adds memory overhead. Debugging Kotlin code on iOS is harder. Static framework required (extensions can't have embedded dynamic frameworks). ~20-30% of code is shareable.

### Option B: Native Swift (simplest, recommended)

Write the iOS version entirely in Swift. Accept the code duplication for the small amount of shareable logic (~230 lines of pure portable code).

**Pros:** Zero memory overhead. Full Xcode debugging. KeyboardKit framework available. Simpler architecture. Better App Store review experience.

**Cons:** Dual maintenance of key layouts, state machines, slash commands. Feature drift risk. Need Swift expertise.

### Option C: Rust + UniFFI (best memory characteristics)

Write core logic in Rust, generate bindings for both Kotlin (Android) and Swift (iOS).

**Pros:** Best memory efficiency for the constrained extension. Production-proven at Mozilla. Single source of truth.

**Cons:** Requires Rust expertise. Build complexity (cross-compilation). Binary size increase. Steepest learning curve.

### Recommendation

**Go with Option B (native Swift) for the initial iOS version.** The shareable code surface is small (~230 lines of pure logic out of ~3000+ total lines). The overhead of introducing KMP or Rust is not justified for the first version. If the iOS and Android codebases diverge significantly over time, consider KMP for shared logic later.

---

## Code portability analysis

### Fully portable (pure logic, zero Android imports)

| File | Lines | Notes |
|------|-------|-------|
| `CtrlState.kt` | 44 | State machine with callback. Trivial Swift rewrite. |
| `KeyboardLayout.kt` | 121 | Key, KeyOutput, Row, Layer types + all layout definitions. Most valuable file to share. |
| `AltKeyMappings.kt` | 46 | Static map of key labels to alternate characters. |
| `HostConfig.kt` | 19 | Pure data class with validation. |
| **Total** | **230** | |

### Partially portable (logic reusable, platform integration needs rewriting)

| File | Lines | Portable % | Notes |
|------|-------|-----------|-------|
| `SlashCommandRegistry.kt` | 134 | ~60% | Command models and MRU logic portable. Asset loading and SharedPreferences need `UserDefaults`/`Bundle.main`. |
| `AppPrefs.kt` | 121 | ~50% | Preference keys and option lists portable. Storage maps to `UserDefaults`. |
| `ThemeManager.kt` | 220 | ~55% | Color definitions portable. Drawable builders need full rewrite for UIKit. |
| `ClipboardHistoryManager.kt` | 108 | ~40% | List management logic portable. Clipboard access completely different. |
| `HostStorage.kt` | 88 | ~35% | CRUD logic portable. Encrypted storage maps to iOS Keychain. |

### Platform-specific (needs full rewrite)

| File | Lines | Notes |
|------|-------|-------|
| `KeyJawnService.kt` | 174 | `InputMethodService` -> `UIInputViewController`. Completely different lifecycle. |
| `QwertyKeyboard.kt` | 558 | Android View hierarchy -> UIKit `UIStackView` + `UIButton`. |
| `ExtraRowManager.kt` | 375 | Android View wiring -> UIKit buttons. |
| `KeySender.kt` | 28 | `InputConnection` -> `UITextDocumentProxy`. Different API surface. |
| `VoiceInputHandler.kt` | 127 | `SpeechRecognizer` -> microphone blocked in extensions. |
| All UI files (popups, panels, previews) | ~800+ | Android `PopupWindow`/`View` -> UIKit equivalents. |
| Full-flavor files (SCP, billing) | ~400 | JSch -> Citadel/Shout. IAP -> StoreKit. |

### Summary

**~15-20% of the codebase by line count is portable.** The 230 lines of fully portable code are the most *valuable* lines -- they define the keyboard layout, key mapping system, and modifier state machine. The rest is UI plumbing that needs platform-specific implementation.

---

## Distribution and business model

### Distribution channels

The App Store is the only practical distribution channel for reaching US iPhone users. Every alternative path is either impossible, impractical, or limited to specific regions.

| Channel | Viable? | Notes |
|---------|---------|-------|
| **App Store** | Yes | Only realistic channel for US users. $99/year developer fee. 15% commission (Small Business Program). |
| **TestFlight** | Beta only | 90-day build expiry, 10,000 testers max. Good for beta testing, not distribution. |
| **AltStore PAL** | EU/Japan only | Legitimate alternative marketplace under DMA. Expanding to Brazil, Australia, UK in 2026. Free to list since April 2025. |
| **EU Web Distribution** | No | Requires 1M+ prior-year installs and EU legal entity. KeyJawn does not qualify. |
| **Enterprise Program** | No | Requires 100+ employees, internal distribution only. Apple actively revokes misuse. |
| **AltStore classic** | Barely | Requires computer refresh every 7 days. Limited to 3 active apps. Not practical for end users. |
| **TrollStore** | Dead | Only works on iOS 14.0-17.0. Apple patched the exploit in iOS 17.0.1. |
| **Ad Hoc** | Barely | Limited to 100 devices per type per year. Must collect UDIDs. Logistical nightmare. |
| **PWA** | No | Cannot provide keyboard or terminal input functionality on iOS. |
| **Direct IPA** | No | No equivalent to Android's direct APK distribution in the US. |

### Revenue per sale

| Channel | Price | Platform cut | Your revenue |
|---------|-------|-------------|-------------|
| App Store IAP (Small Business Program, 15%) | $3.99 | $0.60 | $3.39 |
| External Stripe link (post-Epic v. Apple ruling) | $4.00 | ~$0.42 (Stripe 2.9% + $0.30) | ~$3.58 |
| Android (current, via Stripe) | $4.00 | ~$0.42 | ~$3.58 |
| AltStore PAL (EU) | $3.99 | $0 (self-publish free) | $3.99 minus payment processor |

### Post-Epic v. Apple external payment links

As of May 2025, Apple updated its App Review Guidelines to allow external payment links in iOS apps on the US storefront. You can now include a link inside your iOS app that takes users to a Stripe checkout page, bypassing Apple's IAP. Apple may eventually charge a small "coordination fee" for this -- the Ninth Circuit ruled in December 2025 that Apple can charge fees for linked-out purchases, but the exact rate has been sent back to the district court. Stripe published a guide for developers on how to implement this.

In practice: offer both IAP ($3.99) and an external Stripe link ($4.00) side by side. Users who want convenience use IAP. Users who want to support you directly use Stripe. You keep more revenue on Stripe sales.

### Break-even analysis

At $3.39 per IAP sale, 30 sales per year covers the $99 developer fee. That is roughly 2-3 sales per month. At 100 sales/year, net revenue is $240 after the developer fee.

### EU alternative distribution

AltStore PAL operates as a legitimate alternative marketplace in the EU (and expanding to Japan, Brazil, Australia, UK). Developers can self-publish for free since April 2025. Apps must still be notarized through Apple ($99/year developer program required). If KeyJawn lists on AltStore PAL, it reaches EU/Japan users outside the App Store, but the audience is small and technically inclined.

### Recommended pricing model

**Free app + $3.99 one-time IAP** for pro features (terminal keys, SCP upload, voice input, themes, clipboard history). This mirrors the Android lite/full strategy, maximizes discoverability on the App Store, and avoids subscription fatigue. Include an external Stripe payment link as an alternative to IAP for users who prefer it.

---

## App Store considerations

### Review guidelines for keyboard extensions (4.4.1)

**Required:**
- Must provide keyboard input functionality
- Must include globe/next-keyboard button (`advanceToNextInputMode()`)
- Must function without Full Access and without network
- Number and Decimal keyboard types must be supported

**Prohibited:**
- Repurposing keyboard buttons for non-keyboard behaviors
- Advertising within the keyboard extension
- Using prohibited APIs (`UIApplication.shared`, `openURL` from extension)

### Full Access implications

When a user grants Full Access, Apple shows: *"Full Access allows the developer of this keyboard to transmit anything you type."* This warning scares many users.

**Features requiring Full Access:**
- Network access (SCP upload)
- Clipboard reading (`UIPasteboard`)
- Haptic feedback
- Shared container write access with containing app

**The keyboard must work without Full Access** for basic typing. Gate only advanced features behind it.

### Voice input

**Keyboard extensions have NO microphone access.** This is a hard platform restriction. Workaround: deep-link to containing app for voice capture, share transcript via App Group, return to keyboard.

### Pricing

Given KeyJawn's niche audience (terminal/CLI power users), recommended model:
- Free download with basic keyboard
- One-time IAP ($3.99-$6.99) for pro features (themes, clipboard, SCP)
- Mirrors the existing Android pricing approach

### Review process

Keyboard apps are notoriously difficult to get approved. Developers report 5-10+ rejection rounds. Common pitfalls:
- Keyboard not working without Full Access
- Missing globe button
- Containing app too thin
- Vague rejection messages from reviewers
- Include detailed App Review notes explaining the terminal keyboard use case

### Developer account

- Apple Developer Program: $99/year (vs Google Play's one-time $25)
- Requires Mac + Xcode for development
- TestFlight for beta distribution (testers must manually enable keyboard in Settings)

---

## Competitive landscape

### Existing iOS terminal apps

| App | Approach | Special keys | Voice | Price |
|-----|----------|-------------|-------|-------|
| **Moshi** | SSH/Mosh terminal | Ctrl, Alt, Esc, Tab, arrows, pipe, slash, tilde, Home/End/PgUp/PgDn | Local Whisper (3 engines) | Free (beta) |
| **Blink Shell** | SSH/Mosh terminal | SmartKeys bar (inputAccessoryView), hardware key remapping | No | $19.99/yr |
| **Termius** | SSH client | Strip panel with Ctrl, Esc, Tab, arrows. Spacebar-drag arrows | No | Free + $10/mo |
| **a-Shell** | Local shell | Caps Lock as Escape, Ctrl+[ for Esc | No | Free |
| **iSH** | Linux emulator | Tab, Ctrl, Esc, 4-way arrow rocker | No | Free |
| **Prompt 3** (Panic) | SSH client | Custom toolbar, Clips feature | No | $19.99/yr or $100 |
| **La Terminal** | SSH (SwiftTerm) | Built-in terminal keys, visionOS support | No | Paid |
| **Secure Shellfish** | SSH client | Special key bar, drag-and-drop files | No | ~$10/yr |

### Moshi deep dive (primary competitor)

[Moshi](https://getmoshi.app) launched in early 2026 as a free beta. It is the closest existing product to what KeyJawn would be on iOS -- a terminal app built specifically for AI coding agents (Claude Code, Aider, Codex).

**Keyboard features:**
- Custom toolbar at screen bottom with Ctrl, Alt, Esc, Tab, arrow keys, pipe, slash, backslash, tilde, dash, underscore, Home, End, PgUp, PgDn
- Toolbar is swipeable to reveal more buttons
- Single-tap modifier activates for next keystroke only (auto-reset after one keypress)
- Double-tap within 400ms locks the modifier (bar appears under the key) -- nearly identical to KeyJawn's `CtrlState` (OFF/ARMED/LOCKED)
- Long-press Ctrl opens a panel with tmux commands, Claude Code shortcuts (/compact, /clear, /resume, /help), and common Ctrl combinations
- Double-tap the keyboard toggle button sends Enter

**Voice input:**
- Three engine options: Apple Intelligence, OpenAI Whisper (on-device), or system dictation
- On-device Whisper means zero-latency voice recognition with no cloud dependency
- Activated by long-pressing the keyboard toggle button

**Connection features:**
- Mosh protocol support for network resilience (survives WiFi/cellular switches)
- SSH with key and password authentication
- Push notifications via webhooks when agents need input
- Biometric SSH key unlock

**What Moshi does not have:**
- No configurable slash command registry with MRU ordering
- No remappable extra row slots
- No SCP/SFTP image upload with path insertion
- No alt key mappings (long-press accent characters)
- No clipboard history with pinning
- No color themes (Dark, Light, OLED, Terminal)
- Pricing TBD (currently free beta)

### Existing iOS keyboard apps

| App | Focus | Relevant features |
|-----|-------|-------------------|
| **Gboard** | General typing | Swipe, search, voice (via app switch), GIFs |
| **SwiftKey** | General typing | Swipe, AI predictions, clipboard manager |
| **Fleksy** | Privacy-first typing | No Full Access needed, fast tap typing |

No existing iOS keyboard extension provides terminal keys.

### KeyJawn's differentiator

KeyJawn could compete on several fronts that no existing iOS app covers:

1. **Slash command system with MRU ordering.** No competitor has a configurable slash command registry with built-in and custom command sets. For LLM CLI workflows, quick access to `/compact`, `/review`, `/help`, `/clear`, and custom commands is a real productivity gain.
2. **Configurable extra row.** Remappable slots where users can assign different key codes or text macros to the Esc/Tab/Ctrl positions.
3. **SCP/SFTP image upload with paste-path.** Pick an image, upload it via SFTP, insert the remote path into the terminal -- one action. No competitor offers this.
4. **Alt key mappings.** Long-press access to accented characters and punctuation variants while in a terminal context.
5. **Clipboard history with pinning.** Pinned items persist across sessions. Useful for frequently-pasted paths, tokens, and commands.
6. **Color themes.** Dark, Light, OLED black, Terminal green -- consistent across keyboard and terminal.
7. **One-time purchase.** At $4, KeyJawn would undercut every subscription-based competitor (Blink $19.99/yr, Termius $120/yr, Prompt $19.99/yr). A one-time price is a strong selling point for a niche utility.
8. **Hybrid model.** No competitor offers both a terminal app AND a system-wide keyboard extension in one bundle. The keyboard extension provides slash commands, clipboard history, and text features in any app, while the terminal app provides full terminal key support.

---

## Recommendations

### Phase 0: Web terminal proof of concept (validate the use case)

Deploy a WebSSH relay on houseofjawn behind Cloudflare Tunnel. Fork [Sshwifty](https://github.com/nirui/sshwifty) and add KeyJawn's extra row as HTML/CSS/JS above the terminal.

- **Time to ship:** Days (basic deployment) to 1-2 weeks (with custom keyboard)
- **Cost:** $0 (runs on existing infrastructure)
- **Purpose:** Validate the iOS workflow before investing in a native app
- Works on any phone, not just iOS -- becomes a universal web fallback
- If the workflow holds up after a week of daily use, proceed to Phase 1

### Phase 1: Terminal app (primary native product)

Build **KeyJawn Terminal** -- an SSH/Mosh client with KeyJawn's keyboard as the custom `inputAccessoryView`.

- Use [SwiftTerm](https://github.com/migueldeicaza/SwiftTerm) (MIT) as the terminal emulator engine
- Use [Citadel](https://github.com/orlandos-nl/Citadel) (MIT) as the SSH/SFTP library
- Study [SwiftTermApp](https://github.com/migueldeicaza/SwiftTermApp) as a complete reference implementation (MIT-licensed iOS SSH client built on SwiftTerm, open core of La Terminal)
- Cross-compile Mosh using [blinksh/build-mosh](https://github.com/blinksh/build-mosh) scripts for network-resilient connections
- Full Esc, Ctrl, Tab, arrow support via `UIKeyCommand` + accessory toolbar
- Voice-to-text via `SFSpeechRecognizer` (full access in main app)
- SFTP upload via Citadel (replaces SCP, more reliable for file transfers)
- Slash commands, themes, clipboard -- all features work
- Target iOS, iPadOS, macOS (via Catalyst), and optionally visionOS
- Direct competitor to Moshi, Blink Shell, Termius
- Note: the full QWERTY keyboard layout (`QwertyKeyboard`, `KeyboardLayout.kt`) is unnecessary -- iOS's system keyboard handles text input, you only need the terminal key bar

### Phase 2: Keyboard extension (broader reach)

Add a **keyboard extension** to the same app bundle for system-wide use.

- QWERTY layout, slash commands, alt-key long-press, themes
- Tab via `insertText("\t")`
- Clipboard history (with Full Access)
- Voice-to-text via deep-link to containing app
- Terminal keys (Esc, Ctrl, arrows) included but with caveat messaging that they only work in compatible terminal apps
- KeyboardKit framework as foundation to accelerate development

### Phase 3 (optional): Hardware accessory

ESP32-S3 Bluetooth HID dongle for universal terminal keyboard functionality.
- Companion iOS app with KeyJawn's keyboard UI
- Dongle presents as USB HID keyboard to any target device
- Works with servers, embedded systems, BIOS, Smart TVs
- Unique market position

### Development requirements

- Mac with Xcode 16+
- Apple Developer Program ($99/year)
- iOS device for testing (simulator doesn't enforce memory limits)
- Native Swift/UIKit skills (no cross-platform framework viable for the keyboard)
- SwiftTerm + Citadel for terminal app
- Sshwifty or webssh2 for web terminal proof of concept (Phase 0)

### Estimated scope

| Component | Effort estimate |
|-----------|----------------|
| Web terminal proof of concept (Phase 0) | Days to 1-2 weeks |
| Terminal app (SwiftTerm + Citadel + KeyJawn accessory bar) | Core product, 4-8 weeks |
| Mosh support (cross-compile libmoshios) | 1-2 weeks |
| Containing app (settings, host management, onboarding) | Moderate |
| Keyboard extension (QWERTY + text features) | Moderate |
| Voice input (SFSpeechRecognizer in main app) | Small |
| SFTP upload (Citadel in main app) | Moderate |
| Themes, clipboard, slash commands | Small-Medium each |
| App Store submission + review rounds | Allow 2-4 weeks |

### What NOT to do

- Do not use any cross-platform UI framework for the keyboard extension (Flutter, RN, Compose all exceed memory limits)
- Do not promise system-wide Esc/Ctrl functionality (it will not work in most apps)
- Do not try to replicate the exact Android architecture (the platforms are too different)
- Do not skip real-device testing (simulator has relaxed memory limits)
- Do not build the keyboard extension without a substantial containing app (App Store rejection)
- Do not use KMP for the initial version -- the shareable code surface (~230 lines) does not justify the build complexity
- Do not fork Blink Shell as a base -- its GPL-3.0 license requires open-sourcing derivatives, and the codebase is large and complex
