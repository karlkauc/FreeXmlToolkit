package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.util.VersionUtil;

/**
 * The Welcome / Dashboard landing shown when no document is open, implementing the
 * Figma "Redesign · Unified — Welcome / Dashboard" frame: a hero, a row of
 * quick-action cards (New File / Open File / Open Folder / From URL), a recent-files
 * list with a Clear link, and a Tools grid whose cards switch shell activities.
 */
public class EditorWelcomePane extends VBox {

    private final ObservableList<File> recentFiles = FXCollections.observableArrayList();
    private ListView<File> recentList;
    private final Label recentStat = new Label("0");
    private final Label favoritesStat = new Label("0");
    private final Label templatesStat = new Label("0");
    private final Label queriesStat = new Label("0");

    /** Data-backed dashboard counters (recent files, favorites, templates, saved queries). */
    public record WelcomeStats(int recentFiles, int favorites, int templates, int savedQueries) {
    }

    /**
     * @param onNew         invoked with {@link EditorFileType#XML} for the New File card
     * @param onOpen        invoked for the Open File card (shows a file chooser)
     * @param onOpenRecent  invoked with a recent file when the user selects it
     * @param onClearRecent invoked when the Clear link is pressed
     * @param onAction      invoked with an action key for the remaining cards:
     *                      {@code open-folder}, {@code from-url}, and the tool/activity ids
     *                      {@code validation}, {@code transform}, {@code schema}, {@code pdf},
     *                      {@code signature}, {@code favorites}
     */
    public EditorWelcomePane(Consumer<EditorFileType> onNew, Runnable onOpen, Consumer<File> onOpenRecent,
            Runnable onClearRecent, Consumer<String> onAction) {
        getStyleClass().add("fxt-editor-empty-state");

        VBox content = new VBox(24,
                buildHero(),
                buildQuickActions(onNew, onOpen, onAction),
                buildStats(),
                buildTips(),
                buildLowerRow(onOpenRecent, onClearRecent, onAction));
        content.getStyleClass().add("fxt-welcome-content");
        content.setPadding(new Insets(40));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("fxt-welcome-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    /** Replaces the recent-files list (most-recent first). */
    public void setRecentFiles(List<File> files) {
        // Clear the selection before replacing the items to avoid a JavaFX
        // ListViewBehavior IndexOutOfBoundsException (see javafx-listview-setall-crash).
        if (recentList != null) {
            recentList.getSelectionModel().clearSelection();
        }
        recentFiles.setAll(files);
    }

    /** Updates the dashboard stat cards with the latest counts. */
    public void setStats(WelcomeStats stats) {
        if (stats == null) {
            return;
        }
        recentStat.setText(Integer.toString(stats.recentFiles()));
        favoritesStat.setText(Integer.toString(stats.favorites()));
        templatesStat.setText(Integer.toString(stats.templates()));
        queriesStat.setText(Integer.toString(stats.savedQueries()));
    }

    // ----- stats + tips ----------------------------------------------------

    private Region buildStats() {
        HBox row = new HBox(16,
                statCard("welcome-stat-recent", "bi-clock-history", "Recent files", recentStat),
                statCard("welcome-stat-favorites", "bi-star", "Favorites", favoritesStat),
                statCard("welcome-stat-templates", "bi-collection", "Templates", templatesStat),
                statCard("welcome-stat-queries", "bi-braces", "Saved queries", queriesStat));
        for (javafx.scene.Node card : row.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        return row;
    }

    private Region statCard(String numberId, String iconLiteral, String label, Label numberLabel) {
        StackPane tile = new StackPane(icon(iconLiteral, 16));
        tile.getStyleClass().add("fxt-card-icon");

        numberLabel.setId(numberId);
        numberLabel.getStyleClass().add("fxt-welcome-stat-number");
        Label caption = new Label(label);
        caption.getStyleClass().add("fxt-welcome-stat-label");

        HBox card = new HBox(12, tile, new VBox(0, numberLabel, caption));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("fxt-welcome-stat-card");
        return card;
    }

    private Region buildTips() {
        Label tip = new Label("Tip: drag a file onto the window to open it · Ctrl+F to find, Ctrl+H to "
                + "replace · drop an XSD on an XML to bind it for validation.");
        tip.setWrapText(true);
        HBox banner = new HBox(10, icon("bi-lightbulb", 16), tip);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("fxt-welcome-tips");
        return banner;
    }

    // ----- hero ------------------------------------------------------------

    private Region buildHero() {
        StackPane tile = new StackPane(icon("bi-code-slash", 28));
        tile.getStyleClass().add("fxt-welcome-hero-icon");

        Label title = new Label("Welcome to FreeXmlToolkit");
        title.getStyleClass().add("fxt-welcome-hero-title");

        Label subtitle = new Label("The modern XML · XSD · XSLT · Schematron workbench");
        subtitle.getStyleClass().add("fxt-welcome-hero-subtitle");

        Label badge = new Label("v" + VersionUtil.getVersion() + " · up to date",
                icon("bi-check-circle-fill", 12));
        badge.getStyleClass().add("fxt-welcome-version-badge");

        HBox subRow = new HBox(10, subtitle, badge);
        subRow.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(6, title, subRow);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox hero = new HBox(18, tile, text);
        hero.setAlignment(Pos.CENTER_LEFT);
        return hero;
    }

    // ----- quick actions ---------------------------------------------------

    private Region buildQuickActions(Consumer<EditorFileType> onNew, Runnable onOpen, Consumer<String> onAction) {
        Button neu = quickCard("welcome-new", "bi-file-earmark-plus", "New File", "XML · XSD · XSLT", true);
        neu.setOnAction(e -> onNew.accept(EditorFileType.XML));

        Button open = quickCard("welcome-open", "bi-folder2-open", "Open File", "Browse your disk", false);
        open.setOnAction(e -> onOpen.run());

        Button folder = quickCard("welcome-openfolder", "bi-folder", "Open Folder", "Open a workspace", false);
        folder.setOnAction(e -> onAction.accept("open-folder"));

        Button url = quickCard("welcome-url", "bi-box-arrow-up-right", "From URL", "Fetch & open", false);
        url.setOnAction(e -> onAction.accept("from-url"));

        HBox row = new HBox(16, neu, open, folder, url);
        for (Button b : List.of(neu, open, folder, url)) {
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setMaxWidth(Double.MAX_VALUE);
        }
        return row;
    }

    private Button quickCard(String id, String iconLiteral, String title, String subtitle, boolean primary) {
        StackPane tile = new StackPane(icon(iconLiteral, 18));
        tile.getStyleClass().add(primary ? "fxt-card-icon-primary" : "fxt-card-icon");

        Label t = new Label(title);
        t.getStyleClass().add(primary ? "fxt-card-title-on-primary" : "fxt-card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add(primary ? "fxt-card-subtitle-on-primary" : "fxt-card-subtitle");

        HBox g = new HBox(12, tile, new VBox(2, t, s));
        g.setAlignment(Pos.CENTER_LEFT);

        Button b = new Button();
        b.setId(id);
        b.setGraphic(g);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("fxt-quick-card");
        if (primary) {
            b.getStyleClass().add("fxt-quick-card-primary");
        }
        return b;
    }

    // ----- recent files + tools -------------------------------------------

    private Region buildLowerRow(Consumer<File> onOpenRecent, Runnable onClearRecent, Consumer<String> onAction) {
        VBox recent = buildRecent(onOpenRecent, onClearRecent);
        VBox tools = buildTools(onAction);
        HBox row = new HBox(24, recent, tools);
        HBox.setHgrow(recent, Priority.ALWAYS);
        HBox.setHgrow(tools, Priority.ALWAYS);
        recent.setMaxWidth(Double.MAX_VALUE);
        tools.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }

    private VBox buildRecent(Consumer<File> onOpenRecent, Runnable onClearRecent) {
        Label header = new Label("Recent files");
        header.getStyleClass().add("fxt-welcome-section-title");

        Hyperlink clear = new Hyperlink("Clear");
        clear.setId("welcome-clear");
        clear.getStyleClass().add("fxt-welcome-clear");
        clear.setOnAction(e -> onClearRecent.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = new HBox(header, spacer, clear);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        recentList = new ListView<>(recentFiles);
        recentList.getStyleClass().add("fxt-welcome-recent");
        recentList.setPlaceholder(new Label("No recent files"));
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
        VBox.setVgrow(recentList, Priority.ALWAYS);

        return new VBox(12, headerRow, recentList);
    }

    private VBox buildTools(Consumer<String> onAction) {
        Label header = new Label("Tools");
        header.getStyleClass().add("fxt-welcome-section-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.getColumnConstraints().addAll(equalColumn(), equalColumn());

        Button[] cards = {
                toolCard("tool-validation", "validation", "bi-check2-circle", "Validate", "XSD & Schematron", onAction),
                toolCard("tool-transform", "transform", "bi-arrow-repeat", "Transform", "XSLT 3.0 / XQuery", onAction),
                toolCard("tool-schema", "schema", "bi-diagram-3", "Schema", "Graphical XSD editor", onAction),
                toolCard("tool-pdf", "pdf", "bi-file-earmark-pdf", "PDF / FOP", "XSL-FO to PDF", onAction),
                toolCard("tool-signature", "signature", "bi-shield-lock", "Signature", "XML-DSig sign & verify", onAction),
                toolCard("tool-favorites", "favorites", "bi-star", "Favorites", "Pinned files", onAction)
        };
        for (int i = 0; i < cards.length; i++) {
            grid.add(cards[i], i % 2, i / 2);
            GridPane.setHgrow(cards[i], Priority.ALWAYS);
        }

        return new VBox(12, header, grid);
    }

    private static ColumnConstraints equalColumn() {
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        col.setHgrow(Priority.ALWAYS);
        return col;
    }

    private Button toolCard(String id, String activityKey, String iconLiteral, String title, String subtitle,
            Consumer<String> onAction) {
        StackPane tile = new StackPane(icon(iconLiteral, 16));
        tile.getStyleClass().add("fxt-card-icon");

        Label t = new Label(title);
        t.getStyleClass().add("fxt-card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("fxt-card-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox g = new HBox(12, tile, new VBox(2, t, s), spacer, icon("bi-chevron-right", 14));
        g.setAlignment(Pos.CENTER_LEFT);

        Button b = new Button();
        b.setId(id);
        b.setGraphic(g);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("fxt-tool-card");
        // Make the graphic fill the button so the chevron sits flush right (CSS padding is 16).
        g.prefWidthProperty().bind(b.widthProperty().subtract(32));
        b.setOnAction(e -> onAction.accept(activityKey));
        return b;
    }

    // ----- helpers ---------------------------------------------------------

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon i = new IconifyIcon(literal);
        i.setIconSize(size);
        return i;
    }

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
