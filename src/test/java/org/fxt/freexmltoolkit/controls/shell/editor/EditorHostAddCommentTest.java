package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Adding an XSD comment node to the selected node (AddCommentCommand) round-trips to the text. */
@ExtendWith(ApplicationExtension.class)
class EditorHostAddCommentTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
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
    void addsCommentToSelectedNode(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:schema")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        // Caret on the schema line selects the schema node.
        int caret = XSD.indexOf("<xs:schema") + 4;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "Schema".equals(inspector.getKindText()));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.addCommentToActive("ADDED_COMMENT")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("ADDED_COMMENT"));
        assertTrue(host.getActiveText().orElse("").contains("<!--"), "a comment node must be serialized");
    }
}
