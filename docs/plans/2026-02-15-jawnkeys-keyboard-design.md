# JawnKeys — custom Android terminal keyboard

**Date:** 2026-02-15
**Status:** Approved

## Problem

Using Claude Code via SSH from a phone (Samsung S24 Ultra → Edge browser → Cockpit web terminal) lacks arrow keys, Tab, Escape, and Ctrl modifiers. Standard mobile keyboards don't provide terminal-specific keys, and autocorrect causes text duplication in web-based terminals (xterm.js).

## Solution

An Android Input Method Editor (IME) — a system keyboard replacement — with a dedicated terminal key row above a basic QWERTY layout. Includes an image upload feature for sharing screenshots with Claude Code.

## Architecture

- Android app built in Kotlin
- Subclasses `InputMethodService` — registers as a system keyboard in Android Settings
- Single XML layout: terminal extra row + QWERTY keyboard
- `KeySender` utility translates button taps into `KeyEvent` objects via `InputConnection`
- JSch library for SCP file transfer (image upload)
- Per-app settings stored in SharedPreferences

## Target user

Joe Amditis, SSHing into houseofjawn (100.122.208.15) and officejawn (100.84.214.24) via Tailscale from a Samsung S24 Ultra. Primary use: conversing with Claude Code through a terminal. Occasionally uses other terminal/dev apps.

## Extra row layout (8 keys)

```
+-----+-----+------+-----+-----+-----+-----+-----+
| Esc | Tab | Ctrl |  <  |  v  |  ^  |  >  | Upl |
+-----+-----+------+-----+-----+-----+-----+-----+
```

### Key behaviors

| Key | Tap | Long-press |
|-----|-----|------------|
| Esc | KEYCODE_ESCAPE | -- |
| Tab | KEYCODE_TAB | -- |
| Ctrl | Toggle modifier (arm for next keypress) | Sticky lock (stays on until tapped again) |
| Arrow keys | KEYCODE_DPAD_LEFT/DOWN/UP/RIGHT | Key repeat while held |
| Upload | Open Android photo/file picker | Open host picker / settings |

### Ctrl modifier behavior

- Tap Ctrl: highlights (armed). Next QWERTY keypress sends as Ctrl+key combo. Auto-releases.
- Double-tap / long-press Ctrl: locks on for multiple combos. Tap again to release.
- Visual: key turns amber when armed, red when locked.

## Main QWERTY layout

Three layers toggled by shift/sym keys.

### Layer 1 — Lowercase (default)

```
+---+---+---+---+---+---+---+---+---+---+
| q | w | e | r | t | y | u | i | o | p |
+---+---+---+---+---+---+---+---+---+---+
| a | s | d | f | g | h | j | k | l |   |
+---+---+---+---+---+---+---+---+---+---+
| ^ | z | x | c | v | b | n | m |  <x   |
+---+---+---+---+---+---+---+---+-------+
|sym|         space             | . | Ret |
+---+---------------------------+---+-----+
```

### Layer 2 — Uppercase (shift)

Same layout, capitalized. Single tap shift = one capital then revert. Double-tap = caps lock.

### Layer 3 — Symbols (sym)

```
+---+---+---+---+---+---+---+---+---+---+
| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 0 |
+---+---+---+---+---+---+---+---+---+---+
| - | _ | = | + | / | \ | | | ~ | ` |   |
+---+---+---+---+---+---+---+---+---+---+
| ! | @ | # | $ | % | & | * | ( | ) |   |
+---+---+---+---+---+---+---+---+---+---+
|abc|         space             | . | Ret |
+---+---------------------------+---+-----+
```

Pipe, tilde, backtick available in symbols layer.

## Autocorrect

- **Off by default** — web terminals (xterm.js) mishandle autocorrect's delete-then-replace pattern, causing text duplication
- **Toggle via long-press spacebar** — small "AC" badge on spacebar shows current state
- **Per-app memory** — JawnKeys remembers autocorrect preference per app (off for Edge/Cockpit, on for messaging apps)

## Image upload feature

### Purpose

1. Share screenshots with Claude Code during a session
2. Quick file transfer from phone gallery to server

### Setup (one-time)

- Configure in JawnKeys settings (gear icon via long-press on upload button)
- Server connection: hostname/IP, username, SSH private key
- Upload directory: `/tmp/jawnkeys/` (default, auto-created on first use)
- Multiple hosts supported, switchable via long-press on upload button

### Upload flow

1. Tap upload button -> Android photo picker opens
2. Select image -> JawnKeys SCPs it to active server
3. Toast confirms: "Uploaded -> /tmp/jawnkeys/img-20260215-1423.png"
4. File path inserted at cursor in terminal
5. Tell Claude Code to read that path

### Connection details (default)

- houseofjawn: 100.122.208.15 (Tailscale)
- officejawn: 100.84.214.24 (Tailscale)
- Username: jamditis
- Auth: SSH key pair (generated in-app or imported)

## Visual design

- Dark theme matching terminal aesthetics
- Muted gray keys, white text
- Ctrl key: amber when armed, red when locked
- AC badge on spacebar: visible when autocorrect is on
- Extra row visually separated from QWERTY area with a subtle divider

## Technical details

- **Min SDK:** 26 (Android 8.0) — covers S24 Ultra and all modern Samsung devices
- **SSH library:** JSch or Apache MINA SSHD for SCP transfers
- **Key events:** Sent via `InputConnection.sendKeyEvent()` for terminal keys, `commitText()` for character input
- **Autocorrect:** Android's `TextServicesManager` for spell checking when enabled
- **Storage:** SharedPreferences for per-app settings, EncryptedSharedPreferences for SSH credentials

## Scope boundaries

### In scope (v1)

- Extra terminal key row (Esc, Tab, Ctrl, arrows, upload)
- Basic QWERTY with shift/symbols layers
- Autocorrect toggle with per-app memory
- Image upload via SCP with path insertion
- Multi-host configuration
- Dark theme

### Out of scope (future)

- Alt modifier key
- Customizable key layouts / reordering
- Swipe typing
- Themes / color customization
- Clipboard history
- Macro / snippet support
