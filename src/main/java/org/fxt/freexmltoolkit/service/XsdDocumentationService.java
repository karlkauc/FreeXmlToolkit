/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getAttributeValue;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getDirectChildElement;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getDirectChildElements;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.nodeListToList;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.WILDCARD_TYPE;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.findDefaultRootElement;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.findRootElement;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.findRootElements;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.isAbstractRoot;
import static org.fxt.freexmltoolkit.service.XsdSampleXmlSupport.wildcardSampleNamespace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.OpenContent;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.RestrictionInfo;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate.Status;
import org.fxt.freexmltoolkit.util.PathValidator;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class XsdDocumentationService {

    static final int MAX_ALLOWED_DEPTH = 99;
    private static final Logger logger = LogManager.getLogger(XsdDocumentationService.class);
    private static final String NS_PREFIX = "xs";
    private static final String NS_URI = "http://www.w3.org/2001/XMLSchema";

    String xsdFilePath;
    public XsdDocumentationData xsdDocumentationData = new XsdDocumentationData();

    /**
     * All local schema files involved in the current schema, collected transitively
     * (main schema plus every resolved {@code xs:include} / local {@code xs:import} and any
     * downloaded remote import). Populated by {@link #processAllSchemas()} and consumed when
     * copying the schema sources next to the generated HTML documentation.
     */
    private final java.util.Set<java.nio.file.Path> processedSchemaFiles = new java.util.LinkedHashSet<>();

    int counter;
    boolean parallelProcessing = true;

    public enum ImageOutputMethod {
        SVG, PNG, JPG
    }

    /**
     * How the Markdown renderer is applied to {@code xs:documentation} content.
     * <ul>
     *   <li>{@link #OFF} - never render Markdown</li>
     *   <li>{@link #ALL} - render every node's documentation as Markdown</li>
     *   <li>{@link #PER_NODE} - render only nodes whose {@code xs:appinfo} says
     *       {@code @markdown = true}</li>
     * </ul>
     */
    public enum MarkdownMode {
        OFF, ALL, PER_NODE
    }

    private ImageOutputMethod imageOutputMethod = ImageOutputMethod.SVG;
    MarkdownMode markdownMode = MarkdownMode.ALL;
    Boolean includeTypeDefinitionsInSourceCode = false;

    private TaskProgressListener progressListener;
    private final XsdSampleDataGenerator xsdSampleDataGenerator = new XsdSampleDataGenerator();
    /** Generate every element's sample during traversal; {@code false} defers it to the first read. */
    private boolean eagerSampleData = true;
    private RandomGenerator random = RandomGenerator.getDefault();

    /** Separates the value stream from the choice stream of one seed, so a changed choice does not shift values. */
    static final long VALUE_SEED_SALT = 0x5DEECE66DL;

    /**
     * Folder the generated samples will be written to; the schema reference in their root
     * element is made relative to it. {@code null} (the default) means "unsaved document":
     * the schema is then referenced by its bare file name.
     */
    private java.nio.file.Path sampleOutputDirectory;

    /**
     * @param outputDirectory the folder the generated samples will be written to, or
     *                        {@code null} for a sample that opens unsaved in the editor
     * @see SampleSchemaLocation#forSample(String, java.nio.file.Path)
     */
    public void setSampleOutputDirectory(java.nio.file.Path outputDirectory) {
        this.sampleOutputDirectory = outputDirectory;
    }

    /**
     * Makes the samples of this service reproducible: choices, repetitions and values are drawn from sources seeded
     * with {@code seed}. Call it before each sample that should not depend on the samples generated before it.
     */
    public void setSampleSeed(long seed) {
        sampleSeed = seed;
        reseedSample();
    }

    /**
     * Restarts the seeded streams. Loading the schema draws values of its own, and how many depends on what the
     * process did before, so every expansion and every document starts from the seed again.
     */
    private void reseedSample() {
        if (sampleSeed == null) {
            return;
        }
        random = new java.util.Random(sampleSeed);
        xsdSampleDataGenerator.setRandom(new java.util.Random(sampleSeed ^ VALUE_SEED_SALT));
        xsdSampleDataGenerator.setClock(XsdSampleDataGenerator.SEEDED_CLOCK);
    }

    /** The seed of reproducible samples, or {@code null} for random ones. */
    public Long sampleSeed() {
        return sampleSeed;
    }

    private Long sampleSeed;
    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
    /** Reads annotations, identity constraints, assertions, alternatives, open content and wildcards. */
    private final XsdComponentDetailsReader componentDetails = new XsdComponentDetailsReader(this);
    XsdDocumentationSvgService xsdDocumentationSvgService = new XsdDocumentationSvgService();

    // DOM/XPath related fields
    private XPath xpath;
    private Document doc;
    private Map<String, Node> elementMap = new HashMap<>();
    Map<String, Node> complexTypeMap = new HashMap<>();
    Map<String, Node> simpleTypeMap = new HashMap<>();
    private Map<String, Node> groupMap = new HashMap<>();
    private Map<String, Node> attributeGroupMap = new HashMap<>();
    private Map<String, Node> attributeMap = new HashMap<>();
    private static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
    /** Prefix per namespace chosen for samples, so two namespaces never share a prefix. */
    private final Map<String, String> samplePrefixes = new HashMap<>();
    /** Global type definitions keyed by kind, target namespace and local name ({@link #qualifiedKey}). */
    private final Map<String, Node> qualifiedGlobalDefs = new HashMap<>();
    /**
     * Source code snippet per schema node for the current run. A node is expanded once per XPath it is reachable
     * under, and every copy carries the same snippet (it depends on the node and the run's options only), so the
     * copies share one String instead of serializing the node again.
     */
    private final Map<Node, String> sourceSnippetMemo = new java.util.concurrent.ConcurrentHashMap<>();
    /** Documentation entry per xs:documentation node for the current run, shared by all expanded copies. */
    final Map<Node, DocumentationInfo> documentationMemo = new java.util.concurrent.ConcurrentHashMap<>();

    /** Global elements of included documents of the main target namespace (sample root candidates). */
    private final List<Node> includedGlobalElements = new ArrayList<>();
    /** Local names of all elements referenced via {@code ref} in any schema document. */
    private final Set<String> referencedElementNames = new HashSet<>();
    /**
     * Substitution group members keyed by their head ({@link #qualifiedKey} of kind {@code substitutionHead}, plus a
     * local-name fallback of kind {@code substitutionHead*}), in document order.
     */
    private final Map<String, List<Node>> substitutionMembers = new HashMap<>();
    /**
     * Global complex types keyed by the base type they extend or restrict (kind {@code derivedFrom}, plus a local-name
     * fallback of kind {@code derivedFrom*}), in document order.
     */
    private final Map<String, List<Node>> derivedComplexTypes = new HashMap<>();

    /** Set by {@link #processXsd}: every global element is expanded, so sample generation needs no expansion. */
    private boolean fullyProcessed;
    private boolean schemaLoaded;
    /** Root, mode and Markdown mode of the current {@link #expandForSample} expansion, or {@code null}. */
    private String sampleExpansionKey;
    /** Node limit while expanding for a sample; -1 outside such an expansion. */
    private int expansionNodeLimit = -1;
    private String expansionRootName;
    /** Skip optional particles while expanding for a mandatory-only sample. */
    private boolean pruneOptionalParticles;
    /** Set while {@link #expandForSample} runs; the documentation expansion is never bounded this way. */
    private boolean sampleExpansion;
    /** Element declarations whose optional content the current sample expansion has already expanded. */
    private final Set<Node> sampleOptionalContentExpanded = Collections.newSetFromMap(new IdentityHashMap<>());

    // Language configuration for documentation generation
    final Set<String> discoveredLanguages = new LinkedHashSet<>();
    private Set<String> includedLanguages = null; // null = all languages
    private String fallbackLanguage = null; // Fallback language when "default" is not available

    // SVG documentation display configuration
    private boolean showDocumentationInSvg = true; // Whether to show documentation in SVG diagrams
    private boolean showConstraintsInSvg = true; // Whether element boxes list constraints and assertions
    private boolean showRootConstraintsInSvg = true; // ... the page's own element box as well
    private boolean showConstraintTargets = false; // Whether field targets list the constraints pointing to them
    private boolean generateSvgOverviewPage = true; // Whether to generate the schema-svg.html overview page
    private boolean addMetadataInOutput = false; // Whether to add metadata comments to generated HTML files
    private String faviconPath = null; // Optional path to custom favicon for HTML documentation
    private boolean deduplicateDataDictionaryByType = false; // List each named type only once in the data dictionary

    // Per-run memo of XPath-independent identity-constraint results, keyed by the source DOM node.
    // Repeated expansions of a shared type reuse the cached result instead of re-parsing constraints.
    private final Map<Node, XsdExtendedElement> identityConstraintMemo = new IdentityHashMap<>();

    // Per-run memo of fully resolved named types, keyed by local type name. A named type always
    // resolves to the same base type + merged facets regardless of the XPath it is reached from,
    // so the (potentially deep) type-hierarchy walk only has to run once per type name instead of
    // once per element that uses it. Only fully resolved (non-null) results are cached; null
    // results (type-not-found or circular) are path-dependent and therefore never cached.
    private final Map<String, XsdSampleDataGenerator.ResolvedType> resolvedTypeMemo = new HashMap<>();

    // Thread-local storage for element reference nodes (to preserve cardinality attributes)
    private static final ThreadLocal<Node> referenceNodeThreadLocal = new ThreadLocal<>();
    /** The group reference whose group's compositor is being expanded; its bounds are the compositor's. */
    private static final ThreadLocal<Node> groupReferenceThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> forcedNamespacePrefixThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> forcedNamespaceUriThreadLocal = new ThreadLocal<>();

    // Provides a separate, thread-safe Transformer instance for each thread.
    // This avoids the cost of constant recreation in a parallel environment.
    private static final ThreadLocal<Transformer> transformerThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            TransformerFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory();
            // Security feature to block external DTDs and entities
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(ServiceRegistry.get(PropertiesService.class).getXmlIndentSpaces()));
            return transformer;
        } catch (Exception e) {
            // If initialization fails, a RuntimeException is thrown.
            // This is appropriate in a ThreadLocal initializer.
            throw new RuntimeException("Failed to initialize thread-local Transformer", e);
        }
    });

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
    }

    /**
     * Returns all local schema files involved in the current schema, collected transitively
     * during {@link #processAllSchemas()} (main schema plus every resolved {@code xs:include},
     * local {@code xs:import} and any downloaded remote import). The set is empty until the
     * schema has been processed.
     *
     * @return the involved schema files as normalized absolute paths (insertion order preserved)
     */
    public java.util.Set<java.nio.file.Path> getProcessedSchemaFiles() {
        return processedSchemaFiles;
    }

    public ImageOutputMethod getImageOutputMethod() {
        return imageOutputMethod;
    }

    public void setMethod(ImageOutputMethod method) {
        this.imageOutputMethod = method;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    /**
     * Sets how the Markdown renderer is applied.
     *
     * @param markdownMode the mode; null falls back to {@link MarkdownMode#ALL}
     */
    public void setMarkdownMode(MarkdownMode markdownMode) {
        this.markdownMode = markdownMode == null ? MarkdownMode.ALL : markdownMode;
    }

    /**
     * @return how the Markdown renderer is applied
     */
    public MarkdownMode getMarkdownMode() {
        return markdownMode;
    }

    /**
     * Compatibility overload of {@link #setMarkdownMode(MarkdownMode)}.
     *
     * @param useMarkdownRenderer true maps to {@link MarkdownMode#ALL}, false to
     *                            {@link MarkdownMode#OFF}
     */
    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        setMarkdownMode(Boolean.FALSE.equals(useMarkdownRenderer) ? MarkdownMode.OFF : MarkdownMode.ALL);
    }

    /** @return the per-element default derived from the current {@link MarkdownMode}. */
    private boolean defaultMarkdownRendering() {
        return markdownMode == MarkdownMode.ALL;
    }

    /**
     * Resolves the effective Markdown rendering for one node.
     *
     * <p>{@link MarkdownMode#OFF} is a hard kill switch; otherwise a node's own {@code @markdown}
     * beats the mode's default, so {@code ALL} still honours a node that opts out.
     *
     * @param nodeFlag the node's stated preference, or null when it says nothing
     * @return whether this node's documentation renders as Markdown
     */
    boolean resolveMarkdownRendering(Boolean nodeFlag) {
        if (markdownMode == MarkdownMode.OFF) {
            return false;
        }
        return nodeFlag != null ? nodeFlag : defaultMarkdownRendering();
    }

    public void setIncludeTypeDefinitionsInSourceCode(Boolean includeTypeDefinitionsInSourceCode) {
        this.includeTypeDefinitionsInSourceCode = includeTypeDefinitionsInSourceCode;
    }

    /**
     * Sets whether to generate the SVG overview page (schema-svg.html).
     * When disabled, no SVG overview page is generated and the SVG link
     * is hidden in the navigation of all HTML pages.
     *
     * @param generateSvgOverviewPage true to generate the SVG page (default), false to skip it
     */
    public void setGenerateSvgOverviewPage(boolean generateSvgOverviewPage) {
        this.generateSvgOverviewPage = generateSvgOverviewPage;
    }

    /**
     * Sets whether to add metadata comments to generated HTML files.
     * When enabled, HTML comments containing timestamp, OS, app version, and source file
     * are added to all generated HTML documentation files.
     *
     * @param addMetadataInOutput true to add metadata comments, false to skip them (default)
     */
    public void setAddMetadataInOutput(boolean addMetadataInOutput) {
        this.addMetadataInOutput = addMetadataInOutput;
    }

    /**
     * Returns whether metadata comments should be added to generated HTML files.
     *
     * @return true if metadata comments should be added, false otherwise
     */
    public boolean isAddMetadataInOutput() {
        return addMetadataInOutput;
    }

    /**
     * Sets the path to a custom favicon file for HTML documentation.
     * Supported formats: .ico, .png, .svg
     *
     * @param faviconPath Path to the favicon file, or null/empty to not include a favicon
     */
    public void setFaviconPath(String faviconPath) {
        this.faviconPath = faviconPath;
    }

    /**
     * Sets whether the data dictionary lists each named complex/simple type only once (the first
     * occurrence) instead of every recursive occurrence. Elements with a built-in or no type are
     * always listed individually.
     *
     * @param deduplicateDataDictionaryByType {@code true} to collapse repeated named types
     */
    public void setDeduplicateDataDictionaryByType(boolean deduplicateDataDictionaryByType) {
        this.deduplicateDataDictionaryByType = deduplicateDataDictionaryByType;
    }

    /**
     * Returns the configured favicon path.
     *
     * @return Path to the favicon file, or null if not set
     */
    public String getFaviconPath() {
        return faviconPath;
    }

    /**
     * Returns all languages discovered during XSD parsing.
     * Must be called after processXsd() has completed.
     *
     * @return An unmodifiable set of language codes (e.g., "en", "de", "default")
     */
    public Set<String> getDiscoveredLanguages() {
        return Collections.unmodifiableSet(discoveredLanguages);
    }

    /**
     * Sets the languages to include in documentation output.
     * Pass null to include all languages.
     *
     * @param languages Set of language codes to include, or null for all
     */
    public void setIncludedLanguages(Set<String> languages) {
        if (languages != null) {
            // Filter out null values to prevent NPE in downstream operations
            this.includedLanguages = languages.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            this.includedLanguages = null;
        }
    }

    /**
     * Tells whether documentation in the given (lower-case) language should be part of the output.
     * The untagged "default" documentation is always kept as fallback; without a configured
     * selection every language is included.
     *
     * @param normalizedLang lower-case language code, or "default" for untagged documentation
     * @return true if documentation in this language must be collected
     */
    boolean isLanguageIncluded(String normalizedLang) {
        if (includedLanguages == null || includedLanguages.isEmpty() || "default".equals(normalizedLang)) {
            return true;
        }
        return includedLanguages.stream().anyMatch(lang -> lang.equalsIgnoreCase(normalizedLang));
    }

    /**
     * Returns the configured included languages.
     *
     * @return Set of included language codes, or null if all languages are included
     */
    public Set<String> getIncludedLanguages() {
        return includedLanguages == null ? null : Collections.unmodifiableSet(includedLanguages);
    }

    /**
     * Sets the fallback language to use when "default" (no language tag) documentation is not available.
     *
     * @param language The language code to use as fallback (e.g., "en", "de"), or null for no fallback
     */
    public void setFallbackLanguage(String language) {
        this.fallbackLanguage = language;
    }

    /**
     * Returns the configured fallback language.
     *
     * @return The fallback language code, or null if no fallback is configured
     */
    public String getFallbackLanguage() {
        return fallbackLanguage;
    }

    /**
     * Sets whether documentation should be displayed in SVG diagrams.
     *
     * @param showDocumentation true to show documentation, false to hide it
     */
    public void setShowDocumentationInSvg(boolean showDocumentation) {
        this.showDocumentationInSvg = showDocumentation;
    }

    /**
     * Sets whether the element boxes of the diagrams list their identity constraints and
     * assertions, as the cards of the Graphic view do.
     *
     * @param showConstraints true to list them (the default)
     */
    public void setShowConstraintsInSvg(boolean showConstraints) {
        this.showConstraintsInSvg = showConstraints;
    }

    /** @return whether the element boxes of the diagrams list constraints and assertions */
    public boolean isShowConstraintsInSvg() {
        return showConstraintsInSvg;
    }

    /**
     * Sets whether the box of a page's own element lists its constraints as well; off, only the
     * child boxes do.
     *
     * @param showRootConstraints true to list them there too (the default)
     */
    public void setShowRootConstraintsInSvg(boolean showRootConstraints) {
        this.showRootConstraintsInSvg = showRootConstraints;
    }

    /** @return whether the box of a page's own element lists its constraints too */
    public boolean isShowRootConstraintsInSvg() {
        return showRootConstraintsInSvg;
    }

    /**
     * Sets whether a constraint's field target (e.g. {@code BenchmarkID} for a key on {@code Fund})
     * lists the constraint too: in its diagram box and in a "Referenced by Identity Constraints"
     * section of its page, linked to the declaring element. The diagram rows need
     * {@link #setShowConstraintsInSvg constraints in diagrams}.
     *
     * @param showConstraintTargets true to show them (default false)
     */
    public void setShowConstraintTargets(boolean showConstraintTargets) {
        this.showConstraintTargets = showConstraintTargets;
    }

    /** @return whether a constraint's field target lists it too */
    public boolean isShowConstraintTargets() {
        return showConstraintTargets;
    }

    /**
     * Returns whether documentation is displayed in SVG diagrams.
     *
     * @return true if documentation is shown, false otherwise
     */
    public boolean isShowDocumentationInSvg() {
        return showDocumentationInSvg;
    }

    public void generateXsdDocumentation(File outputDirectory) throws Exception {
        logger.debug("Starting documentation generation...");
        processXsd(this.markdownMode);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);
        xsdDocumentationHtmlService.setIncludedLanguages(this.includedLanguages);
        xsdDocumentationHtmlService.setAddMetadataInOutput(this.addMetadataInOutput);
        xsdDocumentationHtmlService.setFaviconPath(this.faviconPath);
        xsdDocumentationHtmlService.setDeduplicateDataDictionaryByType(this.deduplicateDataDictionaryByType);

        xsdDocumentationSvgService.setOutputDirectory(outputDirectory);
        xsdDocumentationSvgService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationSvgService.setFaviconPath(this.faviconPath);

        // The ImageService is now centrally initialized here so it's ready for pre-creation.
        xsdDocumentationHtmlService.xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());
        xsdDocumentationHtmlService.xsdDocumentationImageService.setShowDocumentation(this.showDocumentationInSvg);
        xsdDocumentationHtmlService.xsdDocumentationImageService.setShowConstraints(this.showConstraintsInSvg);
        xsdDocumentationHtmlService.xsdDocumentationImageService.setShowRootConstraints(this.showRootConstraintsInSvg);
        xsdDocumentationHtmlService.xsdDocumentationImageService.setShowConstraintTargets(this.showConstraintTargets);

        executeAndTrack("Copying resources", xsdDocumentationHtmlService::copyResources);
        executeAndTrack("Generating root page", xsdDocumentationHtmlService::generateRootPage);
        if (generateSvgOverviewPage) {
            executeAndTrack("Generating SVG page", xsdDocumentationSvgService::generateSvgPage);
        }
        executeAndTrack("Generating list of complex types", xsdDocumentationHtmlService::generateComplexTypesListPage);
        executeAndTrack("Generating list of simple types", xsdDocumentationHtmlService::generateSimpleTypesListPage);
        executeAndTrack("Generating data dictionary", xsdDocumentationHtmlService::generateDataDictionaryPage);
        executeAndTrack("Generating search index", xsdDocumentationHtmlService::generateSearchIndex);

        if (parallelProcessing) {
            executeAndTrack("Generating detail pages for complex types (parallel)", xsdDocumentationHtmlService::generateComplexTypePagesInParallel);
            executeAndTrack("Generating detail pages for simple types (parallel)", xsdDocumentationHtmlService::generateSimpleTypePagesInParallel);
            executeAndTrack("Generating detail pages for elements (parallel)", xsdDocumentationHtmlService::generateDetailsPagesInParallel);
        } else {
            executeAndTrack("Generating detail pages for complex types", xsdDocumentationHtmlService::generateComplexTypePages);
            executeAndTrack("Generating detail pages for simple types", xsdDocumentationHtmlService::generateSimpleTypePages);
            executeAndTrack("Generating detail pages for elements", xsdDocumentationHtmlService::generateDetailPages);
        }

        // Generate languages.json for JavaScript-based language switching
        executeAndTrack("Generating languages.json", () -> generateLanguagesJson(outputDirectory));

        // Make sure the background Data Dictionary Excel export has finished before returning.
        executeAndTrack("Finalizing data dictionary Excel", xsdDocumentationHtmlService::awaitDataDictionaryExcel);
    }

    /**
     * Generates languages.json file containing all discovered documentation languages.
     * This file is used by the JavaScript language switcher for on-the-fly language switching.
     *
     * @param outputDirectory The output directory where languages.json will be created
     */
    private void generateLanguagesJson(File outputDirectory) {
        try {
            // Build the JSON content
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"available\": [");

            // Always include "default" first
            List<String> sortedLanguages = new ArrayList<>();
            sortedLanguages.add("default");

            // Use includedLanguages if set, otherwise use all discovered languages
            Set<String> languagesToUse = (includedLanguages != null && !includedLanguages.isEmpty())
                    ? includedLanguages
                    : discoveredLanguages;

            // Add other languages sorted alphabetically
            languagesToUse.stream()
                    .filter(Objects::nonNull)  // Filter out null values
                    .filter(lang -> !"default".equalsIgnoreCase(lang))
                    .sorted()
                    .forEach(sortedLanguages::add);

            for (int i = 0; i < sortedLanguages.size(); i++) {
                if (i > 0) {
                    json.append(", ");
                }
                json.append("\"").append(sortedLanguages.get(i)).append("\"");
            }

            json.append("],\n");
            json.append("  \"default\": \"default\",\n");

            // Add showSwitcher flag - only show language dropdown when multiple languages exist
            boolean hasMultipleLanguages = sortedLanguages.size() > 1;
            json.append("  \"showSwitcher\": ").append(hasMultipleLanguages).append(",\n");

            // Add showSvgPage flag - whether the SVG overview page was generated
            json.append("  \"showSvgPage\": ").append(generateSvgOverviewPage).append(",\n");

            // Add fallback language setting
            if (fallbackLanguage != null && !fallbackLanguage.isBlank()) {
                json.append("  \"fallback\": \"").append(fallbackLanguage).append("\"\n");
            } else {
                json.append("  \"fallback\": null\n");
            }

            json.append("}\n");

            // Write to file
            Path languagesJsonPath = outputDirectory.toPath().resolve("languages.json");
            Files.writeString(languagesJsonPath, json.toString(), StandardCharsets.UTF_8);

            logger.debug("Generated languages.json with {} languages, fallback: {}", sortedLanguages.size(), fallbackLanguage);
        } catch (IOException e) {
            logger.error("Failed to generate languages.json", e);
        }
    }

    /**
     * Whether {@link #processXsd} generates every element's sample value up front (the default: documentation,
     * data dictionary and sample XML need them all, in a reproducible order). Editor schema loads pass
     * {@code false}: they only show a few samples in completion hints, so those are generated on first read.
     */
    public void setEagerSampleData(boolean eagerSampleData) {
        this.eagerSampleData = eagerSampleData;
    }

    /**
     * Compatibility overload of {@link #processXsd(MarkdownMode)}.
     *
     * @param useMarkdownRenderer true maps to {@link MarkdownMode#ALL}, false to
     *                            {@link MarkdownMode#OFF}
     * @throws Exception if the schema cannot be processed
     */
    public void processXsd(Boolean useMarkdownRenderer) throws Exception {
        processXsd(Boolean.FALSE.equals(useMarkdownRenderer) ? MarkdownMode.OFF : MarkdownMode.ALL);
    }

    /**
     * Parses the schema and builds the documentation model.
     *
     * @param markdownMode how the Markdown renderer is applied
     * @throws Exception if the schema cannot be processed
     */
    public void processXsd(MarkdownMode markdownMode) throws Exception {
        loadSchemaDefinitions(markdownMode);

        // Start traversal from global elements
        resetExpansion();
        for (Node globalElement : xsdDocumentationData.getGlobalElements()) {
            String rootName = getAttributeValue(globalElement, "name");
            traverseNode(globalElement, "/" + rootName, null, 0, new HashSet<>());
        }

        // Build the type usage map after traversal
        buildTypeUsageMap();
        fullyProcessed = true;
        sampleExpansionKey = null;
    }

    /**
     * Loads the schema - all schema files, the global definition caches and the sample type resolver - without
     * expanding any element. Does nothing but update the Markdown mode when the schema is already loaded. The sample
     * XML methods load on demand; call this directly to get loading errors as exceptions.
     *
     * @param markdownMode how the Markdown renderer is applied
     * @throws Exception if the schema cannot be loaded
     */
    public void loadSchema(MarkdownMode markdownMode) throws Exception {
        if (schemaLoaded) {
            setMarkdownMode(markdownMode);
            return;
        }
        loadSchemaDefinitions(markdownMode);
    }

    private void loadSchemaDefinitions(MarkdownMode markdownMode) throws Exception {
        setMarkdownMode(markdownMode);
        initializeXmlTools();

        // Process all schemas (including xs:include and xs:import)
        processAllSchemas();

        // Parse the main XSD file
        this.doc = parseXsdFile(new File(xsdFilePath).toPath());

        // Initialize caches for global definitions from all processed schemas
        initializeCachesFromAllSchemas();

        // Populate XsdDocumentationData
        populateDocumentationData();

        // Configure the sample data generator with type resolver BEFORE traversal
        // so that sample data generation can resolve named types to base XML types
        configureTypeResolver();
        schemaLoaded = true;
    }

    /** Resets the per-run node metadata memos and the XPath counter before a traversal. */
    private void resetExpansion() {
        identityConstraintMemo.clear();
        resolvedTypeMemo.clear();
        sourceSnippetMemo.clear();
        documentationMemo.clear();
        counter = 0;
    }

    /**
     * Expands only one global element for sample XML generation, replacing the previous sample expansion. Unlike
     * {@link #processXsd(MarkdownMode)}, which expands every global element for the documentation, this keeps memory
     * proportional to the chosen root: in mandatory-only mode optional particles are not expanded at all (choice
     * options stay, the generators pick among all of them), and the expansion stops with a
     * {@link SampleXmlLimits.LimitExceededException} beyond {@link SampleXmlLimits#maxExpandedNodes()} nodes.
     * Does nothing after {@link #processXsd(MarkdownMode)} or when the same root and mode are already expanded.
     *
     * <p>Only for generation without profile rules: a rule targeting an optional path finds nothing in a
     * mandatory-only expansion.</p>
     *
     * @param rootElementName local name of a global element, as returned by {@link #getRootElementNames()}
     * @param mandatoryOnly   expand required particles only
     * @param markdownMode    how the Markdown renderer is applied
     * @throws IllegalArgumentException              if the schema has no global element with that name
     * @throws SampleXmlLimits.LimitExceededException if the root expands beyond the node limit
     * @throws Exception                             if the schema cannot be loaded
     */
    public void expandForSample(String rootElementName, boolean mandatoryOnly, MarkdownMode markdownMode) throws Exception {
        if (fullyProcessed) {
            return;
        }
        String key = rootElementName + '|' + mandatoryOnly + '|' + markdownMode;
        if (key.equals(sampleExpansionKey)) {
            return;
        }
        loadSchema(markdownMode);
        Node globalElement = sampleRootElements().stream()
                .filter(node -> rootElementName.equals(getAttributeValue(node, "name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No global element named '" + rootElementName + "'"));

        sampleExpansionKey = null;
        xsdDocumentationData.setExtendedXsdElementMap(null);
        resetExpansion();
        reseedSample();
        expansionNodeLimit = SampleXmlLimits.maxExpandedNodes();
        expansionRootName = rootElementName;
        pruneOptionalParticles = mandatoryOnly;
        sampleExpansion = true;
        sampleOptionalContentExpanded.clear();
        try {
            traverseNode(globalElement, "/" + rootElementName, null, 0, new HashSet<>());
        } finally {
            expansionNodeLimit = -1;
            pruneOptionalParticles = false;
            sampleExpansion = false;
            sampleOptionalContentExpanded.clear();
        }
        sampleExpansionKey = key;
    }

    /**
     * Configures the XsdSampleDataGenerator with a type resolver that resolves
     * named types to base XML types, collecting all restrictions along the way.
     */
    private void configureTypeResolver() {
        xsdSampleDataGenerator.setTypeResolver(typeName -> resolveTypeToBase(typeName, new HashSet<>()));
        // The realistic generator only sees the documentation data: hand it the same resolver (SIRI
        // ParticipantRefStructure -> ParticipantCodeType -> NMTOKEN came out empty through its element map scan)
        xsdDocumentationData.setNamedTypeResolver(typeName -> {
            XsdSampleDataGenerator.ResolvedType resolved = resolveTypeToBase(typeName, new HashSet<>());
            return resolved == null ? null
                    : new org.fxt.freexmltoolkit.domain.NamedTypeResolver.Resolution(resolved.baseType(),
                    resolved.mergedRestriction(), resolved.list());
        });
    }

    /**
     * Resolves a type name recursively to its base XML Schema type,
     * collecting all restrictions and facets along the type hierarchy.
     *
     * @param typeName The type name to resolve (e.g., "FundAmountType")
     * @param visited Set of already visited types to prevent infinite loops
     * @return ResolvedType with base type and merged restrictions, or null if not found
     */
    private XsdSampleDataGenerator.ResolvedType resolveTypeToBase(String typeName, Set<String> visited) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        // Normalize type name (remove namespace prefix for lookup)
        String localTypeName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1)
            : typeName;

        // Check for base XML types - these are the terminal cases
        if (isBaseXmlType(localTypeName)) {
            return new XsdSampleDataGenerator.ResolvedType(typeName, null);
        }

        // Reuse a previously computed resolution for this named type. A non-null cached result is
        // path-independent (the type fully resolved), so it is safe to return directly and also
        // short-circuits the recursion below.
        XsdSampleDataGenerator.ResolvedType cached = resolvedTypeMemo.get(localTypeName);
        if (cached != null) {
            return cached;
        }

        // Prevent infinite loops in circular type references
        if (visited.contains(localTypeName)) {
            logger.debug("Circular type reference detected for type: {}", typeName);
            return null;
        }
        visited.add(localTypeName);

        // Look up the type in simpleTypeMap
        Node typeNode = simpleTypeMap.get(localTypeName);
        if (typeNode != null) {
            XsdSampleDataGenerator.ResolvedType resolved = resolveSimpleType(typeNode, visited);
            if (resolved != null) {
                resolvedTypeMemo.put(localTypeName, resolved);
            }
            return resolved;
        }

        // Look up the type in complexTypeMap
        typeNode = complexTypeMap.get(localTypeName);
        if (typeNode != null) {
            XsdSampleDataGenerator.ResolvedType resolved = resolveComplexType(typeNode, visited);
            if (resolved != null) {
                resolvedTypeMemo.put(localTypeName, resolved);
            }
            return resolved;
        }

        // Type not found in schema
        logger.debug("Type '{}' not found in schema, cannot resolve", typeName);
        return null;
    }

    /**
     * Resolves a simpleType node to its base type with restrictions.
     */
    private XsdSampleDataGenerator.ResolvedType resolveSimpleType(Node typeNode, Set<String> visited) {
        // Look for restriction element
        Node restriction = getDirectChildElement(typeNode, "restriction");
        if (restriction != null) {
            String baseType = getAttributeValue(restriction, "base");
            Map<String, List<String>> facets = extractFacetsFromRestriction(restriction);

            // A restriction without a base holds its base type inline (a list of doubles with length="2")
            if (baseType == null || baseType.isBlank()) {
                Node inlineBase = getDirectChildElement(restriction, "simpleType");
                XsdSampleDataGenerator.ResolvedType inner = inlineBase == null
                        ? null : resolveSimpleType(inlineBase, visited);
                if (inner != null) {
                    Map<String, List<String>> merged = new HashMap<>();
                    if (inner.mergedRestriction() != null && inner.mergedRestriction().facets() != null) {
                        merged.putAll(inner.mergedRestriction().facets());
                    }
                    merged.putAll(facets);
                    return new XsdSampleDataGenerator.ResolvedType(inner.baseType(),
                            new XsdExtendedElement.RestrictionInfo(inner.baseType(), merged), inner.list());
                }
            }

            // Recursively resolve the base type
            XsdSampleDataGenerator.ResolvedType baseResolved = resolveTypeToBase(baseType, visited);
            if (baseResolved != null) {
                // Merge facets: current facets override inherited ones
                Map<String, List<String>> mergedFacets = new HashMap<>();
                if (baseResolved.mergedRestriction() != null && baseResolved.mergedRestriction().facets() != null) {
                    mergedFacets.putAll(baseResolved.mergedRestriction().facets());
                }
                mergedFacets.putAll(facets);

                return new XsdSampleDataGenerator.ResolvedType(
                    baseResolved.baseType(),
                    new XsdExtendedElement.RestrictionInfo(baseResolved.baseType(), mergedFacets)
                );
            }

            // Base type is a base XML type, return with current facets
            return new XsdSampleDataGenerator.ResolvedType(
                baseType,
                new XsdExtendedElement.RestrictionInfo(baseType, facets)
            );
        }

        // Look for list element
        Node listNode = getDirectChildElement(typeNode, "list");
        if (listNode != null) {
            String itemType = getAttributeValue(listNode, "itemType");
            if (itemType != null) {
                XsdSampleDataGenerator.ResolvedType itemResolved = resolveTypeToBase(itemType, visited);
                // The item type's resolution, marked as a list so a sample writes several items
                return itemResolved == null ? null : new XsdSampleDataGenerator.ResolvedType(
                        itemResolved.baseType(), itemResolved.mergedRestriction(), true);
            }
        }

        // Look for union element
        Node unionNode = getDirectChildElement(typeNode, "union");
        if (unionNode != null) {
            String memberTypes = getAttributeValue(unionNode, "memberTypes");
            if (memberTypes != null && !memberTypes.isBlank()) {
                // Use the first member type
                String firstMember = memberTypes.trim().split("\\s+")[0];
                return resolveTypeToBase(firstMember, visited);
            }
            // Member types declared inline (XBRL nonZeroDecimal): use the first one
            Node inlineMember = getDirectChildElement(unionNode, "simpleType");
            if (inlineMember != null) {
                return resolveSimpleType(inlineMember, visited);
            }
        }

        return null;
    }

    /**
     * Resolves a complexType node to its base type (for simpleContent extensions).
     */
    private XsdSampleDataGenerator.ResolvedType resolveComplexType(Node typeNode, Set<String> visited) {
        // Look for simpleContent element
        Node simpleContent = getDirectChildElement(typeNode, "simpleContent");
        if (simpleContent != null) {
            // Check for extension
            Node extension = getDirectChildElement(simpleContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                return resolveTypeToBase(baseType, visited);
            }

            // Check for restriction
            Node restriction = getDirectChildElement(simpleContent, "restriction");
            if (restriction != null) {
                String baseType = getAttributeValue(restriction, "base");
                Map<String, List<String>> facets = extractFacetsFromRestriction(restriction);

                XsdSampleDataGenerator.ResolvedType baseResolved = resolveTypeToBase(baseType, visited);
                if (baseResolved != null) {
                    Map<String, List<String>> mergedFacets = new HashMap<>();
                    if (baseResolved.mergedRestriction() != null && baseResolved.mergedRestriction().facets() != null) {
                        mergedFacets.putAll(baseResolved.mergedRestriction().facets());
                    }
                    mergedFacets.putAll(facets);

                    return new XsdSampleDataGenerator.ResolvedType(
                        baseResolved.baseType(),
                        new XsdExtendedElement.RestrictionInfo(baseResolved.baseType(), mergedFacets)
                    );
                }

                return new XsdSampleDataGenerator.ResolvedType(
                    baseType,
                    new XsdExtendedElement.RestrictionInfo(baseType, facets)
                );
            }
        }

        // Look for complexContent element
        Node complexContent = getDirectChildElement(typeNode, "complexContent");
        if (complexContent != null) {
            // Check for extension or restriction
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) {
                String baseType = getAttributeValue(extension, "base");
                return resolveTypeToBase(baseType, visited);
            }

            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) {
                String baseType = getAttributeValue(restriction, "base");
                return resolveTypeToBase(baseType, visited);
            }
        }

        // Complex type without simpleContent - cannot generate text content
        return null;
    }

    /**
     * Extracts facets from a restriction element.
     */
    private Map<String, List<String>> extractFacetsFromRestriction(Node restriction) {
        Map<String, List<String>> facets = new HashMap<>();

        for (Node child = restriction.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String localName = child.getLocalName();
            if (localName == null) {
                continue;
            }

            // These are facet elements
            switch (localName) {
                case "enumeration", "pattern" -> {
                    String value = getAttributeValue(child, "value");
                    if (value != null) {
                        facets.computeIfAbsent(localName, k -> new ArrayList<>()).add(value);
                    }
                }
                case "minLength", "maxLength", "length",
                     "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
                     "totalDigits", "fractionDigits", "whiteSpace" -> {
                    String value = getAttributeValue(child, "value");
                    if (value != null) {
                        facets.put(localName, List.of(value));
                    }
                }
                default -> { }
            }
        }

        return facets;
    }

    /**
     * Checks if the given type name is a base XML Schema type.
     */
    private boolean isBaseXmlType(String typeName) {
        if (typeName == null) {
            return false;
        }

        String localName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1)
            : typeName;

        return switch (localName.toLowerCase()) {
            case "string", "normalizedstring", "token", "language", "nmtoken", "nmtokens",
                 "name", "ncname", "id", "idref", "idrefs", "entity", "entities",
                 "integer", "nonnegativeinteger", "positiveinteger", "nonpositiveinteger", "negativeinteger",
                 "long", "int", "short", "byte",
                 "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte",
                 "decimal", "float", "double",
                 "boolean",
                 "date", "time", "datetime", "duration", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday",
                 "base64binary", "hexbinary",
                 "anyuri", "qname", "notation",
                 "anysimpletype", "anytype" -> true;
            default -> false;
        };
    }

    /**
     * Collects the main schema and every schema document it reaches through {@code xs:include} and
     * {@code xs:import}, transitively. Never touches the user's files: remote documents are resolved
     * through the Schema Library / XML catalogs first and otherwise fetched into the shared schema
     * cache (proxy-aware, SSRF-checked).
     */
    private void processAllSchemas() throws Exception {
        Path mainFile = new File(this.xsdFilePath).toPath().toAbsolutePath().normalize();
        Queue<Path> filesToProcess = new LinkedList<>();
        filesToProcess.add(mainFile);

        // Base URI per collected file: its own location, or the remote URL a cached copy stands for
        Map<Path, String> baseUris = new HashMap<>();
        baseUris.put(mainFile, mainFile.toUri().toString());
        Map<String, java.util.Optional<Path>> remoteResolutions = new HashMap<>();
        Set<Path> processedFiles = new HashSet<>();
        processedSchemaFiles.clear();

        while (!filesToProcess.isEmpty()) {
            Path currentFile = filesToProcess.poll();
            if (!Files.exists(currentFile) || !processedFiles.add(currentFile)) {
                continue;
            }
            processedSchemaFiles.add(currentFile);

            logger.debug("Processing schema file: {}", currentFile);
            Document document = parseXsdFile(currentFile);

            NodeList includeNodes = (NodeList) xpath.evaluate("//xs:include[@schemaLocation]", document, XPathConstants.NODESET);
            for (int i = 0; i < includeNodes.getLength(); i++) {
                String location = ((Element) includeNodes.item(i)).getAttribute("schemaLocation");
                // An included document has the including one's namespace: never resolve it by namespace
                Path includedFile = resolveSchemaReference(location, null, currentFile, baseUris, remoteResolutions);
                if (includedFile != null) {
                    filesToProcess.add(includedFile);
                } else if (!location.isEmpty()) {
                    logger.warn("Included file not found: {} (included from {})", location, currentFile);
                }
            }

            NodeList importNodes = (NodeList) xpath.evaluate("//xs:import[@schemaLocation]", document, XPathConstants.NODESET);
            for (int i = 0; i < importNodes.getLength(); i++) {
                Element importElement = (Element) importNodes.item(i);
                String location = importElement.getAttribute("schemaLocation");
                Path importedFile = resolveSchemaReference(location, importElement.getAttribute("namespace"),
                        currentFile, baseUris, remoteResolutions);
                if (importedFile != null) {
                    filesToProcess.add(importedFile);
                } else if (!location.isEmpty()) {
                    logger.warn("Imported file not found: {} (imported from {})", location, currentFile);
                }
            }
        }
    }

    /**
     * Resolves a {@code schemaLocation} of the schema document {@code referencingFile} to a local file.
     * A relative location is relative to the referencing document, not to the main schema. For a
     * document that stands for a remote URL, a relative location without a local counterpart is
     * resolved against that URL. Remote locations go through {@link #resolveRemoteImport}.
     *
     * @param baseUris          base URI per collected file; receives the entry of the resolved file
     * @param remoteResolutions memo of remote lookups, so an unreachable URL is only tried once
     * @return the normalized local file, or {@code null} if the location cannot be resolved
     */
    private Path resolveSchemaReference(String location, String namespace, Path referencingFile,
                                        Map<Path, String> baseUris, Map<String, java.util.Optional<Path>> remoteResolutions) {
        if (location.isEmpty()) {
            return null;
        }
        String baseUri = baseUris.get(referencingFile);
        String remoteLocation = null;
        try {
            if (isRemote(location)) {
                remoteLocation = location;
            } else {
                Path local = referencingFile.resolveSibling(location).toAbsolutePath().normalize();
                if (Files.exists(local)) {
                    baseUris.putIfAbsent(local, local.toUri().toString());
                    return local;
                }
                Path mapped = libraryFileForNamespace(namespace, baseUri);
                if (mapped != null) {
                    // Keep a copy next to the referencing schema under the declared name.
                    mapped = LocalSchemaCopies.localize(referencingFile.toAbsolutePath().getParent(), location, mapped)
                            .toAbsolutePath().normalize();
                    baseUris.putIfAbsent(mapped, mapped.toUri().toString());
                    return mapped;
                }
                if (isRemote(baseUri)) {
                    remoteLocation = URI.create(baseUri).resolve(location).toString();
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Invalid schema location '{}' in {}: {}", location, referencingFile, e.getMessage());
            return null;
        }
        if (remoteLocation == null) {
            return null;
        }
        String target = remoteLocation;
        // The local copy of a downloaded schema, next to the referencing one (LocalSchemaCopies).
        // (never inside the application cache: LocalSchemaCopies refuses that folder)
        Path copyDir = referencingFile.toAbsolutePath().getParent();
        Path existingCopy = LocalSchemaCopies.existing(copyDir, location, namespace).orElse(null);
        if (existingCopy != null) {
            existingCopy = existingCopy.toAbsolutePath().normalize();
            baseUris.putIfAbsent(existingCopy, target);
            return existingCopy;
        }
        java.util.Optional<Path> resolved = remoteResolutions.computeIfAbsent(target, url -> {
            try {
                return java.util.Optional.ofNullable(resolveRemoteImport(namespace, url, baseUri))
                        .map(path -> path.toAbsolutePath().normalize());
            } catch (Exception e) {
                logger.error("Failed to resolve remote schema: {}", url, e);
                return java.util.Optional.empty();
            }
        });
        if (resolved.isEmpty()) {
            logger.warn("Remote schema could not be resolved (offline or unreachable): {}", target);
            return null;
        }
        Path file = LocalSchemaCopies.localize(copyDir, location, resolved.get()).toAbsolutePath().normalize();
        baseUris.putIfAbsent(file, target);
        return file;
    }

    /**
     * Initialize caches for global definitions from all processed schema files.
     */
    /**
     * Initializes the global-definition caches from every schema file that
     * {@link #processAllSchemas()} collected — main schema, includes, local imports and
     * remote imports resolved through the Schema Library / catalogs / schema cache. Re-walking
     * the imports from disk here would silently skip the resolved remote ones (their DOM was
     * only rewritten in memory), leaving e.g. imported complexTypes unresolved.
     */
    private void initializeCachesFromAllSchemas() throws Exception {
        includedGlobalElements.clear();
        referencedElementNames.clear();
        substitutionMembers.clear();
        derivedComplexTypes.clear();
        samplePrefixes.clear();
        Path main = new File(this.xsdFilePath).toPath().toAbsolutePath().normalize();
        String mainNamespace = doc != null ? doc.getDocumentElement().getAttribute("targetNamespace") : "";
        for (Path currentFile : schemaFilesToScan()) {
            logger.debug("Initializing caches from: {}", currentFile);
            Document document = parseXsdFile(currentFile);
            initializeCaches(document);
            collectSampleRootCandidates(document, currentFile.equals(main), mainNamespace);
        }
    }

    /**
     * Records every element name referenced via {@code ref}, and the global elements of an included document of the
     * main target namespace as sample root candidates. A document without a target namespace counts as a chameleon
     * include of the main namespace.
     */
    private void collectSampleRootCandidates(Document document, boolean mainDocument, String mainNamespace) throws Exception {
        NodeList refs = (NodeList) xpath.evaluate("//xs:element/@ref", document, XPathConstants.NODESET);
        for (int i = 0; i < refs.getLength(); i++) {
            referencedElementNames.add(stripNamespace(refs.item(i).getNodeValue()));
        }
        String namespace = document.getDocumentElement().getAttribute("targetNamespace");
        if (mainDocument || !(namespace.isEmpty() || namespace.equals(mainNamespace))) {
            return;
        }
        includedGlobalElements.addAll(nodeListToList(
                (NodeList) xpath.evaluate("/xs:schema/xs:element[@name]", document, XPathConstants.NODESET)));
    }

    /**
     * Global elements offered as sample roots: those of the main document, then those of included documents of the
     * same namespace; each name once, in document order.
     */
    private List<Node> sampleRootElements() {
        Map<String, Node> roots = new LinkedHashMap<>();
        for (Node node : xsdDocumentationData.getGlobalElements()) {
            roots.putIfAbsent(getAttributeValue(node, "name"), node);
        }
        for (Node node : includedGlobalElements) {
            roots.putIfAbsent(getAttributeValue(node, "name"), node);
        }
        return new ArrayList<>(roots.values());
    }

    /** The schema files collected by {@link #processAllSchemas()}, falling back to the main schema. */
    private List<Path> schemaFilesToScan() {
        List<Path> files = new ArrayList<>();
        Path main = new File(this.xsdFilePath).toPath().toAbsolutePath().normalize();
        if (Files.exists(main)) {
            files.add(main);
        }
        for (Path p : processedSchemaFiles) {
            if (!files.contains(p) && Files.exists(p)) {
                files.add(p);
            }
        }
        return files;
    }

    /**
     * Whether a particle can be left out of a mandatory-only sample expansion: an element (or element reference),
     * group reference, sequence, choice or all with {@code minOccurs="0"}, matching
     * {@link XsdExtendedElement#isMandatory()}. Choice options are kept because the generators select among all options.
     */
    private boolean isOptionalParticle(Node node) {
        String localName = node.getLocalName();
        if (!"element".equals(localName) && !"group".equals(localName) && !"sequence".equals(localName)
                && !"choice".equals(localName) && !"all".equals(localName) && !"any".equals(localName)) {
            return false;
        }
        Node parent = node.getParentNode();
        if (parent != null && "choice".equals(parent.getLocalName())) {
            return false;
        }
        String minOccurs = getAttributeValue(node, "minOccurs");
        if (minOccurs == null) {
            return false;
        }
        try {
            return Integer.parseInt(minOccurs.trim()) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void traverseNode(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE || level > MAX_ALLOWED_DEPTH) {
            return;
        }
        if (expansionNodeLimit >= 0 && xsdDocumentationData.getExtendedXsdElementMap().size() > expansionNodeLimit) {
            throw SampleXmlLimits.nodeLimitExceeded(expansionRootName, expansionNodeLimit);
        }
        if (pruneOptionalParticles && level > 0 && isOptionalParticle(node)) {
            return;
        }
        // A type reached again through a different element declaration repeats its compositor, which is no recursion:
        // every cycle passes through an element declaration or a group. Samples need that content (UCI
        // StoreLoadoutItemType holds StoreList of its own abstract base); the documentation keeps the stricter guard.
        boolean trackRecursion = !sampleExpansion || !isCompositor(node);
        if (trackRecursion && visitedOnPath.contains(node)) {
            logger.info("Recursion detected at node '{}' on path '{}'. Aborting this branch.", getAttributeValue(node, "name"), currentXPath);
            markIncompleteUnlessOptional(particleOf(node), parentXPath);
            return;
        }
        if (trackRecursion) {
            visitedOnPath.add(node);
        }

        try {
            // Handle references (ref="...")
            String ref = getAttributeValue(node, "ref");
            if (ref != null && !ref.isEmpty()) {
                // Handle elements from external namespaces (e.g., ds:Signature from XML Digital Signature)
                // These are included in documentation but marked as external references. Prefixed
                // attribute, attributeGroup and group references are resolved below, never emitted as elements.
                if ("element".equals(node.getLocalName()) && ref.contains(":") && isExternalNamespaceReference(ref)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Processing external namespace reference for documentation: {}", ref);
                    }
                    processExternalNamespaceReference(node, ref, currentXPath, parentXPath, level, visitedOnPath);
                    return;
                }

                Node referencedNode = findReferencedNode(node.getLocalName(), ref, node);
                if (referencedNode != null) {
                    boolean elementRef = "element".equals(node.getLocalName());
                    Node emitted = elementRef ? sampleSubstitute(referencedNode, visitedOnPath) : referencedNode;
                    if (elementRef && isUnsubstitutedAbstract(emitted)) {
                        if (mayBeLeftOut(node)) {
                            return; // no instance may contain it; the content model does not need it here
                        }
                        markIncompleteContent(parentXPath);
                    }
                    String emittedNamespace = instanceNamespace(emitted);
                    boolean switchNamespace = emitted != referencedNode
                            && !emittedNamespace.equals(instanceNamespace(referencedNode));
                    String outerPrefix = forcedNamespacePrefixThreadLocal.get();
                    String outerUri = forcedNamespaceUriThreadLocal.get();
                    if (switchNamespace) {
                        forceNamespace(emittedNamespace, emitted);
                    }
                    // Store the reference node to preserve cardinality attributes (minOccurs/maxOccurs)
                    referenceNodeThreadLocal.set(node);
                    try {
                        traverseNode(emitted, currentXPath, parentXPath, level, visitedOnPath);
                    } finally {
                        referenceNodeThreadLocal.remove();
                        if (switchNamespace) {
                            restoreForcedNamespace(outerPrefix, outerUri);
                        }
                    }
                } else {
                    logger.warn("Reference '{}' for node '{}' could not be resolved.", ref, node.getLocalName());
                }
                return; // Stop processing the current ref-node
            }

            String localName = node.getLocalName();
            if (sampleExpansion && !pruneOptionalParticles && "element".equals(localName)
                    && !sampleOptionalContentExpanded.add(node)) {
                // A sample expands the optional content of each declaration once; later occurrences get their
                // required content only. Grammars whose optional content recurses through many different
                // elements (JATS inline elements, SIRI AffectedLine/AffectedStopPoint) stay linear in size.
                pruneOptionalParticles = true;
                try {
                    processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
                } finally {
                    pruneOptionalParticles = false;
                }
            } else if ("element".equals(localName) || "attribute".equals(localName)) {
                processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
            } else if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                // Create explicit sequence/choice/all nodes for SVG visualization
                processElementOrAttribute(node, currentXPath, parentXPath, level, visitedOnPath);
            } else if ("group".equals(localName)) {
                // For groups, just traverse their children (groups are references)
                for (Node child : getDirectChildElements(node)) {
                    String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                    String childXPath = "element".equals(child.getLocalName())
                            ? uniqueChildXPath(currentXPath + "/" + childName) : currentXPath;
                    traverseNode(child, childXPath, currentXPath, level, visitedOnPath);
                }
            }
        } finally {
            if (trackRecursion) {
                visitedOnPath.remove(node);
            }
        }
    }

    /**
     * Records an element wildcard ({@code xs:any}) of a sample expansion at its position in the content model, so
     * both generators can emit an element for it (xmldsig {@code SignatureProperty}, XBRL {@code segment}, UBL
     * {@code ExtensionContent}). Only wildcards a sample element satisfies without a declaration ({@code lax},
     * {@code skip}) are recorded; a required strict one leaves its parent's content incomplete. The documentation
     * model is left as it is.
     */
    private void addWildcardPlaceholder(Node wildcard, String parentXPath, int level) {
        if (!sampleExpansion || (pruneOptionalParticles && isOptionalParticle(wildcard))) {
            return;
        }
        Node strictElement = wildcardSampleNamespace(wildcard) == null ? strictWildcardElement(wildcard) : null;
        if (wildcardSampleNamespace(wildcard) == null && strictElement == null) {
            markIncompleteUnlessOptional(wildcard, parentXPath);
            return;
        }
        String xpath = parentXPath + "/ANY_" + counter;
        XsdExtendedElement placeholder = new XsdExtendedElement();
        placeholder.setElementName("ANY");
        placeholder.setElementType(WILDCARD_TYPE);
        placeholder.setCurrentXpath(xpath);
        placeholder.setParentXpath(parentXPath);
        placeholder.setLevel(level);
        placeholder.setCounter(counter++);
        placeholder.setCurrentNode(wildcard);
        if (strictElement != null) {
            // A strict wildcard needs a declaration: the sample writes this global element
            placeholder.setReferencedTypeName(getAttributeValue(strictElement, "name"));
            placeholder.setSourceNamespace(instanceNamespace(strictElement));
        }
        if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
            xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).addChild(xpath);
        }
        xsdDocumentationData.putExtendedXsdElement(xpath, placeholder);
    }

    /**
     * A global element a {@code processContents="strict"} wildcard may hold: one the namespace constraint allows,
     * concrete, and of a built-in simple type, so an empty element is a valid instance of it. {@code null} when the
     * schema declares none (datajud requires {@code ##other} without importing a schema of another namespace).
     */
    private Node strictWildcardElement(Node wildcard) {
        if (!(wildcard instanceof Element any)) {
            return null;
        }
        String constraint = any.hasAttribute("namespace") ? any.getAttribute("namespace").trim() : "##any";
        String ownNamespace = any.getOwnerDocument().getDocumentElement().getAttribute("targetNamespace");
        List<Node> candidates = new ArrayList<>(xsdDocumentationData.getGlobalElements());
        candidates.addAll(includedGlobalElements);
        for (Node candidate : candidates) {
            String namespace = instanceNamespace(candidate);
            String type = getAttributeValue(candidate, "type");
            if (isAbstractDeclaration(candidate) || type == null || !isBaseXmlType(type)) {
                continue;
            }
            if (allowsNamespace(constraint, namespace, ownNamespace)) {
                return candidate;
            }
        }
        return null;
    }

    /** Whether a wildcard's namespace constraint allows an element of {@code namespace}. */
    private static boolean allowsNamespace(String constraint, String namespace, String ownNamespace) {
        for (String token : constraint.split("\\s+")) {
            boolean allowed = switch (token) {
                case "##any" -> true;
                case "##other" -> !namespace.equals(ownNamespace);
                case "##local" -> namespace.isEmpty();
                case "##targetNamespace" -> namespace.equals(ownNamespace);
                default -> token.equals(namespace);
            };
            if (allowed) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a sample expansion reached an abstract element that nothing substitutes: KML declares
     * {@code ObjectSimpleExtensionGroup} ({@code anySimpleType}) without any member, and an instance may not contain
     * it.
     */
    private boolean isUnsubstitutedAbstract(Node declaration) {
        return sampleExpansion && isAbstractDeclaration(declaration);
    }

    /** Whether a particle may be absent: {@code minOccurs="0"}, or one option of a choice with others. */
    private boolean mayBeLeftOut(Node particle) {
        String minOccurs = getAttributeValue(particle, "minOccurs");
        if (minOccurs != null && "0".equals(minOccurs.trim())) {
            return true;
        }
        Node parent = particle.getParentNode();
        return parent != null && "choice".equals(parent.getLocalName()) && getDirectChildElements(parent).stream()
                .filter(n -> !"annotation".equals(n.getLocalName()))
                .count() > 1;
    }

    /**
     * Records that a sample expansion could not expand a required particle below {@code parentXPath}: recursion through
     * the same declaration or group was cut, or a strict wildcard needs a declaration. Nothing is recorded for a
     * particle that may be left out.
     */
    private void markIncompleteUnlessOptional(Node particle, String parentXPath) {
        if (!mayBeLeftOut(particle)) {
            markIncompleteContent(parentXPath);
        }
    }

    /**
     * Marks the content of a sample expansion entry incomplete (see {@link XsdSampleXmlSupport#hasCompleteContent}). A choice is never
     * marked: it stays complete while one of its other options is.
     */
    private void markIncompleteContent(String parentXPath) {
        XsdExtendedElement parent = sampleExpansion && parentXPath != null
                ? xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath) : null;
        if (parent != null && !(parent.getElementName() != null && parent.getElementName().startsWith("CHOICE"))) {
            parent.markSampleContentIncomplete();
        }
    }

    /** The particle through which a declaration is expanded: the reference that reached it, or the declaration. */
    private static Node particleOf(Node declaration) {
        if (referenceNodeThreadLocal.get() instanceof Element reference && declaration instanceof Element declared
                && reference.getLocalName().equals(declared.getLocalName())) {
            String ref = reference.getAttribute("ref");
            if (ref.substring(ref.indexOf(':') + 1).equals(declared.getAttribute("name"))) {
                return reference;
            }
        }
        return declaration;
    }

    /**
     * The element map key of an element particle: {@code candidate}, or {@code candidate[n]} when an earlier particle
     * with the same name under the same parent already holds it. JATS {@code ruby-model} ({@code rp, rt, rp}) and an
     * extension that restates a base element have two particles of one name; with one key, the second replaced the
     * first.
     */
    private String uniqueChildXPath(String candidate) {
        Map<String, XsdExtendedElement> map = xsdDocumentationData.getExtendedXsdElementMap();
        if (!map.containsKey(candidate)) {
            return candidate;
        }
        int position = 2;
        while (map.containsKey(candidate + "[" + position + "]")) {
            position++;
        }
        return candidate + "[" + position + "]";
    }

    private static boolean isCompositor(Node node) {
        String localName = node.getLocalName();
        return "sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName);
    }

    /**
     * Makes the elements expanded next take {@code namespace}: no prefix for the sample's default namespace, else the
     * {@link #instancePrefix} for it.
     */
    private void forceNamespace(String namespace, Node declaration) {
        String prefix = instancePrefix(namespace, declaration);
        if (prefix == null) {
            forcedNamespacePrefixThreadLocal.remove();
            forcedNamespaceUriThreadLocal.remove();
        } else {
            forcedNamespacePrefixThreadLocal.set(prefix);
            forcedNamespaceUriThreadLocal.set(namespace);
        }
    }

    private void restoreForcedNamespace(String prefix, String uri) {
        if (prefix == null) {
            forcedNamespacePrefixThreadLocal.remove();
        } else {
            forcedNamespacePrefixThreadLocal.set(prefix);
        }
        if (uri == null) {
            forcedNamespaceUriThreadLocal.remove();
        } else {
            forcedNamespaceUriThreadLocal.set(uri);
        }
    }

    /**
     * Processes an external namespace reference (e.g., ds:Signature from xs:import).
     * Creates an XsdExtendedElement to represent the external reference in documentation.
     * The element will be marked as an external reference and included in the documentation
     * without sample data generation.
     *
     * @param node       The reference node (xs:element with ref="...")
     * @param ref        The reference value (e.g., "ds:Signature")
     * @param currentXPath The current XPath position
     * @param parentXPath The parent XPath
     * @param level      The nesting level
     * @param visitedOnPath Set of visited nodes to avoid recursion loops
     */
    private void processExternalNamespaceReference(Node node, String ref, String currentXPath, String parentXPath,
                                                   int level, Set<Node> visitedOnPath) {
        // Extract element name from the reference (e.g., "ds:Signature" -> "Signature")
        String elementName = ref.contains(":") ? ref.substring(ref.lastIndexOf(":") + 1) : ref;
        String prefix = ref.contains(":") ? ref.substring(0, ref.indexOf(":")) : "";

        // Update XPath for this element
        String elementXPath = currentXPath + "/" + elementName;

        String namespaceUri = getNamespaceUriForReference(ref);

        // Try to get documentation from the imported schema if available
        Node referencedNode = findReferencedNode("element", ref, node);
        if (referencedNode != null) {
            // Prefer building the full subtree from the referenced element (or, for an abstract one in a sample, from
            // a concrete substitution group member, which may belong to yet another namespace)
            Node emitted = sampleSubstitute(referencedNode, visitedOnPath);
            if (isUnsubstitutedAbstract(emitted)) {
                if (mayBeLeftOut(node)) {
                    return; // no instance may contain it; the content model does not need it here
                }
                markIncompleteContent(parentXPath);
            }
            referenceNodeThreadLocal.set(node);
            if (emitted != referencedNode && !instanceNamespace(emitted).equals(instanceNamespace(referencedNode))) {
                forceNamespace(instanceNamespace(emitted), emitted);
            } else {
                forcedNamespacePrefixThreadLocal.set(prefix);
                forcedNamespaceUriThreadLocal.set(namespaceUri);
            }
            try {
                traverseNode(emitted, elementXPath, parentXPath, level, visitedOnPath);
            } finally {
                referenceNodeThreadLocal.remove();
                forcedNamespacePrefixThreadLocal.remove();
                forcedNamespaceUriThreadLocal.remove();
            }

            XsdExtendedElement created = xsdDocumentationData.getExtendedXsdElementMap().get(elementXPath);
            if (created != null) {
                created.setExternalNamespaceReference(true);
                created.setExternalNamespaceUri(namespaceUri);
                if (created.getSourceNamespacePrefix() == null || created.getSourceNamespacePrefix().isBlank()) {
                    created.setSourceNamespacePrefix(prefix);
                }
                if (created.getSourceNamespace() == null || created.getSourceNamespace().isBlank()) {
                    created.setSourceNamespace(namespaceUri);
                }
            }
            return;
        }

        XsdExtendedElement extendedElem = new XsdExtendedElement();
        extendedElem.setUseMarkdownRenderer(defaultMarkdownRendering());
        extendedElem.setCurrentNode(node);
        extendedElem.setCounter(counter++);
        extendedElem.setLevel(level);
        extendedElem.setCurrentXpath(elementXPath);
        extendedElem.setParentXpath(parentXPath);
        extendedElem.setElementName(elementName);

        // Mark as external namespace reference
        extendedElem.setExternalNamespaceReference(true);
        extendedElem.setExternalNamespaceUri(namespaceUri);
        extendedElem.setSourceNamespacePrefix(prefix);
        extendedElem.setSourceNamespace(namespaceUri);

        // Set type information
        extendedElem.setElementType("External Reference (" + ref + ")");

        // Add a note that this is an imported element
        extendedElem.addDocumentation(new XsdExtendedElement.DocumentationInfo("default",
                "Element imported from namespace: " + (namespaceUri != null ? namespaceUri : ref)));
        // Store source code from the reference node itself
        extendedElem.setSourceCode(sourceSnippet(node));

        // Add to parent's children list
        if (parentXPath != null && xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
            xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).addChild(elementXPath);
        }

        // Store in the element map
        xsdDocumentationData.putExtendedXsdElement(elementXPath, extendedElem);
        logger.debug("Added external namespace reference: {} (namespace: {})", ref, namespaceUri);
    }

    private void processElementOrAttribute(Node node, String currentXPath, String parentXPath, int level, Set<Node> visitedOnPath) {
        XsdExtendedElement extendedElem = new XsdExtendedElement();
        extendedElem.setUseMarkdownRenderer(defaultMarkdownRendering());
        extendedElem.setCurrentNode(node);

        // Check if this element is from a reference and set the cardinality node
        Node refNode = referenceNodeThreadLocal.get();
        if (refNode != null) {
            extendedElem.setCardinalityNode(refNode);
            // The reference's minOccurs/maxOccurs belong to the referenced declaration, not to its content
            referenceNodeThreadLocal.remove();
        }

        extendedElem.setCounter(counter++);
        extendedElem.setLevel(level);
        extendedElem.setCurrentXpath(currentXPath);
        extendedElem.setParentXpath(parentXPath);

        String localName = node.getLocalName();
        boolean isAttribute = "attribute".equals(localName);
        boolean isContainer = "sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName);

        String name;
        if (isContainer) {
            name = localName.toUpperCase();
        } else {
            name = getAttributeValue(node, "name");
        }
        extendedElem.setElementName(isAttribute ? "@" + name : name);
        if (!isContainer) {
            String emitNamespace = declarationNamespace(node, isAttribute);
            extendedElem.setEmission(emitNamespace, emissionPrefix(emitNamespace, node, isAttribute));
        }

        // NEW: Read the origin of the namespace from the attribute set by the Flattener.
        String sourceNamespace = getAttributeValue(node, "fxt:sourceNamespace");
        if (sourceNamespace != null && !sourceNamespace.isBlank()) {
            extendedElem.setSourceNamespace(sourceNamespace);
            // Find the corresponding prefix for display.
            xsdDocumentationData.getNamespaces().entrySet().stream()
                    .filter(entry -> sourceNamespace.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(extendedElem::setSourceNamespacePrefix);
        } else {
            // Fallback for imported schemas when traversing an external reference subtree
            String forcedNamespaceUri = forcedNamespaceUriThreadLocal.get();
            if (forcedNamespaceUri != null && !forcedNamespaceUri.isBlank()) {
                extendedElem.setSourceNamespace(forcedNamespaceUri);
                String forcedPrefix = forcedNamespacePrefixThreadLocal.get();
                if (forcedPrefix != null && !forcedPrefix.isBlank()) {
                    extendedElem.setSourceNamespacePrefix(forcedPrefix);
                }
            }
        }

        // Add to parent's children list (only if not already added)
        if (parentXPath != null && xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
            xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).addChild(currentXPath);
        }
        xsdDocumentationData.putExtendedXsdElement(currentXPath, extendedElem);

        // --- Type, Documentation, and Content Processing ---
        String typeName = getAttributeValue(node, "type");
        Node typeDefinitionNode = findTypeDefinition(node, typeName);

        // A sample cannot use an abstract type: expand a concrete derived type and emit it as xsi:type
        if (sampleExpansion && !isAttribute && !isContainer && typeDefinitionNode != null
                && "complexType".equals(typeDefinitionNode.getLocalName())
                && isAbstractDeclaration(typeDefinitionNode)) {
            Node concreteType = concreteDerivedType(typeDefinitionNode);
            if (concreteType != null) {
                String typeNamespace = instanceNamespace(concreteType);
                String prefix = instancePrefix(typeNamespace, concreteType);
                String localTypeName = getAttributeValue(concreteType, "name");
                typeName = prefix == null ? localTypeName : prefix + ":" + localTypeName;
                extendedElem.setXsiType(typeName, typeNamespace.isEmpty() ? null : typeNamespace, prefix);
                typeDefinitionNode = concreteType;
            }
        }

        if (isContainer) {
            extendedElem.setElementType("(container)");
        } else {
            if (typeName != null) {
                // Named type reference - but also check if it's a complexType with simpleContent extension
                // to expose the base type (e.g., FXRateType extends xs:decimal)
                String resolvedType = typeName;
                if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
                    Node simpleContent = getDirectChildElement(typeDefinitionNode, "simpleContent");
                    if (simpleContent != null) {
                        Node extension = getDirectChildElement(simpleContent, "extension");
                        if (extension != null) {
                            String baseType = getAttributeValue(extension, "base");
                            if (baseType != null && !baseType.isEmpty()) {
                                // Use the base type (e.g., xs:decimal) for sample data generation
                                // since the generator needs the primitive type, not the named type
                                resolvedType = simpleContentBaseType(baseType, extension);
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Resolved named type '{}' to base type '{}' via simpleContent extension",
                                            typeName, baseType);
                                }
                            }
                        }
                    }
                }
                extendedElem.setElementType(resolvedType);
            } else if (typeDefinitionNode != null) {
                // Inline type definition - check if it's a simple type with restriction
                Node restrictionNode = findRestriction(node, typeDefinitionNode);
                if (restrictionNode != null) {
                    String baseType = getAttributeValue(restrictionNode, "base");
                    if (baseType != null && !baseType.isEmpty()) {
                        extendedElem.setElementType(baseType);
                    } else {
                        extendedElem.setElementType("(anonymous)");
                    }
                } else {
                    // Check for simpleContent with extension (e.g., xs:simpleContent > xs:extension base="xs:float")
                    Node simpleContent = getDirectChildElement(typeDefinitionNode, "simpleContent");
                    if (simpleContent != null) {
                        Node extension = getDirectChildElement(simpleContent, "extension");
                        if (extension != null) {
                            String baseType = getAttributeValue(extension, "base");
                            if (baseType != null && !baseType.isEmpty()) {
                                extendedElem.setElementType(simpleContentBaseType(baseType, extension));
                            } else {
                                extendedElem.setElementType("(anonymous)");
                            }
                        } else {
                            extendedElem.setElementType("(anonymous)");
                        }
                    } else {
                        extendedElem.setElementType("(anonymous)");
                    }
                }
            }
        }

        // Process annotations from the element itself and its type definition. The element's own
        // @markdown wins; the type's annotation only supplies the fallback.
        Boolean ownMarkdown = componentDetails.processAnnotations(getDirectChildElement(node, "annotation"), extendedElem);
        Boolean typeMarkdown = typeDefinitionNode != null
                ? componentDetails.processAnnotations(getDirectChildElement(typeDefinitionNode, "annotation"), extendedElem)
                : null;
        extendedElem.setUseMarkdownRenderer(
                resolveMarkdownRendering(ownMarkdown != null ? ownMarkdown : typeMarkdown));

        // Process list and union types (for simpleType definitions)
        if (typeDefinitionNode != null && "simpleType".equals(typeDefinitionNode.getLocalName())) {
            componentDetails.processListAndUnionTypes(typeDefinitionNode, extendedElem);
        }

        // Process content (children for elements, restrictions for attributes/simple types)
        if (!isAttribute) {
            if (isContainer) {
                // For sequence/choice/all containers, process direct children
                for (Node child : getDirectChildElements(node)) {
                    String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                    String childLocalName = child.getLocalName();
                    String childXPath;

                    if ("element".equals(childLocalName)) {
                        childXPath = uniqueChildXPath(currentXPath + "/" + childName);
                        traverseNode(child, childXPath, currentXPath, level + 1, visitedOnPath);
                    } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                        // Nested containers
                        String containerName = childLocalName.toUpperCase();
                        childXPath = currentXPath + "/" + containerName + "_" + counter;
                        traverseNode(child, childXPath, currentXPath, level + 1, visitedOnPath);
                    } else if ("group".equals(childLocalName)) {
                        processGroupReference(child, currentXPath, level + 1, visitedOnPath);
                    } else if ("any".equals(childLocalName)) {
                        addWildcardPlaceholder(child, currentXPath, level + 1);
                    }
                }
            } else {
                Node contentModel = findContentModel(node, typeDefinitionNode);
                if (contentModel != null) {
                    // Handle complex content (sequence, choice, extension, etc.)
                    processComplexContent(contentModel, currentXPath, level, visitedOnPath);
                }

                // Additionally process attributes that are defined directly on the complexType
                // (i.e., siblings of <sequence>/<choice>), which are not inside the content model.
                Node attributeContext = (typeDefinitionNode != null) ? typeDefinitionNode : node;
                processAttributes(attributeContext, currentXPath, level, visitedOnPath);
            }
        }

        // Process restrictions
        Node restrictionNode = findRestriction(node, typeDefinitionNode);
        if (restrictionNode != null) {
            extendedElem.setRestrictionInfo(parseRestriction(restrictionNode));
            if (extendedElem.getElementType() == null || extendedElem.getElementType().isEmpty()) {
                extendedElem.setElementType(getAttributeValue(restrictionNode, "base"));
            }
            // Process assertions on simpleType restrictions (xs:assertion)
            componentDetails.processAssertions(restrictionNode, extendedElem);
        }

        // Process XSD 1.0 identity constraints (key, keyref, unique) on elements.
        // Identity constraints are XPath-independent (they describe the element's own
        // key/keyref/unique definitions), so memoize per source node across expansions.
        if (!isAttribute && !isContainer) {
            XsdExtendedElement donor = identityConstraintMemo.get(node);
            if (donor != null) {
                componentDetails.copyIdentityConstraints(donor, extendedElem);
            } else {
                componentDetails.processIdentityConstraints(node, extendedElem);
                identityConstraintMemo.put(node, extendedElem);
            }
        }

        // Process XSD 1.1 type alternatives on elements
        if (!isAttribute && !isContainer) {
            componentDetails.processTypeAlternatives(node, extendedElem);
        }

        // Process XSD 1.1 assertions on complexType (xs:assert)
        if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
            componentDetails.processAssertions(typeDefinitionNode, extendedElem);
        }

        // Process XSD 1.1 open content on complexType (xs:openContent)
        if (typeDefinitionNode != null && "complexType".equals(typeDefinitionNode.getLocalName())) {
            componentDetails.processOpenContent(typeDefinitionNode, extendedElem);
        }

        // Process wildcards (xs:any, xs:anyAttribute)
        // Check in the element node itself
        componentDetails.processWildcards(node, extendedElem);
        // Check in the type definition (complexType)
        if (typeDefinitionNode != null) {
            componentDetails.processWildcards(typeDefinitionNode, extendedElem);
        }
        // Check in content model
        Node contentModel = findContentModel(node, typeDefinitionNode);
        if (contentModel != null) {
            componentDetails.processWildcards(contentModel, extendedElem);
        }

        // Prefer fixed/default values where present (especially for attributes); a reference may set its own
        String fixedValue = refNode != null && getAttributeValue(refNode, "fixed") != null
                ? getAttributeValue(refNode, "fixed") : getAttributeValue(node, "fixed");
        String defaultValue = refNode != null && getAttributeValue(refNode, "default") != null
                ? getAttributeValue(refNode, "default") : getAttributeValue(node, "default");

        // Final steps - generate separate source code snippets
        setSourceCodeSnippets(extendedElem, node, typeName, typeDefinitionNode);

        if (fixedValue != null) {
            extendedElem.setSampleData(fixedValue);
        } else if (defaultValue != null) {
            extendedElem.setSampleData(defaultValue);
        } else if (eagerSampleData) {
            extendedElem.setSampleData(xsdSampleDataGenerator.generate(extendedElem));
        } else {
            extendedElem.setSampleDataSupplier(() -> lazySample(extendedElem));
        }
    }

    /** First-read sample of a lazily loaded schema; the generator is not thread-safe, hence the lock. */
    private String lazySample(XsdExtendedElement element) {
        synchronized (xsdSampleDataGenerator) {
            try {
                return xsdSampleDataGenerator.generate(element);
            } catch (RuntimeException | StackOverflowError e) {
                logger.debug("No sample for {}: {}", element.getElementName(), e.toString());
                return "";
            }
        }
    }

    /**
     * Processes attributes that are declared directly under a complexType (outside of sequence/choice/all).
     */
    private void processAttributes(Node contextNode, String parentXPath, int level, Set<Node> visitedOnPath) {
        if (contextNode == null) {
            return;
        }
        processAttributeUses(contextNode, parentXPath, level, visitedOnPath, new HashSet<>());
    }

    /**
     * Adds the {@code attribute} children of {@code holder} and the attributes of its {@code attributeGroup}
     * references (nested groups included) to {@code parentXPath}. Names in {@code declaredNames} are skipped and every
     * name seen is recorded there, so a derived declaration hides the inherited one; a prohibited attribute is left out.
     */
    private void processAttributeUses(Node holder, String parentXPath, int level, Set<Node> visitedOnPath,
                                      Set<String> declaredNames) {
        for (Node child : getDirectChildElements(holder)) {
            String localName = child.getLocalName();
            if ("attributeGroup".equals(localName)) {
                processAttributeGroupReference(child, parentXPath, level, visitedOnPath, declaredNames);
                continue;
            }
            if (!"attribute".equals(localName)) {
                continue;
            }

            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            if (childName == null || childName.isBlank() || !declaredNames.add(stripNamespace(childName))
                    || "prohibited".equals(getAttributeValue(child, "use"))) {
                continue;
            }

            String childXPath = parentXPath + "/@" + childName;

            // Ensure attributes are added as children to the parent element (deduped)
            if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
                xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).addChild(childXPath);
            }

            traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
        }
    }

    private void processAttributeGroupReference(Node groupRef, String parentXPath, int level, Set<Node> visitedOnPath,
                                                Set<String> declaredNames) {
        String ref = getAttributeValue(groupRef, "ref");
        if (ref == null || ref.isBlank()) {
            return;
        }
        Node group = findReferencedNode("attributeGroup", ref, groupRef);
        if (group == null) {
            logger.warn("Reference '{}' for node 'attributeGroup' could not be resolved.", ref);
            return;
        }
        if (!visitedOnPath.add(group)) {
            return; // an attribute group that contains itself
        }
        try {
            processAttributeUses(group, parentXPath, level, visitedOnPath, declaredNames);
        } finally {
            visitedOnPath.remove(group);
        }
    }

    /**
     * Adds the attributes a complexContent or simpleContent restriction inherits: those of every type in its base chain
     * that are not declared (restated or prohibited) closer to the restriction.
     */
    private void processRestrictionBaseAttributes(Node restriction, String parentXPath, int level,
                                                  Set<Node> visitedOnPath, Set<String> declaredNames) {
        Set<Node> chain = Collections.newSetFromMap(new IdentityHashMap<>());
        Node type = findGlobalType(getAttributeValue(restriction, "base"), restriction);
        while (type != null && chain.add(type)) {
            processAttributeUses(type, parentXPath, level, visitedOnPath, declaredNames);
            Node derivation = findContentModel(type, null);
            if (derivation == null || !("extension".equals(derivation.getLocalName())
                    || "restriction".equals(derivation.getLocalName()))) {
                return;
            }
            processAttributeUses(derivation, parentXPath, level, visitedOnPath, declaredNames);
            type = findGlobalType(getAttributeValue(derivation, "base"), derivation);
        }
    }

    /**
     * Expands a model group reference ({@code <xs:group ref="…"/>}) in place: the particles of the referenced
     * group's compositor become children of {@code parentXPath}, whether the reference is a type's whole content
     * model, a particle of a compositor or part of an extension.
     */
    private void processGroupReference(Node groupRef, String parentXPath, int level, Set<Node> visitedOnPath) {
        String ref = getAttributeValue(groupRef, "ref");
        if (ref == null || ref.isBlank() || (pruneOptionalParticles && isOptionalParticle(groupRef))) {
            return;
        }
        Node group = findReferencedNode("group", ref, groupRef);
        if (group == null) {
            logger.warn("Reference '{}' for node 'group' could not be resolved.", ref);
            return;
        }
        if (!visitedOnPath.add(group)) {
            markIncompleteUnlessOptional(groupRef, parentXPath);
            return; // a group that contains itself
        }
        try {
            for (Node compositor : getDirectChildElements(group)) {
                String name = compositor.getLocalName();
                if ("sequence".equals(name) || "choice".equals(name) || "all".equals(name)) {
                    groupReferenceThreadLocal.set(groupRef);
                    try {
                        processComplexContent(compositor, parentXPath, level, visitedOnPath);
                    } finally {
                        groupReferenceThreadLocal.remove();
                    }
                }
            }
        } finally {
            visitedOnPath.remove(group);
        }
    }

    private void processComplexContent(Node contentNode, String parentXPath, int level, Set<Node> visitedOnPath) {
        String localName = contentNode.getLocalName();

        // A group reference as the whole content model (JATS: <complexType><group ref="article-full-model"/>)
        if ("group".equals(localName)) {
            processGroupReference(contentNode, parentXPath, level, visitedOnPath);
            return;
        }

        // Handle the case where contentNode itself is a choice/sequence/all
        // This happens when an inline complexType has a direct compositor as its content
        if ("choice".equals(localName) || "sequence".equals(localName) || "all".equals(localName)) {
            // A compositor reached here bypasses traverseNode, so prune it the same way (JATS mixed content:
            // <complexType mixed="true"><choice minOccurs="0" maxOccurs="unbounded">)
            if (pruneOptionalParticles && isOptionalParticle(contentNode)) {
                return;
            }
            // Create a wrapper element for this compositor
            String containerName = localName.toUpperCase();
            String containerXPath = parentXPath + "/" + containerName + "_" + counter;

            // Create and register the container element
            XsdExtendedElement containerElem = new XsdExtendedElement();
            containerElem.setElementName(containerName);
            containerElem.setCurrentXpath(containerXPath);
            containerElem.setParentXpath(parentXPath);
            containerElem.setLevel(level);
            containerElem.setCounter(counter);
            containerElem.setCurrentNode(contentNode);
            containerElem.setElementType("(container)");
            // A sample repeats a referenced group by the reference's bounds (MathML mfrac: minOccurs="2")
            Node groupReference = groupReferenceThreadLocal.get();
            groupReferenceThreadLocal.remove();
            if (sampleExpansion && groupReference != null) {
                containerElem.setCardinalityNode(groupReference);
            }

            // Add container as child of parent
            if (xsdDocumentationData.getExtendedXsdElementMap().containsKey(parentXPath)) {
                xsdDocumentationData.getExtendedXsdElementMap().get(parentXPath).addChild(containerXPath);
            }
            xsdDocumentationData.putExtendedXsdElement(containerXPath, containerElem);

            // Process children of this compositor with the container as their parent
            for (Node child : getDirectChildElements(contentNode)) {
                String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
                String childLocalName = child.getLocalName();

                if ("element".equals(childLocalName)) {
                    String childXPath = uniqueChildXPath(containerXPath + "/" + childName);
                    traverseNode(child, childXPath, containerXPath, level + 1, visitedOnPath);
                } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                    // Nested compositor
                    processComplexContent(child, containerXPath, level + 1, visitedOnPath);
                } else if ("group".equals(childLocalName)) {
                    processGroupReference(child, containerXPath, level + 1, visitedOnPath);
                } else if ("attribute".equals(childLocalName)) {
                    String childXPath = parentXPath + "/@" + childName; // Attributes go on parent, not container
                    traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
                } else if ("any".equals(childLocalName)) {
                    addWildcardPlaceholder(child, containerXPath, level + 1);
                }
            }
            return; // Done processing this compositor
        }

        // Handle extension
        if ("extension".equals(localName)) {
            String baseType = getAttributeValue(contentNode, "base");
            Node baseTypeNode = findGlobalType(baseType, contentNode);
            if (baseTypeNode != null && !derivesFromItself(baseTypeNode)) {
                // First, process the base type's content
                Node baseContentModel = findContentModel(baseTypeNode, null);
                if (baseContentModel != null) {
                    processComplexContent(baseContentModel, parentXPath, level, visitedOnPath);
                }
                // Then the attributes declared on the base type itself, next to its particle or without one
                // (rim IdentifiableType@id, Subsonic JukeboxStatus); a derived base's own are handled by the recursion
                processAttributes(baseTypeNode, parentXPath, level, visitedOnPath);
            }
        }

        // Process all direct children (sequence, choice, element, attribute, etc.)
        for (Node child : getDirectChildElements(contentNode)) {
            String childName = getAttributeValue(child, "name", getAttributeValue(child, "ref"));
            String childLocalName = child.getLocalName();
            String childXPath;

            if ("attribute".equals(childLocalName) || "attributeGroup".equals(childLocalName)) {
                continue; // attribute uses follow the particles, see below
            } else if ("element".equals(childLocalName)) {
                childXPath = uniqueChildXPath(parentXPath + "/" + childName);
            } else if ("sequence".equals(childLocalName) || "choice".equals(childLocalName) || "all".equals(childLocalName)) {
                // Create explicit sequence/choice/all nodes for SVG visualization
                String containerName = childLocalName.toUpperCase();
                childXPath = parentXPath + "/" + containerName + "_" + counter;
                traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
                continue; // Skip normal traverseNode call for containers
            } else if ("group".equals(childLocalName)) {
                processGroupReference(child, parentXPath, level + 1, visitedOnPath);
                continue;
            } else if ("any".equals(childLocalName)) {
                addWildcardPlaceholder(child, parentXPath, level + 1);
                continue;
            } else {
                childXPath = parentXPath; // For other containers
            }
            traverseNode(child, childXPath, parentXPath, level + 1, visitedOnPath);
        }

        // Attribute uses of this extension or restriction; a restriction also inherits the base chain's attributes
        Set<String> declaredAttributes = new HashSet<>();
        processAttributeUses(contentNode, parentXPath, level, visitedOnPath, declaredAttributes);
        if ("restriction".equals(localName)) {
            processRestrictionBaseAttributes(contentNode, parentXPath, level, visitedOnPath, declaredAttributes);
        }
    }

    /**
     * Generates a sample XML instance for the first global element of the schema (document order). Unless
     * {@link #processXsd(MarkdownMode)} has run, only that element is expanded ({@link #expandForSample}).
     *
     * @param mandatoryOnly  emit only required elements/attributes when {@code true}
     * @param maxOccurrences cap on repeated elements
     * @return the sample XML, or an XML comment describing why none could be generated
     */
    public String generateSampleXml(boolean mandatoryOnly, int maxOccurrences) {
        if (!fullyProcessed) {
            String loadError = loadSchemaForSample();
            if (loadError != null) {
                return loadError;
            }
            String root = getDefaultRootElementName();
            if (root == null) {
                return "<!-- No root element found in XSD -->";
            }
            return generateSampleXml(root, mandatoryOnly, maxOccurrences);
        }

        XsdExtendedElement rootElement = findDefaultRootElement(xsdDocumentationData.getExtendedXsdElementMap());
        if (rootElement == null) {
            return "<!-- No root element found in XSD -->";
        }
        return generateSampleXmlFor(rootElement, mandatoryOnly, maxOccurrences);
    }

    /**
     * Generates a sample XML instance for the named global element. Unless {@link #processXsd(MarkdownMode)} has run,
     * only that element is expanded ({@link #expandForSample}); generating the same root and mode again reuses the
     * expansion.
     *
     * @param rootElementName local name of a global element, as returned by {@link #getRootElementNames()}
     * @param mandatoryOnly   emit only required elements/attributes when {@code true}
     * @param maxOccurrences  cap on repeated elements
     * @return the sample XML, or an XML comment when the schema could not be processed or a limit was exceeded
     * @throws IllegalArgumentException if the schema has no global element with that name
     */
    public String generateSampleXml(String rootElementName, boolean mandatoryOnly, int maxOccurrences) {
        if (!fullyProcessed) {
            String loadError = loadSchemaForSample();
            if (loadError != null) {
                return loadError;
            }
            try {
                expandForSample(rootElementName, mandatoryOnly, MarkdownMode.OFF);
            } catch (SampleXmlLimits.LimitExceededException e) {
                return e.toXmlComment();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Failed to process XSD for sample XML generation.", e);
                return "<!-- Error processing XSD: " + e.getMessage() + " -->";
            }
        }
        XsdExtendedElement rootElement = findRootElement(xsdDocumentationData.getExtendedXsdElementMap(), rootElementName);
        return generateSampleXmlFor(rootElement, mandatoryOnly, maxOccurrences);
    }

    /**
     * Lists the global elements a sample XML can be generated for, in schema document order. Abstract elements are
     * left out: no instance may contain one. Loads the schema if needed but expands no element.
     *
     * @return the root element names; empty when the schema has none or could not be loaded
     */
    public List<String> getRootElementNames() {
        if (fullyProcessed) {
            return findRootElements(xsdDocumentationData.getExtendedXsdElementMap()).stream()
                    .filter(e -> !isAbstractRoot(e))
                    .map(XsdExtendedElement::getElementName)
                    .toList();
        }
        if (loadSchemaForSample() != null) {
            return List.of();
        }
        return sampleRootElements().stream()
                .filter(node -> !isAbstractDeclaration(node))
                .map(node -> getAttributeValue(node, "name"))
                .toList();
    }

    /**
     * The global element a sample is generated for when no root is chosen: the first non-abstract element of the main
     * document (the first one if all are abstract). If the main document declares none (JATS keeps all of them in
     * included modules), the first element of an included document that is neither abstract nor referenced by another
     * element, then the first non-abstract one.
     *
     * @return the root element name, or {@code null} if the schema has no global element or could not be loaded
     */
    public String getDefaultRootElementName() {
        if (fullyProcessed) {
            XsdExtendedElement root = findDefaultRootElement(xsdDocumentationData.getExtendedXsdElementMap());
            return root == null ? null : root.getElementName();
        }
        if (loadSchemaForSample() != null) {
            return null;
        }
        List<Node> mainElements = xsdDocumentationData.getGlobalElements();
        if (!mainElements.isEmpty()) {
            return mainElements.stream()
                    .filter(node -> !isAbstractDeclaration(node))
                    .findFirst()
                    .or(() -> Optional.of(mainElements.getFirst()))
                    .map(node -> getAttributeValue(node, "name"))
                    .orElse(null);
        }
        List<Node> candidates = sampleRootElements();
        return candidates.stream()
                .filter(node -> !isAbstractDeclaration(node)
                        && !referencedElementNames.contains(getAttributeValue(node, "name")))
                .findFirst()
                .or(() -> candidates.stream().filter(node -> !isAbstractDeclaration(node)).findFirst())
                .or(() -> candidates.stream().findFirst())
                .map(node -> getAttributeValue(node, "name"))
                .orElse(null);
    }

    private boolean isAbstractDeclaration(Node elementNode) {
        return "true".equals(getAttributeValue(elementNode, "abstract"));
    }

    /**
     * Loads the schema once for sample generation (see {@link #loadSchema(MarkdownMode)}).
     *
     * @return {@code null} on success, otherwise the XML comment reported to the caller
     */
    private String loadSchemaForSample() {
        if (schemaLoaded) {
            return null;
        }
        try {
            loadSchema(MarkdownMode.OFF);
            return null;
        } catch (Exception e) {
            logger.error("Failed to process XSD for sample XML generation.", e);
            return "<!-- Error processing XSD: " + e.getMessage() + " -->";
        }
    }

    private String generateSampleXmlFor(XsdExtendedElement rootElement, boolean mandatoryOnly, int maxOccurrences) {
        reseedSample();
        return new XsdSampleXmlWriter(xsdDocumentationData, xsdSampleDataGenerator, random, xsdFilePath,
                sampleOutputDirectory).write(rootElement, mandatoryOnly, maxOccurrences);
    }

    /**
     * Validates the generated XML against the XSD schema.
     * Automatically detects XSD 1.1 schemas and uses appropriate validation.
     * @param xmlContent The XML content to validate
     * @return Validation result with success status and any error messages
     */
    public ValidationResult validateXmlAgainstSchema(String xmlContent) {
        return XsdSampleXmlValidator.validate(xmlContent, xsdFilePath, doc);
    }

    /**
     * Result of a validation operation.
     * @param isValid Whether the validation was successful
     * @param message The validation message
     * @param errors List of validation errors
     */
    public record ValidationResult(boolean isValid, String message, List<ValidationError> errors) {

        public ValidationResult(boolean isValid, String message, List<ValidationError> errors) {
            this.isValid = isValid;
            this.message = message;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public ValidationResult(boolean isValid, String message) {
            this(isValid, message, List.of());
        }
    }

    /**
     * Represents a validation error.
     * @param message The error message
     * @param lineNumber The line number
     * @param columnNumber The column number
     * @param severity The severity level
     */
    public record ValidationError(
            int lineNumber,
            int columnNumber,
            String severity,  // "Error", "Warning", "Fatal Error"
            String message
    ) {
        @Override
        public @NonNull String toString() {
            return String.format("[%s] Line %d, Column %d: %s", severity, lineNumber, columnNumber, message);
        }
    }

    /**
     * Checks if a reference points to an external namespace (different from the target namespace).
     * External namespace references (like ds:Signature from XML Digital Signature) are still included
     * in documentation but marked as external references.
     *
     * @param ref The element reference (e.g., "ds:Signature", "tns:Element")
     * @return true if the reference is to an external namespace (imported schema)
     */
    private boolean isExternalNamespaceReference(String ref) {
        if (ref == null || !ref.contains(":")) {
            return false;
        }

        String prefix = ref.substring(0, ref.indexOf(":"));
        if (prefix.isBlank()) {
            return false;
        }

        // Check if we have namespace information from the schema
        if (xsdDocumentationData != null && xsdDocumentationData.getNamespaces() != null) {
            Map<String, String> namespaces = xsdDocumentationData.getNamespaces();
            String targetNs = xsdDocumentationData.getTargetNamespace();
            String prefixNs = namespaces.get(prefix);

            // Skip if it's the XSD namespace itself (xs:, xsd:)
            if (prefixNs != null && prefixNs.equals(NS_URI)) {
                return false;
            }

            // If the prefix maps to a known namespace:
            // - If there's a targetNamespace, check if it's different
            // - If there's no targetNamespace (unqualified schema), any imported namespace is external
            if (prefixNs != null) {
                if (targetNs == null || targetNs.isBlank()) {
                    // No targetNamespace schema - any prefixed namespace reference is external
                    return true;
                }
                // Has targetNamespace - check if prefix maps to a different namespace
                return !prefixNs.equals(targetNs);
            }
        }

        // Fallback: for schemas without a targetNamespace, treat any non-XSD prefixed ref as external
        String targetNs = xsdDocumentationData != null ? xsdDocumentationData.getTargetNamespace() : null;
        if (targetNs == null || targetNs.isBlank()) {
            return !"xml".equals(prefix);
        }

        return false;
    }

    /**
     * Gets the namespace URI for a given prefix from the reference.
     *
     * @param ref The element reference (e.g., "ds:Signature")
     * @return The namespace URI or null if not found
     */
    private String getNamespaceUriForReference(String ref) {
        if (ref == null || !ref.contains(":")) {
            return null;
        }

        String prefix = ref.substring(0, ref.indexOf(":"));
        if (prefix.isBlank()) {
            return null;
        }

        if (xsdDocumentationData != null && xsdDocumentationData.getNamespaces() != null) {
            String namespaceUri = xsdDocumentationData.getNamespaces().get(prefix);
            if (namespaceUri != null && !namespaceUri.isBlank()) {
                return namespaceUri;
            }
        }

        // Fallback: try to resolve from the main schema element
        if (doc != null && doc.getDocumentElement() != null) {
            String namespaceUri = doc.getDocumentElement().getAttribute("xmlns:" + prefix);
            if (namespaceUri != null && !namespaceUri.isBlank()) {
                return namespaceUri;
            }
        }

        return null;
    }

    // --- XML/DOM Processing Helper Methods ---
    private void initializeXmlTools() throws Exception {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return NS_PREFIX.equals(prefix) ? NS_URI : null;
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
    }

    /**
     * Parses a schema file from its bytes, so the XML parser honours the encoding declaration and a byte order mark
     * (reading the file as a UTF-8 string breaks e.g. windows-1251 schemas and UTF-8 files with a BOM).
     */
    private Document parseXsdFile(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            InputSource source = new InputSource(in);
            source.setSystemId(file.toUri().toString());
            return newXsdDocumentBuilder().parse(source);
        }
    }

    private DocumentBuilder newXsdDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        // Allow DOCTYPE declarations (required for some W3C schemas like xmldsig-core-schema.xsd)
        // but disable external entity processing for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);

        return factory.newDocumentBuilder();
    }

    private void initializeCaches(Document doc) throws Exception {
        // Initialize maps if they don't exist yet
        if (elementMap == null) {
            elementMap = new HashMap<>();
        }
        if (complexTypeMap == null) {
            complexTypeMap = new HashMap<>();
        }
        if (simpleTypeMap == null) {
            simpleTypeMap = new HashMap<>();
        }
        if (groupMap == null) {
            groupMap = new HashMap<>();
        }
        if (attributeGroupMap == null) {
            attributeGroupMap = new HashMap<>();
        }
        if (attributeMap == null) {
            attributeMap = new HashMap<>();
        }

        // Accumulate definitions from this document
        Map<String, Node> elements = findAndCacheGlobalDefs(doc, "element");
        Map<String, Node> complexTypes = findAndCacheGlobalDefs(doc, "complexType");
        Map<String, Node> simpleTypes = findAndCacheGlobalDefs(doc, "simpleType");
        Map<String, Node> groups = findAndCacheGlobalDefs(doc, "group");
        Map<String, Node> attributeGroups = findAndCacheGlobalDefs(doc, "attributeGroup");
        Map<String, Node> attributes = findAndCacheGlobalDefs(doc, "attribute");
        elementMap.putAll(elements);
        complexTypeMap.putAll(complexTypes);
        simpleTypeMap.putAll(simpleTypes);
        groupMap.putAll(groups);
        attributeGroupMap.putAll(attributeGroups);
        attributeMap.putAll(attributes);

        // Namespace-qualified view of the global definitions: two namespaces may declare one with the same local
        // name (JATS and MathML both declare sec, list and title), and the local-name maps above keep only one.
        String targetNamespace = doc.getDocumentElement().getAttribute("targetNamespace");
        elements.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("element", targetNamespace, name), node));
        complexTypes.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("complexType", targetNamespace, name), node));
        simpleTypes.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("simpleType", targetNamespace, name), node));
        groups.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("group", targetNamespace, name), node));
        attributeGroups.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("attributeGroup", targetNamespace, name), node));
        attributes.forEach((name, node) -> qualifiedGlobalDefs.put(qualifiedKey("attribute", targetNamespace, name), node));

        indexSubstitutionGroups(doc);
        indexDerivedComplexTypes(doc);
    }

    /** Records every global element of {@code doc} under each head of its (XSD 1.1: list-valued) substitution group. */
    private void indexSubstitutionGroups(Document doc) throws Exception {
        NodeList members = (NodeList) xpath.evaluate("/xs:schema/xs:element[@name and @substitutionGroup]", doc,
                XPathConstants.NODESET);
        for (int i = 0; i < members.getLength(); i++) {
            Node member = members.item(i);
            for (String head : getAttributeValue(member, "substitutionGroup").trim().split("\\s+")) {
                if (!head.isEmpty()) {
                    indexByQName(substitutionMembers, "substitutionHead", head, member, member);
                }
            }
        }
    }

    /** Records every global complex type of {@code doc} under the base type of its complex or simple content. */
    private void indexDerivedComplexTypes(Document doc) throws Exception {
        NodeList types = (NodeList) xpath.evaluate(
                "/xs:schema/xs:complexType[@name]/*[local-name()='complexContent' or local-name()='simpleContent']"
                        + "/*[(local-name()='extension' or local-name()='restriction') and @base]",
                doc, XPathConstants.NODESET);
        for (int i = 0; i < types.getLength(); i++) {
            Node derivation = types.item(i);
            Node type = derivation.getParentNode().getParentNode();
            indexByQName(derivedComplexTypes, "derivedFrom", getAttributeValue(derivation, "base"), derivation, type);
        }
    }

    private static void indexByQName(Map<String, List<Node>> index, String kind, String qName, Node context,
                                     Node value) {
        int colon = qName.indexOf(':');
        String prefix = colon > 0 ? qName.substring(0, colon) : null;
        String localName = qName.substring(colon + 1);
        index.computeIfAbsent(qualifiedKey(kind, context.lookupNamespaceURI(prefix), localName), k -> new ArrayList<>())
                .add(value);
        index.computeIfAbsent(qualifiedKey(kind + "*", null, localName), k -> new ArrayList<>()).add(value);
    }

    /** Entries of {@code index} for the global declaration {@code node}: by namespace and name, else by name. */
    private static List<Node> indexedFor(Map<String, List<Node>> index, String kind, Node node) {
        String name = ((org.w3c.dom.Element) node).getAttribute("name");
        List<Node> entries = index.get(qualifiedKey(kind, declaredNamespace(node), name));
        if (entries == null) {
            entries = index.get(qualifiedKey(kind + "*", null, name));
        }
        return entries == null ? List.of() : entries;
    }

    /** The target namespace of the schema document declaring {@code node}; empty for none (chameleon). */
    private static String declaredNamespace(Node node) {
        return node.getOwnerDocument().getDocumentElement().getAttribute("targetNamespace");
    }

    /** The namespace a global declaration takes in an instance: its document's, or the main one for a chameleon. */
    private String instanceNamespace(Node node) {
        String namespace = declaredNamespace(node);
        if (!namespace.isEmpty()) {
            return namespace;
        }
        String main = xsdDocumentationData.getTargetNamespace();
        return main == null ? "" : main;
    }

    /**
     * The element a sample emits for the global element {@code declaration}: the declaration itself, or, while
     * expanding for a sample, a concrete member of its substitution group when it is abstract. Members of abstract
     * members are searched too, nearest first, preferring the head's namespace; a member on the current path is
     * skipped so the expansion stays finite (KML {@code Folder} contains {@code AbstractFeatureGroup}).
     */
    private Node sampleSubstitute(Node declaration, Set<Node> visitedOnPath) {
        if (!sampleExpansion || !isAbstractDeclaration(declaration)) {
            return declaration;
        }
        String headNamespace = instanceNamespace(declaration);
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(declaration);
        ArrayDeque<Node> heads = new ArrayDeque<>(List.of(declaration));
        Node otherNamespace = null;
        while (!heads.isEmpty()) {
            for (Node member : indexedFor(substitutionMembers, "substitutionHead", heads.poll())) {
                if (!seen.add(member)) {
                    continue;
                }
                if (isAbstractDeclaration(member)) {
                    heads.add(member);
                } else if (!visitedOnPath.contains(member)) {
                    if (instanceNamespace(member).equals(headNamespace)) {
                        return member;
                    }
                    if (otherNamespace == null) {
                        otherNamespace = member;
                    }
                }
            }
        }
        return otherNamespace != null ? otherNamespace : declaration;
    }

    /**
     * A concrete global complex type derived, directly or through abstract intermediates, from the abstract type
     * {@code abstractType}: the nearest one, preferring the abstract type's namespace and a type whose own content does
     * not declare an element of the abstract type again; {@code null} if there is none. Garmin {@code AbstractStep_t}
     * has {@code Repeat_t}, whose required {@code Child} is an {@code AbstractStep_t} again, declared before
     * {@code Step_t}; the recursion was cut and the workout left without its step.
     */
    private Node concreteDerivedType(Node abstractType) {
        String namespace = instanceNamespace(abstractType);
        String abstractName = getAttributeValue(abstractType, "name");
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(abstractType);
        ArrayDeque<Node> bases = new ArrayDeque<>(List.of(abstractType));
        Node sameNamespace = null;
        Node otherNamespace = null;
        while (!bases.isEmpty()) {
            for (Node derived : indexedFor(derivedComplexTypes, "derivedFrom", bases.poll())) {
                if (!seen.add(derived)) {
                    continue;
                }
                if (isAbstractDeclaration(derived)) {
                    bases.add(derived);
                } else if (instanceNamespace(derived).equals(namespace)) {
                    if (!declaresElementOfType(derived, abstractName)) {
                        return derived;
                    }
                    if (sameNamespace == null) {
                        sameNamespace = derived;
                    }
                } else if (otherNamespace == null) {
                    otherNamespace = derived;
                }
            }
        }
        return sameNamespace != null ? sameNamespace : otherNamespace;
    }

    /** Whether a type declares, anywhere in its own content, an element whose type has the local name {@code name}. */
    private static boolean declaresElementOfType(Node type, String name) {
        if (name == null || !(type instanceof Element typeElement)) {
            return false;
        }
        NodeList elements = typeElement.getElementsByTagNameNS(NS_URI, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            String elementType = ((Element) elements.item(i)).getAttribute("type");
            if (elementType.substring(elementType.indexOf(':') + 1).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The prefix a sample uses for {@code namespace}: none for the main target namespace (the sample's default
     * namespace) or no namespace, else the prefix the declaring document binds, one of the schema's prefixes for it,
     * or a generated one.
     */
    private String instancePrefix(String namespace, Node declaration) {
        String main = xsdDocumentationData.getTargetNamespace();
        if (namespace == null || namespace.isEmpty() || namespace.equals(main)) {
            return null;
        }
        return prefixFor(namespace, declaration);
    }

    /**
     * The prefix a sample binds to {@code namespace}, the same for the whole schema: {@code xml} for the XML namespace,
     * else the prefix the declaring document binds, one of the schema's prefixes for it, or a generated {@code nsN}. A
     * prefix is never shared by two namespaces.
     */
    private String prefixFor(String namespace, Node declaration) {
        if (XML_NAMESPACE_URI.equals(namespace)) {
            return "xml";
        }
        String chosen = samplePrefixes.get(namespace);
        if (chosen != null) {
            return chosen;
        }
        Map<String, String> known = xsdDocumentationData.getNamespaces() != null
                ? xsdDocumentationData.getNamespaces() : Map.of();
        String own = declaration.lookupPrefix(namespace);
        if (isFreePrefix(own, namespace, known)) {
            chosen = own;
        } else {
            chosen = known.entrySet().stream()
                    .filter(e -> namespace.equals(e.getValue()) && isFreePrefix(e.getKey(), namespace, known))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }
        if (chosen == null) {
            int n = 1;
            while (known.containsKey("ns" + n) || samplePrefixes.containsValue("ns" + n)) {
                n++;
            }
            chosen = "ns" + n;
        }
        samplePrefixes.put(namespace, chosen);
        return chosen;
    }

    private boolean isFreePrefix(String prefix, String namespace, Map<String, String> known) {
        return prefix != null && !prefix.isEmpty() && !"xsi".equals(prefix) && !"xml".equals(prefix)
                && namespace.equals(known.getOrDefault(prefix, namespace)) && !samplePrefixes.containsValue(prefix);
    }

    /**
     * The namespace of an element or attribute declaration in an instance: a global one takes its document's target
     * namespace; a local one only when it is qualified ({@code form}, else the document's {@code elementFormDefault}
     * or {@code attributeFormDefault}), otherwise none ({@code ""}). AEAT Modelo 170 {@code comun:Modelo} is a
     * qualified local element of an imported type; SIRI FR-IDF {@code MessageText} an unqualified one.
     */
    private String declarationNamespace(Node declaration, boolean attribute) {
        Node parent = declaration.getParentNode();
        boolean global = parent != null && "schema".equals(parent.getLocalName());
        if (!global) {
            String form = getAttributeValue(declaration, "form");
            if (form == null) {
                form = declaration.getOwnerDocument().getDocumentElement()
                        .getAttribute(attribute ? "attributeFormDefault" : "elementFormDefault");
            }
            if (!"qualified".equals(form)) {
                return "";
            }
        }
        return instanceNamespace(declaration);
    }

    /**
     * The prefix an element or attribute is written with: none without a namespace, none for an element of the main
     * target namespace (the sample's default namespace), otherwise {@link #prefixFor}. Attributes never take the
     * default namespace, so a qualified attribute always has a prefix.
     */
    private String emissionPrefix(String namespace, Node declaration, boolean attribute) {
        if (namespace.isEmpty()) {
            return null;
        }
        String main = xsdDocumentationData.getTargetNamespace();
        if (!attribute && namespace.equals(main == null ? "" : main)) {
            return null;
        }
        return prefixFor(namespace, declaration);
    }

    private void populateDocumentationData() throws Exception {
        // Use the main schema file for basic metadata
        Element schemaElement = doc.getDocumentElement();
        xsdDocumentationData.setTargetNamespace(getAttributeValue(schemaElement, "targetNamespace"));
        xsdDocumentationData.setVersion(getAttributeValue(schemaElement, "version"));

        // NEW: Read the schema attributes and store them in the data object.
        // The default value "unqualified" is used if the attribute is not present.
        xsdDocumentationData.setAttributeFormDefault(getAttributeValue(schemaElement, "attributeFormDefault", "unqualified"));
        xsdDocumentationData.setElementFormDefault(getAttributeValue(schemaElement, "elementFormDefault", "unqualified"));

        // XSD 1.1: Process xs:defaultOpenContent (applies to all types in schema)
        Node defaultOpenContentNode = getDirectChildElement(schemaElement, "defaultOpenContent");
        if (defaultOpenContentNode != null) {
            OpenContent defaultOpenContent = componentDetails.parseOpenContentNode(defaultOpenContentNode, true);
            xsdDocumentationData.setDefaultOpenContent(defaultOpenContent);
        }

        // Get global elements from the main schema
        xsdDocumentationData.setGlobalElements(nodeListToList((NodeList) xpath.evaluate("/xs:schema/xs:element[@name]", doc, XPathConstants.NODESET)));

        // Create the lists of global types and sort them alphabetically by name.
        List<Node> complexTypes = new ArrayList<>(complexTypeMap.values());
        complexTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalComplexTypes(complexTypes);

        List<Node> simpleTypes = new ArrayList<>(simpleTypeMap.values());
        simpleTypes.sort(Comparator.comparing(node -> getAttributeValue(node, "name")));
        xsdDocumentationData.setGlobalSimpleTypes(simpleTypes);

        // Extract namespaces from all processed schemas
        Map<String, String> nsMap = new HashMap<>();

        // Start with the main schema
        var attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                nsMap.put(attr.getLocalName(), attr.getNodeValue());
            }
        }

        // Add namespaces from included/imported schemas (including resolved remote imports)
        for (Path currentFile : schemaFilesToScan()) {
            Document document = parseXsdFile(currentFile);
            var currentAttributes = document.getDocumentElement().getAttributes();
            for (int i = 0; i < currentAttributes.getLength(); i++) {
                Node attr = currentAttributes.item(i);
                if (attr.getNodeName().startsWith("xmlns:")) {
                    nsMap.putIfAbsent(attr.getLocalName(), attr.getNodeValue());
                }
            }
        }

        xsdDocumentationData.setNamespaces(nsMap);
    }

    private void buildTypeUsageMap() {
        logger.debug("Building type usage index for faster lookups...");
        // Group elements by type name WITHOUT namespace prefix for proper lookup
        // (type attribute may be "tns:PersonType" but lookup uses just "PersonType")
        Map<String, List<XsdExtendedElement>> typeUsageMap = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(element -> element.getElementType() != null && !element.getElementType().isEmpty())
                .collect(Collectors.groupingBy(element -> stripNamespace(element.getElementType())));

        typeUsageMap.values().parallelStream()
                .forEach(list -> list.sort(Comparator.comparing(XsdExtendedElement::getCurrentXpath)));

        xsdDocumentationData.setTypeUsageMap(typeUsageMap);
        logger.debug("Type usage index built with {} types.", typeUsageMap.size());
    }

    private RestrictionInfo parseRestriction(Node restrictionNode) {
        String base = getAttributeValue(restrictionNode, "base");
        // The map is now initialized for lists
        Map<String, List<String>> facets = new LinkedHashMap<>();

        // First, inherit facets from the base type if it's a named type (not primitive)
        if (base != null && !base.startsWith("xs:") && !base.startsWith("xsd:")) {
            RestrictionInfo inheritedFacets = getInheritedFacets(base, restrictionNode, new HashSet<>());
            if (inheritedFacets != null && inheritedFacets.facets() != null) {
                // Create mutable copies of inherited facet lists so they can be extended
                inheritedFacets.facets().forEach((k, v) -> facets.put(k, new ArrayList<>(v)));
                // Update the base to the ultimate primitive type if available
                if (inheritedFacets.base() != null) {
                    base = inheritedFacets.base();
                }
            }
        }

        // Then the restriction's own facets: each replaces the inherited facet of the same name (SIRI
        // DaysOfWeekEnumerationx lists 12 of DayTypeEnumeration's 22 values; appending gave all 22 again)
        facets.putAll(directFacets(restrictionNode));
        return new RestrictionInfo(base, facets);
    }

    /**
     * Gets inherited facets from a named type by following the type chain.
     * This handles cases like a restriction with base="LEICodeType" where the actual
     * pattern facet is defined in the LEICodeType simpleType.
     *
     * @param typeName    The name of the type to get inherited facets from.
     * @param contextNode The node carrying the reference, whose in-scope namespaces resolve the prefix.
     * @param visiting    Simple types already on the derivation chain (guards against circular derivations).
     * @return RestrictionInfo with the ultimate base type and all inherited facets.
     */
    private RestrictionInfo getInheritedFacets(String typeName, Node contextNode, Set<Node> visiting) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }

        Node typeNode = lookupGlobalDefinition("simpleType", simpleTypeMap, typeName, contextNode);
        if (typeNode == null || !visiting.add(typeNode)) {
            return null;
        }

        // Find restriction in the simpleType
        Node restriction = getDirectChildElement(typeNode, "restriction");
        if (restriction == null) {
            return null;
        }

        // Recursively get base type's facets (for chains like Type1 -> Type2 -> xs:string)
        String baseType = getAttributeValue(restriction, "base");
        Map<String, List<String>> facets = new LinkedHashMap<>();

        // First get inherited facets from the base type
        if (baseType != null && !baseType.startsWith("xs:") && !baseType.startsWith("xsd:")) {
            RestrictionInfo inherited = getInheritedFacets(baseType, restriction, visiting);
            if (inherited != null && inherited.facets() != null) {
                facets.putAll(inherited.facets());
                baseType = inherited.base(); // Use the ultimate primitive type
            }
        }

        // Then this type's own facets, each replacing the inherited facet of the same name
        facets.putAll(directFacets(restriction));

        return new RestrictionInfo(baseType, facets);
    }

    /**
     * The facets declared directly on a restriction, values grouped by facet name in document order. A derivation
     * step's facet replaces the base type's facet of the same name: a restriction's enumeration is a subset of its
     * base's, never an addition.
     */
    private Map<String, List<String>> directFacets(Node restriction) {
        Map<String, List<String>> facets = new LinkedHashMap<>();
        for (Node facetNode : getDirectChildElements(restriction)) {
            String facetName = facetNode.getLocalName();
            if (!"annotation".equals(facetName)) {
                facets.computeIfAbsent(facetName, k -> new ArrayList<>()).add(getAttributeValue(facetNode, "value"));
            }
        }
        return facets;
    }

    private Node findTypeDefinition(Node elementNode, String typeName) {
        // Case 1: Inline definition
        if (elementNode != null) {
            Node inlineComplex = getDirectChildElement(elementNode, "complexType");
            if (inlineComplex != null) {
                return inlineComplex;
            }
            Node inlineSimple = getDirectChildElement(elementNode, "simpleType");
            if (inlineSimple != null) {
                return inlineSimple;
            }
        }
        // Case 2: Global reference, resolved through the prefix bound at the referencing element
        return findGlobalType(typeName, elementNode);
    }

    /**
     * Finds a global complex or simple type by QName.
     *
     * @param typeName    the (possibly prefixed) type name
     * @param contextNode the node carrying the reference, or {@code null} to look up by local name only
     * @return the type definition node, or {@code null}
     */
    private Node findGlobalType(String typeName, Node contextNode) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        Node typeNode = lookupGlobalDefinition("complexType", complexTypeMap, typeName, contextNode);
        return typeNode != null ? typeNode : lookupGlobalDefinition("simpleType", simpleTypeMap, typeName, contextNode);
    }

    /**
     * Resolves a global definition referenced by a QName using the namespace bound to its prefix at
     * {@code contextNode}. Falls back to the local-name map when the namespace is unknown or has no matching
     * definition (e.g. chameleon includes).
     */
    private Node lookupGlobalDefinition(String kind, Map<String, Node> localNameMap, String qName, Node contextNode) {
        String localName = stripNamespace(qName);
        if (contextNode != null) {
            int colon = qName.indexOf(':');
            String prefix = colon > 0 ? qName.substring(0, colon) : null;
            String namespace = contextNode.lookupNamespaceURI(prefix);
            if (namespace != null || prefix == null) {
                Node node = qualifiedGlobalDefs.get(qualifiedKey(kind, namespace, localName));
                if (node != null) {
                    return node;
                }
            }
        }
        return localNameMap.get(localName);
    }

    private static String qualifiedKey(String kind, String namespace, String localName) {
        return kind + "{" + (namespace == null ? "" : namespace) + "}" + localName;
    }

    /**
     * Whether following the extension chain from {@code typeNode} leads back to a type already on the chain, which
     * only an invalid schema can declare. Guards the recursive base-type expansion in
     * {@link #processComplexContent}.
     */
    private boolean derivesFromItself(Node typeNode) {
        Set<Node> chain = Collections.newSetFromMap(new IdentityHashMap<>());
        Node current = typeNode;
        while (current != null) {
            if (!chain.add(current)) {
                return true;
            }
            Node contentModel = findContentModel(current, null);
            if (contentModel == null || !"extension".equals(contentModel.getLocalName())) {
                return false;
            }
            current = findGlobalType(getAttributeValue(contentModel, "base"), contentModel);
        }
        return false;
    }

    /**
     * Follows simpleContent derivations through named complex types to the type the content is based on (GML
     * {@code LengthType} → {@code MeasureType} → {@code xs:double}), so a value is generated for that type.
     *
     * @param baseType the base of a simpleContent extension
     * @param context  the node declaring {@code baseType}, for prefix resolution
     * @return the first base that is not a named complex type with simple content
     */
    private String simpleContentBaseType(String baseType, Node context) {
        Set<Node> chain = Collections.newSetFromMap(new IdentityHashMap<>());
        String current = baseType;
        Node currentContext = context;
        while (true) {
            Node type = findGlobalType(current, currentContext);
            Node simpleContent = type != null && "complexType".equals(type.getLocalName()) && chain.add(type)
                    ? getDirectChildElement(type, "simpleContent") : null;
            if (simpleContent == null) {
                return current;
            }
            Node derivation = getDirectChildElement(simpleContent, "extension");
            if (derivation == null) {
                derivation = getDirectChildElement(simpleContent, "restriction");
            }
            String next = derivation != null ? getAttributeValue(derivation, "base") : null;
            if (next == null || next.isEmpty()) {
                return current;
            }
            current = next;
            currentContext = derivation;
        }
    }

    /**
     * Finds a globally defined type (simple or complex) by its name.
     * This method is intended for use by other services that need to look up types.
     *
     * @param typeName The name of the type to find (e.g., "MyComplexType", "xs:string").
     * @return The corresponding DOM Node if a custom type is found, otherwise null.
     */
    public Node findTypeNodeByName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        // Remove namespace prefix if present (e.g., "myNs:MyType" -> "MyType")
        String cleanTypeName = stripNamespace(typeName);

        // First, check the cache for complex types
        Node typeNode = complexTypeMap.get(cleanTypeName);
        if (typeNode != null) {
            return typeNode;
        }

        // If not found, check the cache for simple types
        return simpleTypeMap.get(cleanTypeName);
    }

    private Node findContentModel(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) {
            return null;
        }

        // Look inside <complexContent>
        Node complexContent = getDirectChildElement(contextNode, "complexContent");
        if (complexContent != null) {
            Node extension = getDirectChildElement(complexContent, "extension");
            if (extension != null) {
                return extension;
            }
            Node restriction = getDirectChildElement(complexContent, "restriction");
            if (restriction != null) {
                return restriction;
            }
        }

        // Look inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            Node extension = getDirectChildElement(simpleContent, "extension");
            if (extension != null) {
                return extension;
            }
            Node restriction = getDirectChildElement(simpleContent, "restriction");
            if (restriction != null) {
                return restriction;
            }
        }

        // Look for direct particles if no complex/simpleContent is present
        Node sequence = getDirectChildElement(contextNode, "sequence");
        if (sequence != null) {
            return sequence;
        }
        Node choice = getDirectChildElement(contextNode, "choice");
        if (choice != null) {
            return choice;
        }
        Node all = getDirectChildElement(contextNode, "all");
        if (all != null) {
            return all;
        }
        Node group = getDirectChildElement(contextNode, "group");
        return group;
    }

    private Node findRestriction(Node elementNode, Node typeDefinitionNode) {
        Node contextNode = (typeDefinitionNode != null) ? typeDefinitionNode : elementNode;
        if (contextNode == null) {
            return null;
        }

        // Direct <restriction> inside a <simpleType>
        Node restriction = getDirectChildElement(contextNode, "restriction");
        if (restriction != null) {
            return restriction;
        }

        // Restriction inside <simpleContent>
        Node simpleContent = getDirectChildElement(contextNode, "simpleContent");
        if (simpleContent != null) {
            return getDirectChildElement(simpleContent, "restriction");
        }
        return null;
    }

    public Node findReferencedNode(String localName, String ref) {
        return findReferencedNode(localName, ref, null);
    }

    /**
     * Resolves a {@code ref} to a global element, group or attribute group in the namespace its prefix is bound to at
     * {@code contextNode}, falling back to the local name.
     *
     * @param contextNode the node carrying the reference, or {@code null} to look up by local name only
     */
    private Node findReferencedNode(String localName, String ref, Node contextNode) {
        return switch (localName) {
            case "group" -> lookupGlobalDefinition("group", groupMap, ref, contextNode);
            case "attributeGroup" -> lookupGlobalDefinition("attributeGroup", attributeGroupMap, ref, contextNode);
            case "element" -> lookupGlobalDefinition("element", elementMap, ref, contextNode);
            case "attribute" -> lookupGlobalDefinition("attribute", attributeMap, ref, contextNode);
            default -> null;
        };
    }

    private Map<String, Node> findAndCacheGlobalDefs(Document doc, String tagName) throws Exception {
        Map<String, Node> map = new HashMap<>();
        // Find global definitions directly under the schema root
        NodeList nodes = (NodeList) xpath.evaluate("/xs:schema/xs:" + tagName + "[@name]", doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = getAttributeValue(node, "name");
            if (name != null && !name.isEmpty()) {
                map.put(name, node);
            }
        }
        logger.debug("Cached {} global definitions for tag <{}>", map.size(), tagName);
        return map;
    }

    private void executeAndTrack(String taskName, Runnable task) {
        // Check for cancellation before starting each task
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Documentation generation was cancelled");
        }

        long startTime = System.currentTimeMillis();
        if (progressListener != null) {
            progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.RUNNING, 0));
        }
        try {
            task.run();

            // Check for cancellation after task completion
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Documentation generation was cancelled");
            }

            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FINISHED, duration));
            }
        } catch (CancellationException e) {
            // Re-throw cancellation exceptions without reporting as failure
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            if (progressListener != null) {
                progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FAILED, duration));
            }
            throw e; // Re-throw the exception after reporting
        }
    }

    /** The (memoized) source code snippet of a schema node; see {@link #sourceSnippetMemo}. */
    private String sourceSnippet(Node node) {
        return sourceSnippetMemo.computeIfAbsent(node, this::nodeToString);
    }

    private String nodeToString(Node node) {
        try {
            StringWriter writer = new StringWriter();
            // Gets the Transformer instance that is specific to the current thread.
            Transformer transformer = transformerThreadLocal.get();
            transformer.transform(new DOMSource(withoutDeselectedDocumentation(node)), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.warn("Could not serialize node to string.", e);
            return "<!-- Error serializing node -->";
        }
    }

    /**
     * Returns the node to serialize for the "Source Code" snippets. When the user has de-selected
     * documentation languages, a deep copy without the {@code xs:documentation} elements of those
     * languages is returned so the snippets do not leak documentation that was excluded from the
     * output; otherwise the node itself is returned.
     */
    private Node withoutDeselectedDocumentation(Node node) {
        if (includedLanguages == null || includedLanguages.isEmpty() || node == null) {
            return node;
        }
        Node copy = node.cloneNode(true);
        removeDeselectedDocumentation(copy);
        return copy;
    }

    private void removeDeselectedDocumentation(Node node) {
        NodeList children = node.getChildNodes();
        // Iterate backwards because removing a child shifts the indices of later siblings.
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if ("documentation".equals(child.getLocalName())
                    && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(child.getNamespaceURI())) {
                String lang = getAttributeValue(child, "xml:lang", "default");
                String normalizedLang = lang != null ? lang.toLowerCase() : "default";
                if (!isLanguageIncluded(normalizedLang)) {
                    node.removeChild(child);
                }
                continue;
            }
            removeDeselectedDocumentation(child);
        }
    }

    public String stripNamespace(String value) {
        if (value == null) {
            return "";
        }
        int colonIndex = value.indexOf(':');
        return (colonIndex != -1) ? value.substring(colonIndex + 1) : value;
    }

    /**
     * Resolves a remote {@code xs:include} or {@code xs:import} to a local file without writing into
     * the user's directory: Schema Library / catalog mappings first, then the shared schema cache
     * (downloads only when allowed by the offline rule). Returns {@code null} on a miss.
     */
    /**
     * The local schema the Schema Library maps an imported namespace to, when the import's relative
     * {@code schemaLocation} names a file that does not exist (JATS imports {@code standard-modules/xlink.xsd}). No
     * download.
     */
    private Path libraryFileForNamespace(String namespace, String baseUri) {
        if (namespace == null || namespace.isBlank() || (baseUri != null && isRemote(baseUri))) {
            return null;
        }
        // Downloads are allowed (the generator runs in the background); the result is copied
        // next to the referencing schema by the caller.
        SchemaLibraryService library = SchemaLibraryServiceImpl.shared();
        return SchemaLibraryLookup.localFileFor(library, namespace, null, baseUri, library.isRemoteDownloadAllowed())
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::exists)
                .orElse(null);
    }

    private Path resolveRemoteImport(String namespace, String location, String baseUri) {
        SchemaLibraryService library = SchemaLibraryServiceImpl.shared();
        return SchemaLibraryLookup.localFileFor(library, namespace, location, baseUri, library.isRemoteDownloadAllowed())
                .or(() -> {
                    if (!library.isRemoteDownloadAllowed() || !PathValidator.isUrlSafeToAccess(location)) {
                        return java.util.Optional.empty();
                    }
                    try {
                        return java.util.Optional.of(SchemaResourceCache.shared().getOrDownload(location));
                    } catch (java.io.IOException e) {
                        logger.warn("Cannot download imported schema {}: {}", location, e.getMessage());
                        org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "schema_doc.import_download");
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }

    private boolean isRemote(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String loc = location.toLowerCase();
        return loc.startsWith("http://") || loc.startsWith("https://") || loc.startsWith("ftp://");
    }

    /**
     * Sets separate source code snippets for element definition and referenced type definition.
     * This method splits the source code into two parts for better display in collapsible containers.
     *
     * @param extendedElem       The extended element to update
     * @param node               The current node
     * @param typeName           The type name reference
     * @param typeDefinitionNode The type definition node (if inline)
     */
    private void setSourceCodeSnippets(XsdExtendedElement extendedElem, Node node, String typeName, Node typeDefinitionNode) {
        // Always set the element's own source code
        String elementSourceCode = sourceSnippet(node);

        // If the option is disabled or there's no type reference, set only element source code
        if (!includeTypeDefinitionsInSourceCode || typeName == null || typeName.isEmpty()) {
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // Check if this is a built-in XSD type (xs:string, xs:int, etc.)
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            logger.debug("Skipping built-in type definition for: {}", typeName);
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // If we have an inline type definition, it's already included in the element source code
        if (typeDefinitionNode != null && typeDefinitionNode.getParentNode() == node) {
            logger.debug("Type definition is inline, already included in element source code");
            extendedElem.setSourceCode(elementSourceCode);
            return;
        }

        // Look up the global type definition
        String cleanTypeName = stripNamespace(typeName);
        Node globalTypeNode = complexTypeMap.get(cleanTypeName);
        if (globalTypeNode == null) {
            globalTypeNode = simpleTypeMap.get(cleanTypeName);
        }

        // If we found a global type definition, set it separately
        if (globalTypeNode != null) {
            String typeSourceCode = sourceSnippet(globalTypeNode);
            logger.debug("Setting separate type definition for '{}' in referencedTypeCode", cleanTypeName);

            // Set element source code (without type definition)
            extendedElem.setSourceCode(elementSourceCode);
            // Set referenced type separately for display in its own section
            extendedElem.setReferencedTypeCode(typeSourceCode);
            extendedElem.setReferencedTypeName(cleanTypeName);
        } else {
            logger.debug("No global type definition found for: {}", cleanTypeName);
            extendedElem.setSourceCode(elementSourceCode);
        }
    }
}
