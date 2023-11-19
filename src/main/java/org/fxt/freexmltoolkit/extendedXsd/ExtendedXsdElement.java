/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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

import org.w3c.dom.Node;
import org.xmlet.xsdparser.xsdelements.XsdDocumentation;
import org.xmlet.xsdparser.xsdelements.XsdElement;

import java.util.ArrayList;
import java.util.List;

public class ExtendedXsdElement {
    XsdElement xsdElement;
    int level;
    List<XsdDocumentation> xsdDocumentation;

    List<String> children = new ArrayList<>();

    String currentXpath;

    String sourceCode;
    Node currentNode;

    int counter;

    public List<String> getChildren() {
        return children;
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

    public void setXsdDocumentation(List<XsdDocumentation> xsdDocumentation) {
        this.xsdDocumentation = xsdDocumentation;
    }

    public String getCurrentXpath() {
        return currentXpath;
    }

    public void setCurrentXpath(String currentXpath) {
        this.currentXpath = currentXpath;
    }
}
