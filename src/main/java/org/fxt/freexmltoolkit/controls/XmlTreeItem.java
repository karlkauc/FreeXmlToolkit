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

package org.fxt.freexmltoolkit.controls;

import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

public class XmlTreeItem extends TreeItem<Node> {

    private final static Logger logger = LogManager.getLogger(XmlTreeItem.class);

    public XmlTreeItem(Node node) {
        super(node);

        if (node.hasChildNodes()) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node temp = node.getChildNodes().item(i);

                switch (temp.getNodeType()) {
                    case Node.ELEMENT_NODE -> {
                        logger.debug("ELEMENT NODE: {}", temp.getNodeName());
                        if (temp.hasChildNodes()) {
                            logger.debug("CHILD NODE value: {}", temp.getFirstChild().getNodeValue());
                            logger.debug("CHILD NODE type: {}", temp.getFirstChild().getNodeType());
                            logger.debug("CHILD NODE text: {}", temp.getFirstChild().getTextContent());
                        }
                    }
                    case Node.ATTRIBUTE_NODE -> logger.debug("Attribute Node: {}", temp.getAttributes());
                    case Node.TEXT_NODE -> {
                        logger.debug("Text Node: {}", temp.getTextContent());
                    }
                    case Node.COMMENT_NODE -> logger.debug("Comment Node: {}", temp.getNodeValue());
                    case Node.DOCUMENT_NODE -> logger.debug("Document Node: {}", temp.getTextContent());
                }

                if (temp.hasAttributes()) {
                    for (int x = 0; x < temp.getAttributes().getLength(); x++) {
                        var att = temp.getAttributes().item(x);
                        logger.debug("ATTRIBUTES: {} - {}", att.getNodeName(), att.getTextContent());
                        this.getChildren().add(new XmlTreeItem(att));
                    }
                }


                if (temp.getNodeType() == Node.ELEMENT_NODE) {
                    this.getChildren().add(new XmlTreeItem(temp));
                }
            }
        }
    }
}
