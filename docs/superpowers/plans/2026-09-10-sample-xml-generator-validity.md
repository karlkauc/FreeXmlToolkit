# Sample XML Generator: Valid Output on Real-World Schemas (Improvement Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan work package by work package. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "Generate Sample XML…" (plain and realistic, mandatory-only and with optional elements) produces
schema-valid XML for real-world schemas, and always produces *some* XML instead of an error comment, a stack
overflow or an OutOfMemoryError.

**Evidence:** `docs/superpowers/reports/2026-09-10-sample-xml-generator-real-world-audit.md`. That audit ran the
generator over the 35-schema corpus in `src/test/resources/xsd/real-world` and validated every sample offline with
Xerces. This plan is prioritized by the measured numbers in that report, not by guesses.

**Architecture:** Two phases.
1. **Phase 1** fixes the generator where it stands: the XPath-keyed element map built by
   `XsdDocumentationService.processXsd`, rendered by `generateSampleXml` / `ProfiledXmlGeneratorService`, with values
   from `XsdSampleDataGenerator`. Phase 1 fixes loading, memory and the largest structural error clusters, and each
   package is measured with the corpus audit.
2. **Decision gate:** decide whether the structural walk moves onto the Xerces `XSModel`, which the audit harness
   already compiles for validation. The value layer (`XsdSampleDataGenerator`, strategies, profiles) stays either way.

**Tech Stack:** Java 25 (preview), Xerces 2.12.2 (exist-db XSD 1.1 build), Gson, JUnit 6 (Jupiter), Gradle Kotlin DSL.

## Baseline (audit of 2026-09-10, maxOccurrences = 2)

31 of the 35 schemas are evaluable: the reference schema compiles offline and has at least one global element.

| Measure | plain required | plain optional | realistic required | realistic optional |
|---|---|---|---|---|
| First global element (what the app generates today), valid of 31 | 19 | 14 | 20 | 15 |
| … schemas without any XML (error comment, StackOverflowError, OOM) | 7 | 7 | 7 | 7 |
| All app-offered global elements, valid of 130 validated samples | 74 | 63 | 74 | 62 |

Of the 274 invalid samples, the report maps each validation error to one of the work packages below.
- **Touched** is the number of invalid samples that contain at least one error of that package.
- **Alone** is the number of samples that become valid once only that package is done.

| WP | Touched | Alone | Schemas |
|---|---|---|---|
| D content type and root emission | 109 | 58 | rim, XBRL, INSPIRE |
| B inherited attributes | 103 | 50 | rim, XTCE 2018/2025, Subsonic, INSPIRE |
| C namespace qualification | 60 | 55 | INSPIRE, AEAT Modelo 170, SIRI FR-IDF, XBRL |
| G mandatory content completeness | 33 | 22 | datajud, SIRI, xmldsig, XBRL, XTCE, FundsXML4 |
| F simple values | 30 | 11 | XBRL, INSPIRE, XTCE, Garmin TCX, FundsXML4, SIRI FR-IDF |
| E abstract elements/types | 26 | 12 | XBRL, INSPIRE, rim, Garmin TCX |
| H identity constraints / IDs | 13 | 0 | XTCE, Garmin TCX, FundsXML4 |

- **Skew:** the breadth numbers are weighted towards rim (43 roots), xmldsig (24) and INSPIRE (13).
- **User-facing impact:** WP A matters most to users, because it turns "no XML at all" into output for 7 of the 31
  schemas (plus A-GRA).
- **Progress after the quick wins A1, A2 (types), D1, F3** (same day, report §11):

  | Measure | plain required | plain optional | realistic required | realistic optional |
  |---|---|---|---|---|
  | First element valid (of 31) | 23 | 18 | 22 | 15 |
  | No XML (of 31) | 4 | 4 | 4 | 5 |
  | Breadth valid (of 132) | 82 | 70 | 81 | 69 |

  UBL 2.1 and goAML now fail on memory instead of parsing or recursion, which makes **A5 the next priority** for
  "no XML".
- **Progress after A5** (report §12): first element valid 24 · 17 · 24 · 19, no XML 1 · 2 · 1 · 2.
  - UCI, A-GRA, KML and goAML now generate.
  - Remaining "no XML": JATS (**A4 next**) and UBL 2.1 with optional elements (node limit).
  - KSeF FA(3) generates XML, but its validation times out in the audit.

## Global Constraints

- **No behaviour loss:** keep `generateSampleXml(boolean, int)`, `generateSampleXml(String, boolean, int)`,
  `getRootElementNames()` and `ProfiledXmlGeneratorService.generateRealistic(…, rootElementName)` working;
  profiles and XPath rules keep matching.
- **Tests first:** every work package starts with failing golden tests. Use a mini-XSD per construct under
  `src/test/resources/xsd/sample-gen/<construct>/` and validate the generated XML with Xerces in both generators and
  both modes. Mirror `SampleXmlRootSelectionTest`.
- **Re-audit:** after each package, run `./gradlew sampleXmlAudit` and record the new numbers in the report's
  history table. A package is done when its "alone" samples are valid and no schema regresses.
- **Documentation model:** the XPath-keyed element map also drives the HTML/Excel documentation. Changes to
  `processXsd` must keep `ProcessXsdEquivalenceTest` and the documentation tests green.
- **Circular references:** always guard against them (see `.claude/rules/domain.md`).
- **Offline tests:** tests never touch the network.
- **Gradle runs:** no source edits while a Gradle run is in progress. Run single test classes by exact class name.
- **Corpus in git:** the corpus is not committed (publisher licences). Ratchet tests that need it must skip when it
  is absent.

---

## WP A: Loading robustness and memory (no XML at all)

**Evidence:** UBL 2.1 and KSeF FA(3) throw `StackOverflowError`. goAML and nalog end in "Error processing XSD". JATS
reports "No root element found". UCI and A-GRA run out of memory even with a 14 GB heap. KML runs out of memory at
6 GB.

- [x] **A1. Encoding and BOM.** *(done 2026-09-10: `parseXsdFile(Path)` parses bytes; `XsdSchemaEncodingTest`)* Stop reading schema files with `Files.readString(…, UTF_8)` and parsing the string.
  Parse the bytes (`new InputSource(InputStream)` with the file URI as systemId), so the XML parser honours the
  declared encoding and a BOM.
  - Sites: `XsdDocumentationService.java:557`, `:1447`, `:1524`, `:2986`.
  - Golden tests: a windows-1251 schema (nalog) and a UTF-8-with-BOM schema (goAML).
- [x] **A2. Namespace-aware global definition maps.** *(types done 2026-09-10:
  `lookupGlobalDefinition`/`findGlobalType` resolve complex and simple types through the prefix, with a local-name
  fallback; `getInheritedFacets` has a cycle guard; the three extension expansions skip circular chains
  (`derivesFromItself`); `CrossNamespaceTypeResolutionTest`. Still keyed by local name: `elementMap`, `groupMap`,
  `attributeGroupMap` and the sample type resolver `resolveTypeToBase` / `resolvedTypeMemo`, which gives KSeF FA(3)
  wrong facets for its value generation.)* `simpleTypeMap`, `complexTypeMap`, `elementMap`,
  `groupMap` and `attributeGroupMap` are keyed by local name (`stripNamespace`).
  - **UBL:** `udt:IdentifierType` extends `ccts-cct:IdentifierType`, so `processComplexContent` (`:1995-2004`)
    recurses into itself.
  - **FA(3):** `TData` restricts `etd:TData`, so `getInheritedFacets` (`:3536-3564`) does the same.
  - **Fix:** key the maps by `{namespace}local`, resolve prefixes against the in-scope namespace context of the
    referencing node, and fall back to the local name only when the name is unique.
  - Add a visited-set cycle guard to `getInheritedFacets`, `findTypeDefinition` and the extension branch of
    `processComplexContent`.
  - Golden test: two namespaces declaring a type with the same local name, one deriving from the other.
- [ ] **A3. Resolve includes/imports relative to the including document.**
  - `processAllSchemas` resolves every local `schemaLocation` against the main schema's directory (`:1460`,
    `:1498`, and `baseUri` at `:1485`).
  - Per processing run, the generator logged about 58 "Included file not found" and 11 "Imported file not found"
    warnings for SIRI 2.2 (plus about 29 unresolved references), about 49 includes for SIRI FR-IDF, about 37 for
    JATS, about 8 for INSPIRE and about 5 imports for UBL.
  - Queue `(file, baseDir)` pairs instead, route remote `xs:include` through `resolveRemoteImport` as well, and
    follow `xs:redefine`/`xs:override` locations.
  - Golden test: a nested directory corpus with `../` includes.
  - Re-audit expectation: SIRI 2.2 `ServiceDelivery` (missing `ResponseTimestamp`) should become valid.
- [ ] **A4. Offer roots from included documents.** `populateDocumentationData` only takes
  `/xs:schema/xs:element[@name]` of the main document (`:2961`). The app therefore offers 0 of JATS's 308
  same-namespace global elements and 3 of SIRI's 385.
  - Collect global elements of every document with the main target namespace (includes, chameleon includes), in
    document order.
  - Expose them via `getRootElementNames()`, mark abstract ones, and default to the first non-abstract root.
  - Test: `JATS-archivearticle1-4.xsd` offers `article`.
- [x] **A5. Bounded memory.** *(done 2026-09-10, report §12:
  - shared Markdown renderer; snippets and documentation shared per schema node
  - root-scoped sample expansion (`expandForSample`) with node and output limits (`SampleXmlLimits`)
  - `BoundedPatternSampler` instead of `Generex.random`
  - UCI, A-GRA, KML and goAML now generate; UBL 2.1 generates mandatory-only and reports the node limit with
    optional elements
  - still open: the full documentation `processXsd` of UBL 2.1 exceeds a 3 GB heap)* `processXsd` expands the full subtree of *every* global element into the XPath map
  before a single sample is generated. UCI (722 globals, one file) and A-GRA (860 globals) exhaust 14 GB. KML
  (292 globals) needs more than 6 GB and 78 s per `processXsd` run.
  - Measure with a heap histogram which part grows: the per-node documentation strings, `sourceCode` copies of every
    node, or the sheer path count.
  - Generation should not need the documentation map. Either expand lazily from the chosen root only, or share one
    expanded subtree per named type instead of copying it per path.
  - Acceptance: every corpus schema generates within the default 6 GB worker heap.
  - Keep a hard node budget with a clear error comment instead of OOM.

**Target:** "no XML" drops from 7 to 0 of 31 (plus A-GRA once its missing include is tolerated).

## WP D: Content type and root emission

**Evidence:** 109 invalid samples are touched, 58 of them only by this package.

- [x] **D1. Root with simple content.** *(done 2026-09-10 for roots without child elements, in both generators;
  `SampleXmlRootEmissionTest`)* `generateSampleXmlFor` never writes the root element's own value and
  always writes `>\n … </root>` (`:2188`, `:2205`).
  - A global element of simple type or simple content (XBRL `measure` QName, `numerator` decimal) comes out empty
    or whitespace.
  - An empty-content root (rim `Action`) gets whitespace, which fails `cvc-complex-type.2.1`.
  - Fix: render the root through the same path as child elements. Emit text for simple content, a self-closing tag
    for empty content, and no indentation whitespace inside simple or empty content.
  - Apply the same fix to `ProfiledXmlGeneratorService.buildXmlDocument`.
- [ ] **D2. Attribute-only complex types are not simple content.** `XsdSampleDataGenerator.java:147-155` treats an
  element whose children are all attributes as `simpleContent` and generates text for it. rim `LocalizedString`
  (124 occurrences), `Address` and `PersonName` break on that.
  - Decide from the type definition (`complexContent` / `simpleContent` / no content model), not from the child
    list.
  - Store a content-type flag (EMPTY, SIMPLE, ELEMENT_ONLY, MIXED) on `XsdExtendedElement` during `processXsd`.
- [ ] **D3. Mixed content.** Emit no text inside element-only content; for `mixed="true"`, text is optional.

**Target:** the 58 "alone" samples become valid (rim 38, XBRL 16, INSPIRE 4).

## WP B: Inherited attributes (and attribute groups) of base types

**Evidence:** 103 samples are touched, 50 of them only by this package. Examples of required attributes that are
missing: rim `@id` (from `IdentifiableType`, two extension levels up), XTCE `@parameterRef`, Subsonic
`jukeboxPlaylist@currentIndex|playing|gain` (base `JukeboxStatus`), INSPIRE `@uom`.

- [ ] **B1.** In `processComplexContent`'s extension branch (`:1995-2004`), only the base type's *content model* is
  processed (`findContentModel`), so attributes and `attributeGroup` refs of the base are dropped.
  - Walk the full derivation chain (with the A2 cycle guard) and collect attribute uses and attribute groups of
    every level, for `complexContent` and `simpleContent` extensions and restrictions (a restriction may prohibit
    attributes).
- [ ] **B2.** Verify `attributeGroup` refs at every level, including nested groups and groups from imported
  namespaces (see C3).
- [ ] **B3.** Golden tests:
  - a three-level extension chain with a required attribute at the top
  - a `simpleContent` extension with a required attribute (`MeasureType@uom`)
  - an attribute group in the base

**Target:** rim 48 + 42 (with D), XTCE and Subsonic samples valid; `cvc-complex-type.4` without a namespace
disappears from the audit.

## WP C: Namespace qualification

**Evidence:** 60 samples are touched, 55 of them only by this package. The generator emits elements in the root's
default namespace unless they came through a prefixed element `ref`. Examples:
- AEAT Modelo 170 `comun:Modelo` is emitted in the `m170` namespace (100 % of its samples invalid).
- INSPIRE `gn:spelling` is emitted in the wrong namespace (128 occurrences).
- SIRI FR-IDF `MessageText` is emitted in the SIRI namespace although its local declaration is unqualified.
- XBRL `xlink:type`/`xlink:href` attribute refs are emitted as *child elements*, because `traverseNode` routes any
  prefixed `ref` to `processExternalNamespaceReference` (`:1557-1565`).

- [ ] **C1. Namespace per particle.** Record on each `XsdExtendedElement` the namespace it must be emitted in:
  - global element: the target namespace of its declaring document
  - local element: the declaring document's target namespace if `form="qualified"` or that document's
    `elementFormDefault="qualified"`, else no namespace
  
  `elementFormDefault`/`attributeFormDefault` are read today (`:2950-2951`) but never used, and only for the main
  document.
- [ ] **C2. Rendering.** Declare one prefix per used namespace on the root (reuse the schema's prefixes, generate
  `ns1…` on clashes). Write every element as `prefix:local`. For no-namespace local elements under a default-namespace
  root, either render the root with a prefix or emit `xmlns=""`. Cover `collectUsedNamespaces` (`:2138` today, which
  skips attributes) and the profiled generator.
- [ ] **C3. Attribute refs to other namespaces.**
  - Handle `xs:attribute ref="xlink:href"` and imported `attributeGroup`s as attributes with a declared prefix, never
    as elements.
  - Qualified local attributes (`attributeFormDefault`) get a prefix too.
  - `xml:lang` uses the reserved `xml` prefix.
- [ ] **C4.** Golden tests:
  - a type imported from namespace B used by an element in A, with B qualified and with B unqualified
  - a chameleon include
  - an `xlink` attribute group
  - `xml:lang`

**Target:** AEAT Modelo 170 3/3, SIRI FR-IDF 2/2 (plain), the INSPIRE `gn:` cluster and the XBRL `link:*Ref`
samples (xlink attributes currently emitted as child elements) valid.

## WP G: Mandatory content completeness

**Evidence:** 33 samples are touched, 22 of them only by this package.

- [ ] **G1. Choice option selection** (`buildXmlElementContent` `:2473-2527`, `processChildElementsForGeneration`
  `:2654-2725`).
  - A random option is chosen and then skipped when it is optional (mandatory-only mode) or would be empty. A
    required choice then ends up empty (datajud `comunicacaoprocessual`).
  - Fix: choose only among options that produce content. In mandatory-only mode prefer the option with the smallest
    required content, and fall back to emitting an optional option's minimal content instead of nothing.
- [ ] **G2. Required recursion.** `traverseNode` aborts a branch as soon as a node repeats on the path (`:1548`). A
  choice with `minOccurs="2"` whose options recurse (XTCE `ORedConditions`/`ANDedConditions`) then has too few
  children.
  - Let the generator (not the documentation walk) expand recursive particles on demand, with a depth budget. Near
    the budget, pick the non-recursive option.
- [ ] **G3. Required wildcards.** `xs:any` is only recorded (`processWildcards` `:3425`).
  - For `minOccurs ≥ 1`, emit one element that the namespace constraint allows. For `##other`, use a foreign
    namespace such as `urn:fxt:sample`; for a list, use the first listed namespace. With `processContents="strict"`,
    pick a global element of an allowed namespace.
  - Evidence: xmldsig `SignatureProperty` (8 samples), XBRL `segment`/`scenario`.
- [ ] **G4. Occurrence bounds.** Honour `minOccurs`/`maxOccurs` on `sequence`/`all` (repeat the group), and emit at
  least `minOccurs` repetitions in mandatory-only mode. Today a repeating element always gets `maxOccurrences` copies,
  even in mandatory-only mode (`:2529-2543`).
- [ ] **G5.** Golden tests:
  - a required choice with only optional options
  - `choice minOccurs=2` with recursive options
  - required `xs:any ##other`
  - a sequence with `minOccurs=2`

## WP F: Simple value generation

**Evidence:** 30 samples are touched, 11 of them only by this package.

- [ ] **F1. Types without a value.** QName, NOTATION, ENTITY/ENTITIES and anySimpleType/anyAtomicType fall into the
  `default` branch and yield `""` (`XsdSampleDataGenerator.java:279-288`).
  - QName: `prefix:local`, with a prefix declared on the root (e.g. the target-namespace prefix).
  - NOTATION: a declared notation or omit.
  - anySimpleType: `"text"`.
- [ ] **F2. Unions and lists.** Generate a value from the first member type that can produce one, including inline
  `simpleType` members; for lists, emit 1..n items. Today union members resolve to one (named) member or nothing.
  Evidence: XBRL `nonZeroDecimal`, `dateUnion`, XTCE `EpochType`.
- [x] **F3. Unsigned ranges.** *(done 2026-09-10: `generateUnsignedInteger` prints via `BigInteger` and clamps to
  the type's value space; `XsdSampleDataGeneratorTest`)* `unsignedByte`, `unsignedShort`, `unsignedInt` and `unsignedLong` are printed via the
  *signed* Java type (`printByte(value.byteValue())` etc., `:226-241`). Values above the signed maximum wrap to
  negative numbers (Garmin `Cadence = -77`, 233 occurrences). Use the right range per built-in type and print via
  `BigInteger`.
- [ ] **F4. Facets on attributes and simple content.** Attribute types with a pattern (XTCE `NameType`
  `[^.\[\]:/ \t]+`) and simple-content elements (SIRI FR-IDF `StopPointRef` NMTOKEN, INSPIRE `value` double) come
  out empty. Use the same type resolution for attributes and simple-content bases as for elements.
- [ ] **F5. Pattern generation.** *(partly done with A5: `BoundedPatternSampler` terminates within the length range,
  caps repetitions and emits only XML 1.0 characters. Pattern intersection and XSD escapes are still open.)*
  - Restrict Generex output to printable characters. The XTCE samples contain unassigned or control code points;
    one realistic XTCE sample is not even well-formed.
  - Intersect patterns across the derivation chain (all steps must match).
  - Translate XSD-only escapes (`\i`, `\c`, `\p{Is…}`) before Generex.
- [ ] **F6.** `min/maxExclusive` for decimals: use the smallest step that fits `fractionDigits`, not ±1.

## WP E: Abstract elements and types

**Evidence:** 26 samples are touched, 12 of them only by this package: XBRL `item`/`tuple`, INSPIRE
`bu-base:Building`, `AddressComponent`, rim `Action`, Garmin `Creator` (`AbstractSource_t`).

- [ ] **E1. Substitution groups.** Build a substitution-group index over all documents. Where a particle
  references an abstract element (or any head element), emit a concrete, non-abstract member, preferring the target
  namespace.
- [ ] **E2. Abstract types.** For an element whose type is abstract, choose a concrete derived type (extension or
  restriction, any namespace), emit `xsi:type="prefix:Type"` and generate the derived content.
- [ ] **E3. Abstract roots.** Do not offer abstract global elements as roots (or list them last and generate a
  substitution-group member instead). KML has 124 abstract globals, XBRL 2, INSPIRE 1.

## WP H: Identity constraints and IDs

**Evidence:** 13 samples are touched, all together with other packages (XTCE, Garmin TCX, FundsXML4).

- [ ] **H1.** In the plain path, a value is computed once per schema node (`displaySampleData`) and repeated for
  every occurrence. Keys, uniques and IDs therefore collide. Examples: `Folder@Name` "ExampleText", `Activity/Id`
  dateTime, FundsXML4 `UniqueID` `id_generated_1`. Generate per occurrence for ID-typed values and constrained
  fields.
- [ ] **H2.** `IdentityConstraintTracker`: support attribute fields reached through `selector` paths with `.//`,
  union selectors (`a|b`), and keys on dateTime/QName values. Ensure a `keyref` has a matching key (XTCE
  `containerRef`).

## WP R: Realistic path parity and reproducibility

- [ ] **R1.** The realistic generator produced the same structural failures. It differs only in values: its own,
  weaker `setupTypeResolver` (`ProfiledXmlGeneratorService.java:855`) scans the element map per lookup. Route it
  through `XsdDocumentationService.resolveTypeToBase` and share the structural rendering (D, C, G) instead of keeping
  two copies of the walk.
- [ ] **R2.** Seedable randomness for values and choices, so an audit run is reproducible. The datajud first
  element flipped between valid and invalid across runs.

---

## Decision gate after WP A–D: element map vs. XSModel

After A–D, re-run the audit. If the remaining structural clusters (C, E, G) still exceed about 20 % of all generated
samples, move the *structural* walk of the generator onto the Xerces `XSModel` instead of extending the DOM-based
walk further.
- `XSModel` gives namespace-correct components, inherited attribute uses
  (`XSComplexTypeDefinition.getAttributeUses()`), content types, particles with occurrence ranges, wildcards,
  substitution-group affiliations, abstract flags and full facet/union/list information. Catalogs are handled through
  the same `LSResourceResolver`.
- The audit harness (`AuditSchemaCompiler`) already compiles exactly this model.
- Keep `XsdSampleDataGenerator`, strategies and `GenerationProfile` XPath rules as the value layer on top; map
  generated paths back to the XPath syntax the rules use.

**Recommendation:** do A1, A2, D1 and F3 first. They are small, isolated and remove crashes and very frequent errors.
Then do A3–A5 and B, re-measure, and take the gate decision with the numbers.

## Verification (every work package)

1. Golden tests for the construct (both generators × both modes, Xerces-validated):
   `./gradlew test --tests "org.fxt.freexmltoolkit.service.<TestClass>"`.
2. Regression tests: `SampleXmlRootSelectionTest`, `SampleXmlRunnerTest`, `ProfiledXmlGeneratorServiceTest`,
   `GeneratedXmlValidationTest`, `ExternalNamespaceReferenceTest`, `XsdSampleDataGeneratorTest`,
   `ProcessXsdEquivalenceTest`, `sampleaudit.AuditHarnessSelfTest`.
3. Corpus audit: `./gradlew sampleXmlAudit` (about 10 minutes; since the quick wins about 40 minutes because of the
   KSeF FA(3) validation). Compare `build/sample-xml-audit/summary.csv` with the baseline above and append the numbers
   to the report's history table.
   - [x] Harness: add a time limit for validating a single sample *(done with A5)* (FA(3) took about 35 minutes) and record it as
     `VALIDATION_TIMEOUT`.
4. Optional ratchet, once the corpus licence question is settled: `SampleXmlCorpusRatchetTest` with per-schema
   minimum valid counts from `summary.csv`, skipped when the corpus is absent.
