package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A {@code ref} into an imported namespace (the FundsXML4 {@code ds:Signature} case) must be
 * resolved by the graphic view out of the box. The visual tree builder can only do that when it
 * is handed the schema's imported schemas — {@link XsdGraphView} has to seed them itself, because
 * {@code XsdNodeFactory} deliberately keeps imported components out of the main schema tree.
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphViewImportedRefTest {

    private XsdGraphView graph;

    @Start
    void start(Stage stage) throws Exception {
        // The builder caches its type/element indexes per schema identity across instances.
        XsdVisualTreeBuilder.invalidateCache();

        XsdSchema schema = new XsdNodeFactory()
                .fromFile(Path.of("src/test/resources/schema/import_ref/main.xsd"));
        graph = new XsdGraphView(schema);
        stage.setScene(new Scene(graph, 800, 500));
        stage.show();
    }

    @Test
    void refIntoImportedNamespaceIsResolvedWithoutExplicitWiring() {
        WaitForAsyncUtils.waitForFxEvents();

        VisualNode root = graph.getRootNode();
        assertNotNull(root, "the graph view must have built a visual tree");

        VisualNode signature = findByRef(root, "ds:Signature");
        assertNotNull(signature, "the ds:Signature reference must appear in the diagram");

        VisualNode sequence = signature.getChildren().stream()
                .filter(c -> "sequence".equals(c.getLabel()))
                .findFirst()
                .orElse(null);
        assertNotNull(sequence,
                "ds:Signature must expand into the imported SignatureType's sequence, but had children: "
                        + signature.getChildren().stream().map(VisualNode::getLabel).toList());

        assertEquals(List.of("SignedInfo", "SignatureValue"),
                sequence.getChildren().stream().map(VisualNode::getLabel).toList(),
                "the imported type's particles must be rendered");
    }

    private static VisualNode findByRef(VisualNode node, String ref) {
        if (node.getModelObject() instanceof XsdElement element && ref.equals(element.getRef())) {
            return node;
        }
        for (VisualNode child : node.getChildren()) {
            VisualNode found = findByRef(child, ref);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
