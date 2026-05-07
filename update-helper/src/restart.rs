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
