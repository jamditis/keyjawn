# App review remediation pause handoff

> **Status:** Safe pause on August 27, 2026, at the end of the local iPhone
> verification run. Read this file and [`../../tasks/todo.md`](../../tasks/todo.md)
> before changing the working tree. This handoff is not approval to archive,
> upload, edit store metadata, reply to App Review, or resubmit.

## Repository state

- Repository: `/Users/jamditis/Desktop/Crimes/playground/keyjawn`
- Branch: `main`
- Checked-out commit: `e0aff639ce8a251aa3e2fff27e4be21ab197b6c6`
- The latest GitHub state was pulled before this remediation began.
- The working tree contains the full uncommitted remediation. It has 78 tracked
  files with changes plus new files. Do not reset, clean, stash, regenerate, or
  pull before reviewing `git status`, this handoff, and `tasks/todo.md`.
- `git diff --check` passed at the pause.
- No Xcode test process remained active at the pause.

No external release mutation occurred. The session did not change a build
number, create an archive, upload a build, edit App Store Connect metadata,
send a reviewer response, cancel or resubmit a submission, create a tag, upload
to Google Play, or promote a Play track.

## Current Apple state

- App Store Connect app ID: `6759345867`.
- Review submission ID: `83b2805c-650c-4bf6-91ff-0c6339f36324`.
- Apple rejected review build 2 under guideline 2.5.2 on June 12, 2026.
- At this handoff, both checked-in iOS targets remained at source build 8. The
  later approved release pass selected unused source build 9 for both targets.
- The remediation removes the session-free local Preview path and documents the
  real boundary: KeyJawn sends terminal input to a configured remote SSH host;
  the remote host executes commands and returns output.
- The custom keyboard works outside the KeyJawn app in apps and text fields that
  permit third-party keyboards. It inserts text or control sequences. The
  receiving app decides how to interpret them. iOS blocks custom keyboards in
  secure and some phone-pad fields, and an app can block them.

## Finished local work

- Removed the Preview/local-echo path, unused Photos key, unused URL scheme,
  unused accent reference, and confirmed dead code.
- Kept local device storage inside the app, extension, App Group, and Keychain
  boundary. The only direct production file read is fixed legacy SSH-key
  migration inside the entitled App Group.
- Restricted copied-image upload to SSH-key hosts at the model, UI, and uploader
  layers. Password authentication remains available for terminal connections.
- Kept Full Access optional for basic typing and built-in shortcuts. Shared
  settings, user-created shortcuts, clipboard history, and copied-image upload
  use Full Access.
- Set `IsASCIICapable` to true for the English QWERTY extension.
- Forwarded terminal emulator replies through the live remote SSH session.
- Isolated UI-test persistence and isolated every store test from the repository
  SQLite database.
- Added the ignored SQLite sidecar pattern `store/keyjawn-store.db-*`.
- Ran a live key-authenticated SSH, host-trust, PTY, resize, and SFTP test against
  `houseofjawn`. The uploaded bytes matched, and the temporary remote file and
  marked authorized key were removed.
- Updated README, changelog, social source, maintainer docs, App Store draft,
  website, privacy text, support text, Play release guide, and screenshot docs.
- Created five current App Store JPEGs for iPhone and five for iPad. All have
  accepted dimensions, no alpha channel, no credentials, no private hosts, and
  no third-party branding.
- Updated the website to Astro 7.2.9 and Node 22.19 or later. The site builds 10
  pages, has an SVG favicon and Open Graph metadata, and `npm audit` reports zero
  vulnerabilities.
- Verified Android API 36, version code 10, version name 1.4.0, and package
  `com.keyjawn.lite`. The full Android suite passed 425 tests; lite passed 421.
  Both lint variants reported zero errors.

## Final review work completed tonight

The full local Claude review session was
`523c158c-8114-4509-b041-5bf85ed8c0c7`. It finished after 52 read-only review
turns and found no P1. It found two P2 documentation gaps:

1. The App Review evidence and metadata did not disclose the built-in terminal's
   optional microphone and Speech framework path.
2. The copy said all text shortcuts worked without Full Access, but user-created
   shortcuts use the shared App Group and need Full Access in the extension.

Both P2 findings are fixed. The App Review draft now explains voice permissions,
Apple Speech processing, the no-recording boundary, and an exact reviewer Mic
test. It now says built-in shortcuts work without Full Access and user-created
shortcuts need it. `AppReviewBoundaryTests` protects both disclosures.

Claude also found two P3 items. The stale comment that said Esc replaced Mic is
fixed. A failing test proved that plain `é` became a lossy byte and Ctrl+`é`
became an unrelated control byte. `ANSISequence` now emits plain text as UTF-8
and applies Ctrl masking only to one ASCII scalar.

## Verification at the pause

- Focused final run: 21 tests passed. This includes 11 ANSI sequence tests and
  10 App Review boundary tests.
- Final iPhone 15 Pro Max/iOS 17.5 unit run: 184 executed, 183 passed, 1 expected
  live SSH test skipped, 0 failed.
- iPhone result bundle:
  `/tmp/keyjawn-iphone-units-20260827-2233.xcresult`
- The matching iPad run did not start tests. It was launched concurrently and
  Xcode stopped it because both processes used the same shared build database.
  This was an orchestration failure, not a product test failure.
- Failed iPad result bundle:
  `/tmp/keyjawn-ipad-units-20260827-2233.xcresult`
- Before the last two tests were added, the iPad Pro 13-inch (M4)/iOS 17.5 suite
  passed 181 tests with 1 expected live skip. The final-code iPad rerun remains
  required.
- All five UI tests passed earlier on the iPhone 15 Pro Max/iOS 17.5 and iPad Pro
  13-inch (M4)/iOS 17.5. The last changes did not alter those UI flows.
- Store tests: 16 passed.
- Worker tests: 160 passed and 12 expected skips.
- App Store field lengths: name 7/30, subtitle 28/30, promotional text 162/170,
  description 2,071/4,000, and keywords 71/100.
- `git diff --check` passes. The stale no-Full-Access and Mic-comment claims are
  absent.
- Strict Swift formatting still reports older alignment style in touched files.
  The new guard follows formatter output. Do not apply a broad formatter rewrite
  as part of the App Review fix without reviewing that separate diff.

## Important local database disclosure

An early store test run used the ignored local database before test isolation
was fixed. It modified `store/keyjawn-store.db` at about 21:31 on August 27 and
left test rows. No trusted baseline exists, so the file was not deleted,
restored, or changed afterward. The source cause is fixed: collection-time and
per-test database paths now use temporary databases, and the full store suite
passes without changing the repository database.

Recorded database state after the fix:

```text
sha256: bc4a1424830eee91980d2c9ad6b55390ca5c4d381bfff1bcfe65263871109a8b
mtime: 1787880715
size: 28672 bytes
```

Treat the current ignored database as user state with known test contamination.
Do not delete or replace it without the user's direction.

## First steps tomorrow

1. Read this file and `tasks/todo.md`. Run `git status --short` and
   `git diff --check`. Do not clean the working tree.
2. Use `git fetch origin` only as a read-only freshness check. Compare the dirty
   checkout with `origin/main` before deciding whether any integration is safe.
3. Rerun the final iPad unit suite sequentially with a separate derived-data
   directory:

   ```bash
   cd /Users/jamditis/Desktop/Crimes/playground/keyjawn/ios
   xcodebuild test \
     -project KeyJawn.xcodeproj \
     -scheme KeyJawn \
     -destination 'platform=iOS Simulator,id=2C2000E7-5878-41DF-9D73-60DB7C4822FD' \
     -derivedDataPath /tmp/keyjawn-ipad-derived-20260828 \
     -resultBundlePath /tmp/keyjawn-ipad-units-20260828.xcresult \
     -only-testing:KeyJawnKitTests
   ```

   Expected result: 184 executed, 183 passed, 1 expected live skip, 0 failed.
4. Run a fresh read-only Claude review of the final diff. Ask it to confirm that
   the two P2 findings are closed and that no P1 or P2 remains. Do not mark the
   final Claude checkbox until this follow-up finishes.
5. Inspect the current App Privacy answers in App Store Connect without
   publishing changes. Compare them with Apple's current App Privacy guidance
   and the signed voice implementation.
6. Use the user's physical iPad if available for the Mic permission,
   transcription, and rotation flow. The simulator is enough for the system
   keyboard and Full Access checks.
7. Obtain explicit authority before creating or changing an isolated reviewer
   account on `houseofjawn`. Then dry-run password login, fresh public-key
   authorization, key login, voice, shortcuts, clipboard, copied-image upload,
   cleanup, and rotation.
8. If the iPad run and follow-up review pass, update `tasks/todo.md`. Stop again
   before the build number, archive, upload, reviewer response, metadata edit,
   or resubmission gates.

## Remaining external gates

- Apple: verify current App Privacy answers.
- Apple: record a current export-compliance determination. Verify
  `ITSAppUsesNonExemptEncryption` and any required documentation before upload.
- Apple: obtain explicit approval before selecting Publish for App Privacy
  answers.
- Apple: complete the isolated reviewer-account UI flow.
- Apple: choose an unused build number only with approval and update both targets
  together.
- Apple: obtain separate approval for archive, upload, metadata changes,
  reviewer response, submission cancellation, and resubmission.
- Website publication: a push to `main` that changes `website/**` runs
  `.github/workflows/deploy-site.yml` and publishes the site. Obtain explicit
  approval before that push.
- Google Play: add at least five more opted-in closed testers and keep at least
  12 opted in for 14 days before applying for production access.
- Google Play: obtain approval before any tag, upload, track promotion, or
  production-access application.

## Recoverable temporary material

- Stale public assets:
  `/tmp/keyjawn-stale-public-assets-20260827.H9zFl5`
- Non-uploadable Android verification artifacts:
  `/tmp/keyjawn-nonuploadable-android-artifacts.uwMniA`
- Python build debris:
  `/tmp/keyjawn-python-build-debris.80TXzI`
- Incomplete worker virtual environment:
  `/tmp/keyjawn-worker-incomplete-venv-20260827`
- Other verification artifacts:
  `/tmp/keyjawn-verification-artifacts-20260827-2207`

Do not treat any temporary Android bundle or key as a Play upload artifact.
