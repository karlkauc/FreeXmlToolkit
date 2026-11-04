package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken;

/**
 * Renders the cards of the graphic XSD view: one rounded surface per node with a kind-tinted
 * icon tile, the name, the type line and a cardinality pill (Figma "07.6b"), compositors as small
 * symbol boxes, and orthogonal "comb" connectors between them.
 * <p>
 * Occurrence information stays visible in the outline: optional cards are dashed, repeating cards
 * carry a second, offset outline behind them.
 *
 * @since 2.0
 */
public class XsdNodeRenderer {

    /** Height of a card without constraint rows. */
    static final double CARD_HEIGHT = 52;
    private static final double MIN_NODE_WIDTH = 160;
    private static final double CARD_RADIUS = 8;
    private static final double TILE_SIZE = 24;
    private static final double TILE_RADIUS = 6;
    private static final double TILE_X = 10;
    private static final double TILE_ICON_SIZE = 13;
    private static final double TEXT_X = 44;
    private static final double NAME_Y = 11;
    private static final double TYPE_Y = 28;
    private static final double TEXT_RIGHT_PADDING = 12;
    private static final double PILL_HEIGHT = 16;
    private static final double PILL_PADDING = 6;
    private static final double PILL_GAP = 8;
    /** Offset of the second outline that marks a repeating card. */
    private static final double STACK_OFFSET = 3;

    private static final double COMPOSITOR_SIZE = 30;
    private static final double COMPOSITOR_RADIUS = 7;
    private static final double COMPOSITOR_ICON_SIZE = 16;

    private static final double EXPAND_BUTTON_SIZE = 18;
    private static final double EXPAND_BUTTON_RIGHT = 6;
    private static final double BADGE_RIGHT_MARGIN = 10;
    private static final double BADGE_BUTTON_GAP = 6;

    private static final double CONNECTOR_WIDTH = 2;
    /** Distance of the vertical connector bus from the left edge of the children. */
    private static final double BUS_TO_CHILD = 24;
    /** Horizontal gap card → compositor, compositor → cards, card → card. */
    private static final double GAP_TO_COMPOSITOR = 28;
    private static final double GAP_FROM_COMPOSITOR = 38;
    private static final double GAP_CARD_TO_CARD = 44;
    /** Vertical gap between the children of a compositor, and between the children of a card. */
    private static final double GAP_IN_COMPOSITOR = 14;
    private static final double GAP_IN_CARD = 20;

    /** Rows a card lists before the rest collapse into a "+N more" line. */
    static final int MAX_CONSTRAINT_ROWS = 4;
    private static final double CONSTRAINT_ROW_HEIGHT = 18;
    private static final double CONSTRAINT_SECTION_PADDING = 6;
    private static final double CONSTRAINT_LABEL_WIDTH = 42;
    private static final double CONSTRAINT_LABEL_HEIGHT = 14;
    private static final double CONSTRAINT_LABEL_GAP = 6;
    private static final double CONSTRAINT_LEFT = 10;
    private static final double CONSTRAINT_RIGHT = 14;
    /** Room for the include badge in a card's bottom-left corner. */
    private static final double INCLUDE_BADGE_CLEARANCE = 14;

    private final Font nameFont = weighted(DesignTokens.FONT_FAMILY_UI, "SemiBold", FontWeight.BOLD, 12.5);
    private final Font typeFont = Font.font(DesignTokens.FONT_FAMILY_MONO, 10.5);
    private final Font pillFont = weighted(DesignTokens.FONT_FAMILY_MONO, "Medium", FontWeight.BOLD, 10);
    private final Font compositorLabelFont = Font.font(DesignTokens.FONT_FAMILY_MONO, 9);
    private final Font constraintFont = Font.font(DesignTokens.FONT_FAMILY_MONO, 10);
    private final Font constraintLabelFont = Font.font(DesignTokens.FONT_FAMILY_MONO, FontWeight.BOLD, 9);
    private final Font badgeFont = Font.font(DesignTokens.FONT_FAMILY_UI, FontWeight.BOLD, 7);
    private final javafx.scene.text.Text textMeasurer = new javafx.scene.text.Text();

    /**
     * A font in an intermediate weight. The bundled Inter / JetBrains Mono files register their
     * SemiBold and Medium cuts as families of their own ("Inter SemiBold"), so
     * {@code Font.font("Inter", FontWeight.SEMI_BOLD, …)} silently falls back to Regular.
     *
     * @param family   the base family
     * @param cut      the cut's family suffix, e.g. "SemiBold"
     * @param fallback the weight to use on the base family when the cut is not installed
     */
    static Font weighted(String family, String cut, FontWeight fallback, double size) {
        String cutFamily = family + " " + cut;
        return Font.getFamilies().contains(cutFamily)
                ? Font.font(cutFamily, size)
                : Font.font(family, fallback, size);
    }

    /** Whether cards list their constraints; off, they are drawn as if they had none. */
    private boolean showConstraints = true;

    // Text width cache: key = "fontName:fontSize:text", value = measured width
    // Avoids expensive Text.getBoundsInLocal() calls on repeated rendering (zoom, scroll, redraw)
    private final Map<String, Double> textWidthCache = new ConcurrentHashMap<>();

    /**
     * Dark-mode flag: every color is a design token resolved for this theme. Initialized from the
     * current theme; owners with a lifecycle (XsdGraphView) update it on switches.
     */
    private boolean darkMode = DesignTokens.currentTheme() == DesignTokens.Theme.DARK;

    /** Switches the renderer between the light and dark drawing palette. */
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    /**
     * Sets whether cards list their identity constraints and assertions.
     *
     * @param show true to list them; takes effect at the next layout
     */
    public void setShowConstraints(boolean show) {
        this.showConstraints = show;
    }

    /**
     * Clears the text width cache. Call when fonts change or schema is reloaded.
     */
    public void clearTextWidthCache() {
        textWidthCache.clear();
    }

    // ------------------------------------------------------------------ palette

    private Color token(ColorToken token) {
        return token.color(darkMode ? DesignTokens.Theme.DARK : DesignTokens.Theme.LIGHT);
    }

    private Color surface() {
        return token(ColorToken.BG_SURFACE);
    }

    /**
     * Card, compositor, button and connector outlines. Dark keeps the Figma border token; on the
     * light canvas that token is nearly invisible (1.3:1), so light mixes the muted text colour in.
     */
    private Color border() {
        return darkMode ? token(ColorToken.BORDER_DEFAULT)
                : token(ColorToken.TEXT_MUTED).interpolate(surface(), 0.45);
    }

    /** The type line and other secondary text: muted in dark (Figma), secondary in light (contrast). */
    private Color secondaryText() {
        return token(darkMode ? ColorToken.TEXT_MUTED : ColorToken.TEXT_SECONDARY);
    }

    /** An accent used as text on a faint tint of itself: darkened in light mode to stay readable. */
    private Color accentText(Color tint) {
        return darkMode ? tint : tint.interpolate(Color.BLACK, 0.28);
    }

    /** The accent of a node kind: icon, pill and selected tile take it. */
    Color kindTint(NodeWrapperType type) {
        return token(switch (type) {
            case ELEMENT -> ColorToken.PRIMARY;
            case ATTRIBUTE -> ColorToken.TEAL;
            case SIMPLE_TYPE -> ColorToken.SUCCESS;
            case SCHEMA -> ColorToken.PURPLE;
            case ENUMERATION -> ColorToken.INDIGO;
            case COMMENT -> ColorToken.TEXT_MUTED;
            default -> ColorToken.NEUTRAL;
        });
    }

    /** The tile behind a card's icon: the subtle primary background for elements, else the tint, faint. */
    private Color tileFill(NodeWrapperType type) {
        if (type == NodeWrapperType.ELEMENT) {
            return token(ColorToken.BG_SUBTLE);
        }
        return withAlpha(kindTint(type), darkMode ? 0.18 : 0.14);
    }

    private static Color withAlpha(Color color, double alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    // ------------------------------------------------------------------ card content

    /**
     * The symbol of a card: by structure, not by datatype — a card with content of its own
     * (children) shows the hierarchy symbol, a card with simple content the code symbol.
     */
    static String cardIcon(VisualNode node) {
        return switch (node.getType()) {
            case ELEMENT -> node.hasChildren() ? "bi-diagram-3" : "bi-code-slash";
            case ATTRIBUTE -> "bi-at";
            case COMPLEX_TYPE -> "bi-diagram-3";
            case SIMPLE_TYPE -> "bi-code-slash";
            case GROUP -> "bi-folder";
            case SCHEMA -> "bi-hdd-stack";
            case ENUMERATION -> "bi-list-check";
            case COMMENT -> "bi-chat-left-quote";
            case SEQUENCE -> "bi-list-nested";
            case CHOICE -> "bi-signpost-split";
            case ALL -> "bi-collection";
        };
    }

    /** The second line of a card: the type, or what kind of node it is when it has none. */
    static String typeLine(VisualNode node) {
        String detail = node.getDetail();
        return detail != null && !detail.isBlank() ? detail : kindName(node.getType());
    }

    /** The XSD name of a node kind, as the type line and the title chip show it. */
    static String kindName(NodeWrapperType type) {
        return switch (type) {
            case ELEMENT -> "element";
            case ATTRIBUTE -> "attribute";
            case COMPLEX_TYPE -> "complexType";
            case SIMPLE_TYPE -> "simpleType";
            case GROUP -> "group";
            case SCHEMA -> "schema";
            case ENUMERATION -> "enumeration";
            case COMMENT -> "comment";
            case SEQUENCE -> "sequence";
            case CHOICE -> "choice";
            case ALL -> "all";
        };
    }

    /** Optional cards are drawn dashed: minOccurs 0, or an attribute that is not required. */
    static boolean isOptional(VisualNode node) {
        if (node.getType() == NodeWrapperType.ATTRIBUTE) {
            return node.getModelObject() instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute
                    && !"required".equals(attribute.getUse());
        }
        if (node.getType() != NodeWrapperType.ELEMENT && node.getType() != NodeWrapperType.GROUP) {
            return false;
        }
        return node.getMinOccurs() == 0;
    }

    /** Repeating cards get a second outline behind them: maxOccurs above 1 or unbounded. */
    static boolean isRepeating(VisualNode node) {
        if (node.getType() != NodeWrapperType.ELEMENT && node.getType() != NodeWrapperType.GROUP) {
            return false;
        }
        return node.getMaxOccurs() > 1 || node.getMaxOccurs() == -1;
    }

    // ------------------------------------------------------------------ measuring

    /**
     * Calculates the width a card needs: the tile, the wider of name (+ pill) and type line,
     * the constraint rows, and the expand button; at least {@value #MIN_NODE_WIDTH}.
     */
    public double calculateNodeWidth(VisualNode node) {
        if (isCompositorNode(node)) {
            return COMPOSITOR_SIZE;
        }
        String label = node.getLabel() != null ? node.getLabel() : "";
        double nameLine = estimateTextWidth(label, nameFont);
        String pill = node.getCardinalityBadge();
        if (pill != null && !pill.isEmpty()) {
            nameLine += PILL_GAP + pillWidth(pill);
        }
        double typeLine = estimateTextWidth(typeLine(node), typeFont);
        double right = node.hasChildren()
                ? BADGE_BUTTON_GAP + EXPAND_BUTTON_SIZE + EXPAND_BUTTON_RIGHT
                : TEXT_RIGHT_PADDING;
        double content = TEXT_X + Math.max(nameLine, typeLine) + right;
        return Math.max(MIN_NODE_WIDTH, Math.max(content, constraintRowsWidth(node)));
    }

    private double pillWidth(String pill) {
        return estimateTextWidth(pill, pillFont) + PILL_PADDING * 2;
    }

    /** @return the lines a card's constraint section needs: its rows, plus one for "+N more" */
    private int constraintLines(VisualNode node) {
        if (!showConstraints || isCompositorNode(node)) {
            return 0;
        }
        int rows = node.getConstraints().size();
        return rows > MAX_CONSTRAINT_ROWS ? MAX_CONSTRAINT_ROWS + 1 : rows;
    }

    private double constraintRowsWidth(VisualNode node) {
        if (constraintLines(node) == 0) {
            return 0;
        }
        double widest = 0;
        for (ConstraintRow row : node.getConstraints().subList(0,
                Math.min(MAX_CONSTRAINT_ROWS, node.getConstraints().size()))) {
            widest = Math.max(widest, estimateTextWidth(row.text(), constraintFont));
        }
        return CONSTRAINT_LEFT + CONSTRAINT_LABEL_WIDTH + CONSTRAINT_LABEL_GAP + widest + CONSTRAINT_RIGHT;
    }

    /**
     * The details of a card's constraints, for a pointer over its constraint section.
     *
     * @param node the card under the pointer, or null
     * @param y    the pointer's position in diagram space
     * @return the text to show, or null if the pointer is not over a constraint section
     */
    String constraintTooltipAt(VisualNode node, double y) {
        if (node == null || constraintLines(node) == 0 || y < node.getY() + CARD_HEIGHT) {
            return null;
        }
        return node.getConstraints().stream().map(ConstraintRow::tooltip)
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    /**
     * Calculates the complete card height: the two text lines, plus the constraint section and
     * the include badge's room when the card lists constraints.
     */
    public double calculateNodeHeight(VisualNode node) {
        if (isCompositorNode(node)) {
            return COMPOSITOR_SIZE;
        }
        int lines = constraintLines(node);
        if (lines == 0) {
            return CARD_HEIGHT;
        }
        return CARD_HEIGHT + lines * CONSTRAINT_ROW_HEIGHT + CONSTRAINT_SECTION_PADDING * 2
                + (node.isFromInclude() ? INCLUDE_BADGE_CLEARANCE : 0);
    }

    private static boolean isCompositorNode(VisualNode node) {
        return node.getType() == NodeWrapperType.SEQUENCE
                || node.getType() == NodeWrapperType.CHOICE
                || node.getType() == NodeWrapperType.ALL;
    }

    /**
     * Estimates text width for layout purposes.
     */
    private double estimateTextWidth(String text, Font font) {
        String cacheKey = font.getName() + ":" + font.getSize() + ":" + text;
        return textWidthCache.computeIfAbsent(cacheKey, k -> {
            textMeasurer.setFont(font);
            textMeasurer.setText(text);
            return textMeasurer.getBoundsInLocal().getWidth();
        });
    }

    // ------------------------------------------------------------------ layout metrics

    /** @return the horizontal gap between a card and its children's column */
    public double horizontalGap(VisualNode parent, VisualNode child) {
        if (isCompositorNode(parent)) {
            return GAP_FROM_COMPOSITOR;
        }
        return isCompositorNode(child) ? GAP_TO_COMPOSITOR : GAP_CARD_TO_CARD;
    }

    /** @return the vertical gap between two children of the given node */
    public double siblingGap(VisualNode parent) {
        return isCompositorNode(parent) ? GAP_IN_COMPOSITOR : GAP_IN_CARD;
    }

    public double getCompositorSize() {
        return COMPOSITOR_SIZE;
    }

    /** @return where connectors meet a card: the middle of its two text lines */
    static double anchorY(VisualNode node) {
        double height = node.getHeight();
        return node.getY() + (isCompositorNode(node) ? height : Math.min(height, CARD_HEIGHT)) / 2;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Renders a visual node for an XSD element.
     *
     * @param gc   Graphics context
     * @param node The visual node to render
     * @param x    X position
     * @param y    Y position
     */
    public void renderNode(GraphicsContext gc, VisualNode node, double x, double y) {
        if (isCompositorNode(node)) {
            renderCompositorSymbol(gc, node, x, y);
        } else {
            renderRegularNode(gc, node, x, y);
        }
    }

    /** @return the left edge of a card's expand button, at the right end of the card */
    static double expandButtonX(double x, double width) {
        return x + width - EXPAND_BUTTON_SIZE - EXPAND_BUTTON_RIGHT;
    }

    /**
     * @return the left edge of a card's cardinality pill: at the right end of the card, or left of
     *         the expand button, whose room {@code calculateNodeWidth} has already reserved
     */
    static double badgeX(double x, double width, double badgeWidth, boolean hasExpandButton) {
        double right = hasExpandButton ? expandButtonX(x, width) - BADGE_BUTTON_GAP : x + width - BADGE_RIGHT_MARGIN;
        return right - badgeWidth;
    }

    /**
     * Renders a card: shadow, stack outline (repeating), surface with border (dashed when optional),
     * icon tile, name, pill, type line, constraint rows, expand button and the state overlays.
     */
    private void renderRegularNode(GraphicsContext gc, VisualNode node, double x, double y) {
        double width = node.getWidth();
        double height = node.getHeight();
        if (width <= 0 || height <= 0 || width > 4000) {
            return;
        }
        boolean selected = node.isSelected();
        Color tint = kindTint(node.getType());
        Color strokeColor = selected ? token(ColorToken.PRIMARY)
                : node.isHovered() ? withAlpha(token(ColorToken.PRIMARY), 0.55) : border();
        double strokeWidth = selected ? 2 : 1;

        gc.save();
        if (isRepeating(node)) {
            // A stronger stroke than the card's own: the stack must read as "more than one" at a glance
            drawCardShape(gc, x + STACK_OFFSET, y + STACK_OFFSET, width, height, surface(),
                    withAlpha(token(ColorToken.TEXT_MUTED), 0.7), 1, false);
        }
        // Soft shadow: a darker shape one pixel below (a DropShadow effect costs too much per card)
        gc.setFill(Color.rgb(0, 0, 0, darkMode ? 0.18 : 0.08));
        gc.fillRoundRect(x, y + 1, width, height + 1, CARD_RADIUS * 2, CARD_RADIUS * 2);
        drawCardShape(gc, x, y, width, height,
                selected ? token(ColorToken.BG_SUBTLE) : surface(), strokeColor, strokeWidth, isOptional(node));

        // Icon tile
        double tileY = y + (CARD_HEIGHT - TILE_SIZE) / 2;
        gc.setFill(selected ? tint : tileFill(node.getType()));
        gc.fillRoundRect(x + TILE_X, tileY, TILE_SIZE, TILE_SIZE, TILE_RADIUS * 2, TILE_RADIUS * 2);
        double iconOffset = (TILE_SIZE - TILE_ICON_SIZE) / 2;
        SvgIconRenderer.renderIcon(gc, cardIcon(node), x + TILE_X + iconOffset, tileY + iconOffset,
                TILE_ICON_SIZE, selected ? token(ColorToken.ON_PRIMARY) : accentText(tint));

        // Name and type line
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        gc.setFont(nameFont);
        gc.setFill(selected ? token(ColorToken.PRIMARY) : token(ColorToken.TEXT_PRIMARY));
        gc.fillText(node.getLabel() != null ? node.getLabel() : "", x + TEXT_X, y + NAME_Y);
        gc.setFont(typeFont);
        gc.setFill(secondaryText());
        gc.fillText(typeLine(node), x + TEXT_X, y + TYPE_Y);

        // Cardinality pill
        String pill = node.getCardinalityBadge();
        if (pill != null && !pill.isEmpty()) {
            double pillWidth = pillWidth(pill);
            double pillX = badgeX(x, width, pillWidth, node.hasChildren());
            double pillY = y + NAME_Y - 1;
            gc.setFill(withAlpha(tint, darkMode ? 0.2 : 0.14));
            gc.fillRoundRect(pillX, pillY, pillWidth, PILL_HEIGHT, PILL_HEIGHT, PILL_HEIGHT);
            gc.setFill(accentText(tint));
            gc.setFont(pillFont);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(pill, pillX + pillWidth / 2, pillY + PILL_HEIGHT / 2);
        }

        renderConstraintRows(gc, node, x, y, width);
        gc.restore();

        if (node.isFocused()) {
            gc.setStroke(token(ColorToken.INDIGO));
            gc.setLineWidth(2);
            gc.setLineDashes(4, 4);
            gc.strokeRoundRect(x - 4, y - 4, width + 8, height + 8, (CARD_RADIUS + 4) * 2, (CARD_RADIUS + 4) * 2);
            gc.setLineDashes(null);
        }
        if (node.isDropTarget()) {
            renderDropIndicator(gc, node, x, y, width, height);
        }
        if (node.isDragging()) {
            gc.setGlobalAlpha(0.45);
            gc.setFill(token(ColorToken.BG_SUBTLE));
            gc.fillRoundRect(x, y, width, height, CARD_RADIUS * 2, CARD_RADIUS * 2);
            gc.setGlobalAlpha(1.0);
        }
        if (node.isInEditMode()) {
            double size = 8;
            double badgeX = x + width - size - 4;
            double badgeY = y + 4;
            gc.setFill(token(ColorToken.WARNING));
            gc.fillOval(badgeX, badgeY, size, size);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            double cx = badgeX + size / 2;
            double cy = badgeY + size / 2;
            gc.strokeLine(cx - 2, cy + 2, cx + 2, cy - 2);
        }
        if (node.isFromInclude()) {
            double size = 10;
            double badgeX = x + 4;
            double badgeY = y + height - size - 4;
            gc.setFill(token(ColorToken.INFO));
            gc.fillRoundRect(badgeX, badgeY, size, size, 3, 3);
            gc.setFill(Color.WHITE);
            gc.setFont(badgeFont);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("I", badgeX + size / 2, badgeY + size / 2);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.TOP);
        }
        if (node.hasChildren()) {
            drawExpandButton(gc, node, x, y, width);
        }
    }

    private void drawCardShape(GraphicsContext gc, double x, double y, double width, double height,
                               Color fill, Color stroke, double strokeWidth, boolean dashed) {
        gc.setFill(fill);
        gc.fillRoundRect(x, y, width, height, CARD_RADIUS * 2, CARD_RADIUS * 2);
        gc.setStroke(stroke);
        gc.setLineWidth(strokeWidth);
        gc.setLineDashes(dashed ? new double[]{4, 3} : null);
        double inset = strokeWidth / 2;
        gc.strokeRoundRect(x + inset, y + inset, width - strokeWidth, height - strokeWidth,
                CARD_RADIUS * 2 - strokeWidth, CARD_RADIUS * 2 - strokeWidth);
        gc.setLineDashes(null);
    }

    /** Draws the section that lists a card's constraints below its text lines. */
    private void renderConstraintRows(GraphicsContext gc, VisualNode node, double x, double y, double width) {
        int lines = constraintLines(node);
        if (lines == 0) {
            return;
        }
        double top = y + CARD_HEIGHT;
        gc.setStroke(border());
        gc.setLineWidth(1);
        gc.strokeLine(x + CONSTRAINT_LEFT, top, x + width - CONSTRAINT_LEFT, top);

        gc.setTextBaseline(VPos.CENTER);
        double rowY = top + CONSTRAINT_SECTION_PADDING;
        java.util.List<ConstraintRow> rows = node.getConstraints();
        for (int i = 0; i < Math.min(MAX_CONSTRAINT_ROWS, rows.size()); i++) {
            renderConstraintRow(gc, rows.get(i), x + CONSTRAINT_LEFT, rowY);
            rowY += CONSTRAINT_ROW_HEIGHT;
        }
        if (rows.size() > MAX_CONSTRAINT_ROWS) {
            gc.setFill(secondaryText());
            gc.setFont(constraintFont);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("+" + (rows.size() - MAX_CONSTRAINT_ROWS) + " more", x + CONSTRAINT_LEFT,
                    rowY + CONSTRAINT_ROW_HEIGHT / 2);
        }
        gc.setTextBaseline(VPos.TOP);
    }

    private void renderConstraintRow(GraphicsContext gc, ConstraintRow row, double x, double rowY) {
        Color color = constraintColor(row.kind());
        double labelY = rowY + (CONSTRAINT_ROW_HEIGHT - CONSTRAINT_LABEL_HEIGHT) / 2;
        if (row.incoming()) {
            // declared on an ancestor: an outlined label and muted text set it apart from own rows
            gc.setStroke(withAlpha(color, 0.6));
            gc.setLineWidth(1);
            gc.strokeRoundRect(x + 0.5, labelY + 0.5, CONSTRAINT_LABEL_WIDTH - 1, CONSTRAINT_LABEL_HEIGHT - 1, 4, 4);
        } else {
            gc.setFill(withAlpha(color, darkMode ? 0.2 : 0.12));
            gc.fillRoundRect(x, labelY, CONSTRAINT_LABEL_WIDTH, CONSTRAINT_LABEL_HEIGHT, 4, 4);
        }

        gc.setFill(accentText(color));
        gc.setFont(constraintLabelFont);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(row.kind().label(), x + CONSTRAINT_LABEL_WIDTH / 2, rowY + CONSTRAINT_ROW_HEIGHT / 2);

        gc.setFill(token(row.incoming() ? ColorToken.TEXT_MUTED : ColorToken.TEXT_SECONDARY));
        gc.setFont(constraintFont);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(row.text(), x + CONSTRAINT_LABEL_WIDTH + CONSTRAINT_LABEL_GAP, rowY + CONSTRAINT_ROW_HEIGHT / 2);
    }

    /** Label color per kind, readable on the card surface of either theme. */
    private Color constraintColor(ConstraintRow.Kind kind) {
        return switch (kind) {
            case KEY -> token(ColorToken.WARNING);
            case UNIQUE -> token(ColorToken.PURPLE);
            case KEYREF -> token(ColorToken.PRIMARY);
            case ASSERT -> token(ColorToken.TEAL);
        };
    }

    /**
     * Draws the drop feedback of a card: an insertion line above (before) or below (after) it,
     * a frame for a drop into it, and a red frame when the drop is refused.
     */
    private void renderDropIndicator(GraphicsContext gc, VisualNode node, double x, double y,
                                     double width, double height) {
        XsdDropPlanner.Position position = node.getDropPosition();
        Color into = token(ColorToken.SUCCESS);
        if (node.isDropBlocked() || position == null || position == XsdDropPlanner.Position.INTO) {
            gc.setStroke(node.isDropBlocked() ? token(ColorToken.DANGER) : into);
            gc.setLineWidth(3);
            gc.strokeRoundRect(x - 4, y - 4, width + 8, height + 8, (CARD_RADIUS + 4) * 2, (CARD_RADIUS + 4) * 2);
            if (!node.isDropBlocked()) {
                gc.setGlobalAlpha(0.12);
                gc.setFill(into);
                gc.fillRoundRect(x, y, width, height, CARD_RADIUS * 2, CARD_RADIUS * 2);
                gc.setGlobalAlpha(1.0);
            }
            return;
        }
        Color line = token(ColorToken.PRIMARY);
        double lineY = position == XsdDropPlanner.Position.BEFORE ? y - 6 : y + height + 6;
        gc.setStroke(line);
        gc.setFill(line);
        gc.setLineWidth(2.5);
        gc.strokeLine(x + 4, lineY, x + width, lineY);
        gc.fillOval(x - 1, lineY - 4, 8, 8);
    }

    /**
     * Renders a compositor (sequence, choice, all) as a small rounded box with its symbol; a
     * cardinality other than 1..1 is written below the box.
     */
    private void renderCompositorSymbol(GraphicsContext gc, VisualNode node, double x, double y) {
        double size = node.getWidth();
        boolean selected = node.isSelected();
        double strokeWidth = selected ? 2 : 1.5;
        Color stroke = selected ? token(ColorToken.PRIMARY)
                : node.isHovered() ? withAlpha(token(ColorToken.PRIMARY), 0.55) : border();
        double arc = COMPOSITOR_RADIUS * 2;

        gc.setFill(selected ? token(ColorToken.BG_SUBTLE) : surface());
        gc.fillRoundRect(x, y, size, size, arc, arc);
        gc.setStroke(stroke);
        gc.setLineWidth(strokeWidth);
        double inset = strokeWidth / 2;
        gc.strokeRoundRect(x + inset, y + inset, size - strokeWidth, size - strokeWidth, arc, arc);

        double iconOffset = (size - COMPOSITOR_ICON_SIZE) / 2;
        SvgIconRenderer.renderIcon(gc, cardIcon(node), x + iconOffset, y + iconOffset, COMPOSITOR_ICON_SIZE,
                selected ? token(ColorToken.PRIMARY) : token(ColorToken.TEXT_SECONDARY));

        String cardinality = getCardinalityString(node);
        if (!cardinality.isEmpty()) {
            gc.setFont(compositorLabelFont);
            gc.setFill(secondaryText());
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.TOP);
            gc.fillText(cardinality, x + size / 2, y + size + 2);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        if (node.isFocused()) {
            gc.setStroke(token(ColorToken.INDIGO));
            gc.setLineWidth(2);
            gc.setLineDashes(4, 4);
            gc.strokeRoundRect(x - 3, y - 3, size + 6, size + 6, arc + 6, arc + 6);
            gc.setLineDashes(null);
        }
        // Compositors only take drops into them
        if (node.isDropTarget()) {
            gc.setStroke(node.isDropBlocked() ? token(ColorToken.DANGER) : token(ColorToken.SUCCESS));
            gc.setLineWidth(3);
            gc.strokeRoundRect(x - 4, y - 4, size + 8, size + 8, arc + 8, arc + 8);
        }
        if (node.isDragging()) {
            gc.setGlobalAlpha(0.45);
            gc.setFill(token(ColorToken.BG_SUBTLE));
            gc.fillRoundRect(x, y, size, size, arc, arc);
            gc.setGlobalAlpha(1.0);
        }
        // No expand button for compositor symbols - they auto-expand
    }

    /**
     * Draws the expand/collapse button: a chevron pointing right (collapsed) or left (expanded),
     * a dot for children not loaded yet, three dots while they load.
     */
    private void drawExpandButton(GraphicsContext gc, VisualNode node, double x, double y, double nodeWidth) {
        double btnX = expandButtonX(x, nodeWidth);
        double btnY = y + (CARD_HEIGHT - EXPAND_BUTTON_SIZE) / 2;
        node.setExpandButtonBounds(btnX, btnY, EXPAND_BUTTON_SIZE, EXPAND_BUTTON_SIZE);

        boolean loading = node.isLoadingChildren();
        gc.setFill(surface());
        gc.fillRoundRect(btnX, btnY, EXPAND_BUTTON_SIZE, EXPAND_BUTTON_SIZE, 8, 8);
        gc.setStroke(loading ? token(ColorToken.WARNING) : border());
        gc.setLineWidth(1);
        gc.strokeRoundRect(btnX + 0.5, btnY + 0.5, EXPAND_BUTTON_SIZE - 1, EXPAND_BUTTON_SIZE - 1, 7, 7);

        double cx = btnX + EXPAND_BUTTON_SIZE / 2;
        double cy = btnY + EXPAND_BUTTON_SIZE / 2;
        if (loading) {
            gc.setFill(token(ColorToken.WARNING));
            double dot = 2.5;
            for (int i = -1; i <= 1; i++) {
                gc.fillOval(cx + i * 4 - dot / 2, cy - dot / 2, dot, dot);
            }
            return;
        }
        double iconSize = 11;
        SvgIconRenderer.renderIcon(gc, node.isExpanded() ? "bi-chevron-left" : "bi-chevron-right",
                cx - iconSize / 2, cy - iconSize / 2, iconSize, token(ColorToken.TEXT_SECONDARY));
        if (node.hasUnloadedChildren() && !node.isExpanded()) {
            gc.setFill(token(ColorToken.PRIMARY));
            gc.fillOval(btnX + EXPAND_BUTTON_SIZE - 5, btnY + 1, 4, 4);
        }
    }

    /**
     * Draws the connector between a node and one of its children: from the parent's right edge to
     * a vertical bus just left of the children, along the bus, and into the child — the comb shape
     * all siblings share.
     */
    public void renderConnection(GraphicsContext gc, VisualNode parent, VisualNode child) {
        double startX = parent.getX() + parent.getWidth();
        double startY = anchorY(parent);
        double endX = child.getX();
        double endY = anchorY(child);
        double busX = endX - BUS_TO_CHILD;
        if (busX <= startX) {
            busX = startX + (endX - startX) / 2;
        }
        gc.setStroke(border());
        gc.setLineWidth(CONNECTOR_WIDTH);
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineJoin(StrokeLineJoin.MITER);
        if (Math.abs(startY - endY) < 0.5) {
            gc.strokeLine(startX, startY, endX, endY);
        } else {
            gc.strokePolyline(new double[]{startX, busX, busX, endX}, new double[]{startY, startY, endY, endY}, 4);
        }
    }

    /**
     * Generates the cardinality string for display based on minOccurs and maxOccurs.
     * <p>
     * Examples: 1..1 → "" (default, not shown), 0..1 → "0..1", 1..unbounded → "1..*",
     * 0..unbounded → "0..*", 2..5 → "2..5", 3..3 → "3".
     */
    static String getCardinalityString(VisualNode node) {
        int minOccurs = node.getMinOccurs();
        int maxOccurs = node.getMaxOccurs();
        if (minOccurs == 1 && maxOccurs == 1) {
            return "";
        }
        StringBuilder cardinality = new StringBuilder();
        cardinality.append(minOccurs);
        if (maxOccurs == Integer.MAX_VALUE || maxOccurs == -1) {
            cardinality.append("..*");
        } else if (maxOccurs != minOccurs) {
            cardinality.append("..").append(maxOccurs);
        }
        return cardinality.toString();
    }

    /**
     * Represents node type for rendering.
     */
    public enum NodeWrapperType {
        SCHEMA,
        ELEMENT,
        ATTRIBUTE,
        COMPLEX_TYPE,
        SIMPLE_TYPE,
        GROUP,
        ENUMERATION,
        SEQUENCE,      // xs:sequence compositor
        CHOICE,        // xs:choice compositor
        ALL,           // xs:all compositor
        COMMENT        // XML comment (<!-- ... -->)
    }

    /**
     * Visual representation of a node in the graph.
     * Now supports dynamic updates from the model layer.
     */
    public static class VisualNode {
        // Mutable fields - can be updated when model changes
        private String label;
        private String detail;
        private int minOccurs;
        private int maxOccurs;

        // Immutable fields
        private final NodeWrapperType type;
        private final Object modelObject;  // Can be XsdNode (new) or XsdModel objects (old)
        private final VisualNode parent;
        private final java.util.List<VisualNode> children = new java.util.ArrayList<>();

        // Mutable callback - can be updated when tree is rebuilt
        private Runnable onModelChangeCallback;

        private double x, y, width, height;
        private double expandBtnX, expandBtnY, expandBtnW, expandBtnH;
        private boolean expanded = false;

        // Visual feedback states
        private boolean selected = false;
        private boolean hovered = false;
        private boolean focused = false;
        private boolean inEditMode = false;
        private boolean dragging = false;
        private boolean dropTarget = false;
        private XsdDropPlanner.Position dropPosition;
        private boolean dropBlocked;

        // Lazy loading support (Phase 2 optimization)
        private boolean childrenLoaded = true;  // True if children have been loaded (or no lazy loading)
        private boolean hasLazyChildren = false;  // True if this node has children that haven't been loaded
        private java.util.function.Supplier<java.util.List<VisualNode>> lazyChildrenLoader;  // Loader for lazy children
        private boolean loadingChildren;  // True while children are being loaded

        // New properties for improved visual representation
        private String cardinalityBadge;        // Cardinality pill text (e.g., "0..1", "1..*", "req"); "" = none

        // Identity constraints and assertions the card lists
        private java.util.function.Supplier<java.util.List<ConstraintRow>> constraintSource;
        private java.util.List<ConstraintRow> constraints = java.util.List.of();

        // PropertyChangeListener for model updates
        private final java.beans.PropertyChangeListener modelListener;

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent) {
            this(label, detail, type, modelObject, parent, 1, 1, null);
        }

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent, int minOccurs, int maxOccurs) {
            this(label, detail, type, modelObject, parent, minOccurs, maxOccurs, null);
        }

        public VisualNode(String label, String detail, NodeWrapperType type, Object modelObject, VisualNode parent, int minOccurs, int maxOccurs, Runnable onModelChangeCallback) {
            this.label = label;
            this.detail = detail;
            this.type = type;
            this.modelObject = modelObject;
            this.parent = parent;
            this.minOccurs = minOccurs;
            this.maxOccurs = maxOccurs;
            this.onModelChangeCallback = onModelChangeCallback;

            // Setup PropertyChangeListener for model updates (only for XsdNode objects)
            this.modelListener = evt -> {
                // A descendant's change says nothing about this node's own label; structural changes
                // rebuild the tree anyway (XsdGraphView). Reacting here made every edit refresh all
                // ancestors of the edited node, in every copy of them.
                if ("descendantChanged".equals(evt.getPropertyName())) {
                    return;
                }
                // Auto-update this VisualNode from model when model changes
                // Use Platform.runLater to ensure UI updates happen on JavaFX Application Thread
                javafx.application.Platform.runLater(() -> {
                    updateFromModel();
                    // Also trigger callback for view refresh if provided
                    if (onModelChangeCallback != null) {
                        onModelChangeCallback.run();
                    }
                });
            };

            // Register listener with model if it's an XsdNode
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                xsdNode.addPropertyChangeListener(modelListener);
                // Initialize detail string from model immediately
                updateFromModel();
            }

            // Initialize visual properties based on node type
            initializeVisualProperties();
        }

        /**
         * Refreshes the derived display properties (the cardinality pill) from the node's state.
         * Called on construction and after every model change.
         */
        public void initializeVisualProperties() {
            this.cardinalityBadge = formatCardinalityBadge();
        }

        /**
         * The text of the cardinality pill: empty for the default 1..1, "req"/"opt"/"prohibited"
         * for attributes, "ROOT" for the schema, otherwise {@code min..max} with {@code *}.
         */
        private String formatCardinalityBadge() {
            return switch (type) {
                case SCHEMA -> "ROOT";
                case COMMENT, SEQUENCE, CHOICE, ALL, ENUMERATION, COMPLEX_TYPE, SIMPLE_TYPE -> "";
                case ATTRIBUTE -> {
                    if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
                        String use = attribute.getUse();
                        yield "required".equals(use) ? "req" : "prohibited".equals(use) ? "prohibited" : "opt";
                    }
                    yield "";
                }
                default -> XsdNodeRenderer.getCardinalityString(this);
            };
        }

        /**
         * Updates this VisualNode's display properties from the underlying XsdNode model.
         * Called automatically when the model fires PropertyChangeEvents.
         * <p>
         * This method enables in-place updates instead of rebuilding the entire visual tree,
         * making the editor more efficient and preserving UI state (selection, expansion).
         */
        private void updateFromModel() {
            if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode)) {
                return;  // Not an XsdNode, cannot update
            }

            // Update name/label
            this.label = labelOf(xsdNode);

            // Update cardinality
            this.minOccurs = xsdNode.getMinOccurs();
            this.maxOccurs = xsdNode.getMaxOccurs();

            // Rebuild detail string based on node type
            this.detail = buildDetailString(xsdNode);

            // Re-initialize visual properties (icon, colors, etc.) based on updated detail
            initializeVisualProperties();

            // "assertions" and "type" change no children, so no rebuild refreshes these
            refreshConstraints();
        }

        /**
         * Sets where this card gets the constraints it lists.
         *
         * @param source computes the rows; asked now and whenever the card's model changes
         */
        public void setConstraintSource(java.util.function.Supplier<java.util.List<ConstraintRow>> source) {
            this.constraintSource = source;
            refreshConstraints();
        }

        /** Asks the constraint source again, e.g. after a display option changed what it lists. */
        void refreshConstraints() {
            if (constraintSource != null) {
                constraints = constraintSource.get();
            }
        }

        /** @return the identity constraints and assertions this card lists */
        public java.util.List<ConstraintRow> getConstraints() {
            return constraints;
        }

        /** A group reference has no name of its own: it shows what it refers to. */
        private static String labelOf(org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdGroup group && group.isReference()) {
                return "group: " + group.getRef();
            }
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup group
                    && group.isReference()) {
                return "attributeGroup: " + group.getRef();
            }
            return xsdNode.getName() != null ? xsdNode.getName() : "(unnamed)";
        }

        /**
         * Builds the detail string for display based on the node type.
         * <p>
         * Detail formats:
         * - XsdElement: "xs:string [N] [A] [F] [Q]" (type + flags: nillable, abstract, fixed, qualified)
         * - XsdAttribute: "xs:int (required) [Q]" (type + use + qualified)
         * - XsdSimpleType: "xs:string" (base type)
         * - XsdComplexType: "xs:string" (base type if simpleContent)
         * - Compositor: "3 items" (child count)
         * - Other nodes: "" (empty)
         * <p>
         * Flags:
         * - [N] = nillable
         * - [A] = abstract
         * - [F] = fixed value set
         * - [Q] = qualified form
         * - [U] = unqualified form
         * - [Doc] = has documentation
         * - [App] = has appinfo
         *
         * @param xsdNode the XSD node
         * @return the detail string
         */
        private String buildDetailString(org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            StringBuilder detail = new StringBuilder();

            // Handle XsdElement - show type and constraints
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
                // Add type (either explicit type or base type from inline simpleType restriction)
                String effectiveType = getEffectiveType(element);
                if (effectiveType != null) {
                    // Resolve type reference to primitive base type for better icon display
                    String resolvedType = resolveBaseTypeToPrimitive(effectiveType, 0, new java.util.HashSet<>());
                    detail.append(resolvedType);
                }

                // Add constraint flags
                if (element.isNillable()) {
                    detail.append(" [N]");
                }
                if (element.isAbstract()) {
                    detail.append(" [A]");
                }
                if (element.getFixed() != null && !element.getFixed().isEmpty()) {
                    detail.append(" [F]");
                }

                // Add form flag
                if ("qualified".equals(element.getForm())) {
                    detail.append(" [Q]");
                } else if ("unqualified".equals(element.getForm())) {
                    detail.append(" [U]");
                }

                return detail.toString().trim();
            }

            // Handle XsdAttribute - show type and form (use indicator is shown in body)
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute attribute) {
                // Add type only - use indicator is already shown in the "Use:" row in the body
                if (attribute.getType() != null) {
                    // Resolve type reference to primitive base type for better icon display
                    String resolvedType = resolveBaseTypeToPrimitive(attribute.getType(), 0, new java.util.HashSet<>());
                    detail.append(resolvedType);
                }

                // Add form flag
                if ("qualified".equals(attribute.getForm())) {
                    detail.append(" [Q]");
                } else if ("unqualified".equals(attribute.getForm())) {
                    detail.append(" [U]");
                }

                return detail.toString().trim();
            }

            // Handle XsdSimpleType - extract base type for icon determination
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
                for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : simpleType.getChildren()) {
                    String base = extractBaseType(child);
                    if (base != null) {
                        // Resolve to the ultimate primitive base type (up to 5 levels deep)
                        String resolvedBase = resolveBaseTypeToPrimitive(base, 0, new java.util.HashSet<>());
                        return resolvedBase;
                    }
                }
            }

            // Handle XsdComplexType - check for SimpleContent
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
                 for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : complexType.getChildren()) {
                     if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent) {
                         for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode contentChild : simpleContent.getChildren()) {
                             String base = extractBaseType(contentChild);
                             if (base != null) {
                                 // Resolve to the ultimate primitive base type (up to 5 levels deep)
                                 String resolvedBase = resolveBaseTypeToPrimitive(base, 0, new java.util.HashSet<>());
                                 return resolvedBase;
                             }
                         }
                     }
                 }
            }

            // Handle Compositor nodes (sequence, choice, all) - show child count
            if (type == NodeWrapperType.SEQUENCE || type == NodeWrapperType.CHOICE || type == NodeWrapperType.ALL) {
                return xsdNode.getChildren().size() + " items";
            }

            // Handle wildcards - what they admit, and how it gets validated
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAny any) {
                return any.getNamespace() + " · " + any.getProcessContents().getValue();
            }
            if (xsdNode instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute any) {
                return any.getNamespace() + " · " + any.getProcessContents().getValue();
            }

            // Default: empty detail
            return "";
        }

        /**
         * Gets the effective type of an element.
         * For elements with explicit type reference, returns that type.
         * For elements with inline simpleType and restriction, returns the base type from the restriction.
         *
         * @param element the XSD element
         * @return the effective type, or null if not found
         */
        private String getEffectiveType(org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            // First check for explicit type reference
            String explicitType = element.getType();
            if (explicitType != null && !explicitType.isEmpty()) {
                return explicitType;
            }

            // Check for inline complexType with simpleContent
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : element.getChildren()) {
                if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
                    // Look for simpleContent in complexType
                    for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode complexChild : complexType.getChildren()) {
                        if (complexChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent) {
                            // Look for extension or restriction in simpleContent
                            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode contentChild : simpleContent.getChildren()) {
                                String baseType = extractBaseType(contentChild);
                                if (baseType != null) {
                                    return baseType;
                                }
                            }
                        }
                    }
                }
            }

            // Check for inline simpleType with restriction
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : element.getChildren()) {
                if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
                    // Look for restriction in simpleType children
                    for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode restrictionChild : simpleType.getChildren()) {
                        String baseType = extractBaseType(restrictionChild);
                        if (baseType != null) {
                            return baseType;
                        }
                    }
                }
            }

            return null;
        }

        /**
         * Extracts the base type from an XsdExtension or XsdRestriction node.
         *
         * @param node the node to extract base type from
         * @return the base type, or null if not found
         */
        private String extractBaseType(org.fxt.freexmltoolkit.controls.v2.model.XsdNode node) {
            // Check if it's an extension or restriction
            if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
                String base = restriction.getBase();
                if (base != null && !base.isEmpty()) {
                    return base;
                }
            } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension) {
                String base = extension.getBase();
                if (base != null && !base.isEmpty()) {
                    return base;
                }
            }
            return null;
        }

        /**
         * Resolves a base type name to the ultimate primitive type by following the type chain.
         * Handles SimpleType restrictions, List, Union, and ComplexType SimpleContent.
         * Protects against circular references and limits depth to 5 levels.
         *
         * @param typeName the type name to resolve (e.g., "BaseTextType")
         * @param depth current recursion depth
         * @param visited set of already visited types to prevent circular loops
         * @return the ultimate primitive base type (e.g., "xs:string"), or the original typeName if not found
         */
        private String resolveBaseTypeToPrimitive(String typeName, int depth, java.util.Set<String> visited) {
            // Base cases
            if (typeName == null || typeName.isEmpty()) {
                return typeName;
            }

            // Depth limit: stop at 5 levels
            if (depth >= 5) {
                return typeName;
            }

            // Check if already a primitive type
            if (isPrimitiveType(typeName)) {
                return typeName;
            }

            // Circular reference check
            if (visited.contains(typeName)) {
                return typeName;
            }

            // Mark as visited
            visited.add(typeName);

            // Find the type definition in the schema
            org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = getRootSchema();
            if (schema == null) {
                return typeName;
            }

            org.fxt.freexmltoolkit.controls.v2.model.XsdNode typeDef = findTypeDefinition(schema, typeName);
            if (typeDef == null) {
                return typeName;  // Type not found (e.g., imported)
            }

            // Handle SimpleType
            if (typeDef instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
                for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : simpleType.getChildren()) {
                    // Check for Restriction
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
                        String base = restriction.getBase();
                        if (base != null && !base.isEmpty()) {
                            return resolveBaseTypeToPrimitive(base, depth + 1, visited);
                        }
                    // Check for List
                    } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdList list) {
                        String itemType = list.getItemType();
                        if (itemType != null && !itemType.isEmpty()) {
                            return resolveBaseTypeToPrimitive(itemType, depth + 1, visited);
                        }
                    // Check for Union - use first member type
                    } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdUnion union) {
                        java.util.List<String> memberTypes = union.getMemberTypes();
                        if (!memberTypes.isEmpty()) {
                            return resolveBaseTypeToPrimitive(memberTypes.get(0), depth + 1, visited);
                        }
                    }
                }
            // Handle ComplexType (only SimpleContent)
            } else if (typeDef instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
                for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : complexType.getChildren()) {
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent) {
                        // Check for Extension
                        org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension = simpleContent.getExtension();
                        if (extension != null) {
                            String base = extension.getBase();
                            if (base != null && !base.isEmpty()) {
                                return resolveBaseTypeToPrimitive(base, depth + 1, visited);
                            }
                        }
                        // Check for Restriction
                        org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction = simpleContent.getRestriction();
                        if (restriction != null) {
                            String base = restriction.getBase();
                            if (base != null && !base.isEmpty()) {
                                return resolveBaseTypeToPrimitive(base, depth + 1, visited);
                            }
                        }
                    }
                }
            }

            return typeName;  // Could not resolve further
        }

        /**
         * Checks if a type name represents a primitive XSD type.
         *
         * @param typeName the type name to check
         * @return true if it's a primitive type, false otherwise
         */
        private boolean isPrimitiveType(String typeName) {
            if (typeName == null || typeName.isEmpty()) {
                return false;
            }

            // Check for xs: or xsd: prefix (most common)
            if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
                return true;
            }

            // Check for unprefixed common primitives (in case of default namespace)
            String lower = typeName.toLowerCase();
            return lower.matches("^(string|integer|int|long|short|byte|decimal|float|double|boolean|date|time|" +
                    "datetime|duration|base64binary|hexbinary|anyuri|uri|qname|notation|token|normalizedstring|" +
                    "ncname|name|language|id|idref|anytype|any)$");
        }

        /**
         * Gets the root XsdSchema node by navigating up the parent chain.
         *
         * @return the root XsdSchema, or null if not found
         */
        private org.fxt.freexmltoolkit.controls.v2.model.XsdSchema getRootSchema() {
            VisualNode current = this;
            while (current != null) {
                if (current.modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema) {
                    return schema;
                }
                current = current.parent;
            }
            return null;
        }

        /**
         * Finds a type definition (SimpleType or ComplexType) in a schema by name.
         *
         * @param schema the schema to search
         * @param typeName the type name to find (with or without namespace prefix)
         * @return the XsdNode if found, null otherwise
         */
        private org.fxt.freexmltoolkit.controls.v2.model.XsdNode findTypeDefinition(
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema, String typeName) {
            if (schema == null || typeName == null || typeName.isEmpty()) {
                return null;
            }

            // Strip namespace prefix for matching (e.g., "tns:MyType" → "MyType")
            String localName = typeName.contains(":") ? typeName.substring(typeName.indexOf(':') + 1) : typeName;

            // Search schema children for matching SimpleType or ComplexType
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : schema.getChildren()) {
                if ((child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType ||
                     child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType) &&
                    localName.equals(child.getName())) {
                    return child;
                }
            }

            return null;
        }

        public void addChild(VisualNode child) {
            children.add(child);
        }

        public boolean hasChildren() {
            return !children.isEmpty() || hasLazyChildren;
        }

        /**
         * Returns true if this node has unloaded lazy children.
         *
         * @return true if children need to be loaded
         */
        public boolean hasUnloadedChildren() {
            return hasLazyChildren && !childrenLoaded;
        }

        public boolean containsPoint(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        public boolean expandButtonContainsPoint(double px, double py) {
            return hasChildren() && px >= expandBtnX && px <= expandBtnX + expandBtnW
                    && py >= expandBtnY && py <= expandBtnY + expandBtnH;
        }

        /** Flips the expanded state; expanding loads deferred children, see {@link #setExpanded}. */
        public void toggleExpanded() {
            setExpanded(!expanded);
        }

        // Getters and setters
        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public NodeWrapperType getType() {
            return type;
        }

        /**
         * Gets the model node if the model object is an XsdNode.
         *
         * @return the XsdNode, or null if model object is not an XsdNode
         */
        public org.fxt.freexmltoolkit.controls.v2.model.XsdNode getModelNode() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                return xsdNode;
            }
            return null;
        }

        public Object getModelObject() {
            return modelObject;
        }

        public VisualNode getParent() {
            return parent;
        }

        public java.util.List<VisualNode> getChildren() {
            return children;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public boolean isExpanded() {
            return expanded;
        }

        public int getMinOccurs() {
            return minOccurs;
        }

        public int getMaxOccurs() {
            return maxOccurs;
        }

        public String getCardinalityBadge() {
            return cardinalityBadge;
        }

        public void setX(double x) {
            this.x = x;
        }

        public void setY(double y) {
            this.y = y;
        }

        public void setWidth(double w) {
            this.width = w;
        }

        public void setHeight(double h) {
            this.height = h;
        }

        public void setExpanded(boolean e) {
            this.expanded = e;
            // Trigger lazy loading when expanding a node with unloaded children
            if (e && hasUnloadedChildren()) {
                loadChildrenIfNeeded();
            }
        }

        /**
         * Loads children lazily if they haven't been loaded yet.
         * Called automatically when expanding a node with lazy children.
         */
        public void loadChildrenIfNeeded() {
            if (childrenLoaded || lazyChildrenLoader == null || loadingChildren) {
                return;
            }

            loadingChildren = true;
            try {
                java.util.List<VisualNode> lazyChildren = lazyChildrenLoader.get();
                if (lazyChildren != null) {
                    for (VisualNode child : lazyChildren) {
                        children.add(child);
                    }
                }
                childrenLoaded = true;
                hasLazyChildren = !children.isEmpty();  // Now we know if we actually have children

                // Trigger callback to redraw if available
                if (onModelChangeCallback != null) {
                    onModelChangeCallback.run();
                }
            } finally {
                loadingChildren = false;
            }
        }

        /**
         * Sets up lazy loading for this node's children.
         * The loader will be called when the node is first expanded.
         *
         * @param loader function that creates the child nodes; it may also add them to this card
         *               directly and return nothing
         */
        public void setLazyChildrenLoader(java.util.function.Supplier<java.util.List<VisualNode>> loader) {
            this.lazyChildrenLoader = loader;
            this.childrenLoaded = false;
            this.hasLazyChildren = true;
        }

        /**
         * Returns true if children have been loaded (or if lazy loading is not enabled).
         *
         * @return true if children are available
         */
        public boolean isChildrenLoaded() {
            return childrenLoaded;
        }

        /**
         * Returns true if children are currently being loaded.
         *
         * @return true if loading is in progress
         */
        public boolean isLoadingChildren() {
            return loadingChildren;
        }

        public void setExpandButtonBounds(double x, double y, double w, double h) {
            this.expandBtnX = x;
            this.expandBtnY = y;
            this.expandBtnW = w;
            this.expandBtnH = h;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public boolean isHovered() {
            return hovered;
        }

        public void setHovered(boolean hovered) {
            this.hovered = hovered;
        }

        public boolean isFocused() {
            return focused;
        }

        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        public boolean isInEditMode() {
            return inEditMode;
        }

        public void setInEditMode(boolean inEditMode) {
            this.inEditMode = inEditMode;
        }

        public boolean isDragging() {
            return dragging;
        }

        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }

        public boolean isDropTarget() {
            return dropTarget;
        }

        public void setDropTarget(boolean dropTarget) {
            this.dropTarget = dropTarget;
            if (!dropTarget) {
                dropPosition = null;
                dropBlocked = false;
            }
        }

        /** @return where a drop on this card lands (null = no specific position), while it is the drop target */
        public XsdDropPlanner.Position getDropPosition() {
            return dropPosition;
        }

        /** @return whether the drop on this card is refused by the edit rules */
        public boolean isDropBlocked() {
            return dropBlocked;
        }

        /**
         * Marks this card as the drop target with the given feedback.
         *
         * @param position where the drop lands
         * @param blocked  whether it is refused
         */
        public void setDropIndicator(XsdDropPlanner.Position position, boolean blocked) {
            this.dropTarget = true;
            this.dropPosition = position;
            this.dropBlocked = blocked;
        }

        /**
         * Detaches this node and its subtree from the model. The graph view calls it before it
         * replaces the visual tree: the listeners otherwise stayed registered on the model nodes,
         * so every rebuild added another full set and every later edit notified all of them.
         */
        public void dispose() {
            java.util.ArrayDeque<VisualNode> pending = new java.util.ArrayDeque<>();
            pending.push(this);
            while (!pending.isEmpty()) {
                VisualNode node = pending.pop();
                if (node.modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                    xsdNode.removePropertyChangeListener(node.modelListener);
                }
                node.onModelChangeCallback = null;
                node.children.forEach(pending::push);
            }
        }

        /**
         * Sets the callback to be invoked when the underlying model changes.
         * This allows updating the callback after tree rebuild operations.
         *
         * @param callback the callback to invoke on model changes
         * @since 2.0
         */
        public void setOnModelChangeCallback(Runnable callback) {
            this.onModelChangeCallback = callback;
        }

        /**
         * Checks if this node is from an included file.
         * Uses the sourceInfo from the underlying XsdNode model.
         *
         * @return true if the node is from an included file, false otherwise
         * @since 2.0
         */
        public boolean isFromInclude() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                var sourceInfo = xsdNode.getSourceInfo();
                return sourceInfo != null && sourceInfo.isFromInclude();
            }
            return false;
        }

        /**
         * Gets the source file name if available.
         *
         * @return the source file name, or null if not from an include
         * @since 2.0
         */
        public String getSourceFileName() {
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                var sourceInfo = xsdNode.getSourceInfo();
                if (sourceInfo != null) {
                    return sourceInfo.getFileName();
                }
            }
            return null;
        }
    }
}
