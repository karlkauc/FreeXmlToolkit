package org.fxt.freexmltoolkit.controls.v2.view.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * {@link DiagramGraphics} that records every call as an SVG element — the vector counterpart
 * of the canvas, so the diagram renderers write SVG (and, through it, PDF) unchanged.
 *
 * <p>Mapping:
 * <ul>
 *   <li>shapes and text are written in user space with the current transform as a
 *       {@code transform="matrix(…)"} attribute;</li>
 *   <li>paths are kept in device space, the way the canvas transforms each segment as it is
 *       added; their stroke width is scaled by the transform at the time they are stroked;</li>
 *   <li>a {@link #clip()} opens a {@code <g clip-path>} in device space, nested in the
 *       current one (so clips intersect); {@link #restore()} returns to the saved group;</li>
 *   <li>text positions follow the canvas' alignment and baseline rules, measured with the
 *       same JavaFX font metrics the renderers lay out with.</li>
 * </ul>
 * The defaults match {@code GraphicsContext}: black paints, line width 1, square caps, miter
 * joins with limit 10, left-aligned text on the baseline.
 */
public final class SvgDiagramGraphics implements DiagramGraphics {

    /** The SVG namespace. */
    public static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    /** Ascent and descent per font, measured once with a JavaFX {@link Text}. */
    private static final Map<Font, double[]> METRICS = new ConcurrentHashMap<>();

    private final Document document;
    private final Element root;
    private final Element defs;
    private final Set<FontFace> usedFaces = new LinkedHashSet<>();
    private final Deque<State> stack = new ArrayDeque<>();
    private final StringBuilder path = new StringBuilder();
    private State state;
    private int clipCount;

    /** A font face the drawing used: CSS family, weight (100–900) and style. */
    public record FontFace(String family, int weight, boolean italic) {
    }

    /** The drawing attributes {@link #save()} pushes. */
    private static final class State {
        Paint fill = Color.BLACK;
        Paint stroke = Color.BLACK;
        double lineWidth = 1;
        double[] dashes;
        StrokeLineCap cap = StrokeLineCap.SQUARE;
        StrokeLineJoin join = StrokeLineJoin.MITER;
        double alpha = 1;
        Font font;
        TextAlignment align = TextAlignment.LEFT;
        VPos baseline = VPos.BASELINE;
        Affine transform = new Affine();
        Element container;

        State copy() {
            State s = new State();
            s.fill = fill;
            s.stroke = stroke;
            s.lineWidth = lineWidth;
            s.dashes = dashes;
            s.cap = cap;
            s.join = join;
            s.alpha = alpha;
            s.font = font;
            s.align = align;
            s.baseline = baseline;
            s.transform = new Affine(transform);
            s.container = container;
            return s;
        }
    }

    /**
     * Creates an empty SVG drawing.
     *
     * @param width  the drawing width in pixels (also the {@code viewBox} width)
     * @param height the drawing height in pixels
     */
    public SvgDiagramGraphics(double width, double height) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            document = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No DOM implementation for the SVG export", e);
        }
        root = document.createElementNS(SVG_NS, "svg");
        root.setAttributeNS(XMLNS_NS, "xmlns", SVG_NS);
        root.setAttribute("version", "1.1");
        root.setAttribute("width", num(width));
        root.setAttribute("height", num(height));
        root.setAttribute("viewBox", "0 0 " + num(width) + " " + num(height));
        // The canvas defaults the elements inherit (SVG's own are butt caps and miter limit 4)
        root.setAttribute("stroke-linecap", "square");
        root.setAttribute("stroke-linejoin", "miter");
        root.setAttribute("stroke-miterlimit", "10");
        document.appendChild(root);
        defs = document.createElementNS(SVG_NS, "defs");
        root.appendChild(defs);
        state = new State();
        state.container = root;
    }

    // ------------------------------------------------------------------ document

    /** @return the SVG document drawn so far */
    public Document getDocument() {
        return document;
    }

    /** @return the font faces the text drawn so far uses, in order of first use */
    public Set<FontFace> getUsedFontFaces() {
        return Set.copyOf(usedFaces);
    }

    /**
     * Sets the document title ({@code <title>}, shown by viewers and read by screen readers).
     *
     * @param title the title; blank leaves the document without one
     */
    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        Element element = document.createElementNS(SVG_NS, "title");
        element.setTextContent(title);
        root.insertBefore(element, defs);
    }

    /**
     * Changes the document size and the part of the drawing it shows; the drawing is scaled to
     * fit and centred ({@code preserveAspectRatio} default). Used to put a drawing on a page.
     *
     * @param width  the document width in pixels
     * @param height the document height in pixels
     * @param viewX  left edge of the shown part, in drawing coordinates
     * @param viewY  top edge of the shown part
     * @param viewW  width of the shown part
     * @param viewH  height of the shown part
     */
    public void setDocumentSize(double width, double height, double viewX, double viewY, double viewW, double viewH) {
        root.setAttribute("width", num(width));
        root.setAttribute("height", num(height));
        root.setAttribute("viewBox", num(viewX) + " " + num(viewY) + " " + num(viewW) + " " + num(viewH));
    }

    /**
     * @param face a font face of the drawing
     * @return the classpath resource of the bundled TrueType file for it (Inter, JetBrains Mono), or null
     */
    public static String bundledFontResource(FontFace face) {
        return BundledFonts.resourceFor(face);
    }

    /**
     * Embeds the bundled fonts (Inter, JetBrains Mono) the drawing uses as {@code @font-face}
     * rules with base64 TrueType data, so the text keeps the widths it was laid out with on
     * machines without those fonts. Faces of other families are left to the viewer.
     *
     * @return the number of embedded faces
     */
    public int embedBundledFonts() {
        StringBuilder css = new StringBuilder();
        int count = 0;
        for (FontFace face : usedFaces) {
            String resource = BundledFonts.resourceFor(face);
            if (resource == null) {
                continue;
            }
            try (InputStream in = SvgDiagramGraphics.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                css.append("@font-face{font-family:'").append(face.family()).append("';font-weight:")
                        .append(face.weight()).append(";font-style:").append(face.italic() ? "italic" : "normal")
                        .append(";src:url(data:font/ttf;base64,")
                        .append(Base64.getEncoder().encodeToString(in.readAllBytes()))
                        .append(") format('truetype');}\n");
                count++;
            } catch (IOException e) {
                // Not embedded: the viewer's fallback font is used for this face
            }
        }
        if (count > 0) {
            Element style = document.createElementNS(SVG_NS, "style");
            style.setAttribute("type", "text/css");
            style.appendChild(document.createCDATASection(css.toString()));
            defs.appendChild(style);
        }
        return count;
    }

    /**
     * Writes the document as UTF-8 XML.
     *
     * @param out the target writer
     * @throws IOException when the document cannot be written
     */
    public void writeTo(Writer out) throws IOException {
        try {
            Transformer transformer = TransformerFactory.newDefaultInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new IOException("Could not write the SVG document: " + e.getMessage(), e);
        }
    }

    /** @return the document as an XML string */
    public String toXml() {
        StringWriter out = new StringWriter();
        try {
            writeTo(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ state

    @Override
    public void save() {
        stack.push(state.copy());
    }

    @Override
    public void restore() {
        if (!stack.isEmpty()) {
            state = stack.pop();
        }
    }

    @Override
    public void translate(double x, double y) {
        state.transform.appendTranslation(x, y);
    }

    @Override
    public void scale(double x, double y) {
        state.transform.appendScale(x, y);
    }

    @Override
    public void setFill(Paint paint) {
        if (paint != null) {
            state.fill = paint;
        }
    }

    @Override
    public Paint getFill() {
        return state.fill;
    }

    @Override
    public void setStroke(Paint paint) {
        if (paint != null) {
            state.stroke = paint;
        }
    }

    @Override
    public void setLineWidth(double width) {
        if (width > 0 && Double.isFinite(width)) {
            state.lineWidth = width;
        }
    }

    @Override
    public void setLineDashes(double... dashes) {
        state.dashes = dashes == null || dashes.length == 0 ? null : dashes.clone();
    }

    @Override
    public void setLineCap(StrokeLineCap cap) {
        if (cap != null) {
            state.cap = cap;
        }
    }

    @Override
    public void setLineJoin(StrokeLineJoin join) {
        if (join != null) {
            state.join = join;
        }
    }

    @Override
    public void setGlobalAlpha(double alpha) {
        state.alpha = Math.max(0, Math.min(1, alpha));
    }

    @Override
    public void setFont(Font font) {
        if (font != null) {
            state.font = font;
        }
    }

    @Override
    public void setTextAlign(TextAlignment align) {
        if (align != null) {
            state.align = align;
        }
    }

    @Override
    public void setTextBaseline(VPos baseline) {
        if (baseline != null) {
            state.baseline = baseline;
        }
    }

    // ------------------------------------------------------------------ text

    @Override
    public void fillText(String text, double x, double y) {
        Element element = text(text, x, y);
        if (element != null) {
            applyFill(element);
            append(element, true);
        }
    }

    @Override
    public void strokeText(String text, double x, double y) {
        Element element = text(text, x, y);
        if (element != null) {
            element.setAttribute("fill", "none");
            applyStroke(element, 1);
            append(element, true);
        }
    }

    private Element text(String text, double x, double y) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Font font = state.font != null ? state.font : Font.getDefault();
        double[] metrics = metrics(font);
        double ascent = metrics[0];
        double descent = metrics[1];
        double baselineY = switch (state.baseline) {
            case TOP -> y + ascent;
            case CENTER -> y + (ascent - descent) / 2;
            case BOTTOM -> y - descent;
            default -> y;
        };
        FontFace face = faceOf(font);
        usedFaces.add(face);
        Element element = document.createElementNS(SVG_NS, "text");
        element.setAttribute("x", num(x));
        element.setAttribute("y", num(baselineY));
        element.setAttribute("font-family", cssFamilyList(face.family()));
        element.setAttribute("font-size", num(font.getSize()));
        if (face.weight() != 400) {
            element.setAttribute("font-weight", Integer.toString(face.weight()));
        }
        if (face.italic()) {
            element.setAttribute("font-style", "italic");
        }
        switch (state.align) {
            case CENTER -> element.setAttribute("text-anchor", "middle");
            case RIGHT -> element.setAttribute("text-anchor", "end");
            default -> { }
        }
        element.setAttributeNS(XML_NS, "xml:space", "preserve");
        element.setTextContent(text);
        return element;
    }

    /** @return {ascent, descent} of the font, both positive, as JavaFX lays text out */
    private static double[] metrics(Font font) {
        return METRICS.computeIfAbsent(font, f -> {
            Text probe = new Text("Hg");
            probe.setFont(f);
            Bounds bounds = probe.getLayoutBounds(); // origin on the baseline
            return new double[]{-bounds.getMinY(), bounds.getMaxY()};
        });
    }

    /**
     * Maps a JavaFX font onto a CSS face: cut families such as "Inter SemiBold" become family
     * "Inter" with weight 600, the logical families keep their name.
     */
    static FontFace faceOf(Font font) {
        String family = font.getFamily() == null ? "System" : font.getFamily().trim();
        String name = font.getName() == null ? family : font.getName();
        String style = font.getStyle() == null ? "" : font.getStyle();
        String descriptor = (name + " " + style).toLowerCase(Locale.ROOT).replace("-", " ");
        int weight = weightOf(descriptor);
        boolean italic = descriptor.contains("italic") || descriptor.contains("oblique");
        String base = family.replaceAll("(?i)\\s+(extra ?light|ultra ?light|thin|light|regular|medium|"
                + "semi ?bold|demi ?bold|extra ?bold|ultra ?bold|bold|black|heavy|italic)$", "");
        // A cut family can carry two suffixes ("Inter SemiBold Italic")
        base = base.replaceAll("(?i)\\s+(extra ?light|thin|light|medium|semi ?bold|extra ?bold|bold|black)$", "");
        return new FontFace(base.isBlank() ? family : base, weight, italic);
    }

    private static int weightOf(String descriptor) {
        if (descriptor.contains("thin") || descriptor.contains("hairline")) {
            return 100;
        }
        if (descriptor.matches(".*(extra|ultra) ?light.*")) {
            return 200;
        }
        if (descriptor.matches(".*(semi|demi) ?bold.*")) {
            return 600;
        }
        if (descriptor.matches(".*(extra|ultra) ?bold.*")) {
            return 800;
        }
        if (descriptor.contains("black") || descriptor.contains("heavy")) {
            return 900;
        }
        if (descriptor.contains("bold")) {
            return 700;
        }
        if (descriptor.contains("medium")) {
            return 500;
        }
        if (descriptor.contains("light")) {
            return 300;
        }
        return 400;
    }

    /** @return the CSS font-family list for a face family, with fallbacks of the same kind */
    static String cssFamilyList(String family) {
        String lower = family.toLowerCase(Locale.ROOT);
        if (lower.equals("monospaced") || lower.equals("monospace")) {
            return "'JetBrains Mono', Consolas, 'DejaVu Sans Mono', 'Courier New', monospace";
        }
        if (lower.equals("system") || lower.equals("sansserif") || lower.equals("sans-serif")) {
            return "'Segoe UI', 'Helvetica Neue', Arial, sans-serif";
        }
        if (lower.equals("serif")) {
            return "'Times New Roman', serif";
        }
        boolean mono = lower.contains("mono") || lower.contains("courier") || lower.contains("consol");
        return "'" + family.replace("'", "") + "', "
                + (mono ? "Consolas, 'DejaVu Sans Mono', 'Courier New', monospace"
                        : "'Segoe UI', 'Helvetica Neue', Arial, sans-serif");
    }

    // ------------------------------------------------------------------ shapes

    @Override
    public void fillRect(double x, double y, double w, double h) {
        if (w > 0 && h > 0) {
            Element rect = rectElement(x, y, w, h, 0, 0);
            applyFill(rect);
            append(rect, true);
        }
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
        if (w >= 0 && h >= 0) {
            Element rect = rectElement(x, y, w, h, 0, 0);
            rect.setAttribute("fill", "none");
            applyStroke(rect, 1);
            append(rect, true);
        }
    }

    @Override
    public void clearRect(double x, double y, double w, double h) {
        // An SVG drawing has no pixels to clear; the exporter paints the background itself.
    }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        if (w > 0 && h > 0) {
            Element rect = rectElement(x, y, w, h, arcWidth, arcHeight);
            applyFill(rect);
            append(rect, true);
        }
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        if (w >= 0 && h >= 0) {
            Element rect = rectElement(x, y, w, h, arcWidth, arcHeight);
            rect.setAttribute("fill", "none");
            applyStroke(rect, 1);
            append(rect, true);
        }
    }

    private Element rectElement(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        Element rect = document.createElementNS(SVG_NS, "rect");
        rect.setAttribute("x", num(x));
        rect.setAttribute("y", num(y));
        rect.setAttribute("width", num(w));
        rect.setAttribute("height", num(h));
        double rx = Math.min(Math.abs(arcWidth) / 2, w / 2);
        double ry = Math.min(Math.abs(arcHeight) / 2, h / 2);
        if (rx > 0 && ry > 0) {
            rect.setAttribute("rx", num(rx));
            rect.setAttribute("ry", num(ry));
        }
        return rect;
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        if (w > 0 && h > 0) {
            Element ellipse = ellipse(x, y, w, h);
            applyFill(ellipse);
            append(ellipse, true);
        }
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        if (w >= 0 && h >= 0) {
            Element ellipse = ellipse(x, y, w, h);
            ellipse.setAttribute("fill", "none");
            applyStroke(ellipse, 1);
            append(ellipse, true);
        }
    }

    private Element ellipse(double x, double y, double w, double h) {
        Element ellipse = document.createElementNS(SVG_NS, "ellipse");
        ellipse.setAttribute("cx", num(x + w / 2));
        ellipse.setAttribute("cy", num(y + h / 2));
        ellipse.setAttribute("rx", num(w / 2));
        ellipse.setAttribute("ry", num(h / 2));
        return ellipse;
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        Element line = document.createElementNS(SVG_NS, "line");
        line.setAttribute("x1", num(x1));
        line.setAttribute("y1", num(y1));
        line.setAttribute("x2", num(x2));
        line.setAttribute("y2", num(y2));
        applyStroke(line, 1);
        append(line, true);
    }

    @Override
    public void strokePolyline(double[] xPoints, double[] yPoints, int nPoints) {
        Element polyline = poly("polyline", xPoints, yPoints, nPoints);
        if (polyline != null) {
            polyline.setAttribute("fill", "none");
            applyStroke(polyline, 1);
            append(polyline, true);
        }
    }

    @Override
    public void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) {
        Element polygon = poly("polygon", xPoints, yPoints, nPoints);
        if (polygon != null) {
            applyFill(polygon);
            append(polygon, true);
        }
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        Element polygon = poly("polygon", xPoints, yPoints, nPoints);
        if (polygon != null) {
            polygon.setAttribute("fill", "none");
            applyStroke(polygon, 1);
            append(polygon, true);
        }
    }

    private Element poly(String tag, double[] xPoints, double[] yPoints, int nPoints) {
        int n = Math.min(nPoints, Math.min(xPoints.length, yPoints.length));
        if (n < 2) {
            return null;
        }
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                points.append(' ');
            }
            points.append(num(xPoints[i])).append(',').append(num(yPoints[i]));
        }
        Element element = document.createElementNS(SVG_NS, tag);
        element.setAttribute("points", points.toString());
        return element;
    }

    // ------------------------------------------------------------------ paths (device space)

    @Override
    public void beginPath() {
        path.setLength(0);
    }

    @Override
    public void moveTo(double x, double y) {
        segment('M', x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        segment('L', x, y);
    }

    @Override
    public void quadraticCurveTo(double xc, double yc, double x1, double y1) {
        segment('Q', xc, yc, x1, y1);
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        segment('C', xc1, yc1, xc2, yc2, x1, y1);
    }

    @Override
    public void closePath() {
        if (!path.isEmpty()) {
            path.append('Z');
        }
    }

    @Override
    public void rect(double x, double y, double w, double h) {
        segment('M', x, y);
        segment('L', x + w, y);
        segment('L', x + w, y + h);
        segment('L', x, y + h);
        path.append('Z');
    }

    /** Appends a path command with its points transformed to device space, as the canvas does. */
    private void segment(char command, double... coords) {
        if (!path.isEmpty()) {
            path.append(' ');
        }
        path.append(command);
        for (int i = 0; i + 1 < coords.length; i += 2) {
            Point2D p = state.transform.transform(coords[i], coords[i + 1]);
            path.append(' ').append(num(p.getX())).append(' ').append(num(p.getY()));
        }
    }

    @Override
    public void fill() {
        if (!path.isEmpty()) {
            Element element = pathElement();
            applyFill(element);
            append(element, false);
        }
    }

    @Override
    public void stroke() {
        if (!path.isEmpty()) {
            Element element = pathElement();
            element.setAttribute("fill", "none");
            applyStroke(element, transformScale());
            append(element, false);
        }
    }

    @Override
    public void clip() {
        String id = "clip" + (++clipCount);
        Element clipPath = document.createElementNS(SVG_NS, "clipPath");
        clipPath.setAttribute("id", id);
        clipPath.appendChild(pathElement());
        defs.appendChild(clipPath);
        Element group = document.createElementNS(SVG_NS, "g");
        group.setAttribute("clip-path", "url(#" + id + ")");
        state.container.appendChild(group);
        state.container = group;
    }

    private Element pathElement() {
        Element element = document.createElementNS(SVG_NS, "path");
        element.setAttribute("d", path.isEmpty() ? "M 0 0" : path.toString());
        return element;
    }

    // ------------------------------------------------------------------ attributes

    private void append(Element element, boolean userSpace) {
        if (userSpace && !state.transform.isIdentity()) {
            Affine t = state.transform;
            element.setAttribute("transform", "matrix(" + num(t.getMxx()) + " " + num(t.getMyx()) + " "
                    + num(t.getMxy()) + " " + num(t.getMyy()) + " " + num(t.getTx()) + " " + num(t.getTy()) + ")");
        }
        state.container.appendChild(element);
    }

    private void applyFill(Element element) {
        Color color = colorOf(state.fill);
        element.setAttribute("fill", hex(color));
        double opacity = color.getOpacity() * state.alpha;
        if (opacity < 1) {
            element.setAttribute("fill-opacity", num(opacity));
        }
    }

    /** @param widthScale the factor that takes the line width to the element's space */
    private void applyStroke(Element element, double widthScale) {
        Color color = colorOf(state.stroke);
        element.setAttribute("stroke", hex(color));
        double opacity = color.getOpacity() * state.alpha;
        if (opacity < 1) {
            element.setAttribute("stroke-opacity", num(opacity));
        }
        if (state.lineWidth * widthScale != 1) {
            element.setAttribute("stroke-width", num(state.lineWidth * widthScale));
        }
        if (state.cap != StrokeLineCap.SQUARE) {
            element.setAttribute("stroke-linecap", state.cap == StrokeLineCap.ROUND ? "round" : "butt");
        }
        if (state.join != StrokeLineJoin.MITER) {
            element.setAttribute("stroke-linejoin", state.join == StrokeLineJoin.ROUND ? "round" : "bevel");
        }
        if (state.dashes != null) {
            StringBuilder dashes = new StringBuilder();
            for (double dash : state.dashes) {
                if (!dashes.isEmpty()) {
                    dashes.append(' ');
                }
                dashes.append(num(dash * widthScale));
            }
            element.setAttribute("stroke-dasharray", dashes.toString());
        }
    }

    /** @return the uniform scale of the current transform (the canvas scales strokes by it) */
    private double transformScale() {
        Affine t = state.transform;
        return Math.sqrt(Math.abs(t.getMxx() * t.getMyy() - t.getMxy() * t.getMyx()));
    }

    /** @return the colour of a paint; gradients are represented by their first stop */
    private static Color colorOf(Paint paint) {
        if (paint instanceof Color color) {
            return color;
        }
        if (paint instanceof LinearGradient gradient && !gradient.getStops().isEmpty()) {
            return gradient.getStops().getFirst().getColor();
        }
        if (paint instanceof RadialGradient gradient && !gradient.getStops().isEmpty()) {
            return gradient.getStops().getFirst().getColor();
        }
        return Color.BLACK;
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /** Formats a coordinate with at most three decimals and no exponent. */
    static String num(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** The bundled font files behind the faces the renderers use. */
    static final class BundledFonts {

        private BundledFonts() {
        }

        /** @return the classpath resource of the bundled TrueType file closest to the face, or null */
        static String resourceFor(FontFace face) {
            String family = face.family().toLowerCase(Locale.ROOT).replace(" ", "");
            if (family.equals("inter")) {
                String cut = switch (face.weight()) {
                    case 100 -> "Thin";
                    case 200 -> "ExtraLight";
                    case 300 -> "Light";
                    case 500 -> "Medium";
                    case 600 -> "SemiBold";
                    case 700 -> "Bold";
                    case 800 -> "ExtraBold";
                    case 900 -> "Black";
                    default -> "Regular";
                };
                return "/css/fonts/Inter-" + cut + ".ttf";
            }
            if (family.equals("jetbrainsmono")) {
                boolean bold = face.weight() >= 600;
                String cut;
                if (face.italic()) {
                    cut = bold ? "BoldItalic" : "Italic";
                } else if (bold) {
                    cut = "Bold";
                } else {
                    cut = face.weight() == 500 ? "Medium" : "Regular";
                }
                return "/css/fonts/JetBrainsMono-" + cut + ".ttf";
            }
            return null;
        }
    }
}
