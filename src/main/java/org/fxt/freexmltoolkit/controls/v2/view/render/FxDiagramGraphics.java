package org.fxt.freexmltoolkit.controls.v2.view.render;

import java.util.Objects;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** {@link DiagramGraphics} on a JavaFX canvas: every call goes straight to its {@link GraphicsContext}. */
public final class FxDiagramGraphics implements DiagramGraphics {

    private final GraphicsContext gc;

    /** @param gc the canvas context to draw on */
    public FxDiagramGraphics(GraphicsContext gc) {
        this.gc = Objects.requireNonNull(gc, "gc");
    }

    /** @return the wrapped canvas context */
    public GraphicsContext context() {
        return gc;
    }

    @Override public void save() { gc.save(); }
    @Override public void restore() { gc.restore(); }
    @Override public void translate(double x, double y) { gc.translate(x, y); }
    @Override public void scale(double x, double y) { gc.scale(x, y); }
    @Override public void setFill(Paint paint) { gc.setFill(paint); }
    @Override public Paint getFill() { return gc.getFill(); }
    @Override public void setStroke(Paint paint) { gc.setStroke(paint); }
    @Override public void setLineWidth(double width) { gc.setLineWidth(width); }
    @Override public void setLineDashes(double... dashes) { gc.setLineDashes(dashes); }
    @Override public void setLineCap(StrokeLineCap cap) { gc.setLineCap(cap); }
    @Override public void setLineJoin(StrokeLineJoin join) { gc.setLineJoin(join); }
    @Override public void setGlobalAlpha(double alpha) { gc.setGlobalAlpha(alpha); }
    @Override public void setFont(Font font) { gc.setFont(font); }
    @Override public void setTextAlign(TextAlignment align) { gc.setTextAlign(align); }
    @Override public void setTextBaseline(VPos baseline) { gc.setTextBaseline(baseline); }
    @Override public void fillText(String text, double x, double y) { gc.fillText(text, x, y); }
    @Override public void strokeText(String text, double x, double y) { gc.strokeText(text, x, y); }
    @Override public void fillRect(double x, double y, double w, double h) { gc.fillRect(x, y, w, h); }
    @Override public void strokeRect(double x, double y, double w, double h) { gc.strokeRect(x, y, w, h); }
    @Override public void clearRect(double x, double y, double w, double h) { gc.clearRect(x, y, w, h); }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        gc.fillRoundRect(x, y, w, h, arcWidth, arcHeight);
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) {
        gc.strokeRoundRect(x, y, w, h, arcWidth, arcHeight);
    }

    @Override public void fillOval(double x, double y, double w, double h) { gc.fillOval(x, y, w, h); }
    @Override public void strokeOval(double x, double y, double w, double h) { gc.strokeOval(x, y, w, h); }
    @Override public void strokeLine(double x1, double y1, double x2, double y2) { gc.strokeLine(x1, y1, x2, y2); }

    @Override
    public void strokePolyline(double[] xPoints, double[] yPoints, int nPoints) {
        gc.strokePolyline(xPoints, yPoints, nPoints);
    }

    @Override
    public void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) {
        gc.fillPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        gc.strokePolygon(xPoints, yPoints, nPoints);
    }

    @Override public void beginPath() { gc.beginPath(); }
    @Override public void moveTo(double x, double y) { gc.moveTo(x, y); }
    @Override public void lineTo(double x, double y) { gc.lineTo(x, y); }

    @Override
    public void quadraticCurveTo(double xc, double yc, double x1, double y1) {
        gc.quadraticCurveTo(xc, yc, x1, y1);
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        gc.bezierCurveTo(xc1, yc1, xc2, yc2, x1, y1);
    }

    @Override public void closePath() { gc.closePath(); }
    @Override public void rect(double x, double y, double w, double h) { gc.rect(x, y, w, h); }
    @Override public void fill() { gc.fill(); }
    @Override public void stroke() { gc.stroke(); }
    @Override public void clip() { gc.clip(); }
}
