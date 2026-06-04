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
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector CONSTRAINTS section can delete an identity constraint (key/keyref/unique) node via
 * the command stack (DeleteNodeCommand), and it round-trips to the text.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXsdConstraintDeleteTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
                <xs:key name="bookKey">
                  <xs:selector xpath="book"/>
                  <xs:field xpath="@id"/>
                </xs:key>
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
    void deletesSelectedIdentityConstraint(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:key")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        var catalog = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.getActiveSchemaRoot()
                .flatMap(s -> s.getChildren().stream()
                        .filter(n -> "catalog".equals(n.getName())).findFirst())
                .orElse(null));
        assertNotNull(catalog, "the catalog element must be in the tree model");
        var keyNode = catalog.getChildren().stream()
                .filter(n -> n.getNodeType() == XsdNodeType.KEY).findFirst().orElse(null);
        assertNotNull(keyNode, "the xs:key must be a selectable model node");

        final org.fxt.freexmltoolkit.controls.v2.model.XsdNode key = keyNode;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(catalog);
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> "catalog".equals(inspector.getNodeNameText()));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.deleteConstraintNode(key)));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !host.getActiveText().orElse("x").contains("xs:key"));
    }
}
