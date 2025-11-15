# Comprehensive XSD 1.1 Facets Demo - Anleitung

## Übersicht

Die Datei `test-comprehensive-facets-demo.xsd` ist ein **vollständiges Beispiel** für alle XSD 1.1 Facets-Features und demonstriert alle implementierten Funktionalitäten.

---

## Struktur der Demo-Datei

### SECTION 1: Direkte XSD Built-in Typen
**Element:** `DirectBuiltInTypes`

**Inhalt:**
- Elemente mit direkten XSD-Typen **ohne** Restrictions
- Keine Custom Types
- Keine Facets

**Beispiele:**
```xml
<xs:element name="SimpleString" type="xs:string"/>
<xs:element name="Integer" type="xs:integer"/>
<xs:element name="DateTime" type="xs:dateTime"/>
```

**UI-Verhalten:**
- Keine Facets angezeigt
- FacetsPanel zeigt: "No facets defined"

---

### SECTION 2: Inline Restrictions
**Element:** `InlineRestrictions`

**Inhalt:**
- Elemente mit **inline** Restrictions
- Direkt am Element definierte SimpleType-Restrictions
- Editierbar in der UI

**Beispiele:**

**Username (String mit Länge + Pattern):**
```xml
<xs:element name="Username">
    <xs:simpleType>
        <xs:restriction base="xs:string">
            <xs:minLength value="3"/>
            <xs:maxLength value="20"/>
            <xs:pattern value="[a-zA-Z0-9]+"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**Age (Integer mit Range):**
```xml
<xs:element name="Age">
    <xs:simpleType>
        <xs:restriction base="xs:int">
            <xs:minInclusive value="0"/>
            <xs:maxInclusive value="150"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**Price (Decimal mit Precision):**
```xml
<xs:element name="Price">
    <xs:simpleType>
        <xs:restriction base="xs:decimal">
            <xs:totalDigits value="10"/>
            <xs:fractionDigits value="2"/>
            <xs:minInclusive value="0"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**Status (Enumeration):**
```xml
<xs:element name="Status">
    <xs:simpleType>
        <xs:restriction base="xs:string">
            <xs:enumeration value="ACTIVE"/>
            <xs:enumeration value="INACTIVE"/>
            <xs:enumeration value="PENDING"/>
            <xs:enumeration value="SUSPENDED"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**AppointmentTime (XSD 1.1 - explicitTimezone):**
```xml
<xs:element name="AppointmentTime">
    <xs:simpleType>
        <xs:restriction base="xs:dateTime">
            <xs:minInclusive value="2024-01-01T00:00:00Z"/>
            <xs:maxInclusive value="2025-12-31T23:59:59Z"/>
            <xs:explicitTimezone value="required"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**EvenNumber (XSD 1.1 - Assertion):**
```xml
<xs:element name="EvenNumber">
    <xs:simpleType>
        <xs:restriction base="xs:integer">
            <xs:assertion test="$value mod 2 = 0"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI-Verhalten:**
- Element auswählen → zeigt inline Restriction
- Facets **editierbar** (Add/Edit/Delete aktiv)
- Fixed Facets (z.B. whiteSpace für int) gelb markiert

---

### SECTION 3: Custom SimpleTypes
**Definierte Typen:**

#### 1. ISINType
```xml
<xs:simpleType name="ISINType">
    <xs:restriction base="xs:string">
        <xs:length value="12"/>
        <xs:pattern value="[A-Z]{2}[A-Z0-9]{9}[0-9]{1}"/>
    </xs:restriction>
</xs:simpleType>
```
- **International Securities Identification Number**
- Exakt 12 Zeichen
- Pattern: 2 Buchstaben + 9 alphanumerisch + 1 Ziffer

#### 2. EmailType
```xml
<xs:simpleType name="EmailType">
    <xs:restriction base="xs:string">
        <xs:minLength value="5"/>
        <xs:maxLength value="254"/>
        <xs:pattern value="[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"/>
    </xs:restriction>
</xs:simpleType>
```
- **Email-Adresse** mit Pattern-Validierung
- 5-254 Zeichen

#### 3. PhoneNumberType
```xml
<xs:simpleType name="PhoneNumberType">
    <xs:restriction base="xs:string">
        <xs:pattern value="\+[1-9][0-9]{1,14}"/>
    </xs:restriction>
</xs:simpleType>
```
- **Internationale Telefonnummer** (E.164 Format)
- Beginnt mit +

#### 4. ZIPCodeType
```xml
<xs:simpleType name="ZIPCodeType">
    <xs:restriction base="xs:string">
        <xs:pattern value="[0-9]{5}(-[0-9]{4})?"/>
    </xs:restriction>
</xs:simpleType>
```
- **US ZIP Code**
- 5 Ziffern oder 5+4 Ziffern

#### 5. PercentageType
```xml
<xs:simpleType name="PercentageType">
    <xs:restriction base="xs:decimal">
        <xs:minInclusive value="0.00"/>
        <xs:maxInclusive value="100.00"/>
        <xs:fractionDigits value="2"/>
    </xs:restriction>
</xs:simpleType>
```
- **Prozentsatz** 0.00 - 100.00
- 2 Nachkommastellen

#### 6. ProductCodeType
```xml
<xs:simpleType name="ProductCodeType">
    <xs:restriction base="xs:string">
        <xs:minLength value="5"/>
        <xs:maxLength value="15"/>
        <xs:pattern value="[A-Z0-9]+"/>
    </xs:restriction>
</xs:simpleType>
```
- **Produktcode**
- Großbuchstaben + Ziffern
- 5-15 Zeichen

#### 7. CurrencyCodeType
```xml
<xs:simpleType name="CurrencyCodeType">
    <xs:restriction base="xs:string">
        <xs:length value="3"/>
        <xs:pattern value="[A-Z]{3}"/>
        <xs:enumeration value="USD"/>
        <xs:enumeration value="EUR"/>
        <xs:enumeration value="GBP"/>
        <xs:enumeration value="JPY"/>
        <xs:enumeration value="CHF"/>
    </xs:restriction>
</xs:simpleType>
```
- **ISO 4217 Währungscode**
- Exakt 3 Buchstaben
- Enumeration für gängige Währungen

#### 8. AmountType
```xml
<xs:simpleType name="AmountType">
    <xs:restriction base="xs:decimal">
        <xs:totalDigits value="12"/>
        <xs:fractionDigits value="2"/>
        <xs:minExclusive value="0"/>
    </xs:restriction>
</xs:simpleType>
```
- **Geldbetrag**
- Positiv (> 0)
- Max 12 Ziffern, 2 Nachkommastellen

#### 9. YearType
```xml
<xs:simpleType name="YearType">
    <xs:restriction base="xs:int">
        <xs:minInclusive value="1900"/>
        <xs:maxInclusive value="2100"/>
    </xs:restriction>
</xs:simpleType>
```
- **Jahreszahl** 1900-2100

#### 10. PriorityType
```xml
<xs:simpleType name="PriorityType">
    <xs:restriction base="xs:string">
        <xs:enumeration value="LOW"/>
        <xs:enumeration value="MEDIUM"/>
        <xs:enumeration value="HIGH"/>
        <xs:enumeration value="CRITICAL"/>
    </xs:restriction>
</xs:simpleType>
```
- **Prioritätsstufen**

#### 11. WorkingHoursType (XSD 1.1)
```xml
<xs:simpleType name="WorkingHoursType">
    <xs:restriction base="xs:dateTime">
        <xs:assertion test="hours-from-dateTime($value) ge 8 and hours-from-dateTime($value) le 18"/>
    </xs:restriction>
</xs:simpleType>
```
- **Arbeitszeiten** (8-18 Uhr)
- XSD 1.1 Assertion

#### 12. BusinessDayType (XSD 1.1)
```xml
<xs:simpleType name="BusinessDayType">
    <xs:restriction base="xs:date">
        <xs:assertion test="...Monday-Friday check..."/>
    </xs:restriction>
</xs:simpleType>
```
- **Geschäftstag** (Montag-Freitag)
- XSD 1.1 Assertion

**UI-Verhalten:**
- SimpleType direkt auswählen
- Facets **editierbar** (Add/Edit/Delete aktiv)
- Normale Darstellung

---

### SECTION 4: Elemente mit Custom Types
**Element:** `ElementsWithCustomTypes`

**Inhalt:**
- Elemente, die Custom Types **referenzieren**
- Demonstriert **Inherited Facets Feature**

**Beispiele:**

```xml
<xs:element name="ISIN" type="ISINType"/>
<xs:element name="ContactEmail" type="EmailType"/>
<xs:element name="MobilePhone" type="PhoneNumberType"/>
<xs:element name="PostalCode" type="ZIPCodeType"/>
<xs:element name="DiscountRate" type="PercentageType"/>
<xs:element name="ProductID" type="ProductCodeType"/>
<xs:element name="Currency" type="CurrencyCodeType"/>
<xs:element name="TotalAmount" type="AmountType"/>
<xs:element name="ManufactureYear" type="YearType"/>
<xs:element name="Priority" type="PriorityType"/>
<xs:element name="MeetingTime" type="WorkingHoursType"/>
<xs:element name="DeliveryDate" type="BusinessDayType"/>
```

**UI-Verhalten (WICHTIG!):**

**1. Element auswählen (z.B. "ISIN"):**
```
┌─────────────────────────────────────────────────┐
│ Facets (Restrictions)                           │
├─────────────────────────────────────────────────┤
│ ℹ️ Showing facets from referenced type         │
│    'ISINType' (read-only)                       │
├─────────────────────────────────────────────────┤
│ Type    │ Value                      │ Fixed   │
├─────────┼────────────────────────────┼─────────┤
│ length  │ 12                         │ ☐       │ ← BLAU
│ pattern │ [A-Z]{2}[A-Z0-9]{9}[0-9]{1}│ ☐       │ ← BLAU
├─────────────────────────────────────────────────┤
│ [Add] [Edit] [Delete] ← ALLE DEAKTIVIERT       │
└─────────────────────────────────────────────────┘
```
- **Blauer Info-Banner**
- **Facets blau + kursiv** markiert
- **Buttons deaktiviert**
- **Read-only** (nicht editierbar)

**2. Versuch zu editieren:**
```
Alert:
  Title: Cannot Edit
  Header: Inherited Facet
  Content: This facet is inherited from the referenced type 'ISINType'
           and cannot be edited here.

           To modify this facet, edit the SimpleType definition.
```

**3. SimpleType "ISINType" direkt auswählen:**
```
┌─────────────────────────────────────────────────┐
│ Facets (Restrictions)                           │
├─────────────────────────────────────────────────┤
│ Type    │ Value                      │ Fixed   │
├─────────┼────────────────────────────┼─────────┤
│ length  │ 12                         │ ☐       │ ← Normal
│ pattern │ [A-Z]{2}[A-Z0-9]{9}[0-9]{1}│ ☐       │ ← Normal
├─────────────────────────────────────────────────┤
│ [Add] [Edit] [Delete] ← ALLE AKTIV             │
└─────────────────────────────────────────────────┘
```
- **Kein Info-Banner**
- **Normale Darstellung**
- **Buttons aktiv**
- **Editierbar**

---

### SECTION 5: Alle Facet-Typen
**Element:** `AllFacetsShowcase`

**Inhalt:**
- Zeigt **jeden einzelnen Facet-Typ** isoliert
- Ideal zum Testen einzelner Facets

**Facet-Typen:**
1. LENGTH
2. MIN_LENGTH
3. MAX_LENGTH
4. PATTERN
5. ENUMERATION
6. WHITESPACE
7. MAX_INCLUSIVE
8. MAX_EXCLUSIVE
9. MIN_INCLUSIVE
10. MIN_EXCLUSIVE
11. TOTAL_DIGITS
12. FRACTION_DIGITS
13. ASSERTION (XSD 1.1)
14. EXPLICIT_TIMEZONE (XSD 1.1)

**UI-Verhalten:**
- Jedes Element zeigt genau einen Facet-Typ
- Ideal für isolierte Tests

---

### SECTION 6: Fixed Facets Demo
**Element:** `FixedFacetsDemo`

**Inhalt:**
- Demonstriert **Fixed Facets** laut XSD Spezifikation
- Zeigt gelbe Markierung in der UI

**Beispiele:**

**1. Integer (fractionDigits=0 fixed):**
```xml
<xs:element name="IntegerNumber">
    <xs:simpleType>
        <xs:restriction base="xs:integer">
            <xs:minInclusive value="1"/>
            <xs:maxInclusive value="1000"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI zeigt (zusätzlich zu den editierbaren Facets):**
- fractionDigits = 0 (GELB markiert, read-only)
- whiteSpace = collapse (GELB markiert, read-only)

**2. Int (min/max fixed):**
```xml
<xs:element name="IntNumber">
    <xs:simpleType>
        <xs:restriction base="xs:int">
            <xs:totalDigits value="8"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI zeigt (zusätzlich zu totalDigits):**
- minInclusive = -2147483648 (GELB, read-only)
- maxInclusive = 2147483647 (GELB, read-only)
- fractionDigits = 0 (GELB, read-only)

**3. UnsignedInt (min=0 fixed):**
```xml
<xs:element name="UnsignedIntNumber">
    <xs:simpleType>
        <xs:restriction base="xs:unsignedInt">
            <xs:maxInclusive value="1000000"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI zeigt:**
- minInclusive = 0 (GELB, read-only)
- maxInclusive = 4294967295 (GELB, read-only - außer überschrieben)

**4. NormalizedString (whiteSpace=replace fixed):**
```xml
<xs:element name="NormalizedText">
    <xs:simpleType>
        <xs:restriction base="xs:normalizedString">
            <xs:maxLength value="255"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI zeigt:**
- whiteSpace = replace (GELB, read-only)

**5. Token (whiteSpace=collapse fixed):**
```xml
<xs:element name="TokenText">
    <xs:simpleType>
        <xs:restriction base="xs:token">
            <xs:minLength value="1"/>
        </xs:restriction>
    </xs:simpleType>
</xs:element>
```

**UI zeigt:**
- whiteSpace = collapse (GELB, read-only)

---

## UI-Test-Szenarien

### Test 1: Direkte Built-in Typen
1. Öffne `test-comprehensive-facets-demo.xsd`
2. Erweitere Element "DirectBuiltInTypes"
3. Wähle Element "SimpleString"
4. **Erwartung:** FacetsPanel zeigt "No facets defined"

### Test 2: Inline Restriction (editierbar)
1. Erweitere Element "InlineRestrictions"
2. Wähle Element "Username"
3. **Erwartung:**
   - 3 Facets angezeigt: minLength=3, maxLength=20, pattern=...
   - Buttons AKTIV
   - Normale Darstellung (nicht blau)
   - Editierbar

### Test 3: Inherited Facets (read-only)
1. Erweitere Element "ElementsWithCustomTypes"
2. Wähle Element "ISIN"
3. **Erwartung:**
   - Info-Banner: "Showing facets from referenced type 'ISINType' (read-only)"
   - 2 Facets: length=12, pattern=...
   - **BLAU + KURSIV** markiert
   - Alle Buttons DEAKTIVIERT
   - Tooltip: "Inherited from type 'ISINType' (read-only)"

### Test 4: Edit Inherited Facet (sollte fehlschlagen)
1. Bei Element "ISIN" ausgewählt
2. Doppelklick auf Facet oder Edit-Button
3. **Erwartung:**
   - Alert-Dialog: "Cannot Edit - Inherited Facet"
   - Keine Änderung möglich

### Test 5: SimpleType direkt editieren
1. Wähle SimpleType "ISINType" im Tree
2. **Erwartung:**
   - KEIN Info-Banner
   - 2 Facets: length=12, pattern=...
   - **NORMALE** Darstellung (nicht blau)
   - Alle Buttons AKTIV
   - Editierbar

### Test 6: Fixed Facets (gelb)
1. Erweitere "FixedFacetsDemo"
2. Wähle Element "IntegerNumber"
3. **Erwartung:**
   - Editierbare Facets: minInclusive=1, maxInclusive=1000 (normal)
   - Fixed Facets: fractionDigits=0, whiteSpace=collapse (**GELB**)
   - Tooltip bei gelben Facets: "Fixed value for xs:integer..."

### Test 7: XSD 1.1 Assertion
1. Erweitere "InlineRestrictions"
2. Wähle Element "EvenNumber"
3. **Erwartung:**
   - 1 Facet: assertion mit XPath-Ausdruck
   - Editierbar

### Test 8: XSD 1.1 explicitTimezone
1. Erweitere "InlineRestrictions"
2. Wähle Element "AppointmentTime"
3. **Erwartung:**
   - 3 Facets: minInclusive, maxInclusive, explicitTimezone=required
   - Alle editierbar

### Test 9: Alle Facet-Typen
1. Erweitere "AllFacetsShowcase"
2. Teste jedes Element einzeln
3. **Erwartung:**
   - Jedes Element zeigt genau seinen spezifischen Facet-Typ
   - Alle editierbar

### Test 10: Datentyp-spezifische Facets
1. Wähle Element "Username" (base=xs:string)
2. Klicke "Add"
3. **Erwartung im Dialog:**
   - Dropdown zeigt NUR: length, minLength, maxLength, pattern, enumeration, whiteSpace, assertion
   - NICHT: totalDigits, fractionDigits, minInclusive, etc.

---

## Zusammenfassung der Features

### ✅ Demonstriert:

1. **44 XSD Datentypen** - Alle Built-in Typen
2. **14 Facet-Typen** - Alle Standard + XSD 1.1 Facets
3. **Custom SimpleTypes** - 12 realistische Beispiele
4. **Inherited Facets** - Elemente mit referenzierten Typen
5. **Fixed Facets** - XSD Spec-definierte Einschränkungen
6. **Inline Restrictions** - Direkt am Element
7. **XSD 1.1 Features** - assertions, explicitTimezone

### Farb-Kodierung in der UI:

| Farbe | Bedeutung | Editierbar? |
|-------|-----------|-------------|
| **Normal** | Eigene Facets | ✅ Ja |
| **BLAU** | Inherited Facets | ❌ Nein |
| **GELB** | Fixed Facets (XSD Spec) | ❌ Nein |

---

**Erstellt von:** Claude Code
**Version:** 2.0
**Datum:** 2025-11-15
**Zweck:** Vollständiges Test- und Demo-File für XSD 1.1 Facets
