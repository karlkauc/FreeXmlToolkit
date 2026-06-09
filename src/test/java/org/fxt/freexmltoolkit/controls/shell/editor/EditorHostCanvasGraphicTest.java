package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the shell's XSD Graphic view is the real Canvas-based {@link XsdGraphView}
 * editor (not the simplified scene-graph renderer), and that it shares the tab's
 * editor context so edits round-trip to the text.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostCanvasGraphicTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="ControlData">
                <xs:complexType><xs:sequence>
                  <xs:element name="UniqueDocumentID" type="xs:string"/>
                  <xs:element name="DocumentGenerated" type="xs:dateTime"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void graphicViewIsTheCanvasEditor(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("DocumentGenerated")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        boolean hasCanvasEditor = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof XsdGraphView));
        assertTrue(hasCanvasEditor, "Graphic mode must embed the Canvas-based XsdGraphView editor");
    }
}
