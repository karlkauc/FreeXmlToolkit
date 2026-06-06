package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the "open XSD and reveal an element in the Graphic view" bridge that
 * replaces the retired legacy XSD editor's navigateToElementInGraphView (used by
 * the XML editor's "go to schema definition").
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostOpenXsdRevealTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(new HBox(host), 1200, 760));
        stage.show();
    }

    @Test
    void opensXsdSwitchesToGraphicAndSelectsTheNamedElement() throws Exception {
        Path xsd = Files.createTempFile("reveal", ".xsd");
        Files.writeString(xsd, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="target" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        xsd.toFile().deleteOnExit();

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openXsdAndReveal(xsd, "target"));
        WaitForAsyncUtils.waitFor(8, java.util.concurrent.TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.waitForFxEvents();
            var sel = host.activeSelectedNodeProperty().get();
            return sel != null && "target".equals(sel.getName());
        });

        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get(), "should switch to the Graphic view");
        assertEquals("target", host.activeSelectedNodeProperty().get().getName(),
                "the named element must be selected in the graph");
    }
}
