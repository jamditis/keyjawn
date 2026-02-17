# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

KeyJawn is a custom Android keyboard (InputMethodService) for using LLM CLI agents from a phone. It provides terminal keys (Esc, Tab, Ctrl, arrows), voice-to-text, slash command shortcuts, and SCP image upload — all in a dedicated row above a standard QWERTY layout.

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
- Dashboard: `keyjawn-store.amditis.tech/admin?token=<ADMIN_TOKEN>`
- Token in `pass`: `claude/services/keyjawn-admin-token`

**DB location:** `store/keyjawn-store.db`

## Website

Astro static site at `website/`. Deployed to GitHub Pages at `keyjawn.amditis.tech`.

**Build:** `cd website && npm run build`

**Key pages:** index, features, pricing, privacy, manual, about, thanks (post-purchase redirect)

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
