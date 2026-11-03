package org.fxt.freexmltoolkit.util;

import java.io.InputStream;
import java.net.URI;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.LSInputImpl;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

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

    /** Implementation the first {@link TransformerFactory#newInstance()} resolved; reused to skip the lookup. */
    private static volatile Class<?> transformerFactoryClass;

    /**
     * A new {@link TransformerFactory} of the platform's configured implementation. The first call runs the JAXP
     * lookup ({@code TransformerFactory.newInstance()}), which scans the {@code META-INF/services} entries of every jar
     * on the class path; on Windows that took seconds per call (and allocated enough to run out of memory once). Later
     * calls instantiate the same class directly. Each call still returns a fresh factory: factories are not
     * thread-safe.
     */
    static TransformerFactory newTransformerFactory() {
        Class<?> known = transformerFactoryClass;
        if (known != null) {
            try {
                return TransformerFactory.newInstance(known.getName(), known.getClassLoader());
            } catch (javax.xml.transform.TransformerFactoryConfigurationError e) {
                logger.debug("Cached TransformerFactory {} unusable, looking it up again: {}", known.getName(),
                        e.getMessage());
            }
        }
        TransformerFactory factory = TransformerFactory.newInstance();
        transformerFactoryClass = factory.getClass();
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
        TransformerFactory factory = newTransformerFactory();

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
     * Protocols allowed by default for external schema references (xs:import / xs:include /
     * xs:redefine). Local files and classpath jar resources are permitted so that legitimate
     * multi-file schemas keep working, while network protocols (http, https, ftp) are blocked
     * to prevent SSRF and remote-file disclosure when validating an untrusted schema.
     */
    public static final String DEFAULT_ALLOWED_SCHEMA_PROTOCOLS = "file,jar:file";

    /**
     * Protocol set for services that <b>intentionally</b> support resolving schemas from remote
     * locations (e.g. the XSD validation services with HTTP/HTTPS schema caching). Adds network
     * protocols on top of {@link #DEFAULT_ALLOWED_SCHEMA_PROTOCOLS}. Prefer
     * {@link #DEFAULT_ALLOWED_SCHEMA_PROTOCOLS} unless remote schema resolution is a required feature.
     */
    public static final String LOCAL_AND_REMOTE_SCHEMA_PROTOCOLS = "file,jar:file,http,https";

    /**
     * Creates a secure {@link SchemaFactory} for the given schema language with XXE / SSRF
     * protection enabled.
     *
     * <p>The returned factory has:
     * <ul>
     *   <li>{@code FEATURE_SECURE_PROCESSING} enabled (entity-expansion limits, etc.)</li>
     *   <li>DTDs named by a schema's DOCTYPE are never loaded: they resolve to an empty document</li>
     *   <li>External schema access restricted to {@value #DEFAULT_ALLOWED_SCHEMA_PROTOCOLS},
     *       which permits local {@code xs:import}/{@code xs:include} while blocking network
     *       protocols (http/https/ftp) to prevent SSRF.</li>
     * </ul>
     *
     * <p>Callers that install a custom {@code LSResourceResolver} for relative imports remain
     * fully compatible: the resolver still intercepts references first; the access restriction
     * only governs what the default resolver is allowed to fetch.
     *
     * @param schemaLanguage the schema language URI, e.g.
     *                        {@link XMLConstants#W3C_XML_SCHEMA_NS_URI}
     * @return a secure SchemaFactory instance
     */
    public static SchemaFactory createSecureSchemaFactory(String schemaLanguage) {
        return createSecureSchemaFactory(SchemaFactory.newInstance(schemaLanguage), DEFAULT_ALLOWED_SCHEMA_PROTOCOLS);
    }

    /**
     * Applies secure XXE / SSRF settings to an existing {@link SchemaFactory}, restricting
     * external schema references to {@value #DEFAULT_ALLOWED_SCHEMA_PROTOCOLS}.
     *
     * <p>Use this overload when the factory must be obtained from a specific implementation
     * (for example the Xerces XSD 1.1 {@code XMLSchema11Factory}) rather than via
     * {@link SchemaFactory#newInstance(String)}.
     *
     * @param factory the factory to harden
     * @return the same factory instance, hardened
     */
    public static SchemaFactory createSecureSchemaFactory(SchemaFactory factory) {
        return createSecureSchemaFactory(factory, DEFAULT_ALLOWED_SCHEMA_PROTOCOLS);
    }

    /**
     * Applies secure XXE / SSRF settings to an existing {@link SchemaFactory}.
     *
     * <p>Always enables {@code FEATURE_SECURE_PROCESSING} and never loads a DTD named by a schema:
     * the installed resolver answers every DTD request with an empty document (the JAXP
     * {@code ACCESS_EXTERNAL_DTD} property is set too, but the bundled Xerces ignores it). The set
     * of protocols permitted for external schema references ({@code xs:import}/{@code xs:include}/
     * {@code xs:redefine}) is caller-controlled via {@code allowedSchemaProtocols}.
     *
     * @param factory                the factory to harden
     * @param allowedSchemaProtocols comma-separated protocol list for {@code ACCESS_EXTERNAL_SCHEMA}
     *                               (e.g. {@value #DEFAULT_ALLOWED_SCHEMA_PROTOCOLS} for local-only,
     *                               or {@code "file,jar:file,http,https"} for services that
     *                               intentionally support remote schemas). Use {@code ""} to block
     *                               all external schema access.
     * @return the same factory instance, hardened
     */
    public static SchemaFactory createSecureSchemaFactory(SchemaFactory factory, String allowedSchemaProtocols) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception e) {
            logger.warn("Could not enable secure processing on SchemaFactory: {}", e.getMessage());
        }
        // Each property on its own: the bundled (exist-db) Xerces fork rejects ACCESS_EXTERNAL_DTD,
        // and that must not cost the schema protocol restriction.
        setPropertyIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setPropertyIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, allowedSchemaProtocols);

        // The resolver is the control that actually holds on that Xerces: DTDs named by a schema's
        // DOCTYPE get an empty document, and without remote protocols network references are
        // blocked. Callers that need remote schema resolution pass a protocol list containing
        // "http"/"https" and usually install their own resolver, which must treat DTDs the same way
        // (see emptyDtd).
        factory.setResourceResolver(allowedSchemaProtocols.toLowerCase().contains("http")
                ? createDtdSkippingResolver()
                : createLocalOnlySchemaResolver());
        return factory;
    }

    private static void setPropertyIfSupported(SchemaFactory factory, String name, String value) {
        try {
            factory.setProperty(name, value);
        } catch (Exception e) {
            logger.debug("SchemaFactory does not support {}: {}", name, e.getMessage());
        }
    }

    /**
     * Answers a request for a DTD with an empty document.
     *
     * <p>A schema's DOCTYPE plays no part in building the schema, but W3C schemas name DTDs that
     * are not shipped beside them ({@code XMLSchema.dtd}, {@code datatypes.dtd}). A resolver that
     * returns {@code null} for one makes the parser fetch the DTD itself: a missing file fails the
     * whole schema ({@code schema_reference.4}), and a remote one is downloaded. Every schema
     * resolver asks this first.
     *
     * @param type     the resource type passed to the resolver
     * @param publicId the public identifier, may be {@code null}
     * @param systemId the system identifier, may be {@code null}
     * @param baseURI  the base URI, may be {@code null}
     * @return an empty input for a DTD, or {@code null} for any other resource
     */
    public static LSInput emptyDtd(String type, String publicId, String systemId, String baseURI) {
        boolean dtd = XMLConstants.XML_DTD_NS_URI.equals(type)
                || (systemId != null && systemId.strip().toLowerCase(java.util.Locale.ROOT).endsWith(".dtd"));
        if (!dtd) {
            return null;
        }
        logger.debug("Skipping DTD named by a schema: {}", systemId);
        return new LSInputImpl(publicId, systemId, baseURI, InputStream.nullInputStream());
    }

    /**
     * Creates a resolver that only answers DTD requests (with an empty document, see
     * {@link #emptyDtd}) and leaves every other reference to the processor.
     *
     * @return the resolver
     */
    public static LSResourceResolver createDtdSkippingResolver() {
        return (type, namespaceURI, publicId, systemId, baseURI) -> emptyDtd(type, publicId, systemId, baseURI);
    }

    /**
     * Creates an {@link LSResourceResolver} that blocks schema references over network protocols
     * (http/https/ftp) while delegating local (file / classpath) resolution to the processor's
     * default handling (by returning {@code null}). DTDs get an empty document wherever they are
     * ({@link #emptyDtd}), so they are neither fetched nor blocked.
     *
     * <p>This is the effective SSRF control for schema resolution, independent of whether the
     * underlying parser honours the JAXP {@code ACCESS_EXTERNAL_SCHEMA} property.
     *
     * @return a resolver that throws {@link SecurityException} on remote references
     */
    public static LSResourceResolver createLocalOnlySchemaResolver() {
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            LSInput dtd = emptyDtd(type, publicId, systemId, baseURI);
            if (dtd != null) {
                return dtd;
            }
            String scheme = resolveScheme(systemId, baseURI);
            if (scheme != null && (scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp"))) {
                throw new SecurityException(
                        "Blocked remote schema reference (" + scheme + "): systemId=" + systemId
                                + ", base=" + baseURI);
            }
            // Local / relative references: let the processor resolve them as usual.
            return null;
        };
    }

    /**
     * Determines the URI scheme of a (possibly relative) schema reference, resolving it against the
     * supplied base URI when necessary.
     *
     * @param systemId the referenced system identifier (may be relative or {@code null})
     * @param baseURI  the base URI the reference is resolved against (may be {@code null})
     * @return the lower-case scheme (e.g. {@code "http"}, {@code "file"}), or {@code null} if it
     *         cannot be determined (treated as local)
     */
    private static String resolveScheme(String systemId, String baseURI) {
        try {
            if (systemId != null && !systemId.isBlank()) {
                URI sys = URI.create(systemId.trim());
                if (sys.getScheme() != null) {
                    return sys.getScheme().toLowerCase();
                }
                // Relative systemId: resolve against the base URI to discover the effective scheme.
                if (baseURI != null && !baseURI.isBlank()) {
                    URI resolved = URI.create(baseURI.trim()).resolve(sys);
                    return resolved.getScheme() != null ? resolved.getScheme().toLowerCase() : null;
                }
            } else if (baseURI != null && !baseURI.isBlank()) {
                URI base = URI.create(baseURI.trim());
                return base.getScheme() != null ? base.getScheme().toLowerCase() : null;
            }
        } catch (IllegalArgumentException e) {
            // Malformed URI - treat as local/unknown; the processor will reject it if invalid.
            logger.debug("Could not parse schema reference scheme (systemId={}, base={}): {}",
                    systemId, baseURI, e.getMessage());
        }
        return null;
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
