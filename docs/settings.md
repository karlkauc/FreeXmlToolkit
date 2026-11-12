# Settings {#settings-page}

> Settings open as a **full page** (a tab in the main editor
> area) rather than in the narrow left side panel. The options are presented
> as **color-coded cards**, sorted into eight groups.

Click the gear icon at the bottom of the activity bar (or press **Ctrl+,**): the **Settings page
opens as a tab in the main editor area**, where there is room for all options.

![Settings page](img/unified-shell-settings.png#only-light)
![Settings page](img/unified-shell-settings-dark.png#only-dark)
*The Settings page: groups in the side panel, colour-coded cards in the main area*

**Finding a setting** - the side panel on the left lists the eight groups. Click a group to
scroll the page to it; the group you are looking at is highlighted. Type into the **search field** at the top of the side panel to filter both
the group list and the cards on the page down to the matching settings; if nothing matches, the
page shows *"No settings match the search."*.

**Managed settings** - on a computer where an administrator manages FreeXmlToolkit, some
settings may be greyed out and a notice at the top of the page says so; see
[Administration & Policies](administration.md).

**Saving** - a save bar stays fixed at the bottom of the page:

- **Unsaved changes** appears as soon as you change an option that has not been saved yet.
- **Save Settings** stores and applies your changes.
- **Reset to Defaults…** asks for confirmation and then puts the default values into the
  controls. Nothing is stored until you click **Save Settings**, so you can still review or
  adjust the values first. The reset leaves your privacy / usage statistics choices, your user
  info, your templates, the file associations and the extension settings (FundsXML, Developer)
  unchanged.
- The **language** applies at the next start.
- A few actions take effect **immediately**, without Save: switching the **theme** or the **accent colour**, registering
  or unregistering **file associations**, and creating, editing or deleting **templates**.

| Group | Cards and options |
|-------|-------------------|
| **Appearance** | **Theme** - **Light**, **Dark** or **System** (follows the light or dark setting of the operating system, also while the application runs; the theme button in the header and **View → Toggle Light / Dark Theme** switch to a fixed Light or Dark, so choose **System** here again to follow the operating system), plus **High contrast**; see [High contrast](#high-contrast) below. **Accent colour** - the colour of buttons, selection, focus and links: eight presets or **Own colour**; see [Accent colour](#accent-colour) below. **Text size & font** - **Small**, **Normal**, **Large** or **Extra Large** text, the **Density** (**Compact** / **Normal** / **Comfortable**) and the font of the interface; see [Text size and font](#text-size-and-font) below. **Language** - the language of the interface: **System default**, **English** or **Deutsch** (takes effect after a restart; the system default is used when it is English or German, otherwise English). **Layout** - **Show toolbar button labels**; **Show activity bar labels**; **Use small icons**; toolbar icon size (**Small** / **Large**); **Show left side panel**; **Show Properties (inspector) panel**. **Rendering** - JavaFX graphics pipeline: **Auto** / **Hardware** / **Software** (takes effect after restart), with the active pipeline and GPU status; see [Rendering mode (hardware vs. software)](#rendering-mode) below. **Memory** - **Maximum memory** for the application: **Automatic** (half of the installed memory) or a fixed limit (takes effect after restart); see [Memory and large files](#memory) below. |
| **Editor** | **Editor font** - font, size and line spacing of every code editor, see [Editor font](#editor-font). **Syntax colours** - a colour scheme and own colours per role, see [Syntax colours](#syntax-colours). **Editor** - XML indent and JSON indent (spaces); **Auto-format after loading**; **Pretty-print XSD on save**; **Pretty-print Schematron on load**; **Recent files to remember** (1–100, default 20) - every file opened in the editor, opened in the background or saved with **Save As** goes into File → Recent, the Explorer's **RECENT** list and the Welcome page. **Templates** - a configurable **templates directory**, plus a **New / Edit / Delete** list of your own templates; see [Managing your templates](#managing-your-templates) below. **User Info** - **Name**, **Email** and **Company**, used for example when generating documentation or signing. |
| **Schemas & Validation** | **XSD** - **Auto-save** (with an interval in minutes); **Create backups on save** (with the number of versions to keep, and an optional **separate backup directory**). **Schema Library** - **Use the Schema Library to bind schemas automatically**; shows the library file's location and a **Manage schema cache…** link; see [Schema Library](schema-library.md). **Parser** - **XML parser** engine (Xerces or Saxon); **Allow XSLT extension functions**. |
| **Files & Cache** | **Temp & Cache** - **Use system temp folder** or a custom temp folder; **Clear Temp Folder** to free disk space; **Clear Cache Folder** to delete cached files (downloaded schemas etc.); **Manage schema cache…**. **Log Files** - **Delete old log files automatically at startup** (on by default) with **Keep for (days)** (default 14) and **Maximum total size (MB)** (default 100; 0 = no limit); **Clean Up Now** applies the limits at once, **Open log folder** shows the folder, and the card names the folder with its file count and size. The log file in use is never deleted. **File Associations** - make FreeXmlToolkit the **default application** for XML, XSD, XSLT, Schematron and JSON files (per user, no admin rights); see [File Associations](file-associations.md). |
| **Network & Security** | **HTTP Proxy** - **Connection**: *System proxy* (default, includes automatic configuration via PAC/WPAD), *Manual proxy* (host and port) or *No proxy*; **No proxy for**: hosts connected directly (`*` wildcard, e.g. `*.corp.example, 10.*`; local addresses always); **Proxy authentication**: user (`DOMAIN\user` or `user@domain` also work), password (stored encrypted) and NTLM domain, or — without a user — sign-in with the Windows account (NTLM/Kerberos; takes effect after a restart); **Timeouts** for connecting and reading; **Test Connection** checks an address with the values shown, saved or not. Every connection uses these settings: schema and catalog downloads, updates, FundsXML, telemetry, `doc()` in XPath/XQuery, JSON Schema `$ref`s, XProc (incl. `p:http-request`) and HTML previews. **Security** - **Trust all certificates**: accept any TLS certificate for HTTPS downloads (schemas, updates); use with care. **Allow JavaScript and remote content in HTML previews** (off: previews run no scripts and load nothing from the network). **Run XProc pipelines without restrictions** (off: pipelines may not run programs, run computed pipelines, send HTTP requests or load documents from the network); see [Threat Model](threat-model.md). **Updates** - **Check for updates on startup**. |
| **Privacy** | **Usage Statistics** - **Enable usage tracking** (local, anonymous feature-usage counters shown on the Welcome page) and **Clear statistics**. Below them: **Send anonymous usage statistics** and **Send anonymous error reports** (both on by default) and a **What is sent?** link that shows the exact data and your anonymous installation ID. See [Privacy & Anonymous Telemetry](telemetry.md). |
| **AI** | **AI Assistant** - **Use the AI assistant** (off by default); what **may be sent** to local models and to remote providers (*Schema only*, *Structure*, *Excerpts*); **Show every request before it is sent**; the **Providers** list (**Add… / Edit… / Remove**, **Use for AI actions**). Removed entirely when an administrator sets `ai.allowed=false`. See [AI](#ai) below and [AI Assistant](ai-assistant.md). |
| **Extensions** | **FundsXML** - **Enable FundsXML extensions** adds the [FundsXML activity](fundsxml-extensions.md) to the activity bar. **Developer** - **Record execution statistics** collects duration, CPU and memory per XSLT/XQuery/validation run; view them in the **Execution Statistics** tool tab or via the "last run" item in the status bar. **Support the Project** - a **Sponsor on GitHub** link. |

## Accent colour {#accent-colour}

The accent colour replaces the blue of the primary buttons, selected rows, focus rings, links,
active tabs and toggles - in the shell, in dialogs, in the XSD diagram and in the grid. Pick one of
the presets (Blue, Indigo, Violet, Green, Teal, Orange, Red, Graphite) or any colour with
**Own colour**. The choice applies at once to every open window, in the light and the dark theme.

You do not have to find a colour that works everywhere: the shades derived from it are adjusted so
that texts on and in the accent keep a contrast of at least 4.5:1 (WCAG AA). A very light colour,
for example, is darkened for text on white and lightened for text on the dark backgrounds.

![The Settings page in the violet accent](img/unified-shell-accent-violet.png)
*Settings → Appearance with the violet accent*

![The same page in the dark theme](img/unified-shell-accent-violet-dark.png)
*The same accent in the dark theme*

The workflow colours of the activities (Validation green, Transform orange, ...) stay as they are.

## Text size and font {#text-size-and-font}

**Settings → Appearance → Text size & font** makes the text of menus, side panels, tabs, the
Properties panel, dialogs and the Settings page larger or smaller:

| Size | Body text |
|---|---|
| Small | 12 px |
| Normal | 13 px (the design's size) |
| Large | 14 px |
| Extra Large | 16 px |

All sizes of the interface grow or shrink by the same factor, so headings stay larger than body
text. The activity bar on the left keeps its size, because it has a fixed width. **Font** replaces
the design's Inter with another installed font (the list is filled when you open it); **Reset**
returns to Inter at Normal size. The change applies at once to every open window.

**Density** sets the vertical room of the interface: **Compact** tightens the space above and
below texts and between rows - in panels, lists, trees, menus, toolbars, dialogs and the Settings
page - so more fits on the screen; **Comfortable** adds room and makes rows and buttons taller.
The horizontal room stays, so texts in narrow panels keep their width. **Reset** also returns the density
to Normal.

![Compact density](img/unified-shell-density-compact.png)
*Compact*

![Comfortable density](img/unified-shell-density-comfortable.png)
*Comfortable*

The code editors are not affected: they have their own [editor font](#editor-font).

![Settings → Appearance with the Text size & font card](img/unified-shell-settings-ui-font.png)
*The card with Large selected*

![The shell in Extra Large](img/unified-shell-ui-font-xlarge.png)
*The shell in Extra Large: side panel, tabs and Properties panel grow, the editor keeps its font*

## High contrast {#high-contrast}

**Settings → Appearance → Theme → High contrast** works on top of the light or the dark theme:
all texts - including secondary and muted texts, links and the syntax colours - are strengthened
to a contrast of at least 7:1 (WCAG AAA), borders and outlines to 4.5:1, and the keyboard focus
ring takes the text colour instead of the light blue halo. The accent colour and the syntax colours
you chose stay recognisable; only their brightness is adjusted.

![High contrast, light theme](img/unified-shell-high-contrast.png)
*High contrast in the light theme*

![High contrast, dark theme](img/unified-shell-high-contrast-dark.png)
*High contrast in the dark theme*

## Editor font {#editor-font}

**Settings → Editor → Editor font** sets the font of every code editor: the XML, XSD, XSLT, XQuery
and JSON editors, the Query Console, the transform output and the debugger's value views.

- **Font** - the bundled JetBrains Mono or any monospace font installed on your computer (the list
  is filled when you open it; you can also type a name). A font that is missing on another
  computer falls back to an installed monospace font.
- **Size (pt)** - 8 to 32 points, default 11. The smaller code panels (console, output) use the
  same number in pixels, so they stay a little more compact.
- **Line spacing** - 1.0 to 2.0; the extra space goes above and below each line, so the line
  numbers stay level with the text.
- The **Graphic** grid uses the same font family (at its own size; zoom it with Ctrl+mouse wheel).
  A new family reaches the grid after a restart.
- **Ctrl+mouse wheel changes and keeps the size of all editors** (on by default) - zooming an
  editor changes this size for all editors and keeps it for the next start; **Ctrl+0** returns to
  11 pt. Switched off, a zoom only affects that editor until it is closed, and **Ctrl+0** returns to
  the size set here. The console and output areas always zoom on their own.

Changes apply at once; **Reset Font** restores JetBrains Mono, 11 pt, single spacing.

![Settings → Editor → Editor font](img/unified-shell-settings-editor-font.png)
*The Editor font card with its preview line*

![An XML document with line spacing 1.4](img/unified-shell-editor-font-spacing.png)
*The same font at 13 pt with line spacing 1.4*

## Syntax colours {#syntax-colours}

**Settings → Editor → Syntax colours** sets the colours of XML, JSON and XPath code: in the
editors, the Query Console results, the transform output, the compare view and the values of the
Graphic grid.

- **Scheme** - **FreeXmlToolkit** (the design's colours), **GitHub**, **Solarized**, **Monokai**, **oXygen**,
  **XMLSpy-like** or **High Contrast**. Each scheme has a light and a dark variant. oXygen and XMLSpy-like
  recreate the colours of those editors' text views so a familiar look carries over; they are modelled on
  them, not supplied by Syncro Soft or Altova.
- **Own colours** - one colour per role: text, element / JSON key, attribute, value / string,
  brackets, comment, number, keyword (`true`, `false`, `null`) and declaration. A colour you pick
  replaces the scheme's colour for that role in the **current** theme; switch the theme to set the
  colours of the other one.
- **Reset Colours** returns to the FreeXmlToolkit scheme and clears your own colours in both themes.

Colours of a scheme and colours you pick are adjusted until they stay readable on the editor
background: text keeps a contrast of at least 4.5:1, brackets 3:1 (a pale yellow, for example, is
darkened in the light theme). The preview below the colours shows the result; every change applies
at once.

![Settings → Editor → Syntax colours](img/unified-shell-settings-syntax-colours.png)
*The Syntax colours card with the Monokai scheme (light variant)*

![An XML document in Monokai, dark theme](img/unified-shell-syntax-monokai-dark.png)
*The same scheme in the dark theme*

![An XML document in the oXygen scheme, light theme](img/unified-shell-syntax-oxygen.png)

*oXygen, light: dark-blue tags, orange attribute names, brown values, green comments.*

![An XML document in the XMLSpy-like scheme, light theme](img/unified-shell-syntax-xmlspy.png)

*XMLSpy-like, light: blue tags, red attribute names, black values.*

## AI {#ai}

**Settings → AI** switches the [AI assistant](ai-assistant.md) on (it is off by default), sets what may be sent
to local and to remote providers (*Schema only*, *Structure*, *Excerpts*), whether every request is shown before it is
sent, and manages the providers (Ollama, LM Studio, OpenAI-compatible services, Anthropic): **Add…**, **Edit…**,
**Remove** and **Use for AI actions**. Changes are saved at once. When the administrator sets `ai.allowed=false`, the
group is not shown at all.

## Clearing the Cache Folder

The **Clear Cache Folder** button in the **TEMP & CACHE** section deletes the contents of the
application's local cache folder (`~/.freeXmlToolkit/cache`) - for example downloaded schemas,
including schemas fetched automatically for missing `xs:import` references. Clearing is safe:
such schemas are simply re-downloaded the next time they are needed. A confirmation dialog is
shown first, because the action cannot be undone. The cache folder itself is kept; only its
contents are removed.

## Memory and large files {#memory}

> The **MEMORY** card sets how much memory the application may use, so that large files
> can be opened on machines with plenty of RAM.

The editor keeps a whole document in memory several times over (text, line model,
highlighting, structured views), roughly **16 times the file size**. The memory limit
therefore decides how large a file can be:

| Maximum memory | Largest file that opens |
|----------------|-------------------------|
| 4 GB | about 256 MB |
| 8 GB | about 512 MB |
| 16 GB | about 1 GB |

- **Automatic** *(default)* uses half of the installed memory.
- A fixed value (2 GB up to the installed memory) applies from the **next start**; the
  application restarts itself once with that limit. The card shows the limit of the current
  session and the installed memory.
- If the chosen limit cannot be used, the application falls back to **Automatic** on its own.
- In a development run (`./gradlew run`) the setting has no effect; the Gradle task sets the memory.

Opening a file of **50 MB or more** asks whether to open it in the editor (editing will be slow)
or in the read-only [Large File Viewer](large-files.md), which shows files of any size with a few
megabytes of memory. A file that does not fit into the current memory limit can only be opened in
the viewer; the message names the limit and points to this card.

## Rendering mode (hardware vs. software) {#rendering-mode}

> The **RENDERING** card lets you choose how JavaFX draws the UI,
> per machine, without relying on a fixed JVM flag.

JavaFX can render either on the **GPU** (hardware-accelerated, faster) or purely in
**software**. On machines with only an integrated GPU, hardware rendering can become
unstable on very large diagrams/grids, while machines with a dedicated GPU benefit from it.
The **Mode** dropdown controls this:

| Mode | Behaviour |
|------|-----------|
| **Auto** *(default)* | The application detects the graphics adapter at startup and picks **hardware** rendering on a dedicated GPU (and on macOS), or **software** rendering on integrated/unknown adapters. The detection result is cached. |
| **Hardware** | Always prefer GPU rendering (with an automatic software fallback if the GPU pipeline cannot start). Use this if **Auto** does not recognize your dedicated GPU. |
| **Software** | Always use software rendering. Use this if hardware rendering causes display glitches or crashes on your machine. |

Below the dropdown the card shows the **current run's** rendering status:

- **Active pipeline** - what JavaFX is *actually* using right now: *Hardware — Direct3D*,
  *Hardware — OpenGL ES2*, *Hardware — Metal*, or *Software*. This is read from the live
  graphics pipeline, so with **Auto** you can see at a glance whether you ended up on
  hardware or software rendering.
- **Detected GPU** - the graphics adapter(s) the application found on this machine.

Notes:

- **A restart is required** for a change to take effect - the graphics pipeline is chosen
  once when the application starts. The **Active pipeline** line always reflects the
  pipeline of the *running* session, not an unsaved dropdown change.
- An explicit `-Dprism.order=...` JVM flag (command line) still **overrides** this setting,
  so power users can force a specific pipeline for troubleshooting.
- **Auto** is intentionally conservative: any adapter it cannot confidently identify as a
  dedicated GPU falls back to software rendering. If your dedicated GPU is not detected,
  switch the mode to **Hardware**.

## Managing your templates

> The **TEMPLATES** card lets you keep your own starting templates,
> which then show up (filtered by file type) in the [New File dialog](unified-shell.md#new-file-dialog).

- **Templates directory** - Use **Browse…** to point the application at the folder where your
  templates live. Leave it empty to use the default location. Changing the directory takes
  effect immediately, **without restarting** the application.
- **Your templates** - The card lists your own (non-built-in) templates. Use:
    - **New** - Create a template. A dialog asks for its **Name**, **Category**,
      **Description**, **File type**, and **Content**.
    - **Edit** - Change the selected template.
    - **Delete** - Remove the selected template.

Each template is stored as a `.template` file in the templates directory. Once saved, your
templates appear in the **New File** dialog whenever their file type is selected. For the wider
template and XPath-snippet system, see [Template Management](template-management.md).
