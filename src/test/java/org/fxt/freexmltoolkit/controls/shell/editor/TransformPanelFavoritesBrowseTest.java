package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Transform panel lets the user pick XSLT and XML files straight from the
 * favorites and page through them with ◀/▶ (auto-running each step). This verifies
 * the favorites star menus, the cyclic browse navigation with its position label,
 * and the auto-run gate that waits until both axes are present.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelFavoritesBrowseTest {

    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/"><out/></xsl:template>
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
    void starMenusListFavoritesByType(@TempDir Path tmp) throws Exception {
        Path xslt = writeFile(tmp, "DqCheckA.xslt", XSLT);
        Path xml = writeFile(tmp, "InvoiceSample.xml", "<invoice/>");
        FavoritesService svc = FavoritesService.getInstance();
        try {
            svc.addFavorite(xslt.toFile().getAbsolutePath(), "DqCheckA", "DQ-Checks");
            svc.addFavorite(xml.toFile().getAbsolutePath(), "InvoiceSample", null);

            List<String> xsltNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.refreshXsltFavMenu();
                return panel.xsltFavoriteNames();
            });
            List<String> xmlNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.refreshInputFavMenu();
                return panel.inputFavoriteNames();
            });

            assertTrue(xsltNames.stream().anyMatch(n -> n.contains("DqCheckA")),
                    "XSLT star menu must list the XSLT favorite, was: " + xsltNames);
            assertTrue(xmlNames.stream().anyMatch(n -> n.contains("InvoiceSample")),
                    "XML star menu must list the XML favorite, was: " + xmlNames);
            // Cross-type isolation: the XML favorite must not appear in the XSLT menu.
            assertFalse(xsltNames.stream().anyMatch(n -> n.contains("InvoiceSample")),
                    "XSLT star menu must not list XML favorites, was: " + xsltNames);
        } finally {
            svc.removeFavoriteByPath(xslt.toFile().getAbsolutePath());
            svc.removeFavoriteByPath(xml.toFile().getAbsolutePath());
        }
    }

    @Test
    void browsingCyclesThroughTheListAndUpdatesPosition(@TempDir Path tmp) throws Exception {
        File a = writeFile(tmp, "a.xslt", XSLT).toFile();
        File b = writeFile(tmp, "b.xslt", XSLT).toFile();
        File c = writeFile(tmp, "c.xslt", XSLT).toFile();
        List<File> group = List.of(a, b, c);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectXslt(group, a);
            return null;
        });
        assertEquals("1 / 3", panel.xsltBrowsePosition());
        assertEquals(a, panel.currentXsltFile());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> { panel.nextXslt(); return null; });
        assertEquals("2 / 3", panel.xsltBrowsePosition());
        assertEquals(b, panel.currentXsltFile());

        // Wrap-around forward: c → a.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> { panel.nextXslt(); panel.nextXslt(); return null; });
        assertEquals("1 / 3", panel.xsltBrowsePosition());
        assertEquals(a, panel.currentXsltFile());

        // Wrap-around backward: a → c.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> { panel.prevXslt(); return null; });
        assertEquals("3 / 3", panel.xsltBrowsePosition());
        assertEquals(c, panel.currentXsltFile());
    }

    @Test
    void inputBrowsingSetsTheOverrideFile(@TempDir Path tmp) throws Exception {
        File x1 = writeFile(tmp, "one.xml", "<a/>").toFile();
        File x2 = writeFile(tmp, "two.xml", "<b/>").toFile();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectInput(List.of(x1, x2), x1);
            return null;
        });
        assertEquals("1 / 2", panel.inputBrowsePosition());
        assertEquals(x1, panel.currentInputOverride());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> { panel.nextInput(); return null; });
        assertEquals("2 / 2", panel.inputBrowsePosition());
        assertEquals(x2, panel.currentInputOverride());
    }

    @Test
    void autoRunIsGatedUntilBothAxesPresent(@TempDir Path tmp) throws Exception {
        File xslt = writeFile(tmp, "g.xslt", XSLT).toFile();
        File xml = writeFile(tmp, "g.xml", "<a/>").toFile();

        boolean none = WaitForAsyncUtils.waitForAsyncFx(2000, panel::readyToAutoRun);
        assertFalse(none, "nothing selected → must not auto-run");

        boolean onlyXslt = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt);
            return panel.readyToAutoRun();
        });
        assertFalse(onlyXslt, "only a stylesheet, no input → must not auto-run");

        boolean both = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setInputOverride(xml);
            return panel.readyToAutoRun();
        });
        assertTrue(both, "stylesheet + input override → ready to auto-run");
    }

    private static Path writeFile(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
