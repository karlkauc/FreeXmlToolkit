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
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.fxt.freexmltoolkit.util.DesktopActions;

/**
 * Checks a pasted identifier (IBAN, BIC, LEI, ISIN, Austrian UID) while it is typed; a valid LEI can be looked up at
 * GLEIF, a valid ISIN at ESMA FIRDS / OpenFIGI (on a click - the value leaves the computer). Shared by {@link IndustryPanel}, the FundsXML and the
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
        boolean lei = kind == IdentifierKind.LEI && Gleif.allowed();
        boolean isin = kind == IdentifierKind.ISIN && Isin.allowed();
        gleifButton.setText(I18n.t(isin ? "IdentifierChecker.isinButton.text" : "IndustryPanel.gleifButton.text"));
        gleifButton.getTooltip().setText(I18n.t(isin ? "IdentifierChecker.isinButton.tooltip" : "IndustryPanel.gleifButton.tooltip"));
        show(gleifButton, problem == null && (lei || isin));
    }

    /** Looks the value up online in the background: an LEI at GLEIF, an ISIN at ESMA FIRDS (OpenFIGI as fallback). */
    @FXML
    public void onGleifLookup() {
        if (looking) {
            return;
        }
        if (kindCombo.getValue() == IdentifierKind.ISIN) {
            lookUpIsin(valueField.getText().strip().toUpperCase(Locale.ROOT));
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

    private void lookUpIsin(String isin) {
        looking = true;
        Node icon = gleifButton.getGraphic();
        ProgressIndicator spinner = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        gleifButton.setGraphic(spinner);
        FxtGui.executorService.submit(() -> {
            IsinRecord record = null;
            String failure = null;
            try {
                record = Isin.cache().lookup(List.of(isin)).get(isin);
            } catch (Exception e) {
                failure = e.getMessage();
            }
            IsinRecord found = record;
            String error = failure;
            Platform.runLater(() -> {
                looking = false;
                gleifButton.setGraphic(icon);
                if (isin.equals(valueField.getText().strip().toUpperCase(Locale.ROOT))) {
                    show(found, error);
                }
            });
        });
    }

    private void show(IsinRecord r, String failure) {
        gleifResult.getChildren().clear();
        if (failure != null) {
            line("✗ " + failure, null);
            return;
        }
        if (r == null) {
            return;
        }
        if (r.found()) {
            line(r.name(), "fxt-gleif-name");
            if (r.source() == IsinRecord.Source.FIRDS) {
                if (r.cfi() != null) {
                    line(I18n.t("IdentifierChecker.isin-cfi", r.cfi()), null);
                }
                if (r.currency() != null) {
                    line(I18n.t("IdentifierChecker.isin-currency", r.currency()), null);
                }
                line(r.activeVenues() > 0 ? I18n.t("IdentifierChecker.isin-venues", r.activeVenues(), r.totalVenues())
                        : I18n.t("IdentifierChecker.isin-ended") + (r.terminatedSince() == null ? "" : " (" + r.terminatedSince() + ")"), null);
                if (r.issuerLei() != null) {
                    Hyperlink issuer = new Hyperlink(I18n.t("IdentifierChecker.isin-issuer", r.issuerLei()));
                    issuer.setId("industry-identifier-issuer-lei");
                    issuer.setWrapText(true);
                    issuer.setOnAction(e -> lookUpIssuer(r.issuerLei()));
                    gleifResult.getChildren().add(issuer);
                }
                line(I18n.t("IdentifierChecker.isin-source-firds"), null);
            } else {
                if (r.securityType() != null) {
                    line(r.securityType() + (r.ticker() == null ? "" : " · " + r.ticker()), null);
                }
                if (r.figi() != null) {
                    line("FIGI " + r.figi(), null);
                }
                line(I18n.t("IdentifierChecker.isin-source-openfigi"), null);
            }
        }
        IsinRecord.Problem problem = r.problem();
        if (problem != null) {
            line("ℹ " + problem.message(), null);
        }
        Hyperlink link = new Hyperlink(I18n.t(r.source() == IsinRecord.Source.OPENFIGI
                ? "IdentifierChecker.isin-open-openfigi" : "IdentifierChecker.isin-open-firds"));
        link.setId("industry-identifier-isin-link");
        link.setOnAction(e -> DesktopActions.browse(java.net.URI.create(r.searchUrl()), ex -> { }));
        gleifResult.getChildren().add(link);
    }

    /** The issuer's LEI of an ISIN: switch to LEI and look it up at GLEIF. */
    private void lookUpIssuer(String lei) {
        if (!kindCombo.getItems().contains(IdentifierKind.LEI) || !Gleif.allowed()) {
            return;
        }
        kindCombo.setValue(IdentifierKind.LEI);
        valueField.setText(lei);
        onGleifLookup();
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
