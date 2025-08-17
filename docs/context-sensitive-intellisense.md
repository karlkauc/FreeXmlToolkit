# Kontext-sensitive IntelliSense für XML Editor

## Übersicht

Der XML Editor in FreeXMLToolkit bietet jetzt eine kontext-sensitive IntelliSense-Funktionalität, die nur die
Kindelemente des aktuellen Knotens anzeigt. Dies macht die Vervollständigung präziser und benutzerfreundlicher.

## Hauptfunktionen

### 1. Kontext-sensitive Elementvorschläge

- **Intelligente Filterung**: Zeigt nur Kindelemente des aktuellen Knotens an
- **XSD-basiert**: Extrahiert automatisch Parent-Child-Beziehungen aus XSD-Schemas
- **Fallback-Mechanismus**: Verwendet allgemeine Elementnamen wenn kein spezifischer Kontext gefunden wird

### 2. ENTER-Taste für Elementauswahl

- **Direkte Auswahl**: ENTER-Taste übernimmt das hervorgehobene Element
- **Sofortige Einfügung**: Element wird sofort an der Cursor-Position eingefügt
- **Popup-Schließung**: IntelliSense-Popup schließt sich automatisch nach Auswahl

### 3. Automatisches Tag-Schließen

- **"<" Trigger**: Öffnet kontext-sensitive IntelliSense
- **">" Auto-Closing**: Schließt Tags automatisch nach Elementauswahl
- **Cursor-Positioning**: Positioniert Cursor korrekt zwischen Tags

## Technische Implementierung

### Kontext-Erkennung

```java
private String getCurrentElementContext() {
    // Analysiert XML-Struktur bis zur aktuellen Cursor-Position
    // Verwendet Stack-basierte Logik für verschachtelte Elemente
    // Gibt den aktuellen Parent-Element-Namen zurück
}
```

### XSD-Parsing für Kontext

```java
private Map<String, List<String>> extractContextElementNamesFromXsd(File xsdFile) {
    // Extrahiert complexType-Definitionen
    // Analysiert sequence, choice, all-Elemente
    // Erstellt Parent-Child-Mapping
}
```

### Kontext-sensitive Vervollständigung

```java
private List<String> getContextSpecificElements(String parentElement) {
    // Sucht Kindelemente für aktuellen Parent
    // Fallback auf allgemeine Elementnamen
    // Rückgabe kontext-spezifischer Vorschläge
}
```

## Verwendung

### Schritt 1: XSD-Schema laden

1. Öffnen Sie den XML Editor
2. Klicken Sie auf "..." in der XSD Schema Sektion
3. Wählen Sie eine XSD-Datei aus
4. Kontext-Informationen werden automatisch extrahiert

### Schritt 2: Kontext-sensitive IntelliSense verwenden

1. Positionieren Sie den Cursor an der gewünschten Stelle
2. Tippen Sie "<"
3. **Nur relevante Kindelemente** werden angezeigt
4. Navigieren Sie mit ↑/↓
5. Drücken Sie **ENTER** um das Element auszuwählen
6. Tippen Sie ">" um das Tag automatisch zu schließen

### Beispiel-Workflow

```
1. Root-Level: <
   → Zeigt: header, body, footer

2. Innerhalb <body>: <
   → Zeigt: section, article, aside

3. Innerhalb <section>: <
   → Zeigt: h1, h2, h3, p, ul, ol

4. Innerhalb <ul>: <
   → Zeigt: li
```

## Beispiel-XSD-Struktur

```xml
<xs:complexType name="DocumentType">
    <xs:sequence>
        <xs:element name="header" type="HeaderType"/>
        <xs:element name="body" type="BodyType"/>
        <xs:element name="footer" type="FooterType"/>
    </xs:sequence>
</xs:complexType>

<xs:complexType name="BodyType">
    <xs:sequence>
        <xs:element name="section" type="SectionType"/>
        <xs:element name="article" type="ArticleType"/>
        <xs:element name="aside" type="AsideType"/>
    </xs:sequence>
</xs:complexType>
```

**Ergebnis**:

- Innerhalb `<document>`: header, body, footer
- Innerhalb `<body>`: section, article, aside

## Tastenkombinationen

| Taste   | Aktion                                |
|---------|---------------------------------------|
| `<`     | Kontext-sensitive IntelliSense öffnen |
| `>`     | Tag automatisch schließen             |
| `↑/↓`   | In Popup navigieren                   |
| `ENTER` | **Element auswählen und einfügen**    |
| `ESC`   | Popup schließen ohne Auswahl          |

## Vorteile der kontext-sensitive IntelliSense

### 1. Präzisere Vorschläge

- **Reduzierte Auswahl**: Nur relevante Elemente werden angezeigt
- **Schnellere Auswahl**: Weniger Optionen = schnellere Entscheidung
- **Weniger Fehler**: Keine ungültigen Elemente in der Auswahl

### 2. Bessere Benutzerfreundlichkeit

- **Intuitive Bedienung**: ENTER-Taste für sofortige Auswahl
- **Kontext-Awareness**: Automatische Anpassung an aktuelle Position
- **Konsistente Erfahrung**: Verhält sich wie moderne IDEs

### 3. XSD-Integration

- **Automatische Extraktion**: Keine manuelle Konfiguration nötig
- **Schema-basiert**: Vorschläge basieren auf tatsächlichen Schema-Definitionen
- **Namespace-Support**: Unterstützt verschiedene XML-Namespaces

## Beispiel-Dateien

### XSD-Schema

- `examples/xsd/context-sensitive-demo.xsd`: Vollständiges Beispiel-Schema
- Demonstriert Parent-Child-Beziehungen
- Enthält verschiedene complexType-Definitionen

### XML-Dokument

- `examples/xml/context-sensitive-demo.xml`: Beispiel-XML-Datei
- Zeigt kontext-sensitive Struktur
- Demonstriert verschiedene Verschachtelungsebenen

## Technische Details

### Kontext-Erkennung Algorithmus

1. **Cursor-Position**: Bestimmt aktuelle Position im XML
2. **Rückwärts-Analyse**: Analysiert XML-Struktur bis zur Position
3. **Stack-basierte Logik**: Verfolgt Element-Verschachtelung
4. **Parent-Identifikation**: Identifiziert aktuelles Parent-Element

### XSD-Parsing Prozess

1. **ComplexType-Extraktion**: Findet alle complexType-Definitionen
2. **Sequence/Choice/All-Analyse**: Analysiert Element-Gruppierungen
3. **Element-Mapping**: Erstellt Parent-Child-Beziehungen
4. **Type-Referenz-Auflösung**: Löst Type-Referenzen auf

### Fallback-Mechanismus

1. **Kontext-Suche**: Sucht nach spezifischen Kindelementen
2. **Fallback-Liste**: Verwendet allgemeine Elementnamen
3. **Fehlerbehandlung**: Robuste Behandlung von Parsing-Fehlern

## Fehlerbehebung

### Keine kontext-spezifischen Vorschläge

- **XSD laden**: Stellen Sie sicher, dass ein XSD-Schema geladen ist
- **Schema-Struktur**: Überprüfen Sie complexType-Definitionen
- **Namespace**: Stellen Sie sicher, dass Namespaces korrekt sind

### Falsche Kontext-Erkennung

- **XML-Struktur**: Überprüfen Sie die XML-Verschachtelung
- **Cursor-Position**: Stellen Sie sicher, dass der Cursor korrekt positioniert ist
- **Schema-Konsistenz**: Überprüfen Sie XSD- und XML-Konsistenz

### ENTER-Taste funktioniert nicht

- **Popup-Sichtbarkeit**: Stellen Sie sicher, dass das Popup angezeigt wird
- **Fokus**: Überprüfen Sie, ob das Popup den Fokus hat
- **Event-Handling**: Prüfen Sie die Konsolen-Ausgabe auf Fehlermeldungen

## Zukünftige Erweiterungen

### Geplante Features

1. **Attribute-Vervollständigung**: Kontext-sensitive Attribut-Vorschläge
2. **Namespace-Support**: Vollständige Namespace-Unterstützung
3. **LSP-Integration**: Erweiterte Language Server Protocol Integration
4. **Real-time Updates**: Dynamische Aktualisierung basierend auf Änderungen

### Performance-Optimierungen

1. **Caching**: Cache für XSD-Parsing-Ergebnisse
2. **Lazy Loading**: On-demand Kontext-Berechnung
3. **Incremental Updates**: Effiziente Updates bei Änderungen
