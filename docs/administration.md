# Administration and Policies

This page is for administrators who install FreeXmlToolkit on many computers. Two optional
files in one folder set the application up for every user on a computer:

| File | Effect |
|---|---|
| `policy.properties` | **Locked settings.** Each line overrides the user's own setting. Users cannot change it: the control is greyed out in Settings, and a notice at the top of the Settings page says that the administrator manages it. |
| `defaults.properties` | **Defaults.** Each line applies until the user changes the setting. |

The [command line](command-line.md) (`fxt-cli`) reads the same files.

## Where the files go

| System | Folder |
|---|---|
| Windows | `%ProgramData%\FreeXmlToolkit\` (usually `C:\ProgramData\FreeXmlToolkit\`) |
| macOS | `/Library/Application Support/FreeXmlToolkit/` |
| Linux | `/etc/freexmltoolkit/` |

To keep the files somewhere else, for example on a network share, set the environment variable
`FXT_POLICY_FILE` machine-wide to the full path of the policy file. The Java option
`-Dfxt.policy.file=<path>` does the same. `defaults.properties` is then read from the same
folder.

Users only need read access; only administrators should be able to write. The application reads
the files at start, so users have to restart it after a change.

## File format

The files use the format of `~/.freeXmlToolkit/FreeXmlToolkit.properties`: one `key=value` per line,
and `#` starts a comment. Write backslashes in Windows paths twice (`C:\\Schemas`) or use
forward slashes (`C:/Schemas`). Characters outside Latin-1 have to be written as `\uXXXX`.

## Settings you can set

Any key of the user's settings file works. These are the ones most often managed:

| Key | Values | Setting |
|---|---|---|
| `update.allowed` | `false` | **Policy only.** No update check, no **Check for Updates** in the Help menu or the About dialog, no update installs. Use it when software distribution delivers new versions. |
| `update.check.enabled` | `true` / `false` | Automatic update check at start (Settings → Updates) |
| `telemetry.usage.enabled`, `telemetry.errors.enabled` | `true` / `false` | Anonymous usage statistics and error reports (Settings → Privacy) |
| `fxt.telemetry.disabled` | `true` | Telemetry fully off, without the first-start notice (see [Privacy & Telemetry](telemetry.md)) |
| `useSystemProxy`, `manualProxy` | `true` / `false` | Proxy mode: system (`useSystemProxy=true`), manual (`manualProxy=true`), or none (both `false`) |
| `http.proxy.host`, `http.proxy.port` | host, port | Manual proxy |
| `http.proxy.nonProxyHosts` | `localhost, 127.*, *.corp` | Addresses reached without the proxy |
| `http.proxy.domain`, `http.proxy.windowsSignOn` | domain, `true` / `false` | NTLM proxy: domain; sign on with the Windows account |
| `http.connectTimeoutSeconds`, `http.readTimeoutSeconds` | seconds | Network timeouts |
| `ssl.trustAllCerts` | `false` | Lock "Trust all certificates" off |
| `security.xslt.allow.extensions` | `false` | Lock Java extension calls in stylesheets off |
| `security.xproc.unrestricted` | `false` | Lock the XProc restrictions on: no `p:os-exec`, `p:run`, `p:http-request`, no network (see [Threat Model](threat-model.md)) |
| `security.htmlPreview.allowActiveContent` | `false` | Lock JavaScript and remote content in HTML previews off |
| `security.dtd.allowed` | `false` | No DTD validation and no DTD loading; DOCTYPEs are only checked for well-formedness |
| `editor.editedWithComment` | `true` / `false` | "Edited with FreeXmlToolkit" comment in saved XML files (Settings → Editor; default on) |
| `standards.expertMode` | `true` / `false` | Choice of language versions (Settings → Schemas & Validation → Standards); see [Language Versions](language-versions.md) |
| `standards.xpath`, `standards.xquery`, `standards.xslt` | `1.0` / `2.0` / `3.0` / `3.1` (as the language has them) | Default XPath, XQuery and XSLT version in expert mode |
| `standards.xsd`, `standards.xml` | `auto` / `1.0` / `1.1` | Default XSD and XML version in expert mode |
| `standards.schematronBinding` | `default` / `xslt` / `xslt2` / `xslt3` | Default query binding of a Schematron without `@queryBinding` in expert mode |
| `fundsxml.enabled` | `true` / `false` | FundsXML extension |
| `ui.language` | `system` / `en` / `de` | Language of the interface (Settings → Appearance → Language) |
| `ui.theme` | `light` / `dark` / `system` | Theme (Settings → Appearance → Theme) |
| `ui.accent` | `blue` / `indigo` / `violet` / `green` / `teal` / `orange` / `red` / `graphite` or `#rrggbb` | Accent colour (Settings → Appearance → Accent colour), e.g. the company colour |
| `ui.font.size` | `small` / `normal` / `large` / `xlarge` | Text size of the interface (Settings → Appearance → Text size & font) |
| `ui.font.family` | font family name (empty = Inter) | Font of the interface |
| `ui.density` | `compact` / `normal` / `comfortable` | Density of the interface |
| `ui.contrast.high` | `true` / `false` | High contrast (Settings → Appearance → Theme) |
| `syntax.scheme` | `default` / `github` / `solarized` / `monokai` / `oxygen` / `xmlspy` / `high-contrast` | Syntax colour scheme (Settings → Editor → Syntax colours) |
| `syntax.color.<role>.<light\|dark>` | `#rrggbb` (empty = the scheme's) | Own colour of a role: `text`, `element`, `attribute`, `value`, `punctuation`, `comment`, `number`, `keyword`, `declaration` |
| `editor.font.family` | font family name (empty = JetBrains Mono) | Editor font (Settings → Editor → Editor font) |
| `editor.font.size` | `8`–`32` (points) | Editor font size |
| `editor.lineSpacing` | `1.0`–`2.0` | Editor line spacing |
| `editor.zoom.remember` | `true` / `false` | Ctrl+mouse wheel changes and keeps the editor size |
| `plugins.external.allowed` | `false` | **Policy only.** Load no [plugins](plugins.md) from the plugins folder (built-in ones still load) |
| `fop.external.allowed` | `false` | **Policy only.** Only the bundled Apache FOP renders PDFs; [external XSL-FO processors](pdf-generator.md#external-xsl-fo-processors) are hidden and refused (panel, scenarios, `fxt-cli fop`) |
| `fop.processors` | JSON list | The external XSL-FO processors (as written by *Settings → XSL-FO Processors*); in `defaults.properties` a starting list, in `policy.properties` a fixed one |
| `fop.processor.default` | `builtin` or an id | The default XSL-FO processor |
| `xmlenc.allowed` | `false` | **Policy only.** No [XML encryption](xml-encryption.md) or decryption (page, editor menu and `fxt-cli`) |
| `xmlenc.legacyAlgorithms.allowed` | `false` | **Policy only.** Block the legacy XML encryption algorithms (RSA-1_5, TripleDES, CBC modes, finite-field DH) for encryption and decryption |
| `ai.allowed` | `false` | **Policy only.** Every AI feature off: the [AI assistant](ai-assistant.md) (no Settings group, no menu items) and the [MCP server](mcp-server.md) (`fxt-cli mcp` refuses to start) |
| `ai.mcp.allowed` | `false` | **Policy only.** `fxt-cli mcp` refuses to start, so AI agents cannot use the application's tools |
| `ai.remote.allowed` | `false` | **Policy only.** The [AI assistant](ai-assistant.md) may use only providers on this computer or hosts in `ai.localHosts` |
| `ai.enabled`, `ai.dataLevel`, `ai.dataLevel.remote`, `ai.preview.always` | `true`/`false`; `SCHEMA` / `STRUCTURE` / `EXCERPTS` | AI assistant on/off, what may be sent to local / remote providers, preview every request (Settings → AI) |
| `ai.providers`, `ai.provider.default` | JSON list, provider id | The AI providers; in `policy.properties` a fixed list. API keys are never read from the machine-wide files |
| `ai.localHosts` | host list | Hosts that count as local (a company gateway) |
| `plugin.<id>.enabled` | `true` / `false` | A [plugin](plugins.md) on or off (Settings → Plugins), e.g. `plugin.fundsxml.enabled` |
| `schemaLibrary.autoBind.enabled` | `true` / `false` | Bind schemas from the Schema Library automatically |
| `xml.parser.type` | `XERCES` / `SAXON` | XSD validator (XSD 1.1 schemas always use Xerces) |
| `memory.max.mb` | megabytes, `0` = automatic | Maximum memory (Settings → Memory) |
| `templates.directory` | folder | Shared template folder |
| `useSystemTempFolder`, `customTempFolder` | `true` / `false`, folder | Folder for temporary files |
| `log.cleanup.enabled`, `log.cleanup.maxAgeDays`, `log.cleanup.maxSizeMb` | | Log file cleanup |
| `xml.indent.spaces`, `json.indent.spaces` | number | Indentation when formatting |

Keys that start with `fxt.` are Java options rather than settings: they replace the
`-D…` options from [Operation without Internet](offline-operation.md) and are the only way to set
them on macOS, where the launcher configuration cannot be changed. Those in `policy.properties`
always apply. Those in `defaults.properties` apply unless the option is given on the command line.

| Key | Effect |
|---|---|
| `fxt.telemetry.disabled=true` | No telemetry at all |
| `fxt.schema.namespaceFallback=false` | Never download a schema by its namespace URL; bundled Schema Library entries only from the cache |
| `fxt.schema.localCopies=false` | Never write downloaded schemas next to the schema that imports them |
| `fxt.locale=de` | Language of the interface, whatever the setting says (`en`, `de`) |
| `fxt.plugins.dir=<folder>` | Load [plugins](plugins.md) from this folder instead of `~/.freeXmlToolkit/plugins` |

!!! warning "No passwords in these files"
    Every user can read the files. Do not put a proxy password into them. Use the system proxy
    or the Windows sign-on (`http.proxy.windowsSignOn=true`) instead.

## Examples

A computer without internet access, on which users cannot switch anything back on:

```properties
# C:\ProgramData\FreeXmlToolkit\policy.properties
update.allowed=false
fxt.telemetry.disabled=true
telemetry.usage.enabled=false
telemetry.errors.enabled=false
fundsxml.enabled=false
fxt.schema.namespaceFallback=false
ssl.trustAllCerts=false
security.xslt.allow.extensions=false
security.xproc.unrestricted=false
security.htmlPreview.allowActiveContent=false
```

A company proxy that users can still change, with a shared template folder:

```properties
# /etc/freexmltoolkit/defaults.properties
useSystemProxy=false
manualProxy=true
http.proxy.host=proxy.example.com
http.proxy.port=8080
http.proxy.nonProxyHosts=localhost, 127.*, *.example.com
templates.directory=/srv/shared/fxt-templates
```

## Silent installation

| System | Command |
|---|---|
| Windows (MSI) | `msiexec /i FreeXmlToolkit-x64-<version>.msi /qn /l*v fxt-install.log` (optionally `INSTALLDIR="D:\Apps\FreeXmlToolkit"`) |
| macOS (PKG) | `sudo installer -pkg FreeXmlToolkit-arm64-<version>.pkg -target /` (Intel: `FreeXmlToolkit-x64-…`) |
| Debian / Ubuntu | `sudo apt-get install -y ./freexmltoolkit-x64_<version>_amd64.deb` |
| Fedora / RHEL / openSUSE | `sudo rpm -i freexmltoolkit-x64-<version>-1.x86_64.rpm` |

Distribute `policy.properties` (and `defaults.properties`) with the same tool, for example with
group policy preferences (*Computer Configuration → Preferences → Windows Settings → Files*),
Intune, Jamf or a configuration-management system. An update of the application does not touch
these files.

With `update.allowed=false`, new versions only arrive through your distribution. Each release
publishes SHA-256 checksums and an SBOM (see [Licenses](licenses.md#software-bill-of-materials-sbom)).

## Checking what applies

- The Settings page shows the notice, with the path of the policy file, when anything is locked.
  Locked controls are greyed out.
- At start, the log names the folder and the number of locked settings and defaults.
- The user's own file (`~/.freeXmlToolkit/FreeXmlToolkit.properties`) keeps only the user's own
  values. Removing a key from the policy brings the user's previous value back.

See also the [Threat Model](threat-model.md) and [Operation without Internet](offline-operation.md).
