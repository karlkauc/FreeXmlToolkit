package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.XSpecRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Tools → Run XSpec Tests on the active document: tree, failure details, HTML report, re-run. */
@ExtendWith(ApplicationExtension.class)
class XSpecViewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 650));
        stage.show();
    }

    @Test
    void runsTheActiveXSpec(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("upper.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="name"><n><xsl:value-of select="upper-case(.)"/></n></xsl:template>
                </xsl:stylesheet>
                """);
        Path xspec = Files.writeString(dir.resolve("upper.xspec"), """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" stylesheet="upper.xsl">
                  <x:scenario label="upper case">
                    <x:context><name>ada</name></x:context>
                    <x:expect label="upper"><n>ADA</n></x:expect>
                    <x:expect label="wrong"><n>Ada</n></x:expect>
                  </x:scenario>
                </x:description>
                """);
        assertEquals(EditorFileType.XML, EditorFileType.fromFileName("upper.xspec"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xspec));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("x:description")).orElse(false));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> XSpecView.runActive(host)));
        XSpecView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (XSpecView) host.getToolTabs().stream()
                .filter(t -> t.getContent() instanceof XSpecView).findFirst().orElseThrow().getContent());
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.resultForTesting() != null));
        XSpecRunner.Result result = WaitForAsyncUtils.waitForAsyncFx(2000, view::resultForTesting);
        assertEquals("2 tests: 1 passed, 1 failed, 0 pending", result.summary(), result.error());

        // the failure is selected and shows expected and actual
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::expectedForTesting).contains("Ada"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::actualForTesting).contains("ADA"));
        assertEquals("Failed (1 of 2)", XSpecView.statusOf(result.scenarios().get(0)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onHtmlReport();
            return null;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getOpenDocuments().stream().anyMatch(d -> "upper-report.html".equals(d.getDisplayName().replace("*", "").strip()))));

    }
}
