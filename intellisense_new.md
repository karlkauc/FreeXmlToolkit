Ich möchte die Intellisense Funktion im XmlCodeEditor komplett überarbeiten.

Die Intellisense und auto close Funktion hängt davon ab, welches File gerade bearbeitet wird:
* * XML ohne verknüpften XSD ("*.xml")
* XML mit verknüpften XSD ("*.xml")
* Schematron File ("*.sch")
* XSD File ("*.xsd")
* XSLT Stylesheet ("*.xslt")
* XSL-FO Stylesheet ("*.xsl")

# Allgemein
Alle File Typen sollen ein Auto Close haben.

Beispiel:
"<Name>|" soll automatisch zu "<Name>|</Name>" werden.
Wobei "|" den Cursor repräsentiert.

XML ohne verknüpften XSD sollen keine weitere Intellisense Funktion haben.


## Xml mit verknüpften XSD
XML mit verknüpften XSD sollen eine erweiterte Intellisense und auto close Funktion haben.
beim Erstellen eines knoten sollen automatisch alle mandatory sub knoten mit leerem inhalt erstellt werden.

### Intellisense Popup
Wenn das Zeichen "<" eingegeben wird, soll ein intellisense popup alle Kindknoten anzeigen, welche an dieser Stelle eingefügt werden können. 
Die Liste ist im Sidepanel vorhanden. Die Liste soll nach dem Vorkommen im XSD Schema sortiert sein. 

Das Intellisense Popup soll denselben 3-Spalten Aufbau wie das "Completion Popup" (siehe @INTELLISENSE-DEMO.MD) haben.
(Inklusiver Tastatur Bindings)


### Auto Close: 
"<knoten>|"

soll zu folgendem werden:

"<knoten>
  <sub1>|</sub1>
  <sub2>
    <sub3></sub3>
  </sub2>
</knoten>"

### Documentation
wenn auf einem knoten mit STRG-geklickt wird, soll das 3-spaltige "Completion Popup" erscheinen (siehe @INTELLISENSE-DEMO.MD)

### Textinhalte bei Simple Types und Enumerations
Wenn ein Knoten den Datentype "xs:boolean" hat, sollen über eine Liste nur die Werte "true" und "false" ausgewählt werden können. 
Wenn ein Knoten als restriction eine Enumeration hat, soll über eine Liste nur eines dieser Werte ausgewählt werden können. 


## Schematron File
Schematron Files sollen eine autocomplete Funktion haben, welche den Schematron Standard unterstützt. 

Zum Beispiel:
"
<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
queryBinding="xslt2">

    <sch:title>FundsXML Advanced Business Logic Validation (Simplified)</sch:title>

    <sch:pattern id="eam-internal-rules-applied-to-automatic-upload">
        <sch:title>EAM Internal rules - applied to automatic upload</sch:title>

        <sch:rule id="0-0" context="/FundsXML4/Funds/Fund/Identifiers">
            <sch:assert test="LEI">
                fonds lei: <sch:value-of select="LEI" />
            </sch:assert>
        </sch:rule>
..."

Die intellisense funktion welche mit dem "<" getriggert wird, soll bei einem leeren file den Knoten "<sch:schema>" vorschlagen. 
Nicht die anderen Knoten. 
Wenn ich innerhalb des "<sch:schema>" Knoten bin, sollen nur die knoten "<sch:title>" und "<sch:pattern>" vorgeschlagen werden. 
Aber nicht die Knoten "<sch:rule>" oder "<sch:assert>". Diese sollen nur vorgeschlagen werden, wenn ich innerhalb des "<sch:pattern>" Knoten bin.


## XSD File
XSD Files sollen eine autocomplete Funktion haben, welche den XSD Standard unterstützt. 

Zum Beispiel:
"<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:altova="http://www.altova.com/xml-schema-extensions"
    xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    attributeFormDefault="unqualified"
    elementFormDefault="qualified">
    <xs:import namespace="http://www.w3.org/2000/09/xmldsig#"
        schemaLocation="xmldsig-core-schema.xsd"/>
    <xs:element name="FundsXML4">
        <xs:annotation>
            <xs:documentation>asdf</xs:documentation>
        </xs:annotation>
        <xs:complexType>
        <xs:sequence>
            <xs:element name="ControlData" type="ControlDataType">
        <xs:annotation>
            <xs:documentation>Meta data of xml document (like unique id, date, data supplier, language, ...)</xs:documentation>
        </xs:annotation>
    </xs:element>
    <xs:element minOccurs="0" name="Funds">
        <xs:annotation>
            <xs:documentation>List of funds, umbrellas, sicavs, portfolios ...</xs:documentation>
        </xs:annotation>
...
"


Bei einem leeren Element muss ein "xs:schema" Knoten erstellt werden. Aber es darf kein "xs:komplexType" erstellt werden. 
Innerhalb eines "xs:schema" dürfen Knoten für "xs:element" und "xs:complexType" erstellt werden. Aber kein "xs:annotation".
Innerhalb eines "xs:element" darf ein "xs:annotation" erstellt werden. Und so weiter. 

Bei einem dürfen attribute wie "minOccurs", "maxOccurs", "name" etc. verwendet werden. 

## XSLT Stylesheet und XSL-FO Stylesheet
Wende dieselben Prinzipien wie oben an. 



