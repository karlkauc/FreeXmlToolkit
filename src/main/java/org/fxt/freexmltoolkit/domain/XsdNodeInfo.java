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
 */
public record XsdNodeInfo(
        String name,
        String type,
        String documentation,
        List<XsdNodeInfo> children,
        String xpath
) {
}