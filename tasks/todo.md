# App Store rejection remediation and iOS cleanup

## Goal

Resolve the guideline 2.5.2 rejection, reduce the iOS review surface, verify
build 9, and keep its manual release blocked while App Review is pending.

## Scope

- Include the iOS app, keyboard extension, `KeyJawnKit`, shared iOS code, iOS tests, generated Xcode project, release metadata, and related documentation.
- Preserve Android and store runtime behavior. Verify Android release readiness
  where the Google Play deadline requires it. Update stale tests, public copy,
  and website metadata only where the final all-surface sweep requires it.
- Preserve the user's current `.gitignore` change and shared Xcode scheme files.
- Do not use external skills or plugins. The user later authorized the built-in
  computer-use workflow for a read-only Google Play Console check. No Play
  Console state was changed.

## Session task list — August 28, 2026

### Session goal

Complete the remaining safe App Store rejection-remediation checks, keep the
local evidence current, and stop before every release or external-write gate.

### Active release path

- [x] Receive explicit approval to complete the remaining in-scope release
  process while the user is away. Approval received on August 28, 2026. This
  includes the bounded keyboard and brand work, verification, an unused build
  number, archive creation, upload, App Store Connect review metadata, build
  selection, and resubmission. It does not include unrelated repositories,
  website publication, public tags, or destructive cleanup.

- [x] Recover the current repository state and preserve the dirty working tree.
- [x] Confirm `HEAD` matches `origin/main`, both iOS targets remain at version
  `1.0.0`, build `8`, and `ITSAppUsesNonExemptEncryption` remains absent.
- [x] Build the optimized Release simulator app without code signing and inspect
  the app and keyboard extension binaries for linked Citadel cryptography.
- [x] Run a signed Release device preflight without creating an archive. Xcode
  selected App Store profiles for `com.keyjawn` and `com.keyjawn.keyboard`, both
  with `get-task-allow=false`; strict code-signature and embedded-extension
  validation passed. Independent signing review found no current P1 or P2
  blocker and requires the same checks on the final archive.
- [x] Record the export-compliance evidence and close the independent review
  with no remaining P1 or P2 finding.
- [x] Pair the physical iPad (A16) running iPadOS 26.6.1 with this Mac over its
  cable. CoreDevice and Xcode now identify it. Developer Mode was disabled at
  the first post-pairing check.
- [x] Enable Developer Mode and confirm the paired wired developer tunnel.
- [x] Attempt the current-tree Debug device build without allowing Apple
  Developer account changes. It stopped before compilation because this Mac
  has no cached iOS development profile for either app target. This is signing
  setup evidence, not a code failure.
- [x] Obtain explicit approval to let Xcode register this iPad and create or
  update development profiles for `com.keyjawn` and `com.keyjawn.keyboard`.
  Approval was received on August 28, 2026.
- [x] Build, sign, inspect, install, and launch the current exact-tree Debug app
  on the iPad. Both bundles are version `1.0.0`, build `8`; strict signature
  validation passed; both profiles contain this iPad; and the installed app
  reports the expected version. This is development-signing evidence, not an
  App Store archive result.
- [x] Build and install the final build 9 tree on Joe's iPad. Physical UI
  automation still times out while enabling automation mode, so this proves
  installation but does not replace the manual gesture, rotation, microphone,
  and reviewer-account checks.
- [ ] Verify microphone permission, transcription, and rotation. This check
  does not change App Store state.
- [x] Obtain explicit approval to create or change the isolated reviewer
  account on `houseofjawn`. Approval was received on August 28, 2026.
- [x] Provision the locked `houseofjawn` `keyjawn-review` account with home directory
  `/home/keyjawn-review`, writable directory `/home/keyjawn-review/uploads`, no
  privileged group, and no SSH forwarding, agent forwarding, X11, or tunneling.
  Password authentication remains disabled for every other SSH account.
- [x] Audit `houseofjawn`, `officejawn`, `landofjawn`, Legion2025, and A4000 for
  reviewer-host suitability. No host had a verified public raw SSH endpoint.
  `landofjawn` is the preferred host because it is a Linux desktop with the best
  current uptime, no suspend target enabled, and no Tailscale Funnel conflict.
- [x] Obtain explicit approval to create a dedicated reviewer SSH service on
  `landofjawn` loopback port `2222` and expose only that service through a
  public Tailscale Funnel on port `10000`. Approval was received on August 28,
  2026. Keep the normal SSH service private.
- [x] Configure the dedicated service to allow only `keyjawn-review`, with root
  login, forwarding, agent forwarding, X11, tunneling, and user startup files
  disabled. The locked account has a valid interactive shell, private home and
  upload directories, a low unauthenticated-connection limit, and a September
  30, 2026 expiry. The separate configuration passed validation before start.
- [x] Repair the dedicated service after a reboot removed `/run/sshd`. A
  failing pre-fix service check and journal trace identified the missing runtime
  directory. The custom unit now creates it before configuration validation.
  Post-fix checks confirm `root:root 0755`, an active service, a loopback-only
  `127.0.0.1:2222` listener, the reviewed host-key fingerprint, and the active,
  enabled `keyjawn-review-expire.timer` for September 30, 2026.
- [x] Verify the Funnel's TCP reachability from outside the tailnet. Five
  independent internet probes reached the endpoint. A separate endpoint-name
  key scan presented the dedicated Ed25519 fingerprint
  `SHA256:TN5b86aTecgmBw8uHquuHSeWmeReLI4HSDqiXOnXvdA`, and the locked account
  rejected login. The normal SSH service is not part of the Funnel. Keep the
  public hostname and future password out of this repository.
- [x] Enable a persistent shutdown timer for September 30, 2026. It disables
  the Funnel, stops the dedicated SSH service, and locks the account. Renew it
  only if App Review is still active near that date.
- [x] Run an independent, read-only review of the deployed endpoint. The review
  took 241 seconds and covered the account, permissions, effective SSH policy,
  services, sockets, timer, Funnel mapping, and host-key identity. It found no
  isolation or service configuration defect. It retained one P1 acceptance
  gate: complete an SSH handshake from a device that is not using the tailnet.
  It also recorded that Funnel does not preserve a source address that OpenSSH
  can use as acceptance evidence, and that final teardown must disable the
  dedicated service and timer. This was not a repository diff review.
- [x] Run a one-time public password and SFTP preflight with a generated
  process-memory-only password. Password login passed, SFTP upload passed, the
  remote bytes matched, the exact test file was removed, and the account was
  locked again. The first byte-check command expanded before `sudo` and could
  not traverse the private home directory; its cleanup succeeded, and the
  corrected check passed.
- [x] Stop and relock the reviewer account after the first physical-device
  password command changed the password but failed before clipboard delivery
  and timer creation. The failure exposed no password and left the dedicated
  service and monthly expiry timer active. Correct the quoting before retry.
- [x] Create the final review password without writing it to the repository or
  logs. The password was set through process memory, restored to the Mac
  clipboard after each temporary hostname paste, and not printed in test output.
  Sensitive physical-test results were moved to Trash. Enter the password only
  in the protected App Store Connect review field.
- [x] Replace the physical-iOS POSIX SSH bootstrap with SwiftNIO Transport
  Services in the app and keyboard extension. A failing source-boundary test
  preceded the change. The focused compile and transport tests pass, and the
  physical iPad no longer remains in the original POSIX connecting state.
- [x] Prove that the original raw Funnel mode accepts TCP but does not forward
  the SSH stream. Tailscale's public side requires TLS. The reviewer endpoint
  now uses TLS-terminated TCP on public port `10000`, and an independent TLS
  client receives the dedicated OpenSSH banner. Plain SSH remains the app's
  per-host default.
- [x] Add a backward-compatible per-host TLS tunnel setting. Legacy host JSON
  decodes with the option off, enabled hosts round-trip, and the terminal and
  copied-image uploader share the same Network.framework transport.
- [x] Replace raw channel-error text with a tested user message for TCP and TLS
  setup failures. Authentication and host-key errors remain specific.
- [ ] Run the isolated reviewer-account flow: password login, fresh public-key
  authorization, key login, voice, shortcuts, clipboard, copied-image upload,
  settings, and rotation. Remove only temporary keys and files after the dry
  run. Keep the reviewer account active through App Review. The physical iPad
  saved the reviewer DNS name and TLS setting without committing or printing
  the host name or password. The first two public-path attempts failed before SSH because
  AdGuard re-enabled its on-demand VPN. The iPad then locked before the
  on-demand rule could be disabled. Repeat this gate after the device is
  unlocked; do not treat the failed attempts as reviewer-endpoint evidence.
- [ ] After the reviewer flow works, freeze its functional behavior and write a
  focused visual specification for onboarding, host setup, connection states,
  terminal chrome, settings, shared colors, typography, icons, and app assets.
  Reuse the existing KeyJawn brand. Do not change SSH, SFTP, storage,
  permissions, signing, or keyboard input behavior in this pass.
- [x] Reproduce the physical iPad portrait overlap where the system undo, redo,
  and paste controls cover KeyJawn's top-left extra-row actions. The geometry
  test first failed with the KeyJawn control at `6 pt` and the system group
  ending at `142 pt`. Full-width iPad layouts now reserve `152 pt`; the focused
  unit and UI geometry tests pass. The exact tree also builds and installs on
  Joe's iPad. A wired QuickTime capture of build 9 on Joe's iPad in portrait
  confirms that the iPadOS undo, redo, and paste group does not cover the
  KeyJawn extra row. The full keyboard is visible with no clipping, and the
  swipe-down secondary-character labels are visible. The reduced local evidence
  is `/tmp/keyjawn-ipad-portrait-audit.xAAAxp/keyboard-system-controls-final.png`.
  This visual evidence does not replace the passing UI gesture test.
- [x] Review the keyboard density and reduce unused spacing without shrinking
  touch targets below the accessibility requirement. The new rendered-frame
  test first measured and rejected the old `11 pt` QWERTY row gap. The gap is
  now `7 pt`, the test passes, and every tested iPad key remains at least
  `44 pt` high.
- [x] Specify and test Apple-style swipe-down secondary characters so common
  symbols do not require a full layer change. The iPad letter keys now show the
  standard number and punctuation map and insert it with a downward flick. The
  mapping, theme contrast, touch cancellation, one-shot Shift path, primary tap,
  phone exclusion, and real IME path all have focused passing tests. The symbol
  layer remains available and terminal output is unchanged.
- [x] Trace the reported Paste duplication. KeyJawn has no direct Paste action:
  `Clip` snapshots the current system clipboard into pinned and recent history,
  while the Paste control is Apple's assistant action. Keep `Clip` and the new
  assistant inset because the two controls have different jobs.
- [x] Review the bounded visual direction with the user before implementation.
  The user approved finishing the remaining in-scope release work while away
  on August 28, 2026. Preserve functional behavior and keep the visual pass
  behind the keyboard and release validation gates.
- [x] Implement the approved visual pass without changing app behavior. The
  host app now uses the website palette, prompt mark, launch background, form
  surfaces, onboarding progress, empty state, and settings identity. Exact
  palette and contrast tests pass. All four launch-flow screenshots were
  inspected on iPhone and iPad with no clipping or unreadable text.
- [x] Complete the post-visual-pass independent tracked-and-untracked diff
  review before the release archive. The full review and first bounded
  follow-up are complete. The final clean-candidate confirmation reported
  0 unresolved P1 and 0 unresolved P2.
- [x] Obtain explicit approval for an unused build number. Approval received on
  August 28, 2026 as part of the remaining in-scope release authorization.
- [x] Select an unused build number, update both iOS targets together, and
  regenerate the Xcode project. The live App Store Connect API listed only
  builds 1, 2, and 8. Both targets now use build 9.
- [x] Run the exact-tree format, analysis, unit, UI, metadata, entitlement, and
  Release build checks after the build-number change. The final build 9 tree
  passes 202 unit tests with 1 opt-in live SSH skip and 0 failures. All 5 UI
  tests pass on iPad Pro 13-inch and iPhone 15 Pro simulators on iOS 17.5.
  Both real system-keyboard cases also pass on iPad Pro 13-inch (M5) with
  iOS 26.5, including the Full Access permission prompt and restoration.
  The final iPad result is
  `/tmp/keyjawn-build9-ui-ipad-final-20260829.xcresult`; the final unit result is
  `/tmp/keyjawn-build9-units-final-clean-index-20260829.xcresult`; and the iOS 26.5
  keyboard result is
  `/tmp/keyjawn-build9-ime-ipad-ios26-postfix-20260829.xcresult`.
  Static analysis passes for the app and extension. The Release device build
  passes with matching build 9 plists, arm64 binaries, App Store profiles,
  production entitlements, privacy manifests, and the expected cryptography
  symbols. The final build 9 tree also built and installed on Joe's iPad;
  physical UI automation still times out before assertions while enabling
  automation mode.
- [ ] Review the complete tracked and untracked diff. Record the exact commit
  and working-tree evidence used for the archive. Do not push a tree that
  changes `website/**` without separate website-publication approval. The
  earlier 134-file candidate review is complete, but the later Network.framework
  and TLS tunnel changes require a new exact-tree review. Record the commit used
  for the archive after the remaining physical gates pass. No commit or push
  was made.
- [x] Obtain approval to create the distribution archive. Approval received on
  August 28, 2026 as part of the remaining in-scope release authorization.
- [x] Create and inspect the first signed build 9 archive. The current source
  has changed since that archive, so the archive is superseded and cannot be
  uploaded. Create and inspect a fresh archive after the bounded correction
  cycle. A stale incompatible keyboard profile has the same display name on
  this Mac, so do not rely on the profile name alone.
- [x] Confirm whether distribution includes France and obtain the release-level
  export-compliance determination. France is excluded from the verified
  175-territory availability record. The fresh signed build 9 archive uses
  standard encryption outside the Apple operating system. Complete the
  build-specific App Store Connect questionnaire after upload processing.
- [x] Obtain approval to upload the inspected archive. Approval received on
  August 28, 2026 as part of the remaining in-scope release authorization.
- [x] After upload processing finishes, verify the processed build, signing,
  privacy manifest, and export-compliance state. Build 9 is valid. Apple's
  processed bundle reports the expected app and extension identifiers, arm64,
  iOS 17.0 minimum, distribution entitlements, App Group, and Keychain group.
  The exact uploaded IPA contains the verified first-party privacy manifests.
  Clean-install behavior remains covered by the accepted simulator waiver and
  prior test evidence; no new physical-device claim was made.
- [x] Obtain approval for the required App Store Connect review metadata edits,
  reviewer response, build selection, and resubmission. Approval received on
  August 28, 2026 as part of the remaining in-scope release authorization.
  Record the result after each action.
- [ ] Monitor App Review and record the final decision. Do not publish the
  website, tag a release, or change another store without separate approval.
- [ ] After App Review finishes, obtain approval before disabling or removing
  the reviewer account.
- [x] Confirm this repository contains no Windows, MSIX, AppX, or Microsoft
  Store release project. Treat Microsoft Partner Center as a separate scope
  until the user identifies its product and repository.

### Safe work

- [x] Recover the August 27 handoff and preserve the dirty working tree.
- [x] Fetch `origin` without changing the checkout and confirm `HEAD` and
  `origin/main` are both `e0aff63`.
- [x] Run `git diff --check` and confirm both iOS targets remain at version
  `1.0.0`, build `8`.
- [x] Run XcodeGen and confirm that it reproduces the same generated project
  hash.
- [x] Run the pre-review iPad Pro 13-inch (M4)/iOS 17.5 unit checkpoint with isolated
  derived data: 184 executed, 183 passed, 1 expected live SSH test skipped, and
  0 failed. Result: `/tmp/keyjawn-ipad-units-20260828.xcresult`.
- [x] Run a read-only Claude review of the complete tracked and untracked diff.
  It reported 0 P1, 4 P2, and 9 P3 findings. Verify each finding against the
  code and current external state before changing the product.
- [x] Fix all four verified P2 findings with failing tests first: correct the
  reviewer upload sequence, protect website publication and current privacy and
  export-compliance gates, and remove public claims that the closed TestFlight
  invitation is accepting testers.
- [x] Resolve the actionable P3 findings with focused tests: preserve armed Ctrl
  for unmapped non-ASCII input, correct upload-panel and SCP-key copy, disclose
  the Full Access boundary for user-created shortcuts, recapture the affected
  screenshots, align all five captions, record the non-ASCII fix, exercise a
  built-in shortcut with Full Access off, and add the local store favicon.
- [x] Reject the proposed terminal delegate guard after tracing both input
  paths. `TerminalInputView` owns user input. SwiftTerm's delegate callback
  carries emulator protocol replies that must reach the remote session.
- [x] Run the final iPhone 15 Pro Max/iOS 17.5 unit suite: 190 executed, 189
  passed, 1 expected live SSH test skipped, and 0 failed. Result:
  `/tmp/keyjawn-iphone-units-final-v4-20260828.xcresult`.
- [x] Run the final iPad Pro 13-inch (M4)/iOS 17.5 unit suite: 190 executed, 189
  passed, 1 expected live SSH test skipped, and 0 failed. Result:
  `/tmp/keyjawn-ipad-units-final-v4-20260828.xcresult`.
- [x] Run the full store suite: 34 passed. Run the full worker suite in an
  isolated environment: 160 passed and 12 environment-dependent tests skipped.
  Run the website production build: 10 pages built.
- [x] Run the real system-keyboard UI path with Full Access off and confirm that
  typing plus the built-in `/compact` shortcut produce `hi/compact`.
- [x] Prevent silent system-keyboard skips and resolve the CI iPad destination
  by UDID. The final iOS 26.5 run executed both Full Access cases with 2 passed,
  0 skipped, and 0 failed. The iOS 17.5 unit suite executed 203 tests with 202
  passed, 1 expected opt-in live SSH skip, and 0 failed.
- [x] Recapture and inspect the changed Full Access onboarding screenshot on
  iPhone and iPad. Confirm all 10 App Store JPEGs have the accepted dimensions
  and no alpha channel.
- [x] Run a fresh read-only Claude review of the final diff. Session
  `29ccc668-f40d-4d3e-a629-2d1fb7a0332b` confirmed that all four verified P2
  findings are closed and that no P1 or P2 remains.
- [x] Resolve the five follow-up P3 findings. Widen the extension mic invariant
  to every preset. Fix the verified non-ASCII unsubscribe-token 500 with a
  failing test first. Reject the claimed onboarding gap because a direct test
  already covers it. Reject the `allowScripts` finding because current npm and
  the Node 24 publication runtime support that field. Verify all three workflow
  tags and the `withastro/action@v6` inputs through the official repositories
  without running the publication workflow.
- [x] Resolve the targeted follow-up review findings. Reproduce the plus-address
  unsubscribe-link P2 and mixed-case database-update P3 with failing tests,
  then encode the query, normalize the email identity, and use a
  case-insensitive database match. Make the extension source scan recursive
  across both target roots and assert the live controller uses mic-free preset
  keys. The focused extension bundle passed 2 tests, and both final simulator
  bundles passed 190 tests with the expected live SSH skip.
- [x] Resolve the final narrow review findings. Extend the extension safety scan
  to linked `KeyJawnKit` sources and require one audited `setKeys` call. Store a
  real mixed-case legacy row in the regression. Replace the mutating GET with a
  signed confirmation GET and a mutating POST. Share email normalization across
  Stripe ingestion, unsubscribe, download, and support, while preserving access
  to legacy mixed-case rows and preventing a duplicate welcome email.
- [x] Resolve the clean-follow-up findings. Make required secrets fail closed,
  make support and download responses uniform, replace the daily download quota
  with a serialized cooldown, handle an empty releases table, count actual SMTP
  outcomes, enforce case-insensitive email uniqueness with a safe collision
  stop, and couple the expanded extension audio scan to `project.yml`.
- [x] Complete the bounded last remediation pass. Move support and download
  notifications out of the response path, release failed download reservations,
  reject non-ASCII admin tokens, preserve shell secret-command failures, and pin
  the full extension dependency block. Stop broad review expansion after this
  pass.
- [x] Inspect the current App Privacy answers without publishing changes. The
  published answer is `Data Not Collected`, published six months ago. It matches
  the current source because user-started SSH and SFTP go only to the selected
  host, voice uses Apple's Speech framework, and the app links no developer
  analytics, ads, tracking SDK, crash reporter, or KeyJawn data endpoint. Check
  the signed dependency set and Apple's current questions again at submission
  time. No App Store field was edited and Publish was not selected.
- [ ] If a physical iPad is available, verify microphone permission,
  transcription, and rotation without changing App Store state.
- [ ] Obtain explicit authority before creating or changing an isolated
  reviewer account. Then verify password login, key login, voice, shortcuts,
  clipboard, copied-image upload, settings, cleanup, and rotation.
- [x] Update this review record after each completed check.

### Stop conditions

- [ ] Get separate explicit approval before a build-number change, archive,
  upload, metadata edit, reviewer response, submission cancellation, or
  resubmission.
- [ ] Get explicit approval before website publication. A push to `main` that
  changes `website/**` runs `.github/workflows/deploy-site.yml` and publishes the
  site.
- [x] Record the current export-compliance determination. Citadel contains
  standard BoringSSL-backed SSH algorithms, and the optimized Release simulator
  binaries keep them after linking, although KeyJawn's live sessions use the
  SwiftNIO SSH defaults. The
  earlier `usesNonExemptEncryption: false` value is not supported by this
  evidence. Keep the plist key absent until the final Release binary audit and
  App Store Connect questionnaire establish the value and any compliance code.
- [x] Before upload, confirm whether distribution includes France and whether a
  French declaration is required. France is excluded, so no French declaration
  is required for this release. The archive encryption inspection is complete.
  Complete the build-specific App Store Connect questionnaire after upload, and
  keep the separate US BIS classification or reporting duty as a release gate.
- [ ] Do not edit App Privacy answers or select Publish without explicit
  approval at action time.
- [ ] Obtain explicit approval before creating the password-store entry
  `claude/services/keyjawn-unsubscribe-secret`. Provision it before the next
  store service restart or deployment.
- [ ] Get separate explicit approval before a version tag, Play upload, Play
  track promotion, tester change, or production-access application.

## Simplicity standard

Use `/Users/jamditis/Desktop/complexity-and-readability.md` as the required method.

- Preserve correctness and existing public behavior first.
- Match the local style and patterns in each file.
- Read every shipping iOS source file, but refactor only a unit with a clear readability, dead-surface, or runtime problem.
- Treat file length and build size as signals. Do not compute or report a complexity score.
- Judge each function or method by whether its job fits one sentence and its happy path is easy to follow.
- Prefer guard clauses, named intermediate facts, and flat dispatch before extraction.
- Extract only a real concept with a clear name. Do not create `helper` or `util` layers.
- Keep one algorithm together when its steps share state or must be read in order.
- Do not restructure generated, vendored, or unrelated code.
- Use a small diff and a focused test for each iteration before starting the next one.

## Confirmed baseline

- [x] Pull `origin/main`; local and remote are both at `e0aff63`.
- [x] Preserve the existing dirty worktree.
- [x] Read the June 12, 2026 rejection and Apple screenshot.
- [x] Confirm Apple reviewed build 2 on an iPad Air 11-inch (M3).
- [x] Confirm the baseline source was build 8 and contained later changes after
  review build 2. The approved release pass later selected unused build 9.
- [x] Audit production code for local file, process, shell, document picker, runtime loader, SSH, SFTP, clipboard, App Group, and Keychain access.
- [x] Run the baseline iPad unit suite: 167 tests passed and 0 failed.
- [x] Record the baseline release-simulator app, extension, executable, and framework sizes.

The baseline is a universal simulator build, not an App Store download-size estimate:

- App bundle: 117,236 KiB.
- Embedded keyboard extension: 54,348 KiB.
- App executable: 63,817,168 bytes.
- Extension executable: 55,505,024 bytes.
- Asset catalog: 245,368 bytes.
- Swift compatibility library: 138,784 bytes.

## Root cause

Before remediation, the first-party code did not access arbitrary iPhone or iPad
files or execute code locally. The likely rejection cause was an ambiguous
review surface:

- The rejected build showed a `Terminal` tab even when no remote host was configured.
- The app declared a Photos usage description but used no Photos API.
- The app declared a custom URL scheme but had no URL handler.
- The upload UI said `SCP upload` without saying that it read only a copied image
  and wrote only to a remote host.
- The empty Hosts screen did not state that SSH commands run on a remote server.

## Implementation record

### 0. Documentation and social-worker sweep

- [x] Add one active App Store metadata draft with placeholders and no credentials.
- [x] Add a root changelog and release-note templates with explicit distribution state.
- [x] Update the README, social context, contributor guide, review evidence, and worker prompts.
- [x] Label Android-only features and describe the iOS keyboard only where third-party keyboards are supported.
- [x] Mark old App Store and iOS design documents as superseded.
- [x] Run focused worker tests and scan active text for stale App Store claims.
- [x] Update every active website page, the shared FAQ and JSON-LD source,
  privacy copy, public `llms.txt`, store HTML metadata, and the retired web
  prototype notice.
- [x] Run the Astro production build, full store test suite, and focused iOS
  screenshot render test.
- [x] Replace the five stale iOS screenshots with reviewer-safe captures from
  the current build at accepted iPhone and iPad dimensions.
- [x] Remove the unreferenced binary `favicon.ico`. No page references it.
- [x] Record the verified Google Play state: internal and closed testing are
  active; open testing and production are inactive; 7 of 12 required closed
  testers are opted in.
- [x] Confirm the Android source is version 1.4.0, version code 10, and targets
  API 36 before Google's August 31, 2026 update deadline.

### 1. Add failing review-boundary tests

- [x] Add tests that fail while the unused Photos usage key is present.
- [x] Add tests that fail while the unused custom URL scheme is present.
- [x] Add tests that require first-run and empty-host copy to say `remote SSH server` and to deny local device file access.
- [x] Add a structural test that rejects local process, shell, document browser, file provider, security-scoped URL, and runtime-loader APIs in production sources.
- [x] Whitelist only the fixed legacy SSH-key migration file inside the entitled App Group container.
- [x] Update the launch-flow test to require only the intended app tabs.
- [x] Run the focused tests and record the expected failures before production edits.

### 2. Remove misleading and unused review surfaces

- [x] Remove the unused `NSPhotoLibraryUsageDescription` key from `ios/project.yml` and the generated app `Info.plist`.
- [x] Remove the unused `keyjawn` URL scheme from `ios/project.yml` and the generated app `Info.plist`.
- [x] Remove the missing `AccentColor` build-setting reference instead of adding an unused asset only to silence the warning.
- [x] Remove the local-echo Preview tab and the `nil` session branch from `TerminalViewController`.
- [x] Make the terminal controller require an SSH session.
- [x] Change the empty Hosts copy to explain that KeyJawn connects to a remote SSH server and cannot browse files on the iPhone or iPad.
- [x] Update onboarding with the same remote-versus-local boundary.
- [x] Rename `SCP upload` to `Upload copied image` and state that the image comes from the clipboard and goes to a remote host.
- [x] Keep ordinary keyboard input functional without Full Access. Keep network upload and shared settings gated behind Full Access.

### 3. Run an iterative simplicity, dead-surface, and efficiency pass

- [x] Remove `CitadelSCPUploader.UploadError.noHosts` after the final call-site audit showed no use.
- [x] Remove helper entry points that were called only by tests when the shipped event path already had direct behavior coverage.
- [x] Remove stale comments and documentation that described the old Terminal or Preview flow.
- [x] Check every direct package dependency against production imports and call sites.
- [x] Keep SwiftTerm, Citadel, SwiftNIO, SwiftNIOSSH, and KeyJawnKit because production imports and call sites require them.
- [x] Keep the app and extension panel controllers separate because their permissions, output paths, and presentation rules differ.
- [x] Review each changed function for flat control flow, obvious names, one job, and nesting that is easy to follow.
- [x] Review the largest and most frequently changed iOS units by eye and keep cohesive units intact.
- [x] Use guard clauses or flat `switch` dispatch only where they make the happy path easier to read.
- [x] Keep cohesive SSH, keyboard layout, and image preparation algorithms together.
- [x] Remove the Swift 6 `NIOSSHHandler` Sendable warning with an event-loop-safe pipeline operation. Do not add an unchecked conformance.
- [x] Confirm the remaining App Intents metadata messages are Xcode notices; neither target links App Intents.
- [x] Run a second dead-code, duplicate-data, and readability audit after the first cleanup.
- [x] Compare release-simulator sizes and build warnings with the baseline.
- [x] Accept only changes that improve human readability, remove dead surface, reduce runtime work, or remove review ambiguity without weakening behavior or tests.

### 4. Update documentation and review evidence

- [x] Add a short iOS sandbox and remote-execution note for maintainers.
- [x] Update `CLAUDE.md` with the current rejection state, build history, test commands, and release gates.
- [x] Update `README.md` only where the public iOS behavior changes.
- [x] Draft the App Review reply with exact code facts and no unsupported claims.
- [x] State that SSH commands execute on the configured remote host and terminal output returns as network data.
- [x] State that SFTP is upload-only and writes only the copied image to a configured remote path.
- [x] State that the only direct production file read is a fixed legacy key inside `group.com.keyjawn`.
- [x] State why `RequestsOpenAccess` is enabled and what still works without Full Access.
- [x] Prepare exact reviewer test steps and placeholders for an isolated SSH review account. Do not create or transmit credentials without approval.
- [x] Validate App Store field lengths: name 7/30, subtitle 28/30,
  promotional text 162/170, and keywords 71/100 characters.

### 5. Verify the finished change

- [x] Run `xcodegen generate` and review every generated line that changes.
- [x] Run the focused rejection-regression tests.
- [x] Run all unit tests on an iPad Air 11-inch (M3).
- [x] Run all unit tests on an iPhone 17 Pro.
- [x] Run all five UI tests on both devices.
- [x] Install and select the system keyboard in clean iPad simulators, set Full Access explicitly off and on, and type through the extension with no skips.
- [x] Repeat both system-keyboard permission states on iPhone/iOS 26.2.
- [x] Run `xcodebuild analyze` for the app and extension.
- [x] Run `xcrun swift-format lint` on changed Swift files. The new boundary test and the new app-launch code are clean; 15 older changed files report 308 pre-existing style warnings that are outside this focused diff.
- [x] Run `git diff --check` and inspect the full diff line by line.
- [x] Inspect the built `Info.plist`, entitlements, embedded extension version, dependency list, and executable architectures.
- [x] Verify the built app has no Photos purpose key or unused URL scheme.
- [x] Compare before-and-after app, extension, executable, and framework sizes.
- [ ] Manually verify host-key trust, password and key login, remote terminal input and output, voice permissions and transcription, live-session slash shortcuts, clipboard, copied-image upload, settings, and iPad rotation against an isolated SSH host.
- [x] Run automated stress tests for Ctrl, layer changes, slash panel, clipboard panel, upload panel, host configuration, and remote output.
- [x] Run the opt-in live integration test against `houseofjawn`: capture and match its host key, authenticate with KeyJawn's Ed25519 identity, verify remote PTY output and resize, upload through the production SFTP path, verify the exact remote bytes, and remove the temporary file and authorized key.
- [ ] Verify password login, voice permissions and transcription, live-session slash and clipboard UI, copied-image preparation through the full UI, and iPad rotation with the isolated App Review account.
- [x] Read the current App Privacy answers and confirm that `Data Not Collected`
  matches the current voice implementation and Apple's current rules for
  Apple-service processing. Recheck the final signed dependency set at
  submission time.
- [x] Run a final local Claude Code review, then resolve or document every finding.
- [x] For every changed unit, confirm its job fits one sentence and its happy path does not require tracking nested conditions.
- [x] Confirm the final code still looks like the surrounding KeyJawn code and that a smaller diff would not be clearer.

### 6. Verify Android Google Play readiness

- [x] Run both Android unit suites and both lint variants.
- [x] Build the lite release bundle without uploading it. The build used a
  disposable local verification key because the Play upload key is not on this
  Mac. The resulting bundle is not an upload artifact.
- [x] Inspect the release bundle for package `com.keyjawn.lite`, version code 10,
  version name 1.4.0, and target API 36.
- [x] Prepare the internal-to-closed promotion steps in
  `docs/release-style-guide.md`. Do not upload or promote
  without explicit approval.
- [ ] Add at least five closed-test users and keep at least 12 opted in for 14
  days before applying for production access. This is an external release gate.

## Release gates

- [x] Keep both target build numbers unchanged during this remediation.
- [x] Choose an unused build number and update both targets together.
- [x] Obtain explicit user approval before creating an archive.
- [x] Obtain separate explicit user approval before uploading.
- [x] Obtain explicit user approval at action time before a reviewer message,
  App Store Connect metadata change, submission cancellation, or resubmission.
- [ ] Obtain explicit user approval before creating a version tag because the
  current workflow uploads the lite Android bundle to Google Play internal
  testing.
- [ ] Obtain separate explicit user approval before promoting an Android build
  to closed testing or applying for Google Play production access.

## Review record

- Plan status: Approved on August 27, 2026. Code, local automation, and live key-authenticated SSH/SFTP verification are complete. The final reviewer-account UI checks remain.
- Production code changes: Removed the Preview/local-echo path and unused metadata; clarified the remote boundary; isolated UI-test persistence; forwarded terminal emulator replies; removed confirmed dead surface; fixed the Swift 6 SSH pipeline warning; normalized store email identity; made unsubscribe require a confirming POST; made store authentication fail closed; and made public account responses uniform.
- Verification: The final-code iPhone 15 Pro Max/iOS 17.5 and iPad Pro 13-inch
  (M4)/iOS 17.5 runs each executed 190 tests: 189 passed, 1 opt-in live test
  skipped, and 0 failed. The result bundles are
  `/tmp/keyjawn-iphone-units-final-v4-20260828.xcresult` and
  `/tmp/keyjawn-ipad-units-final-v4-20260828.xcresult`. All 5 UI tests passed on both
  simulators, 10 focused boundary tests passed, static analysis passed, and the
  release-simulator build passed. An
  earlier full UI run also passed on iPhone 17 Pro/iOS 26.2 and iPad Air
  11-inch (M3)/iOS 18.5. The UI suite installs and selects the system keyboard,
  checks Full Access off and on, verifies KeyJawn's extra row, and inserts text.
  The final focused Full Access-off run also selected the built-in `/compact`
  shortcut and produced `hi/compact`.
  The post-CI-fix iPhone 15 Pro/iOS 17.5 unit run executed 203 tests: 202
  passed, 1 expected opt-in live SSH test skipped, and 0 failed. The result is
  `/tmp/keyjawn-build9-units-final-clean-index-20260829.xcresult`. The iPad Pro
  13-inch (M5)/iOS 26.5 system-keyboard run executed both Full Access cases:
  2 passed, 0 skipped, and 0 failed. The result is
  `/tmp/keyjawn-build9-ime-ipad-ios26-postfix-20260829.xcresult`.
  The live SSH/SFTP test passed separately against an isolated review host.
- Independent review: Claude Code session
  `96bc6eb9-df4e-450b-99dc-824b7ae780f8` inspected the complete tracked and
  untracked diff in read-only plan mode. It reported 0 P1, 4 P2, and 9 P3
  findings. All four P2 findings were verified and fixed with failing tests
  first. Eight P3 findings produced code, copy, metadata, screenshot, or test
  improvements. One P3 proposal was rejected after code-path tracing because
  SwiftTerm's delegate sends emulator protocol replies, not duplicate user
  input.
- Full-diff independent review: Claude Code session
  `29ccc668-f40d-4d3e-a629-2d1fb7a0332b` reported zero unresolved P1 and zero
  unresolved P2 findings. Its five P3 findings were verified separately. The
  mic-preset invariant and Unicode unsubscribe error were fixed. The onboarding
  gap was already covered by `OnboardingTests`. Current npm and the Node 24
  runtime support `allowScripts`. The three workflow tags exist, and
  `withastro/action@v6` supports the configured inputs.
- Targeted follow-up review: A read-only review confirmed the Unicode token fix
  but found one adjacent P2 and three P3 coverage or identity gaps. The P2 was
  an unencoded plus-address in generated unsubscribe links. The P3 findings
  were the case-sensitive database update, a model-only mic assertion, and an
  incomplete extension source scan. All four were fixed and verified locally.
- Final narrow review: A high-effort read-only pass reported 0 P1, 1 P2, and 4
  P3 findings. The P2 was the omission of linked `KeyJawnKit` sources from the
  extension scan. The P3 findings were an ineffective legacy mixed-case test,
  an incomplete mic-bearing call blocklist, a state-changing unsubscribe GET,
  and inconsistent email identity in download and support. All findings were
  reproduced where applicable, fixed, and covered by focused tests. A clean
  follow-up review remains required before this checkbox can close.
- Clean-follow-up review: The next high-effort read-only pass confirmed that all
  prior findings were closed, then reported 1 P1, 3 P2, and 4 P3 findings in
  adjacent store and extension code. The P1 was the missing production
  unsubscribe secret. The P2 findings were support enumeration, the download
  quota oracle, and empty-token admin access. The P3 findings were empty-release
  handling, false email-delivery counters, schema collation, and incomplete
  audio-source/config coupling. All were reproduced and fixed. Another clean
  follow-up remains required.
- Bounded verification review: The next pass confirmed the earlier findings
  were closed, then reported 0 P1, 1 P2, and 4 P3 findings in the direct paths.
  The P2 was a response-latency purchaser oracle. The P3 findings were Unicode
  admin-token handling, masked shell failures, failed-download cooldowns, and
  dependency-list drift. All five were reproduced where applicable and fixed.
  Per the user-directed stop rule, only one final P1/P2 confirmation follows;
  no new adjacent P3 audit will start.
- Final P1/P2 confirmation: The last bounded, read-only pass verified the exact
  latency, authentication, shell-failure, and download-delivery fixes. It
  reported 0 unresolved P1 and 0 unresolved P2 findings. The review loop stopped
  at this result as directed.
- Current release-candidate confirmation: A clean local clone made from the
  staged 134-file candidate passed all 20 App review boundary tests. The first
  clean-clone run exposed a `/tmp` versus `/private/tmp` path-alias bug in the
  test scanner. The canonical-path fix passed the same clean-clone suite at
  `/tmp/keyjawn-clean-index-boundary-postfix-20260829.xcresult`. The final
  read-only follow-up reported 0 unresolved P1 and 0 unresolved P2. The review
  loop stopped at that result.
- Remaining verification: An isolated SSH review account is required for
  password login and the final reviewer-facing UI pass for voice, shortcuts,
  clipboard, copied-image upload, and rotation. A physical iPad is optional for
  most checks but is useful for the microphone permission and live
  transcription path.
- App Privacy audit: App Store Connect currently shows `Data Not Collected`,
  published six months ago. The current source sends user-started SSH and SFTP
  traffic only to the host selected by the user and uses Apple's Speech
  framework for optional voice input. No developer analytics, ads, tracking
  SDK, crash reporter, or KeyJawn data endpoint is linked. The published answer
  matches that implementation under Apple's current Apple-service processing
  guidance. Recheck the signed dependency set and current questions at
  submission time. No answer was edited and Publish was not selected.
- Export-compliance audit: KeyJawn's live terminal and SFTP sessions use the
  SwiftNIO SSH defaults, not Citadel's optional algorithm additions. Citadel
  0.12.0 still contains BoringSSL-backed AES-128-CTR, Diffie-Hellman group 14,
  and RSA implementations. A code-signing-disabled, optimized Release simulator
  build succeeded, and its app and extension binaries contain those symbols.
  The earlier `usesNonExemptEncryption: false` value is not supported by this
  evidence. No plist value was added. Before upload, inspect the final Release
  device archive, complete the App Store Connect questionnaire, confirm whether
  distribution includes France, obtain the French declaration if required, and
  confirm the separate US BIS classification or reporting duty.
- External changes at the time of this sweep: None.
- Archive or upload at the time of this sweep: None. A later August 31 release
  pass created and verified the signed archive recorded below without uploading
  it.
- Documentation and social-worker sweep: Added the App Store v1.0 metadata
  draft, root changelog, release templates, policy-safe public copy, historical
  banners, and guarded worker prompts. The full worker suite passed 160 tests
  with 12 expected environment-dependent skips in an isolated environment. The
  website production build generated all 10 routes.
- Store verification: 34 tests passed. Every test now uses a temporary SQLite
  database, and the repository database checksum and modification time stayed
  unchanged during the suite. A regression rule also prevents SQLite WAL and
  shared-memory sidecars from entering the repository. A malformed non-ASCII
  unsubscribe token now returns the intended 400 response instead of raising an
  unhandled `TypeError`. Generated unsubscribe links preserve plus-addresses,
  valid mixed-case links update the stored email without case sensitivity, and
  GET requests require confirmation before a POST changes the subscription.
  Download and support find legacy mixed-case purchasers. Stripe normalizes new
  rows and does not duplicate an older mixed-case purchaser.
  Public support and download responses are uniform. Download requests use a
  serialized cooldown and work before a release row exists. Admin credentials
  fail closed, email delivery counters use SMTP results, and the schema rejects
  case-variant duplicates or stops safely on legacy collisions.
  Support and download notifications run after the response. A failed download
  delivery retains its attempt and cooldown record, does not increase the
  dashboard count, and counts toward the uniform five-attempt daily limit.
  Non-ASCII admin tokens are rejected without an exception. The final store
  suite passes 35 tests in an isolated environment.
- Distribution state after the sweep: Apple rejected review build 2 under
  guideline 2.5.2. The August 31 release pass created, verified, and uploaded a
  fresh signed build 9 archive from the corrected source. Build 9 is selected
  for version 1.0, manual release is enabled, and the submission is waiting for
  review. The reviewer response and test steps were included in the review
  notes. The app is not approved and not publicly released.
- Google Play state after the sweep: KeyJawn Lite 1.3.0 is active on the
  internal and closed test tracks. Open testing and production are inactive.
  Google Play app signing is active. The source and local verification bundle
  are version 1.4.0, version code 10, and target API 36. The disposable-key
  bundle cannot be uploaded, and no Android bundle was uploaded or promoted.
- Android verification: 425 full-flavor and 421 lite-flavor unit tests passed.
  Both lint variants completed without errors. Existing lint debt remains: 23
  full warnings and 26 lite warnings, plus baseline entries that no longer
  match current warnings. Keep that cleanup out of this App Store remediation.
- Screenshot verification: Five 1290 x 2796 iPhone JPEGs and five 2064 x 2752
  iPad JPEGs from build 9 have no alpha channel. No release screenshot contains
  credentials, private host aliases, or terminal history. The five stale website
  screenshots and the unreferenced binary favicon are out of the public source
  tree. The Full Access onboarding image was recaptured on both device classes
  after the user-created shortcut disclosure changed. A native-resolution pixel
  audit confirmed that the top-center iPad mark is the normal iPadOS multitasking
  ellipsis, not a duplicated or ghosted app control.
- Final CI keyboard-path verification: A new boundary test first failed because
  `KeyboardIMETests` could call `XCTSkip` when Settings navigation failed. The
  suite now supports the iPad Settings sidebar query and fails on any unsupported
  navigation state. The regression test passes, and both real iPad system-keyboard
  cases pass with no skip in
  `/tmp/keyjawn-ci-ipad-ime-postfix-20260829.xcresult`.
- Final App Store submission verification: The live read-back reports version
  1.0 and review submission `83b2805c-650c-4bf6-91ff-0c6339f36324` as
  `WAITING_FOR_REVIEW`. Build 9 is selected and `VALID`, release type is
  `MANUAL`, and `usesNonExemptEncryption=false` remains saved. The protected
  reviewer username and password fields are present. The reviewer service and
  expiry timer are active. The final worker suite passed 20 tests, the focused
  Swift suite passed 27 tests, Xcode's Swift formatter passed, and
  `git diff --check HEAD` passed.
- Final release-state review: A bounded read-only Claude CLI pass produced no
  result before its review window ended, so it was stopped and not counted as a
  pass. The required line review found and corrected three stale documentation
  statements. No second review loop was started. The earlier full-diff review
  gate remains 0 unresolved P1 and 0 unresolved P2 findings.

## August 29 transport and release follow-up

- [x] Move iOS SSH and SFTP connections to SwiftNIO Transport Services so they
  use Network.framework on physical iOS.
- [x] Keep plain SSH as the default and add an optional TLS-terminated tunnel
  setting with system certificate validation and separate SSH host-key checks.
- [x] Prevent the first-use host-key probe from offering a password or private
  key before the user accepts the fingerprint.
- [x] Bound connection, SSH setup, PTY setup, and copied-image upload work.
- [x] Make deadline completion atomic so a timed-out channel cannot return a
  successful connection, PTY, or upload path.
- [x] Close pending and active NIO channels when the Swift task is canceled.
- [x] Retain copied-image upload tasks in both UI surfaces, cancel them when the
  panel closes, and suppress late path insertion.
- [x] Map connection, authentication, SSH setup, SFTP setup, and timeout errors
  to short repair messages without storing raw endpoint or path text in logs.
- [x] Show a host-key verification instruction when an SSH-key host is not yet
  pinned. Do not offer plain `ssh-keyscan` for a TLS relay.
- [x] Add `KEYJAWN_LIVE_SSH_TLS=1` to the opt-in live SSH and SFTP test path.
- [x] Require the live-test TLS choice to be explicit and accept only `0` or
  `1`.
- [x] Disable every visible copied-image upload dismissal path after the remote
  write starts, including the in-app extra-row upload key. Restore dismissal
  after a visible failure.
- [x] Make the live upload pass marker require matching payload text, the exact
  byte count, successful remote deletion, and confirmed file absence. Build the
  result marker from separate shell arguments so PTY echo cannot satisfy it.
- [x] Run the final iPad unit suite. Result:
  `/tmp/keyjawn-niots-full-units-final9-20260829.xcresult`; 233 total, 232
  passed, one expected opt-in live test skipped, zero failed.
- [x] Run the final iPad UI suite by itself. Result:
  `/tmp/keyjawn-niots-full-ui-final9-20260829.xcresult`; five passed, zero
  skipped, zero failed.
- [x] Run static analysis for the app and keyboard extension with isolated
  derived data at `/tmp/keyjawn-niots-final9-analyze-derived`; zero errors.
- [x] Run strict Swift format checks and `git diff --check` on the final changes.
- [x] Run the external Claude transport review. Fix its PTY-echo P1 and
  extra-row-dismiss P2 with failing regression tests before the implementation
  changes.
- [x] Run the final read-only transport follow-up on the corrected exact tree.
  Result: P1: 0, P2: 0, with 35 focused tests passed and zero failed.
- [x] Build the corrected exact tree for the connected iPad and install it over
  the existing app data from `/tmp/keyjawn-physical-final9-derived`.
- [x] Record the release owner's August 31, 2026 decision to use the prior
  physical-iPad evidence and complete the remaining UI checks in the iPad
  simulator. This is an explicit hardware-test waiver, not a new physical test
  pass. The current device-dependent public TLS, password and key login, voice,
  clipboard, copied-image upload, settings, rotation, and AdGuard restoration
  flow remain unverified on build 9 hardware. Record simulator evidence and
  this residual risk with the release.
- [x] Regenerate `ios/KeyJawn.xcodeproj` from `ios/project.yml`; the project and
  shared scheme bytes did not change. Run the protected screenshot-render test
  with `CI=1`; one test passed and the tracked screenshot was not replaced.
- [x] Run the full build 9 scheme on the iOS 26.5 iPhone 17 Pro simulator.
  Result bundle: `/tmp/keyjawn-release-build9-iphone-20260831-0947.xcresult`.
  Result: 237 passed, one expected live-network skip, zero failed.
- [x] Run the full build 9 scheme on the iOS 26.5 iPad A16 simulator. Result
  bundle: `/tmp/keyjawn-release-build9-ipad-20260831-0952.xcresult`. Result:
  237 passed, one expected live-network skip, zero failed. The system-keyboard
  UI pass covered Full Access off and on, portrait assistant-control geometry,
  `hi` input, downward `Q` flick to `1`, ordinary `q`, and `/compact`.
- [x] Inspect the retained portrait keyboard attachment from the iPad result.
  The iPadOS undo, redo, and paste group does not overlap KeyJawn's `^C` and
  `Tab` controls; key spacing is even and secondary symbols are visible. The
  temporary audit is `/tmp/keyjawn-ipad-audit.QXQA3r`. No tracked image changed.
- [x] Bind the pre-archive source to base commit `e0aff639ce8a251aa3e2fff27e4be21ab197b6c6`
  and iOS source digest
  `2e40bfe2d95404bd0ba4fb3cba8c9224ab5720c019309fb4fa7fd69387f966ef`.
- [x] Confirm France distribution from App Store Connect availability and the
  export-compliance scope from the signed archive. On August 31, 2026, create
  app availability for all 175 current territories with France (`FRA`)
  unavailable, the other 174 territories available, pre-order disabled, no
  release date, and automatic availability in future territories disabled. A
  full API read-back matched all 175 current territory records with no missing
  or duplicate record. The signed archive uses standard encryption outside the
  Apple operating system, so the per-build encryption questionnaire remains an
  explicit post-upload gate. France is excluded, so this release does not need
  French encryption documentation.
- [x] Run a read-only App Store Connect audit on August 31, 2026. At the time of
  this audit, version 1.0
  remains in `PREPARE_FOR_SUBMISSION`, build 8 remains selected, and the review
  submission remains in `UNRESOLVED_ISSUES`. Apple rejected review build 2;
  build 8 is the latest uploaded and selected build; build 9 is the current
  local candidate. The app has an `appAvailabilityV2` relationship, but its
  related record returned `NOT_FOUND`. Country availability was not set up, and
  France was not confirmed as included or excluded. No App Store Connect field
  changed during that audit. The later verified availability write below
  supersedes this earlier state.
- [x] After the iPhone and iPad simulator gate passed, freeze the candidate and
  create the signed archive. Inspect signing, embedded content, and linked
  cryptography before upload. The later correction cycle supersedes this
  archive and requires a fresh archive.
- [x] Create the signed build 9 archive with `bash scripts/build.sh
  --archive-only`. The script regenerated an unchanged project, created
  `/tmp/KeyJawn.xcarchive`, exported `/tmp/KeyJawn-export/KeyJawn.ipa`, and did
  not upload it. The IPA is 15,375,705 bytes with SHA-256
  `64af1b56ae886347492f4d012b3ddb2644115db06386da239ea3adeaff8a332d`.
- [x] Verify the exact IPA. Its ZIP is valid, it contains 37 nonempty app files,
  and deep strict code-sign verification passes. The app and extension are
  arm64, version 1.0.0, build 9, and device families 1 and 2. Both use the
  distribution identity, shared App Group, and shared Keychain group with
  `get-task-allow` false. The embedded App Store profile UUIDs are
  `4a26762c-af50-46f8-9929-089e61888e67` for the app and
  `58d4ab34-8d41-4695-8ee3-8026065666be` for the keyboard extension.
- [x] Inspect linked cryptography in the signed bytes. Both binaries link
  CryptoKit and Network.framework. The keyboard extension also contains
  BoringSSL AES, RSA, elliptic-curve, and AEAD symbols, and the IPA embeds the
  matching Swift Crypto privacy manifests. Build 9 therefore uses industry
  standard encryption outside the Apple operating system. Both final plists
  omit `ITSAppUsesNonExemptEncryption`, so the per-build questionnaire remains
  required. If France is selected, Apple's matrix requires a French encryption
  declaration before review submission.
- [x] Freeze the August 31 large-diff review at base commit
  `e0aff639ce8a251aa3e2fff27e4be21ab197b6c6` before corrections. The review
  covered 135 tracked files with 8,497 additions and 4,578 deletions, 16 binary
  files, 21 additional unstaged changes, and 39 untracked files. The frozen
  binary diff SHA-256 was
  `9c04d85889ce0402d82613682353408d65c87fff05d492077451c5877444e51b`.
- [x] Complete three independent reviews of the frozen candidate. The iOS code
  review took 11 minutes 35 seconds, the service and website review took 15
  minutes 18 seconds, and the visual review took 18 minutes 2 seconds. The
  reviews found missing first-party privacy manifests, an implicit extension
  device family, stale release records and screenshots, unpinned deployment
  actions, generated worker files, and keyboard accessibility and width issues.
- [x] Define one bounded correction set for all P1 and P2 findings plus the
  low-risk iPad wording fix. Keep icon redesign, floating-keyboard layout work,
  and upload announcements out of this release correction cycle.
- [x] Finish the bounded corrections, review every correction line, and pass the
  focused and full test suites. The focused iOS suite passed 53 tests and the
  focused worker suite passed 20 tests. The isolated full store suite passed 35
  tests. The isolated full worker suite passed 162 tests with 12 expected
  environment skips. On both iPhone 17 Pro and iPad (A16) simulators, all 240
  unit tests passed with one expected live-network skip and all 5 UI tests
  passed. Static analysis passed. Strict Swift format checks passed for the
  correction-specific Swift files. `git diff --check HEAD` passed. Regenerating
  the Xcode project produced identical project and shared-scheme hashes. The
  full-tree Swift format scan also exposed older formatting debt outside this
  bounded release correction; do not expand this cycle into a bulk format
  change.
- [x] Obtain an independent post-correction review with its exact input, scope,
  start time, end time, elapsed time, and P0 through P3 findings. The first
  Claude attempt ran from `2026-08-31T15:19:47Z` to `15:28:18Z` but could inspect
  only 5 of 42 changed files because its shell tools were denied. A single
  continuation ran from `15:31:41Z` to `15:36:15Z`; it inspected both changed
  screenshots and all 3 untracked files, but its sandbox denied the exact
  2,774-line diff artifact, so it inspected 0 of 42 diff sections. Both attempts
  correctly withheld a pass. Their initial P1/P2 claims were reconciled against
  passing compiler evidence, the intentional export-compliance gate, the
  verified TLS review endpoint, and Citadel 0.11.1 source. No claim remained.
  Copilot CLI was not installed, so no replacement reviewer was started. After
  explicit approval, one final Claude pass received narrow read-only Git access.
  It ran from `2026-08-31T16:01:11Z` to `16:12:00Z`, 10 minutes 49 seconds. It
  verified HEAD `e0aff639ce8a251aa3e2fff27e4be21ab197b6c6`, all 42 unstaged diff
  sections, both changed screenshots, all 3 untracked files, and binary diff
  SHA-256
  `eff9894feaa6fe4f6f265686ed0c384ed9fe735fbe49e42697a18ec6e6a5ca17`.
  It reported P0=0, P1=0, P2=0, and P3=6 and issued `release review gate:
  pass`. The P3 items cover test precision, a currently unreachable upload-panel
  dismissal path, the intentional 30-second upload deadline, a dead timeout
  presentation parameter, an implicit test-only NIO dependency, and lessons-file
  ordering. Record them for later; do not reopen this release review cycle.
- [x] Create and inspect a fresh signed build 9 archive from the corrected exact
  source. The archive was created on August 31, 2026 with
  `bash scripts/build.sh --archive-only`; no upload ran. The exported IPA is
  15,380,011 bytes with SHA-256
  `5fd0fb8735bbc96d34e97a0e958f6e3cc3259b45c63529eb1cd790f9be1edbb4`.
  Both bundles are version `1.0.0`, build `9`, minimum iOS `17.0`, device
  families `1,2`, and arm64. Strict signature validation passed. Both profiles
  have `get-task-allow=false`, match their bundle identifiers, and expire on
  February 18, 2027. Both final bundle roots contain the exact first-party
  privacy manifest with SHA-256
  `f2f3eaaf431c51d10a8256f56bc136c4e94653a2cfaa9d9871ea45ab5b910ccd`
  and reasons `CA92.1` and `1C8F.1`. The embedded Swift NIO manifest covers the
  linked file-timestamp APIs with reason `0A2A.1`. IPA ZIP validation passed,
  all 39 app files are nonempty, and both dSYM UUIDs match. The earlier IPA is
  retained only in `/tmp/keyjawn-superseded-archive.SlPjRl` and must not be
  uploaded.
- [x] Select and confirm App Store country availability. App Store Connect
  accepted availability record `6759345867` on August 31, 2026. Read-back
  confirmed France unavailable, all other 174 current territories available,
  no pre-order, no release date, and no automatic future-territory selection.
  France is excluded, so no French encryption declaration is required for this
  release.
- [x] Upload the inspected archive. Apple accepted the exact 15,380,011-byte IPA
  with SHA-256
  `5fd0fb8735bbc96d34e97a0e958f6e3cc3259b45c63529eb1cd790f9be1edbb4`
  under delivery ID `a90c6924-1b39-4504-891c-27425d354d25` with no upload
  errors. Apple processed build 9 as `VALID`. Set
  `usesNonExemptEncryption=false` after final archive inspection, France
  exclusion, and Apple documentation review; API read-back passed. Build 9 is
  selected for version 1.0 and waiting for review.
- [x] With action-time approval, set version 1.0 to manual release, select build
  9, validate and save the isolated reviewer account in the protected review
  fields, include the guideline response and test steps in the review notes,
  resolve the rejected item, and resubmit review submission
  `83b2805c-650c-4bf6-91ff-0c6339f36324`. Apple accepted the resubmission on
  August 31, 2026 and read-back reports `WAITING_FOR_REVIEW`. The old submission
  was reused; no cancellation or replacement submission was required. The app
  is not approved and not publicly released.
- [x] Apple approved version 1.0 on September 17, 2026. App Store Connect
  read-back reports `PENDING_DEVELOPER_RELEASE`, and manual release remains
  enabled. Apple's public lookup returned no listing at the time of this update.
- [ ] With action-time approval, release version 1.0 in App Store Connect. Then
  verify the public listing before publishing an App Store URL or availability
  claim.
