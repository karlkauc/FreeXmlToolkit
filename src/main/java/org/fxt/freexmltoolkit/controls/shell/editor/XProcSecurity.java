package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import com.xmlcalabash.XmlCalabash;

/**
 * Restrictions for XProc pipelines, which are programs: by default a pipeline may not run operating
 * system commands ({@code p:os-exec}), run a pipeline computed at run time ({@code p:run}, which
 * could contain anything), send HTTP requests ({@code p:http-request}) or load documents from the
 * network ({@code http:}, {@code https:}, {@code ftp:} hrefs; {@code doc()} in expressions). Local
 * files can be read and written as before. The setting {@value #UNRESTRICTED} (also lockable by the
 * administrator policy) lifts all of it. See docs/threat-model.md.
 */
public final class XProcSecurity {

    private static final Logger logger = LogManager.getLogger(XProcSecurity.class);

    /** Settings key: {@code true} runs pipelines without restrictions. */
    public static final String UNRESTRICTED = "security.xproc.unrestricted";
    static final String XPROC_NAMESPACE = "http://www.w3.org/ns/xproc";
    /** Steps refused unless unrestricted. */
    static final Set<String> RESTRICTED_STEPS = Set.of("os-exec", "run", "http-request");
    /** Imports followed when looking for restricted steps (cycle and size guard). */
    private static final int MAX_IMPORTS = 200;

    private XProcSecurity() {
    }

    /** @return whether the user (or the administrator) allowed unrestricted pipelines */
    public static boolean unrestricted() {
        try {
            return Boolean.parseBoolean(ServiceRegistry.get(PropertiesService.class).get(UNRESTRICTED));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The restricted steps a pipeline uses, including the local libraries it imports
     * ({@code p:import}).
     *
     * @param pipelineFile the pipeline's file (relative imports), or {@code null}
     * @return the step names, e.g. {@code p:os-exec}, in the order found
     */
    public static List<String> restrictedSteps(String pipelineText, File pipelineFile) {
        Set<String> found = new LinkedHashSet<>();
        Set<Path> seen = new HashSet<>();
        Deque<Object[]> pending = new ArrayDeque<>();    // {text, base path or null}
        pending.add(new Object[] {pipelineText, pipelineFile == null ? null : pipelineFile.toPath().toAbsolutePath()});
        int imports = 0;
        while (!pending.isEmpty() && imports < MAX_IMPORTS) {
            Object[] next = pending.poll();
            Path base = (Path) next[1];
            for (String href : scan((String) next[0], found)) {
                Path imported = localImport(base, href);
                if (imported != null && seen.add(imported) && Files.isRegularFile(imported)) {
                    try {
                        pending.add(new Object[] {Files.readString(imported), imported});
                        imports++;
                    } catch (IOException e) {
                        logger.debug("Cannot read XProc import {}: {}", imported, e.getMessage());
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    /** Collects restricted steps of one document into {@code found}; @return its {@code p:import} hrefs */
    private static List<String> scan(String text, Set<String> found) {
        List<String> imports = new java.util.ArrayList<>();
        try {
            XMLStreamReader reader = SecureXmlFactory.createSecureXMLInputFactory()
                    .createXMLStreamReader(new StringReader(text == null ? "" : text));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && XPROC_NAMESPACE.equals(reader.getNamespaceURI())) {
                    String name = reader.getLocalName();
                    if (RESTRICTED_STEPS.contains(name)) {
                        found.add("p:" + name);
                    } else if ("import".equals(name)) {
                        String href = reader.getAttributeValue(null, "href");
                        if (href != null && !href.isBlank()) {
                            imports.add(href.strip());
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            // not well-formed: the parser of the run reports it
        }
        return imports;
    }

    private static Path localImport(Path base, String href) {
        try {
            URI uri = URI.create(href);
            if (uri.getScheme() != null && !"file".equalsIgnoreCase(uri.getScheme())) {
                return null;      // remote: blocked at load time
            }
            if (uri.getScheme() != null) {
                return Path.of(uri).normalize();
            }
            Path dir = base == null ? Path.of("").toAbsolutePath() : base.getParent();
            return dir == null ? null : dir.resolve(uri.getPath()).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the message for a refused pipeline */
    static String refusal(List<String> steps) {
        return "This pipeline uses " + String.join(", ", steps) + ", which " + (steps.size() == 1 ? "is" : "are")
                + " not allowed: XProc pipelines may not run programs, run computed pipelines or send HTTP "
                + "requests. Allow it under Settings > Network & Security > \"Run XProc pipelines without "
                + "restrictions\" only for pipelines you trust.";
    }

    /**
     * Where XML Calabash's HTTP client is sent in the restricted mode: nothing listens there, so every
     * network access of a pipeline ({@code p:load}, {@code p:document}, imports, {@code doc()}) fails.
     * Calabash fetches all of them with its own client and the proxies of its configuration.
     */
    static final String BLACKHOLE = "http://127.0.0.1:1";

    /**
     * Restricts a Calabash instance before it is built: its XML resolver (which fetches documents,
     * imports and {@code doc()} targets itself) may only access {@code file:} and {@code jar:} URIs, and
     * its own HTTP client (the fallback and {@code p:http-request}) is sent to {@link #BLACKHOLE}.
     */
    static void restrict(com.xmlcalabash.XmlCalabashBuilder builder) {
        org.xmlresolver.XMLResolverConfiguration config = new org.xmlresolver.XMLResolverConfiguration();
        config.setFeature(org.xmlresolver.ResolverFeature.ACCESS_EXTERNAL_DOCUMENT, "file,jar");
        config.setFeature(org.xmlresolver.ResolverFeature.ACCESS_EXTERNAL_ENTITY, "file,jar");
        builder.getXmlResolver().set(new org.xmlresolver.XMLResolver(config));
        builder.getProxies().put("http", BLACKHOLE);
        builder.getProxies().put("https", BLACKHOLE);
    }

    /** Hardens the Saxon configuration of a Calabash instance (remote {@code doc()}, parser settings). */
    static void restrict(XmlCalabash calabash) {
        org.fxt.freexmltoolkit.service.SaxonResourceAccess.restrict(
                calabash.getSaxonConfiguration().getProcessor().getUnderlyingConfiguration());
    }

    /** @return the explanation when a run failed on the blocked network, else {@code message} */
    static String explain(String message) {
        if (message != null && (message.contains("127.0.0.1:1") || message.contains("Connection refused")
                || message.contains("access denied"))) {
            return "The pipeline tried to access the network, which XProc pipelines may not do. Allow it under "
                    + "Settings > Network & Security > \"Run XProc pipelines without restrictions\" only for pipelines "
                    + "you trust. (" + message + ")";
        }
        return message;
    }

    static boolean isRemote(String uri) {
        String lower = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("ftp:");
    }
}
