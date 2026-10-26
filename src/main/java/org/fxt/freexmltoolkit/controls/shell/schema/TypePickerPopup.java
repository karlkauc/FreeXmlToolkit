package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice.TypeDetails;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.Category;

/**
 * The dropdown of a {@link TypePickerField}: a grouped, filterable list of {@link TypeChoice}s with
 * an explanation pane for the highlighted entry. Layout in {@code pages/views/TypePickerPopup.fxml}.
 *
 * <p>The popup never takes keyboard focus away from the field: rows are highlighted on hover and
 * picked on mouse release (both consumed before the {@code ListView} can request focus), and the
 * field forwards Up/Down/Enter to {@link #moveSelection(int)} / {@link #pickSelected()}.
 */
public final class TypePickerPopup {

    /** A row of the list: a group header or a type. */
    public sealed interface Row permits Header, Item {
    }

    /** A non-selectable group title ("Simple types", "Numeric", …). */
    public record Header(String title) implements Row {
    }

    /** A selectable type. */
    public record Item(TypeChoice choice) implements Row {
    }

    private static final double MAX_WIDTH = 620;
    private static final double PAGE = 8;

    private final Popup popup = new Popup();
    private final VBox content;

    @FXML private ListView<Row> typeList;
    @FXML private VBox detailBox;
    @FXML private Label detailTitle;
    @FXML private Label detailKind;
    @FXML private Label detailNote;
    @FXML private Label detailDoc;
    @FXML private HBox baseRow;
    @FXML private Label detailBase;
    @FXML private HBox derivationRow;
    @FXML private Label detailDerivation;
    @FXML private HBox exampleRow;
    @FXML private Label detailExample;
    @FXML private HBox sourceRow;
    @FXML private Label detailSource;
    @FXML private VBox detailFacts;
    @FXML private Label detailEmpty;
    @FXML private Label footer;

    private List<TypeChoice> choices = List.of();
    private Consumer<TypeChoice> onPick = c -> { };
    private String filter = "";
    private String current = "";

    public TypePickerPopup() {
        content = FxmlViews.loadContent(this, "/pages/views/TypePickerPopup.fxml");
        popup.getContent().add(content);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        typeList.setFocusTraversable(false);
        typeList.setCellFactory(lv -> new RowCell());
        typeList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> showDetails(nv));
        content.addEventFilter(KeyEvent.KEY_PRESSED, this::onPopupKey);
        showDetails(null);
    }

    /**
     * Shows the popup under {@code anchor}.
     *
     * @param anchor  the field the popup belongs to (must be in a shown window)
     * @param choices the entries to offer
     * @param filter  the initial filter text (empty shows everything)
     * @param current the current type reference, pre-highlighted when the filter is empty
     * @param onPick  called with the chosen entry; the popup hides itself first
     */
    public void show(Node anchor, List<TypeChoice> choices, String filter, String current, Consumer<TypeChoice> onPick) {
        if (anchor == null || anchor.getScene() == null || anchor.getScene().getWindow() == null) {
            return;
        }
        this.choices = choices == null ? List.of() : choices;
        this.onPick = onPick == null ? c -> { } : onPick;
        this.current = current == null ? "" : current.trim();
        applyTheme();
        double width = Math.min(MAX_WIDTH, Screen.getPrimary().getVisualBounds().getWidth() - 40);
        content.setPrefWidth(width);
        setFilter(filter);
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        if (!popup.isShowing()) {
            popup.show(anchor.getScene().getWindow(), b.getMinX(), b.getMaxY() + 2);
        }
    }

    /** Re-filters the list (case-insensitive contains on local and qualified names) and highlights the best row. */
    public void setFilter(String text) {
        this.filter = text == null ? "" : text.trim();
        List<Row> rows = buildRows();
        typeList.getSelectionModel().clearSelection();
        typeList.getItems().setAll(rows);
        int select = -1;
        if (filter.isEmpty() && !current.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) instanceof Item item && item.choice().qualifiedName().equals(current)) {
                    select = i;
                    break;
                }
            }
        }
        if (select < 0) {
            select = nextItem(-1, 1);
        }
        if (select >= 0) {
            typeList.getSelectionModel().select(select);
            typeList.scrollTo(Math.max(0, select - 1));
        } else {
            showDetails(null);
        }
    }

    private List<Row> buildRows() {
        Map<String, List<TypeChoice>> groups = new LinkedHashMap<>();
        for (TypeChoice choice : choices) {
            if (choice.matches(filter)) {
                groups.computeIfAbsent(choice.group(), g -> new ArrayList<>()).add(choice);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<TypeChoice>> group : groups.entrySet()) {
            rows.add(new Header(group.getKey()));
            List<TypeChoice> items = group.getValue();
            if (!filter.isEmpty()) {
                // exact match first, then prefix matches, original order otherwise
                List<TypeChoice> sorted = new ArrayList<>();
                items.stream().filter(c -> isExact(c, filter)).forEach(sorted::add);
                items.stream().filter(c -> !isExact(c, filter) && c.startsWith(filter)).forEach(sorted::add);
                items.stream().filter(c -> !c.startsWith(filter)).forEach(sorted::add);
                items = sorted;
            }
            items.forEach(c -> rows.add(new Item(c)));
        }
        return rows;
    }

    private static boolean isExact(TypeChoice choice, String text) {
        return choice.qualifiedName().equalsIgnoreCase(text) || choice.displayName().equalsIgnoreCase(text);
    }

    /** Moves the highlight by {@code delta} selectable rows (headers are skipped). */
    public void moveSelection(int delta) {
        if (delta == 0 || typeList.getItems().isEmpty()) {
            return;
        }
        int index = typeList.getSelectionModel().getSelectedIndex();
        int step = delta > 0 ? 1 : -1;
        int target = index;
        for (int i = 0; i < Math.abs(delta); i++) {
            int next = nextItem(target, step);
            if (next < 0) {
                break;
            }
            target = next;
        }
        if (target != index && target >= 0) {
            typeList.getSelectionModel().select(target);
            typeList.scrollTo(Math.max(0, target - 1));
        }
    }

    /** Moves by a page. */
    public void movePage(int direction) {
        moveSelection((int) (direction * PAGE));
    }

    private int nextItem(int from, int step) {
        List<Row> rows = typeList.getItems();
        for (int i = from + step; i >= 0 && i < rows.size(); i += step) {
            if (rows.get(i) instanceof Item) {
                return i;
            }
        }
        return -1;
    }

    /** @return the highlighted type, or {@code null} when no row is selectable. */
    public TypeChoice selectedChoice() {
        return typeList.getSelectionModel().getSelectedItem() instanceof Item item ? item.choice() : null;
    }

    /** Hides the popup and hands the highlighted type to the pick handler (no-op without one). */
    public boolean pickSelected() {
        TypeChoice choice = selectedChoice();
        if (choice == null) {
            return false;
        }
        pick(choice);
        return true;
    }

    private void pick(TypeChoice choice) {
        hide();
        onPick.accept(choice);
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    private void onPopupKey(KeyEvent e) {
        switch (e.getCode()) {
            case DOWN -> { moveSelection(1); e.consume(); }
            case UP -> { moveSelection(-1); e.consume(); }
            case PAGE_DOWN -> { movePage(1); e.consume(); }
            case PAGE_UP -> { movePage(-1); e.consume(); }
            case ENTER -> { pickSelected(); e.consume(); }
            case ESCAPE -> { hide(); e.consume(); }
            default -> {
                // other keys belong to the field
            }
        }
    }

    // ----- details -------------------------------------------------------------------------

    private void showDetails(Row row) {
        TypeDetails d = row instanceof Item item ? safeDetails(item.choice()) : null;
        boolean has = d != null;
        setShown(detailEmpty, !has);
        setShown(detailTitle, has);
        setShown(detailKind, has);
        if (!has) {
            setShown(detailNote, false);
            setShown(detailDoc, false);
            setShown(baseRow, false);
            setShown(derivationRow, false);
            setShown(exampleRow, false);
            setShown(sourceRow, false);
            detailFacts.getChildren().clear();
            setShown(detailFacts, false);
            return;
        }
        detailTitle.setText(d.title());
        detailKind.setText(d.kindLabel());
        text(detailNote, d.note());
        text(detailDoc, d.documentation());
        row(baseRow, detailBase, d.baseType());
        row(derivationRow, detailDerivation, d.derivation());
        row(exampleRow, detailExample, d.example());
        row(sourceRow, detailSource, d.source());
        detailFacts.getChildren().clear();
        for (String fact : d.facts()) {
            Label label = new Label("• " + fact);
            label.setWrapText(true);
            label.getStyleClass().add("fxt-type-picker-detail-fact");
            detailFacts.getChildren().add(label);
        }
        setShown(detailFacts, !d.facts().isEmpty());
    }

    private static TypeDetails safeDetails(TypeChoice choice) {
        try {
            return choice.details() == null ? null : choice.details().get();
        } catch (RuntimeException e) {
            return new TypeDetails(choice.qualifiedName(), choice.kind().name(), null, null,
                    choice.summary(), List.of(), null, null, null);
        }
    }

    private static void text(Label label, String value) {
        label.setText(value == null ? "" : value);
        setShown(label, value != null && !value.isBlank());
    }

    private static void row(HBox row, Label value, String text) {
        value.setText(text == null ? "" : text);
        setShown(row, text != null && !text.isBlank());
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void applyTheme() {
        content.getStyleClass().removeAll("root", "fxt-theme-dark", "fxt-theme-light");
        content.getStyleClass().addAll("root", ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (content.getStylesheets().isEmpty()) {
            for (String sheet : new String[]{"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = TypePickerPopup.class.getResource(sheet);
                if (url != null) {
                    content.getStylesheets().add(url.toExternalForm());
                }
            }
        }
    }

    // ----- cells ---------------------------------------------------------------------------

    /** The icon literal and colour of a choice's row. */
    static String iconFor(TypeChoice choice) {
        return switch (choice.kind()) {
            case SIMPLE -> "bi-type";
            case COMPLEX -> "bi-diagram-3";
            case BUILTIN -> switch (categoryOf(choice)) {
                case STRING -> "bi-fonts";
                case NUMERIC -> "bi-123";
                case DATE_TIME -> "bi-calendar3";
                case BINARY -> "bi-file-binary";
                case BOOLEAN -> "bi-toggle-on";
                case URI_NAMES -> "bi-link-45deg";
                case IDENTITY -> "bi-fingerprint";
                case ANY -> "bi-braces";
            };
        };
    }

    private static DesignTokens.ColorToken colorFor(TypeChoice choice) {
        return switch (choice.kind()) {
            case SIMPLE -> DesignTokens.ColorToken.TEAL;
            case COMPLEX -> DesignTokens.ColorToken.PURPLE;
            case BUILTIN -> switch (categoryOf(choice)) {
                case STRING -> DesignTokens.ColorToken.PRIMARY;
                case NUMERIC -> DesignTokens.ColorToken.INDIGO;
                case DATE_TIME -> DesignTokens.ColorToken.ACCENT;
                case BINARY, ANY -> DesignTokens.ColorToken.NEUTRAL;
                case BOOLEAN -> DesignTokens.ColorToken.SUCCESS;
                case URI_NAMES -> DesignTokens.ColorToken.INFO;
                case IDENTITY -> DesignTokens.ColorToken.WARNING;
            };
        };
    }

    private static Category categoryOf(TypeChoice choice) {
        for (Category c : Category.values()) {
            if (c.displayName().equals(choice.group())) {
                return c;
            }
        }
        return Category.ANY;
    }

    private final class RowCell extends ListCell<Row> {
        private final HBox box = new HBox(8);
        private final IconifyIcon icon = new IconifyIcon();
        private final Label name = new Label();
        private final Label summary = new Label();

        RowCell() {
            // Never widen the list to the summary text: the cell follows the list width and the
            // summary label truncates with an ellipsis instead.
            setPrefWidth(0);
            setMinWidth(0);
            summary.setMinWidth(0);
            icon.setIconSize(14);
            name.getStyleClass().add("fxt-type-picker-row-name");
            name.setMinWidth(Label.USE_PREF_SIZE);
            summary.getStyleClass().add("fxt-type-picker-row-summary");
            summary.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(summary, Priority.ALWAYS);
            box.getChildren().addAll(icon, name, summary);
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // Highlight on hover, pick on release – consumed so the ListView never takes focus
            // from the field (keys keep going to the field).
            addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {
                if (getItem() instanceof Item) {
                    typeList.getSelectionModel().select(getIndex());
                }
            });
            addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (getItem() instanceof Item) {
                    typeList.getSelectionModel().select(getIndex());
                }
                e.consume();
            });
            addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
                if (getItem() instanceof Item item) {
                    pick(item.choice());
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().remove("fxt-type-picker-header");
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setDisable(false);
                return;
            }
            if (row instanceof Header header) {
                setText(header.title());
                setGraphic(null);
                setDisable(true);
                getStyleClass().add("fxt-type-picker-header");
                return;
            }
            TypeChoice choice = ((Item) row).choice();
            setDisable(false);
            setText(null);
            icon.setIconLiteral(iconFor(choice));
            SemanticIcon.bind(icon, colorFor(choice));
            name.setText(choice.qualifiedName());
            summary.setText(choice.summary() == null ? "" : choice.summary());
            setGraphic(box);
        }
    }

    // ----- test accessors ------------------------------------------------------------------

    /** @return the rows currently listed (tests). */
    public List<Row> rows() {
        return List.copyOf(typeList.getItems());
    }

    /** @return the title shown in the detail pane (tests). */
    String detailTitleText() {
        return detailTitle.isVisible() ? detailTitle.getText() : "";
    }

    /** @return the documentation shown in the detail pane (tests). */
    String detailDocText() {
        return detailDoc.isVisible() ? detailDoc.getText() : "";
    }

    /** @return the popup's content node (tests). */
    VBox content() {
        return content;
    }
}
