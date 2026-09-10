package org.fxt.freexmltoolkit.service.sampleaudit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.service.SchemaLibraryLookup;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Fail-closed resolver for compiling the audit's reference schemas offline.
 *
 * <ul>
 *   <li>Local references are served from disk, relative to the referencing document.</li>
 *   <li>Remote references are served only through the Schema Library (the corpus folder's OASIS
 *       catalog), never downloaded. A miss is recorded and answered with an empty input: returning
 *       {@code null} would let the bundled Xerces fork fetch the URL itself.</li>
 *   <li>DTDs referenced from schema documents are answered with an empty input.</li>
 * </ul>
 */
final class AuditResourceResolver implements LSResourceResolver {

    private static final String DTD_TYPE = "http://www.w3.org/TR/REC-xml";

    private final SchemaLibraryService library;
    private final Set<String> blockedRemote = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> missingLocal = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> skippedDtds = Collections.synchronizedSet(new LinkedHashSet<>());

    AuditResourceResolver(SchemaLibraryService library) {
        this.library = library;
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
                                   String baseURI) {
        if (systemId == null || systemId.isBlank()) {
            return null; // import without schemaLocation: nothing to fetch
        }
        String absolute = absolutize(systemId, baseURI);
        String scheme = scheme(absolute);

        if (DTD_TYPE.equals(type) || absolute.toLowerCase(Locale.ROOT).endsWith(".dtd")) {
            skippedDtds.add(absolute);
            return new AuditInput(publicId, absolute, new ByteArrayInputStream(new byte[0]));
        }
        if (scheme == null || "file".equals(scheme)) {
            Path file = toPath(absolute);
            if (file != null && Files.isRegularFile(file)) {
                return fileInput(publicId, file);
            }
            // Like the app's validation resolver: a missing file can still be served by namespace
            // (e.g. an xml.xsd import whose relative location does not exist).
            Optional<Path> byNamespace = SchemaLibraryLookup.localFileFor(library, namespaceURI, null, publicId, baseURI, false);
            if (byNamespace.isPresent()) {
                return fileInput(publicId, byNamespace.get());
            }
            missingLocal.add(absolute);
            return new AuditInput(publicId, absolute, new ByteArrayInputStream(new byte[0]));
        }
        Optional<Path> local = SchemaLibraryLookup.localFileFor(library, namespaceURI, absolute, publicId, baseURI, false);
        if (local.isPresent()) {
            return fileInput(publicId, local.get());
        }
        blockedRemote.add(absolute);
        return new AuditInput(publicId, absolute, new ByteArrayInputStream(new byte[0]));
    }

    List<String> blockedRemote() {
        synchronized (blockedRemote) {
            return new ArrayList<>(blockedRemote);
        }
    }

    List<String> missingLocal() {
        synchronized (missingLocal) {
            return new ArrayList<>(missingLocal);
        }
    }

    List<String> skippedDtds() {
        synchronized (skippedDtds) {
            return new ArrayList<>(skippedDtds);
        }
    }

    static String absolutize(String systemId, String baseURI) {
        String location = systemId.trim().replace('\\', '/').replace(" ", "%20");
        try {
            URI uri = new URI(location);
            if (uri.isAbsolute() || baseURI == null || baseURI.isBlank()) {
                return uri.toString();
            }
            return new URI(baseURI.replace(" ", "%20")).resolve(uri).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return location;
        }
    }

    private static String scheme(String uri) {
        int colon = uri.indexOf(':');
        if (colon <= 1) {
            return null; // relative, or a Windows drive letter
        }
        return uri.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    private static Path toPath(String uri) {
        try {
            return uri.startsWith("file:") ? Path.of(URI.create(uri)) : Path.of(uri);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LSInput fileInput(String publicId, Path file) {
        String systemId = file.toAbsolutePath().normalize().toUri().toString();
        try {
            return new AuditInput(publicId, systemId, Files.newInputStream(file));
        } catch (IOException e) {
            return new AuditInput(publicId, systemId, null);
        }
    }

    /** Minimal {@link LSInput}; Xerces detects the encoding from the byte stream. */
    private static final class AuditInput implements LSInput {
        private String publicId;
        private String systemId;
        private String baseURI;
        private InputStream byteStream;

        AuditInput(String publicId, String systemId, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader characterStream) { }
        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream byteStream) { this.byteStream = byteStream; }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String stringData) { }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String systemId) { this.systemId = systemId; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String publicId) { this.publicId = publicId; }
        @Override public String getBaseURI() { return baseURI; }
        @Override public void setBaseURI(String baseURI) { this.baseURI = baseURI; }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String encoding) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean certifiedText) { }
    }
}
