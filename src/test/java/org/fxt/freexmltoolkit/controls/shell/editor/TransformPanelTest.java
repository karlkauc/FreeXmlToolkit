package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

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
 * TestFX verification of the Transform panel: XSLT transformation of the active
 * XML, and XPath evaluation, both producing output.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void transformsActiveXmlWithXslt(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
        assertTrue(panel.getOutputText().contains("<out>Hello</out>"), panel.getOutputText());
    }

    @Test
    void evaluatesXPathAgainstActiveXml(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("/greeting");
            panel.runXPath();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hello"));
        assertTrue(panel.getOutputText().contains("Hello"), panel.getOutputText());
    }

    @Test
    void evaluatesJsonPathAgainstActiveJson(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\"fund\":{\"id\":\"EAM_2024\"}}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("$.fund.id");
            panel.runXPath();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("EAM_2024"));
        assertTrue(panel.getOutputText().contains("EAM_2024"), panel.getOutputText());
    }

    @Test
    void passesParametersAndHonorsTextOutputFormat(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("param.xslt");
        Files.writeString(xslt, """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:param name="who" select="'nobody'"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/">Hi <xsl:value-of select="$who"/></xsl:template>
                </xsl:stylesheet>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.addParameter("who", "Karl");
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hi Karl"));
        assertTrue(panel.getOutputText().contains("Hi Karl"), panel.getOutputText());
        assertFalse(panel.getOutputText().contains("<?xml"), "text output must not be wrapped as XML");
    }

    @Test
    void loadsASavedQueryFileIntoTheField(@TempDir Path tmp) throws Exception {
        Path q = tmp.resolve("q.xpath");
        Files.writeString(q, "/root/item[@id]\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.loadQueryFromFile(q.toFile());
            return null;
        });
        assertEquals("/root/item[@id]", panel.getQueryText());
    }

    @Test
    void exposesSaveAndSavedQueryControls() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean hasSave = panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Save Query".equals(b.getText()));
        boolean hasSaved = panel.lookupAll(".menu-button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.MenuButton b && "Saved".equals(b.getText()));
        assertTrue(hasSave, "panel must offer a 'Save Query' action");
        assertTrue(hasSaved, "panel must offer a 'Saved' queries menu");
    }

    @Test
    void runsXQueryAgainstActiveXml(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
            panel.setXQuery("string(/greeting)");
            panel.runXQuery();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hello"));
        assertTrue(panel.getOutputText().contains("Hello"), panel.getOutputText());
    }

    private void openGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
    }
}
