package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/** Names for screen readers: icon buttons from their tooltip or inner label, fields from the label before them. */
@ExtendWith(ApplicationExtension.class)
class AccessibleNamesTest {

    @Test
    void namesButtonsAndFields() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            Button icon = new Button();
            icon.setTooltip(new Tooltip("Undo (Ctrl+Z)\nsecond line"));
            Button card = new Button(null, new VBox(new Label("Open File"), new Label("Browse the disk")));
            Button named = new Button("Save");
            Button keep = new Button();
            keep.setAccessibleText("Mine");
            keep.setTooltip(new Tooltip("Other"));
            Label portLabel = new Label("Port:");
            TextField port = new TextField();
            ComboBox<String> lonely = new ComboBox<>();
            GridPane grid = new GridPane();
            Label hostLabel = new Label("Host");
            TextField host = new TextField();
            grid.add(hostLabel, 0, 0);
            grid.add(host, 1, 0);
            VBox root = new VBox(icon, card, named, keep, new HBox(portLabel, port), new HBox(lonely), grid);

            AccessibleNames.apply(root);

            assertEquals("Undo (Ctrl+Z)", icon.getAccessibleText(), "first line of the tooltip");
            assertEquals("Open File", card.getAccessibleText(), "the card's first label");
            assertNull(named.getAccessibleText(), "the text is the name already");
            assertEquals("Mine", keep.getAccessibleText(), "an explicit name stays");
            assertSame(port, portLabel.getLabelFor());
            assertSame(host, hostLabel.getLabelFor(), "GridPane: the label in the same row");
            assertNull(lonely.getAccessibleText());
            return null;
        });
    }
}
