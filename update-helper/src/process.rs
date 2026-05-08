use std::time::Duration;

use crate::error::{HelperError, Result};
use crate::logger::Logger;

/// Number of FILETIME ticks (100-ns) per millisecond.
const TICKS_PER_MS: i64 = 10_000;

/// Truncate a Win32 FILETIME value to millisecond granularity.
///
/// Java's `ProcessHandle.info().startInstant()` reports the parent's start
/// time at millisecond precision (the JDK divides FILETIME by 10000 when
/// converting to `Instant`). Calling `GetProcessTimes` directly from Rust
/// returns the original 100-ns precision. Comparing the two without
/// alignment causes every legitimate match to look "recycled" by a few
/// hundred microseconds. Truncating both sides to the same coarser grid
/// solves this; real PID recycling produces deltas of seconds or more, so
/// 1-ms granularity still catches it.
#[cfg_attr(not(windows), allow(dead_code))]
fn truncate_to_ms(filetime: i64) -> i64 {
    (filetime / TICKS_PER_MS) * TICKS_PER_MS
}

/// Wait for the parent process (identified by PID + creation time) to exit.
/// Returns Ok(()) when parent has exited (or never existed by the time we look).
/// Returns Err(ParentWaitTimeout) if the timeout expires while parent is alive.
///
/// The creation_time check defends against PID recycling: if a different
/// process now holds the PID (its creation time will differ), we treat the
/// original parent as gone. Comparison is done at millisecond granularity
/// to match the precision Java emits via `Instant.toEpochMilli`.
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
    use windows_sys::Win32::Storage::FileSystem::SYNCHRONIZE;
    use windows_sys::Win32::System::Threading::{
        GetProcessTimes, OpenProcess, WaitForSingleObject,
        PROCESS_QUERY_LIMITED_INFORMATION,
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
        if handle.is_null() {
            log.info(&format!("Parent PID {} already gone (OpenProcess returned NULL)", parent_pid));
            return Ok(());
        }

        // PID-recycling defense (compare at millisecond granularity; see
        // `truncate_to_ms` doc-comment for why).
        match get_creation_time(handle) {
            Ok(actual)
                if truncate_to_ms(actual) != truncate_to_ms(expected_creation_time) =>
            {
                unsafe { CloseHandle(handle) };
                log.warn(&format!(
                    "PID {} appears recycled (creation time {} != expected {}, ms-truncated {} != {}); treating as exited",
                    parent_pid,
                    actual,
                    expected_creation_time,
                    truncate_to_ms(actual),
                    truncate_to_ms(expected_creation_time)
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

    #[test]
    fn truncate_to_ms_drops_sub_millisecond_ticks() {
        // 1 ms = 10_000 FILETIME ticks; sub-ms portion must be discarded.
        assert_eq!(truncate_to_ms(134_226_980_863_754_612), 134_226_980_863_750_000);
        assert_eq!(truncate_to_ms(134_226_980_863_750_000), 134_226_980_863_750_000);
        assert_eq!(truncate_to_ms(0), 0);
        assert_eq!(truncate_to_ms(9_999), 0);
        assert_eq!(truncate_to_ms(10_000), 10_000);
    }

    #[test]
    fn ms_truncation_treats_java_emitted_value_as_equal() {
        // Regression for the v1.9.1 update failure: Java's
        // `Instant.toEpochMilli`-based FILETIME emit is millisecond-aligned,
        // while Rust's GetProcessTimes returns the full 100-ns value. They
        // must compare equal when they describe the same process.
        let rust_filetime = 134_226_980_863_754_612_i64;
        let java_filetime = 134_226_980_863_750_000_i64;
        assert_ne!(
            rust_filetime, java_filetime,
            "raw values differ — that's the bug condition"
        );
        assert_eq!(
            truncate_to_ms(rust_filetime),
            truncate_to_ms(java_filetime),
            "ms-truncated values must match for the same process"
        );
    }

    #[test]
    fn ms_truncation_still_detects_real_pid_recycling() {
        // Real PID recycling produces creation-time deltas of seconds or
        // more (the original process must exit before its PID is reusable).
        // 1 second = 10_000_000 FILETIME ticks.
        let original = 134_226_980_863_750_000_i64;
        let recycled = original + 10_000_000; // +1s
        assert_ne!(
            truncate_to_ms(original),
            truncate_to_ms(recycled),
            "second-scale delta must still trip the recycling guard"
        );
    }
}
