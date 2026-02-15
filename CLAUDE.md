# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

KeyJawn is a custom Android keyboard (InputMethodService) for using LLM CLI agents from a phone. It provides terminal keys (Esc, Tab, Ctrl, arrows), voice-to-text, slash command shortcuts, and SCP image upload — all in a dedicated row above a standard QWERTY layout.

## Build commands

```bash
# Build debug APKs (both flavors)
./gradlew assembleDebug

# Build a single flavor
./gradlew assembleFullDebug
./gradlew assembleLiteDebug

# Run all unit tests
./gradlew testFullDebugUnitTest
./gradlew testLiteDebugUnitTest

# Run a single test class
./gradlew testFullDebugUnitTest --tests "com.keyjawn.CtrlStateTest"

# Install on connected device
adb install app/build/outputs/apk/full/debug/app-full-debug.apk
```

## Build environment

- Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0
- JDK 17, compileSdk 35, minSdk 26
- CI runs on GitHub Actions (`.github/workflows/build.yml`) — builds both flavors, uploads APK artifacts, creates releases on version tags

## Product flavors

The app ships as two flavors differentiated by the `feature` dimension:

- **full** (`com.keyjawn`) — includes SCP upload via JSch, requires INTERNET + READ_MEDIA_IMAGES + RECORD_AUDIO
- **lite** (`com.keyjawn.lite`) — no SCP, no network permissions, only RECORD_AUDIO

The flavor split is implemented through source sets:
- `app/src/main/` — shared code (service, keyboard layouts, key sender, prefs, voice, slash commands)
- `app/src/full/` — `ScpUploader.kt` (JSch-based SCP) and `UploadHandler.kt` (real implementation)
- `app/src/lite/` — `UploadHandler.kt` (no-op stub, `isAvailable = false`)
- `app/src/testFull/` — tests for full-only classes (ScpUploader)

Both flavors share the same `UploadHandler` interface. The full version does actual SCP; the lite version is a no-op.

## Architecture

All source is in `com.keyjawn` — flat package, no sub-packages.

**Service layer:**
- `KeyJawnService` — the `InputMethodService` entry point. Inflates the keyboard view, wires up all components, manages lifecycle.

**Input handling:**
- `KeySender` — sends key events and text to the active `InputConnection`. All key output flows through here.
- `CtrlState` — state machine for the Ctrl modifier (OFF -> ARMED on tap, LOCKED on long-press, ARMED auto-resets after one keypress).
- `ExtraRowManager` — wires the terminal key row (Esc, Tab, Ctrl, arrows, upload, mic) to `KeySender`. Owns `CtrlState`.

**Keyboard layout:**
- `KeyboardLayout.kt` — defines `Key`, `KeyOutput` (sealed class), `Row`, `Layer` types and three static layers (lowercase, uppercase, symbols) in `KeyboardLayouts`.
- `QwertyKeyboard` — dynamically builds the QWERTY grid from `KeyboardLayouts` layers, handles layer switching (shift, symbols), dispatches key presses through `KeySender`.

**Features:**
- `SlashCommandRegistry` + `SlashCommandPopup` — slash command quick-insert (triggered by `/` key on symbols layer). Registry loads commands, popup presents them.
- `VoiceInputHandler` — speech recognition using Android's `SpeechRecognizer`. Wired to mic button in extra row.
- `UploadHandler` / `ScpUploader` — SCP image upload (full flavor only). `HostConfig` + `HostStorage` manage SSH server credentials (encrypted via AndroidX security-crypto).
- `NumberRowManager` — wires the dedicated number row (0-9) above the QWERTY grid. Long-press types the shifted symbol (!@#$%^&*()).
- `AltKeyMappings` — static map of long-press alternate characters keyed by primary key label. Covers accented vowels, common letters (n, c, s, y), and punctuation variants. Uppercase variants auto-derived from lowercase lookups.
- `AltKeyPopup` — small horizontal `PopupWindow` anchored above the pressed key. Shows one button per alt character. For single-alt keys (like number row), sends directly without a popup.
- `ClipboardHistoryManager` + `ClipboardPopup` — clipboard history tracking and paste popup. Popup has rounded corners, section header, dividers between items, and muted empty state.
- `RepeatTouchListener` — fires repeated key events while arrow buttons are held down.
- `AppPrefs` — per-app autocorrect toggle (long-press spacebar). Stores preferences per package name. Defaults to OFF.

**Settings:**
- `SettingsActivity` — host management UI for configuring SSH servers.

## Key patterns

- `InputConnection` is accessed via lambda providers (`() -> InputConnection?`) since the active connection changes.
- Ctrl modifier uses a three-state machine: OFF, ARMED (one-shot), LOCKED (sticky until toggled off).
- The `slash` key on the symbols layer has `KeyOutput.Slash` output and triggers the slash command popup. The `/` key on lower/upper layers is `KeyOutput.Character("/")` and just types `/`. Long-pressing the `/` character key types `.` instead.
- Long-press behavior on QWERTY keys: looks up `AltKeyMappings.getAlts(key.label)`. If one alt, sends it directly. If multiple, shows `AltKeyPopup`. Keys with existing long-press handlers (Space, Slash) are skipped because they use different `KeyOutput` subtypes.
- Tests use Robolectric for Android framework classes and Mockito-Kotlin for mocking.
- Some test classes have pre-existing failures (SlashCommandRegistryTest file-not-found, QwertyKeyboardTest resource-not-found). These are not regressions.

## Code style

- Kotlin with standard Android conventions
- No emojis in source code, logs, or UI text
- Sentence case for all UI strings (not Title Case)
