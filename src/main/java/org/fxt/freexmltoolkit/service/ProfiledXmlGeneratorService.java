/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.GeneratedFile;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.strategy.ValueStrategy;
import org.fxt.freexmltoolkit.service.strategy.ValueStrategyFactory;
import org.w3c.dom.Node;

/**
 * Generates XML example data from XSD schemas using configurable generation profiles.
 * Supports XPath-based rules for controlling value generation per element/attribute,
 * batch generation of multiple files, and various value strategies (sequences, templates, etc.).
 */
public class ProfiledXmlGeneratorService {

    private static final Logger logger = LogManager.getLogger(ProfiledXmlGeneratorService.class);
    private static final Pattern WILDCARD_INDEX = Pattern.compile("\\[\\*]");
    private static final Pattern CONTAINER_SEGMENT = Pattern.compile("/(SEQUENCE|CHOICE|ALL)_?\\d*");

    /**
     * Cache of compiled regex patterns for rule XPaths that contain {@code [*]} wildcards.
     * Without this cache, {@link #xpathMatches(String, String, String)} would call
     * {@link String#matches(String)} which compiles a fresh {@link Pattern} on every call —
     * with N rules and M elements that becomes O(N*M) regex compilations and dominates
     * generation time for large schemas like FundsXML.
     */
    private static final ConcurrentHashMap<String, Pattern> WILDCARD_PATTERN_CACHE = new ConcurrentHashMap<>();

    private final Random random;

    /**
     * Creates a generator with a non-deterministic {@link Random} source. CHOICE
     * selections and the CHOICE cardinality logic will differ across runs.
     */
    public ProfiledXmlGeneratorService() {
        this.random = new Random();
    }

    /**
     * Creates a generator seeded with a fixed value so CHOICE selection and the
     * random cardinality picks become reproducible across runs. Useful for tests
     * that want to assert on the generated structure.
     *
     * <p>Note: values supplied via {@link XsdSampleDataGenerator} (numeric samples,
     * dates, enumeration picks when no enumeration filter applies) still draw from
     * {@link java.util.concurrent.ThreadLocalRandom} and are not affected by this
     * seed; structural reproducibility only covers CHOICE pathways.</p>
     */
    public ProfiledXmlGeneratorService(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a single XML document from an XSD schema using the given profile.
     *
     * <p>When the profile has no enabled non-AUTO rules, generation is delegated to the
     * plain {@link XsdDocumentationService#generateSampleXml(boolean, int)} so that the
     * "all AUTO" case is byte-equivalent to running without any profile. This avoids
     * structural divergences between the two generators (CHOICE cardinality, empty-container
     * detection, external-namespace handling) that otherwise compound across deeply
     * nested schemas like FundsXML.</p>
     *
     * @param profile     the generation profile with rules and settings
     * @param data        the parsed XSD documentation data
     * @param xsdFilePath the path to the XSD file (for schema location reference)
     * @return the generated XML as a string
     */
    public String generate(GenerationProfile profile, XsdDocumentationData data, String xsdFilePath) {
        if (hasNoEffectiveRules(profile)) {
            logger.debug("Profile '{}' has no effective rules; delegating to plain generator",
                    profile != null ? profile.getName() : "<null>");
            return delegateToPlainGenerator(profile, xsdFilePath);
        }

        logger.debug("Generating with profile '{}' ({} enabled rules, mandatoryOnly={}, maxOccurrences={})",
                profile.getName(),
                profile.getEnabledRules().size(),
                profile.isMandatoryOnly(),
                profile.getMaxOccurrences());

        XsdSampleDataGenerator sampleGenerator = new XsdSampleDataGenerator();
        setupTypeResolver(sampleGenerator, data);
        ValueStrategyFactory strategyFactory = new ValueStrategyFactory(sampleGenerator);
        GenerationContext context = new GenerationContext();

        return buildXmlDocument(profile, data, xsdFilePath, strategyFactory, context);
    }

    /**
     * Generates multiple XML documents in batch mode.
     *
     * <p>When the profile has no enabled non-AUTO rules, each batch entry is produced via
     * the plain generator (see {@link #generate(GenerationProfile, XsdDocumentationData, String)}).</p>
     *
     * @param profile     the generation profile with rules, batch count, and file name pattern
     * @param data        the parsed XSD documentation data
     * @param xsdFilePath the path to the XSD file
     * @return list of generated files with names and content
     */
    public List<GeneratedFile> generateBatch(GenerationProfile profile, XsdDocumentationData data, String xsdFilePath) {
        boolean delegate = hasNoEffectiveRules(profile);
        XsdSampleDataGenerator sampleGenerator = null;
        ValueStrategyFactory strategyFactory = null;
        GenerationContext context = null;

        if (!delegate) {
            sampleGenerator = new XsdSampleDataGenerator();
            setupTypeResolver(sampleGenerator, data);
            strategyFactory = new ValueStrategyFactory(sampleGenerator);
            context = new GenerationContext();
        }

        List<GeneratedFile> files = new ArrayList<>();
        int count = Math.max(1, profile.getBatchCount());
        logger.info("Generating batch of {} files for profile '{}' ({} mode)",
                count, profile.getName(), delegate ? "plain-delegation" : "profiled");

        for (int i = 0; i < count; i++) {
            String content;
            if (delegate) {
                content = delegateToPlainGenerator(profile, xsdFilePath);
            } else {
                if (i > 0) {
                    context.resetForNewFile();
                }
                content = buildXmlDocument(profile, data, xsdFilePath, strategyFactory, context);
            }
            String fileName = resolveFileName(profile.getFileNamePattern(), i + 1);
            files.add(new GeneratedFile(fileName, content));
        }

        return files;
    }

    /**
     * Returns {@code true} if the profile has no rules that would affect generation,
     * i.e. the rules list is empty or every enabled rule uses
     * {@link GenerationStrategy#AUTO} <em>and</em> carries no structural config such
     * as a {@code maxOccurrences} override. In this case the profiled generator should
     * delegate to the plain generator to avoid structural divergence.
     *
     * <p>AUTO rules that specify {@code maxOccurrences} DO affect generation (they
     * change repeat counts) so they must keep the profiled path active.</p>
     */
    static boolean hasNoEffectiveRules(GenerationProfile profile) {
        if (profile == null) {
            return true;
        }
        return profile.getEnabledRules().stream()
                .allMatch(r -> r.getStrategy() == GenerationStrategy.AUTO && !hasStructuralConfig(r));
    }

    private static boolean hasStructuralConfig(XPathRule rule) {
        Map<String, String> cfg = rule == null ? null : rule.getConfig();
        return cfg != null && cfg.get("maxOccurrences") != null;
    }

    /**
     * Delegates XML generation to {@link XsdDocumentationService#generateSampleXml(boolean, int)}
     * using the profile's mandatoryOnly and maxOccurrences settings. The plain generator
     * performs its own XSD parsing internally, so no shared state with the profiled path
     * is required.
     *
     * <p>Visible for testing so subclasses can verify that delegation occurred.</p>
     */
    String delegateToPlainGenerator(GenerationProfile profile, String xsdFilePath) {
        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFilePath);
        return docService.generateSampleXml(profile.isMandatoryOnly(), profile.getMaxOccurrences());
    }

    /**
     * Extracts all XPaths from the parsed XSD data for auto-populating the rules table.
     *
     * @param data the parsed XSD documentation data
     * @return list of XPath info records
     */
    public List<XPathInfo> extractXPaths(XsdDocumentationData data) {
        List<XPathInfo> xpaths = new ArrayList<>();
        Map<String, XsdExtendedElement> elementMap = data.getExtendedXsdElementMap();

        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            XsdExtendedElement element = entry.getValue();
            String xpath = entry.getKey();
            String name = element.getElementName();

            // Skip structural containers
            if (name == null || name.startsWith("SEQUENCE") || name.startsWith("CHOICE") || name.startsWith("ALL")) {
                continue;
            }

            boolean isAttribute = name.startsWith("@");
            String typeName = element.getElementType() != null ? element.getElementType() : "";
            String cleanXpath = stripContainers(xpath);

            xpaths.add(new XPathInfo(cleanXpath, typeName, element.isMandatory(), isAttribute, element.getCounter()));
        }

        // Sort by schema order (counter) to preserve XSD document order
        xpaths.sort(Comparator.comparing(XPathInfo::schemaOrder));
        return xpaths;
    }

    // ---- Internal XML building ----

    private String buildXmlDocument(GenerationProfile profile, XsdDocumentationData data,
                                     String xsdFilePath, ValueStrategyFactory strategyFactory,
                                     GenerationContext context) {
        Map<String, XsdExtendedElement> elementMap = data.getExtendedXsdElementMap();

        List<XsdExtendedElement> rootElements = elementMap.values().stream()
                .filter(e -> e.getParentXpath() == null || e.getParentXpath().equals("/"))
                .sorted(Comparator.comparing(XsdExtendedElement::getCounter))
                .toList();

        if (rootElements.isEmpty()) {
            return "<!-- No root element found in XSD -->";
        }

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        XsdExtendedElement rootElement = rootElements.getFirst();
        String rootName = rootElement.getElementName();

        // Schema location and namespace declarations
        String targetNamespace = data.getTargetNamespace();
        String schemaLocationUri = new File(xsdFilePath).toURI().toString();

        xml.append("<").append(rootName)
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");

        if (targetNamespace != null && !targetNamespace.isBlank()) {
            xml.append(" xmlns=\"").append(targetNamespace).append("\"")
                    .append(" xsi:schemaLocation=\"")
                    .append(targetNamespace).append(" ")
                    .append(schemaLocationUri).append("\"");
        } else {
            xml.append(" xsi:noNamespaceSchemaLocation=\"")
                    .append(schemaLocationUri).append("\"");
        }

        // Collect used namespaces
        Map<String, String> usedNamespaces = collectUsedNamespaces(rootElement, profile.isMandatoryOnly(), elementMap);
        for (Map.Entry<String, String> ns : usedNamespaces.entrySet()) {
            if (ns.getKey() != null && !ns.getKey().isEmpty() && ns.getValue() != null && !ns.getValue().isEmpty()) {
                xml.append(" xmlns:").append(ns.getKey()).append("=\"").append(ns.getValue()).append("\"");
            }
        }

        // Root element attributes
        List<XPathRule> enabledRules = profile.getEnabledRules();
        IdentityConstraintTracker constraintTracker = new IdentityConstraintTracker();
        constraintTracker.scanConstraints(elementMap);

        List<XsdExtendedElement> rootAttributes = rootElement.getChildren().stream()
                .map(elementMap::get)
                .filter(Objects::nonNull)
                .filter(e -> e.getElementName().startsWith("@"))
                .toList();

        for (XsdExtendedElement attr : rootAttributes) {
            String attrXpath = rootElement.getCurrentXpath() + "/@" + attr.getElementName().substring(1);
            renderAttribute(xml, attr, attrXpath, profile, enabledRules, strategyFactory, context, constraintTracker);
        }

        xml.append(">\n");

        // Root element children
        List<XsdExtendedElement> rootChildren = rootElement.getChildren().stream()
                .map(elementMap::get)
                .filter(Objects::nonNull)
                .filter(e -> !e.getElementName().startsWith("@"))
                .toList();

        for (XsdExtendedElement child : rootChildren) {
            buildElement(xml, child, profile, enabledRules, elementMap, strategyFactory, context, constraintTracker, 1);
        }

        xml.append("</").append(rootName).append(">\n");
        return xml.toString();
    }

    private void buildElement(StringBuilder sb, XsdExtendedElement element, GenerationProfile profile,
                               List<XPathRule> rules, Map<String, XsdExtendedElement> elementMap,
                               ValueStrategyFactory strategyFactory, GenerationContext context,
                               IdentityConstraintTracker constraintTracker, int indentLevel) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("XML generation cancelled");
        }
        if (element == null) {
            return;
        }

        String elementName = element.getElementName();
        if (elementName == null || elementName.startsWith("@")) {
            return;
        }

        String xpath = element.getCurrentXpath();
        boolean mandatoryOnly = profile.isMandatoryOnly();
        int maxOccurrences = profile.getMaxOccurrences();

        // Check if there's a rule for this element
        Optional<XPathRule> matchedRule = matchRule(xpath, rules);

        // Handle OMIT strategy
        if (matchedRule.isPresent() && matchedRule.get().getStrategy() == GenerationStrategy.OMIT) {
            return;
        }

        // Skip optional elements in mandatory-only mode (unless explicitly ruled or
        // an enabled non-AUTO rule targets a descendant — without this lookahead a
        // FIXED rule on /a/b/c would silently never apply because optional ancestor
        // /a would be skipped before the recursion even reached c).
        if (mandatoryOnly && !element.isMandatory() && matchedRule.isEmpty()
                && !hasDescendantRule(xpath, rules)) {
            return;
        }

        // Handle container elements (SEQUENCE, CHOICE, ALL)
        if (elementName.startsWith("SEQUENCE") || elementName.startsWith("ALL")) {
            List<XsdExtendedElement> containerChildren = element.getChildren().stream()
                    .map(elementMap::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                    .toList();
            for (XsdExtendedElement child : containerChildren) {
                buildElement(sb, child, profile, rules, elementMap, strategyFactory, context, constraintTracker, indentLevel);
            }
            return;
        }

        if (elementName.startsWith("CHOICE")) {
            List<XsdExtendedElement> choiceOptions = element.getChildren().stream()
                    .map(elementMap::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                    .toList();
            if (!choiceOptions.isEmpty()) {
                int repeatCount = calculateChoiceRepeatCount(element, mandatoryOnly, maxOccurrences);
                // Prefer options whose subtree contains an enabled non-AUTO rule so users
                // can steer CHOICE selection by adding rules for the option they want
                // (e.g. FIXED rules on Bond/Nominal → generator picks Bond, not Future).
                List<XsdExtendedElement> preferred = choiceOptions.stream()
                        .filter(opt -> hasRuleInSubtree(opt, rules))
                        .toList();
                List<XsdExtendedElement> selectionPool = preferred.isEmpty() ? choiceOptions : preferred;
                for (int i = 0; i < repeatCount; i++) {
                    XsdExtendedElement selected = selectionPool.get(random.nextInt(selectionPool.size()));
                    buildElement(sb, selected, profile, rules, elementMap, strategyFactory, context, constraintTracker, indentLevel);
                }
            }
            return;
        }

        // Skip optional containers that would be empty (unless a descendant rule needs them)
        if (!element.isMandatory() && matchedRule.isEmpty()
                && !hasDescendantRule(xpath, rules)
                && wouldProduceEmptyContainer(element, mandatoryOnly, elementMap)) {
            return;
        }

        // Calculate repeat count for this element; a per-rule maxOccurrences config
        // overrides the profile's global setting for this xpath only.
        int repeatCount = calculateElementRepeatCount(element, maxOccurrences);
        int override = findRepeatOverride(xpath, rules);
        if (override >= 0) {
            repeatCount = override;
        }

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            String qualifiedName = getQualifiedName(element);

            sb.append(indent).append("<").append(qualifiedName);

            // Attributes
            List<XsdExtendedElement> attributes = element.getChildren().stream()
                    .map(elementMap::get)
                    .filter(Objects::nonNull)
                    .filter(e -> e.getElementName().startsWith("@"))
                    .toList();

            List<XsdExtendedElement> childElements = element.getChildren().stream()
                    .map(elementMap::get)
                    .filter(Objects::nonNull)
                    .filter(e -> !e.getElementName().startsWith("@"))
                    .toList();

            for (XsdExtendedElement attr : attributes) {
                String attrXpath = xpath + "/@" + attr.getElementName().substring(1);
                renderAttribute(sb, attr, attrXpath, profile, rules, strategyFactory, context, constraintTracker);
            }

            // Resolve value for this element
            context.setCurrentXPath(xpath);
            String value = resolveElementValue(element, xpath, matchedRule.orElse(null), strategyFactory, context, constraintTracker);

            // Handle NIL sentinel
            if (ValueStrategy.NIL_SENTINEL.equals(value)) {
                sb.append(" xsi:nil=\"true\"/>\n");
                continue;
            }

            if (childElements.isEmpty() && value.isEmpty()) {
                sb.append("/>\n");
            } else {
                sb.append(">").append(escapeXml(value));
                if (!childElements.isEmpty()) {
                    sb.append("\n");
                    for (XsdExtendedElement child : childElements) {
                        buildElement(sb, child, profile, rules, elementMap, strategyFactory, context, constraintTracker, indentLevel + 1);
                    }
                    sb.append(indent);
                }
                sb.append("</").append(qualifiedName).append(">\n");
            }

            // Record generated value for XPATH_REF
            if (!value.isEmpty()) {
                context.recordGeneratedValue(xpath, value);
            }
        }
    }

    private void renderAttribute(StringBuilder sb, XsdExtendedElement attr, String attrXpath,
                                  GenerationProfile profile, List<XPathRule> rules,
                                  ValueStrategyFactory strategyFactory, GenerationContext context,
                                  IdentityConstraintTracker constraintTracker) {
        Optional<XPathRule> attrRule = matchRule(attrXpath, rules);

        // Handle OMIT for attributes
        if (attrRule.isPresent() && attrRule.get().getStrategy() == GenerationStrategy.OMIT) {
            return;
        }

        // Skip optional attributes in mandatory-only mode (unless explicitly ruled)
        String fixedOrDefault = getAttributeValue(attr.getCurrentNode(), "fixed",
                getAttributeValue(attr.getCurrentNode(), "default", null));
        if (profile.isMandatoryOnly() && !attr.isMandatory() && fixedOrDefault == null && attrRule.isEmpty()) {
            return;
        }

        String attrName = attr.getElementName().substring(1);
        String attrValue;

        if (attrRule.isPresent() && attrRule.get().getStrategy() != GenerationStrategy.AUTO) {
            context.setCurrentXPath(attrXpath);
            ValueStrategy strategy = strategyFactory.forStrategy(attrRule.get().getStrategy());
            attrValue = strategy.resolve(attr, attrRule.get().getConfig(), context);
        } else if (fixedOrDefault != null) {
            attrValue = fixedOrDefault;
        } else {
            attrValue = attr.getDisplaySampleData() != null ? attr.getDisplaySampleData() : "";
        }

        // Apply constraint tracking
        if (constraintTracker != null && fixedOrDefault == null) {
            if (constraintTracker.isConstrainedField(attrXpath) || constraintTracker.isKeyrefField(attrXpath)) {
                attrValue = constraintTracker.getUniqueValue(attrXpath, attrValue, attr);
            }
        }

        sb.append(" ").append(attrName).append("=\"").append(escapeXml(attrValue)).append("\"");

        // Record for XPATH_REF
        if (!attrValue.isEmpty()) {
            context.recordGeneratedValue(attrXpath, attrValue);
        }
    }

    private String resolveElementValue(XsdExtendedElement element, String xpath, XPathRule rule,
                                        ValueStrategyFactory strategyFactory, GenerationContext context,
                                        IdentityConstraintTracker constraintTracker) {
        String value;

        if (rule != null && rule.getStrategy() != GenerationStrategy.AUTO) {
            ValueStrategy strategy = strategyFactory.forStrategy(rule.getStrategy());
            value = strategy.resolve(element, rule.getConfig(), context);
        } else {
            // Prefer the schema's fixed/default if present (those must stay constant),
            // otherwise ask AutoValueStrategy for a fresh sample so values vary across
            // repeat iterations instead of all positions showing the same amount.
            String fixedOrDefault = getAttributeValue(element.getCurrentNode(), "fixed",
                    getAttributeValue(element.getCurrentNode(), "default", null));
            if (fixedOrDefault != null) {
                value = fixedOrDefault;
            } else {
                ValueStrategy autoStrategy = strategyFactory.forStrategy(GenerationStrategy.AUTO);
                value = autoStrategy.resolve(element, java.util.Collections.emptyMap(), context);
            }
        }

        // Apply constraint tracking
        if (constraintTracker != null && !value.isEmpty()) {
            if (constraintTracker.isConstrainedField(xpath) || constraintTracker.isKeyrefField(xpath)) {
                value = constraintTracker.getUniqueValue(xpath, value, element);
            }
        }

        return value;
    }

    // ---- XPath matching ----

    /**
     * Matches a current XPath against the list of rules.
     * Priority order: highest priority value wins; on tie, most specific path wins.
     *
     * <p>{@link #stripContainers(String)} is called once per invocation and the result is
     * threaded into {@link #xpathMatches(String, String, String)} so each rule check does
     * not re-strip the same path.</p>
     */
    Optional<XPathRule> matchRule(String currentXPath, List<XPathRule> rules) {
        if (currentXPath == null || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }

        String stripped = stripContainers(currentXPath);

        XPathRule bestMatch = null;
        int bestSpecificity = -1;

        for (XPathRule rule : rules) {
            if (!rule.isEnabled() || rule.getStrategy() == GenerationStrategy.AUTO) {
                continue;
            }
            if (xpathMatches(currentXPath, stripped, rule.getXpath())) {
                int specificity = calculateSpecificity(rule.getXpath());
                if (bestMatch == null
                        || rule.getPriority() > bestMatch.getPriority()
                        || (rule.getPriority() == bestMatch.getPriority() && specificity > bestSpecificity)) {
                    bestMatch = rule;
                    bestSpecificity = specificity;
                }
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * Looks up a per-rule {@code maxOccurrences} override for the given element XPath.
     * Any enabled rule (including AUTO, since the override is structural rather than
     * value-generating) whose XPath matches the current element and whose config
     * carries a numeric {@code maxOccurrences} key wins. Returns {@code -1} when no
     * override applies and the caller should keep the profile's global count.
     */
    static int findRepeatOverride(String currentXPath, List<XPathRule> rules) {
        if (currentXPath == null || rules == null || rules.isEmpty()) {
            return -1;
        }
        String stripped = stripContainers(currentXPath);
        for (XPathRule rule : rules) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            Map<String, String> cfg = rule.getConfig();
            if (cfg == null) {
                continue;
            }
            String override = cfg.get("maxOccurrences");
            if (override == null) {
                continue;
            }
            if (!xpathMatches(currentXPath, stripped, rule.getXpath())) {
                continue;
            }
            try {
                int n = Integer.parseInt(override.trim());
                if (n >= 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // Invalid number — fall through and try next rule.
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if any enabled non-AUTO rule targets a descendant of the
     * given element (i.e. some rule's xpath has the element's xpath as a strict prefix).
     * Used to keep optional ancestors in the output when a deeper rule would otherwise
     * silently never apply because its container was skipped first.
     *
     * <p>Descendant patterns ({@code //foo}) are excluded from this lookahead because
     * we cannot tell from the rule alone whether the target lives under this subtree.</p>
     */
    static boolean hasDescendantRule(String elementXpath, List<XPathRule> rules) {
        if (elementXpath == null || rules == null || rules.isEmpty()) {
            return false;
        }
        String prefix = stripContainers(elementXpath) + "/";
        for (XPathRule rule : rules) {
            if (rule == null || !rule.isEnabled() || rule.getStrategy() == GenerationStrategy.AUTO) {
                continue;
            }
            String ruleXpath = rule.getXpath();
            if (ruleXpath == null || ruleXpath.startsWith("//")) {
                continue;
            }
            if (ruleXpath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any enabled non-AUTO rule targets this element or anything
     * in its subtree. Used to steer CHOICE selection — the option with rules in its
     * subtree is the one the user wants, so we prefer it over random selection.
     *
     * <p>Unlike {@link #hasDescendantRule} this also honours {@code //name} descendant
     * patterns: a rule like {@code //Bond/Nominal} is considered to match the {@code Bond}
     * CHOICE option even though we don't know the full ancestor path.</p>
     */
    static boolean hasRuleInSubtree(XsdExtendedElement element, List<XPathRule> rules) {
        if (element == null || rules == null || rules.isEmpty()) {
            return false;
        }
        String xpath = element.getCurrentXpath();
        String name = element.getElementName();
        if (xpath == null && name == null) {
            return false;
        }
        String stripped = xpath != null ? stripContainers(xpath) : null;
        String prefix = stripped != null ? stripped + "/" : null;
        for (XPathRule rule : rules) {
            if (rule == null || !rule.isEnabled() || rule.getStrategy() == GenerationStrategy.AUTO) {
                continue;
            }
            String ruleXpath = rule.getXpath();
            if (ruleXpath == null) {
                continue;
            }
            if (ruleXpath.startsWith("//")) {
                // Descendant pattern: consider it a match if the first segment after //
                // is the element name or appears anywhere in the path to the target.
                if (name != null) {
                    String tail = ruleXpath.substring(2); // strip leading //
                    String firstSegment = tail.contains("/") ? tail.substring(0, tail.indexOf('/')) : tail;
                    if (name.equals(firstSegment)) {
                        return true;
                    }
                }
                continue;
            }
            if (stripped != null && (ruleXpath.equals(stripped) || ruleXpath.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Backwards-compatible single-arg form for tests and external callers. Computes
     * {@code stripped} on the fly; prefer {@link #xpathMatches(String, String, String)}
     * inside the rule loop where {@code stripped} can be computed once.
     */
    static boolean xpathMatches(String currentXPath, String ruleXPath) {
        if (currentXPath == null || ruleXPath == null) {
            return false;
        }
        return xpathMatches(currentXPath, stripContainers(currentXPath), ruleXPath);
    }

    /**
     * Checks if a current XPath matches a rule XPath pattern.
     * Supports exact match, wildcard {@code [*]}, and descendant {@code //} patterns.
     * Structural containers (SEQUENCE_N, CHOICE_N, ALL_N) in the current XPath
     * are stripped before matching so users don't need to know about them.
     *
     * <p>For exact-path rules and descendant rules, no regex is compiled. For wildcard
     * rules the compiled {@link Pattern} is cached in {@link #WILDCARD_PATTERN_CACHE}
     * to avoid repeated compilation.</p>
     */
    static boolean xpathMatches(String currentXPath, String stripped, String ruleXPath) {
        if (currentXPath == null || ruleXPath == null) {
            return false;
        }

        // Exact match (against stripped path)
        if (stripped.equals(ruleXPath) || currentXPath.equals(ruleXPath)) {
            return true;
        }

        // Descendant match: //name matches any element with that local name
        if (ruleXPath.startsWith("//")) {
            String suffix = ruleXPath.substring(1); // Remove one /, keep /name
            return stripped.endsWith(suffix)
                    || stripped.contains(suffix + "/")
                    || stripped.contains(suffix + "[");
        }

        // No wildcard and no exact match → no possible match (skip regex compilation entirely)
        if (!ruleXPath.contains("[*]")) {
            return false;
        }

        // Wildcard match: /order/item[*]/sku matches /order/item[1]/sku, etc.
        Pattern pattern = WILDCARD_PATTERN_CACHE.computeIfAbsent(ruleXPath, rx -> {
            String regex = WILDCARD_INDEX.matcher(Pattern.quote(rx))
                    .replaceAll("\\\\E\\\\[\\\\d+\\\\]\\\\Q");
            regex = regex.replace("\\Q\\E", "");
            return Pattern.compile(regex);
        });
        return pattern.matcher(stripped).matches() || pattern.matcher(currentXPath).matches();
    }

    /**
     * Strips structural container segments (SEQUENCE_N, CHOICE_N, ALL_N)
     * from an XPath so users can write clean paths like /order/customer/name
     * instead of /order/SEQUENCE_1/customer/SEQUENCE_2/name.
     */
    static String stripContainers(String xpath) {
        if (xpath == null) return null;
        return CONTAINER_SEGMENT.matcher(xpath).replaceAll("");
    }

    /**
     * Calculates specificity of a rule XPath. Higher = more specific.
     * Exact paths are more specific than wildcards, which are more specific than descendants.
     */
    private static int calculateSpecificity(String ruleXPath) {
        if (ruleXPath.startsWith("//")) {
            return 1; // Least specific
        }
        if (ruleXPath.contains("[*]")) {
            return 2; // Medium specificity
        }
        return 3; // Exact match - most specific
    }

    // ---- Helper methods ----

    private Map<String, String> collectUsedNamespaces(XsdExtendedElement element, boolean mandatoryOnly,
                                                       Map<String, XsdExtendedElement> elementMap) {
        Map<String, String> namespaces = new HashMap<>();
        collectNamespacesRecursive(element, mandatoryOnly, namespaces, new HashSet<>(), elementMap);
        return namespaces;
    }

    private void collectNamespacesRecursive(XsdExtendedElement element, boolean mandatoryOnly,
                                             Map<String, String> namespaces, Set<String> visited,
                                             Map<String, XsdExtendedElement> elementMap) {
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }
        String xpath = element.getCurrentXpath();
        if (xpath == null || visited.contains(xpath)) {
            return;
        }
        visited.add(xpath);

        String prefix = element.getSourceNamespacePrefix();
        String ns = element.getSourceNamespace();
        if (prefix != null && !prefix.isEmpty() && ns != null && !ns.isEmpty()) {
            namespaces.put(prefix, ns);
        }

        List<XsdExtendedElement> children = element.getChildren().stream()
                .map(elementMap::get)
                .filter(Objects::nonNull)
                .toList();
        for (XsdExtendedElement child : children) {
            collectNamespacesRecursive(child, mandatoryOnly, namespaces, visited, elementMap);
        }
    }

    private void setupTypeResolver(XsdSampleDataGenerator generator, XsdDocumentationData data) {
        Map<String, XsdExtendedElement> elementMap = data.getExtendedXsdElementMap();
        generator.setTypeResolver(typeName -> {
            for (XsdExtendedElement elem : elementMap.values()) {
                if (typeName.equals(elem.getReferencedTypeName()) || typeName.equals(elem.getElementType())) {
                    String baseType = elem.getElementType();
                    var restriction = elem.getRestrictionInfo();
                    if (baseType != null) {
                        return new XsdSampleDataGenerator.ResolvedType(baseType, restriction);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Mirrors {@code XsdDocumentationService.processChildElementsForGeneration}'s CHOICE
     * repeat logic so the profiled generator produces the same shape as the plain one.
     * In non-mandatory mode an optional CHOICE (minOccurs=0) is randomly skipped or
     * generated up to its bounded maximum; in mandatory mode it's emitted exactly
     * minOccurs times. Without this alignment the profiled path emitted at least one
     * instance of every optional CHOICE which inflated output by an order of magnitude.
     */
    private int calculateChoiceRepeatCount(XsdExtendedElement element, boolean mandatoryOnly, int maxOccurrences) {
        Node node = element.getCurrentNode();
        String minOccursStr = getAttributeValue(node, "minOccurs", "1");
        String maxOccursStr = getAttributeValue(node, "maxOccurs", "1");

        int minOccurs;
        try {
            minOccurs = Integer.parseInt(minOccursStr);
        } catch (NumberFormatException e) {
            minOccurs = 1;
        }

        int choiceMaxOccurs;
        if ("unbounded".equalsIgnoreCase(maxOccursStr)) {
            choiceMaxOccurs = maxOccurrences;
        } else {
            try {
                choiceMaxOccurs = Math.min(Integer.parseInt(maxOccursStr), maxOccurrences);
            } catch (NumberFormatException e) {
                choiceMaxOccurs = 1;
            }
        }

        if (mandatoryOnly && minOccurs == 0) {
            return 0;
        }
        if (mandatoryOnly) {
            return minOccurs;
        }

        int effectiveMax = Math.min(choiceMaxOccurs, maxOccurrences);
        if (minOccurs >= effectiveMax) {
            return effectiveMax;
        }
        return minOccurs + random.nextInt(effectiveMax - minOccurs + 1);
    }

    private int calculateElementRepeatCount(XsdExtendedElement element, int maxOccurrences) {
        String maxOccurs = getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
        if ("1".equals(maxOccurs)) {
            return 1;
        }
        if ("unbounded".equalsIgnoreCase(maxOccurs)) {
            return maxOccurrences;
        }
        try {
            return Math.min(Integer.parseInt(maxOccurs), maxOccurrences);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Determines whether rendering this element would produce no content
     * (no text and no rendered children). Mirrors
     * {@code XsdDocumentationService.wouldProduceEmptyContainer} so the profiled
     * generator agrees with the plain generator on which optional elements to
     * skip — without this alignment the profiled path emits empty containers
     * like {@code <BreakDowns/>} that the plain path correctly omits, producing
     * thousands of {@code "content of element X is not complete"} validation
     * errors against schemas like FundsXML.
     *
     * <p>Recurses through structural containers (SEQUENCE, CHOICE, ALL) and
     * treats external-namespace references (e.g. {@code ds:Signature}) as
     * non-empty so they remain in the output.</p>
     */
    private boolean wouldProduceEmptyContainer(XsdExtendedElement element, boolean mandatoryOnly,
                                                Map<String, XsdExtendedElement> elementMap) {
        if (element == null) {
            return true;
        }

        // External namespace references render even without local children.
        if (element.isExternalNamespaceReference()) {
            return false;
        }

        // Has text content → not empty.
        String sampleData = element.getDisplaySampleData();
        if (sampleData != null && !sampleData.isEmpty()) {
            return false;
        }

        List<XsdExtendedElement> childElements = element.getChildren().stream()
                .map(elementMap::get)
                .filter(Objects::nonNull)
                .filter(e -> e.getElementName() != null && !e.getElementName().startsWith("@"))
                .toList();

        if (childElements.isEmpty()) {
            // No element children — only rendered if the element carries attributes.
            boolean hasAttributes = element.getChildren().stream()
                    .map(elementMap::get)
                    .filter(Objects::nonNull)
                    .anyMatch(e -> e.getElementName() != null && e.getElementName().startsWith("@"));
            return !hasAttributes;
        }

        for (XsdExtendedElement child : childElements) {
            String childName = child.getElementName();
            if (childName == null) {
                continue;
            }

            if (childName.startsWith("SEQUENCE") || childName.startsWith("ALL") || childName.startsWith("CHOICE")) {
                if (!wouldProduceEmptyContainer(child, mandatoryOnly, elementMap)) {
                    return false;
                }
            } else if (!mandatoryOnly || child.isMandatory()) {
                // A non-container child that would actually be rendered.
                return false;
            }
        }

        return true;
    }

    private String getQualifiedName(XsdExtendedElement element) {
        String name = element.getElementName();
        String prefix = element.getSourceNamespacePrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + name;
        }
        return name;
    }

    String resolveFileName(String pattern, int fileNumber) {
        if (pattern == null || pattern.isBlank()) {
            return "example_" + fileNumber + ".xml";
        }
        // Replace {seq:N} with zero-padded file number
        return SequenceValueStrategy_replaceSequencePlaceholders(pattern, fileNumber);
    }

    private String SequenceValueStrategy_replaceSequencePlaceholders(String pattern, int value) {
        java.util.regex.Matcher matcher = Pattern.compile("\\{seq(?::(\\d+))?}").matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String widthStr = matcher.group(1);
            int width = widthStr != null ? Integer.parseInt(widthStr) : 0;
            String replacement = width > 0
                    ? String.format("%0" + width + "d", value)
                    : String.valueOf(value);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        if (result.toString().equals(pattern)) {
            return pattern; // No placeholders found, use as-is
        }
        return result.toString();
    }

    private static String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) {
            return defaultValue;
        }
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return attrNode != null ? attrNode.getNodeValue() : defaultValue;
    }

    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
