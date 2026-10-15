#!/usr/bin/env python3
"""Render the package-manager manifest templates in packaging/ for one release.

Usage: render-manifests.py <version> <SHA256SUMS.txt> <output-dir> [scoop|homebrew|flatpak ...]

Without a target list all templates are rendered.

Templates use @@VERSION@@ and @@SHA256_<ASSET>@@ placeholders; the asset keys map to the
release file names below. Fails when a placeholder stays unresolved, so a renamed release
asset cannot silently produce a broken manifest.
"""
import re
import sys
from pathlib import Path

TEMPLATES = {
    "scoop/freexmltoolkit.json": "scoop/freexmltoolkit.json",
    "homebrew/freexmltoolkit.rb": "homebrew/Casks/freexmltoolkit.rb",
    "flatpak/io.github.karlkauc.FreeXmlToolkit.yml": "flatpak/io.github.karlkauc.FreeXmlToolkit.yml",
}

ASSETS = {
    "WINDOWS_X64_ZIP": "FreeXmlToolkit-windows-x64-app-image-{v}.zip",
    "WINDOWS_X64_MSI": "FreeXmlToolkit-x64-{v}.msi",
    "MACOS_ARM64_DMG": "FreeXmlToolkit-arm64-{v}.dmg",
    "MACOS_X64_DMG": "FreeXmlToolkit-x64-{v}.dmg",
    "LINUX_X64_ZIP": "FreeXmlToolkit-linux-x64-app-image-{v}.zip",
}


def parse_sums(path):
    sums = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) == 2:
            sums[parts[1].lstrip("*")] = parts[0].lower()
    return sums


def main(argv):
    if len(argv) < 4:
        sys.exit(__doc__)
    version, sums_file, out_dir = argv[1], argv[2], Path(argv[3])
    targets = set(argv[4:])
    unknown = targets - {src.split("/")[0] for src in TEMPLATES}
    if unknown:
        sys.exit(f"unknown targets: {sorted(unknown)}")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        sys.exit(f"invalid version: {version}")
    sums = parse_sums(sums_file)
    values = {"VERSION": version}
    for key, pattern in ASSETS.items():
        name = pattern.format(v=version)
        if name in sums:
            values["SHA256_" + key] = sums[name]

    base = Path(__file__).resolve().parent
    for src, dest in TEMPLATES.items():
        if targets and src.split("/")[0] not in targets:
            continue
        text = (base / src).read_text(encoding="utf-8")
        text = re.sub(r"@@([A-Z0-9_]+)@@", lambda m: values.get(m.group(1), m.group(0)), text)
        missing = sorted(set(re.findall(r"@@([A-Z0-9_]+)@@", text)))
        if missing:
            sys.exit(f"{src}: unresolved placeholders {missing} (asset missing from {sums_file}?)")
        target = out_dir / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        print(f"rendered {target}")


if __name__ == "__main__":
    main(sys.argv)
