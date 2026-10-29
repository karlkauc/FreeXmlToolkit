package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * While a tool tab is active, the Type Library lists the XSD tab's declarations without parsing the
 * schema on the FX thread (the parse may download remote imports): the first call returns what is
 * there, the parse runs on a worker and {@code activeModelRevision} announces the new model.
 */
@ExtendWith(ApplicationExtension.class)
class TypeLibraryToolTabParseTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="person" type="PersonType"/>
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="name" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void toolTabListsTypesAfterABackgroundParse(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PersonType")).orElse(false));

        int revisionBefore = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.openToolTab("Tool", "bi-gear", new Region());
            return host.activeModelRevisionProperty().get();
        });
        List<String> first = WaitForAsyncUtils.waitForAsyncFx(2000, () -> names(host.getActiveNamedTypes()));
        assertTrue(first.isEmpty(), "the text-view model is not parsed on the FX thread, was: " + first);

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.activeModelRevisionProperty().get() > revisionBefore);
        List<String> types = WaitForAsyncUtils.waitForAsyncFx(2000, () -> names(host.getActiveNamedTypes()));
        List<String> elements = WaitForAsyncUtils.waitForAsyncFx(2000, () -> names(host.getActiveGlobalElements()));
        assertTrue(types.contains("PersonType"), "types after the background parse: " + types);
        assertTrue(elements.contains("person"), "elements after the background parse: " + elements);
    }

    private static List<String> names(List<XsdNode> nodes) {
        return nodes.stream().map(XsdNode::getName).toList();
    }
}
