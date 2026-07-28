# XProc 3.0 Support — Design

Date: 2026-07-28

## Goal

Execute XProc 3.0 pipelines (`.xpl`) as first-class editor documents, mirroring
the XQuery/XPath *Run Query* and XSLT *Run Transform* features: open a pipeline,
pick a target (or rely on the automatic most-recently-active XML document),
press **Run Pipeline** / `Ctrl+Enter`, read the result in the OUTPUT panel.
Plus a bundled example collection in `release/examples/xproc/`.

## Engine choice

**XML Calabash 3** (`com.xmlcalabash:xmlcalabash:3.0.51`, MIT, Maven Central) —
effectively the only option: MorganaXProc-IIIse is GPL-3.0 and not on Maven
Central; XML Calabash 1.x (XProc 1.0) is archived.

### Saxon pin: 13.0 → 12.10

Calabash 3.0.51 is compiled against Saxon-HE **12.10** and uses Saxon internals
(its POM even constrains `Saxon-HE` to `strictly 12.10`). Running it against
Saxon 13 fails at pipeline-compile time. Decision (user-approved): downgrade the
project to Saxon-HE 12.10. The only source change was reverting the
`UnparsedTextURIResolver.resolve()` 4-argument lambda in
`XsltTransformationEngine` (see commit `5f4c5f98` which introduced it). We lose
the Saxon 13 XPath/XSLT 4.0 draft features; move back to 13.x once Calabash
supports it.

### Dependency notes

- `log4j-to-slf4j` is excluded (the project routes SLF4J → Log4j2 via
  `log4j-slf4j2-impl`; both bridges together would recurse).
- `org.jline` is excluded (CLI-only interactive debugger terminal).
- `javax.activation` must **not** be excluded — Calabash's `MediaType`
  detection uses `MimetypesFileTypeMap` at runtime.
- `com.networknt:json-schema-validator` resolves to the project's 3.0.6
  (Calabash wants 1.5.9) → `p:validate-with-json-schema` is a documented
  known limitation; downgrading is not an option (the JSON editor needs 3.x).
- Saxon-EE-only steps (`p:validate-with-xml-schema`, `p:xsl-formatter`) are
  unavailable on HE — documented in the examples README and user docs.
- jlink runtime modules added for Calabash's transitive deps:
  `jdk.unsupported` (Kotlin coroutines), `jdk.zipfs` (commons-compress),
  `java.net.http`.

## Runner design

`controls/shell/editor/XProcRunner` follows the `TransformRunner` convention:
static, UI-free, never throws, errors returned as `"ERROR: …"` strings (the
OUTPUT panel routes that prefix to `showFailure`). It returns a
`Result(text, OutputFormat)` record because — unlike XSLT — the output format
is only known *after* the run, from the primary output document's media type
(`MediaType.classification()` → HTML/XHTML/JSON/TEXT/XML routing).

Key decisions:

- **Fresh `XmlCalabash` instance per run.** Upstream declares the API unstable
  and thread-safety is undocumented; instantiation cost is acceptable for an
  explicit user action. All Calabash imports are confined to `XProcRunner` and
  the API-pinning `XmlCalabashSmokeTest`.
- **Base URI:** the pipeline is parsed from the live editor text via a
  `StreamSource` whose system id is the backing file's URI, so relative
  `href`s (`p:document`, stylesheets, Schematron) resolve against the
  pipeline's directory even for unsaved edits. Untitled documents fall back
  to the working directory.
- **Input binding:** the primary input port (explicit `primary="true"` or the
  single declared input) is bound from the resolved `QueryTarget` — in-memory
  text for open-document targets, the file itself for file targets. A declared
  input without any available target is an error *unless* the port carries
  default bindings; a pipeline with no inputs runs unbound (self-contained
  pipelines are legal — no hard guard like XSLT's).
- **Output:** documents on the primary output port (fallback: first non-empty
  port), serialized with Calabash's `DocumentWriter` (honors the pipeline's
  serialization properties), multiple documents joined with newlines.

## Editor integration

Mirrors `RUN_TRANSFORM` exactly: `EditorAction.RUN_PIPELINE` gated to the new
`EditorFileType.XPROC` (`.xpl`, `.xproc`, icon `bi-diagram-2`, teal `#0ca678`),
`EditorActions.runActivePipeline()` with the shared generation counter,
`runActive()` dispatching XPROC → pipeline, `Ctrl+Enter` filter in
`EditorHost.addTab`, a *Run Pipeline* toolbar button (`bi-play-btn`), and the
Target dropdown visible for `.xpl` documents.

Decisions:

- `isXmlFamily` **includes XPROC** — a pipeline is well-formed XML and a
  legitimate query/transform target; self-targeting is already prevented by
  `resolveQueryTarget`.
- View modes: Text + Tree, **no Graphic** (the grid adds nothing for
  pipelines) — the existing `supportsView` defaults already produce this.
- `NewFileDialog` offers XProc with a `p:declare-step` skeleton
  (`defaultContent()`).
- OS file association for `.xpl` is **deliberately skipped**, consistent with
  `.xq`/`.xpath` (future work: register all three in
  `FileAssociationServiceImpl` / `UnifiedEditorFileType`).

## Examples

`release/examples/xproc/01…08-*.xpl` + README, FundsXML4-based, following the
XQuery collection's conventions (numbered kebab-case names, header comment with
Purpose/Techniques). Coverage: identity, `p:delete`, `p:xslt` with relative
hrefs (CSV + JSON), `p:add-attribute`/`p:insert` chaining with computed
options, `p:for-each` + `p:wrap-sequence`, `p:variable` + `p:choose`,
`p:validate-with-schematron` with the SVRL `report` port. Bundling is
automatic (the build copies all of `release/examples` into app images).
`ExamplesIntegrityTest` executes every pipeline against the equity sample.

## Testing

- `XmlCalabashSmokeTest` — pins the exact Calabash API call sequence (parse
  from `Source` with base URI, port binding via
  `XProcDocument.Companion.ofXml(node, exec.getConfig())`, `BufferingReceiver`,
  `DocumentWriter`), so an incompatible Calabash upgrade fails here first.
- `XProcRunnerTest` — 8 cases: both target kinds, relative href resolution,
  `p:xslt`, missing-target guidance, static error code surfacing, JSON format
  routing, self-contained pipelines.
- `EditorActionsTest` / `QueryTargetResolutionTest` — gate, toolbar wiring,
  end-to-end run, XPROC as XML-family target.
- `ExamplesIntegrityTest` — all bundled pipelines execute; CSV pipeline
  produces the known header.
- Full-suite regression gate after the Saxon downgrade.
