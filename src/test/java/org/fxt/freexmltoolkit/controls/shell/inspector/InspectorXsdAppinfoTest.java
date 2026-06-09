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
 * The inspector edits an XSD node's {@code xs:appinfo} (machine-readable annotation metadata) via the
 * ChangeAppinfoCommand — closing that gap alongside the already-editable documentation.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorXsdAppinfoTest {

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
    void editingAppinfoRoundTripsToText(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:element")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });

        // Select the element via the Text caret, then set its appinfo through the host.
        int caret = XSD.indexOf("name=\"root\"");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "root".equals(inspector.getNodeNameText()));

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.changeActiveAppinfo("SAP_FIELD_42")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("SAP_FIELD_42"));
        assertTrue(host.getActiveText().orElse("").contains("appinfo"),
                "the serialized schema must carry an xs:appinfo, was:\n" + host.getActiveText().orElse(""));
    }
}
