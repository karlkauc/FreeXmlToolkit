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
 * Information about an XPath extracted from an XSD schema.
 * Used to auto-populate the XPath rules table in the UI.
 *
 * @param xpath       the full XPath expression (e.g., "/order/customer/name")
 * @param typeName    the XSD type name (e.g., "xs:string", "CustomerType")
 * @param mandatory   whether this element/attribute is required (minOccurs >= 1 or use="required")
 * @param isAttribute whether this is an attribute (true) or element (false)
 * @param schemaOrder the position in the XSD document (for preserving document order)
 */
public record XPathInfo(String xpath, String typeName, boolean mandatory, boolean isAttribute, int schemaOrder) {
}
