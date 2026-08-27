# XML Catalog example (Schema Library)

A self-contained demo of OASIS XML catalogs in FreeXmlToolkit. All schema references in this
folder point at **`schemas.example.org`, a host that does not exist** — the catalog maps them
to the local `schemas/` folder, so everything validates offline.

| File | Purpose |
|---|---|
| `catalog.xml` | Main catalog: `system`, `rewriteSystem`, `public`, `nextCatalog` |
| `namespaces-catalog.xml` | Chained catalog: namespace → schema (`uri` entries) |
| `schemas/invoice.xsd` | Invoice schema; imports the common types from an unreachable URL |
| `schemas/common.xsd` | Shared types (`Money`, `Address`, `CurrencyCode`) |
| `invoice.xml` | Valid document; `xsi:schemaLocation` points at the unreachable host |
| `invoice-namespace-only.xml` | Valid document **without** `xsi:schemaLocation` (bound by namespace) |
| `invoice-invalid.xml` | Two validation errors (bad currency code, missing `<total>`) |

## Try it

1. Open FreeXmlToolkit and switch to the **Schema Library** activity (activity bar, below *Schema*).
2. Open the **Catalogs** tab (second icon) and click **Add catalog…** → select `catalog.xml`.
   The list shows the catalog with its entry count; `namespaces-catalog.xml` is picked up
   automatically through `nextCatalog`.
3. Open `invoice.xml`. The status bar shows **XSD: invoice.xsd** — the unreachable
   `xsi:schemaLocation` URL was rewritten by the `rewriteSystem` entry, and the `xs:import`
   inside `invoice.xsd` was served by the `system` entry.
4. Press **Validate** (or use the Validation activity): the document is *Valid*.
5. Open `invoice-namespace-only.xml`: no `xsi:schemaLocation` at all, still bound — the
   `uri name="urn:example:invoice"` entry of the chained catalog resolved the root namespace.
6. Open `invoice-invalid.xml` and validate: two errors are reported against the
   catalog-resolved schemas (`currency="euro"` violates `CurrencyCode`, `<total>` is missing).
7. Optional: in the Catalogs tab select the catalog and click **Import entries into Mappings**
   to copy the namespace mappings into your library as regular entries.

## What to look at

* Without the catalog, step 3 falls back to a download attempt for
  `http://schemas.example.org/…` and fails (no such host).
* Remove or disable the catalog (toggle button in the Catalogs tab) and re-open the documents
  to see the difference.
* Resolution order is *your mappings → catalogs → bundled standards*; a user mapping for the
  same namespace would win over the catalog.

The same files are used by the automated test
`src/test/java/org/fxt/freexmltoolkit/service/catalog/CatalogExampleTest.java`.
