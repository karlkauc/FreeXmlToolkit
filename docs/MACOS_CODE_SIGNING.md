# macOS Code Signing and Notarization Guide

## Problem

When distributing macOS applications (DMG files), users see this error:

> **"Apple konnte nicht überprüfen, ob „FreeXmlToolkit" frei von Schadsoftware ist"**
>
> (Apple could not verify that "FreeXmlToolkit" is free from malware)

This happens because macOS **Gatekeeper** blocks unsigned applications by default. Starting with macOS 10.15 (Catalina), even signed apps require **notarization** by Apple.

## Solutions Overview

We provide three solutions depending on your use case:

| Solution | Use Case | Requirements | Distribution |
|----------|----------|--------------|--------------|
| **Ad-hoc Signing** | Local testing only | macOS with Xcode CLI Tools | ❌ Cannot distribute |
| **Developer ID Signing** | Internal distribution | Apple Developer Account ($99/year) | ✅ Can distribute (with warning) |
| **Notarization** | Public distribution | Developer ID + Notarization | ✅ No warnings |

---

## Solution 1: Ad-hoc Signing (Testing Only)

### When to Use
- Testing the DMG on **your own Mac**
- No plans to distribute to others
- No Apple Developer Account

### Quick Start

```bash
# Build the DMG
./gradlew createMacOSExecutableArm64  # for Apple Silicon
# or
./gradlew createMacOSExecutableX64    # for Intel Mac

# Sign with ad-hoc signature
./gradlew signMacOSExecutableArm64AdHoc
# or
./gradlew signMacOSExecutableX64AdHoc
```

### Manual Signing

```bash
# Sign a specific DMG file
./scripts/sign-macos-adhoc.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg
```

### Installing Ad-hoc Signed App

1. Open the DMG file
2. If blocked, go to **System Settings** → **Privacy & Security**
3. Click **"Open Anyway"** next to the blocked app message
4. The app will now open

### Limitations
- ❌ Only works on **your Mac**
- ❌ **Cannot** be distributed to other users
- ❌ Users will still see security warnings

---

## Solution 2: Developer ID Signing

### When to Use
- Distributing to a **limited audience** (team, testers, customers)
- Willing to invest in Apple Developer Account
- Users can accept one-time security prompt

### Prerequisites

1. **Apple Developer Account** ($99/year)
   - Sign up at: https://developer.apple.com

2. **Developer ID Certificate**
   - Log in to https://developer.apple.com/account
   - Go to **Certificates, Identifiers & Profiles**
   - Create **Developer ID Application** certificate
   - Download and install in **Keychain Access**

3. **Xcode Command Line Tools**
   ```bash
   xcode-select --install
   ```

### Verify Certificate Installation

```bash
# List all Developer ID certificates
security find-identity -v -p codesigning | grep "Developer ID Application"
```

You should see something like:
```
1) 1234ABCD... "Developer ID Application: Your Name (TEAM123456)"
```

### Signing with Developer ID

#### Option A: Automatic (Gradle)

```bash
# Set environment variable (optional - script will auto-detect if not set)
export MACOS_SIGNING_IDENTITY="Developer ID Application: Your Name"

# Build and sign
./gradlew createMacOSExecutableArm64
./gradlew signMacOSExecutableArm64
```

#### Option B: Manual Script

```bash
# Auto-detect certificate
./scripts/sign-macos-dmg.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg

# Or specify certificate explicitly
./scripts/sign-macos-dmg.sh \
  build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg \
  "Developer ID Application: Your Name"
```

### Distribution

After signing with Developer ID:
- ✅ Users can install with **one security prompt**
- ✅ Better than ad-hoc (but still shows warning)
- ⚠️ For best experience, proceed to **Notarization** (Solution 3)

---

## Solution 3: Notarization (Recommended for Public Distribution)

### When to Use
- **Public distribution** (GitHub releases, website downloads)
- Want **zero security warnings** for users
- macOS 10.15+ users (most modern Macs)

### Prerequisites

Same as Developer ID Signing, plus:

1. **App-Specific Password**
   - Go to https://appleid.apple.com
   - Sign in with your Apple ID
   - Go to **Security** → **App-Specific Passwords**
   - Generate new password (save it securely!)

2. **Apple Team ID**
   - Go to https://developer.apple.com/account
   - Click **Membership**
   - Copy your **Team ID** (e.g., `TEAM123456`)

### Complete Workflow

#### Step 1: Build the DMG
```bash
./gradlew createMacOSExecutableArm64
```

#### Step 2: Sign with Developer ID
```bash
./gradlew signMacOSExecutableArm64
# or manually:
./scripts/sign-macos-dmg.sh build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg
```

#### Step 3: Notarize with Apple

**Option A: Using Gradle**
```bash
export APPLE_ID="your@email.com"
export APPLE_TEAM_ID="TEAM123456"
export APPLE_APP_PASSWORD="abcd-efgh-ijkl-mnop"

./gradlew notarizeMacOSDMG
```

**Option B: Manual Script**
```bash
./scripts/notarize-macos-dmg.sh \
  build/dist/macos-arm64-dmg/FreeXmlToolkit-arm64-1.0.0.dmg \
  your@email.com \
  TEAM123456 \
  abcd-efgh-ijkl-mnop
```

#### What Happens During Notarization

1. **Upload** - DMG is uploaded to Apple servers (2-5 minutes)
2. **Scan** - Apple scans for malware (5-30 minutes)
3. **Approval** - If clean, Apple approves
4. **Stapling** - Notarization ticket is stapled to DMG
5. **Done** - DMG is ready for distribution!

### Verification

```bash
# Verify signature
codesign --verify --deep --verbose=2 FreeXmlToolkit-arm64-1.0.0.dmg

# Verify notarization
xcrun stapler validate FreeXmlToolkit-arm64-1.0.0.dmg

# View signature details
codesign -dvv FreeXmlToolkit-arm64-1.0.0.dmg
```

### Distribution

After notarization:
- ✅ **Zero security warnings** for users
- ✅ Smooth installation experience
- ✅ Works on all macOS 10.15+ systems
- ✅ Ready for public distribution

---

## Quick Reference: All Commands

### Ad-hoc Signing (Testing)
```bash
./gradlew createMacOSExecutableArm64
./gradlew signMacOSExecutableArm64AdHoc
```

### Developer ID Signing
```bash
./gradlew createMacOSExecutableArm64
./gradlew signMacOSExecutableArm64
```

### Full Notarization Workflow
```bash
# 1. Build
./gradlew createMacOSExecutableArm64

# 2. Sign
./gradlew signMacOSExecutableArm64

# 3. Set credentials
export APPLE_ID="your@email.com"
export APPLE_TEAM_ID="TEAM123456"
export APPLE_APP_PASSWORD="abcd-efgh-ijkl-mnop"

# 4. Notarize
./gradlew notarizeMacOSDMG
```

---

## Troubleshooting

### "codesign command not found"
```bash
xcode-select --install
```

### "No Developer ID certificate found"
1. Go to https://developer.apple.com/account
2. Download **Developer ID Application** certificate
3. Double-click to install in Keychain Access
4. Verify: `security find-identity -v -p codesigning`

### "Notarization failed: Invalid"
Common causes:
- DMG not signed with Developer ID first
- Using ad-hoc signature instead of Developer ID
- Hardened runtime not enabled (our scripts enable it automatically)

### "App still shows security warning"
- Make sure you completed **all three steps**: Build → Sign → Notarize
- Verify notarization: `xcrun stapler validate your-app.dmg`
- If notarization shows "not found", run the notarization step again

### "How do I know if my DMG is signed?"
```bash
codesign --verify --verbose=2 FreeXmlToolkit.dmg
# If signed: "valid on disk" and "satisfies its Designated Requirement"
# If not signed: "code object is not signed at all"
```

### "How do I know if my DMG is notarized?"
```bash
xcrun stapler validate FreeXmlToolkit.dmg
# If notarized: "The validate action worked!"
# If not notarized: "does not have a ticket stapled to it"
```

---

## CI/CD Integration (GitHub Actions)

### Secrets Setup

Add these secrets to your GitHub repository:
- `APPLE_ID` - Your Apple Developer email
- `APPLE_TEAM_ID` - Your Team ID
- `APPLE_APP_PASSWORD` - App-specific password
- `MACOS_CERTIFICATE` - Base64-encoded .p12 certificate file
- `MACOS_CERTIFICATE_PASSWORD` - Certificate password

### Example Workflow

```yaml
name: Build and Sign macOS DMG

on:
  release:
    types: [published]

jobs:
  build-macos:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          distribution: 'liberica'
          java-version: '25'
          java-package: jdk+fx

      - name: Import Code Signing Certificate
        run: |
          # Create temporary keychain
          security create-keychain -p actions build.keychain
          security default-keychain -s build.keychain
          security unlock-keychain -p actions build.keychain

          # Import certificate
          echo "${{ secrets.MACOS_CERTIFICATE }}" | base64 --decode > certificate.p12
          security import certificate.p12 \
            -k build.keychain \
            -P "${{ secrets.MACOS_CERTIFICATE_PASSWORD }}" \
            -T /usr/bin/codesign

          security set-key-partition-list \
            -S apple-tool:,apple: \
            -s -k actions build.keychain

      - name: Build DMG
        run: ./gradlew createMacOSExecutableArm64

      - name: Sign DMG
        run: ./gradlew signMacOSExecutableArm64

      - name: Notarize DMG
        env:
          APPLE_ID: ${{ secrets.APPLE_ID }}
          APPLE_TEAM_ID: ${{ secrets.APPLE_TEAM_ID }}
          APPLE_APP_PASSWORD: ${{ secrets.APPLE_APP_PASSWORD }}
        run: ./gradlew notarizeMacOSDMG

      - name: Upload DMG
        uses: actions/upload-artifact@v3
        with:
          name: FreeXmlToolkit-macOS
          path: build/dist/macos-arm64-dmg/*.dmg
```

---

## Cost Summary

| Item | Cost | Notes |
|------|------|-------|
| **Ad-hoc Signing** | Free | Testing only, cannot distribute |
| **Developer ID** | $99/year | Apple Developer Program membership |
| **Notarization** | Free | Included with Developer ID |
| **CI/CD** | Free | GitHub Actions free tier sufficient |

---

## Best Practices

### 1. Always Use Hardened Runtime
- Our scripts automatically enable hardened runtime (`--options runtime`)
- Required for notarization
- Improves security

### 2. Timestamp Signatures
- Our scripts use `--timestamp` flag
- Ensures signatures remain valid even if certificate expires
- Required for long-term distribution

### 3. Deep Code Signing
- Sign all nested code (`--deep` flag)
- Important for apps with frameworks and plugins
- Our scripts handle this automatically

### 4. Keep Certificates Secure
- Never commit certificates to Git
- Use environment variables or secrets management
- Rotate app-specific passwords periodically

### 5. Verify Before Distribution
```bash
# Always verify both signature and notarization
codesign --verify --deep --verbose=2 your-app.dmg
xcrun stapler validate your-app.dmg
```

---

## Resources

### Official Apple Documentation
- [Code Signing Guide](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/)
- [Notarizing macOS Software](https://developer.apple.com/documentation/security/notarizing_macos_software_before_distribution)
- [Hardened Runtime](https://developer.apple.com/documentation/security/hardened_runtime)

### Tools
- [Apple Developer Portal](https://developer.apple.com/account)
- [App-Specific Passwords](https://appleid.apple.com)
- [Keychain Access](https://support.apple.com/guide/keychain-access/)

### Community
- [Apple Developer Forums](https://developer.apple.com/forums/)
- [Stack Overflow - macOS Code Signing](https://stackoverflow.com/questions/tagged/codesign)

---

## Summary

| Goal | Solution | Command |
|------|----------|---------|
| Test on your Mac | Ad-hoc signing | `./gradlew signMacOSExecutableArm64AdHoc` |
| Internal distribution | Developer ID | `./gradlew signMacOSExecutableArm64` |
| Public distribution | Notarization | `./gradlew notarizeMacOSDMG` |

**Recommended for most users**: Complete the full notarization workflow for the best user experience.

---

## Support

If you encounter issues:

1. Check the **Troubleshooting** section above
2. Verify your Apple Developer Account is active
3. Ensure certificates are properly installed
4. Review the script output for error messages
5. Open an issue with the error details

For quick help:
```bash
# Check signing status
./scripts/sign-macos-dmg.sh --help

# Verify your setup
security find-identity -v -p codesigning
codesign --verify --verbose=2 your-app.dmg
xcrun stapler validate your-app.dmg
```
