# Updates and network access

How FreeXmlToolkit finds and installs a new version, and how it reaches the internet through a
proxy. Both are set under **Settings → Network & Security**. For the complete list of
connections the application can open, see [Operation without internet](offline.md).

## Updates

### Looking for a new version

- **At startup** - a few seconds after the window opens, FreeXmlToolkit asks GitHub for the
  latest release. Switch this off with **Settings → Network & Security → UPDATES → Check for
  updates on startup** (on by default; setting `update.check.enabled`).
- **By hand** - **Help → Check for Updates…** (on macOS in the application menu), or the update
  button of **Help → About**. When there is nothing newer, a message says *FreeXmlToolkit is up to
  date*; when the check fails, a warning shows the reason.

The check asks `api.github.com` for the latest release and, when GitHub's limit for anonymous
requests is used up (common behind a shared company address), reads the release page on
`github.com` instead. It sends nothing about your documents. A new release is offered only once
the package for your platform has been uploaded to it. The result is kept until you close the
application; a second check in the same session shows the same answer.

### The Update Available dialog

When a newer version exists, the **Update Available** dialog shows your version, the new version
and the release notes (**What's New**). Choose:

| Button | Effect |
|---|---|
| **Download & Install** | Downloads the new version, installs it and restarts the application (see below) |
| **Open in Browser** | Opens the release page, where you can download a package yourself |
| **Remind Me Later** | Closes the dialog; the version is offered again at the next start |
| **Skip This Version** | This version is not offered again; the one after it is |

### Download, installation and restart

**Download & Install** first checks that FreeXmlToolkit runs in no other window. If it does, you
are asked to save your work there, close those windows and choose **Retry** - the update replaces
the installed files, so the other windows would stop working.

A progress window then shows the stages **Downloading update…**, **Extracting files…** and
**Installing update…**; you can cancel while it downloads. The download is the portable package
of the release (`FreeXmlToolkit-<platform>-app-image-<version>.zip`) from `github.com`. Before
anything is unpacked, its SHA-256 checksum is compared with the one GitHub publishes for the
release; a mismatch aborts the update and deletes the file - see
[Automatic Update Integrity](security-features.md#automatic-update-integrity). Your settings in
`~/.freeXmlToolkit` are not touched.

What happens next depends on the platform:

| Platform | Installation |
|---|---|
| **Windows** | FreeXmlToolkit closes; a small helper program replaces the files of the installation folder and starts the new version. If your account may not write to the installation folder (for example an MSI installation under `Program Files`), Windows asks for administrator permission first. Files that a virus scanner still holds are retried for up to about three minutes. |
| **macOS** | The new, signed application bundle is put in the place of the installed one in a single step (the old one is restored if that fails), then the new version starts. |
| **Linux** | The files are copied over the installation folder while the application runs, then the new version starts. This needs write access to the installation folder, as with the portable ZIP in your home folder. A copy installed from the `.deb` or `.rpm` package belongs to `root`: install the new package instead (`sudo apt install ./<file>.deb`, `sudo dnf install <file>.rpm`). |

If the update fails, a message names the reason; nothing is lost, and you can try again or
install the new version by hand from the
[release page](https://github.com/karlkauc/FreeXmlToolkit/releases/latest). Two log files in your
home folder help with a bug report: `fxt-update-debug.log` (all platforms) and
`fxt-update-helper.log` (Windows).

### Where the application does not update itself

- **Installed by a package manager** - a copy installed with Scoop or Homebrew belongs to the
  package manager. The **Update Available** dialog then has no **Download & Install**; it shows
  the command to run instead (`scoop update freexmltoolkit`,
  `brew upgrade --cask freexmltoolkit`) with a **Copy** button. See
  [Install](../get-started/install.md#updates).
- **Updates switched off by the administrator** - with `update.allowed=false` in the machine-wide
  policy file there is no update check, **Check for Updates…** in the Help menu and the About
  dialog is disabled, and nothing is installed. New versions then arrive through your software
  distribution. See [Policies and silent installation](policies.md).
- **The command line** - `fxt-cli` never checks for updates.

### For administrators

| Goal | Setting |
|---|---|
| No update check at start, users can still check by hand | `update.check.enabled=false` (in `defaults.properties`, or locked in `policy.properties`) |
| No updates through the application at all | `update.allowed=false` (`policy.properties` only) |
| Refuse an update that has no published checksum | `update.requireChecksum=true` |

The update needs `api.github.com` and `github.com` (including the addresses GitHub redirects
release downloads to) to be reachable through the proxy.

## Network access {#network}

Every connection FreeXmlToolkit opens goes through one place and uses the settings of the
**HTTP PROXY** card under **Settings → Network & Security**: schema and catalog downloads, the
update check and download, the FundsXML extension, telemetry, the online identifier lookups,
the AI assistant, JSON Schema `$ref`s, **Open from URL**, XProc pipelines and HTML previews.
`fxt-cli` uses the same settings.

### Choosing the proxy

**Connection** has three choices:

| Choice | Meaning |
|---|---|
| **System proxy** (default) | The proxy of the operating system, including automatic configuration (PAC / WPAD). |
| **Manual proxy** | The **Proxy host** and **Port** you enter. |
| **No proxy** | Always connect directly. |

**No proxy for** lists the hosts that are reached directly, whatever the choice above. Separate
them with commas; `*` is a wildcard: `localhost, *.corp.example, 10.*`. `localhost` and the
`127.*` addresses are always reached directly.

### Signing in at the proxy

Under **Proxy authentication**:

- **User** and **Password** - for a proxy that asks for a name and password. The password is
  stored encrypted in your settings file.
- **Domain (NTLM)** - optional. It is put in front of the user name (`DOMAIN\user`). You can also
  type `DOMAIN\user` or `user@domain` into **User** and leave the domain empty.
- **Without a user, sign in with the Windows account (NTLM / Kerberos)** - on by default. On
  Windows, when no user is entered, the proxy gets the account you are signed in with, so no
  password has to be stored. Kerberos (Negotiate) uses the tickets of your Windows logon (the
  JDK's SSPI bridge, `sun.security.jgss.native=true`, set unless given on the command line); a
  proxy that offers no Kerberos gets NTLM. Changing this option takes effect after a restart.

### Timeouts

**Timeouts (s)** sets how long a connection may take to be established (**connect**, default 30
seconds) and how long the application waits for data (**read**, default 60 seconds). Raise them
on a slow line; a value may be at most 3600.

### Test Connection

Enter an address (the field suggests `https://github.com`) and click **Test Connection**. The
test uses the values shown in the card, saved or not, so you can try a proxy before you save.
The result appears next to the button.

A failing download with **HTTP 407** means the proxy wants a sign-in: enter a user and password,
or - on Windows - leave **User** empty and keep the Windows sign-in on.

**"The proxy refused the sign-in (NTLM / Kerberos)"** means the proxy asked for a Windows sign-in
and did not accept it. Proxies often let some sites through without a sign-in, so a test with
`https://github.com` can succeed while `https://api.gleif.org` fails - test with the address that
fails. Then try the other way of signing in: enter **User** (`DOMAIN\user`) and **Password**, or
leave them empty and switch the Windows sign-in on; restart the application after changing the
Windows sign-in.

### Certificates

**Settings → Network & Security → SECURITY → Trust all certificates** makes HTTPS downloads
(for example of schemas) accept any TLS certificate. It helps behind a proxy that inspects HTTPS
with a certificate the Java runtime does not know, but it also removes the protection against a
forged server - use it with care. The update check and the update download ignore this option
and always verify the certificate.

### For administrators

All proxy values can be preset or locked for every user: `useSystemProxy`, `manualProxy`,
`http.proxy.host`, `http.proxy.port`, `http.proxy.nonProxyHosts`, `http.proxy.domain`,
`http.proxy.windowsSignOn`, `http.connectTimeoutSeconds`, `http.readTimeoutSeconds` - see
[Policies and silent installation](policies.md). Do not put a proxy password into those files;
every user can read them. Use the system proxy or the Windows sign-in instead.

## See also

- [Operation without internet](offline.md) - every connection, how to switch it off, schemas
  without a network
- [Security features](security-features.md#automatic-update-integrity) - how an update download
  is verified
- [Settings](../reference/settings.md) - the **Network & Security** group
