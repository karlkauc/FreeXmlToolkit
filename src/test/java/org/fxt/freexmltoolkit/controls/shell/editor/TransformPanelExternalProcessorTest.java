package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.xsltproc.ExternalXsltRunnerTest;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Transform panel runs the stylesheet with the external processor chosen in its Processor combo. */
@ExtendWith(ApplicationExtension.class)
class TransformPanelExternalProcessorTest {

    private EditorHost host;
    private TransformPanel panel;
    private final XsltProcessorConfig fake = ExternalXsltRunnerTest.fake("ok", 60);

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        XsltProcessorStore store = XsltProcessorStore.settings();
        store.save(List.of(fake));
        store.setLastId(fake.id());
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @AfterEach
    void forget() {
        XsltProcessorStore store = XsltProcessorStore.settings();
        store.save(List.of());
        store.setLastId(XsltProcessorStore.BUILTIN_ID);
    }

    @Test
    void runsWithTheChosenProcessor(@TempDir Path tmp) throws Exception {
        assertTrue(fake.id().equals(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.xsltProcessor() == null ? null
                : panel.xsltProcessor().id())), "the remembered choice");
        CheckBox profile = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (CheckBox) panel.lookup("#transform-profile"));
        if (profile != null) {
            assertTrue(profile.isDisabled(), "profiling needs Saxon HE");
        }
        Path xml = Files.writeString(tmp.resolve("doc.xml"), "<who>World</who>");
        Path xslt = Files.writeString(tmp.resolve("t.xsl"), """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/">vendor=<xsl:value-of select="system-property('xsl:vendor')"/> who=<xsl:value-of select="/who"/></xsl:template>
                </xsl:stylesheet>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("who")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> panel.getOutputText().contains("who=World"));
        assertFalse(panel.getOutputText().contains("Saxonica"), panel.getOutputText());
    }
}
