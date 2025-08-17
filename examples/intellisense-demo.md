# IntelliSense Demo für XML Editor

## Übersicht

Der XML Editor in FreeXMLToolkit bietet jetzt eine vollständige IntelliSense-Funktionalität mit automatischer
Vervollständigung von XML-Elementnamen.

## Funktionen

### 1. IntelliSense-Trigger mit "<"

- **Aktion**: Tippen Sie das Zeichen "<" im XML Editor
- **Ergebnis**: Ein Popup-Fenster erscheint mit einer Liste verfügbarer Elementnamen
- **Navigation**: Verwenden Sie die Pfeiltasten ↑/↓ um durch die Liste zu navigieren
- **Auswahl**: Drücken Sie ENTER um ein Element auszuwählen

### 2. Automatisches Schließen von Tags mit ">"

- **Aktion**: Tippen Sie das Zeichen ">" nach einem Elementnamen
- **Ergebnis**: Das schließende Tag wird automatisch eingefügt
- **Beispiel**: `<root>` wird zu `<root></root>` mit dem Cursor zwischen den Tags

### 3. XSD-Integration

- **Automatische Erkennung**: Elementnamen werden automatisch aus dem geladenen XSD-Schema extrahiert
- **Fallback**: Wenn kein XSD geladen ist, werden Standard-Elementnamen verwendet

## Verwendung

### Schritt 1: XSD-Schema laden

1. Öffnen Sie den XML Editor
2. Klicken Sie auf "..." in der XSD Schema Sektion
3. Wählen Sie eine XSD-Datei aus
4. Die Elementnamen werden automatisch für IntelliSense extrahiert

### Schritt 2: IntelliSense verwenden

1. Positionieren Sie den Cursor an der gewünschten Stelle
2. Tippen Sie "<"
3. Wählen Sie ein Element aus der Popup-Liste
4. Tippen Sie ">" um das Tag automatisch zu schließen

### Schritt 3: Navigation

- **↑/↓**: Durch die Liste navigieren
- **ENTER**: Element auswählen
- **ESC**: Popup schließen ohne Auswahl

## Beispiel-Workflow

```
1. Tippen Sie: <
   → Popup erscheint mit verfügbaren Elementnamen

2. Navigieren Sie zu "root" mit ↑/↓
   → "root" wird hervorgehoben

3. Drücken Sie ENTER
   → "<root" wird eingefügt

4. Tippen Sie: >
   → "<root></root>" wird erstellt, Cursor zwischen Tags

5. Tippen Sie: <
   → Neues Popup für verschachtelte Elemente

6. Wählen Sie "element"
   → "<root><element></element></root>"
```

## Unterstützte Tastenkombinationen

| Taste   | Aktion                    |
|---------|---------------------------|
| `<`     | IntelliSense-Popup öffnen |
| `>`     | Tag automatisch schließen |
| `↑/↓`   | In Popup navigieren       |
| `ENTER` | Element auswählen         |
| `ESC`   | Popup schließen           |
| `TAB`   | Standard Tab-Verhalten    |

## Technische Details

### Elementnamen-Extraktion

- **XSD-Parsing**: Automatische Extraktion von `<xs:element name="...">` Definitionen
- **Namespace-Support**: Unterstützung für verschiedene XML-Namespaces
- **Fallback-Liste**: Standard-Elementnamen wenn kein XSD verfügbar

### Popup-Positionierung

- **Cursor-basiert**: Popup erscheint an der aktuellen Cursor-Position
- **Screen-Koordinaten**: Korrekte Positionierung auf dem Bildschirm
- **Auto-Hide**: Popup verschwindet automatisch bei Auswahl oder ESC

### Auto-Closing Logic

- **Pattern-Matching**: Erkennung von öffnenden Tags
- **Self-Closing Support**: Keine Auto-Closing für `<br>`, `<img>`, etc.
- **Cursor-Positioning**: Cursor wird korrekt zwischen Tags positioniert

## Fehlerbehebung

### Popup erscheint nicht

- Stellen Sie sicher, dass ein XSD-Schema geladen ist
- Überprüfen Sie, ob das "<" Zeichen korrekt eingegeben wurde
- Prüfen Sie die Konsolen-Ausgabe auf Fehlermeldungen

### Falsche Elementnamen

- Laden Sie das korrekte XSD-Schema neu
- Überprüfen Sie die XSD-Datei auf gültige Element-Definitionen
- Verwenden Sie die Fallback-Liste für Standard-Elemente

### Auto-Closing funktioniert nicht

- Stellen Sie sicher, dass Sie ">" nach einem gültigen Elementnamen tippen
- Überprüfen Sie, ob das Element nicht als self-closing definiert ist
- Prüfen Sie die Konsolen-Ausgabe auf Fehlermeldungen

## Erweiterte Funktionen

### LSP-Integration (geplant)

- **Vollständige LSP-Unterstützung**: Integration mit Language Server Protocol
- **Erweiterte Vervollständigung**: Attribute, Namespaces, etc.
- **Real-time Updates**: Dynamische Aktualisierung basierend auf Kontext

### Schema-basierte Validierung

- **Kontext-spezifische Vorschläge**: Nur gültige Elemente für aktuelle Position
- **Attribute-Vervollständigung**: Automatische Vervollständigung von Attributen
- **Namespace-Support**: Vollständige Namespace-Unterstützung
