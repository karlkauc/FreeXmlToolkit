# FAQ & Troubleshooting

Answers to common questions and fixes for typical problems. If your problem is not listed,
send a report from inside the app with **Help → Report a Problem…** or open an issue on
[GitHub](https://github.com/karlkauc/FreeXmlToolkit/issues).

## General

### How does FreeXmlToolkit compare to Oxygen XML Editor, XMLSpy or XMLBlueprint?

See [Comparison with Other Editors](comparison.md): a feature table with sources, including the
areas where the other editors offer more.

## Starting and Display

### Application Does Not Start (Windows)

On some Windows computers, the application may fail to start or crash immediately after launch. This is typically caused by incompatible graphics drivers.

**What to try:**

1. **Update your graphics drivers** -- Visit your GPU manufacturer's website (NVIDIA, AMD, or Intel) and install the latest driver for your graphics card.
2. **Try the portable version** -- If you used the installer (.exe or .msi), try the portable (.zip) version instead.
3. **Report the issue** -- If the problem persists, please report it on the project's GitHub Issues page with your Windows version and graphics card model.

> **Note:** FreeXmlToolkit picks a safe renderer automatically (software rendering on graphics adapters it does not recognise). You can force hardware or software rendering under **Settings → Appearance → RENDERING**; the change takes effect after a restart.

### Application Looks Blurry or Slow

If the interface appears blurry or animations are slow, your system may be using software rendering instead of hardware acceleration. Updating your graphics drivers usually resolves this.

## Large Files and Memory

### A large file does not open or the app runs out of memory

The editor keeps a document in memory several times over (text, highlighting, structured
views). Raise the limit under **Settings → Appearance → MEMORY** (**Automatic** uses half of the
installed memory) and restart the application. The [Memory and large files](settings.md#memory)
section lists how large a file each limit allows. To look at, search or copy from a file of any
size without raising the limit, open it in the [Large File Viewer](large-files.md).

## Network

### Downloads fail behind a corporate proxy (e.g. HTTP 407)

Configure the proxy under **Settings → Network & Security → HTTP PROXY**: *System proxy*
(default, includes PAC/WPAD), *Manual proxy* or *No proxy*, hosts to reach directly, and proxy
authentication (user and password, or the Windows account via NTLM/Kerberos). **Test Connection**
checks an address with the values shown. Every connection of the application uses these
settings - schema and catalog downloads, updates, FundsXML, telemetry, JSON Schema `$ref`s,
XProc and HTML previews. See [Settings](settings.md).

### Can I use FreeXmlToolkit offline?

Yes. Editing, validation against local schemas, transformations, debugging and PDF output run
locally. What goes online is optional or comes from your documents: the update check, the
anonymous telemetry, the FundsXML extension, and schemas a document or import refers to by an
`http(s)` URL that is neither cached nor mapped to a local file. [Operation without
internet](offline-operation.md) lists every connection, how to switch it off and how to prepare
schemas for an offline machine.

## Schemas and Validation

### My XML document is not bound to its schema

- Bind one by hand: click the **No XSD** indicator in the status bar or use **Schema ▾ → Set
  XSD Schema…** - see [Binding an XSD Schema](xml-editor.md#binding-an-xsd-schema).
- Documents without `xsi:schemaLocation` are bound through the
  [Schema Library](schema-library.md) (namespace mappings and XML catalogs) - its
  [troubleshooting section](schema-library.md#7-troubleshooting) explains why a mapping may not
  match.

### Imported schemas are not found

Imports and includes are resolved from local files, from the schema cache, via HTTP and through
the Schema Library's catalogs. When a server is unreachable, check the proxy settings above; a
stale download can be removed with **Settings → Files & Cache → TEMP & CACHE → Clear Cache
Folder** (it is fetched again when needed).

### Are DTD and RELAX NG supported?

Yes, for validation and IntelliSense: a document is checked against the DTD of its `<!DOCTYPE>` or
the RELAX NG schema of its `<?xml-model?>` (XML syntax `.rng` and compact syntax `.rnc`), or against
one you bind. There is no graphical editor or documentation generator for DTDs and RELAX NG schemas
(those are for XSD). See [Schema Support](schema-support.md#dtd-and-relax-ng).

## Settings, Logs and Privacy

### Where are my settings stored?

In `~/.freeXmlToolkit/FreeXmlToolkit.properties` in your home folder, next to the cache and
other application data. **Settings → Reset to Defaults…** restores the default values.

### Where are the log files?

**Settings → Files & Cache → LOG FILES → Open log folder** opens the folder; the card also
shows how many log files there are and how much space they use. Old logs are deleted
automatically (by default after 14 days or above 100 MB).

### How do I switch off the anonymous usage statistics?

Clear **Send anonymous usage statistics** and **Send anonymous error reports** under
**Settings → Privacy → USAGE STATISTICS** and click **Save Settings**. Administrators can
disable both centrally - see [Privacy & Telemetry](telemetry.md#how-to-switch-it-off).

## Updates and Versions

### How do I update?

**Help → Check for Updates…** (on macOS in the application menu) looks for a newer version;
the update check at startup can be switched off under **Settings → Network & Security →
UPDATES**. Installations from Scoop or Homebrew are updated with the package manager - see
[Installation](installation.md#updates).

### Which version am I running, and what changed?

**Help → About FreeXmlToolkit** shows the version. The [What's New](whats-new.md) page lists
the highlights of every release.

### Where did the XSD Editor / XSLT Viewer / … tabs go?

The former per-tool tabs were replaced by the Unified Shell - see
[Where the former tabs went](whats-new.md#where-the-former-tabs-went).

### Ctrl+Shift+F no longer formats my document

Following the VS Code convention, **Ctrl+Shift+F** opens **Find in Files**; **Format Document**
is now **Shift+Alt+F**. See [Keyboard Shortcuts](keyboard-shortcuts.md).
