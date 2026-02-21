# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

KeyJawn is a custom keyboard for using LLM CLI agents from a phone. It provides terminal keys (Esc, Tab, Ctrl, arrows), voice-to-text, slash command shortcuts, and SCP image upload — all in a dedicated row above a standard QWERTY layout.

- **Android**: `InputMethodService`-based keyboard extension. Two flavors: lite (free, Google Play) and full ($4, Stripe/website).
- **iOS**: Standalone SwiftUI app with a built-in SSH terminal (SwiftTerm + SwiftNIO SSH via Citadel) and a companion `UIInputViewController` keyboard extension. Currently in TestFlight beta; App Store launch pending.

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

# Archive + export for TestFlight/App Store
bash ios/scripts/build.sh
```

**Architecture:**
- `KeyJawnKit/` — shared Swift package used by both the main app and the keyboard extension. Contains keyboard models (`KeyboardLayout`, `HostConfig`, `SlashCommand`, `CtrlState`) and UIKit views (`QwertyKeyboardView`, `ExtraRowView`, `SlashCommandPanel`).
- `KeyJawn/` — main SwiftUI app. Host management (`HostListView`, `HostEditView`, `HostStore`), SSH terminal (`SSHSession` via Citadel/SwiftNIO SSH, `TerminalViewController` via SwiftTerm), settings (`SettingsView`, `SSHKeysView`), and `SSHKeyStore` (Keychain-backed Ed25519 identity key).
- `KeyJawnKeyboard/` — `UIInputViewController` keyboard extension. Uses `QwertyKeyboardView` + `ExtraRowView` from KeyJawnKit. Uses `group.com.keyjawn` App Group to share host configs and SSH key bytes with the main app (`AppGroupHostStore`, `AppGroupSSHKeyStore`). SCP image upload via `CitadelSCPUploader` (Citadel/SwiftNIO SFTP).

**Key decisions:**
- One Ed25519 identity key for the whole app (not per-host). Public key is shown in Settings → SSH keys for the user to copy to `authorized_keys`. Private key stored in Keychain under `com.keyjawn / ssh-identity-ed25519`.
- Host key pinning via `NIOSSHPublicKey(openSSHPublicKey:)`. If no host key is stored, connects with `.acceptAnything()` (warns in UI).
- Keyboard extension cannot use `present()`. Overlays (slash command panel) are added as `UIView` children of the extension root view.
- Debug build uses automatic signing; Release build uses manual signing with App Store provisioning profiles (`KeyJawn AppStore`, `KeyJawn Keyboard AppStore`).
- App Group `group.com.keyjawn` is registered in Apple Developer portal and enabled on both App IDs (`com.keyjawn` and `com.keyjawn.keyboard`). Provisioning profiles regenerated 2026-02-20.

**App Store Connect:**
- App ID: `com.keyjawn` / keyboard extension: `com.keyjawn.keyboard`
- App Store Connect app numeric ID: `6759345867`
- App Store version 1.0 ID: `071542f6-0cdb-43a5-b07c-7b74688e937b`
- Build v2 ID: `7e8af1a4-eac8-4b57-8ae0-8a3bd2655c1f` — current build in review
- Screenshots uploaded: `APP_IPHONE_67` (1290×2796) + `APP_IPAD_PRO_3GEN_129` (2048×2732) for en-US localization
- Provisioning profiles managed via Apple Developer API (`ios/scripts/asc.py`)
- App Store Connect API credentials in `pass` at `claude/services/appstore-connect-{issuer-id,key-id,api-key}`

**App Store Connect API notes:**
- `appStoreVersionSubmissions` is deprecated — use `reviewSubmissions` + `reviewSubmissionItems` instead
- Submit flow: `POST /v1/reviewSubmissions` → `POST /v1/reviewSubmissionItems` → `PATCH /v1/reviewSubmissions/{id}` with `submitted: true`
- Pricing set via `POST /v1/appPriceSchedules` with `baseTerritory: USA` and inline `appPrices` using `${local-id}` format (must use file-based script, not heredoc — shell strips `${}`)
- App Privacy (data usage) labels must be published via web UI — no API endpoint exists for this
- `usesNonExemptEncryption: false` must be set on each build via `PATCH /v1/builds/{id}` (SSH uses IETF-standard algorithms, exempt from EAR)
- Screenshot upload flow: create set → reserve slot (`POST /v1/appScreenshots`) → PUT bytes to `uploadOperations[].url` → commit with MD5 checksum

**Submission status (2026-02-20):**
- App Store review: submitted, state `WAITING_FOR_REVIEW` (review submission ID `83b2805c-650c-4bf6-91ff-0c6339f36324`)
- TestFlight external group: "KeyJawn Beta (external)" (ID `360a2954-431c-4d74-aa68-faaf86baa926`)
- Public TestFlight link: `https://testflight.apple.com/join/8vMqguKK` (limit 50 testers)
- Beta App Review: `IN_REVIEW` — must clear before external testers can install
- Internal group: "KeyJawn Beta" (ID `55817758-6243-4d2b-b0f3-ffc95221cbdb`)

- **Submission `83b2805c` (v1.0) rejected 2026-02-20** for Guideline 3.2.2 — reviewer interpreted the slash command panel as a third-party app collection due to tool-branded category names (`claudeCode`, `aider`, `codex`) in the data model and a branded screenshot.
  - Reply sent to Apple explaining `textDocumentProxy.insertText()` behavior (text autocomplete, not a storefront)
  - `SlashCommand.Category` enum renamed: `claudeCode`→`session`, `aider`→`context`, `codex`→`files`
  - Aider command set replaced with Gemini CLI commands (commits `56c11fd`, `b361c92`, `c495696`)
  - `ios-claude-code.png` renamed to `ios-slash-commands.png` (commit `325f1c5`) — **PNG content still needs replacement** with an unbranded keyboard screenshot from Mac/Simulator
  - App Store description audited — no third-party tool names found, no changes needed

### Mac checklist (do this when you sit down at the MacBook)

**Step 1: Pull latest**
```bash
cd /path/to/keyjawn
git pull origin main
```

**Step 2: Take a new screenshot**
1. Open Xcode, run the KeyJawn app on the iPhone 17 Pro simulator
2. Open any text field (e.g. Notes), switch to KeyJawn Keyboard, tap the `/` key on the symbols layer to open the slash command panel
3. Screenshot: panel open over the keyboard showing shortcuts (`/compact`, `/clear`, etc.) — **no Claude Code or other third-party tool branding visible in the terminal area**
4. Save it as the replacement file:
```bash
cp ~/Desktop/screenshot.png website/public/screenshots/ios-screenshots/ios-slash-commands.png
```
5. Commit:
```bash
git add website/public/screenshots/ios-screenshots/ios-slash-commands.png
git commit -m "replace slash commands screenshot with unbranded keyboard UI"
```

**Step 3: Bump the build number**

Open `ios/project.yml`. Find `CURRENT_PROJECT_VERSION` and increment it by 1 (e.g. `7` → `8`). Then commit:
```bash
git add ios/project.yml
git commit -m "bump iOS build number for 3.2.2 resubmission"
```

**Step 4: Build and upload to TestFlight**
```bash
cd ios
bash scripts/build.sh
```
Expected: archives, exports IPA, uploads to TestFlight. Build appears in App Store Connect → TestFlight within ~15 min with status "Processing".

**Step 5: Push**
```bash
git push origin main
```

**Step 6: Decide on resubmission**

Check App Store Connect → App Review for Apple's response to the appeal (submission `83b2805c`):
- **Appeal approved** → submit the new build as an update when ready
- **Appeal denied or no response after 7 days** → cancel the current submission, then submit the new build with this note in App Review Information → Notes:

> The slash command panel is a text shortcut picker. Tapping any item inserts a plain text string (e.g. "/compact") into the active text field. No third-party apps are embedded, linked, or sold. Category labels in the code have been renamed from tool names to functional terms (Session, Context, Files) to remove any ambiguity.

**Code style:** Swift 6 strict concurrency. Use `@MainActor` for UI classes. For Citadel types that lack `Sendable` conformance, add `@unchecked @retroactive Sendable` extensions (see `SSHSession.swift`). No emojis in source or UI.

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

**Social feed engagement (separate from worker):** Handled by the jawn-browser extension on houseofjawn (display :99, profile at `~/.config/jawn-browser-profile/`). Daily shortcuts run via the Claude Code browser extension — `/like-post` at 9am (Twitter) and 6pm (Bluesky). The worker's `SocialScrollerConfig` is intentionally scoped to Twitter/Bluesky only with `search_platforms = ()` — the Playwright-based scroller is not used.

## Google Play

- **Developer account:** `thejawnstars@gmail.com` (verified, account #5874742502246953529)
- **Credentials:** `pass show claude/services/google-play-{email,password,account-id}`
- **Application ID:** `com.keyjawn.lite` (lite only — full version stays on website/Stripe)
- **CI publishing:** Gradle Play Publisher (GPP) 3.13.0 in `.github/workflows/build.yml`
  - `publish-play-store` job runs on version tags after the `release` job
  - Needs `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` GitHub secret (service account key JSON)
  - `PLAY_TRACK` defaults to `internal` — change to `production` once verified
  - Only publishes `publishLiteReleaseBundle` — full flavor is never uploaded to Play
- **Store listing metadata:** `app/src/lite/play/listings/en-US/` (managed by GPP)
- **Asset generation:** `scripts/generate-store-assets.py` (Selenium + Chromium snap → icon, feature graphic, screenshots)
- **Setup status (completed 2026-02-17):**
  - App created in Play Console
  - First AAB uploaded to internal testing (v1.3.0)
  - Store listing, content rating, data safety, and all other forms complete
  - 7 testers configured on internal track
  - CI auto-publish ready (`publish-play-store` job) -- change track to `production` when ready

## Code style

- Kotlin with standard Android conventions
- No emojis in source code, logs, or UI text
- Sentence case for all UI strings (not Title Case)
- **No direct LLM API calls.** Never make direct API calls to LLM services (Anthropic, OpenAI, Google AI, etc.) unless Joe gives explicit permission. Use CLI tools (`claude -p`, `gemini -p`) via subprocess calls instead — these use existing subscriptions at no marginal cost.
