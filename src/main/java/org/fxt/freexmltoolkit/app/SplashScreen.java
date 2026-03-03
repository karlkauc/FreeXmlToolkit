package org.fxt.freexmltoolkit.app;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Animated splash screen shown during application startup.
 *
 * <p>Displays logo, app name, version, and a progress bar with
 * smooth animations. Enforces a minimum display time of 1.5 seconds
 * to prevent flashing on fast machines.
 */
public class SplashScreen {

    private static final Logger logger = LogManager.getLogger(SplashScreen.class);

    private static final double WIDTH = 550;
    private static final double HEIGHT = 350;
    private static final long MIN_DISPLAY_MS = 1500;

    /**
     * Loading steps with display message and progress value.
     */
    public enum LoadingStep {
        INITIALIZING("Initializing...", 0.0),
        LOADING_FONTS("Loading fonts...", 0.15),
        PREPARING_SERVICES("Preparing services...", 0.30),
        LOADING_UI("Loading UI...", 0.55),
        CONFIGURING("Configuring...", 0.80),
        READY("Ready", 1.0);

        private final String message;
        private final double progress;

        LoadingStep(String message, double progress) {
            this.message = message;
            this.progress = progress;
        }

        public String getMessage() {
            return message;
        }

        public double getProgress() {
            return progress;
        }
    }

    private Stage stage;
    private VBox container;
    private ImageView logoView;
    private Label titleLabel;
    private Label versionLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private long showTimeMs;

    /**
     * Shows the splash screen with entrance animations.
     */
    public void show() {
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("FreeXmlToolkit");

        // Try to set the taskbar icon
        try {
            stage.getIcons().add(new Image(
                    getClass().getResourceAsStream("/img/logo.png")));
        } catch (Exception e) {
            logger.debug("Could not load splash icon", e);
        }

        container = new VBox(12);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(40, 40, 30, 40));
        container.setPrefSize(WIDTH, HEIGHT);
        container.setMaxSize(WIDTH, HEIGHT);
        container.getStyleClass().add("splash-container");

        // Logo
        logoView = createLogo();

        // Title
        titleLabel = new Label("FreeXmlToolkit");
        titleLabel.getStyleClass().add("splash-title");

        // Version
        versionLabel = new Label("v" + getVersion());
        versionLabel.getStyleClass().add("splash-version");

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("splash-progress-bar");
        progressBar.setPrefWidth(WIDTH - 120);
        progressBar.setMaxWidth(WIDTH - 120);

        // Status
        statusLabel = new Label("Initializing...");
        statusLabel.getStyleClass().add("splash-status");

        // Spacers for visual balance
        var topSpacer = new javafx.scene.layout.Region();
        topSpacer.setPrefHeight(10);
        var midSpacer = new javafx.scene.layout.Region();
        midSpacer.setPrefHeight(8);

        container.getChildren().addAll(
                topSpacer,
                logoView,
                titleLabel,
                versionLabel,
                midSpacer,
                progressBar,
                statusLabel
        );

        // Set initial animation states
        container.setOpacity(0);
        logoView.setOpacity(0);
        logoView.setScaleX(0.7);
        logoView.setScaleY(0.7);
        titleLabel.setOpacity(0);
        titleLabel.setTranslateY(15);
        versionLabel.setOpacity(0);
        progressBar.setOpacity(0);
        statusLabel.setOpacity(0);

        StackPane root = new StackPane(container);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        // Load CSS
        try {
            var cssUrl = getClass().getResource("/css/splash-screen.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            logger.debug("Could not load splash CSS", e);
        }

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        showTimeMs = System.currentTimeMillis();
        playEntranceAnimation();
    }

    /**
     * Updates the progress bar and status text.
     *
     * @param step the current loading step
     */
    public void updateProgress(LoadingStep step) {
        if (stage == null || !stage.isShowing()) {
            return;
        }

        statusLabel.setText(step.getMessage());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(progressBar.progressProperty(), step.getProgress(),
                                Interpolator.EASE_BOTH)
                )
        );
        timeline.play();
    }

    /**
     * Dismisses the splash screen with a fade-out animation.
     * Enforces minimum display time before fading out.
     *
     * @param onComplete callback invoked after the splash is fully dismissed
     */
    public void dismiss(Runnable onComplete) {
        if (stage == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        long elapsed = System.currentTimeMillis() - showTimeMs;
        long remaining = MIN_DISPLAY_MS - elapsed;

        Runnable fadeOutAndClose = () -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                stage.close();
                stage = null;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            fadeOut.play();
        };

        if (remaining > 0) {
            PauseTransition wait = new PauseTransition(Duration.millis(remaining));
            wait.setOnFinished(e -> fadeOutAndClose.run());
            wait.play();
        } else {
            fadeOutAndClose.run();
        }
    }

    private ImageView createLogo() {
        ImageView iv = new ImageView();
        try {
            Image logo = new Image(getClass().getResourceAsStream("/img/logo.png"));
            iv.setImage(logo);
            iv.setFitWidth(80);
            iv.setFitHeight(80);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
        } catch (Exception e) {
            logger.debug("Could not load splash logo", e);
        }
        return iv;
    }

    private void playEntranceAnimation() {
        // 1. Container fades in (400ms)
        FadeTransition containerFade = new FadeTransition(Duration.millis(400), container);
        containerFade.setFromValue(0);
        containerFade.setToValue(1.0);

        // 2. Logo scales from 0.7→1.0 + fades in (500ms), starts at T+200ms
        PauseTransition logoPause = new PauseTransition(Duration.millis(200));

        ScaleTransition logoScale = new ScaleTransition(Duration.millis(500), logoView);
        logoScale.setFromX(0.7);
        logoScale.setFromY(0.7);
        logoScale.setToX(1.0);
        logoScale.setToY(1.0);
        logoScale.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition logoFade = new FadeTransition(Duration.millis(500), logoView);
        logoFade.setFromValue(0);
        logoFade.setToValue(1.0);

        // 3. Title slides up + fades in (300ms), starts at T+400ms
        PauseTransition titlePause = new PauseTransition(Duration.millis(200));

        FadeTransition titleFade = new FadeTransition(Duration.millis(300), titleLabel);
        titleFade.setFromValue(0);
        titleFade.setToValue(1.0);

        Timeline titleSlide = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(titleLabel.translateYProperty(), 0, Interpolator.EASE_OUT)
                )
        );

        // 4. Version fades in (250ms), starts at T+550ms
        PauseTransition versionPause = new PauseTransition(Duration.millis(150));

        FadeTransition versionFade = new FadeTransition(Duration.millis(250), versionLabel);
        versionFade.setFromValue(0);
        versionFade.setToValue(1.0);

        // 5. Progress bar + status appear (250ms), starts at T+700ms
        PauseTransition progressPause = new PauseTransition(Duration.millis(150));

        FadeTransition progressFade = new FadeTransition(Duration.millis(250), progressBar);
        progressFade.setFromValue(0);
        progressFade.setToValue(1.0);

        FadeTransition statusFade = new FadeTransition(Duration.millis(250), statusLabel);
        statusFade.setFromValue(0);
        statusFade.setToValue(1.0);

        // Build the sequence
        SequentialTransition sequence = new SequentialTransition(
                containerFade,
                logoPause,
                // Logo animations run together via parallel trick: start both, use longer duration
                new javafx.animation.ParallelTransition(logoScale, logoFade),
                titlePause,
                new javafx.animation.ParallelTransition(titleFade, titleSlide),
                versionPause,
                versionFade,
                progressPause,
                new javafx.animation.ParallelTransition(progressFade, statusFade)
        );

        sequence.play();
    }

    private String getVersion() {
        // Try to read version from build properties or package info
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "1.6.2";
    }
}
