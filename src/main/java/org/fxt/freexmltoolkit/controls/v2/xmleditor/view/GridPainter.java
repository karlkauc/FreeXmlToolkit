package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

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

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;


/**
 * Draws a {@link GridCanvasView}: the theme palette, the rows of the root grid and of opened
 * cells, embedded tables, the sticky scroll header, drop / header-drag indicators and the
 * empty-state and info overlays. Holds the colours and the current culling rectangle; all
 * layout and row state stay in the view.
 *
 * @param <N> the model node type
 */
final class GridPainter<N> {

    /** The view this painter draws. */
    private final GridCanvasView<N> view;

    GridPainter(GridCanvasView<N> view) {
        this.view = view;
    }

    /** The visible area in the current drawing coordinates (shifted while an opened cell's grid draws). */
    private double cullTop;
    private double cullBottom;
    private double cullLeft;
    private double cullRight;

    /** Set while {@link #renderExport} draws: no interaction states, no overlays, no scrolling. */
    private boolean exporting;

    // ==================== Colors (design tokens, theme-dependent) ====================
    //
    // Instance fields (kept in UPPER_SNAKE for diff-friendliness with the former
    // constants); (re)assigned by applyPalette() for the current light/dark theme from
    // the DesignTokens so the grid follows the application palette.

    // Backgrounds
    private Color rowBgEven;

    // Selection/Hover
    private Color selectedBg;
    private Color selectedBorder;
    private Color hoveredBg;

    // Text colors
    private Color textElement;
    private Color textAttributeName;
    private Color textAttributeValue;
    private Color textContent;
    private Color textSecondary;
    private Color textComment;
    private Color textCdata;
    private Color textPi;

    // JSON value colors (keys reuse textElement, containers reuse textElement/accent)
    private Color textJsonString;
    private Color textJsonNumber;
    private Color textJsonBoolean;
    private Color textJsonNull;

    // Cell frames, tree lines (cell sub-trees) & expand arrows
    private Color frameLine;
    private Color expandBarHover;
    private Color expandBarArrow;

    // Child count, instance index, collapsed preview
    private Color childCountColor;
    private Color instanceIndex;
    private Color previewText;

    // Accent (the design system's primary)
    private Color accent;

    // Table colors
    Color tableHeaderBg;
    private Color tableHeaderText;
    private Color tableHeaderSelected;
    private Color tableBorder;
    private Color tableRowHover;
    private Color tableRowSelected;
    private Color tableMissingBg;
    private Color danger;
    private Color namespacePrefix;

    /**
     * Assigns the light or dark grid palette from the {@link org.fxt.freexmltoolkit.controls.theme.DesignTokens}
     * so the canvas renders in the application's colours in both themes.
     */
    void applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme theme) {
        rowBgEven = BG_SURFACE.color(theme);

        selectedBg = BG_SUBTLE.color(theme);
        selectedBorder = PRIMARY.color(theme);
        hoveredBg = BG_SURFACE_2.color(theme);
        view.highlightColor = WF_FAVORITES_BG.color(theme);
        view.queryHighlightColor = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.WF_SEARCH_BG.color(theme);

        textElement = CODE_ELEM.color(theme);
        textAttributeName = CODE_ATTR.color(theme);
        textAttributeValue = CODE_VAL.color(theme);
        textContent = CODE_TEXT.color(theme);
        textSecondary = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);
        textComment = TEAL.color(theme);
        textCdata = INDIGO.color(theme);
        textPi = PURPLE.color(theme);

        textJsonString = CODE_VAL.color(theme);
        textJsonNumber = WF_TRANSFORM_FG.color(theme);
        textJsonBoolean = TEAL.color(theme);
        textJsonNull = TEXT_MUTED.color(theme);

        frameLine = BORDER_DEFAULT.color(theme);
        expandBarHover = PRIMARY.color(theme);
        expandBarArrow = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);

        childCountColor = TEXT_MUTED.color(theme);
        instanceIndex = childCountColor;
        previewText = childCountColor;
        accent = PRIMARY.color(theme);

        tableHeaderBg = BG_SURFACE_2.color(theme);
        tableHeaderText = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.TEXT_SECONDARY.color(theme);
        tableHeaderSelected = BG_SUBTLE.color(theme);
        tableBorder = frameLine;
        tableRowHover = hoveredBg;
        tableRowSelected = selectedBg;
        tableMissingBg = BG_CANVAS.color(theme);

        // User-configurable cell colours (Settings → Grid table colours) win over the tokens
        tableHeaderBg = GridColors.resolve(theme, GridColors.Slot.TABLE_HEADER);
        tableMissingBg = GridColors.resolve(theme, GridColors.Slot.MISSING_CELL);
        tableHeaderSelected = GridColors.resolve(theme, GridColors.Slot.SELECTION);
        tableRowSelected = tableHeaderSelected;
        selectedBg = tableHeaderSelected;
        selectedBorder = GridColors.resolve(theme, GridColors.Slot.FOCUS);
        danger = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.DANGER.color(theme);
        namespacePrefix = org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.CODE_DECL.color(theme);
    }

    // ==================== Fonts ====================

    private static final Font ROW_FONT = GridFont.ROW.toFont();
    private static final Font ROW_FONT_BOLD = GridFont.ROW_BOLD.toFont();
    private static final Font SMALL_FONT = GridFont.SMALL.toFont();
    private static final Font ICON_FONT = GridFont.ICON.toFont();

    /** Size of the small table glyph drawn after a table owner's label. */
    static final double TABLE_ICON_SIZE = 10;

    /**
     * Draws a wrapped text block: the first line centred on {@code firstLineCenterY},
     * every further line {@link GridCanvasView#LINE_HEIGHT} lower. Lines outside the canvas are skipped.
     */
    private void drawTextBlock(TextBlock block, double x, double firstLineCenterY) {
        if (block == null) {
            return;
        }
        double canvasBottom = cullBottom + GridCanvasView.LINE_HEIGHT;
        List<String> lines = block.lines();
        for (int k = 0; k < lines.size(); k++) {
            double y = firstLineCenterY + k * GridCanvasView.LINE_HEIGHT;
            if (y < cullTop - GridCanvasView.LINE_HEIGHT) {
                continue;
            }
            if (y > canvasBottom) {
                break;
            }
            view.gc.fillText(lines.get(k), x, y);
        }
    }

    /**
     * Main render method. Clears the canvas and draws visible rows.
     */
    void render() {
        double w = view.canvas.getWidth();
        double h = view.canvas.getHeight();

        if (w <= 0 || h <= 0) {
            return;
        }

        // Clear canvas
        view.gc.setFill(rowBgEven);
        view.gc.fillRect(0, 0, w, h);

        if (view.visibleRows.isEmpty()) {
            drawEmptyState();
            return;
        }

        // Calculate visible row range using Y positions (viewport in model units)
        int firstVisible = view.findRowIndexAtY(view.scrollOffsetY);
        int lastVisible = view.findRowIndexAtY(view.scrollOffsetY + view.viewportHeightModel());
        if (firstVisible < 0) firstVisible = 0;
        if (lastVisible < 0 || lastVisible >= view.visibleRows.size()) lastVisible = view.visibleRows.size() - 1;

        // Save, apply the zoom, then translate for the horizontal scroll offset
        view.gc.save();
        double z = view.getZoom();
        view.gc.scale(z, z);
        view.gc.translate(-view.scrollOffsetX, 0);
        cullTop = 0;
        cullBottom = view.viewportHeightModel();
        cullLeft = view.scrollOffsetX;
        cullRight = view.scrollOffsetX + view.viewportWidthModel();

        // The name cells of the first visible row's ancestors start above the viewport but
        // span into it: draw them first (outermost first), then the rows in view.
        for (int idx : view.ancestorIndices(firstVisible)) {
            drawRow(idx);
        }
        for (int i = firstVisible; i <= lastVisible; i++) {
            drawRow(i);
        }

        // Draw inline tables beside their owner rows
        for (int i = firstVisible; i <= lastVisible; i++) {
            FlatRow row = view.visibleRows.get(i);
            if (row.hasRepeatingTable() && row.isExpanded()) {
                renderInlineTable(row, view.rowYPositions[i] - view.scrollOffsetY);
            }
        }

        drawDropIndicator();
        drawHeaderDragIndicator();

        view.gc.restore();

        // Sticky scroll header: the ancestors of the first visible row (zoomed, not scrolled)
        drawScrollHeader(firstVisible);

        // Draw info overlay (not scrolled)
        drawInfo();
    }

    /**
     * Draws the rows and tables meeting {@code region} (content coordinates, unscrolled and
     * unzoomed) onto an export surface, in the given theme and without interaction states
     * (selection, hover, search flashes, inline editing), the sticky header or the info line.
     * The view's own surface, scroll position and palette are restored afterwards; the caller
     * re-renders the view, because drawing records hit-test geometry.
     *
     * @param g      the export surface, transformed so content coordinates map onto it
     * @param region the part of the content to draw
     * @param theme  the palette to draw with
     */
    void renderExport(org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics g,
                      javafx.geometry.Rectangle2D region,
                      org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme theme) {
        org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics screen = view.gc;
        double scrollX = view.scrollOffsetX;
        double scrollY = view.scrollOffsetY;
        view.gc = g;
        view.scrollOffsetX = 0;
        view.scrollOffsetY = 0;
        exporting = true;
        applyPalette(theme);
        try {
            if (view.visibleRows.isEmpty()) {
                return;
            }
            cullTop = region.getMinY();
            cullBottom = region.getMaxY();
            cullLeft = region.getMinX();
            cullRight = region.getMaxX();
            int last = view.visibleRows.size() - 1;
            int first = Math.max(0, view.findRowIndexAtY(region.getMinY()));
            int to = view.findRowIndexAtY(region.getMaxY());
            if (to < 0 || to > last) {
                to = last;
            }
            for (int idx : view.ancestorIndices(first)) {
                drawRow(idx);
            }
            for (int i = first; i <= to; i++) {
                drawRow(i);
            }
            for (int i = first; i <= to; i++) {
                FlatRow row = view.visibleRows.get(i);
                if (row.hasRepeatingTable() && row.isExpanded()) {
                    renderInlineTable(row, view.rowYPositions[i]);
                }
            }
        } finally {
            exporting = false;
            view.gc = screen;
            view.scrollOffsetX = scrollX;
            view.scrollOffsetY = scrollY;
            applyPalette(org.fxt.freexmltoolkit.controls.theme.DesignTokens.currentTheme());
        }
    }

    /** @return the page colour of the grid in a theme (the even-row background) */
    static Color pageBackground(org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme theme) {
        return BG_SURFACE.color(theme);
    }

    /** @return an interaction state as drawn: never while exporting */
    private boolean shows(boolean interactionState) {
        return interactionState && !exporting;
    }

    /** Draws the red problem marker ("!" in a circle) at the right end of a name cell's first line. */
    private void drawProblemMarker(double rightX, double cy) {
        double r = 6;
        double cx = rightX - r - 4;
        view.gc.setFill(danger);
        view.gc.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        view.gc.setFill(rowBgEven);
        view.gc.setFont(ICON_FONT);
        view.gc.setTextAlign(TextAlignment.CENTER);
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.fillText("!", cx, cy + 0.5);
        view.gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawScrollHeader(int firstVisible) {
        List<FlatRow> chain = view.scrollHeaderRows();
        if (chain.isEmpty()) {
            view.headerSegments = List.of();
            return;
        }
        double z = view.getZoom();
        view.gc.save();
        view.gc.scale(z, z);
        double width = view.viewportWidthModel();
        view.gc.setFill(tableHeaderBg);
        view.gc.fillRect(0, 0, width, GridCanvasView.SCROLL_HEADER_HEIGHT);
        view.gc.setStroke(frameLine);
        view.gc.setLineWidth(1);
        view.gc.strokeLine(0, GridCanvasView.SCROLL_HEADER_HEIGHT - 0.5, width, GridCanvasView.SCROLL_HEADER_HEIGHT - 0.5);

        List<GridCanvasView.HeaderSegment> segments = new ArrayList<>();
        double x = GridCanvasView.LEFT_MARGIN;
        double cy = GridCanvasView.SCROLL_HEADER_HEIGHT / 2;
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.setTextAlign(TextAlignment.LEFT);
        for (int i = 0; i < chain.size(); i++) {
            FlatRow row = chain.get(i);
            if (i > 0) {
                view.gc.setFont(SMALL_FONT);
                view.gc.setFill(textSecondary);
                view.gc.fillText("\u203A", x, cy);
                x += 12;
            }
            double start = x;
            drawRowIcon(row, x - 4, cy);
            x += GridCanvasView.ICON_AREA_WIDTH - 4;
            String label = row.getLabel() != null ? row.getLabel() : "";
            view.gc.setFont(ROW_FONT_BOLD);
            view.gc.setFill(getRowLabelColor(row.getType()));
            fillName(label, x, cy, GridFont.ROW_BOLD);
            x += view.metrics.width(label, GridFont.ROW_BOLD);
            if (row.getInstanceIndex() > 0) {
                view.gc.setFont(SMALL_FONT);
                view.gc.setFill(instanceIndex);
                String idx = " " + GridCanvasView.instanceIndexText(row);
                view.gc.fillText(idx, x, cy);
                x += view.metrics.width(idx, GridFont.SMALL);
            }
            segments.add(new GridCanvasView.HeaderSegment(row, start, x));
            x += 10;
            if (x > width) {
                break;
            }
        }
        view.headerSegments = segments;
        view.gc.restore();
    }

    /**
     * Draws one row: its name cell (spanning the child block of an expanded container), the
     * value cell with the wrapped value or the grey preview, the cell frames and the expand arrow.
     */
    private void drawRow(int visibleIndex) {
        drawRowAt(view.visibleRows.get(visibleIndex), view.rowYPositions[visibleIndex] - view.scrollOffsetY,
                view.rowHeights[visibleIndex], view.rowSpanHeight(visibleIndex),
                exporting ? Math.max(view.totalWidth, cullRight)
                        : Math.max(view.totalWidth, view.viewportWidthModel() + view.scrollOffsetX));
    }

    /** Space kept free between the clipped name and the value column. */
    private static final double NAME_CLIP_GAP = 2;

    /**
     * Draws one row of any grid (the root or an opened cell's) in the current coordinates.
     *
     * @param top   the row's top
     * @param own   its own height (0 for an expanded container)
     * @param span  the height of its name cell (own line plus visible descendants)
     * @param right where the value cells end
     */
    private void drawRowAt(FlatRow row, double top, double own, double span, double right) {
        double labelX = row.getLabelX();
        double valueX = row.getValueX();
        boolean tableOwner = row.hasRepeatingTable() && row.isExpanded();
        boolean hasValueCell = own > 0 && !tableOwner;

        // -- Background (name cell; the value cell too while the row has one) --
        Color bgColor = null;
        if (shows(row == view.highlightedRow)) {
            bgColor = view.highlightColor;
        } else if (shows(row.isSelected())) {
            bgColor = selectedBg;
        } else if (shows(view.queryRows.contains(row))) {
            bgColor = view.queryHighlightColor;
        } else if (shows(row.isHovered())) {
            bgColor = hoveredBg;
        }
        if (bgColor != null) {
            view.gc.setFill(bgColor);
            view.gc.fillRect(labelX, top, valueX - labelX, span);
            if (hasValueCell) {
                view.gc.fillRect(valueX, top, right - valueX, own);
            }
        }

        // -- Cell frames (hairlines): the name cell, and the bottom edge of the value cell --
        view.gc.setStroke(frameLine);
        view.gc.setLineWidth(1);
        view.gc.strokeRect(labelX + 0.5, top + 0.5, valueX - labelX, span);
        if (hasValueCell) {
            view.gc.strokeLine(valueX, top + own + 0.5, right, top + own + 0.5);
        }

        // -- Selection border --
        if (shows(row.isSelected())) {
            view.gc.setStroke(selectedBorder);
            view.gc.setLineWidth(1);
            view.gc.strokeRect(labelX + 0.5, top + 0.5, valueX - labelX, span);
            if (hasValueCell) {
                view.gc.strokeRect(valueX + 0.5, top + 0.5, right - valueX - 1, own);
            }
        }

        // -- Expand arrow, icon, label: clipped to the name cell, so a narrowed name column never
        //    lets a long name run under the value column --
        double lineCenterY = top + GridCanvasView.ROW_HEIGHT / 2;
        double nameRight = valueX - NAME_CLIP_GAP;
        // Set before the save: the value text below draws with the same alignment
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.setTextAlign(TextAlignment.LEFT);
        view.gc.save();
        view.gc.beginPath();
        view.gc.rect(labelX, top, Math.max(0, nameRight - labelX), span);
        view.gc.clip();
        if (row.isExpandable()) {
            drawExpandArrow(row, labelX, lineCenterY);
        }
        double iconX = view.rowIconX(row);
        drawRowIcon(row, iconX, lineCenterY);

        double textX = view.rowTextX(row);

        boolean isEditingThisRowName = shows(view.editor.editingRow == row && view.editor.editingElementName);
        boolean isEditingThisRowValue = shows(view.editor.editingRow == row && view.editor.editingValue);

        double afterLabelX = textX;
        if (view.displayLabel(row) != null && !isEditingThisRowName) {
            view.gc.setFont(ROW_FONT_BOLD);
            view.gc.setFill(view.formulaOf(row) != null ? accent : getRowLabelColor(row.getType()));
            fillName(view.displayLabel(row), textX, lineCenterY, GridFont.ROW_BOLD);
            afterLabelX += view.labelTextWidth(row);
        }

        // <n> instance number, (n) child count, table glyph
        view.gc.setFont(SMALL_FONT);
        if (row.getInstanceIndex() > 0) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            view.gc.setFill(instanceIndex);
            String text = GridCanvasView.instanceIndexText(row);
            view.gc.fillText(text, afterLabelX, lineCenterY);
            afterLabelX += view.metrics.width(text, GridFont.SMALL);
        }
        if (row.isExpandable()) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            view.gc.setFill(childCountColor);
            String text = GridCanvasView.childCountText(row);
            view.gc.fillText(text, afterLabelX, lineCenterY);
            afterLabelX += view.metrics.width(text, GridFont.SMALL);
        }
        if (row.showsTableGlyph()) {
            afterLabelX += GridMetrics.CHILD_COUNT_GAP;
            drawTableGlyph(afterLabelX, lineCenterY - TABLE_ICON_SIZE / 2, row.hasRepeatingTable());
            // A glyph cut off by the name column cannot be clicked (the click belongs to the value)
            row.setTableGlyphX(afterLabelX + TABLE_ICON_SIZE <= nameRight ? afterLabelX : Double.NaN);
        } else {
            row.setTableGlyphX(Double.NaN);
        }
        view.gc.restore();

        List<String> problems = view.problemsOf(row);
        if (problems != null) {
            drawProblemMarker(valueX, lineCenterY);
        }

        // -- Value text (wrapped) or the grey one-line preview, clipped to the value cell --
        if (hasValueCell && row.getValueBlock() != null && !isEditingThisRowValue) {
            view.gc.save();
            view.gc.beginPath();
            view.gc.rect(valueX, top, right - valueX, own);
            view.gc.clip();
            view.gc.setFont(ROW_FONT);
            if (row.getType() == FlatRow.RowType.FILTER) {
                String key = view.keyOf(row.getParentRow());
                view.gc.setFill(view.filterErrors.containsKey(key) ? danger
                        : view.viewState.isFilterActive(key) ? accent : textSecondary);
            } else if (problems != null && !view.showsPreview(row)) {
                view.gc.setFill(danger);
            } else if (view.showsPreview(row)) {
                view.gc.setFill(previewText);
            } else if (row.isLeafWithValue()) {
                view.gc.setFill(row.getType().isJson() ? getRowValueColor(row.getType()) : textContent);
            } else {
                view.gc.setFill(getRowValueColor(row.getType()));
            }
            GridModelAdapter.FormulaView formula = view.formulaOf(row);
            if (formula != null) {
                view.gc.setFill(textPi);
            }
            drawTextBlock(row.getValueBlock(), valueX + GridCanvasView.VALUE_PADDING, lineCenterY);
            if (formula != null && row.getResultBlock() != null) {
                view.gc.setFill(formula.error() != null ? danger : textContent);
                drawTextBlock(row.getResultBlock(), valueX + GridCanvasView.VALUE_PADDING,
                        lineCenterY + row.getValueBlock().lineCount() * GridCanvasView.LINE_HEIGHT);
                if (formula.stored()) {
                    view.gc.setFont(SMALL_FONT);
                    view.gc.setFill(textSecondary);
                    view.gc.setTextAlign(TextAlignment.RIGHT);
                    view.gc.fillText("stored", right - 8, lineCenterY);
                    view.gc.setTextAlign(TextAlignment.LEFT);
                }
            }
            view.gc.restore();
        }
    }

    /**
     * Draws a node name in the current fill; a namespace prefix ({@code xsi:} in
     * {@code xsi:type}) is drawn in the prefix colour to set prefixes apart.
     */
    private void fillName(String name, double x, double y, GridFont font) {
        String prefix = namespacePrefix(name);
        if (prefix == null) {
            view.gc.fillText(name, x, y);
            return;
        }
        javafx.scene.paint.Paint fill = view.gc.getFill();
        view.gc.setFill(namespacePrefix);
        view.gc.fillText(prefix, x, y);
        view.gc.setFill(fill);
        view.gc.fillText(name.substring(prefix.length()), x + view.metrics.width(prefix, font), y);
    }

    /** @return the namespace prefix of a name incl. the colon ({@code "xsi:"}), or {@code null} */
    static String namespacePrefix(String name) {
        int colon = name == null ? -1 : name.indexOf(':');
        return colon > 0 && colon < name.length() - 1 ? name.substring(0, colon + 1) : null;
    }

    /** Draws the ▶ / ▼ expand arrow in the arrow area left of the icon. */
    private void drawExpandArrow(FlatRow row, double labelX, double cy) {
        boolean hovered = shows(row == view.hoveredExpandBar);
        view.gc.setFill(hovered ? expandBarHover : expandBarArrow);
        double cx = labelX + GridCanvasView.EXPAND_BAR_WIDTH / 2 + 1;
        double size = 4;
        if (row.isExpanded()) {
            view.gc.fillPolygon(new double[]{cx - size, cx + size, cx},
                    new double[]{cy - size / 2, cy - size / 2, cy + size / 2 + 1}, 3);
        } else {
            view.gc.fillPolygon(new double[]{cx - size / 2, cx + size / 2 + 1, cx - size / 2},
                    new double[]{cy - size, cy, cy + size}, 3);
        }
    }

    /** Draws the small 3x3 table glyph that marks a repeating group; filled when its table is shown. */
    private void drawTableGlyph(double x, double y, boolean active) {
        double s = TABLE_ICON_SIZE;
        view.gc.setStroke(active ? accent : tableHeaderText);
        view.gc.setLineWidth(1);
        view.gc.strokeRect(x + 0.5, y + 0.5, s, s);
        view.gc.strokeLine(x + 0.5, y + s / 3 + 0.5, x + s + 0.5, y + s / 3 + 0.5);
        view.gc.strokeLine(x + 0.5, y + 2 * s / 3 + 0.5, x + s + 0.5, y + 2 * s / 3 + 0.5);
        view.gc.strokeLine(x + s / 3 + 0.5, y + 0.5, x + s / 3 + 0.5, y + s + 0.5);
        view.gc.strokeLine(x + 2 * s / 3 + 0.5, y + 0.5, x + 2 * s / 3 + 0.5, y + s + 0.5);
    }

    /**
     * Draws the type-specific icon for a row (node-type icons): {@code <>} element,
     * {@code =} attribute, {@code Abc} text, {@code [..]} CDATA, {@code <!} comment,
     * {@code <?} processing instruction, {@code XML} document.
     */
    private void drawRowIcon(FlatRow row, double x, double cy) {
        FlatRow.RowType type = row.getType();
        view.gc.setLineWidth(1.5);
        double size = 4;
        double cx = x + GridCanvasView.ICON_AREA_WIDTH / 2;

        switch (type) {
            case ELEMENT -> {
                // <> angle brackets in the element colour
                view.gc.setStroke(textElement);
                view.gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                view.gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                view.gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                view.gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
            }
            case ATTRIBUTE -> iconGlyph("=", cx, cy, textAttributeName, ICON_FONT);
            case TEXT -> iconGlyph("Abc", cx, cy, textContent, SMALL_FONT);
            case COMMENT -> iconGlyph("<!", cx, cy, textComment, ICON_FONT);
            case CDATA -> {
                // [ ] square brackets
                view.gc.setStroke(textCdata);
                view.gc.strokeLine(cx - size, cy - size, cx - size + 2, cy - size);
                view.gc.strokeLine(cx - size, cy - size, cx - size, cy + size);
                view.gc.strokeLine(cx - size, cy + size, cx - size + 2, cy + size);
                view.gc.strokeLine(cx + size, cy - size, cx + size - 2, cy - size);
                view.gc.strokeLine(cx + size, cy - size, cx + size, cy + size);
                view.gc.strokeLine(cx + size, cy + size, cx + size - 2, cy + size);
            }
            case PROCESSING_INSTRUCTION -> {
                if (view.formulaOf(row) != null) {
                    iconGlyph("\u0192", cx, cy, accent, ICON_FONT);
                } else {
                    iconGlyph("<?", cx, cy, textPi, ICON_FONT);
                }
            }
            case DOCUMENT -> iconGlyph("XML", cx, cy, textSecondary, SMALL_FONT);
            case GROUP -> iconGlyph("\u2026", cx, cy, textSecondary, ICON_FONT);
            case FILTER -> {
                boolean active = view.viewState.isFilterActive(view.keyOf(row.getParentRow()));
                iconGlyph(active ? "\u25BC" : "\u25BD", cx, cy, active ? accent : textSecondary, ICON_FONT);
            }
            case JSON_OBJECT, JSON_ARRAY, JSON_STRING, JSON_NUMBER, JSON_BOOLEAN, JSON_NULL -> {
                // JSON glyphs: {} [] " # T/F ∅ in the type colour
                Color color = (type == FlatRow.RowType.JSON_OBJECT || type == FlatRow.RowType.JSON_ARRAY)
                        ? textElement : getRowValueColor(type);
                iconGlyph(jsonGlyph(row), cx, cy, color, ICON_FONT);
            }
            default -> { /* other row types draw no icon */ }
        }
    }

    /** Draws a centred text glyph as a row icon. */
    private void iconGlyph(String glyph, double cx, double cy, Color color, Font font) {
        view.gc.setFont(font);
        view.gc.setFill(color);
        view.gc.setTextAlign(TextAlignment.CENTER);
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.fillText(glyph, cx, cy);
        view.gc.setTextAlign(TextAlignment.LEFT);
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

    private void drawEmptyState() {
        view.gc.setFill(textSecondary);
        view.gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 14));
        view.gc.setTextAlign(TextAlignment.CENTER);
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.fillText(view.adapter.emptyStateText(), view.canvas.getWidth() / 2, view.canvas.getHeight() / 2);
    }

    private void drawInfo() {
        if (view.visibleRows.isEmpty()) {
            return;
        }

        String info = String.format("%d rows (%.0f x %.0f px)", view.visibleRows.size(), view.totalWidth, view.totalHeight);

        view.gc.setFill(textSecondary);
        view.gc.setFont(SMALL_FONT);
        view.gc.setTextAlign(TextAlignment.RIGHT);
        view.gc.setTextBaseline(VPos.BOTTOM);
        view.gc.fillText(info, view.canvas.getWidth() - 5, view.canvas.getHeight() - 3);
    }

    // ==================== Table Rendering ====================

    /**
     * Renders an inline repeating-elements table beside its owner row's name cell, cell by
     * cell through the table's drawn grid (so the standard and the flipped orientation share
     * one renderer): corner, column headers, row numbers and data cells.
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
        int drawnRows = table.drawnRowCount();

        // Cull drawn rows and columns to the view (a flipped table has one column per record)
        double viewTop = cullTop - tableY;
        double viewBottom = cullBottom - tableY;
        double viewLeft = cullLeft;
        double viewRight = cullRight;

        view.gc.setTextBaseline(VPos.CENTER);
        for (int dr = 0; dr < drawnRows; dr++) {
            double rowTopRel = table.drawnRowTop(dr);
            double rowHeight = table.drawnRowHeight(dr);
            if (rowTopRel + rowHeight < viewTop) {
                continue;
            }
            if (rowTopRel > viewBottom) {
                break;
            }
            double rowTop = tableY + rowTopRel;
            for (int dc = 0; dc < drawnCols; dc++) {
                double colX = table.drawnColumnX(dc);
                double colWidth = table.drawnColumnWidth(dc);
                if (colX + colWidth < viewLeft) {
                    continue;
                }
                if (colX > viewRight) {
                    break;
                }
                RepeatingElementsTable.DrawnCell cell = table.cellAt(dr, dc);
                switch (cell.kind()) {
                    case CORNER -> drawCornerCell(colX, rowTop, colWidth, rowHeight);
                    case COLUMN_HEADER -> drawColumnHeaderCell(table, table.getColumn(cell.columnIndex()),
                            colX, rowTop, colWidth, rowHeight);
                    case ROW_NUMBER -> {
                        drawRowNumberCell(table, cell.rowIndex(), colX, rowTop, colWidth, rowHeight);
                        if (view.problemsOfMember(tableRow, cell.rowIndex()) != null) {
                            drawProblemMarker(colX + colWidth + 14, rowTop + GridCanvasView.ROW_HEIGHT / 2);
                        }
                    }
                    case DATA -> drawDataCell(table, cell.rowIndex(), cell.columnIndex(),
                            colX, rowTop, colWidth, rowHeight);
                    default -> { }
                }
            }
        }

        // -- Table outer border --
        view.gc.setStroke(tableBorder);
        view.gc.setLineWidth(1);
        view.gc.strokeRect(tableX + 0.5, tableY + 0.5, tableWidth, tableHeight);
    }

    private void drawHeaderCellFrame(double x, double y, double w, double h, Color fill) {
        view.gc.setFill(fill);
        view.gc.fillRect(x, y, w, h);
        view.gc.setStroke(tableBorder);
        view.gc.setLineWidth(1);
        view.gc.strokeRect(x + 0.5, y + 0.5, w, h);
    }

    /** The top-left cell: the flip toggle glyph. */
    private void drawCornerCell(double x, double y, double w, double h) {
        drawHeaderCellFrame(x, y, w, h, tableHeaderBg);
        iconGlyph("⇄", x + w / 2, y + GridCanvasView.ROW_HEIGHT / 2, tableHeaderText, ICON_FONT);
    }

    /**
     * A column header: type icon, name and the sort arrow at the right (faint until the table is
     * sorted by the column); highlighted when sorted by or when the whole column is selected.
     */
    private void drawColumnHeaderCell(RepeatingElementsTable table, RepeatingElementsTable.TableColumn col,
                                      double x, double y, double w, double h) {
        boolean sorted = col != null && table.isSortedBy(col.getName());
        boolean selected = shows(col != null && view.tables.isWholeColumnSelected(table, table.getColumns().indexOf(col)));
        drawHeaderCellFrame(x, y, w, h, sorted || selected ? tableHeaderSelected : tableHeaderBg);
        if (col == null) {
            return;
        }
        view.gc.save();
        view.gc.beginPath();
        view.gc.rect(x, y, w, h);
        view.gc.clip();
        double centerY = y + GridCanvasView.ROW_HEIGHT / 2;
        drawColumnTypeIcon(col, x + RepeatingElementsTable.CELL_PADDING + 6, centerY);
        view.gc.setFill(tableHeaderText);
        view.gc.setFont(ROW_FONT_BOLD);
        view.gc.setTextAlign(TextAlignment.LEFT);
        view.gc.setTextBaseline(VPos.CENTER);
        fillName(col.getName(), x + RepeatingElementsTable.CELL_PADDING + HEADER_ICON_WIDTH, centerY, GridFont.ROW_BOLD);
        view.gc.setFont(SMALL_FONT);
        view.gc.setTextAlign(TextAlignment.CENTER);
        view.gc.setFill(sorted ? tableHeaderText : previewText);
        view.gc.fillText(sorted && !table.isSortAscending() ? "▼" : "▲",
                x + w - RepeatingElementsTable.SORT_ZONE / 2 - 2, centerY);
        view.gc.setTextAlign(TextAlignment.LEFT);
        view.gc.restore();
    }

    /** A row-number cell, highlighted when its row is selected. */
    private void drawRowNumberCell(RepeatingElementsTable table, int rowIndex,
                                   double x, double y, double w, double h) {
        boolean selected = shows(view.tables.selAnchorRow >= 0 ? view.tables.isWholeRowSelected(table, rowIndex)
                : rowIndex == table.getSelectedRowIndex());
        drawHeaderCellFrame(x, y, w, h, selected ? tableHeaderSelected : tableHeaderBg);
        view.gc.setFont(SMALL_FONT);
        view.gc.setFill(tableHeaderText);
        view.gc.setTextAlign(TextAlignment.RIGHT);
        view.gc.setTextBaseline(VPos.CENTER);
        view.gc.fillText(String.valueOf(rowIndex + 1), x + w - RepeatingElementsTable.CELL_PADDING, y + GridCanvasView.ROW_HEIGHT / 2);
        view.gc.setTextAlign(TextAlignment.LEFT);
    }

    /** A data cell: background (selection, hover, missing), border, value, suffix and sub-tree. */
    private void drawDataCell(RepeatingElementsTable table, int rowIndex, int columnIndex,
                              double cellX, double rowTop, double colWidth, double rowHeight) {
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        RepeatingElementsTable.TableColumn col = table.getColumn(columnIndex);
        if (col == null) {
            return;
        }
        String colName = col.getName();
        boolean isComplex = row.hasComplexChild(colName);
        boolean missing = !row.getValues().containsKey(colName) && !isComplex;

        Color bg = null;
        boolean inRange = view.tables.inCellRange(table, rowIndex, columnIndex);
        boolean flashed = table == view.highlightedTable && rowIndex == view.highlightedTableRow
                && (view.highlightedTableColumn < 0 || columnIndex == view.highlightedTableColumn);
        boolean queryHit = false;
        for (int[] hit : view.queryCells.getOrDefault(table, java.util.List.of())) {
            if (hit[0] == rowIndex && (hit[1] < 0 || hit[1] == columnIndex)) {
                queryHit = true;
                break;
            }
        }
        if (shows(flashed)) {
            bg = view.highlightColor;
        } else if (shows(inRange)) {
            bg = selectedBg;
        } else if (shows(queryHit)) {
            bg = view.queryHighlightColor;
        } else if (shows(rowIndex == table.getSelectedRowIndex() && view.tables.selAnchorRow < 0)) {
            bg = tableRowSelected;
        } else if (shows(rowIndex == view.tables.hoveredTableRowIndex && table == view.tables.hoveredTable)) {
            bg = tableRowHover;
        } else if (missing) {
            bg = tableMissingBg;
        }
        if (bg != null) {
            view.gc.setFill(bg);
            view.gc.fillRect(cellX, rowTop, colWidth, rowHeight);
        }
        view.gc.setStroke(tableBorder);
        view.gc.setLineWidth(1);
        view.gc.strokeRect(cellX + 0.5, rowTop + 0.5, colWidth, rowHeight);
        if (inRange && rowIndex == view.tables.selFocusRow && columnIndex == view.tables.selFocusCol) {
            view.gc.setStroke(selectedBorder);
            view.gc.strokeRect(cellX + 1, rowTop + 1, colWidth - 1, rowHeight - 1);
        }

        double cellCenterY = rowTop + GridCanvasView.ROW_HEIGHT / 2;

        // Expand arrow for complex cells (Canvas-drawn polygon)
        if (isComplex) {
            double arrowX = cellX + 8;
            double arrowSize = 3;
            view.gc.setFill(textSecondary);
            if (row.isColumnExpanded(colName)) {
                view.gc.fillPolygon(
                        new double[]{arrowX - arrowSize, arrowX, arrowX + arrowSize},
                        new double[]{cellCenterY - arrowSize / 2, cellCenterY + arrowSize, cellCenterY - arrowSize / 2},
                        3);
            } else {
                view.gc.fillPolygon(
                        new double[]{arrowX - arrowSize / 2, arrowX + arrowSize, arrowX - arrowSize / 2},
                        new double[]{cellCenterY - arrowSize, cellCenterY, cellCenterY + arrowSize},
                        3);
            }
        }

        // Cell value (wrapped; offset for the expand arrow on complex cells)
        RepeatingElementsTable.CellLayout layout = table.getCellLayout(row, colName);
        view.gc.setFont(ROW_FONT);
        view.gc.setTextAlign(TextAlignment.LEFT);
        view.gc.setTextBaseline(VPos.CENTER);
        double textOffsetX = isComplex ? GridMetrics.COMPLEX_ARROW_OFFSET : 0;
        double textX = cellX + RepeatingElementsTable.CELL_PADDING + textOffsetX;

        // Clip to the cell: nothing (a user-narrowed column, an over-wide line) may
        // ever run into the neighbouring cell.
        view.gc.save();
        view.gc.beginPath();
        view.gc.rect(cellX, rowTop, colWidth, rowHeight);
        view.gc.clip();

        if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            view.gc.setFill(textAttributeValue);
        } else if (isComplex) {
            view.gc.setFill(textSecondary);
        } else {
            view.gc.setFill(textContent);
        }
        drawTextBlock(layout.summary(), textX, cellCenterY);

        // The cell element's own attributes (display-only, e.g. ccy=EUR) in the
        // attribute color, right after the value or wrapped on their own lines.
        if (layout.suffix() != null) {
            view.gc.setFill(textAttributeName);
            double lastLineY = cellCenterY + (layout.summary().lineCount() - 1) * GridCanvasView.LINE_HEIGHT;
            if (layout.suffixOnNewLine()) {
                drawTextBlock(layout.suffix(), textX, lastLineY + GridCanvasView.LINE_HEIGHT);
            } else {
                view.gc.fillText(layout.suffix().lastLine(), textX + layout.suffixX(), lastLineY);
            }
        }

        // An opened cell's content: a grid of its own below the summary block
        if (layout.grid() != null) {
            renderBlock(layout.grid(), cellX + RepeatingElementsTable.CELL_PADDING,
                    rowTop + layout.summaryHeight(), colWidth - 2 * RepeatingElementsTable.CELL_PADDING);
        }

        view.gc.restore();
    }

    /** Width reserved for the type icon in a column header. */
    private static final double HEADER_ICON_WIDTH = RepeatingElementsTable.HEADER_ICON_WIDTH;

    /** Draws the {@code =} / {@code <>} / {@code Abc} type icon of a column header. */
    private void drawColumnTypeIcon(RepeatingElementsTable.TableColumn col, double cx, double cy) {
        switch (col.getType()) {
            case ATTRIBUTE -> iconGlyph("=", cx, cy, textAttributeName, ICON_FONT);
            case TEXT_CONTENT -> iconGlyph("Abc", cx, cy, textContent, SMALL_FONT);
            case CHILD_ELEMENT -> {
                double size = 3.5;
                view.gc.setStroke(textElement);
                view.gc.setLineWidth(1.5);
                view.gc.strokeLine(cx - size, cy, cx - size / 2, cy - size);
                view.gc.strokeLine(cx - size, cy, cx - size / 2, cy + size);
                view.gc.strokeLine(cx + size, cy, cx + size / 2, cy - size);
                view.gc.strokeLine(cx + size, cy, cx + size / 2, cy + size);
            }
            default -> { }
        }
    }

    // ==================== Color Helpers ====================

    private Color getRowLabelColor(FlatRow.RowType type) {
        return switch (type) {
            case ELEMENT, JSON_OBJECT, JSON_ARRAY, JSON_STRING, JSON_NUMBER, JSON_BOOLEAN, JSON_NULL -> textElement;
            case ATTRIBUTE -> textAttributeName;
            case TEXT -> textContent;
            case COMMENT -> textComment;
            case CDATA -> textCdata;
            case PROCESSING_INSTRUCTION -> textPi;
            case DOCUMENT -> textSecondary;
            case GROUP -> textSecondary;
            case FILTER -> accent;
        };
    }

    private Color getRowValueColor(FlatRow.RowType type) {
        return switch (type) {
            case ATTRIBUTE -> textAttributeValue;
            case ELEMENT -> textContent;
            case COMMENT -> textComment;
            case CDATA -> textCdata;
            case PROCESSING_INSTRUCTION -> textPi;
            case JSON_STRING -> textJsonString;
            case JSON_NUMBER -> textJsonNumber;
            case JSON_BOOLEAN -> textJsonBoolean;
            case JSON_NULL -> textJsonNull;
            default -> textContent;
        };
    }

    /** Draws the insertion line of a running column-header drag. */
    private void drawHeaderDragIndicator() {
        GridDragSupport.HeaderDrag drag = view.drags.headerDrag;
        if (drag == null || !drag.active || drag.target < 0) {
            return;
        }
        RepeatingElementsTable table = drag.table;
        double[] origin = view.tableOrigin(table);
        if (origin == null) {
            return;
        }
        RepeatingElementsTable.CellRect rect = table.cellRect(0, drag.target);
        double x = origin[0] + (drag.before ? rect.x() : rect.x() + rect.width());
        double top = origin[1];
        view.gc.setStroke(accent);
        view.gc.setLineWidth(2);
        view.gc.strokeLine(x, top, x, top + table.getHeight());
    }

    /** Draws the drop indicator: an insertion line (before/after), a frame (into / value). */
    private void drawDropIndicator() {
        if (view.drags.dropTargetRow == null || view.drags.dropPosition == null) {
            return;
        }
        FlatRow row = view.drags.dropTargetRow;
        GridDragSupport.RowBox box = view.drags.rowBox(row);
        if (box == null) {
            return;
        }
        double top = box.top();
        double right = box.right();
        double labelX = box.ox() + row.getLabelX();
        double valueX = box.ox() + row.getValueX();
        view.gc.setStroke(accent);
        view.gc.setLineWidth(2);
        switch (view.drags.dropPosition) {
            case BEFORE -> view.gc.strokeLine(labelX, top + 1, right, top + 1);
            case AFTER -> {
                double bottom = top + box.span();
                view.gc.strokeLine(labelX, bottom - 1, right, bottom - 1);
            }
            case INTO -> view.gc.strokeRect(labelX + 1, top + 1, valueX - labelX - 2, Math.max(GridCanvasView.ROW_HEIGHT, box.span()) - 2);
            case VALUE -> view.gc.strokeRect(valueX + 1, top + 1, right - valueX - 2, Math.max(GridCanvasView.ROW_HEIGHT, box.own()) - 2);
            default -> { }
        }
    }

    /**
     * Draws an opened cell's grid with its block origin at ({@code ox}, {@code oy}) in the current
     * coordinates: its rows, then the embedded tables of its rows (whose cells may open grids again).
     *
     * @param minRight the cell's inner width: value cells reach at least that far
     */
    private void renderBlock(GridBlock grid, double ox, double oy, double minRight) {
        GridBlockLayout.Result g = grid.geometry() != null ? grid.geometry() : grid.geometry(view.cellMeasure);
        double top0 = cullTop;
        double bottom0 = cullBottom;
        double left0 = cullLeft;
        double right0 = cullRight;
        view.gc.save();
        view.gc.translate(ox, oy);
        cullTop = top0 - oy;
        cullBottom = bottom0 - oy;
        cullLeft = left0 - ox;
        cullRight = right0 - ox;
        try {
            List<FlatRow> visible = g.visible();
            double right = Math.max(g.width(), minRight);
            for (int i = 0; i < visible.size(); i++) {
                int last = g.lastDescendant()[i];
                double top = g.y()[i];
                double span = g.y()[last] + g.height()[last] - top;
                if (top + span < cullTop || top > cullBottom) {
                    continue;
                }
                drawRowAt(visible.get(i), top, g.height()[i], span, right);
            }
            for (int i = 0; i < visible.size(); i++) {
                FlatRow row = visible.get(i);
                if (row.hasRepeatingTable() && row.isExpanded()) {
                    double top = g.y()[i];
                    if (top + g.height()[i] >= cullTop && top <= cullBottom) {
                        renderInlineTable(row, top);
                    }
                }
            }
        } finally {
            cullTop = top0;
            cullBottom = bottom0;
            cullLeft = left0;
            cullRight = right0;
            view.gc.restore();
        }
    }
}
