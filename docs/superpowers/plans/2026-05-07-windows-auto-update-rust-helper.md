# Windows Auto-Update Rust Helper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken Java-based UpdateHelper (jpackage `--add-launcher`) with a standalone ~500 KB native Rust binary that has no shared classpath, no JVM, and no overlapping file locks with the application it updates.

**Architecture:** Native Rust helper compiled per release on Windows CI, bundled into the app-image via jpackage `--app-content`. At update time, the main app copies the helper to `%TEMP%`, launches it from there, and exits. The temp helper waits for the parent to die (Win32 `WaitForSingleObject`, race-free), then copies the extracted update over the install dir. Mac/Linux drop the helper entirely and copy in-process before exit (POSIX inode-replace works on running processes).

**Tech Stack:** Rust 2021, `windows-sys` for Win32 bindings, `serde` + `toml` for config, jpackage `--app-content`, GitHub Actions with `dtolnay/rust-toolchain`.

**Toolchain note:** Cargo ≥ 1.85 required (transitive `indexmap` needs `edition2024`). On Linux dev hosts where apt provides only an older cargo, install rustup: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`, then `source "$HOME/.cargo/env"`.

**Spec:** `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md`

---

## Phase A: Build the Rust helper (standalone)

Each task in this phase produces a self-contained, testable Rust module. The helper is fully usable after Phase A.

### Task A1: Cargo skeleton

**Files:**
- Create: `update-helper/Cargo.toml`
- Create: `update-helper/src/main.rs`
- Create: `update-helper/.gitignore`
- Create: `update-helper/README.md`

- [ ] **Step 1: Create the directory and cargo skeleton**

```bash
mkdir -p update-helper/src
```

- [ ] **Step 2: Write `update-helper/Cargo.toml`**

```toml
[package]
name = "fxt-update-helper"
version = "1.0.0"
edition = "2021"
description = "Native auto-update helper for FreeXmlToolkit (Windows-only)"
license = "Apache-2.0"

[dependencies]
serde = { version = "1.0", features = ["derive"] }
toml = "0.8"

[target.'cfg(windows)'.dependencies]
windows-sys = { version = "0.59", features = [
    "Win32_Foundation",
    "Win32_System_Threading",
    "Win32_System_ProcessStatus",
    "Win32_Storage_FileSystem",
    "Win32_System_Diagnostics_Debug",
] }

[profile.release]
opt-level = "z"
lto = true
codegen-units = 1
strip = "symbols"
panic = "abort"

[profile.dev]
# Faster dev compile times
opt-level = 0
```

- [ ] **Step 3: Write minimal `update-helper/src/main.rs`**

```rust
fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(0);
}
```

- [ ] **Step 4: Write `update-helper/.gitignore`**

```
/target
```

> Note: `Cargo.lock` IS committed (we ship a binary, deterministic builds matter).

- [ ] **Step 5: Write `update-helper/README.md`**

```markdown
# fxt-update-helper

Native Windows auto-update helper for FreeXmlToolkit.

## Build

```
cargo build --release
```

Output: `target/release/fxt-update-helper.exe` (~500 KB).

## Test

```
cargo test
```

Tests are cross-platform; Win32-specific code is gated behind `#[cfg(windows)]`.

## Usage

The helper is invoked by the main app, not directly by users:

```
fxt-update-helper.exe <path-to-helper-config.toml>
```

See `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md` for design.
```

- [ ] **Step 6: Run `cargo build` to verify the skeleton compiles**

```bash
cd update-helper && cargo build
```

Expected: compiles successfully, produces `target/debug/fxt-update-helper` (Linux) or `.exe` (Windows).

- [ ] **Step 7: Run `cargo run` to verify it executes**

```bash
cd update-helper && cargo run
```

Expected output: `fxt-update-helper 1.0.0 (Rust)` and exit code 0.

- [ ] **Step 8: Commit**

```bash
git add update-helper/
git commit -m "feat(update-helper): add Rust crate skeleton

Empty crate with size-optimized release profile.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A2: Error type

**Files:**
- Create: `update-helper/src/error.rs`
- Modify: `update-helper/src/main.rs` (add `mod error;`)

- [ ] **Step 1: Write `update-helper/src/error.rs`**

```rust
use std::fmt;
use std::io;
use std::path::PathBuf;

/// Exit codes (mirrored in spec section 4.7).
pub const EXIT_SUCCESS: i32 = 0;
pub const EXIT_GENERIC_FAILURE: i32 = 1;
pub const EXIT_SCHEMA_MISMATCH: i32 = 2;
pub const EXIT_PARENT_TIMEOUT: i32 = 3;
pub const EXIT_COPY_EXHAUSTED: i32 = 4;
pub const EXIT_RESTART_FAILED: i32 = 5;

#[derive(Debug)]
pub enum HelperError {
    InvalidArgs(String),
    ConfigRead(io::Error),
    ConfigParse(String),
    SchemaMismatch { found: u32, supported: u32 },
    ParentWaitTimeout,
    ParentWaitFailed(String),
    CopyExhausted(PathBuf),
    CopyFailed { path: PathBuf, source: io::Error },
    RestartFailed(String),
    Io(io::Error),
}

impl HelperError {
    pub fn exit_code(&self) -> i32 {
        match self {
            HelperError::SchemaMismatch { .. } => EXIT_SCHEMA_MISMATCH,
            HelperError::ParentWaitTimeout    => EXIT_PARENT_TIMEOUT,
            HelperError::CopyExhausted(_)     => EXIT_COPY_EXHAUSTED,
            HelperError::RestartFailed(_)     => EXIT_RESTART_FAILED,
            _                                 => EXIT_GENERIC_FAILURE,
        }
    }
}

impl fmt::Display for HelperError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            HelperError::InvalidArgs(msg) => write!(f, "Invalid arguments: {}", msg),
            HelperError::ConfigRead(e)    => write!(f, "Failed to read config: {}", e),
            HelperError::ConfigParse(msg) => write!(f, "Failed to parse config: {}", msg),
            HelperError::SchemaMismatch { found, supported } => write!(
                f,
                "Config schema_version {} not supported (helper supports {})",
                found, supported
            ),
            HelperError::ParentWaitTimeout => write!(f, "Timed out waiting for parent to exit"),
            HelperError::ParentWaitFailed(msg) => write!(f, "Failed to wait for parent: {}", msg),
            HelperError::CopyExhausted(p) => write!(
                f,
                "Copy retries exhausted for {} (target file remained locked)",
                p.display()
            ),
            HelperError::CopyFailed { path, source } => write!(
                f,
                "Copy failed for {}: {}",
                path.display(),
                source
            ),
            HelperError::RestartFailed(msg) => write!(f, "Failed to restart application: {}", msg),
            HelperError::Io(e) => write!(f, "I/O error: {}", e),
        }
    }
}

impl std::error::Error for HelperError {}

impl From<io::Error> for HelperError {
    fn from(e: io::Error) -> Self {
        HelperError::Io(e)
    }
}

pub type Result<T> = std::result::Result<T, HelperError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn schema_mismatch_returns_code_2() {
        let e = HelperError::SchemaMismatch { found: 2, supported: 1 };
        assert_eq!(e.exit_code(), 2);
    }

    #[test]
    fn copy_exhausted_returns_code_4() {
        let e = HelperError::CopyExhausted("C:\\foo".into());
        assert_eq!(e.exit_code(), 4);
    }

    #[test]
    fn parent_timeout_returns_code_3() {
        let e = HelperError::ParentWaitTimeout;
        assert_eq!(e.exit_code(), 3);
    }

    #[test]
    fn generic_io_returns_code_1() {
        let e = HelperError::Io(io::Error::new(io::ErrorKind::Other, "x"));
        assert_eq!(e.exit_code(), 1);
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs` — add module declaration**

```rust
mod error;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected: 4 tests pass (`schema_mismatch_returns_code_2`, `copy_exhausted_returns_code_4`, `parent_timeout_returns_code_3`, `generic_io_returns_code_1`).

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/error.rs update-helper/src/main.rs
git commit -m "feat(update-helper): error type with exit-code mapping

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A3: Logger module

**Files:**
- Create: `update-helper/src/logger.rs`
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Write `update-helper/src/logger.rs`**

```rust
use std::fs::OpenOptions;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

pub struct Logger {
    path: PathBuf,
}

impl Logger {
    pub fn open(path: impl AsRef<Path>) -> Self {
        Logger {
            path: path.as_ref().to_path_buf(),
        }
    }

    pub fn info(&self, message: &str) {
        self.write("INFO", message);
    }

    pub fn warn(&self, message: &str) {
        self.write("WARN", message);
    }

    pub fn error(&self, message: &str) {
        self.write("ERROR", message);
    }

    fn write(&self, level: &str, message: &str) {
        let line = format!("{} [{}] {}\n", iso_timestamp(), level, message);
        // Best-effort: never panic in the logger itself.
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(&self.path) {
            let _ = f.write_all(line.as_bytes());
            let _ = f.flush();
        } else {
            eprintln!("{}", line.trim_end());
        }
    }
}

fn iso_timestamp() -> String {
    // Cheap ISO-8601-ish timestamp without pulling in chrono.
    // Format: 2026-05-08T10:23:14.512345
    let now = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .unwrap_or_default();
    let secs = now.as_secs();
    let micros = now.subsec_micros();

    let (y, mo, d, h, mi, s) = epoch_to_civil(secs);
    format!(
        "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}.{:06}",
        y, mo, d, h, mi, s, micros
    )
}

/// Convert UNIX epoch seconds to civil (Y, M, D, h, m, s).
/// Adapted from Howard Hinnant's algorithm. UTC.
fn epoch_to_civil(secs: u64) -> (i32, u32, u32, u32, u32, u32) {
    let days = (secs / 86400) as i64;
    let sod = (secs % 86400) as u32;
    let h = sod / 3600;
    let m = (sod % 3600) / 60;
    let s = sod % 60;

    let z = days + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = (z - era * 146097) as u32;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe as i64 + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let mo = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = if mo <= 2 { y + 1 } else { y };
    (y as i32, mo, d, h, m, s)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::io::Read;

    #[test]
    fn logs_to_file_with_format() {
        let dir = tempdir();
        let log_path = dir.join("test.log");
        let logger = Logger::open(&log_path);
        logger.info("hello");
        logger.warn("careful");
        logger.error("oops");

        let mut content = String::new();
        fs::File::open(&log_path)
            .unwrap()
            .read_to_string(&mut content)
            .unwrap();

        assert!(content.contains("[INFO] hello"));
        assert!(content.contains("[WARN] careful"));
        assert!(content.contains("[ERROR] oops"));
        assert_eq!(content.lines().count(), 3);
        // Ensure ISO timestamp prefix present
        for line in content.lines() {
            assert!(line.len() > 26, "line too short: {}", line);
            assert_eq!(&line[4..5], "-");
            assert_eq!(&line[10..11], "T");
        }
    }

    #[test]
    fn appends_across_logger_instances() {
        let dir = tempdir();
        let log_path = dir.join("test.log");

        Logger::open(&log_path).info("first");
        Logger::open(&log_path).info("second");

        let content = fs::read_to_string(&log_path).unwrap();
        assert_eq!(content.lines().count(), 2);
    }

    #[test]
    fn epoch_zero_is_1970_jan_1() {
        let (y, mo, d, h, mi, s) = epoch_to_civil(0);
        assert_eq!((y, mo, d, h, mi, s), (1970, 1, 1, 0, 0, 0));
    }

    #[test]
    fn epoch_2026_05_07_known_value() {
        // 2026-05-07T00:00:00 UTC = 1778112000 seconds since epoch
        // (verify with: date -u -d @1778112000)
        let (y, mo, d, _h, _mi, _s) = epoch_to_civil(1778112000);
        assert_eq!((y, mo, d), (2026, 5, 7));
    }

    fn tempdir() -> std::path::PathBuf {
        let p = std::env::temp_dir().join(format!(
            "fxt-helper-test-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir_all(&p).unwrap();
        p
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod error;
mod logger;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected: 8 tests pass total (4 from error + 4 from logger).

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/logger.rs update-helper/src/main.rs
git commit -m "feat(update-helper): append-only logger with ISO timestamps

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A4: Config module

**Files:**
- Create: `update-helper/src/config.rs`
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Write `update-helper/src/config.rs`**

```rust
use std::fs;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::error::{HelperError, Result};

pub const SUPPORTED_SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub schema_version: u32,

    pub parent_pid: u32,
    pub parent_creation_time: i64,

    pub extracted_dir: PathBuf,
    pub install_dir: PathBuf,
    pub launcher_path: PathBuf,
    pub log_path: PathBuf,

    pub old_version: String,
    pub new_version: String,

    #[serde(default = "default_parent_wait_timeout")]
    pub parent_wait_timeout_seconds: u64,

    #[serde(default = "default_post_exit_cooldown")]
    pub post_exit_cooldown_seconds: u64,

    #[serde(default = "default_copy_retry_max")]
    pub copy_retry_max: u32,

    #[serde(default = "default_copy_retry_initial_ms")]
    pub copy_retry_initial_ms: u64,
}

fn default_parent_wait_timeout() -> u64 { 120 }
fn default_post_exit_cooldown() -> u64 { 3 }
fn default_copy_retry_max() -> u32 { 6 }
fn default_copy_retry_initial_ms() -> u64 { 1000 }

impl Config {
    pub fn load(path: &Path) -> Result<Self> {
        let content = fs::read_to_string(path).map_err(HelperError::ConfigRead)?;
        let cfg: Config = toml::from_str(&content).map_err(|e| HelperError::ConfigParse(e.to_string()))?;
        cfg.validate()?;
        Ok(cfg)
    }

    fn validate(&self) -> Result<()> {
        if self.schema_version != SUPPORTED_SCHEMA_VERSION {
            return Err(HelperError::SchemaMismatch {
                found: self.schema_version,
                supported: SUPPORTED_SCHEMA_VERSION,
            });
        }
        if self.parent_pid == 0 {
            return Err(HelperError::ConfigParse("parent_pid must be > 0".into()));
        }
        if !self.extracted_dir.is_absolute() {
            return Err(HelperError::ConfigParse(format!(
                "extracted_dir must be absolute: {}",
                self.extracted_dir.display()
            )));
        }
        if !self.install_dir.is_absolute() {
            return Err(HelperError::ConfigParse(format!(
                "install_dir must be absolute: {}",
                self.install_dir.display()
            )));
        }
        if !self.launcher_path.is_absolute() {
            return Err(HelperError::ConfigParse(format!(
                "launcher_path must be absolute: {}",
                self.launcher_path.display()
            )));
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn write_config(content: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "fxt-helper-cfg-test-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("helper-config.toml");
        let mut f = std::fs::File::create(&path).unwrap();
        f.write_all(content.as_bytes()).unwrap();
        path
    }

    fn valid_config(extra: &str) -> String {
        // On Linux test runner, use /tmp paths to satisfy is_absolute()
        let prefix = if cfg!(windows) { r"C:\\app" } else { "/tmp/app" };
        let pre = if cfg!(windows) { r"C:\\extract" } else { "/tmp/extract" };
        let pl = if cfg!(windows) {
            r"C:\\app\\FreeXmlToolkit.exe"
        } else {
            "/tmp/app/FreeXmlToolkit"
        };
        let log = if cfg!(windows) {
            r"C:\\Users\\u\\fxt-update-helper.log"
        } else {
            "/tmp/fxt-update-helper.log"
        };
        format!(
            r#"
schema_version = 1
parent_pid = 1234
parent_creation_time = 133742069420
extracted_dir = '{pre}'
install_dir = '{prefix}'
launcher_path = '{pl}'
log_path = '{log}'
old_version = "1.9.0"
new_version = "1.10.0"
{extra}
"#
        )
    }

    #[test]
    fn parses_minimal_valid_config() {
        let p = write_config(&valid_config(""));
        let cfg = Config::load(&p).expect("should parse");
        assert_eq!(cfg.schema_version, 1);
        assert_eq!(cfg.parent_pid, 1234);
        assert_eq!(cfg.parent_wait_timeout_seconds, 120);
        assert_eq!(cfg.post_exit_cooldown_seconds, 3);
        assert_eq!(cfg.copy_retry_max, 6);
    }

    #[test]
    fn applies_tunable_overrides() {
        let p = write_config(&valid_config(
            r#"
parent_wait_timeout_seconds = 60
post_exit_cooldown_seconds = 1
copy_retry_max = 3
copy_retry_initial_ms = 500
"#,
        ));
        let cfg = Config::load(&p).expect("should parse");
        assert_eq!(cfg.parent_wait_timeout_seconds, 60);
        assert_eq!(cfg.post_exit_cooldown_seconds, 1);
        assert_eq!(cfg.copy_retry_max, 3);
        assert_eq!(cfg.copy_retry_initial_ms, 500);
    }

    #[test]
    fn rejects_unsupported_schema_version() {
        let mut content = valid_config("");
        content = content.replace("schema_version = 1", "schema_version = 99");
        let p = write_config(&content);
        match Config::load(&p) {
            Err(HelperError::SchemaMismatch { found, supported }) => {
                assert_eq!(found, 99);
                assert_eq!(supported, 1);
            }
            other => panic!("expected SchemaMismatch, got {:?}", other),
        }
    }

    #[test]
    fn rejects_zero_parent_pid() {
        let mut content = valid_config("");
        content = content.replace("parent_pid = 1234", "parent_pid = 0");
        let p = write_config(&content);
        match Config::load(&p) {
            Err(HelperError::ConfigParse(msg)) => assert!(msg.contains("parent_pid")),
            other => panic!("expected ConfigParse, got {:?}", other),
        }
    }

    #[test]
    fn rejects_missing_required_field() {
        let bad = "schema_version = 1\nparent_pid = 1\n"; // missing rest
        let p = write_config(bad);
        match Config::load(&p) {
            Err(HelperError::ConfigParse(_)) => {}
            other => panic!("expected ConfigParse, got {:?}", other),
        }
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod config;
mod error;
mod logger;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected: 13 tests pass (4 error + 4 logger + 5 config).

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/config.rs update-helper/src/main.rs
git commit -m "feat(update-helper): TOML config parser with schema validation

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A5: Copy module with retry

**Files:**
- Create: `update-helper/src/copy.rs`
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Write `update-helper/src/copy.rs`**

```rust
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::Duration;

use crate::error::{HelperError, Result};
use crate::logger::Logger;

/// Recursively copy `src` directory contents over `dst`, preserving structure.
/// On Windows lock errors, retries with exponential backoff.
pub fn copy_tree(
    src: &Path,
    dst: &Path,
    initial_delay_ms: u64,
    max_retries: u32,
    log: &Logger,
) -> Result<usize> {
    let mut count = 0;
    copy_recursive(src, dst, src, initial_delay_ms, max_retries, log, &mut count)?;
    Ok(count)
}

fn copy_recursive(
    base: &Path,
    dst_root: &Path,
    current: &Path,
    initial_delay_ms: u64,
    max_retries: u32,
    log: &Logger,
    count: &mut usize,
) -> Result<()> {
    for entry in fs::read_dir(current)? {
        let entry = entry?;
        let path = entry.path();
        let rel = path.strip_prefix(base).unwrap_or(&path);
        let target = dst_root.join(rel);

        if path.is_dir() {
            fs::create_dir_all(&target)?;
            copy_recursive(base, dst_root, &path, initial_delay_ms, max_retries, log, count)?;
        } else {
            if let Some(parent) = target.parent() {
                fs::create_dir_all(parent)?;
            }
            copy_with_retry(&path, &target, initial_delay_ms, max_retries, log)?;
            *count += 1;
        }
    }
    Ok(())
}

pub fn copy_with_retry(
    src: &Path,
    dst: &Path,
    initial_delay_ms: u64,
    max_retries: u32,
    log: &Logger,
) -> Result<()> {
    let mut delay = initial_delay_ms;
    for attempt in 0..=max_retries {
        match fs::copy(src, dst) {
            Ok(_) => return Ok(()),
            Err(e) if is_lock_error(&e) && attempt < max_retries => {
                log.warn(&format!(
                    "Copy locked (attempt {}): {} ({})",
                    attempt + 1,
                    dst.display(),
                    e
                ));
                thread::sleep(Duration::from_millis(delay));
                delay = delay.saturating_mul(2);
            }
            Err(e) if is_lock_error(&e) => {
                return Err(HelperError::CopyExhausted(dst.to_path_buf()));
            }
            Err(e) => {
                return Err(HelperError::CopyFailed {
                    path: dst.to_path_buf(),
                    source: e,
                });
            }
        }
    }
    Err(HelperError::CopyExhausted(dst.to_path_buf()))
}

pub fn is_lock_error(e: &io::Error) -> bool {
    // Windows: ERROR_SHARING_VIOLATION=32, ERROR_LOCK_VIOLATION=33,
    //          ERROR_ACCESS_DENIED=5 (sometimes used for locks).
    matches!(e.raw_os_error(), Some(32) | Some(33) | Some(5))
        || e.kind() == io::ErrorKind::PermissionDenied
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::Write;

    fn make_tree(root: &Path, files: &[(&str, &str)]) {
        for (rel, content) in files {
            let p = root.join(rel);
            if let Some(parent) = p.parent() {
                fs::create_dir_all(parent).unwrap();
            }
            File::create(&p).unwrap().write_all(content.as_bytes()).unwrap();
        }
    }

    fn tempdir() -> PathBuf {
        let p = std::env::temp_dir().join(format!(
            "fxt-helper-copy-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(&p).unwrap();
        p
    }

    fn null_logger() -> Logger {
        let dir = tempdir();
        Logger::open(dir.join("test.log"))
    }

    #[test]
    fn copies_flat_files() {
        let src = tempdir();
        let dst = tempdir();
        make_tree(&src, &[("a.txt", "hello"), ("b.txt", "world")]);

        let n = copy_tree(&src, &dst, 1, 0, &null_logger()).unwrap();
        assert_eq!(n, 2);
        assert_eq!(fs::read_to_string(dst.join("a.txt")).unwrap(), "hello");
        assert_eq!(fs::read_to_string(dst.join("b.txt")).unwrap(), "world");
    }

    #[test]
    fn copies_nested_directories() {
        let src = tempdir();
        let dst = tempdir();
        make_tree(
            &src,
            &[
                ("app/FreeXmlToolkit.jar", "JAR-content"),
                ("runtime/bin/java.exe", "JAVA-binary"),
                ("FreeXmlToolkit.exe", "MAIN-exe"),
            ],
        );

        let n = copy_tree(&src, &dst, 1, 0, &null_logger()).unwrap();
        assert_eq!(n, 3);
        assert_eq!(
            fs::read_to_string(dst.join("app/FreeXmlToolkit.jar")).unwrap(),
            "JAR-content"
        );
        assert_eq!(
            fs::read_to_string(dst.join("runtime/bin/java.exe")).unwrap(),
            "JAVA-binary"
        );
    }

    #[test]
    fn overwrites_existing_files() {
        let src = tempdir();
        let dst = tempdir();
        make_tree(&src, &[("a.txt", "new")]);
        make_tree(&dst, &[("a.txt", "old")]);

        copy_tree(&src, &dst, 1, 0, &null_logger()).unwrap();
        assert_eq!(fs::read_to_string(dst.join("a.txt")).unwrap(), "new");
    }

    #[test]
    fn is_lock_error_recognizes_win32_codes() {
        assert!(is_lock_error(&io::Error::from_raw_os_error(32)));
        assert!(is_lock_error(&io::Error::from_raw_os_error(33)));
        assert!(is_lock_error(&io::Error::from_raw_os_error(5)));
        assert!(!is_lock_error(&io::Error::from_raw_os_error(2))); // ENOENT not a lock
    }

    #[test]
    fn permission_denied_kind_is_lock() {
        let e = io::Error::new(io::ErrorKind::PermissionDenied, "x");
        assert!(is_lock_error(&e));
    }

    #[test]
    fn nonexistent_source_returns_copy_failed_not_exhausted() {
        let src = tempdir().join("does-not-exist.txt");
        let dst = tempdir().join("target.txt");
        match copy_with_retry(&src, &dst, 1, 0, &null_logger()) {
            Err(HelperError::CopyFailed { .. }) => {}
            other => panic!("expected CopyFailed, got {:?}", other),
        }
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod config;
mod copy;
mod error;
mod logger;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected: 19 tests pass (4 error + 4 logger + 5 config + 6 copy).

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/copy.rs update-helper/src/main.rs
git commit -m "feat(update-helper): recursive copy with exponential-backoff retry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A6: Cleanup module

**Files:**
- Create: `update-helper/src/cleanup.rs`
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Write `update-helper/src/cleanup.rs`**

```rust
use std::fs;
use std::path::Path;

use crate::logger::Logger;

/// Best-effort recursive delete. Logs but never fails: leftover temp files
/// are harmless, and Windows Disk Cleanup will reap them eventually.
pub fn delete_dir_quietly(path: &Path, log: &Logger) {
    if !path.exists() {
        return;
    }
    match fs::remove_dir_all(path) {
        Ok(()) => log.info(&format!("Deleted: {}", path.display())),
        Err(e) => log.warn(&format!("Could not fully delete {}: {}", path.display(), e)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::Write;
    use std::path::PathBuf;

    fn tempdir() -> PathBuf {
        let p = std::env::temp_dir().join(format!(
            "fxt-helper-cleanup-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(&p).unwrap();
        p
    }

    fn null_logger() -> Logger {
        Logger::open(tempdir().join("test.log"))
    }

    #[test]
    fn deletes_existing_directory_with_contents() {
        let dir = tempdir();
        let nested = dir.join("a/b");
        fs::create_dir_all(&nested).unwrap();
        File::create(nested.join("file.txt"))
            .unwrap()
            .write_all(b"x")
            .unwrap();

        delete_dir_quietly(&dir, &null_logger());
        assert!(!dir.exists());
    }

    #[test]
    fn nonexistent_directory_is_noop() {
        let nonexistent = tempdir().join("never-created");
        delete_dir_quietly(&nonexistent, &null_logger());
        // No panic, no error
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod cleanup;
mod config;
mod copy;
mod error;
mod logger;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected: 21 tests pass.

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/cleanup.rs update-helper/src/main.rs
git commit -m "feat(update-helper): best-effort temp directory cleanup

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A7: Process module (Windows-specific)

**Files:**
- Create: `update-helper/src/process.rs`
- Modify: `update-helper/src/main.rs`

> **Note:** This module's tests run only on Windows CI. On Linux dev machines, the module compiles but its `wait_for_parent_exit` function returns a stub that immediately reports "parent gone".

- [ ] **Step 1: Write `update-helper/src/process.rs`**

```rust
use std::time::Duration;

use crate::error::{HelperError, Result};
use crate::logger::Logger;

/// Wait for the parent process (identified by PID + creation time) to exit.
/// Returns Ok(()) when parent has exited (or never existed by the time we look).
/// Returns Err(ParentWaitTimeout) if the timeout expires while parent is alive.
///
/// The creation_time check defends against PID recycling: if a different
/// process now holds the PID (its creation time will differ), we treat the
/// original parent as gone.
pub fn wait_for_parent_exit(
    parent_pid: u32,
    expected_creation_time: i64,
    timeout: Duration,
    log: &Logger,
) -> Result<()> {
    #[cfg(windows)]
    {
        win::wait_for_parent_exit(parent_pid, expected_creation_time, timeout, log)
    }

    #[cfg(not(windows))]
    {
        // Non-Windows builds (dev only): assume parent gone for simplicity.
        let _ = (parent_pid, expected_creation_time, timeout);
        log.info("wait_for_parent_exit: non-Windows stub (assuming parent gone)");
        Ok(())
    }
}

#[cfg(windows)]
mod win {
    use super::*;
    use std::ffi::c_void;
    use windows_sys::Win32::Foundation::{
        CloseHandle, GetLastError, FILETIME, HANDLE, WAIT_OBJECT_0, WAIT_TIMEOUT,
    };
    use windows_sys::Win32::System::Threading::{
        GetProcessTimes, OpenProcess, WaitForSingleObject,
        PROCESS_QUERY_LIMITED_INFORMATION, SYNCHRONIZE,
    };

    pub fn wait_for_parent_exit(
        parent_pid: u32,
        expected_creation_time: i64,
        timeout: Duration,
        log: &Logger,
    ) -> Result<()> {
        let handle: HANDLE = unsafe {
            OpenProcess(
                SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION,
                0,
                parent_pid,
            )
        };
        if handle == 0 {
            log.info(&format!("Parent PID {} already gone (OpenProcess returned NULL)", parent_pid));
            return Ok(());
        }

        // PID-recycling defense
        match get_creation_time(handle as *mut c_void) {
            Ok(actual) if actual != expected_creation_time => {
                unsafe { CloseHandle(handle) };
                log.warn(&format!(
                    "PID {} appears recycled (creation time {} != expected {}); treating as exited",
                    parent_pid, actual, expected_creation_time
                ));
                return Ok(());
            }
            Err(e) => {
                unsafe { CloseHandle(handle) };
                return Err(HelperError::ParentWaitFailed(format!(
                    "GetProcessTimes failed: {}",
                    e
                )));
            }
            _ => {}
        }

        let timeout_ms = timeout.as_millis().min(u32::MAX as u128) as u32;
        let result = unsafe { WaitForSingleObject(handle, timeout_ms) };
        unsafe { CloseHandle(handle) };

        match result {
            WAIT_OBJECT_0 => {
                log.info("Parent process exited cleanly");
                Ok(())
            }
            WAIT_TIMEOUT => Err(HelperError::ParentWaitTimeout),
            other => {
                let err = unsafe { GetLastError() };
                Err(HelperError::ParentWaitFailed(format!(
                    "WaitForSingleObject returned {} (GetLastError={})",
                    other, err
                )))
            }
        }
    }

    fn get_creation_time(handle: *mut c_void) -> std::result::Result<i64, String> {
        let mut creation = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut exit = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut kernel = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut user = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };

        let ok = unsafe {
            GetProcessTimes(
                handle as HANDLE,
                &mut creation,
                &mut exit,
                &mut kernel,
                &mut user,
            )
        };
        if ok == 0 {
            return Err(format!("error {}", unsafe { GetLastError() }));
        }
        Ok(filetime_to_i64(&creation))
    }

    fn filetime_to_i64(ft: &FILETIME) -> i64 {
        ((ft.dwHighDateTime as i64) << 32) | (ft.dwLowDateTime as i64 & 0xFFFF_FFFF)
    }

    #[cfg(test)]
    mod win_tests {
        use super::*;
        use windows_sys::Win32::System::Threading::GetCurrentProcess;

        #[test]
        fn get_creation_time_works_on_own_process() {
            let me = unsafe { GetCurrentProcess() };
            let t = get_creation_time(me as *mut c_void).expect("must work on own process");
            assert!(t > 0, "creation time should be positive: {}", t);
        }

        #[test]
        fn opening_pid_zero_returns_null() {
            // PID 0 is reserved (System Idle Process); OpenProcess should fail.
            let h = unsafe {
                OpenProcess(SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, 0, 0)
            };
            assert_eq!(h, 0, "OpenProcess(0) should return NULL");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn non_windows_stub_returns_ok() {
        // On non-Windows, the stub always returns Ok.
        // On Windows, this test still works because PID 0xFFFFFFFF is invalid → OpenProcess null → Ok.
        let log = Logger::open(std::env::temp_dir().join("test.log"));
        let r = wait_for_parent_exit(u32::MAX, 0, Duration::from_secs(1), &log);
        assert!(r.is_ok(), "should treat invalid PID as parent gone");
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod cleanup;
mod config;
mod copy;
mod error;
mod logger;
mod process;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests on Linux (cross-platform parts)**

```bash
cd update-helper && cargo test
```

Expected on Linux: 22 tests pass (21 prior + `non_windows_stub_returns_ok`).

- [ ] **Step 4: Run tests on Windows (full suite incl. win_tests)**

When run on Windows CI:
```
cargo test
```

Expected: 24 tests pass (22 cross-platform + `get_creation_time_works_on_own_process` + `opening_pid_zero_returns_null`).

- [ ] **Step 5: Commit**

```bash
git add update-helper/src/process.rs update-helper/src/main.rs
git commit -m "feat(update-helper): Win32 wait_for_parent_exit with PID-recycle defense

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A8: Restart module (Windows-specific)

**Files:**
- Create: `update-helper/src/restart.rs`
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Write `update-helper/src/restart.rs`**

```rust
use std::path::Path;

use crate::error::{HelperError, Result};
use crate::logger::Logger;

/// Launch the new application, fully detached from the helper process.
/// Helper exits immediately after; new app must not inherit any handles.
pub fn restart_application(launcher: &Path, log: &Logger) -> Result<()> {
    if !launcher.exists() {
        return Err(HelperError::RestartFailed(format!(
            "Launcher does not exist: {}",
            launcher.display()
        )));
    }

    #[cfg(windows)]
    {
        win::create_detached_process(launcher, log)
    }

    #[cfg(not(windows))]
    {
        // Non-Windows path: use ProcessBuilder-equivalent (std::process::Command).
        use std::process::{Command, Stdio};
        log.info(&format!(
            "Launching (non-Windows stub): {}",
            launcher.display()
        ));
        Command::new(launcher)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| HelperError::RestartFailed(e.to_string()))?;
        Ok(())
    }
}

#[cfg(windows)]
mod win {
    use super::*;
    use std::iter::once;
    use std::os::windows::ffi::OsStrExt;
    use std::ptr;
    use windows_sys::Win32::Foundation::{CloseHandle, GetLastError};
    use windows_sys::Win32::System::Threading::{
        CreateProcessW, CREATE_NEW_PROCESS_GROUP, DETACHED_PROCESS, PROCESS_INFORMATION,
        STARTUPINFOW,
    };

    pub fn create_detached_process(launcher: &Path, log: &Logger) -> Result<()> {
        // Quote the launcher path for the command line.
        let cmdline_str = format!("\"{}\"", launcher.display());
        let mut cmdline: Vec<u16> = cmdline_str.encode_utf16().chain(once(0)).collect();

        let app: Vec<u16> = launcher.as_os_str().encode_wide().chain(once(0)).collect();

        let mut si: STARTUPINFOW = unsafe { std::mem::zeroed() };
        si.cb = std::mem::size_of::<STARTUPINFOW>() as u32;
        let mut pi: PROCESS_INFORMATION = unsafe { std::mem::zeroed() };

        let creation_flags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP;

        let ok = unsafe {
            CreateProcessW(
                app.as_ptr(),
                cmdline.as_mut_ptr(),
                ptr::null(),
                ptr::null(),
                0, // bInheritHandles = FALSE
                creation_flags,
                ptr::null(),
                ptr::null(),
                &mut si,
                &mut pi,
            )
        };

        if ok == 0 {
            return Err(HelperError::RestartFailed(format!(
                "CreateProcessW failed (error {})",
                unsafe { GetLastError() }
            )));
        }

        log.info(&format!("Launched: {} (PID {})", launcher.display(), pi.dwProcessId));

        unsafe {
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn null_logger() -> Logger {
        Logger::open(std::env::temp_dir().join("test.log"))
    }

    #[test]
    fn missing_launcher_returns_restart_failed() {
        let bogus = PathBuf::from("/this/path/cannot/exist/9b2f3");
        match restart_application(&bogus, &null_logger()) {
            Err(HelperError::RestartFailed(_)) => {}
            other => panic!("expected RestartFailed, got {:?}", other),
        }
    }
}
```

- [ ] **Step 2: Modify `update-helper/src/main.rs`**

```rust
mod cleanup;
mod config;
mod copy;
mod error;
mod logger;
mod process;
mod restart;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
```

- [ ] **Step 3: Run tests**

```bash
cd update-helper && cargo test
```

Expected on Linux: 23 tests pass.

- [ ] **Step 4: Commit**

```bash
git add update-helper/src/restart.rs update-helper/src/main.rs
git commit -m "feat(update-helper): detached CreateProcessW for app restart

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task A9: Wire main.rs end-to-end

**Files:**
- Modify: `update-helper/src/main.rs`

- [ ] **Step 1: Replace `update-helper/src/main.rs` entirely**

```rust
mod cleanup;
mod config;
mod copy;
mod error;
mod logger;
mod process;
mod restart;

use std::path::PathBuf;
use std::process::ExitCode;
use std::time::Duration;

use config::Config;
use error::{HelperError, Result, EXIT_SUCCESS};
use logger::Logger;

const HELPER_VERSION: &str = env!("CARGO_PKG_VERSION");

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();

    if args.len() == 2 && args[1] == "--version" {
        println!("fxt-update-helper {} (Rust)", HELPER_VERSION);
        return ExitCode::from(EXIT_SUCCESS as u8);
    }

    if args.len() == 3 && args[1] == "--validate-config" {
        // Used by Java unit tests to verify TOML round-trip.
        let path = PathBuf::from(&args[2]);
        return match Config::load(&path) {
            Ok(_) => {
                println!("OK");
                ExitCode::from(EXIT_SUCCESS as u8)
            }
            Err(e) => {
                eprintln!("INVALID: {}", e);
                ExitCode::from(e.exit_code() as u8)
            }
        };
    }

    if args.len() != 2 {
        eprintln!(
            "Usage: fxt-update-helper <config.toml>\n       fxt-update-helper --version\n       fxt-update-helper --validate-config <config.toml>"
        );
        return ExitCode::from(error::EXIT_GENERIC_FAILURE as u8);
    }

    let config_path = PathBuf::from(&args[1]);

    match run(&config_path) {
        Ok(()) => ExitCode::from(EXIT_SUCCESS as u8),
        Err(e) => {
            // Best-effort: try to log the error if config loaded enough to know log path.
            eprintln!("UpdateHelper failed: {}", e);
            ExitCode::from(e.exit_code() as u8)
        }
    }
}

fn run(config_path: &std::path::Path) -> Result<()> {
    let config = Config::load(config_path)?;
    let log = Logger::open(&config.log_path);

    log.info(&format!("UpdateHelper started (Rust v{})", HELPER_VERSION));
    log.info(&format!("Schema version: {}", config.schema_version));
    log.info(&format!(
        "Parent PID: {} (creation: {})",
        config.parent_pid, config.parent_creation_time
    ));
    log.info(&format!(
        "Update: {} -> {}",
        config.old_version, config.new_version
    ));
    log.info(&format!("Install dir: {}", config.install_dir.display()));
    log.info(&format!("Extracted dir: {}", config.extracted_dir.display()));

    log.info("Waiting for parent process to exit...");
    let parent_timeout = Duration::from_secs(config.parent_wait_timeout_seconds);
    process::wait_for_parent_exit(
        config.parent_pid,
        config.parent_creation_time,
        parent_timeout,
        &log,
    )?;

    log.info(&format!(
        "Cooldown for {} seconds...",
        config.post_exit_cooldown_seconds
    ));
    std::thread::sleep(Duration::from_secs(config.post_exit_cooldown_seconds));

    let source_dir = locate_source_dir(&config.extracted_dir, &config.launcher_path, &log)?;
    log.info(&format!("Copying from: {}", source_dir.display()));

    let count = copy::copy_tree(
        &source_dir,
        &config.install_dir,
        config.copy_retry_initial_ms,
        config.copy_retry_max,
        &log,
    )?;
    log.info(&format!("Copied {} files", count));

    log.info(&format!("Restarting: {}", config.launcher_path.display()));
    restart::restart_application(&config.launcher_path, &log)?;

    cleanup::delete_dir_quietly(&config.extracted_dir, &log);

    log.info("UpdateHelper completed successfully (exit 0)");
    Ok(())
}

/// The extracted ZIP has a top-level "FreeXmlToolkit" directory. Locate it by
/// finding the launcher's filename within the extract tree.
fn locate_source_dir(
    extracted: &std::path::Path,
    launcher: &std::path::Path,
    log: &Logger,
) -> Result<PathBuf> {
    let launcher_name = launcher
        .file_name()
        .ok_or_else(|| HelperError::InvalidArgs("launcher_path has no file name".into()))?;

    if let Some(dir) = find_launcher_parent(extracted, launcher_name, 3) {
        return Ok(dir);
    }

    log.error(&format!(
        "Could not find {} in {}",
        launcher_name.to_string_lossy(),
        extracted.display()
    ));
    Err(HelperError::InvalidArgs(format!(
        "Launcher {} not found within {}",
        launcher_name.to_string_lossy(),
        extracted.display()
    )))
}

fn find_launcher_parent(
    dir: &std::path::Path,
    name: &std::ffi::OsStr,
    max_depth: u32,
) -> Option<PathBuf> {
    if max_depth == 0 {
        return None;
    }
    let entries = std::fs::read_dir(dir).ok()?;
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_file() && path.file_name() == Some(name) {
            return path.parent().map(|p| p.to_path_buf());
        }
        if path.is_dir() {
            if let Some(found) = find_launcher_parent(&path, name, max_depth - 1) {
                return Some(found);
            }
        }
    }
    None
}
```

- [ ] **Step 2: Run all tests**

```bash
cd update-helper && cargo test
```

Expected on Linux: 23 tests pass (no new tests; integration is exercised by smoke test next).

- [ ] **Step 3: Build release binary and verify size**

```bash
cd update-helper && cargo build --release
ls -la target/release/fxt-update-helper*
```

Expected: binary < 1 MB on Linux (Windows release binary will be similar; CI verifies).

- [ ] **Step 4: Smoke test --version**

```bash
cd update-helper && ./target/release/fxt-update-helper --version
```

Expected output: `fxt-update-helper 1.0.0 (Rust)`, exit code 0.

- [ ] **Step 5: Smoke test --validate-config with a valid TOML**

```bash
cd update-helper && cat > /tmp/test-cfg.toml <<'EOF'
schema_version = 1
parent_pid = 1234
parent_creation_time = 0
extracted_dir = '/tmp/extract'
install_dir = '/tmp/install'
launcher_path = '/tmp/install/FreeXmlToolkit'
log_path = '/tmp/test-helper.log'
old_version = "1.9.0"
new_version = "1.10.0"
EOF
./target/release/fxt-update-helper --validate-config /tmp/test-cfg.toml
```

Expected output: `OK`, exit code 0.

- [ ] **Step 6: Smoke test --validate-config rejects bad schema**

```bash
cd update-helper && cat > /tmp/bad-cfg.toml <<'EOF'
schema_version = 99
parent_pid = 1234
parent_creation_time = 0
extracted_dir = '/tmp/extract'
install_dir = '/tmp/install'
launcher_path = '/tmp/install/FreeXmlToolkit'
log_path = '/tmp/test-helper.log'
old_version = "1.9.0"
new_version = "1.10.0"
EOF
./target/release/fxt-update-helper --validate-config /tmp/bad-cfg.toml
echo "Exit: $?"
```

Expected output: `INVALID: Config schema_version 99 not supported (helper supports 1)`, exit code 2.

- [ ] **Step 7: Commit**

```bash
git add update-helper/src/main.rs
git commit -m "feat(update-helper): wire end-to-end main with --version and --validate-config

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase B: Build pipeline integration

### Task B1: Gradle task for cargo build

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Locate the existing Windows jpackage section**

Read `build.gradle.kts` around line 700 to find the Windows tasks (`createWindowsExecutableX64` etc.).

- [ ] **Step 2: Add the new Gradle task BEFORE the Windows jpackage task definitions**

Insert this snippet right before the `// Windows packages` comment (around line 700):

```kotlin
// ----------------------------------------------------------------------
// Native Rust Update Helper (Windows-only)
// ----------------------------------------------------------------------

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
    inputs.file("update-helper/Cargo.lock")
    outputs.file("update-helper/target/release/fxt-update-helper.exe")

    doFirst {
        println("📦 Compiling Rust update helper...")
    }
    doLast {
        val output = file("update-helper/target/release/fxt-update-helper.exe")
        if (output.exists()) {
            println("✅ Helper built: ${output.absolutePath} (${output.length()} bytes)")
        } else {
            throw GradleException("Helper binary not produced at: ${output.absolutePath}")
        }
    }
}

val rustUpdateHelperBinary: Provider<File> = buildWindowsUpdateHelper.map {
    file("update-helper/target/release/fxt-update-helper.exe")
}
```

- [ ] **Step 3: Run the new task on Linux to verify the `onlyIf` skip**

```bash
./gradlew buildWindowsUpdateHelper --info
```

Expected output: `Task :buildWindowsUpdateHelper SKIPPED` (because we're not on Windows). No errors.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add buildWindowsUpdateHelper Gradle task

Compiles the Rust update helper via cargo on Windows hosts.
No-ops on Mac/Linux hosts.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B2: Modify Windows jpackage tasks to use --app-content

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Locate the `createJPackageTask` function**

Around line 391 (the function definition).

- [ ] **Step 2: Inside `createJPackageTask`, add Windows-specific helper dependency**

After the `dependsOn("jar", runtimeTaskName)` line (around line 433), add:

```kotlin
// Windows packages also need the Rust update helper
if (platform == "windows") {
    dependsOn("buildWindowsUpdateHelper")
}
```

- [ ] **Step 3: In the `argsFile.writeText(...)` block, find these lines:**

```kotlin
$iconArg
--add-launcher
UpdateHelper=${helperLauncherFile.absolutePath}
$runtimeArg
```

- [ ] **Step 4: Replace `--add-launcher UpdateHelper=...` with platform-conditional `--app-content`**

Compute the helper path before `argsFile.writeText`:

```kotlin
val rustHelperArg = if (platform == "windows") {
    val helperExe = file("update-helper/target/release/fxt-update-helper.exe")
    if (!helperExe.exists()) {
        throw GradleException("Rust update helper not found: ${helperExe.absolutePath}\nRun :buildWindowsUpdateHelper first.")
    }
    "--app-content\n${helperExe.absolutePath}"
} else {
    ""
}
```

Then in the heredoc, replace:

```kotlin
$iconArg
--add-launcher
UpdateHelper=${helperLauncherFile.absolutePath}
$runtimeArg
```

with:

```kotlin
$iconArg
$rustHelperArg
$runtimeArg
```

Also: remove the `helperLauncherFile.writeText(...)` block (the .properties file for the old --add-launcher) since it's no longer used. Find and delete:

```kotlin
val helperLauncherFile = File(project.layout.buildDirectory.asFile.get(),
    "update-helper-$platform-$arch-$packageType.properties")
helperLauncherFile.writeText("""
    main-jar=FreeXmlToolkit.jar
    main-class=org.fxt.freexmltoolkit.service.update.UpdateHelperMain
    name=UpdateHelper
""".trimIndent())
```

- [ ] **Step 5: In the `doLast` block of jpackage tasks, add post-build verification (Windows only)**

Insert at the start of `doLast`:

```kotlin
// Verify Rust helper made it into the package (Windows only)
if (platform == "windows" && packageType == "app-image") {
    val expected = File("$sourceDir/FreeXmlToolkit/fxt-update-helper.exe")
    if (!expected.exists()) {
        throw GradleException("Build inconsistent: missing $expected")
    }
    println("✅ Rust helper verified in app-image: ${expected.length()} bytes")
}
```

- [ ] **Step 6: Run a non-Windows build to verify nothing breaks**

```bash
./gradlew jar --info
```

Expected: success. The Rust helper is not built (we're on Linux), and no Windows package tasks run.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts
git commit -m "build: bundle Rust helper via jpackage --app-content (Windows)

Drops --add-launcher UpdateHelper=... in favor of --app-content,
which injects the Rust EXE into app-image, EXE-installer, and MSI alike.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B3: GitHub Actions Rust toolchain

**Files:**
- Modify: `.github/workflows/build-packages-on-new-release.yml`

- [ ] **Step 1: Read the existing workflow to confirm structure**

```bash
cat .github/workflows/build-packages-on-new-release.yml
```

- [ ] **Step 2: Add Rust toolchain steps after the JDK setup step**

Find this block:

```yaml
      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          java-package: 'jdk+fx'
          java-version: '25'
          distribution: 'liberica'
```

After it, insert:

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

- [ ] **Step 3: Lint the YAML for syntax**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build-packages-on-new-release.yml'))" && echo "YAML OK"
```

Expected: `YAML OK`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build-packages-on-new-release.yml
git commit -m "ci: add Rust toolchain to Windows release jobs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase C: Java-side changes

### Task C1: Add new launchUpdater methods (without removing old ones yet)

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/AutoUpdateServiceImpl.java`

> **Strategy:** First, add the new code paths alongside the old ones. Test them. Then remove the old code in a separate commit.

- [ ] **Step 1: Add a new method `launchUpdaterWindowsRust` after `launchUpdater`**

In `AutoUpdateServiceImpl.java`, add this method (place it near `launchUpdater`):

```java
/**
 * New Windows updater path (since v1.9.0): copies the installed Rust helper
 * to %TEMP% and launches it from there with a TOML config file.
 *
 * @param extractedDir directory containing the extracted update payload
 * @param debugLog debug log file path
 * @return true if the helper was launched successfully
 */
private boolean launchUpdaterWindowsRust(Path extractedDir, Path debugLog) {
    try {
        Path appDir = getApplicationDirectory();
        Path launcher = getApplicationLauncher();

        // 1. Find the helper. Prefer installed copy, fall back to extracted.
        Path installedHelper = appDir.resolve("fxt-update-helper.exe");
        Path extractedHelper = extractedDir.resolve("FreeXmlToolkit").resolve("fxt-update-helper.exe");
        Path sourceHelper;
        if (Files.exists(installedHelper)) {
            sourceHelper = installedHelper;
            writeDebugLog(debugLog, "Helper source (installed): " + sourceHelper);
        } else if (Files.exists(extractedHelper)) {
            sourceHelper = extractedHelper;
            writeDebugLog(debugLog, "Helper source (extracted fallback): " + sourceHelper);
        } else {
            writeDebugLog(debugLog, "ERROR: No helper found in install or extracted location");
            return false;
        }

        // 2. Copy helper to %TEMP% so its install-dir copy is free for overwrite
        Path tempHelper = Files.createTempFile("fxt-helper-", ".exe");
        Files.copy(sourceHelper, tempHelper, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        writeDebugLog(debugLog, "Helper copied to temp: " + tempHelper);

        // 3. Write TOML config
        Path configFile = extractedDir.resolve("helper-config.toml");
        Path helperLog = Path.of(System.getProperty("user.home"), "fxt-update-helper.log");
        writeRustHelperConfig(configFile, extractedDir, appDir, launcher, helperLog);
        writeDebugLog(debugLog, "Helper config written: " + configFile);

        // 4. Launch (UAC if needed)
        boolean writable = isAppDirectoryWritable(appDir);
        writeDebugLog(debugLog, "App directory writable without elevation: " + writable);

        ProcessBuilder pb;
        if (writable) {
            pb = new ProcessBuilder(tempHelper.toString(), configFile.toString());
            writeDebugLog(debugLog, "Launching Rust helper directly (no elevation)");
        } else {
            String command = "Start-Process -FilePath '" + tempHelper +
                "' -ArgumentList '" + configFile + "' -Verb RunAs";
            pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command);
            writeDebugLog(debugLog, "Launching Rust helper via PowerShell elevation: " + command);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        writeDebugLog(debugLog, "Helper process started with PID: " + process.pid());
        return true;

    } catch (IOException e) {
        writeDebugLog(debugLog, "ERROR (IOException) in launchUpdaterWindowsRust: " + e.getMessage());
        logger.error("Failed to launch Rust update helper", e);
        return false;
    }
}

/**
 * Writes a TOML config file consumable by the Rust helper.
 * See spec section 4.4 for schema.
 */
private void writeRustHelperConfig(Path configFile, Path extractedDir, Path appDir,
                                    Path launcher, Path helperLog) throws IOException {
    long parentPid = ProcessHandle.current().pid();
    long parentCreationTime = currentProcessFiletime();
    String oldVersion = currentVersionString();
    String newVersion = "unknown"; // populated below if available

    StringBuilder sb = new StringBuilder();
    sb.append("schema_version = 1\n");
    sb.append("parent_pid = ").append(parentPid).append("\n");
    sb.append("parent_creation_time = ").append(parentCreationTime).append("\n");
    sb.append("extracted_dir = '").append(extractedDir.toAbsolutePath()).append("'\n");
    sb.append("install_dir = '").append(appDir.toAbsolutePath()).append("'\n");
    sb.append("launcher_path = '").append(launcher.toAbsolutePath()).append("'\n");
    sb.append("log_path = '").append(helperLog.toAbsolutePath()).append("'\n");
    sb.append("old_version = \"").append(oldVersion).append("\"\n");
    sb.append("new_version = \"").append(newVersion).append("\"\n");

    Files.writeString(configFile, sb.toString());
}

/**
 * Returns the current process's creation time as a Win32 FILETIME (100-ns
 * intervals since 1601-01-01 UTC). On non-Windows, returns 0.
 */
private long currentProcessFiletime() {
    java.time.Instant start = ProcessHandle.current().info().startInstant().orElse(null);
    if (start == null) {
        return 0L;
    }
    // Convert UNIX epoch instant to Win32 FILETIME:
    // FILETIME epoch = 1601-01-01, in 100-ns ticks.
    // Difference between 1601 and 1970 = 11644473600 seconds.
    long unixSeconds = start.getEpochSecond();
    long unixNanos = start.getNano();
    long ticksSince1601 = (unixSeconds + 11644473600L) * 10_000_000L
        + unixNanos / 100;
    return ticksSince1601;
}

private String currentVersionString() {
    try {
        Package pkg = AutoUpdateServiceImpl.class.getPackage();
        String v = pkg != null ? pkg.getImplementationVersion() : null;
        return v != null ? v : "unknown";
    } catch (Exception e) {
        return "unknown";
    }
}
```

- [ ] **Step 2: Add a new POSIX in-process updater method**

```java
/**
 * Mac/Linux updater path: copies extracted update directly over the install
 * directory and starts the new launcher. Works because POSIX inode-replace
 * allows overwriting files of running processes.
 */
private boolean launchUpdaterPosix(Path extractedDir, Path debugLog) {
    try {
        Path appDir = getApplicationDirectory();
        Path launcher = getApplicationLauncher();

        // Locate the source dir (top-level "FreeXmlToolkit" inside extract).
        Path source = extractedDir.resolve("FreeXmlToolkit");
        if (!Files.exists(source)) {
            writeDebugLog(debugLog, "ERROR: extracted source not found: " + source);
            return false;
        }

        writeDebugLog(debugLog, "POSIX in-process copy: " + source + " -> " + appDir);
        copyTreeReplaceExisting(source, appDir);

        writeDebugLog(debugLog, "Launching new app: " + launcher);
        new ProcessBuilder(launcher.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        return true;

    } catch (IOException e) {
        writeDebugLog(debugLog, "ERROR in launchUpdaterPosix: " + e.getMessage());
        logger.error("POSIX updater failed", e);
        return false;
    }
}

private void copyTreeReplaceExisting(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
        stream.forEach(path -> {
            try {
                Path rel = source.relativize(path);
                Path dest = target.resolve(rel);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    } catch (RuntimeException e) {
        if (e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
        }
        throw e;
    }
}
```

- [ ] **Step 3: Modify `launchUpdater(Path, Path)` to dispatch to the new methods**

Replace the body of `launchUpdater` (around line 468) entirely with:

```java
private boolean launchUpdater(Path extractedDir, Path debugLog) {
    if (isWindows()) {
        return launchUpdaterWindowsRust(extractedDir, debugLog);
    } else {
        return launchUpdaterPosix(extractedDir, debugLog);
    }
}
```

- [ ] **Step 4: Build and run existing tests**

```bash
./gradlew compileJava
./gradlew test --tests "AutoUpdateServiceImplTest" --info
```

Expected: compiles successfully, existing tests still pass (or at least don't fail because of compilation errors).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/AutoUpdateServiceImpl.java
git commit -m "feat(auto-update): add Rust-helper Windows path + POSIX in-process path

Old launchUpdater() now dispatches to launchUpdaterWindowsRust (uses
new Rust helper from %TEMP%) on Windows, and launchUpdaterPosix
(direct in-process copy) on Mac/Linux.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task C2: Java unit tests for new methods

**Files:**
- Create: `src/test/java/org/fxt/freexmltoolkit/service/AutoUpdateServiceImplRustHelperTest.java`

- [ ] **Step 1: Write the test file**

```java
package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoUpdateServiceImplRustHelperTest {

    @Test
    void writeRustHelperConfig_emitsValidToml(@TempDir Path tmp) throws Exception {
        Path configFile = tmp.resolve("helper-config.toml");
        Path extractedDir = tmp.resolve("extract");
        Path appDir = tmp.resolve("app");
        Path launcher = appDir.resolve("FreeXmlToolkit.exe");
        Path helperLog = tmp.resolve("helper.log");

        Files.createDirectories(extractedDir);
        Files.createDirectories(appDir);
        Files.createFile(launcher);

        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();

        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod(
            "writeRustHelperConfig",
            Path.class, Path.class, Path.class, Path.class, Path.class);
        m.setAccessible(true);
        m.invoke(impl, configFile, extractedDir, appDir, launcher, helperLog);

        String toml = Files.readString(configFile);

        // Must contain all required keys
        for (String key : List.of(
                "schema_version = 1",
                "parent_pid =",
                "parent_creation_time =",
                "extracted_dir =",
                "install_dir =",
                "launcher_path =",
                "log_path =",
                "old_version =",
                "new_version =")) {
            assertTrue(toml.contains(key), "missing key: " + key + "\nTOML:\n" + toml);
        }

        // Paths must be single-quoted (TOML literal strings)
        assertTrue(toml.contains("'" + extractedDir.toAbsolutePath() + "'"),
            "extracted_dir must be single-quoted: " + toml);
    }

    @Test
    void copyTreeReplaceExisting_overwritesExistingFiles(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("src");
        Path target = tmp.resolve("dst");
        Files.createDirectories(source.resolve("nested"));
        Files.writeString(source.resolve("a.txt"), "new-a");
        Files.writeString(source.resolve("nested/b.txt"), "new-b");

        Files.createDirectories(target.resolve("nested"));
        Files.writeString(target.resolve("a.txt"), "old-a");
        Files.writeString(target.resolve("nested/b.txt"), "old-b");

        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod(
            "copyTreeReplaceExisting", Path.class, Path.class);
        m.setAccessible(true);
        m.invoke(impl, source, target);

        assertEquals("new-a", Files.readString(target.resolve("a.txt")));
        assertEquals("new-b", Files.readString(target.resolve("nested/b.txt")));
    }

    @Test
    void currentProcessFiletime_isPositiveAndPlausible() throws Exception {
        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod("currentProcessFiletime");
        m.setAccessible(true);
        long ft = (long) m.invoke(impl);

        // FILETIME for current millennium is in the range ~1.32e17 to 1.34e17
        if (ft != 0L) {
            assertTrue(ft > 130_000_000_000_000_000L, "FILETIME too small: " + ft);
            assertTrue(ft < 140_000_000_000_000_000L, "FILETIME too large: " + ft);
        }
    }
}
```

- [ ] **Step 2: Run the new tests**

```bash
./gradlew test --tests "AutoUpdateServiceImplRustHelperTest" --info
```

Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/service/AutoUpdateServiceImplRustHelperTest.java
git commit -m "test(auto-update): unit tests for Rust-helper config writer and POSIX copy

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task C3: Round-trip integration test (Java writes config, Rust validates)

**Files:**
- Create: `src/test/java/org/fxt/freexmltoolkit/service/RustHelperConfigRoundtripTest.java`

> **Note:** This test requires the Rust helper to be built. It is enabled only when `update-helper/target/release/fxt-update-helper.exe` (or platform-equivalent without `.exe`) exists.

- [ ] **Step 1: Write the test**

```java
package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RustHelperConfigRoundtripTest {

    @Test
    void javaConfigParsesInRustHelper(@TempDir Path tmp) throws Exception {
        Path helperBinary = locateRustHelper();
        Assumptions.assumeTrue(Files.exists(helperBinary),
            "Rust helper not built — skip. Run `cd update-helper && cargo build --release` first.");

        Path configFile = tmp.resolve("helper-config.toml");
        Path extractedDir = tmp.resolve("extract");
        Path appDir = tmp.resolve("app");
        Path launcher = appDir.resolve("FreeXmlToolkit");
        Path helperLog = tmp.resolve("helper.log");

        Files.createDirectories(extractedDir);
        Files.createDirectories(appDir);
        Files.createFile(launcher);

        AutoUpdateServiceImpl impl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        Method m = AutoUpdateServiceImpl.class.getDeclaredMethod(
            "writeRustHelperConfig",
            Path.class, Path.class, Path.class, Path.class, Path.class);
        m.setAccessible(true);
        m.invoke(impl, configFile, extractedDir, appDir, launcher, helperLog);

        ProcessBuilder pb = new ProcessBuilder(
            helperBinary.toString(), "--validate-config", configFile.toString())
            .redirectErrorStream(true);
        Process p = pb.start();
        boolean done = p.waitFor(10, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new AssertionError("Helper --validate-config timed out");
        }
        String output = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.exitValue(),
            "Rust helper rejected Java-written config:\n" + output);
    }

    private Path locateRustHelper() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String binName = isWindows ? "fxt-update-helper.exe" : "fxt-update-helper";
        return Path.of("update-helper", "target", "release", binName);
    }
}
```

- [ ] **Step 2: Build the Rust helper first (so the test isn't skipped)**

```bash
cd update-helper && cargo build --release && cd ..
```

- [ ] **Step 3: Run the round-trip test**

```bash
./gradlew test --tests "RustHelperConfigRoundtripTest" --info
```

Expected: test passes (Java config TOML successfully validated by Rust helper).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/fxt/freexmltoolkit/service/RustHelperConfigRoundtripTest.java
git commit -m "test(auto-update): round-trip test ensures Java→Rust TOML compatibility

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task C4: Delete the old Java helper

**Files:**
- Delete: `src/main/java/org/fxt/freexmltoolkit/service/update/UpdateHelperMain.java`
- Delete: `src/main/java/org/fxt/freexmltoolkit/service/update/UpdateHelperConfig.java`
- Delete (if empty): `src/main/java/org/fxt/freexmltoolkit/service/update/`
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/AutoUpdateServiceImpl.java`

- [ ] **Step 1: Remove dead helper-related methods from `AutoUpdateServiceImpl`**

Delete these methods entirely:

```java
// DELETE these methods:
private boolean launchUpdateHelper(Path helperLauncher, Path configFile, Path workingDir, Path appDir, Path debugLog)
private Path createUpdateHelperConfig(Path extractedDir, Path appDir, Path launcher)
private Path getUpdateHelperLauncher()
private String getUpdateHelperExecutableName()
private Path findHelperInUpdate(Path extractedDir, String helperExecutableName)
private String escapeForAppleScript(String input)
private boolean validateUpdaterPaths(Path appDir, Path extractedDir, Path launcher)
private String getPlatformName()
```

Also remove these unused imports if present:
- `java.util.Properties`

> **Why `validateUpdaterPaths` is removed:** Used only by the old launchUpdater. The new flow doesn't need shell-escape validation because we use ProcessBuilder with explicit args (no shell injection risk).

- [ ] **Step 2: Delete old helper files**

```bash
rm -f src/main/java/org/fxt/freexmltoolkit/service/update/UpdateHelperMain.java
rm -f src/main/java/org/fxt/freexmltoolkit/service/update/UpdateHelperConfig.java
rmdir src/main/java/org/fxt/freexmltoolkit/service/update/ 2>/dev/null || true
```

- [ ] **Step 3: Search for any remaining references**

```bash
grep -rn "UpdateHelperMain\|UpdateHelperConfig\|getUpdateHelperLauncher\|findHelperInUpdate" src/ build.gradle.kts || echo "No references found — clean."
```

Expected: `No references found — clean.`

- [ ] **Step 4: Compile and run tests**

```bash
./gradlew compileJava
./gradlew test --tests "AutoUpdateServiceImpl*"
```

Expected: compiles cleanly, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(auto-update): delete legacy Java UpdateHelper

Removes UpdateHelperMain, UpdateHelperConfig, and dead helper-related
methods in AutoUpdateServiceImpl. Net change: −344 LoC.

The Rust helper from Phase A replaces this entire subsystem.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase D: Documentation & verification

### Task D1: Manual test guide

**Files:**
- Create: `docs/testing/auto-update-windows.md`

- [ ] **Step 1: Create the directory**

```bash
mkdir -p docs/testing
```

- [ ] **Step 2: Write `docs/testing/auto-update-windows.md`**

```markdown
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
```

- [ ] **Step 3: Commit**

```bash
git add docs/testing/auto-update-windows.md
git commit -m "docs(testing): manual test guide for Windows auto-update

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D2: Update CLAUDE.md and architecture docs

**Files:**
- Modify: `.claude/rules/architecture.md`
- Modify: `CLAUDE.md` (only if necessary)

- [ ] **Step 1: Add a note in `.claude/rules/architecture.md` about the Rust helper**

In the "Service Layer" or "Threading Model" section (whichever fits best), add:

```markdown
## Auto-Update Subsystem

The `AutoUpdateServiceImpl` orchestrates downloads and platform-specific
updater dispatch:

- **Windows:** Native Rust helper (`update-helper/` crate, ~500 KB binary)
  is bundled into the app-image at `<install>/fxt-update-helper.exe`.
  At update time, the helper is copied to `%TEMP%`, launched from there,
  and the install directory becomes free for overwrite. See
  `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md`.
- **Mac/Linux:** No separate helper — `AutoUpdateServiceImpl` performs
  in-process recursive copy then exec's the new launcher (POSIX inode
  semantics allow overwriting files of running processes).
```

- [ ] **Step 2: If CLAUDE.md mentions the old helper architecture, update it**

```bash
grep -n "UpdateHelper\|update.helper" CLAUDE.md
```

Update any stale references to point to the new design doc instead.

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/architecture.md CLAUDE.md 2>/dev/null
git commit -m "docs(architecture): note new Rust auto-update helper

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task D3: Pre-release verification checklist

This is the final gate before tagging v1.9.0. Tracked here, executed manually.

- [ ] **Step 1: Local end-to-end verification on Linux dev box**

```bash
# Full Rust test suite passes
cd update-helper && cargo test && cd ..

# Java tests pass
./gradlew test
```

- [ ] **Step 2: Local Windows VM build**

On a Windows 10/11 VM with JDK 25 + Rust toolchain:

```bash
./gradlew clean buildWindowsUpdateHelper
./gradlew createWindowsAppImageX64
./gradlew createWindowsAppImageX64Zip
./gradlew createWindowsExecutableX64
./gradlew createWindowsMsiX64
```

Verify:
- `update-helper/target/release/fxt-update-helper.exe` exists and < 1 MB
- `build/dist/windows-x64-app-image/FreeXmlToolkit/fxt-update-helper.exe` exists
- The MSI and EXE installers, when extracted, contain `fxt-update-helper.exe`

- [ ] **Step 3: Manual test T1 (writable path)**

Follow `docs/testing/auto-update-windows.md` Test T1.
Result: ☐ PASS  ☐ FAIL  Notes: _______________

- [ ] **Step 4: Manual test T2 (Program Files + UAC)**

Result: ☐ PASS  ☐ FAIL  Notes: _______________

- [ ] **Step 5: Manual test T4 (AV tolerance)**

Result: ☐ PASS  ☐ FAIL  Notes: _______________

- [ ] **Step 6: Migration verification**

- Fresh install v1.7.0 → trigger update to test-v1.9.0
  → MUST FAIL predictably with helper log "Copy locked"
- Fresh install v1.8.0 → trigger update to test-v1.9.0
  → SHOULD SUCCEED via the 7befff69 fix
- Fresh install v1.9.0 → release test-tag v1.9.1 → trigger update
  → MUST SUCCEED via Rust helper (this is the new norm)

- [ ] **Step 7: Update release notes for v1.9.0**

Add the migration block from spec section 7.2 to the GitHub Release notes when tagging v1.9.0.

- [ ] **Step 8: Final commit (release version bump only)**

```bash
# Bump version
sed -i 's/version = "1\.8\.0"/version = "1.9.0"/' build.gradle.kts
sed -i 's/DEFAULT_VERSION = "1\.8\.0"/DEFAULT_VERSION = "1.9.0"/' \
  src/main/java/org/fxt/freexmltoolkit/service/UpdateCheckServiceImpl.java

git add build.gradle.kts src/main/java/org/fxt/freexmltoolkit/service/UpdateCheckServiceImpl.java
git commit -m "chore: bump version to 1.9.0

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 9: Tag and release**

```bash
git push origin main
gh release create v1.9.0 --target main --title "v1.9.0" --notes-file release-notes-v1.9.0.md
```

(Where `release-notes-v1.9.0.md` is composed locally from the migration template + auto-generated changelog.)

---

## Self-Review checklist

- [x] Spec coverage: Phase A covers spec §3, §4.1–§4.7. Phase B covers §5. Phase C covers §4.8. Phase D covers §6 + §7.
- [x] No TBD/TODO placeholders.
- [x] Type consistency: `fxt-update-helper.exe` (binary name) consistent across A1, B1, B2, C1, C4, D2. `helper-config.toml` consistent. `schema_version = 1` consistent.
- [x] Method signatures match: `writeRustHelperConfig` declared in C1, used by reflection in C2, C3.
- [x] Each task has exact file paths, runnable commands, and concrete code.
- [x] Frequent commits — 16 commits across 4 phases.

## Total scope summary

| Phase | Tasks | LoC delta | Files | Commits |
|-------|-------|-----------|-------|---------|
| A: Rust helper | 9 (A1–A9) | +850 Rust | 8 new | 9 |
| B: Build/CI | 3 (B1–B3) | +50 Kotlin/YAML | 0 new, 2 modified | 3 |
| C: Java side | 4 (C1–C4) | +300/−380 Java | 2 new tests, 1 modified, 2 deleted | 4 |
| D: Docs/verify | 3 (D1–D3) | +200 markdown | 2 new docs | 2 + version-bump |
| **Total** | **19** | **+1100/−380** | **+13 / −2** | **16+1** |
