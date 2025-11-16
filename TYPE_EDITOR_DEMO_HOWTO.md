# Type Editor Dummy UI - Demo starten

**Phase 0** - Dummy UI Visualisierung mit Dummy-Daten

---

## 🚀 Wie starte ich die Demo?

### Option 1: In IntelliJ IDEA (empfohlen)

1. **Öffne das Projekt** in IntelliJ IDEA
2. **Navigate zu:**
   ```
   src/main/java/org/fxt/freexmltoolkit/demo/TypeEditorDummyDemo.java
   ```
3. **Rechtsklick auf die Klasse** → "Run 'TypeEditorDummyDemo.main()'"
4. **Fertig!** Die Demo-Applikation startet

### Option 2: Via Gradle

```bash
./gradlew run --args="org.fxt.freexmltoolkit.demo.TypeEditorDummyDemo"
```

### Option 3: Kommandozeile (nach Build)

```bash
# Build
./gradlew build

# Run
java -cp build/classes/java/main org.fxt.freexmltoolkit.demo.TypeEditorDummyDemo
```

---

## 🎮 Wie benutze ich die Demo?

### Beim Start:

Die Demo öffnet sich mit einem **Welcome Tab** und einer **Control Panel** am unteren Rand.

### Control Panel Buttons:

| Button | Beschreibung | Öffnet |
|--------|--------------|--------|
| 📦 ComplexType: AddressType | Öffnet ComplexType Editor | Tab mit grafischem Editor (3-Spalten-Layout) |
| 📦 ComplexType: AmountType | Öffnet weiteren ComplexType | Zweiter Tab zum Testen von Multi-Tab |
| 📄 SimpleType: ISINType | Öffnet SimpleType Editor | Tab mit 5 Sub-Panels |
| 📄 SimpleType: EmailAddressType | Öffnet weiteren SimpleType | Zweiter Tab zum Testen |
| 📋 SimpleTypes List | Öffnet SimpleTypes Übersicht | Tab mit TableView aller Types |

### Features in der Demo:

#### 1. ComplexType Editor Tab
- ✅ **Toolbar** oben (Save, Undo, Redo, Find Usage - alle disabled)
- ✅ **3-Spalten-Layout:**
  - Links: TreeView mit Type als Root (📦 AddressType ⭐)
  - Mitte: Canvas Placeholder mit ASCII-Art
  - Rechts: Properties Panel
- ✅ **Mock-Daten:** Sequence mit 3 Dummy-Elementen

#### 2. SimpleType Editor Tab
- ✅ **Toolbar** oben (Save, Close, Find Usage - alle disabled)
- ✅ **5 Tabs:**
  1. **General** - Name, Final Checkboxes
  2. **Restriction** - Base Type + Facets Placeholder
  3. **List** - ItemType Selector (disabled)
  4. **Union** - MemberTypes List (disabled)
  5. **Annotation** - Documentation + AppInfo TextAreas
- ✅ **Alle Panels** vorhanden und visualisiert

#### 3. SimpleTypes List Tab
- ✅ **Filter Bar** - Search + Sort (disabled)
- ✅ **TableView** mit 14 Dummy SimpleTypes:
  - BicCodeType, EmailAddressType, ISINType, etc.
  - Spalten: Name, Base Type, Facets, Usage Count, Actions
- ✅ **Preview Panel** - Zeigt XSD Preview bei Selection
- ✅ **Action Toolbar** - Edit, Duplicate, Find Usage, Delete (alle disabled)

### Tab-Management:

- **Mehrere Tabs** können gleichzeitig geöffnet sein
- **Tab-Closing** funktioniert (✕ Button)
- **Tab-Switching** durch Klick auf Tab-Header

---

## 🎨 Was sehe ich in der Demo?

### Layout-Visualisierung:

Die Demo zeigt die **vollständige UI-Struktur** wie sie in Phase 1+ implementiert wird:

1. ✅ **Tab-System** mit verschiedenen Tab-Typen
2. ✅ **ComplexType Editor Layout** (Tree | Canvas | Properties)
3. ✅ **SimpleType Editor Layout** (5 Tabs mit Panels)
4. ✅ **SimpleTypes List Layout** (Table + Preview)
5. ✅ **Toolbar-Struktur** in allen Editoren
6. ✅ **Placeholder-Content** zur Visualisierung

### Dummy-Daten:

#### ComplexTypes (Beispiele):
- **AddressType** - Wie in FundsXML4.xsd
- **AmountType** - Wie in FundsXML4.xsd

#### SimpleTypes (14 Beispiele in Liste):
- BicCodeType (xs:string, minL/maxL)
- EmailAddressType (xs:string, pattern)
- ISINType (xs:string, length/pattern)
- ISOCountryCodeType (xs:string, minL/maxL)
- ISOCurrencyCodeType (xs:string, pattern)
- Text256Type (xs:string, maxLength)
- ... und mehr

---

## ⚠️ Was funktioniert NICHT in der Demo?

**Alle Interaktionen sind deaktiviert** - dies ist nur eine Visualisierung!

❌ **Nicht funktional:**
- Buttons (Save, Undo, Redo, etc.)
- Context Menus
- Datenbearbeitung
- Command-System (Undo/Redo)
- Speichern
- Model-Updates
- Echte Type-Daten aus Schema

✅ **Funktional:**
- Tabs öffnen/schließen
- Tab-Switching
- Layout-Visualisierung
- Dummy-Daten anzeigen
- SimpleTypes List Selection → Preview Update

---

## 🎯 Zweck der Demo

### Für User:
- ✅ **UI-Layout prüfen** - Entspricht es den Mockups?
- ✅ **Tab-Struktur testen** - Ist die Aufteilung verständlich?
- ✅ **Multi-Tab erleben** - Mehrere Types gleichzeitig geöffnet
- ✅ **Feedback geben** - Änderungswünsche vor Phase 1

### Für Entwickler:
- ✅ **UI-Struktur etabliert** - Klassen-Hierarchie steht
- ✅ **Layout-Templates** - Alle Panels definiert
- ✅ **Integration Points** - TODOs markiert für Phase 1+
- ✅ **Code-Basis** - Bereit für echte Implementierung

---

## 📸 Screenshots der Demo

### Beim Start:
```
┌────────────────────────────────────────────────────────┐
│ 🎨 XSD Type Editor - DUMMY UI DEMO (Phase 0)          │
│ This is a visualization of the Type Editor UI...       │
├────────────────────────────────────────────────────────┤
│ [Welcome Tab (active)]                                 │
│                                                        │
│        Welcome to Type Editor Dummy UI                 │
│        Phase 0 - UI Structure Visualization            │
│                                                        │
│        Use the buttons at the bottom to open tabs...   │
│                                                        │
├────────────────────────────────────────────────────────┤
│ Open Tab: [📦 ComplexType: AddressType]               │
│          [📦 ComplexType: AmountType]                  │
│          [📄 SimpleType: ISINType]                     │
│          [📄 SimpleType: EmailAddressType]            │
│          [📋 SimpleTypes List]                         │
└────────────────────────────────────────────────────────┘
```

### Nach Öffnen von ComplexType:
```
┌────────────────────────────────────────────────────────┐
│ [Welcome] [ComplexType: AddressType ×]                 │
├────────────────────────────────────────────────────────┤
│ [💾 Save] [↶ Undo] [↷ Redo] [🔍 Find Usage]           │
├──────────────┬───────────────────┬─────────────────────┤
│ 📦 Address ⭐ │  ┌──────────────┐ │ Type Properties:    │
│  └ 📋 seq    │  │ AddressType  │ │ Name: [AddressType] │
│    ├─📄 El1  │  └──────┬───────┘ │ Abstract: ☐         │
│    ├─📄 El2  │         │         │ Mixed: ☐            │
│    └─📄 El3  │    ┌────▼─────┐   │                     │
│              │    │ Elements │   │ (Dummy Panel)       │
│              │    └──────────┘   │                     │
└──────────────┴───────────────────┴─────────────────────┘
```

### Nach Öffnen von SimpleTypes List:
```
┌────────────────────────────────────────────────────────┐
│ [Welcome] [SimpleTypes List ×]                         │
├────────────────────────────────────────────────────────┤
│ SimpleTypes Overview              [+ Add SimpleType]   │
├────────────────────────────────────────────────────────┤
│ 🔍 [Filter...] Sort by: [Name ▼]                       │
├────────────────────────────────────────────────────────┤
│ Name            │ Base    │ Facets      │ Usage │ Acti.│
│ 📄 BicCodeType  │ string  │ minL, maxL  │  12   │ [Ed] │
│ 📄 ISINType     │ string  │ len, patt   │ 156   │ [Ed] │
│ ...                                                     │
├────────────────────────────────────────────────────────┤
│ Preview (XSD):                                         │
│ <xs:simpleType name="ISINType">                        │
│   <xs:restriction base="xs:string">                    │
│     <!-- Facets: length, pattern -->                   │
│   </xs:restriction>                                    │
│ </xs:simpleType>                                       │
└────────────────────────────────────────────────────────┘
```

---

## ✅ Checklist: Demo Review

**Bitte prüfen:**

- [ ] Demo startet ohne Fehler
- [ ] Welcome Tab wird angezeigt
- [ ] Control Panel Buttons funktionieren
- [ ] ComplexType Tab öffnet mit 3-Spalten-Layout
- [ ] SimpleType Tab öffnet mit 5 Sub-Tabs
- [ ] SimpleTypes List Tab zeigt Tabelle mit 14 Types
- [ ] Mehrere Tabs können gleichzeitig geöffnet sein
- [ ] Tabs können geschlossen werden (✕)
- [ ] Layout entspricht den Mockups
- [ ] Preview Panel funktioniert in SimpleTypes List

**Feedback:**
- [ ] UI-Layout OK? Änderungswünsche?
- [ ] Tab-Struktur verständlich?
- [ ] Bereit für Phase 1 (echte Implementierung)?

---

## 🐛 Troubleshooting

### Problem: Demo startet nicht

**Lösung 1:** Prüfe JavaFX
```bash
# JavaFX sollte im Classpath sein (Liberica Full JDK)
java --list-modules | grep javafx
```

**Lösung 2:** Build zuerst
```bash
./gradlew clean build
```

### Problem: "Cannot find symbol" Fehler

**Ursache:** Model-Klassen (XsdComplexType, XsdSimpleType) nicht gefunden

**Lösung:** Stelle sicher, dass das Projekt vollständig gebaut ist
```bash
./gradlew compileJava
```

### Problem: UI sieht anders aus

**Ursache:** Styling oder JavaFX Version

**Lösung:** Demo verwendet inline Styles, sollte plattformunabhängig sein

---

## 📝 Notizen für Phase 1

**Nach Demo-Review:**
1. User-Feedback sammeln
2. Eventuell UI-Anpassungen vornehmen
3. Dann starten mit Phase 1:
   ```bash
   git checkout -b feature/type-editor-phase-1
   ```

**Erste Tasks in Phase 1:**
- TypeEditorTabManager funktional machen
- Tab-Lifecycle mit Unsaved Changes
- Schema Tree erweitern (Types-Node)
- Doppelklick-Handler

---

**Demo bereit!** 🎉

Starte mit: `Run TypeEditorDummyDemo.main()`
