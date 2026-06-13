# KeyJawn Red-Team Security Assessment (Android, iOS, and whole repo)

Date: 2026-02-21  
Assessor: Codex (manual adversarial code review)

## Scope
- Android app (keyboard + SCP upload paths)
- iOS app + iOS keyboard extension
- Worker automation backend (`worker/`)
- Repo-wide abuse paths affecting trust boundaries and secrets

## Methodology
- Static adversarial review of auth, secret storage, transport security, command execution, and trust boundaries.
- Manual exploitability scoring by impact × likelihood.
- Focused on realistic attacker models:
  1. On-path network attacker (MitM)
  2. Malicious local app / extension context abuse
  3. Compromised host with config write access
  4. Adversary with partial automation-infra access (Redis / bot integration)

---

## Executive summary
The largest systemic issue is **inconsistent SSH trust guarantees**: both iOS app and iOS keyboard extension will connect with host key verification disabled when a host key is absent or invalid. That creates a practical MitM path to credential theft/session hijack.

The second major issue is **private key material copied from Keychain into App Group `UserDefaults`** for extension access. This weakens key custody and enlarges secret exposure in exactly the component (`RequestsOpenAccess=true` keyboard extension) with the widest attack surface.

The automation worker has a **fail-open approval mode** (auto-approve when Telegram creds are absent) and several shell-based execution chokepoints that are acceptable only if config integrity is guaranteed.

---

## Findings

### 1) iOS SSH connections allow unverified host identity (MitM risk)
**Severity:** High  
**Area:** iOS app terminal + iOS keyboard uploader

**Evidence**
- App terminal falls back to `.acceptAnything()` when no pinned host key is present.  
- Keyboard SCP uploader does the same fallback for uploads.

**Attack path**
1. Victim connects from hostile Wi‑Fi / network segment.
2. Attacker presents rogue SSH server cert/key.
3. Client accepts host key (because verification is disabled), attacker relays/harvests credentials and session data.

**Why this matters**
This defeats core SSH identity guarantees and enables transparent interception.

**Remediation**
- Replace fail-open behavior with strict TOFU/pinning UX:
  - first connect: display fingerprint + explicit trust prompt
  - later connects: hard fail on mismatch
- Never silently downgrade to `.acceptAnything()`.

---

### 2) iOS private SSH key mirrored into App Group `UserDefaults`
**Severity:** High  
**Area:** iOS key custody

**Evidence**
- Key is stored in Keychain (good), but raw private key bytes are also written to App Group defaults for keyboard extension use.

**Attack path**
1. Attacker gains code exec in extension context / abuses open-access keyboard paths / local forensic access.
2. Reads App Group defaults blob with private key bytes.
3. Reuses key to authenticate as victim to configured hosts.

**Why this matters**
This bypasses Keychain protections and makes theft of long-lived SSH identity significantly easier.

**Remediation**
- Stop mirroring raw private keys to `UserDefaults`.
- Use shared Keychain access group for extension-safe key retrieval.
- If impossible, derive short-lived delegated credentials instead of exporting root private key material.

---

### 3) iOS host metadata mirrored into App Group defaults
**Severity:** Medium  
**Area:** iOS extension trust boundary

**Evidence**
- Host list JSON is mirrored to App Group defaults for extension reads.

**Attack path**
Tampering with host list in shared container can redirect uploads/sessions to attacker-controlled endpoints (especially dangerous when host-key verification is optional).

**Remediation**
- Integrity-protect host config (e.g., signed blob with app-held key).
- Validate host list against strict schema and enforce pinning requirement before network use.

---

### 4) iOS keyboard extension requests Open Access (expected but high-risk context)
**Severity:** Medium  
**Area:** iOS keyboard extension exposure

**Evidence**
- `RequestsOpenAccess` is enabled.

**Risk note**
Open access is often required for network features, but it broadens data exfiltration and supply-chain impact if extension logic is compromised.

**Remediation**
- Minimize extension privileges and data residency.
- Move sensitive operations to containing app where possible.
- Add hard telemetry/alerts for unexpected upload/session behavior.

---

### 5) Android SCP client has insecure API footgun (`knownHostsManager` nullable)
**Severity:** Medium  
**Area:** Android transport security API design

**Evidence**
- `ScpUploader.upload(...)` accepts nullable `knownHostsManager`; when null it sets `StrictHostKeyChecking=no`.

**Attack path**
Future call sites can accidentally omit manager and silently disable host verification, reintroducing MitM risk.

**Remediation**
- Make host-key verifier mandatory in API signature.
- Remove `StrictHostKeyChecking=no` branch.
- Enforce compile-time secure defaults.

---

### 6) Worker approval path fails open when Telegram is unconfigured
**Severity:** Medium  
**Area:** Automation governance / posting safety

**Evidence**
- If bot token/chat id missing, `request_approval` returns `"approve"` automatically.

**Attack path**
Misconfiguration or secret-loss incident can convert gated actions into ungated auto-execution.

**Remediation**
- Fail closed (`deny`/`backlog`) when approval channel unavailable.
- Add startup health check that refuses to run posting workflows without approval config.

---

### 7) Shell-based worker execution relies on config integrity
**Severity:** Medium  
**Area:** Worker command execution

**Evidence**
- `asyncio.create_subprocess_shell` is used in curation evaluation and social scroller command execution.

**Attack path**
If an attacker can tamper with config values (e.g., model/script path/ssh host), they may gain command injection or command substitution leverage.

**Remediation**
- Prefer `create_subprocess_exec` with argv arrays.
- Strict allowlist validation for model names, hosts, and script paths.
- Immutable config source + permission hardening.

---

## Platform posture summary

### iOS
- Strong points: main app uses Keychain for base key storage.
- Critical weaknesses: host verification can be skipped; key material copied to weaker store; open-access extension magnifies blast radius.
- Priority: **fix host verification and key custody first**.

### Android
- Strong points: encrypted host storage; upload path uses known-host workflow at current call site.
- Weakness: uploader API still permits insecure branch; host-key fingerprints in regular prefs can be tampered on compromised device.
- Priority: remove insecure branch and enforce verifier requirement.

### Worker / whole system
- Strong points: approval flow exists; HTML escaping is done for Telegram rendering.
- Weaknesses: fail-open approvals + shell execution surfaces dependent on trusted config boundary.
- Priority: fail-closed governance and shell hardening.

---

## Prioritized remediation plan
1. **Block all unverified SSH sessions** (iOS app + keyboard, Android future-proofing).
2. **Eliminate private key export to App Group defaults**; move to shared Keychain access group.
3. **Fail closed on missing approval channel** in worker.
4. Convert shell invocations to exec-style APIs and validate config inputs.
5. Add regression tests asserting:
   - no `.acceptAnything()` in production code paths
   - approval request without Telegram config returns backlog/deny
   - uploader cannot be called without host verifier.

## Suggested adversarial test cases (post-fix)
- MitM harness with rogue SSH server key to verify hard-fail behavior.
- Extension-container tamper test for host list and key retrieval.
- Worker boot with missing Telegram creds must halt posting pipeline.
- Config poisoning tests for command construction paths.
