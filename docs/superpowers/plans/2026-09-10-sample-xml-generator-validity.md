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
- **Progress after A4** (report §13): no XML 0 · 1 · 0 · 1, only UBL 2.1 with optional elements (node limit).
  - JATS generates `article`, still invalid: its content modules are among the includes that are not found.
- **Progress after A3, B, G6, D4, A6 and the A2 element references** (report §14): first element valid
  26 · 20 · 26 · 19, no XML 0 · 0 · 0 · 0 (KSeF FA(3) still exceeds the validation timeout).
  - rim 42 of 43 roots valid in every mode; SIRI 2.2 365 of 385 (mandatory only); JATS 295 of 308 and `article`
    valid (mandatory only).
  - Largest remaining clusters: UCI `DateTimeType` (`xs:dateTime` with pattern `.+Z`, sampled as a string; 722
    samples, one type, F5), abstract
    roots and elements (E, KML 124), IDREF without ID (H, most of JATS with optional elements), namespace
    qualification (C, AEAT). Next: **F5 for the UCI timestamp**, then E3, H and C.
- **Progress after F5 typed values for patterns** (report §15): first element valid 28 · 19 · 27 · 19, no XML 0;
  breadth valid 1,621 · 1,130 · 1,520 · 990 of 1,814 (UCI 0 → 709 of 722 mandatory-only).
  - Largest remaining clusters: abstract elements and types (E: `cvc-elt.2` 143, `cvc-type.2` 83 samples, KML 125
    abstract roots), IDREF without ID with optional elements (H: 275, nearly all JATS). Next: **E**, then H.
- **Progress after E, the defects it exposed, and H1/H2** (report §16): first element valid 28 · 21 · 27 · 19, no XML
  0; breadth valid 1,637 · 1,548 · 1,537 · 1,390 of 1,672 (abstract roots are no longer offered; F5: 1,621 · 1,130 ·
  1,520 · 990 of 1,814).
  - Samples with abstract elements or types 143 · 83 → 0 (plain, mandatory only); IDREF without ID with optional
    elements 268 → 0; UCI 722 · 719 of 722, JATS 275 of 308 with optional elements.
  - Fixed on the way (found by the re-audits): `minOccurs` above the repetition cap (G4), reference bounds inherited by
    the referenced content, sampler dead ends below the minimum length (F5), repeated compositors counted as recursion
    (G2), identity-constraint paths with prefixes, `.//`, `*` and `|` (H2), and exponential repetition (UBL 2.1).
  - Largest remaining clusters: namespace qualification including foreign attributes (C: JATS `xlink`, INSPIRE,
    AEAT) and realistic values for SIRI with optional elements (207 samples). Next: **C**, then those values.
- **Progress after C and the defects it exposed** (report §17): first element valid 30 · 25 · 28 · 23, no XML 0;
  breadth valid 1,657 · 1,629 · 1,553 · 1,423 of 1,672 (invalid 35 · 124 · 135 · 282 → 15 · 43 · 119 · 249).
  - Samples with an element in the wrong namespace or a missing qualified attribute → 0 in every mode; AEAT 3 of 3,
    INSPIRE 12 of 12, JATS 301 of 308 and KML 145 of 145 with optional elements (plain).
  - Fixed on the way: imports of a missing relative file now resolve through the Schema Library by namespace (JATS
    `xlink`, also in the app), `fixed`/`default` on attribute references, dk.brics operators in XSD patterns, and
    e-mail patterns that skipped the sampler.
  - Largest remaining cluster: realistic SIRI values (100 · 208 samples). Next: **those values** (F, R1), then G.
- **Progress after R1 type resolution in the realistic generator** (report §18): first element valid 30 · 24 · 30 · 24,
  no XML 0; breadth valid 1,658 · 1,637 · 1,657 · 1,634 of 1,672 (invalid 14 · 35 · 15 · 38).
  - The realistic generator is on par with the plain one; SIRI 2.2 realistic 271 → 370 of 370 (mandatory only),
    164 → 351 (with optional elements).
  - Remaining: SIRI with optional elements 21 (`cvc-type.3.1.3`, `cvc-enumeration-valid`, not analysed), XBRL 5–6,
    JATS IDREFs in documents without any ID (6), xmldsig required `xs:any` (2). Next: **analyse SIRI**, then G.
- **Progress after F4 inherited facets** (report §19): first element valid 30 · 25 · 29 · 25, no XML 0; breadth valid
  1,657 · 1,651 · 1,657 · 1,651 of 1,672 (invalid 15 · 21 · 15 · 21; §15: 193 · 684 · 294 · 824 of 1,814).
  - The SIRI cluster was a facet defect: a restriction's enumeration was appended to its base's. SIRI 2.2 is now 370 of
    370 in every mode.
  - Remaining, by samples affected: required `xs:any` (G3, 14), QName values (F1, 13), IDREFs in documents without
    any ID (H, 11), `hexBinary` with `length` (F, 7). Next: **G3**, then F1.
- **Progress after G3 required wildcards** (report §20): first element valid 30 · 25 · 30 · 25, no XML 0; breadth
  valid 1,660 · 1,654 · 1,662 · 1,654 of 1,672 (invalid 12 · 18 · 10 · 18).
  - xmldsig 24 of 24 and XBRL 4 of 8 in every mode; wildcard errors 28 → 2.
  - Remaining: `QName`, binary `length` and inline union values (F: XBRL, UCI), IDREFs in documents without any ID
    (H: JATS 7). Next: **those values**, then H.
- **Progress after F1/F2 values, H IDs for references and unsubstituted abstract elements** (report §21): first
  element valid 30 · 24 · 30 · 25, no XML 0; breadth valid 1,671 · 1,664 · 1,670 · 1,659 of 1,672 (invalid
  1 · 8 · 2 · 13; §15: 193 · 684 · 294 · 824 of 1,814).
  - KML 145 of 145, XBRL 8 of 8 and UCI 722 of 722 in every mode; no IDREF without an ID is left.
  - Found on the way: an abstract element nothing substitutes (KML `ObjectSimpleExtensionGroup`) is left out where
    the content model allows it.
  - Remaining: particles with the same name in one compositor collide in the element map (JATS `ruby`), empty
    required choices (G1: JATS `statement`, `question`, datajud), duplicate key values (H2: XTCE, Garmin). Next:
    **the element-map collision**, then G1.
- **Progress after G7 particles with the same name** (report §22): first element valid 30 · 25 · 30 · 24, no XML 0;
  breadth valid 1,672 · 1,663 · 1,672 · 1,661 of 1,672 (invalid 0 · 9 · 0 · 11).
  - Every sample with mandatory elements only is valid, in both generators; JATS `ruby` is valid in every mode.
  - Remaining, all with optional elements: empty required choices (G1: JATS `statement`, `question`, `speech`,
    `open-access`; datajud's strict `xs:any`), key values (H2: XTCE, Garmin), INSPIRE `bu-base:Building`, UBL
    `WitnessParty` order. Next: **G1**.
- **Progress after G1 choices with complete content** (report §23): first element valid 30 · 27 · 30 · 27, no XML 0;
  breadth valid 1,672 · 1,668 · 1,672 · 1,669 of 1,672 (invalid 0 · 4 · 0 · 3).
  - JATS 308 of 308, INSPIRE 12 of 12, datajud and UBL valid in every mode.
  - Remaining: key values (H2: XTCE duplicate names and `messageNameKey` on `MessageSet/*`, Garmin `dateTime` key),
    one UCI `AngleType` value rounded past `maxInclusive`. Next: **H2**.
- **Progress after H2 key values of their own type** (report §24): first element valid 30 · 29 · 30 · 29, no XML 0;
  breadth valid 1,672 · 1,670 · 1,672 · 1,671 of 1,672 (invalid 0 · 2 · 0 · 1).
  - XTCE valid in every mode.
  - Remaining: Garmin keyref fallback without generated key values and a suffixed key past `maxLength` (H2), UCI
    angles rounded past `maxInclusive` (F6). Next: **F6**, then the Garmin keys.
- **Progress after F6 numbers within their bounds** (report §25): first element valid 30 · 29 · 30 · 29, no XML 0;
  breadth valid 1,672 · 1,671 · 1,672 · 1,671 of 1,672 (invalid 0 · 1 · 0 · 1).
  - UCI 722 of 722 in every mode. Only Garmin's root is left.
  - Remaining: keyrefs before their keys, no `Workout` because `Repeat_t` recursion leaves its step incomplete, and a
    suffixed key past `maxLength` (H2). Next: **the Garmin keys and steps**.
- **Progress after the Garmin keyrefs and derived types** (report §26): first element valid 30 · 30 · 30 · 29, no XML
  0; breadth valid 1,672 · 1,672 · 1,672 · 1,672 of 1,672 (invalid 0 · 0 · 0 · 0).
  - Every validated sample of every offered root is valid in both generators and modes.
  - Remaining: Garmin's realistic first-element sample repeats a `StepId` value, because two field paths of one unique
    constraint advance different numeric bases with one counter (H2). Next: **numeric key values against used values**.

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
  (`derivesFromItself`); `CrossNamespaceTypeResolutionTest`. Element, group and attribute-group references followed on
  2026-09-10 (`CrossNamespaceReferenceResolutionTest`): JATS imports MathML, which declares its own `sec`, `list`,
  `title`, `annotation` and `product`, so JATS `sec` had MathML content and attributes once B2 expanded attribute
  groups. Still keyed by local name: the sample type resolver `resolveTypeToBase` / `resolvedTypeMemo`, which gives
  KSeF FA(3) wrong facets for its value generation.)* `simpleTypeMap`, `complexTypeMap`, `elementMap`,
  `groupMap` and `attributeGroupMap` are keyed by local name (`stripNamespace`).
  - **UBL:** `udt:IdentifierType` extends `ccts-cct:IdentifierType`, so `processComplexContent` (`:1995-2004`)
    recurses into itself.
  - **FA(3):** `TData` restricts `etd:TData`, so `getInheritedFacets` (`:3536-3564`) does the same.
  - **Fix:** key the maps by `{namespace}local`, resolve prefixes against the in-scope namespace context of the
    referencing node, and fall back to the local name only when the name is unique.
  - Add a visited-set cycle guard to `getInheritedFacets`, `findTypeDefinition` and the extension branch of
    `processComplexContent`.
  - Golden test: two namespaces declaring a type with the same local name, one deriving from the other.
- [x] **A3. Resolve includes/imports relative to the including document.** *(done 2026-09-10, commit `47df9550`,
  report §14: every `schemaLocation` resolves against its declaring document; a remote `xs:include` goes through the
  Schema Library; relative references of a remote document resolve against its URL; `XsdNestedIncludeResolutionTest`.
  Not done: `xs:redefine`/`xs:override`, which no corpus schema uses. The re-audit exposed defects in the newly
  reached GML and JATS content, fixed alongside: C3 in part, D4, G6.)*
  - `processAllSchemas` resolves every local `schemaLocation` against the main schema's directory (`:1460`,
    `:1498`, and `baseUri` at `:1485`).
  - Per processing run, the generator logged about 58 "Included file not found" and 11 "Imported file not found"
    warnings for SIRI 2.2 (plus about 29 unresolved references), about 49 includes for SIRI FR-IDF, about 37 for
    JATS, about 8 for INSPIRE and about 5 imports for UBL.
  - Queue `(file, baseDir)` pairs instead, route remote `xs:include` through `resolveRemoteImport` as well, and
    follow `xs:redefine`/`xs:override` locations.
  - Golden test: a nested directory corpus with `../` includes.
  - Re-audit expectation: SIRI 2.2 `ServiceDelivery` (missing `ResponseTimestamp`) should become valid.
- [x] **A4. Offer roots from included documents.** *(done 2026-09-10, commit `be93cdc9`, report §13:
  - roots from same-namespace includes, chameleon includes included
  - default root = first main-document element, otherwise the first unreferenced non-abstract element of an include
    (JATS → `article`)
  - abstract roots are still offered, see E3)* `populateDocumentationData` only takes
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
- [x] **A6. Bounded sample expansion of recursive optional content.** *(done 2026-09-10, report §14.)* Once group
  references and includes resolved (A3, G6), JATS `article` (both modes) and SIRI `Siri` with optional elements
  exceeded the node limit. Compositors reached through `processComplexContent` bypassed the mandatory-only pruning,
  and optional content recursing through many different declarations (JATS inline elements) grew with the number of
  permutations. A sample now expands the optional content of each element declaration once; later occurrences get
  their required content only. JATS `article`: 237 nodes mandatory-only, 175,165 with optional elements; SIRI
  `Siri`: 11,298. Test: `SampleXmlBoundedExpansionTest`.

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
- [x] **D4. Empty content below the root.** *(done 2026-09-10, found by the A3 re-audit: INSPIRE `gn:nameStatus`,
  `gn:nativeness`, `specification`.)* An element whose only children are structural containers that emit nothing
  (GML `ReferenceType` = empty `sequence` + attribute groups) was written as `<x>`, newline, indentation, `</x>`.
  Both generators now write a self-closing tag. The plain generator also skips an optional `sequence`/`all` in
  mandatory-only mode, as the namespace collection and the realistic generator do; emitting it had used an
  undeclared `gml` prefix (`SampleXmlForeignContentTest`).
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

- [x] **B1.** *(done 2026-09-10: the extension branch adds the base type's own attributes; a complexContent or
  simpleContent restriction inherits the attributes of its base chain unless it restates or prohibits them
  (`processRestrictionBaseAttributes`); prohibited attributes are left out.)* In `processComplexContent`'s extension branch (`:1995-2004`), only the base type's *content model* is
  processed (`findContentModel`), so attributes and `attributeGroup` refs of the base are dropped.
  - Walk the full derivation chain (with the A2 cycle guard) and collect attribute uses and attribute groups of
    every level, for `complexContent` and `simpleContent` extensions and restrictions (a restriction may prohibit
    attributes).
- [x] **B2.** *(done 2026-09-10: attribute groups were not expanded anywhere; `processAttributeUses` now expands
  them, nested groups included, on types, extensions and restrictions. Groups are still looked up by local name, see
  A2.)* Verify `attributeGroup` refs at every level, including nested groups and groups from imported
  namespaces (see C3).
- [x] **B3.** *(done 2026-09-10: `SampleXmlInheritedAttributesTest`, both generators × both modes, validated with
  Xerces; it also needed a multi-level simpleContent base (`LengthType` → `MeasureType` → `xs:double`), now followed by
  `simpleContentBaseType`, part of F4.)* Golden tests:
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

- [x] **C1. Namespace per particle.** Record on each `XsdExtendedElement` the namespace it must be emitted in:
  - global element: the target namespace of its declaring document
  - local element: the declaring document's target namespace if `form="qualified"` or that document's
    `elementFormDefault="qualified"`, else no namespace
  
  `elementFormDefault`/`attributeFormDefault` are read today (`:2950-2951`) but never used, and only for the main
  document. *(2026-09-11: every element and attribute of a sample expansion records the namespace its declaration
  gives it, using the declaring document's `form` defaults; `XsdExtendedElement.getEmitNamespace()`.)*
- [x] **C2. Rendering.** Declare one prefix per used namespace on the root (reuse the schema's prefixes, generate
  `ns1…` on clashes). Write every element as `prefix:local`. For no-namespace local elements under a default-namespace
  root, either render the root with a prefix or emit `xmlns=""`. Cover `collectUsedNamespaces` (`:2138` today, which
  skips attributes) and the profiled generator. *(2026-09-11: both generators keep the main target namespace as the
  default and track the default in scope: an unqualified local element gets `xmlns=""`, a main-namespace element
  below it `xmlns="…"`; foreign namespaces get one schema-wide prefix each (the declaring document's, another of the
  schema's, or a generated `nsN`, never shared); the collectors include attributes, also optional ones with a fixed or
  default value.)*
- [x] **C3. Attribute refs to other namespaces.** *(in part, 2026-09-10: a prefixed `attribute`, `attributeGroup`
  or `group` ref is no longer emitted as an element (`SampleXmlForeignContentTest`); imported attribute groups expand
  to attributes (B2). Still open: emitting a foreign attribute with a declared prefix; `xlink:href` refs are dropped.)*
  - Handle `xs:attribute ref="xlink:href"` and imported `attributeGroup`s as attributes with a declared prefix, never
    as elements.
  - Qualified local attributes (`attributeFormDefault`) get a prefix too.
  - `xml:lang` uses the reserved `xml` prefix.
  - *(2026-09-11: global attributes resolve, so `ref="xlink:href"` and `ref="xml:lang"` are emitted with their prefix
    (`xml` never declared), `use="required"` on the reference counts, and qualified local attributes get a prefix;
    `SampleXmlNamespaceQualificationTest`. Found by the C re-audit: a reference's own `fixed` or `default` wins over
    the referenced declaration's (INSPIRE `xlink:type fixed="simple"`), and an import whose relative `schemaLocation`
    names a missing file resolves through the Schema Library by namespace (JATS `standard-modules/xlink.xsd`, which
    left every `xlink:href` unresolved; also affects the app).)*
- [x] **C4.** Golden tests *(`SampleXmlNamespaceQualificationTest`; chameleon includes in `SampleXmlIncludedRootsTest`)*:
  - a type imported from namespace B used by an element in A, with B qualified and with B unqualified
  - a chameleon include
  - an `xlink` attribute group
  - `xml:lang`

**Target:** AEAT Modelo 170 3/3, SIRI FR-IDF 2/2 (plain), the INSPIRE `gn:` cluster and the XBRL `link:*Ref`
samples (xlink attributes currently emitted as child elements) valid.

## WP G: Mandatory content completeness

**Evidence:** 33 samples are touched, 22 of them only by this package.

- [x] **G1. Choice option selection** (`buildXmlElementContent` `:2473-2527`, `processChildElementsForGeneration`
  `:2654-2725`).
  - A random option is chosen and then skipped when it is optional (mandatory-only mode) or would be empty. A
    required choice then ends up empty (datajud `comunicacaoprocessual`).
  - Fix: choose only among options that produce content. In mandatory-only mode prefer the option with the smallest
    required content, and fall back to emitting an optional option's minimal content instead of nothing.
  - *(2026-09-11, report §23: the sample expansion marks entries whose required content it could not expand, a cut
    recursion, a strict wildcard or an abstract element without a member. Both generators pick only options with
    complete content and leave out optional content that cannot be completed; `SampleXmlIncompleteContentTest`. The
    smallest-content preference in mandatory-only mode was not needed.)*
- [ ] **G2. Required recursion.** `traverseNode` aborts a branch as soon as a node repeats on the path (`:1548`). A
  choice with `minOccurs="2"` whose options recurse (XTCE `ORedConditions`/`ANDedConditions`) then has too few
  children.
  - Let the generator (not the documentation walk) expand recursive particles on demand, with a depth budget. Near
    the budget, pick the non-recursive option.
  - *(in part, 2026-09-11, commit `7b80b666`, found by the E re-audit: a compositor reached again through another
    element declaration no longer counts as recursion while expanding for a sample, so a concrete type that contains
    itself keeps its required content (UCI `StoreLoadoutItemType`). Recursion through the same element declaration or
    group is still cut; `SampleXmlAbstractContentTest`.)*
- [ ] **G3. Required wildcards.** `xs:any` is only recorded (`processWildcards` `:3425`).
  - For `minOccurs ≥ 1`, emit one element that the namespace constraint allows. For `##other`, use a foreign
    namespace such as `urn:fxt:sample`; for a list, use the first listed namespace. With `processContents="strict"`,
    pick a global element of an allowed namespace.
  - Evidence: xmldsig `SignatureProperty` (8 samples), XBRL `segment`/`scenario`.
  - *(2026-09-11, for `processContents="lax"` and `"skip"`, all the corpus uses: while expanding for a sample, a
    wildcard is recorded at its position in the content model, and both generators write an element there, in
    `urn:fxt:sample:extension` for `##any`/`##other`, without a namespace for `##local`, else in the first listed
    namespace, declared on the element itself. `strict` wildcards, which need a declaration, are still left out;
    `SampleXmlWildcardTest`.)*
- [ ] **G4. Occurrence bounds.** Honour `minOccurs`/`maxOccurs` on `sequence`/`all` (repeat the group), and emit at
  least `minOccurs` repetitions in mandatory-only mode. Today a repeating element always gets `maxOccurrences` copies,
  even in mandatory-only mode (`:2529-2543`). *(in part, 2026-09-11, commit `7b80b666`, found by the E re-audit:
  elements and choices repeat at least `minOccurs` times, also beyond `maxOccurrences` (UCI `Covariance` 6–120, 252
  invalid samples); the bounds of an element reference are read from the reference and no longer inherited by the
  referenced element's content. Commit `7f3d07ec`: repetitions beyond `minOccurs` are emitted only the first time
  an XPath is emitted in a document (UBL 2.1 exceeded the output limit). Repeating `sequence`/`all` groups is still
  open; `SampleXmlOccurrenceBoundsTest`.)*
- [x] **G7. Particles with the same name in one compositor.** *(2026-09-11, found by the F1/H re-audit: the element
  map keys a particle as parent XPath plus name, so JATS `ruby-model` (`rb, (rt | (rp, rt, rp))`) and FundsXML4 UK,
  which declares `UCITSExistingPerformanceFees` and `UKAssumedPortfolioReturn` twice, lost the first of two particles
  of one name, in samples and in the documentation. A further particle now gets `name[n]` as its key;
  `SampleXmlRepeatedParticlesTest`. The FundsXML golden map gained exactly these two entries.)*
- [ ] **G5.** Golden tests:
  - a required choice with only optional options
  - `choice minOccurs=2` with recursive options
  - required `xs:any ##other`
  - a sequence with `minOccurs=2`
- [x] **G6. Model group references.** *(done 2026-09-10, found by the A3 re-audit: JATS `article` came out as
  `<article/>`.)* A `<xs:group ref>` that is a type's whole content model was never resolved, one inside a
  compositor was ignored, and one inside an extension was traversed at the parent's XPath. `processGroupReference`
  expands the group's compositor in place in all three positions (cycle guard, optional refs pruned in mandatory-only
  mode); `SampleXmlGroupReferenceTest`. Corpus: JATS 182 such content models, INSPIRE 8, SIRI 16.

## WP F: Simple value generation

**Evidence:** 30 samples are touched, 11 of them only by this package.

- [ ] **F1. Types without a value.** QName, NOTATION, ENTITY/ENTITIES and anySimpleType/anyAtomicType fall into the
  `default` branch and yield `""` (`XsdSampleDataGenerator.java:279-288`).
  - QName: `prefix:local`, with a prefix declared on the root (e.g. the target-namespace prefix).
  - NOTATION: a declared notation or omit.
  - anySimpleType: `"text"`.
  - *(in part, 2026-09-11: `QName`, `anySimpleType` and `anyAtomicType` get `sample`, an unprefixed NCName that is
    a valid QName in any namespace context (XBRL `measure`); `hexBinary` and `base64Binary` honour `length`,
    `minLength` and `maxLength`, counted in octets (UCI `SHA_2_256_HashType`, 32 octets). NOTATION and ENTITY values
    need declarations and stay open; `SampleXmlSimpleValuesTest`.)*
- [ ] **F2. Unions and lists.** Generate a value from the first member type that can produce one, including inline
  `simpleType` members; for lists, emit 1..n items. Today union members resolve to one (named) member or nothing.
  Evidence: XBRL `nonZeroDecimal`, `dateUnion`, XTCE `EpochType`.
  - *(in part, 2026-09-11: a union whose member types are declared inline, without `memberTypes`, resolves to its
    first inline member (XBRL `nonZeroDecimal`); `SampleXmlSimpleValuesTest`.)*
- [x] **F3. Unsigned ranges.** *(done 2026-09-10: `generateUnsignedInteger` prints via `BigInteger` and clamps to
  the type's value space; `XsdSampleDataGeneratorTest`)* `unsignedByte`, `unsignedShort`, `unsignedInt` and `unsignedLong` are printed via the
  *signed* Java type (`printByte(value.byteValue())` etc., `:226-241`). Values above the signed maximum wrap to
  negative numbers (Garmin `Cadence = -77`, 233 occurrences). Use the right range per built-in type and print via
  `BigInteger`.
- [ ] **F4. Facets on attributes and simple content.** *(in part, 2026-09-10: a simpleContent chain through
  several named complex types now resolves to its simple base, `simpleContentBaseType`.)* Attribute types with a pattern (XTCE `NameType`
  `[^.\[\]:/ \t]+`) and simple-content elements (SIRI FR-IDF `StopPointRef` NMTOKEN, INSPIRE `value` double) come
  out empty. Use the same type resolution for attributes and simple-content bases as for elements.
  - *(2026-09-11, found by the R1 re-audit: a restriction's facets were appended to its base type's, so SIRI
    `DaysOfWeekEnumerationx`, which lists 12 of `DayTypeEnumeration`'s 22 values, allowed all 22 and `DayType` came
    out as `schoolDays`. Each facet of a derivation step now replaces the inherited facet of the same name, in the
    element map and therefore in the documentation too; `SampleXmlRestrictedFacetsTest`. Patterns of different
    steps must all match (intersection, see F5); the derived pattern is kept.)*
- [ ] **F5. Pattern generation.** *(partly done with A5: `BoundedPatternSampler` terminates within the length range,
  caps repetitions and emits only XML 1.0 characters. Pattern intersection and XSD escapes are still open.
  2026-09-11: a pattern on a typed built-in base (date/time, numeric, boolean) narrows the type's lexical space; the
  generator now produces values of the type (date/time also with `Z` or `+00:00`) and keeps the first one the pattern
  accepts, falling back to pattern sampling. UCI `DateTimeType` (`xs:dateTime`, `.+Z`) had made all 722 UCI samples
  invalid. Test: `XsdSampleDataGeneratorTest.patternOnTypedBaseYieldsAValidTypedValue`. 2026-09-11, commit
  `7b80b666`: the sampler skips transitions that can no longer reach the minimum length; UCI
  `NITF_DeclassificationExemptionType` (length 4) had fallen back to concatenated alternatives. Test:
  `BoundedPatternSamplerTest.alternativesThatCannotReachTheMinimumLengthAreAvoided`. Found by the C re-audit: the
  sampler parses patterns without the dk.brics automaton operators, so `@`, `&`, `~`, `#` and `<` are literals (KML
  `.+@.+` had produced `@`), and `XsdSampleDataGenerator` no longer sends patterns with `@` and `+` straight to its
  fallback, a Generex-era heuristic (`XsdSampleDataGeneratorTest.emailPatternsYieldMatchingValues`).)*
  - Restrict Generex output to printable characters. The XTCE samples contain unassigned or control code points;
    one realistic XTCE sample is not even well-formed.
  - Intersect patterns across the derivation chain (all steps must match).
  - Translate XSD-only escapes (`\i`, `\c`, `\p{Is…}`) before Generex.
- [x] **F6.** `min/maxExclusive` for decimals: use the smallest step that fits `fractionDigits`, not ±1.
  *(2026-09-11, report §25, found by the G1 re-audit: an exclusive range narrower than two steps inward by a quarter
  of its width, and rounded `float`, `double` and `decimal` values stay within their facets, compared in their own
  value space (UCI `AngleType` got `3.1416` above π); `XsdSampleDataGeneratorTest`.)*

## WP E: Abstract elements and types

**Evidence:** 26 samples are touched, 12 of them only by this package: XBRL `item`/`tuple`, INSPIRE
`bu-base:Building`, `AddressComponent`, rim `Action`, Garmin `Creator` (`AbstractSource_t`).

- [x] **E1. Substitution groups.** Build a substitution-group index over all documents. Where a particle
  references an abstract element (or any head element), emit a concrete, non-abstract member, preferring the target
  namespace. *(2026-09-11, commit `684dc174`: while expanding for a sample, a reference to an abstract element expands
  a concrete member, searched through abstract members nearest first, preferring the head's namespace and skipping
  members on the current path; a member from another namespace gets that namespace's prefix. A non-abstract head is
  still emitted as itself. Test: `SampleXmlAbstractContentTest`.)*
- [x] **E2. Abstract types.** For an element whose type is abstract, choose a concrete derived type (extension or
  restriction, any namespace), emit `xsi:type="prefix:Type"` and generate the derived content. *(2026-09-11, same
  commit: nearest concrete derived global complex type, preferring the abstract type's namespace; both generators emit
  `xsi:type`, unprefixed in the sample's default namespace, and declare a foreign prefix on the root. `block` is not
  consulted.)*
  *(2026-09-11, report §26: an abstract type gets a concrete derived type whose own content does not declare an element
  of the abstract type again; Garmin's first derived type `Repeat_t` requires a recursive `Child`, so workouts were
  left out as incomplete. `SampleXmlRecursiveDerivedTypeTest`.)*
- [x] **E3. Abstract roots.** Do not offer abstract global elements as roots (or list them last and generate a
  substitution-group member instead). KML has 124 abstract globals, XBRL 2, INSPIRE 1. *(2026-09-11, same commit:
  `getRootElementNames()` leaves them out and the default root is the first non-abstract global element. The
  documentation model (`processXsd`) still expands them. Found by the F1 re-audit: an abstract element nothing
  substitutes, such as KML `ObjectSimpleExtensionGroup`, is left out of a sample where it is optional or one option of
  a choice; `SampleXmlAbstractContentTest`.)*

## WP H: Identity constraints and IDs

**Evidence:** 13 samples are touched, all together with other packages (XTCE, Garmin TCX, FundsXML4).

- [x] **H1.** In the plain path, a value is computed once per schema node (`displaySampleData`) and repeated for
  every occurrence. Keys, uniques and IDs therefore collide. Examples: `Folder@Name` "ExampleText", `Activity/Id`
  dateTime, FundsXML4 `UniqueID` `id_generated_1`. Generate per occurrence for ID-typed values and constrained
  fields. *(2026-09-11, commit `7f3d07ec`, found by the E re-audit once element references repeated: both generators generate values of
  `xs:ID` type per occurrence and point `xs:IDREF(S)` at an ID of the same document; an IDREF emitted before any ID
  reserves the next one, and references left dangling at the end point at the first emitted ID. Constrained fields
  already went through `IdentityConstraintTracker`. Test: `SampleXmlIdentityValuesTest`. With mandatory elements only,
  an optional ID attribute is emitted too when the same element emits a required IDREF (JATS `answer`
  `pointer-to-question`); `SampleXmlIdReferencesTest`.)*
- [ ] **H2.** `IdentityConstraintTracker`: support attribute fields reached through `selector` paths with `.//`,
  union selectors (`a|b`), and keys on dateTime/QName values. Ensure a `keyref` has a matching key (XTCE
  `containerRef`). *(in part, 2026-09-11, commit `7f3d07ec`: selector and field paths with prefixes, `.//`, `*` and `|` resolve against
  the element map (SIRI `.//siri:KeyValue`, `siri:Values/siri:*`, Garmin `tc2:Folder/@Name`), so their fields get
  unique values; `IdentityConstraintTrackerTest`. Keys on dateTime/QName values and keyref completeness are open.)*
  *(2026-09-11, report §24, found by the G1 re-audit: a repeated key value of a date-time, time, date or decimal is
  advanced in its own lexical space instead of getting a suffix (Garmin `Id`); a repeated string-pattern value is
  sampled again (XTCE `NameType`); an optional element a key selects but that cannot hold one of its fields is left
  out (XTCE `messageNameKey` on `MessageSet/*`). `SampleXmlIdentityKeysTest`, `IdentityConstraintTrackerTest`. A QName
  key with a suffix stays a valid QName.)*
  *(2026-09-11, report §26, found by the H2 and F6 re-audits: a keyref reached before its key reserves its unsuffixed
  base value, which every such keyref reuses and the key takes first (Garmin `CourseNameRef` in `Folders`); the
  fallback had run without the element's facets, so a suffix exceeded `maxLength`.
  `IdentityConstraintTrackerTest.aKeyrefBeforeItsKeyReservesTheKeysFirstValue`.)*

## WP R: Realistic path parity and reproducibility

- [ ] **R1.** The realistic generator produced the same structural failures. It differs only in values: its own,
  weaker `setupTypeResolver` (`ProfiledXmlGeneratorService.java:855`) scans the element map per lookup. Route it
  through `XsdDocumentationService.resolveTypeToBase` and share the structural rendering (D, C, G) instead of keeping
  two copies of the walk. *(in part, 2026-09-11: the realistic generator
  resolved named types by scanning the element map for an element of the same type name, which never reached the
  built-in type of a simple-content chain; SIRI `ParticipantRefStructure` → `ParticipantCodeType` → `NMTOKEN` and
  `NaturalLanguageStringStructure` → `PopulatedStringType` came out empty. The schema processing service now hands its
  namespace-aware resolver to the documentation data (`NamedTypeResolver`), and the realistic generator uses it;
  `SampleXmlRealisticValuesTest`. The structural walk still exists twice.)*
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
