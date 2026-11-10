package org.fxt.freexmltoolkit.service.export;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.DoubleConsumer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.apache.fop.svg.AbstractFOPTranscoder;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.controls.v2.view.render.FxDiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.SvgDiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.SvgDiagramGraphics.FontFace;

/**
 * Exports an {@link ExportableDiagram} (XSD diagram, XML/JSON grid, flame graph) as PNG, SVG,
 * PDF or onto the clipboard.
 *
 * <p>Raster images are drawn tile by tile onto an off-screen canvas (a canvas texture is limited to
 * a few thousand pixels) — one tile per FX pulse, so the window stays responsive — and assembled
 * into one image. SVG is drawn through {@link SvgDiagramGraphics}; PDF is that SVG converted by
 * FOP's {@link PDFTranscoder} with the bundled fonts embedded.
 *
 * <p>Drawing must run on the JavaFX thread; encoding and writing files run on the given executor.
 */
public final class DiagramExporter {

    private static final Logger logger = LogManager.getLogger(DiagramExporter.class);

    /** Edge length of one raster tile in pixels. */
    static final int TILE = 2048;
    /** Largest raster image (pixels); larger exports are refused with a hint to use SVG or PDF. */
    public static final long MAX_PIXELS = 150_000_000L;
    /** Largest image put on the clipboard. */
    public static final long MAX_CLIPBOARD_PIXELS = 40_000_000L;
    /** Screen resolution the diagram units correspond to. */
    private static final double BASE_DPI = 96;
    /** Page margin of PDF pages with a fixed size. */
    private static final double PAGE_MARGIN_MM = 10;

    /** Bundled font resources copied to files once per run, for FOP. */
    private static final Map<String, Path> FONT_FILES = new ConcurrentHashMap<>();

    private DiagramExporter() {
    }

    /** Raised when an export cannot be made (nothing to draw, image too large). */
    public static final class DiagramExportException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DiagramExportException(String message) {
            super(message);
        }

        public DiagramExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------ geometry

    /**
     * @return the exported part of the diagram in its coordinates, padding included
     * @throws DiagramExportException when the diagram is empty
     */
    public static Rectangle2D exportRegion(ExportableDiagram diagram, DiagramExportOptions options) {
        Rectangle2D content = diagram.exportContentBounds();
        if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0) {
            throw new DiagramExportException("The view is empty: there is nothing to export.");
        }
        Rectangle2D area = content;
        if (options.area() == DiagramExportOptions.Area.VISIBLE) {
            Rectangle2D visible = diagram.exportVisibleBounds();
            if (visible != null && visible.getWidth() > 0 && visible.getHeight() > 0) {
                Rectangle2D cut = intersection(visible, content);
                area = cut != null ? cut : visible;
            }
        }
        double pad = options.padding();
        return new Rectangle2D(area.getMinX() - pad, area.getMinY() - pad,
                area.getWidth() + 2 * pad, area.getHeight() + 2 * pad);
    }

    private static Rectangle2D intersection(Rectangle2D a, Rectangle2D b) {
        double minX = Math.max(a.getMinX(), b.getMinX());
        double minY = Math.max(a.getMinY(), b.getMinY());
        double maxX = Math.min(a.getMaxX(), b.getMaxX());
        double maxY = Math.min(a.getMaxY(), b.getMaxY());
        return maxX > minX && maxY > minY ? new Rectangle2D(minX, minY, maxX - minX, maxY - minY) : null;
    }

    /** @return {width, height} in pixels of a raster export of the region at the scale */
    public static int[] pixelSize(Rectangle2D region, double scale) {
        long width = Math.max(1, (long) Math.ceil(region.getWidth() * scale));
        long height = Math.max(1, (long) Math.ceil(region.getHeight() * scale));
        return new int[]{(int) Math.min(Integer.MAX_VALUE, width), (int) Math.min(Integer.MAX_VALUE, height)};
    }

    /** @return the background colour of the export, or null for a transparent one */
    static Color backgroundOf(ExportableDiagram diagram, DiagramExportOptions options, DesignTokens.Theme theme) {
        return switch (options.background()) {
            case WHITE -> Color.WHITE;
            case TRANSPARENT -> null;
            case THEME -> diagram.exportBackground(theme);
        };
    }

    // ------------------------------------------------------------------ high-level entry points

    /**
     * Exports to a file in the options' format. Call on the JavaFX thread.
     *
     * @param diagram    the view to export
     * @param options    what to produce
     * @param file       the target file
     * @param background executor for encoding and writing
     * @param progress   receives 0..1 on the FX thread while raster tiles are drawn (may be null)
     * @return completes with the file once written
     */
    public static CompletableFuture<Path> export(ExportableDiagram diagram, DiagramExportOptions options, Path file,
                                                 Executor background, DoubleConsumer progress) {
        try {
            return switch (options.format()) {
                case PNG -> renderImage(diagram, options, progress)
                        .thenApplyAsync(image -> {
                            writePng(image, options.scale(), file);
                            return file;
                        }, background);
                case SVG -> {
                    SvgDiagramGraphics svg = renderSvg(diagram, options);
                    diagram.exportFinished();
                    yield CompletableFuture.supplyAsync(() -> {
                        writeSvg(svg, file);
                        return file;
                    }, background);
                }
                case PDF -> {
                    SvgDiagramGraphics svg = renderSvg(diagram, options);
                    diagram.exportFinished();
                    preparePage(svg, exportRegion(diagram, options), options.pageSize());
                    String xml = svg.toXml();
                    List<FontFace> faces = new ArrayList<>(svg.getUsedFontFaces());
                    yield CompletableFuture.supplyAsync(() -> {
                        writePdf(xml, faces, file);
                        return file;
                    }, background);
                }
            };
        } catch (RuntimeException e) {
            diagram.exportFinished();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Puts the diagram on the clipboard as an image (PNG options: area, scale, palette, background).
     * Call on the JavaFX thread.
     *
     * @return completes on the FX thread once the clipboard holds the image
     */
    public static CompletableFuture<Image> copyToClipboard(ExportableDiagram diagram, DiagramExportOptions options,
                                                           DoubleConsumer progress) {
        Rectangle2D region;
        try {
            region = exportRegion(diagram, options);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        int[] size = pixelSize(region, options.scale());
        if ((long) size[0] * size[1] > MAX_CLIPBOARD_PIXELS) {
            return CompletableFuture.failedFuture(new DiagramExportException(String.format(Locale.ROOT,
                    "The image would be %d × %d pixels, too large for the clipboard. "
                            + "Choose a smaller scale, the visible area, or export to a file.", size[0], size[1])));
        }
        return renderImage(diagram, options, progress).thenApply(image -> {
            Image fxImage = toFxImage(image);
            ClipboardContent content = new ClipboardContent();
            content.putImage(fxImage);
            Clipboard.getSystemClipboard().setContent(content);
            return fxImage;
        });
    }

    // ------------------------------------------------------------------ raster

    /**
     * Draws the diagram into an ARGB image, one tile per FX pulse. Call on the JavaFX thread.
     *
     * @return completes on the FX thread with the image
     */
    public static CompletableFuture<BufferedImage> renderImage(ExportableDiagram diagram, DiagramExportOptions options,
                                                               DoubleConsumer progress) {
        Rectangle2D region;
        try {
            region = exportRegion(diagram, options);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        double scale = options.scale();
        int[] size = pixelSize(region, scale);
        long pixels = (long) size[0] * size[1];
        if (pixels > MAX_PIXELS) {
            return CompletableFuture.failedFuture(new DiagramExportException(String.format(Locale.ROOT,
                    "The image would be %d × %d pixels, more than a PNG export can hold. "
                            + "Choose a smaller scale or the visible area, or export as SVG or PDF.", size[0], size[1])));
        }
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        if (pixels * 4 * 2 > available) {
            return CompletableFuture.failedFuture(new DiagramExportException(String.format(Locale.ROOT,
                    "Not enough memory for a %d × %d pixel image. "
                            + "Choose a smaller scale or the visible area, or export as SVG or PDF.", size[0], size[1])));
        }
        BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        DesignTokens.Theme theme = options.theme().resolve();
        Color background = backgroundOf(diagram, options, theme);

        List<int[]> tiles = new ArrayList<>();
        for (int y = 0; y < size[1]; y += TILE) {
            for (int x = 0; x < size[0]; x += TILE) {
                tiles.add(new int[]{x, y, Math.min(TILE, size[0] - x), Math.min(TILE, size[1] - y)});
            }
        }
        CompletableFuture<BufferedImage> result = new CompletableFuture<>();
        Iterator<int[]> next = tiles.iterator();
        Runnable[] step = new Runnable[1];
        int[] done = {0};
        step[0] = () -> {
            try {
                int[] tile = next.next();
                drawTile(diagram, region, scale, theme, background, tile, target, size[0]);
                done[0]++;
                if (progress != null) {
                    progress.accept((double) done[0] / tiles.size());
                }
                if (next.hasNext()) {
                    Platform.runLater(step[0]);
                } else {
                    diagram.exportFinished();
                    result.complete(image);
                }
            } catch (RuntimeException | OutOfMemoryError e) {
                diagram.exportFinished();
                result.completeExceptionally(e instanceof OutOfMemoryError
                        ? new DiagramExportException("Not enough memory for the image.", e) : e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            step[0].run();
        } else {
            Platform.runLater(step[0]);
        }
        return result;
    }

    /** Draws one tile onto an off-screen canvas and copies its pixels into the image. */
    private static void drawTile(ExportableDiagram diagram, Rectangle2D region, double scale, DesignTokens.Theme theme,
                                 Color background, int[] tile, int[] target, int imageWidth) {
        int x0 = tile[0];
        int y0 = tile[1];
        int w = tile[2];
        int h = tile[3];
        Canvas canvas = new Canvas(w, h);
        DiagramGraphics g = new FxDiagramGraphics(canvas.getGraphicsContext2D());
        if (background != null) {
            g.setFill(background);
            g.fillRect(0, 0, w, h);
        }
        double left = region.getMinX() + x0 / scale;
        double top = region.getMinY() + y0 / scale;
        g.save();
        g.scale(scale, scale);
        g.translate(-left, -top);
        diagram.paintExport(g, new Rectangle2D(left, top, w / scale, h / scale), theme);
        g.restore();

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage snapshot = canvas.snapshot(parameters, null);
        int sw = Math.min(w, (int) snapshot.getWidth());
        int sh = Math.min(h, (int) snapshot.getHeight());
        int[] row = new int[sw * sh];
        snapshot.getPixelReader().getPixels(0, 0, sw, sh, PixelFormat.getIntArgbInstance(), row, 0, sw);
        for (int y = 0; y < sh; y++) {
            System.arraycopy(row, y * sw, target, (y0 + y) * imageWidth + x0, sw);
        }
    }

    /** @return the image as a JavaFX image */
    public static Image toFxImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        WritableImage fx = new WritableImage(w, h);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return fx;
    }

    /**
     * Writes a PNG whose resolution metadata (pHYs) makes it print at the diagram's natural size.
     *
     * @param scale the pixels per diagram unit the image was drawn at
     */
    public static void writePng(BufferedImage image, double scale, Path file) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new DiagramExportException("No PNG encoder available.");
        }
        ImageWriter writer = writers.next();
        try (OutputStream out = Files.newOutputStream(file);
             ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
            try {
                String pixelsPerMeter = Long.toString(Math.round(BASE_DPI * scale / 0.0254));
                IIOMetadataNode physical = new IIOMetadataNode("pHYs");
                physical.setAttribute("pixelsPerUnitXAxis", pixelsPerMeter);
                physical.setAttribute("pixelsPerUnitYAxis", pixelsPerMeter);
                physical.setAttribute("unitSpecifier", "meter");
                IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
                root.appendChild(physical);
                metadata.mergeTree("javax_imageio_png_1.0", root);
            } catch (IOException | RuntimeException e) {
                logger.debug("PNG resolution metadata not written: {}", e.getMessage());
            }
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, metadata), param);
        } catch (IOException e) {
            throw new DiagramExportException("Could not write " + file.getFileName() + ": " + e.getMessage(), e);
        } finally {
            writer.dispose();
        }
    }

    // ------------------------------------------------------------------ vector

    /**
     * Draws the diagram into an SVG document sized to the export region. Call on the JavaFX thread;
     * the caller calls {@link ExportableDiagram#exportFinished()} afterwards.
     */
    public static SvgDiagramGraphics renderSvg(ExportableDiagram diagram, DiagramExportOptions options) {
        Rectangle2D region = exportRegion(diagram, options);
        DesignTokens.Theme theme = options.theme().resolve();
        Color background = backgroundOf(diagram, options, theme);
        SvgDiagramGraphics svg = new SvgDiagramGraphics(region.getWidth(), region.getHeight());
        String title = options.title().isBlank() ? diagram.exportTitle() : options.title();
        svg.setTitle(title);
        if (background != null) {
            svg.setFill(background);
            svg.fillRect(0, 0, region.getWidth(), region.getHeight());
        }
        svg.save();
        svg.translate(-region.getMinX(), -region.getMinY());
        diagram.paintExport(svg, region, theme);
        svg.restore();
        if (options.embedFonts() && options.format() == DiagramExportOptions.Format.SVG) {
            svg.embedBundledFonts();
        }
        return svg;
    }

    /** Writes the SVG document as UTF-8. */
    public static void writeSvg(SvgDiagramGraphics svg, Path file) {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            svg.writeTo(out);
        } catch (IOException e) {
            throw new DiagramExportException("Could not write " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Puts the drawing on a page: {@link DiagramExportOptions.PageSize#FIT} keeps the drawing's
     * size, a paper size centres it with a margin, rotated to landscape for wide drawings and
     * shrunk to fit (never enlarged).
     */
    static void preparePage(SvgDiagramGraphics svg, Rectangle2D region, DiagramExportOptions.PageSize pageSize) {
        if (pageSize == DiagramExportOptions.PageSize.FIT) {
            return;
        }
        double pxPerMm = BASE_DPI / 25.4;
        boolean landscape = region.getWidth() > region.getHeight();
        double pageW = (landscape ? pageSize.longSideMm() : pageSize.shortSideMm()) * pxPerMm;
        double pageH = (landscape ? pageSize.shortSideMm() : pageSize.longSideMm()) * pxPerMm;
        double margin = PAGE_MARGIN_MM * pxPerMm;
        double fit = Math.min(1, Math.min((pageW - 2 * margin) / region.getWidth(),
                (pageH - 2 * margin) / region.getHeight()));
        // The view box in drawing units that maps onto the whole page at that scale, content centred
        double viewW = pageW / fit;
        double viewH = pageH / fit;
        double viewX = (region.getWidth() - viewW) / 2;
        double viewY = (region.getHeight() - viewH) / 2;
        svg.setDocumentSize(pageW, pageH, viewX, viewY, viewW, viewH);
    }

    /**
     * Converts the SVG to PDF with FOP, embedding the bundled fonts the drawing uses so the text
     * stays text (selectable, searchable) in the faces it was laid out with.
     */
    public static void writePdf(String svgXml, List<FontFace> faces, Path file) {
        PDFTranscoder transcoder = new PDFTranscoder();
        transcoder.addTranscodingHint(AbstractFOPTranscoder.KEY_AUTO_FONTS, Boolean.FALSE);
        transcoder.addTranscodingHint(AbstractFOPTranscoder.KEY_STROKE_TEXT, Boolean.FALSE);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, (float) (25.4 / BASE_DPI));
        try {
            transcoder.configure(new DefaultConfigurationBuilder().build(
                    new java.io.ByteArrayInputStream(fontConfiguration(faces).getBytes(StandardCharsets.UTF_8))));
        } catch (ConfigurationException e) {
            logger.warn("PDF export without embedded fonts: {}", e.getMessage());
        }
        Path temp = null;
        try {
            // A sibling of the target (same file system, normal permissions), moved over it when complete
            temp = file.toAbsolutePath().resolveSibling("." + file.getFileName() + ".part");
            try (OutputStream out = Files.newOutputStream(temp)) {
                TranscoderInput input = new TranscoderInput(new StringReader(svgXml));
                input.setURI(file.toAbsolutePath().getParent().toUri().resolve("diagram.svg").toString());
                transcoder.transcode(input, new TranscoderOutput(out));
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (IOException | TranscoderException e) {
            throw new DiagramExportException("Could not write " + file.getFileName() + ": " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    logger.debug("Temporary PDF not deleted: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * @return the FOP transcoder configuration registering the bundled font files of the faces.
     *         FOP converts CSS weights through AWT's weight scale and back, so 600 can arrive as
     *         another weight: every weight 100–900 of a family is therefore mapped to the file of the
     *         nearest weight the drawing uses.
     */
    static String fontConfiguration(List<FontFace> faces) {
        // family + italic → weight → font file
        Map<String, java.util.TreeMap<Integer, Path>> files = new java.util.LinkedHashMap<>();
        Map<String, FontFace> keys = new java.util.HashMap<>();
        for (FontFace face : faces) {
            String resource = SvgDiagramGraphics.bundledFontResource(face);
            Path fontFile = resource == null ? null : fontFile(resource);
            if (fontFile != null) {
                String key = face.family() + "/" + face.italic();
                files.computeIfAbsent(key, k -> new java.util.TreeMap<>()).putIfAbsent(face.weight(), fontFile);
                keys.putIfAbsent(key, face);
            }
        }
        StringBuilder xml = new StringBuilder("<cfg><fonts>");
        for (Map.Entry<String, java.util.TreeMap<Integer, Path>> entry : files.entrySet()) {
            FontFace face = keys.get(entry.getKey());
            java.util.TreeMap<Integer, Path> byWeight = entry.getValue();
            Map<Path, List<Integer>> weightsPerFile = new java.util.LinkedHashMap<>();
            for (int weight = 100; weight <= 900; weight += 100) {
                Integer below = byWeight.floorKey(weight);
                Integer above = byWeight.ceilingKey(weight);
                int nearest = below == null ? above : above == null ? below
                        : (weight - below <= above - weight ? below : above);
                weightsPerFile.computeIfAbsent(byWeight.get(nearest), p -> new ArrayList<>()).add(weight);
            }
            for (Map.Entry<Path, List<Integer>> file : weightsPerFile.entrySet()) {
                xml.append("<font kerning=\"yes\" embed-url=\"").append(escapeAttribute(file.getKey().toUri().toString()))
                        .append("\">");
                for (int weight : file.getValue()) {
                    xml.append("<font-triplet name=\"").append(escapeAttribute(face.family()))
                            .append("\" style=\"").append(face.italic() ? "italic" : "normal")
                            .append("\" weight=\"").append(weight).append("\"/>");
                }
                xml.append("</font>");
            }
        }
        return xml.append("</fonts></cfg>").toString();
    }

    /** @return the bundled font resource copied to a temporary file (once per run), or null */
    private static Path fontFile(String resource) {
        return FONT_FILES.computeIfAbsent(resource, r -> {
            try (InputStream in = DiagramExporter.class.getResourceAsStream(r)) {
                if (in == null) {
                    return null;
                }
                String name = r.substring(r.lastIndexOf('/') + 1);
                Path file = Files.createTempFile("fxt-font-", "-" + name);
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                file.toFile().deleteOnExit();
                return file;
            } catch (IOException e) {
                logger.warn("Font {} not available for the PDF export: {}", r, e.getMessage());
                return null;
            }
        });
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
