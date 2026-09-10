package org.fxt.freexmltoolkit.service.sampleaudit;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import org.apache.xerces.jaxp.validation.XSGrammarPoolContainer;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSNamespaceItem;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.GlobalElement;
import org.fxt.freexmltoolkit.service.sampleaudit.AuditModels.Issue;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Reference schema compilation and instance validation for the sample XML audit. Uses the bundled
 * Xerces directly (XSD 1.0 or 1.1 factory chosen explicitly), compiles each schema once and keeps
 * every message with its Xerces error key (e.g. {@code cvc-complex-type.2.4.a}).
 */
final class AuditSchemaCompiler {

    static final int MAX_STORED_ISSUES = 50;

    private static final String FULL_CHECKING = "http://apache.org/xml/features/validation/schema-full-checking";
    private static final String HONOUR_ALL_SCHEMA_LOCATIONS = "http://apache.org/xml/features/honour-all-schemaLocations";
    private static final Pattern ERROR_KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_.-]+)?):\\s");

    private AuditSchemaCompiler() {
    }

    /** A compiled reference schema; {@link #ok()} is false when Xerces reported any error. */
    record Compiled(Schema schema, List<Issue> errors, int warningCount, List<GlobalElement> globals, long millis) {
        boolean ok() {
            return schema != null && errors.isEmpty();
        }
    }

    /** Result of checking one instance document. */
    record Validation(boolean wellFormed, String rootElement, int errorCount, int warningCount,
                      Map<String, Integer> errorKeys, List<Issue> issues) {
    }

    static Compiled compile(Path mainXsd, boolean xsd11, LSResourceResolver resolver) {
        long start = System.nanoTime();
        IssueCollector collector = new IssueCollector(null, null);
        Schema schema = null;
        try {
            SchemaFactory factory = xsd11
                    ? new org.apache.xerces.jaxp.validation.XMLSchema11Factory()
                    : new org.apache.xerces.jaxp.validation.XMLSchemaFactory();
            factory.setFeature(FULL_CHECKING, true);
            factory.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS, true);
            factory.setResourceResolver(resolver);
            factory.setErrorHandler(collector);
            try (InputStream in = Files.newInputStream(mainXsd)) {
                String systemId = mainXsd.toAbsolutePath().normalize().toUri().toString();
                schema = factory.newSchema(new StreamSource(in, systemId));
            }
        } catch (SAXException e) {
            collector.record("fatal", e);
        } catch (IOException | RuntimeException e) {
            collector.recordRaw("fatal", e.toString());
        }
        List<Issue> errors = collector.issues.stream().filter(i -> !"warning".equals(i.severity())).toList();
        if (errors.isEmpty() && collector.errorCount > 0) {
            errors = List.of(new Issue("error", "other", "errors reported but not stored", -1, -1, null, null));
        }
        List<GlobalElement> globals = schema == null ? List.of() : globalElements(schema);
        return new Compiled(schema, errors, collector.warningCount, globals, (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Checks well-formedness and, when {@code schema} is not {@code null}, validity of {@code xml}.
     * The validator ignores {@code xsi:schemaLocation} hints; only the compiled schema is used.
     */
    static Validation validate(Schema schema, String xml) {
        ElementPathHandler pathHandler = new ElementPathHandler();
        IssueCollector collector = new IssueCollector(xml, pathHandler);
        boolean wellFormed = true;
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            try {
                parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
                // parser without that feature: generated samples never carry a DOCTYPE anyway
            }
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            if (schema != null) {
                ValidatorHandler validatorHandler = schema.newValidatorHandler();
                validatorHandler.setErrorHandler(collector);
                pathHandler.delegate = validatorHandler;
            }
            reader.setContentHandler(pathHandler);
            reader.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // parser warnings are not well-formedness problems
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            wellFormed = false;
            collector.record("fatal", e);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            wellFormed = false;
            collector.recordRaw("fatal", e.toString());
        }
        return new Validation(wellFormed, pathHandler.root, collector.errorCount, collector.warningCount,
                collector.errorKeys, collector.issues);
    }

    /** The Xerces message key at the start of {@code message}, or {@code other}. */
    static String errorKey(String message) {
        if (message == null) {
            return "other";
        }
        Matcher matcher = ERROR_KEY.matcher(message);
        if (matcher.find()) {
            String key = matcher.group(1);
            if (key.indexOf('-') >= 0 || key.indexOf('.') >= 0 || key.indexOf('_') >= 0) {
                return key;
            }
        }
        return "other";
    }

    /** All global element declarations of the compiled schema, across every namespace. */
    static List<GlobalElement> globalElements(Schema schema) {
        if (!(schema instanceof XSGrammarPoolContainer container)) {
            return List.of();
        }
        XMLGrammarPool pool = container.getGrammarPool();
        if (pool == null) {
            return List.of();
        }
        Map<String, GlobalElement> globals = new LinkedHashMap<>();
        for (Grammar grammar : pool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA)) {
            if (grammar instanceof XSNamespaceItem namespaceItem) {
                XSNamedMap declarations = namespaceItem.getComponents(XSConstants.ELEMENT_DECLARATION);
                for (int i = 0; i < declarations.getLength(); i++) {
                    XSElementDeclaration declaration = (XSElementDeclaration) declarations.item(i);
                    globals.putIfAbsent("{" + declaration.getNamespace() + "}" + declaration.getName(),
                            new GlobalElement(declaration.getNamespace(), declaration.getName(), declaration.getAbstract()));
                }
            }
        }
        return new ArrayList<>(globals.values());
    }

    /** Collects Xerces messages: full counts per key, a capped list of stored issues with snippets. */
    private static final class IssueCollector implements ErrorHandler {
        private final String xml;
        private final ElementPathHandler pathHandler;
        private String[] lines;
        private final Set<String> keysWithSnippet = new HashSet<>();
        final List<Issue> issues = new ArrayList<>();
        final Map<String, Integer> errorKeys = new TreeMap<>();
        int errorCount;
        int warningCount;

        IssueCollector(String xml, ElementPathHandler pathHandler) {
            this.xml = xml;
            this.pathHandler = pathHandler;
        }

        @Override
        public void warning(SAXParseException e) {
            record("warning", e);
        }

        @Override
        public void error(SAXParseException e) {
            record("error", e);
        }

        @Override
        public void fatalError(SAXParseException e) {
            record("fatal", e);
        }

        void record(String severity, SAXException e) {
            String message = e.getMessage();
            int line = -1;
            int column = -1;
            if (e instanceof SAXParseException parseException) {
                line = parseException.getLineNumber();
                column = parseException.getColumnNumber();
                if (xml == null && parseException.getSystemId() != null) {
                    message = message + " [" + parseException.getSystemId() + "]";
                }
            }
            add(severity, message, line, column);
        }

        void recordRaw(String severity, String message) {
            add(severity, message, -1, -1);
        }

        private void add(String severity, String message, int line, int column) {
            String key = errorKey(message);
            if ("warning".equals(severity)) {
                warningCount++;
            } else {
                errorCount++;
                errorKeys.merge(key, 1, Integer::sum);
            }
            if (issues.size() >= MAX_STORED_ISSUES) {
                return;
            }
            String snippet = null;
            if (xml != null && line > 0 && keysWithSnippet.add(key)) {
                snippet = snippet(line);
            }
            String path = pathHandler == null ? null : pathHandler.path();
            issues.add(new Issue(severity, key, message, line, column, path, snippet));
        }

        private String snippet(int line) {
            if (lines == null) {
                lines = xml.split("\n", -1);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(1, line - 3); i <= Math.min(lines.length, line + 1); i++) {
                String text = lines[i - 1];
                if (text.length() > 300) {
                    text = text.substring(0, 300) + "…";
                }
                sb.append(i == line ? ">" : " ").append(i).append(": ").append(text).append('\n');
            }
            return sb.toString();
        }
    }

    /** Forwards SAX events to the validator while tracking the current element path. */
    private static final class ElementPathHandler implements ContentHandler {
        ContentHandler delegate;
        String root;
        private final Deque<String> stack = new ArrayDeque<>();

        String path() {
            return stack.isEmpty() ? null : "/" + String.join("/", stack);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            if (delegate != null) {
                delegate.setDocumentLocator(locator);
            }
        }

        @Override
        public void startDocument() throws SAXException {
            if (delegate != null) {
                delegate.startDocument();
            }
        }

        @Override
        public void endDocument() throws SAXException {
            if (delegate != null) {
                delegate.endDocument();
            }
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            if (delegate != null) {
                delegate.startPrefixMapping(prefix, uri);
            }
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            if (delegate != null) {
                delegate.endPrefixMapping(prefix);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (root == null) {
                root = qName;
            }
            stack.addLast(qName);
            if (delegate != null) {
                delegate.startElement(uri, localName, qName, atts);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (delegate != null) {
                delegate.endElement(uri, localName, qName);
            }
            stack.pollLast();
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (delegate != null) {
                delegate.characters(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            if (delegate != null) {
                delegate.ignorableWhitespace(ch, start, length);
            }
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            if (delegate != null) {
                delegate.processingInstruction(target, data);
            }
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            if (delegate != null) {
                delegate.skippedEntity(name);
            }
        }
    }
}
