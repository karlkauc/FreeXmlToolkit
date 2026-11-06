package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A transform result opened in its own tab gets a schema (validation + IntelliSense): the one it
 * declares - relative locations resolve against the input's folder - or else the input's schema,
 * when that declares the result's root element. The validation-time reconcile keeps the binding,
 * and so does a re-run.
 */
@ExtendWith(ApplicationExtension.class)
class TransformResultSchemaBindingTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="report"><xs:complexType><xs:sequence>
                <xs:element name="line" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;
    /** Copies the input unchanged. */
    private static final String IDENTITY = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:mode on-no-match="shallow-copy"/>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        panel.setAutoOpenResultTab(true);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void relativeLocationInTheResultResolvesAgainstTheInputFolder(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Path xsd = in.resolve("out.xsd");
        Files.writeString(xsd, XSD);
        // the input has no schema; the stylesheet writes a relative reference into the result
        openInput(in.resolve("doc.xml"), "<report><line>a</line></report>");
        OpenDocument result = transform(tmp, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <xsl:output method="xml"/>
                  <xsl:template match="/report">
                    <report xsi:noNamespaceSchemaLocation="out.xsd"><xsl:copy-of select="node()"/></report>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        assertEquals(xsd.toFile().getCanonicalFile(), waitForSchema(result).getCanonicalFile());
        assertKeptByReconcile(result);
    }

    @Test
    void resultInheritsTheInputSchemaWhenItsOwnReferenceIsUnresolvable(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("bound.xsd");
        Files.writeString(xsd, XSD);
        // declared location does not exist (like an unreachable UNC path); the user bound the XSD by hand
        Path input = tmp.resolve("doc.xml");
        openInput(input, """
                <report xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:noNamespaceSchemaLocation="missing/none.xsd"><line>a</line></report>""");
        bindManually(xsd.toFile());

        OpenDocument result = transform(tmp, IDENTITY);
        assertEquals(xsd.toFile(), waitForSchema(result));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.activeSchemaSourceDetailProperty().get() != null);
        assertTrue(host.activeSchemaSourceDetailProperty().get().contains("transform input"),
                host.activeSchemaSourceDetailProperty().get());
        assertKeptByReconcile(result);

        // a re-run updates the same tab and keeps its schema
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.transform();
            return null;
        });
        Thread.sleep(800);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(xsd.toFile(), host.schemaFileOf(result));
    }

    @Test
    void anUnrelatedResultDoesNotInheritTheInputSchema(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("bound.xsd");
        Files.writeString(xsd, XSD);
        openInput(tmp.resolve("doc.xml"), "<report><line>a</line></report>");
        bindManually(xsd.toFile());

        OpenDocument result = transform(tmp, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="xml"/>
                  <xsl:template match="/"><summary count="{count(//line)}"/></xsl:template>
                </xsl:stylesheet>
                """);
        // give the detection time to finish, then make sure nothing was bound
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(host.schemaFileOf(result), "<summary> is not declared by the input's schema");
    }

    // ----- helpers -------------------------------------------------------------

    private void openInput(Path xml, String content) throws Exception {
        Files.writeString(xml, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("line")).orElse(false));
        Thread.sleep(300); // the open-time schema detection of the input
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void bindManually(File xsd) throws Exception {
        var bound = WaitForAsyncUtils.asyncFx(() -> host.setSchemaForActiveDocument(xsd)).get(5, TimeUnit.SECONDS);
        assertTrue(bound.get(10, TimeUnit.SECONDS), "the input's schema must bind");
    }

    private OpenDocument transform(Path tmp, String xslt) throws Exception {
        Path sheet = tmp.resolve("t.xslt");
        Files.writeString(sheet, xslt);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> "Transform-Result.xml".equals(d.getDisplayName())));
        return host.getOpenDocuments().stream()
                .filter(d -> "Transform-Result.xml".equals(d.getDisplayName())).findFirst().orElseThrow();
    }

    private File waitForSchema(OpenDocument result) throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.schemaFileOf(result) != null);
        return host.schemaFileOf(result);
    }

    /** The validation-time reconcile (what every validation run does) must keep the binding. */
    private void assertKeptByReconcile(OpenDocument result) throws Exception {
        File before = host.schemaFileOf(result);
        String text = WaitForAsyncUtils.asyncFx(() -> host.getActiveText().orElse("")).get(5, TimeUnit.SECONDS);
        var supplier = WaitForAsyncUtils.asyncFx(() -> host.schemaBinding.schemaForValidation(text))
                .get(5, TimeUnit.SECONDS);
        File forValidation = java.util.concurrent.CompletableFuture.supplyAsync(supplier::get).get(15, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(before, forValidation, "validation uses the bound schema");
        assertEquals(before, host.schemaFileOf(result), "the reconcile keeps the binding");
    }
}
