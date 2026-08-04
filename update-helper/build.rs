// Embed the FreeXmlToolkit application icon and an application manifest into
// the helper .exe. The icon is the same logo.ico bundled with the main app;
// using one source of truth avoids drift between launcher and helper visuals.
// The manifest (manifest.xml) requests asInvoker so UAC "installer detection"
// never demands elevation for the helper — without it, locked-down machines
// refuse the unelevated launch with CreateProcess error=740.
//
// On non-Windows targets this is a no-op — the helper itself is Windows-only,
// but cargo test on Linux/Mac dev machines must still succeed.

fn main() {
    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os != "windows" {
        return;
    }

    println!("cargo:rerun-if-changed=icon/logo.ico");
    println!("cargo:rerun-if-changed=manifest.xml");

    #[cfg(windows)]
    {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("icon/logo.ico");
        res.set_manifest_file("manifest.xml");
        if let Err(e) = res.compile() {
            // Soft-fail: a missing rc tool degrades the binary (no icon, no
            // manifest — UAC heuristics may then require elevation again, but
            // the Java-side error-740 fallback covers that case). The CI
            // Windows runner ships rc.exe via the Windows SDK so this
            // normally succeeds, and the release workflow asserts the
            // manifest is present in the shipped exe.
            println!("cargo:warning=Failed to embed icon/manifest: {}", e);
        }
    }
}
