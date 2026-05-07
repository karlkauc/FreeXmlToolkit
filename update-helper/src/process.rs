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
        match get_creation_time(handle) {
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

    fn get_creation_time(handle: HANDLE) -> std::result::Result<i64, String> {
        let mut creation = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut exit = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut kernel = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };
        let mut user = FILETIME { dwLowDateTime: 0, dwHighDateTime: 0 };

        let ok = unsafe {
            GetProcessTimes(
                handle,
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
        // Assemble the 64-bit FILETIME value via unsigned arithmetic so the
        // result is correct by construction. Casting `dwLowDateTime` (u32)
        // straight to i64 sign-extends when bit 31 is set; doing the assembly
        // in u64 first avoids that pitfall entirely.
        ((ft.dwHighDateTime as u64) << 32 | ft.dwLowDateTime as u64) as i64
    }

    #[cfg(test)]
    mod win_tests {
        use super::*;
        use windows_sys::Win32::System::Threading::GetCurrentProcess;

        #[test]
        fn get_creation_time_works_on_own_process() {
            let me = unsafe { GetCurrentProcess() };
            let t = get_creation_time(me).expect("must work on own process");
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

    fn null_logger() -> Logger {
        let p = std::env::temp_dir().join(format!(
            "fxt-helper-test-{}.log",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        Logger::open(p)
    }

    #[test]
    fn non_windows_stub_returns_ok() {
        // On non-Windows, the stub always returns Ok.
        // On Windows, this test still works because PID 0xFFFFFFFF is invalid → OpenProcess null → Ok.
        let log = null_logger();
        let r = wait_for_parent_exit(u32::MAX, 0, Duration::from_secs(1), &log);
        assert!(r.is_ok(), "should treat invalid PID as parent gone");
    }
}
