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
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Selecting an XSD comment node (which the Tree already renders) shows its content editable, and
 * editing it round-trips via EditCommentCommand — closing the XSD comment-editing gap.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXsdCommentTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <!-- original comment -->
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
    void editsSelectedXsdCommentContent(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("original comment")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        XsdNode comment = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.getActiveSchemaRoot()
                .map(s -> s.getChildren().stream().filter(c -> c instanceof XsdComment).findFirst().orElse(null))
                .orElse(null));
        assertNotNull(comment, "the schema-level comment must be a selectable model node");

        final XsdNode commentNode = comment;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(commentNode);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> "Comment".equals(inspector.getKindText()));
        assertTrue(inspector.getContentText().contains("original comment"),
                "the comment content must be shown, was: " + inspector.getContentText());

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.changeActiveComment("EDITED_COMMENT")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("EDITED_COMMENT"));
    }
}
