package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the shipped real-world example Schematrons must compile and
 * validate on the ph-schematron/SchXslt backend exactly like user files do.
 */
class SchematronExamplesSmokeTest {

    @Test
    void eamExampleValidates() throws Exception {
        File sch = new File("release/examples/schematron/eam-rules.sch");
        File xml = new File("release/examples/schematron/eam_rules.xml");
        assertTrue(sch.exists() && xml.exists(), "example files must ship with the repo");

        SchematronService service = new SchematronServiceImpl();
        assertTrue(service.isValidSchematronFile(sch), "example rules must compile");
        List<SchematronService.SchematronValidationError> errors =
                service.validateXml(Files.readString(xml.toPath()), sch);
        assertNotNull(errors);
        // findings (if any) must carry the SVRL details the UI navigates by
        for (SchematronService.SchematronValidationError error : errors) {
            assertNotNull(error.message());
        }
    }

    @Test
    void quickFixDemoOffersAFixForEveryFinding() throws Exception {
        File sch = new File("release/examples/schematron/quickfix-demo.sch");
        File xml = new File("release/examples/schematron/quickfix-demo.xml");
        assertTrue(sch.exists() && xml.exists(), "SQF demo files must ship with the repo");

        String content = Files.readString(xml.toPath());
        SchematronService.SchematronReport report =
                new SchematronServiceImpl().validateXmlWithSvrl(content, sch);
        var catalog = org.fxt.freexmltoolkit.service.sqf.SqfParser.parse(sch);
        var findings = org.fxt.freexmltoolkit.service.sqf.SqfCorrelator.correlate(report.svrl(), catalog);

        assertTrue(findings.size() >= 7, "the demo document must trigger all rules: " + findings.size());
        for (var finding : findings) {
            assertTrue(!finding.fixes().isEmpty(),
                    "every demo finding must offer a quick fix: " + finding.test());
        }
    }

    @Test
    void allShippedSchematronExamplesCompile() {
        File dir = new File("release/examples/schematron");
        File[] schematrons = dir.listFiles((d, name) -> name.endsWith(".sch"));
        assertNotNull(schematrons);
        assertTrue(schematrons.length > 0);
        SchematronService service = new SchematronServiceImpl();
        for (File sch : schematrons) {
            assertTrue(service.isValidSchematronFile(sch),
                    "example must compile on the SchXslt backend: " + sch.getName());
        }
    }
}
