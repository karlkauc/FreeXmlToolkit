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
  Locked files: each is retried with a doubling delay capped at 8 s, 24 retries (≈ 3 min,
  `copy::MAX_RETRY_DELAY_MS`, `default_copy_retry_max`) — a virus scanner holds fresh files that
  long, and giving up leaves a half-copied install running the old version. Every run writes one
  `RESULT <code> [folder=<top folder>]` line to `~/fxt-update-helper.log` (`src/result.rs`; before
  the restart, so the new process can read it); on `version_mismatch` the next start reports it as
  `meta.helper_result` / `helper_folder` (`service/update/HelperResult`, `UpdateFunnel.reportInstalled`).
  After the copy the helper calls `SHChangeNotify(SHCNE_ASSOCCHANGED)` and starts `ie4uinit.exe -show`
  (`src/icons.rs`, best effort) so pins, Start menu and associated files show a changed icon at once;
  `WindowsAssociationStrategy` does the same after registering / removing file types.
- **macOS:** `service/update/MacBundleUpdater` — the update ZIP holds the signed
  `FreeXmlToolkit.app` plus an extras folder `FreeXmlToolkit/`. Extracted with `ditto`
  (ZipInputStream loses the runtime's symlinks), the new bundle is staged next to the
  installed one and swapped in by two renames (old bundle restored if the second fails),
  then started with `open -n`. Never copy files into the bundle — it breaks the code
  signature. Before 2.5.2 the macOS self-update never worked (manual install needed once).
  **No process from the JVM after the swap:** macOS starts processes through
  `$JAVA_HOME/lib/jspawnhelper`, which refuses another Java build ("Incorrect Java version"),
  and after the swap that path is the new runtime. So a `/bin/sh` relaunch helper
  (`RELAUNCH_SCRIPT`, injectable `Launcher`) is started before the swap and waits for
  `<extracted>/.relaunch.go` (then extras `ditto`, `touch` + `lsregister -f` of the bundle so Finder /
  Dock drop the cached icon, `open -n`) or `.abort`; only when it
  cannot start does the old in-process `open` run, and its failure says "installed".
- **Linux:** No separate helper — `AutoUpdateServiceImpl` performs
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
