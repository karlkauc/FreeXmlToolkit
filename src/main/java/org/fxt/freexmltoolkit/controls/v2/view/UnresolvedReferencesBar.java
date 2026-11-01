package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.List;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.view.UnresolvedSchemaReferences.Entry;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * Bar above the diagram naming the includes and imports that could not be loaded, which is why
 * parts of the schema are missing or show as unresolved. Dismissing it holds until the set of
 * failures changes.
 */
public final class UnresolvedReferencesBar extends HBox {

    @FXML private Label message;

    private List<Entry> shown = List.of();
    private List<Entry> dismissed = List.of();

    /** Creates the bar, hidden until {@link #setEntries} reports failures. */
    public UnresolvedReferencesBar() {
        FxmlViews.load(this, "/pages/views/UnresolvedReferencesBar.fxml");
        managedProperty().bind(visibleProperty());
        setVisible(false);
    }

    /**
     * Shows the given failures.
     *
     * @param entries the current failures, empty to hide the bar
     */
    public void setEntries(List<Entry> entries) {
        shown = entries;
        boolean show = !entries.isEmpty() && !entries.equals(dismissed);
        if (show) {
            message.setText(UnresolvedSchemaReferences.message(entries));
            message.setTooltip(new Tooltip(entries.stream()
                    .map(entry -> entry.location() + ": " + entry.error())
                    .collect(Collectors.joining("\n"))));
        }
        setVisible(show);
    }

    /** Hides the bar for the failures it shows now. */
    @FXML
    public void onDismiss() {
        dismissed = shown;
        setVisible(false);
    }
}
