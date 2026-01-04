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

package org.fxt.freexmltoolkit.controls.v2.common.utilities;

import javafx.scene.control.Alert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ValidationError;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * Utility class for XML validation operations.
 *
 * <p>Provides functionality for validating XML against XSD schemas,
 * extracting element information from XSD files, and displaying validation results.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlValidationHelper {
    private static final Logger logger = LogManager.getLogger(XmlValidationHelper.class);

    private XmlValidationHelper() {
        // Utility class - no instantiation
    }

    /**
     * Converts a SAXParseException to a structured ValidationError.
     *
     * @param saxException the SAX exception
     * @return structured validation error
     */
    public static ValidationError convertToValidationError(SAXParseException saxException) {
        int lineNumber = saxException.getLineNumber();
        int columnNumber = saxException.getColumnNumber();
        String message = saxException.getMessage();

        // Clean up common error message patterns for better readability
        if (message != null) {
            // Remove common prefixes that are not useful for end users
            message = message.replaceAll("^cvc-[^:]*:\\s*", "");
            message = message.replaceAll("^The content of element '[^']*' is not complete\\.", "Content is incomplete.");
        }

        return new ValidationError(lineNumber, columnNumber, message, "ERROR");
    }

    /**
     * Shows a validation result alert dialog.
     *
     * @param alertType the type of alert
     * @param title the alert title
     * @param message the message to display
     */
    public static void showValidationAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(450);
        alert.showAndWait();
    }

    /**
     * Extracts element names from an XSD file for completion suggestions.
     *
     * @param xsdFile the XSD file to extract element names from
     * @return list of element names found in the XSD
     */
    public static List<String> extractElementNamesFromXsd(File xsdFile) {
        List<String> elementNames = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            // Find all element definitions
            NodeList elementNodes = document.getElementsByTagName("xs:element");
            for (int i = 0; i < elementNodes.getLength(); i++) {
                Element element = (Element) elementNodes.item(i);
                String name = element.getAttribute("name");
                if (!name.isEmpty()) {
                    elementNames.add(name);
                }
            }

            // Also find elements in other namespaces
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                if (element.getTagName().endsWith(":element")) {
                    String name = element.getAttribute("name");
                    if (!name.isEmpty() && !elementNames.contains(name)) {
                        elementNames.add(name);
                    }
                }
            }

            logger.debug("Extracted {} element names from XSD: {}", elementNames.size(), elementNames);

        } catch (Exception e) {
            logger.error("Error extracting element names from XSD: {}", xsdFile.getAbsolutePath(), e);
            // Add some default element names as fallback
            elementNames.addAll(Arrays.asList("root", "element", "item", "data", "content"));
        }

        return elementNames;
    }

    /**
     * Extracts context-sensitive element names (parent-child relationships) from XSD file.
     * Uses simple DOM parsing to get mandatory children only.
     *
     * @param xsdFile the XSD file to extract context information from
     * @return map of parent element names to their mandatory child element names only
     */
    public static Map<String, List<String>> extractContextElementNamesFromXsd(File xsdFile) {
        Map<String, List<String>> contextElementNames = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            // Find all complex types that define element structures
            NodeList complexTypes = document.getElementsByTagName("xs:complexType");
            for (int i = 0; i < complexTypes.getLength(); i++) {
                Element complexType = (Element) complexTypes.item(i);
                String typeName = complexType.getAttribute("name");

                if (!typeName.isEmpty()) {
                    // Find mandatory child elements within this complex type
                    List<String> mandatoryChildren = new ArrayList<>();
                    extractMandatoryChildrenFromComplexType(complexType, mandatoryChildren);

                    if (!mandatoryChildren.isEmpty()) {
                        contextElementNames.put(typeName, mandatoryChildren);
                        logger.debug("Found {} mandatory children for type '{}': {}",
                                mandatoryChildren.size(), typeName, mandatoryChildren);
                    }
                }
            }

            // Find direct element definitions and their relationships
            NodeList allElements = document.getElementsByTagName("xs:element");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String elementName = element.getAttribute("name");
                String elementType = element.getAttribute("type");

                if (!elementName.isEmpty() && !elementType.isEmpty()) {
                    // If this element has a type, check if we have mandatory children for that type
                    if (!elementType.startsWith("xs:")) {
                        List<String> childElements = contextElementNames.get(elementType);
                        if (childElements != null && !childElements.isEmpty()) {
                            contextElementNames.put(elementName, new ArrayList<>(childElements));
                            logger.debug("Mapped element '{}' to type '{}' with {} children",
                                    elementName, elementType, childElements.size());
                        }
                    }
                }
            }

            // Add root-level elements
            List<String> rootElements = new ArrayList<>();
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String elementName = element.getAttribute("name");
                if (!elementName.isEmpty()) {
                    rootElements.add(elementName);
                }
            }
            if (!rootElements.isEmpty()) {
                contextElementNames.put("root", rootElements);
            }

            logger.debug("Extracted context element names from XSD: {}", contextElementNames);

        } catch (Exception e) {
            logger.error("Error extracting context element names from XSD: {}", xsdFile.getAbsolutePath(), e);
            // Add some default fallback
            contextElementNames.put("ControlData", List.of("UniqueDocumentID", "DocumentGenerated", "ContentDate", "DataSupplier", "DataOperation", "Language"));
            contextElementNames.put("DataSupplier", List.of("SystemCountry", "Short", "Name", "Type"));
        }

        return contextElementNames;
    }

    /**
     * Extracts mandatory children from a complex type element.
     *
     * @param complexType the complex type element
     * @param mandatoryChildren list to populate with mandatory children
     */
    public static void extractMandatoryChildrenFromComplexType(Element complexType, List<String> mandatoryChildren) {
        // Look for sequence, choice, or all elements
        NodeList sequences = complexType.getElementsByTagName("xs:sequence");
        NodeList choices = complexType.getElementsByTagName("xs:choice");
        NodeList alls = complexType.getElementsByTagName("xs:all");

        // Process sequences (most common)
        processElementsForMandatory(sequences, mandatoryChildren);
        // Process choices
        processElementsForMandatory(choices, mandatoryChildren);
        // Process alls
        processElementsForMandatory(alls, mandatoryChildren);
    }

    /**
     * Processes element groups to extract mandatory children.
     *
     * @param elementGroups the element groups to process
     * @param mandatoryChildren list to populate with mandatory children
     */
    public static void processElementsForMandatory(NodeList elementGroups, List<String> mandatoryChildren) {
        for (int j = 0; j < elementGroups.getLength(); j++) {
            Element group = (Element) elementGroups.item(j);
            NodeList elements = group.getElementsByTagName("xs:element");

            for (int k = 0; k < elements.getLength(); k++) {
                Element element = (Element) elements.item(k);
                String elementName = element.getAttribute("name");
                String minOccurs = element.getAttribute("minOccurs");

                // Only add if mandatory (minOccurs is 0 or not specified, default is 1)
                if (isMandatoryFromMinOccurs(minOccurs)) {
                    if (!mandatoryChildren.contains(elementName)) {
                        mandatoryChildren.add(elementName);
                    }
                }
            }
        }
    }

    /**
     * Checks if an element is mandatory based on its minOccurs attribute.
     *
     * @param minOccurs the minOccurs attribute value (empty string means default 1)
     * @return true if element is mandatory
     */
    public static boolean isMandatoryFromMinOccurs(String minOccurs) {
        if (minOccurs == null || minOccurs.isEmpty()) {
            // Default is 1 (mandatory)
            return true;
        }
        try {
            int min = Integer.parseInt(minOccurs);
            return min > 0;
        } catch (NumberFormatException e) {
            // If we can't parse, assume mandatory
            return true;
        }
    }

}
