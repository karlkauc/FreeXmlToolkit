# XSD 1.1 Datentypen und ihre anwendbaren Facets

## Facet-Übersicht

| Facet | Beschreibung | Anwendbar auf |
|-------|--------------|---------------|
| **length** | Exakte Länge (string, binary) | String-Typen, Binary |
| **minLength** | Minimale Länge | String-Typen, Binary |
| **maxLength** | Maximale Länge | String-Typen, Binary |
| **pattern** | Regulärer Ausdruck | Alle außer boolean |
| **enumeration** | Liste erlaubter Werte | Alle Typen |
| **whiteSpace** | Whitespace-Behandlung (preserve/replace/collapse) | Alle Typen |
| **maxInclusive** | Maximum (inklusive) | Numeric, Date/Time |
| **maxExclusive** | Maximum (exklusive) | Numeric, Date/Time |
| **minInclusive** | Minimum (inklusive) | Numeric, Date/Time |
| **minExclusive** | Minimum (exklusive) | Numeric, Date/Time |
| **totalDigits** | Gesamtzahl Ziffern | decimal und abgeleitete |
| **fractionDigits** | Anzahl Nachkommastellen | decimal und abgeleitete |
| **assertions** | XPath 2.0 Ausdrücke (XSD 1.1) | Alle Typen |
| **explicitTimezone** | required/prohibited/optional (XSD 1.1) | Date/Time Typen |

---

## 1. String-basierte Typen

### xs:string
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace
- ✅ assertions

### xs:normalizedString (abgeleitet von xs:string)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: replace)
- ✅ assertions

### xs:token (abgeleitet von xs:normalizedString)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:language (abgeleitet von xs:token)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:Name (abgeleitet von xs:token)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:NCName (abgeleitet von xs:Name)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:ID, xs:IDREF, xs:ENTITY (abgeleitet von xs:NCName)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:NMTOKEN (abgeleitet von xs:token)
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

---

## 2. Numerische Typen

### xs:decimal
- ✅ totalDigits
- ✅ fractionDigits
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:integer (abgeleitet von xs:decimal)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:nonPositiveInteger (abgeleitet von xs:integer)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 0)
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:negativeInteger (abgeleitet von xs:nonPositiveInteger)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: -1)
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:long (abgeleitet von xs:integer)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 9223372036854775807)
- ✅ maxExclusive
- ✅ minInclusive (fixed: -9223372036854775808)
- ✅ minExclusive
- ✅ assertions

### xs:int (abgeleitet von xs:long)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 2147483647)
- ✅ maxExclusive
- ✅ minInclusive (fixed: -2147483648)
- ✅ minExclusive
- ✅ assertions

### xs:short (abgeleitet von xs:int)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 32767)
- ✅ maxExclusive
- ✅ minInclusive (fixed: -32768)
- ✅ minExclusive
- ✅ assertions

### xs:byte (abgeleitet von xs:short)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 127)
- ✅ maxExclusive
- ✅ minInclusive (fixed: -128)
- ✅ minExclusive
- ✅ assertions

### xs:nonNegativeInteger (abgeleitet von xs:integer)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive (fixed: 0)
- ✅ minExclusive
- ✅ assertions

### xs:unsignedLong (abgeleitet von xs:nonNegativeInteger)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 18446744073709551615)
- ✅ maxExclusive
- ✅ minInclusive (fixed: 0)
- ✅ minExclusive
- ✅ assertions

### xs:unsignedInt (abgeleitet von xs:unsignedLong)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 4294967295)
- ✅ maxExclusive
- ✅ minInclusive (fixed: 0)
- ✅ minExclusive
- ✅ assertions

### xs:unsignedShort (abgeleitet von xs:unsignedInt)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 65535)
- ✅ maxExclusive
- ✅ minInclusive (fixed: 0)
- ✅ minExclusive
- ✅ assertions

### xs:unsignedByte (abgeleitet von xs:unsignedShort)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive (fixed: 255)
- ✅ maxExclusive
- ✅ minInclusive (fixed: 0)
- ✅ minExclusive
- ✅ assertions

### xs:positiveInteger (abgeleitet von xs:nonNegativeInteger)
- ✅ totalDigits
- ✅ fractionDigits (fixed: 0)
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive (fixed: 1)
- ✅ minExclusive
- ✅ assertions

### xs:float
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:double
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ✅ enumeration
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

---

## 3. Datum/Zeit Typen

### xs:dateTime
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:dateTimeStamp (XSD 1.1, abgeleitet von xs:dateTime)
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (fixed: required)
- ✅ assertions

### xs:date
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:time
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:duration
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:yearMonthDuration (XSD 1.1, abgeleitet von xs:duration)
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:dayTimeDuration (XSD 1.1, abgeleitet von xs:duration)
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ assertions

### xs:gYear
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:gYearMonth
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:gMonth
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:gMonthDay
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

### xs:gDay
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ maxInclusive
- ✅ maxExclusive
- ✅ minInclusive
- ✅ minExclusive
- ✅ explicitTimezone (XSD 1.1)
- ✅ assertions

---

## 4. Boolean

### xs:boolean
- ✅ pattern
- ✅ whiteSpace (fixed: collapse)
- ❌ enumeration (nicht sinnvoll, nur true/false)
- ✅ assertions

---

## 5. Binary Typen

### xs:hexBinary
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:base64Binary
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

---

## 6. URI Typen

### xs:anyURI
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

---

## 7. Spezielle Typen

### xs:QName
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

### xs:NOTATION
- ✅ length
- ✅ minLength
- ✅ maxLength
- ✅ pattern
- ✅ enumeration
- ✅ whiteSpace (fixed: collapse)
- ✅ assertions

---

## Facet-Kategorien nach Datentyp

### Kategorien:

1. **STRING_TYPES**: xs:string und Ableitungen
   - length, minLength, maxLength, pattern, enumeration, whiteSpace, assertions

2. **DECIMAL_TYPES**: xs:decimal und Ableitungen
   - totalDigits, fractionDigits, pattern, whiteSpace, enumeration, min/maxInclusive, min/maxExclusive, assertions

3. **FLOAT_TYPES**: xs:float, xs:double
   - pattern, whiteSpace, enumeration, min/maxInclusive, min/maxExclusive, assertions

4. **DATETIME_TYPES**: xs:dateTime, xs:date, xs:time, xs:gYear, etc.
   - pattern, enumeration, whiteSpace, min/maxInclusive, min/maxExclusive, explicitTimezone, assertions

5. **DURATION_TYPES**: xs:duration und Ableitungen
   - pattern, enumeration, whiteSpace, min/maxInclusive, min/maxExclusive, assertions

6. **BINARY_TYPES**: xs:hexBinary, xs:base64Binary
   - length, minLength, maxLength, pattern, enumeration, whiteSpace, assertions

7. **BOOLEAN_TYPE**: xs:boolean
   - pattern, whiteSpace, assertions

8. **URI_TYPE**: xs:anyURI
   - length, minLength, maxLength, pattern, enumeration, whiteSpace, assertions

9. **QNAME_NOTATION_TYPES**: xs:QName, xs:NOTATION
   - length, minLength, maxLength, pattern, enumeration, whiteSpace, assertions
