# TODO - FreeXmlToolkit

## Existing Tasks (Original)

* Schematron Editor: Zweite Button-Bar in erste Button-Bar integrieren
  * Wenn auf der Seite nicht möglich - Buttons deaktivieren
* XSLT Developer - Button open XSLT Stylesheet neben "Open XML"
* XSLT Developer - Recent mit hinzufügen
* XSLT Developer - Parameter default leer

---

## Not Yet Implemented Features (Code TODOs)

### High Priority - Beta/Mock Implementations

#### ~~XsltTransformationEngine.java (XSLT Debugging)~~ ✅ IMPLEMENTED
* ~~**extractTemplateMatchingInfo()** - Returns sample data instead of actual template matching info from Saxon~~
* ~~**extractVariableValues()** - Returns sample data instead of actual XSLT variable values~~
* ~~**extractCallStack()** - Returns sample data instead of actual transformation call stack~~
* **Status:** Fully implemented using Saxon TraceListener API (XsltDebugTraceListener, XsltDebugMessageListener)

### Medium Priority - Disabled Features

#### SchemaGeneratorController.java
* **Batch Processing** - Button disabled, feature not implemented
* **Type Definitions Filtering** - Only logs debug message, no actual filtering

#### XsdController.java
* **Favorites Management** - Menu item disabled, redirects to Settings (not implemented)

#### TemplatesController.java
* **Advanced Parameter Validation** - Shows "All parameters are valid" placeholder

#### XmlUltimateController.java
* **Recursively build tree from DOM** (line 2034) - Only creates root node
* **XML update from properties** (line 2046) - Empty implementation

### Low Priority - Smart Features

#### SnippetManagerPanel.java
* **Smart context-aware suggestions** - Falls back to basic search

#### SnippetContextMenu.java
* **Intelligent context-aware suggestions** - Based on selected text, element name, attributes (not implemented)

### Workarounds/Technical Debt

#### XsdParsingServiceImpl.java (line 271)
* Temporary solution: Serializes and re-parses XSD documents (performance inefficiency)
* Waiting for XsdNodeFactory to support Document input

#### XmlCodeFoldingManager.java (line 495)
* Workaround for RichTextFX paragraph graphic factory limitation

#### NestedGridNode.java (line 802)
* Hack: Uses marker-based approach for table state management

#### SvgIconRenderer.java (line 726)
* Basic approximation instead of full SVG rendering (SVGPath requires Scene)
