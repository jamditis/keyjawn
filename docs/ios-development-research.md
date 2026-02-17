# KeyJawn iOS Development Research

Comprehensive analysis of every viable (and non-viable) approach to bringing KeyJawn to iOS. Research conducted February 2026.

## Table of contents

- [Executive summary](#executive-summary)
- [The fundamental problem](#the-fundamental-problem)
- [Approach 1: iOS keyboard extension (native Swift)](#approach-1-ios-keyboard-extension-native-swift)
- [Approach 2: Terminal app with built-in keyboard](#approach-2-terminal-app-with-built-in-keyboard)
- [Approach 3: Hybrid -- keyboard extension + terminal app](#approach-3-hybrid----keyboard-extension--terminal-app)
- [Approach 4: Bluetooth HID hardware bridge](#approach-4-bluetooth-hid-hardware-bridge)
- [Cross-platform framework evaluation](#cross-platform-framework-evaluation)
- [Code sharing strategies](#code-sharing-strategies)
- [Code portability analysis](#code-portability-analysis)
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
| Keyboard extension (native Swift) | Partial (Tab, slash commands, text features) | Yes | Medium | Secondary |
| Terminal app with built-in keyboard | Full (Esc, Ctrl, arrows, everything) | No (in-app only) | Medium-High | **Primary** |
| Hybrid (both) | Full within app, partial system-wide | Both | High | Best long-term |
| Bluetooth HID hardware bridge | Full (any device) | Yes (hardware) | Medium | Niche/accessory |

**Bottom line:** The most impactful iOS product is a **terminal app** (SSH/Mosh client with KeyJawn's keyboard built in), optionally paired with a **keyboard extension** for the text-only features that do work system-wide. The closest existing competitor is **Moshi** (getmoshi.app), an iOS terminal app specifically designed for running Claude Code from iPhone.

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
| **Moshi** | SSH/Mosh terminal | Ctrl, Alt, Esc, Tab, arrows (inputAccessoryView) | Local Whisper model | Paid |
| **Blink Shell** | SSH/Mosh terminal | SmartKeys bar (inputAccessoryView), hardware key remapping | No | $15.99 |
| **Termius** | SSH client | Strip panel with Ctrl, Esc, Tab, arrows. Spacebar-drag arrows | No | Free + subscription |
| **a-Shell** | Local shell | Caps Lock as Escape, Ctrl+[ for Esc | No | Free |
| **iSH** | Linux emulator | Tab, Ctrl, Esc, 4-way arrow rocker | No | Free |
| **Prompt 3** (Panic) | SSH client | Custom toolbar, Clips feature | No | $19.99 |
| **La Terminal** | SSH (SwiftTerm) | Built-in terminal keys, visionOS support | No | Paid |

### Existing iOS keyboard apps

| App | Focus | Relevant features |
|-----|-------|-------------------|
| **Gboard** | General typing | Swipe, search, voice (via app switch), GIFs |
| **SwiftKey** | General typing | Swipe, AI predictions, clipboard manager |
| **Fleksy** | Privacy-first typing | No Full Access needed, fast tap typing |

### KeyJawn's differentiator

No existing iOS keyboard extension provides terminal keys. Terminal apps provide them only within their own app. KeyJawn could be the first to:
1. Offer a terminal-focused keyboard extension (even with limited terminal key support system-wide)
2. Pair it with a terminal app for full functionality
3. Potentially establish a convention with terminal app developers for handling control characters via `insertText()`

---

## Recommendations

### Phase 1: Terminal app (highest impact, most viable)

Build **KeyJawn Terminal** -- an SSH/Mosh client with KeyJawn's keyboard as the custom `inputAccessoryView`.

- Use [SwiftTerm](https://github.com/migueldeicaza/SwiftTerm) as the terminal emulator engine
- Full Esc, Ctrl, Tab, arrow support via `UIKeyCommand` + accessory toolbar
- Voice-to-text via `SFSpeechRecognizer` (full access in main app)
- SCP upload with no memory constraints
- Slash commands, themes, clipboard -- all features work
- Target iOS, iPadOS, macOS (via Catalyst), and optionally visionOS
- Direct competitor to Moshi, Blink Shell, Termius

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
- SwiftTerm integration for terminal app

### Estimated scope

| Component | Effort estimate |
|-----------|----------------|
| Terminal app (SwiftTerm + KeyJawn accessory bar) | Core product |
| SSH/Mosh connection management | Significant |
| Containing app (settings, host management, onboarding) | Moderate |
| Keyboard extension (QWERTY + text features) | Moderate |
| Voice input (SFSpeechRecognizer in main app) | Small |
| SCP upload (Citadel/Shout in main app) | Moderate |
| Themes, clipboard, slash commands | Small-Medium each |
| App Store submission + review rounds | Allow 2-4 weeks |

### What NOT to do

- Do not use any cross-platform UI framework for the keyboard extension (Flutter, RN, Compose all exceed memory limits)
- Do not promise system-wide Esc/Ctrl functionality (it will not work in most apps)
- Do not try to replicate the exact Android architecture (the platforms are too different)
- Do not skip real-device testing (simulator has relaxed memory limits)
- Do not build the keyboard extension without a substantial containing app (App Store rejection)
