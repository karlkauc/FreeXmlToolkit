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

    // Capture the copy outcome but DO NOT bail out on failure: the launcher
    // .exe and the application JAR sit alphabetically before runtime/bin
    // (the lock-prone subtree), so by the time a copy fails most user-visible
    // files are already updated. Restarting still gives the user the new
    // version; aborting here would force them to start the app manually.
    let copy_result = copy::copy_tree(
        &source_dir,
        &config.install_dir,
        config.copy_retry_initial_ms,
        config.copy_retry_max,
        &log,
    );
    match &copy_result {
        Ok(count) => log.info(&format!("Copied {} files", count)),
        Err(e) => log.error(&format!(
            "Copy partially failed: {}. Attempting restart anyway.",
            e
        )),
    }

    log.info(&format!("Restarting: {}", config.launcher_path.display()));
    let restart_result = restart::restart_application(&config.launcher_path, &log);
    if let Err(e) = &restart_result {
        log.error(&format!("Restart failed: {}", e));
    }

    cleanup::delete_dir_quietly(&config.extracted_dir, &log);

    match (copy_result, restart_result) {
        (Ok(_), Ok(())) => {
            log.info("UpdateHelper completed successfully (exit 0)");
            Ok(())
        }
        (Err(copy_err), Ok(())) => {
            log.warn("UpdateHelper completed with copy errors; restart succeeded");
            Err(copy_err)
        }
        (Ok(_), Err(restart_err)) => Err(restart_err),
        // Both failed: surface the copy error since it's the root cause.
        (Err(copy_err), Err(_)) => Err(copy_err),
    }
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
