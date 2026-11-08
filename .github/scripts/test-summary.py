#!/usr/bin/env python3
"""Summarizes JUnit XML results (all TEST-*.xml below the given folder) as Markdown.

Used by .github/workflows/tests.yml to write the job summary of the sharded test run.
Usage: test-summary.py RESULTS_DIR [COVERAGE_XML]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def coverage(path):
    """Line coverage in percent from a JaCoCo XML report, or None."""
    if not path or not os.path.isfile(path):
        return None
    root = ET.parse(path).getroot()
    for counter in root.findall("counter"):  # report-level counters only
        if counter.get("type") == "LINE":
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            return 100.0 * covered / max(1, missed + covered)
    return None


def main():
    results_dir = sys.argv[1]
    tests = failures = errors = skipped = 0
    seconds = 0.0
    failed = []
    for path in sorted(glob.glob(os.path.join(results_dir, "**", "TEST-*.xml"), recursive=True)):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            failed.append((os.path.basename(path), "(unreadable result file)"))
            continue
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        seconds += float(suite.get("time", 0) or 0)
        for case in suite.findall("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                message = (problem.get("message") or "").strip().splitlines()
                failed.append((f"{case.get('classname')}.{case.get('name')}",
                               message[0][:200] if message else ""))

    status = "passed" if failures == errors == 0 and tests > 0 else "FAILED"
    print(f"## Tests {status}\n")
    print("| Tests | Failed | Errors | Skipped | Test time |")
    print("|---:|---:|---:|---:|---:|")
    print(f"| {tests} | {failures} | {errors} | {skipped} | {seconds / 60:.1f} min |")
    percent = coverage(sys.argv[2] if len(sys.argv) > 2 else None)
    if percent is not None:
        print(f"\nLine coverage: **{percent:.1f} %**")
    if failed:
        print("\n### Failed tests\n")
        print("| Test | Message |")
        print("|---|---|")
        for name, message in failed[:100]:
            escaped = message.replace("|", "\\|")
            print(f"| `{name}` | {escaped} |")
        if len(failed) > 100:
            print(f"\n… and {len(failed) - 100} more.")


if __name__ == "__main__":
    main()
