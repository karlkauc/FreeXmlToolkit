package org.fxt.freexmltoolkit.domain;

import java.util.List;

/**
 * Repräsentiert einen Knoten in der XSD-Struktur auf rekursive Weise.
 * Jeder Knoten kann eine Liste von Kind-Knoten (wiederum XsdNodeInfo) enthalten.
 *
 * @param name          Der Name des Elements.
 * @param type          Der Typ des Elements (z.B. "xs:string" oder ein complexType).
 * @param documentation Die aus <xs:annotation> extrahierte Dokumentation.
 * @param children      Eine Liste der Kind-Knoten.
 * @param xpath         Der eindeutige XPath zu diesem Element, dient als Schlüssel.
 * @param exampleValues Eine Liste von Beispielwerten.
 * @param minOccurs     Die minimale Anzahl des Vorkommens (z.B. "0", "1").
 * @param maxOccurs     Die maximale Anzahl des Vorkommens (z.B. "1", "unbounded").
 */
public record XsdNodeInfo(
        String name,
        String type,
        String xpath,
        String documentation,
        List<XsdNodeInfo> children,
        List<String> exampleValues,
        String minOccurs, // <-- NEU
        String maxOccurs  // <-- NEU
) {}