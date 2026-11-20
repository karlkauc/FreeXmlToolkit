//! The `RESULT` line at the end of a helper run. The application reads the last one after a
//! start that still runs the old version and reports its fields in `update_installed`
//! (`UpdateFunnel`), so the line holds fixed codes only: never a path, a file name or a message.

use std::path::{Component, Path};

use crate::error::HelperError;

/// Marker the application searches for; keep in sync with `HelperResult.MARKER` (Java).
pub const MARKER: &str = "RESULT ";

/// `RESULT ok`, or `RESULT <code>` with ` folder=<name>` for a copy error: the top-level entry of
/// the install directory the failing file belongs to (`app`, `runtime`, `.` for the root).
pub fn line(error: Option<&HelperError>, install_dir: &Path) -> String {
    match error {
        None => format!("{}ok", MARKER),
        Some(e) => {
            let mut text = format!("{}{}", MARKER, code(e));
            if let Some(folder) = failed_path(e).and_then(|p| top_folder(p, install_dir)) {
                text.push_str(" folder=");
                text.push_str(&folder);
            }
            text
        }
    }
}

/// Fixed code of an error (stable: the application and the telemetry rely on it).
pub fn code(error: &HelperError) -> &'static str {
    match error {
        HelperError::InvalidArgs(_) => "invalid_args",
        HelperError::ConfigRead(_) | HelperError::ConfigParse(_) | HelperError::SchemaMismatch { .. } => "config",
        HelperError::ParentWaitTimeout => "parent_timeout",
        HelperError::ParentWaitFailed(_) => "parent_wait_failed",
        HelperError::CopyExhausted(_) => "copy_exhausted",
        HelperError::CopyFailed { .. } => "copy_failed",
        HelperError::RestartFailed(_) => "restart_failed",
        HelperError::Io(_) => "io",
    }
}

fn failed_path(error: &HelperError) -> Option<&Path> {
    match error {
        HelperError::CopyExhausted(p) => Some(p),
        HelperError::CopyFailed { path, .. } => Some(path),
        _ => None,
    }
}

/// The first component of `path` below `install_dir`, if it is a plain short name.
fn top_folder(path: &Path, install_dir: &Path) -> Option<String> {
    let rel = path.strip_prefix(install_dir).ok()?;
    let mut parts = rel.components();
    let first = parts.next()?;
    if parts.next().is_none() {
        return Some(".".to_string()); // a file directly in the install directory
    }
    match first {
        Component::Normal(name) => {
            let name = name.to_str()?;
            let plain = name.len() <= 32
                && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.');
            if plain { Some(name.to_string()) } else { None }
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io;
    use std::path::PathBuf;

    fn install() -> PathBuf {
        PathBuf::from("/opt/FreeXmlToolkit")
    }

    #[test]
    fn success_is_ok() {
        assert_eq!(line(None, &install()), "RESULT ok");
    }

    #[test]
    fn a_locked_file_names_its_top_folder_only() {
        let e = HelperError::CopyExhausted(install().join("runtime").join("bin").join("server").join("jvm.dll"));
        assert_eq!(line(Some(&e), &install()), "RESULT copy_exhausted folder=runtime");
    }

    #[test]
    fn a_file_in_the_root_is_dot() {
        let e = HelperError::CopyFailed {
            path: install().join("FreeXmlToolkit.exe"),
            source: io::Error::new(io::ErrorKind::Other, "x"),
        };
        assert_eq!(line(Some(&e), &install()), "RESULT copy_failed folder=.");
    }

    #[test]
    fn a_path_outside_the_install_has_no_folder() {
        let e = HelperError::CopyExhausted(PathBuf::from("/home/someone/secret/file.txt"));
        assert_eq!(line(Some(&e), &install()), "RESULT copy_exhausted");
    }

    #[test]
    fn other_errors_carry_their_code_only() {
        assert_eq!(line(Some(&HelperError::ParentWaitTimeout), &install()), "RESULT parent_timeout");
        assert_eq!(
            line(Some(&HelperError::RestartFailed("C:\\Users\\x".into())), &install()),
            "RESULT restart_failed"
        );
    }
}
