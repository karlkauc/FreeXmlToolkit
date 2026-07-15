# File Associations

FreeXmlToolkit can register itself as the **default application** for the file types it
edits — XML, XSD, XSLT, Schematron and JSON — so that double-clicking a file in your
file manager opens it directly in the toolkit.

Registration is always **per user** and requires **no administrator rights** on any
platform.

## Registering from the app

1. Open **Settings** (Activity Bar → gear icon).
2. Find the **FILE ASSOCIATIONS** card.
3. Tick the file types you want (XML, XSD Schema, XSLT Stylesheet, Schematron, JSON).
4. Click **Register**.

To undo, select the types and click **Unregister** — FreeXmlToolkit restores the
previously configured default application where it recorded one.

> **Note:** The card is only active when you run the *installed* application. When the
> app is started from an IDE or a build directory there is no native launcher to
> register, and the card is disabled.

## What happens per platform

### Windows

Windows does not allow any application to make itself the default silently — the actual
default choice is protected by the operating system. FreeXmlToolkit therefore:

1. Registers itself (ProgIds and application capabilities) under
   `HKCU\Software\Classes` — visible in every "Open with" menu, no admin rights needed.
2. Opens the Windows **Settings → Default apps** page pre-filtered to FreeXmlToolkit,
   where you confirm the file types with one click.

**Unregister** removes all registry entries FreeXmlToolkit created.

### macOS

The default handler is set directly through Launch Services (the same mechanism as
Finder's *Get Info → Open with → Change All…*) — silent and per user.

**Unregister** restores the previous default application where one was recorded. If no
previous handler is known, use Finder's *Change All…* to pick another application —
macOS offers no way to "clear" a default.

### Linux

FreeXmlToolkit writes a desktop entry to `~/.local/share/applications/` and sets the
defaults via `xdg-mime default` (stored in `~/.config/mimeapps.list`). Since Schematron
files have no MIME type in the shared MIME database, a small per-user MIME definition
(`application/x-schematron+xml` for `*.sch` / `*.schematron`) is installed as well.

**Unregister** restores the recorded previous defaults, or removes FreeXmlToolkit's
entries from `mimeapps.list`.

## Installer registration

The native installers (MSI/EXE, DMG/PKG, DEB/RPM) additionally declare the file types at
install time, so FreeXmlToolkit appears in "Open with" menus right after installation.
This does **not** make it the default — use the Settings card for that.

## Tips

- **Register also repairs:** after moving or updating the installation, click
  **Register** again — it rewrites all entries with the current installation path.
- Opening files from the command line works too: `FreeXmlToolkit myfile.xml` opens the
  file on startup (and in a running IDE session,
  `./gradlew run --args="myfile.xml"`).
