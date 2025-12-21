# FundsXML 4.2.10 Schema - Inkonsistenzen-Report

**Analysedatum:** 2025-12-10
**Schema-Version:** 4.2.10
**Analysierte Dateien:** Alle XSD-Dateien im Verzeichnis `include_files/`

---

## Inhaltsverzeichnis

1. [Kritische Fehler](#1-kritische-fehler)
2. [Mittlere Fehler](#2-mittlere-fehler)
3. [Geringfügige Inkonsistenzen](#3-geringfügige-inkonsistenzen)
4. [Empfehlungen](#4-empfehlungen)

---

## 1. Kritische Fehler

### 1.1 Vertauschte Sprachattribute (de/en)

**Schweregrad:** KRITISCH
**Datei:** `include_files/FundsXML4_PortfolioData.xsd`
**Zeilen:** 95-100

#### Beschreibung
Die XML-Dokumentations-Attribute `xml:lang="de"` und `xml:lang="en"` sind vertauscht. Der englische Text steht unter dem deutschen Sprachcode und umgekehrt.

#### Betroffener Code
```xml
<xs:element name="Identifiers" type="IdentifiersType" minOccurs="0">
    <xs:annotation>
        <xs:documentation xml:lang="de">Identifiers of instrument (like ISIN, Ticker, ...)</xs:documentation>
        <xs:documentation xml:lang="en">Kennnummer des im Portfolio enthaltenen Instrumentes (ISIN, Ticker, ...)</xs:documentation>
    </xs:annotation>
</xs:element>
```

#### Auswirkung
- Systeme, die sprachspezifische Dokumentation extrahieren, liefern falsche Texte
- Automatische Übersetzungstools oder Dokumentationsgeneratoren produzieren inkorrekte Ausgaben
- Benutzer, die nach deutscher Dokumentation filtern, erhalten englischen Text

#### Korrektur
```xml
<xs:element name="Identifiers" type="IdentifiersType" minOccurs="0">
    <xs:annotation>
        <xs:documentation xml:lang="en">Identifiers of instrument (like ISIN, Ticker, ...)</xs:documentation>
        <xs:documentation xml:lang="de">Kennnummer des im Portfolio enthaltenen Instrumentes (ISIN, Ticker, ...)</xs:documentation>
    </xs:annotation>
</xs:element>
```

---

## 2. Mittlere Fehler

### 2.1 Falscher Datentyp für YearOfBirth

**Schweregrad:** MITTEL
**Datei:** `include_files/FundsXML4_FundStaticData.xsd`
**Zeile:** 208

#### Beschreibung
Das Feld `YearOfBirth` (Geburtsjahr) ist als `xs:date` definiert, obwohl der Name nur ein Jahr impliziert.

#### Betroffener Code
```xml
<xs:element name="YearOfBirth" type="xs:date" minOccurs="0">
    <xs:annotation>
        <xs:documentation xml:lang="en">Date of birth of the portfolio manager</xs:documentation>
        <xs:documentation xml:lang="de">[AUTO] Geburtsdatum des Portfolio-Managers</xs:documentation>
    </xs:annotation>
</xs:element>
```

#### Analyse
| Aspekt | Ist-Zustand | Erwartung basierend auf Name |
|--------|-------------|------------------------------|
| Feldname | `YearOfBirth` | Nur Jahr |
| Datentyp | `xs:date` (YYYY-MM-DD) | `xs:gYear` (YYYY) |
| Dokumentation EN | "Date of birth" | "Year of birth" |
| Dokumentation DE | "Geburtsdatum" | "Geburtsjahr" |

#### Auswirkung
- Semantische Verwirrung für Entwickler
- Inkonsistenz zwischen Feldname und erwartetem Inhalt
- Potenzielle Datenschutzbedenken (vollständiges Geburtsdatum vs. nur Jahr)

#### Empfohlene Korrekturen
**Option A:** Feldname ändern zu `DateOfBirth`
```xml
<xs:element name="DateOfBirth" type="xs:date" minOccurs="0">
```

**Option B:** Datentyp ändern zu `xs:gYear`
```xml
<xs:element name="YearOfBirth" type="xs:gYear" minOccurs="0">
```

---

### 2.2 Fehlende Module in ModuleUsage-Enumeration

**Schweregrad:** MITTEL
**Datei:** `include_files/FundsXML4.xsd`
**Zeilen:** 537-555 (Enumeration) vs. 12-25 (Includes)

#### Beschreibung
Die `ModuleUsage`-Enumeration listet nicht alle tatsächlich inkludierten Schema-Module auf.

#### Includes (Zeilen 12-25)
```xml
<xs:include schemaLocation="FundsXML4_Core.xsd"/>
<xs:include schemaLocation="FundsXML4_CountrySpecificData.xsd"/>
<xs:include schemaLocation="FundsXML4_FundStaticData.xsd"/>
<xs:include schemaLocation="FundsXML4_FundDynamicData.xsd"/>
<xs:include schemaLocation="FundsXML4_ShareClassData.xsd"/>
<xs:include schemaLocation="FundsXML4_AssetMasterData.xsd"/>
<xs:include schemaLocation="FundsXML4_AssetMgmtCompDynData.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_EMIR.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_EMT.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_KIID.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_PRIIPS.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_SolvencyII.xsd"/>
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_EFT.xsd"/>      <!-- FEHLT in Enumeration -->
<xs:include schemaLocation="FundsXML4_RegulatoryReporting_EET.xsd"/>      <!-- FEHLT in Enumeration -->
```

#### ModuleUsage-Enumeration (Zeilen 537-555)
```xml
<xs:enumeration value="AssetMasterData"/>
<xs:enumeration value="AssetMgmtCompDynData"/>
<xs:enumeration value="CountrySpecificData_AT"/>
<xs:enumeration value="CountrySpecificData_DE"/>
<xs:enumeration value="CountrySpecificData_DK"/>
<xs:enumeration value="CountrySpecificData_FR"/>
<xs:enumeration value="CountrySpecificData_LU"/>
<xs:enumeration value="CountrySpecificData_NL"/>
<xs:enumeration value="FundDynamicData"/>
<xs:enumeration value="FundStaticData"/>
<xs:enumeration value="PortfolioData"/>
<xs:enumeration value="RegulatoryReporting_EMIR"/>
<xs:enumeration value="RegulatoryReporting_EMT"/>
<xs:enumeration value="RegulatoryReporting_KIID"/>
<xs:enumeration value="RegulatoryReporting_PRIIPS"/>
<xs:enumeration value="RegulatoryReporting_SolvencyII"/>
<xs:enumeration value="ShareClassData"/>
<xs:enumeration value="TransactionData"/>
<!-- FEHLEND: RegulatoryReporting_EFT -->
<!-- FEHLEND: RegulatoryReporting_EET -->
```

#### Fehlende Module
| Modul | Include vorhanden | In Enumeration |
|-------|-------------------|----------------|
| `RegulatoryReporting_EFT` | Ja (Zeile 24) | **NEIN** |
| `RegulatoryReporting_EET` | Ja (Zeile 25) | **NEIN** |

#### Auswirkung
- Nutzer können EFT- und EET-Module nicht in `ModuleUsage` deklarieren
- Inkonsistenz zwischen Schema-Struktur und Metadaten
- Potenzielle Validierungsprobleme bei strengen Parsern

#### Korrektur
Hinzufügen zur Enumeration:
```xml
<xs:enumeration value="RegulatoryReporting_EFT"/>
<xs:enumeration value="RegulatoryReporting_EET"/>
```

---

### 2.3 Falsche Dokumentation "Three letter ISO code"

**Schweregrad:** MITTEL
**Datei:** `include_files/FundsXML4.xsd`
**Zeilen:** 254-255

#### Beschreibung
Die Dokumentation behauptet, es handle sich um einen "Three letter ISO code", aber `ISOCountryCodeType` ist als 2-Zeichen-Code definiert (ISO 3166-1 alpha-2).

#### Betroffener Code
```xml
<xs:element name="Country" type="ISOCountryCodeType" maxOccurs="unbounded">
    <xs:annotation>
        <xs:documentation xml:lang="en">Three letter ISO code of country</xs:documentation>
        <xs:documentation xml:lang="de">[AUTO] Dreibuchstabiger ISO-Ländercode</xs:documentation>
    </xs:annotation>
</xs:element>
```

#### ISOCountryCodeType Definition (FundsXML4_Core.xsd:1183-1190)
```xml
<xs:simpleType name="ISOCountryCodeType">
    <xs:annotation>
        <xs:documentation xml:lang="en">The ISOCountryCodeType is a type for decoding ISO-CountryCodes.
        It is used the two-letter ISO-CountryCodes (ISO 3166-1alpha-2).
        It is of the type string and has a length from exactly two letters.</xs:documentation>
    </xs:annotation>
    <xs:restriction base="xs:string">
        <xs:maxLength value="2"/>
    </xs:restriction>
</xs:simpleType>
```

#### Auswirkung
- Irreführende Dokumentation für Entwickler
- Potenzielle Implementierungsfehler durch Verwendung von 3-Zeichen-Codes (ISO 3166-1 alpha-3)

#### Korrektur
```xml
<xs:documentation xml:lang="en">Two letter ISO code of country (ISO 3166-1 alpha-2)</xs:documentation>
<xs:documentation xml:lang="de">Zweibuchstabiger ISO-Ländercode (ISO 3166-1 alpha-2)</xs:documentation>
```

---

## 3. Geringfügige Inkonsistenzen

### 3.1 Wirkungslose maxLength-Beschränkung bei ListedType

**Schweregrad:** GERING
**Datei:** `include_files/FundsXML4.xsd`
**Zeilen:** 192-210

#### Beschreibung
Die `maxLength`-Beschränkung von 12 Zeichen wird von mehreren Enumeration-Werten überschritten.

#### Betroffener Code
```xml
<xs:simpleType>
    <xs:restriction base="xs:string">
        <xs:minLength value="3"/>
        <xs:maxLength value="12"/>
        <xs:enumeration value="AIFMD-AnnexIV"/>           <!-- 13 Zeichen -->
        <xs:enumeration value="AnnualReport"/>            <!-- 12 Zeichen - OK -->
        <xs:enumeration value="HalfYearReport"/>          <!-- 14 Zeichen -->
        <xs:enumeration value="QuarterlyReport"/>         <!-- 15 Zeichen -->
        <xs:enumeration value="MonthlyReport"/>           <!-- 13 Zeichen -->
        <xs:enumeration value="AuditReport"/>             <!-- 11 Zeichen - OK -->
        <xs:enumeration value="Factsheet"/>               <!-- 9 Zeichen - OK -->
        <xs:enumeration value="Prospectus"/>              <!-- 10 Zeichen - OK -->
        <xs:enumeration value="PRIIPS-KID"/>              <!-- 10 Zeichen - OK -->
        <xs:enumeration value="KID / Simplified Prospectus"/>  <!-- 26 Zeichen -->
        <xs:enumeration value="MarketingNotification"/>   <!-- 21 Zeichen -->
        <xs:enumeration value="SFDR-PAIStatement"/>       <!-- 17 Zeichen -->
        <xs:enumeration value="SFDR Website Disclosure"/> <!-- 23 Zeichen -->
        <xs:enumeration value="MarketingMaterial"/>       <!-- 17 Zeichen -->
        <xs:enumeration value="ValuationReport"/>         <!-- 15 Zeichen -->
    </xs:restriction>
</xs:simpleType>
```

#### Analyse der Zeichenlängen
| Enumeration-Wert | Länge | Status |
|------------------|-------|--------|
| `Factsheet` | 9 | OK |
| `Prospectus` | 10 | OK |
| `PRIIPS-KID` | 10 | OK |
| `AuditReport` | 11 | OK |
| `AnnualReport` | 12 | OK |
| `AIFMD-AnnexIV` | 13 | **ÜBERSCHREITET** |
| `MonthlyReport` | 13 | **ÜBERSCHREITET** |
| `HalfYearReport` | 14 | **ÜBERSCHREITET** |
| `QuarterlyReport` | 15 | **ÜBERSCHREITET** |
| `ValuationReport` | 15 | **ÜBERSCHREITET** |
| `MarketingMaterial` | 17 | **ÜBERSCHREITET** |
| `SFDR-PAIStatement` | 17 | **ÜBERSCHREITET** |
| `MarketingNotification` | 21 | **ÜBERSCHREITET** |
| `SFDR Website Disclosure` | 23 | **ÜBERSCHREITET** |
| `KID / Simplified Prospectus` | 26 | **ÜBERSCHREITET** |

#### Auswirkung
- Die `maxLength`-Beschränkung ist wirkungslos, da Enumerations-Werte Vorrang haben
- Code-Qualitätsproblem: Irreführende Constraints
- Bei XML Schema 1.1 könnte dies in strikten Parsern zu Warnungen führen

#### Korrektur
Entweder `maxLength` auf 30 erhöhen oder komplett entfernen:
```xml
<xs:restriction base="xs:string">
    <xs:minLength value="3"/>
    <xs:maxLength value="30"/>
    <!-- Enumerations... -->
</xs:restriction>
```

---

### 3.2 Doppelte XSD-Dateien

**Schweregrad:** GERING
**Dateien:**
- `/FundsXML4.xsd` (Root-Level, ~2.4MB, monolithisch)
- `/include_files/FundsXML4.xsd` (~50KB, modular)

#### Beschreibung
Es existieren zwei `FundsXML4.xsd` Dateien mit unterschiedlicher Struktur:
- Die Root-Level-Datei ist eine generierte/zusammengeführte monolithische Version
- Die Datei in `include_files/` verwendet modulare Includes

#### Auswirkung
- Potenzielle Verwirrung bei Entwicklern
- Mögliche Divergenz der Versionen bei manuellen Änderungen
- Unklarheit, welche Datei als "master" gilt

#### Empfehlung
- Klare Dokumentation, welche Datei für welchen Zweck verwendet werden soll
- Automatisierter Build-Prozess, der die monolithische Datei aus den Modulen generiert

---

### 3.3 Uneinheitliches Deprecation-Format

**Schweregrad:** GERING
**Dateien:** Mehrere

#### Beschreibung
Die `@deprecated`-Annotationen folgen keinem einheitlichen Format.

#### Gefundene Varianten
```xml
<!-- Variante 1: Kurz -->
<xs:appinfo source="@deprecated"/>

<!-- Variante 2: Mit Hinweis -->
<xs:appinfo source="@deprecated will no longer be supported in a future FundsXML schema version"/>

<!-- Variante 3: Mit Referenz -->
<xs:appinfo source="@deprecated old version. do not use any more. use {@link /FundsXML4/RegulatoryReportings/IndirectReporting/TripartiteTemplateSolvencyII_V7} instead"/>

<!-- Variante 4: Mit Version (im Inhalt) -->
<xs:appinfo>@deprecated: please use UnderlyingAsset instead
@deprecated-since: 4.2.7</xs:appinfo>
```

#### Fundstellen
| Datei | Zeile | Format |
|-------|-------|--------|
| `FundsXML4_AssetMasterData.xsd` | 3211 | Variante 4 (mit Version) |
| `FundsXML4.xsd` | 682, 687, 692, 698 | Variante 2 |
| `FundsXML4.xsd` | 731, 737, 743, 749 | Variante 1 |
| `FundsXML4.xsd` | 967, 980 | Variante 3 |
| `FundsXML4.xsd` | 993 | Variante 1 |

#### Empfohlenes einheitliches Format
```xml
<xs:appinfo>
    @deprecated
    @deprecated-since: [VERSION]
    @deprecated-replacement: [XPATH oder Elementname]
    @deprecated-removal: [geplante Entfernungsversion, falls bekannt]
</xs:appinfo>
```

---

### 3.4 Inkonsistente Length-Constraints für semantisch gleiche Felder

**Schweregrad:** GERING
**Datei:** `include_files/FundsXML4_FundStaticData.xsd`

#### 3.4.1 OpenClosedEnded

| Kontext | Zeilen | minLength | maxLength | Enumerations |
|---------|--------|-----------|-----------|--------------|
| `FundStaticDataType` | 71-81 | - | - | OPEN, CLOSED |
| `SubfundStaticDataType` | 467-479 | 4 | 6 | OPEN, CLOSED |

**FundStaticDataType (ohne Constraints):**
```xml
<xs:element name="OpenClosedEnded" minOccurs="0">
    <xs:simpleType>
        <xs:restriction base="xs:string">
            <xs:enumeration value="OPEN"/>
            <xs:enumeration value="CLOSED"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**SubfundStaticDataType (mit Constraints):**
```xml
<xs:element name="OpenClosedEnded" minOccurs="0">
    <xs:simpleType>
        <xs:restriction base="xs:string">
            <xs:minLength value="4"/>
            <xs:maxLength value="6"/>
            <xs:enumeration value="OPEN"/>
            <xs:enumeration value="CLOSED"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

#### 3.4.2 ClosedType

| Kontext | Zeilen | length | Enumerations |
|---------|--------|--------|--------------|
| `FundStaticDataType` | 83-93 | - | HARD, SOFT |
| `SubfundStaticDataType` | 481-492 | 4 | HARD, SOFT |

#### 3.4.3 ManagementType mit unnötig spezifischen Constraints

**Datei:** `include_files/FundsXML4_FundStaticData.xsd`, Zeilen 447-460

```xml
<xs:element name="ManagementType" minOccurs="0">
    <xs:simpleType>
        <xs:restriction base="xs:string">
            <xs:minLength value="6"/>   <!-- ACTIVE = 6 Zeichen -->
            <xs:maxLength value="7"/>   <!-- PASSIVE = 7 Zeichen -->
            <xs:enumeration value="ACTIVE"/>
            <xs:enumeration value="PASSIVE"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

Die Constraints `minLength="6"` und `maxLength="7"` sind exakt auf die beiden Werte zugeschnitten und würden das Hinzufügen neuer Werte wie `INDEX` (5 Zeichen) oder `QUANTITATIVE` (12 Zeichen) verhindern.

#### 3.4.4 SalesCategory

**Zeilen:** 494-506

```xml
<xs:minLength value="6"/>
<xs:maxLength value="11"/>
<xs:enumeration value="PUBLIC"/>       <!-- 6 Zeichen -->
<xs:enumeration value="RETAIL"/>       <!-- 6 Zeichen -->
<xs:enumeration value="RETAIL-HNW"/>   <!-- 10 Zeichen -->
<xs:enumeration value="SPECIAL"/>      <!-- 7 Zeichen -->
```

Hier ist `maxLength="11"`, aber der längste Wert hat nur 10 Zeichen - akzeptabel, aber könnte präziser sein.

---

## 4. Empfehlungen

### Priorisierte Maßnahmen

| Priorität | Fehler | Empfohlene Aktion |
|-----------|--------|-------------------|
| **1 - Kritisch** | Vertauschte Sprachen | Sofort korrigieren |
| **2 - Hoch** | Fehlende Module EFT/EET | In nächstem Release |
| **3 - Hoch** | "Three letter" Dokumentation | In nächstem Release |
| **4 - Mittel** | YearOfBirth Typ | Prüfen und entscheiden |
| **5 - Niedrig** | maxLength Constraints | Bei Gelegenheit |
| **6 - Niedrig** | Deprecation Format | Richtlinie erstellen |
| **7 - Niedrig** | Inkonsistente Constraints | Bei Refactoring |

### Prozessempfehlungen

1. **Automatisierte Validierung einführen**
   - Schema-Lint-Tools für Konsistenzprüfungen
   - Prüfung auf Length-Constraints vs. Enumeration-Werte
   - Sprachattribut-Validierung

2. **Dokumentationsrichtlinien**
   - Einheitliches Deprecation-Format definieren
   - Sprachkonventionen (immer EN zuerst, dann DE)
   - Versionierung von Änderungen

3. **Build-Pipeline**
   - Automatische Generierung der monolithischen XSD aus Modulen
   - Diff-Prüfung zwischen generierter und committed Datei

---

## Anhang: Betroffene Dateien

| Datei | Anzahl Fehler |
|-------|---------------|
| `include_files/FundsXML4_PortfolioData.xsd` | 1 |
| `include_files/FundsXML4_FundStaticData.xsd` | 5 |
| `include_files/FundsXML4.xsd` | 4 |
| `include_files/FundsXML4_Core.xsd` | 0 (korrekt) |

---

*Dieser Report wurde automatisch generiert am 2025-12-10.*
