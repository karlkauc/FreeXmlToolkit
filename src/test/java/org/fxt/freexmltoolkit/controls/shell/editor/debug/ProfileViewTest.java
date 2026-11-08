package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltProfile;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The profile view: call tree, flame graph (zoom, open), lines with their source text. */
@ExtendWith(ApplicationExtension.class)
class ProfileViewTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="f xs">
              <xsl:function name="f:slow" as="xs:integer">
                <xsl:param name="n" as="xs:integer"/>
                <xsl:sequence select="sum(for $i in 1 to $n * 2000 return $i mod 7)"/>
              </xsl:function>
              <xsl:template match="/"><out><xsl:apply-templates select="//n"/></out></xsl:template>
              <xsl:template match="n"><v><xsl:value-of select="f:slow(xs:integer(.))"/></v></xsl:template>
            </xsl:stylesheet>
            """;

    private StackPane root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 1000, 600));
        stage.show();
    }

    @Test
    void showsTreeFlameGraphAndLines(@TempDir Path dir) throws Exception {
        Path xsl = Files.writeString(dir.resolve("slow.xsl"), XSLT);
        var profiled = TransformRunner.profile("<r><n>5</n><n>9</n></r>", null, XSLT, xsl.toUri().toString(), Map.of(),
                OutputFormat.XML, 0);
        assertTrue(profiled.result().isSuccess(), profiled.result().getErrorMessage());
        List<Object[]> opened = new ArrayList<>();
        ProfileView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ProfileView v = new ProfileView(profiled.profile(), profiled.result());
            v.setSourceOpener((systemId, line) -> opened.add(new Object[] {systemId, line}));
            root.getChildren().setAll(v);
            return v;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // call tree: (transformation) > match="/" > match="n" > f:slow#1, the hot path expanded
        var treeRoot = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.treeForTesting().getRoot());
        assertEquals("(transformation)", treeRoot.getValue().label());
        var document = treeRoot.getChildren().get(0);
        assertTrue(document.isExpanded());
        assertEquals("f:slow#1", document.getChildren().get(0).getChildren().get(0).getValue().label());

        // flame graph: one bar per node; a click zooms into a bar, a click on it again zooms out
        FlameGraph flame = view.flameGraphForTesting();
        List<FlameGraph.Box> boxes = WaitForAsyncUtils.waitForAsyncFx(2000, flame::boxes);
        assertEquals(4, boxes.size(), boxes.toString());
        FlameGraph.Box slow = boxes.stream().filter(b -> b.node().label().equals("f:slow#1")).findFirst().orElseThrow();
        assertTrue(slow.width() > 0);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            flame.zoomTo(slow.node());
            return null;
        });
        assertSame(slow.node(), flame.zoomedNode());
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, flame::boxes).size());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            flame.resetZoom();
            return null;
        });
        assertEquals(4, WaitForAsyncUtils.waitForAsyncFx(2000, flame::boxes).size());

        // lines: the source text is read from the module
        List<XsltProfile.LineStats> lines = WaitForAsyncUtils.waitForAsyncFx(2000, view::linesForTesting);
        assertFalse(lines.isEmpty());
        XsltProfile.LineStats hottest = lines.get(0);
        String source = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.sourceLineForTesting(hottest));
        assertEquals(Files.readAllLines(xsl).get(hottest.line() - 1).strip(), source);
        assertTrue(opened.isEmpty());
    }
}
