package org.fxt.freexmltoolkit.domain;

import java.util.List;

/**
 * Repräsentiert einen Knoten in der XSD-Struktur auf rekursive Weise.
 * Jeder Knoten kann eine Liste von Kind-Knoten (wiederum XsdNodeInfo) enthalten.
 */
public record XsdNodeInfo(
        String name,
        String type,
        String xpath,
        String documentation,
        List<XsdNodeInfo> children,
        List<String> exampleValues,
        String minOccurs,
        String maxOccurs,
        NodeType nodeType
) {
    /**
     * NEU: Definiert die Art des Knotens, um die Darstellung in der UI zu steuern.
     */
    public enum NodeType {
        ELEMENT,
        ATTRIBUTE,
        SEQUENCE,
        CHOICE,
        ANY
    }
}