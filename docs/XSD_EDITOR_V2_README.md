# XSD Editor V2 - Dokumentation

Willkommen zur Dokumentation für die Neuimplementierung des grafischen XSD-Editors!

## 📚 Übersicht

Diese Dokumentation beschreibt die komplette Neuimplementierung des XSD-Editors (Version 2) mit Fokus auf:

- **Inkrementelle Updates**: Grafische Darstellung wird nicht mehr komplett neu aufgebaut, sondern nur geänderte Teile werden aktualisiert
- **Globale Type-Verwaltung**: Simple und Complex Types können grafisch erstellt, angezeigt und bearbeitet werden
- **Erweiterte Interaktivität**: Inline-Editing, Enhanced Drag & Drop, Schema-Erstellung von Grund auf
- **XMLSpy-ähnliche Funktionalität**: Annäherung an die Funktionalität von Altova XMLSpy 2026 Professional

## 📖 Dokumente

### 1. [XSD_EDITOR_V2_PLAN.md](./XSD_EDITOR_V2_PLAN.md)
**Der Masterplan** - Vollständige Beschreibung des Projekts

**Inhalt:**
- Executive Summary
- **Kapitel 1**: Detaillierte Featureliste der aktuellen Implementierung (V1)
  - Grafische Darstellung (Knoten-Typen, Visuelle Features, Styling)
  - Such- und Filter-Funktionalität
  - Interaktive Bearbeitungsfunktionen (40+ Features)
  - Undo/Redo System
  - Drag & Drop System
  - Live-Validierung
  - XSD 1.0 und 1.1 Unterstützung
  - Unterstützende Komponenten
- **Kapitel 2**: Identifizierte Probleme der aktuellen Implementierung
- **Kapitel 3**: XML Spy 2026 Professional - Feature-Vergleich
- **Kapitel 4**: Anforderungen für XSD Editor V2 (Must-Have, Should-Have, Nice-to-Have)
- **Kapitel 5**: Technische Architektur V2 (Model-View-Separation, Update-Mechanismus)
- **Kapitel 6**: Implementierungs-Phasen (5 Phasen, 14 Milestones)
- **Kapitel 7**: Testing-Strategie
- **Kapitel 8**: Risiken & Mitigation
- **Kapitel 9**: Success-Kriterien
- **Kapitel 10**: Nächste Schritte
- **Kapitel 11**: Anhang (Code-Beispiele, Referenzen, Glossar)

**Verwendung:**
- Für strategische Entscheidungen
- Für Architektur-Diskussionen
- Für Verständnis des "Warum" und "Was"
- Als Referenz für Stakeholder

---

### 2. [XSD_EDITOR_V2_ROADMAP.md](./XSD_EDITOR_V2_ROADMAP.md)
**Die Implementierungs-Checkliste** - Schritt-für-Schritt-Anleitung

**Inhalt:**
- **Phase 1**: Foundation & Architecture (3 Milestones)
  - Model-Layer
  - Basic View-Layer
  - Integration in App
- **Phase 2**: Incremental Updates & Type-Library (3 Milestones)
  - Incremental Update Mechanism
  - Type-Library-Panel
  - Type-Editing
- **Phase 3**: Interactive Editing Features (3 Milestones)
  - Enhanced Drag & Drop
  - Inline-Editing
  - Create Schema from Scratch
- **Phase 4**: Advanced Features & Polish (3 Milestones)
  - Visual Enhancements (Minimap, Breadcrumbs, Animations)
  - Schema Templates
  - Testing & Stabilization
- **Phase 5**: Beta-Testing & Release (2 Milestones)
  - Beta-Release
  - Final Release

**Besonderheit:**
- Jedes Deliverable hat eine Checkbox [ ]
- Kann direkt in deinem Editor als TODO-Liste verwendet werden
- Definiert klare "Definition of Done" für jeden Milestone
- Enthält Quick-Start-Guide für sofortigen Start

**Verwendung:**
- Für tägliche Entwicklungsarbeit
- Zum Tracken des Fortschritts
- Als Checkliste beim Code-Review
- Für Sprint-Planning

---

## 🎯 Kernziele der V2-Implementierung

### 1. State-Preservation bei Änderungen
**Problem in V1:** Bei jeder Property-Änderung wird die gesamte Grafik neu aufgebaut, alle aufgeklappten Knoten werden wieder zugeklappt.

**Lösung in V2:**
- Model-View-Separation mit Change-Detection
- Nur geänderte Knoten werden visuell aktualisiert
- Expansion-States, Selection, Scroll-Position bleiben erhalten

### 2. Globale Type-Verwaltung
**Problem in V1:** Globale Simple und Complex Types werden nicht grafisch dargestellt, nur Elements werden visualisiert.

**Lösung in V2:**
- Type-Library-Panel zeigt alle globalen Types
- Types sind grafisch darstellbar und editierbar wie Elements
- Type-Hierarchien werden visualisiert

### 3. Schema-Erstellung von Grund auf
**Problem in V1:** Neues Schema muss im Text-Editor begonnen werden, grafischer Editor kann nur existierende Schemas bearbeiten.

**Lösung in V2:**
- "Create New Schema" Dialog mit Wizard
- Leeres Schema-Template
- Erste Elemente können direkt grafisch hinzugefügt werden

### 4. Erweiterte Interaktivität
**Problem in V1:** Meiste Operationen erfordern Dialoge, kein Inline-Editing, limitiertes Drag & Drop.

**Lösung in V2:**
- Inline-Editing für Namen, Types, Kardinalitäten
- Enhanced Drag & Drop (Copy vs. Move, Type-Assignment)
- Quick-Actions für häufige Operationen

---

## 🚀 Schnellstart für Entwickler

### Schritt 1: Dokumentation lesen
```
1. XSD_EDITOR_V2_PLAN.md durchlesen (Kapitel 1-6 sind wichtig)
2. XSD_EDITOR_V2_ROADMAP.md Phase 1 im Detail durchgehen
```

### Schritt 2: Branch erstellen
```bash
git checkout -b feature/xsd-editor-v2
```

### Schritt 3: Package-Struktur anlegen
```bash
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/model
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/view
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/controller
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/rendering
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/commands
mkdir -p src/main/java/org/fxt/freexmltoolkit/controls/v2/dialogs

mkdir -p src/test/java/org/fxt/freexmltoolkit/controls/v2
mkdir -p src/test/java/org/fxt/freexmltoolkit/controls/v2/model
```

### Schritt 4: Start mit Phase 1, Milestone 1.1
Siehe [XSD_EDITOR_V2_ROADMAP.md](./XSD_EDITOR_V2_ROADMAP.md) für Details.

**Erste Aufgabe:** Erstelle `XsdSchemaModel.java`
```java
package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Root model representing an XSD Schema.
 * Supports XSD 1.0 and 1.1.
 */
public class XsdSchemaModel {
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private String version = "1.0"; // "1.0" or "1.1"
    private String targetNamespace;
    private String elementFormDefault = "qualified";
    private String attributeFormDefault = "unqualified";

    private final List<XsdElementModel> globalElements = new ArrayList<>();
    private final List<XsdComplexTypeModel> globalComplexTypes = new ArrayList<>();
    private final List<XsdSimpleTypeModel> globalSimpleTypes = new ArrayList<>();

    // TODO: Implement getters, setters with property change support
    // TODO: Implement add/remove methods for global components
}
```

---

## 📊 Projekt-Status

**Aktueller Status:** Planung abgeschlossen, bereit für Implementierung
**Nächster Milestone:** Phase 1, Milestone 1.1 (Model-Layer)
**Geschätzter Zeitaufwand:** 14-16 Wochen (5 Phasen)

### Phasen-Übersicht
```
✅ Phase 0: Planung & Analyse (abgeschlossen)
⏳ Phase 1: Foundation & Architecture (Wochen 1-3)
⏳ Phase 2: Incremental Updates & Type-Library (Wochen 4-7)
⏳ Phase 3: Interactive Editing Features (Wochen 8-11)
⏳ Phase 4: Advanced Features & Polish (Wochen 12-14)
⏳ Phase 5: Beta-Testing & Release (Wochen 15-16)
```

---

## 🛠️ Technologie-Stack

- **Programmiersprache:** Java 24 (mit Preview-Features)
- **UI-Framework:** JavaFX 24
- **Testing:** JUnit 5, TestFX, Mockito
- **XSD-Parsing:** Xerces (bereits verwendet in V1)
- **Build-System:** Gradle
- **Version-Control:** Git

---

## 📝 Konventionen

### Git-Commits
```
feat(v2-model): Add XsdSchemaModel with property change support
fix(v2-view): Fix node rendering for complex types
test(v2-model): Add tests for XsdModelDiffer
docs(v2): Update roadmap progress
```

### Code-Style
- Folge den existierenden Code-Conventions des Projekts
- Javadoc für alle Public-APIs
- Unit-Tests für alle Business-Logic
- Integration-Tests für komplexe Workflows

### Testing
- **Unit-Tests:** Minimum 80% Coverage
- **Integration-Tests:** Alle kritischen Workflows
- **UI-Tests:** Visual Regression Tests mit TestFX
- **Performance-Tests:** Update-Time < 16ms, Load-Time < 2s

---

## 🤝 Beitragen

### Code-Review-Prozess
1. Implementiere Feature/Milestone komplett
2. Schreibe Tests (Unit + Integration)
3. Update Roadmap (Checkboxen abhaken)
4. Erstelle Pull-Request mit Referenz zum Milestone
5. Code-Review durch Maintainer
6. Merge nach erfolgreichem Review und grünen Tests

### Fragen & Diskussionen
- **Technische Fragen:** Erstelle GitHub Issue mit Label `v2-question`
- **Feature-Requests:** Erstelle GitHub Issue mit Label `v2-enhancement`
- **Bug-Reports:** Erstelle GitHub Issue mit Label `v2-bug`

---

## 📚 Weiterführende Ressourcen

### XSD-Spezifikationen
- [XML Schema 1.0 Part 1: Structures](https://www.w3.org/TR/xmlschema-1/)
- [XML Schema 1.0 Part 2: Datatypes](https://www.w3.org/TR/xmlschema-2/)
- [XML Schema 1.1 Part 1: Structures](https://www.w3.org/TR/xmlschema11-1/)
- [XML Schema 1.1 Part 2: Datatypes](https://www.w3.org/TR/xmlschema11-2/)

### JavaFX
- [JavaFX 24 Documentation](https://openjfx.io/javadoc/24/)
- [JavaFX CSS Reference Guide](https://openjfx.io/javadoc/24/javafx.graphics/javafx/scene/doc-files/cssref.html)

### Testing
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [TestFX Documentation](https://github.com/TestFX/TestFX)

### Design-Inspiration
- [Altova XMLSpy XSD Editor](https://www.altova.com/de/xmlspy-xml-editor/xsd-editor)

---

## 📞 Kontakt

**Projekt-Maintainer:** [Dein Name/Team]
**Email:** [Email]
**GitHub:** [Repository-URL]

---

**Letzte Aktualisierung:** 2025-11-05
**Version:** 1.0
**Status:** Ready for Development 🚀
