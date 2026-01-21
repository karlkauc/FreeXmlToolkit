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

    /** The parsed DOM document containing the XSD schema. */
    private final Document document;

    /** The root xs:schema element of the parsed document. */
    private final Element schemaElement;

    /** The source file path, or null if parsed from string/URL. */
    private final Path sourceFile;

    /** The target namespace of this schema, or null for no-namespace schemas. */
    private final String targetNamespace;

    /** All namespace declarations (prefix to URI mapping). */
    private final Map<String, String> namespaceDeclarations;

    /** List of resolved xs:include directives. */
    private final List<ResolvedInclude> resolvedIncludes;

    /** List of resolved xs:import directives. */
    private final List<ResolvedImport> resolvedImports;

    /** The time when this schema was parsed. */
    private final Instant parseTime;

    /** The options used for parsing this schema. */
    private final XsdParseOptions options;

    /** XML comments that appeared before the schema element. */
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
     * Returns the parsed DOM document containing the XSD schema.
     *
     * @return the parsed DOM document
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Returns the root xs:schema element of the parsed document.
     *
     * @return the root xs:schema element
     */
    public Element getSchemaElement() {
        return schemaElement;
    }

    /**
     * Returns the source file path if available.
     *
     * @return the source file path, or empty if parsed from string/URL
     */
    public Optional<Path> getSourceFile() {
        return Optional.ofNullable(sourceFile);
    }

    /**
     * Returns the base directory for resolving relative paths.
     * If this schema was parsed from a file, returns the parent directory.
     * Otherwise, returns the current working directory.
     *
     * @return the base directory for resolving relative paths
     */
    public Path getBaseDirectory() {
        return sourceFile != null ? sourceFile.getParent() : Path.of(".");
    }

    /**
     * Returns the target namespace of this schema.
     *
     * @return the target namespace, or null for no-namespace schemas
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Returns all namespace declarations defined in this schema.
     *
     * @return all namespace declarations (prefix to URI mapping)
     */
    public Map<String, String> getNamespaceDeclarations() {
        return namespaceDeclarations;
    }

    /**
     * Returns the list of resolved xs:include directives.
     *
     * @return list of resolved includes
     */
    public List<ResolvedInclude> getResolvedIncludes() {
        return resolvedIncludes;
    }

    /**
     * Returns the list of resolved xs:import directives.
     *
     * @return list of resolved imports
     */
    public List<ResolvedImport> getResolvedImports() {
        return resolvedImports;
    }

    /**
     * Returns the timestamp when this schema was parsed.
     *
     * @return the time when this schema was parsed
     */
    public Instant getParseTime() {
        return parseTime;
    }

    /**
     * Returns the options that were used for parsing this schema.
     *
     * @return the options used for parsing
     */
    public XsdParseOptions getOptions() {
        return options;
    }

    /**
     * Returns any XML comments that appeared before the schema element.
     *
     * @return XML comments that appeared before the schema element
     */
    public String getLeadingComments() {
        return leadingComments;
    }

    /**
     * Collects all files involved in this schema, including the main file and all included files recursively.
     *
     * @return all files involved in this schema (main plus includes)
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
     * Checks whether this schema has any resolved xs:include directives.
     *
     * @return true if this schema has any resolved includes
     */
    public boolean hasIncludes() {
        return !resolvedIncludes.isEmpty();
    }

    /**
     * Checks whether this schema has any resolved xs:import directives.
     *
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
         * Checks whether this include was successfully resolved and parsed.
         *
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
         * Checks whether this import was successfully resolved without errors.
         *
         * @return true if this import was successfully resolved
         */
        public boolean isResolved() {
            return error == null;
        }

        /**
         * Checks whether the imported schema was actually loaded and parsed.
         *
         * @return true if the imported schema was loaded
         */
        public boolean isLoaded() {
            return parsedSchema != null;
        }
    }

    /**
     * Builder for creating ParsedSchema instances.
     * Use {@link ParsedSchema#builder()} to obtain a new builder instance.
     */
    public static final class Builder {
        /** The parsed DOM document. */
        private Document document;

        /** The root xs:schema element. */
        private Element schemaElement;

        /** The source file path. */
        private Path sourceFile;

        /** The target namespace. */
        private String targetNamespace;

        /** Namespace declarations (prefix to URI). */
        private final Map<String, String> namespaceDeclarations = new LinkedHashMap<>();

        /** List of resolved includes. */
        private final List<ResolvedInclude> resolvedIncludes = new ArrayList<>();

        /** List of resolved imports. */
        private final List<ResolvedImport> resolvedImports = new ArrayList<>();

        /** The parse timestamp. */
        private Instant parseTime;

        /** The parse options. */
        private XsdParseOptions options;

        /** Leading comments before the schema element. */
        private String leadingComments;

        /**
         * Private constructor to enforce use of static factory method.
         */
        private Builder() {
        }

        /**
         * Sets the DOM document containing the parsed XSD schema.
         *
         * @param document the parsed DOM document
         * @return this builder for method chaining
         */
        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        /**
         * Sets the root xs:schema element.
         *
         * @param schemaElement the root schema element
         * @return this builder for method chaining
         */
        public Builder schemaElement(Element schemaElement) {
            this.schemaElement = schemaElement;
            return this;
        }

        /**
         * Sets the source file path from which the schema was parsed.
         *
         * @param sourceFile the source file path
         * @return this builder for method chaining
         */
        public Builder sourceFile(Path sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        /**
         * Sets the target namespace of the schema.
         *
         * @param targetNamespace the target namespace URI
         * @return this builder for method chaining
         */
        public Builder targetNamespace(String targetNamespace) {
            this.targetNamespace = targetNamespace;
            return this;
        }

        /**
         * Adds a namespace declaration to the schema.
         *
         * @param prefix the namespace prefix
         * @param uri    the namespace URI
         * @return this builder for method chaining
         */
        public Builder addNamespaceDeclaration(String prefix, String uri) {
            this.namespaceDeclarations.put(prefix, uri);
            return this;
        }

        /**
         * Sets all namespace declarations, replacing any existing ones.
         *
         * @param declarations the namespace declarations (prefix to URI mapping)
         * @return this builder for method chaining
         */
        public Builder namespaceDeclarations(Map<String, String> declarations) {
            this.namespaceDeclarations.clear();
            this.namespaceDeclarations.putAll(declarations);
            return this;
        }

        /**
         * Adds a resolved include to the schema.
         *
         * @param include the resolved include information
         * @return this builder for method chaining
         */
        public Builder addResolvedInclude(ResolvedInclude include) {
            this.resolvedIncludes.add(include);
            return this;
        }

        /**
         * Sets all resolved includes, replacing any existing ones.
         *
         * @param includes the list of resolved includes
         * @return this builder for method chaining
         */
        public Builder resolvedIncludes(List<ResolvedInclude> includes) {
            this.resolvedIncludes.clear();
            this.resolvedIncludes.addAll(includes);
            return this;
        }

        /**
         * Adds a resolved import to the schema.
         *
         * @param imp the resolved import information
         * @return this builder for method chaining
         */
        public Builder addResolvedImport(ResolvedImport imp) {
            this.resolvedImports.add(imp);
            return this;
        }

        /**
         * Sets all resolved imports, replacing any existing ones.
         *
         * @param imports the list of resolved imports
         * @return this builder for method chaining
         */
        public Builder resolvedImports(List<ResolvedImport> imports) {
            this.resolvedImports.clear();
            this.resolvedImports.addAll(imports);
            return this;
        }

        /**
         * Sets the timestamp when the schema was parsed.
         *
         * @param parseTime the parse timestamp
         * @return this builder for method chaining
         */
        public Builder parseTime(Instant parseTime) {
            this.parseTime = parseTime;
            return this;
        }

        /**
         * Sets the parse options that were used.
         *
         * @param options the parse options
         * @return this builder for method chaining
         */
        public Builder options(XsdParseOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Sets any XML comments that appeared before the schema element.
         *
         * @param leadingComments the leading comments
         * @return this builder for method chaining
         */
        public Builder leadingComments(String leadingComments) {
            this.leadingComments = leadingComments;
            return this;
        }

        /**
         * Builds a new ParsedSchema instance from the configured values.
         *
         * @return a new ParsedSchema instance
         * @throws NullPointerException if document or schemaElement is null
         */
        public ParsedSchema build() {
            return new ParsedSchema(this);
        }
    }
}
