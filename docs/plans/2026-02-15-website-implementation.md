# KeyJawn website implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build and deploy a terminal-aesthetic marketing + docs website for KeyJawn at `keyjawn.amditis.tech`.

**Architecture:** Astro static site generator with plain CSS (no Tailwind, no React). Six pages: landing, features, manual, support, pricing, blog post. Shared layout components for nav/footer. GitHub Pages hosting with GitHub Actions deploy. Cloudflare DNS.

**Tech stack:** Astro 5.x, plain CSS with custom properties, JetBrains Mono + Satoshi fonts, GitHub Pages, GitHub Actions

**Design doc:** `docs/plans/2026-02-15-website-design.md`

---

### Task 1: Create repo and scaffold Astro project

**Files:**
- Create: `keyjawn-site/package.json`
- Create: `keyjawn-site/astro.config.mjs`
- Create: `keyjawn-site/tsconfig.json`
- Create: `keyjawn-site/src/pages/index.astro` (placeholder)
- Create: `keyjawn-site/public/CNAME`

**Step 1: Create the repo directory and initialize**

```bash
cd C:/Users/amdit/OneDrive/Desktop/Crimes
mkdir keyjawn-site && cd keyjawn-site
git init
npm create astro@latest -- --template minimal --no-install --no-git .
npm install
```

If `npm create astro` prompts interactively, answer: template=minimal, TypeScript=no, install deps=yes.

**Step 2: Configure Astro for GitHub Pages**

`astro.config.mjs`:
```javascript
import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://keyjawn.amditis.tech',
  output: 'static',
});
```

**Step 3: Add CNAME for custom domain**

`public/CNAME`:
```
keyjawn.amditis.tech
```

**Step 4: Verify it builds and runs**

```bash
npm run dev
```

Expected: Astro dev server starts, localhost shows the default page.

```bash
npm run build
```

Expected: Build succeeds, outputs to `dist/`.

**Step 5: Commit**

```bash
git add -A
git commit -m "feat: scaffold Astro project for keyjawn site"
```

---

### Task 2: Global CSS and design tokens

**Files:**
- Create: `src/styles/global.css`

**Step 1: Create the global stylesheet**

This file defines all CSS custom properties from the design doc, font imports, base element styles, and utility classes. Include:

- CSS custom properties for all colors (`--bg`, `--surface`, `--border`, `--text`, `--text-muted`, `--accent`, `--accent-warm`, `--code-bg`)
- `@font-face` or Google Fonts import for JetBrains Mono and Satoshi (Satoshi from fontshare.com CDN, JetBrains Mono from Google Fonts)
- CSS reset (minimal: box-sizing, margin/padding reset, font smoothing)
- Base styles: `body`, `h1-h6`, `p`, `a`, `code`, `pre`, `kbd` (styled as key caps)
- `.container` class (max-width 1100px, centered, padding)
- Blinking cursor animation (`@keyframes blink`)
- Card styles (`.card` with surface bg, border, hover glow)
- Terminal prompt prefix style (`.prompt::before { content: '$ ' }`)
- Button styles (`.btn-primary` green accent, `.btn-secondary` outline)
- Responsive breakpoints (mobile at 768px)
- `-webkit-font-smoothing: antialiased` on body

**Step 2: Verify fonts load**

Import the CSS in a test page and confirm both fonts render.

**Step 3: Commit**

```bash
git add src/styles/global.css
git commit -m "feat: add global CSS with design tokens and base styles"
```

---

### Task 3: Shared layout and navigation components

**Files:**
- Create: `src/layouts/Base.astro`
- Create: `src/components/Nav.astro`
- Create: `src/components/Footer.astro`

**Step 1: Create Base layout**

`src/layouts/Base.astro` — wraps every page. Includes:
- `<!DOCTYPE html>` with `lang="en"`
- `<head>`: charset, viewport, title prop, description prop, OG tags, font imports, global.css import
- `<body>`: Nav component, `<main>` slot, Footer component
- Inline SVG favicon (terminal/keyboard themed, no emoji)

Props: `title: string`, `description: string`

**Step 2: Create Nav component**

`src/components/Nav.astro` — fixed top bar:
- Left: "KeyJawn" in JetBrains Mono, styled as terminal prompt (`> keyjawn`)
- Right: links to all pages (Features, Manual, Support, Pricing, Blog)
- Mobile: hamburger button that toggles a fullscreen overlay menu
- Active page highlighted with accent color
- Use `Astro.url.pathname` to detect active page
- Small `<script>` tag for mobile menu toggle (vanilla JS, inline)

**Step 3: Create Footer component**

`src/components/Footer.astro`:
- Three columns: Navigation links, Resources (GitHub, Issues, APK download), Legal (MIT license, privacy)
- Bottom bar: copyright, "Built with terminal love" or similar
- Muted text color, subtle top border

**Step 4: Update index.astro to use Base layout**

Replace placeholder content with Base layout wrapper and a simple "Coming soon" message to verify everything works.

**Step 5: Run dev server and verify**

```bash
npm run dev
```

Expected: Page loads with nav, footer, dark background, correct fonts.

**Step 6: Commit**

```bash
git add src/layouts/Base.astro src/components/Nav.astro src/components/Footer.astro src/pages/index.astro
git commit -m "feat: add Base layout, Nav, and Footer components"
```

---

### Task 4: Reusable components

**Files:**
- Create: `src/components/FeatureCard.astro`
- Create: `src/components/TerminalBlock.astro`
- Create: `src/components/KeyCap.astro`
- Create: `src/components/PricingCard.astro`
- Create: `src/components/FAQ.astro`
- Create: `src/components/PhoneMockup.astro`

**Step 1: Create FeatureCard**

Props: `title`, `description`, `icon` (text/emoji or inline SVG). Card with surface background, border, hover glow. Terminal-style title with `$` prefix.

**Step 2: Create TerminalBlock**

Props: `command` (string), `output` (optional string). Styled code block with terminal window chrome (three colored dots at top), dark background, green monospace text. Copy button (vanilla JS onclick) that copies the command text. Slot for custom content if needed.

**Step 3: Create KeyCap**

Props: `label` (string). Inline element styled as a physical key cap — rounded rect, slight 3D shadow/border, monospace text. Used throughout the site to reference keys like `Esc`, `Tab`, `Ctrl`.

**Step 4: Create PricingCard**

Props: `title`, `price`, `features` (string array), `highlighted` (boolean), `ctaText`, `ctaLink`. Card with feature checklist. Highlighted card gets accent border/glow.

**Step 5: Create FAQ**

Props: `items` (array of `{question, answer}`). Accordion with `<details>`/`<summary>` elements. Styled with border-bottom dividers, smooth open/close via CSS transition on max-height (or just use native `<details>` behavior).

**Step 6: Create PhoneMockup**

SVG phone outline (simple rounded rectangle with notch/camera). Inside: a `<slot>` or a static image of KeyJawn in a terminal session. This is a visual-only component — no interactivity.

**Step 7: Commit**

```bash
git add src/components/
git commit -m "feat: add reusable components (FeatureCard, TerminalBlock, KeyCap, PricingCard, FAQ, PhoneMockup)"
```

---

### Task 5: Landing page

**Files:**
- Modify: `src/pages/index.astro`

**Step 1: Build the landing page**

Use the `@skill frontend-design` approach for this page. It should be the most visually striking page. Sections:

**Hero:**
- Full-width dark section with subtle CRT glow (radial gradient in green, very low opacity)
- Large JetBrains Mono headline: `> the keyboard for the CLI renaissance` with blinking cursor `_`
- Subheadline in Satoshi: "Terminal keys, voice input, and SCP upload on a keyboard built for AI-era CLI users."
- Two buttons: `[Download APK]` (accent green, links to GitHub releases) and `[View source]` (outline, links to GitHub repo)
- PhoneMockup component below the text

**Feature grid:**
- Section heading: `$ ls features/`
- 3-column grid (2-column on tablet, 1-column on mobile) of FeatureCard components
- Features: Terminal key row, Voice input, SCP upload, Slash commands, Swipe gestures, Number row

**Built for section:**
- Section heading: `$ cat compatible.txt`
- Horizontal row of text labels: Claude Code, Cockpit, Termux, Aider, JuiceSSH, ConnectBot
- Muted text style, subtle separator dots between items

**Install section:**
- Section heading: `$ install keyjawn`
- TerminalBlock with: `adb install keyjawn-full.apk`
- Or two download buttons: Full APK, Lite APK
- Link to GitHub releases

**Step 2: Run dev server and verify**

```bash
npm run dev
```

Expected: Landing page renders with all sections, responsive on mobile.

**Step 3: Commit**

```bash
git add src/pages/index.astro
git commit -m "feat: build landing page with hero, features, install sections"
```

---

### Task 6: Features page

**Files:**
- Create: `src/pages/features.astro`

**Step 1: Build the features page**

Each feature gets its own section with alternating layout (text left / visual right, then swap). Use terminal-style headings.

Features to cover (each with heading, 2-3 sentence description, and KeyCap elements showing relevant keys):

1. `$ keyjawn --terminal-keys` — Esc, Tab, Ctrl, arrow keys always visible
2. `$ keyjawn --number-row` — Dedicated 0-9 with long-press shifted symbols
3. `$ keyjawn --alt-keys` — Long-press for accented characters and symbol variants
4. `$ keyjawn --voice` — Tap mic, see waveform, streaming partial transcription
5. `$ keyjawn --clipboard` — Clipboard history with one-tap paste
6. `$ keyjawn --scp-upload` — Pick photo, SCP to server, path inserted at cursor
7. `$ keyjawn --slash-commands` — Quick-insert for LLM CLI commands
8. `$ keyjawn --swipe` — Left=delete word, right=space, up=symbols, down=abc
9. `$ keyjawn --autocorrect` — Per-app toggle via long-press spacebar
10. `$ keyjawn --shift` — Single-tap for one uppercase letter, double-tap for caps lock

**Step 2: Verify responsive layout**

```bash
npm run dev
```

**Step 3: Commit**

```bash
git add src/pages/features.astro
git commit -m "feat: build features page with terminal-style section headers"
```

---

### Task 7: User manual page

**Files:**
- Create: `src/pages/manual.astro`

**Step 1: Build the manual page**

Docs-style page with anchor-linked sections. Optional: sticky sidebar nav on desktop that lists all sections and highlights the current one on scroll (vanilla JS IntersectionObserver). On mobile: just a table of contents at the top with anchor links.

Sections (each with `id` for deep linking):

1. **Getting started** — Download APK, go to Settings > System > Languages & Input > On-screen keyboard, enable KeyJawn, set as default. Include ADB install command in TerminalBlock.
2. **Basic typing** — Three layers: lowercase (default), uppercase (tap Shift), symbols (tap ?123 or swipe up). Swipe down or tap "abc" to return.
3. **Shift and caps lock** — Single tap = one uppercase letter then auto-returns. Double-tap = caps lock (stays uppercase). Tap again to turn off. Visual indicator: Shift key changes background color.
4. **Terminal keys** — Esc (sends KEYCODE_ESCAPE), Tab (sends KEYCODE_TAB), Ctrl (toggle modifier — armed on tap, locked on long-press, auto-resets after one keypress), Left/Right arrows (hold to repeat).
5. **Number row** — Always visible above QWERTY. Long-press any number for its shifted symbol. Small hint label shows the alt character.
6. **Alt characters** — Long-press letter keys for accented variants. Single-alt keys fire immediately. Multi-alt keys show a popup. Examples: a -> a,a,a,a, n -> n, etc.
7. **Voice input** — Tap mic in extra row. Waveform animation shows audio level. Text appears as you speak (streaming). Tap stop button or mic again to finish.
8. **Clipboard history** — Tap clipboard button in extra row. Shows recent copied text. Tap any item to paste.
9. **SCP upload (full version)** — Configure SSH hosts in Settings. Tap upload button, pick photo. KeyJawn SCPs it to your server and types the remote path at your cursor. Useful for sharing screenshots with Claude Code.
10. **Slash commands** — Tap the "slash" key on the symbols layer to open the command picker. Select a command to insert it. Dismiss without selecting to type a literal `/`.
11. **Swipe gestures** — Swipe left on keyboard area = delete word. Swipe right = space. Swipe up = switch to symbols. Swipe down = switch to letters.
12. **Autocorrect** — Off by default. Long-press spacebar to toggle per app. When on, spacebar shows "SPACE" label. Useful for chat apps, harmful in terminals.

Use `<code>`, KeyCap components, and TerminalBlock where appropriate.

**Step 2: Verify**

```bash
npm run dev
```

**Step 3: Commit**

```bash
git add src/pages/manual.astro
git commit -m "feat: build user manual with all keyboard documentation"
```

---

### Task 8: Support page

**Files:**
- Create: `src/pages/support.astro`

**Step 1: Build the support page**

**FAQ section** using the FAQ accordion component. Questions:
- "How do I install KeyJawn?" — Link to manual getting started section.
- "Why don't keys work in my browser?" — Explain the commitText vs key event issue with WebView terminals. This is a known Android limitation.
- "How do I set up SCP upload?" — Brief explanation + link to manual SCP section.
- "Can I use this with Termux?" — Yes, works with any app that accepts keyboard input.
- "Is my SSH password stored securely?" — Yes, encrypted via AndroidX security-crypto EncryptedSharedPreferences.
- "Why is autocorrect off by default?" — Autocorrect uses setComposingText which breaks web-based terminals. Long-press spacebar to enable per app.
- "Will there be an iOS version?" — Not planned. iOS doesn't allow the level of keyboard customization needed.
- "Is this open source?" — Yes, MIT license. GitHub link.

**Report a bug section:**
- Link to GitHub Issues
- Template: what to include (device model, Android version, app you were typing in, what happened vs what you expected)

**Known limitations:**
- No swipe-to-type (word prediction from gesture path) — would require a closed-source Google library
- Basic autocorrect only (no word prediction, no suggestions bar)
- SCP upload requires the full version

**Contact:**
- GitHub Issues (preferred)
- Email: optional, up to Joe

**Step 2: Commit**

```bash
git add src/pages/support.astro
git commit -m "feat: build support page with FAQ, bug reporting, known limitations"
```

---

### Task 9: Pricing page

**Files:**
- Create: `src/pages/pricing.astro`

**Step 1: Build the pricing page**

Two PricingCard components side by side (stacked on mobile).

**Lite card** (not highlighted):
- Price: "Free"
- Features: QWERTY keyboard (3 layers), Terminal key row (Esc, Tab, Ctrl, arrows), Number row with alt hints, Alt character popups on long-press, Shift / caps lock, No permissions required, Open source (MIT)
- CTA: "Download Lite" -> GitHub releases

**Full card** (highlighted with accent border):
- Price: "$4 — lifetime"
- Features: Everything in Lite plus: Voice input with streaming transcription, Clipboard history manager, SCP image upload, Multi-host SSH management, Encrypted credential storage, Slash command shortcuts, Swipe gestures, Per-app autocorrect toggle
- CTA: "Get Full version" -> payment link (placeholder for now, can be GitHub Sponsors, Gumroad, or Lemonsqueezy)
- Badge: "No subscriptions. No ads. No tracking."

**"Why $4?" section** below the cards:
- Short paragraph: This is a one-person project. $4 keeps development going. No recurring payments. No data collection. No ads. The code is open source either way. Price goes up when an iOS version ships.

**Step 2: Commit**

```bash
git add src/pages/pricing.astro
git commit -m "feat: build pricing page with lite/full comparison"
```

---

### Task 10: Blog post

**Files:**
- Create: `src/pages/blog/why-i-built-keyjawn.md`
- Create: `src/layouts/Blog.astro`

**Step 1: Create Blog layout**

`src/layouts/Blog.astro` — extends Base layout. Adds:
- Article `<article>` wrapper with max-width ~720px
- Post title from frontmatter
- Date from frontmatter
- Author: "Joe Amditis"
- Prose styles: larger line-height, styled headings, blockquote styles, image styles

**Step 2: Write the blog post**

`src/pages/blog/why-i-built-keyjawn.md` with frontmatter:

```yaml
---
layout: ../../layouts/Blog.astro
title: "Why I built a keyboard for the CLI renaissance"
description: "AI tools brought millions of new users to the terminal. Mobile keyboards haven't caught up."
date: 2026-02-15
author: Joe Amditis
---
```

Content following the narrative arc from the design doc:

1. **Opening:** Personal story — lying in bed, phone in hand, SSHing into a Raspberry Pi via Cockpit in Edge. Running Claude Code. Trying to type `Ctrl+C` and there's no Ctrl key. Copying a caret character from a website and pasting it. The moment where you think "this is absurd."

2. **The shift:** A year ago, "using the terminal" meant sysadmins and developers debugging production. Now it means your friend who's never opened a code editor is running `claude` in their terminal because someone on Twitter told them to. AI CLI tools — Claude Code, Aider, Open Interpreter, Goose — brought a wave of people to the command line who aren't there for the same reasons as before. They're having conversations, not writing bash scripts.

3. **The gap:** Every mobile keyboard is built for texting. Autocorrect that turns `git` into `got`. No Escape key. No Tab key. No way to send Ctrl+C without installing a separate terminal emulator with its own keyboard. If you SSH from your phone's browser into Cockpit or a web terminal, you get Gboard and a prayer.

4. **What KeyJawn does:** A dedicated terminal row — Esc, Tab, Ctrl, arrows — always visible above the QWERTY layout. Voice input that streams text as you speak (because 90% of talking to an AI agent is natural language). SCP upload so you can snap a screenshot and share it with Claude Code without leaving the keyboard. Slash commands for the `/` prefix patterns that every LLM CLI uses.

5. **The bigger picture:** Mobile CLI usage is growing. Not because people love terminals, but because AI agents live there. As these tools get better, more people will want to use them on the go — from their couch, their commute, their bed at 2 AM. The keyboard is the bottleneck, and nobody's building for this use case.

6. **Two versions:** Lite is free and does everything except SCP upload. Full is $4, lifetime, and includes SCP and SSH host management. No subscriptions, no ads, no tracking. The code is MIT-licensed and on GitHub.

7. **Call to action:** Try it. If something doesn't work, file an issue. If you want to contribute, PRs are open. If you find it useful, the $4 helps.

Write this in Joe's voice: casual, contractions, short sentences, opinionated. Not corporate. Not overly technical. Someone who solves problems by building things.

**Step 3: Verify blog post renders**

```bash
npm run dev
```

Navigate to `/blog/why-i-built-keyjawn`. Verify markdown renders with Blog layout, proper typography, readable on mobile.

**Step 4: Commit**

```bash
git add src/layouts/Blog.astro src/pages/blog/why-i-built-keyjawn.md
git commit -m "feat: add blog post - why I built a keyboard for the CLI renaissance"
```

---

### Task 11: GitHub Actions deploy workflow

**Files:**
- Create: `.github/workflows/deploy.yml`

**Step 1: Create the deploy workflow**

`.github/workflows/deploy.yml`:

```yaml
name: Deploy to GitHub Pages

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v5
      - name: Install, build, and upload
        uses: withastro/action@v5

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

**Step 2: Verify build works locally**

```bash
npm run build
```

Expected: Clean build, no errors.

**Step 3: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add GitHub Actions workflow for GitHub Pages deploy"
```

---

### Task 12: Create GitHub repo and push

**Step 1: Create the repo on GitHub**

```bash
gh repo create jamditis/keyjawn-site --public --description "Marketing and docs site for KeyJawn Android keyboard" --source . --push
```

**Step 2: Enable GitHub Pages**

```bash
gh api repos/jamditis/keyjawn-site/pages -X POST -f build_type=workflow
```

Or manually: repo Settings > Pages > Source: GitHub Actions.

**Step 3: Verify deploy**

Push triggers the workflow. Check Actions tab. Once deployed, verify the site loads at the GitHub Pages URL.

**Step 4: Configure Cloudflare DNS**

Add a CNAME record in Cloudflare for `amditis.tech`:
- Name: `keyjawn`
- Target: `jamditis.github.io`
- Proxy: DNS only (gray cloud) — GitHub Pages handles SSL

In the GitHub repo settings, add `keyjawn.amditis.tech` as a custom domain.

Wait for SSL cert provisioning (can take a few minutes).

**Step 5: Verify custom domain**

Navigate to `https://keyjawn.amditis.tech`. Site should load with valid SSL.

---

## Task summary

| Task | What | Key files |
|------|------|-----------|
| 1 | Scaffold Astro project | `package.json`, `astro.config.mjs`, `public/CNAME` |
| 2 | Global CSS and design tokens | `src/styles/global.css` |
| 3 | Shared layout, Nav, Footer | `src/layouts/Base.astro`, `src/components/Nav.astro`, `src/components/Footer.astro` |
| 4 | Reusable components | `src/components/` (FeatureCard, TerminalBlock, KeyCap, PricingCard, FAQ, PhoneMockup) |
| 5 | Landing page | `src/pages/index.astro` |
| 6 | Features page | `src/pages/features.astro` |
| 7 | User manual | `src/pages/manual.astro` |
| 8 | Support page | `src/pages/support.astro` |
| 9 | Pricing page | `src/pages/pricing.astro` |
| 10 | Blog post + Blog layout | `src/pages/blog/why-i-built-keyjawn.md`, `src/layouts/Blog.astro` |
| 11 | GitHub Actions deploy | `.github/workflows/deploy.yml` |
| 12 | Create repo, push, configure DNS | GitHub + Cloudflare |
