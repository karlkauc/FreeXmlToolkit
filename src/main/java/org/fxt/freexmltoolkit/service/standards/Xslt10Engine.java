package org.fxt.freexmltoolkit.service.standards;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.TransformationProfile;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.util.RootedPaths;

/**
 * Runs a stylesheet on the JDK's own XSLT 1.0 processor (XSLTC, {@link TransformerFactory#newDefaultInstance()},
 * independent of Saxon on the class path), so a stylesheet behaves exactly as on a pure XSLT 1.0 / XPath 1.0
 * processor: XSLT 2.0 instructions and XPath 2.0 functions are compile errors, values follow the 1.0 rules.
 *
 * <p>Secure processing is on (no Java extension functions). External DTDs are never read. Included / imported
 * stylesheets and {@code document()} are read through a resolver that allows {@code file:} URIs only — and,
 * with {@code readableRoots} (real paths, see {@link RootedPaths#toRealPaths}), only inside those folders
 * (the MCP sandbox).
 */
public final class Xslt10Engine {

    private Xslt10Engine() {
    }

    /**
     * Transforms {@code xml} with {@code xslt}.
     *
     * @param xmlBaseUri    the input's URI for relative references, or {@code null}
     * @param xsltSystemId  the stylesheet's URI (includes/imports resolve against it), or {@code null}
     * @param parameters    stylesheet parameters (string values), or {@code null}
     * @param outputFormat  the output method, or {@code null} for the stylesheet's own {@code xsl:output}
     * @param readableRoots folders reads are confined to, or {@code null} for any local file
     */
    public static XsltTransformationResult transform(String xml, String xmlBaseUri, String xslt,
                                                     String xsltSystemId, Map<String, Object> parameters,
                                                     OutputFormat outputFormat, List<Path> readableRoots) {
        TransformationProfile profile = new TransformationProfile();
        profile.startTransformation();
        profile.setInputSize(xml == null ? 0 : xml.length());
        Collector errors = new Collector();
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        URIResolver resolver = new LocalResolver(readableRoots);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // the resolver decides; XSLTC checks this before calling it
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "file");
        } catch (Exception e) {
            // the JDK factory supports all of these
        }
        factory.setErrorListener(errors);
        factory.setURIResolver(resolver);

        profile.startCompilation();
        Templates templates;
        try {
            StreamSource stylesheet = new StreamSource(new StringReader(xslt));
            if (xsltSystemId != null) {
                stylesheet.setSystemId(xsltSystemId);
            }
            templates = factory.newTemplates(stylesheet);
        } catch (TransformerException e) {
            return errors.toResult(e, XsltTransformationResult.PHASE_COMPILE);
        }
        profile.endCompilation();
        if (templates == null) {
            return errors.toResult(null, XsltTransformationResult.PHASE_COMPILE);
        }

        try {
            Transformer transformer = templates.newTransformer();
            transformer.setErrorListener(errors);
            transformer.setURIResolver(resolver);
            if (parameters != null) {
                parameters.forEach((name, value) -> transformer.setParameter(name, value == null ? "" : value.toString()));
            }
            if (outputFormat != null) {
                transformer.setOutputProperty(OutputKeys.METHOD, switch (outputFormat) {
                    case HTML, XHTML -> "html";
                    case TEXT, JSON -> "text";
                    default -> "xml";
                });
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            }
            StreamSource input = new StreamSource(new StringReader(xml == null ? "" : xml));
            if (xmlBaseUri != null) {
                input.setSystemId(xmlBaseUri);
            }
            StringWriter out = new StringWriter();
            profile.startTransformationPhase();
            transformer.transform(input, new StreamResult(out));
            profile.endTransformationPhase();
            profile.endTransformation();
            profile.setOutputSize(out.toString().length());
            XsltTransformationResult result = XsltTransformationResult.success(out.toString(),
                    outputFormat == null ? OutputFormat.XML : outputFormat, profile);
            errors.warnings.forEach(result::addWarning);
            return result;
        } catch (TransformerException e) {
            return errors.toResult(e, XsltTransformationResult.PHASE_RUNTIME);
        } catch (RuntimeException e) {
            return XsltTransformationResult.error("XSLT 1.0: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles {@code xslt} on the XSLT 1.0 processor without running it: every compile error as an issue with its
     * line (empty = the stylesheet is valid XSLT 1.0).
     */
    public static List<CompatibilityIssue> compileIssues(String xslt, String xsltSystemId) {
        // An XSLT 1.0 processor compiles a stylesheet declaring a higher version in forwards-compatible mode and
        // silently ignores what it does not know; the check reports the version and compiles strictly as 1.0.
        List<CompatibilityIssue> versionIssues = new ArrayList<>();
        java.util.regex.Matcher root = ROOT_VERSION.matcher(xslt == null ? "" : xslt);
        String strict = xslt;
        if (root.find()) {
            String declared = root.group(3);
            if (!"1.0".equals(declared.strip())) {
                int line = 1;
                for (int i = 0; i < root.start(3); i++) {
                    if (xslt.charAt(i) == '\n') {
                        line++;
                    }
                }
                versionIssues.add(new CompatibilityIssue(line, 0, "FXT-COMPAT-VERSION", "The stylesheet declares "
                        + "version=\"" + declared + "\" but XSLT 1.0 is selected (an XSLT 1.0 processor would run it in "
                        + "forwards-compatible mode and skip what it does not know)."));
                strict = xslt.substring(0, root.start(3)) + "1.0" + xslt.substring(root.end(3));
            }
        }
        List<CompatibilityIssue> compiled = compileStrict(strict, xsltSystemId);
        if (versionIssues.isEmpty()) {
            return compiled;
        }
        versionIssues.addAll(compiled);
        return versionIssues;
    }

    /** {@code <xsl:stylesheet … version="…">} / {@code xsl:transform}, any prefix: group 3 = the version. */
    private static final java.util.regex.Pattern ROOT_VERSION = java.util.regex.Pattern.compile(
            "<\\s*([\\w.-]+:)?(stylesheet|transform)\\b[^>]*?\\bversion\\s*=\\s*[\"']([^\"']*)[\"']");

    private static List<CompatibilityIssue> compileStrict(String xslt, String xsltSystemId) {
        Collector errors = new Collector();
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "file");
        } catch (Exception e) {
            // the JDK factory supports all of these
        }
        factory.setErrorListener(errors);
        factory.setURIResolver(new LocalResolver(null));
        TransformerException thrown = null;
        try {
            StreamSource stylesheet = new StreamSource(new StringReader(xslt));
            if (xsltSystemId != null) {
                stylesheet.setSystemId(xsltSystemId);
            }
            if (factory.newTemplates(stylesheet) != null && errors.errors.isEmpty()) {
                return List.of();
            }
        } catch (TransformerException e) {
            thrown = e;
        }
        List<CompatibilityIssue> issues = new ArrayList<>();
        List<TransformerException> all = new ArrayList<>(errors.errors);
        TransformerException last = thrown;
        if (last != null && all.stream().noneMatch(e -> e == last)) {
            all.add(last);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TransformerException e : all) {
            String message = describe(e);
            int line = e.getLocator() != null ? e.getLocator().getLineNumber() : 0;
            if (!message.isBlank() && seen.add(message)) {
                issues.add(new CompatibilityIssue(Math.max(0, line), 0, "XTSE", message
                        + " (XSLT 1.0)"));
            }
        }
        if (issues.isEmpty()) {
            issues.add(new CompatibilityIssue(0, 0, "XTSE", "The stylesheet does not compile as XSLT 1.0."));
        }
        return issues;
    }

    /** Collects the processor's reports: XSLTC tells the details through the listener, not the exception. */
    private static final class Collector implements ErrorListener {
        private final List<TransformerException> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void warning(TransformerException e) {
            warnings.add(describe(e));
        }

        @Override
        public void error(TransformerException e) {
            errors.add(e);
        }

        @Override
        public void fatalError(TransformerException e) throws TransformerException {
            errors.add(e);
            throw e;
        }

        XsltTransformationResult toResult(TransformerException thrown, String phase) {
            List<TransformerException> all = new ArrayList<>(errors);
            if (thrown != null && all.stream().noneMatch(e -> e == thrown)) {
                all.add(thrown);
            }
            StringBuilder message = new StringBuilder("XSLT 1.0 (JDK XSLTC): ");
            int line = -1;
            boolean first = true;
            for (TransformerException e : all) {
                String text = describe(e);
                if (text.isBlank() || message.indexOf(text) >= 0) {
                    continue;
                }
                if (!first) {
                    message.append(System.lineSeparator());
                }
                message.append(text);
                first = false;
                if (line < 1 && e.getLocator() != null && e.getLocator().getLineNumber() > 0) {
                    line = e.getLocator().getLineNumber();
                }
            }
            if (first) {
                message.append("the stylesheet could not be compiled or run");
            }
            return XsltTransformationResult.error(message.toString(), null, phase, line);
        }
    }

    private static String describe(TransformerException e) {
        String msg = e.getMessage();
        Throwable cause = e;
        while ((msg == null || msg.isBlank()) && cause.getCause() != null) {
            cause = cause.getCause();
            msg = cause.getMessage();
        }
        msg = msg == null ? "" : msg.replaceFirst("^javax\\.xml\\.transform\\.TransformerException: ", "");
        SourceLocator loc = e.getLocator();
        if (loc != null && loc.getLineNumber() > 0 && !msg.contains("line " + loc.getLineNumber())) {
            msg = "line " + loc.getLineNumber() + (loc.getColumnNumber() > 0 ? ", column " + loc.getColumnNumber()
                    : "") + ": " + msg;
        }
        return msg;
    }

    /** Resolves {@code file:} references, confined to the readable roots when given. */
    private record LocalResolver(List<Path> roots) implements URIResolver {
        @Override
        public Source resolve(String href, String base) throws TransformerException {
            try {
                URI uri = base != null && !base.isBlank() ? URI.create(base).resolve(href) : URI.create(href);
                if (!"file".equalsIgnoreCase(uri.getScheme())) {
                    throw new TransformerException("Only local files can be read (" + uri + ")");
                }
                Path path = Path.of(uri);
                if (roots != null && !RootedPaths.isFileUriWithin(uri, roots)) {
                    throw new TransformerException("Reading outside the workspace roots is not allowed: " + href);
                }
                return new StreamSource(path.toFile());
            } catch (IllegalArgumentException e) {
                throw new TransformerException("Cannot resolve " + href + ": " + e.getMessage(), e);
            }
        }
    }
}
