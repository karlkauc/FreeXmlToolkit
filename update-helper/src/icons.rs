//! Makes Windows show the icons of the updated files instead of cached ones.
//!
//! Explorer keeps file-type and shortcut icons in its icon cache. After an update that changes the
//! application icon, the taskbar pin, the Start menu and the files associated with the application
//! kept the old icon until the next sign-in. Best effort: a failure is logged and changes nothing.

use crate::logger::Logger;

/// Tells Explorer that file associations changed and asks it to rebuild its icon cache.
pub fn refresh_icon_cache(log: &Logger) {
    #[cfg(windows)]
    {
        win::notify_association_change();
        match win::spawn_ie4uinit() {
            Ok(()) => log.info("Asked Explorer to refresh its icons"),
            Err(e) => log.warn(&format!("Icon refresh not started: {}", e)),
        }
    }

    #[cfg(not(windows))]
    {
        let _ = log;
    }
}

#[cfg(windows)]
mod win {
    use std::os::windows::process::CommandExt;
    use std::process::{Command, Stdio};
    use std::ptr;
    use windows_sys::Win32::UI::Shell::{SHChangeNotify, SHCNE_ASSOCCHANGED, SHCNF_IDLIST};

    /// No console window for the helper process.
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;

    pub fn notify_association_change() {
        unsafe { SHChangeNotify(SHCNE_ASSOCCHANGED as i32, SHCNF_IDLIST, ptr::null(), ptr::null()) };
    }

    /// `ie4uinit.exe -show` refreshes the icon cache (Windows 10 and 11); started, not awaited.
    pub fn spawn_ie4uinit() -> std::io::Result<()> {
        Command::new("ie4uinit.exe")
            .arg("-show")
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .creation_flags(CREATE_NO_WINDOW)
            .spawn()
            .map(|_| ())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn refresh_never_panics() {
        let dir = std::env::temp_dir().join(format!("fxt-icons-test-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let log = Logger::open(&dir.join("helper.log"));
        refresh_icon_cache(&log);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
