package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * An XSLT tab is checked while typing: its static errors are marked in the text and listed in the PROBLEMS panel
 * (source XSLT), a fixed stylesheet clears them.
 */
@ExtendWith(ApplicationExtension.class)
class XsltLiveCheckTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private boolean xsltProblems() {
        return host.getActiveProblems().stream().anyMatch(p -> XsltLiveCheck.SOURCE.equals(p.source()));
    }

    @Test
    void aStaticErrorIsMarkedAndListedAndClearedOnceFixed(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("t.xsl");
        Files.writeString(file, """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                  <xsl:template match="/"><xsl:value-of select="ocunt(//a)"/></xsl:template>
                </xsl:stylesheet>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, this::xsltProblems);

        var problem = host.getActiveProblems().stream().filter(p -> XsltLiveCheck.SOURCE.equals(p.source()))
                .findFirst().orElseThrow();
        assertEquals("XPST0017", problem.ruleId());
        assertEquals(2, problem.line());
        assertEquals("error", problem.severity());
        var area = WaitForAsyncUtils.waitForAsyncFx(1000, host::getActiveCodeArea);
        int at = area.getText().indexOf("ocunt");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> area.getStyleOfChar(at + 1).contains("validation-error")));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText(at, at + 5, "count");
            return null;
        });
        WaitForAsyncUtils.waitFor(6, TimeUnit.SECONDS, () -> !xsltProblems());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(1000,
                () -> !area.getStyleOfChar(at + 1).contains("validation-error")));
    }
}
