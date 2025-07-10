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

import org.xmlet.xsdparser.core.utils.NamespaceInfo;
import org.xmlet.xsdparser.xsdelements.XsdComplexType;
import org.xmlet.xsdparser.xsdelements.XsdElement;
import org.xmlet.xsdparser.xsdelements.XsdSchema;
import org.xmlet.xsdparser.xsdelements.XsdSimpleType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XsdDocumentationData {

    Map<String, ExtendedXsdElement> extendedXsdElementMap = new HashMap<>();
    private Map<String, List<ExtendedXsdElement>> typeUsageMap = new HashMap<>();

    List<XsdSchema> xmlSchema;
    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;
    private List<XsdElement> elements;
    Map<String, NamespaceInfo> namespaces = new HashMap<>();
    String version;

    String xsdFilePath;

    public Map<String, List<ExtendedXsdElement>> getTypeUsageMap() {
        return typeUsageMap;
    }

    public void setTypeUsageMap(Map<String, List<ExtendedXsdElement>> typeUsageMap) {
        this.typeUsageMap = typeUsageMap;
    }

    public List<XsdSchema> getXmlSchema() {
        return xmlSchema;
    }

    public void setXmlSchema(List<XsdSchema> xmlSchema) {
        this.xmlSchema = xmlSchema;
        this.xsdComplexTypes = this.xmlSchema.getFirst().getChildrenComplexTypes().collect(Collectors.toList());
        this.xsdSimpleTypes = xmlSchema.getFirst().getChildrenSimpleTypes().collect(Collectors.toList());
        this.namespaces = xmlSchema.getFirst().getNamespaces();
    }

    public List<XsdComplexType> getXsdComplexTypes() {
        return xsdComplexTypes;
    }

    public void setXsdComplexTypes(List<XsdComplexType> xsdComplexTypes) {
        this.xsdComplexTypes = xsdComplexTypes;
    }

    public List<XsdSimpleType> getXsdSimpleTypes() {
        return xsdSimpleTypes;
    }

    public void setXsdSimpleTypes(List<XsdSimpleType> xsdSimpleTypes) {
        this.xsdSimpleTypes = xsdSimpleTypes;
    }

    public List<XsdElement> getElements() {
        return elements;
    }

    public void setElements(List<XsdElement> elements) {
        this.elements = elements;
    }

    public Map<String, NamespaceInfo> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Map<String, NamespaceInfo> namespaces) {
        this.namespaces = namespaces;
    }

    public Map<String, ExtendedXsdElement> getExtendedXsdElementMap() {
        return extendedXsdElementMap;
    }

    public void setExtendedXsdElementMap(Map<String, ExtendedXsdElement> extendedXsdElementMap) {
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

    public String getNameSpacesAsString() {
        return namespaces
                .keySet()
                .stream()
                .map(ns -> ns + "=" + "'" + namespaces.get(ns).getName() + "'" + "<br />")
                .collect(Collectors.joining());
    }
}
