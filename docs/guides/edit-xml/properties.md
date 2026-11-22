# Edit the properties of a node

The Properties inspector shows the details of the currently selected node and lets you change
them. Toggle the panel with the toggle at the right end of the editor toolbar; it appears on
the right side of the editor. The [Properties Inspector](../../reference/inspector.md) page describes it for every
document type.

## Selecting a Node

How you select a node depends on the active view:

- **Text view** - Move the text caret into an element. That element is selected automatically
  and shown in the inspector. If the caret is not inside a well-formed element, the inspector
  shows a read-only view with the node's name and XPath. In large files (from 8 MB) the
  inspector follows the caret right away after edits in the grid. After you type, it shows no
  node until you pause for about 1.5 seconds. The file is then parsed again in the background,
  which takes a few seconds for very large files (about 5 seconds for 150 MB). The hover tip
  that names the element and XPath under the mouse is not shown in files of more than 2 MB:
  use the inspector or **Copy XPath** there.
- **Tree view** - Click a node in the tree (element, text, comment, CDATA, or processing
  instruction).
- **Graphic view** - Select a row in the grid.

## What You Can Edit

Depending on the node type, the inspector lets you edit:

- **Element name** - click the name in the node header at the top of the inspector to rename it
- **Namespace** (prefix and URI) - in the **NAMESPACE** section, right below NODE & XPATH
- **Attributes** - in the framed list of the **VALUE & ATTRIBUTES** section: click a name to
  rename it, click a value to edit it (**Enter** saves, **Esc** cancels), point at a row and
  click the trash icon to delete it, and use the row below the list to add one. You can also
  reach the rows with **Tab**: **Enter** edits the value, **Delete** removes the row
- **Text content** of leaf elements
- **Text** of comments, CDATA sections, and processing instructions

Editable values look like plain text; the field frame appears when you point at or click them.
Long values wrap instead of being cut off. **NODE & XPATH** shows the element's **Depth** (the
root element is 0) and has **Copy XPath** and **Copy Node (XML)** buttons next to the XPath.
Sections without content are not shown.
The **⋮** menu at the top of the inspector offers **Copy XPath**, **Copy Node (XML)**,
**Expand All Sections** and **Collapse All Sections**, and the **star** next to it adds the file
to your Favorites. For the full layout, see
[Properties Inspector](../../reference/inspector.md).

Your edits are written straight back to the document, so the source text always stays in sync.
In the Text view, edits are applied as a minimal change that keeps your caret and scroll
position in place.

## Schema Information

When the XML file is bound to an XSD schema, the inspector also shows what the schema says
about the selected element. The node header's validation pill shows **Valid**, the number of
errors, or *Well-formed · no schema bound* when no schema is bound.

- **Schema facts** - Type, Base type, Derivation (with list item type / union member types),
  the facets including enumeration values, minOccurs/maxOccurs, Use, **Order** (the element's
  position in its parent sequence or choice, e.g. *3 of 3 · sequence*), nillable, default and
  fixed values, *Since …* / *Deprecated: …*, the schema's appinfo, and identity constraints,
  assertions and type alternatives in **CONSTRAINTS**.
- **Declaration box** - *Declared in X.xsd · local element · type T* with an **Open in schema**
  link that opens the XSD in the Graphic view, expanded along the element's path in the
  document, and selects this element there.
- **Editing the schema from here** - where the declaration is editable, Type,
  minOccurs/maxOccurs, Nillable, Fixed, Default, the facets and the documentation/appinfo change
  the declaration **in the XSD**. The XSD opens as a background tab (your XML stays in front),
  the change is one undo step there, and after you save the XSD the XML document's schema facts
  are reloaded. Declarations from included/imported schemas and cached Schema Library copies are
  read-only. See
  [Properties Inspector - Editing the bound schema](../../reference/inspector.md#editing-the-bound-schema).
- **Documentation** - the element's documentation from the schema (*"No documentation in the
  schema"* when there is none).
- **Value check** - *"Value matches the schema facets"* in green, or an amber warning such as
  *"Value should be one of: …"* when the current text breaks a facet.
- **EXAMPLE VALUES** - shown as chips; click one to use it as the element's text.
- **VALID CHILDREN** - shown as chips; click one to add that child element (*None* when the
  element allows no children).
- **USED IN** - the containing element and its type.
- **Attributes** - each row shows its declared type as a badge (`*` = required); attributes the
  schema does not declare are marked **undeclared**; enumerated attributes get a drop-down.
  **DECLARED, NOT SET** chips (e.g. **+ id \***) add a missing declared attribute, with a
  default or fixed value filled in.

## Shared Across Views

All three views share a single in-memory model for each open document. This means your edits -
and your full **Undo / Redo** history - are preserved when you switch between the Text, Tree,
and Graphic views.

Switching views also keeps your place. The node at the caret is selected and opened in the Tree and the grid.
Coming back to the Text view, the caret goes to the node you last selected, and the node's lines are tinted
(see [View Modes](../../get-started/workspace-tour.md#view-modes)) - in very large documents (hundreds of MB) too.

!!! note
    Adding, deleting, or moving whole nodes (structural editing) is done in the
    **Graphic** view (the grid) via its right-click context menu. The Tree view can delete the
    selected node with **Delete**; otherwise the Text and Tree views provide property editing
    through the inspector.

---
