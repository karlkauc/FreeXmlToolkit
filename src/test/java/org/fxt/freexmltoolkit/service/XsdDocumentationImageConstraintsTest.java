package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.domain.XsdAssertion;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The documentation diagrams list an element's identity constraints and assertions inside its
 * box, as the cards of the Graphic view do, unless that is switched off.
 */
class XsdDocumentationImageConstraintsTest {

    @TempDir
    Path tempDir;

    private static final String LONG_FIELD = "aFieldNameMuchLongerThanTheLimitOfOneDiagramLineEvenInTheDocumentation";

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="BaseType">
                    <xs:sequence>
                        <xs:element name="v" type="xs:int"/>
                    </xs:sequence>
                    <xs:assert test="v &gt; 0"/>
                </xs:complexType>
                <xs:complexType name="DerivedType">
                    <xs:complexContent>
                        <xs:extension base="BaseType">
                            <xs:sequence>
                                <xs:element name="w" type="xs:int" minOccurs="0"/>
                            </xs:sequence>
                            <xs:assert test="not(w) or w &lt; 100"/>
                        </xs:extension>
                    </xs:complexContent>
                </xs:complexType>
                <xs:simpleType name="Positive">
                    <xs:restriction base="xs:decimal">
                        <xs:assertion test="$value &gt; 0"/>
                    </xs:restriction>
                </xs:simpleType>
                <xs:complexType name="PriceType">
                    <xs:simpleContent>
                        <xs:extension base="Positive">
                            <xs:attribute name="ccy" type="xs:string"/>
                            <xs:assert test="@ccy"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
                <xs:element name="Library">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Book" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="ISBN" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="Loan" minOccurs="0" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="BookRef" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="Measure" type="DerivedType"/>
                            <xs:element name="Price" type="PriceType">
                                <xs:unique name="CcyOnce"><xs:selector xpath="."/><xs:field xpath="@ccy"/></xs:unique>
                                <xs:unique name="Ghost"><xs:selector xpath="Missing"/><xs:field xpath="x"/></xs:unique>
                            </xs:element>
                            <xs:element name="Busy">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="a" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                                <xs:unique name="U1"><xs:selector xpath="a"/><xs:field xpath="%1$s"/></xs:unique>
                                <xs:unique name="U2"><xs:selector xpath="a"/><xs:field xpath="."/></xs:unique>
                                <xs:unique name="U3"><xs:selector xpath="a"/><xs:field xpath="."/></xs:unique>
                                <xs:unique name="U4"><xs:selector xpath="a"/><xs:field xpath="."/></xs:unique>
                                <xs:unique name="U5"><xs:selector xpath="a"/><xs:field xpath="."/></xs:unique>
                                <xs:unique name="U6"><xs:selector xpath="a"/><xs:field xpath="."/></xs:unique>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                    <xs:key name="BookKey">
                        <xs:selector xpath="Book"/>
                        <xs:field xpath="ISBN"/>
                    </xs:key>
                    <xs:unique name="LoanUnique">
                        <xs:selector xpath="Loan"/>
                        <xs:field xpath="BookRef"/>
                    </xs:unique>
                    <xs:keyref name="LoanBook" refer="BookKey">
                        <xs:selector xpath="Loan"/>
                        <xs:field xpath="BookRef"/>
                    </xs:keyref>
                </xs:element>
            </xs:schema>
            """.formatted(LONG_FIELD);

    private XsdDocumentationService service;
    private XsdDocumentationImageService imageService;

    @BeforeEach
    void setUp() throws Exception {
        File xsdFile = tempDir.resolve("library.xsd").toFile();
        Files.writeString(xsdFile.toPath(), XSD);

        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.processXsd(false);

        imageService = new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap());
    }

    @Test
    void rootBoxListsItsIdentityConstraints() {
        String svg = imageService.generateSvgString(element("Library"));

        assertTrue(svg.contains(">KEY</text>"), "key label");
        assertTrue(svg.contains(">UNIQ</text>"), "unique label");
        assertTrue(svg.contains(">REF</text>"), "keyref label");
        List<String> rows = rowTexts(svg);
        assertTrue(rows.contains("BookKey  Book / ISBN"), "key: name, selector and field: " + rows);
        assertTrue(rows.contains("LoanUnique  Loan / BookRef"), "unique: name, selector and field: " + rows);
        assertTrue(rows.contains("LoanBook  Loan / BookRef → BookKey"), "keyref: … → refer: " + rows);
        assertTrue(svg.contains("selector: Book"), "the tooltip has the details");
    }

    @Test
    void childBoxListsOwnThenInheritedAssertions() {
        String svg = imageService.generateSvgString(element("Library"));

        int own = svg.indexOf(">not(w) or w &lt; 100</text>");
        int inherited = svg.indexOf(">v &gt; 0</text>");
        assertTrue(own > 0, "the derived type's own assert");
        assertTrue(inherited > 0, "the base type's assert");
        assertTrue(own < inherited, "own assertions come first");
        assertTrue(svg.contains(">ASSERT</text>"), "assert label");
    }

    @Test
    void longListsEndInAMoreLineAndLongTextsAreShortened() {
        String svg = imageService.generateSvgString(element("Library"));

        assertTrue(svg.contains(">+2 more"), "6 constraints: 4 rows and +2 more");
        String u1 = rowTexts(svg).stream().filter(t -> t.startsWith("U1  ")).findFirst()
                .orElseThrow(() -> new AssertionError("U1 row expected"));
        assertEquals(DiagramConstraint.MAX_TEXT_LENGTH, u1.length(), u1);
        assertTrue(u1.endsWith("…"), u1);
    }

    @Test
    void boxesGrowAndTheNextOneStartsBelow() {
        String svg = imageService.generateSvgString(element("Library"));
        double withConstraints = rectHeight(svg, "Measure");

        double previousBottom = 0;
        for (String name : List.of("Book", "Loan", "Measure", "Price", "Busy")) {
            Matcher rect = rect(svg, name);
            double top = attr(rect.group(), "y");
            assertTrue(top >= previousBottom, name + " starts at " + top + ", above the previous box's end " + previousBottom);
            previousBottom = top + attr(rect.group(), "height");
        }

        imageService.setShowConstraints(false);
        String plain = imageService.generateSvgString(element("Library"));
        assertTrue(withConstraints > rectHeight(plain, "Measure"), "the constraint rows make the box taller");
    }

    @Test
    void switchedOffTheBoxesListNothing() {
        imageService.setShowConstraints(false);
        String svg = imageService.generateSvgString(element("Library"));

        assertFalse(svg.contains("constraint-row"), "no constraint rows");
        assertFalse(svg.contains(" more"), "no +N more line");
        assertFalse(svg.contains(">KEY</text>"), "no labels");
    }

    @Test
    void selectorFieldsAndReferencedKeyLinkToTheirPages() {
        String svg = imageService.generateSvgString(element("Library"));

        assertLink(svg, "Book", element("Book").getPageName());
        assertLink(svg, "ISBN", element("ISBN").getPageName());
        assertLink(svg, "Loan", element("Loan").getPageName());
        assertLink(svg, "BookRef", element("BookRef").getPageName());
        assertLink(svg, "BookKey", element("Library").getPageName() + "#identity-constraints");
    }

    @Test
    void attributeFieldsLinkAndUnknownPathsStayText() {
        String svg = imageService.generateSvgString(element("Library"));

        assertLink(svg, ".", element("Price").getPageName());
        XsdExtendedElement ccy = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "@ccy".equals(e.getElementName())).findFirst().orElseThrow();
        assertLink(svg, "@ccy", ccy.getPageName());
        assertTrue(rowTexts(svg).contains("Ghost  Missing / x"), "the selector is still shown: " + rowTexts(svg));
        assertFalse(svg.contains(">Missing</a>"), "a path that selects nothing gets no link");
    }

    @Test
    void rootConstraintsCanBeLeftToTheChildBoxes() {
        imageService.setShowConstraints(false);
        double plainRoot = rootBoxHeight(imageService.generateSvgString(element("Library")));

        imageService.setShowConstraints(true);
        imageService.setShowRootConstraints(false);
        String svg = imageService.generateSvgString(element("Library"));

        List<String> rows = rowTexts(svg);
        assertTrue(rows.stream().noneMatch(r -> r.startsWith("BookKey") || r.startsWith("LoanUnique")
                || r.startsWith("LoanBook")), "the page's own box lists nothing: " + rows);
        assertTrue(svg.contains(">not(w) or w &lt; 100</text>"), "the child boxes keep their rows");
        assertTrue(rows.stream().anyMatch(r -> r.startsWith("CcyOnce")), "the child boxes keep their rows: " + rows);
        assertEquals(plainRoot, rootBoxHeight(svg), 0.01, "the page's own box is drawn as without constraints");
    }

    private static double rootBoxHeight(String svg) {
        Matcher rect = Pattern.compile("<rect[^>]*data-root=\"true\"[^>]*>", Pattern.DOTALL).matcher(svg);
        assertTrue(rect.find(), "root box expected");
        return attr(rect.group(), "height");
    }

    @Test
    void longPathsShowTheirLastStepBeforeTheLineIsCut() {
        var segments = DiagramConstraint.shorten(List.of(
                new DiagramConstraint.Segment("benchmarkID  ", DiagramConstraint.Role.TEXT, null),
                new DiagramConstraint.Segment("FundStaticData/Benchmarks/Benchmark", DiagramConstraint.Role.SELECTOR,
                        "FundStaticData/Benchmarks/Benchmark"),
                new DiagramConstraint.Segment(" / ", DiagramConstraint.Role.TEXT, null),
                new DiagramConstraint.Segment("BenchmarkID", DiagramConstraint.Role.FIELD, "BenchmarkID")));

        assertEquals("…/Benchmark", segments.get(1).text());
        assertEquals("FundStaticData/Benchmarks/Benchmark", segments.get(1).path(), "the link keeps the full path");
        assertEquals("BenchmarkID", segments.get(3).text(), "the field stays visible");
    }

    private static void assertLink(String svg, String text, String href) {
        assertNotNull(href, text + ": the target has no page");
        Pattern link = Pattern.compile("<a[^>]*href=\"" + Pattern.quote(href) + "\"[^>]*>"
                + Pattern.quote(text) + "</a>", Pattern.DOTALL);
        assertTrue(link.matcher(svg).find(), text + " should link to " + href);
    }

    /** @return the visible text of every constraint line (the kind label excluded) */
    private static List<String> rowTexts(String svg) {
        List<String> texts = new java.util.ArrayList<>();
        // The line text is set in 10px, its kind label in 9px
        Matcher row = Pattern.compile("<text[^>]*font-size=\"10\"[^>]*>(.*?)</text>", Pattern.DOTALL).matcher(svg);
        while (row.find()) {
            texts.add(row.group(1).replaceAll("<[^>]+>", "")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").strip());
        }
        return texts;
    }

    @Test
    void assertionsAreCollectedFromDerivationsAndBaseTypesOnce() {
        assertEquals(List.of("not(w) or w < 100", "v > 0"), tests(element("Measure")));
        // xs:assertion of the simple base type and xs:assert of the simpleContent extension
        List<String> price = tests(element("Price"));
        assertTrue(price.contains("$value > 0"), price.toString());
        assertTrue(price.contains("@ccy"), price.toString());
        assertEquals(price.size(), price.stream().distinct().count(), "each assertion once: " + price);
    }

    private static List<String> tests(XsdExtendedElement element) {
        return element.getAssertions().stream().map(XsdAssertion::getTest).toList();
    }

    private XsdExtendedElement element(String name) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Element " + name + " not found"));
    }

    private static Matcher rect(String svg, String name) {
        Matcher rect = Pattern.compile("<rect[^>]*\\sid=\"[^\"]*/" + name + "\"[^>]*>", Pattern.DOTALL).matcher(svg);
        assertTrue(rect.find(), name + " box expected");
        return rect;
    }

    private static double rectHeight(String svg, String name) {
        return attr(rect(svg, name).group(), "height");
    }

    private static double attr(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + name + "=\"([^\"]+)\"").matcher(tag);
        assertTrue(m.find(), name + " missing in " + tag);
        return Double.parseDouble(m.group(1));
    }
}
