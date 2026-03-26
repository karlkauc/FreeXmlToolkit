package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;

/**
 * Flat row-based XML View (XMLSpy-style).
 *
 * <p>Renders XML as a flat list of rows with:</p>
 * <ul>
 *   <li>One row per element, attribute, text node, comment, etc.</li>
 *   <li>Tree connection lines showing parent-child relationships</li>
 *   <li>Vertical expand/collapse bars for expanded elements</li>
 *   <li>Two-column layout (name | value)</li>
 *   <li>Zebra-striped backgrounds</li>
 *   <li>Icons per row type</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasView extends Pane {

    private static final Logger logger = LogManager.getLogger(XmlCanvasView.class);

    // ==================== Context ====================

    private final XmlEditorContext context;

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

    // ==================== Layout Constants ====================

    private static final double ROW_HEIGHT = 24;
    private static final double INDENT = 20;
    private static final double ICON_WIDTH = 18;
    private static final double ICON_AREA_WIDTH = 24;
    private static final double EXPAND_BAR_WIDTH = 12;
    private static final double SCROLLBAR_WIDTH = 14;
    private static final double LEFT_MARGIN = 8;
    private static final double NAME_VALUE_GAP = 16;
    private static final double MIN_NAME_COL_WIDTH = 120;
    private static final double MIN_VALUE_COL_WIDTH = 100;

    /** Calculated name column width (adapts to content). */
    private double nameColumnWidth = MIN_NAME_COL_WIDTH;

    // ==================== Scroll State ====================

    private double scrollOffsetX = 0;
    private double scrollOffsetY = 0;
    private double totalHeight = 0;
    private double totalWidth = 0;

    // ==================== Selection / Hover State ====================

    private FlatRow selectedRow = null;
    private FlatRow hoveredRow = null;
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

    private final XmlGridContextMenu contextMenu;

    // ==================== Highlight State ====================

    private FlatRow highlightedRow = null;
    private static final Color HIGHLIGHT_COLOR = Color.rgb(254, 240, 138);  // Yellow highlight

    // ==================== Status Bar & Toast ====================

    private StackPane toastContainer;

    // ==================== Document Change Callback ====================

    private java.util.function.Consumer<String> onDocumentModified;

    // ==================== Colors (XMLSpy-style) ====================

    // Row backgrounds
    private static final Color ROW_BG_EVEN = Color.WHITE;
    private static final Color ROW_BG_ODD = Color.rgb(249, 250, 251);

    // Selection/Hover
    private static final Color SELECTED_BG = Color.rgb(239, 246, 255);
    private static final Color SELECTED_BORDER = Color.rgb(59, 130, 246);
    private static final Color HOVERED_BG = Color.rgb(243, 244, 246);

    // Text colors
    private static final Color TEXT_ELEMENT = Color.rgb(37, 99, 235);        // Blue
    private static final Color TEXT_ATTRIBUTE_NAME = Color.rgb(146, 64, 14); // Brown/red
    private static final Color TEXT_ATTRIBUTE_VALUE = Color.rgb(21, 128, 61); // Green
    private static final Color TEXT_CONTENT = Color.rgb(31, 41, 55);         // Dark gray
    private static final Color TEXT_SECONDARY = Color.rgb(107, 114, 128);    // Gray
    private static final Color TEXT_COMMENT = Color.rgb(13, 148, 136);       // Teal
    private static final Color TEXT_CDATA = Color.rgb(107, 114, 128);        // Gray
    private static final Color TEXT_PI = Color.rgb(124, 58, 237);            // Violet

    // Tree lines & expand bars
    private static final Color TREE_LINE_COLOR = Color.rgb(209, 213, 219);
    private static final Color EXPAND_BAR_COLOR = Color.rgb(209, 213, 219);
    private static final Color EXPAND_BAR_HOVER = Color.rgb(156, 163, 175);
    private static final Color EXPAND_BAR_ARROW = Color.rgb(107, 114, 128);

    // Row separator
    private static final Color ROW_SEPARATOR = Color.rgb(229, 231, 235);

    // Child count
    private static final Color CHILD_COUNT_COLOR = Color.rgb(156, 163, 175);

    // Table colors (kept for repeating table integration)
    private static final Color TABLE_HEADER_BG = Color.rgb(236, 253, 245);
    private static final Color TABLE_HEADER_TEXT = Color.rgb(5, 150, 105);
    private static final Color TABLE_BORDER = Color.rgb(167, 243, 208);
    private static final Color TABLE_ROW_EVEN = Color.WHITE;
    private static final Color TABLE_ROW_ODD = Color.rgb(249, 250, 251);
    private static final Color TABLE_ROW_HOVER = Color.rgb(236, 253, 245);
    private static final Color TABLE_ROW_SELECTED = Color.rgb(209, 250, 229);

    // ==================== Fonts ====================

    private static final Font ROW_FONT = Font.font("Monospaced", FontWeight.NORMAL, 12);
    private static final Font ROW_FONT_BOLD = Font.font("Monospaced", FontWeight.SEMI_BOLD, 12);
    private static final Font SMALL_FONT = Font.font("Monospaced", FontWeight.NORMAL, 10);
    private static final Font ICON_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);

    // ==================== Constructor ====================

    public XmlCanvasView(XmlEditorContext context) {
        this.context = context;

        // Create canvas with caching enabled for better rendering performance
        this.canvas = new Canvas(800, 600);
        this.canvas.setCache(true);
        this.canvas.setCacheHint(CacheHint.SPEED);
        this.gc = canvas.getGraphicsContext2D();

        // Create container with caching
        this.canvasContainer = new Pane();
        this.canvasContainer.setCache(true);
        this.canvasContainer.setCacheHint(CacheHint.SPEED);
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

        // Listen for document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);

        // Listen for mixed content detection
        context.addPropertyChangeListener("mixedContentDetected", this::onMixedContentDetected);

        // Create context menu
        contextMenu = new XmlGridContextMenu(context, this::refresh);

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
        double viewportWidth = canvas.getWidth();
        double viewportHeight = canvas.getHeight();

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
        }
    }

    // ==================== Tree Building ====================

    /**
     * Rebuilds the flat row list from the document.
     * Preserves expand/collapse state across rebuilds by matching rows by label+type+depth.
     */
    private void rebuildTree() {
        // Collect expand state before rebuilding
        java.util.Map<String, Boolean> expandState = new java.util.HashMap<>();
        for (FlatRow row : allRows) {
            if (row.isExpandable()) {
                String key = row.getType() + ":" + row.getDepth() + ":" + row.getLabel();
                expandState.put(key, row.isExpanded());
            }
        }

        XmlDocument doc = context.getDocument();
        if (doc == null) {
            allRows = new ArrayList<>();
            visibleRows = new ArrayList<>();
            totalHeight = 0;
            totalWidth = 0;
            updateScrollBars();
            render();
            return;
        }

        allRows = FlatRow.flatten(doc);
        attachRepeatingTables();

        // Restore expand state
        if (!expandState.isEmpty()) {
            for (FlatRow row : allRows) {
                if (row.isExpandable()) {
                    String key = row.getType() + ":" + row.getDepth() + ":" + row.getLabel();
                    Boolean wasExpanded = expandState.get(key);
                    if (wasExpanded != null) {
                        row.setExpanded(wasExpanded);
                    }
                }
            }
            // Re-apply visibility based on restored expand state
            recalculateVisibility();
        }

        recalculateVisibleRows();
        updateScrollBars();
        render();
    }

    /**
     * Attaches RepeatingElementsTable instances to FlatRows that represent
     * repeating element groups (2+ siblings with the same tag name).
     */
    private void attachRepeatingTables() {
        for (FlatRow row : allRows) {
            if (row.getType() != FlatRow.RowType.ELEMENT) {
                continue;
            }
            if (row.getParentRow() == null) {
                continue;
            }

            XmlNode parentModel = row.getParentRow().getModelNode();
            if (!(parentModel instanceof XmlElement parentElement)) {
                continue;
            }

            // Check if this element name appears multiple times under parent
            List<XmlElement> sameNameSiblings = new ArrayList<>();
            for (XmlNode sibling : parentElement.getChildren()) {
                if (sibling instanceof XmlElement se && se.getName().equals(row.getLabel())) {
                    sameNameSiblings.add(se);
                }
            }

            if (sameNameSiblings.size() >= 2) {
                RepeatingElementsTable table = new RepeatingElementsTable(
                        row.getLabel(), sameNameSiblings, row.getDepth(), this::markLayoutDirty);
                row.setRepeatingTable(table);
            }
        }
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
     * A row is visible if all its ancestors are expanded.
     */
    private void recalculateVisibility() {
        for (FlatRow row : allRows) {
            if (row.getParentRow() == null) {
                row.setVisible(true);
            } else {
                row.setVisible(isAncestorChainExpanded(row));
            }
        }
    }

    /**
     * Checks whether all ancestors of a row are expanded (meaning the row should be visible).
     */
    private boolean isAncestorChainExpanded(FlatRow row) {
        FlatRow parent = row.getParentRow();
        while (parent != null) {
            if (!parent.isExpanded()) {
                return false;
            }
            parent = parent.getParentRow();
        }
        return true;
    }

    /**
     * Filters allRows by visibility, caches result in visibleRows,
     * and recalculates nameColumnWidth, Y positions, and total dimensions.
     */
    private void recalculateVisibleRows() {
        visibleRows = new ArrayList<>();
        for (FlatRow row : allRows) {
            if (row.isVisible()) {
                visibleRows.add(row);
            }
        }

        // Calculate name column width based on content
        double maxNameWidth = MIN_NAME_COL_WIDTH;
        for (FlatRow row : visibleRows) {
            if (row.getLabel() != null) {
                double labelWidth = getRowLabelX(row) + row.getLabel().length() * 7.2 + NAME_VALUE_GAP;
                maxNameWidth = Math.max(maxNameWidth, labelWidth);
            }
        }
        nameColumnWidth = maxNameWidth;

        // Calculate Y positions and total dimensions accounting for expanded tables
        rowYPositions = new double[visibleRows.size()];
        double currentY = 0;
        double maxWidth = nameColumnWidth + MIN_VALUE_COL_WIDTH + LEFT_MARGIN;

        for (int i = 0; i < visibleRows.size(); i++) {
            rowYPositions[i] = currentY;
            currentY += ROW_HEIGHT;

            // If this row has an expanded repeating table, add table height
            FlatRow row = visibleRows.get(i);
            if (row.hasRepeatingTable() && row.isExpanded()) {
                RepeatingElementsTable table = row.getRepeatingTable();
                table.calculateWidth(canvas.getWidth());
                table.calculateHeight();
                currentY += table.getHeight();

                // Track table width for horizontal scroll
                double tableRight = getRowLabelX(row) + table.getWidth();
                maxWidth = Math.max(maxWidth, tableRight);
            }
        }

        totalHeight = currentY;
        totalWidth = Math.max(maxWidth, canvas.getWidth());
    }

    /**
     * Calculates the X position where the label text starts for a given row.
     */
    private double getRowLabelX(FlatRow row) {
        return LEFT_MARGIN + (row.getDepth() + 1) * INDENT + ICON_AREA_WIDTH;
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
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);

        if (visibleRows.isEmpty()) {
            drawEmptyState();
            return;
        }

        // Calculate visible row range using Y positions
        int firstVisible = findRowIndexAtY(scrollOffsetY);
        int lastVisible = findRowIndexAtY(scrollOffsetY + h);
        if (firstVisible < 0) firstVisible = 0;
        if (lastVisible < 0 || lastVisible >= visibleRows.size()) lastVisible = visibleRows.size() - 1;

        // Save and translate for scroll offset
        gc.save();
        gc.translate(-scrollOffsetX, 0);

        // Draw tree lines first (behind everything)
        drawTreeLines(firstVisible, lastVisible);

        // Draw each visible row
        for (int i = firstVisible; i <= lastVisible; i++) {
            drawRow(visibleRows.get(i), i);
        }

        // Draw inline tables for expanded repeating element rows
        for (int i = firstVisible; i <= lastVisible; i++) {
            FlatRow row = visibleRows.get(i);
            if (row.hasRepeatingTable() && row.isExpanded()) {
                double rowY = rowYPositions[i] - scrollOffsetY;
                double tableY = rowY + ROW_HEIGHT; // Table starts below the header row
                renderInlineTable(row, tableY);
            }
        }

        // Draw expand bars on top
        drawExpandBars(firstVisible, lastVisible);

        gc.restore();

        // Draw info overlay (not scrolled)
        drawInfo();
    }

    /**
     * Draws a single flat row.
     */
    private void drawRow(FlatRow row, int visibleIndex) {
        double y = rowYPositions[visibleIndex] - scrollOffsetY;
        double w = Math.max(totalWidth, canvas.getWidth() + scrollOffsetX);

        // -- Background --
        Color bgColor;
        if (row == highlightedRow) {
            bgColor = HIGHLIGHT_COLOR;
        } else if (row.isSelected()) {
            bgColor = SELECTED_BG;
        } else if (row.isHovered()) {
            bgColor = HOVERED_BG;
        } else {
            bgColor = (visibleIndex % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
        }
        gc.setFill(bgColor);
        gc.fillRect(0, y, w, ROW_HEIGHT);

        // -- Selection border --
        if (row.isSelected()) {
            gc.setStroke(SELECTED_BORDER);
            gc.setLineWidth(2);
            gc.strokeRect(1, y + 1, w - 2, ROW_HEIGHT - 2);
        }

        // -- Row separator line (bottom) --
        gc.setStroke(ROW_SEPARATOR);
        gc.setLineWidth(0.5);
        gc.strokeLine(LEFT_MARGIN, y + ROW_HEIGHT, w, y + ROW_HEIGHT);

        // -- Icon --
        double iconX = LEFT_MARGIN + (row.getDepth() + 1) * INDENT;
        double iconCenterY = y + ROW_HEIGHT / 2;
        drawRowIcon(row.getType(), iconX, iconCenterY);

        // -- Label text --
        double labelX = iconX + ICON_AREA_WIDTH;
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.LEFT);

        boolean isEditingThisRowName = (editingRow == row && editingElementName);
        boolean isEditingThisRowValue = (editingRow == row && editingValue);

        if (row.getLabel() != null && !isEditingThisRowName) {
            gc.setFont(ROW_FONT_BOLD);
            gc.setFill(getRowLabelColor(row.getType()));

            String label = row.getLabel();
            if (row.getType() == FlatRow.RowType.ATTRIBUTE) {
                label = "@" + label;
            }
            gc.fillText(label, labelX, iconCenterY);
        }

        // -- Value text --
        double valueX = nameColumnWidth;
        if (row.getValue() != null && !isEditingThisRowValue) {
            gc.setFont(ROW_FONT);
            gc.setFill(getRowValueColor(row.getType()));

            // For elements with leaf values, show = "value" style
            if (row.getType() == FlatRow.RowType.ELEMENT && row.isLeafWithValue()) {
                gc.setFill(TEXT_SECONDARY);
                gc.fillText("=", valueX - 12, iconCenterY);
                gc.setFill(TEXT_CONTENT);
                String displayValue = "\"" + row.getValue() + "\"";
                gc.fillText(displayValue, valueX, iconCenterY);
            } else {
                gc.fillText(row.getValue(), valueX, iconCenterY);
            }
        }

        // -- Child count for expandable elements --
        if (row.isExpandable()) {
            double labelWidth = (row.getLabel() != null ? row.getLabel().length() * 7.2 : 0);
            double countX = labelX + labelWidth + 6;

            // Grid icon for repeating table rows
            if (row.hasRepeatingTable()) {
                // Draw small grid/table icon
                gc.setStroke(TABLE_HEADER_TEXT);
                gc.setLineWidth(1);
                double gx = countX;
                double gy = iconCenterY - 4;
                gc.strokeRect(gx, gy, 8, 8);
                gc.strokeLine(gx + 4, gy, gx + 4, gy + 8);
                gc.strokeLine(gx, gy + 4, gx + 8, gy + 4);
                countX += 12;
            }

            String countText = "(" + row.getChildCount() + (row.hasRepeatingTable() ? "x" : "") + ")";
            gc.setFont(SMALL_FONT);
            gc.setFill(row.hasRepeatingTable() ? TABLE_HEADER_TEXT : CHILD_COUNT_COLOR);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(countText, countX, iconCenterY);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    /**
     * Draws the type-specific icon for a row.
     */
    private void drawRowIcon(FlatRow.RowType type, double x, double cy) {
        gc.setLineWidth(1.5);
        double size = 4;
        double cx = x + ICON_AREA_WIDTH / 2;

        switch (type) {
            case ELEMENT -> {
                // <> angle brackets in blue
                gc.setStroke(TEXT_ELEMENT);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
            }
            case ATTRIBUTE -> {
                // = equals sign in red/brown
                gc.setFont(ICON_FONT);
                gc.setFill(TEXT_ATTRIBUTE_NAME);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText("=", cx, cy);
            }
            case TEXT -> {
                // T in green
                gc.setStroke(TEXT_CONTENT);
                gc.setLineWidth(2);
                gc.strokeLine(cx - size, cy - size, cx + size, cy - size);
                gc.strokeLine(cx, cy - size, cx, cy + size);
                gc.setLineWidth(1.5);
            }
            case COMMENT -> {
                // Rounded rect (speech bubble) in teal
                gc.setStroke(TEXT_COMMENT);
                gc.strokeRoundRect(cx - size, cy - size * 0.6, size * 2, size * 1.2, 3, 3);
            }
            case CDATA -> {
                // [[ ]] square brackets
                gc.setStroke(TEXT_CDATA);
                gc.strokeLine(cx - size, cy - size, cx - size + 2, cy - size);
                gc.strokeLine(cx - size, cy - size, cx - size, cy + size);
                gc.strokeLine(cx - size, cy + size, cx - size + 2, cy + size);
                gc.strokeLine(cx + size, cy - size, cx + size - 2, cy - size);
                gc.strokeLine(cx + size, cy - size, cx + size, cy + size);
                gc.strokeLine(cx + size, cy + size, cx + size - 2, cy + size);
            }
            case PROCESSING_INSTRUCTION -> {
                // Circle in violet
                gc.setStroke(TEXT_PI);
                gc.strokeOval(cx - size / 2, cy - size / 2, size, size);
            }
            case DOCUMENT -> {
                // Document icon
                gc.setStroke(TEXT_SECONDARY);
                gc.strokeRect(cx - size * 0.7, cy - size, size * 1.4, size * 2);
                gc.strokeLine(cx - size * 0.3, cy - size * 0.4, cx + size * 0.3, cy - size * 0.4);
                gc.strokeLine(cx - size * 0.3, cy, cx + size * 0.3, cy);
            }
        }
    }

    /**
     * Draws vertical expand/collapse bars for expanded elements.
     */
    private void drawExpandBars(int firstVisible, int lastVisible) {
        // Start from index 0, not firstVisible, so that expand bars from off-screen
        // parents whose descendants are visible still get drawn
        for (int i = 0; i <= lastVisible; i++) {
            FlatRow row = visibleRows.get(i);
            if (!row.isExpandable()) {
                continue;
            }

            double barX = LEFT_MARGIN + row.getDepth() * INDENT + INDENT / 2 - EXPAND_BAR_WIDTH / 2;
            double rowY = rowYPositions[i] - scrollOffsetY;

            boolean isHovered = (row == hoveredExpandBar);
            Color barColor = isHovered ? EXPAND_BAR_HOVER : EXPAND_BAR_COLOR;

            if (!row.isExpanded()) {
                // Collapsed: draw right-pointing arrow
                gc.setFill(isHovered ? EXPAND_BAR_HOVER : EXPAND_BAR_ARROW);
                double arrowX = barX + EXPAND_BAR_WIDTH / 2;
                double arrowY = rowY + ROW_HEIGHT / 2;
                double arrowSize = 4;
                gc.fillPolygon(
                        new double[]{arrowX - arrowSize / 2, arrowX + arrowSize / 2, arrowX - arrowSize / 2},
                        new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                        3
                );
            } else {
                // Expanded: draw vertical bar from element row down to last visible descendant
                int lastDescendantIndex = findLastVisibleDescendantIndex(row, i);
                if (lastDescendantIndex <= i) {
                    continue; // No visible descendants
                }

                double barTop = rowY + ROW_HEIGHT / 2;
                double barBottom = rowYPositions[lastDescendantIndex] - scrollOffsetY + ROW_HEIGHT / 2;

                // Draw the vertical bar
                gc.setStroke(barColor);
                gc.setLineWidth(2);
                gc.strokeLine(barX + EXPAND_BAR_WIDTH / 2, barTop, barX + EXPAND_BAR_WIDTH / 2, barBottom);

                // Down arrow at top
                double arrowSize = 3;
                gc.setFill(isHovered ? EXPAND_BAR_HOVER : EXPAND_BAR_ARROW);
                gc.fillPolygon(
                        new double[]{barX + EXPAND_BAR_WIDTH / 2 - arrowSize,
                                barX + EXPAND_BAR_WIDTH / 2,
                                barX + EXPAND_BAR_WIDTH / 2 + arrowSize},
                        new double[]{barTop - 1, barTop + arrowSize * 1.5, barTop - 1},
                        3
                );

                // Up arrow at bottom
                gc.fillPolygon(
                        new double[]{barX + EXPAND_BAR_WIDTH / 2 - arrowSize,
                                barX + EXPAND_BAR_WIDTH / 2,
                                barX + EXPAND_BAR_WIDTH / 2 + arrowSize},
                        new double[]{barBottom + 1, barBottom - arrowSize * 1.5, barBottom + 1},
                        3
                );
            }
        }
    }

    /**
     * Finds the index (in visibleRows) of the last visible descendant of the given row.
     */
    private int findLastVisibleDescendantIndex(FlatRow parentRow, int parentIndex) {
        int lastIndex = parentIndex;
        for (int i = parentIndex + 1; i < visibleRows.size(); i++) {
            FlatRow candidate = visibleRows.get(i);
            if (isDescendantOf(candidate, parentRow)) {
                lastIndex = i;
            } else {
                break; // All descendants are contiguous in the flat list
            }
        }
        return lastIndex;
    }

    /**
     * Checks if candidate is a descendant of ancestor.
     */
    private boolean isDescendantOf(FlatRow candidate, FlatRow ancestor) {
        return FlatRow.isDescendantOf(candidate, ancestor);
    }

    /**
     * Draws tree connection lines from parent expand bars to child row icons.
     */
    private void drawTreeLines(int firstVisible, int lastVisible) {
        gc.setStroke(TREE_LINE_COLOR);
        gc.setLineWidth(1);

        for (int i = firstVisible; i <= lastVisible; i++) {
            FlatRow row = visibleRows.get(i);
            FlatRow parent = row.getParentRow();

            if (parent == null) {
                continue; // No tree line for root-level rows
            }

            double rowY = rowYPositions[i] - scrollOffsetY + ROW_HEIGHT / 2;

            // Horizontal line from parent's vertical bar center to this row's icon position
            double parentBarCenterX = LEFT_MARGIN + parent.getDepth() * INDENT + INDENT / 2;
            double iconLeftX = LEFT_MARGIN + (row.getDepth() + 1) * INDENT;

            gc.strokeLine(parentBarCenterX, rowY, iconLeftX, rowY);
        }
    }

    private void drawEmptyState() {
        gc.setFill(TEXT_SECONDARY);
        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("No XML document loaded", canvas.getWidth() / 2, canvas.getHeight() / 2);
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
            int mid = (lo + hi) / 2;
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
        double height = ROW_HEIGHT;
        if (visibleIndex >= 0 && visibleIndex < visibleRows.size()) {
            FlatRow row = visibleRows.get(visibleIndex);
            if (row.hasRepeatingTable() && row.isExpanded()) {
                height += row.getRepeatingTable().getHeight();
            }
        }
        return height;
    }

    // ==================== Table Rendering ====================

    /**
     * Renders an inline repeating elements table below its header row.
     *
     * @param tableRow the FlatRow that owns the table
     * @param tableY   the Y coordinate where the table starts (screen coordinates)
     */
    private void renderInlineTable(FlatRow tableRow, double tableY) {
        RepeatingElementsTable table = tableRow.getRepeatingTable();
        if (table == null) {
            return;
        }

        double tableX = getRowLabelX(tableRow) - ICON_AREA_WIDTH;
        double tableWidth = table.getWidth();
        double tableHeight = table.getHeight();

        // Store position for hit testing
        table.setX(tableX);
        table.setY(tableY + scrollOffsetY); // Store in absolute coords for hit testing

        // -- Table header (element name + count) --
        gc.setFill(TABLE_HEADER_BG);
        gc.fillRect(tableX, tableY, tableWidth, RepeatingElementsTable.HEADER_HEIGHT);
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(tableX, tableY, tableWidth, RepeatingElementsTable.HEADER_HEIGHT);

        gc.setFont(ROW_FONT_BOLD);
        gc.setFill(TABLE_HEADER_TEXT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        String headerText = table.getElementName() + " (" + table.getElementCount() + "x)";
        gc.fillText(headerText, tableX + RepeatingElementsTable.CELL_PADDING,
                tableY + RepeatingElementsTable.HEADER_HEIGHT / 2);

        if (!table.isExpanded()) {
            return;
        }

        // -- Column headers --
        double colHeaderY = tableY + RepeatingElementsTable.HEADER_HEIGHT;
        gc.setFill(Color.rgb(243, 244, 246)); // Light gray background
        gc.fillRect(tableX, colHeaderY, tableWidth, RepeatingElementsTable.ROW_HEIGHT);
        gc.setStroke(TABLE_BORDER);
        gc.strokeRect(tableX, colHeaderY, tableWidth, RepeatingElementsTable.ROW_HEIGHT);

        double colX = tableX + RepeatingElementsTable.GRID_PADDING;
        gc.setFont(SMALL_FONT);
        gc.setTextBaseline(VPos.CENTER);
        double colCenterY = colHeaderY + RepeatingElementsTable.ROW_HEIGHT / 2;

        for (int c = 0; c < table.getColumns().size(); c++) {
            RepeatingElementsTable.TableColumn col = table.getColumns().get(c);
            double colWidth = col.getWidth();

            // Column separator
            if (c > 0) {
                gc.setStroke(ROW_SEPARATOR);
                gc.setLineWidth(0.5);
                gc.strokeLine(colX, colHeaderY, colX, colHeaderY + RepeatingElementsTable.ROW_HEIGHT);
            }

            // Column name
            gc.setFill(TEXT_SECONDARY);
            gc.setFont(ROW_FONT_BOLD);
            gc.setTextAlign(TextAlignment.LEFT);
            String displayName = col.getDisplayName();

            // Sort indicator
            if (table.isSortedBy(col.getName())) {
                displayName += table.isSortAscending() ? " \u25B2" : " \u25BC";
            }

            gc.fillText(truncateText(displayName, colWidth - RepeatingElementsTable.CELL_PADDING * 2),
                    colX + RepeatingElementsTable.CELL_PADDING, colCenterY);

            colX += colWidth;
        }

        // -- Data rows (cumulative positioning for variable row heights) --
        double dataY = colHeaderY + RepeatingElementsTable.ROW_HEIGHT;
        double currentRowY = dataY;
        for (int r = 0; r < table.getRows().size(); r++) {
            RepeatingElementsTable.TableRow row = table.getRows().get(r);
            double rowTop = currentRowY;
            double rowHeight = table.calculateRowHeight(row);

            // Row background
            Color rowBg;
            if (r == table.getSelectedRowIndex()) {
                rowBg = TABLE_ROW_SELECTED;
            } else if (r == hoveredTableRowIndex && table == hoveredTable) {
                rowBg = TABLE_ROW_HOVER;
            } else {
                rowBg = (r % 2 == 0) ? TABLE_ROW_EVEN : TABLE_ROW_ODD;
            }
            gc.setFill(rowBg);
            gc.fillRect(tableX, rowTop, tableWidth, rowHeight);

            // Row border
            gc.setStroke(TABLE_BORDER);
            gc.setLineWidth(0.5);
            gc.strokeLine(tableX, rowTop + rowHeight, tableX + tableWidth, rowTop + rowHeight);

            // Row number
            gc.setFont(SMALL_FONT);
            gc.setFill(TEXT_SECONDARY);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);

            // Cell values (summary line is always at the top of the cell)
            double cellX = tableX + RepeatingElementsTable.GRID_PADDING;
            double cellCenterY = rowTop + RepeatingElementsTable.ROW_HEIGHT / 2;

            for (int c = 0; c < table.getColumns().size(); c++) {
                RepeatingElementsTable.TableColumn col = table.getColumns().get(c);
                double colWidth = col.getWidth();

                // Column separator (full cell height)
                if (c > 0) {
                    gc.setStroke(ROW_SEPARATOR);
                    gc.setLineWidth(0.5);
                    gc.strokeLine(cellX, rowTop, cellX, rowTop + rowHeight);
                }

                String colName = col.getName();
                boolean isComplex = row.hasComplexChild(colName);

                // Draw expand arrow for complex cells (Canvas-drawn polygon)
                if (isComplex) {
                    double arrowX = cellX + 8;
                    double arrowY = cellCenterY;
                    double arrowSize = 3;
                    gc.setFill(TEXT_SECONDARY);

                    if (row.isColumnExpanded(colName)) {
                        // Down-pointing triangle (expanded)
                        gc.fillPolygon(
                                new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                                new double[]{arrowY - arrowSize / 2, arrowY + arrowSize, arrowY - arrowSize / 2},
                                3
                        );
                    } else {
                        // Right-pointing triangle (collapsed)
                        gc.fillPolygon(
                                new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                                new double[]{arrowY - arrowSize, arrowY, arrowY + arrowSize},
                                3
                        );
                    }
                }

                // Cell value (with offset for expand arrow on complex cells)
                String value = row.getValue(colName);
                gc.setFont(ROW_FONT);
                gc.setTextAlign(TextAlignment.LEFT);

                double textOffsetX = isComplex ? 14 : 0;

                if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                    gc.setFill(TEXT_ATTRIBUTE_VALUE);
                } else if (isComplex) {
                    gc.setFill(TEXT_SECONDARY);
                } else {
                    gc.setFill(TEXT_CONTENT);
                }

                gc.fillText(truncateText(value, colWidth - RepeatingElementsTable.CELL_PADDING * 2 - textOffsetX),
                        cellX + RepeatingElementsTable.CELL_PADDING + textOffsetX, cellCenterY);

                // Draw expanded cell content as a full tree (sub-rows below the summary line)
                if (row.isColumnExpanded(colName)) {
                    List<FlatRow> cellRows = row.getExpandedCellRows(colName);
                    renderCellTree(cellRows, cellX, rowTop + RepeatingElementsTable.ROW_HEIGHT, colWidth);
                }

                cellX += colWidth;
            }

            currentRowY += rowHeight;
        }

        // -- Table outer border --
        gc.setStroke(TABLE_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(tableX, tableY, tableWidth, tableHeight);

        // Left and right borders for the full table height
        gc.strokeLine(tableX, tableY, tableX, tableY + tableHeight);
        gc.strokeLine(tableX + tableWidth, tableY, tableX + tableWidth, tableY + tableHeight);
    }

    // ==================== Cell Tree Rendering ====================

    /**
     * Renders a full tree view within an expanded cell's content area.
     * Supports recursive expand/collapse, expand indicators, tree lines,
     * icons, labels, values, and child counts -- the same behavior as
     * the main tree view but rendered within cell bounds.
     *
     * @param cellRows  the flattened rows for this cell
     * @param cellX     the X coordinate of the cell's left edge
     * @param cellY     the Y coordinate where cell content starts (below summary line)
     * @param cellWidth the width of the cell
     */
    private void renderCellTree(List<FlatRow> cellRows, double cellX, double cellY, double cellWidth) {
        // Filter to visible rows only
        List<FlatRow> visibleCellRows = cellRows.stream()
                .filter(FlatRow::isVisible)
                .toList();

        if (visibleCellRows.isEmpty()) {
            return;
        }

        double cellPadding = RepeatingElementsTable.CELL_PADDING;

        // Calculate name column width for this cell's content
        double cellNameColWidth = 0;
        for (FlatRow row : visibleCellRows) {
            double indent = row.getDepth() * INDENT + ICON_AREA_WIDTH;
            double labelW = (row.getLabel() != null ? row.getLabel().length() * 7.2 : 0);
            double expandW = row.isExpandable() ? EXPAND_BAR_WIDTH : 0;
            cellNameColWidth = Math.max(cellNameColWidth, indent + expandW + labelW + 20);
        }
        cellNameColWidth = Math.min(cellNameColWidth, cellWidth * 0.5);

        // Draw tree connection lines within cell
        drawCellTreeLines(visibleCellRows, cellX + cellPadding, cellY);

        // Draw each visible sub-row
        for (int i = 0; i < visibleCellRows.size(); i++) {
            FlatRow row = visibleCellRows.get(i);
            double rowY = cellY + i * RepeatingElementsTable.ROW_HEIGHT;
            double rowCenterY = rowY + RepeatingElementsTable.ROW_HEIGHT / 2;

            double contentX = cellX + cellPadding + row.getDepth() * INDENT;

            // Draw expand indicator for expandable elements
            if (row.isExpandable()) {
                drawCellExpandIndicator(row, contentX, rowY);
            }

            // Draw icon
            double iconX = contentX + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);
            drawRowIcon(row.getType(), iconX, rowCenterY);

            // Draw label
            double labelX = iconX + ICON_AREA_WIDTH;
            gc.setFont(ROW_FONT);
            gc.setFill(getRowLabelColor(row.getType()));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);
            if (row.getLabel() != null) {
                double availLabel = cellNameColWidth - row.getDepth() * INDENT
                        - ICON_AREA_WIDTH - (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);
                gc.fillText(truncateText(row.getLabel(), availLabel), labelX, rowCenterY);
            }

            // Draw child count for expandable elements
            if (row.isExpandable()) {
                gc.setFont(SMALL_FONT);
                gc.setFill(TEXT_SECONDARY);
                double afterLabel = labelX + (row.getLabel() != null ? row.getLabel().length() * 7.2 : 0) + 4;
                gc.fillText("(" + row.getChildCount() + ")", afterLabel, rowCenterY);
            }

            // Draw value
            double valueX = cellX + cellPadding + cellNameColWidth;
            if (row.getValue() != null) {
                gc.setFont(ROW_FONT);
                gc.setFill(getRowValueColor(row.getType()));
                double availValue = cellWidth - cellPadding * 2 - cellNameColWidth;
                gc.fillText(truncateText(row.getValue(), availValue), valueX, rowCenterY);
            }

            // Row separator
            gc.setStroke(ROW_SEPARATOR);
            gc.setLineWidth(0.3);
            gc.strokeLine(cellX + cellPadding, rowY + RepeatingElementsTable.ROW_HEIGHT,
                    cellX + cellWidth - cellPadding, rowY + RepeatingElementsTable.ROW_HEIGHT);
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
     * @param visibleCellRows the visible sub-rows in the cell
     * @param baseX           the X offset for tree line calculations
     * @param baseY           the Y coordinate where the first sub-row starts
     */
    private void drawCellTreeLines(List<FlatRow> visibleCellRows, double baseX, double baseY) {
        gc.setStroke(TREE_LINE_COLOR);
        gc.setLineWidth(0.5);

        for (int i = 0; i < visibleCellRows.size(); i++) {
            FlatRow row = visibleCellRows.get(i);
            FlatRow parent = row.getParentRow();
            if (parent == null || parent.getDepth() < 0) {
                continue; // Skip rows whose parent is the virtual root
            }

            double rowY = baseY + i * RepeatingElementsTable.ROW_HEIGHT
                    + RepeatingElementsTable.ROW_HEIGHT / 2;
            double parentBarX = baseX + parent.getDepth() * INDENT + EXPAND_BAR_WIDTH / 2;
            double iconX = baseX + row.getDepth() * INDENT
                    + (row.isExpandable() ? EXPAND_BAR_WIDTH : 0);

            // Horizontal branch from parent bar to child icon
            gc.strokeLine(parentBarX, rowY, iconX, rowY);
        }
    }

    // ==================== Table Interaction ====================

    /**
     * Handles a mouse click inside an inline repeating table.
     */
    private void handleTableClick(MouseEvent event, RepeatingElementsTable table,
                                   double mx, double my, double tableScreenTop, FlatRow tableOwner) {
        // Check header click (toggle expand)
        if (my < tableScreenTop + RepeatingElementsTable.HEADER_HEIGHT) {
            table.toggleExpanded();
            recalculateVisibleRows();
            updateScrollBars();
            render();
            return;
        }

        if (!table.isExpanded()) {
            return;
        }

        // Check column header click (sort)
        double colHeaderY = tableScreenTop + RepeatingElementsTable.HEADER_HEIGHT;
        if (my >= colHeaderY && my < colHeaderY + RepeatingElementsTable.ROW_HEIGHT) {
            int colIdx = table.getColumnIndexAt(mx);
            if (colIdx >= 0) {
                RepeatingElementsTable.TableColumn col = table.getColumn(colIdx);
                if (col != null && table.isColumnSortable(col.getName())) {
                    handleTableSort(table, col.getName(), tableOwner);
                }
            }
            return;
        }

        // Data row click
        int rowIdx = getTableRowIndexAtScreenY(table, my, tableScreenTop);
        if (rowIdx >= 0 && rowIdx < table.getRows().size()) {
            RepeatingElementsTable.TableRow row = table.getRows().get(rowIdx);

            // Check if click is on a complex cell's expand arrow
            if (event.getClickCount() == 1) {
                int colIdx = table.getColumnIndexAt(mx);
                if (colIdx >= 0) {
                    RepeatingElementsTable.TableColumn col = table.getColumn(colIdx);
                    if (col != null && row.hasComplexChild(col.getName())) {
                        // Check if click is on the expand arrow area (first ~16px of cell)
                        double cellLeft = table.getColumnX(col.getName());
                        if (mx >= cellLeft && mx <= cellLeft + 16) {
                            row.toggleColumnExpanded(col.getName());
                            table.recalculateColumnWidths();
                            recalculateVisibleRows();
                            updateScrollBars();
                            render();
                            return;
                        }
                    }
                }
            }

            // Check if click is within an expanded cell's sub-row expand indicator
            if (event.getClickCount() == 1) {
                int colIdx = table.getColumnIndexAt(mx);
                if (colIdx >= 0) {
                    RepeatingElementsTable.TableColumn col = table.getColumn(colIdx);
                    if (col != null && row.isColumnExpanded(col.getName())) {
                        List<FlatRow> cellRows = row.getExpandedCellRows(col.getName());
                        List<FlatRow> visibleCellRows = cellRows.stream()
                                .filter(FlatRow::isVisible).toList();

                        // Calculate Y of this row's top edge
                        double dataStartY = tableScreenTop + RepeatingElementsTable.HEADER_HEIGHT
                                + RepeatingElementsTable.ROW_HEIGHT;
                        double rowTopY = dataStartY;
                        for (int i = 0; i < rowIdx; i++) {
                            rowTopY += table.calculateRowHeight(table.getRows().get(i));
                        }
                        double subRowStartY = rowTopY + RepeatingElementsTable.ROW_HEIGHT; // after summary
                        double cellLeft = table.getColumnX(col.getName());
                        double cellPad = RepeatingElementsTable.CELL_PADDING;

                        // Determine which sub-row was clicked
                        int subRowIdx = (int) ((my - subRowStartY) / RepeatingElementsTable.ROW_HEIGHT);
                        if (subRowIdx >= 0 && subRowIdx < visibleCellRows.size()) {
                            FlatRow subRow = visibleCellRows.get(subRowIdx);

                            // Check if click is on this sub-row's expand indicator
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
                }
            }

            table.setSelectedRowIndex(rowIdx);
            selectedTable = table;

            // Double-click to edit cell
            if (event.getClickCount() == 2) {
                int colIdx = table.getColumnIndexAt(mx);
                if (colIdx >= 0) {
                    RepeatingElementsTable.TableColumn col = table.getColumn(colIdx);
                    if (col != null) {
                        startEditingTableCell(table, rowIdx, col.getName(), mx, my, tableScreenTop);
                    }
                }
            }

            render();
        }
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

        var sortCmd = new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SortElementsCommand(
                table, columnName, ascending);
        context.executeCommand(sortCmd);

        // Update sort state
        table.setSortState(columnName, ascending);

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

        int rowIdx = getTableRowIndexAtScreenY(table, my, tableScreenTop);
        int colIdx = table.getColumnIndexAt(mx);

        if (rowIdx != hoveredTableRowIndex || colIdx != hoveredTableColumnIndex) {
            hoveredTableRowIndex = rowIdx;
            hoveredTableColumnIndex = colIdx;
            table.setHoveredRowIndex(rowIdx);
            table.setHoveredColumnIndex(colIdx);
            render();
        }
    }

    /**
     * Gets the table data row index at a screen Y coordinate.
     * Uses cumulative row heights to account for expanded complex cells.
     */
    private int getTableRowIndexAtScreenY(RepeatingElementsTable table, double screenY, double tableScreenTop) {
        double dataStartY = tableScreenTop + RepeatingElementsTable.HEADER_HEIGHT + RepeatingElementsTable.ROW_HEIGHT;
        if (screenY < dataStartY) {
            return -1;
        }

        double currentY = dataStartY;
        for (int i = 0; i < table.getRows().size(); i++) {
            double rowHeight = table.calculateRowHeight(table.getRows().get(i));
            if (screenY >= currentY && screenY < currentY + rowHeight) {
                return i;
            }
            currentY += rowHeight;
        }
        return -1;
    }

    /**
     * Starts editing a table cell.
     */
    private void startEditingTableCell(RepeatingElementsTable table, int rowIndex, String columnName,
                                        double mx, double my, double tableScreenTop) {
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

        // Calculate cell position (using cumulative row heights)
        double cellX = table.getColumnX(columnName) - scrollOffsetX;
        double dataStartY = tableScreenTop + RepeatingElementsTable.HEADER_HEIGHT + RepeatingElementsTable.ROW_HEIGHT;
        double cellY = dataStartY;
        for (int i = 0; i < rowIndex; i++) {
            cellY += table.calculateRowHeight(table.getRows().get(i));
        }
        double cellWidth = col.getWidth();

        // Build XPath for schema lookup
        XmlElement rowElement = row.getElement();
        String elementXPath = getRowXPath(findFlatRowForModelNode(rowElement));
        String attributeName = (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) ? columnName : null;

        createEditField(currentValue, cellX, cellY, cellWidth, elementXPath, attributeName);
    }

    /**
     * Finds the FlatRow for a given model node. Used for XPath building.
     */
    private FlatRow findFlatRowForModelNode(XmlNode node) {
        for (FlatRow row : allRows) {
            if (row.getModelNode() == node) {
                return row;
            }
        }
        return null;
    }

    // ==================== Color Helpers ====================

    private Color getRowLabelColor(FlatRow.RowType type) {
        return switch (type) {
            case ELEMENT -> TEXT_ELEMENT;
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
            default -> TEXT_CONTENT;
        };
    }

    private String truncateText(String text, double maxWidth) {
        if (text == null) {
            return "";
        }
        int maxChars = (int) (maxWidth / 7);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
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
            boolean changed = false;

            // Horizontal scrolling: Shift + scroll or trackpad horizontal gesture
            if (e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())) {
                if (totalWidth > canvas.getWidth()) {
                    double deltaX = e.isShiftDown() ? -e.getDeltaY() : -e.getDeltaX();
                    double newOffsetX = Math.max(0, Math.min(scrollOffsetX + deltaX, totalWidth - canvas.getWidth()));
                    if (newOffsetX != scrollOffsetX) {
                        scrollOffsetX = newOffsetX;
                        hScrollBar.setValue(scrollOffsetX);
                        changed = true;
                    }
                }
            } else {
                // Vertical scrolling
                if (totalHeight > canvas.getHeight()) {
                    double deltaY = -e.getDeltaY();
                    double newOffsetY = Math.max(0, Math.min(scrollOffsetY + deltaY, totalHeight - canvas.getHeight()));
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

        // Keyboard shortcuts
        canvas.setFocusTraversable(true);
        canvas.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        // Request focus when clicked (but not if editing)
        canvas.setOnMousePressed(e -> {
            if (!canvas.isFocused() && editField == null && activeWidgetNode == null) {
                canvas.requestFocus();
            }
        });
    }

    // ==================== Mouse Handling ====================

    private void handleMouseClick(MouseEvent event) {
        double mx = event.getX() + scrollOffsetX;
        double my = event.getY();

        if (visibleRows.isEmpty()) {
            return;
        }

        // Handle right-click for context menu
        if (event.getButton() == MouseButton.SECONDARY) {
            handleContextMenu(event, mx, my);
            return;
        }

        // Calculate which row was clicked
        int rowIndex = findRowIndexAtScreenY(my);

        // Check if click is inside an inline table
        if (rowIndex >= 0 && rowIndex < visibleRows.size()) {
            FlatRow tableOwner = visibleRows.get(rowIndex);
            if (tableOwner.hasRepeatingTable() && tableOwner.isExpanded()) {
                double rowScreenY = rowYPositions[rowIndex] - scrollOffsetY;
                double tableTop = rowScreenY + ROW_HEIGHT;
                RepeatingElementsTable table = tableOwner.getRepeatingTable();
                double tableBottom = tableTop + table.getHeight();
                if (my >= tableTop && my < tableBottom) {
                    handleTableClick(event, table, mx, my, tableTop, tableOwner);
                    return;
                }
            }
        }

        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            selectRow(null);
            return;
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);

        // Check if clicking on expand bar area
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
            double valueColStart = nameColumnWidth;

            if (mx >= valueColStart) {
                // Double-click in value column: edit value
                startEditingValue(clickedRow);
                return;
            } else {
                // Double-click in name column
                if (clickedRow.getType() == FlatRow.RowType.ELEMENT) {
                    if (clickedRow.isLeafWithValue()) {
                        // Leaf element with value: edit the value
                        startEditingValue(clickedRow);
                    } else {
                        // Non-leaf element: edit element name
                        startEditingElementName(clickedRow);
                    }
                    return;
                }
            }
        }

        // Single click: select row
        // If clicking on an expandable element's icon/label area, toggle expand
        if (clickedRow.isExpandable() && event.getClickCount() == 1) {
            double iconX = LEFT_MARGIN + (clickedRow.getDepth() + 1) * INDENT;
            if (mx >= iconX && mx < iconX + ICON_AREA_WIDTH) {
                // Click on icon area: toggle expand
                FlatRow.toggleExpand(clickedRow, allRows);
                recalculateVisibleRows();
                updateScrollBars();
            }
        }

        selectRow(clickedRow);
    }

    private void handleContextMenu(MouseEvent event, double mx, double my) {
        int rowIndex = findRowIndexAtScreenY(my);
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return;
        }

        FlatRow clickedRow = visibleRows.get(rowIndex);
        selectRow(clickedRow);

        // Update selection model
        context.getSelectionModel().setSelectedNode(clickedRow.getModelNode());

        // Show context menu
        contextMenu.show(canvas, event.getScreenX(), event.getScreenY(), clickedRow.getModelNode());
    }

    private void handleMouseMove(MouseEvent event) {
        double mx = event.getX() + scrollOffsetX;
        double my = event.getY();

        if (visibleRows.isEmpty()) {
            return;
        }

        boolean needsRedraw = false;

        // Calculate hovered row
        int rowIndex = findRowIndexAtScreenY(my);

        // Check table hover state
        boolean tableHovered = false;
        if (rowIndex >= 0 && rowIndex < visibleRows.size()) {
            FlatRow tableOwner = visibleRows.get(rowIndex);
            if (tableOwner.hasRepeatingTable() && tableOwner.isExpanded()) {
                double rowScreenY = rowYPositions[rowIndex] - scrollOffsetY;
                double tableTop = rowScreenY + ROW_HEIGHT;
                RepeatingElementsTable table = tableOwner.getRepeatingTable();
                double tableBottom = tableTop + table.getHeight();
                if (my >= tableTop && my < tableBottom) {
                    tableHovered = true;
                    updateTableHover(table, mx, my, tableTop);
                }
            }
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
        if (!tableHovered && rowIndex >= 0 && rowIndex < visibleRows.size()) {
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

        // Update hovered expand bar
        FlatRow newHoveredExpandBar = findExpandBarAt(mx, my);
        if (newHoveredExpandBar != hoveredExpandBar) {
            hoveredExpandBar = newHoveredExpandBar;
            needsRedraw = true;
        }

        // Update cursor
        if (hoveredExpandBar != null) {
            canvas.setCursor(Cursor.HAND);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }

        if (needsRedraw) {
            render();
        }
    }

    /**
     * Finds which expand bar (if any) is at the given canvas coordinates.
     */
    private FlatRow findExpandBarAt(double mx, double my) {
        if (visibleRows.isEmpty() || rowYPositions.length == 0) {
            return null;
        }

        double absoluteY = my + scrollOffsetY;

        // Find the row index at the click Y position to limit search range.
        // An expand bar can only cover the click if it starts at or before the clicked row.
        int clickedRowIdx = findRowIndexAtY(absoluteY);
        int searchLimit = Math.min(clickedRowIdx + 1, visibleRows.size());

        // Search only rows up to the clicked row — bars starting after cannot cover the click
        for (int i = 0; i < searchLimit; i++) {
            FlatRow row = visibleRows.get(i);
            if (!row.isExpandable()) {
                continue;
            }

            double barX = LEFT_MARGIN + row.getDepth() * INDENT + INDENT / 2 - EXPAND_BAR_WIDTH / 2;
            double barRight = barX + EXPAND_BAR_WIDTH;

            if (mx < barX || mx > barRight) {
                continue;
            }

            double rowY = rowYPositions[i];

            if (!row.isExpanded()) {
                // Collapsed: just check the element's own row
                if (absoluteY >= rowY && absoluteY < rowY + ROW_HEIGHT) {
                    return row;
                }
            } else {
                // Expanded: check the full bar range
                int lastDescIdx = findLastVisibleDescendantIndex(row, i);
                double barTop = rowY;
                double barBottom = rowYPositions[lastDescIdx] + ROW_HEIGHT;
                // Also account for table height on the last descendant
                FlatRow lastDesc = visibleRows.get(lastDescIdx);
                if (lastDesc.hasRepeatingTable() && lastDesc.isExpanded()) {
                    barBottom += lastDesc.getRepeatingTable().getHeight();
                }

                if (absoluteY >= barTop && absoluteY < barBottom) {
                    return row;
                }
            }
        }

        return null;
    }

    // ==================== Keyboard Handling ====================

    private void handleKeyPress(KeyEvent event) {
        // If editing, don't handle navigation keys
        if (editField != null || activeWidgetNode != null) {
            return;
        }

        XmlNode selected = getSelectedNode();

        // Context menu shortcuts (Delete, F2, Ctrl+C/X/V/D)
        if (selected != null && contextMenu != null) {
            contextMenu.handleKeyPress(event, selected);
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

    private void selectRow(FlatRow row) {
        if (selectedRow != null) {
            selectedRow.setSelected(false);
        }

        selectedRow = row;

        if (selectedRow != null) {
            selectedRow.setSelected(true);
            // Update selection model
            context.getSelectionModel().setSelectedNode(row.getModelNode());
        }

        render();
    }

    // ==================== Scroll-to-Selection ====================

    /**
     * Ensures the given row is visible in the viewport.
     */
    private void ensureRowVisible(FlatRow row) {
        if (row == null) {
            return;
        }

        int idx = visibleRows.indexOf(row);
        if (idx < 0 || idx >= rowYPositions.length) {
            return;
        }

        double rowTop = rowYPositions[idx];
        double rowBottom = rowTop + ROW_HEIGHT;
        double viewTop = scrollOffsetY;
        double viewBottom = viewTop + canvas.getHeight();

        if (rowTop < viewTop) {
            animateScrollTo(rowTop - 20);
        } else if (rowBottom > viewBottom) {
            animateScrollTo(rowBottom - canvas.getHeight() + 20);
        }
    }

    /**
     * Smoothly animate scroll to target Y position.
     */
    private void animateScrollTo(double targetY) {
        targetY = Math.max(0, Math.min(targetY, totalHeight - canvas.getHeight()));

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(vScrollBar.valueProperty(), scrollOffsetY)),
            new KeyFrame(Duration.millis(200), new KeyValue(vScrollBar.valueProperty(), targetY))
        );
        timeline.play();
    }

    // ==================== Inline Editing ====================

    private void startEditingValue(FlatRow row) {
        cancelEditing();

        if (row == null) {
            return;
        }

        editingRow = row;
        editingValue = true;
        editingElementName = false;

        String currentValue = row.getValue() != null ? row.getValue() : "";

        int idx = visibleRows.indexOf(row);
        if (idx < 0) {
            return;
        }

        double x = nameColumnWidth - scrollOffsetX;
        double y = rowYPositions[idx] - scrollOffsetY + 2;
        double width = Math.max(canvas.getWidth() - nameColumnWidth, MIN_VALUE_COL_WIDTH);

        // Get XPath for schema lookup
        String elementXPath = getRowXPath(row);
        String attributeName = (row.getType() == FlatRow.RowType.ATTRIBUTE) ? row.getLabel() : null;

        createEditField(currentValue, x, y, width, elementXPath, attributeName);
    }

    private void startEditingElementName(FlatRow row) {
        cancelEditing();

        if (row == null || row.getType() != FlatRow.RowType.ELEMENT) {
            return;
        }

        XmlNode modelNode = row.getModelNode();
        if (!(modelNode instanceof XmlElement)) {
            return;
        }

        editingRow = row;
        editingValue = false;
        editingElementName = true;

        String currentValue = row.getLabel();

        int idx = visibleRows.indexOf(row);
        if (idx < 0) {
            return;
        }

        double labelX = getRowLabelX(row) - scrollOffsetX;
        double y = rowYPositions[idx] - scrollOffsetY + 2;
        double width = nameColumnWidth - labelX - NAME_VALUE_GAP;

        createEditField(currentValue, labelX, y, width);
    }

    private void createEditField(String currentValue, double x, double y, double width) {
        createEditField(currentValue, x, y, width, null, null);
    }

    /**
     * Creates a type-aware edit field using schema information if available.
     */
    private void createEditField(String currentValue, double x, double y, double width,
                                  String elementXPath, String attributeName) {
        // Calculate minimum width based on content
        double contentBasedWidth = (currentValue != null ? currentValue.length() : 0) * 8 + 30;
        double effectiveWidth = Math.max(width, Math.max(contentBasedWidth, 120));

        // Try to create type-aware widget if schema is available
        if (context.hasSchema() && elementXPath != null) {
            TypeAwareWidgetFactory factory = context.getWidgetFactory();

            TypeAwareWidgetFactory.EditWidget widget;
            if (attributeName != null) {
                widget = factory.createAttributeWidget(elementXPath, attributeName, currentValue,
                        newValue -> javafx.application.Platform.runLater(this::commitEditing));
            } else {
                widget = factory.createElementWidget(elementXPath, currentValue,
                        newValue -> javafx.application.Platform.runLater(this::commitEditing));
            }

            if (widget != null) {
                activeEditWidget = widget;
                activeWidgetNode = widget.getNode();

                double widgetMinWidth = effectiveWidth;
                if (activeWidgetNode instanceof javafx.scene.control.DatePicker) {
                    widgetMinWidth = Math.max(widgetMinWidth, 160);
                } else if (activeWidgetNode instanceof javafx.scene.control.ComboBox) {
                    widgetMinWidth = Math.max(widgetMinWidth, 150);
                }

                if (activeWidgetNode instanceof javafx.scene.layout.Region region) {
                    region.setLayoutX(x);
                    region.setLayoutY(y);
                    region.setPrefWidth(widgetMinWidth);
                    region.setMinWidth(widgetMinWidth);
                    region.setPrefHeight(ROW_HEIGHT);
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

                logger.debug("Created type-aware widget for {} (attribute: {})",
                        elementXPath, attributeName);
                return;
            }
        }

        // Fallback to standard TextField
        editField = new TextField(currentValue);
        editField.setLayoutX(x);
        editField.setLayoutY(y);
        editField.setPrefWidth(effectiveWidth);
        editField.setMinWidth(effectiveWidth);
        editField.setPrefHeight(ROW_HEIGHT);
        editField.setStyle("-fx-font-size: 12px; -fx-font-family: 'Segoe UI'; -fx-padding: 2 6;");

        // Add documentation tooltip if available
        if (context.hasSchema() && elementXPath != null) {
            Optional<String> doc = attributeName != null
                    ? context.getSchemaProvider().getAttributeDocumentation(elementXPath, attributeName)
                    : context.getSchemaProvider().getElementDocumentation(elementXPath);
            doc.ifPresent(docText -> {
                Tooltip tooltip = new Tooltip(docText);
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(300);
                Tooltip.install(editField, tooltip);
            });
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
                commitTableCellEditing(newValue);
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

            XmlNode modelNode = editingRow.getModelNode();

            if (editingElementName) {
                // Editing element name
                if (modelNode instanceof XmlElement && !newValue.trim().isEmpty()) {
                    context.executeCommand(new RenameNodeCommand((XmlElement) modelNode, newValue.trim()));
                }
            } else if (editingValue) {
                // Editing value
                if (editingRow.getType() == FlatRow.RowType.ATTRIBUTE) {
                    // Attribute value
                    if (modelNode instanceof XmlElement element) {
                        context.executeCommand(new SetAttributeCommand(element, editingRow.getLabel(), newValue));
                    }
                } else if (editingRow.getType() == FlatRow.RowType.ELEMENT) {
                    // Element text content
                    if (modelNode instanceof XmlElement element) {
                        context.executeCommand(new SetElementTextCommand(element, newValue));
                    }
                } else if (editingRow.getType() == FlatRow.RowType.TEXT) {
                    // Text node
                    if (modelNode instanceof XmlText text) {
                        context.executeCommand(new SetTextCommand(text, newValue));
                    }
                }
            }

            cancelEditing();
            rebuildTree();
            notifyDocumentModified();
        } finally {
            isCommitting = false;
        }
    }

    /**
     * Commits table cell editing (for repeating elements tables).
     */
    private void commitTableCellEditing(String newValue) {
        RepeatingElementsTable.TableRow row = editingTable.getRows().get(editingTableRowIndex);
        XmlElement rowElement = row.getElement();

        RepeatingElementsTable.TableColumn col = null;
        for (RepeatingElementsTable.TableColumn c : editingTable.getColumns()) {
            if (c.getName().equals(editingTableColumnName)) {
                col = c;
                break;
            }
        }

        if (col == null) {
            return;
        }

        if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            context.executeCommand(new SetAttributeCommand(rowElement, editingTableColumnName, newValue));
        } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
            for (XmlNode child : rowElement.getChildren()) {
                if (child instanceof XmlElement childElement) {
                    if (childElement.getName().equals(editingTableColumnName)) {
                        context.executeCommand(new SetElementTextCommand(childElement, newValue));
                        break;
                    }
                }
            }
        } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
            context.executeCommand(new SetElementTextCommand(rowElement, newValue));
        }
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

    // ==================== XPath Building ====================

    /**
     * Builds an XPath for a FlatRow by walking up the parent chain.
     */
    private String getRowXPath(FlatRow row) {
        if (row == null) {
            return "";
        }

        // For attribute rows, build XPath from the parent element
        FlatRow targetRow = row;
        if (row.getType() == FlatRow.RowType.ATTRIBUTE) {
            targetRow = row.getParentRow();
        }

        StringBuilder path = new StringBuilder();
        FlatRow current = targetRow;

        while (current != null) {
            if (current.getType() == FlatRow.RowType.ELEMENT && current.getLabel() != null) {
                if (path.length() > 0) {
                    path.insert(0, "/");
                }
                path.insert(0, current.getLabel());
            }
            current = current.getParentRow();
        }

        return "/" + path;
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent _evt) {
        cancelEditing();
        rebuildTree();
    }

    /**
     * Handles mixed content detection event.
     */
    @SuppressWarnings("unchecked")
    private void onMixedContentDetected(PropertyChangeEvent evt) {
        java.util.List<XmlElement> mixedElements = (java.util.List<XmlElement>) evt.getNewValue();
        if (mixedElements == null || mixedElements.isEmpty()) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Mixed Content Detected");
            alert.setHeaderText("This XML contains " + mixedElements.size()
                    + " element(s) with mixed content");

            StringBuilder details = new StringBuilder();
            details.append("Elements with both text and child elements were found:\n\n");
            int count = 0;
            for (XmlElement elem : mixedElements) {
                if (count++ >= 5) {
                    details.append("... and ").append(mixedElements.size() - 5).append(" more\n");
                    break;
                }
                details.append("- <").append(elem.getName()).append(">\n");
            }
            details.append("\nThis may cause display issues. Consider removing either the text content "
                    + "or child elements to ensure valid XML structure.");

            alert.setContentText(details.toString());
            alert.showAndWait();
        });
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
    public void flashHighlightByModel(XmlNode modelNode) {
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
            String xml = context.serializeToString();
            onDocumentModified.accept(xml);
        }
    }

    // ==================== Public API ====================

    public void refresh() {
        rebuildTree();
        notifyDocumentModified();
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

    public XmlNode getSelectedNode() {
        return selectedRow != null ? selectedRow.getModelNode() : null;
    }

    public void setSelectedNode(XmlNode node) {
        if (node != null) {
            for (FlatRow row : allRows) {
                if (row.getModelNode() == node) {
                    // Ensure ancestors are expanded
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
                    return;
                }
            }
        }
    }
}
