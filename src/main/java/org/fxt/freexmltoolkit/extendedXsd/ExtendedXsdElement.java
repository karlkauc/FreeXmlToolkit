/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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
package org.fxt.freexmltoolkit.extendedXsd;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.w3c.dom.Node;
import org.xmlet.xsdparser.xsdelements.XsdAnnotationChildren;
import org.xmlet.xsdparser.xsdelements.XsdDocumentation;
import org.xmlet.xsdparser.xsdelements.XsdElement;
import org.xmlet.xsdparser.xsdelements.XsdRestriction;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdStringRestrictions;

import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class ExtendedXsdElement {
    private XsdElement xsdElement;
    private XsdRestriction xsdRestriction;
    private int level;
    private List<XsdDocumentation> xsdDocumentation;
    private List<String> children = new ArrayList<>();
    private String currentXpath;
    private String parentXpath;
    private String currentHash;
    private String sourceCode;
    private Node currentNode;
    private int counter;
    private String elementName;
    private String elementType;
    private final MutableDataSet options = new MutableDataSet();
    private final Parser parser;
    private final HtmlRenderer renderer;
    private Boolean useMarkdownRenderer = true;
    private String sampleData;

    public ExtendedXsdElement() {
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    public Map<String, String> getLanguageDocumentation() {
        if (xsdDocumentation == null) {
            return Map.of();
        }
        Map<String, String> stringContent = new HashMap<>();
        for (var doc : xsdDocumentation) {
            if (useMarkdownRenderer) {
                var document = parser.parse(doc.getContent());
                stringContent.put(doc.getAttributesMap().get("xml:lang"), renderer.render(document));
            } else {
                stringContent.put(doc.getAttributesMap().get("xml:lang"), doc.getContent());
            }
        }
        return stringContent;
    }

    public List<String> getExampleValues() {
        if (xsdElement == null) {
            return List.of();
        }
        var appInfoList = xsdElement.getAnnotation().getAppInfoList();
        return appInfoList.stream().map(XsdAnnotationChildren::getContent).collect(Collectors.toList());
    }

    public String getPageName() {
        return elementName + "_" + getMD5Hex(currentXpath) + ".html";
    }

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

    // Getters and setters
    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public Boolean getUseMarkdownRenderer() {
        return useMarkdownRenderer;
    }

    public void setXsdRestriction(XsdRestriction xsdRestriction) {
        this.xsdRestriction = xsdRestriction;
    }

    public XsdRestriction getXsdRestriction() {
        return xsdRestriction;
    }

    public String getXsdRestrictionString() {
        if (xsdRestriction == null) {
            return "";
        }

        StringWriter stringWriter = new StringWriter();

        if (xsdRestriction.getEnumeration() != null) {
            stringWriter
                    .append("Enumeration: ")
                    .append(xsdRestriction.getEnumeration().stream().map(XsdStringRestrictions::getValue).collect(Collectors.joining(", ")))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getPattern() != null) {
            stringWriter
                    .append("Pattern: ")
                    .append(xsdRestriction.getPattern().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMinInclusive() != null) {
            stringWriter
                    .append("Min inclusive: ")
                    .append(xsdRestriction.getMinInclusive().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMaxInclusive() != null) {
            stringWriter
                    .append("Max inclusive: ")
                    .append(xsdRestriction.getMaxInclusive().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMinExclusive() != null) {
            stringWriter
                    .append("Min exclusive: ")
                    .append(xsdRestriction.getMinExclusive().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMaxExclusive() != null) {
            stringWriter
                    .append("Max exclusive: ")
                    .append(xsdRestriction.getMaxExclusive().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getFractionDigits() != null) {
            stringWriter
                    .append("Fraction digits: ")
                    .append(String.valueOf(xsdRestriction.getFractionDigits().getValue()))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getTotalDigits() != null) {
            stringWriter
                    .append("Total digits: ")
                    .append(String.valueOf(xsdRestriction.getTotalDigits().getValue()))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getLength() != null) {
            stringWriter
                    .append("Length: ")
                    .append(String.valueOf(xsdRestriction.getLength().getValue()))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMinLength() != null) {
            stringWriter
                    .append("Min length: ")
                    .append(String.valueOf(xsdRestriction.getMinLength().getValue()))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getMaxLength() != null) {
            stringWriter
                    .append("Max length: ")
                    .append(String.valueOf(xsdRestriction.getMaxLength().getValue()))
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getWhiteSpace() != null) {
            stringWriter
                    .append("White space: ")
                    .append(xsdRestriction.getWhiteSpace().getValue().getValue())
                    .append(System.lineSeparator());
        }

        if (xsdRestriction.getBase() != null) {
            stringWriter
                    .append("Base: ")
                    .append(xsdRestriction.getBase())
                    .append(System.lineSeparator());
        }

        return stringWriter.toString();
    }

    public void setXsdDocumentation(List<XsdDocumentation> xsdDocumentation) {
        this.xsdDocumentation = xsdDocumentation;
    }

    public List<XsdDocumentation> getXsdDocumentation() {
        return xsdDocumentation;
    }

    public void setParentXpath(String parentXpath) {
        this.parentXpath = parentXpath;
    }

    public String getParentXpath() {
        return parentXpath;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getElementType() {
        return elementType;
    }

    public void setChildren(List<String> children) {
        this.children = children;
    }

    public List<String> getChildren() {
        return children;
    }

    public void setCurrentXpath(String currentXpath) {
        this.currentXpath = currentXpath;
    }

    public String getCurrentXpath() {
        return currentXpath;
    }

    public void setCurrentNode(Node currentNode) {
        this.currentNode = currentNode;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public int getCounter() {
        return counter;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setXsdElement(XsdElement xsdElement) {
        this.xsdElement = xsdElement;
    }

    public XsdElement getXsdElement() {
        return xsdElement;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public void setSampleData(String sampleData) {
        this.sampleData = sampleData;
    }

    public String getSampleData() {
        return sampleData;
    }
}