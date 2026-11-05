package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;


import java.util.ArrayList;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;

/**
 * Flat row-based XML View (XMLSpy-style).
 *
 * <p>Renders XML as a flat list of rows laid out as nested containers, the way XMLSpy's
 * Grid View does:</p>
 * <ul>
 *   <li>One row per element, attribute, text node, comment, etc.</li>
 *   <li>Each container's children form a <em>block</em> docked at the container's value
 *       column: every block has its own name-column width (the widest label in it)</li>
 *   <li>An expanded container's name cell spans its whole child block; a collapsed one
 *       shows a grey one-line preview of its content</li>
 *   <li>Repeating siblings render as an embedded table beside the owner's name cell</li>
 *   <li>Hairline cell frames, type icons per row, {@code <n>} instance numbers</li>
 * </ul>
 *
 * <p>The canvas is model-agnostic: everything document-specific (flattening, embedded
 * tables, selection, edit commands, context menu) is delegated to a {@link GridModelAdapter}.
 * {@link XmlCanvasView} binds it to the XML instance model; the JSON grid binds it to
 * the JSON model, so both grids share one look and one set of interactions.</p>
 *
 * @param <N> the model node type
 * @author Claude Code
 * @since 2.0
 */
public class GridCanvasView<N> extends Pane implements XmlSearchTarget {


    // ==================== Model adapter ====================

    /** Everything model-specific: flattening, tables, selection, edit commands, menu. */
    final GridModelAdapter<N> adapter;

    // ==================== Collaborators ====================

    /** Draws the grid. */
    final GridPainter<N> painter = new GridPainter<>(this);
    /** Embedded-table interaction. */
    final GridTableInteraction<N> tables = new GridTableInteraction<>(this);
    /** The inline editor. */
    final GridInlineEditor<N> editor = new GridInlineEditor<>(this);
    /** Node, column-header and separator drags. */
    final GridDragSupport<N> drags = new GridDragSupport<>(this);

    // ==================== Canvas ====================

    final Canvas canvas;
    final GraphicsContext gc;
    final Pane canvasContainer;
    final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;

    // ==================== Flat Row Data ====================

    /** Complete flat list of all rows (visible and hidden). */
    private List<FlatRow> allRows = new ArrayList<>();

    /** Cached list of currently visible rows (after expand/collapse filtering). */
    List<FlatRow> visibleRows = new ArrayList<>();

    /** Cumulative Y offset for each visible row index, accounting for expanded tables above. */
    double[] rowYPositions = new double[0];
    /**
     * For each visible row, the index of its last visible descendant (itself if none), computed once
     * per {@link #recalculateVisibleRows()}. Rendering and hit-testing used to scan forward through all
     * descendants of every expanded row from index 0, per frame and per mouse move.
     */
    private int[] lastDescendantIndex = new int[0];

    // ==================== Search State ====================

    /** Last search text used to build {@link #searchMatches}. */
    private String lastSearchText = "";

    /** Rows matching the current search text, in document order. */
    private List<XmlCanvasSearch.Hit> searchMatches = new ArrayList<>();

    /** Index of the currently highlighted match within {@link #searchMatches}, or -1. */
    private int currentMatchIndex = -1;

    // ==================== Layout Constants ====================

    static final double ROW_HEIGHT = GridMetrics.ROW_HEIGHT;
    static final double LINE_HEIGHT = GridMetrics.LINE_HEIGHT;
    static final double ICON_AREA_WIDTH = GridMetrics.ICON_AREA_WIDTH;
    static final double EXPAND_BAR_WIDTH = GridMetrics.EXPAND_BAR_WIDTH;
    private static final double SCROLLBAR_WIDTH = 14;
    static final double LEFT_MARGIN = 8;
    static final double MIN_NAME_COL_WIDTH = GridBlockLayout.MIN_NAME_COL_WIDTH;
    static final double MIN_VALUE_COL_WIDTH = GridBlockLayout.MIN_VALUE_COL_WIDTH;
    /** Horizontal padding of a value cell's text. */
    static final double VALUE_PADDING = GridBlockLayout.VALUE_PADDING;
    /** Soft length of the inline preview of a collapsed container. */
    static final int PREVIEW_MAX_CHARS = 200;
    /** Length of the preview shown in the hover tooltip. */
    private static final int TOOLTIP_MAX_CHARS = 2000;

    /**
     * Measured text metrics shared with the embedded tables (real FX font measurement); replaced
     * as a whole when word wrap or the cell-line limit changes (see {@link #applyMetrics}).
     */
    GridMetrics metrics = new GridMetrics(TextMeasurer.fx(), GridMetrics.DEFAULT_WRAP_WIDTH);

    /** Width of the root block's name column (the test hook {@link #nameColumnWidthValue()}). */
    private double nameColumnWidth = MIN_NAME_COL_WIDTH;

    /**
     * The per-document view state (expand state, selection, name-column widths, table toggles)
     * that survives the row rebuild after every edit; keyed by the adapter's stable row keys.
     */
    final GridViewState viewState = new GridViewState();

    /**
     * Own height of each visible row: its value line(s), an embedded table's height for a table
     * owner, 0 for an expanded container (its name cell spans the child block instead).
     */
    double[] rowHeights = new double[0];

    /** Index of each visible row in {@link #visibleRows} (identity), rebuilt per layout. */
    java.util.Map<FlatRow, Integer> rowIndexMap = new java.util.IdentityHashMap<>();

    /** The tooltip showing the full preview of a hovered collapsed container. */
    private final Tooltip previewTooltip = new Tooltip();
    private FlatRow previewTooltipRow = null;

    // ==================== Scroll State ====================

    double scrollOffsetX = 0;
    double scrollOffsetY = 0;
    double totalHeight = 0;
    double totalWidth = 0;

    // ==================== Zoom ====================

    /** Smallest zoom factor (50 %). */
    public static final double ZOOM_MIN = 0.5;
    /** Largest zoom factor (300 %). */
    public static final double ZOOM_MAX = 3.0;
    /** Zoom step for Ctrl +/- and Ctrl+wheel. */
    public static final double ZOOM_STEP = 0.1;

    /**
     * The zoom factor applied to the whole grid (fonts, rows, icons, tables). All layout
     * stays in unscaled model pixels; the factor is applied when drawing, when converting
     * mouse coordinates and when placing the inline editor.
     */
    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1.0);

    // ==================== Selection / Hover State ====================

    FlatRow selectedRow = null;
    /**
     * All selected rows (Ctrl+click adds/removes, Shift+click selects a sibling range); the
     * primary selection {@link #selectedRow} is always part of it. Shift+click on the expand
     * arrow of a selected row expands/collapses every selected row (XMLSpy).
     */
    private final List<FlatRow> multiSelection = new ArrayList<>();
    private FlatRow hoveredRow = null;
    /** The row whose expand arrow is under the pointer. */
    FlatRow hoveredExpandBar = null;

    /** A drop target: the row under the pointer and where the payload goes. */
    record DropTarget(FlatRow row, DropPosition position) {
    }

    // ==================== Context Menu ====================

    /** Built on first use (right-click / shortcut): it costs ~20 ms of FX time per view. */
    GridContextMenu<N> contextMenu;

    // ==================== Highlight State ====================

    FlatRow highlightedRow = null;
    Color highlightColor;  // Yellow highlight (theme-dependent, see applyPalette)

    // ==================== Status Bar & Toast ====================

    private StackPane toastContainer;

    // ==================== Document Change Callback ====================

    private java.util.function.Consumer<String> onDocumentModified;

    /**
     * Weakly registered with DesignTokens — the instance field keeps it alive
     * exactly as long as this view; see DesignTokens.addThemeListener.
     */
    private final java.util.function.Consumer<org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme>
            themeListener = theme -> {
                painter.applyPalette(theme);
                render();
            };

    /** Repaints when a user cell colour changes (weakly registered with {@link GridColors}). */
    private final Runnable gridColorListener = () -> {
        painter.applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme());
        render();
    };

    // ==================== Constructor ====================

    /**
     * Creates a canvas over the given model adapter. Subclasses bind a concrete model:
     * {@link XmlCanvasView} for XML instances, the JSON grid's canvas for JSON documents.
     *
     * @param adapter the model adapter (never {@code null})
     */
    protected GridCanvasView(GridModelAdapter<N> adapter) {
        this.adapter = adapter;

        painter.applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme());
        org.fxt.freexmltoolkit.controls.theme.DesignTokens.addThemeListener(themeListener);
        GridColors.addListener(gridColorListener);

        // NOTE: Do NOT enable node caching on the canvas or its container. The canvas is
        // re-rendered on every change and can grow large for big documents; with setCache(true)
        // JavaFX's CacheFilter renders the whole canvas into one backing RTTexture, which fails
        // (null texture / "Unrecognized PGCanvas token") once it exceeds the GPU texture limit.
        this.canvas = new Canvas(800, 600);
        this.gc = canvas.getGraphicsContext2D();

        this.canvasContainer = new Pane();
        canvasContainer.getChildren().add(canvas);

        // Create scroll bars
        this.vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(Orientation.VERTICAL);
        vScrollBar.setMinWidth(SCROLLBAR_WIDTH);
        vScrollBar.setPrefWidth(SCROLLBAR_WIDTH);

        this.hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMinHeight(SCROLLBAR_WIDTH);
        hScrollBar.setPrefHeight(SCROLLBAR_WIDTH);

        // Layout
        getChildren().addAll(canvasContainer, vScrollBar, hScrollBar);

        // Resize listener
        widthProperty().addListener((obs, oldVal, newVal) -> onResize());
        heightProperty().addListener((obs, oldVal, newVal) -> onResize());

        // Scroll bar listeners
        vScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollOffsetY = newVal.doubleValue();
            render();
        });

        hScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollOffsetX = newVal.doubleValue();
            render();
        });

        // Event handlers
        setupEventHandlers();
        drags.setupDragAndDrop();

        // Listen for document changes (document replaced / changed outside the canvas)
        adapter.addModelListener(this::onDocumentChanged);

        // Model-specific one-off hooks (e.g. the XML mixed-content warning)
        adapter.installViewHooks(this);

        // Initial rebuild
        rebuildTree();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) {
            return;
        }

        // Scroll bars only where the content overflows; the canvas takes the space otherwise.
        boolean[] needed = neededScrollBars(w, h);
        double canvasW = w - (needed[0] ? SCROLLBAR_WIDTH : 0);
        double canvasH = h - (needed[1] ? SCROLLBAR_WIDTH : 0);

        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);
        canvas.setLayoutX(0);
        canvas.setLayoutY(0);
        canvasContainer.resizeRelocate(0, 0, canvasW, canvasH);

        // Pane does not size children whose layout is overridden: resize the bars explicitly.
        vScrollBar.setVisible(needed[0]);
        hScrollBar.setVisible(needed[1]);
        vScrollBar.resizeRelocate(w - SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, canvasH);
        hScrollBar.resizeRelocate(0, h - SCROLLBAR_WIDTH, canvasW, SCROLLBAR_WIDTH);

        // Update scroll bars
        updateScrollBars();
        render();
    }

    /**
     * Decides which scroll bars the content needs in a {@code w × h} view. A vertical bar
     * narrows the viewport and may make a horizontal one necessary, and vice versa.
     *
     * @return {vertical, horizontal}
     */
    private boolean[] neededScrollBars(double w, double h) {
        double z = getZoom();
        boolean vertical = totalHeight > h / z;
        boolean horizontal = totalWidth > (w - (vertical ? SCROLLBAR_WIDTH : 0)) / z;
        if (horizontal && !vertical) {
            vertical = totalHeight > (h - SCROLLBAR_WIDTH) / z;
        }
        return new boolean[]{vertical, horizontal};
    }

    private void onResize() {
        updateScrollBars();
        render();
    }

    void updateScrollBars() {
        if (getWidth() > 0 && getHeight() > 0) {
            boolean[] needed = neededScrollBars(getWidth(), getHeight());
            if (needed[0] != vScrollBar.isVisible() || needed[1] != hScrollBar.isVisible()) {
                requestLayout(); // a bar appears or goes away: the canvas changes size
            }
        }
        double viewportWidth = viewportWidthModel();
        double viewportHeight = viewportHeightModel();

        // Vertical scroll bar
        if (totalHeight <= viewportHeight) {
            vScrollBar.setDisable(true);
            vScrollBar.setValue(0);
            scrollOffsetY = 0;
        } else {
            vScrollBar.setDisable(false);
            vScrollBar.setMin(0);
            vScrollBar.setMax(totalHeight - viewportHeight);
            vScrollBar.setVisibleAmount(thumbAmount(totalHeight, viewportHeight));
            vScrollBar.setBlockIncrement(viewportHeight * 0.9);
            vScrollBar.setUnitIncrement(ROW_HEIGHT);
            if (scrollOffsetY > vScrollBar.getMax()) {
                vScrollBar.setValue(vScrollBar.getMax());
            }
        }

        // Horizontal scroll bar
        if (totalWidth <= viewportWidth) {
            hScrollBar.setDisable(true);
            hScrollBar.setValue(0);
            scrollOffsetX = 0;
        } else {
            hScrollBar.setDisable(false);
            hScrollBar.setMin(0);
            hScrollBar.setMax(totalWidth - viewportWidth);
            hScrollBar.setVisibleAmount(thumbAmount(totalWidth, viewportWidth));
            hScrollBar.setBlockIncrement(100);
            hScrollBar.setUnitIncrement(20);
            if (scrollOffsetX > hScrollBar.getMax()) {
                hScrollBar.setValue(hScrollBar.getMax());
            }
        }
    }

    /**
     * The visible amount for a bar whose range is {@code 0 … total - viewport} (the scroll offset).
     * JavaFX sizes the thumb as {@code visibleAmount / (max - min)}; passing the viewport itself
     * made the thumb fill the whole track — and so unable to move — whenever the content was less
     * than twice the viewport. Scaled like this the thumb covers {@code viewport / total} of it.
     *
     * @param total    the content extent (model units), larger than the viewport
     * @param viewport the visible extent (model units)
     * @return the visible amount to set
     */
    static double thumbAmount(double total, double viewport) {
        return (total - viewport) * viewport / total;
    }

    // ==================== Zoom API & coordinate conversion ====================

    /** @return the zoom property (1.0 = 100 %) */
    public DoubleProperty zoomProperty() {
        return zoom;
    }

    /** @return the current zoom factor */
    public double getZoom() {
        return zoom.get();
    }

    /**
     * Sets the zoom factor, clamped to [{@link #ZOOM_MIN}, {@link #ZOOM_MAX}] and rounded
     * to a tenth. Cancels a running inline edit, keeps the scroll position (in model
     * units) and re-renders.
     *
     * @param factor the new zoom factor
     */
    public void setZoom(double factor) {
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, factor));
        double rounded = Math.round(clamped * 10.0) / 10.0;
        if (Math.abs(rounded - zoom.get()) < 0.0001) {
            return;
        }
        editor.cancelEditing();
        zoom.set(rounded);
        updateScrollBars();
        render();
    }

    /** Zooms in by one {@link #ZOOM_STEP}. */
    public void zoomIn() {
        setZoom(getZoom() + ZOOM_STEP);
    }

    /** Zooms out by one {@link #ZOOM_STEP}. */
    public void zoomOut() {
        setZoom(getZoom() - ZOOM_STEP);
    }

    /** Resets the zoom to 100 %. */
    public void zoomReset() {
        setZoom(1.0);
    }

    /** @return the viewport width in unscaled model pixels */
    double viewportWidthModel() {
        return canvas.getWidth() / getZoom();
    }

    /** @return the viewport height in unscaled model pixels */
    double viewportHeightModel() {
        return canvas.getHeight() / getZoom();
    }

    /** @return the model-space x (scroll applied) for a canvas-relative screen x */
    double toModelX(double screenX) {
        return screenX / getZoom() + scrollOffsetX;
    }

    /** @return the unscaled viewport y (scroll NOT applied) for a canvas-relative screen y */
    double toViewportY(double screenY) {
        return screenY / getZoom();
    }

    // ==================== Tree Building ====================

    /**
     * Rebuilds the flat row list from the document.
     * Preserves expand/collapse state across rebuilds by matching rows by label+type+depth.
     */
    void rebuildTree() {
        // Remember expand state (at any depth), open cells and selection by stable key
        if (selectedRow != null) {
            viewState.setSelectedKey(keyOf(selectedRow));
        }
        saveCellExpansion();

        if (!adapter.hasDocument()) {
            allRows = new ArrayList<>();
            visibleRows = new ArrayList<>();
            lastDescendantIndex = new int[0];
            rowIndexMap = new java.util.IdentityHashMap<>();
            selectedRow = null;
            totalHeight = 0;
            totalWidth = 0;
            updateScrollBars();
            render();
            return;
        }

        syncSavedFilters();
        allRows = FlatRow.groupSiblings(applyFilters(adapter.flatten(viewState::isTable)), siblingGroupSize);
        adapter.attachTables(allRows, this::markLayoutDirty);
        for (FlatRow row : allRows) {
            if (row.hasRepeatingTable()) {
                row.whenRepeatingTableReady(table -> configureTable(table, row)); // without building it now
            }
        }

        // Restore expand state and selection
        FlatRow restoredSelection = null;
        String selectedKey = viewState.getSelectedKey();
        for (FlatRow row : allRows) {
            String key = keyOf(row);
            if (row.isExpandable()) {
                Boolean wasExpanded = viewState.expandedState(key);
                if (wasExpanded != null) {
                    row.setExpanded(wasExpanded);
                }
            }
            if (restoredSelection == null && selectedKey != null && selectedKey.equals(key)) {
                restoredSelection = row;
            }
        }
        selectedRow = restoredSelection;
        multiSelection.clear();
        if (selectedRow != null) {
            selectedRow.setSelected(true);
            multiSelection.add(selectedRow);
        }
        fireSelectionChanged();
        hoveredRow = null;
        hoveredExpandBar = null;

        // Re-apply visibility based on restored or default expand state
        recalculateVisibility();

        recalculateVisibleRows();
        if (selectedRow == null && selectedKey != null) {
            // The selection was a row inside an opened cell: its grid exists again after the layout
            FlatRow[] nested = new FlatRow[1];
            forEachLiveRow(row -> {
                if (nested[0] == null && row.getBlock() != null && selectedKey.equals(keyOf(row))) {
                    nested[0] = row;
                }
            });
            if (nested[0] != null) {
                selectedRow = nested[0];
                selectedRow.setSelected(true);
                multiSelection.add(selectedRow);
                fireSelectionChanged();
            }
        }
        updateScrollBars();
        render();
    }

    /**
     * Marks the layout as needing recalculation. Used as a callback for table layout changes.
     */
    private void markLayoutDirty() {
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /** Whether a coalesced re-layout is already queued. */
    private boolean relayoutScheduled;
    /** True while the queued re-layout runs: invalidations it causes itself start no new one. */
    private boolean relayingOut;

    /**
     * Queues one re-layout of the whole grid for the next pulse, however many tables report a
     * change until then. Tables report from inside a layout or a paint (opened cells build their
     * grids while drawn), so the re-layout never runs synchronously.
     */
    private void scheduleRelayout() {
        if (relayoutScheduled || relayingOut) {
            return;
        }
        relayoutScheduled = true;
        javafx.application.Platform.runLater(() -> {
            relayoutScheduled = false;
            relayingOut = true;
            try {
                markLayoutDirty();
            } finally {
                relayingOut = false;
            }
        });
    }

    /** @return whether a re-layout is queued (tests) */
    boolean relayoutPendingForTest() {
        return relayoutScheduled;
    }

    /**
     * Recalculates which rows are visible based on the expand/collapse state.
     * Delegates to {@link FlatRow#applyVisibility(List)}, which keeps direct
     * attribute rows visible alongside their (possibly collapsed) element.
     */
    private void recalculateVisibility() {
        FlatRow.applyVisibility(allRows);
    }

    /**
     * Filters allRows by visibility, caches the result in visibleRows and lays the rows out
     * as nested blocks (XMLSpy-style), in three passes:
     * <ol>
     *   <li><b>block widths</b> — per container (the rows' {@code parentRow}), the widest name
     *       cell of its visible child rows, floored at {@link #MIN_NAME_COL_WIDTH}; a user-dragged
     *       width wins;</li>
     *   <li><b>x</b> — top-down: a row's name cell starts at its parent's value column
     *       ({@code labelX = parent.valueX}), its value column at {@code labelX + blockWidth};</li>
     *   <li><b>heights and y</b> — value lines (wrapped), the inline preview of a collapsed
     *       container, the embedded table of an expanded table owner (the row is as tall as the
     *       table), and 0 for an expanded container whose name cell spans its child block.</li>
     * </ol>
     */
    void recalculateVisibleRows() {
        GridBlockLayout.Result layout = GridBlockLayout.layout(allRows, LEFT_MARGIN, rootMeasure);
        visibleRows = new ArrayList<>(layout.visible());
        lastDescendantIndex = layout.lastDescendant();
        rowIndexMap = new java.util.IdentityHashMap<>(layout.index());
        rowYPositions = layout.y();
        rowHeights = layout.height();
        nameColumnWidth = layout.nameColumnWidth();
        totalHeight = layout.totalHeight();
        totalWidth = layout.width();
    }

    /** How {@link GridBlockLayout} measures the root grid's rows. */
    private final GridBlockLayout.Measure rootMeasure = new BlockMeasure() {
        @Override
        public Double nameColumnOverride(FlatRow parent) {
            return viewState.nameColumnWidth(blockKey(parent));
        }
    };

    /** Row measuring shared by every grid of this view; the name-column override is per grid. */
    abstract class BlockMeasure implements GridBlockLayout.Measure {
        @Override
        public GridMetrics metrics() {
            return metrics;
        }

        @Override
        public double labelWidth(FlatRow row) {
            return labelTextWidth(row) + labelExtrasWidth(row);
        }

        @Override
        public GridModelAdapter.FormulaView formula(FlatRow row) {
            return formulaOf(row);
        }

        @Override
        public String leafValueText(FlatRow row) {
            return adapter.decorateLeafValue(row);
        }

        @Override
        public String preview(FlatRow row) {
            return previewOf(row);
        }

        @Override
        public double availableWidth() {
            return canvas.getWidth();
        }
    }

    /** @return the stable key of a row; sibling-group rows are keyed below their parent */
    String keyOf(FlatRow row) {
        if (row.getType() == FlatRow.RowType.GROUP) {
            return keyOf(row.getParentRow()) + "/#group" + row.getLabel();
        }
        if (row.getType() == FlatRow.RowType.FILTER) {
            return keyOf(row.getParentRow()) + "/#filter";
        }
        return adapter.stableKey(row);
    }

    /** The sibling-group size (XMLSpy "maximum nodes per sibling group"), 0 = no grouping. */
    private int siblingGroupSize = 0;

    /** @return the sibling-group size, 0 = unlimited */
    public int getSiblingGroupSize() {
        return siblingGroupSize;
    }

    /**
     * Splits containers with more child rows than {@code size} into collapsed groups of that
     * many children (only one group per container open at a time).
     *
     * @param size the group size, 0 = no grouping
     */
    public void setSiblingGroupSize(int size) {
        int value = Math.max(0, size);
        if (value != siblingGroupSize) {
            siblingGroupSize = value;
            rebuildTree();
        }
    }

    /** @return the stable key of the block formed by the children of {@code parent} (root block: "") */
    String blockKey(FlatRow parent) {
        return parent == null ? GridViewState.ROOT_BLOCK : keyOf(parent);
    }

    /** @return the evaluated formula of a formula row (cached on the row), or {@code null} */
    GridModelAdapter.FormulaView formulaOf(FlatRow row) {
        if (row.getType() != FlatRow.RowType.PROCESSING_INSTRUCTION) {
            return null;
        }
        if (!row.hasFormulaComputed()) {
            row.setFormula(adapter.formula(row));
        }
        return row.getFormula();
    }

    /** @return the label a row shows: a formula's name, else the row label */
    String displayLabel(FlatRow row) {
        GridModelAdapter.FormulaView f = formulaOf(row);
        return f != null ? f.name() : row.getLabel();
    }

    /** @return the cached one-line preview of a container row (computed once per row) */
    private String previewOf(FlatRow row) {
        if (row.getType() == FlatRow.RowType.GROUP) {
            return null; // a group previews nothing (its node is the whole parent)
        }
        if (!row.hasCollapsedPreview()) {
            row.setCollapsedPreview(adapter.collapsedPreview(row, PREVIEW_MAX_CHARS));
        }
        return row.getCollapsedPreview();
    }

    /** @return whether the row shows the grey inline preview (a collapsed container without a value) */
    boolean showsPreview(FlatRow row) {
        return row.isExpandable() && !row.isExpanded() && row.getValue() == null && row.getValueBlock() != null;
    }

    /**
     * @return the height of the row's name cell: its own line(s) plus every visible descendant
     *         row (an expanded container's cell spans its whole child block)
     */
    double rowSpanHeight(int i) {
        int last = i < lastDescendantIndex.length ? lastDescendantIndex[i] : i;
        return rowYPositions[last] + rowHeights[last] - rowYPositions[i];
    }

    /** @return the measured width of the row's label as drawn */
    double labelTextWidth(FlatRow row) {
        String label = displayLabel(row);
        return label == null ? 0 : metrics.width(label, GridFont.ROW_BOLD);
    }

    /**
     * @return the width of what follows the label in the name cell: the {@code <n>} instance
     *         number, the "(n)" child count of a container and the table icon of a table owner
     */
    private double labelExtrasWidth(FlatRow row) {
        double w = 0;
        if (row.getInstanceIndex() > 0) {
            w += GridMetrics.CHILD_COUNT_GAP + metrics.width(instanceIndexText(row), GridFont.SMALL);
        }
        if (row.isExpandable()) {
            w += GridMetrics.CHILD_COUNT_GAP + metrics.width(childCountText(row), GridFont.SMALL);
        }
        if (row.showsTableGlyph()) {
            w += GridPainter.TABLE_ICON_SIZE + GridMetrics.CHILD_COUNT_GAP;
        }
        return w;
    }

    static String childCountText(FlatRow row) {
        return "(" + row.getChildCount() + ")";
    }

    static String instanceIndexText(FlatRow row) {
        return "<" + row.getInstanceIndex() + ">";
    }

    /** @return the x where the row's icon is drawn (right of the expand-arrow area) */
    double rowIconX(FlatRow row) {
        return row.getLabelX() + EXPAND_BAR_WIDTH;
    }

    /** @return the x where the row's label text starts */
    double rowTextX(FlatRow row) {
        return row.getLabelX() + EXPAND_BAR_WIDTH + ICON_AREA_WIDTH;
    }

    // ==================== Rendering ====================

    /**
     * Main render method. Clears the canvas and draws visible rows.
     */
    public void render() {
        painter.render();
    }

    // ==================== Filters ====================

    /** The error of a filter that could not be evaluated, by row key (shown on its filter line). */
    final java.util.Map<String, String> filterErrors = new java.util.HashMap<>();

    /**
     * Applies the defined filters to freshly flattened rows: below a filtered container only the
     * rows the adapter's predicate keeps remain (the matches, their ancestors and subtrees; the
     * container's own attributes stay), repeating groups are reduced to their matching members,
     * and every filtered container gets a virtual {@link FlatRow.RowType#FILTER} line as first
     * child showing the expression. Inactive filters keep their line but filter nothing.
     */
    private List<FlatRow> applyFilters(List<FlatRow> rows) {
        filterErrors.clear();
        activeFilters.clear();
        return applyFilters(rows, null);
    }

    /**
     * The evaluated active filters by the key of their container, for the opened cells below a
     * filtered container: a filter on the root also reduces the cells inside its tables.
     */
    private final java.util.Map<String, java.util.function.Predicate<Object>> activeFilters = new java.util.HashMap<>();

    /**
     * Applies the filters to the rows of a grid. For an opened cell, {@code anchor} is the cell's
     * virtual anchor (the record, or the element whose content the cell lists): a filter on it
     * reduces the cell's rows and its line comes first in the cell.
     *
     * @param anchor the anchor of an opened cell's rows, {@code null} for the root grid
     */
    private List<FlatRow> applyFilters(List<FlatRow> rows, FlatRow anchor) {
        if (viewState.filters().isEmpty()) {
            return rows;
        }
        java.util.Map<FlatRow, java.util.function.Predicate<Object>> predicates = new java.util.IdentityHashMap<>();
        java.util.Map<FlatRow, String> filtered = new java.util.IdentityHashMap<>();
        List<FlatRow> containers = rows;
        if (anchor != null) {
            containers = new ArrayList<>(rows.size() + 1);
            containers.add(anchor);
            containers.addAll(rows);
        }
        for (FlatRow row : containers) {
            if (!row.getType().isContainer() || row.isVirtual()) {
                continue;
            }
            String key = keyOf(row);
            String expression = viewState.filter(key);
            if (expression == null) {
                continue;
            }
            filtered.put(row, key);
            if (!viewState.isFilterActive(key)) {
                continue;
            }
            try {
                java.util.function.Predicate<Object> p = adapter.filterPredicate(row, expression);
                if (p != null) {
                    predicates.put(row, p);
                    activeFilters.put(key, p);
                }
            } catch (IllegalArgumentException e) {
                filterErrors.put(key, e.getMessage());
            }
        }
        // An opened cell below a filtered container (e.g. the root) is reduced by that filter too
        List<java.util.function.Predicate<Object>> inherited = new ArrayList<>();
        if (anchor != null) {
            String anchorKey = keyOf(anchor);
            activeFilters.forEach((key, p) -> {
                if (anchorKey.startsWith(key + "/")) {
                    inherited.add(p);
                }
            });
        }
        if (filtered.isEmpty() && inherited.isEmpty()) {
            return rows;
        }
        List<FlatRow> result = new ArrayList<>(rows.size() + filtered.size());
        if (anchor != null && filtered.containsKey(anchor)) {
            result.add(new FlatRow(FlatRow.RowType.FILTER, 0, anchor.getModelNode(), anchor,
                    null, filterLineText(filtered.get(anchor)), 0));
        }
        for (FlatRow row : rows) {
            if (!passesFilters(row, predicates) || !passesInherited(row, inherited)) {
                continue;
            }
            result.add(row);
            String key = filtered.get(row);
            if (key != null) {
                String expression = viewState.filter(key);
                FlatRow line = new FlatRow(FlatRow.RowType.FILTER, row.getDepth() + 1, row.getModelNode(), row,
                        null, filterLineText(key), 0);
                result.add(line);
            }
        }
        return result;
    }

    /** @return whether the row survives every filter of its ancestors (and reduces a table group) */
    private boolean passesFilters(FlatRow row, java.util.Map<FlatRow, java.util.function.Predicate<Object>> predicates) {
        if (predicates.isEmpty()) {
            return true;
        }
        FlatRow ancestor = row.getParentRow();
        while (ancestor != null) {
            java.util.function.Predicate<Object> p = predicates.get(ancestor);
            // the filtered element's own attributes and formulas stay (formulas summarize all its data)
            boolean ownAttribute = (row.getType().isAttribute() || adapter.isFormulaRow(row))
                    && row.getParentRow() == ancestor;
            if (p != null && !ownAttribute && !keeps(row, p)) {
                return false;
            }
            ancestor = ancestor.getParentRow();
        }
        return true;
    }

    /** @return whether a row of an opened cell survives the filters of containers above the cell */
    private boolean passesInherited(FlatRow row, List<java.util.function.Predicate<Object>> inherited) {
        if (adapter.isFormulaRow(row)) {
            return true;
        }
        for (java.util.function.Predicate<Object> p : inherited) {
            if (!keeps(row, p)) {
                return false;
            }
        }
        return true;
    }

    /** @return whether one filter keeps the row; a table group is reduced to its kept members */
    private static boolean keeps(FlatRow row, java.util.function.Predicate<Object> p) {
        if (!row.isTableGroupOwner()) {
            return p.test(row.getModelNode());
        }
        List<Object> kept = new ArrayList<>();
        for (Object member : row.getTableMembers()) {
            if (p.test(member)) {
                kept.add(member);
            }
        }
        if (kept.isEmpty()) {
            return false;
        }
        if (kept.size() < row.getTableMembers().size()) {
            row.setTableGroup(row.getTableGroupKey(), kept);
        }
        return true;
    }

    /** @return the key of the filtered container a filter line belongs to */
    private String filterKeyOf(FlatRow filterLine) {
        return keyOf(filterLine.getParentRow());
    }

    /**
     * Sets, changes or (blank) removes the filter of a container row and rebuilds.
     *
     * @param row        the container row
     * @param expression the expression, {@code null}/blank to remove the filter
     */
    public void setFilter(FlatRow row, String expression) {
        String key = keyOf(row);
        boolean blank = expression == null || expression.isBlank();
        viewState.setFilter(key, expression);
        if (!blank) {
            viewState.setFilterActive(key, true);
            row.setExpanded(true);
            viewState.setExpanded(key, true);
        }
        // A filter saved in the document follows: changed there too, removed from it with the filter
        boolean documentChanged = false;
        if (savedFilterKeys.contains(key)) {
            if (blank) {
                savedFilterKeys.remove(key);
                documentChanged = adapter.removeSavedFilter(row);
            } else {
                documentChanged = adapter.saveFilter(row, expression);
            }
        }
        if (documentChanged) {
            refresh();
        } else {
            rebuildTree();
        }
        String error = filterErrors.get(key);
        if (error != null) {
            showToast("Filter: " + error, ToastNotification.Type.WARNING);
        }
    }

    /** Switches the filter of the line's container on or off. */
    private void toggleFilter(FlatRow filterLine) {
        String key = filterKeyOf(filterLine);
        viewState.setFilterActive(key, !viewState.isFilterActive(key));
        rebuildTree();
    }

    /**
     * @return the row a filter of the current selection goes to: the selected row, or - while a
     *         row of an embedded table is selected - the element that holds the table
     */
    FlatRow filterTargetRow() {
        GridBlock cell = contextCellGrid();
        if (cell != null && cell.anchor() != null) {
            return cell.anchor();
        }
        if (selectedRow != null) {
            if (selectedRow.getType() == FlatRow.RowType.FILTER) {
                return selectedRow.getParentRow(); // a filter line stands for its container
            }
            // A repeating group stands for its members: the filter goes to the element holding it
            return selectedRow.isTableGroupOwner() ? selectedRow.getParentRow() : selectedRow;
        }
        RepeatingElementsTable table = tables.selectedTable;
        return table != null && table.getOwnerRow() != null ? table.getOwnerRow().getParentRow() : null;
    }

    /**
     * @return whether the selection is a container that can carry a filter; inside an opened cell
     *         that is the cell's anchor (e.g. the table record whose Datenpunkt cell is open)
     */
    public boolean canFilterSelection() {
        if (selectedRow == null && contextComplexCell() != null) {
            return adapter.supportsFilters(); // the cell is opened when the filter is edited
        }
        FlatRow target = filterTargetRow();
        return target != null && !target.isVirtual() && !target.isTableGroupOwner()
                && (target.isExpandable() || isCellAnchor(target)) && filterSupported(target);
    }

    /** @return whether the row is the virtual anchor of an opened cell's rows */
    private static boolean isCellAnchor(FlatRow row) {
        return row.getDepth() < 0;
    }

    private boolean filterSupported(FlatRow row) {
        return adapter.supportsFilters() && row.getType() == FlatRow.RowType.ELEMENT;
    }

    /** Asks for the filter expression of the selected container (empty removes the filter). */
    public void editFilterOfSelection() {
        FlatRow target = prepareFilterTarget();
        if (target != null) {
            editFilter(target);
        }
    }

    /**
     * @return the row the filter of the selection goes to, opening the context menu's complex cell
     *         first when its content is filtered; {@code null} when the selection cannot be filtered
     */
    FlatRow prepareFilterTarget() {
        if (!canFilterSelection()) {
            return null;
        }
        RepeatingElementsTable.TableRow host = contextComplexCell();
        if (selectedRow == null && host != null && !host.isColumnExpanded(contextTableColumn)) {
            // A filter on a cell's content: open the cell, its anchor carries the filter
            host.toggleColumnExpanded(contextTableColumn);
            contextTable.recalculateColumnWidths();
            recalculateVisibleRows();
            updateScrollBars();
            render();
        }
        return filterTargetRow();
    }

    /** @return whether the selection's filter target carries a filter (on or off) */
    public boolean hasFilterOnSelection() {
        FlatRow target = filterTargetRow();
        return target != null && viewState.filter(keyOf(target)) != null;
    }

    /** Removes the filter of the selection's filter target (e.g. via its filter line) and rebuilds. */
    public void removeFilterOfSelection() {
        FlatRow target = filterTargetRow();
        if (target != null && viewState.filter(keyOf(target)) != null) {
            setFilter(target, null);
        }
    }

    /** @return whether any filter is defined in this view */
    public boolean hasAnyFilter() {
        return !viewState.filters().isEmpty();
    }

    /** Removes every filter of this view and rebuilds. */
    public void removeAllFilters() {
        if (viewState.filters().isEmpty()) {
            return;
        }
        for (String key : List.copyOf(viewState.filters().keySet())) {
            viewState.setFilter(key, null);
        }
        savedFilterKeys.clear();
        if (adapter.removeAllSavedFilters()) {
            refresh(); // saved filters are removed from the document too
        } else {
            rebuildTree();
        }
    }

    // ==================== Saved filters ====================

    /** The keys of the filters that are saved in the document (as last synchronized). */
    private final java.util.Set<String> savedFilterKeys = new java.util.HashSet<>();

    /**
     * Takes the filters saved in the document into the view: a new or changed one comes in
     * <em>inactive</em> (a file never opens filtered unnoticed), one whose instruction is gone
     * (deleted in the text, undone) leaves the view. Filters set only in the view stay as they are.
     */
    private void syncSavedFilters() {
        if (!adapter.supportsSavedFilters()) {
            return;
        }
        java.util.Map<String, String> saved = adapter.savedFilters();
        for (java.util.Iterator<String> it = savedFilterKeys.iterator(); it.hasNext(); ) {
            String key = it.next();
            if (!saved.containsKey(key)) {
                viewState.setFilter(key, null);
                it.remove();
            }
        }
        saved.forEach((key, expression) -> {
            if (expression.isBlank()) {
                return;
            }
            if (!expression.equals(viewState.filter(key))) {
                viewState.setFilter(key, expression);
                viewState.setFilterActive(key, false);
            }
            savedFilterKeys.add(key);
        });
    }

    /** @return the text of a filter line: the expression, marked when it is saved in the document */
    private String filterLineText(String key) {
        String expression = viewState.filter(key);
        return savedFilterKeys.contains(key) ? expression + "   \u00b7 saved" : expression;
    }

    /** @return whether the filter of the selection's target is switched on; empty without a filter */
    public java.util.Optional<Boolean> filterActiveStateOfSelection() {
        if (!hasFilterOnSelection()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(viewState.isFilterActive(keyOf(filterTargetRow())));
    }

    /** Switches the filter of the selection's target on or off (as a click on its funnel). */
    public void toggleFilterActiveOfSelection() {
        if (hasFilterOnSelection()) {
            String key = keyOf(filterTargetRow());
            viewState.setFilterActive(key, !viewState.isFilterActive(key));
            rebuildTree();
        }
    }

    /**
     * @return whether the filter of the selection's target is saved in the document; empty without
     *         a filter or when the model cannot save filters
     */
    public java.util.Optional<Boolean> filterSavedStateOfSelection() {
        if (!adapter.supportsSavedFilters() || !hasFilterOnSelection()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(savedFilterKeys.contains(keyOf(filterTargetRow())));
    }

    /**
     * Saves the filter of the selection's target in the document, or removes it from there again
     * (the filter itself stays in the view). One undoable edit.
     */
    public void toggleFilterSavedOfSelection() {
        if (filterSavedStateOfSelection().isEmpty()) {
            return;
        }
        FlatRow target = filterTargetRow();
        String key = keyOf(target);
        boolean changed;
        if (savedFilterKeys.remove(key)) {
            changed = adapter.removeSavedFilter(target);
        } else {
            changed = adapter.saveFilter(target, viewState.filter(key));
            if (changed || adapter.isSavedFilter(target)) {
                savedFilterKeys.add(key);
            }
        }
        if (changed) {
            refresh();
        } else {
            rebuildTree();
        }
    }

    /** The table, row and column of the last table context menu (a complex cell filters its content). */
    private RepeatingElementsTable contextTable;
    private int contextTableRow = -1;
    private String contextTableColumn;

    /**
     * @return the table row whose complex cell the last table context menu was opened on, while
     *         that table row is still the selection; otherwise {@code null}
     */
    private RepeatingElementsTable.TableRow contextComplexCell() {
        if (selectedRow != null || contextTable == null || contextTable != tables.selectedTable
                || contextTableColumn == null || contextTableRow < 0
                || contextTableRow >= contextTable.getRows().size()
                || contextTable.getSelectedRow() != contextTable.getRows().get(contextTableRow)) {
            return null;
        }
        RepeatingElementsTable.TableRow row = contextTable.getRows().get(contextTableRow);
        return row.hasComplexChild(contextTableColumn) ? row : null;
    }

    /** @return the opened grid of the context menu's complex cell, or {@code null} */
    private GridBlock contextCellGrid() {
        RepeatingElementsTable.TableRow row = contextComplexCell();
        return row != null ? row.getCellGrid(contextTableColumn) : null;
    }

    /** Asks for the filter expression of a container row (empty removes the filter). */
    private void editFilter(FlatRow row) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(
                viewState.filter(keyOf(row)) != null ? viewState.filter(keyOf(row)) : "");
        dialog.setTitle("Filter");
        dialog.setHeaderText("XQuery filter on <" + row.getLabel() + ">\n"
                + "Shows only the matching nodes below it, e.g. Month[Min > 10]. Empty removes the filter.");
        dialog.setContentText("Expression:");
        dialog.getEditor().setPrefColumnCount(40);
        dialog.showAndWait().ifPresent(expression -> setFilter(row, expression));
    }

    /** Filters the root so that only the selected row and its subtree remain (XMLSpy "Filter to Focus"). */
    public void filterToFocus() {
        // The selected row, or the selected member of an embedded table
        Object target = selectedRow != null ? adapter.nodeOf(selectedRow) : selectedTableMember();
        if (target == null || allRows.isEmpty()) {
            return;
        }
        FlatRow root = allRows.stream().filter(r -> r.getParentRow() == null && r.getType().isContainer())
                .findFirst().orElse(null);
        if (root == null) {
            return;
        }
        String expression = adapter.focusFilterExpression(root, target);
        if (expression != null) {
            setFilter(root, expression);
        }
    }

    // ==================== Validation markers ====================

    /** Validation messages by 1-based source line ({@code null} = none / stale). */
    private java.util.Map<Integer, List<String>> problemsByLine = null;

    /**
     * Shows validation problems in the grid: rows (and table rows) whose node starts on a line
     * with a problem get a red marker and the messages as tooltip. The line numbers refer to the
     * parsed text, so the markers are dropped on the first grid edit (they become stale) until
     * the next validation publishes new ones.
     *
     * @param byLine messages per 1-based line; {@code null} or empty clears the markers
     */
    public void setProblems(java.util.Map<Integer, List<String>> byLine) {
        problemsByLine = byLine == null || byLine.isEmpty() ? null : new java.util.HashMap<>(byLine);
        render();
    }

    /** @return the problems of the row's own line, or {@code null} */
    List<String> problemsOf(FlatRow row) {
        if (problemsByLine == null) {
            return null;
        }
        int line = adapter.sourceLine(row);
        return line > 0 && !row.isTableGroupOwner() ? problemsByLine.get(line) : null;
    }

    /** @return the problems of a table row's member, or {@code null} */
    List<String> problemsOfMember(FlatRow owner, int memberIndex) {
        if (problemsByLine == null) {
            return null;
        }
        int[] lines = adapter.memberSourceLines(owner);
        return memberIndex < lines.length && lines[memberIndex] > 0 ? problemsByLine.get(lines[memberIndex]) : null;
    }

    // ==================== Scroll header ====================

    /** Height of the sticky scroll header (unscaled). */
    static final double SCROLL_HEADER_HEIGHT = 22;

    /** A clickable breadcrumb segment of the scroll header (x range in unscaled viewport units). */
    record HeaderSegment(FlatRow row, double x0, double x1) {
    }

    List<HeaderSegment> headerSegments = List.of();

    /** @return the middle of the row's segment in the painted scroll header, or -1 if it has none */
    double scrollHeaderSegmentMiddleForTest(FlatRow row) {
        return headerSegments.stream().filter(segment -> segment.row() == row)
                .mapToDouble(segment -> (segment.x0() + segment.x1()) / 2)
                .findFirst().orElse(-1);
    }

    /**
     * @return the rows the scroll header shows: the containers whose name cell starts above the
     *         viewport but reaches into it (the first visible row's ancestors, outermost first,
     *         plus that row itself when it is a scrolled-off spanning container)
     */
    List<FlatRow> scrollHeaderRows() {
        if (scrollOffsetY <= 0 || visibleRows.isEmpty()) {
            return List.of();
        }
        int first = findRowIndexAtY(scrollOffsetY + SCROLL_HEADER_HEIGHT);
        if (first < 0) {
            return List.of();
        }
        List<FlatRow> chain = new ArrayList<>();
        for (int idx : ancestorIndices(first)) {
            if (rowYPositions[idx] < scrollOffsetY + SCROLL_HEADER_HEIGHT) {
                chain.add(visibleRows.get(idx));
            }
        }
        return chain;
    }

    /**
     * Handles a click into the scroll header: selects the clicked ancestor and scrolls it into
     * view. Any click inside the header band is consumed (rows below it are hidden).
     *
     * @return whether the click was inside the header
     */
    private boolean handleScrollHeaderClick(double viewportX, double viewportY) {
        if (headerSegments.isEmpty() || viewportY >= SCROLL_HEADER_HEIGHT) {
            return false;
        }
        for (HeaderSegment segment : headerSegments) {
            if (viewportX >= segment.x0() && viewportX < segment.x1()) {
                FlatRow row = segment.row();
                selectRow(row);
                Integer idx = rowIndexMap.get(row);
                if (idx != null) {
                    animateScrollTo(rowYPositions[idx] - 4);
                }
                break;
            }
        }
        return true;
    }

    /** @return the visible-row indices of the row's ancestors, outermost first */
    List<Integer> ancestorIndices(int rowIndex) {
        List<Integer> indices = new ArrayList<>();
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return indices;
        }
        FlatRow parent = visibleRows.get(rowIndex).getParentRow();
        while (parent != null) {
            Integer idx = rowIndexMap.get(parent);
            if (idx == null) {
                break;
            }
            indices.add(0, idx);
            parent = parent.getParentRow();
        }
        return indices;
    }

    /**
     * Index of each row's last descendant: descendants are contiguous in the flat (pre-order) list, so a
     * row's run ends at the first following row that is not its descendant. One pass with a stack of the
     * open ancestors.
     */
    static int[] lastDescendantIndices(List<FlatRow> rows) {
        int[] last = new int[rows.size()];
        int[] stack = new int[rows.size()];
        int top = -1;
        for (int i = 0; i < rows.size(); i++) {
            FlatRow row = rows.get(i);
            while (top >= 0 && !FlatRow.isDescendantOf(row, rows.get(stack[top]))) {
                last[stack[top--]] = i - 1;
            }
            last[i] = i;
            stack[++top] = i;
        }
        while (top >= 0) {
            last[stack[top--]] = rows.size() - 1;
        }
        return last;
    }

    // ==================== Row Index Lookup ====================

    /**
     * Finds the visible row index at an absolute Y coordinate (in document space).
     * Uses binary search on rowYPositions for efficiency.
     *
     * @param absoluteY the Y coordinate in document space (includes scroll offset)
     * @return the visible row index, or -1 if outside bounds
     */
    int findRowIndexAtY(double absoluteY) {
        if (visibleRows.isEmpty() || rowYPositions.length == 0) {
            return -1;
        }

        // Binary search for the row containing this Y
        int lo = 0;
        int hi = visibleRows.size() - 1;

        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            double rowTop = rowYPositions[mid];
            double rowBottom = rowTop + getRowTotalHeight(mid);

            if (absoluteY < rowTop) {
                hi = mid - 1;
            } else if (absoluteY >= rowBottom) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }

        // Clamp to valid range
        return Math.min(lo, visibleRows.size() - 1);
    }

    /**
     * Finds the visible row index at a screen Y coordinate (relative to canvas).
     *
     * @param screenY the Y coordinate relative to the canvas
     * @return the visible row index, or -1 if outside bounds
     */
    private int findRowIndexAtScreenY(double screenY) {
        return findRowIndexAtY(screenY + scrollOffsetY);
    }

    /**
     * Gets the total height of a visible row, including any expanded inline table.
     *
     * @param visibleIndex the index in visibleRows
     * @return total height in pixels
     */
    private double getRowTotalHeight(int visibleIndex) {
        if (visibleIndex >= 0 && visibleIndex < rowHeights.length) {
            return rowHeights[visibleIndex];
        }
        return ROW_HEIGHT;
    }

    // ==================== Type change ====================

    /** The popup listing the node types a row can be converted to. */
    private javafx.scene.control.ContextMenu typeMenu;

    /** Shows the type menu (XMLSpy's node-type dropdown) for the row at the screen position. */
    private void showTypeMenu(FlatRow row, double screenX, double screenY) {
        if (typeMenu != null) {
            typeMenu.hide();
        }
        typeMenu = new javafx.scene.control.ContextMenu();
        for (String label : adapter.typeTargets(row)) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
            item.setOnAction(e -> changeRowType(row, label));
            typeMenu.getItems().add(item);
        }
        if (!typeMenu.getItems().isEmpty()) {
            typeMenu.show(canvas, screenX, screenY);
        }
    }

    /**
     * Converts the row's node to the given type and rebuilds.
     *
     * @param row         the row
     * @param targetLabel the target type label (one of the adapter's type targets)
     * @return whether the type changed
     */
    public boolean changeRowType(FlatRow row, String targetLabel) {
        if (!adapter.changeType(row, targetLabel)) {
            return false;
        }
        viewState.setSelectedKey(null);
        rebuildTree();
        notifyDocumentModified();
        return true;
    }

    /** @return the type menu shown last (test hook) */
    javafx.scene.control.ContextMenu typeMenuForTest() {
        return typeMenu;
    }

    // ==================== Table display toggles ====================

    /**
     * Shows the repeating group of the given row as individual numbered rows instead of a
     * table, or back (XMLSpy "Display as Table"). The row is either the table owner or a
     * member row of a group shown as standard rows.
     *
     * @param row the row; ignored when it belongs to no repeating group
     */
    public void toggleTableDisplay(FlatRow row) {
        String key = groupKeyOf(row);
        if (key == null) {
            return;
        }
        viewState.setTable(key, !viewState.isTable(key));
        rebuildTree();
    }

    /** Toggles the table display of the selected row's group (menu / keyboard entry point). */
    public void toggleTableDisplayOfSelection() {
        toggleTableDisplay(selectedRowOrTableOwner());
    }

    /**
     * @return {@code TRUE}/{@code FALSE} when the selection belongs to a repeating group shown
     *         as table / as rows, empty when it belongs to none
     */
    public java.util.Optional<Boolean> tableDisplayStateOfSelection() {
        String key = groupKeyOf(selectedRowOrTableOwner());
        return key == null ? java.util.Optional.empty() : java.util.Optional.of(viewState.isTable(key));
    }

    /**
     * Flips the rows and columns of the given table owner's table, or back
     * (XMLSpy "Flip Rows/Columns").
     *
     * @param owner the table owner row; ignored otherwise
     */
    public void flipTable(FlatRow owner) {
        if (owner == null || !owner.hasRepeatingTable() || owner.getTableGroupKey() == null) {
            return;
        }
        String key = owner.getTableGroupKey();
        boolean flipped = !viewState.isFlipped(key);
        viewState.setFlipped(key, flipped);
        owner.getRepeatingTable().setFlipped(flipped);
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /** Flips the table the selection belongs to (menu entry point). */
    public void flipTableOfSelection() {
        FlatRow owner = selectedRowOrTableOwner();
        if (owner != null && owner.hasRepeatingTable()) {
            flipTable(owner);
        }
    }

    /** @return the flip state of the selection's table, empty when the selection is in none */
    public java.util.Optional<Boolean> flipStateOfSelection() {
        FlatRow owner = selectedRowOrTableOwner();
        if (owner == null || !owner.hasRepeatingTable() || owner.getTableGroupKey() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(viewState.isFlipped(owner.getTableGroupKey()));
    }

    /** @return the selected tree row, or the owner row of the table whose row is selected */
    private FlatRow selectedRowOrTableOwner() {
        if (tables.selectedTable != null) {
            for (FlatRow row : allRows) {
                if (row.isRepeatingTableBuilt() && row.getRepeatingTable() == tables.selectedTable) {
                    return row;
                }
            }
        }
        return selectedRow;
    }

    /** @return the repeating-group key a row belongs to (as owner or as a standard member), or {@code null} */
    private static String groupKeyOf(FlatRow row) {
        if (row == null) {
            return null;
        }
        if (row.getTableGroupKey() != null) {
            return row.getTableGroupKey();
        }
        return row.getStandardGroupKey();
    }

    // ==================== Event Handlers ====================

    private void setupEventHandlers() {
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMouseClick);
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMove);
        canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            boolean needsRedraw = false;
            if (hoveredRow != null) {
                hoveredRow.setHovered(false);
                hoveredRow = null;
                needsRedraw = true;
            }
            if (hoveredExpandBar != null) {
                hoveredExpandBar = null;
                needsRedraw = true;
            }
            if (tables.hoveredTable != null) {
                tables.hoveredTable.setHoveredRowIndex(-1);
                tables.hoveredTable.setHoveredColumnIndex(-1);
                tables.hoveredTable.setHovered(false);
                tables.hoveredTable = null;
                tables.hoveredTableRowIndex = -1;
                tables.hoveredTableColumnIndex = -1;
                needsRedraw = true;
            }
            if (needsRedraw) {
                render();
            }
        });

        // Mouse wheel scrolling (vertical and horizontal)
        canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            // Ctrl + wheel zooms (like the text editor and the XSD diagram) — but never while an
            // inline edit is running: zooming cancels the editor and would drop the typed text.
            if (e.isControlDown() || e.isShortcutDown()) {
                if (editor.editField != null || editor.activeWidgetNode != null) {
                    e.consume();
                    return;
                }
                if (e.getDeltaY() > 0) {
                    zoomIn();
                } else if (e.getDeltaY() < 0) {
                    zoomOut();
                }
                e.consume();
                return;
            }

            boolean changed = false;
            double z = getZoom();
            double viewportW = viewportWidthModel();
            double viewportH = viewportHeightModel();

            // Horizontal scrolling: Shift + scroll or trackpad horizontal gesture
            if (e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())) {
                if (totalWidth > viewportW) {
                    double deltaX = (e.isShiftDown() ? -e.getDeltaY() : -e.getDeltaX()) / z;
                    double newOffsetX = Math.max(0, Math.min(scrollOffsetX + deltaX, totalWidth - viewportW));
                    if (newOffsetX != scrollOffsetX) {
                        scrollOffsetX = newOffsetX;
                        hScrollBar.setValue(scrollOffsetX);
                        changed = true;
                    }
                }
            } else {
                // Vertical scrolling
                if (totalHeight > viewportH) {
                    double deltaY = -e.getDeltaY() / z;
                    double newOffsetY = Math.max(0, Math.min(scrollOffsetY + deltaY, totalHeight - viewportH));
                    if (newOffsetY != scrollOffsetY) {
                        scrollOffsetY = newOffsetY;
                        vScrollBar.setValue(scrollOffsetY);
                        changed = true;
                    }
                }
            }

            if (changed) {
                render();
            }
            e.consume();
        });

        // Keyboard shortcuts: registered on the view (not only the canvas), so
        // navigation also works while the focus sits on the pane or a scrollbar -
        // key events from the focused canvas bubble up here anyway.
        canvas.setFocusTraversable(true);
        addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        // Request focus when clicked (but not if editing)
        canvas.setOnMousePressed(e -> {
            if (!canvas.isFocused() && editor.editField == null && editor.activeWidgetNode == null) {
                canvas.requestFocus();
            }
        });
        // A press on the grid ends here: an enclosing TabPane would otherwise take the focus on
        // every press it receives, and keys ("+", "−", arrows) would no longer reach the grid
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == canvas) {
                e.consume();
            }
        });

        // Column resizing, column-header moves and node-drag candidates
        drags.installMouseHandlers();
    }

    // ==================== Mouse Handling ====================

    /**
     * The row under a point. The row whose own line is at {@code my} is the deepest one; when
     * the point lies left of that row's name cell it is inside the name cell of an ancestor
     * (an expanded container's name cell spans its whole child block).
     *
     * @param mx x in model units
     * @param my y in unscaled viewport units
     * @return the visible-row index, or -1 when there is no row at that y
     */
    private int rowIndexAt(double mx, double my) {
        int idx = findRowIndexAtScreenY(my);
        if (idx < 0 || idx >= visibleRows.size()) {
            return -1;
        }
        FlatRow row = visibleRows.get(idx);
        while (mx < row.getLabelX() && row.getParentRow() != null) {
            Integer parentIdx = rowIndexMap.get(row.getParentRow());
            if (parentIdx == null) {
                break;
            }
            idx = parentIdx;
            row = visibleRows.get(idx);
        }
        return idx;
    }

    /**
     * @return the embedded table under the point, when the row at {@code rowIndex} is an
     *         expanded table owner and the point lies inside its table
     */
    private RepeatingElementsTable tableAt(int rowIndex, double mx, double my) {
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return null;
        }
        FlatRow owner = visibleRows.get(rowIndex);
        if (!owner.hasRepeatingTable() || !owner.isExpanded()) {
            return null;
        }
        RepeatingElementsTable table = owner.getRepeatingTable();
        double tableTop = rowYPositions[rowIndex] - scrollOffsetY;
        // The right edge is inclusive (plus the handle tolerance): the last column's resize
        // separator lies exactly on it.
        if (mx >= table.getX() && mx <= table.getX() + table.getWidth() + GridDragSupport.RESIZE_HANDLE
                && my >= tableTop && my < tableTop + table.getHeight()) {
            return table;
        }
        return null;
    }

    private void handleMouseClick(MouseEvent event) {
        if (drags.suppressNextClick) {
            // The click that JavaFX fires after a column drag must not sort or select.
            drags.suppressNextClick = false;
            return;
        }
        double mx = toModelX(event.getX());
        double my = toViewportY(event.getY());

        if (visibleRows.isEmpty()) {
            return;
        }

        // The sticky scroll header covers the top band of the viewport
        if (event.getButton() == MouseButton.PRIMARY && handleScrollHeaderClick(event.getX() / getZoom(), my)) {
            return;
        }

        // Handle right-click for context menu
        if (event.getButton() == MouseButton.SECONDARY) {
            handleContextMenu(event, mx, my);
            return;
        }

        // Calculate which row was clicked
        int rowIndex = rowIndexAt(mx, my);
        if (rowIndex < 0) {
            selectRow(null);
            return;
        }

        // Check if click is inside an inline table
        RepeatingElementsTable table = tableAt(rowIndex, mx, my);
        if (table != null) {
            tables.handleTableClick(event, table, mx, my, rowYPositions[rowIndex] - scrollOffsetY,
                    visibleRows.get(rowIndex));
            return;
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);

        // A click on a block's name-column separator never selects; a double-click returns the
        // block to automatic width
        GridDragSupport.BlockDrag separator = drags.findBlockSeparatorAt(mx, my);
        if (separator != null) {
            if (event.getClickCount() == 2) {
                drags.resetBlockWidth(separator);
                recalculateVisibleRows();
                updateScrollBars();
                render();
            }
            return;
        }

        // Check if clicking on the expand arrow
        FlatRow expandBarRow = findExpandBarAt(mx, my);
        if (expandBarRow != null && event.isShiftDown() && multiSelection.size() > 1
                && multiSelection.contains(expandBarRow)) {
            // Shift+click on a selected row's arrow: all selected rows follow (XMLSpy)
            setExpandedAll(multiSelection, !expandBarRow.isExpanded());
            return;
        }
        if (expandBarRow != null) {
            FlatRow.toggleExpand(expandBarRow, allRows);
            recalculateVisibleRows();
            updateScrollBars();
            selectRow(expandBarRow);
            render();
            return;
        }

        // Filter line: the funnel switches the filter on/off, a double-click edits the expression
        if (clickedRow.getType() == FlatRow.RowType.FILTER) {
            if (event.getClickCount() == 2) {
                selectRow(clickedRow.getParentRow());
                editFilterOfSelection();
            } else if (mx < clickedRow.getValueX()) {
                toggleFilter(clickedRow);
            }
            return;
        }

        // Double-click handling
        if (event.getClickCount() == 2) {
            editor.editOnDoubleClick(clickedRow, mx >= clickedRow.getValueX());
            return;
        }

        // Single click on the type icon of a row whose type can change: the type menu
        if (event.getClickCount() == 1) {
            double iconX = rowIconX(clickedRow);
            double top = rowYPositions[rowIndex] - scrollOffsetY;
            if (mx >= iconX && mx < iconX + ICON_AREA_WIDTH && my >= top && my < top + ROW_HEIGHT
                    && !clickedRow.isExpandable() && !adapter.typeTargets(clickedRow).isEmpty()) {
                selectRow(clickedRow);
                showTypeMenu(clickedRow, event.getScreenX(), event.getScreenY());
                return;
            }
        }

        // Single click on the table glyph: table display on/off for the repeating group
        if (event.getClickCount() == 1 && clickedRow.showsTableGlyph() && !Double.isNaN(clickedRow.getTableGlyphX())) {
            double gx = clickedRow.getTableGlyphX() - 2;
            double top = rowYPositions[rowIndex] - scrollOffsetY;
            if (mx >= gx && mx < gx + GridPainter.TABLE_ICON_SIZE + 4 && my >= top && my < top + ROW_HEIGHT) {
                toggleTableDisplay(clickedRow);
                return;
            }
        }

        // Single click on an expandable element's icon (first line of the name cell): toggle expand
        if (clickedRow.isExpandable() && event.getClickCount() == 1) {
            double iconX = rowIconX(clickedRow);
            double top = rowYPositions[rowIndex] - scrollOffsetY;
            if (mx >= iconX && mx < iconX + ICON_AREA_WIDTH && my >= top && my < top + ROW_HEIGHT) {
                FlatRow.toggleExpand(clickedRow, allRows);
                recalculateVisibleRows();
                updateScrollBars();
            }
        }

        if (event.getClickCount() == 1 && (event.isShortcutDown() || event.isShiftDown()) && selectedRow != null) {
            extendSelection(clickedRow, event.isShiftDown());
            return;
        }
        selectRow(clickedRow);
    }

    /**
     * Ctrl+click toggles a row in the multi-selection; Shift+click selects every visible sibling
     * between the primary selection and the clicked row (both must share the parent).
     */
    private void extendSelection(FlatRow clicked, boolean range) {
        if (range) {
            FlatRow anchor = selectedRow;
            if (anchor.getParentRow() != clicked.getParentRow()) {
                selectRow(clicked);
                return;
            }
            int a = visibleRows.indexOf(anchor);
            int b = visibleRows.indexOf(clicked);
            for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                FlatRow row = visibleRows.get(i);
                if (row.getParentRow() == anchor.getParentRow() && !multiSelection.contains(row)) {
                    multiSelection.add(row);
                    row.setSelected(true);
                }
            }
        } else if (multiSelection.contains(clicked) && clicked != selectedRow) {
            multiSelection.remove(clicked);
            clicked.setSelected(false);
        } else if (!multiSelection.contains(clicked)) {
            multiSelection.add(clicked);
            clicked.setSelected(true);
        }
        render();
    }

    /** Expands or collapses every expandable row of the list (keeping the others' states). */
    private void setExpandedAll(List<FlatRow> rows, boolean expanded) {
        for (FlatRow row : List.copyOf(rows)) {
            if (row.isExpandable() && row.isExpanded() != expanded) {
                FlatRow.toggleExpand(row, allRows);
            }
        }
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /** @return the selected rows, primary selection included (test hook) */
    List<FlatRow> multiSelectionForTest() {
        return List.copyOf(multiSelection);
    }

    private void handleContextMenu(MouseEvent event, double mx, double my) {
        int rowIndex = rowIndexAt(mx, my);
        if (rowIndex < 0) {
            return;
        }

        // Inside an embedded table: select the clicked row and offer the table-aware menu
        RepeatingElementsTable table = tableAt(rowIndex, mx, my);
        if (table != null) {
            Pointer hit = pointerAt(mx, my);
            if (hit != null && hit.table() == null) {
                // A row inside an opened cell (at any depth): its own node menu
                selectRow(hit.row());
                contextMenu().show(canvas, event.getScreenX(), event.getScreenY(), adapter.nodeOf(hit.row()));
                return;
            }
            if (hit != null && hit.cell() != null) {
                RepeatingElementsTable hitTable = hit.table();
                int tableRow = hit.cell().rowIndex();
                String column = hit.cell().columnIndex() >= 0 ? hitTable.getColumn(hit.cell().columnIndex()).getName()
                        : null;
                showTableContextMenu(event.getScreenX(), event.getScreenY(), hit.row(), hitTable, tableRow, column);
                return;
            }
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);
        selectRow(clickedRow);

        // Update selection model
        N node = adapter.nodeOf(clickedRow);
        adapter.select(node);

        // Show context menu
        contextMenu().show(canvas, event.getScreenX(), event.getScreenY(), node);
    }

    /**
     * Shows the table-aware context menu for a cell of an embedded table: selects the clicked
     * table row (a cell that is an element of its own becomes the node target) and remembers the
     * table, so table-wide actions (filter, formula) can address the element holding it.
     *
     * @param ownerRow the grid row the table belongs to
     * @param tableRow the clicked table row ({@code -1} for the header)
     * @param column   the clicked column, {@code null} for the row-number column
     */
    void showTableContextMenu(double screenX, double screenY, FlatRow ownerRow, RepeatingElementsTable table,
                              int tableRow, String column) {
        contextTable = table;
        contextTableRow = tableRow;
        contextTableColumn = column;
        if (tableRow >= 0) {
            tables.selectTableRow(table, tableRow);
            render();
        }
        N node = tableRow >= 0 ? adapter.nodeOf(table.getRows().get(tableRow)) : adapter.nodeOf(ownerRow);
        // A cell that is an element of its own (e.g. Identifiers of an Asset row) is the target
        // of the node actions; row actions stay in the menu's Table submenu
        N cell = tableRow >= 0 && column != null ? adapter.cellNode(table.getRows().get(tableRow), column) : null;
        if (cell != null) {
            node = cell;
            adapter.select(cell);
        }
        contextMenu().show(canvas, screenX, screenY, node, table, tableRow, column);
    }

    GridContextMenu<N> contextMenu() {
        if (contextMenu == null) {
            contextMenu = adapter.createContextMenu(this::refresh, new GridContextMenu.ViewActions(
                    this::expandAll, this::collapseAll, this::expandSelectedFully, this::collapseUnselected,
                    this::toggleTableDisplayOfSelection, this::tableDisplayStateOfSelection,
                    this::flipTableOfSelection, this::flipStateOfSelection,
                    this::editFilterOfSelection, this::filterToFocus, this::canFilterSelection,
                    this::removeFilterOfSelection, this::hasFilterOnSelection,
                    this::removeAllFilters, this::hasAnyFilter,
                    this::toggleFilterActiveOfSelection, this::filterActiveStateOfSelection,
                    this::toggleFilterSavedOfSelection, this::filterSavedStateOfSelection,
                    this::expandSelectionOneLevel, this::collapseSelectionOneLevel, this::selectModelNode));
        }
        return contextMenu;
    }

    private void handleMouseMove(MouseEvent event) {
        double mx = toModelX(event.getX());
        double my = toViewportY(event.getY());

        if (visibleRows.isEmpty()) {
            return;
        }

        boolean needsRedraw = false;

        // The deepest row or table under the pointer (inside opened cells too)
        Pointer pointer = pointerAt(mx, my);
        RepeatingElementsTable table = pointer == null ? null : pointer.table();
        if (table != null) {
            tables.updateTableHover(table, pointer.lx(), pointer.ly(), pointer.tableTop());
        } else if (tables.hoveredTable != null) {
            tables.hoveredTable.setHoveredRowIndex(-1);
            tables.hoveredTable.setHoveredColumnIndex(-1);
            tables.hoveredTable.setHovered(false);
            tables.hoveredTable = null;
            tables.hoveredTableRowIndex = -1;
            tables.hoveredTableColumnIndex = -1;
            needsRedraw = true;
        }

        FlatRow newHoveredRow = pointer != null && table == null ? pointer.row() : null;
        if (newHoveredRow != hoveredRow) {
            if (hoveredRow != null) {
                hoveredRow.setHovered(false);
            }
            if (newHoveredRow != null) {
                newHoveredRow.setHovered(true);
            }
            hoveredRow = newHoveredRow;
            needsRedraw = true;
        }

        FlatRow newHoveredExpandBar = expandBarAt(pointer);
        if (newHoveredExpandBar != hoveredExpandBar) {
            hoveredExpandBar = newHoveredExpandBar;
            needsRedraw = true;
        }

        // Preview tooltip over the value cell of a collapsed container (root rows)
        updatePreviewTooltip(pointer != null && pointer.block() == null && table == null ? rowIndexAt(mx, my) : -1,
                mx, my);

        if (drags.columnDrag != null || drags.findColumnHandleAt(mx, my) != null || drags.findBlockSeparatorAt(mx, my) != null) {
            canvas.setCursor(Cursor.H_RESIZE);
        } else if (hoveredExpandBar != null || pointsAtToggle(pointer)) {
            canvas.setCursor(Cursor.HAND);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }

        if (needsRedraw) {
            render();
        }
    }

    /**
     * The deepest thing under a point, with the coordinate system it lives in: a row of the root
     * grid or of an opened cell's grid, and — when the point is inside that row's embedded table —
     * the table and its drawn cell.
     *
     * @param block    the opened cell's grid holding the row ({@code null} = the root grid)
     * @param ox       x offset of that grid's coordinates (content x; 0 for the root)
     * @param oy       y offset of that grid's coordinates (viewport y; 0 for the root)
     * @param row      the row under the point (for a table: its owner)
     * @param rowTop   the row's top in the grid's coordinates
     * @param table    the embedded table under the point, or {@code null}
     * @param tableTop the table's top in the grid's coordinates
     * @param cell     the table's drawn cell under the point, or {@code null}
     * @param lx       the point in the grid's coordinates
     * @param ly       the point in the grid's coordinates
     */
    record Pointer(GridBlock block, double ox, double oy, FlatRow row, double rowTop,
                   RepeatingElementsTable table, double tableTop, RepeatingElementsTable.DrawnCell cell,
                   double lx, double ly) {
    }

    /** @return what lies under the canvas point (content x, viewport y), at any depth, or {@code null} */
    Pointer pointerAt(double mx, double my) {
        int idx = rowIndexAt(mx, my);
        if (idx < 0) {
            return null;
        }
        double top = rowYPositions[idx] - scrollOffsetY;
        return descend(null, 0, 0, visibleRows.get(idx), top, tableAt(idx, mx, my), top, mx, my);
    }

    /** Goes from a row (and its table, if hit) into an opened cell under the point, recursively. */
    private Pointer descend(GridBlock block, double ox, double oy, FlatRow row, double rowTop,
                            RepeatingElementsTable table, double tableTop, double lx, double ly) {
        if (table == null) {
            return new Pointer(block, ox, oy, row, rowTop, null, 0, null, lx, ly);
        }
        int dr = table.drawnRowAt(ly - tableTop);
        int dc = table.drawnColumnAt(lx);
        RepeatingElementsTable.DrawnCell cell = dr >= 0 && dc >= 0 ? table.cellAt(dr, dc) : null;
        if (cell != null && cell.kind() == RepeatingElementsTable.CellKind.DATA
                && cell.rowIndex() >= 0 && cell.columnIndex() >= 0) {
            RepeatingElementsTable.TableRow tableRow = table.getRows().get(cell.rowIndex());
            String column = table.getColumn(cell.columnIndex()).getName();
            RepeatingElementsTable.CellLayout layout = table.getCellLayout(tableRow, column);
            if (layout != null && layout.grid() != null) {
                RepeatingElementsTable.CellRect rect = table.cellRect(cell.rowIndex(), cell.columnIndex());
                double gx = rect.x() + RepeatingElementsTable.CELL_PADDING;
                double gy = tableTop + rect.y() + layout.summaryHeight();
                GridBlock grid = layout.grid();
                GridBlockLayout.Result g = grid.geometry();
                int i = ly >= gy && g != null ? grid.rowIndexAt(lx - gx, ly - gy) : -1;
                if (i >= 0) {
                    double x = lx - gx;
                    double y = ly - gy;
                    FlatRow inner = g.visible().get(i);
                    double innerTop = g.y()[i];
                    RepeatingElementsTable nested = null;
                    if (inner.hasRepeatingTable() && inner.isExpanded()) {
                        RepeatingElementsTable t = inner.getRepeatingTable();
                        if (x >= t.getX() && x <= t.getX() + t.getWidth() + GridDragSupport.RESIZE_HANDLE
                                && y >= innerTop && y < innerTop + t.getHeight()) {
                            nested = t;
                        }
                    }
                    return descend(grid, ox + gx, oy + gy, inner, innerTop, nested, innerTop, x, y);
                }
            }
        }
        return new Pointer(block, ox, oy, row, rowTop, table, tableTop, cell, lx, ly);
    }

    /** @return the row whose expand arrow is under the pointer (first line of its name cell), or null */
    private static FlatRow expandBarAt(Pointer p) {
        if (p == null || p.table() != null || !p.row().isExpandable()) {
            return null;
        }
        double left = p.row().getLabelX();
        boolean onArrow = p.lx() >= left && p.lx() < left + EXPAND_BAR_WIDTH + 2;
        boolean onLine = p.ly() >= p.rowTop() && p.ly() < p.rowTop() + ROW_HEIGHT;
        return onArrow && onLine ? p.row() : null;
    }

    /** @return whether the pointer is on a table glyph or on the arrow of a complex table cell */
    private static boolean pointsAtToggle(Pointer p) {
        if (p == null) {
            return false;
        }
        if (p.table() == null) {
            double glyphX = p.row().getTableGlyphX();
            return p.row().showsTableGlyph() && !Double.isNaN(glyphX)
                    && p.lx() >= glyphX - 2 && p.lx() <= glyphX + GridPainter.TABLE_ICON_SIZE + 2
                    && p.ly() >= p.rowTop() && p.ly() < p.rowTop() + ROW_HEIGHT;
        }
        RepeatingElementsTable.DrawnCell cell = p.cell();
        if (cell == null || cell.kind() != RepeatingElementsTable.CellKind.DATA
                || cell.rowIndex() < 0 || cell.columnIndex() < 0) {
            return false;
        }
        String column = p.table().getColumn(cell.columnIndex()).getName();
        if (!p.table().getRows().get(cell.rowIndex()).hasComplexChild(column)) {
            return false;
        }
        RepeatingElementsTable.CellRect rect = p.table().cellRect(cell.rowIndex(), cell.columnIndex());
        return p.lx() >= rect.x() && p.lx() <= rect.x() + 16
                && p.ly() >= p.tableTop() + rect.y() && p.ly() < p.tableTop() + rect.y() + ROW_HEIGHT;
    }

    /**
     * Shows the full (longer) preview of a collapsed container as a tooltip while the pointer
     * is over its value cell; removes the tooltip elsewhere.
     */
    private void updatePreviewTooltip(int rowIndex, double mx, double my) {
        FlatRow target = null;
        String text = null;
        if (rowIndex >= 0) {
            FlatRow row = visibleRows.get(rowIndex);
            double top = rowYPositions[rowIndex] - scrollOffsetY;
            List<String> problems = problemsOf(row);
            if (problems != null && my >= top && my < top + ROW_HEIGHT) {
                target = row;
                text = String.join("\n", problems);
            } else if (showsPreview(row) && mx >= row.getValueX() && my >= top && my < top + ROW_HEIGHT) {
                target = row;
            }
        }
        if (target == previewTooltipRow) {
            return;
        }
        previewTooltipRow = target;
        if (target == null) {
            Tooltip.uninstall(canvas, previewTooltip);
            return;
        }
        String full = text != null ? text : adapter.collapsedPreview(target, TOOLTIP_MAX_CHARS);
        previewTooltip.setText(full != null ? full : "");
        previewTooltip.setWrapText(true);
        previewTooltip.setMaxWidth(600);
        Tooltip.install(canvas, previewTooltip);
    }

    /**
     * Finds which expand arrow (if any) is at the given canvas coordinates: the arrow area
     * left of the icon, on the first line of an expandable row's name cell.
     */
    private FlatRow findExpandBarAt(double mx, double my) {
        if (visibleRows.isEmpty() || rowYPositions.length == 0) {
            return null;
        }
        int idx = rowIndexAt(mx, my);
        if (idx < 0) {
            return null;
        }
        FlatRow row = visibleRows.get(idx);
        if (!row.isExpandable()) {
            return null;
        }
        double arrowLeft = row.getLabelX();
        double arrowRight = arrowLeft + EXPAND_BAR_WIDTH + 2;
        double top = rowYPositions[idx] - scrollOffsetY;
        if (mx >= arrowLeft && mx < arrowRight && my >= top && my < top + ROW_HEIGHT) {
            return row;
        }
        return null;
    }

    // ==================== Keyboard Handling ====================

    private void handleKeyPress(KeyEvent event) {
        // If editing, don't handle navigation keys
        if (editor.editField != null || editor.activeWidgetNode != null) {
            return;
        }

        // View shortcuts: Ctrl+Shift+Plus expands the selection fully, Ctrl+Shift+Minus
        // collapses everything else (before the zoom keys, which share the key codes)
        if (event.isShortcutDown() && event.isShiftDown()) {
            switch (event.getCode()) {
                case PLUS, ADD, EQUALS -> {
                    expandSelectedFully();
                    event.consume();
                    return;
                }
                case MINUS, SUBTRACT -> {
                    collapseUnselected();
                    event.consume();
                    return;
                }
                default -> { /* not a view key */ }
            }
        }

        // Zoom shortcuts: Ctrl + / Ctrl - / Ctrl 0 (also the numpad keys)
        if (event.isShortcutDown()) {
            switch (event.getCode()) {
                case PLUS, ADD, EQUALS -> {
                    zoomIn();
                    event.consume();
                    return;
                }
                case MINUS, SUBTRACT -> {
                    zoomOut();
                    event.consume();
                    return;
                }
                case DIGIT0, NUMPAD0 -> {
                    zoomReset();
                    event.consume();
                    return;
                }
                default -> { /* not a zoom key */ }
            }
        }

        // Table cell clipboard (TSV) before the node shortcuts of the context menu
        if (tables.handleTableClipboardKeys(event)) {
            return;
        }

        // + / − (also the numpad keys): open / close one more level of the selection
        if (!event.isShortcutDown() && !event.isAltDown() && !event.isMetaDown() && !event.isControlDown()) {
            KeyCode code = event.getCode();
            String text = event.getText();
            if (code == KeyCode.ADD || code == KeyCode.PLUS || "+".equals(text)) {
                expandSelectionOneLevel();
                event.consume();
                return;
            }
            if (code == KeyCode.SUBTRACT || code == KeyCode.MINUS || "-".equals(text)) {
                collapseSelectionOneLevel();
                event.consume();
                return;
            }
        }

        // Ctrl+Alt+T: the type menu of the selected row (XMLSpy: Type, Ctrl+Menu)
        if (event.isShortcutDown() && event.isAltDown() && event.getCode() == KeyCode.T && selectedRow != null) {
            int idx = visibleRows.indexOf(selectedRow);
            if (idx >= 0 && !adapter.typeTargets(selectedRow).isEmpty()) {
                javafx.geometry.Point2D p = canvas.localToScreen(
                        (rowIconX(selectedRow) - scrollOffsetX) * getZoom(),
                        (rowYPositions[idx] - scrollOffsetY + ROW_HEIGHT) * getZoom());
                if (p != null) {
                    showTypeMenu(selectedRow, p.getX(), p.getY());
                }
            }
            event.consume();
            return;
        }

        N selected = getSelectedNode();

        // Context menu shortcuts (Delete, F2, Ctrl+C/X/V/D)
        if (selected != null) {
            contextMenu().handleKeyPress(event, selected);
        }

        // Keyboard navigation
        handleKeyNavigation(event);
    }

    /**
     * Handles keyboard navigation between rows.
     */
    private void handleKeyNavigation(KeyEvent event) {
        if (visibleRows.isEmpty()) {
            return;
        }

        switch (event.getCode()) {
            case UP -> {
                selectPreviousRow();
                event.consume();
            }
            case DOWN -> {
                selectNextRow();
                event.consume();
            }
            case LEFT -> {
                if (selectedRow != null && selectedRow.isExpandable() && selectedRow.isExpanded()) {
                    // Collapse if expanded
                    toggleRow(selectedRow);
                } else if (selectedRow != null && selectedRow.getParentRow() != null
                        && selectedRow.getParentRow().getDepth() >= 0) {
                    // Jump to parent
                    selectRow(selectedRow.getParentRow());
                    ensureRowVisible(selectedRow);
                } else if (selectedRow != null && selectedRow.getBlock() != null) {
                    // Top of an opened cell's grid: back to the cell
                    selectHostCell(selectedRow.getBlock());
                }
                event.consume();
            }
            case RIGHT -> {
                if (selectedRow != null && selectedRow.isExpandable() && !selectedRow.isExpanded()) {
                    // Expand if collapsed
                    toggleRow(selectedRow);
                }
                event.consume();
            }
            case ENTER -> {
                if (selectedRow != null) {
                    if (selectedRow.isExpandable()) {
                        toggleRow(selectedRow);
                    } else {
                        // Start editing
                        editor.startEditingElementName(selectedRow);
                    }
                }
                event.consume();
            }
            case SPACE -> {
                // Spacebar toggles the selected container (XMLSpy "Expand/Collapse on spacebar")
                if (selectedRow != null && selectedRow.isExpandable()) {
                    toggleRow(selectedRow);
                }
                event.consume();
            }
            case HOME -> {
                if (!visibleRows.isEmpty()) {
                    selectRow(visibleRows.get(0));
                    ensureRowVisible(selectedRow);
                }
                event.consume();
            }
            case END -> {
                if (!visibleRows.isEmpty()) {
                    selectRow(visibleRows.get(visibleRows.size() - 1));
                    ensureRowVisible(selectedRow);
                }
                event.consume();
            }
            default -> { /* Not a navigation key */ }
        }
    }

    private void selectPreviousRow() {
        if (selectedRow == null) {
            if (!visibleRows.isEmpty()) {
                selectRow(visibleRows.get(0));
                ensureRowVisible(selectedRow);
            }
            return;
        }

        List<FlatRow> rows = visibleOf(selectedRow);
        int idx = rows.indexOf(selectedRow);
        if (idx > 0) {
            selectRow(rows.get(idx - 1));
            ensureRowVisible(selectedRow);
        }
    }

    private void selectNextRow() {
        if (selectedRow == null) {
            if (!visibleRows.isEmpty()) {
                selectRow(visibleRows.get(0));
                ensureRowVisible(selectedRow);
            }
            return;
        }

        List<FlatRow> rows = visibleOf(selectedRow);
        int idx = rows.indexOf(selectedRow);
        if (idx >= 0 && idx < rows.size() - 1) {
            selectRow(rows.get(idx + 1));
            ensureRowVisible(selectedRow);
        }
    }

    /**
     * Visits every row that exists now: the root's, then — for every built table — the rows of its
     * opened cells, recursively (a row's own table is visited before the rows inside it).
     */
    private void forEachLiveRow(java.util.function.Consumer<FlatRow> action) {
        java.util.ArrayDeque<FlatRow> pending = new java.util.ArrayDeque<>(allRows);
        while (!pending.isEmpty()) {
            FlatRow row = pending.poll();
            action.accept(row);
            if (row.isRepeatingTableBuilt()) {
                for (RepeatingElementsTable.TableRow tableRow : row.getRepeatingTable().getRows()) {
                    for (GridBlock grid : tableRow.getCellGrids().values()) {
                        pending.addAll(grid.rows());
                    }
                }
            }
        }
    }

    /** @return every row of the grid the row belongs to (the root's or an opened cell's) */
    private List<FlatRow> rowsOf(FlatRow row) {
        return row.getBlock() == null ? allRows : row.getBlock().rows();
    }

    /** @return the visible rows of the grid the row belongs to, in draw order */
    private List<FlatRow> visibleOf(FlatRow row) {
        GridBlock block = row.getBlock();
        return block == null ? visibleRows : block.geometry(cellMeasure).visible();
    }

    /** Expands or collapses a row of any grid and re-lays out. */
    private void toggleRow(FlatRow row) {
        FlatRow.toggleExpand(row, rowsOf(row));
        if (row.getBlock() != null) {
            row.getBlock().invalidate();
        }
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /** Selects the table cell an opened grid belongs to. */
    private void selectHostCell(GridBlock block) {
        RepeatingElementsTable.TableRow host = block.host();
        RepeatingElementsTable table = host == null ? null : host.getOwner();
        if (table == null) {
            return;
        }
        int r = table.getRows().indexOf(host);
        RepeatingElementsTable.TableColumn col = table.getColumn(block.column());
        if (r < 0 || col == null) {
            return;
        }
        int c = table.getColumns().indexOf(col);
        if (selectedRow != null) {
            selectedRow.setSelected(false);
            selectedRow = null;
        }
        tables.selectTableRow(table, r);
        tables.setCellRange(r, c, r, c);
        render();
    }

    // ==================== Selection ====================

    /**
     * Clears the table selection state.
     * Deselects the currently selected table row and resets the selectedTable reference.
     */
    private void clearTableSelection() {
        if (tables.selectedTable != null) {
            tables.selectedTable.setSelectedRowIndex(-1);
            tables.selectedTable = null;
        }
        tables.selAnchorRow = -1;
        tables.selAnchorCol = -1;
        tables.selFocusRow = -1;
        tables.selFocusCol = -1;
    }

    private void selectRow(FlatRow row) {
        // Clear any table selection to ensure mutual exclusivity
        clearTableSelection();

        if (selectedRow != null) {
            selectedRow.setSelected(false);
        }
        for (FlatRow other : multiSelection) {
            other.setSelected(false);
        }
        multiSelection.clear();
        if (row != null) {
            multiSelection.add(row);
        }

        selectedRow = row;

        if (selectedRow != null) {
            selectedRow.setSelected(true);
            // Update selection model
            adapter.select(adapter.nodeOf(row));
        }
        fireSelectionChanged();

        render();
    }

    private final List<Runnable> selectionListeners = new ArrayList<>();

    /**
     * Registers a listener run whenever the selected row or table row changes, also when the
     * model node stays the same (an attribute row selects its element) and after a rebuild.
     */
    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    void fireSelectionChanged() {
        for (Runnable listener : List.copyOf(selectionListeners)) {
            listener.run();
        }
    }

    /** @return the selected row ({@code null} while nothing or a table row is selected) */
    public FlatRow getSelectedRow() {
        return selectedRow;
    }

    // ==================== Scroll-to-Selection ====================

    /**
     * Ensures the given row is visible in the viewport: its first line at least, and as much
     * of its name cell (an expanded container's spans the child block) as fits.
     */
    private void ensureRowVisible(FlatRow row) {
        if (row == null) {
            return;
        }

        double viewportH = viewportHeightModel();
        double rowTop;
        double rowBottom;
        if (row.getBlock() != null) {
            // A row of an opened cell: where it is on the canvas, one line tall
            double[] origin = rowOrigin(row);
            if (origin == null) {
                return;
            }
            rowTop = origin[1] + scrollOffsetY;
            rowBottom = rowTop + ROW_HEIGHT;
        } else {
            int idx = visibleRows.indexOf(row);
            if (idx < 0 || idx >= rowYPositions.length) {
                return;
            }
            rowTop = rowYPositions[idx];
            rowBottom = rowTop + Math.min(Math.max(rowHeights[idx], ROW_HEIGHT), viewportH - 40);
        }
        double viewTop = scrollOffsetY;
        double viewBottom = viewTop + viewportH;

        if (rowTop < viewTop) {
            animateScrollTo(rowTop - 20);
        } else if (rowBottom > viewBottom) {
            animateScrollTo(rowBottom - viewportH + 20);
        }
    }

    /**
     * Smoothly animate scroll to target Y position.
     */
    private void animateScrollTo(double targetY) {
        targetY = Math.max(0, Math.min(targetY, totalHeight - viewportHeightModel()));

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(vScrollBar.valueProperty(), scrollOffsetY)),
            new KeyFrame(Duration.millis(200), new KeyValue(vScrollBar.valueProperty(), targetY))
        );
        timeline.play();
    }

    // ---- Where rows, tables and opened cells are on the canvas (content x, viewport y) ----

    /** @return the top-left of an opened cell's grid, or {@code null} when it is not laid out */
    double[] blockOrigin(GridBlock grid) {
        RepeatingElementsTable.TableRow host = grid.host();
        RepeatingElementsTable table = host == null ? null : host.getOwner();
        double[] system = table == null ? null : tableOrigin(table);
        if (system == null) {
            return null;
        }
        int r = table.getRows().indexOf(host);
        RepeatingElementsTable.TableColumn col = table.getColumn(grid.column());
        int c = col == null ? -1 : table.getColumns().indexOf(col);
        if (r < 0 || c < 0) {
            return null;
        }
        RepeatingElementsTable.CellRect rect = table.cellRect(r, c);
        RepeatingElementsTable.CellLayout layout = table.getCellLayout(host, grid.column());
        return new double[] {system[0] + rect.x() + RepeatingElementsTable.CELL_PADDING,
                system[1] + rect.y() + layout.summaryHeight()};
    }

    /**
     * @return the x offset of the coordinate system the table lives in (0 for the root) and the
     *         table's top, or {@code null} when it is not laid out
     */
    double[] tableOrigin(RepeatingElementsTable table) {
        FlatRow owner = table.getOwnerRow();
        return owner == null ? null : rowOrigin(owner);
    }

    /**
     * @return the x offset of the row's grid (0 for the root) and the row's top, or {@code null}
     *         when the row is not laid out
     */
    double[] rowOrigin(FlatRow row) {
        GridBlock block = row.getBlock();
        if (block == null) {
            Integer i = rowIndexMap.get(row);
            return i == null ? null : new double[] {0, rowYPositions[i] - scrollOffsetY};
        }
        GridBlockLayout.Result g = block.geometry();
        Integer i = g == null ? null : g.index().get(row);
        double[] origin = i == null ? null : blockOrigin(block);
        return origin == null ? null : new double[] {origin[0], origin[1] + g.y()[i]};
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged() {
        editor.cancelEditing();
        rebuildTree();
    }

    // ==================== Highlight ====================

    /**
     * Flash highlight a row (used after undo/redo to show what changed).
     */
    public void flashHighlight(FlatRow row) {
        if (row == null) {
            return;
        }

        highlightedRow = row;
        render();

        PauseTransition pause = new PauseTransition(Duration.millis(1500));
        pause.setOnFinished(e -> {
            highlightedRow = null;
            render();
        });
        pause.play();
    }

    /**
     * Flash highlight a row by its model node.
     */
    public void flashHighlightByModel(N modelNode) {
        if (modelNode != null) {
            for (FlatRow row : allRows) {
                if (row.getModelNode() == modelNode) {
                    ensureRowVisible(row);
                    flashHighlight(row);
                    return;
                }
            }
        }
    }

    // ==================== Toast Notifications ====================

    /**
     * Show a toast notification.
     */
    public void showToast(String message, ToastNotification.Type type) {
        if (toastContainer != null) {
            ToastNotification.show(toastContainer, message, type);
        } else {
            ToastNotification.show(canvasContainer, message, type);
        }
    }

    /**
     * Set the container for toast notifications.
     */
    public void setToastContainer(StackPane container) {
        this.toastContainer = container;
    }

    // ==================== Status Bar ====================

    /**
     * Sets a callback that is called when the document is modified.
     */
    public void setOnDocumentModified(java.util.function.Consumer<String> callback) {
        this.onDocumentModified = callback;
    }

    /** @return the callback called when the document is modified, or {@code null} */
    public java.util.function.Consumer<String> getOnDocumentModified() {
        return onDocumentModified;
    }

    /**
     * Notifies the callback that the document was modified.
     */
    void notifyDocumentModified() {
        problemsByLine = null; // line numbers refer to the text before this edit
        if (onDocumentModified != null) {
            String serialized = adapter.serialize();
            onDocumentModified.accept(serialized);
        }
    }

    // ==================== Public API ====================

    /** Rebuilds the rows from the model and pushes the serialized document to the round-trip callback. */
    public void refresh() {
        rebuildTree();
        notifyDocumentModified();
    }

    /** Rebuilds the rows from the model without notifying the round-trip callback (e.g. after shell undo). */
    public void rebuild() {
        rebuildTree();
    }

    /** @return the model adapter backing this canvas */
    public GridModelAdapter<N> getAdapter() {
        return adapter;
    }

    /** @return the number of rows currently visible (expanded ancestors), not counting table rows */
    public int visibleRowCount() {
        return visibleRows.size();
    }

    public void expandAll() {
        for (FlatRow row : allRows) {
            if (row.isExpandable()) {
                row.setExpanded(true);
                // Show all descendant rows
            }
        }
        // Make all rows visible
        for (FlatRow row : allRows) {
            row.setVisible(true);
        }
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    public void collapseAll() {
        for (FlatRow row : allRows) {
            if (row.isExpandable()) {
                row.setExpanded(false);
            }
        }
        // Recalculate visibility (only root-level rows remain visible)
        recalculateVisibility();
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /**
     * Expands the given container and every container below it (XMLSpy "Expand Fully").
     *
     * @param row the container row; ignored when {@code null} or not expandable
     */
    public void expandFully(FlatRow row) {
        if (row == null || !row.isExpandable()) {
            return;
        }
        row.setExpanded(true);
        for (FlatRow candidate : allRows) {
            if (candidate.isExpandable() && FlatRow.isDescendantOf(candidate, row)) {
                candidate.setExpanded(true);
            }
        }
        recalculateVisibility();
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    // ==================== One level at a time (+ / −) ====================

    /**
     * Opens one more level of the selection (key "+"): in a table, every complex cell of the
     * selected range (a closed cell opens, an open one opens its shallowest closed sub-rows);
     * otherwise every selected row (a collapsed row expands, an expanded one expands its
     * shallowest collapsed descendants).
     */
    public void expandSelectionOneLevel() {
        stepSelection(true);
    }

    /**
     * Closes one level of the selection (key "−"): the deepest open level inside each selected
     * cell or row closes first, then the cell or row itself.
     */
    public void collapseSelectionOneLevel() {
        stepSelection(false);
    }

    private void stepSelection(boolean open) {
        boolean changed = false;
        if (tables.selectedTable != null && tables.selAnchorRow >= 0) {
            RepeatingElementsTable table = tables.selectedTable;
            for (int r = Math.min(tables.selAnchorRow, tables.selFocusRow); r <= Math.max(tables.selAnchorRow, tables.selFocusRow); r++) {
                if (r >= table.getRows().size()) {
                    break;
                }
                RepeatingElementsTable.TableRow row = table.getRows().get(r);
                for (int c = Math.min(tables.selAnchorCol, tables.selFocusCol); c <= Math.max(tables.selAnchorCol, tables.selFocusCol); c++) {
                    RepeatingElementsTable.TableColumn col = table.getColumn(c);
                    if (col != null && row.hasComplexChild(col.getName())) {
                        changed |= open ? openCellLevel(row, col.getName()) : closeCellLevel(row, col.getName());
                    }
                }
            }
            if (changed) {
                table.recalculateColumnWidths();
            }
        } else {
            for (FlatRow row : List.copyOf(multiSelection)) {
                changed |= open ? openRowLevel(row) : closeRowLevel(row);
            }
            if (changed) {
                recalculateVisibility();
            }
        }
        if (changed) {
            recalculateVisibleRows();
            updateScrollBars();
            render();
        }
    }

    private static boolean openCellLevel(RepeatingElementsTable.TableRow row, String column) {
        if (!row.isColumnExpanded(column)) {
            row.toggleColumnExpanded(column);
            return true;
        }
        List<FlatRow> cellRows = row.getExpandedCellRows(column);
        FlatRow.applyVisibility(cellRows);
        boolean changed = openShallowest(cellRows);
        FlatRow.applyVisibility(cellRows);
        if (changed) {
            row.getCellGrid(column).invalidate();
        }
        return changed;
    }

    private static boolean closeCellLevel(RepeatingElementsTable.TableRow row, String column) {
        if (!row.isColumnExpanded(column)) {
            return false;
        }
        List<FlatRow> cellRows = row.getExpandedCellRows(column);
        FlatRow.applyVisibility(cellRows);
        if (closeDeepest(cellRows)) {
            FlatRow.applyVisibility(cellRows);
            row.getCellGrid(column).invalidate();
            return true;
        }
        row.toggleColumnExpanded(column);
        return true;
    }

    private boolean openRowLevel(FlatRow row) {
        if (!row.isExpandable()) {
            return false;
        }
        if (!row.isExpanded()) {
            row.setExpanded(true);
            return true;
        }
        return openShallowest(descendantsOf(row));
    }

    private boolean closeRowLevel(FlatRow row) {
        if (!row.isExpandable() || !row.isExpanded()) {
            return false;
        }
        if (closeDeepest(descendantsOf(row))) {
            return true;
        }
        row.setExpanded(false);
        return true;
    }

    private List<FlatRow> descendantsOf(FlatRow row) {
        List<FlatRow> result = new ArrayList<>();
        for (FlatRow other : allRows) {
            if (other != row && FlatRow.isDescendantOf(other, row)) {
                result.add(other);
            }
        }
        return result;
    }

    /** Expands the visible collapsed containers of the shallowest depth; @return whether any */
    private static boolean openShallowest(List<FlatRow> rows) {
        int depth = Integer.MAX_VALUE;
        for (FlatRow r : rows) {
            if (r.isExpandable() && !r.isExpanded() && r.isVisible()) {
                depth = Math.min(depth, r.getDepth());
            }
        }
        boolean changed = false;
        for (FlatRow r : rows) {
            if (r.isExpandable() && !r.isExpanded() && r.isVisible() && r.getDepth() == depth) {
                r.setExpanded(true);
                changed = true;
            }
        }
        return changed;
    }

    /** Collapses the visible expanded containers of the deepest depth; @return whether any */
    private static boolean closeDeepest(List<FlatRow> rows) {
        int depth = Integer.MIN_VALUE;
        for (FlatRow r : rows) {
            if (r.isExpandable() && r.isExpanded() && r.isVisible()) {
                depth = Math.max(depth, r.getDepth());
            }
        }
        boolean changed = false;
        for (FlatRow r : rows) {
            if (r.isExpandable() && r.isExpanded() && r.isVisible() && r.getDepth() == depth) {
                r.setExpanded(false);
                changed = true;
            }
        }
        return changed;
    }

    // ==================== Cell expansion across rebuilds ====================

    /**
     * Remembers, for every row that exists now — the root's and those inside opened cells, at any
     * depth — its expand state, and for every built table its opened cells ("row:column").
     */
    private void saveCellExpansion() {
        for (FlatRow row : allRows) {
            saveLiveState(row);
        }
    }

    private void saveLiveState(FlatRow row) {
        if (row.isExpandable()) {
            viewState.setExpanded(keyOf(row), row.isExpanded());
        }
        if (row.getTableGroupKey() == null || !row.isRepeatingTableBuilt()) {
            return;
        }
        RepeatingElementsTable table = row.getRepeatingTable();
        java.util.Set<String> entries = new java.util.LinkedHashSet<>();
        List<RepeatingElementsTable.TableRow> rows = table.getRows();
        for (int r = 0; r < rows.size(); r++) {
            for (var open : rows.get(r).getCellGrids().entrySet()) {
                entries.add(r + ":" + open.getKey());
                for (FlatRow inner : open.getValue().rows()) {
                    saveLiveState(inner);
                }
            }
        }
        viewState.setCellExpansion(row.getTableGroupKey(), entries);
    }

    /** Re-opens the remembered cells of a rebuilt table; stale and old sub-row entries are ignored. */
    private static void restoreCellExpansion(RepeatingElementsTable table, java.util.Set<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        boolean changed = false;
        List<RepeatingElementsTable.TableRow> rows = table.getRows();
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            int r;
            try {
                r = Integer.parseInt(entry.substring(0, colon));
            } catch (NumberFormatException e) {
                continue;
            }
            String column = entry.substring(colon + 1);
            int last = column.lastIndexOf(':');
            if (last >= 0 && column.substring(last + 1).matches("\\d+")) {
                continue; // a sub-row entry of the former flat cell content
            }
            if (r < 0 || r >= rows.size()) {
                continue;
            }
            RepeatingElementsTable.TableRow row = rows.get(r);
            if (row.hasComplexChild(column) && !row.isColumnExpanded(column)) {
                row.toggleColumnExpanded(column);
                changed = true;
            }
        }
        if (changed) {
            table.recalculateColumnWidths();
        }
    }

    /**
     * Sets up a table of any grid once it is built: metrics, flip, how opened cells are built and
     * measured, its owner row, the invalidation of the grid holding it, and its remembered cells.
     */
    private void configureTable(RepeatingElementsTable table, FlatRow owner) {
        table.setMetrics(metrics);
        table.setFlipped(viewState.isFlipped(owner.getTableGroupKey()));
        table.setOwnerRow(owner);
        table.setCellMeasure(cellMeasure);
        table.setCellGridFactory(this::createCellGrid);
        GridBlock holder = owner.getBlock();
        // A table in an opened cell invalidates the grid holding it; a table of the root grid
        // invalidates the canvas itself. Without that, a root table that grew (a cell opened or
        // restored after a rebuild, a row expanded inside a cell, a nested table) kept the old
        // scroll range: the grid ran behind the next pane and the scroll bars could not reach it.
        table.setParentInvalidator(holder == null ? this::scheduleRelayout : holder::invalidate);
        restoreCellExpansion(table, viewState.cellExpansion(owner.getTableGroupKey()));
    }

    /**
     * Builds the grid of an opened cell like the root grid: groups become tables (unless switched
     * off), every remembered expand state and opened cell inside comes back.
     */
    private GridBlock createCellGrid(RepeatingElementsTable table, RepeatingElementsTable.TableRow row,
                                     String column) {
        List<FlatRow> rows = row.getRecord().flattenCell(column, viewState::isTable);
        rows = applyFilters(rows, cellAnchor(rows));
        GridBlock grid = new GridBlock(rows, row, column);
        adapter.attachTables(rows, this::markLayoutDirty);
        for (FlatRow inner : rows) {
            if (inner.isExpandable()) {
                Boolean expanded = viewState.expandedState(keyOf(inner));
                if (expanded != null) {
                    inner.setExpanded(expanded);
                }
            }
            if (inner.hasRepeatingTable()) {
                inner.whenRepeatingTableReady(nested -> configureTable(nested, inner));
            }
        }
        FlatRow.applyVisibility(rows);
        return grid;
    }

    /** @return the virtual anchor the top rows of an opened cell hang off, or {@code null} */
    private static FlatRow cellAnchor(List<FlatRow> rows) {
        for (FlatRow row : rows) {
            FlatRow parent = row.getParentRow();
            if (parent != null && parent.getDepth() < 0) {
                return parent;
            }
        }
        return null;
    }

    /** How the rows of opened cells are measured, with the user's name-column widths of their blocks. */
    final GridBlockLayout.Measure cellMeasure = new BlockMeasure() {
        @Override
        public Double nameColumnOverride(FlatRow parent) {
            if (parent == null) {
                return null;
            }
            if (parent.getDepth() < 0) {
                return parent.getBlock() == null ? null : viewState.nameColumnWidth(GridDragSupport.cellBlockKey(parent.getBlock()));
            }
            return viewState.nameColumnWidth(keyOf(parent));
        }
    };

    /**
     * A click inside an opened cell's grid, in the grid's coordinates: a nested table gets it
     * (recursively), an expand arrow toggles its row, a table glyph switches table/rows display.
     *
     * @return whether the click was used (otherwise it selects the host table row)
     */
    boolean handleBlockClick(MouseEvent event, GridBlock grid, double x, double y) {
        int i = grid.rowIndexAt(x, y);
        GridBlockLayout.Result g = grid.geometry();
        if (i < 0 || g == null) {
            return false;
        }
        FlatRow row = g.visible().get(i);
        double top = g.y()[i];
        if (row.hasRepeatingTable() && row.isExpanded()) {
            RepeatingElementsTable nested = row.getRepeatingTable();
            if (x >= nested.getX() && x <= nested.getX() + nested.getWidth()
                    && y >= top && y <= top + nested.getHeight()) {
                tables.handleTableClick(event, nested, x, y, top, row);
                return true;
            }
        }
        GridDragSupport.BlockDrag separator = drags.blockSeparator(row, x);
        if (separator != null) {
            // A click on a block's separator never selects; a double-click returns it to automatic width
            if (event.getClickCount() == 2) {
                drags.resetBlockWidth(separator);
                markLayoutDirty();
            }
            return true;
        }
        boolean onLine = y <= top + ROW_HEIGHT;
        if (row.getType() == FlatRow.RowType.FILTER) {
            // As in the root grid: the funnel switches the filter, a double-click edits it
            if (event.getClickCount() == 2) {
                editFilter(row.getParentRow());
            } else if (x < row.getValueX()) {
                toggleFilter(row);
            }
            return true;
        }
        if (event.getClickCount() == 1 && onLine) {
            if (row.isExpandable() && x >= row.getLabelX() && x <= row.getLabelX() + EXPAND_BAR_WIDTH) {
                FlatRow.toggleExpand(row, grid.rows());
                grid.invalidate();
                markLayoutDirty();
                return true;
            }
            double glyphX = row.getTableGlyphX();
            if (row.showsTableGlyph() && !Double.isNaN(glyphX)
                    && x >= glyphX - 2 && x <= glyphX + GridPainter.TABLE_ICON_SIZE + 2) {
                toggleTableDisplay(row);
                return true;
            }
        }
        // Anything else on a row selects it (the Inspector follows); a double-click edits it
        selectRow(row);
        if (event.getClickCount() == 2) {
            editor.editOnDoubleClick(row, x >= row.getValueX());
        }
        return true;
    }

    /** Expands the selected container fully (keyboard / menu entry point). */
    public void expandSelectedFully() {
        if (selectedRow != null) {
            expandFully(selectedRow);
            ensureRowVisible(selectedRow);
        }
    }

    /**
     * Collapses every container except the selected row's ancestors (XMLSpy "Collapse
     * Unselected"); the selected row itself keeps its state. Without a selection this is
     * {@link #collapseAll()}.
     */
    public void collapseUnselected() {
        if (selectedRow == null) {
            collapseAll();
            return;
        }
        for (FlatRow row : allRows) {
            if (row.isExpandable() && row != selectedRow && !FlatRow.isDescendantOf(selectedRow, row)) {
                row.setExpanded(false);
            }
        }
        recalculateVisibility();
        recalculateVisibleRows();
        updateScrollBars();
        render();
        ensureRowVisible(selectedRow);
    }

    /** @return the view state (expand state, selection, column widths, table toggles) */
    public GridViewState getViewState() {
        return viewState;
    }

    // ==================== Display settings ====================

    /** @return whether long values wrap onto further lines */
    public boolean isWordWrap() {
        return metrics.wraps();
    }

    /**
     * Switches word wrap: on, values wrap at the wrap width; off, every value stays on one
     * line and the content grows to the right (horizontal scrolling).
     *
     * @param wrap whether to wrap
     */
    public void setWordWrap(boolean wrap) {
        if (wrap == isWordWrap()) {
            return;
        }
        applyMetrics(new GridMetrics(metrics.measurer(),
                wrap ? GridMetrics.DEFAULT_WRAP_WIDTH : GridMetrics.NO_WRAP, metrics.maxLines()));
    }

    /** @return the maximum lines per cell/value, 0 = unlimited */
    public int getMaxCellLines() {
        return metrics.maxLines();
    }

    /**
     * Limits the height of every cell/value to the given number of lines (XMLSpy "Limit cell
     * height"); longer text is cut off with an ellipsis.
     *
     * @param lines the limit, 0 = unlimited
     */
    public void setMaxCellLines(int lines) {
        int limit = Math.max(0, lines);
        if (limit == metrics.maxLines()) {
            return;
        }
        applyMetrics(new GridMetrics(metrics.measurer(), metrics.wrapWidth(), limit));
    }

    /** Installs new metrics on the canvas and every built table, then lays out again. */
    private void applyMetrics(GridMetrics newMetrics) {
        editor.cancelEditing();
        metrics = newMetrics;
        forEachLiveRow(row -> { // the root's rows and those of opened cells, at any depth
            if (row.isRepeatingTableBuilt()) {
                row.getRepeatingTable().setMetrics(newMetrics);
            } else if (row.hasRepeatingTable()) {
                row.whenRepeatingTableReady(table -> table.setMetrics(newMetrics));
            }
        });
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /**
     * Returns every block's name column and every table column to automatic (content) width
     * (XMLSpy "Optimal widths").
     */
    public void optimalWidths() {
        viewState.clearNameColumnWidths();
        RepeatingElementsTable.clearColumnWidthOverrides();
        forEachLiveRow(row -> { // tables and blocks inside opened cells too
            if (row.isRepeatingTableBuilt()) {
                row.getRepeatingTable().clearUserWidths();
            }
            if (row.getBlock() != null) {
                row.getBlock().invalidate();
            }
        });
        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /**
     * Gives the canvas keyboard focus so arrow-key navigation works immediately
     * (e.g. right after the Graphic view is shown), without a mouse click first.
     */
    public void focusCanvas() {
        canvas.requestFocus();
    }

    /** @return the selected model node (a table row's node while a table row is selected) */
    public N getSelectedNode() {
        if (tables.selectedTable != null) {
            RepeatingElementsTable.TableRow tableRow = tables.selectedTable.getSelectedRow();
            if (tableRow != null) {
                N tableNode = adapter.nodeOf(tableRow);
                if (tableNode != null) {
                    return tableNode;
                }
            }
        }
        return selectedRow != null ? adapter.nodeOf(selectedRow) : null;
    }

    /**
     * Selects the first row that matches, revealing it if it is collapsed away (e.g. an
     * attribute row, which {@link #setSelectedNode} cannot address).
     *
     * @return whether a row matched
     */
    public boolean selectRowMatching(java.util.function.Predicate<FlatRow> match) {
        // The root's rows first, then those of opened cells (e.g. a record formula shown in its cell)
        FlatRow[] found = new FlatRow[1];
        forEachLiveRow(row -> {
            if (found[0] == null && match.test(row)) {
                found[0] = row;
            }
        });
        if (found[0] == null) {
            return false;
        }
        revealRow(found[0]);
        return true;
    }

    /** @return the model node of the selected embedded-table row, or {@code null} */
    private N selectedTableMember() {
        RepeatingElementsTable table = tables.selectedTable;
        RepeatingElementsTable.TableRow tableRow = table != null ? table.getSelectedRow() : null;
        return tableRow != null ? adapter.nodeOf(tableRow) : null;
    }

    /**
     * Selects and reveals the row of a model node given without its type (the context menu's
     * view action after it inserted a node, e.g. a new formula).
     */
    void selectModelNode(Object node) {
        if (node != null) {
            selectRowMatching(row -> row.getModelNode() == node);
        }
    }

    /** Selects the row of the given model node, revealing it if it is collapsed away. */
    public void setSelectedNode(N node) {
        if (node != null) {
            for (FlatRow row : allRows) {
                if (row.getModelNode() == node) {
                    revealRow(row);
                    return;
                }
            }
        }
    }

    // ==================== TSV clipboard ====================

    /**
     * @return the selected table cells as tab-separated text (the whole table with a header
     *         line when the selection covers every cell), or {@code null} without a cell selection
     */
    public String selectedCellsAsTsv() {
        return tables.selectedCellsAsTsv();
    }

    /**
     * Pastes a tab-separated block into the selected table, starting at the top-left selected
     * cell (rows beyond the table become new members where the model allows it).
     *
     * @param text the clipboard text
     * @return whether anything was pasted
     */
    public boolean pasteTsvIntoSelection(String text) {
        return tables.pasteTsvIntoSelection(text);
    }

    // ==================== Layout introspection (tests) ====================

    /** @return the draggable row under the point (see {@link GridDragSupport#dragCandidateAt}) */
    FlatRow dragCandidateAt(double mx, double my) {
        return drags.dragCandidateAt(mx, my);
    }

    /** @return where a drop at the point would go (see {@link GridDragSupport#dropTargetAt}) */
    DropTarget dropTargetAt(double mx, double my) {
        return drags.dropTargetAt(mx, my);
    }

    /** @return the namespace prefix of a name incl. the colon (see {@link GridPainter#namespacePrefix}) */
    static String namespacePrefix(String name) {
        return GridPainter.namespacePrefix(name);
    }

    /** @return the currently visible rows, in draw order (test hook) */
    List<FlatRow> visibleRowList() {
        return visibleRows;
    }

    /** @return the laid-out value text of a row, or {@code null} (test hook) */
    TextBlock valueBlockOf(FlatRow row) {
        return row.getValueBlock();
    }

    /** @return the total height of visible row {@code i} incl. its inline table (test hook) */
    double rowHeightAt(int i) {
        return rowHeights[i];
    }

    /** @return the top offset of visible row {@code i} in content space (test hook) */
    double rowTopAt(int i) {
        return rowYPositions[i];
    }

    /** @return the scrollable content width in unscaled pixels (test hook) */
    double contentWidth() {
        return totalWidth;
    }

    /** @return the scrollable content height in unscaled pixels (test hook) */
    double contentHeight() {
        return totalHeight;
    }

    /** @return the width of the root block's name column (test hook) */
    double nameColumnWidthValue() {
        return nameColumnWidth;
    }

    /** @return the x where the row's value column starts (test hook) */
    double valueXOf(FlatRow row) {
        return row.getValueX();
    }

    /** @return the x where the row's name cell starts (test hook) */
    double labelXOf(FlatRow row) {
        return row.getLabelX();
    }

    /** @return the colour the table headers are drawn in (test hook) */
    Color tableHeaderColorForTest() {
        return painter.tableHeaderBg;
    }

    /** Scrolls to the given content y immediately (test hook). */
    void scrollToForTest(double y) {
        vScrollBar.setValue(Math.max(0, Math.min(y, vScrollBar.getMax())));
        scrollOffsetY = vScrollBar.getValue();
        render();
    }

    /** Selects the table cell range (test hook / programmatic selection). */
    void selectCellRangeForTest(RepeatingElementsTable table, int r1, int c1, int r2, int c2) {
        tables.selectTableRow(table, r1);
        tables.setCellRange(r1, c1, r2, c2);
        render();
    }

    /** @return the row under the given model x / viewport y, or {@code null} (test hook) */
    FlatRow rowAtForTest(double mx, double my) {
        int idx = rowIndexAt(mx, my);
        return idx < 0 ? null : visibleRows.get(idx);
    }

    /** @return the drawing canvas, the target of mouse events (test hook) */
    Canvas canvasNode() {
        return canvas;
    }

    /** @return the open plain-text inline editor, or {@code null} (test hook) */
    TextField editFieldForTest() {
        return editor.editField;
    }

    /** @return the horizontal scroll range, 0 when the content fits (test hook) */
    double hScrollBarMax() {
        return hScrollBar.isDisabled() ? 0 : hScrollBar.getMax();
    }

    /** @return whether the vertical / horizontal scroll bar is shown (test hook) */
    boolean[] scrollBarsShown() {
        return new boolean[]{vScrollBar.isVisible(), hScrollBar.isVisible()};
    }

    /** @return the vertical / horizontal scroll bar (test hook) */
    ScrollBar[] scrollBarsForTest() {
        return new ScrollBar[]{vScrollBar, hScrollBar};
    }

    /** @return the vertical scroll offset in model units (test hook) */
    double scrollOffsetYValue() {
        return scrollOffsetY;
    }

    /** @return the horizontal scroll offset in model units (test hook) */
    double scrollOffsetXValue() {
        return scrollOffsetX;
    }

    // ==================== Search (XmlSearchTarget) ====================

    /**
     * Navigates to the next or previous row whose name or value matches the
     * search text. Matches are searched across the whole document; a match
     * hidden inside a collapsed node is revealed by auto-expanding its ancestors.
     *
     * @param searchText the text to find (case-insensitive)
     * @param forward    true to move to the next match, false for the previous
     * @return true if a match was found and navigated to
     */
    @Override
    public boolean find(String searchText, boolean forward) {
        if (searchText == null || searchText.isEmpty()) {
            return false;
        }
        if (!searchText.equals(lastSearchText)) {
            rebuildMatches(searchText);
        }
        if (searchMatches.isEmpty()) {
            return false;
        }

        if (forward) {
            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        } else {
            currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        }

        revealCurrentHit(forward);
        return true;
    }

    /**
     * Counts matches for the given text and navigates to the first one.
     *
     * @param searchText the text to find (case-insensitive)
     * @return the number of matching rows
     */
    @Override
    public int findAll(String searchText) {
        rebuildMatches(searchText);
        if (searchMatches.isEmpty()) {
            return 0;
        }
        int count = XmlCanvasSearch.countMatches(searchMatches);
        currentMatchIndex = 0;
        revealCurrentHit(true);
        return count;
    }

    /**
     * Counts the matches of the given text without moving to one (the search bar's status after
     * "Find Next"). Reuses the current match list while the text is unchanged.
     *
     * @param searchText the text to find (case-insensitive)
     * @return the number of matches
     */
    @Override
    public int countMatches(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return 0;
        }
        if (searchText.equals(lastSearchText)) {
            return XmlCanvasSearch.countMatches(searchMatches);
        }
        java.util.List<XmlCanvasSearch.Hit> hits = XmlCanvasSearch.findHits(allRows, searchText);
        return XmlCanvasSearch.countMatches(hits);
    }

    /**
     * Clears the cached search state.
     */
    @Override
    public void clearSearch() {
        lastSearchText = "";
        searchMatches = new ArrayList<>();
        currentMatchIndex = -1;
    }

    /**
     * Rebuilds the match list for the given search text and resets the cursor.
     *
     * @param searchText the text to match
     */
    private void rebuildMatches(String searchText) {
        lastSearchText = searchText;
        // The root's rows; opened cells (at any depth) are searched at their place, closed ones
        // by their content
        searchMatches = new ArrayList<>(XmlCanvasSearch.findHits(allRows, searchText));
        currentMatchIndex = -1;
    }

    /**
     * Reveals the hit at {@link #currentMatchIndex}. A closed cell whose content matches is opened
     * first and replaced in the match list by the hits inside it (the first one when searching
     * forward, the last one backwards), down to the innermost closed cell.
     */
    private void revealCurrentHit(boolean forward) {
        XmlCanvasSearch.Hit hit = searchMatches.get(currentMatchIndex);
        while (hit.isHidden()) {
            List<XmlCanvasSearch.Hit> inner = openHiddenCell(hit);
            if (inner.isEmpty()) {
                // Only its summary matched: the cell itself is the hit
                hit = new XmlCanvasSearch.Hit(hit.row(), hit.tableRow(), hit.tableColumn());
                searchMatches.set(currentMatchIndex, hit);
                break;
            }
            searchMatches.remove(currentMatchIndex);
            searchMatches.addAll(currentMatchIndex, inner);
            if (!forward) {
                currentMatchIndex += inner.size() - 1;
            }
            hit = searchMatches.get(currentMatchIndex);
        }
        revealHit(hit);
    }

    /**
     * Opens the closed complex cell of a hidden hit (its owner rows are expanded too) and searches
     * the cell's grid.
     *
     * @return the hits inside the cell, empty when it could not be opened
     */
    private List<XmlCanvasSearch.Hit> openHiddenCell(XmlCanvasSearch.Hit hit) {
        FlatRow owner = hit.row();
        RepeatingElementsTable table = owner.getRepeatingTable();
        if (table == null || hit.tableRow() >= table.getRows().size()) {
            return List.of();
        }
        RepeatingElementsTable.TableColumn column = table.getColumn(hit.tableColumn());
        if (column == null) {
            return List.of();
        }
        if (!owner.isExpanded()) {
            owner.setExpanded(true);
        }
        revealRow(owner); // its ancestors open too, so the cell's grid is laid out where it is drawn
        RepeatingElementsTable.TableRow row = table.getRows().get(hit.tableRow());
        if (!row.isColumnExpanded(column.getName())) {
            row.toggleColumnExpanded(column.getName());
            table.recalculateColumnWidths();
        }
        GridBlock grid = row.getCellGrid(column.getName());
        return grid == null ? List.of() : XmlCanvasSearch.findHits(grid.rows(), lastSearchText);
    }

    /**
     * Reveals a search hit: a row, or a table cell (its owner is expanded too and the cell
     * becomes the selected cell range).
     */
    private void revealHit(XmlCanvasSearch.Hit hit) {
        if (!hit.isCell()) {
            revealRow(hit.row());
            return;
        }
        FlatRow owner = hit.row();
        if (!owner.isExpanded()) {
            owner.setExpanded(true);
        }
        revealRow(owner);
        RepeatingElementsTable table = owner.getRepeatingTable();
        if (table != null && hit.tableRow() < table.getRows().size()) {
            tables.selectTableRow(table, hit.tableRow());
            tables.setCellRange(hit.tableRow(), hit.tableColumn(), hit.tableRow(), hit.tableColumn());
            ensureCellVisible(owner, table, hit.tableRow(), hit.tableColumn());
            render();
        }
    }

    /** Scrolls so that the given table cell is inside the viewport. */
    private void ensureCellVisible(FlatRow owner, RepeatingElementsTable table, int row, int col) {
        double[] system = rowOrigin(owner); // the table's coordinate system and top (viewport y)
        if (system == null) {
            return;
        }
        RepeatingElementsTable.CellRect rect = table.cellRect(row, col);
        double top = system[1] + scrollOffsetY + rect.y();
        double bottom = top + rect.height();
        double viewH = viewportHeightModel();
        if (top < scrollOffsetY) {
            animateScrollTo(top - 20);
        } else if (bottom > scrollOffsetY + viewH) {
            animateScrollTo(bottom - viewH + 20);
        }
        double viewW = viewportWidthModel();
        double left = system[0] + rect.x();
        if (left < scrollOffsetX || left + rect.width() > scrollOffsetX + viewW) {
            hScrollBar.setValue(Math.max(0, Math.min(left - 20, hScrollBar.getMax())));
        }
    }

    /**
     * Expands the row's ancestors so it becomes visible, then selects it and
     * scrolls it into view.
     *
     * @param row the row to reveal (ignored if null)
     */
    private void revealRow(FlatRow row) {
        if (row == null) {
            return;
        }
        FlatRow parent = row.getParentRow();
        while (parent != null && parent.getDepth() >= 0) {
            if (parent.isExpandable() && !parent.isExpanded()) {
                parent.setExpanded(true);
            }
            parent = parent.getParentRow();
        }
        if (row.getBlock() != null) {
            // A row of an opened cell: its grid shows it after its ancestors there opened
            FlatRow.applyVisibility(row.getBlock().rows());
            row.getBlock().invalidate();
        } else {
            recalculateVisibility();
        }
        recalculateVisibleRows();
        updateScrollBars();

        selectRow(row);
        ensureRowVisible(row);
    }
}
