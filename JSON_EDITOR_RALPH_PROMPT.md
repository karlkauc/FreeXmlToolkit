# Ralph-Loop Prompt: JSON Editor Implementation

## Prompt zum Starten der Ralph-Loop

Kopiere diesen Prompt in eine neue Claude Code Session:

---

```
Implementiere den JSON Editor für FreeXmlToolkit gemäß dem Plan in JSON_EDITOR_IMPLEMENTATION_PLAN.md.

WICHTIGE REGELN:
1. Lies zuerst den Plan in JSON_EDITOR_IMPLEMENTATION_PLAN.md
2. Arbeite die Phasen in der Reihenfolge 1-7 ab
3. Nach jeder Phase: Führe `./gradlew build` aus und behebe alle Fehler
4. Schreibe Unit Tests für neue Klassen
5. Committe nach jeder abgeschlossenen Phase mit aussagekräftiger Message
6. Halte dich an die existierenden Patterns im Codebase (siehe Referenz-Dateien im Plan)

AKTUELLER FORTSCHRITT:
- [ ] Phase 1: Basis-Infrastruktur
- [ ] Phase 2: Standalone JSON Tab
- [ ] Phase 3: Unified Editor Integration
- [ ] Phase 4: JSON Model & Tree View
- [ ] Phase 5: JSON Schema Validierung
- [ ] Phase 6: JSONC & JSON5 Support
- [ ] Phase 7: JSONPath Support

STARTE mit Phase 1:
1. Füge die Dependencies in build.gradle.kts hinzu
2. Erstelle JsonSyntaxHighlighter.java (Pattern von XmlSyntaxHighlighter)
3. Erstelle JsonService.java mit parseJson(), formatJson(), validateJson()
4. Erstelle json-editor.css mit den Highlighting-Klassen
5. Teste mit ./gradlew build

Wenn Phase 1 fertig ist, mache einen Commit und fahre mit Phase 2 fort.

Arbeite autonom weiter bis alle Phasen abgeschlossen sind oder du auf ein Problem stößt, das du nicht lösen kannst.
```

---

## Alternative: Phasenweise Prompts

Falls du lieber Phase für Phase starten möchtest:

### Phase 1 Prompt
```
Implementiere Phase 1 des JSON Editors (JSON_EDITOR_IMPLEMENTATION_PLAN.md):

1. Füge in build.gradle.kts hinzu:
   - de.marhali:json5-java:3.0.0
   - com.networknt:json-schema-validator:1.5.6
   - com.jayway.jsonpath:json-path:2.9.0

2. Erstelle src/main/java/org/fxt/freexmltoolkit/controls/shared/JsonSyntaxHighlighter.java
   - Nutze XmlSyntaxHighlighter.java als Vorlage
   - Pattern für: KEY, STRING, NUMBER, BOOLEAN, NULL, BRACKET, COMMENT

3. Erstelle src/main/java/org/fxt/freexmltoolkit/service/JsonService.java
   - parseJson(String) -> JsonElement
   - formatJson(String, int indent) -> String
   - validateJson(String) -> List<String> errors
   - isValidJson(String) -> boolean

4. Erstelle src/main/resources/css/json-editor.css
   - .json-key, .json-string, .json-number, .json-boolean, .json-null, .json-bracket, .json-comment

5. Führe ./gradlew build aus und behebe Fehler.
6. Committe: "feat: Add JSON editor infrastructure (Phase 1)"
```

### Phase 2 Prompt
```
Implementiere Phase 2 des JSON Editors (Standalone JSON Tab):

1. Erstelle JsonCodeEditor.java in controls/jsoneditor/editor/
   - Nutze RichTextFX CodeArea
   - Integriere JsonSyntaxHighlighter
   - Undo/Redo Support

2. Erstelle tab_json.fxml (Pattern von tab_xml_ultimate.fxml)
   - Toolbar: New, Open, Save, Format, Validate
   - CodeArea für JSON-Bearbeitung
   - Status-Leiste

3. Erstelle JsonController.java
   - setParentController(MainController)
   - File operations: newFile, openFile, saveFile
   - formatJson(), validateJson()

4. Erweitere main.fxml:
   - Füge JSON-Button im Sidebar hinzu (nach XML)
   - Icon: bi-filetype-json, Farbe: #f57c00

5. Erweitere MainController.java:
   - loadPage switch case für "json"
   - setParentController für JsonController
   - JsonController Feld

6. Teste: ./gradlew run -> JSON Tab öffnen -> Datei laden/speichern
7. Committe: "feat: Add standalone JSON editor tab (Phase 2)"
```

### Phase 3 Prompt
```
Implementiere Phase 3 des JSON Editors (Unified Editor Integration):

1. Erweitere UnifiedEditorFileType.java:
   JSON("bi-filetype-json", "#f57c00", "json-tab", Set.of("json", "jsonc", "json5"))

2. Erstelle JsonUnifiedTab.java (extends AbstractUnifiedEditorTab)
   - Nutze XmlUnifiedTab als Vorlage
   - Implementiere alle abstrakten Methoden
   - JsonCodeEditor als Text-Editor

3. Erweitere UnifiedEditorTabFactory.java:
   case JSON -> new JsonUnifiedTab(file);

4. Erweitere UnifiedEditorController.java:
   - @FXML public void newJsonFile()
   - File Chooser Filter für .json

5. Erweitere tab_unified_editor.fxml:
   - MenuItem für "New JSON File"

6. Teste: Unified Editor -> New -> JSON File
7. Committe: "feat: Integrate JSON editor into Unified Editor (Phase 3)"
```

---

## Tipps für die Ralph-Loop

1. **Starte mit /ralph-loop** um die Loop zu aktivieren
2. **Überwache den Fortschritt** - Claude wird nach jeder Phase einen Commit machen
3. **Bei Problemen** wird Claude stoppen und nachfragen
4. **Build-Fehler** werden automatisch behoben
5. **Tests** werden automatisch ausgeführt

## Erwartete Commits nach Abschluss

```
feat: Add JSON editor infrastructure (Phase 1)
feat: Add standalone JSON editor tab (Phase 2)
feat: Integrate JSON editor into Unified Editor (Phase 3)
feat: Add JSON model and tree view (Phase 4)
feat: Add JSON Schema validation and IntelliSense (Phase 5)
feat: Add JSONC and JSON5 support (Phase 6)
feat: Add JSONPath query support (Phase 7)
```
