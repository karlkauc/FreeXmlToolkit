use std::fs;
use std::io;
use std::path::Path;
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
}
