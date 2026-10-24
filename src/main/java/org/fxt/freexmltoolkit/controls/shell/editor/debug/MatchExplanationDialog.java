package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.debugger.MatchExplanation;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * "Explain Match": which template rules could process the context node of a template-rule
 * frame, which one XSLT picks and why the others lose (non-modal; double-click opens a rule).
 */
public final class MatchExplanationDialog extends Dialog<Void> {

    @FXML private Label summaryLabel;
    @FXML private ListView<MatchExplanation.Line> ruleList;

    /**
     * @param node    how the node is shown, e.g. {@code <item n="2"/>}
     * @param mode    the mode name, {@code #default} for the unnamed one
     * @param lines   the explanation
     * @param onOpen  opens a rule's module at its line
     */
    public MatchExplanationDialog(String node, String mode, List<MatchExplanation.Line> lines,
                                  Consumer<MatchExplanation.Line> onOpen) {
        setTitle("Explain Match");
        setHeaderText("Template rules for " + node + ("#default".equals(mode) ? "" : "  ·  mode " + mode));
        IconifyIcon icon = new IconifyIcon("bi-diagram-3");
        icon.setIconSize(32);
        setGraphic(icon);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/MatchExplanationDialog.fxml"));
        try {
            getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }
        setResizable(true);
        long matching = lines.stream().filter(l -> l.status() == MatchExplanation.Status.WINS
                || l.status() == MatchExplanation.Status.LOSES).count();
        summaryLabel.setText(matching == 0 ? "No template rule of this mode matches - the built-in rule applies."
                : matching + (matching == 1 ? " rule matches." : " rules match.") + "  " + lines.size()
                        + " rule pattern" + (lines.size() == 1 ? "" : "s") + " of this mode tested.");
        ruleList.getItems().setAll(lines);
        ruleList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(MatchExplanation.Line item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.text());
                setStyle(empty || item == null ? "" : switch (item.status()) {
                    case WINS -> "-fx-font-weight: bold;";
                    case NO_MATCH, NOT_TESTABLE -> "-fx-opacity: 0.6;";
                    default -> "";
                });
            }
        });
        ruleList.setOnMouseClicked(e -> {
            MatchExplanation.Line line = ruleList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && line != null) {
                onOpen.accept(line);
            }
        });
    }

    /** The shown lines (tests). */
    List<MatchExplanation.Line> lines() {
        return List.copyOf(ruleList.getItems());
    }

    /** The summary text (tests). */
    String summary() {
        return summaryLabel.getText();
    }
}
