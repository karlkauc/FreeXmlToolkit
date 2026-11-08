# Plugins

FreeXmlToolkit can be extended with **plugins**: jar files that add an activity with its own side
panel, menu items, validation checks, tasks at start and reactions to opened documents. The
[FundsXML extension](fundsxml-extensions.md) is built as such a plugin and serves as the
reference implementation.

!!! warning "Plugins are programs"
    A plugin runs inside FreeXmlToolkit with the same rights as the application: it can read and
    write your files and reach the network. Install plugins only from sources you trust.
    Administrators can switch external plugins off (see [Administration](#administration)).

## Installing a plugin

1. Open **Settings → Extensions → PLUGINS** and click **Open Plugins Folder**
   (`~/.freeXmlToolkit/plugins`, on Windows `%USERPROFILE%\.freeXmlToolkit\plugins`).
2. Copy the plugin's `.jar` file into that folder.
3. Restart FreeXmlToolkit.

The **PLUGINS** card lists every plugin found at start — built-in ones and the jars in the folder —
with its version and description. Untick a plugin and save the settings to switch it off; this
takes effect at the next start. A plugin that could not be loaded is listed with the reason
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

A plugin is a class implementing `org.fxt.freexmltoolkit.plugin.FxtPlugin`, compiled against the
FreeXmlToolkit jar (`FreeXmlToolkit.jar` in the installation's `app` folder; Java 25, JavaFX 25), and
listed in the jar's `META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`:

```java
package com.example.orders;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.plugin.*;

public class OrdersPlugin implements FxtPlugin {

    @Override public String id() { return "com.example.orders"; }
    @Override public String name() { return "Orders"; }
    @Override public String version() { return "1.0"; }
    @Override public String description() { return "Checks and tools for order documents."; }

    @Override
    public void register(PluginContext context) {
        // An activity with its side panel (created on the JavaFX thread when first shown)
        context.addActivity(new PluginActivity("orders", "Orders", "bi-cart",
                ui -> new VBox(new Label("Order tools"))));

        // A Tools menu item
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.TOOLS, "orders-report",
                "Orders Report…", "bi-table", ui -> ui.showActivity("orders"), null));

        // A check that runs with every XML validation
        context.addValidator(new DocumentValidator() {
            @Override public String name() { return "Order rules"; }

            @Override public boolean accepts(String content, String documentName) {
                return content.contains("<order");
            }

            @Override public List<Problem> validate(String content, String documentName) {
                return content.contains("<total>0</total>")
                        ? List.of(new Problem("warning", 0, "The order total is zero."))
                        : List.of();
            }
        });
    }
}
```

`META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`:

```text
com.example.orders.OrdersPlugin
```

Rules:

- **`id()`** must be unique; it is also the settings key `plugin.<id>.enabled`. Two plugins with the
  same id: the second is not loaded.
- **`register()`** runs once at start, before the window opens — also in `fxt-cli`, where only the
  validators are used. Only declare contributions there: do not block and do not create UI.
- Panels, menu actions and document listeners run on the JavaFX thread and get a `PluginUi`:
  the editor area (`editorHost()`), the window, `openFile`, `showActivity`, `showEditorBar` /
  `hideEditorBar` and `refreshContributions()` (asks the activities and menu items again whether
  they are visible, e.g. after a setting changed).
- Validators and startup tasks run on background threads.
- Icons are [Bootstrap Icons](https://icons.getbootstrap.com/) names (`bi-…`).
- Each external plugin has its own class loader; libraries it needs belong into its jar.
- A plugin that throws while registering contributes nothing and is listed as failed; a panel that
  fails shows an error in the side panel; a failing validator becomes one error in the results.

## Built-in plugins

| Plugin | On by default | |
|---|---|---|
| FundsXML | yes (its activity appears once the extension is enabled) | [FundsXML Extensions](fundsxml-extensions.md) |
| ISO 20022 Payments (SEPA) | no | [Industry Plugins](industry-plugins.md#iso-20022-payments-sepa) |
| ebInterface (Austrian e-invoice) | no | [Industry Plugins](industry-plugins.md#ebinterface-austrian-e-invoice) |
| FinDatEx Templates (EMT, EET, EPT) | no | [Industry Plugins](industry-plugins.md#findatex-templates-emt-eet-ept) |
| XBRL (with Arelle) | no | [Industry Plugins](industry-plugins.md#xbrl-with-arelle) |

A plugin can say it is off until the user switches it on (`enabledByDefault()`), and it can add
XML Schemas to the Schema Library (`context.addSchema(new PluginSchema(namespace, url, description, null))`).

## The FundsXML plugin

`FundsXmlPlugin` (built in, in `controls/shell/editor/`) is the reference: it contributes the
FundsXML activity (visible while the extension is enabled), the **FundsXML Website** and
**FundsXML Schema Docs** Help items, the startup task that keeps the schema content up to date,
and a document listener that offers the extension once when a FundsXML document is opened.
Switching the plugin off in **Settings → PLUGINS** removes all of these.

## Administration

| Key (in `policy.properties`) | Effect |
|---|---|
| `plugins.external.allowed=false` | No plugins are loaded from the plugins folder; built-in ones still load |
| `plugin.<id>.enabled=false` | The plugin is not loaded and cannot be switched on (e.g. `plugin.fundsxml.enabled=false`) |

The plugins folder can be moved with the Java option `-Dfxt.plugins.dir=<folder>` (or
`fxt.plugins.dir` in `policy.properties` / `defaults.properties`), for example to a read-only
folder that software distribution fills. See [Administration & Policies](administration.md).
