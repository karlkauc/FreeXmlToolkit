# Windows Auto-Update Redesign — Native Rust Helper

**Date:** 2026-05-07
**Status:** Design — pending implementation
**Target version:** v1.9.0
**Affected platforms:** Windows (Mac/Linux receive a smaller cleanup)

---

## 1. Background

### 1.1 The bug

The Windows auto-updater has failed reliably in every release ≤ v1.8.0. Anwender-Logs show the same failure mode in every attempt since the helper architecture was introduced (`cc14a938`, March 2026):

```
Helper: Application process not found  (after ~4s)
Helper: Cooldown completed (5s)
Helper: Copy failed (attempt 1): app/FreeXmlToolkit.jar — used by another process
... 10 retries × 2s ...
Helper: UpdateHelper failed
```

### 1.2 Root cause

The current `UpdateHelper.exe` is built as a jpackage `--add-launcher` of the main app. This means it shares the **same `app/FreeXmlToolkit.jar`** as classpath. When `UpdateHelper.exe` runs:

1. It launches `<install>/runtime/bin/java.exe`
2. Java opens `<install>/app/FreeXmlToolkit.jar` as classpath
3. `UpdateHelperMain.main()` then attempts `Files.copy(extracted_jar, <install>/app/FreeXmlToolkit.jar)`
4. Windows refuses: the target is locked by the helper's own JVM

The helper saws the branch it sits on. The 10-retry exponential-backoff is academic — the lock will never release because the helper itself holds it.

A partial fix in `7befff69` prefers the extracted helper, which uses the extracted JAR as classpath instead of the installed one. This was released only in v1.8.0 and is unproven in real-world use. It also doesn't solve the architectural fragility: the helper still depends on JVM, classpath, and shared jpackage runtime.

### 1.3 Failed-fix history

16+ commits over 8 months attempted to fix this in pieces:
`bb0c485a`, `62a2ac7c`, `0513a324`, `42a6e64f`, `6c75af7b`, `0d627321`, `7c940549`, `a3687931`, `ee26152a`, `cc14a938`, `d1681b6a`, `05603810`, `9b5ed891`, `9ce8bb3b`, `7befff69`. Per the systematic-debugging skill (Phase 4.5): 3+ failed fixes → architectural problem. We replace the architecture rather than attempt fix #11.

---

## 2. Decisions taken during brainstorming

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Helper exists **only on Windows**. Mac/Linux: in-process copy from main app before exit. | POSIX inode-replace allows overwriting files of running processes. Avoiding helper code on those platforms removes ~80 LoC of irrelevant complexity. |
| D2 | Windows helper is a **native Rust binary**, ~500 KB. | No JVM, no classpath, no shared file with main app. The class of bugs that produced the v1.7.0 failure becomes structurally impossible. |
| D3 | Helper executes from `%TEMP%`, not from `<install>/`. The main app **copies its own helper to `%TEMP%` before launching it**. | Frees the entire install directory (including the helper EXE itself) for being overwritten. No Windows file lock can exist on the install dir. |
| D4 | No in-app UI banner about migration. | User-decided. Release notes carry the migration warning. |
| D5 | Helper is bundled via jpackage `--app-content` flag, not `--add-launcher`. | `--app-content` injects an arbitrary file into the resulting EXE/MSI/ZIP root without entangling it with jpackage's launcher mechanics. |

---

## 3. Architecture

### 3.1 Process flow (Windows)

```
Main App (Java + jpackage, unchanged)
 │
 ├─ Detect update (existing UpdateCheckService)
 ├─ Download ZIP (existing AutoUpdateServiceImpl.downloadFile)
 ├─ Extract to %TEMP%/fxt-update-XXX/extracted/  (existing extractZip)
 │
 ├─ NEW: copy <install>/fxt-update-helper.exe
 │       → %TEMP%/fxt-helper-<pid>-<rand>.exe
 ├─ NEW: write %TEMP%/fxt-update-XXX/helper-config.toml
 ├─ NEW: launch %TEMP%/fxt-helper-<pid>-<rand>.exe <config>
 │       (UAC via PowerShell Start-Process -Verb RunAs if not writable)
 └─ System.exit(0)

Helper (Rust binary, ~500 KB, no JVM)
 │
 ├─ Parse argv[1] as TOML config path
 ├─ Validate schema_version == 1
 ├─ Open log file (append)
 ├─ wait_for_parent_exit(parent_pid, parent_creation_time, 120s)
 │     via Win32 OpenProcess + WaitForSingleObject (race-free)
 ├─ Sleep 3s cooldown
 ├─ Recursive copy extracted_dir/FreeXmlToolkit/* → install_dir/*
 │     with exponential retry on lock errors (1+2+4+8+16+32 = 63s max)
 ├─ CreateProcess(launcher_path, DETACHED_PROCESS) for new app
 ├─ Best-effort delete of extracted_dir
 └─ exit 0
```

### 3.2 Process flow (Mac/Linux)

```
Main App
 │
 ├─ Detect update, download, extract  (existing flow)
 │
 ├─ NEW: in-process copy
 │       extracted_dir/FreeXmlToolkit/* → install_dir/*
 │       (POSIX inode swap — running JVM keeps its old inode)
 ├─ NEW: ProcessBuilder(launcher).start()  for new app
 └─ Platform.exit()
```

No helper process. ~80 LoC of Java helper code deleted.

### 3.3 Why the bug class disappears

The helper has no shared file with either the old or new install:
- No JAR (no classpath)
- No JVM (no `runtime/bin/java.exe`)
- No EXE in install dir at runtime (it ran from `%TEMP%`)

Windows lock semantics: a lock exists when a file is open by some process. The helper opens nothing in the install dir for read. It only opens files there for write (the copy targets). Writing creates the lock for the duration of the write. There is no overlap that could make a target also be a source-of-our-own-lock.

---

## 4. Components

### 4.1 Repo structure

```
FreeXmlToolkit/
├── update-helper/                   ← NEW: Rust crate
│   ├── Cargo.toml
│   ├── Cargo.lock
│   ├── src/
│   │   └── main.rs                  ~250 LoC
│   └── README.md
├── build.gradle.kts                 MODIFIED
├── .github/workflows/
│   └── build-packages-on-new-release.yml  MODIFIED
└── src/main/java/...
    ├── service/AutoUpdateServiceImpl.java  MODIFIED (-200/+80 LoC)
    └── service/update/
        ├── UpdateHelperMain.java    DELETED (-260 LoC)
        └── UpdateHelperConfig.java  DELETED (-84 LoC)
```

Net Java: −264 LoC. Plus ~250 LoC Rust.

### 4.2 Cargo.toml (size-optimized)

```toml
[package]
name = "fxt-update-helper"
version = "1.0.0"
edition = "2021"

[dependencies]
serde = { version = "1.0", features = ["derive"] }
toml = "0.8"
windows-sys = { version = "0.59", features = [
    "Win32_Foundation",
    "Win32_System_Threading",
    "Win32_System_ProcessStatus",
    "Win32_Storage_FileSystem"
] }

[profile.release]
opt-level = "z"        # optimize for size
lto = true             # link-time optimization
codegen-units = 1
strip = "symbols"
panic = "abort"        # no unwinding code
```

Expected output: `target/release/fxt-update-helper.exe` ≈ 500 KB.

### 4.3 Rust modules (within `src/main.rs`)

| Module | Responsibility |
|--------|---------------|
| `config` | Parse + validate TOML config. Reject `schema_version > 1`. Path canonicalization. |
| `logger` | Append-only log file. Flush after every write. ISO-timestamp + level. |
| `process` | `OpenProcess` + `GetProcessTimes` + `WaitForSingleObject`. PID-recycling defense. |
| `copy` | Recursive directory copy. Exponential-backoff retry on `ERROR_SHARING_VIOLATION`/`ERROR_LOCK_VIOLATION`/`ERROR_ACCESS_DENIED`. |
| `restart` | `CreateProcess` with `DETACHED_PROCESS` flag, no inherited handles. |
| `cleanup` | Best-effort `remove_dir_all` of extracted_dir. |

### 4.4 Configuration schema (`helper-config.toml`)

```toml
schema_version = 1                    # MUST match helper's expected version

# Process synchronization
parent_pid = 12345
parent_creation_time = 133742069420   # FILETIME, defends against PID recycling

# Paths (Windows-style, single-quoted to avoid escape issues)
extracted_dir = 'C:\Users\j13hkpb\AppData\Local\Temp\fxt-update-XXX\extracted'
install_dir = 'C:\Data\Apps\FreeXmlToolkit'
launcher_path = 'C:\Data\Apps\FreeXmlToolkit\FreeXmlToolkit.exe'
log_path = 'C:\Users\j13hkpb\fxt-update-helper.log'

# Versions (sanity check + log context)
old_version = '1.9.0'
new_version = '1.10.0'

# Tunables (optional; helper has defaults)
parent_wait_timeout_seconds = 120
post_exit_cooldown_seconds = 3
copy_retry_max = 6
copy_retry_initial_ms = 1000
```

The Java side (`AutoUpdateServiceImpl`) writes this file. Rust reads it. Both sides agree on `schema_version = 1` for this release.

### 4.5 Process synchronization — the key fix

```rust
fn wait_for_parent_exit(parent_pid: u32,
                        expected_creation: i64,
                        timeout: Duration) -> Result<()> {
    let handle = unsafe {
        OpenProcess(SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION,
                    FALSE, parent_pid)
    };
    if handle.is_null() {
        // Parent already gone — proceed
        return Ok(());
    }

    // PID-recycling defense: verify creation time matches
    let actual_creation = get_process_creation_time(handle)?;
    if actual_creation != expected_creation {
        unsafe { CloseHandle(handle) };
        return Ok(());  // PID was recycled to a different process
    }

    let result = unsafe {
        WaitForSingleObject(handle, timeout.as_millis() as u32)
    };
    unsafe { CloseHandle(handle) };

    match result {
        WAIT_OBJECT_0 => Ok(()),
        WAIT_TIMEOUT  => Err(Error::ParentTimeout),
        _             => Err(Error::WaitFailed),
    }
}
```

Replaces the broken Java heuristic that scanned all processes for "freexmltoolkit" in `commandLine`. Race-free, recycle-safe, O(1).

### 4.6 Copy retry strategy

```rust
fn copy_with_retry(src: &Path, dst: &Path, log: &Logger) -> Result<()> {
    let delays_ms = [1_000, 2_000, 4_000, 8_000, 16_000, 32_000];
    for (attempt, delay) in delays_ms.iter().enumerate() {
        match fs::copy(src, dst) {
            Ok(_) => return Ok(()),
            Err(e) if is_lock_error(&e) => {
                log.warn(&format!("Copy locked (attempt {}): {} ({})",
                                  attempt + 1, dst.display(), e));
                thread::sleep(Duration::from_millis(*delay));
            }
            Err(e) => return Err(e.into()),  // non-lock errors fatal
        }
    }
    Err(Error::CopyExhausted(dst.to_path_buf()))
}

fn is_lock_error(e: &io::Error) -> bool {
    matches!(e.raw_os_error(),
        Some(32) | Some(33) | Some(5))
        // ERROR_SHARING_VIOLATION / LOCK_VIOLATION / ACCESS_DENIED
}
```

Total max wait per file: 1 + 2 + 4 + 8 + 16 + 32 = 63 seconds. Tolerates real-world AV scans (typically 1–10s) with significant headroom.

### 4.7 Exit codes

| Code | Meaning |
|------|---------|
| 0 | Update successful, new app launched |
| 1 | Generic failure (see log) |
| 2 | Schema-version mismatch (helper too old for config) |
| 3 | Parent-wait timeout (>120s) |
| 4 | Copy exhausted retries |
| 5 | Restart failed (launcher not found / not executable) |

### 4.8 Java-side changes (`AutoUpdateServiceImpl`)

**Reduced**, not extended:

```java
private boolean launchUpdater_Windows(Path extractedDir, Path debugLog) {
    Path installedHelper = installDir.resolve("fxt-update-helper.exe");
    if (!Files.exists(installedHelper)) {
        installedHelper = extractedDir.resolve(
            "FreeXmlToolkit/fxt-update-helper.exe");  // fallback
    }

    Path tempHelper = Files.createTempFile("fxt-helper-", ".exe");
    Files.copy(installedHelper, tempHelper, REPLACE_EXISTING);

    Path configFile = extractedDir.resolve("helper-config.toml");
    writeHelperConfig(configFile, ...);

    boolean writable = isAppDirectoryWritable(installDir);
    ProcessBuilder pb = writable
        ? new ProcessBuilder(tempHelper.toString(), configFile.toString())
        : new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
            "Start-Process -FilePath '" + tempHelper +
            "' -ArgumentList '" + configFile + "' -Verb RunAs");
    pb.start();
    return true;
}

private boolean launchUpdater_Posix(Path extractedDir, Path debugLog) {
    copyTreeReplaceExisting(
        extractedDir.resolve("FreeXmlToolkit"), installDir);
    new ProcessBuilder(launcher.toString()).start();
    return true;
}
```

**Deleted entirely:**
- `UpdateHelperMain.java`
- `UpdateHelperConfig.java`
- `--add-launcher UpdateHelper=…` in `build.gradle.kts`
- `findHelperInUpdate(...)`, `getUpdateHelperLauncher(...)`, `getUpdateHelperExecutableName(...)`

---

## 5. Build & CI

### 5.1 Gradle integration

```kotlin
val buildWindowsUpdateHelper = tasks.register<Exec>("buildWindowsUpdateHelper") {
    group = "distribution"
    description = "Compile native Rust UpdateHelper for Windows"
    workingDir = file("update-helper")
    commandLine("cargo", "build", "--release")
    onlyIf {
        System.getProperty("os.name").lowercase().contains("windows")
    }
    inputs.dir("update-helper/src")
    inputs.file("update-helper/Cargo.toml")
    outputs.file("update-helper/target/release/fxt-update-helper.exe")
}
```

The Windows jpackage tasks (`createWindowsExecutableX64`, `createWindowsMsiX64`, `createWindowsAppImageX64`) gain:
- `dependsOn("buildWindowsUpdateHelper")`
- jpackage args change: remove `--add-launcher UpdateHelper=…`, add `--app-content <rustHelperPath>`

The Rust EXE ends up at `<install>/fxt-update-helper.exe` for app-image, EXE-installer, and MSI alike. `--app-content` is jpackage's documented way to inject arbitrary files.

### 5.2 GitHub Actions changes

Before all Windows jobs in `.github/workflows/build-packages-on-new-release.yml`:

```yaml
- name: Setup Rust toolchain (Windows only)
  if: matrix.os == 'windows-latest'
  uses: dtolnay/rust-toolchain@stable

- name: Cache Rust artifacts (Windows only)
  if: matrix.os == 'windows-latest'
  uses: Swatinem/rust-cache@v2
  with:
    workspaces: update-helper
```

No changes needed for Mac/Linux jobs.

### 5.3 Build verification

A `doLast` block in the Windows app-image task asserts the helper made it into the package:

```kotlin
doLast {
    val expected = file("$sourceDir/FreeXmlToolkit/fxt-update-helper.exe")
    if (!expected.exists()) {
        throw GradleException("Build inconsistent: missing $expected")
    }
}
```

Catches the failure mode where `--app-content` silently fails or a build-script bug omits the helper.

---

## 6. Testing

### 6.1 Rust unit tests (`cargo test`)

| Module | Tests |
|--------|-------|
| `config` | Valid TOML parses; missing field rejected; `schema_version > 1` rejected; path expansion |
| `copy` | Retry schema with mocked errno; `is_lock_error` matrix for Win32 codes; recursive copy with fixture tree |
| `process` | `get_creation_time` on own process (sanity); wait timeout behavior |
| `restart` | `DETACHED_PROCESS` flags set correctly (mock); cmdline escaping |

Runs on every Windows CI build before jpackage.

### 6.2 Java unit tests (`./gradlew test`)

| Test | Verifies |
|------|----------|
| `AutoUpdateServiceImplTest.writeHelperConfig` | TOML output matches Rust schema (round-trip via helper's `--validate-config` mode) |
| `AutoUpdateServiceImplTest.helperResolution` | Install preferred over extract; fallback reverse |
| `AutoUpdateServiceImplTest.posixDirectCopy` | Mac/Linux path: files overwritten correctly (TempDir fixtures) |

### 6.3 Manual integration tests (Windows VM)

Documented in `docs/testing/auto-update-windows.md`. Mandatory before release:

| ID | Scenario | Install path | Expected | Verification |
|----|----------|-------------|----------|--------------|
| T1 | User-writable install | `C:\Data\Apps` | Direct launch (no UAC), success | New version runs |
| T2 | Program Files + UAC accepted | `C:\Program Files` | UAC prompt, success | New version runs |
| T4 | AV blocks JAR temporarily | `C:\Data\Apps` + Defender | Retries succeed in ≤63s | New version runs |

Optional / on-demand:

| ID | Scenario | Verification |
|----|----------|--------------|
| T3 | UAC declined | Old version continues to run; clear log entry |
| T5 | PID recycling (synthetic) | Helper proceeds via creation_time mismatch path |
| T6 | Helper.exe deleted by AV | Fallback to extracted helper succeeds |

---

## 7. Migration & rollout

### 7.1 Compatibility matrix

| User version | Auto-update to v1.9.0 | Recommendation |
|--------------|----------------------|----------------|
| ≤ v1.7.0 | **Will fail** (legacy bug) | Manual install of MSI/EXE — once |
| v1.8.0 | Should work (Java fix `7befff69`) | Try auto; manual fallback |
| ≥ v1.9.0 | **Works** (Rust helper) | Auto-update routine |

### 7.2 Release-notes addition for v1.9.0

```markdown
## ⚠️ Important Update Note

This release introduces a redesigned Windows auto-update mechanism.

- **Users on v1.7.0 or earlier**: Auto-update will not work due to a
  known bug in those versions. Please download the .msi or .exe
  installer manually from the Releases page and install once.
  Subsequent updates will work automatically.

- **Users on v1.8.0**: Auto-update should work. If it doesn't,
  manual install is recommended.

- **Users on v1.9.0 or later**: All future updates use the new
  reliable mechanism.
```

### 7.3 Pre-release verification

Before tagging v1.9.0:

1. Fresh-install v1.7.0 in a Windows VM, attempt auto-update to v1.9.0 → must fail predictably (validates the migration narrative)
2. Fresh-install v1.8.0, attempt auto-update to v1.9.0 → must succeed (validates 7befff69 in real)
3. Fresh-install v1.9.0, release a test-tag v1.9.1, attempt auto-update → must succeed (validates the new architecture)

If step 2 fails, the release notes' v1.8.0 promise becomes false; revise wording.

---

## 8. Known limitations (deliberate non-goals for v1.0)

| # | Edge case | Behavior | Future mitigation |
|---|-----------|----------|---------------------|
| L1 | Disk full mid-copy | Helper aborts, install half-overwritten, logged | v1.1: pre-flight `available_space ≥ extracted_size × 1.2` |
| L2 | AV deletes a file mid-copy | Partial update, install broken, user must reinstall | v1.1: backup-before-copy pattern |
| L3 | AV quarantines new helper.exe or JAR after copy | Update appears successful, app fails to start next launch | Code-signing (separate workstream) |
| L4 | SmartScreen warns on unsigned helper.exe | UAC dialog says "unknown publisher"; user clicks through | Code-signing |
| L5 | Network/UNC install path | Probably works; not tested | Test on demand if reported |
| L6 | Junction points in install dir | `fs::copy` follows symlinks; could be surprising | Test in T1 with/without junction |
| L7 | PID recycling sub-second window | `creation_time` check handles it | Already solved (§4.5) |
| L8 | Rollback on failure | None — helper aborts, leaves partial state, user reinstalls | If frequent in practice: backup pattern |

YAGNI: backup, pre-flight disk check, and code-signing are postponed to follow-up work. Bug reports drive future iteration.

---

## 9. Out of scope

- **Mac/Linux auto-update refactor** beyond the in-process direct-copy simplification described in §3.2 and §4.8.
- **MSI-based update via `msiexec /i /quiet`**: considered as Option B during brainstorming; not chosen because the Rust approach is equally robust and avoids Windows Installer quirks (registry, MSI database, transforms).
- **In-app banner for failed updates**: explicit user decision against.
- **Differential / delta updates**: full app downloaded as today (~180 MB). Separate optimization, separate plan.

---

## 10. Approval gate

Before proceeding to a writing-plans implementation plan, the user reviews this spec for accuracy, completeness, and any missed concerns.
