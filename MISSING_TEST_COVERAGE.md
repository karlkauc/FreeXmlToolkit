# Fehlende Test Coverage - Aktuelle Analyse

**Stand:** 2025-11-17 nach Service Layer Tests
**Aktuelle Test-Dateien:** 155 (+5 neue Service Tests)

---

## 🔴 PRIORITY 1: KRITISCH (Sofort implementieren)

Diese Klassen sind **architektonische Kernkomponenten** mit **0% Coverage**:

### 1. V2 Editor Core (0% Coverage) ⚠️ HÖCHSTE PRIORITÄT

**Warum kritisch:** Diese 3 Klassen sind das Herzstück des V2 Editors. Bugs hier betreffen alle Bearbeitungsoperationen.

#### CommandManager
- **Datei:** `controls/v2/editor/commands/CommandManager.java`
- **Funktion:** Undo/Redo-Manager für gesamten V2 Editor
- **Tests:** 0
- **Benötigt:** ~18 Tests (siehe PROPOSED_TESTS.md)
- **Test-Bereiche:**
  - Execute/Undo/Redo-Operationen
  - Command-Merging
  - History-Limit-Enforcement
  - PropertyChange-Events
  - Error-Handling
  - Stack-State-Management

#### XsdEditorContext
- **Datei:** `controls/v2/editor/XsdEditorContext.java`
- **Funktion:** Zentrale Koordination für V2 Editor
- **Tests:** 0
- **Benötigt:** ~12 Tests (siehe PROPOSED_TESTS.md)
- **Test-Bereiche:**
  - Context-Initialisierung
  - Dirty-Flag-Management
  - Edit-Mode-Toggle
  - CommandManager-Integration
  - PropertyChange-Notifications

#### SelectionModel
- **Datei:** `controls/v2/editor/selection/SelectionModel.java`
- **Funktion:** Selection-Tracking für alle UI-Operationen
- **Tests:** 0
- **Benötigt:** ~22 Tests (siehe PROPOSED_TESTS.md)
- **Test-Bereiche:**
  - Single/Multi-Selection
  - Add/Remove-Operationen
  - Primary-Selection-Tracking
  - Event-Notifications
  - Toggle-Selection

**Geschätzte Zeit:** 12 Stunden
**ROI:** ⭐⭐⭐⭐⭐ Schützt gesamte V2 Editor-Architektur

---

### 2. Controller Layer (12 von 17 untested - 29% Coverage) ⚠️

**Kritische Controller ohne Tests:**

#### MainController ⚠️ KRITISCH
- **Funktion:** Haupt-Applikationssteuerung, Tab-Management
- **Test-Ansatz:** TestFX für JavaFX-Integration
- **Benötigt:**
  - Application-Initialisierung
  - Tab-Management (create, switch, close)
  - ExecutorService-Koordination
  - Memory-Monitoring

#### XmlUltimateController ⚠️ KRITISCH
- **Funktion:** XML-Editor mit IntelliSense
- **Test-Ansatz:** TestFX mit Mock-Dependencies
- **Benötigt:**
  - Multi-Tab-Editing
  - XML-Validierung
  - XPath/XQuery-Ausführung
  - IntelliSense-Integration

#### Weitere fehlende Controller:
```
❌ SchematronController - Schematron-Validierung
❌ XsltController - XSLT-Transformationen
❌ FopController - PDF-Generierung
❌ SignatureController - Digitale Signaturen
❌ SettingsController - Anwendungseinstellungen
❌ SchemaGeneratorController - Schema-Generierung
❌ TemplatesController - Template-Verwaltung
❌ WelcomeController - Welcome-Screen
❌ XsdValidationController - XSD-Validierung
❌ XsltDeveloperController - XSLT-Entwicklung
❌ XsdController (partial) - Nur 4 spezifische Tests
```

**Geschätzte Zeit:** 12-24 Stunden (1-2h pro Controller)
**ROI:** ⭐⭐⭐⭐ Schützt Hauptfunktionalität

---

### 3. FacetsPanel (0% Coverage) ⚠️

- **Datei:** `controls/v2/editor/panels/FacetsPanel.java`
- **Funktion:** Inherited Facets Feature (Kernfunktionalität)
- **Test-Ansatz:** TestFX UI-Tests
- **Benötigt:**
  - Datatype-spezifisches Filtering
  - Fixed Facets Display (read-only, gelber Hintergrund)
  - Inherited Facets Display (blauer Hintergrund)
  - Facet-Editing via Commands
  - Mode-Switching (editable vs. read-only)
  - Visuelle Unterscheidung

**Geschätzte Zeit:** 4 Stunden
**ROI:** ⭐⭐⭐⭐⭐ Kernfeature

---

## 🟠 PRIORITY 2: HOCH (Nächste Phase)

### 4. IntelliSense System (26/27 Dateien untested - 4% Coverage) ⚠️

**Kernfeature mit fast keinen Tests!**

**Kritische Dateien ohne Tests:**
```
controls/intellisense/
├── ✅ XmlIntelliSenseEngineTest (existiert)
├── ❌ CompletionCache.java - Caching-Mechanismus
├── ❌ CompletionContext.java - Context-Bestimmung
├── ❌ EnhancedCompletionPopup.java - UI-Komponente
├── ❌ FuzzySearch.java - Such-Algorithmus
├── ❌ MultiSchemaManager.java - Mehrere Schemas
├── ❌ NamespaceResolver.java - Namespace-Auflösung
├── ❌ SchemaValidator.java - Validierungs-Integration
├── ❌ XsdDocumentationExtractor.java - Doku-Extraktion
└── ❌ XmlCodeFoldingManager.java - Code-Folding
    ... und 16+ weitere Dateien
```

**Test-Strategie:**
1. **Phase 1 - Core-Algorithmen** (3-4h)
   - FuzzySearch - Matching-Algorithmus
   - CompletionContext - Context-Erkennung
   - NamespaceResolver - Namespace-Regeln

2. **Phase 2 - Integration** (4-5h)
   - MultiSchemaManager - Schema-Loading
   - SchemaValidator - Validierungs-Integration
   - XsdDocumentationExtractor - Doku-Parsing

3. **Phase 3 - UI** (2-3h)
   - EnhancedCompletionPopup - TestFX UI-Tests
   - CompletionCache - Caching-Verhalten
   - XmlCodeFoldingManager - Folding-Logik

**Geschätzte Zeit:** 9-12 Stunden
**ROI:** ⭐⭐⭐⭐⭐ Kernfeature
**Siehe:** INTELLISENSE_TEST_EXAMPLES.md für Vorlagen

---

### 5. Fehlende V2 Model Tests (6 Klassen) ⚠️

**Grundlegende XSD-Node-Typen ohne Tests:**

```java
❌ XsdElement.java - Häufigster Node-Typ ⚠️ PRIORITÄT
❌ XsdAttribute.java - Attribut-Deklarationen ⚠️ PRIORITÄT
❌ XsdSequence.java - Häufigster Compositor ⚠️ PRIORITÄT
❌ XsdChoice.java - Compositor
❌ XsdAll.java - Compositor
❌ XsdNode.java - Basis-Klasse (abstrakt, aber testbar)
```

**Pro Klasse benötigt:**
- Properties (name, type, minOccurs, maxOccurs, etc.)
- PropertyChangeEvent-Firing
- Deep Copy mit Suffix
- Parent-Child-Beziehungen
- Documentation und Appinfo
- Node-Type-Verifikation

**Geschätzte Zeit:** 4-5 Stunden
**ROI:** ⭐⭐⭐⭐
**Siehe:** XsdElementTest in PROPOSED_TESTS.md als Vorlage

---

### 6. Domain Commands (REMOVED - V1 Architecture)

**Status:** ❌ Deleted in commit 8109fd3 (V1 XSD editor removal)

All 12 Domain Command implementations were removed as part of V1 architecture deprecation.
V2 architecture uses commands in `controls/v2/editor/commands/` instead (24 commands).

**No action needed** - V1 commands are deprecated.

---

## 🟡 PRIORITY 3: MITTEL (Zukünftige Verbesserungen)

### 7. V2 Editor UI Components (0% Coverage)

**Panels (2 Dateien):**
- ❌ XsdPropertiesPanel.java

**Tabs (4 Dateien):**
- ❌ AbstractTypeEditorTab.java
- ❌ ComplexTypeEditorTab.java
- ❌ SimpleTypeEditorTab.java
- ❌ SimpleTypesListTab.java

**Views (3 Dateien):**
- ❌ ComplexTypeEditorView.java
- ❌ SimpleTypeEditorView.java
- ❌ SimpleTypesListView.java

**Menu (1 Datei):**
- ❌ XsdContextMenuFactory.java

**Test-Ansatz:** TestFX UI-Tests
**Geschätzte Zeit:** 8-12 Stunden

---

### 8. Service Layer - Restliche Lücken

**Fehlende Service-Tests:**
```java
❌ ConnectionService - Netzwerk-Konnektivität
❌ PropertiesService - App-Einstellungen
❌ XsdLiveValidationService - Live-Validierung
```

**Geschätzte Zeit:** 6-8 Stunden

---

### 9. V2 View/Rendering (3 von 4 untested)

```java
controls/v2/view/
├── ✅ XsdModelViewSyncTest (existiert)
├── ❌ XsdGraphView
├── ❌ XsdNodeRenderer
└── ❌ XsdNodeStyler
```

**Test-Ansatz:** Mock GraphicsContext, Rendering-Calls verifizieren
**Geschätzte Zeit:** 4-6 Stunden

---

### 10. Controls/Editor Managers (4 von 6 untested)

```java
controls/editor/
├── ✅ FindReplaceDialog (tested)
├── ✅ StatusLineController (tested)
├── ❌ FileOperationsManager
├── ❌ SyntaxHighlightManager
├── ❌ XmlContextMenuManager
└── ❌ XmlValidationManager
```

**Geschätzte Zeit:** 4-6 Stunden

---

### 11. V1 Legacy Commands (31 von 33 untested - 6% Coverage)

**Nur 2 Tests vorhanden:**
- ✅ AddAssertionToParentComplexTypeTest
- ✅ AddSimpleTypeAssertionCommandTest
- ❌ 31 weitere Command-Dateien

**Entscheidung notwendig:**
- Wenn V1 deprecated → Dokumentieren als Legacy, Tests überspringen
- Wenn V1 noch in Verwendung → Tests hinzufügen (15-20h)

**Geschätzte Zeit:** 15-20 Stunden (wenn noch relevant)

---

### 12. Controller/Controls Subdirectories (0% Coverage)

```java
controller/controls/
├── ❌ FavoritesPanelController.java
├── ❌ XmlEditorSidebarController.java
└── ❌ SearchReplaceController.java
```

**Geschätzte Zeit:** 3-4 Stunden

---

## 📊 Zusammenfassung nach Priorität

### PRIORITY 1 - KRITISCH (26-40 Stunden):
| Bereich | Tests fehlen | ROI | Zeit |
|---------|--------------|-----|------|
| V2 Editor Core (CommandManager, Context, Selection) | 3 Klassen | ⭐⭐⭐⭐⭐ | 12h |
| Controller Layer (MainController, XmlUltimateController, etc.) | 12 Controller | ⭐⭐⭐⭐ | 12-24h |
| FacetsPanel | 1 Klasse | ⭐⭐⭐⭐⭐ | 4h |

### PRIORITY 2 - HOCH (13-17 Stunden):
| Bereich | Tests fehlen | ROI | Zeit |
|---------|--------------|-----|------|
| IntelliSense System | 26 Dateien | ⭐⭐⭐⭐⭐ | 9-12h |
| V2 Model Tests (Element, Attribute, Sequence) | 6 Klassen | ⭐⭐⭐⭐ | 4-5h |
| ~~Domain Commands~~ | ~~12 Commands~~ | ~~Deprecated~~ | ~~N/A~~ |

### PRIORITY 3 - MITTEL (35-56 Stunden):
| Bereich | Tests fehlen | ROI | Zeit |
|---------|--------------|-----|------|
| V2 UI Components | 10 Dateien | ⭐⭐⭐ | 8-12h |
| Service Layer Lücken | 3 Services | ⭐⭐⭐ | 6-8h |
| V2 Rendering | 3 Dateien | ⭐⭐⭐ | 4-6h |
| Editor Managers | 4 Dateien | ⭐⭐⭐ | 4-6h |
| V1 Commands (falls relevant) | 31 Dateien | ⭐⭐ | 15-20h |
| Controller Subdirectories | 3 Dateien | ⭐⭐ | 3-4h |

---

## 🎯 Empfohlene Reihenfolge

### Sprint 1: V2 Core (1 Woche)
1. ✅ CommandManagerTest (3h)
2. ✅ XsdEditorContextTest (2h)
3. ✅ SelectionModelTest (3h)
4. ✅ FacetsPanelTest (4h)

**Total: ~12h | Impact: Schützt V2 Editor Core**

### Sprint 2: Critical Controllers (1 Woche)
5. ✅ MainControllerTest (4h)
6. ✅ XmlUltimateControllerTest (4h)
7. ✅ SchematronControllerTest (2h)
8. ✅ XsltControllerTest (2h)

**Total: ~12h | Impact: Schützt Hauptfunktionalität**

### Sprint 3: IntelliSense (1-2 Wochen)
9. ✅ FuzzySearchTest (2h)
10. ✅ CompletionContextTest (2h)
11. ✅ NamespaceResolverTest (2h)
12. ✅ MultiSchemaManagerTest (3h)
13. ✅ Weitere IntelliSense-Tests (3h)

**Total: ~12h | Impact: Schützt Auto-Completion**

### Sprint 4: Model Tests (1 Woche)
14. ✅ XsdElementTest (2h)
15. ✅ XsdAttributeTest (1h)
16. ✅ XsdSequenceTest (1h)
17. ~~Domain Command Tests~~ (Deprecated - V1 removed)

**Total: ~4h | Impact: Schützt XSD-Modell**

---

## 🚀 Quick Wins (Schnelle Erfolge)

Wenn wenig Zeit verfügbar:

1. **CommandManager + XsdEditorContext + SelectionModel** (8h)
   - Höchste Priorität, schützt gesamte V2-Architektur
   - Vorlagen in PROPOSED_TESTS.md vorhanden

2. **XsdElement + XsdAttribute + XsdSequence** (4h)
   - Grundlegende Model-Typen
   - Vorlage in PROPOSED_TESTS.md

3. **FuzzySearch + CompletionContext** (4h)
   - Core IntelliSense-Algorithmen
   - Vorlagen in INTELLISENSE_TEST_EXAMPLES.md

**Total: 16h für maximalen Impact**

---

## 📈 Aktueller Stand

**Test-Dateien:** 155
**Geschätzt fehlend:** ~100+ kritische Tests

**Nach Priority 1+2:**
- Neue Tests: ~80
- Coverage: 47% → 70%+
- Zeit: 45-66 Stunden

**Nach Priority 1+2+3:**
- Neue Tests: ~150+
- Coverage: 47% → 80%+
- Zeit: 80-122 Stunden

---

## 💡 Nächste Schritte

**Sofort starten mit:**
1. `CommandManagerTest` (PROPOSED_TESTS.md)
2. `XsdEditorContextTest` (PROPOSED_TESTS.md)
3. `SelectionModelTest` (PROPOSED_TESTS.md)

**Dann:**
4. `FacetsPanelTest` (TestFX)
5. `MainControllerTest` (TestFX)

Diese 5 Tests schützen das Herzstück der Anwendung.
