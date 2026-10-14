# macOS Code Signing and Notarization

The macOS packages (`.dmg`, `.pkg`, app-image `.zip`) are signed with a **Developer ID**
certificate, notarized by Apple and stapled, so Gatekeeper opens them without a warning.
All of it happens in the release workflow
(`.github/workflows/build-packages-on-new-release.yml`); nothing has to be done by hand
per release.

## How it works

| Step | Where | What |
|------|-------|------|
| 1 | workflow | Imports the certificates into a temporary keychain, writes the API key to `$RUNNER_TEMP` |
| 2 | `sign-macos-jar-natives.sh` | Signs native libraries inside jars (JNA's `libjnidispatch.jnilib`) – jpackage does not reach into jars, and the notary service rejects unsigned ones |
| 3 | `jpackage --mac-sign` | Signs the bundle and the jlink runtime with the hardened runtime and `release/macos/entitlements.plist`; signs the `.pkg` with the installer certificate |
| 4 | `notarize-macos.sh` | Signs the `.dmg` itself, submits the artifact (`notarytool`), staples the ticket (retried on network errors) and verifies with `codesign` and `spctl` |
| 5 | Gradle | The app-image is archived with `ditto` **after** stapling (a regular zip would break the signature) |

Gradle switches this on by environment (`build.gradle.kts`, "macOS code signing and notarization"):

| Variable | Effect |
|----------|--------|
| `MACOS_SIGNING_IDENTITY` | `Name (TEAMID)`. Set on a macOS host → packages are signed |
| `MACOS_SIGNING_KEYCHAIN` | Optional keychain holding the certificates |
| `APPLE_API_KEY_ID`, `APPLE_API_ISSUER_ID`, `APPLE_API_KEY_PATH` | Set in addition → packages are notarized and stapled |

Without these variables the build produces unsigned (ad-hoc signed) packages as before.
The `notarizeMacOS{Dmg,Pkg,AppImage}{X64,Arm64}` tasks run automatically as part of
`createMacOSExecutable*`, `createMacOSPkg*` and `createMacOSAppImage*Zip`.

## One-time setup

No Mac is required for any of this.

### 1. Private keys and certificate signing requests

Apple accepts a signing request only once, so each certificate gets its own key and request:

```bash
openssl genrsa -out developer_id.key 2048
openssl req -new -key developer_id.key -out developer_id.csr -subj "/CN=Karl Kauc/C=AT"

openssl genrsa -out developer_id_installer.key 2048
openssl req -new -key developer_id_installer.key -out developer_id_installer.csr -subj "/CN=Karl Kauc Installer/C=AT"
```

### 2. Create the two certificates

At <https://developer.apple.com/account/resources/certificates> click **+**, choose the
*G2 Sub-CA* profile and upload the matching request:

- **Developer ID Application** (`developer_id.csr`) – signs the app and the `.dmg`
- **Developer ID Installer** (`developer_id_installer.csr`) – signs the `.pkg`

Only the Account Holder of the team can create Developer ID certificates.

### 3. Convert to `.p12`

```bash
openssl x509 -inform DER -in developerID_application.cer -out app.pem
openssl pkcs12 -export -legacy -inkey developer_id.key -in app.pem -out app.p12

openssl x509 -inform DER -in developerID_installer.cer -out installer.pem
openssl pkcs12 -export -legacy -inkey developer_id_installer.key -in installer.pem -out installer.p12
```

Use the same export password for both. `-legacy` is required: the macOS `security` tool
cannot read the default OpenSSL 3 encryption.

### 4. App Store Connect API key

At <https://appstoreconnect.apple.com/access/integrations/api> create a **Team Key** with
the *Developer* role. Download the `.p8` file (possible only once) and note the **Key ID**
and the **Issuer ID**. The **Team ID** is shown under *Membership details* in the
developer account.

### 5. GitHub secrets

| Secret | Value |
|--------|-------|
| `MACOS_APP_CERT_P12_BASE64` | `base64 -w0 app.p12` |
| `MACOS_INSTALLER_CERT_P12_BASE64` | `base64 -w0 installer.p12` |
| `MACOS_CERT_PASSWORD` | export password of the `.p12` files |
| `MACOS_SIGNING_IDENTITY` | `Karl Kauc (TEAMID)` – the part after `Developer ID Application: ` |
| `APPLE_API_KEY_P8_BASE64` | `base64 -w0 AuthKey_XXXXXXXXXX.p8` |
| `APPLE_API_KEY_ID` | Key ID |
| `APPLE_API_ISSUER_ID` | Issuer ID |

```bash
base64 -w0 app.p12 | gh secret set MACOS_APP_CERT_P12_BASE64
gh secret set MACOS_SIGNING_IDENTITY --body "Karl Kauc (TEAMID)"
```

Keep the two `.key` files, the `.p12` files and the `.p8` file somewhere safe and **out of
the repository**. Developer ID certificates are valid for five years; packages signed
before the expiry keep working.

## Testing without a release

The workflow has a manual trigger: *Actions → Build and Release Executables → Run
workflow* (or `gh workflow run build-packages-on-new-release.yml --ref <branch>`). A manual
run uploads the packages as workflow artifacts instead of attaching them to a release.
GitHub only offers the trigger once the workflow file containing `workflow_dispatch` is on
the default branch.

To check the result on a Mac, download a package **with a browser** (so the quarantine
attribute is set) and open it, or run:

```bash
spctl --assess --type exec -vv /Applications/FreeXmlToolkit.app   # "accepted, source=Notarized Developer ID"
xcrun stapler validate FreeXmlToolkit-arm64-<version>.dmg
```

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| jpackage: *"No certificate found matching ..."* | `MACOS_SIGNING_IDENTITY` does not match the certificate name exactly, or the installer certificate is missing (`.pkg` jobs). The import step prints `security find-identity` |
| `security import` fails with a MAC / password error | The `.p12` was exported without `-legacy` |
| Notarization *Invalid*: "The binary is not signed" for a path inside a jar | A new dependency ships a native library in a form `sign-macos-jar-natives.sh` does not match (it handles `*.dylib` / `*.jnilib`) |
| Notarization *Invalid*: "The executable does not have the hardened runtime enabled" | Something was signed outside jpackage without `--options runtime` |
| App crashes at start only when signed | A missing hardened-runtime entitlement – see `release/macos/entitlements.plist` |
| `notarytool` returns 401 / 403 | Wrong Key ID / Issuer ID, revoked key, or a pending agreement in the developer account |
| Notarization times out | The first submissions of a new account can take much longer; raise `NOTARIZE_TIMEOUT` (default `45m`) or re-run the job |

On rejection `notarize-macos.sh` prints Apple's notarization log, which names the
offending files.
