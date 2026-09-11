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

/**
 * Resolves a named simple or complex type to the built-in XML Schema type it derives from, merging the facets along
 * the derivation chain. The schema processing service provides one that follows every schema document and namespace,
 * so generators that only see {@link XsdDocumentationData} resolve types as far as the service does.
 */
@FunctionalInterface
public interface NamedTypeResolver {

    /**
     * @param typeName the type name, possibly prefixed
     * @return the built-in base type and the merged restriction, or {@code null} if the type is unknown
     */
    Resolution resolve(String typeName);

    /**
     * A resolved type.
     *
     * @param baseType    the built-in base type, for example {@code xs:NMTOKEN}
     * @param restriction the facets merged along the derivation chain, or {@code null}
     */
    record Resolution(String baseType, XsdExtendedElement.RestrictionInfo restriction) {
    }
}
