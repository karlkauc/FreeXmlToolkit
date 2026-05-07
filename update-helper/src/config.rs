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
