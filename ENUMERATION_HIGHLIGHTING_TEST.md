# Enumeration Highlighting Test

## Problem

Die XML-Knoten mit Enumeration-Werten werden nicht farblich hervorgehoben im XmlCodeEditor.

**Zusätzliches Problem:** Alle Knoten mit demselben Namen werden hervorgehoben, auch wenn sie in unterschiedlichen
Kontexten unterschiedliche Eigenschaften haben.

**Performance-Problem:** Das Laden der Enumeration-Werte dauert extrem lange.

## Lösung

Die folgenden Änderungen wurden implementiert:

### 1. CSS-Stylesheets werden jetzt korrekt geladen

- `loadCssStylesheets()` Methode hinzugefügt
- CSS-Dateien werden zur CodeArea hinzugefügt
- `.enumeration-content` CSS-Klasse definiert

### 2. Kontext-bewusste Enumeration-Erkennung

- **Neue Struktur:** `Map<String, Set<String>> enumerationElementsByContext`
- **Kontext-Erkennung:** Elemente werden nur hervorgehoben, wenn sie im richtigen Kontext sind
- **XPath-ähnliche Kontexte:** `/root`, `/UserContext`, `/SystemContext`, etc.
- Verbesserte Overlay-Logik für Syntax-Hervorhebung
- Debug-Logging für bessere Fehlerdiagnose

### 3. Performance-Optimierungen (NEU)

- **XsdDocumentationData-basiert:** Verwendet bereits geparste XSD-Daten statt erneutes Parsing
- **Background-Thread-Syntax-Highlighting:** Syntax-Hervorhebung läuft im Hintergrund-Thread
- **Debouncing:** Syntax-Hervorhebung wird nur alle 300ms ausgeführt
- **Intelligentes Caching:** Cache wird nur bei XSD-Änderungen aktualisiert
- **Optimierte Kontext-Erkennung:** Charakter-basierte statt Regex-basierte Parsing
- **Reduzierte Log-Ausgaben:** Debug-Logs nur bei Bedarf

### 4. CSS-Definitionen

```css
.enumeration-content {
    -fx-fill: #ff0000;           /* Rote Schrift */
    -fx-font-weight: bold;       /* Fett */
    -fx-background-color: #ffff00; /* Gelber Hintergrund */
}
```

## Testen der Funktionalität

### Schritt 1: Einfacher Test

1. Öffne `test_enumeration.xsd` als XSD-Schema
2. Öffne `test_enumeration_highlighting.xml` als XML-Datei

### Schritt 2: Kontext-bewusster Test (NEU)

1. Öffne `test_context_aware_enumeration.xsd` als XSD-Schema
2. Öffne `test_context_aware_enumeration.xml` als XML-Datei

### Schritt 3: Performance-Test (NEU)

```java
xmlCodeEditor.testEnumerationHighlightingPerformance();
```

### Schritt 4: XsdDocumentationData-Test (NEU)

```java
xmlCodeEditor.testXsdDocumentationDataEnumerationHighlighting();
```

### Schritt 5: Erwartetes Verhalten

#### Einfacher Test:

- `DataOperation` Elemente mit Werten "INITIAL", "UPDATE", "DELETE" sollten hervorgehoben werden
- `Status` Elemente mit Werten "ACTIVE", "INACTIVE", "PENDING" sollten hervorgehoben werden
- `OtherElement` sollte normal dargestellt werden (keine Hervorhebung)

#### Kontext-bewusster Test:

- **Root-Level:**
    - `DataOperation` mit "INITIAL" → **HERVORGEHOBEN**
    - `Status` mit "ACTIVE" → **HERVORGEHOBEN**
    - `OtherElement` → **NICHT hervorgehoben**

- **UserContext:**
    - `Status` mit "ONLINE" → **HERVORGEHOBEN** (anderer Kontext!)
    - `Priority` mit "HIGH" → **HERVORGEHOBEN**
    - `DataOperation` mit "Some user operation" → **NICHT hervorgehoben** (keine Enumeration in diesem Kontext)

- **SystemContext:**
    - `DataOperation` mit "START" → **HERVORGEHOBEN** (anderer Kontext!)
    - `Status` mit "System status" → **NICHT hervorgehoben** (keine Enumeration in diesem Kontext)
    - `Priority` mit "MEDIUM" → **HERVORGEHOBEN**

### Schritt 6: Debug-Informationen

Führe die folgenden Methoden aus, um Debug-Informationen zu erhalten:

```java
xmlCodeEditor.debugCssStatus();
xmlCodeEditor.testEnumerationHighlighting();
xmlCodeEditor.testEnumerationHighlightingPerformance(); // NEU
xmlCodeEditor.testXsdDocumentationDataEnumerationHighlighting(); // NEU
```

### Schritt 7: Log-Ausgaben prüfen

Suche in den Logs nach:

- "Loaded CSS stylesheet"
- "Found enumeration element"
- "Applying enumeration style"
- "Updated enumeration elements cache"
- "Added enumeration element: X in context: Y"
- "Found element context: /path"
- "Performance test completed in X ms" (NEU)
- "Extracting enumeration elements from documentation data" (NEU)

## Technische Details

### Kontext-Erkennung

Das System erkennt den Kontext eines Elements durch:

1. **XsdDocumentationData-Parsing:** Verwendet bereits geparste XSD-Daten
2. **XML-Struktur-Analyse:** Verwendet Stack-basierte Analyse für Element-Nesting
3. **Kontext-Mapping:** Speichert Enumeration-Elemente pro Kontext

### Performance-Optimierungen

1. **XsdDocumentationData-basiert:** Kein erneutes XSD-Parsing erforderlich
2. **Background-Thread-Syntax-Highlighting:** Syntax-Hervorhebung blockiert nicht die UI
3. **Debouncing:** Syntax-Hervorhebung wird verzögert ausgeführt
4. **Intelligentes Caching:** Cache wird nur bei Änderungen aktualisiert
5. **Optimierte Parsing-Algorithmen:** Charakter-basierte statt Regex-basierte Parsing
6. **Reduzierte Log-Ausgaben:** Debug-Logs nur bei Bedarf

### Beispiel-Kontexte

```
/                    → Root-Kontext
/root               → Root-Element-Kontext  
/UserContext        → User-Kontext
/SystemContext      → System-Kontext
```

## Bekannte Probleme

- CSS-Stylesheets müssen korrekt geladen werden
- XSD-Datei muss verfügbar sein
- XsdDocumentationData muss geladen sein
- Enumeration-Elemente müssen im Cache sein
- Komplexe XPath-Ausdrücke werden vereinfacht dargestellt

## Fehlerbehebung

1. Prüfe, ob CSS-Dateien geladen wurden: `debugCssStatus()`
2. Prüfe, ob XSD-Datei verfügbar ist
3. Prüfe, ob XsdDocumentationData geladen ist
4. Prüfe, ob Enumeration-Elemente im Cache sind
5. Prüfe Log-Ausgaben für Fehlermeldungen
6. Prüfe Kontext-Erkennung in den Logs
7. Führe Performance-Test aus: `testEnumerationHighlightingPerformance()`
8. Führe XsdDocumentationData-Test aus: `testXsdDocumentationDataEnumerationHighlighting()`

## Performance-Erwartungen

- **Kleine XML-Dateien (< 1000 Zeilen):** < 50ms
- **Mittlere XML-Dateien (1000-10000 Zeilen):** < 200ms
- **Große XML-Dateien (> 10000 Zeilen):** < 500ms

Die Performance sollte jetzt **deutlich besser** sein durch:

- **Kein XSD-Parsing:** Verwendet bereits vorhandene XsdDocumentationData
- **Background-Thread-Syntax-Highlighting:** UI bleibt responsiv
- **Optimierte Algorithmen:** Effizientere Verarbeitung
