# KeyJawn: Custom mobile keyboard for LLM CLI usage 

![KeyJawn branding graphic](https://i.imgur.com/7MC8v1C.png)

A custom Android keyboard designed for using LLM CLI agents (Claude Code, Aider, etc.) from your phone.

Most mobile keyboards lack the keys you need for terminal work — arrow keys, Tab, Escape, Ctrl modifiers. KeyJawn adds a dedicated terminal key row above a clean QWERTY layout, plus LLM-specific shortcuts and a built-in image upload feature that SCPs screenshots to your server and inserts the file path at your cursor.
## What it does

- **Terminal key row:** Esc, Tab, Ctrl (toggle modifier), arrow keys, image upload, and voice-to-text — all always visible
- **Voice-to-text:** Mic button on the far right of the extra row (where you expect it). Tap to dictate — uses the same speech recognition engine as Gboard.
- **LLM CLI shortcuts:** Quick-insert for `/` commands, common prompts, and slash-command prefixes used by Claude Code, Aider, and other LLM CLI tools
- **Basic QWERTY:** Three layers (lowercase, uppercase, symbols) with no autocorrect by default — because autocorrect breaks web-based terminals
- **Autocorrect toggle:** Long-press spacebar to enable/disable per app. Off for terminals, on for chat apps.
- **Image upload via SCP:** Tap the upload button, pick a photo, and KeyJawn SCPs it to your server and types the file path into the terminal. Useful for sharing screenshots with Claude Code.
- **Multi-host support:** Configure multiple SSH servers and switch between them.

## Who it's for

Anyone who SSHs into a server from their phone to use a CLI-based AI assistant. The keyboard is optimized for the workflow where you're typing natural language 90% of the time and need terminal escape hatches 10% of the time.

Built for and tested with:
- Claude Code via Cockpit web terminal (Edge browser on Android)
- Direct SSH terminal apps (Termux, JuiceSSH, ConnectBot)
- Any LLM CLI tool that uses slash commands (Aider, Open Interpreter, etc.)

## Versions

KeyJawn ships in two flavors:

| | Full | Lite |
|---|---|---|
| Terminal keys, voice, slash commands | Yes | Yes |
| SCP image upload | Yes | No |
| Permissions | INTERNET, READ_MEDIA_IMAGES, RECORD_AUDIO | RECORD_AUDIO |
| SSH credential storage | Yes | No |
| Application ID | `com.keyjawn` | `com.keyjawn.lite` |

**Lite** is for users who want terminal keys and voice input without granting network or storage permissions. **Full** adds SCP upload for sharing screenshots with CLI tools on a remote server.

## Status

Design phase. See [design doc](docs/plans/2026-02-15-jawnkeys-keyboard-design.md) for the full specification.

## Requirements

- Android 8.0+ (API 26+)
- Kotlin / Gradle build system

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## License

MIT
