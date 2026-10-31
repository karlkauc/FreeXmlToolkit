# Installation

FreeXmlToolkit is free and needs no registration. All files are published on the
[GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases/latest); the
[download buttons on the home page](index.md#download) always point to the latest release.

## Installing

1. **Download** the package for your operating system using the [download buttons](index.md#download) on the home page (or from the [GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases/latest))
2. **Install** it:
    - **Windows:** run the `.exe` installer (no administrator rights required) or the `.msi`; the portable `.zip` only needs to be extracted
    - **macOS:** open the `.dmg` and drag FreeXmlToolkit to *Applications*, or run the `.pkg` installer
    - **Linux:** `sudo dpkg -i freexmltoolkit-*.deb` (Debian/Ubuntu) or `sudo rpm -i freexmltoolkit-*.rpm` (Fedora/RHEL/openSUSE); the portable `.zip` only needs to be extracted
3. **Launch** FreeXmlToolkit from your applications menu (portable: run `FreeXmlToolkit` / `FreeXmlToolkit.exe` in the extracted folder)

On first start the app opens on the **Welcome** page - continue with
[Your First Steps](index.md#your-first-steps) or the [Unified Shell](unified-shell.md) guide.

## Download Formats


FreeXmlToolkit is available for Windows, macOS, and Linux in multiple formats. Choose the one that works best for you - all files are published on the [GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases/latest) (see the [download buttons](index.md#download) on the home page).

### Windows

| Format | Architecture | Description |
|--------|--------------|-------------|
| `.exe` | x64 | **Installer** - Standard setup wizard. No admin rights required. Recommended for most users. |
| `.msi` | x64 | **MSI Package** - For enterprise deployment and silent installation via Group Policy. |
| `.zip` | x64 | **Portable** - No installation needed. Extract and run. Good for USB drives or restricted systems. |

### macOS

| Format | Architecture | Description |
|--------|--------------|-------------|
| `.dmg` | Intel (x64), Apple Silicon (ARM64) | **Disk Image** - Standard macOS installation. Drag to Applications folder. |
| `.pkg` | Intel (x64), Apple Silicon (ARM64) | **Package Installer** - Guided installation wizard. Useful for managed deployments. |
| `.zip` | Intel (x64), Apple Silicon (ARM64) | **Portable** - No installation needed. Extract and run the app directly. |

The macOS packages are signed with an Apple Developer ID and notarized by Apple, so they open without a Gatekeeper warning.

### Linux

| Format | Architecture | Description |
|--------|--------------|-------------|
| `.deb` | x64 | **Debian Package** - For Ubuntu, Debian, Linux Mint. Install with `sudo dpkg -i` or double-click. |
| `.rpm` | x64 | **RPM Package** - For Fedora, Red Hat, openSUSE. Install with `sudo rpm -i` or `dnf install`. |
| `.zip` | x64 | **Portable** - No installation needed. Extract and run. Works on any Linux distribution. |

> **Note:** On macOS, pick the build that matches your Mac: **Apple Silicon (ARM64)** for M-series Macs, **Intel (x64)** for older Macs (Apple menu → *About This Mac* shows the chip). Windows and Linux builds are provided for **x64** (Intel/AMD) only.

## Install with a Package Manager

If you already use a package manager, it can install FreeXmlToolkit for you and keep it up to date. The packages are published with every release and contain the same builds as the download buttons on the home page.

### Windows – Scoop

[Scoop](https://scoop.sh) installs the portable build into your user profile – no installer, no admin rights. Add the FreeXmlToolkit bucket once, then install:

```powershell
scoop bucket add karlkauc https://github.com/karlkauc/scoop-bucket
scoop install karlkauc/freexmltoolkit
```

Scoop adds **FreeXmlToolkit** to the Start menu. Update and remove it with:

```powershell
scoop update freexmltoolkit
scoop uninstall freexmltoolkit
```

### macOS – Homebrew

[Homebrew](https://brew.sh) installs the signed and notarized app into *Applications* and picks the Apple Silicon or Intel build automatically (macOS 11 Big Sur or newer):

```bash
brew install --cask karlkauc/tap/freexmltoolkit
```

Update and remove it with:

```bash
brew upgrade --cask freexmltoolkit
brew uninstall --cask freexmltoolkit
```

`brew uninstall --zap --cask freexmltoolkit` also removes your settings (`~/.freeXmlToolkit`).

### Updates

A copy installed by Scoop or Homebrew is updated by the package manager: when a new version is out, the update notice in FreeXmlToolkit shows the command to run instead of downloading the new version itself.

On Linux, use the `.deb` / `.rpm` packages or the portable ZIP.
