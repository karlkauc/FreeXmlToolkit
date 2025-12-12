package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

import javax.xml.validation.Schema;
import java.nio.file.Path;

/**
 * Unified service for parsing XSD schema files.
 *
 * <p>This service provides a single entry point for all XSD parsing operations,
 * supporting both local files and remote URLs, with configurable handling of
 * xs:include and xs:import directives.</p>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Simple parsing with defaults
 * XsdParsingService service = new XsdParsingServiceImpl();
 * ParsedSchema parsed = service.parse(Paths.get("schema.xsd"));
 *
 * // Convert to V2 model for visual editing
 * XsdSchema model = service.toXsdModel(parsed);
 *
 * // Convert to validation schema
 * Schema validationSchema = service.toValidationSchema(parsed, XsdVersion.XSD_1_1);
 *
 * // Parsing with custom options (flatten includes)
 * XsdParseOptions options = XsdParseOptions.forFlattening();
 * ParsedSchema flattened = service.parse(Paths.get("schema.xsd"), options);
 * }</pre>
 *
 * <h2>Include/Import Handling</h2>
 *
 * <ul>
 *   <li><b>xs:include</b>: Can be flattened (inlined) or preserved (structure kept)</li>
 *   <li><b>xs:import</b>: Loaded as references, not inlined into main schema</li>
 * </ul>
 *
 * @see ParsedSchema
 * @see XsdParseOptions
 */
public interface XsdParsingService {

    /**
     * XSD version for validation schema creation.
     */
    enum XsdVersion {
        /** XSD 1.0 - widely supported */
        XSD_1_0,
        /** XSD 1.1 - extended features (assertions, conditional type assignment) */
        XSD_1_1
    }

    /**
     * Parses an XSD file with default options.
     *
     * @param xsdFile path to the XSD file
     * @return the parsed schema
     * @throws XsdParseException if parsing fails
     */
    ParsedSchema parse(Path xsdFile) throws XsdParseException;

    /**
     * Parses an XSD file with custom options.
     *
     * @param xsdFile path to the XSD file
     * @param options parsing options
     * @return the parsed schema
     * @throws XsdParseException if parsing fails
     */
    ParsedSchema parse(Path xsdFile, XsdParseOptions options) throws XsdParseException;

    /**
     * Parses XSD content from a string.
     *
     * @param content       the XSD content
     * @param baseDirectory base directory for resolving relative schema locations
     * @return the parsed schema
     * @throws XsdParseException if parsing fails
     */
    ParsedSchema parse(String content, Path baseDirectory) throws XsdParseException;

    /**
     * Parses XSD content from a string with custom options.
     *
     * @param content       the XSD content
     * @param baseDirectory base directory for resolving relative schema locations
     * @param options       parsing options
     * @return the parsed schema
     * @throws XsdParseException if parsing fails
     */
    ParsedSchema parse(String content, Path baseDirectory, XsdParseOptions options) throws XsdParseException;

    /**
     * Parses an XSD from a URL (HTTP/HTTPS).
     *
     * @param url the URL to fetch the XSD from
     * @return the parsed schema
     * @throws XsdParseException if fetching or parsing fails
     */
    ParsedSchema parseFromUrl(String url) throws XsdParseException;

    /**
     * Parses an XSD from a URL with custom options.
     *
     * @param url     the URL to fetch the XSD from
     * @param options parsing options
     * @return the parsed schema
     * @throws XsdParseException if fetching or parsing fails
     */
    ParsedSchema parseFromUrl(String url, XsdParseOptions options) throws XsdParseException;

    /**
     * Converts a parsed schema to the V2 XsdSchema model.
     * Used for visual editing in the XSD Editor.
     *
     * @param parsedSchema the parsed schema
     * @return the XsdSchema model
     * @throws XsdParseException if conversion fails
     */
    XsdSchema toXsdModel(ParsedSchema parsedSchema) throws XsdParseException;

    /**
     * Converts a parsed schema to a javax.xml.validation.Schema.
     * Used for XML validation.
     *
     * @param parsedSchema the parsed schema
     * @param version      the XSD version to use
     * @return the validation Schema
     * @throws XsdParseException if schema creation fails
     */
    Schema toValidationSchema(ParsedSchema parsedSchema, XsdVersion version) throws XsdParseException;

    /**
     * Checks if a file is a valid XSD schema without full parsing.
     * This is a lightweight check that verifies the root element is xs:schema.
     *
     * @param xsdFile path to the file to check
     * @return true if the file appears to be a valid XSD schema
     */
    boolean isValidXsd(Path xsdFile);

    /**
     * Clears any cached schemas.
     * Call this when you need to ensure fresh parsing.
     */
    void clearCache();

    /**
     * Gets statistics about the cache.
     *
     * @return cache statistics
     */
    CacheStatistics getCacheStatistics();

    /**
     * Cache statistics.
     *
     * @param hits       number of cache hits
     * @param misses     number of cache misses
     * @param size       current number of cached entries
     * @param memorySizeBytes approximate memory size in bytes
     */
    record CacheStatistics(long hits, long misses, int size, long memorySizeBytes) {
        /**
         * @return the cache hit ratio (0.0 to 1.0)
         */
        public double hitRatio() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }
}
