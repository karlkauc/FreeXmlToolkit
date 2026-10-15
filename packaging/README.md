# Package-manager distribution

FreeXmlToolkit is published through winget, Scoop, a Homebrew cask and Flathub. Every
package repackages an asset of the GitHub release, nothing is built separately.

| Manager  | Package / ID                          | Release asset used                             | Updated by                                  |
|----------|---------------------------------------|------------------------------------------------|---------------------------------------------|
| winget   | `KarlKauc.FreeXmlToolkit`             | `FreeXmlToolkit-x64-<v>.msi`                   | job `publish-winget` (winget-releaser → PR) |
| Scoop    | `freexmltoolkit` in own bucket        | `FreeXmlToolkit-windows-x64-app-image-<v>.zip` | job `publish-scoop` (push to bucket)        |
| Homebrew | `freexmltoolkit` cask in own tap      | `FreeXmlToolkit-{arm64,x64}-<v>.dmg`           | job `publish-homebrew` (push to tap)        |
| Flathub  | `io.github.karlkauc.FreeXmlToolkit`   | `FreeXmlToolkit-linux-x64-app-image-<v>.zip`   | Flathub bot via `x-checker-data` (PR)       |

```
packaging/
├── render-manifests.py          # fills @@VERSION@@ / @@SHA256_*@@ from SHA256SUMS.txt
├── scoop/freexmltoolkit.json    # Scoop manifest template
├── homebrew/freexmltoolkit.rb   # Homebrew cask template
├── flatpak/                     # Flathub manifest template + flathub.json
└── linux/share/                 # desktop entry, AppStream metainfo, icons (shipped in the Linux app-image ZIP)
```

## Release flow

`build-packages-on-new-release.yml`:

1. `build` – all packages (unchanged).
2. `checksums` – writes `SHA256SUMS.txt` from GitHub's asset digests and attaches it to the release.
3. `publish-scoop`, `publish-homebrew`, `publish-winget` – skipped for prereleases and while
   their repository variable is unset (see below).

Flathub needs no job: its bot polls the GitHub API and opens an update PR in
`flathub/io.github.karlkauc.FreeXmlToolkit`, which is merged there.

Per release, `release.sh` adds a `<release>` entry to the AppStream metainfo;
`PackagingMetadataTest` fails if the current version is missing.

## Self-update vs. package managers

Package-manager installs never update themselves. `InstallSourceDetector`
(`service/update/`) recognises them (`FXT_INSTALL_SOURCE`, `FLATPAK_ID`, the
`.fxt-install-source` marker written by Scoop's `post_install`, the Scoop path, the Homebrew
Caskroom, `winget list`); the update dialog then shows the upgrade command instead of
"Download & Install", and Flatpak skips the update check entirely.

The MSI `UpgradeCode` is pinned (`WINDOWS_UPGRADE_UUID` in `build.gradle.kts`) to the value
jpackage derived up to 2.4.0 — never change it, or MSI upgrades and winget matching break.

## One-time setup

### Scoop bucket
1. Create the public repo `karlkauc/scoop-bucket` (e.g. from the
   [ScoopInstaller/BucketTemplate](https://github.com/ScoopInstaller/BucketTemplate)).
2. Repository variable `SCOOP_BUCKET_REPO = karlkauc/scoop-bucket`.
3. Users: `scoop bucket add karlkauc https://github.com/karlkauc/scoop-bucket` →
   `scoop install karlkauc/freexmltoolkit`.

### Homebrew tap
1. Create the public repo `karlkauc/homebrew-tap` (the `homebrew-` prefix is mandatory).
2. Repository variable `HOMEBREW_TAP_REPO = karlkauc/homebrew-tap`.
3. Users: `brew install --cask karlkauc/tap/freexmltoolkit`.
4. Later, once the DMGs are signed and notarized (secrets in `scripts/README.md`) and the
   project meets Homebrew's notability rules, submit the cask to `homebrew/homebrew-cask`.

`PACKAGING_REPO_TOKEN` (secret): fine-grained PAT with *Contents: read & write* on the bucket
and the tap.

### winget
1. Install `wingetcreate` on Windows and submit the first version manually:
   `wingetcreate new https://github.com/karlkauc/FreeXmlToolkit/releases/download/v<v>/FreeXmlToolkit-x64-<v>.msi`
   (identifier `KarlKauc.FreeXmlToolkit`, license Apache-2.0). Wait until the PR in
   `microsoft/winget-pkgs` is merged.
2. Fork `microsoft/winget-pkgs` to the account that owns `WINGET_TOKEN`.
3. Secret `WINGET_TOKEN`: classic PAT with `public_repo`.
4. Repository variable `WINGET_ENABLED = true`.

Unsigned MSIs are accepted, but SmartScreen warns; Windows code signing (SignPath.io for
OSS, Azure Trusted Signing) is a recommended follow-up.

### Flathub
1. Render the manifest for the current release and test it locally:
   ```bash
   gh release download v<v> -p SHA256SUMS.txt
   python3 packaging/render-manifests.py <v> SHA256SUMS.txt /tmp/fxt flatpak
   cd /tmp/fxt/flatpak
   flatpak-builder --user --install --force-clean build-dir io.github.karlkauc.FreeXmlToolkit.yml
   flatpak run io.github.karlkauc.FreeXmlToolkit
   flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest io.github.karlkauc.FreeXmlToolkit.yml
   ```
   `flatpak-builder-lint` can only read files below `$HOME` (not `/tmp`). Under a headless
   `xvfb-run`, wrap the run in `dbus-run-session --` — without a session bus the JDK's
   system-proxy lookup in `FxtGui.init` blocks and no window appears.
2. Open a PR against the `new-pr` branch of `flathub/flathub` with the rendered manifest and
   `flatpak/flathub.json`. Explain in the PR:
   - why a prebuilt binary is repackaged (Gradle + jlink cannot build offline in flatpak-builder);
   - the linter exception for `finish-args-home-filesystem-access` (the only linter error):
     JavaFX's GTK file chooser does not use the document portal, and XML projects reference
     schemas, includes and stylesheets by relative path across the user's files.
3. After acceptance, Flathub creates `flathub/io.github.karlkauc.FreeXmlToolkit`; you get
   write access and merge the bot's update PRs there. Verify the app id via the GitHub
   namespace on flathub.org to get the "verified" badge.

## Validating manifests locally

| Manager  | Command |
|----------|---------|
| winget   | `winget validate <dir>` · `winget install --manifest <dir>` |
| Scoop    | `scoop install .\freexmltoolkit.json` · `checkver.ps1 -Dir . -App freexmltoolkit` |
| Homebrew | `brew install --cask ./freexmltoolkit.rb` · `brew audit --cask --new --online freexmltoolkit` · `brew style` |
| Flatpak  | see Flathub step 1 · `appstreamcli validate packaging/linux/share/metainfo/*.xml` · `desktop-file-validate packaging/linux/share/applications/*.desktop` |
