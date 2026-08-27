# IntelliSense: namespace-aware + sequence-position-aware element completion

## Context

Bug report (catalog example `release/examples/catalog/invoice-namespace-only.xml`, bound to
`schemas/invoice.xsd` which imports `common.xsd` for `type="c:Address"`):

1. Typing `<` inside `<billTo>` offers `street` and inserts `<street></street>` — must be
   `<c:street></c:street>` (children of `Address` live in the *common* namespace,
   `elementFormDefault="qualified"`, which the instance binds to prefix `c`).
2. After an existing `<c:street>…</c:street>`, `street` is offered again although
   `maxOccurs=1`; in an `xs:sequence` only the elements that may *follow* the current
   position should be offered.

### Root causes (verified)

| # | Where | Problem |
|---|-------|---------|
| A | `XsdDocumentationService.processElementOrAttribute` (1669-1689) | `sourceNamespace`/`sourceNamespacePrefix` are only set for `fxt:sourceNamespace` (no writer exists → dead) or for `ref="p:X"` (ThreadLocal). Local elements declared inside a complexType from an *imported* schema get **no namespace info**. |
| B | `XsdElementDisplayUtils.buildCompletionItem` (256-263) + `IntelliSenseEngine.handleElementInsertion` (370-382) | label/insertText are the bare local name; prefix survives only as a badge in `CompletionItemCellRenderer`. The instance document's `xmlns` declarations are never consulted. |
| C | `XsdCompletionProvider.countExistingSiblings` (136-194) | Counts raw tag names (`c:street`) but compares with schema local names (`street`) → maxOccurs filter never fires for prefixed siblings. Parent lookup `"<" + parentElement` also fails when the parent is prefixed. |
| D | `XsdCompletionProvider.getElementCompletions` + `XsdElementDisplayUtils.collectRealChildElements` | Compositors are flattened into an unordered set; there is **no** sequence-position logic anywhere. `XsdExtendedElement.children` *is* in document order (`addChild` appends), so the order is available. |
| E | `IntelliSenseEngine.showCompletions` (135-155) | `CompletionCache` is keyed by (xpath, type, mode) only and is never invalidated on text edits → any sibling-dependent filtering is frozen after the first hit for that xpath. |
| F | `ContextAnalyzer.extractElementName` / `MutableXmlSchemaProvider.findBestMatchingElement` | XPath segments keep prefixes (`/invoice/billTo/c:street`) and the map lookup does not strip them (unlike `XsdSchemaAdapter.localName`). Affects completion when the caret is *inside* a prefixed element (e.g. attributes/children of `c:street`). |

## Implementation

### 1. Element namespace resolution from the schema DOM (fixes A)

New pure helper `XsdElementNamespaceResolver` in `org.fxt.freexmltoolkit.domain` (next to
`XsdElementDisplayUtils`):

```java
/** Namespace URI an instance element for this declaration must be in, or null (unqualified). */
public static String resolveNamespaceUri(XsdExtendedElement el, XsdDocumentationData data)
```
- If `el.getSourceNamespace()` is set → return it (keeps the existing `ref="ds:X"` path).
- Else use `el.getCurrentNode()`: `root = node.getOwnerDocument().getDocumentElement()`,
  `tns = root@targetNamespace` (each schema file is parsed into its own `Document` in
  `initializeCachesFromAllSchemas`, so the owner document is the *declaring* schema — this is
  exactly what makes `common.xsd`'s `street` resolvable).
  - global element (parent is `xs:schema`) → `tns`
  - local element → qualified iff `node@form == "qualified"` or (`form` absent and
    `root@elementFormDefault == "qualified"`) → `tns`, else `null`.
- `null` currentNode → fall back to `data.getTargetNamespace()`.

Do **not** change `sourceNamespace` semantics in the service (HTML docs sorting at
`XsdDocumentationHtmlService:241` and sample-XML generation at `XsdDocumentationService:2418`
rely on "null = native").

### 2. Instance prefix map + qualified insert text (fixes B)

- New small utility `XmlNamespaceDeclarations` (in `controls/v2/editor/intellisense/context/`):
  scans `textBeforeCaret` for `xmlns="uri"` / `xmlns:p="uri"` on the open-element ancestors
  (simple approach: regex over all start tags in the text before the caret, later
  declarations win; keep default-namespace and prefix→URI maps; expose `prefixFor(uri)`
  → `Optional<String>` where `""` means default namespace).
- In `XsdCompletionProvider.getElementCompletions`: build the declarations once per call,
  then for each child compute `nsUri = resolveNamespaceUri(child, xsdData)`:
  - `nsUri == null` → bare name
  - instance maps `nsUri` to prefix `p` (non-empty) → `p:name`
  - instance default namespace == `nsUri` → bare name
  - not declared in the instance → fall back to the schema's prefix for that URI
    (`xsdData.getNamespaces()` reverse lookup; e.g. `c`), else bare name.
- `createElementCompletionItem` gets the qualified name and uses it as **label and
  insertText**; keep `.namespace(nsUri)` and `.prefix(p)` so the badge still renders.
- `IntelliSenseEngine.findPartialTextStart`: accept a typed prefix that matches either the
  full label or its local part (after `:`), so typing `<st` still completes to `c:street`.
  `handleElementInsertion` needs no change (it wraps whatever insertText is).

### 3. Prefix-agnostic sibling scan + sequence-position filter (fixes C, D)

Replace `countExistingSiblings` with a scanner that returns the **ordered list of direct
children before the caret** and (new) **after the caret**, keyed by local name:

- Parent-tag search: regex `<(?:[\w.-]+:)?<localName>(?=[\s>/])` on the last occurrence
  before the caret; `extractLastElementFromXPath` result is stripped to its local name.
- Tag-name group → strip prefix (`name.substring(name.indexOf(':')+1)`).
- The "after caret" scan runs on the text after the caret up to the parent's closing tag,
  same depth-tracking loop (`removeCommentsAndCData` reused). `XmlContext` currently only
  carries `textBeforeCaret`; the provider has no full-text access → add `fullText` (or
  `textAfterCaret`) to `XmlContext`/`Builder` and fill it in `ContextAnalyzer.analyze`.

New pure class `AllowedChildrenCalculator` (in `intellisense/providers/`):

```java
List<XsdExtendedElement> compute(XsdExtendedElement parent, XsdDocumentationData data,
                                 List<String> siblingsBefore, List<String> siblingsAfter)
```
Walks `parent.getChildren()` (document order) recursively through `SEQUENCE_*` / `CHOICE_*` /
`ALL_*` nodes (`isCompositorElement`), with `getMaxOccurs`/`minOccurs` read from
`cardinalityNode ?? currentNode` (move `getMaxOccurs` from the provider into the calculator,
add `getMinOccurs`):

- **sequence**: particle index of each existing sibling = index of the particle that
  contains that local name (recursive lookup). `last = max index among siblingsBefore`
  (−1 if none). Offer particle `last` if its occurrence count < maxOccurs; then particles
  `last+1 …` in order, stopping *after* the first particle with `minOccurs ≥ 1`. If
  `siblingsAfter` is non-empty, additionally cap at `min index among siblingsAfter`
  (that particle itself only if count < max). Sequence with `maxOccurs > 1` (rare): if the
  cap logic yields nothing, restart from particle 0.
- **choice**: if any alternative occurs and choice `maxOccurs == 1` → nothing; else offer all
  alternatives whose own count < maxOccurs.
- **all**: offer every child whose count < maxOccurs (current behaviour).
- A nested compositor particle "occurs" if any of its descendant elements occurs; offering
  a compositor particle recurses into it with the same rules.
- Dedupe by local name as before; result order = schema order (relevance score keeps it).

`getElementCompletions` then becomes: parent lookup → sibling scan → calculator → items.

### 4. Cache (fixes E)

In `IntelliSenseEngine.showCompletions` skip `completionCache` for `ContextType.ELEMENT_NAME`
(the element context; check the enum constant name in `ContextType`) — the result depends on
siblings, and the XSD lookup is a map walk. Keep caching for attribute/value contexts.

### 5. Prefix-agnostic XPath lookup (fixes F)

- `ContextAnalyzer.extractElementName`: strip the prefix (keep raw names nowhere else needed —
  `XPathContext` is only used for schema lookup and the cache key).
- Belt and braces: `MutableXmlSchemaProvider.findBestMatchingElement` strips prefixes from
  `cleanSegments` (mirror `XsdSchemaAdapter.localName`).

### 6. Remote-import cache gap (verify first, fix only if reproduced)

`initializeCachesFromAllSchemas` (1483-1492) and the namespace collector (~2907-2916) skip
`isRemote(location)` imports, while `processAllSchemas` resolves them via catalog/cache into
`processedSchemaFiles`. If the end-to-end test in step 7 shows `/invoice/billTo` has no
children, make both loops iterate `processedSchemaFiles` (already transitive, deterministic
order) instead of re-walking imports from disk.

## Tests (TDD — write failing tests first)

- `XsdElementNamespaceResolverTest` (domain): global/local/form-qualified/unqualified/imported
  document cases with hand-built DOMs.
- `XmlNamespaceDeclarationsTest`: default ns, prefixed ns, redeclaration, prefix lookup.
- `AllowedChildrenCalculatorTest`: synthetic `XsdDocumentationData` built like
  `XsdSchemaAdapterResolveTest.element(...)` — sequence progression, maxOccurs, optional gaps,
  required stop, siblingsAfter cap, nested choice inside sequence, choice-once, all.
- Extend `XsdCompletionProviderFilteringTest`: prefixed siblings (`<c:street>`), prefixed
  parent tag; and a provider-level test asserting label/insertText `c:street` with an
  instance that declares `xmlns:c`, and bare `street` when the instance binds the common ns
  as default namespace.
- `ContextAnalyzerTest`: XPath for caret inside `<c:street>` is `/invoice/billTo/street`.
- End-to-end: `CatalogExampleIntelliSenseTest` (pattern from `CatalogExampleTest`: register
  `release/examples/catalog/namespaces-catalog.xml` in a temp `SchemaLibraryServiceImpl`),
  load `schemas/invoice.xsd` via `MutableXmlSchemaProvider`, run `XsdCompletionProvider` on
  `invoice-namespace-only.xml` text cut (a) right after `<billTo>` → contains `c:street`
  only as first required, (b) after `</c:street>` → `c:city` offered, `c:street` not.
- Run `./gradlew test --tests "org.fxt...XsdCompletionProviderFilteringTest"` etc. (exact
  class names — no leading wildcard, forkEvery=1), then full `./gradlew test`.

## Docs

Update `docs/context-sensitive-intellisense.md` (Key Features: namespace prefixes taken from
the document's `xmlns` declarations; sequence-aware suggestions). Trigger `docs-updater`
after implementation; commit + push per the auto-commit memory.

## Files

- new: `domain/XsdElementNamespaceResolver.java`, `intellisense/context/XmlNamespaceDeclarations.java`,
  `intellisense/providers/AllowedChildrenCalculator.java` (+ tests)
- edit: `intellisense/providers/XsdCompletionProvider.java`, `intellisense/IntelliSenseEngine.java`,
  `intellisense/context/ContextAnalyzer.java`, `intellisense/context/XmlContext.java`,
  `controls/v2/editor/services/MutableXmlSchemaProvider.java`,
  (maybe) `service/XsdDocumentationService.java` (step 6), `docs/context-sensitive-intellisense.md`

## Progress (kept up to date during implementation — resume here after a restart)

Branch: `main` (work directly, commit per step). Session name: `intellisense-namespace`.

- [x] Step 1 `XsdElementNamespaceResolver` + test
- [x] Step 2 `XmlNamespaceDeclarations` + test; qualified label/insertText in provider; `findPartialTextStart`
- [x] Step 3 prefix-agnostic sibling scan (before/after caret) + `AllowedChildrenCalculator` + tests
- [x] Step 4 cache bypass for element completion
- [x] Step 5 prefix stripping in `ContextAnalyzer` / `MutableXmlSchemaProvider` + test
- [x] Step 6 end-to-end catalog test (`CatalogExampleIntelliSenseTest`, 6 scenarios green); remote-import cache gap WAS reproduced and fixed (`initializeCachesFromAllSchemas` + namespace collector now iterate `processedSchemaFiles`)
- [x] Step 7 full `./gradlew test` (6792 tests, only `EditorHostHtmlPreviewTest` timed out once and passes in isolation — unrelated flake), docs updated, committed + pushed. **DONE.**

### Notes / decisions made during implementation
- Prefixes are kept in the `XPathContext` element stack (closing-tag completion needs `ns:element`) and stripped only in `buildXPath()`.
- `ContextAnalyzer.buildXPathContext`: look-ahead for an incomplete tag is cut at the next `<` (typing `<` in front of existing markup no longer pushes that tag).
- Siblings AFTER the caret are honoured strictly: in front of an existing `<c:street>` nothing is offered (street can't occur twice, nothing may precede it). Test `nothingFitsInFrontOfAnExistingStreet` documents this.
- `XsdCompletionProvider.countExistingSiblings` removed; tests use `DirectChildScanner` directly.
- Prefix fallback when the instance doesn't declare the namespace: the schema's own prefix (no xmlns is inserted).
