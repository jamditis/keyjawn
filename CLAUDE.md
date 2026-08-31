# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

KeyJawn provides terminal-oriented mobile input for remote CLI work. Android is
a custom keyboard with voice input and optional SCP upload. iOS is a remote SSH
terminal with a companion keyboard and copied-image SFTP upload.

- **Android**: `InputMethodService`-based keyboard extension. Two flavors: lite
  (free APK and limited Google Play testing) and full ($4, Stripe/website).
- **iOS**: Standalone SwiftUI app with a remote SSH terminal (SwiftTerm + SwiftNIO SSH via Citadel) and a companion `UIInputViewController` keyboard extension. Apple rejected review build 2 under guideline 2.5.2. A fresh signed build 9 archive was created and verified from the corrected source on August 31, 2026. The exact archive was uploaded, and Apple processed build 9 as valid. Build 9 is selected for version 1.0, manual release is enabled, and the submission is waiting for review. The app is not approved and not publicly released.

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

- Gradle 8.13, AGP 8.13.2, Kotlin 2.1.0
- JDK 17+ launcher, pinned Gradle daemon JDK 21, compileSdk 36, minSdk 26
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
- `VoiceInputHandler` — speech recognition using Android's `SpeechRecognizer`. Wired to mic button in extra row. Uses `onPermissionNeeded` callback for runtime permission requests.
- `UploadHandler` / `ScpUploader` — SCP image upload (full flavor only). `HostConfig` + `HostStorage` manage SSH server credentials (encrypted via AndroidX security-crypto).
- `NumberRowManager` — wires the dedicated number row (0-9) above the QWERTY grid. Long-press types the shifted symbol (!@#$%^&*()).
- `AltKeyMappings` — static map of long-press alternate characters keyed by primary key label. Covers accented vowels, common letters (n, c, s, y), and punctuation variants. Uppercase variants auto-derived from lowercase lookups.
- `AltKeyPopup` — small horizontal `PopupWindow` anchored above the pressed key. Shows one button per alt character. For single-alt keys (like number row), sends directly without a popup.
- `ClipboardHistoryManager` + `ClipboardPanel` — clipboard history (30 items) with pinning support. Pinned items persist across sessions via SharedPreferences. Full flavor only for pinning.
- `MenuPanel` — settings overlay panel (gear icon in extra row). Inline toggles for tooltips, autocorrect, theme selection. Full flavor only.
- `RepeatTouchListener` — fires repeated key events while arrow buttons are held down.
- `AppPrefs` — per-app autocorrect toggle (long-press spacebar). Stores preferences per package name. Defaults to OFF.
- `ThemeManager` — keyboard color themes (Dark, Light, OLED black, Terminal). Full flavor only. Includes `quickKeyBg()` for themed quick key background.

**Settings:**
- `SettingsActivity` — host management UI for configuring SSH servers.

## Key patterns

- `InputConnection` is accessed via lambda providers (`() -> InputConnection?`) since the active connection changes.
- Ctrl modifier uses a three-state machine: OFF, ARMED (one-shot), LOCKED (sticky until toggled off).
- The `slash` key on the symbols layer has `KeyOutput.Slash` output and triggers the slash command popup. The `/` key on lower/upper layers is `KeyOutput.Character("/")` and just types `/`. Long-pressing the `/` character key types `.` instead.
- Long-press behavior on QWERTY keys: looks up `AltKeyMappings.getAlts(key.label)`. If one alt, sends it directly. If multiple, shows `AltKeyPopup`. Keys with existing long-press handlers (Space, Slash) are skipped because they use different `KeyOutput` subtypes.
- Feature gating uses `BuildConfig.FLAVOR == "full"` at build time. No runtime billing or license checks.
- Overlay panels (MenuPanel, ClipboardPanel) are added as children of a FrameLayout wrapping the keyboard view. They overlay the keyboard rather than replacing it.
- Tests use Robolectric for Android framework classes and Mockito-Kotlin for mocking. `isIncludeAndroidResources = true` is set in build.gradle.kts so Robolectric can load assets and layouts.

## iOS app

Source lives in `ios/`. Managed with XcodeGen — edit `ios/project.yml`, then run `xcodegen generate` to regenerate the `.xcodeproj`.

**Build environment:** Xcode 26+, Swift 6.0, iOS 17+ deployment target, Team ID `5624SD289G`.

**Build commands:**
```bash
# Regenerate xcodeproj after project.yml changes
xcodegen generate

# Simulator build
xcodebuild -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Debug build

# Unit tests (KeyJawnKitTests, hosted by the app — KeyJawnKit links UIKit,
# so these need a simulator runtime rather than `swift test`)
xcodebuild test -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'

# App and installed system-keyboard UI tests
# Keep the Simulator software keyboard visible. Use Command-K if iPhone hides it.
xcodebuild test -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:KeyJawnUITests

# Real SSH/SFTP checks are opt-in. See docs/ios-app-review.md for the required
# KEYJAWN_LIVE_SSH_* variables and cleanup rules.
xcodebuild test -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:KeyJawnKitTests/SSHLiveIntegrationTests

# Static analysis
xcodebuild analyze -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO
```

**Versioning:** `CURRENT_PROJECT_VERSION` and `MARKETING_VERSION` in `ios/project.yml`
are the single source of truth. Both targets set them and both Info.plists
interpolate them via `$(...)`, so bumping the app target's build number and the
extension's together is the whole job. Do not put literal version strings back into
the `info.properties` blocks — a literal there wins over the build setting, which is
how a "bumped" build once shipped still identifying as build 1 and got rejected as a
duplicate upload.

**Architecture:**
- `KeyJawnKit/` — shared Swift package used by both the main app and the keyboard extension. Contains keyboard models (`KeyboardLayout`, `HostConfig`, `SlashCommand`, `CtrlState`) and UIKit views (`QwertyKeyboardView`, `ExtraRowView`, `SlashCommandPanel`).
- `KeyJawn/` — main SwiftUI app. Host management (`HostListView`, `HostEditView`, `HostStore`), SSH terminal (`SSHSession` via Citadel/SwiftNIO SSH, `TerminalViewController` via SwiftTerm), settings (`SettingsView`, `SSHKeysView`), and `SSHKeyStore` (Keychain-backed Ed25519 identity key).
- `KeyJawnKeyboard/` — `UIInputViewController` keyboard extension. Uses `QwertyKeyboardView` + `ExtraRowView` from KeyJawnKit. Reads host configs through `group.com.keyjawn` (`AppGroupHostStore`) and the SSH identity through the shared Keychain access group (`SharedSSHKeyStore`). Copied-image upload accepts only key-authenticated hosts and uses `CitadelSCPUploader` to write to a configured remote path through SFTP. Password authentication remains available for terminal connections.

**Key decisions:**
- One Ed25519 identity key for the whole app (not per-host). Public key is shown in Settings → SSH keys for the user to copy to `authorized_keys`. Private key is stored once in Keychain under `com.keyjawn / ssh-identity-ed25519`, accessible `WhenUnlockedThisDeviceOnly`, using the shared access group `$(AppIdentifierPrefix)com.keyjawn.shared` so both the app and keyboard extension can read it.
- Upgrades migrate the old app-specific Keychain item into the shared group and delete the former App Group file or `UserDefaults` mirror only after a byte-for-byte read-back succeeds. The app-specific access group remains first in both entitlements during this migration release and is passed explicitly to every legacy query or deletion; an unscoped Keychain deletion can also match the shared item.
- Keyboard preferences (`KeyboardPrefs`) live in the `group.com.keyjawn` suite, not `UserDefaults.standard` — the app and the extension have separate standard suites, so anything written there never reaches the keyboard. KeyJawn exposes its optional shared settings and clipboard features only with Full Access; basic input falls back to defaults without it.
- Host key pinning via `NIOSSHPublicKey(openSSHPublicKey:)`. The main app owns a short-lived NIO probe that captures and displays an unpinned server's fingerprint, closes before authentication, then reconnects through Citadel only after the accepted key is saved. The keyboard extension refuses unpinned hosts.
- Keyboard extension cannot use `present()`. Overlays (slash command panel) are added as `UIView` children of the extension root view.
- Debug build uses automatic signing; Release build uses manual signing with App Store provisioning profiles (`KeyJawn AppStore`, `KeyJawn Keyboard AppStore`).
- App Group `group.com.keyjawn` is registered in Apple Developer portal and enabled on both App IDs (`com.keyjawn` and `com.keyjawn.keyboard`). Provisioning profiles regenerated 2026-02-20.

**App Store Connect status:**

- App ID: `com.keyjawn`; keyboard extension: `com.keyjawn.keyboard`.
- App Store Connect app ID: `6759345867`.
- Review submission ID: `83b2805c-650c-4bf6-91ff-0c6339f36324`.
- Apple sent a guideline 2.5.2 rejection on June 12, 2026. The message identifies
  build 2 and an iPad Air 11-inch (M3). The checked-in source is build 9.
- A fresh signed build 9 archive was created and verified from the corrected
  source on August 31, 2026. The exact archive was uploaded, and Apple processed
  build 9 as valid. Build 9 is selected for version 1.0, manual release is
  enabled, and review submission `83b2805c-650c-4bf6-91ff-0c6339f36324` is
  waiting for review. The reviewer response and test steps were included in the
  review notes. The app is not approved and not publicly released.
- The remote-execution, sandbox, and test evidence are in
  `docs/ios-app-review.md`. The metadata template, submitted review-note basis,
  and response copy are in `docs/app-store-v1.0-metadata.md`.
- `CHANGELOG.md` records source changes separately from distribution state.

**Release gates:**

- Do not change the build number until the complete verification pass succeeds and
  the user approves an unused number. Change both target values together.
- Do not run `ios/scripts/build.sh`, archive, upload, send a reviewer reply, change
  App Store Connect metadata, cancel a submission, or resubmit without separate
  user approval at action time.
- Website publication is a separate external action. A push to `main` that changes
  `website/**` triggers `.github/workflows/deploy-site.yml` and publishes the site.
  Get explicit approval before that push.
- Build 9 has `usesNonExemptEncryption: false` after final archive inspection,
  France exclusion, Apple documentation review, and App Store Connect read-back.
  No French declaration is required. Keep the separate US BIS classification or
  reporting duty open until qualified guidance resolves it.
- App Privacy answers are managed in App Store Connect. Read-only inspection is
  allowed for verification. Do not select Publish without explicit approval.
- Do not store review credentials in the repository or logs.

**Code style:** Swift 6 strict concurrency. Keep UI state on `@MainActor`. Keep NIO
channel handlers on their owning event loop. Do not add unchecked `Sendable`
conformance only to silence a compiler warning. No emojis in source or UI.

## Store service

The store backend (`store/`) handles purchases and APK distribution. Runs on houseofjawn as `keyjawn-store` (port 5060), tunneled via Cloudflare at `keyjawn-store.amditis.tech`.

**Stack:** FastAPI + SQLite + Stripe + Gmail SMTP + Cloudflare R2

**Purchase flow:**
1. User clicks "Buy full version ($4)" on website → Stripe Payment Link
2. Stripe `checkout.session.completed` webhook → creates user in DB → sends download email
3. Download email contains a presigned R2 URL (7-day expiry) for the full APK
4. On new releases: `POST /api/releases/{version}/notify` emails all purchasers with fresh download links

**Key files:**
- `store/app.py` — FastAPI app, mounts routes
- `store/db.py` — SQLite schema (users, downloads, tickets, releases)
- `store/r2.py` — Cloudflare R2 client, presigned URL generation (7-day expiry)
- `store/email_sender.py` — Gmail SMTP, download/update/ticket email templates
- `store/telegram.py` — Telegram alerts for purchases and errors
- `store/routes/webhook.py` — Stripe webhook handler
- `store/routes/download.py` — `POST /api/download` (email → presigned R2 URL)
- `store/routes/releases.py` — `POST /api/releases` (register), `POST /api/releases/{v}/notify` (email all)
- `store/routes/admin.py` — admin dashboard (cookie auth)
- `store/routes/support.py` — support ticket submission
- `store/start.sh` — pulls secrets from `pass`, starts gunicorn

**R2 storage:**
- Bucket: `amditis-tech`
- APK path: `keyjawn/releases/v{version}/app-full-release.apk`
- Presigned URLs expire after 7 days, use S3v4 signatures
- R2 credentials in `pass`: `claude/api/cloudflare-r2-access-key-id`, `claude/api/cloudflare-r2-secret-access-key`

**Stripe:**
- Payment link: `https://buy.stripe.com/14AeVdafC3pi9Yl8nIdnW00`
- Webhook: `keyjawn-store.amditis.tech/webhook/stripe`
- Keys in `pass`: `claude/services/keyjawn-stripe-api-key`, `claude/services/keyjawn-stripe-webhook-secret`

**Admin:**
- Dashboard: `keyjawn-store.amditis.tech/admin` (redirects to `/admin/login` — POST form with password field, sets httponly+secure cookie)
- Token in `pass`: `claude/services/keyjawn-admin-token`

**Security hardening (2026-02-20):**
- Admin login moved from GET `?token=` query param to POST `/admin/login` with cookie session
- `UNSUBSCRIBE_SECRET` fails fast at startup if env var not set
- `/api/download` and `/api/support` return uniform 202 responses regardless of whether email exists (prevents enumeration)
- Stripe webhook uses `rowcount` check on INSERT to prevent duplicate welcome emails on retries

**DB location:** `store/keyjawn-store.db`

## Website

Astro static site at `website/`. Deployed to GitHub Pages at `keyjawn.amditis.tech`.

**Build:** `cd website && npm run build`

**Key pages:** index, features, pricing, privacy, manual, about, thanks (post-purchase redirect)

## Worker (social media automation)

Autonomous marketing agent at `worker/`. Monitors Twitter, Bluesky, and Product Hunt for relevant conversations, curates dev tool content, and posts/engages with Telegram approval.

**Full operational reference:** `docs/claude/worker-readme.md`

> **RUNS ON HOUSEOFJAWN.** Moved from officejawn (Feb 2026) — MSU campus WiFi blocked social platform traffic. Service: `sudo systemctl start/stop keyjawn-worker` on houseofjawn directly.

**Stack:** Python 3, asyncio, aiosqlite, twikit (Twitter), atproto (Bluesky), APScheduler, Redis pub/sub

**Install:** `cd worker && pip install -e ".[dev]"`

**Test:** `cd worker && python -m pytest tests/ -v`

**Run:** `cd worker && python -m worker.main`

**Management CLI:** `cd worker && python -m worker.manage <command>`
- `smoke-test` — test Telegram approval flow
- `status` — show DB stats
- `generate-calendar` — generate weekly content calendar
- `curation-scan` — run one curation scan + evaluation
- `curation-status` — show curation pipeline stats
- `discovery-scan` — run on-platform discovery scan
- `weekly-report` — generate metrics report

**Key config:** `worker/worker/config.py`
**DB:** `worker/keyjawn-worker.db` (aiosqlite, 6 tables)
**Credentials:** `pass show claude/social/twitter-keyjawn`, `pass show claude/services/bluesky-keyjawn`

**Product description for posts:** `worker/worker/content.py:build_generation_prompt()` — update this when features ship.
**Content topic pool:** `worker/worker/calendar_gen.py:TOPICS` — update when adding new demo material.

**Twitter posting:** Twikit is Cloudflare-blocked as of Feb 2026. Use `claude --chrome` on Legion for authenticated posting until a fix is found.

**Social feed engagement (separate from worker):** Handled by the Claude Code browser extension on houseofjawn (display :99, Chromium profile at `~/.config/jawn-browser-profile/`). 8 daily scheduled shortcuts:

| Time | Shortcut | What it does |
|------|----------|--------------|
| 10:00 AM | `bsky-engagement` | Like/engage Bluesky feed |
| 10:15 AM | `twitter-engagement` | Like/engage Twitter feed |
| 11:00 AM | `keyjawn-search-twitter` | Search Twitter for relevant posts and like them |
| 11:30 AM | `keyjawn-post-twitter` | Read SOCIAL.md, write 2–3 original tweets |
| 12:00 PM | `keyjawn-search-bluesky` | Search Bluesky for relevant posts and like them |
| 12:30 PM | `keyjawn-post-bluesky` | Read SOCIAL.md, write 2–3 original Bluesky posts |
| 5:15 PM | `twitter-engagement-pm` | Like/engage Twitter feed |
| 5:45 PM | `bsky-engagement-pm` | Like/engage Bluesky feed |

Shortcut prompts stored at `~/.claude/commands/keyjawn-*.md` on houseofjawn. The post shortcuts start from `https://raw.githubusercontent.com/jamditis/keyjawn/main/SOCIAL.md` so the agent reads the live context doc before navigating to the platform. The worker's `SocialScrollerConfig` has `search_platforms = ()` — the Playwright scroller is not used.

## Google Play

- **Developer account:** `thejawnstars@gmail.com` (verified, account #5874742502246953529)
- **Credentials:** `pass show claude/services/google-play-{email,password,account-id}`
- **Application ID:** `com.keyjawn.lite` (lite only — full version stays on website/Stripe)
- **CI publishing:** Gradle Play Publisher (GPP) 3.13.0 in `.github/workflows/build.yml`
  - `publish-play-store` job runs on version tags after the `release` job
  - Needs `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` GitHub secret (service account key JSON)
  - `PLAY_TRACK` defaults to `internal`; a separate approved promotion updates
    the active closed track
  - Only publishes `publishLiteReleaseBundle` — full flavor is never uploaded to Play
- **Store listing metadata:** `app/src/lite/play/listings/en-US/` (managed by GPP)
- **Asset generation:** `scripts/generate-store-assets.py` (Selenium + Chromium snap → icon, feature graphic, screenshots)
- **Verified state on 2026-08-27:**
  - App created in Play Console
  - KeyJawn Lite 1.3.0 is active on internal testing and one closed testing track
  - Open testing and production are inactive
  - Google Play app signing is active
  - 7 of the required 12 closed-test users are opted in; production access also
    requires at least 14 days with 12 opted-in testers
  - Store listing, content rating, data safety, and all other forms complete
  - Source version 1.4.0, version code 10, targets API 36. A local
    disposable-key bundle verified that manifest but cannot be uploaded. The
    Google Play build still reports an API-level action due August 31, 2026.
  - Android unit suites pass 425 full-flavor and 421 lite-flavor tests. Both
    lint variants complete without errors. Existing lint warnings and stale
    baseline entries need a separate Android cleanup pass.
  - A version tag uploads to internal testing. Promotion to closed testing or
    production is a separate external action and needs explicit approval.

## Release checklist — SOCIAL.md

`SOCIAL.md` in the repo root is the live context doc read by the Claude Code browser extension before each social media session. Keep it current.

**Update SOCIAL.md whenever any of the following happen:**

- A new version is tagged and released (update "Current version" and "Recent changes")
- A feature ships, changes behavior, or is removed (update "Features to highlight" and pain points)
- App Store or Google Play status changes (review outcome, new track, rejection/approval)
- A backlog item moves to in-progress or ships (update "What's in progress / coming soon")
- Distribution changes (new download location, price change, new platform)

**Where to update:** `SOCIAL.md` → "Current version", "Recent changes", "What's in progress / coming soon"

Add a line to "Recent changes" in every release commit:
```
git add SOCIAL.md && git commit --amend --no-edit
```
Or add it as a separate commit before tagging.

---

## Code style

- Kotlin with standard Android conventions
- No emojis in source code, logs, or UI text
- Sentence case for all UI strings (not Title Case)
- **No direct LLM API calls.** Never make direct API calls to LLM services (Anthropic, OpenAI, Google AI, etc.) unless Joe gives explicit permission. Use CLI tools (`claude -p`, `gemini -p`) via subprocess calls instead — these use existing subscriptions at no marginal cost.
