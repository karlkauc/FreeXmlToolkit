# Schematron Quick Fixes (SQF)

> **Version:** 2.1.0

FreeXmlToolkit understands **Schematron Quick Fix (SQF)** definitions — the
`sqf:*` vocabulary (namespace `http://www.schematron-quickfix.com/validator/process`)
that lets Schematron authors attach automatic corrections to their rules. When a
document is validated against a Schematron whose asserts/reports reference fixes
via `sqf:fix="…"`, the editor offers those fixes right where the problems are
reported — and applies them with a single click while **preserving the
document's formatting**.

> **Try it:** the bundled examples include two ready-made demo pairs in
> `examples/schematron/` — `quickfix-demo.xml` + `quickfix-demo.sch` (a fully
> commented Schematron covering every fix type) and `funds-quickfix-demo.xml` +
> `funds-quickfix-rules.sch` (realistic FundsXML business rules, including a fix
> whose value is computed from elsewhere in the document). Open an XML file,
> bind the matching `.sch` file in the Validation panel, and validate: every
> finding offers a quick fix.

## Using quick fixes

Bind a Schematron to your XML document as usual (Validation panel → SCHEMATRON
source row, or the Explorer's one-click validation bar). After validation,
problems that carry fixes can be corrected from three places:

- **Problems lists** — right-click a problem row in the Validation panel or the
  PROBLEMS strip below the editor and pick an entry from the **Quick Fix**
  submenu.
- **Editor lightbulb** — lines with fixable problems show a yellow lightbulb in
  the editor gutter. Click it (or press **Alt+Enter** / **Ctrl+.** on the line)
  to open the fix chooser at the caret.
- **Schematron report** — the report table has a **Fix** column with a lightbulb
  button on fixable findings.

Applying a fix

- changes only the affected text region — indentation and formatting of the
  rest of the document stay byte-for-byte untouched,
- is a single native undo step (**Ctrl+Z** restores the document),
- triggers an immediate re-validation, so resolved problems disappear from all
  lists.

If the document was edited after the last validation, the fix refuses to run
and asks for a re-validation instead of guessing.

## Authoring fixes

The Schematron editor's IntelliSense completes the SQF vocabulary
(`sqf:fixes`, `sqf:fix`, the four activities, `sqf:user-entry`, `sqf:call-fix`,
…) including the `sqf:fix` / `sqf:default-fix` attributes on `sch:assert` and
`sch:report`. A minimal example:

```xml
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
            xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
  <sch:pattern>
    <sch:rule context="person">
      <sch:assert test="@id" sqf:fix="addId">Person must have an id.</sch:assert>
      <sqf:fix id="addId">
        <sqf:description>
          <sqf:title>Add generated id attribute</sqf:title>
        </sqf:description>
        <sqf:add node-type="attribute" target="id">p-1</sqf:add>
      </sqf:fix>
    </sch:rule>
  </sch:pattern>
</sch:schema>
```

## Supported SQF features

- The four activities **`sqf:add`**, **`sqf:delete`**, **`sqf:replace`**,
  **`sqf:stringReplace`** — including `@match`, `@node-type`
  (`element`/`attribute`/`processing-instruction`/`comment`/`keep`), `@target`
  (as attribute value template), `@position`
  (`first-child`/`last-child`/`before`/`after`), `@select`, `@regex`/`@flags`
  and `@use-when` conditions.
- **Dynamic content**: XSLT instructions inside fix content (`xsl:value-of`,
  `xsl:choose`, attribute value templates, …) are executed natively (Saxon),
  with the rule's `sch:let` variables and `sch:ns` namespaces in scope.
  `sqf:copy-of` and `sqf:keep` are supported in content.
- **`sqf:user-entry`** — the fix prompts for the value(s) in a dialog; the
  `@default` XPath is evaluated against the failing node and pre-filled.
- **`sqf:call-fix` / generic fixes** — fixes can call other fixes with
  `sqf:with-param`-bound `sqf:param` parameters (cycles are detected).
  A fix declaring a parameter without a default is only reachable via
  `sqf:call-fix`, never offered directly.
- Fixes may be defined **rule-locally** or globally under a schema-level
  `sqf:fixes` container (rule-local ids shadow global ones); `sqf:group`
  wrappers and `sqf:default-fix` ordering are honored, as are fixes pulled in
  via `sch:include` and same-document abstract rules (`sch:extends rule="…"`).

## Known limitations

- `sch:extends` with an external `href` and abstract patterns (`is-a`) are not
  resolved for fix discovery — findings from such rules simply show no fixes.
- `$sqf:match` inside `sqf:stringReplace` content is not supported; use
  `regex-group()` instead (the replacement content runs inside a native
  `xsl:analyze-string` matching-substring context).
- `@use-for-each` (generic iteration with `$sqf:current`) is not supported yet.
- Text nodes in heavily mixed content (text interleaved with comments/CDATA)
  may not be addressable for `sqf:stringReplace`; the fix then reports a clear
  error instead of guessing.

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Schematron](schematron-support.md) | [Home](index.md) | [Schema Support](schema-support.md) |
