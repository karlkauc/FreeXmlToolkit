package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The documentation lists a constraint on its field targets too, when that is switched on. */
class IdentityConstraintTargetDocTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="Fund">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Benchmarks">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="Benchmark" maxOccurs="unbounded">
                                            <xs:complexType>
                                                <xs:sequence>
                                                    <xs:element name="BenchmarkID" type="xs:string"/>
                                                </xs:sequence>
                                                <xs:attribute name="code" type="xs:string"/>
                                            </xs:complexType>
                                        </xs:element>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="Ref" type="xs:string" minOccurs="0"/>
                        </xs:sequence>
                    </xs:complexType>
                    <xs:key name="BenchmarkKey">
                        <xs:selector xpath="Benchmarks/Benchmark"/>
                        <xs:field xpath="BenchmarkID"/>
                    </xs:key>
                    <xs:unique name="CodeOnce">
                        <xs:selector xpath=".//Benchmark"/>
                        <xs:field xpath="@code"/>
                    </xs:unique>
                    <xs:keyref name="RefToBenchmark" refer="BenchmarkKey">
                        <xs:selector xpath="."/>
                        <xs:field xpath="Ref"/>
                    </xs:keyref>
                </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path tempDir;

    private XsdDocumentationService service;
    private XsdDocumentationImageService imageService;

    @BeforeEach
    void setUp() throws Exception {
        File xsdFile = tempDir.resolve("fund.xsd").toFile();
        Files.writeString(xsdFile.toPath(), XSD);
        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.processXsd(false);
        imageService = new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap());
    }

    private XsdExtendedElement element(String name) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst().orElseThrow(() -> new AssertionError(name));
    }

    @Test
    void theIndexMapsFieldTargetsToTheirConstraints() {
        IdentityConstraintTargetIndex index =
                IdentityConstraintTargetIndex.build(service.xsdDocumentationData.getExtendedXsdElementMap());

        List<IdentityConstraintTargetIndex.Incoming> id = index.of(element("BenchmarkID").getCurrentXpath());
        assertEquals(List.of("BenchmarkKey"), id.stream().map(i -> i.constraint().getName()).toList());
        assertEquals("Fund", id.getFirst().declaringName());
        assertNull(id.getFirst().attribute());

        List<IdentityConstraintTargetIndex.Incoming> benchmark = index.of(element("Benchmark").getCurrentXpath());
        assertEquals(1, benchmark.size(), "the attribute field is attached to its element");
        assertEquals("@code", benchmark.getFirst().attribute());

        assertEquals(List.of("RefToBenchmark"),
                index.of(element("Ref").getCurrentXpath()).stream().map(i -> i.constraint().getName()).toList());
        assertTrue(index.of(element("Benchmarks").getCurrentXpath()).isEmpty());
    }

    @Test
    void offByDefaultNothingChanges() {
        assertFalse(imageService.isShowConstraintTargets());
        assertTrue(imageService.incomingConstraints(element("BenchmarkID")).isEmpty());
        assertTrue(imageService.constraintRows(element("BenchmarkID")).isEmpty());
    }

    @Test
    void switchedOnTheTargetBoxListsTheConstraintLinkedToItsOwner() {
        imageService.setShowConstraintTargets(true);

        List<DiagramConstraint> rows = imageService.constraintRows(element("BenchmarkID"));
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().incoming());
        assertEquals("← BenchmarkKey  @ Fund", rows.getFirst().text());
        assertEquals(List.of("← CodeOnce @code  @ Fund"),
                imageService.constraintRows(element("Benchmark")).stream().map(DiagramConstraint::text).toList());
        assertEquals(List.of("← RefToBenchmark → BenchmarkKey  @ Fund"),
                imageService.constraintRows(element("Ref")).stream().map(DiagramConstraint::text).toList());

        String svg = imageService.generateSvgString(element("Benchmark"));
        assertTrue(svg.contains("constraint-incoming"), "the row is marked");
        assertTrue(svg.contains("href=\"" + element("Fund").getPageName() + "#identity-constraints\""),
                "the owner links to its constraints");
    }

    @Test
    void withoutDiagramConstraintsTheBoxesStayEmpty() {
        imageService.setShowConstraintTargets(true);
        imageService.setShowConstraints(false);
        assertTrue(imageService.constraintRows(element("BenchmarkID")).isEmpty());
        assertEquals(1, imageService.incomingConstraints(element("BenchmarkID")).size(),
                "the detail page section does not depend on the diagram option");
    }

    @Test
    void theDetailPageGetsASectionOnlyWhenSwitchedOn() throws Exception {
        Path off = tempDir.resolve("off");
        Path on = tempDir.resolve("on");
        Files.createDirectories(off);
        Files.createDirectories(on);

        service.generateXsdDocumentation(off.toFile());
        String offPage = Files.readString(off.resolve("details").resolve(element("BenchmarkID").getPageName()));
        assertFalse(offPage.contains("Referenced by Identity Constraints"));

        XsdDocumentationService enabled = new XsdDocumentationService();
        enabled.setXsdFilePath(tempDir.resolve("fund.xsd").toString());
        enabled.setShowConstraintTargets(true);
        enabled.generateXsdDocumentation(on.toFile());
        Map<String, XsdExtendedElement> map = enabled.xsdDocumentationData.getExtendedXsdElementMap();
        XsdExtendedElement id = map.values().stream().filter(e -> "BenchmarkID".equals(e.getElementName())).findFirst().orElseThrow();
        XsdExtendedElement fund = map.values().stream().filter(e -> "Fund".equals(e.getElementName())).findFirst().orElseThrow();
        String onPage = Files.readString(on.resolve("details").resolve(id.getPageName()));
        assertTrue(onPage.contains("Referenced by Identity Constraints"), "section shown");
        assertTrue(onPage.contains("BenchmarkKey"));
        assertTrue(onPage.contains(fund.getPageName() + "#identity-constraints"), "linked to the owner");
    }
}
