package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonResourceAccess;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.ElementRegion;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Span;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Step;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * The {@code Documents/Document} elements of a FundsXML 4 document: lists them (linked by {@code DocumentURL}
 * or embedded as {@code BinaryData}), decodes an embedded one, embeds a file as a new {@code Document} and
 * turns a link into an embedded document and back. Changes are text edits that keep the rest of the document
 * as it is (the new elements are indented like their neighbours).
 */
public final class FundsXmlDocuments {

    /** The level a document is assigned to. */
    public enum Level {
        FUND("Fund", null), SUBFUND("Subfunds", "Subfund"), SHARE_CLASS("ShareClasses", "ShareClass");

        /** The child element of {@code Document} ({@code Fund}, {@code Subfunds}, {@code ShareClasses}). */
        final String element;
        /** The repeated element inside it, {@code null} for {@code Fund}. */
        final String item;

        Level(String element, String item) {
            this.element = element;
            this.item = item;
        }
    }

    /** One identifier, e.g. {@code ISIN} / {@code LU0000000001}. */
    public record Identifier(String element, String value) {
    }

    /** A fund, subfund or share class a document belongs to: its identifiers and its name ({@code null}: none). */
    public record Party(List<Identifier> identifiers, String name) {
        public Party {
            identifiers = List.copyOf(identifiers);
        }

        /** @return the first ISIN, else the first LEI, else the first identifier ({@code null}: none) */
        public Identifier main() {
            for (String preferred : List.of("ISIN", "LEI")) {
                for (Identifier id : identifiers) {
                    if (id.element().equals(preferred)) {
                        return id;
                    }
                }
            }
            return identifiers.isEmpty() ? null : identifiers.getFirst();
        }
    }

    /** What a document is assigned to: the fund, subfunds or share classes. */
    public record Assignment(Level level, List<Party> parties) {
        public Assignment {
            parties = List.copyOf(parties);
            if (level == Level.FUND && parties.size() != 1) {
                throw new IllegalArgumentException("a fund assignment has exactly one fund");
            }
        }

        public static Assignment fund(Party fund) {
            return new Assignment(Level.FUND, List.of(fund));
        }

        public static Assignment shareClasses(List<String> isins) {
            return new Assignment(Level.SHARE_CLASS,
                    isins.stream().map(isin -> new Party(List.of(new Identifier("ISIN", isin)), null)).toList());
        }
    }

    /**
     * One {@code Document} of the file.
     *
     * @param index        its position among {@code Documents/Document} (1-based)
     * @param listed       {@code Type/ListedType} (else {@code UnlistedType})
     * @param assignments  the fund, subfunds and share classes it is assigned to (document order)
     * @param url          {@code DocumentURL}, {@code null} when absent
     * @param embedded     whether it has {@code BinaryData}
     * @param sizeInBytes  {@code SizeInBytes}, {@code null} when absent or not a number
     * @param decodedSize  the size of the embedded content, {@code null} when not embedded
     * @param line         line of the {@code Document} start tag (1-based)
     */
    public record Entry(int index, boolean listed, String type, String name, String language,
                        List<Assignment> assignments, String url, boolean embedded, Long sizeInBytes, String fileName,
                        String format, Long decodedSize, int line) {
        public Entry {
            assignments = List.copyOf(assignments);
        }

        public boolean linked() {
            return url != null && !url.isBlank();
        }

        /** @return whether {@code SizeInBytes} is given and differs from the embedded content's size */
        public boolean sizeMismatch() {
            return embedded && sizeInBytes != null && decodedSize != null && !sizeInBytes.equals(decodedSize);
        }
    }

    /** An embedded document's content. @param declaredSize its {@code SizeInBytes}, {@code null} when absent */
    public record Decoded(byte[] bytes, String fileName, Long declaredSize) {
        public boolean sizeMismatch() {
            return declaredSize != null && declaredSize != bytes.length;
        }
    }

    /**
     * A file to embed as a new {@code Document}.
     *
     * @param name       the {@code Name}; {@code null} or blank: the file name without its extension
     * @param listedType a value of the {@code ListedType} enumeration
     * @param language   an {@code xs:language} code, e.g. {@code en}
     */
    public record NewDocument(byte[] bytes, String fileName, String name, String listedType, String language,
                              Assignment assignment) {
    }

    /** A fund, subfund or share class of the file a document can be assigned to. */
    public record Candidate(Level level, Party party) {
    }

    /** The {@code ListedType} values of FundsXML 4.2.11, for when the schema cannot be read. */
    public static final List<String> DEFAULT_LISTED_TYPES = List.of("AIFMD-AnnexIV", "AnnualReport", "HalfYearReport",
            "QuarterlyReport", "MonthlyReport", "AuditReport", "Factsheet", "Prospectus", "PRIIPS-KID",
            "KID / Simplified Prospectus", "MarketingNotification", "SFDR-PAIStatement", "SFDR Website Disclosure",
            "MarketingMaterial", "ValuationReport");

    /** Base64 line length of the embedded content (as MIME). */
    public static final int BASE64_LINE = 76;

    /** The root's children that come before {@code Documents} in the schema. */
    private static final Set<String> BEFORE_DOCUMENTS = Set.of("ControlData", "Funds", "AssetMgmtCompanyDynData",
            "AssetMasterData");
    private static final Pattern LANGUAGE = Pattern.compile("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*");
    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();

    private FundsXmlDocuments() {
    }

    // ---- reading ---------------------------------------------------------------------------------------------

    /**
     * Lists the {@code Documents/Document} elements.
     *
     * @throws IOException when the text is not well-formed XML
     */
    public static List<Entry> list(String xml) throws IOException {
        List<Entry> entries = new ArrayList<>();
        List<XdmNode> documents = documentNodes(root(parse(xml)));
        for (int i = 0; i < documents.size(); i++) {
            XdmNode doc = documents.get(i);
            XdmNode type = first(doc, "Type");
            boolean listed = type != null && first(type, "ListedType") != null;
            String typeText = type == null ? null : text(first(type, listed ? "ListedType" : "UnlistedType"));
            List<Assignment> assignments = new ArrayList<>();
            for (Level level : Level.values()) {
                for (XdmNode element : children(doc, level.element)) {
                    List<Party> parties = new ArrayList<>();
                    for (XdmNode party : level.item == null ? List.of(element) : children(element, level.item)) {
                        parties.add(party(party, "Name"));
                    }
                    if (!parties.isEmpty()) {
                        assignments.add(level == Level.FUND ? Assignment.fund(parties.getFirst())
                                : new Assignment(level, parties));
                    }
                }
            }
            XdmNode binary = first(doc, "BinaryData");
            entries.add(new Entry(i + 1, listed, typeText, text(first(doc, "Name")), text(first(doc, "Language")),
                    assignments, text(first(doc, "DocumentURL")), binary != null,
                    number(text(first(doc, "SizeInBytes"))), text(first(doc, "FileName")), text(first(doc, "Format")),
                    binary == null ? null : decodedLength(binary.getStringValue()), doc.getLineNumber()));
        }
        return entries;
    }

    /**
     * Decodes the {@code BinaryData} of the {@code index}-th document (1-based).
     *
     * @throws IOException when the text is not well-formed, the document is not embedded or not valid base64
     */
    public static Decoded decode(String xml, int index) throws IOException {
        XdmNode doc = document(parse(xml), index);
        XdmNode binary = first(doc, "BinaryData");
        if (binary == null) {
            throw new IOException("Document " + index + " has no BinaryData.");
        }
        return new Decoded(decodeBase64(binary.getStringValue()), text(first(doc, "FileName")),
                number(text(first(doc, "SizeInBytes"))));
    }

    /**
     * Decodes base64 as in {@code xs:base64Binary}: line breaks, spaces and tabs anywhere are ignored.
     *
     * @throws IOException when the text is not valid base64
     */
    public static byte[] decodeBase64(String text) throws IOException {
        StringBuilder compact = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                compact.append(c);
            }
        }
        try {
            return Base64.getDecoder().decode(compact.toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("BinaryData is not valid base64: " + e.getMessage(), e);
        }
    }

    /** @return the number of bytes {@code base64} decodes to, without decoding it */
    static long decodedLength(String base64) {
        long chars = 0;
        int padding = 0;
        for (int i = 0; i < base64.length(); i++) {
            char c = base64.charAt(i);
            if (!Character.isWhitespace(c)) {
                chars++;
                padding = c == '=' ? padding + 1 : 0;
            }
        }
        return chars / 4 * 3 - Math.min(padding, 2);
    }

    /**
     * @return the funds, subfunds and share classes of {@code Funds/Fund} that have an ISIN or LEI, then those the
     *         documents are assigned to (for a delivery of documents only)
     */
    public static List<Candidate> candidates(String xml) throws IOException {
        List<Candidate> result = new ArrayList<>();
        XdmNode root = root(parse(xml));
        for (XdmNode funds : children(root, "Funds")) {
            for (XdmNode fund : children(funds, "Fund")) {
                add(result, Level.FUND, fund);
                for (XdmNode single : children(fund, "SingleFund")) {
                    shareClasses(result, single);
                }
                for (XdmNode subfunds : children(fund, "Subfunds")) {
                    for (XdmNode subfund : children(subfunds, "Subfund")) {
                        add(result, Level.SUBFUND, subfund);
                        shareClasses(result, subfund);
                    }
                }
            }
        }
        // a delivery of documents only: the funds and share classes its documents are assigned to
        for (XdmNode doc : documentNodes(root)) {
            for (Level level : Level.values()) {
                for (XdmNode element : children(doc, level.element)) {
                    for (XdmNode party : level.item == null ? List.of(element) : children(element, level.item)) {
                        Party p = party(party, "Name");
                        if (p.main() != null && result.stream().noneMatch(c -> c.level() == level
                                && c.party().main() != null && c.party().main().equals(p.main()))) {
                            add(result, level, p);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void shareClasses(List<Candidate> result, XdmNode parent) {
        for (XdmNode classes : children(parent, "ShareClasses")) {
            for (XdmNode shareClass : children(classes, "ShareClass")) {
                add(result, Level.SHARE_CLASS, shareClass);
            }
        }
    }

    private static void add(List<Candidate> result, Level level, XdmNode node) {
        add(result, level, party(node, "Names"));
    }

    private static void add(List<Candidate> result, Level level, Party party) {
        List<Identifier> ids = party.identifiers().stream()
                .filter(id -> id.element().equals("ISIN") || id.element().equals("LEI")).toList();
        if (!ids.isEmpty()) {
            result.add(new Candidate(level, new Party(ids, party.name())));
        }
    }

    /** @return the identifiers of {@code node} and its name ({@code Name}, or {@code Names/OfficialName}) */
    private static Party party(XdmNode node, String nameElement) {
        List<Identifier> ids = new ArrayList<>();
        for (XdmNode identifiers : children(node, "Identifiers")) {
            for (XdmNode id : children(identifiers, null)) {
                String value = id.getStringValue().strip();
                if (!value.isEmpty()) {
                    ids.add(new Identifier(id.getNodeName().getLocalName(), value));
                }
            }
        }
        XdmNode name = first(node, nameElement);
        if (name != null && nameElement.equals("Names")) {
            name = first(name, "OfficialName");
        }
        return new Party(ids, text(name));
    }

    /**
     * The {@code ListedType} values of a FundsXML schema file ({@code DocumentType/Type/ListedType}).
     *
     * @return the values, {@link #DEFAULT_LISTED_TYPES} when the file is missing or has none
     */
    public static List<String> listedTypes(Path schemaFile) {
        if (schemaFile == null || !Files.isRegularFile(schemaFile)) {
            return DEFAULT_LISTED_TYPES;
        }
        try {
            String xsd = Files.readString(schemaFile, StandardCharsets.UTF_8);
            int type = xsd.indexOf("name=\"DocumentType\"");
            Matcher element = Pattern.compile("<xs:element\\s+name=\"ListedType\"").matcher(xsd);
            int at = element.find(Math.max(0, type)) ? element.start() : -1;
            int end = at < 0 ? -1 : xsd.indexOf("</xs:restriction>", at);
            if (end < 0) {
                return DEFAULT_LISTED_TYPES;
            }
            List<String> values = new ArrayList<>();
            Matcher m = Pattern.compile("<xs:enumeration\\s+value=\"([^\"]*)\"").matcher(xsd.substring(at, end));
            while (m.find()) {
                values.add(unescape(m.group(1)));
            }
            return values.isEmpty() ? DEFAULT_LISTED_TYPES : List.copyOf(values);
        } catch (IOException | RuntimeException e) {
            return DEFAULT_LISTED_TYPES;
        }
    }

    // ---- helpers for new content -----------------------------------------------------------------------------

    /** @return the MIME type for the file name's extension, {@code application/octet-stream} when unknown */
    public static String mimeType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "html", "htm" -> "text/html";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt" -> "text/plain";
            case "xml" -> "application/xml";
            default -> "application/octet-stream";
        };
    }

    /** @return {@code bytes} as base64 in lines of {@link #BASE64_LINE} characters, joined by {@code lineBreak} */
    public static String base64Lines(byte[] bytes, String lineBreak) {
        return Base64.getMimeEncoder(BASE64_LINE, lineBreak.getBytes(StandardCharsets.US_ASCII)).encodeToString(bytes);
    }

    // ---- changes ---------------------------------------------------------------------------------------------

    /**
     * Embeds a file as a new {@code Document}: appended to {@code Documents}, which is created after
     * {@code ControlData} / {@code Funds} / {@code AssetMgmtCompanyDynData} / {@code AssetMasterData} when absent.
     *
     * @param today the {@code CreationDate}
     * @return the edit (inserting the element)
     * @throws IOException              when the text is not well-formed XML
     * @throws IllegalArgumentException when the file is empty or the type, language or assignment is missing
     */
    public static TextEdit embed(String xml, NewDocument file, LocalDate today) throws IOException {
        if (file.bytes() == null || file.bytes().length == 0) {
            throw new IllegalArgumentException("The file is empty.");
        }
        if (file.listedType() == null || file.listedType().isBlank()) {
            throw new IllegalArgumentException("No document type.");
        }
        if (file.language() == null || !LANGUAGE.matcher(file.language().strip()).matches()) {
            throw new IllegalArgumentException("Not a language code: " + file.language());
        }
        if (file.assignment() == null || file.assignment().parties().isEmpty()) {
            throw new IllegalArgumentException("No fund or share class to assign the document to.");
        }
        XdmNode root = root(parse(xml));
        Layout layout = new Layout(xml, root);
        String rootName = root.getNodeName().getLocalName();
        XdmNode documents = first(root, "Documents");
        if (documents != null) {
            ElementRegion region = region(xml, List.of(new Step(rootName, 1), new Step("Documents", 1)));
            String outer = XmlNodeSpanLocator.indentationAt(xml, region.span().start());
            String inner = outer + layout.unit;
            if (region.selfClosing()) {
                String element = layout.document(file, inner, today);
                return new TextEdit(region.span().start(), region.span().end(), "<" + layout.name("Documents") + ">"
                        + layout.lb + inner + element + layout.lb + outer + "</" + layout.name("Documents") + ">");
            }
            if (region.childElementSpans().isEmpty()) {
                return new TextEdit(region.contentStart(), region.contentEnd(),
                        layout.lb + inner + layout.document(file, inner, today) + layout.lb + outer);
            }
            Span last = region.childElementSpans().getLast();
            String indent = XmlNodeSpanLocator.indentationAt(xml, last.start());
            if (indent.length() <= outer.length()) {
                indent = inner;
            }
            return new TextEdit(last.end(), last.end(), layout.lb + indent + layout.document(file, indent, today));
        }
        // no Documents: after the last child that comes before it in the schema
        List<XdmNode> rootChildren = children(root, null);
        int anchor = -1;
        for (int i = 0; i < rootChildren.size(); i++) {
            if (BEFORE_DOCUMENTS.contains(rootChildren.get(i).getNodeName().getLocalName())) {
                anchor = i;
            }
        }
        ElementRegion rootRegion = region(xml, List.of(new Step(rootName, 1)));
        if (rootRegion.selfClosing()) {
            throw new IOException("The root element is empty.");
        }
        int at;
        String indent;
        if (anchor >= 0) {
            Span span = rootRegion.childElementSpans().get(anchor);
            at = span.end();
            indent = XmlNodeSpanLocator.indentationAt(xml, span.start());
            if (indent.isEmpty() && layout.unit.length() > 0) {
                indent = layout.unit;
            }
        } else {
            at = rootRegion.contentStart();
            indent = XmlNodeSpanLocator.indentationAt(xml, rootRegion.span().start()) + layout.unit;
        }
        String inner = indent + layout.unit;
        return new TextEdit(at, at, layout.lb + indent + "<" + layout.name("Documents") + ">" + layout.lb + inner
                + layout.document(file, inner, today) + layout.lb + indent + "</" + layout.name("Documents") + ">");
    }

    /**
     * Embeds the content of a (linked) document: {@code BinaryData} replaces its {@code DocumentURL}, and
     * {@code SizeInBytes} is set to the content's size.
     *
     * @param index the document's position among {@code Documents/Document} (1-based)
     * @return the edits (non-overlapping)
     */
    public static List<TextEdit> toEmbedded(String xml, int index, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("The file is empty.");
        }
        DocumentText doc = documentText(xml, index);
        Layout layout = doc.layout;
        List<TextEdit> edits = new ArrayList<>();
        int size = doc.indexOf("SizeInBytes");
        int url = doc.indexOf("DocumentURL");
        int binary = doc.indexOf("BinaryData");
        String sizeElement = layout.simple("SizeInBytes", String.valueOf(bytes.length));
        if (size >= 0) {
            edits.add(doc.setContent(size, String.valueOf(bytes.length)));
        }
        if (binary >= 0) {
            String indent = XmlNodeSpanLocator.indentationAt(xml, doc.span(binary).start());
            edits.add(doc.replace(binary, layout.binary(bytes, indent)));
            if (url >= 0) {
                edits.add(doc.remove(url));
            }
        } else if (url >= 0) {
            String indent = XmlNodeSpanLocator.indentationAt(xml, doc.span(url).start());
            edits.add(doc.replace(url, (size >= 0 ? "" : sizeElement + layout.lb + indent) + layout.binary(bytes, indent)));
        } else {
            String indent = doc.childIndent();
            edits.add(doc.append((size >= 0 ? "" : sizeElement + layout.lb + indent) + layout.binary(bytes, indent)));
        }
        return edits;
    }

    /**
     * Replaces an embedded document by a link: {@code DocumentURL} replaces its {@code BinaryData}, and
     * {@code SizeInBytes} is set to the size of the content it had.
     *
     * @param index the document's position among {@code Documents/Document} (1-based)
     * @return the edits (non-overlapping)
     */
    public static List<TextEdit> toLink(String xml, int index, String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("No URL.");
        }
        String link = url.strip();
        DocumentText doc = documentText(xml, index);
        Layout layout = doc.layout;
        int size = doc.indexOf("SizeInBytes");
        int urlAt = doc.indexOf("DocumentURL");
        int binary = doc.indexOf("BinaryData");
        long bytes = binary < 0 ? 0 : decodedLength(doc.nodes.get(binary).getStringValue());
        List<TextEdit> edits = new ArrayList<>();
        String sizePrefix = "";
        if (size >= 0 && bytes > 0) {
            edits.add(doc.setContent(size, String.valueOf(bytes)));
        } else if (bytes > 0) {
            int before = urlAt >= 0 ? urlAt : binary;
            sizePrefix = layout.simple("SizeInBytes", String.valueOf(bytes)) + layout.lb
                    + XmlNodeSpanLocator.indentationAt(xml, doc.span(before).start());
        }
        if (urlAt >= 0) {
            edits.add(sizePrefix.isEmpty() ? doc.setContent(urlAt, escape(link))
                    : doc.replace(urlAt, sizePrefix + layout.simple("DocumentURL", link)));
            if (binary >= 0) {
                edits.add(doc.remove(binary));
            }
        } else if (binary >= 0) {
            edits.add(doc.replace(binary, sizePrefix + layout.simple("DocumentURL", link)));
        } else {
            edits.add(doc.append(layout.simple("DocumentURL", link)));
        }
        return edits;
    }

    // ---- text layout -----------------------------------------------------------------------------------------

    /** Line break, indentation step and prefix of the document; writes new elements. */
    private static final class Layout {
        final String lb;
        final String unit;
        final String prefix;

        Layout(String xml, XdmNode root) {
            lb = xml.contains("\r\n") ? "\r\n" : "\n";
            String p = root.getNodeName().getPrefix();
            prefix = p == null || p.isEmpty() ? "" : p + ":";
            String step = "";
            Optional<ElementRegion> rootRegion = XmlNodeSpanLocator.elementRegion(xml,
                    List.of(new Step(root.getNodeName().getLocalName(), 1)));
            if (rootRegion.isPresent() && !rootRegion.get().childElementSpans().isEmpty()) {
                String rootIndent = XmlNodeSpanLocator.indentationAt(xml, rootRegion.get().span().start());
                String childIndent = XmlNodeSpanLocator.indentationAt(xml,
                        rootRegion.get().childElementSpans().getFirst().start());
                if (childIndent.startsWith(rootIndent) && childIndent.length() > rootIndent.length()) {
                    step = childIndent.substring(rootIndent.length());
                }
            }
            unit = step.isEmpty() ? "  " : step;
        }

        String name(String local) {
            return prefix + local;
        }

        String simple(String local, String value) {
            return "<" + name(local) + ">" + escape(value) + "</" + name(local) + ">";
        }

        /** {@code <BinaryData>} with the base64 lines one step deeper than {@code indent}. */
        String binary(byte[] bytes, String indent) {
            String inner = indent + unit;
            return "<" + name("BinaryData") + ">" + lb + inner + base64Lines(bytes, lb + inner) + lb + indent
                    + "</" + name("BinaryData") + ">";
        }

        /** The new {@code Document} element, starting at {@code indent} (not written before it). */
        String document(NewDocument file, String indent, LocalDate today) {
            String in = indent + unit;
            String name = file.name() == null || file.name().isBlank() ? baseName(file.fileName()) : file.name().strip();
            StringBuilder b = new StringBuilder();
            b.append('<').append(name("Document")).append('>');
            line(b, in, "<" + name("Type") + ">" + simple("ListedType", file.listedType().strip()) + "</" + name("Type") + ">");
            line(b, in, simple("Language", file.language().strip()));
            Assignment assignment = file.assignment();
            Level level = assignment.level();
            line(b, in, "<" + name(level.element) + ">");
            if (level == Level.FUND) {
                party(b, in + unit, assignment.parties().getFirst());
            } else {
                String itemIndent = in + unit;
                for (Party party : assignment.parties()) {
                    line(b, itemIndent, "<" + name(level.item) + ">");
                    party(b, itemIndent + unit, party);
                    line(b, itemIndent, "</" + name(level.item) + ">");
                }
            }
            line(b, in, "</" + name(level.element) + ">");
            if (!name.isEmpty()) {
                line(b, in, simple("Name", name));
            }
            if (file.fileName() != null && !file.fileName().isBlank()) {
                line(b, in, simple("FileName", file.fileName().strip()));
            }
            line(b, in, simple("Format", mimeType(file.fileName())));
            line(b, in, simple("CreationDate", today.toString()));
            line(b, in, simple("SizeInBytes", String.valueOf(file.bytes().length)));
            line(b, in, binary(file.bytes(), in));
            b.append(lb).append(indent).append("</").append(name("Document")).append('>');
            return b.toString();
        }

        /** {@code Identifiers} (ISIN before LEI, as in the schema) and {@code Name} of a party. */
        private void party(StringBuilder b, String indent, Party party) {
            line(b, indent, "<" + name("Identifiers") + ">");
            List<Identifier> ids = new ArrayList<>(party.identifiers());
            ids.sort((x, y) -> Integer.compare(order(x.element()), order(y.element())));
            for (Identifier id : ids) {
                line(b, indent + unit, simple(id.element(), id.value()));
            }
            line(b, indent, "</" + name("Identifiers") + ">");
            if (party.name() != null && !party.name().isBlank()) {
                line(b, indent, simple("Name", party.name().strip()));
            }
        }

        private void line(StringBuilder b, String indent, String text) {
            b.append(lb).append(indent).append(text);
        }
    }

    /** Position of an identifier in {@code IdentifiersType}. */
    private static int order(String element) {
        int i = List.of("ISIN", "Bloomberg", "CUSIP", "GermanWKN", "LEI").indexOf(element);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** A {@code Document} element in the text: its child elements and their spans. */
    private static final class DocumentText {
        final String xml;
        final Layout layout;
        final List<XdmNode> nodes;
        final ElementRegion region;

        DocumentText(String xml, Layout layout, List<XdmNode> nodes, ElementRegion region) {
            this.xml = xml;
            this.layout = layout;
            this.nodes = nodes;
            this.region = region;
        }

        int indexOf(String local) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).getNodeName().getLocalName().equals(local)) {
                    return i;
                }
            }
            return -1;
        }

        Span span(int child) {
            return region.childElementSpans().get(child);
        }

        TextEdit replace(int child, String text) {
            return new TextEdit(span(child).start(), span(child).end(), text);
        }

        /** Sets the text content of a simple child element (also a self-closing one). */
        TextEdit setContent(int child, String escaped) throws IOException {
            String local = nodes.get(child).getNodeName().getLocalName();
            Span span = span(child);
            // locate the child's own content: between the end of its start tag and its end tag
            String element = xml.substring(span.start(), span.end());
            if (element.endsWith("/>")) {
                return replace(child, "<" + layout.name(local) + ">" + escaped + "</" + layout.name(local) + ">");
            }
            int open = element.indexOf('>');
            int close = element.lastIndexOf("</");
            if (open < 0 || close < open) {
                throw new IOException("Cannot locate " + local + " in the text.");
            }
            return new TextEdit(span.start() + open + 1, span.start() + close, escaped);
        }

        /** Removes a child element and the white space before it. */
        TextEdit remove(int child) {
            int start = child == 0 ? region.contentStart() : span(child - 1).end();
            return new TextEdit(start, span(child).end(), "");
        }

        /** Inserts {@code text} as the last child (on its own line, indented like the other children). */
        TextEdit append(String text) {
            int at = nodes.isEmpty() ? region.contentStart() : span(nodes.size() - 1).end();
            return new TextEdit(at, at, layout.lb + childIndent() + text);
        }

        String childIndent() {
            String own = XmlNodeSpanLocator.indentationAt(xml, region.span().start());
            if (!nodes.isEmpty()) {
                String first = XmlNodeSpanLocator.indentationAt(xml, span(0).start());
                if (first.length() > own.length()) {
                    return first;
                }
            }
            return own + layout.unit;
        }
    }

    private static DocumentText documentText(String xml, int index) throws IOException {
        XdmNode root = root(parse(xml));
        XdmNode doc = document(root, index);
        ElementRegion region = region(xml, List.of(new Step(root.getNodeName().getLocalName(), 1),
                new Step("Documents", 1), new Step("Document", index)));
        if (region.selfClosing()) {
            throw new IOException("Document " + index + " is empty.");
        }
        List<XdmNode> nodes = children(doc, null);
        if (nodes.size() != region.childElementSpans().size()) {
            throw new IOException("Document " + index + " was not found in the text.");
        }
        return new DocumentText(xml, new Layout(xml, root), nodes, region);
    }

    // ---- parsing ---------------------------------------------------------------------------------------------

    private static XdmNode parse(String xml) throws IOException {
        try {
            var builder = PROCESSOR.newDocumentBuilder();
            builder.setLineNumbering(true);
            return builder.build(new StreamSource(new StringReader(xml)));
        } catch (SaxonApiException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static XdmNode root(XdmNode document) throws IOException {
        XdmNode root = document.getNodeKind() == XdmNodeKind.ELEMENT ? document
                : children(document, null).stream().findFirst().orElse(null);
        if (root == null) {
            throw new IOException("The document has no root element.");
        }
        return root;
    }

    private static List<XdmNode> documentNodes(XdmNode root) {
        List<XdmNode> result = new ArrayList<>();
        XdmNode documents = first(root, "Documents");
        if (documents != null) {
            result.addAll(children(documents, "Document"));
        }
        return result;
    }

    private static XdmNode document(XdmNode documentNode, int index) throws IOException {
        XdmNode root = documentNode.getNodeKind() == XdmNodeKind.DOCUMENT ? root(documentNode) : documentNode;
        List<XdmNode> documents = documentNodes(root);
        if (index < 1 || index > documents.size()) {
            throw new IOException("There is no Document " + index + ".");
        }
        return documents.get(index - 1);
    }

    private static ElementRegion region(String xml, List<Step> path) throws IOException {
        return XmlNodeSpanLocator.elementRegion(xml, path)
                .orElseThrow(() -> new IOException(path.getLast().localName() + " was not found in the text."));
    }

    /** @return the child elements of {@code parent} with this local name ({@code null} = all) */
    private static List<XdmNode> children(XdmNode parent, String localName) {
        List<XdmNode> result = new ArrayList<>();
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && (localName == null || localName.equals(child.getNodeName().getLocalName()))) {
                result.add(child);
            }
        }
        return result;
    }

    private static XdmNode first(XdmNode parent, String localName) {
        if (parent == null) {
            return null;
        }
        List<XdmNode> list = children(parent, localName);
        return list.isEmpty() ? null : list.getFirst();
    }

    private static String text(XdmNode node) {
        return node == null ? null : node.getStringValue().strip();
    }

    private static Long number(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String baseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName.strip();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
