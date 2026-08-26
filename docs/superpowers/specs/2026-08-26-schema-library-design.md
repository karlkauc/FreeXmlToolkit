# Schema Library, XML Catalog Support and Schema Cache UI — Design

Date: 2026-08-26
Origin: GitHub issue #35 "Feature Request: XSD Schema Libraries" (WizzerWorks, X3D use case)

## 1. Goal

Give users a persistent, visible **Schema Library**: a namespace → schema mapping that
the toolkit consults whenever it needs to find a schema (auto-binding for validation and
IntelliSense, `xs:import`/`xs:include` resolution, JSON `$schema` resolution, XSLT/XQuery
`doc()` resolution). The library is fed from three sources — user entries, registered
**OASIS XML Catalog** files, and a small **bundled** list of well-known standards — and the
existing remote `SchemaResourceCache` finally gets a UI.

Out of scope (YAGNI): cloud store, plugin system, bundling schema files inside the jar,
unifying the four existing resolver implementations (tracked as a follow-up refactoring).

## 2. Current state (findings)

- `SchemaResourceCache` (`~/.freeXmlToolkit/cache/schemas/` + `cache-index.json`) is
  instantiated with `new` at three places; no singleton, no per-entry removal, no UI.
- A second, legacy cache lives in `XmlServiceImpl` (`~/.freeXmlToolkit/cache/<MD5>/`) for
  schemas auto-detected from `xsi:schemaLocation`.
- Schema resolution is implemented four times independently:
  1. `SchemaResolver.ValidationResourceResolver` (LSResourceResolver for Xerces + Saxon
     validation),
  2. `XsdNodeFactory` + `ImportResolutionContext` (V2 model, uses `NamespaceSchemaDownloader`),
  3. `SchemaResolver.resolveReferences` (legacy parse pipeline),
  4. `XsltTransformationEngine.configureResourceAccess` (Saxon `ResourceResolver`, only
     blocks remote access).
- No OASIS catalog support anywhere. No new dependency is needed (own parser).
- Auto-binding: `EditorHost.detectSchemaFor` → `XmlService.getSchemaNameFromXmlContent`
  (only `xsi:schemaLocation` / `xsi:noNamespaceSchemaLocation`);
  `EditorHost.detectJsonSchemaFor` → `JsonService.getSchemaLocationFromJsonContent`
  (`$schema`, json-schema.org meta-schemas are ignored). Manual bindings always win
  (`SchemaRebindPolicy`).
- New activity = constant in `controls/shell/Activity` + one `case` in
  `UnifiedShellView.createSidePanel`.

## 3. Architecture (chosen: facade in front of the existing resolvers)

```
                    ┌──────────────────────────────┐
  user entries ───► │                              │
  catalog files ──► │   SchemaLibraryService       │ ◄── SchemaLibraryPanel (UI)
  bundled.json ───► │  resolveNamespace / SystemId │
                    │  resolveJsonSchema / RootEl. │
                    └──────────────┬───────────────┘
                                   │ Optional<Path|URI>, materialize() via SchemaResourceCache
      ┌──────────────┬─────────────┼──────────────┬──────────────────┐
      ▼              ▼             ▼              ▼                  ▼
 ValidationResource  XsdNodeFactory  SchemaResolver  Xslt ResourceResolver  EditorHost auto-bind
 Resolver (Xerces)   (V2 imports)    (legacy)        (Saxon doc())          (XML + JSON)
```

Each consumer asks the library **first**; on a miss it continues with its current logic.
Existing SSRF guards, cycle detection and remote-block behaviour stay untouched.

### 3.1 Domain

`org.fxt.freexmltoolkit.domain.SchemaLibraryEntry` (record):

| Field | Type | Notes |
|---|---|---|
| `id` | `String` (UUID) | stable identity for UI/edit |
| `namespace` | `String` | XSD target namespace, or JSON `$schema`/`$id` URI; empty string allowed for no-namespace schemas identified by `rootElement` |
| `location` | `String` | absolute local path **or** `http(s)` URL |
| `kind` | `enum SchemaKind { XSD, JSON_SCHEMA, DTD }` | |
| `source` | `enum EntrySource { USER, BUNDLED, CATALOG }` | CATALOG entries are only produced by "Import entries" (they become USER afterwards); the enum value exists for preview display |
| `enabled` | `boolean` | disabled entries are skipped by resolution |
| `description` | `String` | free text, shown in the UI |
| `rootElement` | `String` (nullable) | local name of the document element for no-namespace auto-binding |

`org.fxt.freexmltoolkit.domain.SchemaCatalogRef` (record): `id`, `path`, `enabled`.

### 3.2 Service

`service/SchemaLibraryService` (interface) + `service/SchemaLibraryServiceImpl`, registered
as a singleton in `ServiceRegistry`.

Persistence: `~/.freeXmlToolkit/schema-library.json` (Gson, pretty-printed, `version: 1`),
containing `entries[]` (USER only) and `catalogs[]`. The file is written on every mutation.
Bundled entries are read from the classpath resource `schema-library/bundled.json` and
merged at load time; user entries with the same `namespace`+`kind` override a bundled
entry; a bundled entry can be disabled (stored as `disabledBundled[]` namespaces) but not
deleted.

Resolution API (all return `Optional<SchemaLibraryEntry>` unless noted; all skip disabled
entries; order **USER → CATALOG → BUNDLED**):

- `resolveNamespace(String namespace, SchemaKind kind)`
- `resolveSystemId(String systemId, String baseUri)` — checks user entries whose `location`
  equals the systemId (absolute or resolved against `baseUri`), then the catalogs
  (`uri`, `system`, `rewriteSystem`, `rewriteURI`, `public`, `nextCatalog`), returns the
  resolved target as `Optional<URI>`.
- `resolveJsonSchema(String schemaUri)` — shorthand for `resolveNamespace(uri, JSON_SCHEMA)`.
- `resolveByRootElement(String localName)` — user + bundled entries with matching
  `rootElement` and empty namespace.
- `Optional<Path> materialize(SchemaLibraryEntry entry)` — local path as-is (must exist);
  URL via `SchemaResourceCache.getOrDownload`, guarded by `PathValidator.isUrlSafeToAccess`.
  Never throws; a failure is logged and returned as empty plus `lastError` on the entry
  status (see 3.5).

Catalog handling: an own, dependency-free `SchemaCatalogParser` (StAX, secure factory,
no network) reads OASIS catalogs into an immutable `ParsedCatalog` (entries `system`,
`public`, `uri`, `rewriteSystem`, `rewriteURI`, `nextCatalog`, honouring `xml:base`,
relative targets resolved against the catalog file). `nextCatalog` is followed with a
depth cap of 10 and a visited set. Matching order per catalog: exact `system`/`uri`,
then longest-prefix `rewriteSystem`/`rewriteURI`, then `public`; the first registered
catalog that matches wins. `ParsedCatalog`s are rebuilt lazily when a catalog file's
mtime changes or the catalog list is mutated. `importCatalog(Path)` maps the same
parsed entries to preview `SchemaLibraryEntry` objects with `source = CATALOG`, which
the UI can copy into the user list. (The JDK `javax.xml.catalog` API was rejected: its
behaviour on a miss depends on `RESOLVE` semantics and it is not needed for import.)

CRUD: `getEntries()` (unmodifiable observable list incl. bundled), `addEntry`,
`updateEntry`, `removeEntry` (USER only), `setEnabled(id, boolean)`, `getCatalogs()`,
`addCatalog(Path)`, `removeCatalog(id)`, `reloadCatalogs()`,
`SchemaLibraryEntry entryFromFile(Path)` (reads `targetNamespace` / JSON `$id`/`$schema`
to prefill the Add dialog). All mutations run on the calling thread, fire on an
`ObservableList` (UI observes via `Platform.runLater`), and are persisted synchronously.

Feature toggle: `PropertiesService.isSchemaLibraryAutoBindEnabled()` (default `true`) —
when off, `EditorHost` does not consult the library for auto-binding; the resolver hooks
stay active.

### 3.3 `SchemaResourceCache` changes

- Becomes a singleton obtained via `ServiceRegistry.get(SchemaResourceCache.class)`; the
  three `new SchemaResourceCache()` call sites (`SchemaResolver`, `ImportResolutionContext`,
  `NamespaceSchemaDownloader`) switch to it. The `(ConnectionService, SchemaResourceCache)`
  test constructors stay.
- New API: `List<SchemaCacheEntry> listEntries()`, `boolean removeEntry(String localFilename)`
  (deletes the file and index entry), `Optional<Path> refresh(String url)` (re-download,
  replacing the entry), `Path pathOf(SchemaCacheEntry)`.
- Concurrency: index mutations are `synchronized` on the instance (already the pattern).
- The legacy `XmlServiceImpl` cache is **not** migrated. `XmlService` gains
  `List<Path> listAutoDetectedSchemaCacheDirs()` and `int clearAutoDetectedSchemaCache()`
  so the UI can show and clear it.

### 3.4 Integration points

| Consumer | Change |
|---|---|
| `SchemaResolver.ValidationResourceResolver.resolveResource` | before remote/local resolution: `resolveSystemId(systemId, baseURI)`; if empty and `namespaceURI != null`: `resolveNamespace(ns, XSD)` → `materialize`. Hit → `LSInputImpl` with the local file. Circular-detection bookkeeping still applies to the returned path. |
| `XsdNodeFactory` / `ImportResolutionContext` | before local/cache/network: library lookup by `namespace`, then `schemaLocation` via `resolveSystemId`. `xs:include` (no namespace): `resolveSystemId` only. |
| `SchemaResolver.resolveReferences` | same lookup before its existing include/import logic. |
| `XsltTransformationEngine.configureResourceAccess` | Saxon `ResourceResolver`: try `resolveSystemId(href, base)` first; a hit is served as a local `StreamSource`; otherwise existing behaviour (remote block remains). `UnparsedTextURIResolver` unchanged. |
| `EditorHost.detectSchemaFor` (XML) | if `XmlService.getSchemaNameFromXmlContent` yields nothing **and** the toggle is on: parse document element (namespace + local name, StAX, first start element only) → `resolveNamespace(ns, XSD)` or, without namespace, `resolveByRootElement(localName)` → `materialize` → `SchemaDetection(file, READY)`. Origin stays `AUTO`; `SchemaRebindPolicy` is unchanged so manual bindings still win. |
| `EditorHost.detectJsonSchemaFor` | after `JsonService.getSchemaLocationFromJsonContent`: if the `$schema` URI (including json-schema.org meta-schema URIs) has a library entry, use it; else existing behaviour. |
| `SecureXmlFactory.createLocalOnlySchemaResolver` | unchanged (documentation pipeline stays strictly local). |

Thread-safety: `SchemaLibraryServiceImpl` resolution methods are read-only over immutable
snapshots (`volatile` list + rebuilt catalog resolver), safe to call from validation and
parser threads.

### 3.5 Entry status

`SchemaLibraryService.statusOf(entry)` → `EntryStatus { LOCAL_OK, LOCAL_MISSING, CACHED,
NOT_DOWNLOADED, ERROR(message) }`, computed without network access (cache lookup only).
Used by the UI for the status icon; `materialize` updates a transient `lastError` map.

## 4. UI — Activity "Schema Library"

- `Activity.SCHEMA_LIBRARY("schema-library", "Schema Library", "bi-collection")`, placed
  after `SCHEMA` in the Activity Bar.
- `controls/shell/editor/SchemaLibraryPanel extends VBox` (constructor takes
  `EditorHost`), built from three sections in a `TabPane` (`utility-tab` style):

**Mappings** — `TableView<SchemaLibraryEntry>`: status icon · Namespace · Location ·
Kind · Source · Enabled (CheckBox). Filter `TextField` (namespace/location/description
contains). Toolbar: *Add* (opens `SchemaLibraryEntryDialog`: file chooser or URL field,
namespace prefilled via `entryFromFile`, kind, description, root element), *Edit*,
*Remove* (USER only, confirm), *Enable/Disable*, *Add schema of current document* (uses
`editorHost.activeSchemaProperty()`), *Download* (materialize URL entries now). Bundled
rows use a muted style class `fxt-lib-bundled`. Double-click opens the schema in the
editor (`editorHost.openFile`). Context menu mirrors the toolbar (icons + text).

**Catalogs** — `ListView<SchemaCatalogRef>` cell: path, entry count, enabled, error text
(red) when unparsable. Toolbar: *Add catalog…*, *Remove*, *Reload*, *Import entries into
Mappings* (preview dialog with checkboxes, defaults all selected, existing namespaces
pre-unchecked).

**Cache** — `TableView<SchemaCacheEntry>`: URL · Target namespace · Size · Downloaded ·
Hits. Filter field. Toolbar/context menu: *Open*, *Reveal in Explorer* (Explorer panel
navigates to the file), *Refresh*, *Delete* (confirm), *Clear all* (confirm). Footer
label with `CacheStats` (entries, total size, hit ratio). Second, collapsible group
"Auto-detected schemas" listing the legacy cache directories read-only with *Clear*.

All service calls that may touch disk/network (`materialize`, `refresh`, `clear`) run on
`FxtGui.executorService`; results are applied via `Platform.runLater`. Errors are shown as
non-blocking status text in the panel, never as modal dialogs, except for confirmations.

Settings page: the **TEMP & CACHE** card gets a hyperlink "Manage schema cache…" that
selects the Schema Library activity (Cache tab); a new **SCHEMA LIBRARY** card holds the
"Use schema library for automatic schema binding" checkbox and the path of
`schema-library.json`.

## 5. Bundled list (`src/main/resources/schema-library/bundled.json`)

Namespace → HTTPS URL only, downloaded on demand into `SchemaResourceCache`. Groups:

- **XML core:** XML namespace (`xml.xsd`), XMLDSig core, XML Encryption, XLink 1.1,
  XInclude, XSLT 1.0 and 3.0, ISO Schematron, XSD 1.0 meta-schema.
- **Web standards:** XHTML 1.0 Strict/Transitional, XHTML 1.1, SVG 1.1, MathML 3, Atom 1.0,
  RSS 2.0, SOAP 1.1/1.2 envelope, WSDL 1.1.
- **X3D:** X3D 3.0, 3.1, 3.2, 3.3, 4.0 (web3d.org `specifications/x3d-<v>.xsd`).
- **Finance/business:** FundsXML 4, XBRL 2.1 instance + linkbase, UBL 2.1 (Invoice, Order,
  CreditNote), ebInterface 6.1, CII (UN/CEFACT D16B `CrossIndustryInvoice`).

Each entry: `namespace`, `location`, `kind`, `description`, optional `rootElement`.
`BundledSchemaLibraryTest` asserts: valid JSON, unique `namespace`+`kind`, all locations
`https://`, all pass `PathValidator.isUrlSafeToAccess`. Reachability is **not** tested
(no network in tests); an unreachable URL surfaces as `ERROR` status in the UI.

## 6. Error handling

- Corrupt `schema-library.json`: log, back up as `.broken-<timestamp>`, start with an empty
  user list (bundled still available).
- Unparsable catalog: catalog marked with error text, other catalogs keep working.
- Missing local file of a USER entry: status `LOCAL_MISSING`; resolution skips it and
  falls through to the next source.
- Download failure for a URL entry: status `ERROR`, resolution falls through; no retry
  storm — `materialize` remembers the failure for the session (per entry) and only retries
  on explicit *Download*/*Refresh* or after 10 minutes.
- Catalog and library entries pointing to unsafe URLs are rejected at add time with a
  user-facing message.

## 7. Testing

Unit (JUnit 5, Mockito, `@TempDir`, properties file redirected via `fxt.properties.file`):

- `SchemaLibraryServiceImplTest`: CRUD + persistence round trip; bundled merge/override/
  disable; resolution order USER → CATALOG → BUNDLED; `resolveByRootElement`;
  `materialize` for local/URL (mocked cache); corrupt-file recovery; unsafe URL rejection.
- `SchemaCatalogSupportTest`: `uri`, `system`, `public`, `rewriteSystem`, `rewriteURI`,
  `nextCatalog` (with cycle), relative `xml:base`, unparsable file.
- `SchemaResourceCacheTest` additions: `listEntries`, `removeEntry`, `refresh`.
- Resolver hook tests: `SchemaResolverLibraryHookTest` (LSResourceResolver returns library
  file), `XsdNodeFactoryLibraryImportTest` (import resolved from library without network),
  `XsltTransformationEngineLibraryResolverTest` (`doc()` served from library).
- `EditorHostLibraryAutoBindTest` (TestFX): XML with namespace only → schema bound with
  origin AUTO; manual binding not overridden; toggle off → no binding.
  `EditorHostJsonSchemaLibraryTest`: `$schema` mapped via library.
- `BundledSchemaLibraryTest` (see §5).
- `SchemaLibraryPanelTest` (TestFX): add/remove/filter mapping, catalog import preview,
  cache delete updates the table and footer.

`IconifyIconCoverageTest` must stay green (`bi-collection` is in the bundle — verify).

## 8. Documentation

- New `docs/schema-library.md` (concept, mappings, catalogs, cache, bundled list, auto-bind
  toggle, screenshots via `docScreenshots`).
- Update `docs/unified-shell.md` (activity table), `docs/xsd-validation.md` and
  `docs/json-editor.md` (auto-binding order), `docs/schema-support.md` (catalog support),
  `mkdocs.yml` nav, `CLAUDE.md`/`.claude/rules/architecture.md` (panel table, resolver
  hooks), README feature list.
- Answer issue #35 after release.

## 9. Implementation order

1. Domain records + `SchemaLibraryService` (persistence, bundled merge, resolution, tests).
2. Catalog support (own parser + matching, tests).
3. `SchemaResourceCache` singleton + new API; `XmlService` legacy-cache listing.
4. Resolver hooks (Xerces/Saxon LS resolver, V2 factory, legacy resolver, XSLT).
5. `EditorHost` auto-binding (XML + JSON) + properties toggle.
6. Activity + `SchemaLibraryPanel` (Mappings, Catalogs, Cache) + dialogs + Settings links.
7. `bundled.json` + validity test.
8. Docs, screenshots, CLAUDE.md/rules update.
