package org.fxt.freexmltoolkit.service.xsd;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Represents a fully parsed XSD schema with all includes resolved.
 * This is the intermediate representation produced by XsdParsingService
 * that can be converted to various output formats.
 */
public final class ParsedSchema {

    private final Document document;
    private final Element schemaElement;
    private final Path sourceFile;
    private final String targetNamespace;
    private final Map<String, String> namespaceDeclarations;
    private final List<ResolvedInclude> resolvedIncludes;
    private final List<ResolvedImport> resolvedImports;
    private final Instant parseTime;
    private final XsdParseOptions options;
    private final String leadingComments;

    private ParsedSchema(Builder builder) {
        this.document = Objects.requireNonNull(builder.document, "document must not be null");
        this.schemaElement = Objects.requireNonNull(builder.schemaElement, "schemaElement must not be null");
        this.sourceFile = builder.sourceFile;
        this.targetNamespace = builder.targetNamespace;
        this.namespaceDeclarations = Map.copyOf(builder.namespaceDeclarations);
        this.resolvedIncludes = List.copyOf(builder.resolvedIncludes);
        this.resolvedImports = List.copyOf(builder.resolvedImports);
        this.parseTime = builder.parseTime != null ? builder.parseTime : Instant.now();
        this.options = builder.options;
        this.leadingComments = builder.leadingComments;
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the parsed DOM document
     */
    public Document getDocument() {
        return document;
    }

    /**
     * @return the root xs:schema element
     */
    public Element getSchemaElement() {
        return schemaElement;
    }

    /**
     * @return the source file path, or empty if parsed from string/URL
     */
    public Optional<Path> getSourceFile() {
        return Optional.ofNullable(sourceFile);
    }

    /**
     * @return the base directory for resolving relative paths
     */
    public Path getBaseDirectory() {
        return sourceFile != null ? sourceFile.getParent() : Path.of(".");
    }

    /**
     * @return the target namespace, or null for no-namespace schemas
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * @return all namespace declarations (prefix -> URI)
     */
    public Map<String, String> getNamespaceDeclarations() {
        return namespaceDeclarations;
    }

    /**
     * @return list of resolved includes
     */
    public List<ResolvedInclude> getResolvedIncludes() {
        return resolvedIncludes;
    }

    /**
     * @return list of resolved imports
     */
    public List<ResolvedImport> getResolvedImports() {
        return resolvedImports;
    }

    /**
     * @return the time when this schema was parsed
     */
    public Instant getParseTime() {
        return parseTime;
    }

    /**
     * @return the options used for parsing
     */
    public XsdParseOptions getOptions() {
        return options;
    }

    /**
     * @return XML comments that appeared before the schema element
     */
    public String getLeadingComments() {
        return leadingComments;
    }

    /**
     * @return all files involved in this schema (main + includes)
     */
    public Set<Path> getAllInvolvedFiles() {
        Set<Path> files = new LinkedHashSet<>();
        if (sourceFile != null) {
            files.add(sourceFile);
        }
        for (ResolvedInclude include : resolvedIncludes) {
            files.add(include.resolvedPath());
            if (include.parsedSchema() != null) {
                files.addAll(include.parsedSchema().getAllInvolvedFiles());
            }
        }
        return files;
    }

    /**
     * @return true if this schema has any resolved includes
     */
    public boolean hasIncludes() {
        return !resolvedIncludes.isEmpty();
    }

    /**
     * @return true if this schema has any resolved imports
     */
    public boolean hasImports() {
        return !resolvedImports.isEmpty();
    }

    /**
     * Gets the elementFormDefault attribute.
     *
     * @return "qualified", "unqualified", or null if not specified
     */
    public String getElementFormDefault() {
        return schemaElement.getAttribute("elementFormDefault");
    }

    /**
     * Gets the attributeFormDefault attribute.
     *
     * @return "qualified", "unqualified", or null if not specified
     */
    public String getAttributeFormDefault() {
        return schemaElement.getAttribute("attributeFormDefault");
    }

    /**
     * Represents a resolved xs:include directive.
     *
     * @param schemaLocation the original schemaLocation attribute
     * @param resolvedPath   the resolved absolute path
     * @param parsedSchema   the parsed included schema (null if not parsed)
     * @param error          error message if resolution failed (null if success)
     */
    public record ResolvedInclude(
            String schemaLocation,
            Path resolvedPath,
            ParsedSchema parsedSchema,
            String error
    ) {
        /**
         * @return true if this include was successfully resolved
         */
        public boolean isResolved() {
            return parsedSchema != null && error == null;
        }
    }

    /**
     * Represents a resolved xs:import directive.
     *
     * @param namespace      the namespace being imported
     * @param schemaLocation the schemaLocation attribute (may be null)
     * @param resolvedPath   the resolved absolute path (may be null for namespace-only imports)
     * @param parsedSchema   the parsed imported schema (null if not loaded)
     * @param error          error message if resolution failed (null if success)
     */
    public record ResolvedImport(
            String namespace,
            String schemaLocation,
            Path resolvedPath,
            ParsedSchema parsedSchema,
            String error
    ) {
        /**
         * @return true if this import was successfully resolved
         */
        public boolean isResolved() {
            return error == null;
        }

        /**
         * @return true if the imported schema was loaded
         */
        public boolean isLoaded() {
            return parsedSchema != null;
        }
    }

    /**
     * Builder for ParsedSchema.
     */
    public static final class Builder {
        private Document document;
        private Element schemaElement;
        private Path sourceFile;
        private String targetNamespace;
        private final Map<String, String> namespaceDeclarations = new LinkedHashMap<>();
        private final List<ResolvedInclude> resolvedIncludes = new ArrayList<>();
        private final List<ResolvedImport> resolvedImports = new ArrayList<>();
        private Instant parseTime;
        private XsdParseOptions options;
        private String leadingComments;

        private Builder() {
        }

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder schemaElement(Element schemaElement) {
            this.schemaElement = schemaElement;
            return this;
        }

        public Builder sourceFile(Path sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder targetNamespace(String targetNamespace) {
            this.targetNamespace = targetNamespace;
            return this;
        }

        public Builder addNamespaceDeclaration(String prefix, String uri) {
            this.namespaceDeclarations.put(prefix, uri);
            return this;
        }

        public Builder namespaceDeclarations(Map<String, String> declarations) {
            this.namespaceDeclarations.clear();
            this.namespaceDeclarations.putAll(declarations);
            return this;
        }

        public Builder addResolvedInclude(ResolvedInclude include) {
            this.resolvedIncludes.add(include);
            return this;
        }

        public Builder resolvedIncludes(List<ResolvedInclude> includes) {
            this.resolvedIncludes.clear();
            this.resolvedIncludes.addAll(includes);
            return this;
        }

        public Builder addResolvedImport(ResolvedImport imp) {
            this.resolvedImports.add(imp);
            return this;
        }

        public Builder resolvedImports(List<ResolvedImport> imports) {
            this.resolvedImports.clear();
            this.resolvedImports.addAll(imports);
            return this;
        }

        public Builder parseTime(Instant parseTime) {
            this.parseTime = parseTime;
            return this;
        }

        public Builder options(XsdParseOptions options) {
            this.options = options;
            return this;
        }

        public Builder leadingComments(String leadingComments) {
            this.leadingComments = leadingComments;
            return this;
        }

        public ParsedSchema build() {
            return new ParsedSchema(this);
        }
    }
}
