// Embed the FreeXmlToolkit application icon into the helper .exe so the
// binary shows up with a meaningful glyph in Windows Explorer / Task Manager.
// The icon is the same logo.ico bundled with the main app; using one source
// of truth avoids drift between launcher and helper visuals.
//
// On non-Windows targets this is a no-op — the helper itself is Windows-only,
// but cargo test on Linux/Mac dev machines must still succeed.

fn main() {
    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os != "windows" {
        return;
    }

    println!("cargo:rerun-if-changed=icon/logo.ico");

    #[cfg(windows)]
    {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("icon/logo.ico");
        if let Err(e) = res.compile() {
            // Soft-fail: a missing rc tool degrades the binary cosmetically
            // (no icon) but the helper still works. The CI Windows runner
            // ships rc.exe via the Windows SDK so this normally succeeds.
            println!("cargo:warning=Failed to embed icon: {}", e);
        }
    }
}
