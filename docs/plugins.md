# Extensions

FreeXmlToolkit can be extended with **extensions**: built-in ones such as FundsXML or the industry
extensions, and jar files you add yourself. An extension adds an activity with its own side panel,
menu items, validation checks, tasks at start and reactions to opened documents. Technically every
extension is a *plugin* (the interface developers implement, see
[Plugin development](plugin-development.md)); the
[FundsXML extension](fundsxml-extensions.md) is the reference implementation.

!!! warning "Extensions are programs"
    An extension runs inside FreeXmlToolkit with the same rights as the application: it can read and
    write your files and reach the network. Install extensions only from sources you trust.
    Administrators can switch external plugins off (see [Administration](#administration)).

## Installing an extension

1. Open **Settings → Extensions → INSTALLED EXTENSIONS** and click **Open Extensions Folder**
   (`~/.freeXmlToolkit/plugins`, on Windows `%USERPROFILE%\.freeXmlToolkit\plugins`).
2. Copy the plugin's `.jar` file into that folder.
3. Restart FreeXmlToolkit.

The **INSTALLED EXTENSIONS** card lists every extension found at start — built-in ones and the jars
in the folder — with its version and description. Untick one and save the settings to switch it
off; this takes effect at the next start. FundsXML's row is the FundsXML switch itself and applies
at once. A plugin that could not be loaded is listed with the reason
(e.g. a jar without plugin, or an error while it registered); the others still load.

## What a plugin can contribute

| Contribution | Where it appears |
|---|---|
| **Activity** | A button in the Activity Bar (above Settings) and an entry in **View → Activities**; its side panel is created the first time it is shown. A plugin can hide the activity while it is not in use (FundsXML: while the extension is off). |
| **Menu item** | At the end of the **Tools** menu, or in the **Help** menu (also in the header's Help popup) |
| **Validator** | Its problems appear with every XML validation — the Validation panel, **F8**, and `fxt-cli validate` — with the validator's name as source |
| **Startup task** | Runs once in the background a few seconds after start (FundsXML: checks for new schema versions) |
| **Document listener** | Is told when a document has been opened; it can show a bar above the editor (FundsXML: the hint bar) |
| **Stylesheet** | Added to the window, for the plugin's own panels |
| **Schema** | A Schema Library entry (source *Plugin*) while the plugin is on; never saved to the user's library |

Application services — settings, favorites, XPath snippets, templates, the Schema Library — are
available to a plugin through `PluginContext.service(…)`.

## Writing a plugin

A plugin is a class implementing `org.fxt.freexmltoolkit.plugin.FxtPlugin`, listed in
`META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin` of its jar. The full guide — how plugins are
loaded, every contribution type, threads, translations, building, testing and debugging — is
[Writing Plugins](plugin-development.md); a complete example project is in
[`examples/plugin-sample`](https://github.com/karlkauc/FreeXmlToolkit/tree/main/examples/plugin-sample).

## Built-in plugins

| Plugin | On by default | |
|---|---|---|
| FundsXML | yes (its activity appears once the extension is enabled) | [FundsXML extension](fundsxml-extensions.md) |
| ISO 20022 Payments (SEPA) | no | [ISO 20022 Payments](plugin-iso20022.md) |
| ebInterface (Austrian e-invoice) | no | [ebInterface](plugin-ebinterface.md) |
| FinDatEx Templates (EMT, EET, EPT, TPT) | no | [FinDatEx Templates](plugin-findatex.md) |
| XBRL (with Arelle) | no | [XBRL](plugin-xbrl.md) |
| PK-Meldewesen (FMA pension fund reports) | yes (its activity appears once the extension is enabled) | [PK-Meldewesen](plugin-pk-meldewesen.md) |
| KAG-Meldewesen (FMA reports of management companies) | no | [KAG-Meldewesen](plugin-kag-meldewesen.md) |

A plugin can say it is off until the user switches it on (`enabledByDefault()`), and it can add
XML Schemas to the Schema Library (`context.addSchema(new PluginSchema(namespace, url, description, null))`).

## Updating plugins

Plugins have no update channel of their own. What keeps each part current:

| Part | How it stays current |
|---|---|
| Built-in plugins (FundsXML, ISO 20022, ebInterface, FinDatEx, XBRL, PK-Meldewesen) and their data (FinDatEx field definitions, ISO 20022 schemas, the bundled FMA taxonomy) | With the application: **Help → Check for Updates…** or the automatic update check, see [Installation](installation.md#updates) |
| FundsXML schemas, samples, Schematron rules and snippets | In addition, the FundsXML plugin checks GitHub once a day and installs a new release in the background, see [FundsXML Extensions](fundsxml-extensions.md#automatic-update-checks) (`fundsxml.update.check.enabled=false` turns it off) |
| Plugins from the plugins folder (`.jar`) | By hand: put the new jar into the folder, remove the old one, restart. **Settings → Extensions → INSTALLED EXTENSIONS** shows the version that is loaded. |
| FMA taxonomies (PK-Meldewesen) | Imported from the FMA's ZIP in the PK-Meldewesen panel, see [PK-Meldewesen](plugin-pk-meldewesen.md#newer-taxonomies) |
| Arelle (used by the XBRL plugin) | It is a separate program: `pip install -U arelle-release` |

With the policy `update.allowed=false`, application updates, and with them the built-in plugins and
their data, arrive only through your software distribution. The FundsXML content check is not
affected by this policy; switch it off with `fundsxml.update.check.enabled=false`, or switch the
plugin off with `plugin.fundsxml.enabled=false`.

## The FundsXML plugin

`FundsXmlPlugin` (built in, in `controls/shell/editor/`) is the reference: it contributes the
FundsXML activity (visible while the extension is enabled), the **FundsXML Website** and
**FundsXML Schema Docs** Help items, the startup task that keeps the schema content up to date,
and a document listener that offers the extension once when a FundsXML document is opened.
Switching the plugin off in **Settings → Extensions → INSTALLED EXTENSIONS** removes all of these.

## Administration

| Key (in `policy.properties`) | Effect |
|---|---|
| `plugins.external.allowed=false` | No plugins are loaded from the plugins folder; built-in ones still load |
| `plugin.<id>.enabled=false` | The plugin is not loaded and cannot be switched on (e.g. `plugin.fundsxml.enabled=false`) |

The plugins folder can be moved with the Java option `-Dfxt.plugins.dir=<folder>` (or
`fxt.plugins.dir` in `policy.properties` / `defaults.properties`), for example to a read-only
folder that software distribution fills. See [Administration & Policies](administration.md).
