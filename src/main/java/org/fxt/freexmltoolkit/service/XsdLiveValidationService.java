/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023-2025.
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
 */

package org.fxt.freexmltoolkit.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Live-Validierung für XSD-Schema-Editing mit Echtzeit-Feedback.
 * Diese Klasse fokussiert sich auf strukturelle XSD-Validierung während der Bearbeitung.
 */
public class XsdLiveValidationService {

    private static final Logger logger = LogManager.getLogger(XsdLiveValidationService.class);

    // Singleton instance
    private static XsdLiveValidationService instance;

    // Background validation executor
    private final ExecutorService validationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XSD-Live-Validation");
        t.setDaemon(true);
        return t;
    });

    // Validation listeners
    private final List<ValidationListener> listeners = new ArrayList<>();

    // Validation cache
    private final Map<String, ValidationResult> resultCache = new ConcurrentHashMap<>();

    // XSD Name Pattern (NCName) - mehr restriktiv für bessere Validierung
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.-]*$");

    // XSD Built-in Types für erweiterte Type-Validierung
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time",
            "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary",
            "base64Binary", "anyURI", "QName", "NOTATION", "normalizedString", "token",
            "language", "NMTOKEN", "NMTOKENS", "Name", "NCName", "ID", "IDREF", "IDREFS",
            "ENTITY", "ENTITIES", "integer", "nonPositiveInteger", "negativeInteger", "long",
            "int", "short", "byte", "nonNegativeInteger", "unsignedLong", "unsignedInt",
            "unsignedShort", "unsignedByte", "positiveInteger"
    );

    // Validierungs-Konfiguration
    private boolean liveValidationEnabled = true;
    private final boolean structuralValidation = true;
    private final boolean nameValidation = true;
    private final boolean typeValidation = true;
    private final boolean cardinalityValidation = true;
    private final boolean referenceValidation = true;
    private long validationDelayMs = 500; // Verzögerung für Live-Validierung

    private XsdLiveValidationService() {
        logger.info("XSD Live Validation Service initialized");
    }

    public static synchronized XsdLiveValidationService getInstance() {
        if (instance == null) {
            instance = new XsdLiveValidationService();
        }
        return instance;
    }

    /**
     * Startet Live-Validierung für ein XSD-Document.
     */
    public void validateLive(Document document) {
        if (!liveValidationEnabled || document == null) {
            return;
        }

        // Verzögerte Validierung um UI-Performance nicht zu beeinträchtigen
        Task<ValidationResult> validationTask = new Task<>() {
            @Override
            protected ValidationResult call() throws Exception {
                Thread.sleep(validationDelayMs); // Debouncing
                return validateDocument(document);
            }

            @Override
            protected void succeeded() {
                ValidationResult result = getValue();
                Platform.runLater(() -> notifyListeners(result));
            }

            @Override
            protected void failed() {
                logger.warn("Live validation failed", getException());
            }
        };

        validationExecutor.submit(validationTask);
    }

    /**
     * Validiert ein Element in Echtzeit.
     */
    public void validateElementLive(Element element, Document document) {
        if (!liveValidationEnabled || element == null) {
            return;
        }

        Task<ValidationResult> elementValidationTask = new Task<>() {
            @Override
            protected ValidationResult call() throws Exception {
                Thread.sleep(validationDelayMs / 2); // Schnellere Reaktion für einzelne Elemente
                return validateSingleElement(element, document);
            }

            @Override
            protected void succeeded() {
                ValidationResult result = getValue();
                Platform.runLater(() -> notifyListenersForElement(result, element));
            }

            @Override
            protected void failed() {
                logger.warn("Element live validation failed", getException());
            }
        };

        validationExecutor.submit(elementValidationTask);
    }

    /**
     * Vollständige Dokument-Validierung.
     */
    public ValidationResult validateDocument(Document document) {
        if (document == null) {
            return ValidationResult.empty("Document is null");
        }

        long startTime = System.currentTimeMillis();
        ValidationResult result = new ValidationResult();

        try {
            Element root = document.getDocumentElement();
            if (root == null) {
                result.addError("No root element found", null);
                return result;
            }

            // 1. Schema-Root Validierung
            if (structuralValidation) {
                validateSchemaRoot(root, result);
            }

            // 2. Strukturelle Validierung
            if (structuralValidation) {
                validateStructureRecursive(root, result, new HashSet<>());
            }

            // 3. Namen-Validierung
            if (nameValidation) {
                validateAllNames(document, result);
            }

            // 4. Type-Validierung
            if (typeValidation) {
                validateAllTypes(document, result);
            }

            // 5. Referenz-Validierung
            if (referenceValidation) {
                validateAllReferences(document, result);
            }

            // 6. Cardinality-Validierung
            if (cardinalityValidation) {
                validateAllCardinalities(document, result);
            }

        } catch (Exception e) {
            logger.error("Document validation failed", e);
            result.addError("Validation engine error: " + e.getMessage(), null);
        }

        long duration = System.currentTimeMillis() - startTime;
        result.setValidationTime(duration);

        logger.debug("Document validation completed in {}ms - {} errors, {} warnings",
                duration, result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    /**
     * Validierung eines einzelnen Elements.
     */
    public ValidationResult validateSingleElement(Element element, Document document) {
        ValidationResult result = new ValidationResult();

        if (element == null) {
            result.addError("Element is null", null);
            return result;
        }

        String localName = element.getLocalName();

        // Element-spezifische Validierung
        switch (localName) {
            case "element":
                validateElementDeclaration(element, document, result);
                break;
            case "attribute":
                validateAttributeDeclaration(element, document, result);
                break;
            case "complexType":
                validateComplexTypeDeclaration(element, document, result);
                break;
            case "simpleType":
                validateSimpleTypeDeclaration(element, document, result);
                break;
            case "sequence":
            case "choice":
            case "all":
                validateContentModel(element, result);
                break;
            default:
                validateGenericElement(element, result);
        }

        return result;
    }

    /**
     * Validiert Schema-Root Element.
     */
    private void validateSchemaRoot(Element schema, ValidationResult result) {
        if (!"schema".equals(schema.getLocalName())) {
            result.addError("Root element must be 'schema'", schema);
            return;
        }

        String namespace = schema.getNamespaceURI();
        if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(namespace)) {
            result.addError("Schema must use XML Schema namespace", schema);
        }

        // TargetNamespace empfohlen
        String targetNamespace = schema.getAttribute("targetNamespace");
        if (targetNamespace == null || targetNamespace.trim().isEmpty()) {
            result.addWarning("Consider adding a targetNamespace", schema);
        }

        // ElementFormDefault empfohlen
        String elementFormDefault = schema.getAttribute("elementFormDefault");
        if (!"qualified".equals(elementFormDefault)) {
            result.addInfo("Consider setting elementFormDefault='qualified'", schema);
        }
    }

    /**
     * Rekursive strukturelle Validierung.
     */
    private void validateStructureRecursive(Element element, ValidationResult result, Set<Element> visited) {
        if (visited.contains(element)) {
            return; // Zirkuläre Referenz vermeiden
        }
        visited.add(element);

        // Parent-Child Beziehungen validieren
        String parentName = element.getLocalName();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String childName = childElement.getLocalName();

                // Struktur-Regeln prüfen
                validateParentChildRelationship(parentName, childName, childElement, result);

                // Rekursive Validierung
                validateStructureRecursive(childElement, result, visited);
            }
        }

        visited.remove(element);
    }

    /**
     * Validiert Parent-Child Beziehungen.
     */
    private void validateParentChildRelationship(String parentName, String childName, Element childElement, ValidationResult result) {
        // Skip validation if childName is null
        if (childName == null) {
            return;
        }
        
        // simpleType darf keine Elemente enthalten
        if ("simpleType".equals(parentName) && "element".equals(childName)) {
            result.addError("simpleType cannot contain element declarations", childElement);
        }

        // simpleContent darf keine Elemente enthalten  
        if ("simpleContent".equals(parentName) && "element".equals(childName)) {
            result.addError("simpleContent cannot contain element declarations", childElement);
        }

        // Content Models sollten in complexType sein
        if (Set.of("sequence", "choice", "all").contains(childName)) {
            Element complexTypeAncestor = findAncestorByName(childElement, "complexType");
            if (complexTypeAncestor == null && !"group".equals(parentName)) {
                result.addWarning("Content model should be within complexType or group", childElement);
            }
        }

        // attribute in simpleType ist ungültig
        if ("simpleType".equals(parentName) && "attribute".equals(childName)) {
            result.addError("simpleType cannot contain attribute declarations", childElement);
        }
    }

    /**
     * Validiert alle Namen im Schema.
     */
    private void validateAllNames(Document document, ValidationResult result) {
        NodeList allElements = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "*");

        Set<String> globalElementNames = new HashSet<>();
        Set<String> globalTypeNames = new HashSet<>();
        Set<String> globalAttributeNames = new HashSet<>();

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String name = element.getAttribute("name");

            if (!name.isEmpty()) {
                // Name-Pattern validieren
                if (!NAME_PATTERN.matcher(name).matches()) {
                    result.addError("Invalid XML name: " + name, element);
                }

                // Duplikat-Prüfung
                checkForNameDuplicates(element, name, globalElementNames, globalTypeNames, globalAttributeNames, result);
            }
        }
    }

    /**
     * Validiert alle Type-Referenzen.
     */
    private void validateAllTypes(Document document, ValidationResult result) {
        NodeList allElements = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "*");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            String type = element.getAttribute("type");

            if (!type.isEmpty()) {
                validateTypeReference(type, element, document, result);
            }
        }
    }

    /**
     * Validiert alle Referenzen.
     */
    private void validateAllReferences(Document document, ValidationResult result) {
        // Element-Referenzen
        NodeList elementRefs = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elementRefs.getLength(); i++) {
            Element element = (Element) elementRefs.item(i);
            String ref = element.getAttribute("ref");
            if (!ref.isEmpty() && !findGlobalElement(document, ref)) {
                result.addError("Referenced element not found: " + ref, element);
            }
        }

        // Attribute-Referenzen
        NodeList attributeRefs = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributeRefs.getLength(); i++) {
            Element attribute = (Element) attributeRefs.item(i);
            String ref = attribute.getAttribute("ref");
            if (!ref.isEmpty() && !findGlobalAttribute(document, ref)) {
                result.addError("Referenced attribute not found: " + ref, attribute);
            }
        }
    }

    /**
     * Validiert alle Cardinalities.
     */
    private void validateAllCardinalities(Document document, ValidationResult result) {
        NodeList allElements = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "*");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            validateCardinality(element, result);
        }
    }

    /**
     * Validiert Element-Deklaration.
     */
    private void validateElementDeclaration(Element element, Document document, ValidationResult result) {
        String name = element.getAttribute("name");
        String type = element.getAttribute("type");
        String ref = element.getAttribute("ref");

        // Name XOR Ref erforderlich
        if (name.isEmpty() && ref.isEmpty()) {
            result.addError("Element must have either 'name' or 'ref' attribute", element);
        } else if (!name.isEmpty() && !ref.isEmpty()) {
            result.addError("Element cannot have both 'name' and 'ref' attributes", element);
        }

        // Type-Validierung
        if (!type.isEmpty()) {
            validateTypeReference(type, element, document, result);
        }

        // Cardinality
        validateCardinality(element, result);

        // Nillable nur bei Elementen erlaubt
        String nillable = element.getAttribute("nillable");
        if (!nillable.isEmpty() && !Set.of("true", "false").contains(nillable)) {
            result.addError("Invalid nillable value: " + nillable, element);
        }
    }

    /**
     * Validiert Attribute-Deklaration.
     */
    private void validateAttributeDeclaration(Element attribute, Document document, ValidationResult result) {
        String name = attribute.getAttribute("name");
        String use = attribute.getAttribute("use");
        String defaultValue = attribute.getAttribute("default");
        String fixedValue = attribute.getAttribute("fixed");

        if (name.isEmpty()) {
            result.addError("Attribute must have 'name'", attribute);
        }

        // Use-Validierung
        if (!use.isEmpty() && !Set.of("optional", "required", "prohibited").contains(use)) {
            result.addError("Invalid 'use' value: " + use, attribute);
        }

        // Default/Fixed Konflikte
        if (!defaultValue.isEmpty() && !fixedValue.isEmpty()) {
            result.addError("Attribute cannot have both 'default' and 'fixed' values", attribute);
        }

        if ("prohibited".equals(use) && (!defaultValue.isEmpty() || !fixedValue.isEmpty())) {
            result.addError("Prohibited attribute cannot have default or fixed value", attribute);
        }
    }

    /**
     * Validiert ComplexType-Deklaration.
     */
    private void validateComplexTypeDeclaration(Element complexType, Document document, ValidationResult result) {
        String mixed = complexType.getAttribute("mixed");
        if (!mixed.isEmpty() && !Set.of("true", "false").contains(mixed)) {
            result.addError("Invalid mixed value: " + mixed, complexType);
        }

        String abstractAttr = complexType.getAttribute("abstract");
        if (!abstractAttr.isEmpty() && !Set.of("true", "false").contains(abstractAttr)) {
            result.addError("Invalid abstract value: " + abstractAttr, complexType);
        }

        // Content Model prüfen
        boolean hasContentModel = hasChildElement(complexType, Set.of("sequence", "choice", "all", "simpleContent", "complexContent"));
        if (!hasContentModel) {
            result.addWarning("ComplexType without explicit content model", complexType);
        }
    }

    /**
     * Validiert SimpleType-Deklaration.
     */
    private void validateSimpleTypeDeclaration(Element simpleType, Document document, ValidationResult result) {
        boolean hasDerivation = hasChildElement(simpleType, Set.of("restriction", "union", "list"));
        if (!hasDerivation) {
            result.addError("SimpleType must contain restriction, union, or list", simpleType);
        }
    }

    /**
     * Validiert Content Model (sequence, choice, all).
     */
    private void validateContentModel(Element contentModel, ValidationResult result) {
        String modelType = contentModel.getLocalName();

        validateCardinality(contentModel, result);

        // all-spezifische Validierung
        if ("all".equals(modelType)) {
            String minOccurs = contentModel.getAttribute("minOccurs");
            String maxOccurs = contentModel.getAttribute("maxOccurs");

            if (!minOccurs.isEmpty() && !Set.of("0", "1").contains(minOccurs)) {
                result.addError("'all' minOccurs must be 0 or 1", contentModel);
            }

            if (!maxOccurs.isEmpty() && !"1".equals(maxOccurs)) {
                result.addError("'all' maxOccurs must be 1", contentModel);
            }
        }
    }

    /**
     * Validiert generische Elemente.
     */
    private void validateGenericElement(Element element, ValidationResult result) {
        String name = element.getAttribute("name");
        if (!name.isEmpty() && !NAME_PATTERN.matcher(name).matches()) {
            result.addError("Invalid XML name: " + name, element);
        }
    }

    /**
     * Validiert Cardinality (minOccurs/maxOccurs).
     */
    private void validateCardinality(Element element, ValidationResult result) {
        String minOccurs = element.getAttribute("minOccurs");
        String maxOccurs = element.getAttribute("maxOccurs");

        Integer min = null;
        Integer max = null;

        if (!minOccurs.isEmpty()) {
            try {
                min = Integer.parseInt(minOccurs);
                if (min < 0) {
                    result.addError("minOccurs cannot be negative", element);
                }
            } catch (NumberFormatException e) {
                result.addError("Invalid minOccurs value: " + minOccurs, element);
            }
        }

        if (!maxOccurs.isEmpty() && !"unbounded".equals(maxOccurs)) {
            try {
                max = Integer.parseInt(maxOccurs);
                if (max < 0) {
                    result.addError("maxOccurs cannot be negative", element);
                }
            } catch (NumberFormatException e) {
                result.addError("Invalid maxOccurs value: " + maxOccurs, element);
            }
        }

        // Min <= Max prüfen
        if (min != null && max != null && max < min) {
            result.addError("maxOccurs cannot be less than minOccurs", element);
        }
    }

    /**
     * Validiert Type-Referenz.
     */
    private void validateTypeReference(String type, Element element, Document document, ValidationResult result) {
        String localType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

        if (!BUILTIN_TYPES.contains(localType) && !findCustomType(document, localType)) {
            result.addError("Type not found: " + type, element);
        }
    }

    // === Helper-Methoden ===

    private boolean findGlobalElement(Document document, String name) {
        NodeList elements = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (name.equals(element.getAttribute("name")) && isDirectChildOfSchema(element)) {
                return true;
            }
        }
        return false;
    }

    private boolean findGlobalAttribute(Document document, String name) {
        NodeList attributes = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attribute = (Element) attributes.item(i);
            if (name.equals(attribute.getAttribute("name")) && isDirectChildOfSchema(attribute)) {
                return true;
            }
        }
        return false;
    }

    private boolean findCustomType(Document document, String typeName) {
        // ComplexTypes prüfen
        NodeList complexTypes = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (typeName.equals(complexType.getAttribute("name"))) {
                return true;
            }
        }

        // SimpleTypes prüfen
        NodeList simpleTypes = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (typeName.equals(simpleType.getAttribute("name"))) {
                return true;
            }
        }

        return false;
    }

    private boolean isDirectChildOfSchema(Element element) {
        Node parent = element.getParentNode();
        return parent != null && parent.getNodeType() == Node.ELEMENT_NODE &&
                "schema".equals(parent.getLocalName());
    }

    private Element findAncestorByName(Element element, String ancestorName) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (ancestorName.equals(parentElement.getLocalName())) {
                return parentElement;
            }
            parent = parent.getParentNode();
        }
        return null;
    }

    private boolean hasChildElement(Element parent, Set<String> childNames) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) children.item(i);
                if (childNames.contains(child.getLocalName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkForNameDuplicates(Element element, String name,
                                        Set<String> globalElementNames,
                                        Set<String> globalTypeNames,
                                        Set<String> globalAttributeNames,
                                        ValidationResult result) {
        String elementType = element.getLocalName();

        switch (elementType) {
            case "element":
                if (isDirectChildOfSchema(element)) {
                    if (globalElementNames.contains(name)) {
                        result.addError("Duplicate global element name: " + name, element);
                    } else {
                        globalElementNames.add(name);
                    }
                }
                break;
            case "complexType":
            case "simpleType":
                if (globalTypeNames.contains(name)) {
                    result.addError("Duplicate global type name: " + name, element);
                } else {
                    globalTypeNames.add(name);
                }
                break;
            case "attribute":
                if (isDirectChildOfSchema(element)) {
                    if (globalAttributeNames.contains(name)) {
                        result.addError("Duplicate global attribute name: " + name, element);
                    } else {
                        globalAttributeNames.add(name);
                    }
                }
                break;
        }
    }

    // === Listener Management ===

    public void addValidationListener(ValidationListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeValidationListener(ValidationListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(ValidationResult result) {
        synchronized (listeners) {
            for (ValidationListener listener : listeners) {
                try {
                    listener.onValidationComplete(result);
                } catch (Exception e) {
                    logger.warn("Validation listener failed", e);
                }
            }
        }
    }

    private void notifyListenersForElement(ValidationResult result, Element element) {
        synchronized (listeners) {
            for (ValidationListener listener : listeners) {
                try {
                    listener.onElementValidated(result, element);
                } catch (Exception e) {
                    logger.warn("Validation listener failed", e);
                }
            }
        }
    }

    // === Configuration ===

    public void setLiveValidationEnabled(boolean enabled) {
        this.liveValidationEnabled = enabled;
        logger.debug("Live validation {}", enabled ? "enabled" : "disabled");
    }

    public void setValidationDelayMs(long delayMs) {
        this.validationDelayMs = Math.max(100, delayMs);
    }

    public boolean isLiveValidationEnabled() {
        return liveValidationEnabled;
    }

    public void shutdown() {
        validationExecutor.shutdown();
    }

    // === Nested Classes ===

    public interface ValidationListener {
        void onValidationComplete(ValidationResult result);

        default void onElementValidated(ValidationResult result, Element element) {
            // Optional implementation
        }
    }

    /**
     * Live Validation Result Container.
     */
    public static class ValidationResult {
        private final List<ValidationIssue> errors = new ArrayList<>();
        private final List<ValidationIssue> warnings = new ArrayList<>();
        private final List<ValidationIssue> infos = new ArrayList<>();
        private long validationTime = 0;

        public static ValidationResult empty(String message) {
            ValidationResult result = new ValidationResult();
            result.addInfo(message, null);
            return result;
        }

        public void addError(String message, Element element) {
            errors.add(new ValidationIssue(ValidationIssue.Severity.ERROR, message, element));
        }

        public void addWarning(String message, Element element) {
            warnings.add(new ValidationIssue(ValidationIssue.Severity.WARNING, message, element));
        }

        public void addInfo(String message, Element element) {
            infos.add(new ValidationIssue(ValidationIssue.Severity.INFO, message, element));
        }

        // Getters
        public List<ValidationIssue> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<ValidationIssue> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public List<ValidationIssue> getInfos() {
            return Collections.unmodifiableList(infos);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void setValidationTime(long validationTime) {
            this.validationTime = validationTime;
        }

        public long getValidationTime() {
            return validationTime;
        }

        public List<ValidationIssue> getAllIssues() {
            List<ValidationIssue> all = new ArrayList<>();
            all.addAll(errors);
            all.addAll(warnings);
            all.addAll(infos);
            return all;
        }
    }

    /**
         * Validierung Issue.
         */
        public record ValidationIssue(Severity severity, String message, Element element) {
            public enum Severity {ERROR, WARNING, INFO}

        @Override
            public String toString() {
                return String.format("[%s] %s", severity, message);
            }
        }
}