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

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.w3c.dom.Node;

import java.io.Serial;
import java.io.Serializable;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents an extended XSD element with additional properties and methods,
 * now based on a native DOM Node.
 */
public class ExtendedXsdElement implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    // Core data based on DOM
    private Node currentNode;
    private String elementName;
    private String elementType;
    private String currentXpath;
    private String parentXpath;
    private int level;
    private int counter;
    private String sourceCode;
    private List<String> children = new ArrayList<>();

    // Parsed and structured data from the currentNode
    private List<DocumentationInfo> documentations = new ArrayList<>();
    private RestrictionInfo restrictionInfo;
    private JavadocInfo javadocInfo;
    private List<String> genericAppInfos;
    private List<String> exampleValues = new ArrayList<>();

    // atrificial sample values:
    private String sampleData;

    // Markdown rendering
    private final transient Parser parser;
    private final transient HtmlRenderer renderer;
    private Boolean useMarkdownRenderer = true;

    // Helper records for structured data
    public record DocumentationInfo(String lang, String content) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record RestrictionInfo(String base, Map<String, List<String>> facets) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * Constructs an ExtendedXsdElement with default settings for Markdown.
     */
    public ExtendedXsdElement() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Retrieves the language-specific documentation.
     *
     * @return A map of language codes to documentation content.
     */
    public Map<String, String> getLanguageDocumentation() {
        if (documentations == null) {
            return Map.of();
        }
        return documentations.stream()
                .collect(Collectors.toMap(
                        doc -> doc.lang() != null ? doc.lang() : "default",
                        doc -> useMarkdownRenderer ? renderer.render(parser.parse(doc.content())) : doc.content(),
                        (existing, replacement) -> existing // In case of duplicate langs, keep first
                ));
    }

    /**
     * Retrieves all documentation content as a single string, rendered as HTML if enabled.
     *
     * @return The documentation string, potentially as HTML.
     */
    public String getDocumentationAsHtml() {
        if (documentations == null || documentations.isEmpty()) {
            return "";
        }
        String rawContent = documentations.stream()
                .map(DocumentationInfo::content)
                .collect(Collectors.joining("\n\n"));

        return useMarkdownRenderer ? renderer.render(parser.parse(rawContent)) : rawContent;
    }

    /**
     * Generates a unique page name for the element's detail page.
     *
     * @return The page name (e.g., "elementName_hash.html").
     */
    public String getPageName() {
        return elementName.replace("@", "attr_") + "_" + getMD5Hex(currentXpath) + ".html";
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Retrieves the string representation of the XSD restriction facets.
     *
     * @return The XSD restriction string, formatted as HTML.
     */
    public String getXsdRestrictionString() {
        if (restrictionInfo == null || restrictionInfo.facets().isEmpty()) {
            return "";
        }

        StringWriter stringWriter = new StringWriter();
        final String lineBreak = "<br />";

        // Add base type first if it exists
        if (restrictionInfo.base() != null && !restrictionInfo.base().isBlank()) {
            stringWriter.append("Base: ").append(restrictionInfo.base()).append(lineBreak);
        }

        // Add all other facets
        restrictionInfo.facets().forEach((key, values) -> {
            // Join the list of values into a single string (e.g., "A, B, C")
            String combinedValue = String.join(", ", values);
            stringWriter.append(capitalize(key)).append(": ").append(combinedValue).append(lineBreak);
        });

        return stringWriter.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public boolean isMandatory() {
        if (this.currentNode == null) return false;
        String minOccurs = getAttributeValue(currentNode, "minOccurs");
        // Default for element is "1", for attribute it's based on "use"
        if (elementName.startsWith("@")) {
            String use = getAttributeValue(currentNode, "use");
            return "required".equals(use);
        }
        return "1".equals(minOccurs) || minOccurs == null; // minOccurs defaults to 1
    }

    private String getAttributeValue(Node node, String attrName) {
        if (node == null || node.getAttributes() == null) return null;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : null;
    }

    // --- Static utility methods ---

    public static String getMD5Hex(final String inputString) {
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update(inputString.getBytes());
            return convertByteToHex(md.digest());
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static String convertByteToHex(byte[] byteData) {
        var sb = new StringBuilder();
        for (byte b : byteData) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    // --- Standard Getters and Setters ---

    public Node getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Node currentNode) {
        this.currentNode = currentNode;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getCurrentXpath() {
        return currentXpath;
    }

    public void setCurrentXpath(String currentXpath) {
        this.currentXpath = currentXpath;
    }

    public String getParentXpath() {
        return parentXpath;
    }

    public void setParentXpath(String parentXpath) {
        this.parentXpath = parentXpath;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /**
     * Ruft die Beispieldaten für das Element ab und stellt sicher, dass sie frei von ungültigen XML-Zeichen sind.
     * Diese Methode filtert Steuerzeichen (außer Tabulator, Zeilenumbruch und Wagenrücklauf) heraus,
     * um Fehler beim Schreiben der Daten in HTML-Dateien zu verhindern.
     *
     * @return Die bereinigten Beispieldaten oder ein leerer String, falls keine vorhanden sind.
     */
    public String getSampleData() {
        if (sampleData == null || sampleData.isEmpty()) {
            return "";
        }
        // Dieser reguläre Ausdruck entfernt alle Zeichen, die als "Steuerzeichen" (`\p{C}`) kategorisiert sind,
        // mit Ausnahme der erlaubten Whitespace-Zeichen: Tabulator (\t), Zeilenumbruch (\n) und Wagenrücklauf (\r).
        // Dies verhindert eine "UnmappableCharacterException" beim Schreiben von Dateien, die solche Zeichen enthalten.
        return sampleData.replaceAll("[\\p{C}&&[^\\r\\n\\t]]", "");
    }

    /**
     * Gibt die finalen Beispieldaten für die Anzeige zurück.
     * Priorisiert vordefinierte Beispielwerte aus dem XSD (`exampleValues`).
     * Wenn keine vorhanden sind, wird auf die künstlich generierten Daten (`sampleData`) zurückgegriffen.
     *
     * @return Die besten verfügbaren Beispieldaten als bereinigter String.
     */
    public String getDisplaySampleData() {
        // Priorität 1: Vordefinierte Werte aus dem XSD, falls vorhanden.
        if (exampleValues != null && !exampleValues.isEmpty()) {
            // Nimm den ersten Wert und bereinige ihn von ungültigen Zeichen.
            String firstExample = exampleValues.getFirst();
            if (firstExample == null) {
                return "";
            }
            // Verwende dieselbe Bereinigung wie für generierte Daten.
            return firstExample.replaceAll("[\\p{C}&&[^\\r\\n\\t]]", "");
        }

        // Priorität 2: Künstlich generierte Daten als Fallback.
        return getSampleData(); // Diese Methode enthält bereits die Bereinigung.
    }


    public void setSampleData(String sampleData) {
        this.sampleData = sampleData;
    }

    public List<String> getChildren() {
        return children;
    }

    public void setChildren(List<String> children) {
        this.children = children;
    }

    public List<DocumentationInfo> getDocumentations() {
        return documentations;
    }

    public void setDocumentations(List<DocumentationInfo> documentations) {
        this.documentations = documentations;
    }

    public RestrictionInfo getRestrictionInfo() {
        return restrictionInfo;
    }

    public void setRestrictionInfo(RestrictionInfo restrictionInfo) {
        this.restrictionInfo = restrictionInfo;
    }

    public JavadocInfo getJavadocInfo() {
        return javadocInfo;
    }

    public void setJavadocInfo(JavadocInfo javadocInfo) {
        this.javadocInfo = javadocInfo;
    }

    public List<String> getGenericAppInfos() {
        return genericAppInfos;
    }

    public void setGenericAppInfos(List<String> genericAppInfos) {
        this.genericAppInfos = genericAppInfos;
    }

    public List<String> getExampleValues() {
        return exampleValues;
    }

    public void setExampleValues(List<String> exampleValues) {
        this.exampleValues = exampleValues;
    }

    public Boolean getUseMarkdownRenderer() {
        return useMarkdownRenderer;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }
}