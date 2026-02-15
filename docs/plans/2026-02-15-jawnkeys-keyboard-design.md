# KeyJawn — custom Android terminal keyboard

**Date:** 2026-02-15
**Status:** Approved

## Problem

Using Claude Code via SSH from a phone (Samsung S24 Ultra -> Edge browser -> Cockpit web terminal) lacks arrow keys, Tab, Escape, and Ctrl modifiers. Standard mobile keyboards don't provide terminal-specific keys, autocorrect causes text duplication in web-based terminals (xterm.js), and there's no quick way to type LLM CLI slash commands.

## Solution

An Android Input Method Editor (IME) — a system keyboard replacement — with a dedicated terminal key row above a basic QWERTY layout. Includes LLM CLI slash command shortcuts and a built-in image upload feature for sharing screenshots with Claude Code.

## Architecture

- Android app built in Kotlin
- Subclasses `InputMethodService` — registers as a system keyboard in Android Settings
- Single XML layout: terminal extra row + QWERTY keyboard
- `KeySender` utility translates button taps into `KeyEvent` objects via `InputConnection`
- JSch library for SCP file transfer (image upload)
- Per-app settings stored in SharedPreferences
- Slash command registry loaded from bundled JSON, user-extensible

## Target user

People who SSH into servers from their phone to use LLM CLI tools. The keyboard is optimized for the workflow where you type natural language 90% of the time and need terminal escape hatches 10% of the time.

Primary test case: Claude Code via Cockpit web terminal on a Samsung S24 Ultra.

## Extra row layout (9 keys)

```
+-----+-----+------+-----+-----+-----+-----+-----+-----+
| Esc | Tab | Ctrl |  <  |  v  |  ^  |  >  | Upl | Mic |
+-----+-----+------+-----+-----+-----+-----+-----+-----+
```

Mic is the rightmost key, matching its familiar position on Gboard (far right, above the number row).

### Key behaviors

| Key | Tap | Long-press |
|-----|-----|------------|
| Esc | KEYCODE_ESCAPE | -- |
| Tab | KEYCODE_TAB | -- |
| Ctrl | Toggle modifier (arm for next keypress) | Sticky lock (stays on until tapped again) |
| Arrow keys | KEYCODE_DPAD_LEFT/DOWN/UP/RIGHT | Key repeat while held |
| Mic | Launch voice-to-text (Android SpeechRecognizer) | -- |
| Upload | Open Android photo/file picker | Open host picker / settings |

### Ctrl modifier behavior

- Tap Ctrl: highlights (armed). Next QWERTY keypress sends as Ctrl+key combo. Auto-releases.
- Double-tap / long-press Ctrl: locks on for multiple combos. Tap again to release.
- Visual: key turns amber when armed, red when locked.

## Voice-to-text input

Voice input is essential — many users (especially on phones) rely on speech-to-text for natural language input. Since KeyJawn replaces the system keyboard entirely, we must provide voice input that matches what Gboard offers.

### How it works

1. Tap the Mic key in the extra row
2. Android's speech recognition UI appears (same engine Gboard uses)
3. Speak your message
4. Recognized text is inserted at the cursor via `commitText()`

### Technical approach

Use Android's `SpeechRecognizer` API or launch `ACTION_RECOGNIZE_SPEECH` intent. Both trigger Google's speech recognition service (or Samsung's, depending on the device). The IME receives the transcribed text in `onActivityResult` and commits it to the input connection.

**Permission:** `android.permission.RECORD_AUDIO` required in manifest.

### Behavior

- Mic key has a distinct visual (e.g., lighter color or mic icon) to match familiar positioning
- While listening, the mic key pulses or changes color to indicate active recording
- Partial results are shown in a suggestion bar above the keyboard (optional, v1 can just insert final result)
- Works with autocorrect state — if AC is on, the transcribed text goes through spell check

## LLM CLI slash command shortcuts

### The problem

LLM CLI tools use slash commands (`/help`, `/commit`, `/clear`, `/review-pr`, etc.) that are tedious to type on a phone. The `/` key is buried in the symbols layer, and command names are long.

### Solution: slash command quick-insert

A `/` key on the QWERTY layer triggers a popup of common commands. Tapping a command inserts the full text.

**How it works:**
1. Tap the `/` key (replaces `.` in the bottom row, or accessible via long-press on `.`)
2. A popup appears above the keyboard showing recently used and common commands
3. Tap a command to insert it (e.g., `/commit`, `/help`, `/clear`)
4. The popup dismisses and the text is inserted at the cursor
5. Most-recently-used commands float to the top

### Default command set (bundled)

**Claude Code:**
- `/help`, `/clear`, `/commit`, `/review-pr`, `/compact`, `/cost`, `/doctor`

**Aider:**
- `/add`, `/drop`, `/run`, `/test`, `/diff`, `/undo`, `/commit`

**General:**
- `/exit`, `/quit`, `/help`

### User-customizable

Users can add custom slash commands in KeyJawn settings. The app ships with built-in command sets per tool that users can enable/disable.

### Smart detection (future)

Detect which LLM CLI tool is running and show relevant commands. Not in v1.

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
|sym|         space             | / | Ret |
+---+---------------------------+---+-----+
```

Note: `.` replaced by `/` in the bottom row. Period available via long-press on `/`, or in the symbols layer.

### Layer 2 — Uppercase (shift)

Same layout, capitalized. Single tap shift = one capital then revert. Double-tap = caps lock.

### Layer 3 — Symbols (sym)

```
+---+---+---+---+---+---+---+---+---+---+
| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 0 |
+---+---+---+---+---+---+---+---+---+---+
| - | _ | = | + | . | \ | | | ~ | ` |   |
+---+---+---+---+---+---+---+---+---+---+
| ! | @ | # | $ | % | & | * | ( | ) |   |
+---+---+---+---+---+---+---+---+---+---+
|abc|         space             | / | Ret |
+---+---------------------------+---+-----+
```

Pipe, tilde, backtick, period available in symbols layer.

## Autocorrect

- **Off by default** — web terminals (xterm.js) mishandle autocorrect's delete-then-replace pattern, causing text duplication
- **Toggle via long-press spacebar** — small "AC" badge on spacebar shows current state
- **Per-app memory** — KeyJawn remembers autocorrect preference per app (off for Edge/Cockpit, on for messaging apps)

## Image upload feature

### Purpose

1. Share screenshots with Claude Code during a session
2. Quick file transfer from phone gallery to server

### Setup (one-time)

- Configure in KeyJawn settings (gear icon via long-press on upload button)
- Server connection: hostname/IP, username, SSH private key
- Upload directory: `/tmp/keyjawn/` (default, auto-created on first use)
- Multiple hosts supported, switchable via long-press on upload button

### Upload flow

1. Tap upload button -> Android photo picker opens
2. Select image -> KeyJawn SCPs it to active server
3. Toast confirms: "Uploaded -> /tmp/keyjawn/img-20260215-1423.png"
4. File path inserted at cursor in terminal
5. Tell Claude Code to read that path

### Connection details (example)

- Server: IP or hostname via Tailscale or direct
- Username: configurable
- Auth: SSH key pair (generated in-app or imported)

## Visual design

- Dark theme matching terminal aesthetics
- Muted gray keys, white text
- Ctrl key: amber when armed, red when locked
- AC badge on spacebar: visible when autocorrect is on
- Slash command popup: dark background, rounded corners, scrollable list
- Extra row visually separated from QWERTY area with a subtle divider

## Technical details

- **Voice input:** Android `SpeechRecognizer` API or `ACTION_RECOGNIZE_SPEECH` intent
- **Min SDK:** 26 (Android 8.0) — covers all modern Android devices
- **SSH library:** JSch (com.github.mwiede:jsch) for SCP transfers
- **Key events:** Sent via `InputConnection.sendKeyEvent()` for terminal keys, `commitText()` for character input
- **Autocorrect:** Android's `TextServicesManager` for spell checking when enabled
- **Storage:** SharedPreferences for per-app settings, EncryptedSharedPreferences for SSH credentials
- **Slash commands:** Bundled JSON registry, user additions stored in SharedPreferences

## Scope boundaries

### In scope (v1)

- Extra terminal key row (Esc, Tab, Ctrl, arrows, mic, upload)
- Basic QWERTY with shift/symbols layers
- Slash command quick-insert with popup
- Autocorrect toggle with per-app memory
- Image upload via SCP with path insertion
- Multi-host configuration
- Dark theme

### Out of scope (future)

- Smart LLM tool detection (auto-show relevant commands)
- Alt modifier key
- Customizable key layouts / reordering
- Swipe typing
- Themes / color customization
- Clipboard history
- Macro / snippet support
