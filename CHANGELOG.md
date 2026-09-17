# Changelog

This file records user-visible changes and release state. A source change is not
a distributed release.

## Unreleased

### iOS

- Clarified that terminal commands execute on a user-configured remote SSH
  server, not on the iPhone or iPad.
- Removed the session-free Preview terminal and unused review-facing metadata.
- Clarified that copied-image upload writes one prepared image to a configured
  remote directory. That upload feature does not browse local files or list,
  read, or download remote files.
- Limited copied-image upload to SSH key-authenticated hosts. Password
  authentication remains available for terminal connections.
- Clarified that the keyboard extension inserts text or control sequences and
  that the receiving app decides how to interpret them.
- Fixed non-ASCII terminal input, which previously sent a single lossy byte.
- Corrected the keyboard extension metadata to declare its standard ASCII input.
- Added tests for the remote-execution, local-file, keyboard, and metadata
  boundaries.
- Added reviewer-safe iPhone and iPad App Store screenshot sets from the current
  build.

### Documentation and worker

- Added one App Store metadata and reviewer-response draft with credential-free
  placeholders.
- Added platform labels and iOS custom-keyboard limits to active public copy.
- Added Android, TestFlight, and App Store release-note templates.
- Updated social-worker prompts so they do not claim unsupported iOS behavior or
  an App Store launch.

### Distribution state

- KeyJawn Lite 1.3.0 has active internal and closed Google Play test tracks.
- Google Play production is inactive and Google Play app signing is active.
  The checked-in Android source and a local verification bundle are version
  1.4.0, version code 10, and target API 36. The bundle used a disposable key
  and cannot be uploaded.
- Apple rejected review build 2 under App Store Review guideline 2.5.2.
- Source build 9 exists in the repository.
- A fresh signed build 9 archive was created and verified from the corrected
  source on August 31, 2026. The exact archive was uploaded, and Apple processed
  build 9 as valid. Build 9 is selected for version 1.0, manual release is
  enabled, and Apple approved the version on September 17, 2026. App Store
  Connect reports pending developer release. The app is not publicly available
  until the developer release occurs and Apple publishes the listing.
- This remediation made no Android distribution change.
