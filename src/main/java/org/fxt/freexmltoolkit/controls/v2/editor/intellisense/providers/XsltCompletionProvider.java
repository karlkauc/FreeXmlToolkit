package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.StartTagAttributes;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlNamespaceDeclarations;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XsltElementCatalog.XsltAttribute;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XsltElementCatalog.XsltElement;

/**
 * Completion provider for XSLT documents, backed by the XSLT 3.0 {@link XsltElementCatalog}.
 *
 * <p>Attribute names and values of {@code xsl:*} elements always come from here. Element
 * names come from here only when no schema is bound; otherwise the higher-priority
 * {@link XsdCompletionProvider} answers them from the auto-bound XSLT schema.</p>
 *
 * <p>XSLT elements are recognised by the prefix the document binds to the XSLT namespace,
 * not by a hard-coded {@code xsl:}.</p>
 */
public class XsltCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(XsltCompletionProvider.class);

    /** Elements that are only allowed as children of xsl:stylesheet / xsl:transform / xsl:package. */
    private static final Set<String> DECLARATIONS = Set.of(
            "accumulator", "attribute-set", "character-map", "decimal-format", "function", "global-context-item",
            "import", "import-schema", "include", "key", "mode", "namespace-alias", "output", "param",
            "preserve-space", "strip-space", "template", "use-package", "variable", "expose");

    private static final Set<String> ROOT_ELEMENTS = Set.of("stylesheet", "transform", "package");

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        return mode == EditorMode.XSLT;
    }

    @Override
    public List<CompletionItem> getCompletions(XmlContext context) {
        List<CompletionItem> items = switch (context.getType()) {
            case ELEMENT -> getElementCompletions(context);
            case ATTRIBUTE -> getAttributeCompletions(context);
            case ATTRIBUTE_VALUE -> getAttributeValueCompletions(context);
            default -> new ArrayList<>();
        };
        logger.debug("XsltProvider returned {} items for context {}", items.size(), context.getType());
        return items;
    }

    /**
     * Element completions: the XSLT elements allowed under the parent, qualified with the
     * document's XSLT prefix. The insert text is the bare name; the engine adds the end tag.
     */
    private List<CompletionItem> getElementCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();
        XmlNamespaceDeclarations declarations = XmlNamespaceDeclarations.scan(context.getTextBeforeCaret());
        String parentName = parentElementName(context);
        String parent = xsltLocalName(parentName, declarations);
        boolean hasParent = parentName != null && !parentName.isEmpty();

        for (XsltElement element : XsltElementCatalog.elements()) {
            if (isElementValidInContext(element.name(), parent, hasParent)) {
                String name = declarations.qualify(element.name(), XsltElementCatalog.XSLT_NAMESPACE, "xsl");
                items.add(new CompletionItem.Builder(name, name, CompletionItemType.ELEMENT)
                        .description(element.description())
                        .namespace(XsltElementCatalog.XSLT_NAMESPACE)
                        .build());
            }
        }
        return items;
    }

    /**
     * Attribute completions for the XSLT element whose start tag the caret is in: required
     * attributes first, then its own optional ones, then the standard attributes; attributes
     * already written in the tag are left out. Literal result elements get none.
     */
    private List<CompletionItem> getAttributeCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();
        XmlNamespaceDeclarations declarations = XmlNamespaceDeclarations.scan(context.getTextBeforeCaret());
        String element = xsltLocalName(context.getCurrentElement(), declarations);
        if (element == null) {
            return items;
        }

        Set<String> present = new HashSet<>(
                StartTagAttributes.present(context.getTextBeforeCaret(), context.getTextAfterCaret()));
        List<XsltAttribute> attributes = XsltElementCatalog.attributesOf(element);
        Set<String> standard = new HashSet<>();
        XsltElementCatalog.STANDARD_ATTRIBUTES.forEach(a -> standard.add(a.name()));

        List<XsltAttribute> ordered = new ArrayList<>(attributes.stream().filter(XsltAttribute::required).toList());
        attributes.stream().filter(a -> !a.required()).forEach(ordered::add);
        for (XsltAttribute attribute : ordered) {
            if (present.contains(attribute.name())) {
                continue;
            }
            int relevance = attribute.required() ? 150 : standard.contains(attribute.name()) ? 50 : 100;
            items.add(new CompletionItem.Builder(attribute.name(), attribute.name(), CompletionItemType.ATTRIBUTE)
                    .description(attribute.description() + (attribute.required() ? " [required]" : ""))
                    .required(attribute.required())
                    .relevanceScore(relevance)
                    .examples(attribute.values())
                    .build());
        }
        return items;
    }

    /** Attribute value completions: the suggested values the catalog lists for the attribute. */
    private List<CompletionItem> getAttributeValueCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();
        XmlNamespaceDeclarations declarations = XmlNamespaceDeclarations.scan(context.getTextBeforeCaret());
        String element = xsltLocalName(context.getCurrentElement(), declarations);
        String attributeName = context.getCurrentAttribute();
        if (element == null || attributeName == null) {
            return items;
        }
        XsltElementCatalog.attribute(element, attributeName).ifPresent(attribute -> {
            for (String value : attribute.values()) {
                items.add(new CompletionItem.Builder(value, value, CompletionItemType.VALUE)
                        .description(attribute.name() + ": " + attribute.description())
                        .build());
            }
        });
        return items;
    }

    /**
     * The qualified name of the element a new element would be inserted into, or null at the
     * document root. Reads the element stack, which keeps the prefixes (the XPath strips them);
     * a partly typed name at the caret ({@code <xsl:va}) is on top of that stack and skipped.
     */
    static String parentElementName(XmlContext context) {
        if (context.getXPathContext() == null) {
            return null;
        }
        List<String> stack = context.getXPathContext().getElementStack();
        int top = stack.size() - 1;
        String typed = context.getCurrentElement();
        if (top >= 0 && typed != null && !typed.isEmpty() && typed.equals(stack.get(top))) {
            top--;
        }
        return top >= 0 ? stack.get(top) : null;
    }

    /**
     * Whether a new element at the caret goes into an XSLT element (or is the root element).
     * False inside a literal result element such as {@code <div>}, whose content is a sequence
     * constructor the XSLT schema does not describe.
     */
    static boolean insertsIntoXsltElement(XmlContext context) {
        String parent = parentElementName(context);
        return parent == null
                || xsltLocalName(parent, XmlNamespaceDeclarations.scan(context.getTextBeforeCaret())) != null;
    }

    /**
     * The local name of {@code qualifiedName} when it is an XSLT element, or null. With no
     * namespace declarations in sight, the conventional {@code xsl} prefix is accepted.
     */
    static String xsltLocalName(String qualifiedName, XmlNamespaceDeclarations declarations) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return null;
        }
        int colon = qualifiedName.indexOf(':');
        String prefix = colon < 0 ? "" : qualifiedName.substring(0, colon);
        String local = qualifiedName.substring(colon + 1);
        String uri = declarations.uriFor(prefix)
                .orElse("xsl".equals(prefix) ? XsltElementCatalog.XSLT_NAMESPACE : null);
        return XsltElementCatalog.XSLT_NAMESPACE.equals(uri) ? local : null;
    }

    /**
     * Checks if an element may appear under the parent.
     *
     * @param element   the candidate's local name
     * @param parent    the parent's local name when it is an XSLT element, otherwise null
     * @param hasParent whether there is a parent element at all (a literal result element counts)
     */
    private static boolean isElementValidInContext(String element, String parent, boolean hasParent) {
        if (!hasParent) {
            return ROOT_ELEMENTS.contains(element);
        }
        if (ROOT_ELEMENTS.contains(element)) {
            return false;
        }
        if (parent != null && ROOT_ELEMENTS.contains(parent)) {
            return DECLARATIONS.contains(element);
        }
        if ("choose".equals(parent)) {
            return "when".equals(element) || "otherwise".equals(element);
        }
        // Inside a sequence constructor: instructions, plus the locally declarable param/variable.
        return !DECLARATIONS.contains(element) || "variable".equals(element) || "param".equals(element);
    }

    @Override
    public int getPriority() {
        return 90; // High priority for XSLT mode
    }

    @Override
    public String getName() {
        return "XsltCompletionProvider";
    }
}
