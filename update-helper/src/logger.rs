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
