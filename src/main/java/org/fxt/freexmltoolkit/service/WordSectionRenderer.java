package org.fxt.freexmltoolkit.service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Writes the element and type sections into a Word document. The sections come as the same
 * intermediate XML the PDF is made from ({@link PdfElementReferenceBuilder},
 * {@link PdfTypeReferenceBuilder}), so both formats show the same content: properties, diagram,
 * documentation in every language, restrictions and enumerations, children and attributes,
 * identity constraints, assertions, type alternatives, wildcards, open content, usage, appinfo and
 * the XSD source. Every section is a bookmark; names that point to other sections are links.
 */
final class WordSectionRenderer {

    private static final Logger logger = LogManager.getLogger(WordSectionRenderer.class);

    /** Tallest a diagram may be shown, so a section's text follows it on the page. */
    private static final double MAX_DIAGRAM_HEIGHT_PT = 400;

    private final XsdDocumentationWordService word;
    private final XWPFDocument document;

    WordSectionRenderer(XsdDocumentationWordService word, XWPFDocument document) {
        this.word = word;
        this.document = document;
    }

    /** Writes one {@code section} element. */
    void render(Element section) {
        XWPFParagraph heading = document.createParagraph();
        // Level 3: in the navigation pane, but not in the table of contents (levels 1-2), which
        // would otherwise list thousands of sections for a large schema
        heading.setStyle("Heading3");
        heading.setSpacingBefore(240);
        heading.setSpacingAfter(60);
        word.addBookmark(heading, section.getAttribute("id"));
        XWPFRun name = heading.createRun();
        name.setText(section.getAttribute("name"));
        name.setBold(true);
        name.setFontSize(14);
        name.setColor(word.getColorPrimary());
        XWPFRun kind = heading.createRun();
        kind.setText("  " + section.getAttribute("kind"));
        kind.setFontSize(9);
        kind.setColor(word.getColorSecondary());

        note(text(child(section, "path")));
        Element occurrences = child(section, "occurrences");
        if (occurrences != null) {
            List<Element> paths = children(occurrences, "path");
            int count = Integer.parseInt(occurrences.getAttribute("count"));
            StringBuilder others = new StringBuilder("Used at " + count + " paths. Also at: ");
            for (int i = 0; i < paths.size(); i++) {
                others.append(i > 0 ? "; " : "").append(text(paths.get(i)));
            }
            if (count - 1 > paths.size()) {
                others.append(" … and ").append(count - 1 - paths.size()).append(" more");
            }
            note(others.toString());
        }

        Element properties = child(section, "properties");
        if (properties != null) {
            List<String[]> rows = new ArrayList<>();
            for (Element property : children(properties, "property")) {
                rows.add(new String[]{property.getAttribute("name"), text(property), attr(property, "ref")});
            }
            labelTable(rows);
        }

        Element image = child(section, "image-path");
        if (image != null) {
            diagram(image);
        }

        List<Element> documentation = children(section, "documentation");
        if (!documentation.isEmpty()) {
            subheading("Documentation");
            for (Element doc : documentation) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingAfter(60);
                if (!doc.getAttribute("lang").isEmpty()) {
                    XWPFRun lang = paragraph.createRun();
                    lang.setBold(true);
                    lang.setFontSize(9);
                    lang.setText("[" + doc.getAttribute("lang") + "] ");
                }
                lines(paragraph, text(doc), 9, null);
            }
        }

        Element restrictions = child(section, "restrictions");
        if (restrictions != null) {
            subheading("Restrictions");
            List<String[]> rows = new ArrayList<>();
            if (restrictions.hasAttribute("base")) {
                rows.add(new String[]{"Base", restrictions.getAttribute("base"), null});
            }
            for (Element facet : children(restrictions, "facet")) {
                rows.add(new String[]{facet.getAttribute("name"), text(facet), null});
            }
            List<Element> enumerations = children(restrictions, "enumeration");
            if (!enumerations.isEmpty()) {
                StringBuilder values = new StringBuilder();
                boolean documented = enumerations.stream().anyMatch(e -> e.hasAttribute("documentation"));
                for (Element value : enumerations) {
                    if (documented) {
                        values.append(values.isEmpty() ? "" : "\n").append(text(value));
                        if (value.hasAttribute("documentation")) {
                            values.append(" - ").append(value.getAttribute("documentation"));
                        }
                    } else {
                        values.append(values.isEmpty() ? "" : ", ").append(text(value));
                    }
                }
                rows.add(new String[]{"Values (" + enumerations.size() + ")", values.toString(), null});
            }
            labelTable(rows);
        }

        Element children = child(section, "children");
        if (children != null) {
            subheading("Children and Attributes");
            childrenTable(children(children, "child"));
        }

        List<Element> constraints = children(section, "constraint");
        if (!constraints.isEmpty()) {
            subheading("Identity Constraints");
            for (Element constraint : constraints) {
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{constraint.getAttribute("kind"), constraint.getAttribute("name"), null});
                rows.add(new String[]{"Selector", text(child(constraint, "selector")), null});
                for (Element field : children(constraint, "field")) {
                    rows.add(new String[]{"Field", text(field), null});
                }
                if (child(constraint, "refer") != null) {
                    rows.add(new String[]{"Refers to", text(child(constraint, "refer")), null});
                }
                if (child(constraint, "documentation") != null) {
                    rows.add(new String[]{"Documentation", text(child(constraint, "documentation")), null});
                }
                labelTable(rows);
            }
        }

        List<Element> assertions = children(section, "assertion");
        if (!assertions.isEmpty()) {
            subheading("Assertions (XSD 1.1)");
            List<String[]> rows = new ArrayList<>();
            for (Element assertion : assertions) {
                rows.add(new String[]{assertion.getAttribute("kind"), text(child(assertion, "test")), null});
                if (child(assertion, "documentation") != null) {
                    rows.add(new String[]{"Documentation", text(child(assertion, "documentation")), null});
                }
            }
            labelTable(rows);
        }

        List<Element> alternatives = children(section, "alternative");
        if (!alternatives.isEmpty()) {
            subheading("Type Alternatives (XSD 1.1)");
            List<String[]> rows = new ArrayList<>();
            for (Element alternative : alternatives) {
                rows.add(new String[]{text(child(alternative, "type")), text(child(alternative, "test")), null});
            }
            labelTable(rows);
        }

        List<Element> wildcards = children(section, "wildcard");
        Element openContent = child(section, "open-content");
        if (!wildcards.isEmpty() || openContent != null) {
            subheading("Wildcards and Open Content");
            List<String[]> rows = new ArrayList<>();
            for (Element wildcard : wildcards) {
                rows.add(new String[]{wildcard.getAttribute("kind"), text(child(wildcard, "namespace"))
                        + " (processContents: " + text(child(wildcard, "process-contents")) + ")", null});
            }
            if (openContent != null) {
                rows.add(new String[]{"Open content", text(child(openContent, "mode")) + ", "
                        + text(child(openContent, "namespace")), null});
            }
            labelTable(rows);
        }

        Element usedIn = child(section, "used-in");
        List<Element> derived = children(section, "derived-type");
        if (usedIn != null || !derived.isEmpty()) {
            subheading("Used In");
            if (usedIn != null) {
                for (Element user : children(usedIn, "user")) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentationLeft(200);
                    paragraph.setSpacingAfter(0);
                    linkOrText(paragraph, text(user), attr(user, "ref"), 9);
                }
                int listed = children(usedIn, "user").size();
                int count = Integer.parseInt(usedIn.getAttribute("count"));
                if (count > listed) {
                    note("… and " + (count - listed) + " more");
                }
            }
            if (!derived.isEmpty()) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setIndentationLeft(200);
                XWPFRun label = paragraph.createRun();
                label.setText("Derived types: ");
                label.setFontSize(9);
                for (int i = 0; i < derived.size(); i++) {
                    if (i > 0) {
                        XWPFRun comma = paragraph.createRun();
                        comma.setText(", ");
                        comma.setFontSize(9);
                    }
                    linkOrText(paragraph, text(derived.get(i)), attr(derived.get(i), "ref"), 9);
                }
            }
        }

        Element source = child(section, "source");
        if (source != null) {
            subheading("XSD Source");
            monospace(text(source));
        }

        List<Element> appInfos = children(section, "appinfo");
        if (!appInfos.isEmpty()) {
            subheading("Additional Information");
            for (Element appInfo : appInfos) {
                monospace(text(appInfo));
            }
        }
    }

    private void diagram(Element image) {
        try {
            Path file = Path.of(URI.create(text(image)));
            double widthPt = Double.parseDouble(image.getAttribute("width-mm")) * 72 / 25.4;
            double heightPt = Double.parseDouble(image.getAttribute("height-mm")) * 72 / 25.4;
            double scale = Math.min(1, Math.min(word.textWidthPt() / widthPt, MAX_DIAGRAM_HEIGHT_PT / heightPt));
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            try (InputStream in = Files.newInputStream(file)) {
                paragraph.createRun().addPicture(in, PictureType.PNG, file.getFileName().toString(),
                        Units.toEMU(widthPt * scale), Units.toEMU(heightPt * scale));
            }
        } catch (Exception e) {
            logger.warn("Could not insert diagram {}: {}", text(image), e.getMessage());
        }
    }

    /** A two-column table: label | value (the value a link where a target is given). */
    private void labelTable(List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        XWPFTable table = document.createTable(rows.size(), 2);
        word.setTableStyle(table);
        word.setColumnWidths(table, new int[]{2300, 6700});
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = table.getRow(i);
            word.setTableCell(row, 0, rows.get(i)[0], true, word.getColorHeaderBg());
            word.setTableCellLink(row, 1, rows.get(i)[1], rows.get(i)[2], null);
        }
        spacer();
    }

    private void childrenTable(List<Element> children) {
        XWPFTable table = document.createTable(children.size() + 1, 4);
        word.setTableStyle(table);
        word.setColumnWidths(table, new int[]{2200, 2700, 800, 3300});
        String header = word.getColorHeaderBg();
        word.setTableCell(table.getRow(0), 0, "Name", true, header);
        word.setTableCell(table.getRow(0), 1, "Type", true, header);
        word.setTableCell(table.getRow(0), 2, "Card.", true, header);
        word.setTableCell(table.getRow(0), 3, "Description", true, header);
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            word.setTableCellLink(row, 0, child.getAttribute("name"), attr(child, "ref"), null);
            Element type = child(child, "type");
            word.setTableCellLink(row, 1, text(type), attr(type, "ref"), null);
            word.setTableCell(row, 2, text(child(child, "cardinality")), false, null);
            word.setTableCell(row, 3, text(child(child, "description")), false, null);
        }
        spacer();
    }

    private void subheading(String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(120);
        paragraph.setSpacingAfter(40);
        paragraph.setKeepNext(true);
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(10);
        run.setColor(word.getColorPrimary());
    }

    private void note(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(40);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(8);
        run.setColor(word.getColorSecondary());
    }

    private void monospace(String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(60);
        lines(paragraph, text, 7, "Courier New");
    }

    /** Writes text with its line breaks kept. */
    private static void lines(XWPFParagraph paragraph, String text, int fontSize, String fontFamily) {
        String[] lines = text.split("\r?\n", -1);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(fontSize);
        if (fontFamily != null) {
            run.setFontFamily(fontFamily);
        }
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
        }
    }

    private void linkOrText(XWPFParagraph paragraph, String text, String anchor, int fontSize) {
        if (anchor != null) {
            word.addLink(paragraph, text, anchor, fontSize);
        } else {
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            run.setFontSize(fontSize);
        }
    }

    private void spacer() {
        document.createParagraph().setSpacingAfter(0);
    }

    private static String attr(Element element, String name) {
        return element != null && element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    private static String text(Element element) {
        return element == null ? "" : element.getTextContent();
    }

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getNodeName())) {
                result.add(element);
            }
        }
        return result;
    }
}
