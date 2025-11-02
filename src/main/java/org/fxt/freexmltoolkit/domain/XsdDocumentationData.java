/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.domain;

import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO to hold all relevant data parsed from an XSD for documentation generation.
 * This version uses native Java XML (DOM) types instead of external libraries.
 */
public class XsdDocumentationData {

    // Maps an XPath to its corresponding detailed element information.
    private Map<String, XsdExtendedElement> extendedXsdElementMap = new HashMap<>();

    // Caches which elements use a specific type for faster lookups.
    private Map<String, List<XsdExtendedElement>> typeUsageMap = new HashMap<>();

    // Holds all global <xs:element> nodes.
    private List<Node> globalElements = new ArrayList<>();

    // Holds all global <xs:complexType> nodes.
    private List<Node> globalComplexTypes = new ArrayList<>();

    // Holds all global <xs:simpleType> nodes.
    private List<Node> globalSimpleTypes = new ArrayList<>();

    // Holds all namespaces found in the schema (prefix -> URI).
    private Map<String, String> namespaces = new HashMap<>();

    private String version;
    private String targetNamespace;
    private String xsdFilePath;

    private String attributeFormDefault;
    private String elementFormDefault;

    // XSD 1.1 default open content (applies to all types in schema)
    private OpenContent defaultOpenContent;

    public OpenContent getDefaultOpenContent() {
        return defaultOpenContent;
    }

    public void setDefaultOpenContent(OpenContent defaultOpenContent) {
        this.defaultOpenContent = defaultOpenContent;
    }

    public String getAttributeFormDefault() {
        return attributeFormDefault;
    }

    public void setAttributeFormDefault(String attributeFormDefault) {
        this.attributeFormDefault = attributeFormDefault;
    }

    public String getElementFormDefault() {
        return elementFormDefault;
    }

    public void setElementFormDefault(String elementFormDefault) {
        this.elementFormDefault = elementFormDefault;
    }

    // --- Getters and Setters ---

    public Map<String, List<XsdExtendedElement>> getTypeUsageMap() {
        return typeUsageMap;
    }

    public void setTypeUsageMap(Map<String, List<XsdExtendedElement>> typeUsageMap) {
        this.typeUsageMap = typeUsageMap;
    }

    public List<Node> getGlobalComplexTypes() {
        return globalComplexTypes;
    }

    public void setGlobalComplexTypes(List<Node> globalComplexTypes) {
        this.globalComplexTypes = globalComplexTypes;
    }

    public List<Node> getGlobalSimpleTypes() {
        return globalSimpleTypes;
    }

    public void setGlobalSimpleTypes(List<Node> globalSimpleTypes) {
        this.globalSimpleTypes = globalSimpleTypes;
    }

    public List<Node> getGlobalElements() {
        return globalElements;
    }

    public void setGlobalElements(List<Node> globalElements) {
        this.globalElements = globalElements;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    public Map<String, XsdExtendedElement> getExtendedXsdElementMap() {
        return extendedXsdElementMap;
    }

    public void setExtendedXsdElementMap(Map<String, XsdExtendedElement> extendedXsdElementMap) {
        this.extendedXsdElementMap = extendedXsdElementMap;
    }

    public String getXsdFilePath() {
        return xsdFilePath;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    /**
     * Returns a string representation of the namespaces for display.
     *
     * @return A formatted string of namespaces.
     */
    public String getNameSpacesAsString() {
        if (namespaces == null || namespaces.isEmpty()) {
            return "";
        }
        return namespaces.entrySet().stream()
                .map(entry -> entry.getKey() + "='" + entry.getValue() + "'")
                .collect(Collectors.joining("<br />"));
    }
}