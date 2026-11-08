package org.fxt.freexmltoolkit.plugins;

import java.util.List;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelActionButton;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelActionList;
import org.fxt.freexmltoolkit.controls.shell.editor.SidePanelHeader;
import org.fxt.freexmltoolkit.controls.shell.editor.SidePanelSection;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.PluginUi;

/**
 * The side panel of an industry plugin: what the plugin does, the active document (what the plugin
 * recognises it as, and the result of its checks), the plugin's own actions (new sample document …)
 * and a checker for identifiers (IBAN, BIC, LEI, ISIN …).
 */
public class IndustryPanel extends VBox {

    /** The panel's check of the active document. */
    @FunctionalInterface
    public interface Checker {
        /**
         * @param path the saved file, or {@code null} for an unsaved document
         * @throws Exception shown as "The check could not run: …" (e.g. a missing external program)
         */
        List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name) throws Exception;
    }

    /** An action row of the panel. */
    public record Action(String id, String text, String icon, ActionColor color, java.util.function.Consumer<PluginUi> run) {
    }

    @FXML private SidePanelHeader header;
    @FXML private Label about;
    @FXML private Label documentSummary;
    @FXML private Label checkResult;
    @FXML private PanelActionList actions;
    @FXML private PanelActionButton checkButton;
    @FXML private SidePanelSection identifierSection;
    @FXML private ComboBox<IdentifierKind> identifierKind;
    @FXML private TextField identifierField;
    @FXML private Label identifierResult;

    private final PluginUi ui;
    private final Function<String, String> describer;
    private final Checker checker;
    private List<DocumentValidator.Problem> lastProblems = List.of();
    @FXML private javafx.scene.control.Hyperlink showAllLink;

    /**
     * @param describer   what the document is ({@code "pain.001.001.09 — 2 payments …"}), or {@code null}
     *                    when the plugin does not recognise it
     * @param validator   the plugin's checks
     * @param identifiers the kinds the identifier checker offers (none hides it)
     */
    public IndustryPanel(PluginUi ui, String title, String aboutText, List<Action> extraActions,
                         Function<String, String> describer, DocumentValidator validator,
                         List<IdentifierKind> identifiers) {
        this(ui, title, aboutText, extraActions, describer,
                (content, path, name) -> validator.validate(content, name), identifiers);
    }

    /** @param checker the check of "Check Active Document" (for checks that need the saved file) */
    public IndustryPanel(PluginUi ui, String title, String aboutText, List<Action> extraActions,
                         Function<String, String> describer, Checker checker, List<IdentifierKind> identifiers) {
        this.ui = ui;
        this.describer = describer;
        this.checker = checker;
        FxmlViews.load(this, "/pages/panels/IndustryPanel.fxml");
        header.setTitle(title);
        about.setText(aboutText);
        for (Action action : extraActions) {
            PanelActionButton button = new PanelActionButton();
            button.setId(action.id());
            button.setText(action.text());
            button.setIconLiteral(action.icon());
            button.setColor(action.color());
            button.setOnAction(e -> action.run().accept(ui));
            actions.getChildren().add(button);
        }
        identifierSection.setVisible(!identifiers.isEmpty());
        identifierSection.setManaged(!identifiers.isEmpty());
        identifierKind.getItems().setAll(identifiers);
        identifierKind.setButtonCell(kindCell());
        identifierKind.setCellFactory(list -> kindCell());
        if (!identifiers.isEmpty()) {
            identifierKind.setValue(identifiers.getFirst());
        }
        identifierKind.valueProperty().addListener((obs, o, n) -> checkIdentifier());
        identifierField.textProperty().addListener((obs, o, n) -> checkIdentifier());
        // Deferred: the new tab's text is in place only after the tab switch has been processed
        ui.editorHost().activeTabProperty().addListener((obs, o, n) -> Platform.runLater(this::refreshDocument));
        sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) {
                refreshDocument();
            }
        });
        refreshDocument();
    }

    private static ListCell<IdentifierKind> kindCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(IdentifierKind item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    /** Shows what the active document is for this plugin. */
    void refreshDocument() {
        String text = ui.editorHost().getActiveText().orElse(null);
        String description = text == null ? null : describer.apply(text);
        documentSummary.setText(text == null ? "No document is open."
                : description == null ? "The active document is not one this plugin checks." : description);
        checkButton.setDisable(description == null);
        showCheckResult(null);
        showAllLink.setVisible(false);
        showAllLink.setManaged(false);
    }

    @FXML
    public void onCheck() {
        String text = ui.editorHost().getActiveText().orElse(null);
        if (text == null) {
            return;
        }
        var document = ui.editorHost().getActiveDocument();
        String name = document.map(d -> d.getDisplayName()).orElse(null);
        java.nio.file.Path path = document.map(d -> d.getPath()).orElse(null);
        checkButton.setDisable(true);
        showCheckResult("Checking…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result;
            List<DocumentValidator.Problem> problems = List.of();
            try {
                problems = checker.check(text, path, name);
                result = summary(problems);
            } catch (Exception e) {
                result = "The check could not run: " + e.getMessage();
            }
            String shown = result;
            List<DocumentValidator.Problem> found = problems;
            Platform.runLater(() -> {
                checkButton.setDisable(false);
                lastProblems = found;
                showCheckResult(shown);
                showAllLink.setVisible(found.size() > 10);
                showAllLink.setManaged(found.size() > 10);
            });
        });
    }

    /** @return the problems as text: a count and the first ten */
    static String summary(List<DocumentValidator.Problem> problems) {
        if (problems.isEmpty()) {
            return "✓ No problems found.";
        }
        StringBuilder text = new StringBuilder(problems.size() == 1 ? "1 problem:" : problems.size() + " problems:");
        problems.stream().limit(10).forEach(p -> text.append("\n• ")
                .append(p.line() > 0 ? "Line " + p.line() + ": " : "").append(p.message()));
        if (problems.size() > 10) {
            text.append("\n… and ").append(problems.size() - 10).append(" more.");
        }
        return text.toString();
    }

    /** Opens all problems of the last check as a text document. */
    @FXML
    public void onShowAll() {
        StringBuilder text = new StringBuilder(header.getTitle()).append(" — ").append(lastProblems.size())
                .append(" problems\n\n");
        for (DocumentValidator.Problem p : lastProblems) {
            text.append(p.severity()).append('\t').append(p.line() > 0 ? "line " + p.line() : "").append('\t')
                    .append(p.message()).append('\n');
        }
        ui.editorHost().openGeneratedDocument(text.toString(),
                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.OTHER, "problems.txt");
    }

    private void showCheckResult(String text) {
        boolean show = text != null;
        checkResult.setText(show ? text : "");
        checkResult.setVisible(show);
        checkResult.setManaged(show);
    }

    private void checkIdentifier() {
        IdentifierKind kind = identifierKind.getValue();
        String value = identifierField.getText();
        if (kind == null || value == null || value.isBlank()) {
            identifierResult.setText("");
            return;
        }
        String problem = kind.check(value);
        identifierResult.setText(problem == null ? "✓ Valid " + kind.label() : "✗ " + problem);
    }

    // test access
    String documentSummaryForTesting() {
        return documentSummary.getText();
    }

    String checkResultForTesting() {
        return checkResult.isVisible() ? checkResult.getText() : null;
    }

    void checkIdentifierForTesting(IdentifierKind kind, String value) {
        identifierKind.setValue(kind);
        identifierField.setText(value);
    }

    String identifierResultForTesting() {
        return identifierResult.getText();
    }
}
