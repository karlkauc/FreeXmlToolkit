package org.fxt.freexmltoolkit.service.standards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationStandardsTest {

    private static final String XSD11 = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence><xs:element name="n" type="xs:int"/></xs:sequence>
                  <xs:assert test="n gt 0"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @Test
    void xsdVersionIsDetectedOrForced() throws Exception {
        Path xsd = dir.resolve("s.xsd");
        Files.writeString(xsd, XSD11);
        String xml = "<root><n>-1</n></root>";
        var auto = ValidationRunner.runWithReport(xml, xsd.toFile(), null, "d", null, true,
                ValidationStandards.DEFAULT).problems();
        assertTrue(auto.stream().anyMatch(p -> p.message().contains("assert")), auto.toString());

        var forced10 = ValidationRunner.runWithReport(xml, xsd.toFile(), null, "d", null, true,
                new ValidationStandards(XsdVersionMode.V1_0, null, null)).problems();
        assertTrue(forced10.stream().anyMatch(p -> p.message().contains("XSD 1.0 is selected")), forced10.toString());
    }

    @Test
    void xmlVersionCheck() {
        assertEquals("1.1", XmlVersionCheck.declaredVersion("<?xml version='1.1'?><a/>"));
        assertEquals("1.0", XmlVersionCheck.declaredVersion("<a/>"));
        assertEquals(1, XmlVersionCheck.check("<?xml version=\"1.1\"?><a/>", XmlVersionMode.V1_0).size());
        assertEquals(List.of(), XmlVersionCheck.check("<?xml version=\"1.1\"?><a/>", XmlVersionMode.V1_1));
        assertEquals(List.of(), XmlVersionCheck.check("<a/>", XmlVersionMode.V1_1));
        assertEquals(List.of(), XmlVersionCheck.check("<?xml version=\"1.1\"?><a/>", XmlVersionMode.AUTO));
    }

    @Test
    void schematronBindingIsEnforced() {
        String sch = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <pattern><rule context="r">
                    <assert test="exists(head(a))">x</assert>
                    <report test="for $a in a return $a">y</report>
                    <assert test="count(a) = count(current()/a)">z</assert>
                  </rule></pattern>
                </schema>
                """;
        var v2 = SchematronCompatibilityCheck.check(sch, SchematronBinding.XSLT2);
        assertEquals(1, v2.size(), v2.toString());
        assertTrue(v2.getFirst().message().contains("head"), v2.toString());
        var v1 = SchematronCompatibilityCheck.check(sch, SchematronBinding.XSLT);
        assertEquals(2, v1.size(), v1.toString());
        assertEquals(List.of(), SchematronCompatibilityCheck.check(sch, SchematronBinding.XSLT3));
        String declared = sch.replace("<schema ", "<schema queryBinding=\"xslt3\" ");
        assertEquals(List.of(), SchematronCompatibilityCheck.check(declared, SchematronBinding.XSLT));
    }

    @Test
    void profileLayers() {
        var global = StandardsProfile.LATEST.withXpath(XPathVersion.V2_0);
        var project = StandardsProfile.EMPTY.withXsd(XsdVersionMode.V1_0);
        var tool = StandardsProfile.EMPTY.withXpath(XPathVersion.V3_0);
        var cli = StandardsProfile.EMPTY.withXsd(XsdVersionMode.V1_1);
        var resolved = StandardsProfile.resolve(global, project, tool, cli);
        assertEquals(XPathVersion.V3_0, resolved.xpath());
        assertEquals(XsdVersionMode.V1_1, resolved.xsd());
        assertEquals(XsltVersion.V3_0, resolved.xslt());
        assertTrue(StandardsProfile.LATEST.isLatest());
        assertFalse(resolved.isLatest());
    }

    @Test
    void settingsWithoutExpertModeAreLatest() {
        var props = new java.util.HashMap<String, String>();
        props.put(StandardsSettings.KEY_XPATH, "2.0");
        props.put(StandardsSettings.toolKey(StandardsSettings.TOOL_QUERY_CONSOLE, "xpath"), "1.0");
        var settings = new StandardsSettings(props::get, props::put);
        assertEquals(StandardsProfile.LATEST, settings.global());
        assertEquals(StandardsProfile.LATEST, settings.forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null));
        props.put(StandardsSettings.KEY_EXPERT_MODE, "true");
        assertEquals(XPathVersion.V2_0, settings.global().xpath());
        assertEquals(XPathVersion.V1_0, settings.forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null).xpath());
        settings.setToolChoice(StandardsSettings.TOOL_QUERY_CONSOLE, "xpath", null);
        assertEquals(XPathVersion.V2_0, settings.forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null).xpath());
    }

    @Test
    void projectBindingsCarryStandards() throws Exception {
        Path file = dir.resolve("p.fxtproj");
        Files.writeString(file, """
                {"name":"p","folders":["."],"schemaBindings":[
                  {"files":"legacy/**/*.xml","xsdVersion":"1.0","xmlVersion":"1.0"},
                  {"files":"**/*.xml","schematronBinding":"xslt3","xsdVersion":"1.1"}]}
                """);
        FxtProject project = FxtProject.load(file);
        var legacy = project.standardsFor(dir.resolve("legacy/a/x.xml"));
        assertEquals(XsdVersionMode.V1_0, legacy.xsd());
        assertEquals(XmlVersionMode.V1_0, legacy.xml());
        assertEquals(SchematronBinding.XSLT3, legacy.schematron());
        var other = project.standardsFor(dir.resolve("b.xml"));
        assertEquals(XsdVersionMode.V1_1, other.xsd());
        assertEquals(null, other.xml());
        var copy = project.schemaBindings().getFirst().copy();
        assertEquals("1.0", copy.xsdVersion);
    }
}
