package org.fxt.freexmltoolkit.controls.shell;

import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.dialogs.TelemetryInfoDialog;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.SemanticColors;
import org.fxt.freexmltoolkit.service.telemetry.Telemetry;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryService;

/**
 * One-time, non-blocking notice about the anonymous (opt-out) telemetry, shown as a small card
 * in the bottom-right corner of the main window after the first start. Nothing is sent before
 * the notice was acknowledged: every button (OK, Settings…, What is sent?) marks it as shown.
 */
public final class TelemetryNotice extends VBox {

    private static final Logger logger = LogManager.getLogger(TelemetryNotice.class);
    private static final double MARGIN = 16;
    /** Leaves room for the status bar. */
    private static final double BOTTOM_OFFSET = 36;

    /** Notice text (also used by tests / docs). */
    public static final String MESSAGE = "FreeXmlToolkit sends anonymous usage statistics and error reports "
            + "to help improve the app. No file names, paths or content are ever sent.";

    private Popup popup;

    private TelemetryNotice(Runnable onOk, Runnable onSettings, Runnable onWhatIsSent, boolean dark) {
        super(10);
        setPadding(new Insets(14, 16, 12, 16));
        setMaxWidth(380);
        setPrefWidth(380);
        String background = dark ? "rgb(37,41,48)" : "white";
        String text = dark ? "rgb(229,231,235)" : "rgb(31,41,55)";
        setStyle("-fx-background-color: " + background + ";"
                + "-fx-background-radius: 10;"
                + "-fx-border-color: " + SemanticColors.INFO + ";"
                + "-fx-border-radius: 10;"
                + "-fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 14, 0, 0, 4);");

        IconifyIcon icon = new IconifyIcon("bi-shield-check");
        icon.setIconSize(20);
        icon.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(
                Color.web(SemanticColors.INFO)));
        Label title = new Label("Anonymous usage statistics");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + text + ";");
        HBox header = new HBox(8, icon, title);
        header.setAlignment(Pos.CENTER_LEFT);

        Label message = new Label(MESSAGE);
        message.setWrapText(true);
        message.setStyle("-fx-font-size: 12px; -fx-text-fill: " + text + ";");

        Hyperlink whatIsSent = new Hyperlink("What is sent?");
        whatIsSent.setId("telemetry-notice-what");
        whatIsSent.setOnAction(e -> onWhatIsSent.run());

        Button settings = new Button("Settings…");
        settings.setId("telemetry-notice-settings");
        settings.setOnAction(e -> onSettings.run());
        Button ok = new Button("OK");
        ok.setId("telemetry-notice-ok");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> onOk.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, whatIsSent, spacer, settings, ok);
        actions.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(header, message, actions);
    }

    /**
     * Shows the notice anchored to {@code owner} unless it was already acknowledged or
     * telemetry is inactive for this build. Must be called on the FX thread.
     *
     * @param owner        the main window
     * @param openSettings opens the Settings page
     */
    public static void showIfNeeded(Window owner, Runnable openSettings) {
        try {
            TelemetryService telemetry = Telemetry.get();
            if (!telemetry.isActive() || telemetry.isNoticeShown() || owner == null || !owner.isShowing()) {
                return;
            }
            show(owner, telemetry, openSettings);
        } catch (Throwable t) {
            logger.debug("Could not show telemetry notice: {}", t.toString());
        }
    }

    private static void show(Window owner, TelemetryService telemetry, Runnable openSettings) {
        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.setAutoFix(true);
        popup.setHideOnEscape(false);

        Runnable acknowledge = () -> {
            telemetry.markNoticeShown();
            popup.hide();
        };
        TelemetryNotice notice = new TelemetryNotice(
                acknowledge,
                () -> {
                    acknowledge.run();
                    if (openSettings != null) {
                        openSettings.run();
                    }
                },
                () -> {
                    acknowledge.run();
                    TelemetryInfoDialog.show(owner);
                },
                ThemeManager.currentIsDark());
        notice.popup = popup;
        popup.getContent().add(notice);
        popup.show(owner);

        ChangeListener<Number> reposition = (obs, a, b) -> notice.position(owner);
        owner.xProperty().addListener(reposition);
        owner.yProperty().addListener(reposition);
        owner.widthProperty().addListener(reposition);
        owner.heightProperty().addListener(reposition);
        popup.setOnHidden(e -> {
            owner.xProperty().removeListener(reposition);
            owner.yProperty().removeListener(reposition);
            owner.widthProperty().removeListener(reposition);
            owner.heightProperty().removeListener(reposition);
        });
        notice.position(owner);

        notice.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), notice);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void position(Window owner) {
        if (popup == null) {
            return;
        }
        applyCss();
        autosize();
        double w = prefWidth(-1);
        double h = prefHeight(w);
        popup.setX(owner.getX() + owner.getWidth() - w - MARGIN);
        popup.setY(owner.getY() + owner.getHeight() - h - MARGIN - BOTTOM_OFFSET);
    }
}
