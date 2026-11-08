package org.fxt.freexmltoolkit.service.grammar;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.validate.rng.SAXSchemaReader;

/**
 * Validates an XML document against a RELAX NG schema with Jing — XML syntax ({@code .rng}) or compact
 * syntax ({@code .rnc}). Schema and document are read with secure parsers (no external entities);
 * {@code include} and {@code externalRef} go through {@link GrammarResolver}.
 */
public final class RelaxNgValidator {

    private RelaxNgValidator() {
    }

    /**
     * @param xml      the document
     * @param systemId the document's location, or {@code null}
     * @param schema   the {@code .rng} or {@code .rnc} file
     */
    public static List<GrammarProblem> validate(String xml, String systemId, File schema) {
        List<GrammarProblem> problems = new ArrayList<>();
        Collector collector = new Collector(problems);
        PropertyMapBuilder properties = new PropertyMapBuilder();
        properties.put(ValidateProperty.ERROR_HANDLER, collector);
        properties.put(ValidateProperty.XML_READER_CREATOR, RelaxNgValidator::secureReader);
        properties.put(ValidateProperty.RESOLVER, new SecureGrammarResolver());
        boolean compact = GrammarKind.ofFile(schema).orElse(GrammarKind.RELAX_NG) == GrammarKind.RELAX_NG_COMPACT;
        SchemaReader reader = compact ? CompactSchemaReader.getInstance() : SAXSchemaReader.getInstance();
        ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), reader);
        try {
            if (!driver.loadSchema(ValidationDriver.fileInputSource(schema))) {
                if (problems.isEmpty()) {
                    problems.add(new GrammarProblem("fatal", 0, 0, "The RELAX NG schema " + schema.getName()
                            + " could not be loaded.", schema.toURI().toString()));
                }
                // Problems in the schema itself: mark them so they are not taken for document errors
                return problems;
            }
            InputSource document = new InputSource(new StringReader(xml));
            if (systemId != null) {
                document.setSystemId(systemId);
            }
            driver.validate(document);
        } catch (SAXException | IOException e) {
            if (!(e instanceof SAXParseException)) {
                problems.add(new GrammarProblem("fatal", 0, 0, e.getMessage() != null ? e.getMessage() : e.toString(),
                        null));
            }
        }
        return problems;
    }

    /** A namespace-aware parser that loads no DTDs and no external entities. */
    static XMLReader secureReader() throws SAXException {
        org.apache.xerces.parsers.SAXParser parser = new org.apache.xerces.parsers.SAXParser();
        parser.setFeature("http://xml.org/sax/features/namespaces", true);
        parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.apache.xerces.util.SecurityManager security = new org.apache.xerces.util.SecurityManager();
        security.setEntityExpansionLimit(DtdValidator.ENTITY_EXPANSION_LIMIT);
        parser.setProperty("http://apache.org/xml/properties/security-manager", security);
        return parser;
    }

    private record Collector(List<GrammarProblem> problems) implements ErrorHandler {
        @Override
        public void warning(SAXParseException e) {
            add("warning", e);
        }

        @Override
        public void error(SAXParseException e) {
            add("error", e);
        }

        @Override
        public void fatalError(SAXParseException e) {
            add("fatal", e);
        }

        private void add(String severity, SAXParseException e) {
            problems.add(new GrammarProblem(severity, Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
                    e.getMessage(), e.getSystemId()));
        }
    }
}
