package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for XML templates with comprehensive built-in template library.
 * Manages template storage, retrieval, and provides industry-specific templates.
 */
public class TemplateRepository {

    private static final Logger logger = LogManager.getLogger(TemplateRepository.class);

    // Singleton instance
    private static TemplateRepository instance;

    // Template storage
    private final Map<String, XmlTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<XmlTemplate>> templatesByCategory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categoriesByIndustry = new ConcurrentHashMap<>();

    // Template search index
    private final Map<String, Set<String>> searchIndex = new ConcurrentHashMap<>();

    // Template usage statistics
    private final Map<String, Integer> usageStatistics = new ConcurrentHashMap<>();

    // File service for template persistence
    private final TemplateFileService templateFileService = TemplateFileService.getInstance();

    private TemplateRepository() {
        initializeBuiltInTemplates();
        loadTemplatesFromDirectory();
        buildSearchIndex();
        logger.info("Template Repository initialized with {} templates ({} from directory)",
                templates.size(), countNonBuiltInTemplates());
    }

    public static synchronized TemplateRepository getInstance() {
        if (instance == null) {
            instance = new TemplateRepository();
        }
        return instance;
    }

    // ========== Template Management ==========

    /**
     * Add template to repository
     */
    public void addTemplate(XmlTemplate template) {
        addTemplate(template, false);
    }

    /**
     * Add template to repository with option to save to file
     */
    public void addTemplate(XmlTemplate template, boolean saveToFile) {
        if (template == null || template.getId() == null) {
            return;
        }

        templates.put(template.getId(), template);
        addToCategory(template);
        updateSearchIndex(template);

        // Save to file if requested and template is not built-in
        if (saveToFile && !template.isBuiltIn()) {
            try {
                templateFileService.saveTemplateToDirectory(template);
                logger.debug("Saved template '{}' to file", template.getName());
            } catch (Exception e) {
                logger.error("Failed to save template '{}' to file: {}", template.getName(), e.getMessage());
            }
        }

        logger.debug("Added template '{}' to repository", template.getName());
    }

    /**
     * Remove template from repository
     */
    public boolean removeTemplate(String templateId) {
        return removeTemplate(templateId, false);
    }

    /**
     * Remove template from repository with option to delete from file
     */
    public boolean removeTemplate(String templateId, boolean deleteFromFile) {
        XmlTemplate template = templates.remove(templateId);
        if (template != null) {
            removeFromCategory(template);
            removeFromSearchIndex(template);

            // Delete from file if requested and template is not built-in
            if (deleteFromFile && !template.isBuiltIn()) {
                boolean deleted = templateFileService.deleteTemplateFromDirectory(templateId);
                if (deleted) {
                    logger.debug("Deleted template '{}' file", template.getName());
                }
            }
            
            logger.debug("Removed template '{}' from repository", template.getName());
            return true;
        }
        return false;
    }

    /**
     * Get template by ID
     */
    public XmlTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * Get all templates
     */
    public List<XmlTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Get templates by category
     */
    public List<XmlTemplate> getTemplatesByCategory(String category) {
        return templatesByCategory.getOrDefault(category, new ArrayList<>());
    }

    /**
     * Get all categories
     */
    public Set<String> getAllCategories() {
        return new HashSet<>(templatesByCategory.keySet());
    }

    /**
     * Search templates by keyword
     */
    public List<XmlTemplate> searchTemplates(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllTemplates();
        }

        String lowerKeyword = keyword.toLowerCase().trim();
        Set<String> matchingIds = new HashSet<>();

        // Search in index
        for (Map.Entry<String, Set<String>> entry : searchIndex.entrySet()) {
            if (entry.getKey().contains(lowerKeyword)) {
                matchingIds.addAll(entry.getValue());
            }
        }

        // Return matching templates sorted by relevance
        return matchingIds.stream()
                .map(templates::get)
                .filter(Objects::nonNull)
                .sorted((t1, t2) -> Integer.compare(
                        usageStatistics.getOrDefault(t2.getId(), 0),
                        usageStatistics.getOrDefault(t1.getId(), 0)))
                .collect(Collectors.toList());
    }

    /**
     * Get contextual templates
     */
    public List<XmlTemplate> getContextualTemplates(XmlTemplate.TemplateContext context,
                                                    String currentElement,
                                                    Set<String> availableNamespaces) {
        return templates.values().stream()
                .filter(template -> template.isApplicableInContext(context, currentElement, availableNamespaces))
                .sorted((t1, t2) -> Double.compare(
                        t2.calculateRelevanceScore(context, currentElement, availableNamespaces),
                        t1.calculateRelevanceScore(context, currentElement, availableNamespaces)))
                .collect(Collectors.toList());
    }

    // ========== Built-in Templates Initialization ==========

    private void initializeBuiltInTemplates() {
        // Basic XML Templates
        addBasicXmlTemplates();

        // Web Services Templates
        addWebServiceTemplates();

        // Configuration Templates
        addConfigurationTemplates();

        // Documentation Templates
        addDocumentationTemplates();

        // Industry-specific Templates
        addFinanceTemplates();
        addHealthcareTemplates();
        addAutomotiveTemplates();
        addGovernmentTemplates();

        // Schema and Validation Templates
        addSchemaTemplates();

        // Transformation Templates
        addTransformationTemplates();
    }

    private void addBasicXmlTemplates() {
        // Simple Element Template
        XmlTemplate elementTemplate = new XmlTemplate("simple-element",
                "<${elementName}>${content}</${elementName}>", "Basic");
        elementTemplate.setDescription("Simple XML element with content");
        elementTemplate.addParameter(TemplateParameter.requiredString("elementName")
                .placeholder("Enter element name")
                .pattern("[a-zA-Z][a-zA-Z0-9_-]*"));
        elementTemplate.addParameter(TemplateParameter.stringParam("content", "")
                .placeholder("Enter element content"));
        elementTemplate.getContexts().add(XmlTemplate.TemplateContext.CHILD_ELEMENT);
        elementTemplate.setBuiltIn(true);
        addTemplate(elementTemplate);

        // Element with Attributes Template
        XmlTemplate elementWithAttrs = new XmlTemplate("element-with-attributes",
                "<${elementName}{{#if attribute1}} ${attribute1}=\"${attributeValue1}\"{{/if}}{{#if attribute2}} ${attribute2}=\"${attributeValue2}\"{{/if}}>${content}</${elementName}>",
                "Basic");
        elementWithAttrs.setDescription("XML element with optional attributes");
        elementWithAttrs.addParameter(TemplateParameter.requiredString("elementName"));
        elementWithAttrs.addParameter(TemplateParameter.stringParam("content", ""));
        elementWithAttrs.addParameter(TemplateParameter.stringParam("attribute1", ""));
        elementWithAttrs.addParameter(TemplateParameter.stringParam("attributeValue1", ""));
        elementWithAttrs.addParameter(TemplateParameter.stringParam("attribute2", ""));
        elementWithAttrs.addParameter(TemplateParameter.stringParam("attributeValue2", ""));
        elementWithAttrs.setBuiltIn(true);
        addTemplate(elementWithAttrs);

        // CDATA Section Template
        XmlTemplate cdataTemplate = new XmlTemplate("cdata-section",
                "<${elementName}><![CDATA[${content}]]></${elementName}>", "Basic");
        cdataTemplate.setDescription("Element with CDATA section");
        cdataTemplate.addParameter(TemplateParameter.requiredString("elementName"));
        cdataTemplate.addParameter(TemplateParameter.stringParam("content", "")
                .placeholder("Enter CDATA content"));
        cdataTemplate.setBuiltIn(true);
        addTemplate(cdataTemplate);

        // Comment Template
        XmlTemplate commentTemplate = new XmlTemplate("xml-comment",
                "<!-- ${comment} -->", "Basic");
        commentTemplate.setDescription("XML comment");
        commentTemplate.addParameter(TemplateParameter.requiredString("comment")
                .placeholder("Enter comment text"));
        commentTemplate.getContexts().add(XmlTemplate.TemplateContext.COMMENT);
        commentTemplate.setBuiltIn(true);
        addTemplate(commentTemplate);
    }

    private void addWebServiceTemplates() {
        // SOAP Envelope Template
        XmlTemplate soapEnvelope = new XmlTemplate("soap-envelope",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<soap:Envelope\n" +
                        "    xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                        "    xmlns:tns=\"${targetNamespace}\">\n" +
                        "    <soap:Header>\n" +
                        "        <!-- Header elements -->\n" +
                        "    </soap:Header>\n" +
                        "    <soap:Body>\n" +
                        "        <tns:${operationName}>\n" +
                        "            ${parameters}\n" +
                        "        </tns:${operationName}>\n" +
                        "    </soap:Body>\n" +
                        "</soap:Envelope>", "Web Services");
        soapEnvelope.setDescription("SOAP envelope template");
        soapEnvelope.addParameter(TemplateParameter.requiredString("targetNamespace"));
        soapEnvelope.addParameter(TemplateParameter.requiredString("operationName"));
        soapEnvelope.addParameter(TemplateParameter.stringParam("parameters", "<!-- Parameters here -->"));
        soapEnvelope.setIndustry(XmlTemplate.TemplateIndustry.WEB_SERVICES);
        soapEnvelope.setBuiltIn(true);
        addTemplate(soapEnvelope);

        // REST API Response Template
        XmlTemplate restResponse = new XmlTemplate("rest-response",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<response>\n" +
                        "    <status>${status}</status>\n" +
                        "    <message>${message}</message>\n" +
                        "    <data>\n" +
                        "        ${data}\n" +
                        "    </data>\n" +
                        "    <timestamp>${timestamp}</timestamp>\n" +
                        "</response>", "Web Services");
        restResponse.setDescription("REST API response template");
        restResponse.addParameter(TemplateParameter.enumParam("status", "success", "error", "warning"));
        restResponse.addParameter(TemplateParameter.stringParam("message", ""));
        restResponse.addParameter(TemplateParameter.stringParam("data", ""));
        restResponse.addParameter(TemplateParameter.stringParam("timestamp", "").withAutoGenerate(true)
                .withValueGenerator((current, params) -> java.time.Instant.now().toString()));
        restResponse.setBuiltIn(true);
        addTemplate(restResponse);

        // WSDL Service Template
        XmlTemplate wsdlService = new XmlTemplate("wsdl-service",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<definitions\n" +
                        "    xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                        "    xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                        "    xmlns:tns=\"${targetNamespace}\"\n" +
                        "    targetNamespace=\"${targetNamespace}\">\n" +
                        "\n" +
                        "    <types>\n" +
                        "        <xsd:schema targetNamespace=\"${targetNamespace}\">\n" +
                        "            <!-- Type definitions -->\n" +
                        "        </xsd:schema>\n" +
                        "    </types>\n" +
                        "\n" +
                        "    <message name=\"${operationName}Request\">\n" +
                        "        <!-- Request parameters -->\n" +
                        "    </message>\n" +
                        "\n" +
                        "    <message name=\"${operationName}Response\">\n" +
                        "        <!-- Response parameters -->\n" +
                        "    </message>\n" +
                        "\n" +
                        "    <portType name=\"${serviceName}PortType\">\n" +
                        "        <operation name=\"${operationName}\">\n" +
                        "            <input message=\"tns:${operationName}Request\"/>\n" +
                        "            <output message=\"tns:${operationName}Response\"/>\n" +
                        "        </operation>\n" +
                        "    </portType>\n" +
                        "\n" +
                        "    <binding name=\"${serviceName}Binding\" type=\"tns:${serviceName}PortType\">\n" +
                        "        <soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                        "        <operation name=\"${operationName}\">\n" +
                        "            <soap:operation soapAction=\"${soapAction}\"/>\n" +
                        "            <input><soap:body use=\"literal\"/></input>\n" +
                        "            <output><soap:body use=\"literal\"/></output>\n" +
                        "        </operation>\n" +
                        "    </binding>\n" +
                        "\n" +
                        "    <service name=\"${serviceName}\">\n" +
                        "        <port name=\"${serviceName}Port\" binding=\"tns:${serviceName}Binding\">\n" +
                        "            <soap:address location=\"${serviceUrl}\"/>\n" +
                        "        </port>\n" +
                        "    </service>\n" +
                        "\n" +
                        "</definitions>", "Web Services");
        wsdlService.setDescription("WSDL service definition template");
        wsdlService.addParameter(TemplateParameter.requiredString("serviceName"));
        wsdlService.addParameter(TemplateParameter.requiredString("operationName"));
        wsdlService.addParameter(TemplateParameter.requiredString("targetNamespace"));
        wsdlService.addParameter(TemplateParameter.requiredString("soapAction"));
        wsdlService.addParameter(TemplateParameter.requiredString("serviceUrl"));
        wsdlService.setComplexity(XmlTemplate.TemplateComplexity.COMPLEX);
        wsdlService.setBuiltIn(true);
        addTemplate(wsdlService);
    }

    private void addConfigurationTemplates() {
        // Spring Configuration Template
        XmlTemplate springConfig = new XmlTemplate("spring-config",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "       xmlns:context=\"http://www.springframework.org/schema/context\"\n" +
                        "       xsi:schemaLocation=\"\n" +
                        "           http://www.springframework.org/schema/beans\n" +
                        "           http://www.springframework.org/schema/beans/spring-beans.xsd\n" +
                        "           http://www.springframework.org/schema/context\n" +
                        "           http://www.springframework.org/schema/context/spring-context.xsd\">\n" +
                        "\n" +
                        "    <context:component-scan base-package=\"${basePackage}\"/>\n" +
                        "\n" +
                        "    <bean id=\"${beanId}\" class=\"${beanClass}\">\n" +
                        "        <property name=\"${propertyName}\" value=\"${propertyValue}\"/>\n" +
                        "    </bean>\n" +
                        "\n" +
                        "</beans>", "Configuration");
        springConfig.setDescription("Spring Framework configuration");
        springConfig.addParameter(TemplateParameter.requiredString("basePackage"));
        springConfig.addParameter(TemplateParameter.requiredString("beanId"));
        springConfig.addParameter(TemplateParameter.requiredString("beanClass"));
        springConfig.addParameter(TemplateParameter.stringParam("propertyName", ""));
        springConfig.addParameter(TemplateParameter.stringParam("propertyValue", ""));
        springConfig.getRelatedStandards().add("Spring Framework");
        springConfig.setBuiltIn(true);
        addTemplate(springConfig);

        // Maven POM Template
        XmlTemplate mavenPom = new XmlTemplate("maven-pom",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                        "                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                        "    <modelVersion>4.0.0</modelVersion>\n" +
                        "\n" +
                        "    <groupId>${groupId}</groupId>\n" +
                        "    <artifactId>${artifactId}</artifactId>\n" +
                        "    <version>${version}</version>\n" +
                        "    <packaging>${packaging}</packaging>\n" +
                        "\n" +
                        "    <name>${projectName}</name>\n" +
                        "    <description>${projectDescription}</description>\n" +
                        "\n" +
                        "    <properties>\n" +
                        "        <maven.compiler.source>${javaVersion}</maven.compiler.source>\n" +
                        "        <maven.compiler.target>${javaVersion}</maven.compiler.target>\n" +
                        "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                        "    </properties>\n" +
                        "\n" +
                        "    <dependencies>\n" +
                        "        <!-- Dependencies here -->\n" +
                        "    </dependencies>\n" +
                        "\n" +
                        "</project>", "Configuration");
        mavenPom.setDescription("Maven project POM template");
        mavenPom.addParameter(TemplateParameter.requiredString("groupId"));
        mavenPom.addParameter(TemplateParameter.requiredString("artifactId"));
        mavenPom.addParameter(TemplateParameter.stringParam("version", "1.0.0"));
        mavenPom.addParameter(TemplateParameter.enumParam("packaging", "jar", "war", "pom"));
        mavenPom.addParameter(TemplateParameter.requiredString("projectName"));
        mavenPom.addParameter(TemplateParameter.stringParam("projectDescription", ""));
        mavenPom.addParameter(TemplateParameter.enumParam("javaVersion", "11", "17", "21"));
        mavenPom.getRelatedStandards().add("Maven");
        mavenPom.setBuiltIn(true);
        addTemplate(mavenPom);
    }

    private void addDocumentationTemplates() {
        // API Documentation Template
        XmlTemplate apiDoc = new XmlTemplate("api-documentation",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<api-documentation>\n" +
                        "    <api name=\"${apiName}\" version=\"${apiVersion}\">\n" +
                        "        <description>${apiDescription}</description>\n" +
                        "        <base-url>${baseUrl}</base-url>\n" +
                        "        \n" +
                        "        <endpoint path=\"${endpointPath}\" method=\"${httpMethod}\">\n" +
                        "            <description>${endpointDescription}</description>\n" +
                        "            <parameters>\n" +
                        "                <parameter name=\"${paramName}\" type=\"${paramType}\" required=\"${paramRequired}\">\n" +
                        "                    <description>${paramDescription}</description>\n" +
                        "                </parameter>\n" +
                        "            </parameters>\n" +
                        "            <response>\n" +
                        "                <status-code>200</status-code>\n" +
                        "                <content-type>application/json</content-type>\n" +
                        "                <schema>${responseSchema}</schema>\n" +
                        "            </response>\n" +
                        "        </endpoint>\n" +
                        "    </api>\n" +
                        "</api-documentation>", "Documentation");
        apiDoc.setDescription("API documentation template");
        apiDoc.addParameter(TemplateParameter.requiredString("apiName"));
        apiDoc.addParameter(TemplateParameter.stringParam("apiVersion", "1.0"));
        apiDoc.addParameter(TemplateParameter.requiredString("apiDescription"));
        apiDoc.addParameter(TemplateParameter.requiredString("baseUrl"));
        apiDoc.addParameter(TemplateParameter.requiredString("endpointPath"));
        apiDoc.addParameter(TemplateParameter.enumParam("httpMethod", "GET", "POST", "PUT", "DELETE", "PATCH"));
        apiDoc.addParameter(TemplateParameter.stringParam("endpointDescription", ""));
        apiDoc.addParameter(TemplateParameter.stringParam("paramName", ""));
        apiDoc.addParameter(TemplateParameter.enumParam("paramType", "string", "integer", "boolean", "array", "object"));
        apiDoc.addParameter(TemplateParameter.boolParam("paramRequired", false));
        apiDoc.addParameter(TemplateParameter.stringParam("paramDescription", ""));
        apiDoc.addParameter(TemplateParameter.stringParam("responseSchema", ""));
        apiDoc.setIndustry(XmlTemplate.TemplateIndustry.DOCUMENTATION);
        apiDoc.setBuiltIn(true);
        addTemplate(apiDoc);
    }

    private void addFinanceTemplates() {
        // Financial Transaction Template
        XmlTemplate financialTransaction = new XmlTemplate("financial-transaction",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<financial-transaction>\n" +
                        "    <transaction-id>${transactionId}</transaction-id>\n" +
                        "    <timestamp>${timestamp}</timestamp>\n" +
                        "    <type>${transactionType}</type>\n" +
                        "    <amount currency=\"${currency}\">${amount}</amount>\n" +
                        "    <from-account>${fromAccount}</from-account>\n" +
                        "    <to-account>${toAccount}</to-account>\n" +
                        "    <description>${description}</description>\n" +
                        "    <status>${status}</status>\n" +
                        "    <reference>${reference}</reference>\n" +
                        "</financial-transaction>", "Finance");
        financialTransaction.setDescription("Financial transaction record");
        financialTransaction.addParameter(TemplateParameter.requiredString("transactionId"));
        financialTransaction.addParameter(TemplateParameter.stringParam("timestamp", "").withAutoGenerate(true));
        financialTransaction.addParameter(TemplateParameter.enumParam("transactionType", "transfer", "payment", "deposit", "withdrawal"));
        financialTransaction.addParameter(TemplateParameter.requiredString("amount").pattern("\\d+\\.\\d{2}"));
        financialTransaction.addParameter(TemplateParameter.enumParam("currency", "USD", "EUR", "GBP", "JPY"));
        financialTransaction.addParameter(TemplateParameter.requiredString("fromAccount"));
        financialTransaction.addParameter(TemplateParameter.requiredString("toAccount"));
        financialTransaction.addParameter(TemplateParameter.stringParam("description", ""));
        financialTransaction.addParameter(TemplateParameter.enumParam("status", "pending", "completed", "failed", "cancelled"));
        financialTransaction.addParameter(TemplateParameter.stringParam("reference", ""));
        financialTransaction.setIndustry(XmlTemplate.TemplateIndustry.FINANCE);
        financialTransaction.setBuiltIn(true);
        addTemplate(financialTransaction);
    }

    private void addHealthcareTemplates() {
        // Patient Record Template
        XmlTemplate patientRecord = new XmlTemplate("patient-record",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<patient-record>\n" +
                        "    <patient-id>${patientId}</patient-id>\n" +
                        "    <personal-info>\n" +
                        "        <first-name>${firstName}</first-name>\n" +
                        "        <last-name>${lastName}</last-name>\n" +
                        "        <date-of-birth>${dateOfBirth}</date-of-birth>\n" +
                        "        <gender>${gender}</gender>\n" +
                        "    </personal-info>\n" +
                        "    <contact-info>\n" +
                        "        <phone>${phone}</phone>\n" +
                        "        <email>${email}</email>\n" +
                        "        <address>\n" +
                        "            <street>${street}</street>\n" +
                        "            <city>${city}</city>\n" +
                        "            <zip>${zip}</zip>\n" +
                        "            <country>${country}</country>\n" +
                        "        </address>\n" +
                        "    </contact-info>\n" +
                        "    <medical-info>\n" +
                        "        <blood-type>${bloodType}</blood-type>\n" +
                        "        <allergies>${allergies}</allergies>\n" +
                        "        <emergency-contact>${emergencyContact}</emergency-contact>\n" +
                        "    </medical-info>\n" +
                        "</patient-record>", "Healthcare");
        patientRecord.setDescription("Patient medical record");
        patientRecord.addParameter(TemplateParameter.requiredString("patientId"));
        patientRecord.addParameter(TemplateParameter.requiredString("firstName"));
        patientRecord.addParameter(TemplateParameter.requiredString("lastName"));
        patientRecord.addParameter(TemplateParameter.stringParam("dateOfBirth", "").withType(TemplateParameter.ParameterType.DATE));
        patientRecord.addParameter(TemplateParameter.enumParam("gender", "Male", "Female", "Other"));
        patientRecord.addParameter(TemplateParameter.stringParam("phone", ""));
        patientRecord.addParameter(TemplateParameter.stringParam("email", "").withType(TemplateParameter.ParameterType.EMAIL));
        patientRecord.addParameter(TemplateParameter.stringParam("street", ""));
        patientRecord.addParameter(TemplateParameter.stringParam("city", ""));
        patientRecord.addParameter(TemplateParameter.stringParam("zip", ""));
        patientRecord.addParameter(TemplateParameter.stringParam("country", ""));
        patientRecord.addParameter(TemplateParameter.enumParam("bloodType", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"));
        patientRecord.addParameter(TemplateParameter.stringParam("allergies", ""));
        patientRecord.addParameter(TemplateParameter.stringParam("emergencyContact", ""));
        patientRecord.setIndustry(XmlTemplate.TemplateIndustry.HEALTHCARE);
        patientRecord.setComplexity(XmlTemplate.TemplateComplexity.COMPLEX);
        patientRecord.setBuiltIn(true);
        addTemplate(patientRecord);
    }

    private void addAutomotiveTemplates() {
        // Vehicle Information Template
        XmlTemplate vehicleInfo = new XmlTemplate("vehicle-information",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<vehicle>\n" +
                        "    <identification>\n" +
                        "        <vin>${vin}</vin>\n" +
                        "        <license-plate>${licensePlate}</license-plate>\n" +
                        "        <registration-number>${registrationNumber}</registration-number>\n" +
                        "    </identification>\n" +
                        "    <specifications>\n" +
                        "        <make>${make}</make>\n" +
                        "        <model>${model}</model>\n" +
                        "        <year>${year}</year>\n" +
                        "        <color>${color}</color>\n" +
                        "        <fuel-type>${fuelType}</fuel-type>\n" +
                        "        <transmission>${transmission}</transmission>\n" +
                        "        <engine>\n" +
                        "            <displacement>${engineDisplacement}</displacement>\n" +
                        "            <power>${enginePower}</power>\n" +
                        "            <cylinders>${cylinders}</cylinders>\n" +
                        "        </engine>\n" +
                        "    </specifications>\n" +
                        "    <registration>\n" +
                        "        <owner>${ownerName}</owner>\n" +
                        "        <registration-date>${registrationDate}</registration-date>\n" +
                        "        <expiry-date>${expiryDate}</expiry-date>\n" +
                        "    </registration>\n" +
                        "</vehicle>", "Automotive");
        vehicleInfo.setDescription("Vehicle registration and specification record");
        vehicleInfo.addParameter(TemplateParameter.requiredString("vin").length(17, 17));
        vehicleInfo.addParameter(TemplateParameter.requiredString("licensePlate"));
        vehicleInfo.addParameter(TemplateParameter.stringParam("registrationNumber", ""));
        vehicleInfo.addParameter(TemplateParameter.requiredString("make"));
        vehicleInfo.addParameter(TemplateParameter.requiredString("model"));
        vehicleInfo.addParameter(TemplateParameter.intParam("year", 2024).range(1900, 2030));
        vehicleInfo.addParameter(TemplateParameter.stringParam("color", ""));
        vehicleInfo.addParameter(TemplateParameter.enumParam("fuelType", "Gasoline", "Diesel", "Electric", "Hybrid"));
        vehicleInfo.addParameter(TemplateParameter.enumParam("transmission", "Manual", "Automatic", "CVT"));
        vehicleInfo.addParameter(TemplateParameter.stringParam("engineDisplacement", ""));
        vehicleInfo.addParameter(TemplateParameter.stringParam("enginePower", ""));
        vehicleInfo.addParameter(TemplateParameter.intParam("cylinders", 4));
        vehicleInfo.addParameter(TemplateParameter.requiredString("ownerName"));
        vehicleInfo.addParameter(TemplateParameter.stringParam("registrationDate", "").withType(TemplateParameter.ParameterType.DATE));
        vehicleInfo.addParameter(TemplateParameter.stringParam("expiryDate", "").withType(TemplateParameter.ParameterType.DATE));
        vehicleInfo.setIndustry(XmlTemplate.TemplateIndustry.AUTOMOTIVE);
        vehicleInfo.setBuiltIn(true);
        addTemplate(vehicleInfo);
    }

    private void addGovernmentTemplates() {
        // Government Form Template
        XmlTemplate govForm = new XmlTemplate("government-form",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<government-form>\n" +
                        "    <form-header>\n" +
                        "        <form-id>${formId}</form-id>\n" +
                        "        <form-title>${formTitle}</form-title>\n" +
                        "        <department>${department}</department>\n" +
                        "        <submission-date>${submissionDate}</submission-date>\n" +
                        "        <reference-number>${referenceNumber}</reference-number>\n" +
                        "    </form-header>\n" +
                        "    <applicant>\n" +
                        "        <personal-details>\n" +
                        "            <full-name>${fullName}</full-name>\n" +
                        "            <national-id>${nationalId}</national-id>\n" +
                        "            <date-of-birth>${dateOfBirth}</date-of-birth>\n" +
                        "            <nationality>${nationality}</nationality>\n" +
                        "        </personal-details>\n" +
                        "        <contact-details>\n" +
                        "            <address>${address}</address>\n" +
                        "            <phone>${phone}</phone>\n" +
                        "            <email>${email}</email>\n" +
                        "        </contact-details>\n" +
                        "    </applicant>\n" +
                        "    <application-details>\n" +
                        "        <purpose>${purpose}</purpose>\n" +
                        "        <requested-service>${requestedService}</requested-service>\n" +
                        "        <additional-info>${additionalInfo}</additional-info>\n" +
                        "    </application-details>\n" +
                        "    <declaration>\n" +
                        "        <statement>I declare that the information provided is true and complete.</statement>\n" +
                        "        <signature>${signature}</signature>\n" +
                        "        <date>${declarationDate}</date>\n" +
                        "    </declaration>\n" +
                        "</government-form>", "Government");
        govForm.setDescription("Government application form");
        govForm.addParameter(TemplateParameter.requiredString("formId"));
        govForm.addParameter(TemplateParameter.requiredString("formTitle"));
        govForm.addParameter(TemplateParameter.requiredString("department"));
        govForm.addParameter(TemplateParameter.stringParam("submissionDate", "").withType(TemplateParameter.ParameterType.DATE));
        govForm.addParameter(TemplateParameter.stringParam("referenceNumber", ""));
        govForm.addParameter(TemplateParameter.requiredString("fullName"));
        govForm.addParameter(TemplateParameter.requiredString("nationalId"));
        govForm.addParameter(TemplateParameter.stringParam("dateOfBirth", "").withType(TemplateParameter.ParameterType.DATE));
        govForm.addParameter(TemplateParameter.stringParam("nationality", ""));
        govForm.addParameter(TemplateParameter.stringParam("address", ""));
        govForm.addParameter(TemplateParameter.stringParam("phone", ""));
        govForm.addParameter(TemplateParameter.stringParam("email", "").withType(TemplateParameter.ParameterType.EMAIL));
        govForm.addParameter(TemplateParameter.requiredString("purpose"));
        govForm.addParameter(TemplateParameter.requiredString("requestedService"));
        govForm.addParameter(TemplateParameter.stringParam("additionalInfo", ""));
        govForm.addParameter(TemplateParameter.stringParam("signature", ""));
        govForm.addParameter(TemplateParameter.stringParam("declarationDate", "").withType(TemplateParameter.ParameterType.DATE));
        govForm.setIndustry(XmlTemplate.TemplateIndustry.GOVERNMENT);
        govForm.setComplexity(XmlTemplate.TemplateComplexity.COMPLEX);
        govForm.setBuiltIn(true);
        addTemplate(govForm);
    }

    private void addSchemaTemplates() {
        // XSD Schema Template
        XmlTemplate xsdSchema = new XmlTemplate("xsd-schema",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xs:schema\n" +
                        "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "    targetNamespace=\"${targetNamespace}\"\n" +
                        "    xmlns:tns=\"${targetNamespace}\"\n" +
                        "    elementFormDefault=\"qualified\">\n" +
                        "\n" +
                        "    <xs:element name=\"${rootElement}\" type=\"tns:${rootElementType}\"/>\n" +
                        "\n" +
                        "    <xs:complexType name=\"${rootElementType}\">\n" +
                        "        <xs:sequence>\n" +
                        "            <xs:element name=\"${childElement}\" type=\"xs:${childElementType}\" minOccurs=\"${minOccurs}\" maxOccurs=\"${maxOccurs}\"/>\n" +
                        "        </xs:sequence>\n" +
                        "        <xs:attribute name=\"${attributeName}\" type=\"xs:${attributeType}\" use=\"${attributeUse}\"/>\n" +
                        "    </xs:complexType>\n" +
                        "\n" +
                        "</xs:schema>", "Schema");
        xsdSchema.setDescription("XSD schema definition");
        xsdSchema.addParameter(TemplateParameter.requiredString("targetNamespace"));
        xsdSchema.addParameter(TemplateParameter.requiredString("rootElement"));
        xsdSchema.addParameter(TemplateParameter.requiredString("rootElementType"));
        xsdSchema.addParameter(TemplateParameter.stringParam("childElement", ""));
        xsdSchema.addParameter(TemplateParameter.enumParam("childElementType", "string", "int", "boolean", "date"));
        xsdSchema.addParameter(TemplateParameter.stringParam("minOccurs", "1"));
        xsdSchema.addParameter(TemplateParameter.stringParam("maxOccurs", "1"));
        xsdSchema.addParameter(TemplateParameter.stringParam("attributeName", ""));
        xsdSchema.addParameter(TemplateParameter.enumParam("attributeType", "string", "int", "boolean"));
        xsdSchema.addParameter(TemplateParameter.enumParam("attributeUse", "required", "optional"));
        xsdSchema.getRelatedStandards().add("XML Schema");
        xsdSchema.setBuiltIn(true);
        addTemplate(xsdSchema);
    }

    private void addTransformationTemplates() {
        // XSLT Template
        XmlTemplate xsltTemplate = new XmlTemplate("xslt-transformation",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xsl:stylesheet version=\"3.0\"\n" +
                        "    xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
                        "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "    exclude-result-prefixes=\"xs\">\n" +
                        "\n" +
                        "    <xsl:output method=\"${outputMethod}\" encoding=\"UTF-8\" indent=\"yes\"/>\n" +
                        "\n" +
                        "    <xsl:template match=\"/\">\n" +
                        "        <${rootElement}>\n" +
                        "            <xsl:apply-templates select=\"${selectPath}\"/>\n" +
                        "        </${rootElement}>\n" +
                        "    </xsl:template>\n" +
                        "\n" +
                        "    <xsl:template match=\"${matchPattern}\">\n" +
                        "        <${outputElement}>\n" +
                        "            <xsl:value-of select=\"${valuePath}\"/>\n" +
                        "        </${outputElement}>\n" +
                        "    </xsl:template>\n" +
                        "\n" +
                        "</xsl:stylesheet>", "Transformation");
        xsltTemplate.setDescription("XSLT transformation template");
        xsltTemplate.addParameter(TemplateParameter.enumParam("outputMethod", "xml", "html", "text", "json"));
        xsltTemplate.addParameter(TemplateParameter.requiredString("rootElement"));
        xsltTemplate.addParameter(TemplateParameter.stringParam("selectPath", "*"));
        xsltTemplate.addParameter(TemplateParameter.requiredString("matchPattern"));
        xsltTemplate.addParameter(TemplateParameter.requiredString("outputElement"));
        xsltTemplate.addParameter(TemplateParameter.stringParam("valuePath", "."));
        xsltTemplate.getRelatedStandards().add("XSLT 3.0");
        xsltTemplate.setComplexity(XmlTemplate.TemplateComplexity.COMPLEX);
        xsltTemplate.setBuiltIn(true);
        addTemplate(xsltTemplate);
    }

    // ========== Search Index Management ==========

    private void buildSearchIndex() {
        searchIndex.clear();

        for (XmlTemplate template : templates.values()) {
            updateSearchIndex(template);
        }
    }

    private void updateSearchIndex(XmlTemplate template) {
        Set<String> keywords = extractKeywords(template);

        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            searchIndex.computeIfAbsent(lowerKeyword, k -> new HashSet<>())
                    .add(template.getId());
        }
    }

    private void removeFromSearchIndex(XmlTemplate template) {
        for (Set<String> templateIds : searchIndex.values()) {
            templateIds.remove(template.getId());
        }
    }

    private Set<String> extractKeywords(XmlTemplate template) {
        Set<String> keywords = new HashSet<>();

        // Add name and description words
        if (template.getName() != null) {
            Collections.addAll(keywords, template.getName().toLowerCase().split("[\\s_-]+"));
        }
        if (template.getDescription() != null) {
            Collections.addAll(keywords, template.getDescription().toLowerCase().split("[\\s_-]+"));
        }

        // Add category and industry
        if (template.getCategory() != null) {
            keywords.add(template.getCategory().toLowerCase());
        }
        if (template.getIndustry() != null) {
            keywords.add(template.getIndustry().name().toLowerCase());
        }

        // Add tags
        keywords.addAll(template.getTags().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList()));

        // Add parameter names
        keywords.addAll(template.getParameters().stream()
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toList()));

        return keywords;
    }

    // ========== Category Management ==========

    private void addToCategory(XmlTemplate template) {
        if (template.getCategory() != null) {
            templatesByCategory.computeIfAbsent(template.getCategory(), k -> new ArrayList<>())
                    .add(template);

            // Add to industry categorization
            if (template.getIndustry() != null) {
                categoriesByIndustry.computeIfAbsent(template.getIndustry().name(), k -> new HashSet<>())
                        .add(template.getCategory());
            }
        }
    }

    private void removeFromCategory(XmlTemplate template) {
        if (template.getCategory() != null) {
            List<XmlTemplate> categoryTemplates = templatesByCategory.get(template.getCategory());
            if (categoryTemplates != null) {
                categoryTemplates.remove(template);
                if (categoryTemplates.isEmpty()) {
                    templatesByCategory.remove(template.getCategory());
                }
            }
        }
    }

    // ========== Usage Statistics ==========

    /**
     * Record template usage
     */
    public void recordUsage(String templateId) {
        XmlTemplate template = getTemplate(templateId);
        if (template != null) {
            template.recordUsage();
            usageStatistics.put(templateId, usageStatistics.getOrDefault(templateId, 0) + 1);
            logger.debug("Recorded usage for template '{}'", template.getName());
        }
    }

    /**
     * Get most popular templates
     */
    public List<XmlTemplate> getMostPopularTemplates(int limit) {
        return templates.values().stream()
                .sorted((t1, t2) -> Integer.compare(
                        usageStatistics.getOrDefault(t2.getId(), 0),
                        usageStatistics.getOrDefault(t1.getId(), 0)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get recently used templates
     */
    public List<XmlTemplate> getRecentlyUsedTemplates(int limit) {
        return templates.values().stream()
                .filter(t -> t.getLastUsed() != null)
                .sorted((t1, t2) -> t2.getLastUsed().compareTo(t1.getLastUsed()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ========== Statistics and Reporting ==========

    /**
     * Get repository statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTemplates", templates.size());
        stats.put("categories", templatesByCategory.size());
        stats.put("builtInTemplates", templates.values().stream().mapToInt(t -> t.isBuiltIn() ? 1 : 0).sum());
        stats.put("customTemplates", templates.values().stream().mapToInt(t -> !t.isBuiltIn() ? 1 : 0).sum());
        stats.put("totalUsages", usageStatistics.values().stream().mapToInt(Integer::intValue).sum());

        // Industry breakdown
        Map<String, Integer> industryBreakdown = new HashMap<>();
        for (XmlTemplate template : templates.values()) {
            String industry = template.getIndustry().name();
            industryBreakdown.put(industry, industryBreakdown.getOrDefault(industry, 0) + 1);
        }
        stats.put("industryBreakdown", industryBreakdown);

        // Complexity breakdown
        Map<String, Integer> complexityBreakdown = new HashMap<>();
        for (XmlTemplate template : templates.values()) {
            String complexity = template.getComplexity().name();
            complexityBreakdown.put(complexity, complexityBreakdown.getOrDefault(complexity, 0) + 1);
        }
        stats.put("complexityBreakdown", complexityBreakdown);

        return stats;
    }

    /**
     * Get categories by industry
     */
    public Set<String> getCategoriesByIndustry(String industry) {
        return categoriesByIndustry.getOrDefault(industry, new HashSet<>());
    }

    /**
     * Get templates by industry
     */
    public List<XmlTemplate> getTemplatesByIndustry(XmlTemplate.TemplateIndustry industry) {
        return templates.values().stream()
                .filter(template -> template.getIndustry() == industry)
                .collect(Collectors.toList());
    }

    // ========== File System Integration ==========

    /**
     * Load templates from the templates directory
     */
    private void loadTemplatesFromDirectory() {
        try {
            List<XmlTemplate> fileTemplates = templateFileService.loadTemplatesFromDirectory();

            // Create default templates if directory is empty
            if (fileTemplates.isEmpty()) {
                templateFileService.createDefaultTemplatesIfEmpty();
                fileTemplates = templateFileService.loadTemplatesFromDirectory();
            }

            for (XmlTemplate template : fileTemplates) {
                templates.put(template.getId(), template);
                addToCategory(template);
            }

            logger.info("Loaded {} templates from file system", fileTemplates.size());
        } catch (Exception e) {
            logger.error("Failed to load templates from directory: {}", e.getMessage(), e);
        }
    }

    /**
     * Save a template to file system
     */
    public void saveTemplateToFile(XmlTemplate template) throws Exception {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }

        templateFileService.saveTemplateToDirectory(template);

        // Add to repository if not already present
        if (!templates.containsKey(template.getId())) {
            addTemplate(template, false); // Don't save again to avoid recursion
        }
    }

    /**
     * Create a new template and save it to the file system
     */
    public void createNewTemplate(String id, String name, String content, String category, String description) throws Exception {
        if (templates.containsKey(id)) {
            throw new IllegalArgumentException("Template with ID '" + id + "' already exists");
        }

        XmlTemplate template = new XmlTemplate(id, content, category);
        template.setName(name);
        template.setDescription(description);
        template.setBuiltIn(false);

        // Add to repository and save to file
        addTemplate(template, true);

        logger.info("Created new template '{}' (ID: {})", name, id);
    }

    /**
     * Get templates directory path
     */
    public String getTemplatesDirectoryPath() {
        return templateFileService.getTemplatesDirectoryPath().toString();
    }

    /**
     * Count non-built-in templates
     */
    private long countNonBuiltInTemplates() {
        return templates.values().stream()
                .filter(template -> !template.isBuiltIn())
                .count();
    }

    /**
     * Refresh templates from directory
     */
    public void refreshTemplatesFromDirectory() {
        // Remove all non-built-in templates
        List<String> toRemove = templates.values().stream()
                .filter(template -> !template.isBuiltIn())
                .map(XmlTemplate::getId)
                .collect(Collectors.toList());

        for (String id : toRemove) {
            removeTemplate(id, false); // Don't delete from file
        }

        // Reload from directory
        loadTemplatesFromDirectory();

        // Rebuild search index
        buildSearchIndex();

        logger.info("Refreshed templates from directory. Total: {} ({} from files)",
                templates.size(), countNonBuiltInTemplates());
    }
}