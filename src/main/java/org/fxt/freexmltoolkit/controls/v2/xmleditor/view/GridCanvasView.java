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
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BG_CANVAS;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BG_SUBTLE;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BG_SURFACE;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BG_SURFACE_2;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.BORDER_DEFAULT;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.CODE_ATTR;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.CODE_ELEM;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.CODE_TEXT;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.CODE_VAL;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.INDIGO;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.PRIMARY;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.PURPLE;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEAL;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_MUTED;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.WF_FAVORITES_BG;
import static org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.WF_TRANSFORM_FG;

import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;

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
    private final GridModelAdapter<N> adapter;

    // ==================== Canvas ====================

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final Pane canvasContainer;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;

    // ==================== Flat Row Data ====================

    /** Complete flat list of all rows (visible and hidden). */
    private List<FlatRow> allRows = new ArrayList<>();

    /** Cached list of currently visible rows (after expand/collapse filtering). */
    private List<FlatRow> visibleRows = new ArrayList<>();

    /** Cumulative Y offset for each visible row index, accounting for expanded tables above. */
    private double[] rowYPositions = new double[0];
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
    private List<FlatRow> searchMatches = new ArrayList<>();

    /** Index of the currently highlighted match within {@link #searchMatches}, or -1. */
    private int currentMatchIndex = -1;

    // ==================== Layout Constants ====================

    private static final double ROW_HEIGHT = GridMetrics.ROW_HEIGHT;
    private static final double LINE_HEIGHT = GridMetrics.LINE_HEIGHT;
    private static final double INDENT = GridMetrics.INDENT;
    private static final double ICON_AREA_WIDTH = GridMetrics.ICON_AREA_WIDTH;
    private static final double EXPAND_BAR_WIDTH = GridMetrics.EXPAND_BAR_WIDTH;
    private static final double SCROLLBAR_WIDTH = 14;
    private static final double LEFT_MARGIN = 8;
    /** Breathing room right of the widest content (keeps the last frame line inside the view). */
    private static final double RIGHT_MARGIN = 8;
    private static final double NAME_VALUE_GAP = 16;
    private static final double MIN_NAME_COL_WIDTH = 120;
    private static final double MIN_VALUE_COL_WIDTH = 100;
    /** Horizontal padding of a value cell's text. */
    private static final double VALUE_PADDING = 6;
    /** Soft length of the inline preview of a collapsed container. */
    static final int PREVIEW_MAX_CHARS = 200;
    /** Length of the preview shown in the hover tooltip. */
    private static final int TOOLTIP_MAX_CHARS = 2000;

    /** Measured text metrics shared with the embedded tables (real FX font measurement). */
    private final GridMetrics metrics = new GridMetrics(TextMeasurer.fx(), GridMetrics.DEFAULT_WRAP_WIDTH);

    /** Width of the root block's name column (the test hook {@link #nameColumnWidthValue()}). */
    private double nameColumnWidth = MIN_NAME_COL_WIDTH;

    /**
     * The per-document view state (expand state, selection, name-column widths, table toggles)
     * that survives the row rebuild after every edit; keyed by the adapter's stable row keys.
     */
    private final GridViewState viewState = new GridViewState();

    /**
     * Own height of each visible row: its value line(s), an embedded table's height for a table
     * owner, 0 for an expanded container (its name cell spans the child block instead).
     */
    private double[] rowHeights = new double[0];

    /** Index of each visible row in {@link #visibleRows} (identity), rebuilt per layout. */
    private java.util.Map<FlatRow, Integer> rowIndexMap = new java.util.IdentityHashMap<>();

    /** The tooltip showing the full preview of a hovered collapsed container. */
    private final Tooltip previewTooltip = new Tooltip();
    private FlatRow previewTooltipRow = null;

    // ==================== Scroll State ====================

    private double scrollOffsetX = 0;
    private double scrollOffsetY = 0;
    private double totalHeight = 0;
    private double totalWidth = 0;

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

    private FlatRow selectedRow = null;
    private FlatRow hoveredRow = null;
    /** The row whose expand arrow is under the pointer. */
    private FlatRow hoveredExpandBar = null;

    // ==================== Inline Editing ====================

    private TextField editField = null;
    private FlatRow editingRow = null;
    private boolean editingValue = false;
    private boolean editingElementName = false;

    // Table cell editing state (kept for repeating table integration)
    private RepeatingElementsTable editingTable = null;
    private int editingTableRowIndex = -1;
    private String editingTableColumnName = null;

    // Column resizing (drag a separator in an embedded table's column-header row)
    private static final double RESIZE_HANDLE = 4;

    /** An in-progress column drag: which column of which table, and where it started. */
    private record ColumnDrag(RepeatingElementsTable table, int columnIndex,
                              double startScreenX, double startWidth) {
    }

    private ColumnDrag columnDrag = null;
    private boolean columnDragMoved = false;
    /** Set after a drag so the MOUSE_CLICKED JavaFX fires afterwards does not sort/select. */
    private boolean suppressNextClick = false;

    // Table selection/hover state (kept for repeating table integration)
    private RepeatingElementsTable selectedTable = null;
    private RepeatingElementsTable hoveredTable = null;
    private int hoveredTableRowIndex = -1;
    private int hoveredTableColumnIndex = -1;

    // Type-aware widget editing
    private TypeAwareWidgetFactory.EditWidget activeEditWidget = null;
    private Node activeWidgetNode = null;

    // Guard to prevent double commits
    private boolean isCommitting = false;

    // ==================== Context Menu ====================

    /** Built on first use (right-click / shortcut): it costs ~20 ms of FX time per view. */
    private GridContextMenu<N> contextMenu;

    // ==================== Highlight State ====================

    private FlatRow highlightedRow = null;
    private Color HIGHLIGHT_COLOR;  // Yellow highlight (theme-dependent, see applyPalette)

    // ==================== Status Bar & Toast ====================

    private StackPane toastContainer;

    // ==================== Document Change Callback ====================

    private java.util.function.Consumer<String> onDocumentModified;

    // ==================== Colors (design tokens, theme-dependent) ====================
    //
    // Instance fields (kept in UPPER_SNAKE for diff-friendliness with the former
    // constants); (re)assigned by applyPalette() for the current light/dark theme from
    // the DesignTokens so the grid follows the application palette.

    // Backgrounds
    private Color ROW_BG_EVEN;
    private Color ROW_BG_ODD;

    // Selection/Hover
    private Color SELECTED_BG;
    private Color SELECTED_BORDER;
    private Color HOVERED_BG;

    // Text colors
    private Color TEXT_ELEMENT;
    private Color TEXT_ATTRIBUTE_NAME;
    private Color TEXT_ATTRIBUTE_VALUE;
    private Color TEXT_CONTENT;
    private Color TEXT_SECONDARY;
    private Color TEXT_COMMENT;
    private Color TEXT_CDATA;
    private Color TEXT_PI;

    // JSON value colors (keys reuse TEXT_ELEMENT, containers reuse TEXT_ELEMENT/ACCENT)
    private Color TEXT_JSON_STRING;
    private Color TEXT_JSON_NUMBER;
    private Color TEXT_JSON_BOOLEAN;
    private Color TEXT_JSON_NULL;

    // Cell frames, tree lines (cell sub-trees) & expand arrows
    private Color FRAME_LINE;
    private Color TREE_LINE_COLOR;
    private Color EXPAND_BAR_COLOR;
    private Color EXPAND_BAR_HOVER;
    private Color EXPAND_BAR_ARROW;

    // Row separator
    private Color ROW_SEPARATOR;

    // Child count, instance index, collapsed preview
    private Color CHILD_COUNT_COLOR;
    private Color INSTANCE_INDEX;
    private Color PREVIEW_TEXT;

    // Accent (the design system's primary)
    private Color ACCENT;

    // Table colors
    private Color TABLE_HEADER_BG;
    private Color TABLE_HEADER_TEXT;
    private Color TABLE_HEADER_SELECTED;
    private Color TABLE_BORDER;
    private Color TABLE_ROW_EVEN;
    private Color TABLE_ROW_ODD;
    private Color TABLE_ROW_HOVER;
    private Color TABLE_ROW_SELECTED;
    private Color TABLE_MISSING_BG;

    /**
     * Assigns the light or dark grid palette from the {@link org.fxt.freexmltoolkit.controls.theme.DesignTokens}
     * so the canvas renders in the application's colours in both themes.
     */
    private void applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme theme) {
        ROW_BG_EVEN = BG_SURFACE.color(theme);
        ROW_BG_ODD = ROW_BG_EVEN;

        SELECTED_BG = BG_SUBTLE.color(theme);
        SELECTED_BORDER = PRIMARY.color(theme);
        HOVERED_BG = BG_SURFACE_2.color(theme);
        HIGHLIGHT_COLOR = WF_FAVORITES_BG.color(theme);

        TEXT_ELEMENT = CODE_ELEM.color(theme);
        TEXT_ATTRIBUTE_NAME = CODE_ATTR.color(theme);
        TEXT_ATTRIBUTE_VALUE = CODE_VAL.color(theme);
        TEXT_CONTENT = CODE_TEXT.color(theme);
        TEXT_SECONDARY = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);
        TEXT_COMMENT = TEAL.color(theme);
        TEXT_CDATA = INDIGO.color(theme);
        TEXT_PI = PURPLE.color(theme);

        TEXT_JSON_STRING = CODE_VAL.color(theme);
        TEXT_JSON_NUMBER = WF_TRANSFORM_FG.color(theme);
        TEXT_JSON_BOOLEAN = TEAL.color(theme);
        TEXT_JSON_NULL = TEXT_MUTED.color(theme);

        FRAME_LINE = BORDER_DEFAULT.color(theme);
        TREE_LINE_COLOR = FRAME_LINE;
        EXPAND_BAR_COLOR = FRAME_LINE;
        EXPAND_BAR_HOVER = PRIMARY.color(theme);
        EXPAND_BAR_ARROW = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);

        ROW_SEPARATOR = FRAME_LINE;
        CHILD_COUNT_COLOR = TEXT_MUTED.color(theme);
        INSTANCE_INDEX = CHILD_COUNT_COLOR;
        PREVIEW_TEXT = CHILD_COUNT_COLOR;
        ACCENT = PRIMARY.color(theme);

        TABLE_HEADER_BG = BG_SURFACE_2.color(theme);
        TABLE_HEADER_TEXT = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);
        TABLE_HEADER_SELECTED = BG_SUBTLE.color(theme);
        TABLE_BORDER = FRAME_LINE;
        TABLE_ROW_EVEN = ROW_BG_EVEN;
        TABLE_ROW_ODD = ROW_BG_EVEN;
        TABLE_ROW_HOVER = HOVERED_BG;
        TABLE_ROW_SELECTED = SELECTED_BG;
        TABLE_MISSING_BG = BG_CANVAS.color(theme);
    }

    /**
     * Weakly registered with DesignTokens — the instance field keeps it alive
     * exactly as long as this view; see DesignTokens.addThemeListener.
     */
    private final java.util.function.Consumer<org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme>
            themeListener = theme -> {
                applyPalette(theme);
                render();
            };

    // ==================== Fonts ====================

    private static final Font ROW_FONT = GridFont.ROW.toFont();
    private static final Font ROW_FONT_BOLD = GridFont.ROW_BOLD.toFont();
    private static final Font SMALL_FONT = GridFont.SMALL.toFont();
    private static final Font ICON_FONT = GridFont.ICON.toFont();

    // ==================== Constructor ====================

    /**
     * Creates a canvas over the given model adapter. Subclasses bind a concrete model:
     * {@link XmlCanvasView} for XML instances, the JSON grid's canvas for JSON documents.
     *
     * @param adapter the model adapter (never {@code null})
     */
    protected GridCanvasView(GridModelAdapter<N> adapter) {
        this.adapter = adapter;

        applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme());
        org.fxt.freexmltoolkit.controls.theme.DesignTokens.addThemeListener(themeListener);

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

        // Position canvas
        double canvasW = w - SCROLLBAR_WIDTH;
        double canvasH = h - SCROLLBAR_WIDTH;

        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);
        canvas.setLayoutX(0);
        canvas.setLayoutY(0);

        canvasContainer.setPrefWidth(canvasW);
        canvasContainer.setPrefHeight(canvasH);

        // Position vertical scrollbar
        vScrollBar.setLayoutX(w - SCROLLBAR_WIDTH);
        vScrollBar.setLayoutY(0);
        vScrollBar.setPrefHeight(h - SCROLLBAR_WIDTH);

        // Position horizontal scrollbar
        hScrollBar.setLayoutX(0);
        hScrollBar.setLayoutY(h - SCROLLBAR_WIDTH);
        hScrollBar.setPrefWidth(w - SCROLLBAR_WIDTH);

        // Update scroll bars
        updateScrollBars();
        render();
    }

    private void onResize() {
        updateScrollBars();
        render();
    }

    private void updateScrollBars() {
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
            vScrollBar.setVisibleAmount(viewportHeight);
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
            hScrollBar.setVisibleAmount(viewportWidth);
            hScrollBar.setBlockIncrement(100);
            hScrollBar.setUnitIncrement(20);
            if (scrollOffsetX > hScrollBar.getMax()) {
                hScrollBar.setValue(hScrollBar.getMax());
            }
        }
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
        cancelEditing();
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
    private double viewportWidthModel() {
        return canvas.getWidth() / getZoom();
    }

    /** @return the viewport height in unscaled model pixels */
    private double viewportHeightModel() {
        return canvas.getHeight() / getZoom();
    }

    /** @return the model-space x (scroll applied) for a canvas-relative screen x */
    private double toModelX(double screenX) {
        return screenX / getZoom() + scrollOffsetX;
    }

    /** @return the unscaled viewport y (scroll NOT applied) for a canvas-relative screen y */
    private double toViewportY(double screenY) {
        return screenY / getZoom();
    }

    // ==================== Tree Building ====================

    /**
     * Rebuilds the flat row list from the document.
     * Preserves expand/collapse state across rebuilds by matching rows by label+type+depth.
     */
    private void rebuildTree() {
        // Remember expand state and selection by stable key before rebuilding
        for (FlatRow row : allRows) {
            if (row.isExpandable()) {
                viewState.setExpanded(adapter.stableKey(row), row.isExpanded());
            }
        }
        if (selectedRow != null) {
            viewState.setSelectedKey(adapter.stableKey(selectedRow));
        }

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

        allRows = adapter.flatten();
        adapter.attachTables(allRows, this::markLayoutDirty);
        for (FlatRow row : allRows) {
            if (row.hasRepeatingTable()) {
                row.whenRepeatingTableReady(table -> table.setMetrics(metrics)); // without building it now
            }
        }

        // Restore expand state and selection
        FlatRow restoredSelection = null;
        String selectedKey = viewState.getSelectedKey();
        for (FlatRow row : allRows) {
            String key = adapter.stableKey(row);
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
        if (selectedRow != null) {
            selectedRow.setSelected(true);
        }
        hoveredRow = null;
        hoveredExpandBar = null;

        // Re-apply visibility based on restored or default expand state
        recalculateVisibility();

        recalculateVisibleRows();
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
    private void recalculateVisibleRows() {
        visibleRows = new ArrayList<>();
        for (FlatRow row : allRows) {
            if (row.isVisible()) {
                visibleRows.add(row);
            }
        }
        lastDescendantIndex = lastDescendantIndices(visibleRows);
        rowIndexMap = new java.util.IdentityHashMap<>();
        for (int i = 0; i < visibleRows.size(); i++) {
            rowIndexMap.put(visibleRows.get(i), i);
        }

        // -- Pass 1: name-column width per block (keyed by the block's parent row; null = root) --
        java.util.Map<FlatRow, Double> blockWidths = new java.util.IdentityHashMap<>();
        for (FlatRow row : visibleRows) {
            double w = EXPAND_BAR_WIDTH + ICON_AREA_WIDTH + labelTextWidth(row) + labelExtrasWidth(row)
                    + NAME_VALUE_GAP;
            blockWidths.merge(row.getParentRow(), w, Math::max);
        }
        for (java.util.Map.Entry<FlatRow, Double> entry : blockWidths.entrySet()) {
            Double override = viewState.nameColumnWidth(blockKey(entry.getKey()));
            entry.setValue(override != null ? override : Math.max(MIN_NAME_COL_WIDTH, entry.getValue()));
        }
        nameColumnWidth = blockWidths.getOrDefault(null, MIN_NAME_COL_WIDTH);

        // -- Pass 2: x positions (pre-order: a parent is laid out before its children) --
        for (FlatRow row : visibleRows) {
            FlatRow parent = row.getParentRow();
            double labelX = parent == null ? LEFT_MARGIN : parent.getValueX();
            double blockWidth = blockWidths.getOrDefault(parent, MIN_NAME_COL_WIDTH);
            row.setCellGeometry(labelX, labelX + blockWidth);
        }

        // -- Pass 3: heights, y positions and extents --
        rowYPositions = new double[visibleRows.size()];
        rowHeights = new double[visibleRows.size()];
        double currentY = 0;
        double maxWidth = LEFT_MARGIN + nameColumnWidth + MIN_VALUE_COL_WIDTH + RIGHT_MARGIN;

        for (int i = 0; i < visibleRows.size(); i++) {
            FlatRow row = visibleRows.get(i);
            rowYPositions[i] = currentY;
            row.setValueBlock(null);
            double valueX = row.getValueX();
            maxWidth = Math.max(maxWidth, valueX + MIN_VALUE_COL_WIDTH + RIGHT_MARGIN);

            double rowHeight;
            if (row.hasRepeatingTable() && row.isExpanded()) {
                // Table owner: the embedded table sits beside the name cell, at the value column
                RepeatingElementsTable table = row.getRepeatingTable();
                table.setX(valueX);
                table.setY(currentY);
                table.calculateWidth(canvas.getWidth());
                rowHeight = Math.max(ROW_HEIGHT, table.calculateHeight());
                maxWidth = Math.max(maxWidth, valueX + table.getWidth() + RIGHT_MARGIN);
            } else if (row.isExpandable() && row.isExpanded() && lastDescendantIndex[i] > i) {
                // Expanded container: no own line, the name cell spans the child block
                rowHeight = 0;
            } else if (row.getValue() != null) {
                String text = row.isLeafWithValue() ? adapter.decorateLeafValue(row) : row.getValue();
                TextBlock block = TextBlock.of(metrics, text, GridFont.ROW, metrics.wrapWidth());
                row.setValueBlock(block);
                rowHeight = block.height();
                maxWidth = Math.max(maxWidth, valueX + VALUE_PADDING + block.width() + NAME_VALUE_GAP + RIGHT_MARGIN);
            } else if (row.isExpandable() && !row.isExpanded()) {
                // Collapsed container: one grey preview line (never wrapped)
                String preview = previewOf(row);
                if (preview != null && !preview.isEmpty()) {
                    double width = metrics.width(preview, GridFont.ROW);
                    row.setValueBlock(new TextBlock(List.of(preview), width, ROW_HEIGHT));
                }
                rowHeight = ROW_HEIGHT;
            } else {
                rowHeight = ROW_HEIGHT;
            }

            rowHeights[i] = rowHeight;
            currentY += rowHeight;
        }

        totalHeight = currentY;
        totalWidth = maxWidth;
    }

    /** @return the stable key of the block formed by the children of {@code parent} (root block: "") */
    private String blockKey(FlatRow parent) {
        return parent == null ? GridViewState.ROOT_BLOCK : adapter.stableKey(parent);
    }

    /** @return the cached one-line preview of a container row (computed once per row) */
    private String previewOf(FlatRow row) {
        if (!row.hasCollapsedPreview()) {
            row.setCollapsedPreview(adapter.collapsedPreview(row, PREVIEW_MAX_CHARS));
        }
        return row.getCollapsedPreview();
    }

    /** @return whether the row shows the grey inline preview (a collapsed container without a value) */
    private boolean showsPreview(FlatRow row) {
        return row.isExpandable() && !row.isExpanded() && row.getValue() == null && row.getValueBlock() != null;
    }

    /**
     * @return the height of the row's name cell: its own line(s) plus every visible descendant
     *         row (an expanded container's cell spans its whole child block)
     */
    private double rowSpanHeight(int i) {
        int last = i < lastDescendantIndex.length ? lastDescendantIndex[i] : i;
        return rowYPositions[last] + rowHeights[last] - rowYPositions[i];
    }

    /** @return the measured width of the row's label as drawn */
    private double labelTextWidth(FlatRow row) {
        return row.getLabel() == null ? 0 : metrics.width(row.getLabel(), GridFont.ROW_BOLD);
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
        if (row.hasRepeatingTable()) {
            w += TABLE_ICON_SIZE + GridMetrics.CHILD_COUNT_GAP;
        }
        return w;
    }

    private static String childCountText(FlatRow row) {
        return "(" + row.getChildCount() + ")";
    }

    private static String instanceIndexText(FlatRow row) {
        return "<" + row.getInstanceIndex() + ">";
    }

    /** Size of the small table glyph drawn after a table owner's label. */
    private static final double TABLE_ICON_SIZE = 10;

    /**
     * Draws a wrapped text block: the first line centred on {@code firstLineCenterY},
     * every further line {@link #LINE_HEIGHT} lower. Lines outside the canvas are skipped.
     */
    private void drawTextBlock(TextBlock block, double x, double firstLineCenterY) {
        if (block == null) {
            return;
        }
        double canvasBottom = viewportHeightModel() + LINE_HEIGHT;
        List<String> lines = block.lines();
        for (int k = 0; k < lines.size(); k++) {
            double y = firstLineCenterY + k * LINE_HEIGHT;
            if (y < -LINE_HEIGHT) {
                continue;
            }
            if (y > canvasBottom) {
                break;
            }
            gc.fillText(lines.get(k), x, y);
        }
    }

    /** @return the x where the row's icon is drawn (right of the expand-arrow area) */
    private double rowIconX(FlatRow row) {
        return row.getLabelX() + EXPAND_BAR_WIDTH;
    }

    /** @return the x where the row's label text starts */
    private double rowTextX(FlatRow row) {
        return row.getLabelX() + EXPAND_BAR_WIDTH + ICON_AREA_WIDTH;
    }

    // ==================== Rendering ====================

    /**
     * Main render method. Clears the canvas and draws visible rows.
     */
    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) {
            return;
        }

        // Clear canvas
        gc.setFill(ROW_BG_EVEN);
        gc.fillRect(0, 0, w, h);

        if (visibleRows.isEmpty()) {
            drawEmptyState();
            return;
        }

        // Calculate visible row range using Y positions (viewport in model units)
        int firstVisible = findRowIndexAtY(scrollOffsetY);
        int lastVisible = findRowIndexAtY(scrollOffsetY + viewportHeightModel());
        if (firstVisible < 0) firstVisible = 0;
        if (lastVisible < 0 || lastVisible >= visibleRows.size()) lastVisible = visibleRows.size() - 1;

        // Save, apply the zoom, then translate for the horizontal scroll offset
        gc.save();
        double z = getZoom();
        gc.scale(z, z);
        gc.translate(-scrollOffsetX, 0);

        // The name cells of the first visible row's ancestors start above the viewport but
        // span into it: draw them first (outermost first), then the rows in view.
        for (int idx : ancestorIndices(firstVisible)) {
            drawRow(idx);
        }
        for (int i = firstVisible; i <= lastVisible; i++) {
            drawRow(i);
        }

        // Draw inline tables beside their owner rows
        for (int i = firstVisible; i <= lastVisible; i++) {
            FlatRow row = visibleRows.get(i);
            if (row.hasRepeatingTable() && row.isExpanded()) {
                renderInlineTable(row, rowYPositions[i] - scrollOffsetY);
            }
        }

        gc.restore();

        // Draw info overlay (not scrolled)
        drawInfo();
    }

    /** @return the visible-row indices of the row's ancestors, outermost first */
    private List<Integer> ancestorIndices(int rowIndex) {
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
     * Draws one row: its name cell (spanning the child block of an expanded container), the
     * value cell with the wrapped value or the grey preview, the cell frames and the expand arrow.
     */
    private void drawRow(int visibleIndex) {
        FlatRow row = visibleRows.get(visibleIndex);
        double top = rowYPositions[visibleIndex] - scrollOffsetY;
        double own = rowHeights[visibleIndex];
        double span = rowSpanHeight(visibleIndex);
        double labelX = row.getLabelX();
        double valueX = row.getValueX();
        double right = Math.max(totalWidth, viewportWidthModel() + scrollOffsetX);
        boolean tableOwner = row.hasRepeatingTable() && row.isExpanded();
        boolean hasValueCell = own > 0 && !tableOwner;

        // -- Background (name cell; the value cell too while the row has one) --
        Color bgColor = null;
        if (row == highlightedRow) {
            bgColor = HIGHLIGHT_COLOR;
        } else if (row.isSelected()) {
            bgColor = SELECTED_BG;
        } else if (row.isHovered()) {
            bgColor = HOVERED_BG;
        }
        if (bgColor != null) {
            gc.setFill(bgColor);
            gc.fillRect(labelX, top, valueX - labelX, span);
            if (hasValueCell) {
                gc.fillRect(valueX, top, right - valueX, own);
            }
        }

        // -- Cell frames (hairlines): the name cell, and the bottom edge of the value cell --
        gc.setStroke(FRAME_LINE);
        gc.setLineWidth(1);
        gc.strokeRect(labelX + 0.5, top + 0.5, valueX - labelX, span);
        if (hasValueCell) {
            gc.strokeLine(valueX, top + own + 0.5, right, top + own + 0.5);
        }

        // -- Selection border --
        if (row.isSelected()) {
            gc.setStroke(SELECTED_BORDER);
            gc.setLineWidth(1);
            gc.strokeRect(labelX + 0.5, top + 0.5, valueX - labelX, span);
            if (hasValueCell) {
                gc.strokeRect(valueX + 0.5, top + 0.5, right - valueX - 1, own);
            }
        }

        // -- Expand arrow, icon, label --
        double lineCenterY = top + ROW_HEIGHT / 2;
        if (row.isExpandable()) {
            drawExpandArrow(row, labelX, lineCenterY);
        }
        double iconX = rowIconX(row);
        drawRowIcon(row, iconX, lineCenterY);

        double textX = rowTextX(row);
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.LEFT);

        boolean isEditingThisRowName = (editingRow == row && editingElementName);
        boolean isEditingThisRowValue = (editingRow == row && editingValue);

        double afterLabelX = textX;
        if (row.getLabel() != null && !isEditingThisRowName) {
            gc.setFont(ROW_FONT_BOLD);
            gc.setFill(getRowLabelColor(row.getType()));
            gc.fillText(row.getLabel(), textX, lineCenterY);
            afterLabelX += labelTextWidth(row);
        }

        // <n> instance number, (n) child count, table glyph
        gc.setFont(SMALL_FONT);
        if (row.getInstanceIndex() > 0) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            gc.setFill(INSTANCE_INDEX);
            String text = instanceIndexText(row);
            gc.fillText(text, afterLabelX, lineCenterY);
            afterLabelX += metrics.width(text, GridFont.SMALL);
        }
        if (row.isExpandable()) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            gc.setFill(CHILD_COUNT_COLOR);
            String text = childCountText(row);
            gc.fillText(text, afterLabelX, lineCenterY);
            afterLabelX += metrics.width(text, GridFont.SMALL);
        }
        if (row.hasRepeatingTable()) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            drawTableGlyph(afterLabelX, lineCenterY - TABLE_ICON_SIZE / 2, row.isExpanded());
        }

        // -- Value text (wrapped) or the grey one-line preview, clipped to the value cell --
        if (hasValueCell && row.getValueBlock() != null && !isEditingThisRowValue) {
            gc.save();
            gc.beginPath();
            gc.rect(valueX, top, right - valueX, own);
            gc.clip();
            gc.setFont(ROW_FONT);
            if (showsPreview(row)) {
                gc.setFill(PREVIEW_TEXT);
            } else if (row.isLeafWithValue()) {
                gc.setFill(row.getType().isJson() ? getRowValueColor(row.getType()) : TEXT_CONTENT);
            } else {
                gc.setFill(getRowValueColor(row.getType()));
            }
            drawTextBlock(row.getValueBlock(), valueX + VALUE_PADDING, lineCenterY);
            gc.restore();
        }
    }

    /** Draws the ▶ / ▼ expand arrow in the arrow area left of the icon. */
    private void drawExpandArrow(FlatRow row, double labelX, double cy) {
        boolean hovered = row == hoveredExpandBar;
        gc.setFill(hovered ? EXPAND_BAR_HOVER : EXPAND_BAR_ARROW);
        double cx = labelX + EXPAND_BAR_WIDTH / 2 + 1;
        double size = 4;
        if (row.isExpanded()) {
            gc.fillPolygon(new double[]{cx - size, cx + size, cx},
                    new double[]{cy - size / 2, cy - size / 2, cy + size / 2 + 1}, 3);
        } else {
            gc.fillPolygon(new double[]{cx - size / 2, cx + size / 2 + 1, cx - size / 2},
                    new double[]{cy - size, cy, cy + size}, 3);
        }
    }

    /** Draws the small 3x3 table glyph that marks a repeating group; filled when its table is shown. */
    private void drawTableGlyph(double x, double y, boolean active) {
        double s = TABLE_ICON_SIZE;
        gc.setStroke(active ? ACCENT : TABLE_HEADER_TEXT);
        gc.setLineWidth(1);
        gc.strokeRect(x + 0.5, y + 0.5, s, s);
        gc.strokeLine(x + 0.5, y + s / 3 + 0.5, x + s + 0.5, y + s / 3 + 0.5);
        gc.strokeLine(x + 0.5, y + 2 * s / 3 + 0.5, x + s + 0.5, y + 2 * s / 3 + 0.5);
        gc.strokeLine(x + s / 3 + 0.5, y + 0.5, x + s / 3 + 0.5, y + s + 0.5);
        gc.strokeLine(x + 2 * s / 3 + 0.5, y + 0.5, x + 2 * s / 3 + 0.5, y + s + 0.5);
    }

    /**
     * Draws the type-specific icon for a row (XMLSpy's node-type icons): {@code <>} element,
     * {@code =} attribute, {@code Abc} text, {@code [..]} CDATA, {@code <!} comment,
     * {@code <?} processing instruction, {@code XML} document.
     */
    private void drawRowIcon(FlatRow row, double x, double cy) {
        FlatRow.RowType type = row.getType();
        gc.setLineWidth(1.5);
        double size = 4;
        double cx = x + ICON_AREA_WIDTH / 2;

        switch (type) {
            case ELEMENT -> {
                // <> angle brackets in the element colour
                gc.setStroke(TEXT_ELEMENT);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
            }
            case ATTRIBUTE -> iconGlyph("=", cx, cy, TEXT_ATTRIBUTE_NAME, ICON_FONT);
            case TEXT -> iconGlyph("Abc", cx, cy, TEXT_CONTENT, SMALL_FONT);
            case COMMENT -> iconGlyph("<!", cx, cy, TEXT_COMMENT, ICON_FONT);
            case CDATA -> {
                // [ ] square brackets
                gc.setStroke(TEXT_CDATA);
                gc.strokeLine(cx - size, cy - size, cx - size + 2, cy - size);
                gc.strokeLine(cx - size, cy - size, cx - size, cy + size);
                gc.strokeLine(cx - size, cy + size, cx - size + 2, cy + size);
                gc.strokeLine(cx + size, cy - size, cx + size - 2, cy - size);
                gc.strokeLine(cx + size, cy - size, cx + size, cy + size);
                gc.strokeLine(cx + size, cy + size, cx + size - 2, cy + size);
            }
            case PROCESSING_INSTRUCTION -> iconGlyph("<?", cx, cy, TEXT_PI, ICON_FONT);
            case DOCUMENT -> iconGlyph("XML", cx, cy, TEXT_SECONDARY, SMALL_FONT);
            case JSON_OBJECT, JSON_ARRAY, JSON_STRING, JSON_NUMBER, JSON_BOOLEAN, JSON_NULL -> {
                // JSON glyphs: {} [] " # T/F ∅ in the type colour
                Color color = (type == FlatRow.RowType.JSON_OBJECT || type == FlatRow.RowType.JSON_ARRAY)
                        ? TEXT_ELEMENT : getRowValueColor(type);
                iconGlyph(jsonGlyph(row), cx, cy, color, ICON_FONT);
            }
            default -> { /* other row types draw no icon */ }
        }
    }

    /** Draws a centred text glyph as a row icon. */
    private void iconGlyph(String glyph, double cx, double cy, Color color, Font font) {
        gc.setFont(font);
        gc.setFill(color);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(glyph, cx, cy);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** The icon glyph of a JSON row: {@code {}} object, {@code []} array, {@code "} string,
     *  {@code #} number, {@code T}/{@code F} boolean, {@code ∅} null. */
    private static String jsonGlyph(FlatRow row) {
        return switch (row.getType()) {
            case JSON_OBJECT -> "{}";
            case JSON_ARRAY -> "[]";
            case JSON_STRING -> "\"";
            case JSON_NUMBER -> "#";
            case JSON_BOOLEAN -> "true".equals(row.getValue()) ? "T" : "F";
            case JSON_NULL -> "∅";
            default -> "";
        };
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

    private void drawEmptyState() {
        gc.setFill(TEXT_SECONDARY);
        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(adapter.emptyStateText(), canvas.getWidth() / 2, canvas.getHeight() / 2);
    }

    private void drawInfo() {
        if (visibleRows.isEmpty()) {
            return;
        }

        String info = String.format("%d rows (%.0f x %.0f px)", visibleRows.size(), totalWidth, totalHeight);

        gc.setFill(TEXT_SECONDARY);
        gc.setFont(SMALL_FONT);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.fillText(info, canvas.getWidth() - 5, canvas.getHeight() - 3);
    }

    // ==================== Row Index Lookup ====================

    /**
     * Finds the visible row index at an absolute Y coordinate (in document space).
     * Uses binary search on rowYPositions for efficiency.
     *
     * @param absoluteY the Y coordinate in document space (includes scroll offset)
     * @return the visible row index, or -1 if outside bounds
     */
    private int findRowIndexAtY(double absoluteY) {
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

    // ==================== Table Rendering ====================

    /**
     * Renders an inline repeating-elements table beside its owner row's name cell, through the
     * table's drawn grid: drawn row 0 is the column-header row (corner cell + headers), the
     * following drawn rows are the data rows (row number + cells).
     *
     * @param tableRow the FlatRow that owns the table
     * @param tableY   the Y coordinate where the table starts (screen coordinates)
     */
    private void renderInlineTable(FlatRow tableRow, double tableY) {
        RepeatingElementsTable table = tableRow.getRepeatingTable();
        if (table == null) {
            return;
        }

        double tableX = table.getX();
        double tableWidth = table.getWidth();
        double tableHeight = table.getHeight();
        int drawnCols = table.drawnColumnCount();

        // -- Header row: corner cell + one header cell per column --
        double headerTop = tableY;
        gc.setTextBaseline(VPos.CENTER);
        for (int dc = 0; dc < drawnCols; dc++) {
            double colX = table.drawnColumnX(dc);
            double colWidth = table.drawnColumnWidth(dc);
            RepeatingElementsTable.DrawnCell cell = table.cellAt(0, dc);
            RepeatingElementsTable.TableColumn col = cell.columnIndex() >= 0 ? table.getColumn(cell.columnIndex()) : null;
            boolean sorted = col != null && table.isSortedBy(col.getName());

            gc.setFill(sorted ? TABLE_HEADER_SELECTED : TABLE_HEADER_BG);
            gc.fillRect(colX, headerTop, colWidth, ROW_HEIGHT);
            gc.setStroke(TABLE_BORDER);
            gc.setLineWidth(1);
            gc.strokeRect(colX + 0.5, headerTop + 0.5, colWidth, ROW_HEIGHT);

            gc.save();
            gc.beginPath();
            gc.rect(colX, headerTop, colWidth, ROW_HEIGHT);
            gc.clip();
            double centerY = headerTop + ROW_HEIGHT / 2;
            if (col == null) {
                // Corner cell: the flip glyph
                iconGlyph("⇄", colX + colWidth / 2, centerY, TABLE_HEADER_TEXT, ICON_FONT);
            } else {
                drawColumnTypeIcon(col, colX + RepeatingElementsTable.CELL_PADDING + 6, centerY);
                gc.setFill(TABLE_HEADER_TEXT);
                gc.setFont(ROW_FONT_BOLD);
                gc.setTextAlign(TextAlignment.LEFT);
                String name = col.getName();
                if (sorted) {
                    name += table.isSortAscending() ? " ▲" : " ▼";
                }
                gc.fillText(name, colX + RepeatingElementsTable.CELL_PADDING + HEADER_ICON_WIDTH, centerY);
            }
            gc.restore();
        }

        // -- Data rows (cached row tops; only the rows inside the viewport are drawn) --
        double dataY = tableY + ROW_HEIGHT;
        int rowCount = table.getRows().size();
        int firstRow = dataY < 0 ? table.getRowIndexAtDataOffset(-dataY) : 0;
        if (firstRow < 0) {
            firstRow = 0;
        }
        int lastRow = table.getRowIndexAtDataOffset(viewportHeightModel() - dataY);
        if (lastRow < 0) {
            lastRow = rowCount - 1;
        }
        for (int r = firstRow; r <= lastRow && r < rowCount; r++) {
            RepeatingElementsTable.TableRow row = table.getRows().get(r);
            double rowTop = dataY + table.getRowTop(r);
            double rowHeight = table.calculateRowHeight(row);

            // Row background (selection / hover over the whole row)
            Color rowBg = null;
            if (r == table.getSelectedRowIndex()) {
                rowBg = TABLE_ROW_SELECTED;
            } else if (r == hoveredTableRowIndex && table == hoveredTable) {
                rowBg = TABLE_ROW_HOVER;
            }
            if (rowBg != null) {
                gc.setFill(rowBg);
                gc.fillRect(tableX, rowTop, tableWidth, rowHeight);
            }

            // Row number cell
            double numWidth = table.drawnColumnWidth(0);
            gc.setFill(r == table.getSelectedRowIndex() ? TABLE_HEADER_SELECTED : TABLE_HEADER_BG);
            gc.fillRect(tableX, rowTop, numWidth, rowHeight);
            gc.setStroke(TABLE_BORDER);
            gc.setLineWidth(1);
            gc.strokeRect(tableX + 0.5, rowTop + 0.5, numWidth, rowHeight);
            gc.setFont(SMALL_FONT);
            gc.setFill(TABLE_HEADER_TEXT);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(String.valueOf(r + 1), tableX + numWidth - RepeatingElementsTable.CELL_PADDING,
                    rowTop + ROW_HEIGHT / 2);
            gc.setTextAlign(TextAlignment.LEFT);

            // Cell values (summary line is always at the top of the cell)
            double cellCenterY = rowTop + ROW_HEIGHT / 2;
            for (int c = 0; c < table.getColumns().size(); c++) {
                RepeatingElementsTable.TableColumn col = table.getColumns().get(c);
                double cellX = table.drawnColumnX(c + 1);
                double colWidth = col.getWidth();
                String colName = col.getName();
                boolean isComplex = row.hasComplexChild(colName);
                boolean missing = !row.getValues().containsKey(colName) && !isComplex;

                if (missing && rowBg == null) {
                    gc.setFill(TABLE_MISSING_BG);
                    gc.fillRect(cellX, rowTop, colWidth, rowHeight);
                }
                gc.setStroke(TABLE_BORDER);
                gc.setLineWidth(1);
                gc.strokeRect(cellX + 0.5, rowTop + 0.5, colWidth, rowHeight);

                // Draw expand arrow for complex cells (Canvas-drawn polygon)
                if (isComplex) {
                    double arrowX = cellX + 8;
                    double arrowY = cellCenterY;
                    double arrowSize = 3;
                    gc.setFill(TEXT_SECONDARY);
                    if (row.isColumnExpanded(colName)) {
                        gc.fillPolygon(
                                new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                                new double[]{arrowY - arrowSize / 2, arrowY + arrowSize, arrowY - arrowSize / 2},
                                3);
                    } else {
                        gc.fillPolygon(
                                new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                                new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                                3);
                    }
                }

                // Cell value (wrapped; offset for the expand arrow on complex cells)
                RepeatingElementsTable.CellLayout layout = table.getCellLayout(row, colName);
                gc.setFont(ROW_FONT);
                gc.setTextAlign(TextAlignment.LEFT);

                double textOffsetX = isComplex ? GridMetrics.COMPLEX_ARROW_OFFSET : 0;
                double textX = cellX + RepeatingElementsTable.CELL_PADDING + textOffsetX;

                // Clip to the cell: nothing (a user-narrowed column, an over-wide line) may
                // ever run into the neighbouring cell.
                gc.save();
                gc.beginPath();
                gc.rect(cellX, rowTop, colWidth, rowHeight);
                gc.clip();

                if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                    gc.setFill(TEXT_ATTRIBUTE_VALUE);
                } else if (isComplex) {
                    gc.setFill(TEXT_SECONDARY);
                } else {
                    gc.setFill(TEXT_CONTENT);
                }
                drawTextBlock(layout.summary(), textX, cellCenterY);

                // The cell element's own attributes (display-only, e.g. ccy=EUR) in the
                // attribute color, right after the value or wrapped on their own lines.
                if (layout.suffix() != null) {
                    gc.setFill(TEXT_ATTRIBUTE_NAME);
                    double lastLineY = cellCenterY + (layout.summary().lineCount() - 1) * LINE_HEIGHT;
                    if (layout.suffixOnNewLine()) {
                        drawTextBlock(layout.suffix(), textX, lastLineY + LINE_HEIGHT);
                    } else {
                        gc.fillText(layout.suffix().lastLine(), textX + layout.suffixX(), lastLineY);
                    }
                }

                // Draw expanded cell content as a full tree (sub-rows below the summary block)
                if (row.isColumnExpanded(colName)) {
                    renderCellTree(layout, cellX, rowTop + layout.summaryHeight(), colWidth);
                }

                gc.restore();
            }
        }

        // -- Table outer border --
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(tableX + 0.5, tableY + 0.5, tableWidth, tableHeight);
    }

    /** Width reserved for the type icon in a column header. */
    private static final double HEADER_ICON_WIDTH = RepeatingElementsTable.HEADER_ICON_WIDTH;

    /** Draws the {@code =} / {@code <>} / {@code Abc} type icon of a column header. */
    private void drawColumnTypeIcon(RepeatingElementsTable.TableColumn col, double cx, double cy) {
        switch (col.getType()) {
            case ATTRIBUTE -> iconGlyph("=", cx, cy, TEXT_ATTRIBUTE_NAME, ICON_FONT);
            case TEXT_CONTENT -> iconGlyph("Abc", cx, cy, TEXT_CONTENT, SMALL_FONT);
            case CHILD_ELEMENT -> {
                double size = 3.5;
                gc.setStroke(TEXT_ELEMENT);
                gc.setLineWidth(1.5);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
            }
        }
    }

    // ==================== Cell Tree Rendering ====================

    /**
     * Renders a full tree view within an expanded cell's content area.
     * Supports recursive expand/collapse, expand indicators, tree lines,
     * icons, labels, wrapped values, and child counts -- the same behavior as
     * the main tree view but rendered within cell bounds.
     *
     * @param layout    the cell's measured layout (visible sub-rows, tops, value blocks)
     * @param cellX     the X coordinate of the cell's left edge
     * @param cellY     the Y coordinate where the sub-rows start (below the summary block)
     * @param cellWidth the width of the cell
     */
    private void renderCellTree(RepeatingElementsTable.CellLayout layout, double cellX, double cellY,
                                double cellWidth) {
        List<FlatRow> visibleCellRows = layout.visibleSubRows();
        if (visibleCellRows.isEmpty()) {
            return;
        }

        double cellPadding = RepeatingElementsTable.CELL_PADDING;
        double cellNameColWidth = layout.nameColumnWidth();

        // Draw tree connection lines within cell
        drawCellTreeLines(layout, cellX + cellPadding, cellY);

        // Draw each visible sub-row
        for (int i = 0; i < visibleCellRows.size(); i++) {
            FlatRow row = visibleCellRows.get(i);
            double rowY = cellY + layout.subRowTops()[i];
            double rowHeight = layout.subRowHeight(i);
            double rowCenterY = rowY + ROW_HEIGHT / 2;

            double contentX = cellX + cellPadding + row.getDepth() * INDENT;

            // Draw expand indicator for expandable elements
            if (row.isExpandable()) {
                drawCellExpandIndicator(row, contentX, rowY);
            }

            // Draw icon
            double iconX = contentX + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);
            drawRowIcon(row, iconX, rowCenterY);

            // Draw label (never truncated: the column reserves the full name portion)
            double labelX = iconX + ICON_AREA_WIDTH;
            gc.setFont(ROW_FONT);
            gc.setFill(getRowLabelColor(row.getType()));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);
            if (row.getLabel() != null) {
                gc.fillText(row.getLabel(), labelX, rowCenterY);
            }

            // Draw child count for expandable elements
            if (row.isExpandable()) {
                gc.setFont(SMALL_FONT);
                gc.setFill(TEXT_SECONDARY);
                double afterLabel = labelX + (row.getLabel() != null ? metrics.width(row.getLabel(), GridFont.ROW) : 0)
                        + GridMetrics.CHILD_COUNT_GAP;
                gc.fillText("(" + row.getChildCount() + ")", afterLabel, rowCenterY);
            }

            // Draw value (wrapped)
            double valueX = cellX + cellPadding + cellNameColWidth;
            if (row.getValue() != null) {
                gc.setFont(ROW_FONT);
                gc.setFill(getRowValueColor(row.getType()));
                drawTextBlock(layout.subRowBlocks().get(i), valueX, rowCenterY);
            }

            // Row separator
            gc.setStroke(ROW_SEPARATOR);
            gc.setLineWidth(0.3);
            gc.strokeLine(cellX + cellPadding, rowY + rowHeight, cellX + cellWidth - cellPadding, rowY + rowHeight);
        }
    }

    /**
     * Draws an expand/collapse indicator (triangle) for an expandable sub-row within a cell.
     *
     * @param row the expandable row
     * @param x   the X coordinate where the expand indicator starts
     * @param y   the Y coordinate of the row's top edge
     */
    private void drawCellExpandIndicator(FlatRow row, double x, double y) {
        double arrowX = x + EXPAND_BAR_WIDTH / 2;
        double arrowY = y + RepeatingElementsTable.ROW_HEIGHT / 2;
        double arrowSize = 3;
        gc.setFill(EXPAND_BAR_ARROW);

        if (row.isExpanded()) {
            // Down-pointing triangle (expanded)
            gc.fillPolygon(
                    new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                    new double[]{arrowY - arrowSize / 2, arrowY + arrowSize, arrowY - arrowSize / 2},
                    3);
        } else {
            // Right-pointing triangle (collapsed)
            gc.fillPolygon(
                    new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                    new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                    3);
        }
    }

    /**
     * Draws tree connection lines for visible sub-rows within a cell.
     * Connects child rows to their parent with horizontal branch lines.
     *
     * @param layout the cell layout (visible sub-rows and their tops)
     * @param baseX  the X offset for tree line calculations
     * @param baseY  the Y coordinate where the first sub-row starts
     */
    private void drawCellTreeLines(RepeatingElementsTable.CellLayout layout, double baseX, double baseY) {
        gc.setStroke(TREE_LINE_COLOR);
        gc.setLineWidth(0.5);

        List<FlatRow> visibleCellRows = layout.visibleSubRows();
        for (int i = 0; i < visibleCellRows.size(); i++) {
            FlatRow row = visibleCellRows.get(i);
            FlatRow parent = row.getParentRow();
            if (parent == null || parent.getDepth() < 0) {
                continue; // Skip rows whose parent is the virtual root
            }

            double rowY = baseY + layout.subRowTops()[i] + ROW_HEIGHT / 2;
            double parentBarX = baseX + parent.getDepth() * INDENT + EXPAND_BAR_WIDTH / 2;
            double iconX = baseX + row.getDepth() * INDENT
                    + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);

            // Horizontal branch from parent bar to child icon
            gc.strokeLine(parentBarX, rowY, iconX, rowY);
        }
    }

    // ==================== Table Interaction ====================

    /**
     * Handles a mouse click inside an inline repeating table, resolved through the table's
     * drawn grid (corner, column header, row number or data cell).
     */
    private void handleTableClick(MouseEvent event, RepeatingElementsTable table,
                                   double mx, double my, double tableScreenTop, FlatRow tableOwner) {
        int dr = table.drawnRowAt(my - tableScreenTop);
        if (dr == 0) {
            // A resize handle in the column-header row (the last column's lies on the table's
            // right edge, outside every drawn column): double-click returns to automatic width
            int sepIdx = table.getColumnSeparatorAt(mx, RESIZE_HANDLE);
            if (sepIdx >= 0) {
                if (event.getClickCount() == 2) {
                    table.setColumnUserWidth(table.getColumn(sepIdx).getName(), null);
                    recalculateVisibleRows();
                    updateScrollBars();
                    render();
                }
                return;
            }
        }
        int dc = table.drawnColumnAt(mx);
        if (dr < 0 || dc < 0) {
            return;
        }
        RepeatingElementsTable.DrawnCell cell = table.cellAt(dr, dc);

        switch (cell.kind()) {
            case CORNER -> {
                // The flip toggle (rows ↔ columns) lands here
                return;
            }
            case COLUMN_HEADER -> {
                // Column header: sort (single click only)
                RepeatingElementsTable.TableColumn col = table.getColumn(cell.columnIndex());
                if (col != null && event.getClickCount() == 1 && table.isColumnSortable(col.getName())) {
                    handleTableSort(table, col.getName(), tableOwner);
                }
                return;
            }
            case ROW_NUMBER -> {
                selectTableRow(table, cell.rowIndex());
                render();
                return;
            }
            case DATA -> { /* handled below */ }
        }

        int rowIdx = cell.rowIndex();
        if (rowIdx < 0 || rowIdx >= table.getRows().size()) {
            return;
        }
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIdx);
        RepeatingElementsTable.TableColumn col = table.getColumn(cell.columnIndex());
        if (col == null) {
            return;
        }
        String colName = col.getName();
        double cellLeft = table.getColumnX(colName);

        // Click on a complex cell's expand arrow (first ~16px of the cell)
        if (event.getClickCount() == 1 && row.hasComplexChild(colName)
                && mx >= cellLeft && mx <= cellLeft + 16) {
            row.toggleColumnExpanded(colName);
            table.recalculateColumnWidths();
            recalculateVisibleRows();
            updateScrollBars();
            render();
            return;
        }

        // Click on a sub-row's expand indicator inside an expanded cell
        if (event.getClickCount() == 1 && row.isColumnExpanded(colName)) {
            List<FlatRow> cellRows = row.getExpandedCellRows(colName);
            RepeatingElementsTable.CellLayout layout = table.getCellLayout(row, colName);
            double rowTopY = tableScreenTop + table.drawnRowTop(dr);
            double subRowStartY = rowTopY + layout.summaryHeight();
            double cellPad = RepeatingElementsTable.CELL_PADDING;

            int subRowIdx = layout.subRowIndexAt(my - subRowStartY);
            if (subRowIdx >= 0) {
                FlatRow subRow = layout.visibleSubRows().get(subRowIdx);
                if (subRow.isExpandable()) {
                    double subContentX = cellLeft + cellPad + subRow.getDepth() * INDENT;
                    if (mx >= subContentX && mx <= subContentX + EXPAND_BAR_WIDTH) {
                        FlatRow.toggleExpand(subRow, cellRows);
                        table.recalculateColumnWidths();
                        recalculateVisibleRows();
                        updateScrollBars();
                        render();
                        return;
                    }
                }
            }
        }

        selectTableRow(table, rowIdx);

        // Double-click to edit cell
        if (event.getClickCount() == 2) {
            startEditingTableCell(table, rowIdx, colName, tableScreenTop);
        }

        render();
    }

    /** Selects a table row (clearing the tree-row selection) and tells the selection model. */
    private void selectTableRow(RepeatingElementsTable table, int rowIdx) {
        if (rowIdx < 0 || rowIdx >= table.getRows().size()) {
            return;
        }
        if (selectedTable != null && selectedTable != table) {
            selectedTable.setSelectedRowIndex(-1);
        }
        table.setSelectedRowIndex(rowIdx);
        selectedTable = table;

        // Clear normal row selection (visual only, to avoid dual highlight)
        if (selectedRow != null) {
            selectedRow.setSelected(false);
            selectedRow = null;
        }

        // Notify SelectionModel so sidebar properties pane updates
        adapter.select(adapter.nodeOf(table.getRows().get(rowIdx)));
    }

    /**
     * Handles sorting of a table column.
     * Toggles sort direction if clicking the same column again.
     */
    private void handleTableSort(RepeatingElementsTable table, String columnName, FlatRow tableOwner) {
        // Toggle direction if already sorted by this column
        boolean ascending;
        if (table.isSortedBy(columnName)) {
            ascending = !table.isSortAscending();
        } else {
            ascending = true;
        }

        adapter.sortTable(table, columnName, ascending);

        rebuildTree();
        notifyDocumentModified();
    }

    /**
     * Updates table hover state.
     */
    private void updateTableHover(RepeatingElementsTable table, double mx, double my, double tableScreenTop) {
        if (hoveredTable != table) {
            if (hoveredTable != null) {
                hoveredTable.setHoveredRowIndex(-1);
                hoveredTable.setHoveredColumnIndex(-1);
                hoveredTable.setHovered(false);
            }
            hoveredTable = table;
            table.setHovered(true);
        }

        int dr = table.drawnRowAt(my - tableScreenTop);
        int dc = table.drawnColumnAt(mx);
        int rowIdx = -1;
        int colIdx = -1;
        if (dr >= 0 && dc >= 0) {
            RepeatingElementsTable.DrawnCell cell = table.cellAt(dr, dc);
            rowIdx = cell.rowIndex();
            colIdx = cell.columnIndex();
        }

        if (rowIdx != hoveredTableRowIndex || colIdx != hoveredTableColumnIndex) {
            hoveredTableRowIndex = rowIdx;
            hoveredTableColumnIndex = colIdx;
            table.setHoveredRowIndex(rowIdx);
            table.setHoveredColumnIndex(colIdx);
            render();
        }
    }

    /**
     * Starts editing a table cell.
     */
    private void startEditingTableCell(RepeatingElementsTable table, int rowIndex, String columnName,
                                        double tableScreenTop) {
        cancelEditing();

        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        RepeatingElementsTable.TableColumn col = table.getColumn(columnName);
        if (col == null) {
            return;
        }

        // Don't edit complex children
        if (row.hasComplexChild(columnName)) {
            return;
        }

        editingTable = table;
        editingTableRowIndex = rowIndex;
        editingTableColumnName = columnName;

        String currentValue = row.getValue(columnName);

        // Calculate cell position (using the table's cached row tops)
        double cellX = table.getColumnX(columnName) - scrollOffsetX;
        double cellY = tableScreenTop + ROW_HEIGHT + table.getRowTop(rowIndex);
        double cellWidth = col.getWidth();

        GridModelAdapter.EditSpec spec = adapter.cellEditSpec(table, rowIndex, columnName);
        createEditField(spec != null ? spec : GridModelAdapter.EditSpec.plain(currentValue),
                cellX, cellY, cellWidth);
    }

    // ==================== Color Helpers ====================

    private Color getRowLabelColor(FlatRow.RowType type) {
        return switch (type) {
            case ELEMENT, JSON_OBJECT, JSON_ARRAY, JSON_STRING, JSON_NUMBER, JSON_BOOLEAN, JSON_NULL -> TEXT_ELEMENT;
            case ATTRIBUTE -> TEXT_ATTRIBUTE_NAME;
            case TEXT -> TEXT_CONTENT;
            case COMMENT -> TEXT_COMMENT;
            case CDATA -> TEXT_CDATA;
            case PROCESSING_INSTRUCTION -> TEXT_PI;
            case DOCUMENT -> TEXT_SECONDARY;
        };
    }

    private Color getRowValueColor(FlatRow.RowType type) {
        return switch (type) {
            case ATTRIBUTE -> TEXT_ATTRIBUTE_VALUE;
            case ELEMENT -> TEXT_CONTENT;
            case COMMENT -> TEXT_COMMENT;
            case CDATA -> TEXT_CDATA;
            case PROCESSING_INSTRUCTION -> TEXT_PI;
            case JSON_STRING -> TEXT_JSON_STRING;
            case JSON_NUMBER -> TEXT_JSON_NUMBER;
            case JSON_BOOLEAN -> TEXT_JSON_BOOLEAN;
            case JSON_NULL -> TEXT_JSON_NULL;
            default -> TEXT_CONTENT;
        };
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
            if (needsRedraw) {
                render();
            }
        });

        // Mouse wheel scrolling (vertical and horizontal)
        canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            // Ctrl + wheel zooms (like the text editor and the XSD diagram) — but never while an
            // inline edit is running: zooming cancels the editor and would drop the typed text.
            if (e.isControlDown() || e.isShortcutDown()) {
                if (editField != null || activeWidgetNode != null) {
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
            if (!canvas.isFocused() && editField == null && activeWidgetNode == null) {
                canvas.requestFocus();
            }
        });

        // Column resizing: press on a separator in a column-header row, drag, release
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || editField != null || activeWidgetNode != null) {
                return;
            }
            ColumnDrag handle = findColumnHandleAt(toModelX(e.getX()), toViewportY(e.getY()));
            if (handle != null) {
                // Anchor on the SCREEN x: narrowing a column can shrink the content width and
                // pull the horizontal scroll offset back, which must not feed into the delta.
                columnDrag = new ColumnDrag(handle.table(), handle.columnIndex(), e.getX(), handle.startWidth());
                columnDragMoved = false;
                e.consume();
            }
        });
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (columnDrag == null) {
                return;
            }
            double delta = (e.getX() - columnDrag.startScreenX()) / getZoom();
            double newWidth = Math.max(RepeatingElementsTable.MIN_COLUMN_WIDTH, columnDrag.startWidth() + delta);
            RepeatingElementsTable.TableColumn col = columnDrag.table().getColumn(columnDrag.columnIndex());
            if (col != null && Math.abs(col.getWidth() - newWidth) >= 0.5) {
                columnDrag.table().setColumnUserWidth(col.getName(), newWidth);
                columnDragMoved = true;
                recalculateVisibleRows();
                updateScrollBars();
                render();
            }
            canvas.setCursor(Cursor.H_RESIZE);
            e.consume();
        });
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (columnDrag != null) {
                suppressNextClick = columnDragMoved;
                columnDrag = null;
                e.consume();
            }
        });
    }

    /**
     * Finds the column separator under the pointer: the pointer must sit in the
     * column-header row of an embedded table, within {@link #RESIZE_HANDLE}
     * pixels of a column's right edge.
     *
     * @param mx x in model units
     * @param my y in unscaled viewport units
     * @return the drag descriptor, or {@code null} when no handle is there
     */
    private ColumnDrag findColumnHandleAt(double mx, double my) {
        int rowIndex = rowIndexAt(mx, my);
        if (rowIndex < 0) {
            return null;
        }
        FlatRow owner = visibleRows.get(rowIndex);
        if (!owner.hasRepeatingTable() || !owner.isExpanded()) {
            return null;
        }
        RepeatingElementsTable table = owner.getRepeatingTable();
        double headerTop = rowYPositions[rowIndex] - scrollOffsetY;
        if (my < headerTop || my >= headerTop + ROW_HEIGHT || mx < table.getX()) {
            return null;
        }
        int colIdx = table.getColumnSeparatorAt(mx, RESIZE_HANDLE);
        if (colIdx < 0) {
            return null;
        }
        return new ColumnDrag(table, colIdx, 0, table.getColumn(colIdx).getWidth());
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
        if (mx >= table.getX() && mx <= table.getX() + table.getWidth() + RESIZE_HANDLE
                && my >= tableTop && my < tableTop + table.getHeight()) {
            return table;
        }
        return null;
    }

    private void handleMouseClick(MouseEvent event) {
        if (suppressNextClick) {
            // The click that JavaFX fires after a column drag must not sort or select.
            suppressNextClick = false;
            return;
        }
        double mx = toModelX(event.getX());
        double my = toViewportY(event.getY());

        if (visibleRows.isEmpty()) {
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
            handleTableClick(event, table, mx, my, rowYPositions[rowIndex] - scrollOffsetY,
                    visibleRows.get(rowIndex));
            return;
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);

        // Check if clicking on the expand arrow
        FlatRow expandBarRow = findExpandBarAt(mx, my);
        if (expandBarRow != null) {
            FlatRow.toggleExpand(expandBarRow, allRows);
            recalculateVisibleRows();
            updateScrollBars();
            selectRow(expandBarRow);
            render();
            return;
        }

        // Double-click handling
        if (event.getClickCount() == 2) {
            if (mx >= clickedRow.getValueX()) {
                // Double-click in value column: edit value
                startEditingValue(clickedRow);
                return;
            } else {
                // Double-click in name column
                if (clickedRow.getType().isContainer() || clickedRow.getType().isScalarLeaf()) {
                    if (clickedRow.isLeafWithValue()) {
                        // Leaf with value: edit the value
                        startEditingValue(clickedRow);
                    } else {
                        // Container: edit its name / key
                        startEditingElementName(clickedRow);
                    }
                    return;
                }
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

        selectRow(clickedRow);
    }

    private void handleContextMenu(MouseEvent event, double mx, double my) {
        int rowIndex = rowIndexAt(mx, my);
        if (rowIndex < 0) {
            return;
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);
        selectRow(clickedRow);

        // Update selection model
        N node = adapter.nodeOf(clickedRow);
        adapter.select(node);

        // Show context menu
        contextMenu().show(canvas, event.getScreenX(), event.getScreenY(), node);
    }

    private GridContextMenu<N> contextMenu() {
        if (contextMenu == null) {
            contextMenu = adapter.createContextMenu(this::refresh, new GridContextMenu.ViewActions(
                    this::expandAll, this::collapseAll, this::expandSelectedFully, this::collapseUnselected));
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

        // Calculate hovered row
        int rowIndex = rowIndexAt(mx, my);

        // Check table hover state
        RepeatingElementsTable table = tableAt(rowIndex, mx, my);
        boolean tableHovered = table != null;
        if (tableHovered) {
            updateTableHover(table, mx, my, rowYPositions[rowIndex] - scrollOffsetY);
        }

        // Clear table hover if not over a table
        if (!tableHovered && hoveredTable != null) {
            hoveredTable.setHoveredRowIndex(-1);
            hoveredTable.setHoveredColumnIndex(-1);
            hoveredTable.setHovered(false);
            hoveredTable = null;
            hoveredTableRowIndex = -1;
            hoveredTableColumnIndex = -1;
            needsRedraw = true;
        }

        FlatRow newHoveredRow = null;
        if (!tableHovered && rowIndex >= 0) {
            newHoveredRow = visibleRows.get(rowIndex);
        }

        // Update hovered row
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

        // Update hovered expand arrow
        FlatRow newHoveredExpandBar = findExpandBarAt(mx, my);
        if (newHoveredExpandBar != hoveredExpandBar) {
            hoveredExpandBar = newHoveredExpandBar;
            needsRedraw = true;
        }

        // Preview tooltip over the value cell of a collapsed container
        updatePreviewTooltip(rowIndex, mx, my);

        // Update cursor
        if (columnDrag != null || findColumnHandleAt(mx, my) != null) {
            canvas.setCursor(Cursor.H_RESIZE);
        } else if (hoveredExpandBar != null) {
            canvas.setCursor(Cursor.HAND);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }

        if (needsRedraw) {
            render();
        }
    }

    /**
     * Shows the full (longer) preview of a collapsed container as a tooltip while the pointer
     * is over its value cell; removes the tooltip elsewhere.
     */
    private void updatePreviewTooltip(int rowIndex, double mx, double my) {
        FlatRow target = null;
        if (rowIndex >= 0) {
            FlatRow row = visibleRows.get(rowIndex);
            double top = rowYPositions[rowIndex] - scrollOffsetY;
            if (showsPreview(row) && mx >= row.getValueX() && my >= top && my < top + ROW_HEIGHT) {
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
        String full = adapter.collapsedPreview(target, TOOLTIP_MAX_CHARS);
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
        if (editField != null || activeWidgetNode != null) {
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
                    FlatRow.toggleExpand(selectedRow, allRows);
                    recalculateVisibleRows();
                    updateScrollBars();
                    render();
                } else if (selectedRow != null && selectedRow.getParentRow() != null) {
                    // Jump to parent
                    selectRow(selectedRow.getParentRow());
                    ensureRowVisible(selectedRow);
                }
                event.consume();
            }
            case RIGHT -> {
                if (selectedRow != null && selectedRow.isExpandable() && !selectedRow.isExpanded()) {
                    // Expand if collapsed
                    FlatRow.toggleExpand(selectedRow, allRows);
                    recalculateVisibleRows();
                    updateScrollBars();
                    render();
                }
                event.consume();
            }
            case ENTER -> {
                if (selectedRow != null) {
                    if (selectedRow.isExpandable()) {
                        FlatRow.toggleExpand(selectedRow, allRows);
                        recalculateVisibleRows();
                        updateScrollBars();
                        render();
                    } else {
                        // Start editing
                        startEditingElementName(selectedRow);
                    }
                }
                event.consume();
            }
            case SPACE -> {
                // Spacebar toggles the selected container (XMLSpy "Expand/Collapse on spacebar")
                if (selectedRow != null && selectedRow.isExpandable()) {
                    FlatRow.toggleExpand(selectedRow, allRows);
                    recalculateVisibleRows();
                    updateScrollBars();
                    render();
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

        int idx = visibleRows.indexOf(selectedRow);
        if (idx > 0) {
            selectRow(visibleRows.get(idx - 1));
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

        int idx = visibleRows.indexOf(selectedRow);
        if (idx < visibleRows.size() - 1) {
            selectRow(visibleRows.get(idx + 1));
            ensureRowVisible(selectedRow);
        }
    }

    // ==================== Selection ====================

    /**
     * Clears the table selection state.
     * Deselects the currently selected table row and resets the selectedTable reference.
     */
    private void clearTableSelection() {
        if (selectedTable != null) {
            selectedTable.setSelectedRowIndex(-1);
            selectedTable = null;
        }
    }

    private void selectRow(FlatRow row) {
        // Clear any table selection to ensure mutual exclusivity
        clearTableSelection();

        if (selectedRow != null) {
            selectedRow.setSelected(false);
        }

        selectedRow = row;

        if (selectedRow != null) {
            selectedRow.setSelected(true);
            // Update selection model
            adapter.select(adapter.nodeOf(row));
        }

        render();
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

        int idx = visibleRows.indexOf(row);
        if (idx < 0 || idx >= rowYPositions.length) {
            return;
        }

        double viewportH = viewportHeightModel();
        double rowTop = rowYPositions[idx];
        double rowBottom = rowTop + Math.min(Math.max(rowHeights[idx], ROW_HEIGHT), viewportH - 40);
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

    // ==================== Inline Editing ====================

    private void startEditingValue(FlatRow row) {
        cancelEditing();

        if (row == null || !adapter.canEditValue(row)) {
            return;
        }

        editingRow = row;
        editingValue = true;
        editingElementName = false;

        int idx = visibleRows.indexOf(row);
        if (idx < 0) {
            return;
        }

        double x = row.getValueX() - scrollOffsetX;
        double y = rowYPositions[idx] - scrollOffsetY + 2;
        double width = Math.max(viewportWidthModel() - row.getValueX(), MIN_VALUE_COL_WIDTH);

        GridModelAdapter.EditSpec spec = adapter.editSpec(row, false);
        createEditField(spec != null ? spec
                : GridModelAdapter.EditSpec.plain(row.getValue() != null ? row.getValue() : ""), x, y, width);
    }

    private void startEditingElementName(FlatRow row) {
        cancelEditing();

        if (row == null || !adapter.canEditName(row)) {
            return;
        }

        editingRow = row;
        editingValue = false;
        editingElementName = true;

        int idx = visibleRows.indexOf(row);
        if (idx < 0) {
            return;
        }

        double textX = rowTextX(row);
        double labelX = textX - scrollOffsetX;
        double y = rowYPositions[idx] - scrollOffsetY + 2;
        double width = row.getValueX() - textX - 4;

        GridModelAdapter.EditSpec spec = adapter.editSpec(row, true);
        createEditField(spec != null ? spec : GridModelAdapter.EditSpec.plain(row.getLabel()), labelX, y, width);
    }

    /**
     * Installs the inline editor described by the spec: a type-aware widget when the
     * adapter supplied one, otherwise a plain text field (with the spec's tooltip, if any).
     *
     * @param spec  what to edit
     * @param vx    x in unscaled viewport coordinates (scroll applied, zoom not)
     * @param vy    y in unscaled viewport coordinates
     * @param width the field width in unscaled pixels
     */
    private void createEditField(GridModelAdapter.EditSpec spec, double vx, double vy, double width) {
        String currentValue = spec.currentValue();
        // Calculate minimum width based on content
        double contentBasedWidth = metrics.width(currentValue, GridFont.ROW) + 30;
        double z = getZoom();
        double effectiveWidth = Math.max(width, Math.max(contentBasedWidth, 120)) * z;
        double x = vx * z;
        double y = vy * z;
        double fieldHeight = ROW_HEIGHT * z;

        // Type-aware widget supplied by the adapter (schema-driven for XML, type-driven for JSON)
        {
            TypeAwareWidgetFactory.EditWidget widget = spec.widget();

            if (widget != null) {
                activeEditWidget = widget;
                activeWidgetNode = widget.getNode();

                double widgetMinWidth = effectiveWidth;
                if (activeWidgetNode instanceof javafx.scene.control.DatePicker) {
                    widgetMinWidth = Math.max(widgetMinWidth, 160 * z);
                } else if (activeWidgetNode instanceof javafx.scene.control.ComboBox) {
                    widgetMinWidth = Math.max(widgetMinWidth, 150 * z);
                }

                if (activeWidgetNode instanceof javafx.scene.layout.Region region) {
                    region.setLayoutX(x);
                    region.setLayoutY(y);
                    region.setPrefWidth(widgetMinWidth);
                    region.setMinWidth(widgetMinWidth);
                    region.setPrefHeight(fieldHeight);
                    region.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-size: %.1fpx;", 12 * z));
                } else {
                    activeWidgetNode.setLayoutX(x);
                    activeWidgetNode.setLayoutY(y);
                }

                activeWidgetNode.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEditing();
                    } else if (e.getCode() == KeyCode.ENTER) {
                        commitEditing();
                    }
                });

                activeWidgetNode.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused && activeWidgetNode != null && !isCommitting) {
                        javafx.application.Platform.runLater(() -> {
                            if (activeWidgetNode != null && !activeWidgetNode.isFocused()) {
                                commitEditing();
                            }
                        });
                    }
                });

                canvasContainer.getChildren().add(activeWidgetNode);
                widget.focus();
                return;
            }
        }

        // Fallback to standard TextField
        editField = new TextField(currentValue);
        editField.setLayoutX(x);
        editField.setLayoutY(y);
        editField.setPrefWidth(effectiveWidth);
        editField.setMinWidth(effectiveWidth);
        editField.setPrefHeight(fieldHeight);
        editField.setStyle(String.format(java.util.Locale.ROOT,
                "-fx-font-size: %.1fpx; -fx-font-family: 'Segoe UI'; -fx-padding: 2 6;", 12 * z));

        // Add documentation tooltip if available
        if (spec.tooltip() != null) {
            Tooltip tooltip = new Tooltip(spec.tooltip());
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(300);
            Tooltip.install(editField, tooltip);
        }

        editField.setOnAction(e -> commitEditing());
        editField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEditing();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                commitEditing();
                e.consume();
            }
        });

        editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && editField != null && !isCommitting) {
                javafx.application.Platform.runLater(() -> {
                    if (editField != null && !editField.isFocused()) {
                        commitEditing();
                    }
                });
            }
        });

        canvasContainer.getChildren().add(editField);
        editField.requestFocus();
        editField.selectAll();
    }

    private void commitEditing() {
        if (isCommitting) {
            return;
        }
        isCommitting = true;

        try {
            // Get value from either widget or text field
            String newValue;
            if (activeEditWidget != null) {
                newValue = activeEditWidget.getValue();
            } else if (editField != null) {
                newValue = editField.getText();
            } else {
                cancelEditing();
                return;
            }

            // Handle table cell editing (for repeating tables integration)
            if (editingTable != null && editingTableRowIndex >= 0 && editingTableColumnName != null) {
                boolean accepted = adapter.commitCellEdit(editingTable, editingTableRowIndex,
                        editingTableColumnName, newValue);
                if (!accepted) {
                    keepEditorOpen();
                    return;
                }
                cancelEditing();
                rebuildTree();
                notifyDocumentModified();
                return;
            }

            // Handle row editing
            if (editingRow == null) {
                cancelEditing();
                return;
            }

            boolean accepted = adapter.commitRowEdit(editingRow, editingElementName, newValue);
            if (!accepted) {
                keepEditorOpen();
                return;
            }

            cancelEditing();
            rebuildTree();
            notifyDocumentModified();
        } finally {
            isCommitting = false;
        }
    }

    /**
     * The adapter rejected the entered value (and told the user): keep the inline editor
     * open and give it the focus back so the value can be corrected or the edit escaped.
     */
    private void keepEditorOpen() {
        javafx.application.Platform.runLater(() -> {
            if (editField != null) {
                editField.requestFocus();
                editField.selectAll();
            } else if (activeEditWidget != null) {
                activeEditWidget.focus();
            }
        });
    }

    private void cancelEditing() {
        if (editField != null) {
            canvasContainer.getChildren().remove(editField);
            editField = null;
        }
        if (activeWidgetNode != null) {
            canvasContainer.getChildren().remove(activeWidgetNode);
            activeWidgetNode = null;
            activeEditWidget = null;
        }
        editingRow = null;
        editingValue = false;
        editingElementName = false;
        editingTable = null;
        editingTableRowIndex = -1;
        editingTableColumnName = null;
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged() {
        cancelEditing();
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

    /**
     * Notifies the callback that the document was modified.
     */
    private void notifyDocumentModified() {
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

    /**
     * Gives the canvas keyboard focus so arrow-key navigation works immediately
     * (e.g. right after the Graphic view is shown), without a mouse click first.
     */
    public void focusCanvas() {
        canvas.requestFocus();
    }

    /** @return the selected model node (a table row's node while a table row is selected) */
    public N getSelectedNode() {
        if (selectedTable != null) {
            RepeatingElementsTable.TableRow tableRow = selectedTable.getSelectedRow();
            if (tableRow != null) {
                N tableNode = adapter.nodeOf(tableRow);
                if (tableNode != null) {
                    return tableNode;
                }
            }
        }
        return selectedRow != null ? adapter.nodeOf(selectedRow) : null;
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

    // ==================== Layout introspection (tests) ====================

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
        return editField;
    }

    /** @return the horizontal scroll range, 0 when the content fits (test hook) */
    double hScrollBarMax() {
        return hScrollBar.isDisabled() ? 0 : hScrollBar.getMax();
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

        revealRow(searchMatches.get(currentMatchIndex));
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
        if (!searchMatches.isEmpty()) {
            currentMatchIndex = 0;
            revealRow(searchMatches.get(0));
        }
        return searchMatches.size();
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
        searchMatches = XmlCanvasSearch.findMatches(allRows, searchText);
        currentMatchIndex = -1;
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
        while (parent != null) {
            if (parent.isExpandable() && !parent.isExpanded()) {
                parent.setExpanded(true);
            }
            parent = parent.getParentRow();
        }
        recalculateVisibility();
        recalculateVisibleRows();
        updateScrollBars();

        selectRow(row);
        ensureRowVisible(row);
    }
}
