package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Bounds;
import javafx.scene.Node;
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
 * Layout/usability verification of the Transform panel on small windows: the panel
 * is hosted in a narrow (~260px) activity side panel without its own scrolling, so
 * the RESULT area at the bottom must stay visible even when the window is short —
 * otherwise a transform appears to "do nothing" because its output is clipped.
 * Also verifies that a transform result can be opened as a regular editor tab.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelLayoutTest {

    private static final double SCENE_HEIGHT = 480;

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;
    private Scene scene;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        // Mirror the real shell: the panel is constrained to the side-panel width.
        panel.setPrefWidth(260);
        panel.setMaxWidth(260);
        scene = new Scene(new HBox(host, panel), 900, SCENE_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void resultAreaStaysVisibleInAShortWindow(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);

        Node output = panel.lookup(".fxt-transform-output");
        assertNotNull(output, "the transform output area must exist");
        Bounds bounds = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> output.localToScene(output.getBoundsInLocal()));
        assertTrue(bounds.getMaxY() <= SCENE_HEIGHT + 0.5,
                "the RESULT output must not be clipped below the window (maxY=" + bounds.getMaxY()
                        + ", window height=" + SCENE_HEIGHT + ")");
        assertTrue(bounds.getHeight() >= 100,
                "the RESULT output must keep a usable height, was " + bounds.getHeight());
    }

    @Test
    void controlsAreReachableViaScrollingInAShortWindow() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean transformInScrollPane = panel.lookupAll(".scroll-pane").stream()
                .anyMatch(sp -> sp instanceof javafx.scene.control.ScrollPane scroll
                        && scroll.getContent() != null
                        && scroll.getContent().lookupAll(".button").stream()
                        .anyMatch(n -> n instanceof javafx.scene.control.Button b
                                && "Transform".equals(b.getText())));
        assertTrue(transformInScrollPane,
                "the panel controls (incl. Transform) must sit in a ScrollPane so they stay reachable");
    }

    @Test
    void opensTheTransformResultAsAnEditorTab(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.openResultInEditor();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        OpenDocument result = host.getOpenDocuments().stream()
                .filter(d -> d.getDisplayName() != null && d.getDisplayName().startsWith("Transform-Result"))
                .findFirst().orElse(null);
        assertNotNull(result, "the transform result must open as a new editor tab");
        assertEquals(EditorFileType.XML, result.getFileType());
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<out>Hello</out>")).orElse(false));
        assertTrue(host.getActiveText().orElse("").contains("<out>Hello</out>"));
    }

    private void transformGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> panel.getOutputText().contains("<out>Hello</out>"));
    }
}
