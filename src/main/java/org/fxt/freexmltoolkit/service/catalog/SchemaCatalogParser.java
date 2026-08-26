package org.fxt.freexmltoolkit.service.catalog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses OASIS XML catalogs ({@code system}, {@code public}, {@code uri}, {@code rewriteSystem},
 * {@code rewriteURI}, {@code nextCatalog}, {@code xml:base}) with StAX. Never accesses the network;
 * {@code nextCatalog} is followed for local files only, with a depth cap and cycle protection.
 */
public final class SchemaCatalogParser {

    private static final Logger logger = LogManager.getLogger(SchemaCatalogParser.class);
    public static final int MAX_DEPTH = 10;
    private static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    /** Thrown when a catalog file cannot be read or is not well-formed. */
    public static class CatalogParseException extends IOException {
        public CatalogParseException(String message, Throwable cause) { super(message, cause); }
    }

    private SchemaCatalogParser() { }

    public static ParsedCatalog parse(Path catalog) throws CatalogParseException {
        return parse(catalog.toAbsolutePath().normalize(), 0, new HashSet<>());
    }

    private static ParsedCatalog parse(Path catalog, int depth, Set<Path> visited) throws CatalogParseException {
        visited.add(catalog);
        List<ParsedCatalog.Entry> entries = new ArrayList<>();
        List<Path> nextFiles = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(catalog)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            Deque<URI> bases = new ArrayDeque<>();
            bases.push(catalog.toUri());
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String xmlBase = r.getAttributeValue(XMLConstants.XML_NS_URI, "base");
                    URI base = xmlBase != null ? bases.peek().resolve(xmlBase) : bases.peek();
                    bases.push(base);
                    if (!CATALOG_NS.equals(r.getNamespaceURI()) && r.getNamespaceURI() != null
                            && !r.getNamespaceURI().isEmpty()) {
                        continue;
                    }
                    switch (r.getLocalName()) {
                        case "system" -> add(entries, ParsedCatalog.EntryType.SYSTEM,
                                r.getAttributeValue(null, "systemId"), r.getAttributeValue(null, "uri"), base);
                        case "public" -> add(entries, ParsedCatalog.EntryType.PUBLIC,
                                r.getAttributeValue(null, "publicId"), r.getAttributeValue(null, "uri"), base);
                        case "uri" -> add(entries, ParsedCatalog.EntryType.URI,
                                r.getAttributeValue(null, "name"), r.getAttributeValue(null, "uri"), base);
                        case "rewriteSystem" -> add(entries, ParsedCatalog.EntryType.REWRITE_SYSTEM,
                                r.getAttributeValue(null, "systemIdStartString"),
                                r.getAttributeValue(null, "rewritePrefix"), base);
                        case "rewriteURI" -> add(entries, ParsedCatalog.EntryType.REWRITE_URI,
                                r.getAttributeValue(null, "uriStartString"),
                                r.getAttributeValue(null, "rewritePrefix"), base);
                        case "nextCatalog" -> {
                            String href = r.getAttributeValue(null, "catalog");
                            if (href != null && !href.isBlank()) {
                                URI resolved = base.resolve(href);
                                if ("file".equalsIgnoreCase(resolved.getScheme())) {
                                    nextFiles.add(Path.of(resolved).toAbsolutePath().normalize());
                                } else {
                                    logger.warn("Ignoring non-local nextCatalog '{}' in {}", href, catalog);
                                }
                            }
                        }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    bases.pop();
                }
            }
            r.close();
        } catch (IOException | XMLStreamException | RuntimeException e) {
            throw new CatalogParseException("Cannot parse catalog " + catalog + ": " + e.getMessage(), e);
        }
        List<ParsedCatalog> next = new ArrayList<>();
        for (Path nf : nextFiles) {
            if (visited.contains(nf)) {
                logger.debug("Skipping already visited nextCatalog {}", nf);
                continue;
            }
            if (depth + 1 >= MAX_DEPTH) {
                logger.warn("nextCatalog depth cap ({}) reached at {}", MAX_DEPTH, nf);
                continue;
            }
            if (!Files.isRegularFile(nf)) {
                logger.warn("nextCatalog '{}' referenced from {} does not exist", nf, catalog);
                continue;
            }
            try {
                next.add(parse(nf, depth + 1, visited));
            } catch (CatalogParseException e) {
                logger.warn("Skipping unparsable nextCatalog {}: {}", nf, e.getMessage());
            }
        }
        return new ParsedCatalog(catalog, entries, next);
    }

    private static void add(List<ParsedCatalog.Entry> entries, ParsedCatalog.EntryType type,
                            String key, String target, URI base) {
        if (key == null || key.isBlank() || target == null || target.isBlank()) return;
        String absolute;
        try {
            absolute = normalizeFileUri(base.resolve(target)).toString();
        } catch (IllegalArgumentException e) {
            absolute = target;
        }
        entries.add(new ParsedCatalog.Entry(type, key, absolute));
    }

    /**
     * Restores the canonical {@code file:///path} (empty-authority) form.
     * {@link URI#resolve(String)} drops the empty-authority marker when merging a relative
     * reference into a {@code file:} base, turning {@code file:///a/b} into {@code file:/a/b};
     * that differs from what {@link Path#toUri()} produces, so normalize it back for consistency.
     */
    private static URI normalizeFileUri(URI u) {
        if ("file".equalsIgnoreCase(u.getScheme()) && u.getAuthority() == null
                && u.getPath() != null && u.getPath().startsWith("/")) {
            try {
                return new URI(u.getScheme(), "", u.getPath(), u.getQuery(), u.getFragment());
            } catch (URISyntaxException e) {
                return u;
            }
        }
        return u;
    }
}
