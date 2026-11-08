#!/usr/bin/env python3
"""Writes a shields-style SVG badge with the line coverage of a JaCoCo XML report.

Used by .github/workflows/tests.yml; the badge is pushed to the `badges` branch and shown in the README.
Usage: coverage-badge.py JACOCO_XML OUTPUT_SVG
Prints the coverage in percent (one decimal).
"""
import sys
import xml.etree.ElementTree as ET


def line_coverage(path):
    root = ET.parse(path).getroot()
    for counter in root.findall("counter"):  # report-level counters only
        if counter.get("type") == "LINE":
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            return 100.0 * covered / max(1, missed + covered)
    raise SystemExit(f"No LINE counter in {path}")


def color(percent):
    if percent >= 80:
        return "#4c1"
    if percent >= 70:
        return "#97ca00"
    if percent >= 60:
        return "#dfb317"
    if percent >= 50:
        return "#fe7d37"
    return "#e05d44"


def badge(label, value, fill):
    # Character widths approximated for Verdana 11px, as shields.io does.
    left, right = 6 * len(label) + 12, 7 * len(value) + 12
    width = left + right
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="20" role="img" aria-label="{label}: {value}">
<title>{label}: {value}</title>
<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
<clipPath id="r"><rect width="{width}" height="20" rx="3" fill="#fff"/></clipPath>
<g clip-path="url(#r)"><rect width="{left}" height="20" fill="#555"/><rect x="{left}" width="{right}" height="20" fill="{fill}"/><rect width="{width}" height="20" fill="url(#s)"/></g>
<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
<text x="{left / 2}" y="15" fill="#010101" fill-opacity=".3">{label}</text><text x="{left / 2}" y="14">{label}</text>
<text x="{left + right / 2}" y="15" fill="#010101" fill-opacity=".3">{value}</text><text x="{left + right / 2}" y="14">{value}</text>
</g></svg>
"""


def main():
    percent = line_coverage(sys.argv[1])
    with open(sys.argv[2], "w", encoding="utf-8") as out:
        out.write(badge("coverage", f"{percent:.0f}%", color(percent)))
    print(f"{percent:.1f}")


if __name__ == "__main__":
    main()
