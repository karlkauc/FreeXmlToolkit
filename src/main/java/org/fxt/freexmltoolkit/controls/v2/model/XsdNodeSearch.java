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

package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Pure (UI-free) search helper over an {@link XsdNode} tree.
 *
 * <p>Counterpart of {@code XmlCanvasSearch} for the XSD model: the structured
 * views (XSD tree and graph) build their match lists here so the matching
 * semantics stay identical across views and can be unit-tested without a
 * JavaFX runtime.</p>
 */
public final class XsdNodeSearch {

    private XsdNodeSearch() {
        // Utility class
    }

    /**
     * Returns, in document order, all nodes under {@code root} (inclusive) whose
     * searchable text — name, documentation, appinfo, type/ref/fixed/default,
     * enumeration values, facet values or comment content — contains the search
     * text (case-insensitively).
     *
     * @param root       the subtree root, typically the schema; null yields an empty result
     * @param searchText the text to match; null/blank yields an empty result
     * @return matching nodes in document order (never null)
     */
    public static List<XsdNode> findMatches(XsdNode root, String searchText) {
        List<XsdNode> matches = new ArrayList<>();
        if (root == null || searchText == null || searchText.isBlank()) {
            return matches;
        }
        String needle = searchText.toLowerCase();
        Set<XsdNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<XsdNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            if (!visited.add(node)) {
                continue;
            }
            if (matches(node, needle)) {
                matches.add(node);
            }
            List<XsdNode> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return matches;
    }

    /**
     * Tests whether a single node matches an already-lowercased needle.
     *
     * @param node   the node to test
     * @param needle the lowercased search text (must not be null/empty)
     * @return true if any searchable field of the node contains the needle
     */
    static boolean matches(XsdNode node, String needle) {
        if (node == null) {
            return false;
        }
        if (contains(node.getName(), needle) || contains(node.getAppinfoAsString(), needle)) {
            return true;
        }
        for (XsdDocumentation doc : node.getDocumentations()) {
            if (doc != null && contains(doc.getText(), needle)) {
                return true;
            }
        }
        return switch (node) {
            case XsdElement el -> contains(el.getType(), needle)
                    || contains(el.getRef(), needle)
                    || contains(el.getFixed(), needle)
                    || contains(el.getDefaultValue(), needle)
                    || contains(el.getSubstitutionGroup(), needle)
                    || containsAny(el.getEnumerations(), needle);
            case XsdAttribute at -> contains(at.getType(), needle)
                    || contains(at.getRef(), needle)
                    || contains(at.getFixed(), needle)
                    || contains(at.getDefaultValue(), needle);
            case XsdSimpleType st -> contains(st.getBase(), needle);
            case XsdRestriction re -> contains(re.getBase(), needle);
            case XsdExtension ex -> contains(ex.getBase(), needle);
            case XsdFacet fa -> contains(fa.getValue(), needle)
                    || (fa.getFacetType() != null && contains(fa.getFacetType().getXmlName(), needle));
            case XsdComment co -> contains(co.getContent(), needle);
            default -> false;
        };
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    private static boolean containsAny(List<String> haystacks, String needle) {
        if (haystacks == null) {
            return false;
        }
        for (String h : haystacks) {
            if (contains(h, needle)) {
                return true;
            }
        }
        return false;
    }
}
