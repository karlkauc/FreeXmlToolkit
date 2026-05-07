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
