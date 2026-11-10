package org.fxt.freexmltoolkit.controls.v2.view.render;

import javafx.geometry.VPos;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The drawing surface of the diagram renderers (XSD graph, XML/JSON grid, flame graph).
 *
 * <p>The methods mirror {@link javafx.scene.canvas.GraphicsContext} one to one — same names,
 * same arguments, same semantics — so a renderer draws onto the screen through
 * {@link FxDiagramGraphics} and into an SVG document through {@link SvgDiagramGraphics} without
 * knowing which. {@code GraphicsContext} is final, hence this interface. Only the calls the
 * renderers use are here; add one to every implementation when a renderer needs more.
 */
public interface DiagramGraphics {

    // ------------------------------------------------------------------ state

    /** Pushes the current state (paints, line and text attributes, transform, clip). */
    void save();

    /** Pops the state saved by the matching {@link #save()}. */
    void restore();

    void translate(double x, double y);

    void scale(double x, double y);

    void setFill(Paint paint);

    Paint getFill();

    void setStroke(Paint paint);

    void setLineWidth(double width);

    /** @param dashes the dash lengths, or {@code null} / none for a solid line */
    void setLineDashes(double... dashes);

    void setLineCap(StrokeLineCap cap);

    void setLineJoin(StrokeLineJoin join);

    void setGlobalAlpha(double alpha);

    void setFont(Font font);

    void setTextAlign(TextAlignment align);

    void setTextBaseline(VPos baseline);

    // ------------------------------------------------------------------ shapes and text

    void fillText(String text, double x, double y);

    void strokeText(String text, double x, double y);

    void fillRect(double x, double y, double w, double h);

    void strokeRect(double x, double y, double w, double h);

    /** Clears to transparent; surfaces without pixels (SVG) ignore it. */
    void clearRect(double x, double y, double w, double h);

    void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight);

    void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight);

    void fillOval(double x, double y, double w, double h);

    void strokeOval(double x, double y, double w, double h);

    void strokeLine(double x1, double y1, double x2, double y2);

    void strokePolyline(double[] xPoints, double[] yPoints, int nPoints);

    void fillPolygon(double[] xPoints, double[] yPoints, int nPoints);

    void strokePolygon(double[] xPoints, double[] yPoints, int nPoints);

    // ------------------------------------------------------------------ paths

    void beginPath();

    void moveTo(double x, double y);

    void lineTo(double x, double y);

    void quadraticCurveTo(double xc, double yc, double x1, double y1);

    void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1);

    void closePath();

    void rect(double x, double y, double w, double h);

    void fill();

    void stroke();

    /** Intersects the clip with the current path. */
    void clip();
}
