package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorActions;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** In a narrow Properties column the node name keeps its width; the validation badge gives way. */
@ExtendWith(ApplicationExtension.class)
class InspectorHeaderWidthTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        inspector.setMinWidth(0); // as UnifiedShellView adds it
        StackPane column = new StackPane(inspector);
        stage.setScene(new Scene(new StackPane(column), 288, 600));
        stage.show();
    }

    @Test
    void nameStaysReadableWhileTheBadgeIsTruncated() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setValidationStatus(EditorHost.ValidationState.VALID, 0, EditorActions.WELL_FORMED_NO_SCHEMA);
            TextField name = (TextField) inspector.lookup("#inspector-name");
            name.setVisible(true);
            name.setText("DataSupplier");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TextField name = (TextField) inspector.lookup("#inspector-name");
            Text probe = new Text(name.getText());
            probe.setFont(name.getFont());
            // The title box (name + subtitle) holding the name field.
            var titleBox = (javafx.scene.layout.Region) name.getParent().getParent();
            Label badge = (Label) inspector.lookup("#inspector-validation-badge");
            return titleBox.getWidth() >= probe.getLayoutBounds().getWidth() && badge.getWidth() >= 59.5;
        }));
        double[] widths = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Label badge = (Label) inspector.lookup("#inspector-validation-badge");
            return new double[]{inspector.getWidth(), badge.getWidth()};
        });
        assertTrue(widths[0] <= 288.5, "the panel fits its column: " + widths[0]);
        assertTrue(widths[1] >= 59.5, "the badge keeps its minimum width: " + widths[1]);
    }
}
