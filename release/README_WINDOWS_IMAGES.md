# Windows Installer Images

The Windows installers (EXE and MSI, built by jpackage with WiX) show two images:

| File | Size | Where it appears |
|------|------|------------------|
| `win-banner.png` | 493 × 58 px | Banner across the top of the installer pages (destination folder, shortcuts, progress) |
| `win-dialog.png` | 493 × 312 px | Background of the welcome and the finish page |

## How they get into the installer

Keep the PNGs here; the build does the rest (`build.gradle.kts`, `prepareWindowsInstallerResources`):

1. Both PNGs are converted to 24-bit BMP files — Windows Installer shows BMP, not PNG.
2. jpackage's own `main.wxs` is read from the toolchain JDK and gets two WiX variables,
   `WixUIBannerBmp` and `WixUIDialogBmp`, pointing at the BMPs, inside its `Product` element.
   (Not in `overrides.wxi`: that file is included directly under `<Wix>`, where WiX allows no
   `WixVariable` — error WIX0005.) The unchanged original is kept as `main.original.wxs`.
3. The result (`build/jpackage-resources/windows/`) is passed to jpackage as `--resource-dir`.

This only works with a Windows JDK (jpackage's WiX files exist in Windows builds only), i.e. on the
Windows runners of the release workflow. `./gradlew prepareWindowsInstallerResources` converts the
images on any platform and then reports the missing `main.wxs` outside Windows; `./gradlew checkInstallerOverrides`
tests the insertion on any platform.

## Designing the images

The current images are drawn by `scripts/make-installer-images.py` (app logo, brand colours,
Inter; needs Pillow): change the script and run it again, or replace the PNGs with your own.

WiX writes the page texts **in black on top of the images**:

- **Banner:** the page title (bold) and its description start at the top left, across roughly the
  left two thirds. Keep that area light; a logo belongs at the right end.
- **Dialog:** the welcome/finish title and text start about 180 px from the left (135 dialog units),
  across the rest of the image. Keep that part light; artwork belongs in a strip of up to ~165 px
  on the left.

Use the exact sizes; other sizes are stretched. Transparency is not supported (transparent areas
become white).
