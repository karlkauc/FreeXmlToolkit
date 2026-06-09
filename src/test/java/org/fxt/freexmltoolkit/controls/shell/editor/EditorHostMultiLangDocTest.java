package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The shell can set multiple {@code xs:documentation} entries (per language) on a node via
 * ChangeDocumentationsCommand — preserving multi-language docs instead of collapsing them.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostMultiLangDocTest {

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
    void setsMultipleLanguageDocumentations(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:element")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TEXT);
            return null;
        });
        int caret = XSD.indexOf("name=\"root\"");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.moveActiveCaretTo(caret));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "root".equals(inspector.getNodeNameText()));

        List<XsdDocumentation> docs = List.of(
                new XsdDocumentation("Hello world", "en"),
                new XsdDocumentation("Hallo Welt", "de"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.changeActiveDocumentations(docs)));

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String t = host.getActiveText().orElse("");
            return t.contains("Hello world") && t.contains("Hallo Welt");
        });
        String text = host.getActiveText().orElse("");
        assertTrue(text.contains("xml:lang=\"en\"") && text.contains("xml:lang=\"de\""),
                "both language tags must be present, was:\n" + text);
    }
}
