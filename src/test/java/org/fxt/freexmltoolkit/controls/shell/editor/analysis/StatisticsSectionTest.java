package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure helpers of the Statistics / Quality sub-tabs. */
class StatisticsSectionTest {

    @Test
    void qualityCountTextDistinguishesFilteredFromUnfiltered() {
        assertEquals("1 issue", AnalysisSupport.countText(1, 1, "issue"));
        assertEquals("2251 issues", AnalysisSupport.countText(2251, 2251, "issue"));
        assertEquals("Showing 350 of 2251 issues", AnalysisSupport.countText(350, 2251, "issue"));
        assertEquals("Showing 0 of 1 issue", AnalysisSupport.countText(0, 1, "issue"));
    }

    @Test
    void xpathRowsJoinFindingsWithExpressions() throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType><xs:sequence>
                      <xs:element name="item" maxOccurs="unbounded">
                        <xs:complexType><xs:attribute name="id" type="xs:string"/></xs:complexType>
                      </xs:element>
                    </xs:sequence></xs:complexType>
                    <xs:key name="itemKey"><xs:selector xpath="item"/><xs:field xpath="@id"/></xs:key>
                    <xs:unique name="ghost"><xs:selector xpath="nosuch"/><xs:field xpath="@id"/></xs:unique>
                  </xs:element>
                </xs:schema>
                """;
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        SchemaAnalysisData data = SchemaAnalysisRunner.analyze(xsd, "t.xsd", null);
        java.util.List<XPathSection.Row> rows = XPathSection.buildRows(data);

        assertEquals(4, rows.size(), rows.toString());
        XPathSection.Row ghostSelector = rows.stream()
                .filter(r -> "ghost".equals(r.constraintName()) && "nosuch".equals(r.xpath()))
                .findFirst().orElseThrow();
        assertEquals(XPathSection.Status.WARNING, ghostSelector.status());
        assertTrue(ghostSelector.message().contains("nosuch"), ghostSelector.message());
        assertEquals("Selector", XPathSection.sourceLabel(ghostSelector.source()));
        long valid = rows.stream().filter(r -> r.status() == XPathSection.Status.VALID).count();
        assertEquals(3, valid);
        assertEquals("", rows.stream().filter(r -> "itemKey".equals(r.constraintName())).findFirst().orElseThrow().message());
    }

    @Test
    void coverageBandThresholds() {
        assertEquals("poor", StatisticsSection.coverageBand(0));
        assertEquals("poor", StatisticsSection.coverageBand(39.9));
        assertEquals("fair", StatisticsSection.coverageBand(40));
        assertEquals("fair", StatisticsSection.coverageBand(74.9));
        assertEquals("good", StatisticsSection.coverageBand(75));
        assertEquals("good", StatisticsSection.coverageBand(100));
    }
}
