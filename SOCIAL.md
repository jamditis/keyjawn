# KeyJawn — social context doc

This file is read by the Claude Code browser extension before each social media session.
Keep it current. Update it when features ship, things break, or the story changes.

---

## What KeyJawn is

A custom mobile keyboard built specifically for developers who use LLM CLI agents from their phone.

**The key distinction from other mobile terminal keyboards:**
Most mobile terminal keyboards (Hacker's Keyboard, etc.) are built for traditional terminal use — shell commands, SSH sessions, running htop, navigating vim. That's not the primary use case for KeyJawn. KeyJawn is built around the specific interaction pattern of working with LLM CLI agents: writing prompts, reviewing output, sending follow-ups, uploading context, managing slash commands. The workflow is less about typing `ls -la` and more about `/compact`, voice-dictating a long prompt, copying an agent's output to paste elsewhere, and sending a screenshot to your remote session.

This shapes every feature decision:
- The slash command panel exists because `/compact`, `/clear`, `/memory`, `/cost` are typed constantly in Claude Code and Gemini CLI sessions
- Voice input is for dictating long prompts — not just quick commands
- Clipboard history exists because you're constantly copying context between your agent and other apps
- SCP upload is for sending screenshots and images into your agent's context from your phone
- The one-shot Ctrl key handles the prompt → Ctrl+C → re-prompt cycle that characterizes agent debugging

Traditional terminal keyboards assume you're running commands. KeyJawn assumes you're in a conversation with an agent.

**The terminal keys are still there** — Esc, Tab, Ctrl, arrows — because LLM CLI tools live in terminals and you still need them. But they're not the point. The point is that the rest of the keyboard is designed around how you actually use Claude Code or Gemini CLI, not how you'd use bash in 2010.

The target user: anyone running Claude Code, Gemini CLI, Codex CLI, OpenCode, or similar agents from their phone. SSH power users who also use AI tooling. Termux regulars who've moved beyond shell commands into agent workflows.

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

Use these for post ideas. Be specific, not generic. Frame them around agent workflows, not traditional terminal use.

**Agent-specific pain points (highest priority for posts):**
- You can't run Claude Code seriously from your phone on stock Android because there's no Esc, no Ctrl, no arrows. Every other keyboard makes you open a symbol panel to find them.
- Typing `/compact` or `/clear` into Claude Code by hand, every single session, on a touchscreen, gets old fast. The slash command panel inserts them in one tap.
- Dictating long prompts by voice is faster than typing them on a phone keyboard — but Android's voice input doesn't work inside SSH apps or Termux. KeyJawn's mic button works at the keyboard level, so it fires in any app.
- You want to send a screenshot to your Claude session. On most phones that's: screenshot → open SCP app → find the file → upload → switch back. With Full, it's a button in the keyboard row.
- Clipboard on Android doesn't remember what you copied three prompts ago. KeyJawn keeps 30 items in history. Full version lets you pin the ones you reuse constantly.
- The prompt → Ctrl+C → re-prompt cycle is constant when working with agents. Stock keyboards make you switch modes to get Ctrl. KeyJawn's one-shot Ctrl: tap to arm, next key fires with Ctrl, automatically resets. No mode switching.

**General terminal pain points (also valid but less distinctive):**
- No Esc key on AOSP keyboards. Affects vim, less, any terminal tool.
- Arrow keys require a layer switch on stock keyboards. Kills your flow when navigating command history.
- Per-app autocorrect — you want it off in Termux, on in your notes app. Long-press spacebar handles this without going into Settings.

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
