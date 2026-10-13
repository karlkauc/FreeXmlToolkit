package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.nio.file.Path;
import java.util.function.Supplier;

import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * The Search activity side panel (VS-Code-style "Find in Files"). Hosts two
 * modes behind a toggle: plain-text search/replace across a workspace folder
 * ({@link TextSearchPane}) and XPath-based search/replace against the active
 * document or a folder ({@link XPathSearchPane}).
 */
public class SearchPanel extends VBox {

    private final TextSearchPane textPane;
    private final XPathSearchPane xpathPane;
    @FXML private ToggleGroup modes;
    @FXML private ToggleButton textToggle;
    @FXML private ToggleButton xpathToggle;
    @FXML private StackPane content;

    /** Loads the layout from {@code /pages/panels/SearchPanel.fxml} and adds the two search panes. */
    public SearchPanel(EditorHost editorHost, Supplier<Path> workspaceRoot) {
        FxmlViews.load(this, "/pages/panels/SearchPanel.fxml");

        textPane = new TextSearchPane(editorHost, workspaceRoot);
        xpathPane = new XPathSearchPane(editorHost, workspaceRoot);
        content.getChildren().addAll(textPane, xpathPane);

        // Keep one mode always selected (re-select on de-select click).
        modes.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                modes.selectToggle(oldV != null ? oldV : textToggle);
                return;
            }
            showMode(newV == xpathToggle);
        });
        textToggle.setSelected(true);
        showMode(false);
    }

    private void showMode(boolean xpath) {
        textPane.setVisible(!xpath);
        textPane.setManaged(!xpath);
        xpathPane.setVisible(xpath);
        xpathPane.setManaged(xpath);
    }

    /**
     * Switches to Text mode and focuses the query field (shortcut entry point).
     *
     * @param prefill     optional initial query (e.g. the editor selection); ignored if blank
     * @param showReplace whether to expand the replace row (Ctrl+Shift+H)
     */
    public void focusTextSearch(String prefill, boolean showReplace) {
        textToggle.setSelected(true);
        textPane.focusQuery(prefill, showReplace);
    }
}
