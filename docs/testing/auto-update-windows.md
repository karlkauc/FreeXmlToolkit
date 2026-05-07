# Manual Testing Guide: Windows Auto-Update

This guide covers the manual test scenarios that must pass before tagging
any release that includes auto-update changes.

## Prerequisites

- Windows 10/11 VM
- Two release artifacts:
  - **From** version: a previously released MSI/EXE installer (e.g. v1.8.0)
  - **To** version: a newly built MSI/EXE installer (e.g. v1.9.0 from this branch)
- Internet access (to fetch update metadata from GitHub)

## Mandatory Tests

### T1 — User-writable install (no UAC)

**Setup:**
1. Install **From** version to `C:\Users\<you>\Apps\FreeXmlToolkit`
2. Verify the new version is published on GitHub Releases as **To** version

**Steps:**
1. Launch app → check for updates
2. Accept download prompt
3. Wait for "Update ready. Application will restart..."
4. App should exit, helper should run silently (no UAC dialog)
5. New version should launch automatically

**Pass criteria:**
- `~/fxt-update-debug.log` shows "App directory writable without elevation: true"
- `~/fxt-update-helper.log` shows "Parent process exited cleanly", "Copy completed", "UpdateHelper completed successfully (exit 0)"
- Running app shows new version in Help → About

---

### T2 — Program Files install + UAC accepted

**Setup:**
1. Install **From** version to `C:\Program Files\FreeXmlToolkit` (default)

**Steps:**
1. Launch app → check for updates
2. Accept download prompt
3. UAC dialog appears → **click Yes**
4. Helper runs as elevated process
5. New version launches

**Pass criteria:**
- `~/fxt-update-debug.log` shows "App directory writable without elevation: false"
- `~/fxt-update-debug.log` shows "Launching Rust helper via PowerShell elevation"
- Helper log exists; either at `~/fxt-update-helper.log` or at the elevated profile path
- Running app shows new version

---

### T4 — Antivirus delays JAR access (timing tolerance)

**Setup:**
1. Enable Windows Defender real-time protection
2. Install **From** version to `C:\Users\<you>\Apps\FreeXmlToolkit`

**Steps:**
1. Launch app → check for updates
2. Trigger update
3. While the helper is in cooldown, manually open Task Manager and observe MsMpEng.exe activity
4. Update should complete within 60–90 seconds

**Pass criteria:**
- Helper log may show "Copy locked (attempt 1)" / "Copy locked (attempt 2)" entries (AV scan in progress)
- Eventually shows "Copy completed" — total time ≤ 90s
- `UpdateHelper completed successfully (exit 0)`

---

## Optional Tests

### T3 — UAC declined

**Setup:** Program Files install

**Steps:**
1. Trigger update → UAC dialog → **click No**

**Pass criteria:**
- Old version continues running (does not exit)
- Helper never starts (no `~/fxt-update-helper.log` entry)
- Update can be retried

---

### T5 — PID recycling defense (synthetic)

**Setup:** Code modification only — temporarily change `parent_creation_time` in `writeRustHelperConfig` to `0L` (an invalid value).

**Steps:**
1. Trigger update
2. Helper should detect creation_time mismatch and proceed without waiting

**Pass criteria:**
- Helper log shows "PID ... appears recycled (creation time 0 != expected ...); treating as exited"
- Update proceeds anyway

> **Cleanup:** revert the temporary change before committing.

---

### T6 — Helper deleted by AV (extracted fallback)

**Setup:**
1. Install **From** version to `C:\Users\<you>\Apps\FreeXmlToolkit`
2. Manually delete `C:\Users\<you>\Apps\FreeXmlToolkit\fxt-update-helper.exe`

**Steps:**
1. Trigger update

**Pass criteria:**
- Debug log shows "Helper source (extracted fallback)"
- Update completes successfully using the extracted helper

---

## Reporting Failures

If any mandatory test fails, attach:
1. Full content of `~/fxt-update-debug.log`
2. Full content of `~/fxt-update-helper.log`
3. Output of `tasklist /v | findstr FreeXml` immediately after the failure
4. Windows Defender event log if an AV-related lock is suspected
