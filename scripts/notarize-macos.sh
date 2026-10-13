#!/usr/bin/env bash
#
# Notarizes a Developer-ID-signed macOS artifact with Apple, staples the ticket and
# verifies that Gatekeeper accepts the result.
#
# Usage: notarize-macos.sh <FreeXmlToolkit.app | *.dmg | *.pkg>
#
#   *.dmg  the image itself is signed first (jpackage only signs the app inside it)
#   *.pkg  already signed by jpackage with the "Developer ID Installer" certificate
#   *.app  submitted as a temporary zip; the ticket is stapled to the bundle, so the
#          bundle has to be archived (ditto) only afterwards
#
# Environment:
#   APPLE_API_KEY_PATH      App Store Connect API key (.p8 file)
#   APPLE_API_KEY_ID        key id of that key
#   APPLE_API_ISSUER_ID     issuer id of the App Store Connect team
#   MACOS_SIGNING_IDENTITY  "Name (TEAMID)" - only needed for *.dmg
#   MACOS_SIGNING_KEYCHAIN  optional keychain that holds the certificate
#   NOTARIZE_TIMEOUT        optional, default 45m

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <FreeXmlToolkit.app | *.dmg | *.pkg>" >&2
    exit 2
fi

ARTIFACT="$1"
if [[ ! -e "$ARTIFACT" ]]; then
    echo "Artifact not found: $ARTIFACT" >&2
    exit 1
fi

for var in APPLE_API_KEY_PATH APPLE_API_KEY_ID APPLE_API_ISSUER_ID; do
    if [[ -z "${!var:-}" ]]; then
        echo "$var is not set" >&2
        exit 2
    fi
done

NOTARY_AUTH=(--key "$APPLE_API_KEY_PATH" --key-id "$APPLE_API_KEY_ID" --issuer "$APPLE_API_ISSUER_ID")
TIMEOUT="${NOTARIZE_TIMEOUT:-45m}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Submits a file and waits for the verdict; prints Apple's log and fails unless accepted.
submit() {
    local file="$1" result id status
    echo "Submitting $(basename "$file") to the Apple notary service (timeout $TIMEOUT)..."
    result="$(xcrun notarytool submit "$file" "${NOTARY_AUTH[@]}" \
        --wait --timeout "$TIMEOUT" --output-format json || true)"
    echo "$result"
    id="$(printf '%s' "$result" | plutil -extract id raw -o - - 2>/dev/null || true)"
    status="$(printf '%s' "$result" | plutil -extract status raw -o - - 2>/dev/null || true)"

    if [[ "$status" != "Accepted" ]]; then
        echo "Notarization failed (status: ${status:-unknown})" >&2
        if [[ -n "$id" ]]; then
            xcrun notarytool log "$id" "${NOTARY_AUTH[@]}" >&2 || true
        fi
        exit 1
    fi
    echo "Notarization accepted (submission $id)"
}

staple() {
    xcrun stapler staple "$1"
    xcrun stapler validate "$1"
}

case "$ARTIFACT" in
    *.dmg)
        if [[ -z "${MACOS_SIGNING_IDENTITY:-}" ]]; then
            echo "MACOS_SIGNING_IDENTITY is not set" >&2
            exit 2
        fi
        SIGN_DMG=(codesign --force --timestamp --sign "Developer ID Application: ${MACOS_SIGNING_IDENTITY}")
        if [[ -n "${MACOS_SIGNING_KEYCHAIN:-}" ]]; then
            SIGN_DMG+=(--keychain "$MACOS_SIGNING_KEYCHAIN")
        fi
        "${SIGN_DMG[@]}" "$ARTIFACT"
        submit "$ARTIFACT"
        staple "$ARTIFACT"
        spctl --assess --type open --context context:primary-signature -vv "$ARTIFACT"
        ;;
    *.pkg)
        pkgutil --check-signature "$ARTIFACT"
        submit "$ARTIFACT"
        staple "$ARTIFACT"
        spctl --assess --type install -vv "$ARTIFACT"
        ;;
    *.app)
        ARTIFACT="${ARTIFACT%/}"
        codesign --verify --deep --strict --verbose=2 "$ARTIFACT"
        ditto -c -k --sequesterRsrc --keepParent "$ARTIFACT" "$WORK_DIR/submission.zip"
        submit "$WORK_DIR/submission.zip"
        staple "$ARTIFACT"
        spctl --assess --type exec -vv "$ARTIFACT"
        ;;
    *)
        echo "Unsupported artifact type: $ARTIFACT" >&2
        exit 2
        ;;
esac

echo "Notarized and stapled: $ARTIFACT"
