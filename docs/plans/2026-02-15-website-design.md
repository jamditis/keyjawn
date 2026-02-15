# KeyJawn website design

## Goal

A marketing + docs site for KeyJawn at `keyjawn.amditis.tech`. Communicates what KeyJawn is, why it exists, how to use it, and where to get it. Terminal hacker aesthetic. Hosted on GitHub Pages via a separate `keyjawn-site` repo.

## Tech stack

- **Astro** — static site generator with markdown support for blog/docs content
- **GitHub Pages** — hosting, deploy via GitHub Actions on push
- **Cloudflare DNS** — `keyjawn.amditis.tech` CNAME to GitHub Pages
- No React, no Tailwind. Plain CSS with custom properties. Astro components for shared layout (nav, footer).

## Visual design

### Color palette

| Token | Value | Usage |
|-------|-------|-------|
| `--bg` | `#0a0a0f` | Page background (near-black, slight blue) |
| `--surface` | `#14141c` | Cards, code blocks, elevated surfaces |
| `--border` | `#1e1e2a` | Subtle borders on cards |
| `--text` | `#c8c8d0` | Body text (soft off-white) |
| `--text-muted` | `#5a5a6e` | Secondary text, captions |
| `--accent` | `#6cf2a8` | Terminal green. CTAs, links, active states |
| `--accent-warm` | `#f26c8a` | Badges, emphasis, pricing highlight |
| `--code-bg` | `#0d0d14` | Code block backgrounds |

### Typography

| Role | Font | Weight | Notes |
|------|------|--------|-------|
| Headlines | JetBrains Mono | 700 | Large monospace headlines, terminal feel |
| Body | Satoshi | 400/500 | Clean geometric sans, distinctive but readable |
| Code / terminal | JetBrains Mono | 400 | Code blocks, inline code, terminal prompts |

Both fonts loaded from Google Fonts / CDN. Variable weight where available.

### Design language

- **Terminal prompt motifs:** section headers prefixed with `$` or `>` characters
- **Blinking cursor:** animated `_` on hero headline
- **Key cap shapes:** `<kbd>` elements styled as physical key caps, used as design accents
- **Subtle CRT glow:** faint green radial gradient behind hero area (CSS only)
- **Card hover glow:** border brightens on hover with a soft green glow
- **Code blocks as design elements:** commands styled with copy buttons, terminal window chrome (three dots)
- **No carousels, no autoplay video, no sliders** — developer audiences reject these
- **Phone mockup:** SVG phone outline with KeyJawn keyboard screenshot inside, terminal session above it

### Layout

- Max-width 1100px, centered
- Generous whitespace between sections
- Fixed top nav: logo left, links right, hamburger on mobile
- Footer: links, GitHub, license

## Pages

### 1. Landing page (`/`)

**Hero section:**
- Large monospace headline with blinking cursor: `> the keyboard for the CLI renaissance_`
- One-sentence subheadline: "Terminal keys, voice input, and SCP upload on a keyboard built for AI-era CLI users."
- Two CTA buttons styled as key caps: `[Download]` `[Source code]`
- Below: phone mockup showing KeyJawn in a terminal session

**Feature cards (3-column grid):**
- Terminal key row (Esc, Tab, Ctrl, arrows)
- Voice input with streaming transcription
- SCP image upload
- Slash command shortcuts
- Swipe gestures
- Number row with alt hints

**"Built for" section:**
- Text/logos: Claude Code, Cockpit, Termux, Aider, SSH

**Install section:**
- Styled terminal block with `adb install` command + copy button
- Or: direct APK download links

### 2. Features page (`/features`)

Each feature gets a section:
- Terminal-style heading (`$ keyjawn --feature terminal-keys`)
- Description paragraph
- Visual: keyboard mockup or annotated screenshot
- Key caps showing the relevant keys

Features to cover: terminal row, number row, alt key popups, voice input + waveform, clipboard history, SCP upload, slash commands, swipe gestures, autocorrect toggle, shift states (single/caps lock).

### 3. User manual (`/manual`)

Astro Starlight-style docs layout (or a simulated version with sidebar nav):
- Getting started: install, enable in settings, set as default keyboard
- Basic typing: lowercase/uppercase/symbols layers, shift single-tap vs double-tap
- Terminal keys: what each key does (Esc, Tab, Ctrl toggle/lock, arrows with repeat)
- Number row: digits, long-press for shifted symbols, alt char hints
- Voice input: tap mic, waveform display, streaming partial results, tap stop
- Clipboard history: how to access, paste from history
- SCP upload: configure hosts in settings, pick photo, auto-insert path
- Slash commands: what they are, how to use, how to customize
- Swipe gestures: left=delete word, right=space, up=symbols, down=letters
- Tips: autocorrect toggle (long-press space), per-app behavior

### 4. Support page (`/support`)

- FAQ accordion (common questions: "Why doesn't it work in Chrome?", "How do I set up SCP?", etc.)
- GitHub Issues link as primary support channel
- "Report a bug" guidance (what info to include)
- Known limitations (no swipe-to-type word prediction, autocorrect is basic)
- Contact: GitHub Issues preferred, email as fallback

### 5. Pricing page (`/pricing`)

Two-column comparison card:

**Lite (free):**
- QWERTY keyboard with three layers (lowercase, uppercase, symbols)
- Terminal key row (Esc, Tab, Ctrl, arrows)
- Number row with long-press shifted symbols
- Alt character popups on long-press
- Shift single-tap / double-tap caps lock
- No permissions required
- Open source (MIT)

**Full ($4 lifetime):**
- Everything in Lite, plus:
- Voice input with streaming transcription and waveform
- Clipboard history manager
- SCP image upload to remote servers
- Multi-host SSH management with encrypted credentials
- Slash command shortcuts for LLM CLI tools
- Swipe gestures (delete word, space, layer switch)
- Per-app autocorrect toggle
- No subscriptions, no ads, no tracking

"Why $4?" section: one-person project, one-time payment, no recurring costs, no data collection. Price will increase when an iOS version is available. Link to payment (GitHub Sponsors, Gumroad, or Lemonsqueezy).

### 6. Blog post (`/blog/why-i-built-keyjawn`)

**Narrative arc:**
1. Personal opening: lying in bed, phone in hand, SSHing into your Raspberry Pi via Cockpit in Edge. Trying to use Claude Code. Gboard has no Escape key, no Tab, no Ctrl+C. You're copying and pasting individual characters.
2. The realization: AI tools (Claude Code, Aider, Open Interpreter) brought a wave of new terminal users. People who never cared about `xterm` are now spending hours in CLI sessions — but from their phones, not their desks.
3. The gap: every mobile keyboard is optimized for texting grandma, not running `git rebase -i`. No terminal keys. No way to send Ctrl sequences. Autocorrect that fights you in a shell.
4. What KeyJawn does: dedicated terminal row, voice-to-text for natural language prompts, SCP upload so you can share screenshots with Claude Code, slash commands for LLM CLI tools.
5. The bigger picture: mobile is the next frontier for CLI tools. As AI agents get more capable, more people will want to interact with them on the go. The keyboard is the bottleneck.
6. Open source, two versions, $4 if you want the full thing. No tracking, no ads.
7. Call to action: try it, file issues, contribute.

**Tone:** casual, opinionated, personal. Written in first person. Contractions. No corporate voice.

## Repo structure

```
keyjawn-site/
  astro.config.mjs
  package.json
  src/
    layouts/
      Base.astro        (shared head, nav, footer)
      Blog.astro        (blog post layout)
    pages/
      index.astro
      features.astro
      manual.astro
      support.astro
      pricing.astro
      blog/
        why-i-built-keyjawn.md
    components/
      Nav.astro
      Footer.astro
      FeatureCard.astro
      PricingCard.astro
      TerminalBlock.astro  (styled code block with copy button)
      PhoneMockup.astro    (SVG phone with keyboard screenshot)
      KeyCap.astro         (styled kbd element)
      FAQ.astro            (accordion)
    styles/
      global.css
  public/
    img/
      og-image.png
      keyjawn-screenshot.png
    CNAME
  .github/
    workflows/
      deploy.yml         (Astro build + GitHub Pages deploy)
```

## Deployment

1. Create `keyjawn-site` repo on GitHub (under jamditis org or personal)
2. GitHub Actions workflow: on push to main, build Astro, deploy to GitHub Pages
3. Cloudflare DNS: CNAME `keyjawn` -> `jamditis.github.io` (or whatever the Pages URL is)
4. `CNAME` file in `public/` with `keyjawn.amditis.tech`

## References

- [Charm.sh / charm.land](https://charm.land/) — terminal aesthetic, playful branding
- [Warp.dev](https://www.warp.dev/) — polished dark theme, product-first hero
- [Evil Martians: 100 dev tool landing pages](https://evilmartians.com/chronicles/we-studied-100-devtool-landing-pages-here-is-what-actually-works-in-2025)
- [Astro](https://astro.build/) — static site generator
- [JetBrains Mono](https://www.jetbrains.com/lp/mono/) — monospace font
- [Satoshi font](https://www.fontshare.com/fonts/satoshi) — body font
