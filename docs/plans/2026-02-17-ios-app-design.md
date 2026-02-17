# KeyJawn iOS app design

Hybrid iOS app: SSH terminal with built-in KeyJawn keyboard + system-wide keyboard extension, shipped as a single App Store download.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Shared framework + two targets | Standard Apple pattern. Single source of truth for shared code, lean extension target. |
| SSH library | Citadel (pure Swift, MIT, SwiftNIO) | No C dependencies, SFTP built-in, same ecosystem as SwiftTerm. |
| Terminal engine | SwiftTerm (MIT) | Pure Swift VT100/Xterm emulator. Used by La Terminal and Secure Shellfish. |
| Mosh support | Deferred to later release | SSH first. Mosh adds 1-2 weeks of cross-compilation work. |
| Code sharing | Native Swift (no KMP/Rust) | Only ~230 lines of portable logic. Cross-platform overhead not justified for v1. |
| Pricing | Free + $3.99 one-time IAP | Mirrors Android lite/full strategy. External Stripe link as alternative to IAP. |
| App name | KeyJawn | Single brand. Contains both terminal and keyboard extension. |
| Deployment target | iOS 17.0+ / iPadOS 17.0+ | Covers ~95% active devices. Modern SwiftUI + StoreKit 2 APIs. |

---

## Project structure

```
keyjawn/ios/
├── KeyJawn.xcodeproj
├── KeyJawnKit/                       Shared static framework (local Swift Package)
│   ├── Package.swift
│   └── Sources/
│       ├── Models/
│       │   ├── CtrlState.swift           3-state modifier (OFF/ARMED/LOCKED)
│       │   ├── KeyboardLayout.swift      Key, KeyOutput, Row, Layer + all layout definitions
│       │   ├── AltKeyMappings.swift      Long-press character -> accent variants map
│       │   ├── HostConfig.swift          SSH host data model
│       │   └── SlashCommand.swift        Command + command set models
│       ├── Services/
│       │   ├── SlashCommandRegistry.swift   Command loading, MRU tracking, custom sets
│       │   ├── ClipboardHistoryManager.swift   30 items (pro) / 10 items (free), 15 pinned (pro)
│       │   ├── ThemeManager.swift           4 themes (Dark, Light, OLED, Terminal)
│       │   ├── AppPreferences.swift         UserDefaults wrapper (App Group suite)
│       │   ├── HostStorage.swift            Keychain-backed host config CRUD
│       │   └── KnownHostsManager.swift      SSH fingerprint TOFU verification
│       └── Views/
│           ├── ExtraRowView.swift           Esc, Tab, Ctrl, arrows, Clipboard, Upload, Mic
│           ├── SlashCommandPopup.swift       MRU-ordered command picker
│           ├── AltKeyPopup.swift            Long-press accent character popup
│           ├── ClipboardPanel.swift          History + pinned items overlay
│           ├── MenuPanel.swift              Theme picker, padding slider, toggles
│           └── KeyPreview.swift             Floating key label on press
│
├── KeyJawn/                          Main app target
│   ├── App/
│   │   ├── KeyJawnApp.swift              SwiftUI App entry point
│   │   └── ContentView.swift             Tab navigation (Terminal, Hosts, Settings)
│   ├── Terminal/
│   │   ├── TerminalViewController.swift  UIKit: SwiftTerm + inputAccessoryView
│   │   ├── TerminalHostingView.swift     SwiftUI wrapper for UIKit terminal
│   │   ├── KeyJawnAccessoryView.swift    inputAccessoryView with ExtraRowView
│   │   └── SSHManager.swift              Citadel SSH connection lifecycle
│   ├── Voice/
│   │   └── VoiceInputHandler.swift       SFSpeechRecognizer + waveform
│   ├── Upload/
│   │   └── SFTPUploader.swift            Citadel SFTP upload + path insertion
│   ├── Hosts/
│   │   ├── HostListView.swift            SwiftUI list of saved connections
│   │   └── HostEditView.swift            Add/edit SSH host form
│   ├── Settings/
│   │   ├── SettingsView.swift            Preferences, command sets, about
│   │   └── OnboardingView.swift          First-launch + keyboard enable guide
│   └── Store/
│       └── StoreManager.swift            StoreKit 2 IAP + external Stripe link
│
├── KeyJawnKeyboard/                  Keyboard extension target
│   ├── KeyboardViewController.swift      UIInputViewController entry point
│   ├── QwertyKeyboardView.swift          UIKit: 4-layer QWERTY (UIStackView + UIButton)
│   ├── NumberRowView.swift               UIKit: number row with long-press symbols
│   ├── SpacebarCursorController.swift    Pan gesture -> adjustTextPosition
│   └── Info.plist                        NSExtension config, RequestsOpenAccess
│
├── Shared/                           App Group shared data
└── Assets.xcassets                    Shared asset catalog
```

**KeyJawnKit** is a local Swift Package compiled as a static library. Both targets import it. Memory-heavy dependencies (SwiftTerm, Citadel, SFSpeechRecognizer) live only in the main app target.

**App Group** `group.com.keyjawn` enables shared UserDefaults and file storage between targets.

---

## Terminal app

### View hierarchy

```
┌─────────────────────────────────────┐
│         SwiftTerm TerminalView      │  Full-screen terminal output
│         (UIView, first responder)   │  VT100/Xterm emulation
│                                     │  Tap to position cursor
│                                     │  Pinch to resize font
├─────────────────────────────────────┤
│  ExtraRowView (inputAccessoryView)  │  Esc | Tab | Clip | Ctrl | arrows | Upload | Mic
├─────────────────────────────────────┤
│     iOS system keyboard             │  Standard text input
└─────────────────────────────────────┘
```

The terminal app does NOT need a custom QWERTY keyboard. iOS's system keyboard handles regular text input. KeyJawn provides the extra row as an `inputAccessoryView` above the system keyboard.

### SSH connection flow

```
User taps host -> SSHManager.connect(host)
  -> Citadel SSHConnection(host, port, authenticationMethod)
  -> KnownHostsManager.verify(fingerprint)
     -> First connection: TOFU prompt ("Trust this host?")
     -> Changed key: reject + warning
  -> requestPTY(term: "xterm-256color", cols, rows)
  -> requestShell()
  -> Bidirectional pipe:
     Terminal input -> SSH channel write
     SSH channel read -> SwiftTerm.feed(data)
```

**Auth methods:** password, SSH key (Ed25519/RSA stored in iOS Keychain), key generation within app.

### Extra row (9 slots)

| Slot | Default | Terminal app action | Keyboard extension action |
|------|---------|--------------------|----|
| 0 | Esc | `terminal.send([0x1b])` | `insertText("\u{1b}")` (limited) |
| 1 | Tab | `terminal.send([0x09])` | `insertText("\t")` (works) |
| 2 | Clipboard | Paste into terminal stream | `insertText(selectedItem)` |
| 3 | Ctrl | 3-state modifier, modifies next byte | Visual indicator only in extension |
| 4-7 | arrows | ANSI sequences (`\u{1b}[A/B/C/D`) | `adjustTextPosition` for left/right, heuristic for up/down |
| 8 | Upload | SFTP via Citadel | Deep-link to main app |
| 9 | Mic | SFSpeechRecognizer | Deep-link to main app |

- Arrow keys repeat on long-press
- Ctrl+arrow sends modified ANSI (`\u{1b}[1;5D` for Ctrl+Left)
- Ctrl+C sends `0x03`, Ctrl+D sends `0x04` (character & 0x1f)
- Slots 0-2 customizable via settings

### SFTP upload

```
Tap Upload -> PHPickerViewController
  -> Selected image -> resize if needed
  -> SSHManager.activeConnection.sftpClient
  -> SFTPClient.upload(localFile, to: remotePath/timestamp.jpg)
  -> terminal.send(text: remotePath)
```

### Voice input

```
Tap Mic -> VoiceInputHandler.start()
  -> SFSpeechRecognizer.requestAuthorization()
  -> AVAudioEngine tap -> recognition request
  -> Partial results as waveform overlay
  -> Final result -> terminal.send(text: transcription)
```

### Hardware keyboard support

`UIKeyCommand` overrides capture Esc, Ctrl+key, arrow keys, Tab when an external keyboard is connected. Caps Lock -> Esc remapping option.

---

## Keyboard extension

### Layout

```
┌─────────────────────────────────────┐
│  ExtraRowView                       │  Esc | Tab | Clip | Ctrl | arrows | / | Mic
├─────────────────────────────────────┤
│  NumberRowView                      │  1  2  3  4  5  6  7  8  9  0
├─────────────────────────────────────┤
│  QwertyKeyboardView                 │  4 layers: lowercase, uppercase, symbols, symbols2
│                                     │  Globe button (required) bottom-left
│                                     │  Spacebar cursor drag
│                                     │  Double-tap space -> period
└─────────────────────────────────────┘
```

### Feature compatibility

**Works fully:** QWERTY + layers, number row, shift (OFF/SINGLE/LOCKED), Tab, slash commands, alt-key long-press, spacebar cursor drag, double-tap space to period, auto-capitalize, themes (pro), clipboard history (pro), haptic (Full Access).

**Limited:** Left/right arrows (cursor movement only, not key events), up/down arrows (heuristic), Esc (`insertText("\u{1b}")` ignored by most apps), Ctrl (not possible in extensions).

**Via main app:** voice input, SFTP upload (deep-link using `keyjawn://` URL scheme).

### Implementation

- Pure UIKit (no SwiftUI in extension) for memory efficiency
- `UIStackView` rows of `KeyButton: UIButton` subviews
- Layout data from `KeyboardLayout` in KeyJawnKit
- Long-press on character keys shows `AltKeyPopup` with accent variants
- Globe button calls `advanceToNextInputMode()` (Apple requirement)
- Works without Full Access for basic typing

### Memory budget

Target: sustained usage under 25 MB (safe margin below 30-48 MB ceiling).

| Component | Estimated |
|-----------|-----------|
| UIKit keyboard views | ~8-12 MB |
| KeyJawnKit models + services | ~2-3 MB |
| Theme assets (colors only) | ~1 MB |
| Clipboard history | ~1 MB |
| Slash command registry | <1 MB |
| **Total** | **~13-18 MB** |

### App Store compliance

1. Globe button present and functional
2. Works without Full Access (basic typing, layers, Tab, slash commands)
3. Number/decimal keyboard types detected and handled
4. No prohibited APIs (`UIApplication.shared`, `openURL` from extension)

---

## Data sharing and persistence

### App Group (`group.com.keyjawn`)

```
group.com.keyjawn/
├── Library/Preferences/
│   └── group.com.keyjawn.plist     Shared UserDefaults suite
└── Library/Application Support/
    └── KeyJawn/
        ├── clipboard-history.json   Clipboard items
        └── custom-commands.json     User-created slash command sets
```

### Storage map

| Data | Storage | Shared? |
|------|---------|---------|
| Theme selection | UserDefaults (App Group) | Yes |
| Clipboard history + pins | JSON file (App Group) | Yes |
| Slash command MRU | UserDefaults (App Group) | Yes |
| Custom command sets | JSON file (App Group) | Yes |
| SSH host configs | iOS Keychain (main app) | No |
| Known hosts fingerprints | iOS Keychain (main app) | No |
| IAP entitlement (`isPro`) | UserDefaults (App Group) | Yes |
| UI toggles (haptic, autocorrect, padding) | UserDefaults (App Group) | Yes |
| Extra row slot config | UserDefaults (App Group) | Yes |

---

## Theme system

Four themes defined as `Theme` structs in KeyJawnKit:

| Theme | Keyboard bg | Key bg | Key text | Terminal bg |
|-------|------------|--------|----------|-------------|
| Dark | #1a1a1a | #3a3a3a | #ffffff | #1a1a1a |
| Light | #d1d5db | #ffffff | #000000 | #ffffff |
| OLED | #000000 | #1a1a1a | #ffffff | #000000 |
| Terminal | #0a1a0a | #1a2a1a | #33ff33 | #0a1a0a |

Free users: Dark only. Pro: all four.

---

## IAP gating

| Feature | Free | Pro ($3.99) |
|---------|------|-------------|
| SSH connections | 1 saved host | Unlimited |
| Terminal + extra row | Yes | Yes |
| Themes | Dark only | All 4 |
| Clipboard history | 10 items, no pinning | 30 items, 15 pinned |
| SFTP upload | No | Yes |
| Voice input | No | Yes |
| Slash commands | Built-in only | Built-in + custom |
| Keyboard extension | Basic QWERTY | Full features |

StoreKit 2 for IAP. External Stripe payment link as alternative (post-Epic ruling). `StoreManager` writes `isPro` to shared UserDefaults.

---

## Quick-key (context-aware)

Extra row quick-key adapts to input context:
- Email field -> `@`
- URL field -> `/`
- General text / terminal -> `/`

Detected via `textDocumentProxy.keyboardType` in extension, always `/` in terminal.

---

## Error handling

| Scenario | Behavior |
|----------|----------|
| SSH connection fails | Alert with error, retry button |
| Host key changed | Block connection, show fingerprint warning, require explicit trust |
| Extension killed for memory | iOS kills silently. Mitigation: stay under memory ceiling. |
| SFTP upload fails | Toast in terminal with error, retry available |
| Voice recognition fails | Stop waveform, show brief error, don't insert partial text |
| IAP purchase fails | StoreKit 2 handles retry/restore. "Restore purchases" in settings. |
| App Group data corruption | Validate JSON on read, reset to defaults if malformed |

---

## Testing

### Unit tests (XCTest, KeyJawnKit)

- CtrlState transitions (OFF/ARMED/LOCKED, consumption, double-tap)
- KeyboardLayout layer switching (4 layers, shift/symbol toggling)
- AltKeyMappings coverage (every key returns correct variants)
- SlashCommandRegistry MRU ordering, custom set CRUD, max 10 MRU
- ClipboardHistoryManager limits (10 free, 30 pro, 15 pinned, eviction)
- ThemeManager color correctness per theme, pro gating
- HostConfig validation (hostname required, port range)
- KnownHostsManager TOFU accept, changed key rejection

### Integration tests (main app)

- SSH connection -> PTY -> command -> verify output in SwiftTerm
- SFTP upload round-trip
- StoreKit 2 IAP using Xcode StoreKit Testing (sandbox)
- Deep-link handling (`keyjawn://voice`, `keyjawn://upload`)

### UI tests (XCUITest)

- Keyboard extension: enable, type, verify output
- Extra row: tap keys, verify behavior
- Slash command popup: trigger, select, verify insertion
- Theme switching: change theme, verify color update

### Real device testing (required)

- Simulator doesn't enforce extension memory limits
- Test keyboard enable flow (Settings -> General -> Keyboards -> Add)
- Test on oldest supported device

---

## App Store submission

**Metadata:** Name "KeyJawn", subtitle "Terminal keyboard for CLI agents", category Productivity.

**Keywords:** terminal, keyboard, ssh, cli, escape, ctrl, developer, coding, claude

**Review notes:** Explain terminal keyboard use case. Demo video showing keyboard working without Full Access. Globe button present. No ads in extension. Privacy nutrition label for clipboard + SSH credentials (stored locally).

**Expect 2-4 rejection rounds.** Budget 2-4 weeks for the review cycle.

---

## Dependencies

| Package | Target | Purpose | License |
|---------|--------|---------|---------|
| SwiftTerm | Main app | Terminal emulation (VT100/Xterm) | MIT |
| Citadel | Main app | SSH client + SFTP | MIT |
| StoreKit 2 | Main app | In-app purchases | Apple framework |
| SFSpeechRecognizer | Main app | Voice-to-text | Apple framework |

No third-party dependencies in the keyboard extension target. KeyJawnKit uses only Foundation and UIKit.

---

## What this design does NOT include (deferred)

- Mosh support (UDP, cross-compilation needed)
- Mac Catalyst build
- visionOS support
- KeyboardKit framework (may evaluate later for autocomplete/dictation)
- KMP or Rust code sharing
- Web terminal (Phase 0 from research doc)
- Hardware Bluetooth HID accessory
