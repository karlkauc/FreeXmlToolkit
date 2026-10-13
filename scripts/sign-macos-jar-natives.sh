#!/usr/bin/env bash
#
# Signs the macOS native libraries (*.dylib, *.jnilib) that are packed inside jar files.
#
# Apple's notary service unpacks jars and rejects the submission when it finds an
# unsigned Mach-O binary in one of them; `jpackage --mac-sign` only signs files of the
# bundle itself, not jar contents. Run this on the jpackage input directory before
# jpackage is invoked.
#
# Usage: sign-macos-jar-natives.sh <directory-with-jars>
#
# Environment:
#   MACOS_SIGNING_IDENTITY  "Name (TEAMID)" - the part after "Developer ID Application: "
#   MACOS_SIGNING_KEYCHAIN  optional keychain that holds the certificate

set -euo pipefail

if [[ $# -ne 1 || ! -d "$1" ]]; then
    echo "Usage: $0 <directory-with-jars>" >&2
    exit 2
fi
if [[ -z "${MACOS_SIGNING_IDENTITY:-}" ]]; then
    echo "MACOS_SIGNING_IDENTITY is not set" >&2
    exit 2
fi

JAR_DIR="$(cd "$1" && pwd)"
IDENTITY="Developer ID Application: ${MACOS_SIGNING_IDENTITY}"

CODESIGN=(codesign --force --timestamp --options runtime --sign "$IDENTITY")
if [[ -n "${MACOS_SIGNING_KEYCHAIN:-}" ]]; then
    CODESIGN+=(--keychain "$MACOS_SIGNING_KEYCHAIN")
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

signed=0
while IFS= read -r -d '' jar; do
    natives="$(unzip -Z1 "$jar" | grep -E '\.(dylib|jnilib)$' || true)"
    [[ -z "$natives" ]] && continue

    extract_dir="$WORK_DIR/$(basename "$jar").d"
    mkdir -p "$extract_dir"
    while IFS= read -r entry; do
        echo "Signing $(basename "$jar")!/$entry"
        unzip -q -o "$jar" "$entry" -d "$extract_dir"
        "${CODESIGN[@]}" "$extract_dir/$entry"
        # Replace the entry in place; the path inside the jar stays the same.
        (cd "$extract_dir" && zip -q "$jar" "$entry")
        signed=$((signed + 1))
    done <<< "$natives"
done < <(find "$JAR_DIR" -type f -name '*.jar' -print0)

echo "Signed $signed native librar$([[ $signed -eq 1 ]] && echo y || echo ies) inside jars under $JAR_DIR"
