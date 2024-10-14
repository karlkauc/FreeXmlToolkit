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
import org.xmlet.xsdparser.xsdelements.XsdAnnotation;
import org.xmlet.xsdparser.xsdelements.XsdAnnotationChildren;
import org.xmlet.xsdparser.xsdelements.XsdDocumentation;
import org.xmlet.xsdparser.xsdelements.XsdElement;

import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class ExtendedXsdElement {
    XsdElement xsdElement;
    int level;
    List<XsdDocumentation> xsdDocumentation;

    List<String> children = new ArrayList<>();

    String currentXpath;
    String parentXpath;

    String currentHash;
    String sourceCode;
    Node currentNode;
    int counter;

    String elementName;
    String elementType;

    MutableDataSet options = new MutableDataSet();
    Parser parser;
    HtmlRenderer renderer;

    Boolean useMarkdownRenderer = true;

    String sampleData;

    enum XmlDataType {
        DURATION,
        DATETIME,
        TIME,
        DATE,
        GYEARMONTH,
        GYEAR,
        GMONTHDAY,
        GDAY,
        GMONTH,
        STRING,
        BOOLEAN,
        BASE64BINARY,
        HEXBINARY,
        FLOAT,
        DECIMAL,
        DOUBLE,
        ANYURI,
        QNAME,
        NOTATION,
        NORMALIZEDSTRING,
        TOKEN,
        LANGUAGE,
        NAME,
        NCNAME,
        ID,
        IDREF,
        IDREFS,
        ENTITY,
        ENTITIES,
        INTEGER,
        NONPOSITIVEINTEGER,
        NEGATIVEINTEGER,
        LONG,
        INT,
        SHORT,
        BYTE,
        NONNEGATIVEINTEGER,
        UNSIGNEDLONG,
        UNSIGNEDINT,
        UNSIGNEDSHORT,
        UNSIGNEDBYTE,
        POSITIVEINTEGER
    }

    public ExtendedXsdElement() {
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    public Map<String, String> getLanguageDocumentation() {
        if (this.getXsdDocumentation() == null) {
            return Map.of();
        } else {
            com.vladsch.flexmark.util.ast.Node document;
            Map<String, String> stringContent = new HashMap<>();

            for (var x : this.getXsdDocumentation()) {
                if (useMarkdownRenderer) {
                    document = parser.parse(x.getContent());
                    stringContent.put(x.getAttributesMap().get("xml:lang"), renderer.render(document));
                } else {
                    stringContent.put(x.getAttributesMap().get("xml:lang"), x.getContent());
                }
            }

            return stringContent;
        }
    }

    public List<String> getExampleValues() {
        if (this.xsdElement == null) {
            return List.of();
        } else {
            XsdAnnotation appInfo = this.xsdElement.getAnnotation();
            var appInfoList = appInfo.getAppInfoList();
            return appInfoList.stream().map(XsdAnnotationChildren::getContent).collect(Collectors.toList());
        }
    }

    public String getPageName() {
        return elementName + "_" + getMD5Hex(currentXpath) + ".html";
    }

    public static String getMD5Hex(final String inputString) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(inputString.getBytes());
            byte[] digest = md.digest();
            return convertByteToHex(digest);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    private static String convertByteToHex(byte[] byteData) {
        StringBuilder sb = new StringBuilder();
        for (byte byteDatum : byteData) {
            sb.append(Integer.toString((byteDatum & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public Boolean getUseMarkdownRenderer() {
        return useMarkdownRenderer;
    }

    public void setXsdDocumentation(List<XsdDocumentation> xsdDocumentation) {
        this.xsdDocumentation = xsdDocumentation;
    }

    public String getParentXpath() {
        return parentXpath;
    }

    public void setParentXpath(String parentXpath) {
        this.parentXpath = parentXpath;
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

    public List<String> getChildren() {
        return children;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setChildren(List<String> children) {
        this.children = children;
    }

    public XsdElement getXsdElement() {
        return xsdElement;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Node currentNode) {
        this.currentNode = currentNode;
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

    public void setXsdElement(XsdElement xsdElement) {
        this.xsdElement = xsdElement;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public List<XsdDocumentation> getXsdDocumentation() {
        return xsdDocumentation;
    }

    public String getCurrentXpath() {
        return currentXpath;
    }

    public void setCurrentXpath(String currentXpath) {
        this.currentXpath = currentXpath;
    }

    public String getSampleData() {
        return sampleData;
    }

    public void setSampleData(String sampleData) {
        this.sampleData = sampleData;
    }

}
