package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonResourceAccess;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.ElementRegion;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Step;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;

/**
 * Sets the {@code TotalPercentage} of every portfolio position of a FundsXML 4 document: the position's
 * {@code TotalValue} in the currency of its fund, share class, segment or subfund as a percentage of that node's
 * {@code TotalNetAssetValue} on the portfolio's {@code NavDate}, rounded half-up to two decimals. A missing
 * {@code TotalPercentage} is inserted after {@code TotalValue} / {@code OtherTotalValues} (the schema's order); a
 * present one is kept when it rounds to the same value and replaced otherwise. The result is a list of text
 * edits that keep the rest of the document as it is.
 */
public final class FundsXmlPercentages {

    /**
     * A position whose {@code TotalPercentage} is set.
     *
     * @param from the value it had, {@code null} when the element was inserted
     * @param to   the new value (two decimals)
     * @param line line of the position (1-based)
     */
    public record Change(String position, String owner, String navDate, String from, String to, int line) {
        public boolean inserted() {
            return from == null;
        }
    }

    /** A position that could not be computed or located, and why. */
    public record Skip(String position, String owner, String navDate, String reason, int line) {
    }

    /**
     * What setting the percentages would do.
     *
     * @param edits     the text edits (non-overlapping), empty when nothing changes
     * @param unchanged positions whose value is already right
     */
    public record Plan(List<TextEdit> edits, List<Change> changes, int unchanged, List<Skip> skipped) {
        public Plan {
            edits = List.copyOf(edits);
            changes = List.copyOf(changes);
            skipped = List.copyOf(skipped);
        }

        public long inserted() {
            return changes.stream().filter(Change::inserted).count();
        }

        public long corrected() {
            return changes.size() - inserted();
        }
    }

    static final String NO_VALUE = "no TotalValue in %s";
    static final String NO_NAV = "no net asset value in %s for %s";
    static final String ZERO_NAV = "the net asset value is 0";
    static final String NOT_LOCATED = "not found in the text";

    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();
    private static final QName CCY = new QName("ccy");

    /** Per portfolio with positions: the node, its owner's currency and net asset value on the portfolio's date. */
    private static final String PORTFOLIOS = """
            for $p in //Portfolios/Portfolio[Positions/Position]
            let $ccy := local:owner-ccy($p)
            return map {
              'portfolio': $p,
              'owner': local:owner($p),
              'date': string($p/NavDate),
              'ccy': $ccy,
              'nav': string(($p/../../TotalAssetValues/TotalAssetValue[NavDate = $p/NavDate]
                             /TotalNetAssetValue/Amount[@ccy = $ccy])[1])
            }
            """;

    private FundsXmlPercentages() {
    }

    /**
     * Plans the {@code TotalPercentage} of every position of {@code //Portfolios/Portfolio/Positions/Position}.
     *
     * @throws IOException when the document is not well-formed
     */
    public static Plan plan(String xml) throws IOException {
        XdmNode document;
        XdmValue portfolios;
        try {
            var builder = PROCESSOR.newDocumentBuilder();
            builder.setLineNumbering(true);
            document = builder.build(new StreamSource(new StringReader(xml)));
            XQueryEvaluator evaluator = PROCESSOR.newXQueryCompiler()
                    .compile(FundsXmlDataQuality.prolog() + "\n" + PORTFOLIOS).load();
            evaluator.setContextItem(document);
            portfolios = evaluator.evaluate();
        } catch (SaxonApiException e) {
            throw new IOException(e.getMessage(), e);
        }

        List<Pending> pending = new ArrayList<>();
        List<Skip> skipped = new ArrayList<>();
        int unchanged = 0;
        for (XdmItem item : portfolios) {
            XdmMap map = (XdmMap) item;
            XdmNode portfolio = (XdmNode) map.get("portfolio");
            String owner = text(map, "owner");
            String date = text(map, "date");
            String ccy = text(map, "ccy");
            BigDecimal nav = decimal(text(map, "nav"));
            for (XdmNode positions : children(portfolio, "Positions")) {
                List<Step> positionsPath = XmlNodeSpanLocator.pathOf(positions);
                int index = 0;
                for (XdmNode position : children(positions, "Position")) {
                    index++;
                    String id = childText(position, "UniqueID");
                    int line = position.getLineNumber();
                    BigDecimal value = null;
                    for (XdmNode total : children(position, "TotalValue")) {
                        for (XdmNode amount : children(total, "Amount")) {
                            if (value == null && ccy.equals(amount.getAttributeValue(CCY))) {
                                value = decimal(amount.getStringValue());
                            }
                        }
                    }
                    String reason = value == null ? NO_VALUE.formatted(ccy)
                            : nav == null ? NO_NAV.formatted(ccy, date)
                            : nav.signum() == 0 ? ZERO_NAV : null;
                    if (reason != null) {
                        skipped.add(new Skip(id, owner, date, reason, line));
                        continue;
                    }
                    String to = value.multiply(BigDecimal.valueOf(100)).divide(nav, 2, RoundingMode.HALF_UP).toPlainString();
                    List<Step> path = new ArrayList<>(positionsPath);
                    path.add(new Step("Position", index));
                    XdmNode existing = children(position, "TotalPercentage").stream().findFirst().orElse(null);
                    if (existing != null) {
                        String from = existing.getStringValue();
                        BigDecimal current = decimal(from);
                        if (current != null && current.setScale(2, RoundingMode.HALF_UP).toPlainString().equals(to)) {
                            unchanged++;
                            continue;
                        }
                        pending.add(new Pending(new Change(id, owner, date, from.strip(), to, line),
                                withStep(path, existing), null));
                    } else {
                        XdmNode anchor = null;
                        for (XdmNode child : children(position, null)) {
                            String name = child.getNodeName().getLocalName();
                            if (name.equals("TotalValue") || name.equals("OtherTotalValues")) {
                                anchor = child;
                            }
                        }
                        if (anchor == null) {
                            skipped.add(new Skip(id, owner, date, NOT_LOCATED, line));
                            continue;
                        }
                        pending.add(new Pending(new Change(id, owner, date, null, to, line), null, withStep(path, anchor)));
                    }
                }
            }
        }
        return locate(xml, pending, skipped, unchanged);
    }

    /** A planned change and the element it edits: the {@code TotalPercentage} to fix or the anchor to insert after. */
    private record Pending(Change change, List<Step> percentage, List<Step> anchor) {
    }

    private static Plan locate(String xml, List<Pending> pending, List<Skip> skipped, int unchanged) {
        List<List<Step>> paths = pending.stream().map(p -> p.percentage() != null ? p.percentage() : p.anchor()).toList();
        List<Optional<ElementRegion>> regions = XmlNodeSpanLocator.elementRegions(xml, paths);
        List<TextEdit> edits = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            Pending p = pending.get(i);
            Change change = p.change();
            Optional<ElementRegion> region = regions.get(i);
            if (region.isEmpty()) {
                skipped.add(new Skip(change.position(), change.owner(), change.navDate(), NOT_LOCATED, change.line()));
                continue;
            }
            ElementRegion r = region.get();
            if (p.percentage() != null) {
                edits.add(r.selfClosing()
                        ? new TextEdit(r.span().start(), r.span().end(), element(xml, r, change.to()))
                        : new TextEdit(r.contentStart(), r.contentEnd(), change.to()));
            } else {
                int at = r.span().end();
                String inserted = element(xml, r, change.to());
                edits.add(new TextEdit(at, at, lineBreakFollows(xml, at)
                        ? lineBreak(xml, at) + XmlNodeSpanLocator.indentationAt(xml, r.span().start()) + inserted
                        : inserted));
            }
            changes.add(change);
        }
        return new Plan(edits, changes, unchanged, skipped);
    }

    /** @return {@code <TotalPercentage>value</TotalPercentage>} with the prefix of the element at {@code r} */
    private static String element(String xml, ElementRegion r, String value) {
        int nameStart = r.span().start() + 1;
        int nameEnd = nameStart;
        while (nameEnd < xml.length() && !Character.isWhitespace(xml.charAt(nameEnd))
                && xml.charAt(nameEnd) != '>' && xml.charAt(nameEnd) != '/') {
            nameEnd++;
        }
        String qname = xml.substring(nameStart, nameEnd);
        int colon = qname.indexOf(':');
        String name = (colon >= 0 ? qname.substring(0, colon + 1) : "") + "TotalPercentage";
        return "<" + name + ">" + value + "</" + name + ">";
    }

    /** @return whether only spaces and tabs stand between {@code at} and the next line break */
    private static boolean lineBreakFollows(String xml, int at) {
        int i = at;
        while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
            i++;
        }
        return i < xml.length() && (xml.charAt(i) == '\n' || xml.charAt(i) == '\r');
    }

    /** @return the document's line break at {@code at}: {@code \r\n} or {@code \n} */
    private static String lineBreak(String xml, int at) {
        int i = at;
        while (i < xml.length() && xml.charAt(i) != '\n' && xml.charAt(i) != '\r') {
            i++;
        }
        return xml.startsWith("\r\n", i) ? "\r\n" : "\n";
    }

    private static List<Step> withStep(List<Step> positionPath, XdmNode child) {
        String name = child.getNodeName().getLocalName();
        int position = 0;
        for (XdmNode sibling : children(child.getParent(), name)) {
            position++;
            if (sibling.equals(child)) {
                break;
            }
        }
        List<Step> path = new ArrayList<>(positionPath);
        path.add(new Step(name, position));
        return path;
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

    private static String childText(XdmNode parent, String localName) {
        return children(parent, localName).stream().findFirst().map(n -> n.getStringValue().strip()).orElse("?");
    }

    private static String text(XdmMap map, String key) {
        XdmValue value = map.get(key);
        return value instanceof XdmItem item ? item.getStringValue() : "";
    }

    private static BigDecimal decimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
