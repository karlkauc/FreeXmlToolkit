package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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

    private void openGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
    }
}
