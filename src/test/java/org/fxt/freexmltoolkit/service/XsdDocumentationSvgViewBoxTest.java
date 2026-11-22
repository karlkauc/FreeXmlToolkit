package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationOptions;
import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The overview diagram's view box and background follow the drawn nodes. Children are centred on
 * their parent, so they reach above and left of the start point; a fixed box at 0,0 used to leave
 * them outside the background.
 */
class XsdDocumentationSvgViewBoxTest {

    @Test
    void viewBoxHoldsEveryShapeWithPaddingAndTitleRoom() {
        String content = "<rect x=\"-50\" y=\"-290\" width=\"200\" height=\"80\" class=\"node\"/>"
                + "<line x1=\"150\" y1=\"10\" x2=\"400\" y2=\"60\"/>"
                + "<text x=\"700\" y=\"500\" class=\"text\">name</text>";
        int[] box = XsdDocumentationSvgService.viewBoxAround(content);
        int pad = XsdDocumentationSvgService.DIAGRAM_PADDING;
        assertEquals(-50 - pad, box[0], "left edge");
        assertTrue(box[1] < -290 - pad, "room for the title above the top node");
        assertTrue(box[0] + box[2] >= 700 + pad, "right edge");
        assertTrue(box[1] + box[3] >= 504 + pad, "bottom edge");
    }

    @Test
    void emptyDiagramGetsASmallBox() {
        assertArrayEquals(new int[]{0, 0, 400, 200}, XsdDocumentationSvgService.viewBoxAround(""));
    }

    @Test
    void backgroundOfTheSvgPageCoversEveryNode(@TempDir Path tmp) throws Exception {
        File site = tmp.resolve("site").toFile();
        DocumentationRunner.generate(new File("src/test/resources/testSchema.xsd"), site,
                new DocumentationOptions(), null);
        String page = Files.readString(site.toPath().resolve("schema-svg.html"));

        Matcher svg = Pattern.compile("<svg width=\"(\\d+)\" height=\"(\\d+)\" viewBox=\"(-?\\d+) (-?\\d+) (\\d+) (\\d+)\"")
                .matcher(page);
        assertTrue(svg.find(), "the diagram SVG with its view box");
        int x = Integer.parseInt(svg.group(3)), y = Integer.parseInt(svg.group(4));
        int w = Integer.parseInt(svg.group(5)), h = Integer.parseInt(svg.group(6));
        assertTrue(page.contains("<rect x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\""),
                "the background fills the view box");

        Matcher node = Pattern.compile("<rect x=\"(-?[0-9.]+)\" y=\"(-?[0-9.]+)\" width=\"([0-9.]+)\" height=\"([0-9.]+)\"[^>]*class=\"node\"")
                .matcher(page);
        int nodes = 0;
        while (node.find()) {
            nodes++;
            double nx = Double.parseDouble(node.group(1)), ny = Double.parseDouble(node.group(2));
            double nw = Double.parseDouble(node.group(3)), nh = Double.parseDouble(node.group(4));
            assertTrue(nx >= x && ny >= y && nx + nw <= x + w && ny + nh <= y + h,
                    "node at " + nx + "," + ny + " lies inside the background " + x + "," + y + " " + w + "x" + h);
        }
        assertTrue(nodes > 0, "the diagram has nodes");
    }
}
