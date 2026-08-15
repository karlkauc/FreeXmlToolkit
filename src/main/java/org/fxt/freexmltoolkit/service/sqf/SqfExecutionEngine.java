package org.fxt.freexmltoolkit.service.sqf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.SaxonXPathHelper;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfActivity;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfAdd;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCallFix;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfDelete;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfFix;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfLet;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfReplace;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfStringReplace;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfUserEntry;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.ElementRegion;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Span;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;

/**
 * Computes the text edits for one SQF quick fix: resolves the finding's SVRL
 * location against the <em>current</em> document text (staleness guard), evaluates
 * {@code sch:let} bindings and {@code use-when} conditions, executes the fix's
 * activities and turns every change into a minimal {@link SqfTextEdit} via
 * {@link XmlNodeSpanLocator} — so applying a fix preserves the document's
 * formatting outside the edited spans. The caller applies the edits (sorted
 * descending by start) to the editor text.
 */
public final class SqfExecutionEngine {

    private static final String DEFAULT_CHILD_INDENT = "    ";

    private SqfExecutionEngine() {
    }

    /** One text replacement: replace {@code [start, end)} with {@code replacement}. */
    public record SqfTextEdit(int start, int end, String replacement) {
    }

    /**
     * The full edit plan of one fix application.
     *
     * @param edits    the edits, sorted descending by start offset (safe to apply in order)
     * @param fixTitle the fix's display title (for undo/status messages)
     */
    public record SqfEditPlan(List<SqfTextEdit> edits, String fixTitle) {
    }

    /**
     * @return the user-entry prompts the fix (including fixes it calls via
     *         {@code sqf:call-fix}, recursively) requires before it can run
     */
    public static List<SqfUserEntry> requiredUserEntries(SqfCatalog catalog, SqfFixSuggestion suggestion) {
        SqfFix fix = catalog.fixesByKey().get(suggestion.fixKey());
        if (fix == null) {
            return List.of();
        }
        List<SqfUserEntry> entries = new ArrayList<>();
        collectUserEntries(fix, catalog, new java.util.HashSet<>(), entries);
        return List.copyOf(entries);
    }

    /**
     * Best-effort evaluation of the {@code @default} XPaths of a fix's user entries
     * against the finding's anchor node — the pre-filled values for the prompt dialog.
     */
    public static Map<String, String> defaultUserEntryValues(String documentText, SqfCatalog catalog,
                                                             SqfFixSuggestion suggestion) {
        Map<String, String> defaults = new LinkedHashMap<>();
        try {
            SvrlNodeLocator locator = new SvrlNodeLocator(documentText);
            XdmNode anchor = locator.locate(suggestion.svrlLocation()).orElse(null);
            if (anchor == null) {
                return defaults;
            }
            Evaluation eval = new Evaluation(documentText, catalog.namespaces());
            for (SqfUserEntry entry : requiredUserEntries(catalog, suggestion)) {
                if (entry.defaultXPath() != null) {
                    try {
                        XdmValue value = eval.evaluate(entry.defaultXPath(), anchor);
                        defaults.put(entry.name(),
                                value.size() > 0 ? value.itemAt(0).getStringValue() : "");
                    } catch (SqfExecutionException ignored) {
                        // no default suggestion for this entry
                    }
                }
            }
        } catch (Exception ignored) {
            // defaults are a convenience only
        }
        return defaults;
    }

    private static void collectUserEntries(SqfFix fix, SqfCatalog catalog,
                                           java.util.Set<String> seen, List<SqfUserEntry> out) {
        if (!seen.add(fix.key())) {
            return;
        }
        out.addAll(fix.userEntries());
        for (SqfActivity activity : fix.activities()) {
            if (activity instanceof SqfCallFix callFix) {
                String calleeKey = fix.visibleFixKeysById().get(callFix.ref());
                SqfFix callee = calleeKey != null ? catalog.fixesByKey().get(calleeKey) : null;
                if (callee != null) {
                    collectUserEntries(callee, catalog, seen, out);
                }
            }
        }
    }

    /**
     * @param documentText    the current editor text
     * @param catalog         the SQF catalog the suggestion came from
     * @param suggestion      the chosen fix
     * @param userEntryValues values for the fix's user entries (name → value)
     * @return the edit plan
     * @throws SqfExecutionException when the fix cannot be computed (stale location,
     *                               use-when false, unsupported target, …)
     */
    public static SqfEditPlan computeEdit(String documentText, SqfCatalog catalog,
                                          SqfFixSuggestion suggestion,
                                          Map<String, String> userEntryValues)
            throws SqfExecutionException {
        SqfFix fix = catalog.fixesByKey().get(suggestion.fixKey());
        if (fix == null) {
            throw new SqfExecutionException("Unknown fix: " + suggestion.fixId());
        }

        SvrlNodeLocator locator = new SvrlNodeLocator(documentText);
        XdmNode anchor = locator.locate(suggestion.svrlLocation()).orElseThrow(() ->
                new SqfExecutionException("The document changed since validation — "
                        + "please re-validate and try the fix again."));

        Evaluation eval = new Evaluation(documentText, catalog.namespaces());
        bindUserValues(eval, fix, userEntryValues, anchor);
        for (SqfLet let : fix.letsInScope()) {
            eval.bind(let.name(), eval.evaluate(let.valueXPath(), anchor));
        }
        if (fix.useWhen() != null && !eval.effectiveBoolean(fix.useWhen(), anchor)) {
            throw new SqfExecutionException("Fix '" + fix.title() + "' is not applicable here (use-when).");
        }

        List<SqfTextEdit> edits = new ArrayList<>();
        java.util.Set<String> callStack = new java.util.HashSet<>();
        callStack.add(fix.key());
        executeActivities(fix, anchor, eval, edits, catalog, callStack, userEntryValues);
        if (edits.isEmpty()) {
            throw new SqfExecutionException("Fix '" + fix.title() + "' produced no changes.");
        }
        edits.sort(Comparator.comparingInt(SqfTextEdit::start).reversed());
        for (int i = 1; i < edits.size(); i++) {
            if (edits.get(i).end() > edits.get(i - 1).start()) {
                throw new SqfExecutionException("Fix '" + fix.title() + "' produces overlapping edits.");
            }
        }
        return new SqfEditPlan(List.copyOf(edits), fix.title());
    }

    private static void bindUserValues(Evaluation eval, SqfFix fix,
                                       Map<String, String> userEntryValues, XdmNode anchor)
            throws SqfExecutionException {
        for (SqfUserEntry entry : fix.userEntries()) {
            String value = userEntryValues.get(entry.name());
            if (value == null && entry.defaultXPath() != null) {
                value = eval.evaluate(entry.defaultXPath(), anchor).toString();
            }
            if (value == null) {
                throw new SqfExecutionException("Fix '" + fix.title() + "' needs a value for '"
                        + entry.name() + "'.");
            }
            eval.bind(entry.name(), new XdmAtomicValue(value));
        }
        for (SqfModel.SqfParam param : fix.params()) {
            if (eval.isBound(param.name())) {
                continue; // already supplied via sqf:with-param
            }
            String supplied = userEntryValues.get(param.name());
            if (supplied != null) {
                eval.bind(param.name(), new XdmAtomicValue(supplied));
            } else if (param.defaultXPath() != null) {
                eval.bind(param.name(), eval.evaluate(param.defaultXPath(), anchor));
            } else {
                throw new SqfExecutionException("Fix '" + fix.title()
                        + "' declares parameter '" + param.name() + "' without a default.");
            }
        }
    }

    // ---- activities ---------------------------------------------------------

    private static void executeActivities(SqfFix fix, XdmNode anchor, Evaluation eval,
                                          List<SqfTextEdit> edits, SqfCatalog catalog,
                                          java.util.Set<String> callStack,
                                          Map<String, String> userEntryValues)
            throws SqfExecutionException {
        for (SqfActivity activity : fix.activities()) {
            if (activity.useWhen() != null && !eval.effectiveBoolean(activity.useWhen(), anchor)) {
                continue;
            }
            switch (activity) {
                case SqfDelete delete -> executeDelete(delete, anchor, eval, edits);
                case SqfAdd add -> executeAdd(add, anchor, eval, edits);
                case SqfReplace replace -> executeReplace(replace, anchor, eval, edits);
                case SqfStringReplace sr -> executeStringReplace(sr, anchor, eval, edits);
                case SqfCallFix callFix -> executeCallFix(callFix, fix, anchor, eval, edits,
                        catalog, callStack, userEntryValues);
            }
        }
    }

    /** Inlines a called fix's activities with its {@code sqf:with-param}s bound. */
    private static void executeCallFix(SqfCallFix callFix, SqfFix caller, XdmNode anchor,
                                       Evaluation eval, List<SqfTextEdit> edits, SqfCatalog catalog,
                                       java.util.Set<String> callStack,
                                       Map<String, String> userEntryValues)
            throws SqfExecutionException {
        String calleeKey = caller.visibleFixKeysById().get(callFix.ref());
        SqfFix callee = calleeKey != null ? catalog.fixesByKey().get(calleeKey) : null;
        if (callee == null) {
            throw new SqfExecutionException("sqf:call-fix references unknown fix '"
                    + callFix.ref() + "'.");
        }
        if (!callStack.add(calleeKey)) {
            throw new SqfExecutionException("sqf:call-fix cycle detected at fix '"
                    + callFix.ref() + "'.");
        }
        try {
            Evaluation nested = eval.copyScope();
            // with-params are evaluated in the CALLER's scope, against the anchor
            for (Map.Entry<String, String> param : callFix.withParams().entrySet()) {
                XdmValue value = param.getValue() != null
                        ? eval.evaluate(param.getValue(), anchor)
                        : new XdmAtomicValue("");
                nested.bindValue(param.getKey(), value);
            }
            bindUserValues(nested, callee, userEntryValues, anchor);
            for (SqfLet let : callee.letsInScope()) {
                nested.bind(let.name(), nested.evaluate(let.valueXPath(), anchor));
            }
            if (callee.useWhen() != null && !nested.effectiveBoolean(callee.useWhen(), anchor)) {
                throw new SqfExecutionException("Called fix '" + callee.title()
                        + "' is not applicable here (use-when).");
            }
            executeActivities(callee, anchor, nested, edits, catalog, callStack, userEntryValues);
        } finally {
            callStack.remove(calleeKey);
        }
    }

    private static void executeDelete(SqfDelete delete, XdmNode anchor, Evaluation eval,
                                      List<SqfTextEdit> edits) throws SqfExecutionException {
        for (XdmNode node : eval.matchNodes(delete.match(), anchor)) {
            switch (node.getNodeKind()) {
                case ELEMENT -> {
                    Span span = expandToWholeLine(eval.text, elementRegion(eval.text, node).span());
                    edits.add(new SqfTextEdit(span.start(), span.end(), ""));
                }
                case ATTRIBUTE -> {
                    ElementRegion parent = elementRegion(eval.text, node.getParent());
                    Span span = XmlNodeSpanLocator.attributeSpan(eval.text, parent,
                                    node.getNodeName().getLocalName())
                            .orElseThrow(() -> new SqfExecutionException(
                                    "Cannot locate attribute " + node.getNodeName() + " in the text."));
                    edits.add(new SqfTextEdit(span.start(), span.end(), ""));
                }
                case TEXT -> {
                    Span span = textSegment(eval.text, node);
                    edits.add(new SqfTextEdit(span.start(), span.end(), ""));
                }
                default -> throw new SqfExecutionException(
                        "sqf:delete cannot target " + node.getNodeKind() + " nodes.");
            }
        }
    }

    private static void executeAdd(SqfAdd add, XdmNode anchor, Evaluation eval,
                                   List<SqfTextEdit> edits) throws SqfExecutionException {
        for (XdmNode node : eval.matchNodes(add.match(), anchor)) {
            if (node.getNodeKind() != XdmNodeKind.ELEMENT) {
                throw new SqfExecutionException("sqf:add anchors must be elements, got "
                        + node.getNodeKind() + ".");
            }
            ElementRegion region = elementRegion(eval.text, node);
            String rendered = SqfContentRenderer.render(add.content(), add.select(), node,
                    eval.variables, eval.namespaces);

            String nodeType = add.nodeType() != null ? add.nodeType() : "keep";
            switch (nodeType) {
                case "attribute" -> {
                    String name = eval.evaluateAvt(add.target(), node);
                    String attrText = " " + name + "=\"" + escapeAttributeValue(rendered) + "\"";
                    var existing = XmlNodeSpanLocator.attributeSpan(eval.text, region, name);
                    if (existing.isPresent()) {
                        edits.add(new SqfTextEdit(existing.get().start(), existing.get().end(), attrText));
                    } else {
                        int at = region.selfClosing()
                                ? slashBeforeTagEnd(eval.text, region)
                                : region.startTagEnd();
                        edits.add(new SqfTextEdit(at, at, attrText));
                    }
                }
                default -> {
                    String fragment = wrapFragment(nodeType, add.target(), rendered, eval, node);
                    edits.add(insertion(eval.text, region, add.position(), fragment));
                }
            }
        }
    }

    private static void executeReplace(SqfReplace replace, XdmNode anchor, Evaluation eval,
                                       List<SqfTextEdit> edits) throws SqfExecutionException {
        for (XdmNode node : eval.matchNodes(replace.match(), anchor)) {
            String rendered = SqfContentRenderer.render(replace.content(), replace.select(), node,
                    eval.variables, eval.namespaces);
            String nodeType = replace.nodeType() != null ? replace.nodeType() : "keep";
            switch (node.getNodeKind()) {
                case ELEMENT -> {
                    Span span = elementRegion(eval.text, node).span();
                    String fragment = wrapFragment(nodeType, replace.target(), rendered, eval, node);
                    edits.add(new SqfTextEdit(span.start(), span.end(), fragment));
                }
                case ATTRIBUTE -> {
                    ElementRegion parent = elementRegion(eval.text, node.getParent());
                    String name = replace.target() != null
                            ? eval.evaluateAvt(replace.target(), node)
                            : node.getNodeName().getLocalName();
                    Span span = XmlNodeSpanLocator.attributeSpan(eval.text, parent,
                                    node.getNodeName().getLocalName())
                            .orElseThrow(() -> new SqfExecutionException(
                                    "Cannot locate attribute " + node.getNodeName() + " in the text."));
                    edits.add(new SqfTextEdit(span.start(), span.end(),
                            " " + name + "=\"" + escapeAttributeValue(rendered) + "\""));
                }
                case TEXT -> {
                    Span span = textSegment(eval.text, node);
                    edits.add(new SqfTextEdit(span.start(), span.end(), escapeText(rendered)));
                }
                default -> throw new SqfExecutionException(
                        "sqf:replace cannot target " + node.getNodeKind() + " nodes.");
            }
        }
    }

    private static void executeStringReplace(SqfStringReplace sr, XdmNode anchor, Evaluation eval,
                                             List<SqfTextEdit> edits) throws SqfExecutionException {
        if (sr.regex() == null) {
            throw new SqfExecutionException("sqf:stringReplace requires a regex.");
        }
        for (XdmNode node : eval.matchNodes(sr.match(), anchor)) {
            if (node.getNodeKind() != XdmNodeKind.TEXT) {
                throw new SqfExecutionException("sqf:stringReplace must match text nodes, got "
                        + node.getNodeKind() + ".");
            }
            Span span = textSegment(eval.text, node);
            String newValue = SqfContentRenderer.renderStringReplace(sr.content(), sr.select(),
                    sr.regex(), sr.flags(), node, eval.variables, eval.namespaces);
            edits.add(new SqfTextEdit(span.start(), span.end(), newValue));
        }
    }

    // ---- fragment / span helpers -------------------------------------------

    private static String wrapFragment(String nodeType, String target, String rendered,
                                       Evaluation eval, XdmNode context) throws SqfExecutionException {
        switch (nodeType) {
            case "element" -> {
                String name = eval.evaluateAvt(target, context);
                return rendered.isEmpty()
                        ? "<" + name + "/>"
                        : "<" + name + ">" + rendered + "</" + name + ">";
            }
            case "processing-instruction", "pi" -> {
                String name = eval.evaluateAvt(target, context);
                return "<?" + name + (rendered.isEmpty() ? "" : " " + rendered) + "?>";
            }
            case "comment" -> {
                return "<!--" + rendered + "-->";
            }
            default -> {
                return rendered; // "keep": the content template result as-is
            }
        }
    }

    /** Builds the insertion edit for an element-content position, keeping the layout tidy. */
    private static SqfTextEdit insertion(String xml, ElementRegion region, String position,
                                         String fragment) throws SqfExecutionException {
        String pos = position != null ? position : "first-child";
        switch (pos) {
            case "before" -> {
                int start = region.span().start();
                if (ownLine(xml, start)) {
                    int ls = XmlNodeSpanLocator.lineStart(xml, start);
                    return new SqfTextEdit(ls, ls,
                            XmlNodeSpanLocator.indentationAt(xml, start) + fragment + "\n");
                }
                return new SqfTextEdit(start, start, fragment);
            }
            case "after" -> {
                int end = region.span().end();
                if (ownLine(xml, region.span().start())) {
                    return new SqfTextEdit(end, end,
                            "\n" + XmlNodeSpanLocator.indentationAt(xml, region.span().start()) + fragment);
                }
                return new SqfTextEdit(end, end, fragment);
            }
            case "first-child", "last-child" -> {
                if (region.selfClosing()) {
                    // expand <a/> to <a>fragment</a>
                    String tag = xml.substring(region.span().start(), region.span().end());
                    String name = tagName(tag);
                    String expanded = tag.substring(0, tag.length() - 2).stripTrailing() + ">"
                            + fragment + "</" + name + ">";
                    return new SqfTextEdit(region.span().start(), region.span().end(), expanded);
                }
                boolean multiline = xml.substring(region.contentStart(), region.contentEnd()).indexOf('\n') >= 0;
                String childIndent = childIndent(xml, region);
                if ("first-child".equals(pos)) {
                    int at = region.contentStart();
                    return multiline
                            ? new SqfTextEdit(at, at, "\n" + childIndent + fragment)
                            : new SqfTextEdit(at, at, fragment);
                }
                int at = region.contentEnd();
                if (multiline) {
                    int ls = XmlNodeSpanLocator.lineStart(xml, at);
                    if (xml.substring(ls, at).isBlank()) {
                        return new SqfTextEdit(ls, ls, childIndent + fragment + "\n");
                    }
                    return new SqfTextEdit(at, at, "\n" + childIndent + fragment + "\n"
                            + XmlNodeSpanLocator.indentationAt(xml, region.span().start()));
                }
                return new SqfTextEdit(at, at, fragment);
            }
            default -> throw new SqfExecutionException("Unknown sqf:add position: " + pos);
        }
    }

    private static String childIndent(String xml, ElementRegion region) {
        if (!region.childElementSpans().isEmpty()) {
            return XmlNodeSpanLocator.indentationAt(xml, region.childElementSpans().get(0).start());
        }
        return XmlNodeSpanLocator.indentationAt(xml, region.span().start()) + DEFAULT_CHILD_INDENT;
    }

    private static boolean ownLine(String xml, int offset) {
        int ls = XmlNodeSpanLocator.lineStart(xml, offset);
        return xml.substring(ls, offset).isBlank();
    }

    private static String tagName(String tag) {
        int i = 1;
        while (i < tag.length() && !Character.isWhitespace(tag.charAt(i))
                && tag.charAt(i) != '/' && tag.charAt(i) != '>') {
            i++;
        }
        return tag.substring(1, i);
    }

    private static int slashBeforeTagEnd(String xml, ElementRegion region) {
        int i = region.startTagEnd() - 1;
        while (i > region.span().start() && Character.isWhitespace(xml.charAt(i))) {
            i--;
        }
        return xml.charAt(i) == '/' ? i : region.startTagEnd();
    }

    /** Extends a span to swallow its whole line when only whitespace remains around it. */
    private static Span expandToWholeLine(String xml, Span span) {
        int ls = XmlNodeSpanLocator.lineStart(xml, span.start());
        if (!xml.substring(ls, span.start()).isBlank()) {
            return span;
        }
        int i = span.end();
        while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
            i++;
        }
        if (i < xml.length() && xml.charAt(i) == '\r') {
            i++;
        }
        if (i < xml.length() && xml.charAt(i) == '\n') {
            return new Span(ls, i + 1);
        }
        return span;
    }

    private static ElementRegion elementRegion(String xml, XdmNode element) throws SqfExecutionException {
        return XmlNodeSpanLocator.elementRegion(xml, XmlNodeSpanLocator.pathOf(element))
                .orElseThrow(() -> new SqfExecutionException(
                        "Cannot locate " + element.getNodeName() + " in the document text."));
    }

    /** @return the text segment span of a text node (via its parent's content layout) */
    private static Span textSegment(String xml, XdmNode textNode) throws SqfExecutionException {
        XdmNode parent = textNode.getParent();
        if (parent == null || parent.getNodeKind() != XdmNodeKind.ELEMENT) {
            throw new SqfExecutionException("Text node without element parent is not supported.");
        }
        ElementRegion region = elementRegion(xml, parent);
        List<Span> segments = XmlNodeSpanLocator.textSegmentSpans(xml, region);
        int index = XmlNodeSpanLocator.textNodeIndex(textNode);
        if (index >= segments.size()) {
            throw new SqfExecutionException("Cannot map the text node inside "
                    + parent.getNodeName() + " to the document text (mixed content?).");
        }
        return segments.get(index);
    }

    private static String escapeAttributeValue(String value) {
        // the rendered fragment already escapes & and <; only quotes remain
        return value.replace("\"", "&quot;");
    }

    private static String escapeText(String value) {
        return value; // rendered fragments are already XML-escaped by the serializer
    }

    // ---- XPath evaluation with variable scope -------------------------------

    /** XPath evaluation context: document text, sch:ns bindings and accumulated variables. */
    private static final class Evaluation {
        final String text;
        final Map<String, String> namespaces;
        final Map<QName, XdmValue> variables = new LinkedHashMap<>();

        Evaluation(String text, Map<String, String> namespaces) {
            this.text = text;
            this.namespaces = namespaces;
        }

        void bind(String name, XdmValue value) {
            variables.put(new QName(name), value);
        }

        /** Alias of {@link #bind} for call-fix parameter values. */
        void bindValue(String name, XdmValue value) {
            bind(name, value);
        }

        boolean isBound(String name) {
            return variables.containsKey(new QName(name));
        }

        /** @return a child scope carrying the same document/namespaces and current variables */
        Evaluation copyScope() {
            Evaluation copy = new Evaluation(text, namespaces);
            copy.variables.putAll(variables);
            return copy;
        }

        XdmValue evaluate(String xpath, XdmNode context) throws SqfExecutionException {
            try {
                XPathSelector selector = compile(xpath);
                selector.setContextItem(context);
                return selector.evaluate();
            } catch (SqfExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new SqfExecutionException("Cannot evaluate XPath '" + xpath + "': "
                        + e.getMessage(), e);
            }
        }

        boolean effectiveBoolean(String xpath, XdmNode context) throws SqfExecutionException {
            try {
                XPathSelector selector = compile(xpath);
                selector.setContextItem(context);
                return selector.effectiveBooleanValue();
            } catch (SqfExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new SqfExecutionException("Cannot evaluate condition '" + xpath + "': "
                        + e.getMessage(), e);
            }
        }

        List<XdmNode> matchNodes(String match, XdmNode anchor) throws SqfExecutionException {
            if (match == null || match.isBlank()) {
                return List.of(anchor);
            }
            List<XdmNode> nodes = new ArrayList<>();
            for (XdmItem item : evaluate(match, anchor)) {
                if (item instanceof XdmNode node) {
                    nodes.add(node);
                } else {
                    throw new SqfExecutionException("match='" + match + "' selected a non-node value.");
                }
            }
            if (nodes.isEmpty()) {
                throw new SqfExecutionException("match='" + match + "' selected nothing.");
            }
            return nodes;
        }

        /** Evaluates an attribute value template ({@code {expr}} parts are XPath). */
        String evaluateAvt(String avt, XdmNode context) throws SqfExecutionException {
            if (avt == null) {
                throw new SqfExecutionException("Missing target name.");
            }
            if (avt.indexOf('{') < 0) {
                return avt.replace("}}", "}");
            }
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < avt.length()) {
                char c = avt.charAt(i);
                if (c == '{') {
                    if (i + 1 < avt.length() && avt.charAt(i + 1) == '{') {
                        out.append('{');
                        i += 2;
                        continue;
                    }
                    int close = avt.indexOf('}', i + 1);
                    if (close < 0) {
                        throw new SqfExecutionException("Unclosed '{' in value template: " + avt);
                    }
                    XdmValue value = evaluate(avt.substring(i + 1, close), context);
                    out.append(value.size() > 0 ? value.itemAt(0).getStringValue() : "");
                    i = close + 1;
                } else if (c == '}') {
                    if (i + 1 < avt.length() && avt.charAt(i + 1) == '}') {
                        out.append('}');
                        i += 2;
                    } else {
                        i++;
                    }
                } else {
                    out.append(c);
                    i++;
                }
            }
            return out.toString();
        }

        private XPathSelector compile(String xpath) throws Exception {
            XPathCompiler compiler = SaxonXPathHelper.getProcessor().newXPathCompiler();
            for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                compiler.declareNamespace(ns.getKey(), ns.getValue());
            }
            for (QName variable : variables.keySet()) {
                compiler.declareVariable(variable);
            }
            XPathSelector selector = compiler.compile(xpath).load();
            for (Map.Entry<QName, XdmValue> variable : variables.entrySet()) {
                selector.setVariable(variable.getKey(), variable.getValue());
            }
            return selector;
        }
    }
}
