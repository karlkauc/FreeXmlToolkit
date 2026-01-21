package org.fxt.freexmltoolkit.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerConfigurationException;

/**
 * Factory for creating secure XML processing components.
 *
 * <p>This utility class provides centralized creation of XML parsers, transformers,
 * and other XML processing components with proper security configuration to prevent:
 * <ul>
 *   <li>XXE (XML External Entity) attacks</li>
 *   <li>DTD-based denial of service attacks</li>
 *   <li>External entity resolution attacks</li>
 * </ul>
 *
 * <p>All factory methods in this class apply the following security measures:
 * <ul>
 *   <li>Disable external general entities</li>
 *   <li>Disable external parameter entities</li>
 *   <li>Disable external DTD loading</li>
 *   <li>Disable entity reference expansion</li>
 *   <li>Enable secure processing feature where applicable</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Instead of:
 * DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
 *
 * // Use:
 * DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory();
 * }</pre>
 *
 * @author FreeXmlToolkit Security Team
 * @since 2.0
 */
public final class SecureXmlFactory {

    private static final Logger logger = LogManager.getLogger(SecureXmlFactory.class);

    // Feature constants for XXE protection
    private static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SecureXmlFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a secure DocumentBuilderFactory with XXE protection enabled.
     *
     * <p>The returned factory has the following security features configured:
     * <ul>
     *   <li>DOCTYPE declarations are allowed (required for some W3C schemas)</li>
     *   <li>External general entities are disabled</li>
     *   <li>External parameter entities are disabled</li>
     *   <li>External DTD loading is disabled</li>
     *   <li>Entity reference expansion is disabled</li>
     * </ul>
     *
     * @return a secure DocumentBuilderFactory instance
     */
    public static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        return createSecureDocumentBuilderFactory(true);
    }

    /**
     * Creates a secure DocumentBuilderFactory with XXE protection enabled.
     *
     * @param namespaceAware whether the factory should be namespace-aware
     * @return a secure DocumentBuilderFactory instance
     */
    public static DocumentBuilderFactory createSecureDocumentBuilderFactory(boolean namespaceAware) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);

        configureSecureDocumentBuilderFactory(factory);

        return factory;
    }

    /**
     * Configures an existing DocumentBuilderFactory with XXE protection.
     *
     * <p>Use this method when you need to configure a factory that was created elsewhere,
     * or when you need additional configuration before applying security settings.
     *
     * @param factory the factory to configure
     */
    public static void configureSecureDocumentBuilderFactory(DocumentBuilderFactory factory) {
        try {
            // Allow DOCTYPE declarations (required for some W3C schemas like xmldsig-core-schema.xsd)
            // but disable external entity processing for security
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, false);
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
            factory.setExpandEntityReferences(false);

            // Additional secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            logger.trace("DocumentBuilderFactory configured with XXE protection");
        } catch (ParserConfigurationException e) {
            logger.warn("Could not configure all XML security features: {}", e.getMessage());
        }
    }

    /**
     * Creates a secure DocumentBuilder with XXE protection enabled.
     *
     * <p>This is a convenience method that creates a factory and builder in one call.
     *
     * @return a secure DocumentBuilder instance
     * @throws ParserConfigurationException if the parser cannot be configured
     */
    public static DocumentBuilder createSecureDocumentBuilder() throws ParserConfigurationException {
        return createSecureDocumentBuilderFactory().newDocumentBuilder();
    }

    /**
     * Creates a secure DocumentBuilder with XXE protection enabled.
     *
     * @param namespaceAware whether the parser should be namespace-aware
     * @return a secure DocumentBuilder instance
     * @throws ParserConfigurationException if the parser cannot be configured
     */
    public static DocumentBuilder createSecureDocumentBuilder(boolean namespaceAware) throws ParserConfigurationException {
        return createSecureDocumentBuilderFactory(namespaceAware).newDocumentBuilder();
    }

    /**
     * Creates a secure SAXParserFactory with XXE protection enabled.
     *
     * @return a secure SAXParserFactory instance
     */
    public static SAXParserFactory createSecureSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);

        try {
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, false);
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            logger.trace("SAXParserFactory configured with XXE protection");
        } catch (Exception e) {
            logger.warn("Could not configure all SAX security features: {}", e.getMessage());
        }

        return factory;
    }

    /**
     * Creates a secure SAXParser with XXE protection enabled.
     *
     * @return a secure SAXParser instance
     * @throws ParserConfigurationException if the parser cannot be configured
     * @throws org.xml.sax.SAXException if SAX processing fails
     */
    public static SAXParser createSecureSAXParser() throws ParserConfigurationException, org.xml.sax.SAXException {
        return createSecureSAXParserFactory().newSAXParser();
    }

    /**
     * Creates a secure XMLInputFactory for StAX parsing with XXE protection enabled.
     *
     * @return a secure XMLInputFactory instance
     */
    public static XMLInputFactory createSecureXMLInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        // Disable external entities for StAX
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);

        logger.trace("XMLInputFactory configured with XXE protection");

        return factory;
    }

    /**
     * Creates a secure TransformerFactory with XXE protection enabled.
     *
     * <p>Note: This creates a basic secure TransformerFactory. For Saxon-specific
     * security settings (like disabling Java extensions), use the
     * {@code XsltTransformationEngine} class.
     *
     * @return a secure TransformerFactory instance
     */
    public static TransformerFactory createSecureTransformerFactory() {
        TransformerFactory factory = TransformerFactory.newInstance();

        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            // Limit access to external resources
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            logger.trace("TransformerFactory configured with secure processing");
        } catch (TransformerConfigurationException e) {
            logger.warn("Could not configure all Transformer security features: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // Some TransformerFactory implementations may not support these attributes
            logger.debug("TransformerFactory does not support external access restrictions: {}", e.getMessage());
        }

        return factory;
    }

    /**
     * Creates a secure DocumentBuilderFactory specifically for well-formedness checking.
     *
     * <p>This factory is configured for basic XML parsing without validation,
     * suitable for checking if XML is well-formed.
     *
     * @return a secure DocumentBuilderFactory for well-formedness checking
     */
    public static DocumentBuilderFactory createSecureWellFormednessFactory() {
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory(true);
        factory.setValidating(false);
        return factory;
    }

    /**
     * Creates a secure DocumentBuilder specifically for well-formedness checking.
     *
     * @return a secure DocumentBuilder for well-formedness checking
     * @throws ParserConfigurationException if the parser cannot be configured
     */
    public static DocumentBuilder createSecureWellFormednessBuilder() throws ParserConfigurationException {
        return createSecureWellFormednessFactory().newDocumentBuilder();
    }
}
