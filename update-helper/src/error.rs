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
