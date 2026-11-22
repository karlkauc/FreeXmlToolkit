# Find and replace in a document

Search works in every view mode - Text, Tree and Graphic - for XML documents, XSD schemas and JSON
documents. To search whole folders, see [Search and replace in files](search-in-files.md).

## Finding Text in Your Document

Press **Ctrl+F** to open the Find feature and type the text you are looking for. Use the up and down arrows (**Find Previous** / **Find Next**, or Enter / Shift+Enter) to move between matches. When you reach the last match, the search wraps around and continues from the beginning.

## Search Works in All Views

You can search in the **Text**, **Tree** or **Graphic** view of a document:

- **Text view** - Matches are highlighted in the source code. **Replace** is available here.
- **Graphic view (XML instances)** - The search looks through element names, attribute names, and values across the whole document, including the cells of embedded tables. When you press the up/down arrows, the editor jumps directly to the matching node: if the match is inside a collapsed node, its parent nodes are expanded automatically, the matching row is selected, and it is scrolled into view.
- **Tree and Graphic views (XSD schemas)** - The search looks through the schema nodes themselves: element/attribute/type names, documentation and appinfo, type references, fixed/default values, enumeration and facet values, and comments. Matching nodes are revealed (collapsed branches expand), selected, and scrolled into view — in the Graphic diagram this includes loading lazily rendered subtrees.
- **Tree and Graphic views (JSON documents)** - The search looks through the keys and values of the document. Matches inside collapsed objects or arrays are revealed, selected, and scrolled into view, the same way as in the XML grid.

Searching is **case-insensitive** in all views.

## Switching Views While Searching

If you switch the view mode (Text / Tree / Graphic) or the file tab while the search bar is open, the search automatically re-targets the view you switched to, so you can keep navigating without retyping your search term.

## Where Search Is Available

Pressing **Ctrl+F** opens an **inline search bar** at the top of the editor, with up/down
chevron arrows for Find Previous / Find Next and a replace toggle (Text view only). The same
bar works in every view mode, for every XML-family document type and for JSON documents.

!!! note
    In the **Tree** and **Graphic** views, search is for finding and navigating only - **Replace** is not available there (the replace toggle is disabled). To replace text, use the **Text** view.

!!! note
    HTML documents have a rendered, read-only **Preview** view instead of Tree/Graphic (see [HTML Preview](../../get-started/workspace-tour.md#html-preview)). Pressing **Ctrl+F** there searches the document's underlying markup text, not the rendered page.

## Searching Across Many Files

Ctrl+F searches the **active document**. To search - and replace -
across **all files of a folder**, by plain text or by an XPath expression, use the
**Search** activity in the Unified Shell: press **Ctrl+Shift+F** (Find in Files) or
**Ctrl+Shift+H** (Replace in Files), or click the magnifying-glass icon in the activity
bar. See the [Search Panel](search-in-files.md) guide.

> **Changed shortcut:** Ctrl+Shift+F used to format the document - **Format Document is
> now Shift+Alt+F**.

---
