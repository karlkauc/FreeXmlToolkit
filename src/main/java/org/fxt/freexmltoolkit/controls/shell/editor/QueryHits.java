package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.ResultItem;

/**
 * The hits of a Query Console run: a Text | Hits switch and a "2 of 3" navigator in the results
 * header, and the list of result items. A located node is shown in the active document's current
 * view ({@link EditorHost#revealQueryHits}); atomic values and nodes the query built are listed
 * without a link. The hits belong to the document text they were found in: after an edit or a tab
 * switch they are stale and clear their highlights.
 */
final class QueryHits {

    private final EditorHost editorHost;
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton textToggle = new ToggleButton(I18n.t("QueryHits.text"));
    private final ToggleButton hitsToggle = new ToggleButton(I18n.t("QueryHits.hits"));
    private final Label counter = new Label();
    private final Button previous = new Button(null, icon("bi-chevron-up"));
    private final Button next = new Button(null, icon("bi-chevron-down"));
    private final HBox bar;
    private final ListView<ResultItem> list = new ListView<>();
    private final Node textView;

    private List<ResultItem> items = List.of();
    /** The located hits in list order, and their indexes in {@link #items}. */
    private List<NodeMatch> located = List.of();
    private List<Integer> locatedIndex = List.of();
    private int current = -1;
    private String revision = "";
    private boolean stale;
    private boolean selecting;

    /**
     * @param textView the console's text result view, swapped with the hit list by the switch
     */
    QueryHits(EditorHost editorHost, Node textView) {
        this.editorHost = editorHost;
        this.textView = textView;
        textToggle.setToggleGroup(modeGroup);
        hitsToggle.setToggleGroup(modeGroup);
        textToggle.setId("query-results-text");
        hitsToggle.setId("query-results-hits");
        textToggle.getStyleClass().add("fxt-view-seg");
        hitsToggle.getStyleClass().add("fxt-view-seg");
        textToggle.setFocusTraversable(false);
        hitsToggle.setFocusTraversable(false);
        HBox switcher = new HBox(textToggle, hitsToggle);
        switcher.getStyleClass().add("fxt-view-switch");
        previous.setId("query-hit-previous");
        next.setId("query-hit-next");
        previous.getStyleClass().add("fxt-tool-button");
        next.getStyleClass().add("fxt-tool-button");
        previous.setTooltip(new Tooltip(I18n.t("QueryHits.previous")));
        next.setTooltip(new Tooltip(I18n.t("QueryHits.next")));
        previous.setAccessibleText(I18n.t("QueryHits.previous"));
        next.setAccessibleText(I18n.t("QueryHits.next"));
        previous.setOnAction(e -> step(-1));
        next.setOnAction(e -> step(1));
        counter.setId("query-hit-counter");
        counter.getStyleClass().add("fxt-output-status");
        bar = new HBox(6, switcher, counter, previous, next);
        bar.setAlignment(Pos.CENTER_LEFT);

        list.setId("query-hits-list");
        list.getStyleClass().add("fxt-query-hits");
        list.setCellFactory(v -> new HitCell());
        list.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (!selecting && newV != null && newV.intValue() >= 0) {
                int at = locatedIndex.indexOf(newV.intValue());
                if (at >= 0) {
                    show(at);
                }
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                int at = locatedIndex.indexOf(list.getSelectionModel().getSelectedIndex());
                if (at >= 0) {
                    show(at);
                }
                e.consume();
            }
        });
        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                modeGroup.selectToggle(oldT);
                return;
            }
            showList(newT == hitsToggle);
        });
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> markStale());
        clear();
    }

    /** @return the header bar (switch, counter, previous/next) */
    HBox bar() {
        return bar;
    }

    /** @return the hit list, placed where the text view is */
    ListView<ResultItem> list() {
        return list;
    }

    /**
     * Shows the items of a run on the document revision {@code revision}; the first located hit
     * is shown in the document at once. Without located nodes the console shows text only.
     */
    void setItems(List<ResultItem> runItems, String revision) {
        editorHost.clearQueryHits();
        this.items = runItems == null ? List.of() : List.copyOf(runItems);
        this.revision = revision;
        this.stale = false;
        List<NodeMatch> hits = new ArrayList<>();
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isLocated()) {
                hits.add(items.get(i).match());
                index.add(i);
            }
        }
        located = hits;
        locatedIndex = index;
        current = -1;
        selecting = true;
        list.getItems().setAll(items);
        selecting = false;
        boolean any = !hits.isEmpty();
        bar.setVisible(any);
        bar.setManaged(any);
        if (!any) {
            showList(false);
            counter.setText("");
            return;
        }
        modeGroup.selectToggle(hitsToggle);
        show(0);
    }

    /** No hits (a new run starts, the console closes). */
    void clear() {
        items = List.of();
        located = List.of();
        locatedIndex = List.of();
        current = -1;
        list.getItems().clear();
        bar.setVisible(false);
        bar.setManaged(false);
        showList(false);
        editorHost.clearQueryHits();
    }

    /** Next ({@code +1}) or previous ({@code -1}) hit, wrapping around. */
    void step(int direction) {
        if (located.isEmpty()) {
            return;
        }
        int size = located.size();
        show(current < 0 ? 0 : Math.floorMod(current + direction, size));
    }

    /** @return the counter text ("2 of 3"), for tests */
    String counterText() {
        return counter.getText();
    }

    /** @return the located hits, for tests */
    List<NodeMatch> locatedHits() {
        return located;
    }

    private void show(int at) {
        if (stale || !revision.equals(editorHost.activeRevisionKey())) {
            markStale();
            return;
        }
        current = at;
        counter.setText(I18n.t("QueryHits.counter", at + 1, located.size()));
        selecting = true;
        list.getSelectionModel().select(locatedIndex.get(at));
        list.scrollTo(Math.max(0, locatedIndex.get(at) - 2));
        selecting = false;
        editorHost.revealQueryHits(located, at);
    }

    private void markStale() {
        if (located.isEmpty() || stale) {
            return;
        }
        stale = true;
        editorHost.clearQueryHits();
        counter.setText(I18n.t("QueryHits.stale"));
    }

    private void showList(boolean hits) {
        list.setVisible(hits);
        list.setManaged(hits);
        textView.setVisible(!hits);
        textView.setManaged(!hits);
        if (!hits && modeGroup.getSelectedToggle() != textToggle) {
            modeGroup.selectToggle(textToggle);
        }
    }

    /** "/order[1]/item[2]" from a hit's positional path (plus "/@name" for an attribute). */
    static String location(NodeMatch match) {
        String path = match.path().stream().map(s -> s.localName() + "[" + s.position() + "]")
                .collect(Collectors.joining("/", "/", ""));
        if (match.kind() == net.sf.saxon.s9api.XdmNodeKind.ATTRIBUTE && match.attrName() != null) {
            return path + "/@" + match.attrName();
        }
        if (match.kind() == net.sf.saxon.s9api.XdmNodeKind.TEXT) {
            return path + "/text()";
        }
        return path;
    }

    private static IconifyIcon icon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(14);
        return icon;
    }

    /** A row: the node's kind and location with its value, or the plain value. */
    private static final class HitCell extends ListCell<ResultItem> {
        @Override
        protected void updateItem(ResultItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            if (item.isLocated()) {
                NodeMatch m = item.match();
                String kind = switch (m.kind()) {
                    case ATTRIBUTE -> "bi-at";
                    case TEXT -> "bi-fonts";
                    default -> "bi-code-slash";
                };
                setGraphic(icon(kind));
                setText(location(m) + (item.value().isEmpty() ? "" : "  ·  " + item.value()));
                setTooltip(new Tooltip(I18n.t("QueryHits.line", m.line())));
                getStyleClass().remove("fxt-query-hit-value");
            } else {
                setGraphic(icon("bi-dot"));
                setText(item.value());
                setTooltip(new Tooltip(I18n.t("QueryHits.not-in-document")));
                if (!getStyleClass().contains("fxt-query-hit-value")) {
                    getStyleClass().add("fxt-query-hit-value");
                }
            }
        }
    }
}
