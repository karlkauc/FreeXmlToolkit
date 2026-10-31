# Keyboard Shortcuts

All shortcuts of the Unified Shell in one place. The same list is available in the application
under **Help → Keyboard Shortcuts**, and every menu item shows its shortcut next to it.

!!! tip "macOS"
    On macOS use **Cmd** instead of **Ctrl** for the shortcuts below (see
    [Menu Bar → macOS](unified-shell.md#macos)). Because **Cmd+H** hides the application,
    Find and Replace is **Alt+Cmd+F**; full screen is **Ctrl+Cmd+F** (F11 works too).

## Files and Tabs

| Shortcut | Action |
|----------|--------|
| Ctrl+N | New file (opens the guided New File dialog) |
| Ctrl+O | Open file |
| Ctrl+S | Save current tab |
| Ctrl+Shift+S | Save As (the Save ▾ menu also offers Save All) |
| Ctrl+W (Windows/Linux also Ctrl+F4) | Close the active tab (asks to save unsaved changes) |
| Ctrl+Tab / Ctrl+Shift+Tab | Next / previous tab |
| Ctrl+, | Open the Settings page |
| Ctrl+Q | Quit FreeXmlToolkit (**Cmd+Q** on macOS; Alt+F4 on Windows works as usual). With unsaved documents one dialog lists them and offers **Save All** / **Don't Save** / **Cancel** - the same when the window is closed |

## Editing and Search

| Shortcut | Action |
|----------|--------|
| Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z) | Undo / Redo (works from the Text, Tree and Graphic views) |
| Ctrl+F | Find (in the active document, in every view mode - see below) |
| Ctrl+H | Find and Replace (in the active document); **Alt+Cmd+F** on macOS |
| Ctrl+Shift+F | **Find in Files** - opens the [Search panel](unified-shell.md#search-panel), prefilled with the editor selection |
| Ctrl+Shift+H | **Replace in Files** - opens the Search panel with the replace row expanded |
| Shift+Alt+F | Format (pretty-print) the active document |
| Ctrl+Space | Auto-completion ([IntelliSense](context-sensitive-intellisense.md)); also in the Query Console |
| Ctrl+T | Insert Template… (needs an open document) |
| Alt+Enter / Ctrl+. | Quick Fix - apply a [Schematron Quick Fix](schematron-quick-fixes.md) on the caret line |
| Ctrl+D | Add the active document to favorites |

!!! warning "Changed shortcut"
    **Ctrl+Shift+F no longer formats the document.** Following the VS Code convention, it
    now opens **Search (Find in Files)**, and **Ctrl+Shift+H** opens **Replace in Files**.
    **Format Document moved to Shift+Alt+F** - the toolbar's **Format** button works
    unchanged. The in-editor find/replace stays on **Ctrl+F / Ctrl+H**.

## View

| Shortcut | Action |
|----------|--------|
| Ctrl+L | Show/hide the left side panel |
| Ctrl+Shift+P | Show/hide the [Properties inspector](inspector.md) |
| Ctrl+Shift+D | Show the Favorites panel (press again to collapse the side panel) |
| Ctrl+Shift+X | Toggle the [Query Console](query-console.md) (XPath/XQuery/JSONPath) |
| Ctrl+K | Open the Query Console and focus its input (same as the header search pill) |
| Ctrl++ / Ctrl+- | Zoom in / out (Text view: font size; Graphic view: the grid in 10 % steps, 50-300 %) |
| Ctrl+0 | Reset the zoom to 100 % (Text view: font size, Graphic view: the grid) |
| Ctrl+mouse wheel | Zoom (Text view: font size, Graphic view: the whole grid) |
| F11 | Toggle full screen (also **Ctrl+Cmd+F** on macOS) |
| F1 | Show the Help menu (as a popup, like the header's **?** button) |

## Run, Validate and Convert

| Shortcut | Action |
|----------|--------|
| F5 | Run the active XQuery / XPath / XSLT / XProc document (does nothing for other documents - use F8 to validate) |
| Ctrl+Enter | Run the active XPath/XQuery, XSLT or XProc document (Run Query / Run Transform / Run Pipeline); in the Query Console: run the query |
| F8 | Validate the active document (same run as Tools → Validate, the toolbar's **Validate** button and the Validation panel's **Run Validation**) |
| Ctrl+E | Spreadsheet Converter… (Excel / CSV ↔ XML) |

## Debugger

See the [Debugger](debugger.md) page for details.

| Shortcut | Action |
|----------|--------|
| Shift+F5 | **Debug** - the active XSLT, XQuery or Schematron document, the PDF / FOP panel's stylesheet, or the Transform panel's (asks for one if none is set) |
| Ctrl+Shift+F5 | Re-run the last debug run (input read again) |
| F9 | Toggle the breakpoint on the caret line of a saved stylesheet (also **Tools → Toggle Breakpoint**) |
| Ctrl+F10 | Run to Cursor (starts the debugger to the caret line when none runs) |
| F5 / F7 / F8 / Shift+F8 | While the debugger is **paused**: Continue / Step Into / Step Over / Step Out (F5 and F8 keep their normal meaning otherwise) |
| Shift+F7 | While paused: Smart Step Into - choose which call on the line to step into |
| F6 | While the debugger runs: Pause (break at the next instruction) |
| Alt+F8 | While paused: Evaluate an XPath expression in the selected frame |

## Grid View (Graphic)

These work on the selected row of the [Grid View](grid.md) of XML and JSON documents.

| Shortcut | Action |
|----------|--------|
| Arrow keys, Home / End | Move the selection; Home / End jump to the first / last row |
| F2 | Rename the selected node / key |
| Delete | Delete the selected node(s) |
| Ctrl+C / Ctrl+X / Ctrl+V | Copy / Cut / Paste as sibling (in a table: copy as tab-separated text, paste fills cells) |
| Ctrl+Shift+V | Paste as child |
| Ctrl+D | Duplicate |
| Alt+Up / Alt+Down | Move the node up / down |
| Ctrl+A | Select the whole table (inside a repeating-elements table) |
| Ctrl+Shift++ / Ctrl+Shift+− | Expand Fully / Collapse Unselected |
| Ctrl+Alt+T | Change the type of the selected row |
| Ctrl+Alt+W | Wrap in Element… (XML) |
| Ctrl+Alt+R | Edit as Raw Text… (XML) |
| Ctrl+Alt+X | Copy the XPath / JSONPath of the selected node |
| Ctrl+Alt+C | Copy the selected node as XML / JSON |
| Ctrl+Shift+C | Copy the cell content |

## Search in Every View

Pressing **Ctrl+F** opens an inline search bar with up/down chevron arrows for Find Previous / Find Next (Enter / Shift+Enter work too). The search works in the **Text**, **Tree** and **Graphic** views. In the structured views it searches the nodes themselves — for XSD schemas that is element/attribute/type names, documentation and appinfo, type references, fixed/default values, enumeration and facet values, and comments; for XML instances in the Graphic grid it is element names, attribute names, and values (also inside embedded table cells); for JSON documents (Tree and Graphic) it is keys and values. A match hidden inside a collapsed branch is revealed automatically (ancestors expand), selected, and scrolled into view; matches wrap around when you reach the end. Switching the view mode or tab while the bar is open re-targets the search to the active view. **Replace** is available in the Text view only — in Tree/Graphic views the replace toggle is disabled. In an HTML document's **Preview**, Ctrl+F searches the underlying markup text, not the rendered page. See [XML Editor Features](xml-editor-features.md#search-find) for details.
