# Two-milestone release handoff

Last updated from landofjawn on 2026-07-25.

No pull request has been merged and no release has been built or deployed. Joe
has not given explicit approval for any pull request listed here. Approval must
name the specific pull request; this handoff is not merge approval.

## Release scope

Milestone 1 covers reliability and security:

- #28: replace the Android QWERTY grid with one custom view
- #54: phase-one keyboard haptics
- #64: worker subprocess cleanup
- #67: iOS SSH host-key trust
- #68: shared iOS Keychain identity

Milestone 2 covers interaction foundations:

- #51: per-app Send slot
- #52: keyboard-wide touch trace
- #53: offline voice recognition
- #55: macro model and store

Keep roadmap parents #23 through #27 open as umbrellas. Update their progress
when child work ships.

## Published pull requests

| Issue | Pull request | Branch | State and remaining gate |
|---|---|---|---|
| #54 | [#69](https://github.com/jamditis/keyjawn/pull/69) | `fix/54-haptics-phase-1` | Review fix pushed and its thread resolved. Recheck CI, then obtain Joe's approval. |
| #64 | [#70](https://github.com/jamditis/keyjawn/pull/70) | `fix/64-worker-subprocess-cleanup` | Worker suite and Ruff pass. Recheck CI and review-thread resolution, then obtain Joe's approval. |
| #67 | [#71](https://github.com/jamditis/keyjawn/pull/71) | `fix/67-ios-tofu` | Run Xcode tests and a signed build on macOS, verify trust flows, then obtain Joe's approval. |
| #68 | [#72](https://github.com/jamditis/keyjawn/pull/72) | `fix/68-shared-keychain` | Enable the shared Keychain capability, regenerate profiles, verify signed entitlements and an upgrade, then obtain Joe's approval. |
| #28 | [#73](https://github.com/jamditis/keyjawn/pull/73) | `fix/28-qwerty-custom-view` | Run the instrumentation touch-path test on a device or emulator, then obtain Joe's approval. |
| #55 | [#74](https://github.com/jamditis/keyjawn/pull/74) | `fix/55-macro-store` | Obtain Joe's approval. |
| #51 | [#75](https://github.com/jamditis/keyjawn/pull/75) | `fix/51-send-slot` | Hand-test one terminal or SSH app and one chat app, then obtain Joe's approval. |
| #53 | [#76](https://github.com/jamditis/keyjawn/pull/76) | `fix/53-offline-voice` | Hand-test API 33 or later with installed and missing offline models, then obtain Joe's approval. |
| #52 | [#77](https://github.com/jamditis/keyjawn/pull/77) | `fix/52-glide-trace` | Stacked on #73. Hand-test trace and existing gestures, merge #73 first, retarget to `main`, run CI, then obtain Joe's approval. |

PRs #71 through #77 are drafts. PRs #69 and #70 are open for review. Do not
mark a draft ready until its listed platform gate passes.

## PR #70 verification note

The final review cycle addressed six inline findings plus three local
high-depth findings:

- preserve output when supervisor status and communication complete together
- survive repeated cancellation during spawn and cleanup
- bound root-status reader shutdown
- kill and reap timed-out Windows `taskkill` helpers
- terminate descendants when supervisor status fails
- restore `SIGPIPE` and `SIGXFSZ` before Linux `exec`
- stop a shared status reader without failing shutdown or active waiters
- use `poll()` for descriptors above `FD_SETSIZE`
- avoid importing the Linux supervisor during Windows test collection

Verification after the final fixes:

```text
worker tests: 170 passed, 215 warnings
Ruff: all checks passed
```

Four local review passes were used, which is the configured cap. The fourth
pass found the final three items above; they were fixed test-first and the full
suite was rerun. No fifth review pass was started.

## External and hardware gates

- `adb devices -l` found no connected Android device on landofjawn.
- The Android SDK used in this session is
  `/home/jamditis/.cache/keyjawn-android-sdk`.
- This Linux host has no Xcode or Swift toolchain for the iOS builds.
- The available App Store Connect key returned HTTP 403 for provisioning
  resources. No Apple capability or profile mutation was attempted.
- PR #77 has no pull-request workflow run while its base is
  `fix/28-qwerty-custom-view`; the workflow watches `main`. Its full local
  Android unit, lint, APK, and instrumentation-APK build matrix passed.

## Restart checklist

1. Fetch the nine PR heads and recheck current CI, draft state, approvals, and
   unresolved review conversations. Leave any unverified conversation open.
2. Complete the Android device gates for #73, #75, #76, and #77.
3. Complete the macOS signing, test, entitlement, migration, and trust gates
   for #71 and #72.
4. Ask Joe for explicit approval of each specific PR only after its gates pass.
5. Merge approved milestone 1 PRs. Merge #73 before retargeting #77 to `main`.
6. After all milestone 1 changes are merged, update versions, release notes,
   and `SOCIAL.md`; build and sign both Android flavors; publish lite to the
   GitHub release and Google Play internal track; publish full through R2 and
   the store flow; notify purchasers only after download verification; upload
   applicable iOS changes to TestFlight after signed-build verification.
7. Verify production health and retain the prior artifacts and deployment as
   rollback points.
8. Repeat the release process after all milestone 2 changes are merged, then
   update roadmap-parent progress.

## Local recovery note

`stash@{0}` is an intentionally preserved, rejected Option B experiment for
#28 from branch `fix/28-qwerty-grid-reuse`. Do not pop or delete it as part of
normal release work.
