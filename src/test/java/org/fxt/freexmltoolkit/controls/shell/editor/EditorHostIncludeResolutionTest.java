package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A main XSD using {@code xs:include} resolves the included file's named types (Type Library +
 * facet resolution see them), and editing the main schema round-trips WITHOUT flattening the
 * include into the main file.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostIncludeResolutionTest {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="common.xsd"/>
              <xs:element name="ref" type="Code"/>
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
    void includedTypesResolveAndRoundTripPreservesInclude(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("common.xsd"), COMMON);
        Path main = tmp.resolve("main.xsd");
        Files.writeString(main, MAIN);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(main));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:include")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        // The included type "Code" is visible to the Type Library / model.
        List<String> typeNames = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
        assertTrue(typeNames.contains("Code"), "the included named type must resolve, was: " + typeNames);

        // Editing the main schema must NOT inline (flatten) the include into the main file.
        XsdNode root = host.getActiveSchemaRoot().orElseThrow();
        XsdNode ref = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(root, "ref"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(ref);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveNode("ref2"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("name=\"ref2\""));

        String text = host.getActiveText().orElse("");
        assertTrue(text.contains("xs:include"), "the xs:include directive must survive the edit");
        assertFalse(text.contains("name=\"Code\""),
                "the included type must NOT be inlined/flattened into the main file:\n" + text);
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode c : node.getChildren()) {
            XsdNode f = find(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}
