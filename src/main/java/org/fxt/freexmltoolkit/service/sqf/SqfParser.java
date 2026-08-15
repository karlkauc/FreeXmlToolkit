package org.fxt.freexmltoolkit.service.sqf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SaxonXPathHelper;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.AssertKey;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfActivity;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfAdd;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCallFix;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfDelete;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfFix;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfLet;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfParam;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfReplace;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfStringReplace;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfUserEntry;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * Parses the SQF (Schematron Quick Fix) content out of a Schematron file into an
 * {@link SqfCatalog}: fix definitions (schema-global {@code sqf:fixes} and
 * rule-local {@code sqf:fix}), {@code sqf:group} wrappers, {@code sch:let} scopes
 * and the assert/report → fix references ({@code @sqf:fix}, {@code @sqf:default-fix}).
 *
 * <p>{@code sch:include} is resolved recursively (relative to the including file,
 * depth-limited, cycle-guarded); same-document {@code sch:extends @rule} of abstract
 * rules is merged into the extending rule. External {@code sch:extends href} and
 * abstract patterns ({@code is-a}) are not resolved for fix discovery — findings
 * from such rules simply get no fixes.
 *
 * <p>Fixes without an {@code @id} and references to unknown ids are skipped
 * (logged), never fatal: validation must not break because of broken SQF content.
 */
public final class SqfParser {

    private static final Logger logger = LogManager.getLogger(SqfParser.class);
    private static final int MAX_INCLUDE_DEPTH = 10;

    private SqfParser() {
    }

    /**
     * @param schematronFile the Schematron file to scan for SQF content
     * @return the parsed catalog (empty when the file has no usable fixes)
     * @throws SqfParseException when the file is not well-formed XML
     */
    public static SqfCatalog parse(File schematronFile) throws SqfParseException {
        XdmNode root = rootElement(schematronFile);
        if (root == null || !isSch(root, "schema")) {
            return SqfCatalog.empty(schematronFile);
        }
        Walk walk = new Walk(schematronFile);
        walk.processSchema(root, schematronFile, 0, new HashSet<>());
        return walk.toCatalog();
    }

    private static XdmNode rootElement(File file) throws SqfParseException {
        try {
            DocumentBuilder builder = SaxonXPathHelper.getProcessor().newDocumentBuilder();
            XdmNode doc = builder.build(file);
            for (XdmNode child : childElements(doc)) {
                return child;
            }
            return null;
        } catch (Exception e) {
            throw new SqfParseException("Cannot parse Schematron file "
                    + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    // ---- walking state ------------------------------------------------------

    private static final class FixDraft {
        String key;
        String id;
        String title;
        List<String> paragraphs = new ArrayList<>();
        String useWhen;
        List<SqfLet> letsInScope = new ArrayList<>();
        List<SqfParam> params = new ArrayList<>();
        List<SqfUserEntry> userEntries = new ArrayList<>();
        List<SqfActivity> activities = new ArrayList<>();
        Map<String, String> localScope; // the rule-local id→key map, or null for globals
    }

    private static final class PendingAssert {
        String assertId;
        AssertKey key;
        List<String> refIds;
        String defaultFixId;
        Map<String, String> localScope;
    }

    private static final class Walk {
        final File schematronFile;
        final Map<String, String> namespaces = new LinkedHashMap<>();
        final List<FixDraft> fixes = new ArrayList<>();
        final Map<String, String> globalScope = new LinkedHashMap<>();
        final List<PendingAssert> pendingAsserts = new ArrayList<>();
        final List<SqfLet> schemaLets = new ArrayList<>();
        int keyCounter;

        Walk(File schematronFile) {
            this.schematronFile = schematronFile;
        }

        void processSchema(XdmNode schema, File baseFile, int depth, Set<String> includeStack) {
            for (XdmNode child : childElements(schema)) {
                if (isSch(child, "ns")) {
                    String prefix = attr(child, "prefix");
                    String uri = attr(child, "uri");
                    if (prefix != null && uri != null) {
                        namespaces.put(prefix, uri);
                    }
                } else if (isSch(child, "let")) {
                    addLet(schemaLets, child);
                } else if (isSqf(child, "fixes")) {
                    processFixContainer(child, null, schemaLets, null);
                } else if (isSch(child, "pattern")) {
                    processPattern(child, baseFile, depth, includeStack);
                } else if (isSch(child, "include")) {
                    resolveInclude(child, baseFile, depth, includeStack, root -> {
                        if (isSch(root, "schema")) {
                            processSchema(root, includedFile(child, baseFile), depth + 1, includeStack);
                        } else if (isSch(root, "pattern")) {
                            processPattern(root, includedFile(child, baseFile), depth + 1, includeStack);
                        } else if (isSqf(root, "fixes")) {
                            processFixContainer(root, null, schemaLets, null);
                        }
                    });
                }
            }
        }

        void processPattern(XdmNode pattern, File baseFile, int depth, Set<String> includeStack) {
            if (attr(pattern, "abstract") != null && "true".equals(attr(pattern, "abstract"))) {
                return; // abstract patterns are not resolved for fix discovery
            }
            List<SqfLet> patternLets = new ArrayList<>(schemaLets);
            // Abstract rules may lexically follow their extenders — index them first.
            Map<String, XdmNode> abstractRules = new HashMap<>();
            for (XdmNode child : childElements(pattern)) {
                if (isSch(child, "rule") && "true".equals(attr(child, "abstract"))
                        && attr(child, "id") != null) {
                    abstractRules.put(attr(child, "id"), child);
                }
            }
            for (XdmNode child : childElements(pattern)) {
                if (isSch(child, "let")) {
                    addLet(patternLets, child);
                } else if (isSch(child, "rule")) {
                    if (!"true".equals(attr(child, "abstract"))) {
                        processRule(child, patternLets, abstractRules);
                    }
                } else if (isSch(child, "include")) {
                    resolveInclude(child, baseFile, depth, includeStack, root -> {
                        if (isSch(root, "rule") && !"true".equals(attr(root, "abstract"))) {
                            processRule(root, patternLets, abstractRules);
                        }
                    });
                }
            }
        }

        void processRule(XdmNode rule, List<SqfLet> patternLets, Map<String, XdmNode> abstractRules) {
            String context = attr(rule, "context");
            if (context == null) {
                return;
            }
            List<SqfLet> ruleLets = new ArrayList<>(patternLets);
            Map<String, String> localScope = new LinkedHashMap<>();
            List<XdmNode> ruleContent = new ArrayList<>();
            collectRuleContent(rule, abstractRules, ruleContent, new HashSet<>());

            for (XdmNode child : ruleContent) {
                if (isSch(child, "let")) {
                    addLet(ruleLets, child);
                }
            }
            for (XdmNode child : ruleContent) {
                if (isSqf(child, "fix")) {
                    processFix(child, null, ruleLets, localScope);
                } else if (isSqf(child, "group")) {
                    processFixContainer(child, attr(child, "use-when"), ruleLets, localScope);
                }
            }
            for (XdmNode child : ruleContent) {
                if (isSch(child, "assert") || isSch(child, "report")) {
                    String refs = child.getAttributeValue(new QName(SqfNames.SQF_NS, "fix"));
                    if (refs == null || refs.isBlank()) {
                        continue;
                    }
                    PendingAssert pending = new PendingAssert();
                    pending.assertId = attr(child, "id");
                    pending.key = AssertKey.of(context, attr(child, "test"));
                    pending.refIds = List.of(refs.trim().split("\\s+"));
                    pending.defaultFixId = child.getAttributeValue(new QName(SqfNames.SQF_NS, "default-fix"));
                    pending.localScope = localScope;
                    pendingAsserts.add(pending);
                }
            }
        }

        /** Collects the rule's children, inlining same-pattern abstract rules via sch:extends. */
        private void collectRuleContent(XdmNode rule, Map<String, XdmNode> abstractRules,
                                        List<XdmNode> out, Set<String> visited) {
            for (XdmNode child : childElements(rule)) {
                if (isSch(child, "extends")) {
                    String ref = attr(child, "rule");
                    if (ref != null && abstractRules.containsKey(ref) && visited.add(ref)) {
                        collectRuleContent(abstractRules.get(ref), abstractRules, out, visited);
                    }
                } else {
                    out.add(child);
                }
            }
        }

        /** Processes the sqf:fix children of a sqf:fixes / sqf:group container. */
        void processFixContainer(XdmNode container, String groupUseWhen,
                                 List<SqfLet> lets, Map<String, String> localScope) {
            for (XdmNode child : childElements(container)) {
                if (isSqf(child, "fix")) {
                    processFix(child, groupUseWhen, lets, localScope);
                } else if (isSqf(child, "group")) {
                    String nested = combineUseWhen(groupUseWhen, attr(child, "use-when"));
                    processFixContainer(child, nested, lets, localScope);
                }
            }
        }

        void processFix(XdmNode fix, String groupUseWhen, List<SqfLet> lets,
                        Map<String, String> localScope) {
            String id = attr(fix, "id");
            if (id == null || id.isBlank()) {
                logger.warn("Skipping sqf:fix without @id in {}", schematronFile);
                return;
            }
            FixDraft draft = new FixDraft();
            draft.id = id;
            draft.key = "fix-" + (++keyCounter) + ":" + id;
            draft.useWhen = combineUseWhen(groupUseWhen, attr(fix, "use-when"));
            draft.letsInScope = new ArrayList<>(lets);
            draft.localScope = localScope;

            for (XdmNode child : childElements(fix)) {
                if (isSqf(child, "description")) {
                    for (XdmNode d : childElements(child)) {
                        if (isSqf(d, "title")) {
                            draft.title = d.getStringValue().trim();
                        } else if (isSqf(d, "p")) {
                            draft.paragraphs.add(d.getStringValue().trim());
                        }
                    }
                } else if (isSch(child, "let")) {
                    addLet(draft.letsInScope, child);
                } else if (isSqf(child, "param")) {
                    String name = attr(child, "name");
                    if (name != null) {
                        draft.params.add(new SqfParam(name, attr(child, "default"),
                                "yes".equals(attr(child, "required"))));
                    }
                } else if (isSqf(child, "user-entry")) {
                    draft.userEntries.add(parseUserEntry(child));
                } else if (isSqf(child, "add")) {
                    draft.activities.add(new SqfAdd(attr(child, "match"), attr(child, "node-type"),
                            attr(child, "target"), attr(child, "position"), attr(child, "select"),
                            child, attr(child, "use-when")));
                } else if (isSqf(child, "delete")) {
                    draft.activities.add(new SqfDelete(attr(child, "match"), attr(child, "use-when")));
                } else if (isSqf(child, "replace")) {
                    draft.activities.add(new SqfReplace(attr(child, "match"), attr(child, "node-type"),
                            attr(child, "target"), attr(child, "select"), child, attr(child, "use-when")));
                } else if (isSqf(child, "stringReplace")) {
                    draft.activities.add(new SqfStringReplace(attr(child, "match"), attr(child, "regex"),
                            attr(child, "flags"), attr(child, "select"), child, attr(child, "use-when")));
                } else if (isSqf(child, "call-fix")) {
                    Map<String, String> withParams = new LinkedHashMap<>();
                    for (XdmNode p : childElements(child)) {
                        if (isSqf(p, "with-param") && attr(p, "name") != null) {
                            withParams.put(attr(p, "name"), attr(p, "select"));
                        }
                    }
                    draft.activities.add(new SqfCallFix(attr(child, "ref"), withParams,
                            attr(child, "use-when")));
                }
            }
            fixes.add(draft);
            Map<String, String> scope = localScope != null ? localScope : globalScope;
            scope.putIfAbsent(id, draft.key);
        }

        private SqfUserEntry parseUserEntry(XdmNode entry) {
            String name = attr(entry, "name");
            String title = null;
            StringBuilder description = new StringBuilder();
            for (XdmNode child : childElements(entry)) {
                if (isSqf(child, "description")) {
                    for (XdmNode d : childElements(child)) {
                        if (isSqf(d, "title")) {
                            title = d.getStringValue().trim();
                        } else if (isSqf(d, "p")) {
                            if (description.length() > 0) {
                                description.append(' ');
                            }
                            description.append(d.getStringValue().trim());
                        }
                    }
                }
            }
            return new SqfUserEntry(name, title != null ? title : name,
                    description.toString(), attr(entry, "default"));
        }

        private void resolveInclude(XdmNode include, File baseFile, int depth,
                                    Set<String> includeStack,
                                    java.util.function.Consumer<XdmNode> handler) {
            File target = includedFile(include, baseFile);
            if (target == null || depth >= MAX_INCLUDE_DEPTH) {
                return;
            }
            String canonical;
            try {
                canonical = target.getCanonicalPath();
            } catch (Exception e) {
                canonical = target.getAbsolutePath();
            }
            if (!includeStack.add(canonical)) {
                logger.warn("Circular sch:include of {} in {}", target, schematronFile);
                return;
            }
            try {
                XdmNode root = rootElement(target);
                if (root != null) {
                    handler.accept(root);
                }
            } catch (SqfParseException e) {
                logger.warn("Cannot resolve sch:include {} in {}: {}", target, schematronFile, e.getMessage());
            } finally {
                includeStack.remove(canonical);
            }
        }

        private static File includedFile(XdmNode include, File baseFile) {
            String href = attr(include, "href");
            if (href == null || href.isBlank()) {
                return null;
            }
            File target = new File(href);
            if (!target.isAbsolute()) {
                target = new File(baseFile.getParentFile(), href);
            }
            return target.exists() ? target : null;
        }

        private static void addLet(List<SqfLet> lets, XdmNode let) {
            String name = attr(let, "name");
            String value = attr(let, "value");
            if (name != null && value != null) {
                lets.add(new SqfLet(name, value));
            }
        }

        SqfCatalog toCatalog() {
            Map<String, SqfFix> fixesByKey = new LinkedHashMap<>();
            for (FixDraft draft : fixes) {
                Map<String, String> visible = new LinkedHashMap<>(globalScope);
                if (draft.localScope != null) {
                    visible.putAll(draft.localScope);
                }
                fixesByKey.put(draft.key, new SqfFix(draft.key, draft.id,
                        draft.title != null ? draft.title : draft.id,
                        List.copyOf(draft.paragraphs), draft.useWhen,
                        List.copyOf(draft.letsInScope), List.copyOf(draft.params),
                        List.copyOf(draft.userEntries), List.copyOf(draft.activities),
                        Map.copyOf(visible)));
            }

            Map<String, List<String>> byId = new LinkedHashMap<>();
            Map<AssertKey, List<String>> byKey = new LinkedHashMap<>();
            for (PendingAssert pending : pendingAsserts) {
                List<String> keys = new ArrayList<>();
                for (String refId : pending.refIds) {
                    String key = pending.localScope != null ? pending.localScope.get(refId) : null;
                    if (key == null) {
                        key = globalScope.get(refId);
                    }
                    if (key != null) {
                        keys.add(key);
                    } else {
                        logger.warn("Unknown sqf:fix reference '{}' in {}", refId, schematronFile);
                    }
                }
                if (keys.isEmpty()) {
                    continue;
                }
                if (pending.defaultFixId != null) {
                    String defaultKey = pending.localScope != null
                            ? pending.localScope.get(pending.defaultFixId) : null;
                    if (defaultKey == null) {
                        defaultKey = globalScope.get(pending.defaultFixId);
                    }
                    if (defaultKey != null && keys.remove(defaultKey)) {
                        keys.add(0, defaultKey);
                    }
                }
                List<String> immutable = List.copyOf(keys);
                if (pending.assertId != null && !pending.assertId.isBlank()) {
                    byId.putIfAbsent(pending.assertId, immutable);
                }
                byKey.putIfAbsent(pending.key, immutable);
            }
            return new SqfCatalog(schematronFile, Map.copyOf(namespaces),
                    fixesByKey, byId, byKey);
        }
    }

    // ---- node helpers -------------------------------------------------------

    private static boolean isSch(XdmNode node, String localName) {
        return is(node, SqfNames.SCH_NS, localName);
    }

    private static boolean isSqf(XdmNode node, String localName) {
        return is(node, SqfNames.SQF_NS, localName);
    }

    private static boolean is(XdmNode node, String ns, String localName) {
        if (node.getNodeKind() != XdmNodeKind.ELEMENT) {
            return false;
        }
        QName name = node.getNodeName();
        return localName.equals(name.getLocalName()) && ns.equals(name.getNamespaceUri().toString());
    }

    private static String attr(XdmNode element, String name) {
        return element.getAttributeValue(new QName(name));
    }

    private static String combineUseWhen(String outer, String inner) {
        if (outer == null || outer.isBlank()) {
            return inner;
        }
        if (inner == null || inner.isBlank()) {
            return outer;
        }
        return "(" + outer + ") and (" + inner + ")";
    }

    private static Iterable<XdmNode> childElements(XdmNode node) {
        List<XdmNode> children = new ArrayList<>();
        for (XdmSequenceIterator<XdmNode> it = node.axisIterator(Axis.CHILD); it.hasNext(); ) {
            XdmNode child = it.next();
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                children.add(child);
            }
        }
        return children;
    }
}
