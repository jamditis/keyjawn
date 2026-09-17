# Release notes style guide

Use these templates only after the listed state is verified. A source build, an
archive, an upload, and a store release are different states.

## Required status block

Add this block to each release plan or handoff:

```markdown
## Distribution state

- Android GitHub release: [not started, uploaded, or verified at URL]
- Android website delivery: [not started, uploaded, or verified]
- Google Play: [not started, testing track, submitted, or available]
- iOS source build: [build number]
- iOS archive: [not created or verified archive path and time]
- TestFlight upload: [not uploaded, processing, or verified available]
- App Store submission: [not submitted, in review, rejected with guideline, approved pending developer release, or available]
- External actions approved by: [name and exact approved actions]
```

Do not use “released,” “uploaded,” “submitted,” “processing,” “approved,” or
“available” until a read-back from the applicable service confirms that state.

## Android GitHub release template

```markdown
![KeyJawn branding graphic](https://i.imgur.com/c6z2Gl0.jpeg)

[![Build](https://github.com/jamditis/keyjawn/actions/workflows/build.yml/badge.svg)](https://github.com/jamditis/keyjawn/actions/workflows/build.yml)

## What changed

### [Android feature or fix]

[State what changed, who it helps, and any limit.]

## Verification

- `./gradlew testFullDebugUnitTest`: [result]
- `./gradlew testLiteDebugUnitTest`: [result]
- Release artifact check: [result]

## Downloads

| File | Description |
|------|-------------|
| `app-lite-release.apk` | Free Android version from GitHub |
| Android full version | Available through [keyjawn.amditis.tech](https://keyjawn.amditis.tech/pricing) after the website artifact is verified |

Full changelog: https://github.com/jamditis/keyjawn/compare/v[previous]...v[current]
```

## TestFlight notes template

```text
[State the iOS change in one sentence.]

Test:
- Remote SSH connection and terminal input/output.
- [Specific changed flow.]
- Companion keyboard in a text field that supports third-party keyboards.

Limits:
- Commands execute on the configured remote server, not on iOS.
- The system keyboard appears in secure and phone-pad fields, and apps can block third-party keyboards.
- [Other limit that applies to this build.]
```

## App Store version notes template

```text
This update [states the user-visible change].

- [Change one.]
- [Change two.]
- [Fix or limit, if needed.]
```

Do not put reviewer arguments, test credentials, internal build state, or
unverified future features in public version notes. Use
[`app-store-v1.0-metadata.md`](app-store-v1.0-metadata.md) for the App Review
draft.

## App Review handoff template

```markdown
## App Review state

- Guideline: [guideline]
- Reviewed build: [build number]
- Source build: [build number]
- Archive: [not created or verified path]
- Upload: [not uploaded or verified App Store Connect state]
- Selected submission build: [none or build number]
- Reviewer response: [draft only or sent with verified time]
- Metadata changes: [none or exact verified fields]
- Resubmission: [not done or verified state]
- Remaining checks: [list]
- Required approvals: [list each external action separately]
```

## Current iOS release state

- Source build 9 exists.
- A fresh signed build 9 archive was created and verified from the corrected
  source on August 31, 2026. The exact archive was uploaded, and Apple processed
  build 9 as valid. Build 9 is selected for version 1.0, manual release is
  enabled, and Apple approved the version on September 17, 2026. App Store
  Connect reports pending developer release. The app is not publicly available
  until the developer release occurs and Apple publishes the listing.

## Current Android distribution state

- KeyJawn Lite 1.3.0 is active on the internal and closed Google Play test
  tracks.
- Open testing and production are inactive.
- Google Play app signing is active.
- 7 of the required 12 closed-test users are opted in. Google also requires at
  least 14 days with 12 opted-in testers before production access.
- Source version 1.4.0, version code 10, targets API 36. A local bundle made
  with a disposable verification key confirmed this manifest. It is not an
  upload artifact. No Android bundle was uploaded or promoted in this
  remediation.
- Creating a version tag triggers an internal-track upload. Get explicit user
  approval before creating a tag or promoting a build to another Play track.

### Internal-to-closed promotion procedure

1. Confirm the selected internal artifact, version code, version name, and
   release notes in Play Console.
2. If a new artifact is needed, get explicit approval before an upload or
   version tag.
3. Get separate explicit approval to promote the selected release to the
   existing closed alpha track.
4. After promotion, read back the selected release, track state, and tester
   list from Play Console.
5. Keep at least 12 testers opted in continuously for 14 days.
6. Get separate approval before applying for production access.

## Writing rules

- Use sentence case.
- State the platform for each platform-specific feature.
- Use plain language and one or two short paragraphs per change.
- Describe remote SSH execution as remote. Do not imply local iOS command
  execution or device-file access.
- For the iOS keyboard, say “where third-party keyboards are supported.” Do not
  say “any app,” “every app,” or “system-wide.”
- Label Android-only voice input, SCP upload, themes, and gesture features.
- Do not use emojis, hype, or future distribution claims.
- Format a GitHub release title as `vX.Y.Z - Short sentence-case description`.
