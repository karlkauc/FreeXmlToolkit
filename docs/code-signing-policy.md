# Code signing policy

Free code signing for Windows is provided by [SignPath.io](https://about.signpath.io/),
certificate by [SignPath Foundation](https://signpath.org/).

## What is signed

Only release artifacts that are built by the project's public GitHub Actions workflow
([`build-packages-on-new-release.yml`](https://github.com/karlkauc/FreeXmlToolkit/blob/main/.github/workflows/build-packages-on-new-release.yml))
from the tagged source code of this repository are signed:

- the Windows installers `FreeXmlToolkit-x64-<version>.msi` and `FreeXmlToolkit-x64-<version>.exe`
- the portable Windows package `FreeXmlToolkit-windows-x64-app-image-<version>.zip`
- inside these packages: the application launcher `FreeXmlToolkit.exe`, the update helper
  `fxt-update-helper.exe` and the executables and libraries of the bundled Java runtime

Nothing is signed from a developer's machine. Builds from forks, pull requests or manual
workflow runs are not signed.

Third-party components bundled in the packages (the Java runtime, JavaFX and the Java
libraries listed in [`build.gradle.kts`](https://github.com/karlkauc/FreeXmlToolkit/blob/main/build.gradle.kts))
are open source and are signed as part of the FreeXmlToolkit distribution only.

## Team roles

| Role | Members |
|------|---------|
| Committers and reviewers | [Karl Kauc](https://github.com/karlkauc) |
| Approvers | [Karl Kauc](https://github.com/karlkauc) |

Every signing request is approved manually by an approver. All team members use
multi-factor authentication for GitHub and SignPath.

## Privacy policy

FreeXmlToolkit connects to other systems only for the following purposes:

- **Anonymous usage statistics and error reports** — on by default and can be switched off
  in *Settings → Usage Statistics*. Nothing is sent before the one-time notice was shown after the
  first start. No file contents, file names, paths or personal data are transmitted. Details:
  [Privacy & Anonymous Telemetry](https://karlkauc.github.io/FreeXmlToolkit/telemetry/).
- **Update check** — asks the GitHub Releases API for the latest version at startup (switch off
  with *Check for updates on startup* in the settings). Updates are only downloaded and
  installed when the user confirms them.
- **FundsXML extension** — off by default; when enabled, downloads and updates the FundsXML
  schemas, samples and rules from GitHub.
- **Schemas and stylesheets** referenced by documents the user opens (e.g. `xs:import`,
  `schemaLocation`, catalogs) are downloaded from the locations given in those documents.

Apart from these, the program does not transfer any information to other networked systems
unless specifically requested by the user.

## Reporting problems

If you notice a signed file that was not produced by this project, or a signature you do not
trust, please open an issue at <https://github.com/karlkauc/FreeXmlToolkit/issues> or contact
the maintainer via the e-mail address on the GitHub profile.
