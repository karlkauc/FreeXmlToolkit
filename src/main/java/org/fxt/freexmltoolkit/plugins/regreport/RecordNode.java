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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An element of a report record, read by {@link RecordReader}: local name, attributes, trimmed text,
 * children and the line of its start tag. Rules query it with simple relative paths of local names
 * ({@code "AIFCompleteDescription/AIFPrincipalInfo/AIFBaseCurrencyDescription/BaseCurrency"}); a
 * step {@code "*"} matches any child.
 */
public final class RecordNode {

    private final String name;
    private final int line;
    private final RecordNode parent;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<RecordNode> children = new ArrayList<>();
    private String text = "";

    RecordNode(String name, int line, RecordNode parent) {
        this.name = name;
        this.line = line;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    /** @return a node built in code (tests, rules that synthesise records) */
    public static RecordNode of(String name, int line) {
        return new RecordNode(name, line, null);
    }

    /** @return a new child (building in code) */
    public RecordNode add(String childName, int childLine, String childText) {
        RecordNode child = new RecordNode(childName, childLine, this);
        child.text = childText == null ? "" : childText;
        return child;
    }

    void setText(String text) {
        this.text = text;
    }

    void attribute(String key, String value) {
        attributes.put(key, value);
    }

    /** @return the local name */
    public String name() {
        return name;
    }

    /** @return the line of the start tag (1-based) */
    public int line() {
        return line;
    }

    /** @return the parent inside the record, or {@code null} for the record itself */
    public RecordNode parent() {
        return parent;
    }

    /** @return the trimmed text of a leaf (empty for containers) */
    public String text() {
        return text;
    }

    /** @return the attributes */
    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /** @return the child elements */
    public List<RecordNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** @return all nodes at a relative path, in document order */
    public List<RecordNode> all(String path) {
        List<RecordNode> current = List.of(this);
        if (path == null || path.isEmpty()) {
            return current;
        }
        for (String step : path.split("/")) {
            List<RecordNode> next = new ArrayList<>();
            for (RecordNode node : current) {
                for (RecordNode child : node.children) {
                    if (step.equals("*") || step.equals(child.name)) {
                        next.add(child);
                    }
                }
            }
            current = next;
            if (current.isEmpty()) {
                break;
            }
        }
        return current;
    }

    /** @return the first node at a relative path, or {@code null} */
    public RecordNode first(String path) {
        List<RecordNode> nodes = all(path);
        return nodes.isEmpty() ? null : nodes.getFirst();
    }

    /** @return whether a node exists at the path */
    public boolean has(String path) {
        return first(path) != null;
    }

    /** @return whether a node with non-blank text (or with children) exists at the path */
    public boolean filled(String path) {
        for (RecordNode node : all(path)) {
            if (!node.text.isEmpty() || !node.children.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** @return the text of the first node at the path, empty when there is none */
    public String text(String path) {
        RecordNode node = first(path);
        return node == null ? "" : node.text;
    }

    /** @return the decimal value at the path, or {@code null} when absent or not a number */
    public BigDecimal decimal(String path) {
        String value = text(path);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the line of the first node at the path, else this node's line */
    public int lineOf(String path) {
        RecordNode node = first(path);
        return node == null ? line : node.line;
    }

    /** @return the path of local names from the record down to this node ({@code AIFRecordInfo/FilingType}) */
    public String path() {
        return parent == null ? name : parent.path() + "/" + name;
    }

    @Override
    public String toString() {
        return name + (text.isEmpty() ? "" : "=" + text) + "@" + line;
    }
}
