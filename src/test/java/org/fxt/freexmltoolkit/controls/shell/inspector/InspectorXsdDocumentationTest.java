package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Regression: the inspector must show an XSD node's documentation. The parser stores annotation
 * documentation in the multi-language {@code documentations} list (legacy {@code documentation}
 * String stays null), so the inspector must read the list, not only the legacy field.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXsdDocumentationTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string">
                <xs:annotation>
                  <xs:documentation>Hello documentation</xs:documentation>
                </xs:annotation>
              </xs:element>
            </xs:schema>
            """;

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    @Test
    void inspectorShowsXsdNodeDocumentation(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("documentation")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });

        // Select the documented <root> element via the Text caret, then assert its doc is shown.
        int caret = XSD.indexOf("name=\"root\"");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> "root".equals(inspector.getNodeNameText())
                        && inspector.getDocumentationText().contains("Hello documentation"));
        assertTrue(inspector.getDocumentationText().contains("Hello documentation"),
                "the inspector must display the node's documentation, was: '"
                        + inspector.getDocumentationText() + "'");
    }
}
