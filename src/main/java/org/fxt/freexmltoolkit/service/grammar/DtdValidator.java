package org.fxt.freexmltoolkit.service.grammar;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Validates an XML document against its DTD with Xerces: the DTD its DOCTYPE declares (internal
 * subset included), or — for a document without one — a DTD chosen by the user.
 *
 * <p>Security: external general entities are never loaded (no XXE); DTDs and their parameter-entity
 * modules are loaded only through {@link GrammarResolver} (catalogs, local files, the schema cache for
 * http(s)); entity expansion is limited by Xerces' security manager (no "billion laughs").
 */
public final class DtdValidator {

    /** Entity expansions allowed per document. */
    static final int ENTITY_EXPANSION_LIMIT = 100_000;

    private DtdValidator() {
    }

    /**
     * @param xml      the document
     * @param systemId the document's location (relative DTD paths resolve against it), or {@code null}
     * @param dtd      a DTD to use instead of the declared one (also for a document without DOCTYPE), or
     *                 {@code null}
     */
    public static List<GrammarProblem> validate(String xml, String systemId, File dtd) {
        List<GrammarProblem> problems = new ArrayList<>();
        if (!GrammarPolicy.dtdAllowed()) {
            problems.add(new GrammarProblem("error", 0, 0,
                    "DTD validation is switched off (Settings → Security, or your administrator's policy).", null));
            return problems;
        }
        Handler handler = new Handler(problems, dtd);
        try {
            SAXParser parser = new SAXParser();
            parser.setFeature("http://xml.org/sax/features/validation", true);
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            parser.setFeature("http://apache.org/xml/features/validation/schema", false);
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
            parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
            parser.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
            parser.setFeature("http://xml.org/sax/features/use-entity-resolver2", true);
            org.apache.xerces.util.SecurityManager security = new org.apache.xerces.util.SecurityManager();
            security.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
            parser.setProperty("http://apache.org/xml/properties/security-manager", security);
            parser.setEntityResolver(handler);
            parser.setErrorHandler(handler);
            parser.setContentHandler(handler);
            InputSource source = new InputSource(new StringReader(xml));
            if (systemId != null) {
                source.setSystemId(systemId);
            }
            parser.parse(source);
        } catch (SAXParseException e) {
            // reported through the error handler already
        } catch (SAXException | IOException e) {
            Unavailable unavailable = unavailable(e);
            problems.add(new GrammarProblem("fatal", 0, 0, unavailable != null ? unavailable.getMessage() : message(e), null));
        }
        return problems;
    }

    /** The DTD (or a module) could not be loaded; the message starts with "Cannot load the DTD". */
    static final class Unavailable extends IOException {
        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static Unavailable unavailable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() != t ? t.getCause() : null) {
            if (t instanceof Unavailable u) {
                return u;
            }
            if (t instanceof SAXException sax && sax.getException() instanceof Unavailable u) {
                return u;
            }
        }
        return null;
    }

    private static String message(Exception e) {
        if (e.getMessage() != null) {
            return e.getMessage();
        }
        Throwable cause = e instanceof SAXException sax && sax.getException() != null ? sax.getException() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    /** Collects problems and resolves the DTD and its modules. */
    private static final class Handler extends DefaultHandler2 {
        private final List<GrammarProblem> problems;
        private final File dtd;

        Handler(List<GrammarProblem> problems, File dtd) {
            this.problems = problems;
            this.dtd = dtd;
        }

        @Override
        public InputSource getExternalSubset(String name, String baseURI) throws IOException {
            // A document without DOCTYPE: the DTD the user chose
            return dtd == null ? null : GrammarResolver.resolve(null, dtd.toURI().toString(), null);
        }

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
                throws SAXException, IOException {
            if (dtd != null && "[dtd]".equals(name)) {
                return GrammarResolver.resolve(null, dtd.toURI().toString(), null);
            }
            if (name != null && !"[dtd]".equals(name) && !name.startsWith("%")) {
                // External general entity: never loaded (XXE); the feature is off, this is the second lock
                throw new SAXException("External entity \"" + name + "\" is not loaded.");
            }
            try {
                return GrammarResolver.resolve(publicId, systemId, baseURI);
            } catch (IOException e) {
                throw new Unavailable("Cannot load the DTD " + (systemId != null ? systemId : publicId) + ": "
                        + e.getMessage(), e);
            }
        }

        @Override
        public void warning(SAXParseException e) {
            add("warning", e);
        }

        @Override
        public void error(SAXParseException e) {
            add("error", e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            add("fatal", e);
            throw e;
        }

        private void add(String severity, SAXParseException e) {
            problems.add(new GrammarProblem(severity, Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
                    e.getMessage(), e.getSystemId()));
        }
    }
}
