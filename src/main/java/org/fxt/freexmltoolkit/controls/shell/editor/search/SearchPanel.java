package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.nio.file.Path;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;

/**
 * The Search activity side panel (VS-Code-style "Find in Files"). Hosts two
 * modes behind a toggle: plain-text search/replace across a workspace folder
 * ({@link TextSearchPane}) and XPath-based search/replace against the active
 * document or a folder ({@link XPathSearchPane}).
 */
public class SearchPanel extends VBox {

    private final TextSearchPane textPane;
    private final XPathSearchPane xpathPane;
    private final ToggleButton textToggle = new ToggleButton("Text");
    private final ToggleButton xpathToggle = new ToggleButton("XPath");
    private final StackPane content = new StackPane();

    public SearchPanel(EditorHost editorHost, Supplier<Path> workspaceRoot) {
        getStyleClass().addAll("fxt-side-panel-content", "fxt-search-panel");

        Label title = new Label("SEARCH");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(title, headerSpacer);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- mode toggle: Text | XPath ----------------------------------------
        ToggleGroup modes = new ToggleGroup();
        textToggle.setToggleGroup(modes);
        textToggle.setId("search-mode-text");
        textToggle.setTooltip(new Tooltip("Plain-text search across files"));
        xpathToggle.setToggleGroup(modes);
        xpathToggle.setId("search-mode-xpath");
        xpathToggle.setTooltip(new Tooltip("XPath-based search/replace"));
        for (ToggleButton b : new ToggleButton[]{textToggle, xpathToggle}) {
            b.getStyleClass().addAll("fxt-tool-button", "fxt-search-mode-toggle");
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        HBox modeRow = new HBox(4, textToggle, xpathToggle);
        modeRow.getStyleClass().add("fxt-search-mode-row");

        textPane = new TextSearchPane(editorHost, workspaceRoot);
        xpathPane = new XPathSearchPane(editorHost, workspaceRoot);
        content.getChildren().addAll(textPane, xpathPane);
        VBox.setVgrow(content, Priority.ALWAYS);

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

        getChildren().addAll(header, modeRow, content);
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
