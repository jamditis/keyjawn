---
layout: ../../layouts/Blog.astro
title: "Why I built a keyboard for the CLI renaissance"
description: "AI tools brought millions of new users to the terminal. Mobile keyboards haven't caught up."
date: 2026-02-15
author: Joe Amditis
---

It's 1 AM and I'm lying in bed with my phone, SSHing into my Raspberry Pi through Cockpit in Edge. I've got Claude Code running in a terminal session. I'm trying to fix a bug in an auth flow and I need to type `Ctrl+C` to kill a stuck process.

There's no Ctrl key on Gboard.

I open a new tab, Google "caret character unicode," copy `^`, paste it into the terminal, realize that doesn't actually send a control sequence, and then spend three minutes trying to figure out how to send an interrupt signal from a phone keyboard that was designed for texting your mom.

This is absurd.

## The shift

A couple years ago, "using the terminal" meant you were a sysadmin debugging a production server, or a developer running build scripts. The command line was for people who already lived there.

That changed fast. Claude Code showed up. Then Aider. Then Goose, Open Interpreter, and a dozen other AI CLI tools. Suddenly, people who'd never opened a terminal in their lives were typing `claude` in a shell because someone on Twitter said they should.

These aren't traditional terminal users. They're not writing bash scripts or configuring nginx. They're having conversations. They're describing bugs in natural language and letting an AI agent fix them. The terminal became a chat interface that happens to run in a shell.

And a lot of them are doing it from their phones.

## The gap

Every mobile keyboard on the market is optimized for one thing: texting. Gboard, SwiftKey, Samsung Keyboard -- they're all built for autocorrecting "teh" to "the" and suggesting the next word in your message to grandpa.

Try using one in a terminal:

- Autocorrect turns `git` into `got` and `npm` into `nap`.
- There's no Escape key. Vim users, you're stuck.
- There's no Tab key. No tab completion, no indentation.
- There's no Ctrl key. No `Ctrl+C` to interrupt, no `Ctrl+Z` to suspend, no `Ctrl+L` to clear.
- Arrow keys are either hidden or nonexistent.
- You can't send a `KEYCODE_ESCAPE` or `KEYCODE_TAB` from the standard Android keyboard API without jumping through hoops.

If you SSH from your phone into a web terminal -- Cockpit, Shellinabox, whatever -- you get a standard keyboard and a prayer.

## What KeyJawn does

I built a keyboard that puts terminal keys front and center.

**The extra row.** Above the QWERTY layout, there's a dedicated row with `Esc`, `Tab`, `Ctrl`, and arrow keys. Always visible. No layer switching, no long-pressing, no hunting. `Ctrl` uses a three-state toggle: tap to arm it for one keypress (for `Ctrl+C`), long-press to lock it (for multiple combos), tap again to turn it off.

**Voice input.** When you're talking to an AI agent, 90% of what you type is natural language. "Fix the auth bug in login.ts." "Add a test for the payment flow." Speaking is faster than thumb-typing on a 6-inch screen. KeyJawn streams the transcription as you talk -- you see the words appear in real time, not after a five-second pause.

**SCP upload.** You're in a Claude Code session and you want to share a screenshot. Tap the upload button, pick the photo, and KeyJawn SCPs it to your server and types the remote path at your cursor. One action instead of six.

**Slash commands.** Every LLM CLI has `/help`, `/clear`, `/compact`, and other slash commands. KeyJawn gives you a quick-pick popup for these instead of making you type them out.

**Number row.** Always visible, with long-press hints for shifted symbols. Because switching layers to type a `2` is a waste of everyone's time.

## The bigger picture

Here's what I think is happening: mobile CLI usage is growing. Not because people suddenly love terminals, but because AI agents live there.

Claude Code runs in a terminal. Aider runs in a terminal. If you want to use these tools, you need a shell. And if you want to use them from your couch at midnight -- or your commute, or a waiting room, or bed -- you need a phone keyboard that doesn't fight you every step of the way.

The keyboard is the bottleneck. Nobody's building for this use case because it didn't exist two years ago. Now it does, and the gap between "what mobile keyboards can do" and "what terminal users need" is wide.

## Two versions

**KeyJawn Lite** is free. It's a full QWERTY keyboard with the terminal key row, number row, alt character popups, and shift/caps lock. No permissions required. No network access.

**KeyJawn Full** is $4, lifetime. It adds voice input, clipboard history, SCP upload, SSH host management, slash commands, swipe gestures, and per-app autocorrect toggle. No subscriptions, no ads, no tracking. One payment, done.

The code is MIT-licensed and [on GitHub](https://github.com/jamditis/keyjawn). Both versions are built from the same codebase -- the full version just unlocks the features that need additional permissions (microphone, network, storage).

## Try it

[Download the APK](https://github.com/jamditis/keyjawn/releases) and see if it fits your workflow. If something doesn't work, [file an issue](https://github.com/jamditis/keyjawn/issues). If you want to contribute, PRs are open.

If you find it useful and the $4 is worth it to you, that helps keep development going. If not, the lite version is there and it's free forever.

Either way, your phone keyboard shouldn't be the reason you can't use a terminal.
