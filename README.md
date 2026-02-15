# JawnKeys

A custom Android keyboard designed for using LLM CLI agents (Claude Code, etc.) from your phone.

Most mobile keyboards lack the keys you need for terminal work — arrow keys, Tab, Escape, Ctrl modifiers. JawnKeys adds a dedicated terminal key row above a clean QWERTY layout, plus a built-in image upload feature that SCPs screenshots to your server and inserts the file path at your cursor.

## What it does

- **Terminal key row:** Esc, Tab, Ctrl (toggle modifier), arrow keys, and an image upload button
- **Basic QWERTY:** Three layers (lowercase, uppercase, symbols) with no autocorrect by default — because autocorrect breaks web-based terminals
- **Autocorrect toggle:** Long-press spacebar to enable/disable per app. Off for terminals, on for chat apps.
- **Image upload via SCP:** Tap the upload button, pick a photo, and JawnKeys SCPs it to your server and types the file path into the terminal. Useful for sharing screenshots with Claude Code.
- **Multi-host support:** Configure multiple SSH servers and switch between them.

## Who it's for

Anyone who SSHs into a server from their phone to use a CLI-based AI assistant. The keyboard is optimized for the workflow where you're typing natural language 90% of the time and need terminal escape hatches 10% of the time.

Built for and tested with:
- Claude Code via Cockpit web terminal (Edge browser on Android)
- Direct SSH terminal apps (Termux, JuiceSSH, ConnectBot)

## Status

Design phase. See [design doc](docs/plans/2026-02-15-jawnkeys-keyboard-design.md) for the full specification.

## Requirements

- Android 8.0+ (API 26+)
- Kotlin / Gradle build system

## License

MIT
