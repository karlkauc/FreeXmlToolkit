# Working with problems

Validation errors appear in the **PROBLEMS** panel below the editor and in the **Validation**
side panel - validating (F8, the toolbar, a panel's *Validate*) does not switch to the Validation page. The result stays with its document: switch to another tab (or to the Validation result that
**Validate** / F8 opens) and back, and the status and problems are shown again, as long as you have not
changed the text since. **Select** a problem to show it **in the view you are in** - the view does not change
(with the [AI assistant](../ai-assistant.md) on, the right-click menu also offers **Fix with AI** and **Explain with AI**):

- In the **Text** view the caret moves to the problem's line (and, for XML and XSD errors, its column).
- In the **Tree** view the element of that line is selected and scrolled into view.
- In the **Graphic** view the grid selects the element's row or table cell (opening tables and
  cells on the way) and flashes it; for an XSD the diagram selects the schema node.

The counters in the PROBLEMS header (red ✕ errors, orange ⚠ warnings, blue ⓘ hints) are filters: click one to list
only that severity, click it again to list all problems. The counters keep counting all problems; when new results
contain none of the filtered severity, the filter goes away. **Hints** are findings that need no change as a rule -
an ISIN not in ESMA FIRDS or not traded in the EU, an LEI whose registration is pending; they count neither as errors
nor as warnings. Findings of [Check Identifiers Online](../../industry/identifier-lookup.md) carry a link to the identifier's entry at
GLEIF, ESMA FIRDS or OpenFIGI.

## Large lists: categories, search, grouping, export

A check of a large report can give hundreds of thousands of findings - one per occurrence. The header has the tools to
work them off:

- **Categories** - every finding belongs to a category, grouped: *Document* (not well-formed, schema, Schematron),
  *LEI* (check digits, not registered, lapsed, pending), *ISIN* (unknown, CFI code differs, issuer LEI differs, not
  traded in the EU), *FMA rules* (master data, Form 800, reconciliation and totals, assets, look-through, allocation,
  Form 900, whole report, additional checks), *Extension rules* and *Other*. The menu shows each with its count; tick
  them on and off, or show all / none.
- **Search** - text, an ISIN or LEI, a rule code, a security's name.
- **Group** - *List* (one row per finding, as before), *Security / object* (one group per ISIN, LEI, asset or VRG: a
  security that occurs a hundred times is one row, with its kinds of problems and their occurrences below), *Rule* and
  *Category*. Findings without a security (wrong totals, schema errors) come after the securities, grouped by rule.
  Errors come first, then the groups with the most occurrences. Click an occurrence to jump to it.
- A group's right-click menu: **Show in List** (only the group's findings, with a label to leave), **Copy These
  Problems**, **Export These Problems to Excel…** and, where ESMA FIRDS knows better, **Fix ISIN … in Every Place**:
  the reported CFI code and issuer LEI of the security are replaced with the FIRDS values in every position of the
  document - after one confirmation that lists old → new and how often, as one undo step. A rule or category group
  fixes all its securities at once.
- **Export** - the shown (filtered) or all problems to an Excel workbook: *Summary* (filter, counts per severity and
  category), *Problems* (severity, category, rule, security / object and its name, line, message, link), *By Object*
  (one row per security with its occurrences and kinds of problems) and *By Rule*. The row menu offers the export
  too. The workbook's document properties (title, subject, description, keywords) name the checked document and
  sum up the counts and the filter, so the file can be found and told apart later.

The chosen grouping is remembered.

See [Validation Panel](index.md) for details.
