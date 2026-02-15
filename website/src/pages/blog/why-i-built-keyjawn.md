---
layout: ../../layouts/Blog.astro
title: "Why I built a keyboard for the CLI renaissance"
description: "AI tools brought millions of new users to the terminal. Mobile keyboards haven't caught up."
date: 2026-02-15
author: Joe Amditis
---

More people use the terminal now than at any point in the last decade, and most of them aren't sysadmins.

Claude Code, OpenClaw, Gemini CLI, Codex -- these tools live in a shell. If you want to use them, you need a terminal. And a growing number of people are accessing that terminal from their phones, SSHing into a Raspberry Pi or a Mac Mini from the couch, a waiting room, or the train.

The phone keyboards they're using weren't built for this.

## What's out there

There are two existing approaches to typing in a terminal on Android, and neither one fits the way people use the CLI now.

**[Hacker's Keyboard](https://github.com/nicklaus4/hackerskeyboard)** is a full five-row keyboard with Ctrl, Alt, Esc, arrow keys, and an F-key row. It's been around since 2010 and it's solid at what it does. But it was designed for people running vim and emacs on a phone -- traditional power users who wanted a desktop keyboard layout crammed onto a touchscreen. The app hasn't been updated in years, the keys are small because it's trying to fit everything at once, and it doesn't have any of the features that matter for AI CLI workflows: voice input, slash command shortcuts, image upload.

**[Termius](https://termius.com/)** takes the opposite approach. It's a full SSH client with its own built-in terminal, connection manager, and key toolbar. It's a good product if you want a dedicated app for SSH sessions. But it is its own app. If you use Cockpit in a browser, or Termux, or any other terminal tool, the Termius keyboard doesn't help you. You'd need to do all your terminal work inside Termius specifically.

KeyJawn is neither of those things. It's a system keyboard -- it replaces Gboard, not your SSH client. You install it, enable it in Android settings, and it works in every app. Termux, Cockpit in Chrome, JuiceSSH, a web terminal, whatever. The terminal keys are always there.

## The actual problem

Standard mobile keyboards are designed for texting. That's fine until you need to send `Ctrl+C` to kill a stuck process, or `Escape` to exit a mode, or `Tab` to trigger autocomplete.

On Gboard:

- There's no Escape key.
- There's no Tab key.
- There's no Ctrl key. No `Ctrl+C` to interrupt, no `Ctrl+Z` to suspend, no `Ctrl+L` to clear.
- Arrow keys are either hidden or nonexistent.
- Autocorrect turns `git` into `got` and `npm` into `nap`.

These aren't edge cases anymore. They're the first five minutes of trying to use Claude Code from your phone.

## What KeyJawn does differently

The keyboard has a dedicated terminal row above the QWERTY layout: `Esc`, `Tab`, `Ctrl`, arrow keys, clipboard, mic, and upload. Always visible. No long-pressing, no layer hunting.

**Voice input that streams.** Most of what you type into an AI agent is natural language. "Fix the auth bug in login.ts." "Refactor the dashboard component." Thumb-typing that on a phone is slow. KeyJawn streams speech-to-text in real time -- you see words appear as you say them, and you can edit the result before sending. For the 90% of CLI interaction that's just talking to an agent in plain English, this is faster than typing.

**SCP image upload.** I SSH into Raspberry Pis from my phone constantly. When I need to share a screenshot with Claude Code -- an error message, a UI bug, a terminal output -- the normal process is: save the image, open a file manager, find an SCP client, configure the connection, transfer the file, switch back to the terminal, type the path. KeyJawn does it in one tap. Pick the photo, it SCPs to your server, and the remote file path gets typed at your cursor.

**It's a system keyboard, not an overlay.** This is the thing that bothered me most about existing solutions. Termius has terminal keys, but they're inside the Termius app. Browser extensions add key rows, but they float above the page and break when you switch apps or the browser redraws. Hacker's Keyboard is a system keyboard, but it's a full desktop layout crammed onto a phone screen. KeyJawn installs as an Android input method -- the terminal keys are part of the keyboard itself, not a layer on top of something else. They work in Termux, in Cockpit through Chrome, in JuiceSSH, in any app. You set it once and forget about it.

**Customizable for how you actually work.** I built this for my own workflow: SSHing into a Pi from a couch to run Claude Code sessions. The default key layout reflects that -- the keys I reach for most are the ones I put in the terminal row. Slash command sets are configurable per tool (Claude Code, OpenClaw, Gemini CLI). Autocorrect toggles per app so you can leave it off for terminals and on for Slack. SSH hosts are saved so you can switch between machines without retyping credentials. The point is to fit the keyboard to your workflow instead of working around the keyboard.

**Ctrl as a three-state toggle.** Tap it once to arm it for one keypress (`Ctrl+C`). Long-press to lock it for a series of combos. Tap again to turn it off. Simple, and more useful than not having Ctrl at all.

**Slash command picker.** Claude Code, OpenClaw, and Gemini CLI all use slash commands (`/help`, `/clear`, `/compact`, `/status`). KeyJawn gives you a quick-pick popup instead of making you type them from memory. You can also create custom command sets for your own tools and workflows -- add whatever commands you need, group them by project, toggle sets on and off.

**Keyboard themes.** Four color presets: the default dark, a light theme for outdoor use, true-black OLED for battery savings on AMOLED screens, and a green-on-black terminal theme for the aesthetic. The whole keyboard changes -- keys, backgrounds, hints, accent colors.

**Autocorrect is off by default.** Autocorrect breaks web-based terminals because it uses `setComposingText`, which doesn't map to the key events a shell expects. You can turn it on per app -- keep it off for Termux, on for Slack.

## Two versions

**KeyJawn Lite** is free. Full QWERTY keyboard with the terminal key row, number row, alt character popups, and shift/caps lock. No permissions required. No network access.

**KeyJawn Full** is $4, lifetime. It adds voice input, clipboard history, SCP upload, SSH host management, slash commands, custom command sets, keyboard color themes, swipe gestures, and per-app autocorrect toggle. No subscriptions, no ads, no tracking. One payment, done.

The code is MIT-licensed and [on GitHub](https://github.com/jamditis/keyjawn). Both versions are built from the same codebase -- the full version unlocks features that need additional permissions (microphone, network, storage).

## Try it

[Download the APK](https://github.com/jamditis/keyjawn/releases) and see if it fits your workflow. If something doesn't work, [file an issue](https://github.com/jamditis/keyjawn/issues). PRs are open.

Your phone keyboard shouldn't be the reason you can't use a terminal.
