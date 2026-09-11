# Sample XML Generator: Real-World Corpus Audit (2026-09-10)

This audit tests the XSD → sample XML generator ("Generate Sample XML…", plain and "realistic values") against 35
real-world schemas, including optional elements, and validates every generated file against its schema.

The improvement plan derived from these numbers is
`docs/superpowers/plans/2026-09-10-sample-xml-generator-validity.md`.

> **Update (same day):** the plan's four quick wins (A1, A2, D1, F3) are implemented. §1–§10 describe the baseline;
> §11 has the re-audit. A5 (bounded memory) and A4 (roots from included documents) follow in §12 and §13; §14
> covers A3 (include resolution) and the defects it exposed; §15 covers F5 (typed values for patterns); §16 covers E
> (abstract elements and types) and the defects it exposed; §17 covers C (namespace qualification); §18 covers the
> realistic generator's type resolution (R1); §19 covers inherited facets (F4); §20 covers required wildcards (G3);
> §21 covers QName, binary and union values (F1, F2) and IDs for references (H); §22 covers particles with the same
> name in one content model; §23 covers choices that pick options with complete content (G1).

## 1. Summary

**Corpus:** `src/test/resources/xsd/real-world`, 35 schemas that users loaded into xsd-viewer.online by URL, each with
all includes/imports and an OASIS catalog.
- **31 evaluable schemas:** the reference schema compiles offline with Xerces and has at least one global element.
- **Not evaluable:**
  - A-GRA 6.0a, SIRI IDF 2.0 and ISO 15118-20 V2G do not compile because the publisher's server returns 404 for
    referenced files (as in the manifest).
  - The dealerdesk schema declares no global element at all.

**First global element, exactly as the app generates it** (`SampleXmlRunner`, maxOccurrences = 2), for the 31
evaluable schemas:

| Generator / mode | Valid | Invalid | No XML at all |
|---|---|---|---|
| plain, mandatory only | **19** | 5 | 7 |
| plain, with optional elements | **14** | 10 | 7 |
| realistic, mandatory only | **20** | 4 | 7 |
| realistic, with optional elements | **15** | 9 | 7 |

"No XML at all" means that seven schemas yield only an error comment, a `StackOverflowError` or an
`OutOfMemoryError`:

| Schema | Outcome |
|---|---|
| UBL 2.1 Invoice | stack overflow |
| KSeF FA(3) | stack overflow |
| goAML 5.0.2 | UTF-8 BOM |
| nalog.gov.ru | windows-1251 encoding |
| JATS 1.4 | "No root element found" |
| OASIS UCI 2.5 | OOM at 6 GB and at 14 GB |
| OGC KML 2.2 | OOM at 6 GB (see §7) |

**Every global element the app offers** (breadth test): 139 roots in 26 schemas; 130 of them could be validated
(the other 9 belong to schemas that do not compile).

| Generator / mode | Valid of 130 |
|---|---|
| plain, mandatory only | 74 (57 %) |
| plain, with optional elements | 63 (48 %) |
| realistic, mandatory only | 74 (57 %) |
| realistic, with optional elements | 62 (48 %) plus 1 not well-formed |

**Main causes** (verified in code, details in §5): the 274 invalid samples break down by work package as follows.
"Touched" counts samples with at least one error of that package; "alone" counts samples that become valid once only
that package is fixed.

| Work package | Invalid samples touched | Become valid with this package alone |
|---|---|---|
| D content type and root emission | 109 | 58 |
| B attributes inherited from base types are dropped | 103 | 50 |
| C elements/attributes emitted in the wrong namespace | 60 | 55 |
| G required content missing (choice, recursion, `xs:any`) | 33 | 22 |
| F empty/wrong simple values (QName, unions, unsigned overflow) | 30 | 11 |
| E abstract elements/types emitted literally | 26 | 12 |
| H duplicate key/unique/ID values | 13 | 0 |

In addition, schema loading has four defects that produce no XML at all (§6) and one that silently drops content:
- **Encoding:** schema files are read as a UTF-8 string, so other encodings and a BOM break parsing.
- **Namespace-blind type maps:** a type deriving from a same-named type in another namespace recurses endlessly.
- **Include/import resolution:** local locations are resolved against the main schema's directory instead of the
  including file's, which silently drops SIRI, JATS, INSPIRE and UBL modules.
- **Memory:** eager expansion of every global element exhausts memory.

Both generators fail in the same places. The "realistic" mode changes values, not structure.

## 2. Setup

| Item | Value |
|---|---|
| Code | `main` at `9f21b1af` plus the uncommitted root-selection API (§8) |
| JVM | Gradle toolchain Java 25 (`--enable-preview`); one worker JVM per schema, `-Xmx6g`, `-Xss16m` |
| Validator | Bundled Xerces 2.12.2 (exist-db XSD 1.1 build); `XMLSchemaFactory` / `XMLSchema11Factory` chosen from the manifest's `xsd_version`; full schema checking and honour-all-schemaLocations |
| Resolution | Isolated `SchemaLibraryServiceImpl` per schema (temp dir) with the folder's `catalog.xml`. The folder's `local/*.xsd` copies of W3C schemas (xml.xsd, xlink.xsd, xmldsig) are registered by namespace, which is the offline stand-in for the app's bundled library that would download them. |
| Offline guarantee | A fail-closed `LSResourceResolver`: remote references not in the catalog are recorded and answered with an empty document; DTDs referenced from schemas are answered with an empty document. The schema cache was empty after every worker (`cacheEntriesAfterRun = 0`), so nothing was downloaded. |
| Options | `maxOccurrences = 2` (dialog default); modes mandatory-only and with optional elements |

## 3. Method

For every schema a worker JVM (`SampleXmlAuditWorker`) runs these steps:

1. **Reference schema.** Compiles the schema once and collects all global element declarations from the grammar
   pool (every namespace, with the abstract flag).
2. **First element.** Runs `SampleXmlRunner.generate(xsd, mandatoryOnly, 2, realistic)` for all four combinations.
   This is what the app produces.
3. **Breadth.** Processes the schema once per generator (`processXsd(false)` / `processXsd(TRUE)`). It then
   generates every root from `getRootElementNames()` with `generateSampleXml(root, …)` and
   `ProfiledXmlGeneratorService.generateRealistic(…, root)`.
4. **Classification.** Classifies each sample as one of:
   - `VALID`
   - `INVALID`
   - `NOT_WELL_FORMED`
   - `NOT_VALIDATED` (schema does not compile)
   - `GENERATOR_ERROR` (error text instead of XML)
   - `GENERATOR_EXCEPTION`
   - `TIMEOUT` (120 s per sample, 15 min per processing step)
   
   Every Xerces message keeps its key (e.g. `cvc-complex-type.2.4.a`), element path, line and a snippet.

**Runtime:** about 6 minutes for the corpus (2 workers in parallel), plus a retry of the three OOM schemas with
14 GB.

**Output:** `build/sample-xml-audit/` contains `results.json`, `summary.csv`, and per schema `schema.json`,
`samples.jsonl`, `worker.log` and every generated sample.

**Limitations:**
- **Randomness:** choice selection and values are random and not seedable. The datajud first element was invalid in
  the full run and valid in the smoke run. Numbers can move by a few samples between runs.
- **Skewed breadth set:** the breadth set is what the app offers, so it is dominated by rim (43 roots), xmldsig (24),
  INSPIRE (13) and XBRL (10).
- **Abstract roots:** they are counted as invalid but flagged (`expectedInvalid`): XBRL 2, INSPIRE 1.
- **Stored issues:** at most 50 issues are stored per sample. The per-key counts are complete.
- **Comparability:** the online viewer baseline comes from a different generator and covers *all* global elements in
  all namespaces. It is only directly comparable for small single-namespace schemas.

## 4. Results per schema

Legend:
- Status: ✔ valid, ✘ invalid, ⚠ no XML, NV not validated (reference schema does not compile).
- "Roots app/TNS/all" = global elements the app offers / declared in the target namespace in any file / declared in
  any namespace.
- Breadth columns are *valid / roots*. Times are one `processXsd(false)` run.

| Schema | XSD | Ref. schema | Roots app / TNS / all | First element plain req · opt | First element realistic req · opt | Breadth plain req · opt | Breadth realistic req · opt | processXsd | Online viewer req · opt |
|---|---|---|---|---|---|---|---|---|---|
| dealerdesk lead types v1 | 1.1 | OK | 0 / 0 / 0 | ⚠ no root · ⚠ | ⚠ · ⚠ | – | – | 46 ms | 0/0 · 0/0 |
| bigconecta MConsultarCartoes | 1.0 | OK | 2 / 2 / 2 | ✔ · ✔ | ✔ · ✔ | 2/2 · 2/2 | 2/2 · 2/2 | 102 ms | 2/2 · 2/2 |
| bigconecta MEfetuarTransacao | 1.0 | OK | 12 / 12 / 12 | ✔ · ✔ | ✔ · ✔ | 12/12 · 12/12 | 12/12 · 12/12 | 135 ms | 12/12 · 12/12 |
| bigconecta MListarEmpresas | 1.0 | OK | 2 / 2 / 2 | ✔ · ✔ | ✔ · ✔ | 2/2 · 2/2 | 2/2 · 2/2 | 72 ms | 2/2 · 2/2 |
| bigconecta MObterGruposProd | 1.0 | OK | 2 / 2 / 2 | ✔ · ✔ | ✔ · ✔ | 2/2 · 2/2 | 2/2 · 2/2 | 58 ms | 2/2 · 2/2 |
| bigconecta MObterTransacoesPendentes | 1.0 | OK | 2 / 2 / 2 | ✔ · ✔ | ✔ · ✔ | 2/2 · 2/2 | 2/2 · 2/2 | 50 ms | 2/2 · 2/2 |
| CNJ datajud 1.2 | 1.0 | OK | 1 / 1 / 1 | ✘ · ✔ | ✔ · ✔ | 0/1 · 1/1 | 1/1 · 1/1 | 501 ms | 1/1 · 1/1 |
| OASIS UBL 2.1 Invoice | 1.0 | OK | 0 / 1 / 1621 | ⚠ StackOverflowError (all four) | ⚠ · ⚠ | – | – | fails after 1.7 s | 1621/1621 · 1621/1621 |
| FMU goAML 5.0.2 | 1.1 | OK | 0 / 1 / 1 | ⚠ "Content is not allowed in prolog" (BOM) | ⚠ · ⚠ | – | – | fails | 1/1 · 1/1 |
| A-GRA MessageDefinitions 6.0a | 1.0 | fails (404 include) | – | ⚠ OOM (6 GB and 14 GB) | – | – | – | – | does not compile |
| Google sitemap-video 1.1 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 142 ms | 1/1 · 1/1 |
| INSPIRE Addresses 4.0 | 1.0 | OK | 13 / 13 / 714 | ✘ · ✘ | ✘ · ✘ | 5/13 · 3/13 | 5/13 · 3/13 | 416 ms | 682/714 · 677/714 |
| JATS Archiving 1.4 | 1.0 | OK | 0 / 308 / 493 | ⚠ "No root element found" | ⚠ · ⚠ | – | – | 515 ms | 489/493 · 481/493 |
| Maven Assembly 2.2.0 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 121 ms | 1/1 · 1/1 |
| nalog.gov.ru ON_ZAKZVPER | 1.0 | OK | 0 / 1 / 1 | ⚠ MalformedInputException (windows-1251) | ⚠ · ⚠ | – | – | fails | 1/1 · 1/1 |
| OMG XTCE 2018 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✘ | ✔ · ✘ (not well-formed) | 1/1 · 0/1 | 1/1 · 0/1 | 3.1 s | 1/1 · 0/1 |
| OMG XTCE 2025 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✘ | ✔ · ✘ | 1/1 · 0/1 | 1/1 · 0/1 | 8.0 s | 1/1 · 0/1 |
| AEAT Modelo 170 | 1.0 | OK | 3 / 3 / 3 | ✘ · ✘ | ✘ · ✘ | 0/3 · 0/3 | 0/3 · 0/3 | 166 ms | 3/3 · 3/3 |
| FundsXML 4 | 1.1 | OK | 1 / 1 / 25 | ✔ · ✘ | ✔ · ✘ | 1/1 · 0/1 | 1/1 · 0/1 | 8.5 s | 25/25 · 25/25 |
| NuGet nuspec | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 140 ms | 1/1 · 1/1 |
| OASIS ebRIM 3.0 (rim.xsd) | 1.0 | OK | 43 / 43 / 43 | ✔ · ✘ | ✔ · ✘ | 13/43 · 8/43 | 13/43 · 8/43 | 229 ms | 43/43 · 43/43 |
| KSeF FA(3) | 1.0 | OK | 0 / 1 / 1 | ⚠ StackOverflowError | ⚠ · ⚠ | – | – | fails after 1.9 s | 1/1 · 1/1 |
| simple.xsd | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 67 ms | 1/1 · 1/1 |
| SIRI 2.2 | 1.0 | OK | 3 / 385 / 436 | ✔ · ✔ | ✔ · ✔ | 1/3 · 1/3 | 1/3 · 1/3 | 571 ms | 432/436 · 432/436 |
| SIRI FR-IDF extensions 2.4 | 1.0 | OK | 2 / 2 / 336 | ✘ · ✘ | ✘ · ✘ | 1/2 · 1/2 | 1/2 · 0/2 | 540 ms | 336/336 · 336/336 |
| SIRI IDF 2.0 | 1.0 | fails (404 include) | 3 / – / – | NV · NV | NV · NV | NV | NV | 541 ms | does not compile |
| OASIS UCI 2.5.0 | 1.0 | OK | 0 / 722 / 722 | ⚠ OOM (6 GB and 14 GB) | – | – | – | – | 722/722 · 722/722 |
| W3C xmldsig-core | 1.0 | OK | 24 / 24 / 24 | ✔ · ✔ | ✔ · ✔ | 22/24 · 22/24 | 22/24 · 22/24 | 89 ms | 24/24 · 24/24 |
| OGC KML 2.2 | 1.0 | OK | 0 / 269 / 292 | ⚠ OOM at 6 GB (with 14 GB: ✔ · ✔) | – (14 GB: ✔ · ✔) | – (14 GB: 78/269 · 62/269, 124 abstract) | – (14 GB: 77/269 · 60/269) | 78 s at 14 GB | 179/292 · 179/292 |
| ISO 15118-20 V2G AC DER | 1.0 | fails (404 import) | 6 / – / – | NV · NV | NV · NV | NV | NV | 152 ms | does not compile |
| Subsonic REST 1.16.1 | 1.1 | OK | 1 / 1 / 1 | ✔ · ✘ | ✔ · ✔ | 1/1 · 0/1 | 1/1 · 1/1 | 344 ms | 1/1 · 1/1 |
| GPX 1.1 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 94 ms | 1/1 · 1/1 |
| w3schools note | 1.0 | OK | 1 / 1 / 1 | ✔ · ✔ | ✔ · ✔ | 1/1 · 1/1 | 1/1 · 1/1 | 74 ms | 1/1 · 1/1 |
| Garmin TCX v2 | 1.0 | OK | 1 / 1 / 1 | ✔ · ✘ | ✔ · ✘ | 1/1 · 0/1 | 1/1 · 0/1 | 223 ms | 1/1 · 0/1 |
| XBRL instance 2003 | 1.0 | OK | 10 / 10 / 44 | ✘ · ✘ | ✘ · ✘ | 1/10 · 1/10 | 0/10 · 0/10 | 210 ms | 40/44 · 40/44 |

**Root coverage.** The app only offers global elements declared in the *main* document
(`XsdDocumentationService.java:2961`).
- **JATS:** `article` sits in an included module, so the app offers nothing.
- **SIRI 2.2:** 3 of 385 same-namespace globals.
- **UBL:** the Invoice root is visible, but processing fails before it.
- Elements of other namespaces are never offered. That is defensible, but the breadth test therefore covers far fewer
  roots than the online viewer.

## 5. Validation error clusters

Numbers are INVALID samples over all eight rootSet × generator × mode combinations. Keys are the Xerces message keys.

| Key | Samples | Occurrences | Schemas | Sole key in sample |
|---|---|---|---|---|
| `cvc-complex-type.4` (required attribute missing) | 107 | 865 | 6 | 50 |
| `cvc-complex-type.2.1` (content in an empty-content element) | 96 | 180 | 3 | 42 |
| `cvc-complex-type.2.4.a` (unexpected element) | 56 | 220 | 3 | 52 |
| `cvc-type.3.1.3` (invalid element value; companion key) | 34 | 271 | 5 | 0 |
| `cvc-complex-type.2.4.b` (content incomplete) | 33 | 62 | 7 | 22 |
| `cvc-datatype-valid.1.2.1` (invalid lexical value) | 27 | 1196 | 4 | 20 |
| `cvc-elt.2` (abstract element) | 17 | 38 | 2 | 8 |
| `cvc-type.2` (abstract type) | 17 | 101 | 4 | 0 |
| `cvc-identity-constraint.4.2.2` (duplicate key) | 10 | 46 | 3 | 0 |
| `cvc-datatype-valid.1.2.3` (invalid union value) | 8 | 9 | 3 | 6 |
| `cvc-complex-type.2.2` (simple content with invalid value) | 7 | 1169 | 3 | 0 |
| `cvc-attribute.3` / `cvc-pattern-valid` (attribute value vs. pattern) | 7 / 7 | 77 / 77 | 2 | 0 |
| `cvc-identity-constraint.4.2.1.a` (key without value) | 7 | 21 | 2 | 0 |
| `cvc-identity-constraint.4.1` / `.4.3` (duplicate unique, keyref) | 4 / 4 | 16 / 16 | 1 | 0 |
| `cvc-minInclusive-valid` | 4 | 233 | 1 | 0 |
| `cvc-id.2` (duplicate ID) | 2 | 2 | 1 | 0 |

### 5.1 Attributes inherited from base types are dropped (WP B, 103 samples)

- **Symptom:** `cvc-complex-type.4: Attribute 'id' must appear on element 'Classification'`. Other examples: rim
  (`id` from `IdentifiableType`, two extension levels up), XTCE (`parameterRef` on `ParameterInstanceRef`,
  `Comparison`, …), Subsonic (`jukeboxPlaylist`: `currentIndex`, `playing` and `gain` from base `JukeboxStatus`),
  INSPIRE (`uom` on `bu-base:value`).
- **Cause:** the extension branch of `processComplexContent` (`XsdDocumentationService.java:1995-2004`) processes only
  the base type's *content model* (`findContentModel`). Attributes and attribute groups declared on the base type
  never enter the element map.

### 5.2 Content type and root emission (WP D, 109 samples)

- **Root element:** `generateSampleXmlFor` never writes the root's own value and always writes `>\n … </root>`
  (`:2188`, `:2205`).
  - A global element of simple type is emitted with only whitespace:
    `cvc-datatype-valid.1.2.1: '' is not a valid value for 'QName'` (XBRL `measure`), `… 'decimal'` (`numerator`).
  - An empty-content root gets whitespace: rim `Action`, `cvc-complex-type.2.1`.
- **Attribute-only types:** `XsdSampleDataGenerator.java:147-155` treats any element whose children are all attributes
  as `simpleContent` and generates text for it. rim `LocalizedString` (124 occurrences), `Address` and `PersonName`
  have empty content.

### 5.3 Wrong namespace (WP C, 60 samples)

- **Symptom:** `cvc-complex-type.2.4.a: Invalid content was found starting with element '{m170}Modelo'. One of
  '{comun}Modelo' is expected.`
- **Affected:** AEAT Modelo 170 (every sample), INSPIRE (`gn:spelling`, 128 occurrences), SIRI FR-IDF
  (`MessageText` emitted in the SIRI namespace although declared unqualified).
- **Cause:** only prefixed element `ref`s get a prefix (`processExternalNamespaceReference`, `:1615-1687`).
  - Local elements take the root's default namespace regardless of the declaring document's target namespace and
    `elementFormDefault`.
  - `elementFormDefault`/`attributeFormDefault` are read (`:2950-2951`) but never used.
- **XBRL:** the attribute refs `xlink:type`/`xlink:href` are routed as *element* references (`traverseNode`,
  `:1557-1565`) and emitted as child elements `<xlink:type/>`. Validation reports this as `cvc-complex-type.2.1` plus
  a missing namespaced attribute.

### 5.4 Required content missing (WP G, 33 samples)

- **Choice:** a random option is selected and then skipped when it is optional or would be empty (`:2522`, `:2714`).
  The required choice stays empty: datajud `comunicacaoprocessual`, `cvc-complex-type.2.4.b`.
- **Recursion:** `traverseNode` aborts a branch as soon as a node repeats on the path (`:1548`). XTCE
  `ORedConditions` requires a `choice minOccurs="2"` of recursive options and receives too few children.
- **Wildcards:** `xs:any` is recorded but never generated. xmldsig `SignatureProperty` (8 samples) and XBRL `segment`
  require one `##other` element.
- **SIRI 2.2 `ServiceDelivery`:** it misses `ResponseTimestamp`, inherited through several extension levels from
  modules that were not loaded (§6, include and import resolution).

### 5.5 Simple values (WP F, 30 samples)

- **Unsigned overflow:** Garmin TCX `Cadence = -77` (233 occurrences of `cvc-minInclusive-valid`).
  `unsignedByte/Short/Int/Long` are generated within range and then printed via the *signed* Java type
  (`XsdSampleDataGenerator.java:226-241`, e.g. `printByte(value.byteValue())`).
- **Empty values:**
  - QName, NOTATION and anySimpleType fall into `default -> ""` (`:279-288`).
  - Unions come out empty: XBRL `nonZeroDecimal` and `dateUnion`, XTCE `EpochType`.
  - Attributes with a pattern come out empty: XTCE `name=""` for `NameType`.
  - Simple-content values come out empty: SIRI FR-IDF `StopPointRef` (NMTOKEN), INSPIRE `value` (double).
- **Characters outside XML 1.0:** Generex produced raw control characters for negated character classes (XTCE
  `NameReferenceType`). One realistic XTCE sample is **not well-formed** (U+001C in `<MetaCommandRef>`).

### 5.6 Abstract elements and types (WP E, 26 samples)

- **Abstract elements:** `cvc-elt.2` for XBRL `item`/`tuple` roots and INSPIRE `bu-base:Building`/`BuildingPart`.
- **Abstract types:** `cvc-type.2` for rim `Action`, INSPIRE `AddressComponent` and Garmin `Creator`
  (`AbstractSource_t`).
- **Cause:** there is no substitution-group or `xsi:type` support. Abstract global elements are offered as roots.

### 5.7 Identity constraints and IDs (WP H, 13 samples)

- **Symptoms:**
  - duplicate `unique` values `ExampleText` (Garmin `Folder@Name`)
  - duplicate `key` values (Garmin `Activity/Id` dateTime, XTCE `containerNameKey`)
  - `id_generated_1` repeated (FundsXML4 `UniqueID`)
  - keys without a value (XTCE `messageNameKey`)
- **Cause:** in the plain path a value is computed once per schema node and reused for every repetition.
  `IdentityConstraintTracker` covers only part of the selector/field forms.

## 6. Generator failures (no XML)

| Schema | Failure | Cause (verified) |
|---|---|---|
| nalog.gov.ru (windows-1251) | `MalformedInputException: Input length = 1` | Schema files are read with `Files.readString(…, UTF_8)` (`XsdDocumentationService.java:557, 1447, 1524, 2986`) |
| goAML 5.0.2 (UTF-8 with BOM) | `Content is not allowed in prolog` | The same read-as-string path: the BOM ends up in the `StringReader` passed to `parseXsdContent` |
| KSeF FA(3) | `StackOverflowError` in `getInheritedFacets` (`:3542`) | `TData` restricts `etd:TData`; the simple-type map is keyed by local name, so the type resolves to itself |
| UBL 2.1 Invoice | `StackOverflowError` in `processComplexContent` (`:1996`) / `findTypeDefinition` (`:3593`) | `udt:IdentifierType` extends `ccts-cct:IdentifierType`; the complex-type map is keyed by local name as well |
| JATS 1.4 | `<!-- No root element found in XSD -->` | Roots are taken from the main document only (`:2961`); all 308 globals are in included modules |
| OASIS UCI 2.5.0 (722 globals, one file) | `OutOfMemoryError` after 20 s at 6 GB, also at 14 GB | `processXsd` eagerly expands every global element's subtree into the XPath map |
| A-GRA 6.0a (860 globals) | `OutOfMemoryError` after 11 s at 6 GB, also at 14 GB | As UCI |
| OGC KML 2.2 | `OutOfMemoryError` after 270 s at 6 GB | As UCI; see §7 for 14 GB |
| dealerdesk (types only) | `<!-- No root element found in XSD -->` | Correct; the schema declares no global element |

### Include and import resolution

Local `schemaLocation`s are resolved against the main schema's directory, not the including file's
(`processAllSchemas`, `:1460`, `:1498`; `baseUri` for remote imports at `:1485`). The generator's own warnings, per
processing run (worker total ÷ 6 runs):

| Schema | "Included file not found" | "Imported file not found" | Unresolved references |
|---|---|---|---|
| SIRI 2.2 | ≈58 | ≈11 | ≈29 |
| SIRI IDF 2.0 | ≈50 | ≈10 | ≈29 |
| SIRI FR-IDF 2.4 | ≈49 | ≈10 | – |
| JATS 1.4 | ≈37 | ≈3 | – |
| INSPIRE Addresses | ≈8 | – | – |
| UBL 2.1 | ≈1 | ≈5 | – |

Example: `siri.xsd` includes `siri/siri_journey.xsd`, which includes `../siri_utility/siri_types.xsd`. The generator
looks for `xsd/../siri_utility/siri_types.xsd` next to `siri.xsd` instead. Every type from such a module is missing
from the generated XML.

## 7. Performance and memory

- **`processXsd`:** below 0.6 s for most schemas.
  - 3.1 s: XTCE 2018
  - 8.0 s: XTCE 2025
  - 8.5 s: FundsXML4
- **Largest sample:** FundsXML4 with optional elements, 1.85 MB.
- **OOM at 6 GB:** UCI, A-GRA and KML, already in the first processing step.
- **Retry with a 14 GB worker heap:**
  - UCI and A-GRA: out of memory again, so the failure is unbounded growth, not a heap sized a bit too small.
  - KML: works with 14 GB, but each `processXsd` takes 78 s and the worker needs 19 minutes in total. With 14 GB
    the first element (`kml`) is valid in all four combinations. 124 of the 269 app-offered roots are abstract and
    therefore invalid by definition. Of the 145 concrete roots, valid are:
    
    | Generator | Mandatory only | With optional elements |
    |---|---|---|
    | plain | 78 | 62 |
    | realistic | 77 | 60 |
    
    The remaining errors are mostly empty values of simple-typed global elements (`double`, `boolean`, `integer`;
    WP D), abstract substitution-group heads referenced inside content (`AbstractStyleSelectorGroup`,
    `AbstractViewGroup`, …; WP E), and the empty union `dateTimeType` and `colorType` values (WP F).
  - The KML numbers from this retry are *not* included in the totals of §1 and §5, which describe the default 6 GB
    run.

## 8. Changes made for the audit (uncommitted)

- **API (behaviour-preserving):**
  - `XsdDocumentationService.getRootElementNames()`
  - `XsdDocumentationService.generateSampleXml(String rootElementName, boolean, int)`
  - `ProfiledXmlGeneratorService.generateRealistic(…, String rootElementName)`
  
  The existing signatures delegate to the first root. Covered by `SampleXmlRootSelectionTest` (6 tests).
- **Harness** `src/test/java/org/fxt/freexmltoolkit/service/sampleaudit/`:
  - `SampleXmlGeneratorRealWorldAuditTest` (opt-in parent, one worker JVM per schema)
  - `SampleXmlAuditWorker`
  - `AuditSchemaCompiler`
  - `AuditResourceResolver`
  - `AuditModels`
  - `AuditHarnessSelfTest` (9 tests, part of the normal suite)
- **Gradle:** task `sampleXmlAudit`; `*RealWorldAuditTest*` is excluded from `test`.
- **Regression check after the API change:** all green.

  | Test class | Tests |
  |---|---|
  | `SampleXmlRootSelectionTest` | 6 |
  | `ProfiledXmlGeneratorServiceTest` | 43 |
  | `SampleXmlRunnerTest` | 3 |
  | `GeneratedXmlValidationTest` | 7 |
  | `ExternalNamespaceReferenceTest` | 2 |
  | `ConstraintAwareSampleXmlTest` | 5 |
  | `AuditHarnessSelfTest` | 9 |
  | `XsdSampleDataGeneratorTest` | 38 |
  | `ProcessXsdEquivalenceTest` | 1 |

## 9. Reproduction

```bash
./gradlew sampleXmlAudit                                   # whole corpus, 2 workers
./gradlew sampleXmlAudit -Dsample.audit.only=topografix-com-gpx,w3schools-com-note
./gradlew sampleXmlAudit -Dsample.audit.only=schemas-opengis-net-ogckml22 \
    -Dsample.audit.parallel=1 -Dsample.audit.workerHeap=14g
```

Further options:
- `sample.audit.parallel`
- `sample.audit.workerHeap`
- `sample.audit.schemaTimeoutMinutes` (45)
- `sample.audit.processTimeoutMinutes` (15)
- `sample.audit.sampleTimeoutSeconds` (120)
- `sample.audit.maxRootsPerSchema`

The task skips itself when the corpus folder is absent.

## 10. History

| Date | Change | First element valid (plain req · opt, of 31) | Breadth valid (plain req · opt, of 130) | No XML |
|---|---|---|---|---|
| 2026-09-10 | Baseline | 19 · 14 | 74 · 63 (of 130) | 7 |
| 2026-09-10 | Quick wins A1, A2 (types), D1, F3 | 23 · 18 | 82 · 70 (of 132) | 4 |
| 2026-09-10 | A5 bounded memory (§12) | 24 · 17 | 229 · 200 (of 1,124: UCI, KML and more SIRI roots now generate) | 1 |
| 2026-09-10 | A4 roots from included documents (§13) | 23 · 19 | 534 · 325 (of 1,677: JATS and SIRI now offer their included roots) | 0 |
| 2026-09-11 | A3 includes, B inherited attributes, G6 group refs, D4, A6, A2 element refs (§14) | 26 · 20 | 913 · 571 (of 1,814: SIRI offers 385 roots, UCI completes) | 0 |
| 2026-09-11 | F5 typed values for patterns on typed bases (§15) | 28 · 19 | 1,621 · 1,130 (of 1,814: UCI 709 · 566 of 722) | 0 |
| 2026-09-11 | E abstract elements and types, the defects it exposed, H1/H2 identity values (§16) | 28 · 21 | 1,637 · 1,548 (of 1,672: abstract roots no longer offered) | 0 |
| 2026-09-11 | C namespace qualification and the defects it exposed (§17) | 30 · 25 | 1,657 · 1,629 (of 1,672) | 0 |
| 2026-09-11 | R1 type resolution in the realistic generator (§18) | 30 · 24 | 1,658 · 1,637 (of 1,672; realistic 1,657 · 1,634) | 0 |
| 2026-09-11 | F4 a restriction's facets replace its base's (§19) | 30 · 25 | 1,657 · 1,651 (of 1,672; realistic 1,657 · 1,651) | 0 |
| 2026-09-11 | G3 required element wildcards (§20) | 30 · 25 | 1,660 · 1,654 (of 1,672; realistic 1,662 · 1,654) | 0 |
| 2026-09-11 | F1/F2 QName, binary and union values; H IDs for references; unsubstituted abstract elements (§21) | 30 · 24 | 1,671 · 1,664 (of 1,672; realistic 1,670 · 1,659) | 0 |
| 2026-09-11 | G7 particles with the same name in one content model (§22) | 30 · 25 | 1,672 · 1,663 (of 1,672; realistic 1,672 · 1,661) | 0 |
| 2026-09-11 | G1 choices pick options with complete content (§23) | 30 · 27 | 1,672 · 1,668 (of 1,672; realistic 1,672 · 1,669) | 0 |

## 11. After the quick wins (re-audit, 2026-09-10)

**Changes** (uncommitted):
- **A1:** schema files are parsed from bytes (`parseXsdFile`).
- **A2:** complex and simple types are looked up through the namespace bound to their prefix, with a local-name
  fallback. `getInheritedFacets` has a cycle guard, and the three extension expansions skip circular chains.
- **D1:** a root without child elements carries its own value or is self-closing, in both generators.
- **F3:** unsigned types are printed via `BigInteger`.

**New tests** (all green):

| Test class | Tests |
|---|---|
| `XsdSchemaEncodingTest` | 2 |
| `CrossNamespaceTypeResolutionTest` | 2 |
| `SampleXmlRootEmissionTest` | 12 |
| `XsdSampleDataGeneratorTest` (8 new parameterized cases) | 46 in total |

**Regression:** all test classes that use `XsdDocumentationService`, `XsdSampleDataGenerator`,
`ProfiledXmlGeneratorService` or the sample runners, plus `AuditHarnessSelfTest`: 279 tests, 0 failures.

**First global element** (31 evaluable schemas):

| Generator / mode | Valid before → after | Invalid before → after | No XML before → after |
|---|---|---|---|
| plain, mandatory only | 19 → **23** | 5 → 4 | 7 → 4 |
| plain, with optional elements | 14 → **18** | 10 → 9 | 7 → 4 |
| realistic, mandatory only | 20 → **22** | 4 → 5 | 7 → 4 |
| realistic, with optional elements | 15 → 15 | 9 → 11 | 7 → 5 |

**Every app-offered root:** 132 validated roots instead of 130; nalog and KSeF FA(3) add one each.

| Generator / mode | Valid before | Valid after |
|---|---|---|
| plain, mandatory only | 74 | **82** |
| plain, with optional elements | 63 | **70** |
| realistic, mandatory only | 74 | **81** |
| realistic, with optional elements | 62 | **69** |

**Effects per schema:**

| Schema | Before | After | Cause |
|---|---|---|---|
| nalog.gov.ru (windows-1251) | no XML | all 8 samples valid | A1 |
| goAML 5.0.2 (BOM) | no XML | first element valid in 3 of 4 combinations; then `OutOfMemoryError` at 6 GB (52 s) | A1 removes the parse error; memory is WP A5 |
| XBRL instance | first element invalid in all 4; breadth 1/10 · 1/10 | first element (`numerator`) valid in all 4; breadth 2/10 · 2/10 (plain) | D1 |
| OASIS ebRIM 3.0 | breadth 13/43 · 8/43 | 19/43 · 14/43 | D1 (empty-content roots such as `Action` are self-closing) |
| KSeF FA(3) | `StackOverflowError`, no XML | XML in all 8 combinations, all invalid | A2 removes the crash; see the FA(3) bullet below |
| UBL 2.1 Invoice | `StackOverflowError`, no XML | `OutOfMemoryError` at 6 GB after 221 s, still no XML | A2 removes the crash; memory is WP A5 |
| Garmin TCX | negative `Cadence` values | `Cadence` correct; still invalid with optional elements | F3; see the Garmin bullet below |

- **KSeF FA(3):** the remaining errors are values:
  - empty enumeration value for `KodKraju` (11 per sample)
  - empty `NIP` (pattern)
  - empty `TDataT` dates, `dateTime` and `integer`
  - `Telefon` with 60 characters against `maxLength` 16
  
  The sample type resolver (`resolveTypeToBase` with its memo) is still keyed by local name, which this schema
  breaks; the plan notes this under A2.
- **Garmin TCX:** it stays invalid with optional elements because of duplicate `unique`/`key` values (WP H) and the
  abstract `Creator` type (WP E).
- **Unchanged:** JATS (no root, WP A4); UCI, A-GRA and KML (`OutOfMemoryError` at 6 GB, WP A5).
- **Random flips, not caused by the changes:** datajud, nuspec (control character from Generex, F5), Subsonic
  (inherited attributes, B) and SIRI FR-IDF (namespace, C) switched between valid and invalid in single combinations.
  The failures match the clusters in §5, and the four changes do not touch those code paths.
- **Harness observation:** the FA(3) worker needed 35 minutes, although each generation took under 2 s. The time was
  spent before the first sample was recorded, i.e. while validating it, with the heap near 6 GB. The harness has no
  time limit for validating a single sample. The cause is not analysed yet.
- **Why "no XML" stays at 4:** the loading fixes turned crashes into OOMs, so the memory problem (WP A5) is now the
  main reason for "no XML".

## 12. After A5: bounded memory (re-audit, 2026-09-10)

**Root causes, measured** (class histograms, generator stacks):
- **Markdown renderer per entry:** every `XsdExtendedElement` built its own flexmark `Parser` and `HtmlRenderer` (with
  options, regex patterns, maps and lists), several kilobytes per XPath entry.
- **Snippets per copy:** a schema node reachable under many XPaths was serialized again for every copy (goAML: 942
  nodes, 161,172 entries, 45 million characters of source snippets).
- **Eager expansion:** sample generation used the documentation's expansion of *every* global element (UBL 2.1 was
  still growing at 2.55 million entries when it ran out of memory).
- **Generex:** `Generex.random` recurses without a depth bound. For A-GRA's `.+Z` (`Timestamp`, `Epoch`) it
  occasionally recursed tens of thousands of levels deep. On a default thread stack that ended in a caught
  `StackOverflowError` (47 of 20,000 calls); on the audit worker's 256 MB stack it filled a 6 GB heap within seconds.

**Changes:**
- **Shared Markdown:** one Markdown parser and renderer for all elements.
- **Shared snippets and documentation:** source snippets and documentation entries are built once per schema node
  and shared by all copies. Every XPath's snippet and rendered documentation stays byte-identical: the golden hashes
  of `ProcessXsdSnippetEquivalenceTest` were captured before the change.
- **Root-scoped expansion:** `expandForSample` expands only the chosen root; in mandatory-only mode it skips optional
  particles (choice options stay).
- **Limits:** expansion stops at `fxt.sampleXml.maxExpandedNodes` (1,000,000), output at `fxt.sampleXml.maxOutputChars`
  (50,000,000); both generators then return an XML comment naming the limit.
- **`getRootElementNames()`:** loads the schema without expanding anything. `processXsd` for the documentation still
  expands every global element.
- **`BoundedPatternSampler`** replaces `Generex.random`:
  - a random walk over the same automaton that only takes transitions from which an accepting state stays reachable
    within the remaining length
  - repetition bounds capped at 256
  - only characters XML 1.0 allows, printable ASCII first
- **Harness:** unpaired surrogates are written safely, and validation runs under a time limit (`VALIDATION_TIMEOUT`,
  `sample.audit.validationTimeoutSeconds`, 120 s).

**New tests:** `SampleXmlExpansionLimitTest` (4), `BoundedPatternSamplerTest` (8), `ProcessXsdSnippetEquivalenceTest`
(2), and in `XsdSampleDataGeneratorTest` the cases "large thread stack" and "large repetition bounds".
`ConstraintAwareSampleXmlTest` raises the output limit locally, because its FundsXML4 sample with
`maxOccurrences=3` has 184 million characters. Regression over all generator and documentation test classes: 427
tests, no failures.

**Heap of the full documentation expansion** (`processXsd`, 3 GB test JVM, after GC):

| Schema | Entries | Before | After |
|---|---|---|---|
| goAML 5.0.2 | 161,172 | 730 MB | 249 MB |
| FundsXML 4 | 55,778 | 471 MB | 189 MB |
| OGC KML 2.2 | 2,227,144 | out of memory | 2.1 GB, completes |
| UBL 2.1 Invoice | more than 2.5 million | out of memory | still out of memory; sample generation no longer depends on it |

**First global element** (31 evaluable schemas):

| Generator / mode | Valid: baseline → quick wins → A5 | No XML: baseline → quick wins → A5 |
|---|---|---|
| plain, mandatory only | 19 → 23 → **24** | 7 → 4 → **1** |
| plain, with optional elements | 14 → 18 → 17 | 7 → 4 → **2** |
| realistic, mandatory only | 20 → 22 → **24** | 7 → 4 → **1** |
| realistic, with optional elements | 15 → 15 → **19** | 7 → 5 → **2** |

The remaining "no XML" cases are JATS (no root in the main document, WP A4) and UBL 2.1 with optional elements (the
node-limit comment). KSeF FA(3) generates XML, but its validation did not finish within the time limit, so it counts
as neither valid, invalid nor "no XML" (see the note below the effects table).

**Every offered root:** 1,124 validated roots instead of 132, because UCI (722 roots), KML (269) and SIRI 2.2 / SIRI
IDF (6 roots each instead of 3) now generate. The A-GRA roots (860) generate as well but cannot be validated, because
the publisher's schema is incomplete.

| Generator / mode | Valid of 1,124 |
|---|---|
| plain, mandatory only | 229 |
| plain, with optional elements | 200 |
| realistic, mandatory only | 223 |
| realistic, with optional elements | 195 |

These numbers are not comparable with the earlier 82 / 70, because the set of roots changed.

**Effects per schema:**

| Schema | Quick wins | A5 |
|---|---|---|
| UBL 2.1 Invoice | out of memory, no XML | mandatory only: XML (still invalid); with optional elements: "expands to more than 1,000,000 nodes for root 'Invoice'" |
| OASIS UCI 2.5.0 | out of memory | all 722 roots generate, none valid yet (structural clusters). One sample exceeds 50 million characters, two realistic samples time out. |
| A-GRA 6.0a | out of memory | all 860 roots generate (not validatable); one realistic timeout |
| OGC KML 2.2 | out of memory at 6 GB | first element valid in all four combinations; roots 145/269 · 129/269 (plain) and 141/269 · 123/269 (realistic), 124 of them abstract |
| goAML 5.0.2 | first element in three combinations, then out of memory | all samples generate; valid except plain with optional elements |
| SIRI 2.2, SIRI IDF 2.0 | 3 roots offered | 6 roots: the full expansion used to lose `ServiceRequest`, `SubscriptionRequest` and `CapabilitiesRequest` |
| KSeF FA(3) | XML in all eight combinations, invalid | XML generated; validation did not finish within 120 s (`VALIDATION_TIMEOUT`); see below |

- **KSeF FA(3):** the re-run with the validation time limit generated XML for all four first-element combinations,
  but none of them finished validating within 120 s (`VALIDATION_TIMEOUT`). After four stuck validation threads the
  harness skipped the breadth samples. Validating FA(3) samples was already slow in the quick-win run (35 minutes for
  the worker). The likely cause is Xerces pattern matching backtracking on a generated value; that is not analysed
  yet. It is counted as "XML generated, not validated", not as "no XML".
- **Character regression, fixed:** the first A5 run, before the character fix of the sampler, produced 935 of 1,444
  realistic UCI samples that were not well-formed, because of control characters from `.+Z`.
- **Random flips:** as before (datajud, Subsonic, nuspec, XTCE), within the known clusters.
- **Still open for "no XML":** A4 (roots from included documents, JATS). The documentation of UBL 2.1 still needs
  more memory than 3 GB.

## 13. After A4: roots from included documents (re-audit, 2026-09-10)

**Change** (commit `be93cdc9`):
- **More roots:** `getRootElementNames()` now also offers the global elements of included documents of the main
  target namespace, including chameleon includes. Imports of other namespaces are not offered. Each name appears once,
  in document order.
- **Default root:** `getDefaultRootElementName()`, used by both generators, keeps the first element of the main
  document. Only when the main document declares none does it pick the first included element that is neither
  abstract nor referenced via `ref`.
- **Documentation:** `processXsd` is unchanged.
- **Test:** `SampleXmlIncludedRootsTest` (3).

**First global element** (31 evaluable schemas):

| Generator / mode | Valid: A5 → A4 | No XML: A5 → A4 |
|---|---|---|
| plain, mandatory only | 24 → 23 | 1 → **0** |
| plain, with optional elements | 17 → 19 | 2 → **1** |
| realistic, mandatory only | 24 → 22 | 1 → **0** |
| realistic, with optional elements | 19 → 19 | 2 → **1** |

- **JATS:** now generates `article` instead of answering "No root element found". The sample is still invalid:
  the required `front` is missing (`cvc-complex-type.2.4.b`). JATS is among the schemas whose nested includes the
  generator cannot find (WP A3).
- **Only remaining "no XML":** UBL 2.1 with optional elements (node limit).
- **Valid counts:** they moved by random flips (datajud, goAML, XTCE 2018/2025).
- **Sampler defect found and fixed:** the XTCE 2025 flip exposed it. For `NameType` `[^.\[\]:/ \t]+` the sampler
  could pick a tab or line feed, which an attribute value normalizes to a space. It no longer emits tabs or line
  breaks (`BoundedPatternSamplerTest.samplesContainNoLineBreaksOrTabs`); the fix came after this audit run.

**Every offered root** (not comparable with §12, the set of roots grew again):

| Schema | Roots offered: A5 → A4 | Valid plain req · opt | Valid realistic req · opt |
|---|---|---|---|
| JATS 1.4 | 0 → 308 | 206 · 55 | 206 · 55 |
| SIRI 2.2 | 6 → 268 | 101 · 71 | 86 · 53 |
| SIRI IDF 2.0 | 6 → 217 | not validated (publisher schema incomplete) | – |

| Generator / mode | Valid | Validated |
|---|---|---|
| plain, mandatory only | 534 | 1,677 |
| plain, with optional elements | 325 | 1,675 |
| realistic, mandatory only | 513 | 1,610 |
| realistic, with optional elements | 302 | 1,607 |

- **JATS errors:** 710 invalid samples in total. The most frequent keys (samples containing them):
  - `cvc-id.1` (468): IDREF without a matching ID, WP H
  - `cvc-complex-type.2.4.b` (222)
  - `cvc-complex-type.4` (176)
  - `cvc-elt.2` (176)
  - `cvc-complex-type.2.4.a` (142)
- **Harness:** the UCI worker exceeded its 45-minute budget this time, and the realistic breadth stopped at 655 of
  722 roots. The larger JATS and SIRI breadth sets running in parallel made the run longer.

## 14. After A3: includes relative to their document, and what it exposed (re-audit, 2026-09-11)

**A3** (commit `47df9550`): every `schemaLocation` resolves against the document that declares it, not the main
schema's directory. A remote `xs:include` goes through the Schema Library like a remote import, and relative references
of a remote document resolve against its URL. Test: `XsdNestedIncludeResolutionTest` (2). `xs:redefine` and
`xs:override` are still not followed; no corpus schema uses them.

Unresolved include and import locations per schema (unique, from the generator log):

| Schema | A4 | Now | Remaining |
|---|---|---|---|
| SIRI 2.2 | 45 | 0 | – |
| SIRI IDF 2.0 | 48 | 2 | `siri_common-v1.3.xsd`, `xml.xsd` (missing at the publisher) |
| SIRI FR-IDF 2.4 | 47 | 1 | `xml.xsd` |
| JATS 1.4 | 30 | 2 | `standard-modules/xml.xsd`, `xlink.xsd` |
| INSPIRE Addresses | 8 | 0 | – |
| UBL 2.1 | 4 | 0 | – |

**What the newly reached content exposed.** Three intermediate audit runs found defects that A3 made visible. Each
was fixed test-first before the final run:

| Run | Finding | Fix |
|---|---|---|
| `a3` (stopped) | SIRI 2.2 up from 101 to 305 valid roots, but INSPIRE down from 5 to 1 of 13 and JATS `article` still `<article/>` | see the four rows below (commit `aa335644`) |
| | a prefixed `attributeGroup` ref was emitted as an element (`<gml:SRSReferenceGroup/>`) | only element refs take the external-reference path (C3 in part) |
| | an element whose children emit nothing (GML `ReferenceType`) got indentation whitespace: `cvc-complex-type.2.1` on `gn:nameStatus`/`gn:nativeness` (88 samples each) | self-closing tag in both generators (D4) |
| | the plain generator emitted an optional `sequence` in mandatory-only mode without declaring its `gml` prefix | skip optional containers there, as the realistic generator does (D4) |
| | a `<xs:group ref>` as a type's content model was never expanded (JATS: 182 such content models) | `processGroupReference` for content models, compositors and extensions (G6) |
| | attributes of a base type next to its particle, attribute groups and a restriction's inherited attributes were dropped (rim `IdentifiableType@id`) | WP B; a multi-level simpleContent base now resolves to its simple type (F4 in part) |
| `a3b` (stopped) | JATS `article` (both modes) and SIRI `Siri` with optional elements exceeded the 1,000,000-node limit | A6, commit `16eaca90` |
| `a3c` | JATS with optional elements down from 55 to 36: MathML attributes on JATS `sec` and `list` | A2 element references, commit `741ba3df` |

- **A6, bounded sample expansion:** compositors reached through `processComplexContent` bypassed the mandatory-only
  pruning, and optional content recursing through many different declarations (JATS inline elements, SIRI
  `AffectedLine` → `AffectedStopPoint` → `Lines`) grew with the number of permutations. A sample now expands the
  optional content of each element declaration once; later occurrences get their required content only. The
  documentation expansion is unchanged.

  | Expansion | Before | After |
  |---|---|---|
  | JATS `article`, mandatory only | > 1,000,000 nodes | 237 nodes, 1.3 s |
  | JATS `article`, with optional elements | > 1,000,000 nodes | 175,165 nodes, 6.8 s |
  | SIRI `Siri`, with optional elements | > 1,000,000 nodes | 11,298 nodes, 1.3 s |

- **A2 element references:** JATS imports MathML, which declares its own global `sec`, `list`, `title`, `annotation`
  and `product`. Element, group and attribute-group references were resolved by local name, so JATS `sec` got the
  MathML content and, once attribute groups expanded, its attributes. They now resolve in the namespace of the
  referencing document.
- **Tests:** `SampleXmlForeignContentTest`, `SampleXmlGroupReferenceTest`, `SampleXmlInheritedAttributesTest`,
  `SampleXmlBoundedExpansionTest`, `CrossNamespaceReferenceResolutionTest` (4 each: both generators × both modes,
  validated with Xerces). The golden hashes of `ProcessXsdEquivalenceTest` and `ProcessXsdSnippetEquivalenceTest`
  (FundsXML 4.2.8) are unchanged.

**First global element** (final run `a3d`, 31 evaluable schemas):

| Generator / mode | Valid: A4 → now | No XML: A4 → now |
|---|---|---|
| plain, mandatory only | 23 → **26** | 0 → 0 |
| plain, with optional elements | 19 → **20** | 1 → **0** |
| realistic, mandatory only | 22 → **26** | 0 → 0 |
| realistic, with optional elements | 19 → 19 | 1 → **0** |

KSeF FA(3) generates XML in all four combinations, but its validation still exceeds the harness's 120 seconds, so it
is counted in neither column. UBL 2.1 with optional elements now generates XML instead of reporting the node limit;
the sample is invalid.

**Every offered root** (not comparable with §13 in total: SIRI offers 385 roots instead of 268, and UCI completes):

| Generator / mode | Valid | Validated |
|---|---|---|
| plain, mandatory only | 913 | 1,814 |
| plain, with optional elements | 571 | 1,814 |
| realistic, mandatory only | 815 | 1,814 |
| realistic, with optional elements | 414 | 1,814 |

| Schema | Valid plain req · opt: A4 → now | Valid realistic req · opt: A4 → now |
|---|---|---|
| rim (43 roots) | 19 · 14 → **42 · 42** | 19 · 14 → **42 · 42** |
| SIRI 2.2 | 101 · 71 of 268 → **365 · 306** of 385 | 86 · 53 → **269 · 162** |
| JATS 1.4 (308 roots) | 206 · 55 → **295** · 38 | 206 · 55 → **298** · 33 |
| INSPIRE Addresses (13 roots) | 5 · 3 → 6 · 2 | 5 · 3 → 6 · 2 |

- **JATS with optional elements** stays below A4: 196 of its 270 invalid samples fail only on `cvc-id.1`. Attribute
  groups now emit `rid` and other IDREF attributes, and nothing emits a matching ID (WP H).
- **INSPIRE with optional elements** now reaches more GML content and with it abstract elements (`cvc-elt.2`,
  `cvc-type.2`, WP E).
- **Remaining invalid samples** (plain, mandatory only), by schema:
  - UCI 722 of 722: one type. `DateTimeType` restricts `xs:dateTime` with the pattern `.+Z`, and the generator
    samples the pattern instead of generating a date-time (`CZ`, `s&W5vyZ`, occasionally an empty value;
    `cvc-datatype-valid.1.2.1`, WP F5). 661 samples fail only on `MessageHeader/Timestamp`, which every UCI message
    has; the other 61 also on further `DateTimeType` elements.
  - KML 124 of 269: abstract global elements offered as roots (E3).
  - SIRI 20, JATS 13, XBRL 8, INSPIRE 7, AEAT Modelo 170 3 (all its roots, namespace qualification, WP C).
- **Random flips:** datajud (choice selection, R2) and one XBRL realistic root.

## 15. After F5: typed values for patterns on typed bases (re-audit, 2026-09-11)

**Change** (commit `fb638fdf`): a pattern on a built-in date/time, numeric or boolean base narrows the type's lexical
space. The generator used to sample the pattern alone; it now generates values of the type (date/time also with `Z`
or `+00:00`) and keeps the first one the pattern accepts, falling back to pattern sampling. Both generators take their
values from this path. Test: `XsdSampleDataGeneratorTest.patternOnTypedBaseYieldsAValidTypedValue` (5 cases, each
value validated with Xerces). Patterns on typed bases occur in UCI (`dateTime`, `time`, `int`), KSeF FA(3)
(`decimal`, `date`), A-GRA and the two bigconecta schemas (`boolean`).

**First global element** (31 evaluable schemas):

| Generator / mode | Valid: §14 → now | No XML |
|---|---|---|
| plain, mandatory only | 26 → **28** | 0 |
| plain, with optional elements | 20 → 19 | 0 |
| realistic, mandatory only | 26 → **27** | 0 |
| realistic, with optional elements | 19 → 19 | 0 |

UCI's first element is now valid in both mandatory-only combinations. The other changes are random flips (datajud,
SIRI realistic with optional elements).

**Every offered root** (same 1,814 validated samples per combination as in §14):

| Generator / mode | Valid: §14 → now |
|---|---|
| plain, mandatory only | 913 → **1,621** |
| plain, with optional elements | 571 → **1,130** |
| realistic, mandatory only | 815 → **1,520** |
| realistic, with optional elements | 414 → **990** |

- **UCI 2.5** (722 roots): 0 → 709 · 566 valid (plain, mandatory · optional), 0 → 707 · 575 (realistic).
- **Remaining invalid samples** (plain, mandatory only: 193 of 1,814):
  - KML 125: abstract global elements offered as roots (E3)
  - SIRI 2.2 21, JATS 12, XBRL 8, INSPIRE 7
  - UCI 13: abstract types without `xsi:type` (`cvc-type.2`, e.g. `Metadata`, `Configuration`) and required
    content missing (`cvc-complex-type.2.4.i/j`)
  - AEAT Modelo 170 3 (namespace qualification, C), xmldsig 2, rim 1, SIRI FR-IDF 1
- **By key** (plain, mandatory only): `cvc-elt.2` (abstract element) in 143 samples and `cvc-type.2` (abstract type)
  in 83 make WP E the largest cluster. With optional elements, `cvc-id.1` (IDREF without ID, 275 samples, nearly all
  JATS, WP H) comes first, then E.
- **Next:** E (abstract roots, substitution groups, `xsi:type` for abstract types), then H.

## 16. After E: abstract elements and types, and what it exposed (re-audit, 2026-09-11)

**Change** (commit `684dc174`). An instance may contain neither an abstract element nor an element of an abstract type
without `xsi:type`. While expanding for a sample (the documentation model is unchanged):
- **E1 substitution groups:** a reference to an abstract element expands a concrete member of its substitution group.
  Abstract members are searched too, nearest first, preferring the head's namespace and skipping members already on
  the current path. A member from another namespace gets that namespace's prefix.
- **E2 abstract types:** an element whose type is abstract expands the nearest concrete derived global type. Both
  generators emit it as `xsi:type` and declare its prefix on the root when the type is foreign.
- **E3 abstract roots:** abstract global elements are no longer offered as roots, and the default root is the first
  non-abstract global element. The breadth denominator therefore shrinks (KML 269 → 145 roots, SIRI 2.2 385 → 370,
  XBRL 10 → 8, INSPIRE 13 → 12).

Test: `SampleXmlAbstractContentTest` (substitution through an abstract member, a recursive member, `xsi:type` on a
child and on the root, GML heads with members in a third namespace).

**First audit** (1,672 validated samples per combination; F5 had 1,814 including the abstract roots):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), F5 → E | 28 → 28 | 19 → 20 | 27 → 27 | 19 → 20 |
| Breadth valid, F5 → E | 1,621 → 1,630 | 1,130 → 1,173 | 1,520 → 1,529 | 990 → 1,004 |
| Samples with `cvc-elt.2` (abstract element) | 143 → **0** | 173 → 3 | 142 → 1 | 175 → 2 |
| Samples with `cvc-type.2` (abstract type) | 83 → **0** | 154 → 1 | 83 → 0 | 149 → 1 |

- **Fixed schemas:** KML 145 of 145 (plain, mandatory only; F5 144 of 269), rim 43 of 43 in every mode, SIRI 2.2
  365 of 370, UCI 714 of 722.
- **Left:** heads without any concrete member in the corpus: INSPIRE `bu-base:Building` and `BuildingPart` (the
  members live in modules the Addresses schema does not import), KML `UpdateOpExtensionGroup` and XBRL `item`/`tuple`
  (filled only by taxonomies).

**Exposed defects.** Per root, 177 samples turned valid and 100 turned invalid. 79 of those were UCI with optional
elements, where concrete types now bring in content that older defects break. Each was fixed test-first (commit
`7b80b666`):

| Defect | Evidence | Fix | Test |
|---|---|---|---|
| G4: repetition capped below `minOccurs` | 252 invalid UCI samples with optional elements (`Vertex` ≥ 3, `KnotVector` ≥ 4, `Covariance` 6–120 at maximum 2) | elements and choices repeat at least `minOccurs` times | `SampleXmlOccurrenceBoundsTest` |
| Bounds of an element reference inherited by all its content | found by that test: a child of a `ref minOccurs="4"` repeated four times | the referenced declaration consumes the bounds; documentation cardinalities of such children are corrected too | `SampleXmlOccurrenceBoundsTest` |
| Pattern sampler ignores the minimum length while walking | UCI `NITF_DeclassificationExemptionType` (length 4): the walk took `[DNIO]`, ended at length 1, and the fallback concatenated all alternatives (`X1 25X9I `) | transitions that can no longer reach the minimum length are skipped | `BoundedPatternSamplerTest` |
| A repeated compositor counted as recursion | UCI `StoreList` (type `StoreLoadoutItemPET`) inside `StoreLoadoutItemType` was emitted without its required `Location` | while expanding for a sample, only element declarations and groups count as recursion | `SampleXmlAbstractContentTest` |

The other turned-invalid samples are the known random flips of choice selection and realistic values (SIRI dates,
XBRL unions, datajud).

**Second audit** (after `7b80b666`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| Breadth valid (of 1,672), first E audit → second | 1,630 → 1,636 | 1,173 → **1,290** | 1,529 → 1,537 | 1,004 → **1,124** |

- **UCI 2.5:** 714 → 722 of 722 (plain, mandatory only), 593 → 718 with optional elements.
- **New regressions**, all because element references now repeat with their own `maxOccurs`:
  - duplicate IDs (`cvc-id.2`): KML with optional elements 139 → 124, xmldsig 22 → 19 (`Reference/@Id`)
  - duplicate `unique` values: SIRI `KeyValuePair_unique` and `TypeOfValue_unique`, Garmin
    `RunningSubFolderNamesMustBeUnique`
  - no XML for UBL 2.1 `Invoice` with optional elements: 470 references with `maxOccurs="unbounded"` doubled the
    document per level until it exceeded the 50 MB output limit

**Fixes** (commit `7f3d07ec`, test-first):

| Defect | Fix | Test |
|---|---|---|
| H1: one ID value per schema node, repeated for every occurrence; IDREFs to IDs that were never emitted | both generators generate `xs:ID` values per occurrence and point `xs:IDREF(S)` at an ID of the same document; an IDREF before any ID reserves the next one, and references left dangling point at the first emitted ID | `SampleXmlIdentityValuesTest` |
| H2: selector and field paths with prefixes, `.//`, `*` or `\|` never matched, so their fields got no unique values | `IdentityConstraintTracker` resolves them against the element map | `IdentityConstraintTrackerTest`, `SampleXmlIdentityValuesTest` |
| Repeated ancestors multiply optional repetitions | repetitions beyond `minOccurs` are emitted only the first time an XPath is emitted in a document; later emissions get `max(minOccurs, 1)` | `SampleXmlOccurrenceBoundsTest` |

**Third audit** (after `7f3d07ec`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), F5 → now | 28 → 28 | 19 → **21** | 27 → 27 | 19 → 19 |
| Breadth valid (of 1,672), second audit → now | 1,636 → 1,637 | 1,290 → **1,548** | 1,537 → 1,537 | 1,124 → **1,390** |
| Samples with `cvc-id.1` (IDREF without ID), second audit → now | 6 → 6 | 268 → **0** | 5 → 5 | 274 → **0** |

No schema is without XML: UBL 2.1 with optional elements generates again (still invalid).

**The whole round, F5 → now** (invalid samples among every offered root; F5 validated 1,814 samples per
combination including the abstract roots, now 1,672):

| Generator / mode | Invalid: F5 → now |
|---|---|
| plain, mandatory only | 193 → **35** |
| plain, with optional elements | 684 → **124** |
| realistic, mandatory only | 294 → **135** |
| realistic, with optional elements | 824 → **282** |

- **Per schema:** UCI 722 · 719 of 722 (plain, mandatory · optional), JATS 295 · 275 of 308, KML 145 · 141 of 145,
  rim 43 of 43 and xmldsig 22 of 24 in every mode.
- **No schema regressed** against F5 beyond the known random flips (datajud one sample, SIRI realistic mandatory-only
  271 → 269).
- **Remaining, plain, mandatory only (35):** JATS 13 (`xlink` attributes on foreign-namespace references, C3, and
  IDREFs in documents without any ID), INSPIRE 6 and AEAT Modelo 170 3 (namespace qualification, C), XBRL 6 (abstract
  `item`/`tuple` without members, union values), SIRI 4.
- **Remaining with optional elements:** SIRI 58 (plain, mostly `cvc-complex-type.2.4.a`, not analysed yet) and 207
  (realistic: values that break their facets, `cvc-datatype-valid.1.2.1`, `cvc-minLength-valid`,
  `cvc-complex-type.2.2`), JATS 33 · 26 (`xlink` attributes).
- **Next:** C (namespace qualification, including foreign attributes such as `xlink:href`), then the SIRI values of
  the realistic generator.

## 17. After C: namespace qualification (re-audit, 2026-09-11)

**Change** (commit `f25d1944`). Samples used the root's default namespace for everything that did not come through
a prefixed element reference. While expanding, every element and attribute now records the namespace its
declaration gives it:
- a global declaration takes the target namespace of its document
- a local declaration takes it only when qualified, by `form` or by its own document's `elementFormDefault` or
  `attributeFormDefault`; otherwise it has no namespace

Both generators keep the main target namespace as the default namespace and track the default in scope. An
unqualified local element gets `xmlns=""`, a main-namespace element below it `xmlns="…"` again, and every foreign
namespace one schema-wide prefix. Global attributes resolve, so `ref="xlink:href"` and `ref="xml:lang"` are emitted
with their prefix, and `use="required"` on the reference counts. Test: `SampleXmlNamespaceQualificationTest` (AEAT,
INSPIRE, SIRI FR-IDF, JATS and XBRL constructs, `xml:lang`).

**First audit:**

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §16 → C | 28 → **30** | 21 → **23** | 27 → **29** | 19 → **20** |
| Breadth valid (of 1,672), §16 → C | 1,637 → 1,649 | 1,548 → 1,563 | 1,537 → 1,546 | 1,390 → 1,392 |
| Samples with an element in the wrong namespace | 10 → **0** | 66 → **0** | 10 → **0** | 27 → **0** |

- **Fixed schemas:** AEAT Modelo 170 3 of 3 in every mode (was 0), INSPIRE Addresses 12 of 12 with mandatory elements
  only (was 6), SIRI FR-IDF 2 of 2 (plain), SIRI 2.2 with optional elements 312 → 331 (plain).
- "Wrong namespace" counts samples whose `cvc-complex-type.2.4.a/b` error names an expected element with the same
  local name in another namespace.

**Exposed defects.** Per root, 103 samples turned valid and 58 invalid. Three older defects were behind the
regressions, each fixed test-first (commit `3fe56987`):

| Defect | Evidence | Fix | Test |
|---|---|---|---|
| An import whose relative `schemaLocation` names a missing file was not looked up in the Schema Library | JATS imports `standard-modules/xlink.xsd`; its copy is mapped by namespace, so every `xlink:href`, `xml:lang` and `xml:base` stayed unresolved (millions of log warnings per run). Affects the app too | resolve such an import by namespace through the Schema Library, without download | `SampleXmlNamespaceQualificationTest` |
| `fixed`/`default` on an attribute reference ignored | INSPIRE `<attribute ref="xlink:type" fixed="simple"/>` got `locator` or `resource` once the attribute resolved | the reference's value wins, in the element map and in both generators | `SampleXmlNamespaceQualificationTest` |
| dk.brics operators in XSD patterns | KML `atomEmailAddress` `.+@.+`: `@` is the "any string" operator, the sampler failed and the fallback wrote `@` | parse without automaton operators, so `@`, `&`, `~`, `#` and `<` are literals | `BoundedPatternSamplerTest` |

**Second audit** (after `3fe56987`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| Breadth valid (of 1,672), first C audit → second | 1,649 → 1,653 | 1,563 → **1,606** | 1,546 → 1,554 | 1,392 → **1,424** |
| Samples missing a qualified attribute (`cvc-complex-type.4`) | 4 → **0** | 32 → **0** | 6 → **0** | 24 → **0** |

- **JATS** with optional elements 271 → 303 of 308 (plain), 283 → 303 (realistic); its worker log went from 11.5
  million "could not be resolved" warnings to none.
- **INSPIRE** with optional elements 0 → 11 of 12; **FundsXML 4**'s first element is now valid with optional elements
  too.
- Per root, 112 samples turned valid and 23 invalid, all known random flips of choice selection and values.
- **One exposed defect left:** KML `atom:email` (`.+@.+`) still came out as `@`. `XsdSampleDataGenerator` classed a
  pattern with `@` and `+` as "too complex" (a heuristic from the Generex days) and went straight to its fallback,
  so the sampler fix never applied.

**Third audit** (after `389dfb59`, which lets e-mail patterns reach the sampler):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §16 → now | 28 → **30** | 21 → **25** | 27 → **28** | 19 → **23** |
| Breadth valid (of 1,672), §16 → now | 1,637 → **1,657** | 1,548 → **1,629** | 1,537 → 1,553 | 1,390 → 1,423 |
| Invalid samples (of 1,672), §16 → now | 35 → **15** | 124 → **43** | 135 → 119 | 282 → 249 |

- **Samples with an element in the wrong namespace or a missing qualified attribute:** 0 in every mode (§16: 17 · 109
  · 17 · 56). KML `atomEmailAddress` values are valid again.
- **Per schema:** AEAT 3 of 3 and SIRI FR-IDF 2 of 2 (plain) in every run; INSPIRE 12 · 11 of 12; JATS 300 · 301 of
  308 (plain), 301 · 303 (realistic); KML 145 of 145 in both plain modes; SIRI 2.2 370 · 350 of 370 (plain).
- **No schema regressed** against §16 beyond the known random flips (datajud, one sample).
- **Remaining invalid** (plain, mandatory only: 15): JATS 8, XBRL 5 (abstract `item`/`tuple` without members, union and
  QName values), xmldsig 2 (required `xs:any`, G3).
- **Largest remaining cluster:** the realistic generator's SIRI values (100 samples mandatory only, 208 with optional
  elements: empty `NMTOKEN` and `PopulatedStringType` values, `cvc-complex-type.2.2` on simple content). Next: those
  values, then G (choices, required `xs:any`).

## 18. After R1: type resolution in the realistic generator (re-audit, 2026-09-11)

**Cause.** After C, the largest remaining cluster was SIRI 2.2 with the realistic generator: 100 invalid samples with
mandatory elements only and 208 with optional elements. Nearly all errors were empty values, `cvc-datatype-valid.1.2.1`
(`''` is not a valid `NMTOKEN`), `cvc-minLength-valid` (`PopulatedStringType`) and `cvc-complex-type.2.2` on simple
content. The plain generator wrote values at the same paths.

The realistic generator resolved a named type by scanning the element map for an element of the same type name and
taking that element's type. For a simple-content chain this never reaches a built-in type:
- `RequestorRef` has type `ParticipantRefStructure`, simple content extending `ParticipantCodeType`, which restricts
  `NMTOKEN`
- `Name` has type `NaturalLanguageStringStructure`, simple content extending `PopulatedStringType` (`minLength="1"`)

The value generator then fell back to an empty string.

**Change** (commit `cea64f2d`). The schema processing service hands its namespace-aware type resolution
(`resolveTypeToBase`, the one the plain generator uses) to the documentation data through a small domain interface,
`NamedTypeResolver`. The realistic generator uses it; data not produced by the service keeps the element map scan.
The structural walk still exists in both generators (rest of R1). Test: `SampleXmlRealisticValuesTest`.

**Audit:**

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §17 → R1 | 30 → 30 | 25 → 24 | 28 → **30** | 23 → **24** |
| Breadth valid (of 1,672), §17 → R1 | 1,657 → 1,658 | 1,629 → 1,637 | 1,553 → **1,657** | 1,423 → **1,634** |
| Invalid samples (of 1,672), §17 → R1 | 15 → 14 | 43 → 35 | 119 → **15** | 249 → **38** |

- **The realistic generator is now on par with the plain one** (15 · 38 invalid against 14 · 35).
- **SIRI 2.2** with the realistic generator: 271 → 370 of 370 (mandatory only), 164 → 351 (with optional elements).
  SIRI FR-IDF 2 of 2 and KML 145 · 144 of 145 in both realistic modes; FundsXML 4's first element is valid in every
  mode.
- Per root, 335 samples turned valid and 9 invalid, all random flips of choice selection and values (datajud, JATS
  `ruby`, a UCI `hexBinary` of `length="6"`, a KML abstract head without members).
- **Remaining invalid** (plain, mandatory only: 14): JATS 7 (IDREFs in documents without any ID, 6), XBRL 5 (abstract
  `item`/`tuple` without members, union and QName values), xmldsig 2 (required `xs:any`, G3).
- **With optional elements** (plain: 35): SIRI 21 (`cvc-type.3.1.3` and `cvc-enumeration-valid`, not analysed yet),
  XBRL 6, JATS 3, single samples in INSPIRE, XTCE, UBL and Garmin.
- **Next:** analyse the SIRI values with optional elements, then G (choices that produce content, required
  `xs:any`) and IDREFs without any ID.

## 19. After F4: a restriction's facets replace its base's (re-audit, 2026-09-11)

**Cause.** After R1, most remaining SIRI 2.2 samples with optional elements failed on two sibling elements of
`MonitoringValidityConditionStructure`: `DayType` came out as `schoolDays` or `everyDay`, `HolidayType` as `weekdays`
or `saturday` (`cvc-enumeration-valid`, `cvc-type.3.1.3`, in both generators). Their types are:
- `DaysOfWeekEnumerationx`, a restriction of `DayTypeEnumeration` to 12 of its 22 values
- `HolidayTypeEnumerationx`, a restriction of the same type to the other 10

`parseRestriction` and `getInheritedFacets` first copied the base type's facets and then *appended* the restriction's
own values to the same lists. Both elements therefore allowed all 22 values, and the documentation listed them too.

**Change.** Each facet declared by a derivation step replaces the inherited facet of the same name, in the element
map and therefore in the documentation. Patterns declared on different steps must all match (intersection, F5); the
most derived one is kept. Test: `SampleXmlRestrictedFacetsTest` (enumeration subsets of a shared base, a narrower
`maxLength`, both generators × both modes).

**Golden update.** `ProcessXsdEquivalenceTest` hashes the FundsXML 4 element map, facets included. A canonical dump
before and after the change differs in exactly 2 of 46,601 entries, only in their facets: FundsXML 4 declares the same
pattern on a type and on its base, which was listed twice and is now listed once. The expected hash was updated with
that note.

**Audit** (after `03c7bb5d`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §18 → F4 | 30 → 30 | 24 → **25** | 30 → 29 | 24 → **25** |
| Breadth valid (of 1,672), §18 → F4 | 1,658 → 1,657 | 1,637 → **1,651** | 1,657 → 1,657 | 1,634 → **1,651** |
| Invalid samples (of 1,672), §18 → F4 | 14 → 15 | 35 → **21** | 15 → 15 | 38 → **21** |

- **SIRI 2.2** is valid in every mode: 370 of 370 roots (plain with optional elements 355 → 370, realistic 351 → 370).
  No sample has a `DayType` or `HolidayType` error any more.
- Per root, 43 samples turned valid and 12 invalid, all random flips: choices in JATS and datajud, KML's
  `UpdateOpExtensionGroup` head without members, a UCI `hexBinary` of `length="6"` and a UCI angle rounded to 6.2832,
  above `maxInclusive` 2π.
- **Since F5** (§15), the invalid samples per combination fell from 193 · 684 · 294 · 824 (of 1,814) to 15 · 21 · 15 ·
  21 (of 1,672).
- **Remaining** (plain, with optional elements: 21): UCI 7 (`hexBinary` values that ignore `length`, decimal bounds),
  XBRL 5 (empty QName `measure`, empty union values, required `xs:any` in `segment`), JATS 3 (choices), KML 2,
  single samples in INSPIRE, XTCE, UBL and Garmin. With mandatory elements only (15): JATS 7 (IDREFs in documents
  without any ID), XBRL 5, xmldsig 3 (required `xs:any` in `SignatureProperty`).
- **Next, by samples affected:** required `xs:any` (G3: xmldsig, XBRL, UBL, 14 samples), QName values (F1: XBRL
  `measure`, 13), IDREFs in documents without any ID (H, 11), `hexBinary` with `length` (F, 7).

## 20. After G3: required element wildcards (re-audit, 2026-09-11)

**Cause.** The generators recorded `xs:any` for the documentation only and never emitted an element for it, so an
element whose content is a required wildcard came out empty (`cvc-complex-type.2.4.b`, 14 samples):
- xmldsig `SignatureProperty`: a choice of `<any namespace="##other" processContents="lax"/>`
- XBRL `segment`: `<any namespace="##other" processContents="lax" minOccurs="1" maxOccurs="unbounded"/>`
- UBL `ExtensionContent`: `<any namespace="##other" processContents="lax"/>`

**Change.** While expanding for a sample, a wildcard is recorded at its position in the content model, and both
generators write an element there, repeated like an element:
- `##any` and `##other`: `<ext:sample xmlns:ext="urn:fxt:sample:extension"/>`
- `##local`: `<sample xmlns=""/>`
- `##targetNamespace` or a list: an element in that (the first listed) namespace

The namespace is declared on the element itself. A `strict` wildcard needs a declaration and is still left out; the
corpus declares 53 `lax` and 3 `skip` wildcards and no `strict` one. The documentation model is unchanged. Test:
`SampleXmlWildcardTest` (choice, `minOccurs="1"`, `skip`, `##local` between two elements, a namespace list and an
optional `##any`).

**Audit** (after `17037fae`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §19 → G3 | 30 → 30 | 25 → 25 | 29 → **30** | 25 → 25 |
| Breadth valid (of 1,672), §19 → G3 | 1,657 → **1,660** | 1,651 → **1,654** | 1,657 → **1,662** | 1,651 → **1,654** |
| Invalid samples (of 1,672), §19 → G3 | 15 → **12** | 21 → **18** | 15 → **10** | 21 → **18** |

- **xmldsig** is valid in every mode: 24 of 24 roots (was 22). **XBRL** 4 of 8 in every mode (was 2–3).
- Wildcard errors (`WC[…]` in `cvc-complex-type.2.4.b`) fell from 28 to 2.
- Per root, 24 samples turned valid and 9 invalid, all random choice selections in JATS.
- **Remaining** (plain, mandatory only: 12): JATS 7 (IDREFs in documents without any ID), XBRL 4 (empty `QName`
  `measure`, the inline union `nonZeroDecimal`), KML 1 (a head without members). With optional elements (plain: 18):
  UCI 5 (`hexBinary` values that ignore `length`, decimal bounds), JATS 4, XBRL 4, single samples in INSPIRE, XTCE,
  UBL, Garmin and KML.
- **Next:** the remaining simple values (F: `QName`, binary `length`, inline union members), then IDREFs in documents
  without any ID (H).

## 21. After F1/F2 and H: QName, binary and union values, IDs for references (re-audit, 2026-09-11)

**Cause.** Three kinds of simple value were empty or ignored their facets:
- XBRL `measure` has the built-in type `QName`. The value generator had no branch for `QName` (nor for
  `anySimpleType`) and wrote an empty string.
- XBRL `nonZeroDecimal` is a union of two inline simple types, without `memberTypes`. Type resolution only followed
  `memberTypes`, found nothing, and the value came out empty.
- `hexBinary` and `base64Binary` always got a fixed value of 5 octets. UCI `AA_CodeType` (`length="6"`) and
  `SHA_2_256_HashType` (`length="32"`) require other lengths.

**Change.** `QName`, `anySimpleType` and `anyAtomicType` get `sample`, an unprefixed NCName that is a valid QName in
any namespace context. A union without `memberTypes` resolves to its first inline member. Binary values have as many
octets as `length` demands, or five within `minLength` and `maxLength`. NOTATION and ENTITY values need declarations
and stay open. Test: `SampleXmlSimpleValuesTest`.

**First audit** (after `c11842fb`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| Breadth valid (of 1,672), §20 → F1/F2 | 1,660 → 1,665 | 1,654 → **1,610** | 1,662 → 1,663 | 1,654 → **1,609** |

- **XBRL** 8 of 8 roots in three modes (was 4), **UCI** 722 of 722 in every mode.
- **KML regressed** with optional elements, 144 → 93 of 145 in both generators (`cvc-elt.2`). KML declares
  `ObjectSimpleExtensionGroup` abstract, of type `anySimpleType`, and nothing substitutes it. Its value used to be
  empty, so the optional element was skipped as an empty container; with a value for `anySimpleType` it was emitted.

**Fixes** (test-first, together with H):

| Defect | Fix | Test |
|---|---|---|
| An abstract element nothing substitutes was emitted where the content model could do without it | while expanding for a sample, such an element is left out when it is optional or one option of a choice | `SampleXmlAbstractContentTest` |
| H: JATS `answer` requires `pointer-to-question` (IDREFS) but its `id` is optional; with mandatory elements only, the reference pointed at nothing (11 samples) | an optional ID attribute is emitted when the same element emits a required IDREF | `SampleXmlIdReferencesTest` |

**Second audit** (after `830ec3a9`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §20 → now | 30 → 30 | 25 → 24 | 30 → 30 | 25 → 25 |
| Breadth valid (of 1,672), §20 → now | 1,660 → **1,671** | 1,654 → **1,664** | 1,662 → **1,670** | 1,654 → **1,659** |
| Invalid samples (of 1,672), §20 → now | 12 → **1** | 18 → **8** | 10 → **2** | 18 → **13** |

- **KML** 145 of 145, **XBRL** 8 of 8 and **UCI** 722 of 722 in every mode; no IDREF without an ID is left.
- Per root, 44 samples turned valid and 11 invalid, all random choice selections in JATS and datajud.
- **Since F5** (§15), the invalid samples per combination fell from 193 · 684 · 294 · 824 (of 1,814) to 1 · 8 · 2 · 13
  (of 1,672).
- **Remaining:**
  - JATS: in `ruby-model` (`rb, (rt | (rp, rt, rp))`) both `rp` references get the same element-map key, so the second
    overwrites the first; `statement` and `question` get empty required choices now and then
  - INSPIRE `bu-base:Building`: a required abstract element whose members the Addresses schema does not import
  - XTCE and Garmin: duplicate or suffixed key values (H2)
  - UBL `WitnessParty` order, datajud's choice, and KSeF FA(3), whose validation exceeds the audit's time limit
- **Next:** particles with the same name in one compositor (JATS `ruby`), then choices that produce content (G1).

## 22. After G7: particles with the same name in one content model (re-audit, 2026-09-11)

**Cause.** The element map keys a particle as its parent's XPath plus its name. Two particles with the same name under
one parent therefore shared one key, and the second replaced the first:
- JATS `ruby-model` is `rb, (rt | (rp, rt, rp))`; samples lacked the second `rp` (`cvc-complex-type.2.4.b`)
- FundsXML4 declares `UKAssumedPortfolioReturn` and `UCITSExistingPerformanceFees` twice in the UK sequence; the
  documentation showed only the second of each, under the first one's key and with its text

**Change.** A further particle with the same name gets `name[2]`, `name[3]` and so on as its key, at every place an
element child is keyed. Clean XPaths in the exports are built from element names and are unaffected. Test:
`SampleXmlRepeatedParticlesTest` (two `rp` in one sequence, an extension restating a base element).

**Golden update.** A canonical dump of the FundsXML 4 element map before and after differs by exactly two added
entries, `UKAssumedPortfolioReturn[2]` and `UCITSExistingPerformanceFees[2]`, plus the parent's children list and the
first entry's documentation, which now is its own (08140 instead of 08180). No entry was removed. The expected hashes
of `ProcessXsdEquivalenceTest` and `ProcessXsdSnippetEquivalenceTest` were updated with that note.

**Audit** (after `92524714`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §21 → now | 30 → 30 | 24 → 25 | 30 → 30 | 25 → 24 |
| Breadth valid (of 1,672), §21 → now | 1,671 → **1,672** | 1,664 → 1,663 | 1,670 → **1,672** | 1,659 → **1,661** |
| Invalid samples (of 1,672), §21 → now | 1 → **0** | 8 → 9 | 2 → **0** | 13 → **11** |

- **Mandatory elements only:** every sample of every offered root is valid, in both generators.
- **JATS** `ruby` is valid in every mode. JATS has 308 of 308 valid roots with mandatory elements only.
- The changes with optional elements are random choice selections: JATS 305 → 304 (plain) and 300 → 303
  (realistic), and datajud's invalid sample moved from plain to realistic.
- **Remaining** (all with optional elements):
  - JATS, 4 plain and 5 realistic: required choices that come out empty in `statement`, `question`, `speech` and
    `open-access` (G1)
  - datajud, 1 realistic: `comunicacaoprocessual` requires a strict `xs:any` that no declaration satisfies (G1)
  - XTCE, 2 per generator, and Garmin, 1 per generator: duplicate key values and a `dateTime` key with the suffix
    `_1` (H2)
  - INSPIRE `bu-base:Building` and UBL `WitnessParty` order, 1 per generator each; KSeF FA(3) validation still
    exceeds the audit's time limit
- **Next:** choices that pick options with complete content (G1).

## 23. After G1: choices pick options with complete content (re-audit, 2026-09-11)

**Cause.** A sample expansion cannot expand every required particle, and it left no trace where it stopped:
- Recursion through the same element declaration or group is cut. JATS `fn` holds `(p)+`, so an `fn` inside a `p`
  gets a required choice without any option; `statement` offers `p` or a nested `statement`, and inside a `p` both
  are cut.
- A strict `xs:any` needs a declaration and is not recorded. datajud `comunicacaoprocessual` requires one for
  `##other`, and no imported schema declares an element there.
- An abstract element that nothing substitutes was emitted where the content model requires it (INSPIRE
  `bu-base:Building`).

The generators then picked a choice option at random, or emitted an optional element, whose required content was
missing.

**Change.** The expansion marks an entry whose required content it could not expand. A choice counts as complete when
one option is. Both generators pick only among options with complete content, and they leave out optional content that
cannot be completed. When no option is complete, the old random pick stays. The documentation model is not marked, so
the golden hashes are unchanged. Test: `SampleXmlIncompleteContentTest` (five content models from JATS, datajud and
INSPIRE, both generators and modes, 16 runs each).

**Audit** (after `2b64b208`):

| Measure | plain req | plain opt | realistic req | realistic opt |
|---|---|---|---|---|
| First element valid (of 31), §22 → now | 30 → 30 | 25 → **27** | 30 → 30 | 24 → **27** |
| Breadth valid (of 1,672), §22 → now | 1,672 → 1,672 | 1,663 → **1,668** | 1,672 → 1,672 | 1,661 → **1,669** |
| Invalid samples (of 1,672), §22 → now | 0 → 0 | 9 → **4** | 0 → 0 | 11 → **3** |

- **JATS** 308 of 308, **INSPIRE** 12 of 12, **datajud** and **UBL** 1 of 1: valid in every mode.
- INSPIRE's `Address` no longer contains the abstract `bu-base:Building`; the optional element that required it is left
  out.
- **Remaining:**
  - XTCE, 2 per generator: duplicate `@name` values for `containerNameKey` and `parameterNameKey`, and `messageNameKey`
    selecting `MessageSet/*`, which includes `LongDescription` without a name (H2)
  - Garmin, 1 per generator: the `dateTime` key `Id` gets the suffix `_1` (H2)
  - UCI, 1 plain sample: `AngleType` with `maxInclusive="3.141592653589793E0"` got `3.1416`, a random value rounded
    past the bound (F)
- **Next:** H2, key values of their own type.
