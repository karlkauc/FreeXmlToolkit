# Regression Check Example

A project scenario can name an **expected result**: a check runs the scenario and compares its
result with that file ("golden file"). XML is compared by structure — formatting and attribute
order do not count — and values that change from run to run are XMLUnit placeholders in the
expected file.

## The files

| File | Role |
|------|------|
| `regression.fxtproj` | The project: one scenario, "Fund summary", with an expected result and compare settings |
| `fund-summary.xsl` | The stylesheet: summarises `../xml/FundsXML4_Equity_Fund.xml` (NAV, positions, core positions) |
| `expected/equity-summary.xml` | The expected result, with placeholders for the creation time and the run id |

The scenario's compare settings in `regression.fxtproj`:

```json
"expected": "expected/equity-summary.xml",
"compare": { "tolerance": 0.001, "ignoreComments": true }
```

- `tolerance`: the sum of the weights is `100.00009999999997` in the result and `100` in the
  expected file — equal within 0.001.
- `ignoreComments`: the expected file explains itself in a comment; comments are not compared.

Placeholders used in the expected file:

| Placeholder | For |
|---|---|
| `${xmlunit.isDateTime}` | `created`, a timestamp of the run |
| `${xmlunit.matchesRegex(^d\w+-\d+$)}` | `run`, an id that changes but has a fixed form |

Others: `${xmlunit.ignore}` (anything), `${xmlunit.isNumber}`.

## Running the check

**In FreeXmlToolkit:** open `regression.fxtproj` (File → Project → Open Project…), then

- **File → Project → Check Scenarios**, or
- in the **Transform** panel choose "Fund summary" in SCENARIO and press **Check** (the clipboard
  button next to Run Scenario).

**On the command line:**

```bash
fxt-cli scenario regression.fxtproj --check
fxt-cli scenario regression.fxtproj --check -f junit -r scenarios.xml   # for a CI server
```

## Seeing it fail

Change the stylesheet, e.g. the 10 % bound of a core position (`ge 10`) to `ge 20`, and check
again: the check fails with "Element <core> removed". In the Transform panel, **Differences**
opens the expected and the actual result side by side; **Accept** makes the new result the
expected one — the placeholders stay. Undo the change to make the check pass again.

See also: *Regression checks* in the user documentation (`docs/projects.md`).
