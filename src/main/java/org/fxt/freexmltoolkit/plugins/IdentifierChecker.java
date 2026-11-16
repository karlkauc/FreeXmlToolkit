package org.fxt.freexmltoolkit.plugins;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.util.DesktopActions;

/**
 * Checks a pasted identifier (IBAN, BIC, LEI, ISIN, Austrian UID) while it is typed; a valid LEI can be looked up at
 * GLEIF (on a click - the LEI leaves the computer). Shared by {@link IndustryPanel}, the FundsXML and the
 * PK-Meldewesen panel.
 */
public class IdentifierChecker extends VBox {

    @FXML private ComboBox<IdentifierKind> kindCombo;
    @FXML private TextField valueField;
    @FXML private Label result;
    @FXML private Button gleifButton;
    @FXML private VBox gleifResult;
    private boolean looking;

    public IdentifierChecker() {
        FxmlViews.load(this, "/pages/panels/IdentifierChecker.fxml");
        kindCombo.setButtonCell(kindCell());
        kindCombo.setCellFactory(list -> kindCell());
        kindCombo.valueProperty().addListener((obs, o, n) -> check());
        valueField.textProperty().addListener((obs, o, n) -> check());
        check();
    }

    /** @param kindsToOffer the identifiers the checker offers; the first is selected */
    public IdentifierChecker(List<IdentifierKind> kindsToOffer) {
        this();
        setKinds(kindsToOffer);
    }

    /** Sets the identifiers the checker offers; the first is selected. */
    public void setKinds(List<IdentifierKind> kindsToOffer) {
        kindCombo.getItems().setAll(kindsToOffer);
        if (!kindsToOffer.isEmpty()) {
            kindCombo.setValue(kindsToOffer.getFirst());
        }
    }

    private void check() {
        IdentifierKind kind = kindCombo.getValue();
        String value = valueField.getText();
        gleifResult.getChildren().clear();
        if (kind == null || value == null || value.isBlank()) {
            result.setText("");
            show(gleifButton, false);
            return;
        }
        String problem = kind.check(value);
        result.setText(problem == null ? "✓ Valid " + kind.label() : "✗ " + problem);
        show(gleifButton, problem == null && kind == IdentifierKind.LEI && Gleif.allowed());
    }

    /** Looks the LEI of the field up at GLEIF in the background. */
    @FXML
    public void onGleifLookup() {
        if (looking) {
            return;
        }
        String lei = valueField.getText().strip().toUpperCase(Locale.ROOT);
        looking = true;
        Node icon = gleifButton.getGraphic();
        ProgressIndicator spinner = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        gleifButton.setGraphic(spinner);
        FxtGui.executorService.submit(() -> {
            LeiRecord record = null;
            String failure = null;
            try {
                record = Gleif.cache().lookup(List.of(lei)).get(lei);
            } catch (Exception e) {
                failure = e.getMessage();
            }
            LeiRecord found = record;
            String error = failure;
            Platform.runLater(() -> {
                looking = false;
                gleifButton.setGraphic(icon);
                if (lei.equals(valueField.getText().strip().toUpperCase(Locale.ROOT))) {
                    show(found, error);
                }
            });
        });
    }

    private void show(LeiRecord r, String failure) {
        gleifResult.getChildren().clear();
        if (failure != null) {
            line("✗ " + failure, null);
            return;
        }
        if (r == null) {
            return;
        }
        if (r.found()) {
            line(r.legalName(), "fxt-gleif-name");
            line(I18n.t("IndustryPanel.gleif-place", r.city() == null ? "" : r.city(), r.country() == null ? "" : r.country()), null);
            line(I18n.t("IndustryPanel.gleif-status", r.registrationStatus(), r.entityStatus()), null);
            if (r.nextRenewal() != null) {
                line(I18n.t("IndustryPanel.gleif-renewal", r.nextRenewal().toString()), null);
            }
        }
        LeiRecord.Problem problem = r.problem();
        if (problem != null) {
            line((problem.severity() == LeiRecord.Severity.INFO ? "ℹ " : "✗ ") + problem.message(), null);
        }
        Hyperlink link = new Hyperlink(I18n.t("IndustryPanel.gleif-open"));
        link.setId("industry-identifier-gleif-link");
        link.setOnAction(e -> DesktopActions.browse(java.net.URI.create(r.searchUrl()), ex -> { }));
        gleifResult.getChildren().add(link);
    }

    private void line(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        if (styleClass != null) {
            label.getStyleClass().add(styleClass);
        }
        gleifResult.getChildren().add(label);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
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

    // test access
    void check(IdentifierKind kind, String value) {
        kindCombo.setValue(kind);
        valueField.setText(value);
    }

    String resultText() {
        return result.getText();
    }

    boolean gleifButtonShown() {
        return gleifButton.isVisible();
    }

    String gleifResultText() {
        return gleifResult.getChildren().stream()
                .map(n -> n instanceof Label l ? l.getText() : n instanceof Hyperlink h ? h.getText() : "")
                .collect(Collectors.joining("\n"));
    }
}
