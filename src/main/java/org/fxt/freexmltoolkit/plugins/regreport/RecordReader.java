/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads a report in one StAX pass and hands over its records one at a time: a record is an
 * element at one of the given paths (local names from the root, e.g.
 * {@code AIFReportingInfo/AIFRecordInfo}); its subtree is built as {@link RecordNode}s and dropped
 * after the callback, so memory stays bounded by the largest record. The root element (with its
 * attributes, but without the records) is handed over first as the document header.
 */
public final class RecordReader {

    private RecordReader() {
    }

    /**
     * @param recordPaths the paths of the record elements (namespaces are ignored)
     * @param header      receives the root element (attributes, line) before the first record
     * @param record      receives each record
     * @throws XMLStreamException when the text is not well-formed
     */
    public static void read(String xml, Set<String> recordPaths, Consumer<RecordNode> header,
                            Consumer<RecordNode> record) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        Deque<String> path = new ArrayDeque<>();
        RecordNode current = null;
        RecordNode recordRoot = null;
        StringBuilder text = new StringBuilder();
        boolean headerSent = false;
        RecordNode root = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    path.addLast(name);
                    int line = reader.getLocation().getLineNumber();
                    if (path.size() == 1) {
                        root = RecordNode.of(name, line);
                        copyAttributes(reader, root);
                    }
                    if (current != null) {
                        current = new RecordNode(name, line, current);
                        copyAttributes(reader, current);
                    } else if (recordPaths.contains(String.join("/", path))) {
                        if (!headerSent) {
                            header.accept(root);
                            headerSent = true;
                        }
                        recordRoot = RecordNode.of(name, line);
                        copyAttributes(reader, recordRoot);
                        current = recordRoot;
                    }
                    text.setLength(0);
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (current != null) {
                        text.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (current != null) {
                        if (current.children().isEmpty()) {
                            current.setText(text.toString().strip());
                        }
                        text.setLength(0);
                        if (current == recordRoot) {
                            record.accept(recordRoot);
                            current = null;
                            recordRoot = null;
                        } else {
                            current = current.parent();
                        }
                    }
                    path.removeLast();
                }
            }
        } finally {
            reader.close();
        }
        if (!headerSent && root != null) {
            header.accept(root);
        }
    }

    private static void copyAttributes(XMLStreamReader reader, RecordNode node) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            node.attribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
    }
}
