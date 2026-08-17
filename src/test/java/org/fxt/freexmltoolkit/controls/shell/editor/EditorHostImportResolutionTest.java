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
 * A main XSD that {@code xs:import}s another namespace resolves the imported schema's named types
 * (Type Library sees them); editing the main schema keeps the xs:import directive.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostImportResolutionTest {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/common">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static final String MAIN = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:c="http://example.com/common"
                       targetNamespace="http://example.com/main">
              <xs:import namespace="http://example.com/common" schemaLocation="common.xsd"/>
              <xs:element name="ref" type="c:Code"/>
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
    void importedTypesResolveAndImportSurvivesEdit(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("common.xsd"), COMMON);
        Path main = tmp.resolve("main.xsd");
        Files.writeString(main, MAIN);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(main));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:import")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        List<String> typeNames = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
        assertTrue(typeNames.contains("Code"), "the imported named type must resolve, was: " + typeNames);

        // Editing keeps the import directive (imports are never inlined into the main file).
        XsdNode root = host.getActiveSchemaRoot().orElseThrow();
        XsdNode ref = WaitForAsyncUtils.waitForAsyncFx(2000, () -> find(root, "ref"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.selectNodeInActiveTree(ref);
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveNode("ref2"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("name=\"ref2\""));
        assertTrue(host.getActiveText().orElse("").contains("xs:import"),
                "the xs:import directive must survive the edit");
    }

    @Test
    void transitivelyImportedTypesResolve(@TempDir Path tmp) throws Exception {
        // main.xsd imports mid.xsd, which imports deep.xsd defining a named type.
        // The Type Library reads one level of the root's imported schemas, so this
        // proves that transitive imports are flattened onto the root schema.
        String deep = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/deep">
                  <xs:simpleType name="DeepCode">
                    <xs:restriction base="xs:string"><xs:maxLength value="5"/></xs:restriction>
                  </xs:simpleType>
                </xs:schema>
                """;
        String mid = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:d="http://example.com/deep"
                           targetNamespace="http://example.com/mid">
                  <xs:import namespace="http://example.com/deep" schemaLocation="deep.xsd"/>
                  <xs:element name="midElement" type="d:DeepCode"/>
                </xs:schema>
                """;
        String main = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:m="http://example.com/mid"
                           targetNamespace="http://example.com/main">
                  <xs:import namespace="http://example.com/mid" schemaLocation="mid.xsd"/>
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;
        Files.writeString(tmp.resolve("deep.xsd"), deep);
        Files.writeString(tmp.resolve("mid.xsd"), mid);
        Path mainFile = tmp.resolve("main.xsd");
        Files.writeString(mainFile, main);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(mainFile));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:import")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });

        List<String> typeNames = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
        assertTrue(typeNames.contains("DeepCode"),
                "the transitively imported named type must resolve, was: " + typeNames);
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
