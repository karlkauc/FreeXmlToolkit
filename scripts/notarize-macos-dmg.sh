#!/bin/bash
# Script to notarize macOS DMG with Apple
#
# Notarization is required for distribution on macOS 10.15 (Catalina) and later.
# The DMG must be signed with a Developer ID certificate before notarization.
#
# Prerequisites:
#   - Apple Developer Account
#   - App-specific password created at appleid.apple.com
#   - DMG must be signed with Developer ID (use scripts/sign-macos-dmg.sh first)
#
# Usage:
#   ./notarize-macos-dmg.sh <dmg-file> <apple-id> <team-id> <app-password>
#
# Example:
#   ./notarize-macos-dmg.sh \
#     build/dist/macos-x64-dmg/FreeXmlToolkit-x64-1.0.0.dmg \
#     your@email.com \
#     TEAM123456 \
#     abcd-efgh-ijkl-mnop
#
# How to get credentials:
#   - Apple ID: Your developer account email
#   - Team ID: Found in App Store Connect > Membership
#   - App Password: Create at appleid.apple.com > Security > App-Specific Passwords

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if running on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    print_error "This script must be run on macOS"
    exit 1
fi

# Check if notarytool is available (requires Xcode 13+)
if ! command -v xcrun &> /dev/null; then
    print_error "xcrun command not found. Please install Xcode Command Line Tools:"
    echo "  xcode-select --install"
    exit 1
fi

# Parse arguments
DMG_FILE="$1"
APPLE_ID="$2"
TEAM_ID="$3"
APP_PASSWORD="$4"

if [ -z "$DMG_FILE" ] || [ -z "$APPLE_ID" ] || [ -z "$TEAM_ID" ] || [ -z "$APP_PASSWORD" ]; then
    print_error "Usage: $0 <dmg-file> <apple-id> <team-id> <app-password>"
    print_info ""
    print_info "Example:"
    echo "  $0 FreeXmlToolkit.dmg your@email.com TEAM123456 abcd-efgh-ijkl-mnop"
    print_info ""
    print_info "How to get credentials:"
    echo "  - Apple ID: Your developer account email"
    echo "  - Team ID: Found in App Store Connect > Membership"
    echo "  - App Password: Create at appleid.apple.com > Security > App-Specific Passwords"
    exit 1
fi

if [ ! -f "$DMG_FILE" ]; then
    print_error "DMG file not found: $DMG_FILE"
    exit 1
fi

# Check if DMG is signed
print_info "Verifying DMG signature..."
if ! codesign --verify --verbose=2 "$DMG_FILE" 2>&1 | grep -q "Developer ID"; then
    print_error "DMG is not signed with a Developer ID certificate"
    print_info "Please sign the DMG first using scripts/sign-macos-dmg.sh"
    exit 1
fi

print_success "DMG is properly signed with Developer ID"

# Submit for notarization
print_info "Submitting DMG for notarization..."
print_warning "This may take several minutes..."

SUBMIT_OUTPUT=$(xcrun notarytool submit "$DMG_FILE" \
    --apple-id "$APPLE_ID" \
    --team-id "$TEAM_ID" \
    --password "$APP_PASSWORD" \
    --wait 2>&1)

echo "$SUBMIT_OUTPUT"

# Check if submission was successful
if echo "$SUBMIT_OUTPUT" | grep -q "status: Accepted"; then
    print_success "Notarization successful!"

    # Extract request ID for stapling
    REQUEST_ID=$(echo "$SUBMIT_OUTPUT" | grep "id:" | head -n 1 | awk '{print $2}')

    # Staple the notarization ticket to the DMG
    print_info "Stapling notarization ticket to DMG..."
    xcrun stapler staple "$DMG_FILE"

    # Verify stapling
    print_info "Verifying stapled ticket..."
    xcrun stapler validate "$DMG_FILE"

    if [ $? -eq 0 ]; then
        print_success "Notarization ticket stapled successfully!"
        print_info ""
        print_success "✨ Your DMG is now signed and notarized!"
        print_info ""
        print_info "The DMG is ready for distribution and will not show security warnings on macOS 10.15+"
        print_info "File: $DMG_FILE"
    else
        print_error "Stapling verification failed"
        exit 1
    fi

elif echo "$SUBMIT_OUTPUT" | grep -q "status: Invalid"; then
    print_error "Notarization failed: Invalid submission"
    print_info ""
    print_info "Getting detailed error log..."

    # Extract request ID for error log
    REQUEST_ID=$(echo "$SUBMIT_OUTPUT" | grep "id:" | head -n 1 | awk '{print $2}')

    if [ ! -z "$REQUEST_ID" ]; then
        xcrun notarytool log "$REQUEST_ID" \
            --apple-id "$APPLE_ID" \
            --team-id "$TEAM_ID" \
            --password "$APP_PASSWORD"
    fi

    exit 1

else
    print_error "Notarization failed or timed out"
    echo "$SUBMIT_OUTPUT"
    exit 1
fi

print_info ""
print_success "All done! Your DMG is ready for distribution."
print_info ""
print_info "Signature and notarization details:"
codesign -dvv "$DMG_FILE"
