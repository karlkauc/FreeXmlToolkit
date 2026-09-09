package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.SchematronServiceImpl;

/**
 * Pays the one-time, per-JVM cost of the Schematron engine before a test's timed waits start.
 * <p>
 * ph-schematron/SchXslt compiles its XSLT pipeline on the first validation in a JVM: measured at
 * ~7.3 s here, against ~20 ms for every later run. Tests that wait a few seconds for the first
 * Schematron result therefore time out through no fault of the code under test — and only the
 * <em>first</em> Schematron test of a class fails, which makes the failure look random.
 * <p>
 * Call this from a {@code @BeforeAll}, so the cost is paid on the JUnit thread rather than inside
 * a {@code WaitForAsyncUtils} budget or on the FX thread.
 */
final class SchematronWarmUp {

    private static final String TRIVIAL_SCHEMATRON = """
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
              <sch:pattern><sch:rule context="root">
                <sch:assert test="name">root must have a name child</sch:assert>
              </sch:rule></sch:pattern>
            </sch:schema>
            """;

    private static boolean done;

    private SchematronWarmUp() {
    }

    /** Runs one throwaway Schematron validation, unless this JVM already did. */
    static synchronized void warmUp() {
        if (done) {
            return;
        }
        done = true;
        try {
            Path sch = Files.createTempFile("warmup", ".sch");
            sch.toFile().deleteOnExit();
            Files.writeString(sch, TRIVIAL_SCHEMATRON);
            new SchematronServiceImpl().validateXmlWithSvrl("<root/>", sch.toFile());
        } catch (Exception e) {
            // A failed warm-up must never fail the test that asked for it; the test's own wait
            // still governs correctness, it just no longer pays the compile cost.
            System.err.println("Schematron warm-up skipped: " + e);
        }
    }
}
