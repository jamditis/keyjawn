#!/usr/bin/env bash
# KeyJawn iOS build + upload script
#
# Usage:
#   ./scripts/build.sh              # build + upload to TestFlight
#   ./scripts/build.sh --archive-only  # build and archive, skip upload
#
# Requirements: xcodegen, xcodebuild, xcrun altool, PyJWT+cryptography
# Run from the ios/ directory.

set -euo pipefail

SCHEME="KeyJawn"
WORKSPACE_OR_PROJ="-project KeyJawn.xcodeproj"
ARCHIVE_PATH="/tmp/KeyJawn.xcarchive"
EXPORT_PATH="/tmp/KeyJawn-export"
EXPORT_PLIST="scripts/export-options-appstore.plist"
KEY_ID="76VHW9V6BJ"
ISSUER_ID="7944b086-4ca7-4756-9719-602976a67775"
UPLOAD="${1:-}"

echo "==> Regenerating Xcode project"
xcodegen generate

echo "==> Archiving"
xcodebuild archive \
    $WORKSPACE_OR_PROJ \
    -scheme "$SCHEME" \
    -destination "generic/platform=iOS" \
    -archivePath "$ARCHIVE_PATH" \
    -allowProvisioningUpdates \
    CODE_SIGN_STYLE=Automatic \
    DEVELOPMENT_TEAM=5624SD289G \
    | xcpretty 2>/dev/null || true

# Fall back to raw output if xcpretty not installed
if [ ! -d "$ARCHIVE_PATH" ]; then
    echo "xcpretty not found or archive failed — retrying with raw output"
    xcodebuild archive \
        $WORKSPACE_OR_PROJ \
        -scheme "$SCHEME" \
        -destination "generic/platform=iOS" \
        -archivePath "$ARCHIVE_PATH" \
        -allowProvisioningUpdates \
        CODE_SIGN_STYLE=Automatic \
        DEVELOPMENT_TEAM=5624SD289G
fi

echo "==> Exporting IPA"
xcodebuild -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportPath "$EXPORT_PATH" \
    -exportOptionsPlist "$EXPORT_PLIST" \
    -allowProvisioningUpdates

IPA=$(find "$EXPORT_PATH" -name "*.ipa" | head -1)
echo "==> IPA: $IPA"

if [ "$UPLOAD" = "--archive-only" ]; then
    echo "==> Skipping upload (--archive-only)"
    exit 0
fi

echo "==> Uploading to TestFlight"
xcrun altool --upload-app \
    --type ios \
    --file "$IPA" \
    --apiKey "$KEY_ID" \
    --apiIssuer "$ISSUER_ID" \
    --verbose

echo "==> Done. Build will appear in TestFlight once Apple finishes processing (usually 5-15 min)."
