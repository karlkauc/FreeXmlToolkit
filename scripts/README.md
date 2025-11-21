# macOS Code Signing Scripts

This directory contains scripts for signing and notarizing macOS DMG files.

## Scripts Overview

### 1. `sign-macos-adhoc.sh`
**Purpose:** Ad-hoc signing for local testing only
**Requirements:** macOS with Xcode Command Line Tools
**Distribution:** ❌ Cannot be distributed to other users

```bash
./sign-macos-adhoc.sh <dmg-file>

# Example
./sign-macos-adhoc.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg
```

**Use when:**
- Testing on your own Mac
- No Apple Developer Account
- Not planning to distribute

### 2. `sign-macos-dmg.sh`
**Purpose:** Sign with Developer ID certificate
**Requirements:** Apple Developer Account ($99/year) + Developer ID certificate
**Distribution:** ✅ Can be distributed (users see one-time warning)

```bash
./sign-macos-dmg.sh <dmg-file> [identity]

# Auto-detect certificate
./sign-macos-dmg.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg

# Specify certificate
./sign-macos-dmg.sh \
  build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg \
  "Developer ID Application: Your Name"
```

**Use when:**
- Distributing to team members or customers
- Have Apple Developer Account
- Want better security than ad-hoc

### 3. `notarize-macos-dmg.sh`
**Purpose:** Notarize DMG with Apple (best for public distribution)
**Requirements:** Developer ID signed DMG + Apple credentials
**Distribution:** ✅ Zero security warnings for users

```bash
./notarize-macos-dmg.sh <dmg-file> <apple-id> <team-id> <app-password>

# Example
./notarize-macos-dmg.sh \
  build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg \
  your@email.com \
  TEAM123456 \
  abcd-efgh-ijkl-mnop
```

**Use when:**
- Public distribution (GitHub releases, downloads)
- Want zero security warnings
- Have completed Developer ID signing

## Quick Start Guide

### For Testing (No Apple Account)
```bash
# 1. Build DMG
./gradlew createMacOSExecutableArm64

# 2. Ad-hoc sign
./scripts/sign-macos-adhoc.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg

# 3. Install on your Mac (may need to approve in System Settings)
```

### For Distribution (With Apple Developer Account)
```bash
# 1. Build DMG
./gradlew createMacOSExecutableArm64

# 2. Sign with Developer ID
./scripts/sign-macos-dmg.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg

# 3. Notarize (optional but recommended)
./scripts/notarize-macos-dmg.sh \
  build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg \
  your@email.com \
  TEAM123456 \
  abcd-efgh-ijkl-mnop
```

## Gradle Integration

These scripts can also be invoked via Gradle tasks:

```bash
# Ad-hoc signing (testing)
./gradlew signMacOSExecutableArm64AdHoc
./gradlew signMacOSExecutableX64AdHoc

# Developer ID signing
./gradlew signMacOSExecutableArm64
./gradlew signMacOSExecutableX64

# Notarization (requires environment variables)
export APPLE_ID="your@email.com"
export APPLE_TEAM_ID="TEAM123456"
export APPLE_APP_PASSWORD="abcd-efgh-ijkl-mnop"
./gradlew notarizeMacOSDMG
```

## Prerequisites

### All Scripts
- macOS operating system
- Xcode Command Line Tools: `xcode-select --install`

### Developer ID Signing
- Apple Developer Account ($99/year)
- Developer ID Application certificate installed in Keychain

### Notarization
- Everything from Developer ID Signing, plus:
- App-specific password from https://appleid.apple.com
- Team ID from https://developer.apple.com/account

## Troubleshooting

### "codesign command not found"
```bash
xcode-select --install
```

### "No Developer ID certificate found"
1. Go to https://developer.apple.com/account
2. Download Developer ID Application certificate
3. Install in Keychain Access
4. Verify: `security find-identity -v -p codesigning`

### "App still shows security warning"
- Make sure DMG is **both signed AND notarized**
- Verify: `xcrun stapler validate your-app.dmg`

## Complete Documentation

For detailed instructions, troubleshooting, and CI/CD integration:

**📖 [docs/MACOS_CODE_SIGNING.md](../docs/MACOS_CODE_SIGNING.md)**

## Script Features

All scripts include:
- ✅ Colored output for better readability
- ✅ Error checking and validation
- ✅ Automatic certificate detection
- ✅ Deep code signing
- ✅ Hardened runtime (for Developer ID)
- ✅ Timestamp signatures
- ✅ Signature verification
- ✅ Clear error messages and help text

## Support

If you encounter issues:
1. Check the [Complete Documentation](../docs/MACOS_CODE_SIGNING.md)
2. Verify prerequisites are installed
3. Check script output for specific errors
4. Open an issue with error details
