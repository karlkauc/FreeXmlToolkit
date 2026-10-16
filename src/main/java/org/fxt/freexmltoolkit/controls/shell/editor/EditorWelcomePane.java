package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.util.ProjectLinks;
import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * The Welcome / Dashboard landing shown when no document is open, implementing the
 * Figma "Redesign · Unified — Welcome / Dashboard" frame: a hero, a row of
 * quick-action cards (New File / Open File / Open Folder / From URL), a recent-files
 * list with a Clear link, and a task list whose rows lead into a workflow.
 *
 * <p>First start (UX plan 5.1): counters and the activity trend appear only once they show
 * something; until there are recent files an "Open an Example" card is offered.
 */
public class EditorWelcomePane extends VBox {

    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();
    private final Consumer<EditorFileType> onNew;
    private final Runnable onOpen;
    private final Runnable onClearRecent;
    private final Consumer<String> onAction;
    @FXML private Label versionBadge;
    @FXML private Region fundsXmlSection;
    @FXML private WelcomeQuickCard exampleCard;
    @FXML private HBox statsRow;
    @FXML private VBox taskList;
    @FXML private WelcomeStatCard recentStat;
    @FXML private WelcomeStatCard favoritesStat;
    @FXML private WelcomeStatCard templatesStat;
    @FXML private WelcomeStatCard queriesStat;
    @FXML private VBox trendBox;
    @FXML private javafx.scene.shape.Polyline sparkline;
    @FXML private HBox trendRow;
    @FXML private HBox sponsorBanner;
    @FXML private ListView<File> recentList;
    private int sparklinePointCount;

    /** Data-backed dashboard counters (recent files, favorites, templates, saved queries). */
    public record WelcomeStats(int recentFiles, int favorites, int templates, int savedQueries) {
    }

    /**
     * @param onNew         invoked with {@link EditorFileType#XML} for the New File card
     * @param onOpen        invoked for the Open File card (shows a file chooser)
     * @param onOpenRecent  invoked with a recent file when the user selects it
     * @param onClearRecent invoked when the Clear link is pressed
     * @param onAction      invoked with an action key for the remaining cards:
     *                      {@code open-folder}, {@code from-url}, {@code open-example}, the FundsXML
     *                      keys ({@code fundsxml-*}) and the task keys {@code task-validate},
     *                      {@code task-transform}, {@code task-query}, {@code task-schema},
     *                      {@code task-pdf}, {@code task-sign}
     */
    public EditorWelcomePane(Consumer<EditorFileType> onNew, Runnable onOpen, Consumer<File> onOpenRecent,
            Runnable onClearRecent, Consumer<String> onAction) {
        this.onNew = onNew;
        this.onOpen = onOpen;
        this.onClearRecent = onClearRecent;
        this.onAction = onAction;
        FxmlViews.load(this, "/pages/views/EditorWelcomePane.fxml");

        versionBadge.setText("v" + VersionUtil.getVersion() + " · up to date");
        sponsorBanner.getChildren().add(0, ProjectLinks.sponsorIcon(16));
        fillTrend();

        recentList.setItems(recentFiles);
        recentList.setCellFactory(lv -> new RecentCell());
        recentList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                // Defer out of the selection-change processing (opening builds an editor and
                // mutates other lists); running it inline re-enters the ListViewBehavior
                // listener and throws (see javafx-listview-setall-crash).
                javafx.application.Platform.runLater(() -> {
                    recentList.getSelectionModel().clearSelection();
                    onOpenRecent.accept(newV);
                });
            }
        });
    }

    @FXML
    public void onNew() {
        onNew.accept(EditorFileType.XML);
    }

    @FXML
    public void onOpen() {
        onOpen.run();
    }

    /** Reports the action key a card carries as {@code userData} (e.g. {@code open-folder}). */
    @FXML
    public void onCardAction(ActionEvent event) {
        onAction.accept((String) ((Node) event.getSource()).getUserData());
    }

    @FXML
    public void onSponsor() {
        ProjectLinks.openInBrowser(ProjectLinks.SPONSORS_URL);
    }

    @FXML
    public void onClearRecent() {
        onClearRecent.run();
    }

    /** Replaces the recent-files list (most-recent first). */
    public void setRecentFiles(List<File> files) {
        // Clear the selection before replacing the items to avoid a JavaFX
        // ListViewBehavior IndexOutOfBoundsException (see javafx-listview-setall-crash).
        recentList.getSelectionModel().clearSelection();
        recentFiles.setAll(files);
    }

    /**
     * Shows or hides the FundsXML quick-access section. Shown only when the
     * FundsXML extension is enabled and content is cached.
     */
    public void setFundsXmlVisible(boolean visible) {
        fundsXmlSection.setVisible(visible);
        fundsXmlSection.setManaged(visible);
    }

    /**
     * Updates the dashboard stat cards with the latest counts. A row of zeros says nothing, so
     * the counters only show once one of them is above zero; the example card is offered while
     * there are no recent files (UX plan 5.1).
     */
    public void setStats(WelcomeStats stats) {
        if (stats == null) {
            return;
        }
        recentStat.setValue(Integer.toString(stats.recentFiles()));
        favoritesStat.setValue(Integer.toString(stats.favorites()));
        templatesStat.setValue(Integer.toString(stats.templates()));
        queriesStat.setValue(Integer.toString(stats.savedQueries()));
        boolean anyData = stats.recentFiles() > 0 || stats.favorites() > 0
                || stats.templates() > 0 || stats.savedQueries() > 0;
        show(statsRow, anyData);
        show(exampleCard, stats.recentFiles() == 0);
    }

    /** @return the keys of the task rows, in order (for tests) */
    java.util.List<String> taskKeys() {
        return taskList.getChildren().stream().map(n -> (String) n.getUserData()).toList();
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ----- activity trend ----------------------------------------------------

    /** Draws the 7-day sparkline and adds one colour-coded mini-card per feature category. */
    private void fillTrend() {
        java.util.List<Integer> totals = WelcomeTrend.dailyTotals(7);
        // Nothing recorded in the last 7 days: a flat line and 0 % bars say nothing (UX plan 5.1).
        show(trendBox, totals.stream().anyMatch(t -> t > 0));
        java.util.List<Double> norm = WelcomeTrend.normalize(totals);
        double w = 220, h = 40;
        if (norm.size() >= 2) {
            double step = w / (norm.size() - 1);
            for (int i = 0; i < norm.size(); i++) {
                sparkline.getPoints().addAll(i * step, h - norm.get(i) * h);
            }
        }
        this.sparklinePointCount = sparkline.getPoints().size() / 2;

        var stats = org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl.getInstance();
        try {
            var statsModel = stats.getStatistics();
            for (var cat : org.fxt.freexmltoolkit.service.SkillTracker.getFeaturesByCategory()) {
                double progress = org.fxt.freexmltoolkit.service.SkillTracker
                        .getCategoryProgress(cat.name(), statsModel);
                Region card = trendCategoryCard(cat.name(), progress, categoryAccent(cat.name()));
                HBox.setHgrow(card, Priority.ALWAYS);
                trendRow.getChildren().add(card);
            }
        } catch (Throwable ignored) {
            // no stats model available → omit the strip gracefully
        }
        if (trendRow.getChildren().isEmpty()) {
            trendBox.getChildren().remove(trendRow);
        }
    }

    /**
     * Workflow accent per feature category (spec 2026-09-26 §2, Welcome) as a looked-up colour name
     * ({@code -fxt-wf-<id>-accent}). Inline styles resolve looked-up colours through the scene, so the
     * dot and bar follow a live light/dark switch instead of baking one theme's hex. Package-private for tests.
     */
    static String categoryAccent(String category) {
        return org.fxt.freexmltoolkit.controls.theme.Workflow.forCategory(category).accent().cssVariable();
    }

    /**
     * A compact, colour-coded category card: a coloured dot + name, a tinted progress bar and a
     * percentage caption. Used in the ACTIVITY (LAST 7 DAYS) strip.
     *
     * @param category the category display name
     * @param progress discovery progress as a 0–100 percentage
     * @param color    the category accent colour (a looked-up colour name such as -fxt-wf-validation-accent)
     */
    private Region trendCategoryCard(String category, double progress, String color) {
        Region dot = new Region();
        dot.setMinSize(8, 8);
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");

        Label name = new Label(category);
        name.getStyleClass().add("fxt-trend-cat-name");
        HBox nameRow = new HBox(6, dot, name);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        // getCategoryProgress returns a 0–100 percentage; ProgressBar wants 0.0–1.0.
        javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(progress / 100.0);
        bar.getStyleClass().add("fxt-trend-cat-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + color + ";");

        Label pct = new Label(Math.round(progress) + "%");
        pct.getStyleClass().add("fxt-trend-cat-pct");

        VBox card = new VBox(6, nameRow, bar, pct);
        card.getStyleClass().add("fxt-trend-cat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** @return the number of points in the rendered sparkline (for tests/observers). */
    public int getSparklinePointCount() {
        return sparklinePointCount;
    }

    // ----- helpers ---------------------------------------------------------

    private static String relativeTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        long minutes = (System.currentTimeMillis() - millis) / 60_000;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        long days = hours / 24;
        if (days == 1) {
            return "yesterday";
        }
        if (days < 7) {
            return days + " days ago";
        }
        return (days / 7) + " wk ago";
    }

    /** Renders a recent file with its file-type icon, name, parent path and relative time. */
    private static final class RecentCell extends ListCell<File> {
        private final Label name = new Label();
        private final Label path = new Label();
        private final Label time = new Label();
        private final IconifyIcon icon = new IconifyIcon();
        private final HBox row;

        RecentCell() {
            name.getStyleClass().add("fxt-recent-name");
            path.getStyleClass().add("fxt-recent-path");
            time.getStyleClass().add("fxt-recent-time");
            icon.setIconSize(16);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row = new HBox(10, icon, new VBox(2, name, path), spacer, time);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                icon.setIconLiteral(EditorFileType.fromFileName(item.getName()).icon());
                name.setText(item.getName());
                path.setText(item.getParent() == null ? "" : item.getParent());
                time.setText(relativeTime(item.lastModified()));
                setGraphic(row);
            }
        }
    }
}
