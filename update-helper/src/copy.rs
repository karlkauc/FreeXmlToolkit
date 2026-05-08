use std::fs;
use std::io;
use std::io::Read;
use std::path::Path;
use std::thread;
use std::time::Duration;

use crate::error::{HelperError, Result};
use crate::logger::Logger;

/// Buffer size for byte-wise file comparison. 64 KiB balances syscall
/// overhead and memory use for the typical jpackage payload (~200 MB).
const COMPARE_CHUNK: usize = 64 * 1024;

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
    // Skip work if the destination already matches the source byte-for-byte.
    // Many jpackage payload files (e.g., runtime/bin/*.dll) are unchanged
    // between adjacent versions; trying to overwrite them anyway runs into
    // sharing-violation errors that linger 30+ seconds after the parent exit
    // (Windows file system + AV release handles asynchronously).
    if dst.exists() && files_are_identical(src, dst) {
        return Ok(());
    }

    let mut delay = initial_delay_ms;
    // Try max_retries+1 times total: 1 initial attempt plus max_retries retries.
    for attempt in 0..=max_retries {
        match fs::copy(src, dst) {
            Ok(_) => return Ok(()),
            Err(e) if is_lock_error(&e) => {
                if attempt == max_retries {
                    // All retries exhausted on lock errors.
                    return Err(HelperError::CopyExhausted(dst.to_path_buf()));
                }
                log.warn(&format!(
                    "Copy locked (attempt {}): {} ({})",
                    attempt + 1,
                    dst.display(),
                    e
                ));
                thread::sleep(Duration::from_millis(delay));
                delay = delay.saturating_mul(2);
            }
            Err(e) => {
                return Err(HelperError::CopyFailed {
                    path: dst.to_path_buf(),
                    source: e,
                });
            }
        }
    }
    // Loop has at least one iteration (max_retries: u32, range 0..=u32).
    // The CopyExhausted return inside the loop fires on the last iteration.
    // This is unreachable but rustc cannot prove that.
    unreachable!("loop above always returns")
}

/// Return `true` when `src` and `dst` exist with identical size and byte
/// content. Any I/O error is treated as "not identical" so the caller falls
/// back to the regular copy-and-retry path.
pub fn files_are_identical(src: &Path, dst: &Path) -> bool {
    let s_len = match fs::metadata(src) {
        Ok(m) => m.len(),
        Err(_) => return false,
    };
    let d_len = match fs::metadata(dst) {
        Ok(m) => m.len(),
        Err(_) => return false,
    };
    if s_len != d_len {
        return false;
    }

    let mut s_file = match fs::File::open(src) {
        Ok(f) => f,
        Err(_) => return false,
    };
    let mut d_file = match fs::File::open(dst) {
        Ok(f) => f,
        Err(_) => return false,
    };

    let mut s_buf = vec![0u8; COMPARE_CHUNK];
    let mut d_buf = vec![0u8; COMPARE_CHUNK];
    loop {
        let n = match s_file.read(&mut s_buf) {
            Ok(n) => n,
            Err(_) => return false,
        };
        let m = match d_file.read(&mut d_buf) {
            Ok(m) => m,
            Err(_) => return false,
        };
        if n != m {
            return false;
        }
        if n == 0 {
            return true;
        }
        if s_buf[..n] != d_buf[..n] {
            return false;
        }
    }
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
    use std::path::PathBuf;

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

    #[test]
    fn files_are_identical_true_for_same_content() {
        let dir = tempdir();
        let a = dir.join("a.bin");
        let b = dir.join("b.bin");
        fs::write(&a, b"vcruntime").unwrap();
        fs::write(&b, b"vcruntime").unwrap();
        assert!(files_are_identical(&a, &b));
    }

    #[test]
    fn files_are_identical_false_for_different_size() {
        let dir = tempdir();
        let a = dir.join("a.bin");
        let b = dir.join("b.bin");
        fs::write(&a, b"short").unwrap();
        fs::write(&b, b"a much longer payload").unwrap();
        assert!(!files_are_identical(&a, &b));
    }

    #[test]
    fn files_are_identical_false_for_same_size_different_bytes() {
        let dir = tempdir();
        let a = dir.join("a.bin");
        let b = dir.join("b.bin");
        fs::write(&a, b"AAAA").unwrap();
        fs::write(&b, b"BBBB").unwrap();
        assert!(!files_are_identical(&a, &b));
    }

    #[test]
    fn files_are_identical_handles_large_files_with_late_diff() {
        // Difference past the first chunk: ensures chunked compare iterates.
        let dir = tempdir();
        let a = dir.join("a.bin");
        let b = dir.join("b.bin");
        let mut bytes_a = vec![0u8; COMPARE_CHUNK + 16];
        let mut bytes_b = bytes_a.clone();
        bytes_b[COMPARE_CHUNK + 4] = 0xFF;
        fs::write(&a, &bytes_a).unwrap();
        fs::write(&b, &bytes_b).unwrap();
        assert!(!files_are_identical(&a, &b));
        bytes_a[COMPARE_CHUNK + 4] = 0xFF;
        fs::write(&a, &bytes_a).unwrap();
        assert!(files_are_identical(&a, &b));
    }

    #[test]
    fn copy_with_retry_skips_identical_destination() {
        // Regression for v1.9.x update failure: when an unchanged DLL is
        // locked by Windows post-exit, copy_with_retry must not even try
        // to overwrite it.
        let dir = tempdir();
        let src = dir.join("src.bin");
        let dst = dir.join("dst.bin");
        fs::write(&src, b"identical-content").unwrap();
        fs::write(&dst, b"identical-content").unwrap();

        // Capture the dst's mtime to verify it's untouched.
        let before = fs::metadata(&dst).unwrap().modified().unwrap();
        std::thread::sleep(std::time::Duration::from_millis(20));
        copy_with_retry(&src, &dst, 1, 0, &null_logger()).expect("should skip silently");
        let after = fs::metadata(&dst).unwrap().modified().unwrap();
        assert_eq!(before, after, "dst must not have been rewritten");
    }

    #[test]
    fn copy_with_retry_overwrites_when_destination_differs() {
        // Sanity: skip-identical short-circuit must not hide real updates.
        let dir = tempdir();
        let src = dir.join("src.bin");
        let dst = dir.join("dst.bin");
        fs::write(&src, b"new-version").unwrap();
        fs::write(&dst, b"old-version").unwrap();
        copy_with_retry(&src, &dst, 1, 0, &null_logger()).expect("should copy");
        assert_eq!(fs::read(&dst).unwrap(), b"new-version");
    }
}
