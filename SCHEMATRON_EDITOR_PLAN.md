# 📋 **Comprehensive Plan: Schematron Editor Integration**

## 🏗️ **1. Current Architecture Analysis** ✅

Die Anwendung folgt einem klaren MVC-Pattern mit:

- **Controller pro Dateityp**: `XmlController`, `XsdController`, etc.
- **Tab-basierte UI**: Jeder Controller entspricht einem Tab im linken Menü
- **FXML-Dateien**: `tab_*.fxml` für jeden Controller
- **Service Layer**: Geschäftslogik in separaten Service-Klassen
- **FileChooser Integration**: Zentrale Dateierkennung im `MainController`

---

## 🎯 **2. File Management Extensions**

### **A. FileChooser Extensions**

```java
// MainController.java - handleOpenFile() erweitern:
fileChooser.getExtensionFilters().

addAll(
    new FileChooser.ExtensionFilter("All Supported Files", 
        "*.xml","*.xsd","*.xsl","*.xslt","*.sch","*.schematron"),
// ... existing filters ...
    new FileChooser.

ExtensionFilter("Schematron files (*.sch, *.schematron)",
                        "*.sch","*.schematron")
);
```

### **B. File Type Detection & Routing**

```java
// Neue Methode im MainController:
public void switchToSchematronViewAndLoadFile(File fileToLoad) {
    loadPageFromPath("/pages/tab_schematron.fxml");
    if (this.schematronController != null && fileToLoad != null && fileToLoad.exists()) {
        Platform.runLater(() -> {
            schematronController.loadSchematronFile(fileToLoad);
        });
    }
}
```

---

## 🎨 **3. Schematron Editor UI Design**

### **A. New Components to Create**

#### **3.1 Menu Button (main.fxml)**

```xml

<Button mnemonicParsing="false" onAction="#loadPage" styleClass="menu_button"
        text="Schematron Editor" fx:id="schematron">
    <graphic>
        <FontIcon iconColor="#ff6b6b" iconLiteral="bi-shield-check" iconSize="16"/>
    </graphic>
</Button>
```

#### **3.2 Tab Layout (tab_schematron.fxml)**

```xml

<TabPane>
    <!-- Code Editor Tab -->
    <Tab text="Code" closable="false">
        <SplitPane>
            <!-- Main Editor Area -->
            <VBox>
                <!-- Toolbar -->
                <ToolBar>
                    <Button text="New Rule"/>
                    <Button text="New Pattern"/>
                    <Button text="Validate"/>
                    <Button text="Test Rules"/>
                </ToolBar>
                <!-- Code Editor -->
                <CodeArea fx:id="schematronCodeArea"/>
            </VBox>

            <!-- Sidebar -->
            <VBox>
                <TitledPane text="Schematron Structure"/>
                <TitledPane text="Rule Templates"/>
                <TitledPane text="XPath Helper"/>
            </VBox>
        </SplitPane>
    </Tab>

    <!-- Visual Builder Tab -->
    <Tab text="Visual Builder" closable="false">
        <!-- Drag & Drop Rule Builder -->
    </Tab>

    <!-- Test Tab -->
    <Tab text="Test" closable="false">
        <!-- XML Test Files & Results -->
    </Tab>

    <!-- Documentation Tab -->
    <Tab text="Documentation" closable="false">
        <!-- Generated Documentation -->
    </Tab>
</TabPane>
```

#### **3.3 Controller Class (SchematronController.java)**

## 🎯 **4. Syntax Highlighting & Language Support**

### **A. Schematron Syntax Highlighting**

```java
public class SchematronSyntaxHighlighter {
    private static final Pattern SCHEMATRON_ELEMENTS = Pattern.compile(
            "\\b(schema|pattern|rule|assert|report|let|param|title|p|emph|dir|span)\\b"
    );

    private static final Pattern XPATH_EXPRESSIONS = Pattern.compile(
            "\\btest\\s*=\\s*[\"']([^\"']*)[\"']"
    );

    private static final Pattern SCHEMATRON_ATTRIBUTES = Pattern.compile(
            "\\b(context|test|flag|id|role|subject|fpi|icon|see|space)\\s*="
    );

    public StyleSpans<Collection<String>> computeHighlighting(String text) {
        // Implementation für Syntax-Highlighting
    }
}
```

### **B. Auto-Completion für Schematron**

```java
public class SchematronAutoComplete {
    private static final List<String> SCHEMATRON_ELEMENTS = Arrays.asList(
            "sch:schema", "sch:pattern", "sch:rule", "sch:assert",
            "sch:report", "sch:let", "sch:param", "sch:title"
    );

    private static final List<String> COMMON_XPATH_FUNCTIONS = Arrays.asList(
            "count()", "exists()", "normalize-space()", "string-length()",
            "substring()", "contains()", "starts-with()", "ends-with()"
    );
}
```

---

## 🔧 **5. Core Schematron Services**

### **A. Enhanced SchematronService**

```java
public interface SchematronService {
    // Existing validation methods...

    // New editing methods
    SchematronDocument parseSchematronFile(File file);

    boolean saveSchematronFile(SchematronDocument doc, File file);

    List<SchematronRule> extractRules(SchematronDocument doc);

    List<SchematronPattern> extractPatterns(SchematronDocument doc);

    // Template and generation methods
    String generateSchematronTemplate();

    String generateRuleTemplate(String context);

    String generateAssertTemplate(String test, String message);

    // Testing methods
    SchematronTestResult testSchematronAgainstXML(File schematronFile, File xmlFile);

    List<String> validateXPathExpressions(String xpathExpr, Document xmlContext);
}
```

### **B. New Domain Objects**

```java
public class SchematronDocument {
    private String title;
    private String namespaceURI;
    private List<SchematronPattern> patterns;
    private Map<String, String> namespaces;
    // Getters/Setters
}

public class SchematronPattern {
    private String id;
    private String title;
    private List<SchematronRule> rules;
    // Getters/Setters
}

public class SchematronRule {
    private String context;
    private String id;
    private List<SchematronAssertion> assertions;
    private List<SchematronVariable> variables;
    // Getters/Setters
}

public class SchematronAssertion {
    private AssertionType type; // ASSERT or REPORT
    private String test;
    private String message;
    private String flag;
    private String role;
    // Getters/Setters
}
```

---

## 🚀 **6. Advanced Editor Features**

### **A. Visual Rule Builder**

- **Drag & Drop Interface**: Regeln visuell zusammenstellen
- **XPath Builder**: Grafische XPath-Erstellung
- **Context Selector**: XML-Struktur für Kontext-Auswahl
- **Template Library**: Vorgefertigte Regeln für häufige Szenarien

### **B. Testing & Validation Framework**

```java
public class SchematronTester {
    public SchematronTestResults runTests(File schematronFile, List<File> testXmlFiles) {
        // Run schematron against multiple XML test files
        // Return detailed results with pass/fail status
    }

    public XPathValidationResult validateXPath(String xpath, Document context) {
        // Validate XPath syntax and test against context
    }
}
```

### **C. Documentation Generator**

```java
public class SchematronDocumentationGenerator {
    public String generateHTML(SchematronDocument doc) {
        // Generate readable documentation
    }

    public String generateMarkdown(SchematronDocument doc) {
        // Generate markdown documentation
    }
}
```

---

## 📋 **7. Complete Implementation Plan**

### **Phase 1: Foundation (Woche 1-2)**

1. **✅ File Extensions & Routing**
    - MainController FileChooser erweitern
    - Schematron file detection
    - Menu button hinzufügen

2. **🎨 Basic UI Structure**
    - `tab_schematron.fxml` erstellen
    - `SchematronController.java` grundgerüst
    - Code Editor Tab mit RichTextFX integration

3. **🔧 Core Service Extensions**
    - `SchematronService` interface erweitern
    - Domain objects (`SchematronDocument`, etc.)
    - File I/O operations

### **Phase 2: Editor Features (Woche 3-4)**

4. **🎯 Syntax Highlighting**
    - `SchematronSyntaxHighlighter` implementieren
    - XML + Schematron spezifische highlighting
    - XPath expression highlighting

5. **💡 Auto-Completion**
    - Schematron element completion
    - XPath function completion
    - Context-sensitive suggestions

6. **🔍 Error Detection**
    - XML well-formedness
    - Schematron schema validation
    - XPath syntax validation

### **Phase 3: Advanced Features (Woche 5-6)**

7. **🎨 Visual Rule Builder**
    - Drag & Drop interface
    - Rule template library
    - XPath builder component

8. **🧪 Testing Framework**
    - Test runner implementation
    - Multiple XML file testing
    - Result visualization

9. **📚 Documentation Generator**
    - HTML report generation
    - Rule documentation extraction

### **Phase 4: Integration & Polish (Woche 7-8)**

10. **🔗 Integration Features**
    - XML Editor ↔ Schematron Editor linking
    - Cross-referencing capabilities
    - Shared XSD context

11. **⚡ Performance & UX**
    - Large file handling
    - Background processing
    - Progress indicators
    - Error recovery

---

## 📁 **8. Files to Create/Modify**

### **New Files:**

```
src/main/java/org/fxt/freexmltoolkit/
├── controller/
│   └── SchematronController.java                    [NEW]
├── service/
│   ├── SchematronEditorService.java                [NEW]
│   └── SchematronDocumentationService.java         [NEW]
├── domain/
│   ├── SchematronDocument.java                     [NEW]
│   ├── SchematronPattern.java                      [NEW]
│   ├── SchematronRule.java                         [NEW]
│   └── SchematronAssertion.java                    [NEW]
└── controls/
    ├── SchematronCodeEditor.java                   [NEW]
    ├── SchematronSyntaxHighlighter.java           [NEW]
    ├── SchematronAutoComplete.java                [NEW]
    └── SchematronVisualBuilder.java               [NEW]

src/main/resources/
├── pages/
│   └── tab_schematron.fxml                        [NEW]
├── css/
│   └── schematron-editor.css                      [NEW]
└── templates/
    └── schematron/                                 [NEW DIR]
        ├── basic-template.sch
        ├── iso-schematron-template.sch
        └── business-rules-template.sch
```

### **Modified Files:**

```
src/main/java/org/fxt/freexmltoolkit/
├── controller/MainController.java                  [MODIFY]
└── service/SchematronService.java                  [MODIFY]

src/main/resources/pages/
└── main.fxml                                       [MODIFY]
```

---

## 🎯 **9. Key Features Summary**

### **🔧 Core Editor Features:**

- ✅ Syntax highlighting für Schematron + XPath
- ✅ Auto-completion & IntelliSense
- ✅ Error detection & validation
- ✅ File templates & snippets
- ✅ Folding & outline view

### **🎨 Visual Builder:**

- ✅ Drag & drop rule creation
- ✅ XPath builder with GUI
- ✅ Context selector from XML samples
- ✅ Pattern/Rule hierarchy visualization

### **🧪 Testing & Validation:**

- ✅ Live XML testing against rules
- ✅ Batch testing multiple files
- ✅ Result visualization & reporting
- ✅ XPath debugger & evaluator

### **📚 Documentation & Export:**

- ✅ HTML report generation
- ✅ Rule documentation extraction
- ✅ Markdown export
- ✅ Integration mit existing validation features

---

## 🎉 **10. Implementation Priority & ROI**

### **🚀 High Priority (sofort umsetzbar):**

1. **Basic Schematron File Support** - Dateierkennung & Code Editor
2. **Syntax Highlighting** - Wesentlich für Benutzerfreundlichkeit
3. **Integration mit existing Validation** - Nutzt vorhandene Infrastruktur

### **💡 Medium Priority (nach Grundfunktionen):**

4. **Visual Builder** - Differenzierungsmerkmal gegenüber anderen Tools
5. **Testing Framework** - Professionelle Schematron-Entwicklung
6. **Auto-Completion** - Developer Experience

### **✨ Nice-to-Have (später):**

7. **Documentation Generator** - Zusätzlicher Wert
8. **Advanced XPath Tools** - Power-User Features

---

## 📊 **Effort Estimation:**

- **Phase 1 (Foundation)**: ~40 Stunden
- **Phase 2 (Editor Features)**: ~60 Stunden
- **Phase 3 (Advanced Features)**: ~80 Stunden
- **Phase 4 (Integration & Polish)**: ~40 Stunden

**Total**: ~220 Stunden (≈ 5-6 Wochen Vollzeit)

---

**Dieser Plan macht die FreeXMLToolkit zur ersten Wahl für professionelle Schematron-Entwicklung und erweitert die
Anwendung um einen völlig neuen, wertvollen Funktionsbereich!** 🎯