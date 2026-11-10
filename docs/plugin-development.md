# Writing Plugins

This guide explains how FreeXmlToolkit plugins work and how to write, test, package and ship your own.
For installing and managing plugins as a user, see [Plugins](plugins.md).

A complete, buildable example is in the repository: [`examples/plugin-sample`](https://github.com/karlkauc/FreeXmlToolkit/tree/main/examples/plugin-sample)
— a plugin for "order" documents with an activity, a menu item, a validator, a hint bar and a stylesheet.
The built-in plugins are larger references: **FundsXML** (`controls/shell/editor/FundsXmlPlugin`), **ISO 20022**,
**ebInterface**, **FinDatEx** and **XBRL** (`org.fxt.freexmltoolkit.plugins.*`).

## How plugins work

```text
 start of FreeXmlToolkit (also fxt-cli)
   │
   ├─ PluginManager.loadAll()
   │    ├─ ServiceLoader: built-in plugins (application class path)
   │    └─ every *.jar in the plugins folder, each in its own class loader
   │         (skipped when the policy plugins.external.allowed=false)
   │
   ├─ for each plugin:  id unique?  switched on (plugin.<id>.enabled / enabledByDefault())?
   │    └─ plugin.register(context)          ← your code: declare contributions, nothing else
   │         ok      → contributions are kept
   │         throws  → plugin listed as "failed", contributes nothing
   │
   └─ the window opens: activities, menus, stylesheets, schemas are in place
        ├─ activity shown the first time  → your panel factory runs (JavaFX thread)
        ├─ menu item clicked             → your action runs (JavaFX thread)
        ├─ document opened               → your document listeners run (JavaFX thread)
        ├─ any XML validation            → your validators run (background thread)
        └─ ~5 s after start              → your startup tasks run (background thread)
```

- **Discovery** uses `java.util.ServiceLoader`: a plugin jar lists its plugin class in
  `META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`. A jar can contain several plugins.
- **Isolation**: each external jar gets its own `URLClassLoader` whose parent is the application. Your
  plugin sees the whole application API and its libraries (JavaFX, Saxon, Xerces, Gson, …); libraries
  you need beyond that go into your jar (shade/relocate them if they could clash).
- **Failures are contained**: an exception in `register` disables only that plugin; a panel factory that
  throws shows an error in the side panel; a validator that throws becomes one error in the results; a
  startup task that throws is logged.
- **Changes take effect at the next start**: switching a plugin on or off, adding or removing a jar.

## Your first plugin

```java
package com.example.orders;

public final class OrdersPlugin implements FxtPlugin {

    @Override public String id() { return "com.example.orders"; }   // unique, stable
    @Override public String name() { return "Orders"; }             // shown in Settings → Plugins
    @Override public String version() { return "1.0.0"; }
    @Override public String description() { return "Checks order documents."; }

    @Override
    public void register(PluginContext context) {
        context.addActivity(new PluginActivity("orders", "Orders", "bi-cart",
                ui -> new VBox(new Label("Order tools"))));
    }
}
```

`src/main/resources/META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`:

```text
com.example.orders.OrdersPlugin
```

Build it against the FreeXmlToolkit jar (see [Building and packaging](#building-and-packaging)), copy the
jar into the plugins folder and restart.

## The plugin interface

| Method | Purpose |
|---|---|
| `String id()` | Unique, stable id (`com.example.orders`). Also the settings key `plugin.<id>.enabled`. A second plugin with the same id is not loaded. |
| `String name()` | Name in Settings → Plugins |
| `String version()` | Shown next to the name (optional) |
| `String description()` | One sentence in Settings → Plugins (optional) |
| `boolean enabledByDefault()` | `false`: the plugin stays off until the user switches it on (optional industry plugins do this) |
| `void register(PluginContext context)` | Declares the contributions. Runs once at start, before the window exists — also in `fxt-cli`. **Do not block, do not create UI here.** |

## Contributions

Everything a plugin adds goes through the `PluginContext` passed to `register`:

| Method | Adds |
|---|---|
| `addActivity(PluginActivity)` | An Activity Bar button (above Settings), a **View → Activities** entry and a side panel |
| `addMenuItem(PluginMenuItem)` | An item at the end of **Tools**, or in **Help** (and the header's Help popup) |
| `addValidator(DocumentValidator)` | Checks that run with every XML validation (Validation panel, F8, `fxt-cli validate`) |
| `addStartupTask(Runnable)` | A background task a few seconds after start (update checks, cache refresh) |
| `addDocumentListener(DocumentListener)` | A callback when a document has been opened in the editor |
| `addDocumentBadge(DocumentBadge)` | A small mark in the status bar while the active document is one your plugin recognises |
| `addStylesheet(String url)` | A CSS file added to the window, for your panels |
| `addSchema(PluginSchema)` | An XML Schema in the Schema Library (source *Plugin*) while the plugin is on |
| `service(Class<T>)` | An application service (see [Application services](#application-services)) |
| `pluginId()` | Your id |

### Activities

```java
context.addActivity(new PluginActivity(
        "orders",                       // id: node id activity-orders; must not clash with other activities
        "Orders",                       // label and tooltip
        "bi-cart",                      // Bootstrap icon (https://icons.getbootstrap.com)
        Workflow.VALIDATION,            // colour family of the button and panel (optional)
        ui -> new OrdersPanel(ui),      // the side panel, created once, on the JavaFX thread
        () -> settings.isEnabled()));   // visible now? asked again on ui.refreshContributions()
```

The short constructor `new PluginActivity(id, label, icon, panelFactory)` uses the neutral colour and
is always visible. Keep the panel factory cheap; load data in the background (see
[Threads](#threads)).

### Menu items

```java
context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.TOOLS, "orders-report", "Orders Report…",
        "bi-table", ui -> ui.showActivity("orders"), null /* always visible */));
```

Tools items get the node id `menu-tools-<id>`, Help items `menu-help-<id>`. The visibility supplier is
asked each time the menu opens.

### Validators

```java
context.addValidator(new DocumentValidator() {
    @Override public String name() { return "Order rules"; }        // "source" column of the problems

    @Override public boolean accepts(String content, String documentName) {
        // cheap: look at the root element or namespace only
        return XmlScan.rootOf(content).map(root -> root[0].equals("order")).orElse(false);
    }

    @Override public List<Problem> validate(String content, String documentName) {
        return List.of(new Problem("warning", 12, "The order total is zero."));   // severity, line, message
    }
});
```

- Validators run on a background thread after XSD / DTD / RELAX NG and Schematron, only for
  well-formed documents, and in `fxt-cli validate` too.
- `severity` is `error`, `warning` or `info`; `line` is 1-based (0 when unknown).
- `XmlScan.of(content)` reads the document once into a flat list of elements (path, text, line,
  attributes) — usually all a validator needs. It is securely configured (no DTDs, no external entities).

### Document listeners and editor bars

```java
context.addDocumentListener((ui, path, content) -> {
    if (looksLikeAnOrder(content)) {
        ui.showEditorBar(new OrderHintBar(ui));     // one bar at a time, above the editor
    }
});
```

`path` is `null` for unsaved documents. Call `ui.hideEditorBar()` to remove the bar.

### Document badges

```java
context.addDocumentBadge((path, content) -> looksLikeAnOrder(content)
        ? new DocumentBadge.Badge("Order", "bi-receipt", "Recognised as an order; the order checks apply.",
                Workflow.VALIDATION, ui -> ui.showActivity("orders"))
        : null);
```

The status bar asks every badge when another document becomes active, a document has been loaded
or the settings were saved; return `null` when the document is not yours. It runs on the JavaFX
thread, so look at the text only (no I/O). The chip uses the colours of the given `Workflow`; the
click action and the icon are optional. FundsXML uses it to show that a document was recognised.

### Startup tasks

```java
context.addStartupTask(() -> refreshCodeListsFromServer());   // background thread, once
```

Use `service(ConnectionService.class)` for network access so the proxy settings apply.

### Schemas

```java
context.addSchema(new PluginSchema("urn:example:orders:1",
        "https://example.com/schemas/orders-1.xsd", "Orders 1.0", null));
```

Documents in that namespace get the schema bound automatically. Remote schemas are downloaded once and
cached; the user's own Schema Library entries for the same namespace win.

### Stylesheets

```java
context.addStylesheet(OrdersPlugin.class.getResource("orders.css").toExternalForm());
```

Your CSS can use the design tokens of the application (`-fxt-text-primary`, `-fxt-bg-surface`,
`-fxt-primary-text`, …), so it follows the light and dark theme. See `STYLE_GUIDE.jsonc` in the
repository.

## The application window: `PluginUi`

Panels, menu actions and document listeners receive a `PluginUi` (JavaFX thread only):

| Method | |
|---|---|
| `editorHost()` | The editor area: `getActiveDocument()`, `getActiveText()`, `openFile(path)`, `openGeneratedDocument(text, type, name)`, `activeTabProperty()`, schema binding … |
| `window()` | The main window (owner for dialogs and file choosers) |
| `openFile(Path)` | Opens a file in the editor |
| `showActivity(String id)` | Shows an activity (built-in ids: `explorer`, `search`, `favorites`, `validation`, `transform`, `schema`, `schema-library`, `pdf`, `signature`, `settings`) |
| `refreshContributions()` | Asks every plugin activity and menu item again whether it is visible |
| `showEditorBar(Node)` / `hideEditorBar()` | A bar above the editor |

## Application services

`context.service(Type.class)` returns the application's instances:

| Service | Use |
|---|---|
| `PropertiesService` | Read and write settings — use keys starting with your plugin id (`com.example.orders.serverUrl`) |
| `ConnectionService` | HTTP(S) requests that respect the proxy settings and the administrator policy |
| `FavoritesService` | Add files to the user's favourites |
| `XmlService` | Parse, validate, format XML |
| `SchemaLibraryService` | Look up schemas and catalogs |

Snippets and templates have their own stores: `XPathSnippetRepository.getInstance()` (XPath / XQuery
snippets in the Query Console) and `TemplateRepository.getInstance()` (templates for **New**); the
FundsXML plugin adds its snippets and sample templates there.

Classes of the application outside `org.fxt.freexmltoolkit.plugin` can change between versions; the
`plugin` package is the stable API.

## Threads

| Runs on | Your code |
|---|---|
| Start, before the window | `register` |
| JavaFX application thread | panel factories, menu actions, document listeners, everything in `PluginUi` |
| Background threads | validators, startup tasks |

Never block the JavaFX thread: do file and network work with
`org.fxt.freexmltoolkit.FxtGui.executorService.submit(...)` (or your own executor) and update the UI with
`Platform.runLater(...)`. Validators may run concurrently for different documents; keep them stateless.

## Translations

The application's interface is English and German (Settings → Appearance → Language). Plugins bring
their own texts. A simple way is a `ResourceBundle` in your jar that follows the application language:

```java
ResourceBundle texts = ResourceBundle.getBundle("com.example.orders.messages",
        org.fxt.freexmltoolkit.i18n.I18n.locale(), OrdersPlugin.class.getClassLoader());
```

with `messages.properties` (English) and `messages_de.properties` (German) next to your classes.

## Settings of your plugin

There is no settings page per plugin yet. Store settings with `PropertiesService` under keys that start
with your id, and offer them in your side panel (for example a "Settings…" action that opens a dialog).
Administrators can then pre-set or lock them in `policy.properties` like every other key
(see [Administration](administration.md)). The on/off switch `plugin.<id>.enabled` is managed by the
application.

## Building and packaging

Compile against the FreeXmlToolkit jar — from an installation (`FreeXmlToolkit.jar` in its `app`
folder) or from a source build (`./gradlew jar` → `build/libs/FreeXmlToolkit-<version>.jar`) — with
**Java 25** and `--enable-preview`. A Gradle build (see `examples/plugin-sample/build.gradle.kts`):

```kotlin
plugins { java }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
repositories { mavenCentral() }

dependencies {
    compileOnly(files(property("fxtJar") as String))      // provided at run time
    compileOnly("org.openjfx:javafx-controls:25:linux")   // ditto (any platform classifier)
}

tasks.withType<JavaCompile> { options.compilerArgs.add("--enable-preview") }
```

```bash
gradle jar -PfxtJar=/opt/freexmltoolkit/lib/app/FreeXmlToolkit.jar
cp build/libs/orders-plugin.jar ~/.freeXmlToolkit/plugins/
```

The jar contains your classes, `META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin`, resources
(CSS, bundles, sample files) and libraries the application does not provide.

## Testing

Unit-test validators directly. To test registration, register the plugin as if it had been found:

```java
@Test
void registersItsContributions() {
    PluginManager manager = PluginManager.getInstance();
    manager.registerForTesting(new OrdersPlugin());
    assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals("orders")));
}
```

UI tests can use TestFX like the application's own tests; `PluginShellTest` in the repository shows a
plugin activity, menu item and editor bar inside the real shell.

## Debugging

- Start the application with `-Dfxt.plugins.dir=/path/to/your/build/libs` to load the plugin straight
  from your build folder.
- The log (`~/.freeXmlToolkit/logs`, also **Settings → Files & Cache → Open log folder**) lists every
  plugin and its state at start (`Plugins: [fundsxml=ACTIVE, com.example.orders=FAILED]`) and the stack
  trace of a failed `register`, panel or validator.
- **Settings → Extensions → PLUGINS** shows why a plugin failed.

## Security and administration

A plugin is code with the full rights of the application. Keep that in mind when you write one:

- Use `ConnectionService` for network access (proxy settings, administrator policy).
- Parse untrusted XML with `XmlScan` or the factories in `SecureXmlFactory` (no external entities).
- Do not run external programs without the user's explicit choice (the XBRL plugin runs Arelle only
  from a command the user chose or installed).

Administrators control plugins with `plugins.external.allowed=false` (no jars from the plugins folder)
and `plugin.<id>.enabled=false` (a single plugin), and can move the plugins folder with
`fxt.plugins.dir`; see [Plugins → Administration](plugins.md#administration).

## Updates

FreeXmlToolkit has no update channel for plugins: users replace the jar in the plugins folder and
restart. **Settings → Extensions → PLUGINS** shows `version()`, so raise it with every release.

Data that changes more often than your plugin can be fetched at run time, as the FundsXML plugin does:

- Use a startup task (`addStartupTask`) and `ConnectionService`; the latter applies the proxy and
  certificate settings.
- Cache the data under the user's folder.
- Offer a setting to turn the check off.

## Compatibility

The API in `org.fxt.freexmltoolkit.plugin` is kept compatible within a major version; new
contribution types are added as new methods. Build against the oldest FreeXmlToolkit version you want to
support and state it in your plugin's description or README.
