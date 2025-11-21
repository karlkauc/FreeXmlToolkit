#!/bin/bash
# Script for ad-hoc signing of macOS DMG (for testing only)
#
# Ad-hoc signing allows the app to run on your local Mac without a Developer ID,
# but it CANNOT be distributed to other users.
#
# Usage:
#   ./sign-macos-adhoc.sh <dmg-file>
#
# Example:
#   ./sign-macos-adhoc.sh build/dist/macos-x64-dmg/FreeXmlToolkit-x64-1.0.0.dmg

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
    print_error "Usage: $0 <dmg-file>"
    exit 1
fi

if [ ! -f "$DMG_FILE" ]; then
    print_error "DMG file not found: $DMG_FILE"
    exit 1
fi

print_warning "Ad-hoc signing is for LOCAL TESTING ONLY"
print_warning "This signed app CANNOT be distributed to other users"
print_info ""

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

print_info "Performing ad-hoc signing of application bundle: $APP_PATH"

# Ad-hoc sign the app bundle (using "-" as identity)
codesign --force \
    --sign - \
    --deep \
    --verbose \
    "$APP_PATH"

# Verify signature
print_info "Verifying ad-hoc signature..."
codesign --verify --deep --verbose=2 "$APP_PATH"

if [ $? -eq 0 ]; then
    print_success "Ad-hoc signature verified successfully"
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

# Ad-hoc sign the DMG itself
print_info "Performing ad-hoc signing of DMG: $DMG_FILE"
codesign --force \
    --sign - \
    --verbose \
    "$DMG_FILE"

# Verify DMG signature
print_info "Verifying DMG signature..."
codesign --verify --verbose=2 "$DMG_FILE"

if [ $? -eq 0 ]; then
    print_success "DMG ad-hoc signature verified successfully"
else
    print_error "DMG signature verification failed"
    exit 1
fi

print_success "Ad-hoc code signing completed!"
print_info ""
print_warning "IMPORTANT LIMITATIONS:"
echo "  - This app will only run on YOUR Mac"
echo "  - Users may need to allow it in System Settings > Security & Privacy"
echo "  - This app CANNOT be distributed to other users"
echo "  - For distribution, you need a Developer ID certificate"
print_info ""
print_info "To allow the app to run on your Mac:"
echo "  1. Try to open the app"
echo "  2. If blocked, go to System Settings > Privacy & Security"
echo "  3. Click 'Open Anyway' next to the blocked app message"
print_info ""
print_info "For distribution to other users, use scripts/sign-macos-dmg.sh with a Developer ID"
