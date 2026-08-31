# Contributing to KeyJawn

Thanks for your interest in contributing. KeyJawn has an Android keyboard and an
iOS remote SSH terminal with a companion keyboard.

## Getting started

1. Fork the repository.
2. Clone your fork.
3. Create a branch: `git checkout -b feature/your-feature`.
4. Make and test your changes.
5. Push the branch and open a pull request.

## Development setup

### Android

- Android Studio or the command-line Android SDK.
- JDK 17 or later. The Gradle wrapper provisions its pinned JDK 21 daemon
  toolchain.
- Android SDK 36. The minimum supported version is Android 8.0, API 26.

Build both Android flavors:

```bash
./gradlew assembleFullDebug
./gradlew assembleLiteDebug
```

Run Android tests:

```bash
./gradlew testFullDebugUnitTest
./gradlew testLiteDebugUnitTest
```

Install one Android flavor:

```bash
adb install app/build/outputs/apk/full/debug/app-full-debug.apk
```

### iOS

- Xcode 26 or later.
- Swift 6 and an iOS 17 or later deployment target.
- XcodeGen. Edit `ios/project.yml`, then regenerate the project.

Build and test from `ios/`:

```bash
xcodegen generate
xcodebuild -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
xcodebuild test -project KeyJawn.xcodeproj -scheme KeyJawn \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

The iOS terminal sends input to a user-configured remote SSH server. Do not add a
local shell, command interpreter, device-file browser, runtime loader, or other
behavior that conflicts with the boundary in
[`docs/ios-app-review.md`](docs/ios-app-review.md).

## What to contribute

### Good first issues

Look for issues labeled `good first issue`. These are scoped, well-defined tasks.

### Feature ideas

- Android keyboard keys, touch behavior, voice input, or themes.
- iOS remote SSH connection and terminal improvements.
- iOS companion-keyboard improvements that use supported text-input APIs.
- Tests, accessibility work, and clear documentation.

### Before you start

For anything beyond a small bug fix, open an issue first to discuss the approach. This prevents wasted effort if the feature doesn't fit the project direction.

## Code style

- Use standard Kotlin conventions for Android and Swift 6 strict concurrency for
  iOS.
- Use sentence case for UI text.
- Do not use emojis in source code, logs, or UI text.
- Prefer a small, direct change over a new abstraction.
- Add tests for new behavior and regression tests for bug fixes.
- Label platform-specific features as Android-only or iOS-only in public copy.

## Pull request process

1. Write tests for the change.
2. Run the applicable Android or iOS build, test, lint, and analysis checks.
3. Update the README, changelog, and platform documentation when behavior
   changes.
4. Keep the pull request focused on one feature or fix.
5. Explain why the change is needed and include the commands and results used to
   verify it.

A pull request must not archive or upload an iOS build, change App Store Connect
metadata, send an App Review response, cancel a submission, or resubmit the app.
Those actions require separate approval at action time.

## Reporting bugs

Open an issue with:
- Device model and operating-system version
- App version
- Steps to reproduce
- What you expected vs what happened
- Screenshots if relevant

## Code of conduct

Be respectful. This is a small project built for a specific use case. Constructive feedback is welcome; hostility isn't.
