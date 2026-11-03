package org.fxt.freexmltoolkit.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.OpenContent;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.TypeAlternative;
import org.fxt.freexmltoolkit.domain.Wildcard;
import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Builds the "Element Reference" of the PDF documentation: one section per element (or per
 * element declaration) with what the HTML detail page shows: properties, diagram, documentation
 * in every selected language, restrictions with their values, children and attributes, identity
 * constraints, assertions, type alternatives, wildcards, open content and appinfo.
 *
 * <p>Every element of the documentation gets the id of the section that describes it, so the
 * data dictionary, the index and the child tables can link there.</p>
 */
final class PdfElementReferenceBuilder {

    private static final Logger logger = LogManager.getLogger(PdfElementReferenceBuilder.class);

    /** Other paths a declaration occurs at that its section lists by name; the rest are counted. */
    static final int MAX_LISTED_OCCURRENCES = 10;

    private final XsdDocumentationPdfService pdf;
    private final XsdDocumentationData data;
    private final PdfDocumentationConfig config;
    private final XsdDocumentationImageService imageService;
    private final Path imageDir;
    private final Set<String> languages;
    private final Consumer<String> progress;

    /** Section id of a global type by its name as written (null for none), for links to the type sections. */
    private java.util.function.Function<String, String> typeSections = name -> null;

    /** Section id per element XPath (map key), for links. */
    private final Map<String, String> sectionIds = new HashMap<>();
    /** The elements that get a section, each with the paths it stands for. */
    private final Map<XsdExtendedElement, List<XsdExtendedElement>> sections = new LinkedHashMap<>();

    PdfElementReferenceBuilder(XsdDocumentationPdfService pdf, XsdDocumentationData data, PdfDocumentationConfig config,
                               XsdDocumentationImageService imageService, Path imageDir, Set<String> languages,
                               Consumer<String> progress) {
        this.pdf = pdf;
        this.data = data;
        this.config = config;
        this.imageService = imageService;
        this.imageDir = imageDir;
        this.languages = languages;
        this.progress = progress;
        planSections();
    }

    /**
     * Sets where the names of global types link to.
     *
     * @param typeSections the section id of a type by its name as written, null where there is none
     */
    void setTypeSections(java.util.function.Function<String, String> typeSections) {
        this.typeSections = typeSections;
    }

    /** @return whether there is a reference at all */
    boolean isEnabled() {
        return config.getElementReference() != PdfDocumentationConfig.ElementReference.OFF;
    }

    /**
     * @param xpath an element's key in the element map
     * @return the id of the section that describes it, or null without a reference
     */
    String sectionIdOf(String xpath) {
        return xpath == null ? null : sectionIds.get(xpath);
    }

    /** Decides which elements get a section and gives every element the id of its section. */
    private void planSections() {
        if (!isEnabled()) {
            return;
        }
        List<XsdExtendedElement> elements = new ArrayList<>(data.getExtendedXsdElementMap().values());
        elements.removeIf(pdf::isContainerElement);
        elements.sort(Comparator.comparing(pdf::getCleanXPath));

        boolean perDeclaration = config.getElementReference() == PdfDocumentationConfig.ElementReference.DECLARATIONS;
        Map<Node, XsdExtendedElement> byDeclaration = new IdentityHashMap<>();
        for (XsdExtendedElement element : elements) {
            XsdExtendedElement owner = element;
            Node declaration = element.getCurrentNode();
            if (perDeclaration && declaration != null) {
                owner = byDeclaration.computeIfAbsent(declaration, d -> element);
            }
            sections.computeIfAbsent(owner, o -> new ArrayList<>()).add(element);
        }
        int index = 0;
        for (Map.Entry<XsdExtendedElement, List<XsdExtendedElement>> section : sections.entrySet()) {
            String id = "ref-" + (++index);
            for (XsdExtendedElement occurrence : section.getValue()) {
                sectionIds.put(occurrence.getCurrentXpath(), id);
            }
        }
        logger.info("PDF element reference: {} sections for {} elements", sections.size(), elements.size());
    }

    /**
     * Writes the reference into the intermediate XML.
     *
     * @param doc  the intermediate document
     * @param root its root element
     */
    void write(Document doc, Element root) {
        if (!isEnabled() || sections.isEmpty()) {
            return;
        }
        Element reference = doc.createElement("element-reference");
        root.appendChild(reference);
        int total = sections.size();
        int step = Math.max(1, total / 10);
        int done = 0;
        int diagrams = 0;
        for (Map.Entry<XsdExtendedElement, List<XsdExtendedElement>> entry : sections.entrySet()) {
            if (writeSection(doc, reference, entry.getKey(), entry.getValue())) {
                diagrams++;
            }
            if (++done % step == 0) {
                progress.accept("Element reference: " + done + " of " + total);
            }
        }
        logger.info("PDF element reference written: {} sections, {} diagrams", total, diagrams);
    }

    /** @return whether the section got a diagram */
    private boolean writeSection(Document doc, Element reference, XsdExtendedElement element,
                                 List<XsdExtendedElement> occurrences) {
        boolean attribute = isAttribute(element);
        Element section = doc.createElement("section");
        reference.appendChild(section);
        section.setAttribute("id", sectionIdOf(element.getCurrentXpath()));
        section.setAttribute("name", element.getElementName());
        section.setAttribute("kind", attribute ? "Attribute" : "Element");
        add(doc, section, "path", XsdDocumentationPdfService.breakable(pdf.getCleanXPath(element)));

        // Other places this declaration is used
        if (occurrences.size() > 1) {
            Element others = doc.createElement("occurrences");
            others.setAttribute("count", String.valueOf(occurrences.size()));
            section.appendChild(others);
            occurrences.stream().filter(o -> o != element).limit(MAX_LISTED_OCCURRENCES)
                    .forEach(o -> add(doc, others, "path", XsdDocumentationPdfService.breakable(pdf.getCleanXPath(o))));
        }

        writeProperties(doc, section, element, attribute);
        boolean diagram = !attribute && writeDiagram(doc, section, element);
        writeDocumentation(doc, section, element);
        writeRestrictions(doc, section, element);
        if (!attribute) {
            writeChildren(doc, section, element);
            writeConstraints(doc, section, element);
            writeTypeAlternatives(doc, section, element);
            writeWildcards(doc, section, element);
            writeOpenContent(doc, section, element);
        }
        writeAssertions(doc, section, element);
        if (config.isIncludeSourceCode()) {
            addSource(doc, section, element.getSourceCode());
        }
        for (String appInfo : nonNull(element.getGenericAppInfos())) {
            if (appInfo != null && !appInfo.isBlank()) {
                add(doc, section, "appinfo", appInfo.strip());
            }
        }
        return diagram;
    }

    private void writeProperties(Document doc, Element section, XsdExtendedElement element, boolean attribute) {
        Element properties = doc.createElement("properties");
        section.appendChild(properties);
        Element type = property(doc, properties, "Type", typeDisplay(element));
        setRef(type, typeSections.apply(pdf.declaredType(element)));
        property(doc, properties, attribute ? "Use" : "Cardinality",
                attribute ? (isRequiredAttribute(element) ? "required" : "optional") : pdf.getCardinality(element));
        String namespace = element.getSourceNamespace() != null ? element.getSourceNamespace() : data.getTargetNamespace();
        property(doc, properties, "Namespace", namespace == null || namespace.isBlank() ? "(no namespace)" : namespace);
        if (element.getCurrentNode() instanceof Element declaration) {
            for (String name : List.of("default", "fixed", "nillable", "abstract", "substitutionGroup", "form", "block", "final")) {
                if (declaration.hasAttribute(name)) {
                    property(doc, properties, displayName(name), declaration.getAttribute(name));
                }
            }
        }
        property(doc, properties, "Level", String.valueOf(element.getLevel()));
        // A value a leaf element or attribute could take, as on the HTML detail page
        if (flattenedChildren(element).isEmpty()) {
            String sample = element.getDisplaySampleData();
            if (sample != null && !sample.isBlank() && !"...".equals(sample.strip())) {
                property(doc, properties, "Sample", sample.strip());
            }
        }
    }

    private String typeDisplay(XsdExtendedElement element) {
        if (element.isListType() && element.getListItemType() != null) {
            return "List of " + element.getListItemType();
        }
        if (element.isUnionType() && element.getUnionMemberTypes() != null && !element.getUnionMemberTypes().isEmpty()) {
            return "Union of " + String.join(" | ", element.getUnionMemberTypes());
        }
        return pdf.declaredType(element);
    }

    private boolean writeDiagram(Document doc, Element section, XsdExtendedElement element) {
        if (!config.isIncludeElementDiagrams() || imageService == null || imageDir == null) {
            return false;
        }
        boolean worthDrawing = !flattenedChildren(element).isEmpty()
                || !nonNull(element.getIdentityConstraints()).isEmpty()
                || !nonNull(element.getAssertions()).isEmpty();
        if (!worthDrawing) {
            return false;
        }
        return addDiagram(doc, section, element, sectionIdOf(element.getCurrentXpath()));
    }

    /**
     * Draws the diagram of {@code element} into the image directory (SVG or PNG, as configured) and
     * adds it to {@code section} with its print size.
     *
     * @return whether a diagram was added
     */
    boolean addDiagram(Document doc, Element section, XsdExtendedElement element, String fileName) {
        try {
            Path image = drawDiagram(imageService, config, element, imageDir.resolve(fileName));
            if (image != null) {
                Element img = add(doc, section, "image-path", image.toUri().toString());
                setImageSize(img, image, config, REFERENCE_DIAGRAM_MAX_HEIGHT_MM);
                return true;
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to draw the diagram of {}: {}", element.getCurrentXpath(), e.getMessage());
        }
        return false;
    }

    /**
     * Draws a diagram as {@code base}.svg (vector) or {@code base}.png, as the config says.
     *
     * @return the written file, or null when nothing was drawn
     */
    static Path drawDiagram(XsdDocumentationImageService imageService, PdfDocumentationConfig config,
                            XsdExtendedElement element, Path base) {
        if (config.isVectorDiagrams()) {
            Path svg = base.resolveSibling(base.getFileName() + ".svg");
            return imageService.generatePrintSvg(element, svg.toFile()) != null ? svg : null;
        }
        Path png = base.resolveSibling(base.getFileName() + ".png");
        return imageService.generateImage(element, png.toFile()) != null ? png : null;
    }

    private void writeDocumentation(Document doc, Element section, XsdExtendedElement element) {
        Map<String, String> docs = element.getLanguageDocumentation();
        if (docs == null) {
            return;
        }
        for (Map.Entry<String, String> entry : docs.entrySet()) {
            String lang = entry.getKey();
            boolean wanted = languages == null || languages.isEmpty() || languages.contains(lang) || "default".equals(lang);
            String text = entry.getValue() == null ? "" : pdf.stripHtml(entry.getValue()).strip();
            if (wanted && !text.isEmpty()) {
                Element d = add(doc, section, "documentation", text);
                d.setAttribute("lang", "default".equals(lang) ? "" : lang);
            }
        }
    }

    private void writeRestrictions(Document doc, Element section, XsdExtendedElement element) {
        XsdExtendedElement.RestrictionInfo info = element.getRestrictionInfo();
        if (info == null) {
            return;
        }
        Element restrictions = doc.createElement("restrictions");
        if (info.base() != null && !info.base().isBlank()) {
            restrictions.setAttribute("base", info.base());
        }
        for (Map.Entry<String, List<String>> facet : info.facets().entrySet()) {
            for (String value : nonNull(facet.getValue())) {
                if ("enumeration".equals(facet.getKey())) {
                    add(doc, restrictions, "enumeration", value);
                } else {
                    Element f = add(doc, restrictions, "facet", XsdDocumentationPdfService.breakable(value));
                    f.setAttribute("name", facet.getKey());
                }
            }
        }
        if (restrictions.hasChildNodes() || restrictions.hasAttribute("base")) {
            section.appendChild(restrictions);
        }
    }

    private void writeChildren(Document doc, Element section, XsdExtendedElement element) {
        List<XsdExtendedElement> children = flattenedChildren(element);
        if (children.isEmpty()) {
            return;
        }
        Element list = doc.createElement("children");
        section.appendChild(list);
        for (XsdExtendedElement child : children) {
            list.appendChild(childEntry(doc, child));
        }
    }

    /**
     * @return the row of a child or attribute in a "Children and Attributes" table, linking to its
     *         section and to the section of its type
     */
    Element childEntry(Document doc, XsdExtendedElement child) {
        Element c = doc.createElement("child");
        c.setAttribute("name", child.getElementName());
        c.setAttribute("kind", isAttribute(child) ? "Attribute" : "Element");
        setRef(c, sectionIdOf(child.getCurrentXpath()));
        String type = pdf.declaredType(child);
        Element t = add(doc, c, "type", type);
        setRef(t, typeSections.apply(type));
        add(doc, c, "cardinality", isAttribute(child)
                ? (isRequiredAttribute(child) ? "1" : "0..1") : pdf.getCardinality(child));
        add(doc, c, "description", pdf.getFirstDocumentation(child));
        return c;
    }

    private static void setRef(Element element, String ref) {
        if (ref != null) {
            element.setAttribute("ref", ref);
        }
    }

    private void writeConstraints(Document doc, Element section, XsdExtendedElement element) {
        for (IdentityConstraint constraint : nonNull(element.getIdentityConstraints())) {
            Element c = doc.createElement("constraint");
            section.appendChild(c);
            c.setAttribute("kind", switch (constraint.getType()) {
                case KEY -> "Key";
                case KEYREF -> "Key Reference";
                default -> "Unique";
            });
            c.setAttribute("name", nullToEmpty(constraint.getName()));
            add(doc, c, "selector", XsdDocumentationPdfService.breakable(nullToEmpty(constraint.getSelector())));
            for (String field : nonNull(constraint.getFields())) {
                add(doc, c, "field", XsdDocumentationPdfService.breakable(field));
            }
            if (constraint.getRefer() != null && !constraint.getRefer().isBlank()) {
                add(doc, c, "refer", constraint.getRefer());
            }
            addIfPresent(doc, c, "documentation", constraint.getDocumentation());
        }
    }

    private void writeAssertions(Document doc, Element section, XsdExtendedElement element) {
        for (XsdAssertion assertion : nonNull(element.getAssertions())) {
            Element a = doc.createElement("assertion");
            section.appendChild(a);
            a.setAttribute("kind", assertion.getType() == XsdAssertion.Type.ASSERTION ? "Assertion (simpleType)" : "Assert");
            add(doc, a, "test", XsdDocumentationPdfService.breakable(nullToEmpty(assertion.getTest()).strip()));
            addIfPresent(doc, a, "documentation", assertion.getDocumentation());
        }
    }

    private void writeTypeAlternatives(Document doc, Element section, XsdExtendedElement element) {
        for (TypeAlternative alternative : nonNull(element.getTypeAlternatives())) {
            Element a = doc.createElement("alternative");
            section.appendChild(a);
            add(doc, a, "test", alternative.isDefault() ? "(otherwise)"
                    : XsdDocumentationPdfService.breakable(nullToEmpty(alternative.getTest())));
            add(doc, a, "type", nullToEmpty(alternative.getType()));
            addIfPresent(doc, a, "documentation", alternative.getDocumentation());
        }
    }

    private void writeWildcards(Document doc, Element section, XsdExtendedElement element) {
        for (Wildcard wildcard : nonNull(element.getWildcards())) {
            Element w = doc.createElement("wildcard");
            section.appendChild(w);
            w.setAttribute("kind", nullToEmpty(wildcard.getTypeDisplayName()));
            add(doc, w, "namespace", nullToEmpty(wildcard.getNamespaceDisplayName()));
            add(doc, w, "process-contents", wildcard.getProcessContents() == null ? "strict"
                    : wildcard.getProcessContents().name().toLowerCase(java.util.Locale.ROOT));
            addIfPresent(doc, w, "documentation", wildcard.getDocumentation());
        }
    }

    private void writeOpenContent(Document doc, Element section, XsdExtendedElement element) {
        OpenContent openContent = element.getOpenContent();
        if (openContent == null) {
            return;
        }
        Element o = doc.createElement("open-content");
        section.appendChild(o);
        add(doc, o, "mode", nullToEmpty(openContent.getModeDisplayName()));
        add(doc, o, "namespace", nullToEmpty(openContent.getNamespaceDisplayName()));
        addIfPresent(doc, o, "process-contents", openContent.getProcessContents());
        addIfPresent(doc, o, "documentation", openContent.getDocumentation());
    }

    /** The children and attributes of an element, without the SEQUENCE/CHOICE/ALL containers in between. */
    List<XsdExtendedElement> flattenedChildren(XsdExtendedElement element) {
        List<XsdExtendedElement> result = new ArrayList<>();
        collectChildren(element, result, 0);
        return result;
    }

    private void collectChildren(XsdExtendedElement element, List<XsdExtendedElement> result, int depth) {
        if (element == null || element.getChildren() == null || depth > 64) {
            return;
        }
        for (String childXpath : element.getChildren()) {
            XsdExtendedElement child = data.getExtendedXsdElementMap().get(childXpath);
            if (child == null) {
                continue;
            }
            if (pdf.isContainerElement(child)) {
                collectChildren(child, result, depth + 1);
            } else {
                result.add(child);
            }
        }
    }

    /** Tallest a diagram of a reference section may be, so it leaves room for its text. */
    static final double REFERENCE_DIAGRAM_MAX_HEIGHT_MM = 150;

    /**
     * Writes the size an image is drawn at onto its {@code image-path} element ({@code width-mm},
     * {@code height-mm}): its natural size at 96 dpi, scaled down to fit the text width and
     * {@code maxHeightMm}. FOP does not bound an external graphic by {@code max-height}, so a tall
     * diagram would otherwise run over what follows it.
     */
    static void setImageSize(Element imagePath, Path image, PdfDocumentationConfig config, double maxHeightMm) {
        int[] pixels = pixelSize(image);
        if (pixels == null) {
            return;
        }
        double widthMm = pixels[0] * 25.4 / 96;
        double heightMm = pixels[1] * 25.4 / 96;
        double maxWidthMm = toMm(config.getPageWidth()) - 2 * toMm(config.getMarginSize());
        double scale = Math.min(1, Math.min(maxWidthMm / widthMm, maxHeightMm / heightMm));
        imagePath.setAttribute("width-mm", String.format(java.util.Locale.ROOT, "%.1f", widthMm * scale));
        imagePath.setAttribute("height-mm", String.format(java.util.Locale.ROOT, "%.1f", heightMm * scale));
    }

    static int[] pixelSize(Path image) {
        if (image.getFileName().toString().endsWith(".svg")) {
            return svgSize(image);
        }
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(image.toFile())) {
            if (in == null) {
                return null;
            }
            var readers = javax.imageio.ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (java.io.IOException e) {
            logger.debug("Could not read the size of {}: {}", image, e.getMessage());
            return null;
        }
    }

    /** The width and height of an SVG from its root element's {@code width}/{@code height} (user units = px). */
    private static int[] svgSize(Path svg) {
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(svg)) {
            char[] head = new char[4096];
            int read = reader.read(head);
            String start = read <= 0 ? "" : new String(head, 0, read);
            java.util.regex.Matcher root = java.util.regex.Pattern.compile("<svg[^>]*>", java.util.regex.Pattern.DOTALL)
                    .matcher(start);
            if (!root.find()) {
                return null;
            }
            java.util.regex.Matcher w = java.util.regex.Pattern.compile("\\swidth=\"([0-9.]+)").matcher(root.group());
            java.util.regex.Matcher h = java.util.regex.Pattern.compile("\\sheight=\"([0-9.]+)").matcher(root.group());
            if (!w.find() || !h.find()) {
                return null;
            }
            return new int[]{(int) Math.ceil(Double.parseDouble(w.group(1))), (int) Math.ceil(Double.parseDouble(h.group(1)))};
        } catch (java.io.IOException | NumberFormatException e) {
            logger.debug("Could not read the size of {}: {}", svg, e.getMessage());
            return null;
        }
    }

    /** @return a length such as {@code 210mm}, {@code 8.5in}, {@code 2cm} or {@code 12pt} in millimetres */
    static double toMm(String length) {
        String value = length == null ? "" : length.strip();
        double number = Double.parseDouble(value.replaceAll("[a-z]+$", ""));
        if (value.endsWith("in")) {
            return number * 25.4;
        }
        if (value.endsWith("cm")) {
            return number * 10;
        }
        if (value.endsWith("pt")) {
            return number * 25.4 / 72;
        }
        return number;
    }

    /** Adds the XSD source of a section, if there is one. */
    static void addSource(Document doc, Element section, String source) {
        if (source != null && !source.isBlank()) {
            add(doc, section, "source", source.stripTrailing());
        }
    }

    private static boolean isAttribute(XsdExtendedElement element) {
        return element.getElementName() != null && element.getElementName().startsWith("@");
    }

    private static boolean isRequiredAttribute(XsdExtendedElement attribute) {
        return attribute.getCurrentNode() instanceof Element declaration
                && "required".equals(declaration.getAttribute("use"));
    }

    private static String displayName(String attribute) {
        return switch (attribute) {
            case "substitutionGroup" -> "Substitution group";
            default -> Character.toUpperCase(attribute.charAt(0)) + attribute.substring(1);
        };
    }

    private static Element property(Document doc, Element properties, String name, String value) {
        Element p = add(doc, properties, "property", XsdDocumentationPdfService.breakable(value));
        p.setAttribute("name", name);
        return p;
    }

    private static Element add(Document doc, Element parent, String name, String text) {
        Element element = doc.createElement(name);
        element.setTextContent(text == null ? "" : text);
        parent.appendChild(element);
        return element;
    }

    private static void addIfPresent(Document doc, Element parent, String name, String text) {
        if (text != null && !text.isBlank()) {
            add(doc, parent, name, text.strip());
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
