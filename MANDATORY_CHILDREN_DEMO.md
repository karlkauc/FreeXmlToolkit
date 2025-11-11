# Automatische Erstellung von Pflicht-Kindknoten

## Übersicht

Die Funktionalität zur automatischen Erstellung von Pflicht-Kindknoten ist bereits implementiert und funktioniert
korrekt. Wenn im XmlCodeEditor ein neuer Knoten erstellt wird und ein XSD verknüpft ist, werden automatisch alle
mandatory Kindknoten erstellt.

## Wie es funktioniert

### 1. XSD-Verarbeitung

- Das System analysiert das verknüpfte XSD-Schema
- Es identifiziert alle Elemente mit `minOccurs="1"` oder höher als mandatory
- Diese Informationen werden im `XsdDocumentationService` gespeichert

### 2. Automatische Knotenerstellung

- Wenn ein neuer XML-Knoten erstellt wird (z.B. durch Eingabe von `<root>`)
- Das System prüft, ob ein XSD verknüpft ist (`EditorMode.XML_WITH_XSD`)
- Es ruft `handleAutoCloseWithMandatoryChildren()` auf
- Alle mandatory Kindknoten werden automatisch erstellt

### 3. Beispiel

**XSD Schema:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="root">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="mandatoryChild1" type="xs:string" minOccurs="1"/>
                <xs:element name="mandatoryChild2" minOccurs="1">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="nestedMandatory" type="xs:string" minOccurs="1"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                <xs:element name="optionalChild" type="xs:string" minOccurs="0"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
```

**Eingabe:** `<root>`

**Automatisch generiert:**

```xml
<root>
    <mandatoryChild1></mandatoryChild1>
    <mandatoryChild2>
        <nestedMandatory></nestedMandatory>
    </mandatoryChild2>
</root>
```

## Implementierungsdetails

### Wichtige Klassen und Methoden:

1. **XmlCodeEditor.handleAutoCloseWithMandatoryChildren()**
    - Hauptmethode für die automatische Erstellung
    - Wird aufgerufen, wenn ein neuer Knoten erstellt wird

2. **XsdDocumentationService.getMandatoryChildElements()**
    - Analysiert das XSD und gibt mandatory Kindknoten zurück
    - Berücksichtigt verschachtelte Strukturen

3. **MandatoryElement Klasse**
    - Interne Datenstruktur für mandatory Elemente
    - Unterstützt rekursive Verschachtelung

### Trigger-Events:

- Eingabe von `>` nach einem Elementnamen
- Enter-Taste nach einem öffnenden Tag
- Auto-Completion-Auswahl

## Test-Ergebnisse

Die Funktionalität wurde erfolgreich getestet:

```
[DEBUG] Added mandatory child element: mandatoryChild1 (minOccurs=1, maxOccurs=1, hasChildren=false) 
[DEBUG] Added mandatory child element: nestedMandatory (minOccurs=1, maxOccurs=1, hasChildren=false) 
[DEBUG] Added mandatory child element: mandatoryChild2 (minOccurs=1, maxOccurs=1, hasChildren=true)
[DEBUG] Found 2 mandatory children for element 'root'
```

## Status

✅ **FUNKTIONALITÄT IST BEREITS IMPLEMENTIERT UND FUNKTIONIERT KORREKT**

Die automatische Erstellung von Pflicht-Kindknoten funktioniert wie gewünscht. Wenn ein XSD verknüpft ist, werden beim
Erstellen neuer Knoten automatisch alle mandatory Kindknoten erstellt.

