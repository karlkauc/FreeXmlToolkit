package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sf.saxon.Configuration;

/**
 * Blocks network access from XPath, XQuery and XSLT evaluated with Saxon: {@code doc()},
 * {@code document()}, {@code unparsed-text()} and {@code unparsed-text-lines()} refuse http, https
 * and ftp (URIs the Schema Library or a catalog maps to a local file excepted), local references
 * resolve as usual. Every Saxon processor that evaluates expressions or stylesheets from documents
 * or the user goes through {@link #restrict(Configuration)}: the transformation engine, the Query
 * Console's XPath mode, grid formulas and filters (an expression saved in a document must not make
 * the application fetch a URL, see docs/threat-model.md).
 */
public final class SaxonResourceAccess {

    private static final Logger logger = LogManager.getLogger(SaxonResourceAccess.class);

    private SaxonResourceAccess() {
    }

    /**
     * The XML parser Saxon reads documents with (input of XPath, XQuery, XSLT, grid formulas,
     * Schematron) gets the settings of every other parser in the application: secure processing
     * (bounded entity expansion: a "billion laughs" document ran Saxon out of memory after two
     * minutes) and no external entities or DTDs (XXE: a document could read local files).
     */
    static void secureSourceParser(Configuration config) {
        setParserFeature(config, javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setParserFeature(config, "http://xml.org/sax/features/external-general-entities", false);
        setParserFeature(config, "http://xml.org/sax/features/external-parameter-entities", false);
        setParserFeature(config, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    }

    private static void setParserFeature(Configuration config, String feature, boolean value) {
        try {
            config.setConfigurationProperty(net.sf.saxon.lib.FeatureKeys.XML_PARSER_FEATURE
                    + java.net.URLEncoder.encode(feature, java.nio.charset.StandardCharsets.UTF_8), value);
        } catch (RuntimeException e) {
            logger.warn("Could not set XML parser feature {} for Saxon: {}", feature, e.getMessage());
        }
    }

    /** @return a new Saxon-HE processor whose configuration is {@linkplain #restrict restricted} */
    public static net.sf.saxon.s9api.Processor restrictedProcessor() {
        net.sf.saxon.s9api.Processor processor = new net.sf.saxon.s9api.Processor(false);
        restrict(processor.getUnderlyingConfiguration());
        return processor;
    }

    /**
     * Installs resolvers that block network access from doc()/document() and unparsed-text().
     *
     * <p>For doc()/document(), the Schema Library's user entries and registered catalogs are
     * consulted first: a mapped URI is served from its local target regardless of scheme. Any
     * remaining reference over http/https/ftp is rejected; local (file / relative) references
     * fall through to Saxon's default resolution. This blocks SSRF and remote exfiltration
     * without preventing legitimate local data access.
     *
     * @param config the Saxon configuration to harden
     */
    public static void restrict(Configuration config) {
        secureSourceParser(config);
        try {
            // doc() / document() (and other resource lookups) go through the ResourceResolver in
            // Saxon 12. Capture the existing (default) resolver, then install a wrapper that blocks
            // remote references and delegates everything local to the default.
            final net.sf.saxon.lib.ResourceResolver defaultResolver = config.getResourceResolver();
            config.setResourceResolver(request -> {
                if (request != null && request.uri != null) {
                    // Schema Library / catalogs first: a mapped URI is served from its local
                    // target (or its cached copy; an uncached remote entry is only downloaded
                    // when SchemaLibraryLookup's offline rule allows it).
                    var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                    java.nio.file.Path mapped = org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                            .localFileFor(library, null, request.uri, request.baseUri,
                                    library.isRemoteDownloadAllowed())
                            .orElse(null);
                    if (mapped != null) {
                        return new javax.xml.transform.stream.StreamSource(mapped.toFile());
                    }
                }
                if (request != null && isRemoteScheme(schemeOfRequest(request))) {
                    throw new net.sf.saxon.trans.XPathException(
                            "Blocked remote resource reference (doc()/document()): " + request.uri);
                }
                // Local / relative reference: delegate to the default resolver (null = Saxon default).
                return defaultResolver != null ? defaultResolver.resolve(request) : null;
            });

            // unparsed-text() / unparsed-text-lines(): block remote, delegate local to Saxon's
            // standard resolver so encoding handling is preserved.
            final net.sf.saxon.lib.UnparsedTextURIResolver standardUnparsedText =
                    new net.sf.saxon.lib.StandardUnparsedTextResolver();
            config.setUnparsedTextURIResolver((absoluteURI, encoding, cfg) -> {
                if (absoluteURI != null && isRemoteScheme(absoluteURI.getScheme())) {
                    throw new net.sf.saxon.trans.XPathException(
                            "Blocked remote resource reference in unparsed-text(): " + absoluteURI);
                }
                return standardUnparsedText.resolve(absoluteURI, encoding, cfg);
            });

            logger.debug("SECURITY: network resource access (doc(), unparsed-text()) is blocked");
        } catch (Exception e) {
            logger.warn("Could not install restrictive resource resolvers: {}", e.getMessage());
        }
    }

    /**
     * Determines whether a URI scheme denotes a network protocol that should be blocked.
     *
     * @param scheme the URI scheme (may be {@code null})
     * @return true for http/https/ftp
     */
    private static boolean isRemoteScheme(String scheme) {
        if (scheme == null) {
            return false;
        }
        String s = scheme.toLowerCase();
        return s.equals("http") || s.equals("https") || s.equals("ftp");
    }

    /**
     * Extracts the effective URI scheme of a Saxon {@link net.sf.saxon.lib.ResourceRequest},
     * resolving a relative reference against its base URI when necessary.
     *
     * @param request the resource request
     * @return the lower-case scheme, or {@code null} if it cannot be determined (treated as local)
     */
    private static String schemeOfRequest(net.sf.saxon.lib.ResourceRequest request) {
        try {
            if (request.uri != null && !request.uri.isBlank()) {
                java.net.URI u = java.net.URI.create(request.uri.trim());
                if (u.getScheme() != null) {
                    return u.getScheme().toLowerCase();
                }
            }
            if (request.relativeUri != null && request.baseUri != null
                    && !request.baseUri.isBlank()) {
                java.net.URI resolved = java.net.URI.create(request.baseUri.trim())
                        .resolve(request.relativeUri.trim());
                return resolved.getScheme() != null ? resolved.getScheme().toLowerCase() : null;
            }
            if (request.baseUri != null && !request.baseUri.isBlank()) {
                return java.net.URI.create(request.baseUri.trim()).getScheme();
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Could not parse resource request URI (uri={}, base={}): {}",
                    request.uri, request.baseUri, e.getMessage());
        }
        return null;
    }
}
