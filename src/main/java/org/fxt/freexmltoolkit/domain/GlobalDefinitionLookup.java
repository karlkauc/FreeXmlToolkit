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

/**
 * Finds the global declaration a QName in a schema refers to, resolving its prefix at the node that carries the
 * reference (the same lookup the schema processing service uses while building the documentation data).
 */
@FunctionalInterface
public interface GlobalDefinitionLookup {

    /**
     * @param kind        the declaration's local name: {@code simpleType}, {@code complexType}, {@code element},
     *                    {@code attribute}
     * @param qName       the reference, possibly prefixed
     * @param contextNode the node carrying the reference, whose in-scope namespaces resolve the prefix
     * @return the global declaration, or {@code null} when the schema has none
     */
    Node find(String kind, String qName, Node contextNode);
}
