package org.fxt.freexmltoolkit.controls.shell.editor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiScanner;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;

/**
 * Shows what GLEIF knows about an LEI, or ESMA FIRDS / OpenFIGI about an ISIN, when the mouse rests on it in a text
 * editor (LEI: legal name, country, registration and entity status; ISIN: name, CFI, issuer LEI, EU trading). Known answers (the shared cache) show at once; an unknown LEI is looked up only when the user
 * turned on {@link Gleif#KEY_HOVER}, otherwise the hover offers nothing. Installed on every code area while GLEIF
 * lookups are allowed; works in any text (XML, CSV, JSON).
 */
public final class IdentifierHover {

    private static final Map<CodeArea, IdentifierHover> INSTALLED = new WeakHashMap<>();

    private final CodeArea area;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(2);
    private final Label title = new Label();
    private final Label body = new Label();
    private final PauseTransition hideDelay = new PauseTransition(javafx.util.Duration.millis(400));
    /** The LEI the mouse rests on (a lookup that returns later shows only when it is still this one). */
    private String current;

    private IdentifierHover(CodeArea area) {
        this.area = area;
        title.getStyleClass().add("fxt-debug-hover-title");
        body.getStyleClass().add("fxt-debug-hover-value");
        body.setWrapText(true);
        body.setMaxWidth(560);
        box.getChildren().addAll(title, body);
        box.getStyleClass().addAll("root", "fxt-debug-hover", "fxt-lei-hover");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        box.setOnMouseEntered(e -> hideDelay.stop());
        box.setOnMouseExited(e -> hideDelay.playFromStart());
        hideDelay.setOnFinished(e -> {
            popup.hide();
            current = null;
        });
    }

    /** Installs the hover on a code area once; does nothing when the administrator turned both lookups off. */
    public static synchronized void installOn(CodeArea area) {
        if (area == null || INSTALLED.containsKey(area) || !(Gleif.allowed() || Isin.allowed())) {
            return;
        }
        IdentifierHover hover = new IdentifierHover(area);
        INSTALLED.put(area, hover);
        if (area.getMouseOverTextDelay() == null) {
            area.setMouseOverTextDelay(Duration.ofMillis(500));
        }
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, hover::onBegin);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> hover.hideDelay.playFromStart());
    }

    private void onBegin(MouseOverTextEvent e) {
        double x = e.getScreenPosition().getX();
        double y = e.getScreenPosition().getY() + 14;
        String lei = Gleif.allowed() ? leiAt(area, e.getCharacterIndex()) : null;
        if (lei != null && FinancialIdentifiers.isExampleLei(lei)) {
            current = lei;
            show(new String[] {"LEI " + lei, I18n.t("IdentifierHover.example")}, x, y);
            return;
        }
        if (lei != null) {
            current = lei;
            hover(lei, Gleif.cache().cached(lei).orElse(null), Gleif.hoverLookups(),
                    () -> Gleif.cache().lookup(List.of(lei)).get(lei), IdentifierHover::texts, x, y);
            return;
        }
        String isin = Isin.allowed() ? isinAt(area, e.getCharacterIndex()) : null;
        if (isin != null && FinancialIdentifiers.isExampleIsin(isin)) {
            current = isin;
            show(new String[] {"ISIN " + isin, I18n.t("IdentifierHover.example")}, x, y);
            return;
        }
        if (isin != null) {
            current = isin;
            hover(isin, Isin.cache().cached(isin).orElse(null), Isin.hoverLookups(),
                    () -> Isin.cache().lookup(List.of(isin)).get(isin), IdentifierHover::texts, x, y);
        }
    }

    /** Shows a known answer at once; asks the service only when lookups on hover are on. */
    private <R> void hover(String id, R known, boolean lookUp, java.util.concurrent.Callable<R> fetch,
                           java.util.function.Function<R, String[]> texts, double x, double y) {
        if (known != null) {
            show(texts.apply(known), x, y);
            return;
        }
        if (!lookUp) {
            return;
        }
        FxtGui.executorService.submit(() -> {
            R r;
            try {
                r = fetch.call();
            } catch (Exception ex) {
                r = null;
            }
            R found = r;
            Platform.runLater(() -> {
                if (found != null && id.equals(current)) {
                    show(texts.apply(found), x, y);
                }
            });
        });
    }

    /** @return the ISIN at a character of the area, or {@code null} (reads only the characters around it) */
    static String isinAt(CodeArea area, int index) {
        int length = area.getLength();
        if (index < 0 || index >= length) {
            return null;
        }
        int from = Math.max(0, index - 13);
        int to = Math.min(length, index + 14);
        return org.fxt.freexmltoolkit.service.finance.isin.IsinScanner.at(area.getText(from, to), index - from);
    }

    /** @return title and body for an ISIN answer */
    static String[] texts(IsinRecord r) {
        if (!r.found()) {
            return new String[] {"ISIN " + r.isin(), r.problem() == null ? "" : r.problem().message()};
        }
        StringBuilder body = new StringBuilder();
        if (r.source() == IsinRecord.Source.FIRDS) {
            if (r.cfi() != null) {
                body.append(I18n.t("IdentifierChecker.isin-cfi", r.cfi())).append('\n');
            }
            if (r.issuerLei() != null) {
                body.append("Issuer LEI ").append(r.issuerLei()).append('\n');
            }
            body.append(r.activeVenues() > 0 ? I18n.t("IdentifierChecker.isin-venues", r.activeVenues(), r.totalVenues())
                    : I18n.t("IdentifierChecker.isin-ended") + (r.terminatedSince() == null ? "" : " (" + r.terminatedSince() + ")"));
            body.append('\n').append(I18n.t("IdentifierChecker.isin-source-firds"));
        } else {
            if (r.securityType() != null) {
                body.append(r.securityType()).append(r.ticker() == null ? "" : " · " + r.ticker()).append('\n');
            }
            body.append(I18n.t("IdentifierChecker.isin-source-openfigi"));
        }
        return new String[] {r.name(), body.toString()};
    }

    /** @return the LEI at a character of the area, or {@code null} (reads only the characters around it) */
    static String leiAt(CodeArea area, int index) {
        int length = area.getLength();
        if (index < 0 || index >= length) {
            return null;
        }
        int from = Math.max(0, index - 21);
        int to = Math.min(length, index + 22);
        return LeiScanner.at(area.getText(from, to), index - from);
    }

    /** @return title and body for a record */
    static String[] texts(LeiRecord r) {
        if (!r.found()) {
            return new String[] {"GLEIF: " + r.lei(), I18n.t("IdentifierHover.not-found")};
        }
        StringBuilder body = new StringBuilder();
        body.append(I18n.t("IndustryPanel.gleif-place", r.city() == null ? "" : r.city(), r.country() == null ? "" : r.country()))
                .append('\n').append(I18n.t("IndustryPanel.gleif-status", r.registrationStatus(), r.entityStatus()));
        if (r.nextRenewal() != null) {
            body.append('\n').append(I18n.t("IndustryPanel.gleif-renewal", r.nextRenewal().toString()));
        }
        LeiRecord.Problem p = r.problem();
        if (p != null) {
            body.append("\n\n").append(p.message());
        }
        return new String[] {r.legalName(), body.toString()};
    }

    private void show(String[] t, double x, double y) {
        if (area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        hideDelay.stop();
        title.setText(t[0]);
        body.setText(t[1]);
        box.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        box.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (box.getStylesheets().isEmpty()) {
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = IdentifierHover.class.getResource(sheet);
                if (url != null) {
                    box.getStylesheets().add(url.toExternalForm());
                }
            }
        }
        ThemeManager.syncAppearanceSheet(box);
        popup.show(area.getScene().getWindow(), x, y);
    }
}
