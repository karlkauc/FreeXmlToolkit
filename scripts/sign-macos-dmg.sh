#!/bin/bash
# Script to sign macOS DMG file with Developer ID
#
# Usage:
#   ./sign-macos-dmg.sh <dmg-file> [identity]
#
# Examples:
#   ./sign-macos-dmg.sh build/dist/macos-x64-dmg/FreeXmlToolkit-x64-1.0.0.dmg
#   ./sign-macos-dmg.sh build/dist/macos-x64-dmg/FreeXmlToolkit-x64-1.0.0.dmg "Developer ID Application: Your Name"
#
# Requirements:
#   - Apple Developer Account
#   - Developer ID Application certificate installed in Keychain
#   - Xcode Command Line Tools

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

# Check if codesign is available
if ! command -v codesign &> /dev/null; then
    print_error "codesign command not found. Please install Xcode Command Line Tools:"
    echo "  xcode-select --install"
    exit 1
fi

# Get DMG file path
DMG_FILE="$1"

if [ -z "$DMG_FILE" ]; then
    print_error "Usage: $0 <dmg-file> [identity]"
    exit 1
fi

if [ ! -f "$DMG_FILE" ]; then
    print_error "DMG file not found: $DMG_FILE"
    exit 1
fi

# Get signing identity (optional parameter)
SIGNING_IDENTITY="$2"

# If no identity provided, try to find Developer ID certificate
if [ -z "$SIGNING_IDENTITY" ]; then
    print_info "No signing identity provided, searching for Developer ID certificates..."

    # List all Developer ID Application certificates
    CERTIFICATES=$(security find-identity -v -p codesigning | grep "Developer ID Application" || true)

    if [ -z "$CERTIFICATES" ]; then
        print_error "No Developer ID Application certificate found in Keychain"
        print_info "Please install a Developer ID certificate or use ad-hoc signing (see scripts/sign-macos-adhoc.sh)"
        print_info ""
        print_info "To list all available code signing identities:"
        echo "  security find-identity -v -p codesigning"
        exit 1
    fi

    # Extract first certificate identity
    SIGNING_IDENTITY=$(echo "$CERTIFICATES" | head -n 1 | sed 's/.*"\(.*\)".*/\1/')
    print_info "Using certificate: $SIGNING_IDENTITY"
fi

# Create temporary directory for mounting DMG
MOUNT_POINT=$(mktemp -d)
APP_NAME="FreeXmlToolkit"

print_info "Mounting DMG: $DMG_FILE"
hdiutil attach "$DMG_FILE" -mountpoint "$MOUNT_POINT" -nobrowse -quiet

# Find the .app bundle
APP_PATH="$MOUNT_POINT/$APP_NAME.app"

if [ ! -d "$APP_PATH" ]; then
    print_error "Application bundle not found in DMG: $APP_PATH"
    hdiutil detach "$MOUNT_POINT" -quiet
    rm -rf "$MOUNT_POINT"
    exit 1
fi

print_info "Signing application bundle: $APP_PATH"

# Sign the app bundle with hardened runtime
codesign --force \
    --sign "$SIGNING_IDENTITY" \
    --options runtime \
    --timestamp \
    --deep \
    --verbose \
    "$APP_PATH"

# Verify signature
print_info "Verifying signature..."
codesign --verify --deep --strict --verbose=2 "$APP_PATH"

if [ $? -eq 0 ]; then
    print_success "Application signature verified successfully"
else
    print_error "Signature verification failed"
    hdiutil detach "$MOUNT_POINT" -quiet
    rm -rf "$MOUNT_POINT"
    exit 1
fi

# Unmount DMG
print_info "Unmounting DMG..."
hdiutil detach "$MOUNT_POINT" -quiet
rm -rf "$MOUNT_POINT"

# Now sign the DMG itself
print_info "Signing DMG file: $DMG_FILE"
codesign --force \
    --sign "$SIGNING_IDENTITY" \
    --timestamp \
    --verbose \
    "$DMG_FILE"

# Verify DMG signature
print_info "Verifying DMG signature..."
codesign --verify --verbose=2 "$DMG_FILE"

if [ $? -eq 0 ]; then
    print_success "DMG signature verified successfully"
else
    print_error "DMG signature verification failed"
    exit 1
fi

# Display signature info
print_info "Signature information:"
codesign -dvv "$DMG_FILE"

print_success "Code signing completed successfully!"
print_info ""
print_info "Next steps:"
echo "  1. Notarize the DMG with Apple (see scripts/notarize-macos-dmg.sh)"
echo "  2. Staple the notarization ticket (see scripts/notarize-macos-dmg.sh)"
echo "  3. Distribute the signed and notarized DMG"
print_info ""
print_warning "Important: Notarization is required for distribution on macOS 10.15+"
