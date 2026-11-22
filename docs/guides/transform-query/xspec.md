# Test with XSpec

[XSpec](https://github.com/xspec/xspec) tests are part of FreeXmlToolkit (XSpec 4, bundled): unit
tests for XSLT stylesheets, XQuery modules and Schematron, written as `.xspec` files.

```xml
<x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="price.xsl">
  <x:scenario label="net price of 120">
    <x:call function="f:net"><x:param select="120"/></x:call>
    <x:expect label="is 100" select="100"/>
  </x:scenario>
  <x:scenario label="item template">
    <x:context><item name="pen"/></x:context>
    <x:expect label="upper-case line"><line>PEN</line></x:expect>
  </x:scenario>
</x:description>
```

To start one, choose **File → New…**, type **XML**, and one of the templates **XSpec test (XSLT)**,
**XSpec test (XQuery)** or **XSpec test (Schematron)**; the file is saved as `.xspec`. Open the
`.xspec` file and choose **Tools → Run XSpec Tests**. The tests run with the text in the
editor (also unsaved changes); the file must be saved once, because the stylesheet, query or
Schematron it names is found relative to it. What is tested follows the description:
`stylesheet="…"` an XSLT stylesheet, `query="…"` with `query-at="…"` an XQuery module,
`schematron="…"` a Schematron (with `x:expect-assert`, `x:expect-report`, `x:expect-valid`).

The **XSpec** tab shows the summary (passed, failed, pending) and every scenario with its tests;
scenarios with a failed test are expanded and the first failure is selected. **Expected** and
**Actual** below show what the test expected and what the code returned. **Run Again** runs the
tests again (after you changed the stylesheet or the tests), **HTML Report** opens XSpec's own
report as a preview, **Export JUnit…** writes a JUnit XML report for CI servers. Scenarios marked
`pending="…"` are listed but not run.

On the command line: `fxt-cli xspec tests/price.xspec -r junit.xml` (see
[Command Line](../../automate/cli-transform.md#xspec)).

Ready-to-run examples are in the `examples/xspec/` folder of the installation: tests for an
XSLT stylesheet (functions and template rules), an XQuery library module and a Schematron, plus
`05-failing-demo.xspec`, which fails on purpose so you can see Expected and Actual.

---

## Before you start

- An `.xspec` file for the stylesheet or Schematron under test.

## Next steps

- [Debugger](debug.md)
- [CI recipes](../../automate/ci-recipes.md) - run the tests on every push
