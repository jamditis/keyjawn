# KeyJawn — social context doc

This file is read by the Claude Code browser extension before each social media session.
Keep it current. Update it when features ship, things break, or the story changes.

---

## What KeyJawn is

A custom mobile keyboard for developers who use LLM CLI tools from their phone.
The main addition is a dedicated terminal key row above a standard QWERTY layout: Esc, Tab, Ctrl (one-shot tap / sticky long-press), left/right/up/down arrows, mic button, and SCP upload.
Also includes: number row with long-press symbols, slash command panel for LLM shortcuts, clipboard history, alt-character long-press, swipe gestures, per-app autocorrect toggle.

The target user: anyone who's done real terminal work on a phone and fought the default keyboard to do it.
Specifically: Claude Code, Gemini CLI, Codex CLI, OpenCode users. SSH power users. Termux regulars.

---

## Versions

### Android

**KeyJawn Lite** — free
- Download: APK at https://keyjawn.amditis.tech (GitHub releases, not Google Play yet)
- Includes: all terminal keys, voice input, clipboard history (30 items), slash commands, swipe gestures, per-app autocorrect, alt character long-press
- No network permissions

**KeyJawn Full** — $4 one-time purchase
- Buy at https://keyjawn.amditis.tech (Stripe, email delivery)
- Adds: SCP image upload, clipboard pinning, custom slash command sets, color themes (dark/light/OLED/terminal), menu panel
- Requires INTERNET + READ_MEDIA_IMAGES + RECORD_AUDIO

### iOS

- Standalone app with built-in SSH terminal (SwiftTerm + SwiftNIO SSH via Citadel)
- Companion keyboard extension adds terminal keys to any app
- Currently in **TestFlight beta**: https://testflight.apple.com/join/8vMqguKK (50 spots)
- **App Store review in progress** — submitted, currently under review

---

## Current version

<!-- Update this section whenever a new version ships -->

**Android:** v1.3.0
**iOS:** v1.0 (build in review)

### Recent changes
<!-- Add entries here as things ship. Newest first. -->

- Google Play internal testing set up (7 testers). Production launch pending.
- iOS App Store submission in review.
- Stripe + R2 purchase/delivery pipeline live.

---

## Pain points KeyJawn solves

Use these for post ideas. Be specific, not generic.

- No Esc key on stock Android keyboards. Try using vim, tmux, or any terminal tool without Esc.
- Ctrl modifier is completely absent on AOSP keyboards. Ctrl+C, Ctrl+D, Ctrl+Z all require workarounds.
- Arrow keys on stock keyboards require switching to a symbol layer, breaking your flow.
- Voice input on Android doesn't work inside SSH/terminal apps by default — KeyJawn's mic button handles this at the keyboard level, so it works in Termux, JuiceSSH, Cockpit web terminal, etc.
- Typing `/compact` or `/clear` into Claude Code by hand every session gets old fast. The slash command panel inserts these with one tap.
- Stock clipboard on Android doesn't persist between sessions. KeyJawn's clipboard history keeps 30 items and lets you pin ones you use constantly.
- SCP upload from phone to server required a separate app. Now it's a button in the keyboard.
- Switching between Ctrl+C and normal typing on stock keyboards requires a mode switch. KeyJawn's one-shot Ctrl tap (armed → fires → resets) handles this without mode switching.

---

## What's in progress / coming soon

<!-- Update this section as the backlog evolves -->

- Google Play production launch (Lite) — track progressing from internal → production
- iOS App Store launch pending review outcome
- [ADD BACKLOG ITEMS HERE as they're planned or in flight]

---

## Features to highlight (rotation)

These are specific, post-worthy features. Rotate through them — don't repeat the same one twice in a row.

1. Three-state Ctrl key — tap for one-shot (fires once, resets), long-press for sticky/locked. No mode switching.
2. Voice input works in Termux and SSH apps — mic button in the keyboard row, not the system IME bar.
3. Clipboard history 30 items deep, accessible from keyboard. Full version adds pinning.
4. Slash command panel — type `/compact`, `/clear`, `/memory` etc. in one tap. Works in any app.
5. Number row with long-press shifted symbols. No more switching layers to type `!` or `@`.
6. Alt character long-press on QWERTY keys — accented letters, punctuation variants, etc.
7. Per-app autocorrect toggle via long-press spacebar. Off in Termux, on in Signal.
8. Arrow keys in the terminal row — scroll through command history without leaving the keyboard.
9. SCP image upload to any SSH server, configured per-host. Tap to select and upload from Files.
10. Color themes: dark, light, OLED black, terminal green. (Full version.)
11. iOS: built-in SSH terminal so you don't need a separate app.
12. iOS: keyboard extension brings terminal keys to every app on iPhone — Notes, Messages, anywhere.

---

## Tone and voice notes

These apply to any post written on behalf of KeyJawn.

- Write like a developer who built something and is telling other developers about it.
- Be specific. "Ctrl one-shot mode fires and resets in one tap" is better than "advanced Ctrl modifier."
- No marketing language. No "excited to announce", "game-changer", "revolutionizing", "seamlessly".
- No hyperbole. If something is useful, describe why it's useful. Don't call it amazing.
- No hashtag walls. One or two at most, only if they add discovery value (#ClaudeCode #Termux). Never #dev #coding #mobiledev #keyboard #productivity #tech.
- Keep posts under 240 characters when possible. Twitter is for short takes.
- Don't start with "I" — Twitter/X's algo punishes it. Rearrange the sentence.
- Don't start with "We" either — KeyJawn is a real small product, not a corporate "we".
- Present tense, active voice.
- No emojis unless they're functional (e.g., arrows → for navigation, not decoration).

---

## Links

- Website: https://keyjawn.amditis.tech
- GitHub: https://github.com/jamditis/keyjawn
- iOS TestFlight: https://testflight.apple.com/join/8vMqguKK
- Twitter: @keyjawn
