# Contributing to KeyJawn

Thanks for your interest in contributing. KeyJawn is a custom Android keyboard for LLM CLI users, and we welcome contributions that make it more useful for that workflow.

## Getting started

1. Fork the repo
2. Clone your fork
3. Create a branch: `git checkout -b feature/your-feature`
4. Make your changes
5. Push and open a pull request

## Development setup

KeyJawn is a standard Android Kotlin project:

- **Android Studio** (latest stable) or command-line Android SDK
- **JDK 17+**
- **Min SDK 26** (Android 8.0)
- **Target SDK 35**

### Building

```bash
./gradlew :app:assembleDebug
```

### Running tests

```bash
./gradlew :app:testDebugUnitTest
```

### Installing on a device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or download the debug APK from the GitHub Actions build artifacts.

## What to contribute

### Good first issues

Look for issues labeled `good first issue`. These are scoped, well-defined tasks.

### Feature ideas

- New terminal keys or key combinations
- Long-press behaviors for existing keys
- Visual themes
- SSH connection improvements
- Support for other LLM CLI tools

### Before you start

For anything beyond a small bug fix, open an issue first to discuss the approach. This prevents wasted effort if the feature doesn't fit the project direction.

## Code style

- Kotlin with standard Android conventions
- No emojis in source code, logs, or UI text
- Sentence case for all UI strings (not Title Case)
- Keep it simple — avoid unnecessary abstractions
- Write tests for new functionality

## Pull request process

1. Write tests for your changes
2. Make sure all tests pass: `./gradlew :app:testDebugUnitTest`
3. Keep PRs focused — one feature or fix per PR
4. Write a clear description of what changed and why

## Reporting bugs

Open an issue with:
- Device model and Android version
- App version
- Steps to reproduce
- What you expected vs what happened
- Screenshots if relevant

## Code of conduct

Be respectful. This is a small project built for a specific use case. Constructive feedback is welcome; hostility isn't.
