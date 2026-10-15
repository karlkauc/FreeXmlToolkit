---
paths:
  - "src/main/java/org/fxt/freexmltoolkit/service/*Update*"
  - "src/test/java/org/fxt/freexmltoolkit/service/*Update*"
  - "src/main/java/org/fxt/freexmltoolkit/domain/UpdateInfo*"
  - "update-helper/**"
---

# Auto-Update Subsystem

The `AutoUpdateServiceImpl` orchestrates downloads and platform-specific
updater dispatch:

- **Windows:** Native Rust helper (`update-helper/` crate, ~500 KB binary)
  is bundled into the app-image at `<install>/fxt-update-helper.exe`.
  At update time, the helper is copied to `%TEMP%`, launched from there,
  and the install directory becomes free for overwrite. Since v2.0.1 the
  helper embeds an `asInvoker` manifest (`update-helper/manifest.xml`) so
  UAC installer detection cannot demand elevation (CreateProcess error
  740); if a policy forces elevation anyway, the Java side retries via
  `Start-Process -Verb RunAs`. The helper from the downloaded payload is
  preferred over the installed copy. See
  `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md`.
  **Updater fixes are never retroactive** — the N→N+1 update runs version
  N's Java code, so users with a broken updater need one manual install.
- **Mac/Linux:** No separate helper — `AutoUpdateServiceImpl` performs
  in-process recursive copy then exec's the new launcher (POSIX inode
  semantics allow overwriting files of running processes).

## Package-manager installs (no self-update)

`service/update/InstallSourceDetector` classifies the install as `FLATPAK`, `SCOOP`,
`HOMEBREW`, `WINGET` or `STANDALONE` (`InstallSource`). Only `STANDALONE` may self-update:
`AutoUpdateServiceImpl.downloadAndApplyUpdate` refuses managed installs, the
`UpdateNotificationDialog` replaces "Download & Install" with the package manager's upgrade
command, and Flatpak disables the update check. `InstallSourceDetector.current()` may start
`winget list` (≤5 s) — it is warmed in `UpdateCheckServiceImpl.checkForUpdates` (background);
FX-thread code uses `peek()`. Distribution setup: `packaging/README.md`.
