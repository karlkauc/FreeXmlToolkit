# fxt-update-helper

Native Windows auto-update helper for FreeXmlToolkit.

## Build

```
cargo build --release
```

Output: `target/release/fxt-update-helper.exe` (~500 KB).

## Test

```
cargo test
```

Tests are cross-platform; Win32-specific code is gated behind `#[cfg(windows)]`.

## Usage

The helper is invoked by the main app, not directly by users:

```
fxt-update-helper.exe <path-to-helper-config.toml>
```

See `docs/superpowers/specs/2026-05-07-windows-auto-update-redesign.md` for design.
