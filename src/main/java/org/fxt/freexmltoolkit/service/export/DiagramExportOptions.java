package org.fxt.freexmltoolkit.service.export;

import java.util.Locale;
import java.util.Objects;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;

/**
 * What a diagram export produces: format, area, scale, palette, background, padding, fonts and
 * (PDF) page size.
 *
 * @param format     the output format
 * @param area       the whole diagram or the part on screen
 * @param scale      pixels per diagram unit for PNG / clipboard (1–4); vector formats ignore it
 * @param theme      the palette to draw with
 * @param background the page background
 * @param padding    margin around the content in diagram units
 * @param embedFonts SVG: embed the bundled fonts (PDF always embeds them)
 * @param pageSize   PDF page size
 * @param title      document title (SVG {@code <title>}, PDF metadata); may be blank
 */
public record DiagramExportOptions(Format format, Area area, double scale, ThemeChoice theme,
                                   Background background, double padding, boolean embedFonts,
                                   PageSize pageSize, String title) {

    /** Largest PNG scale offered. */
    public static final double MAX_SCALE = 4;

    /** Output formats. */
    public enum Format {
        PNG("png"), SVG("svg"), PDF("pdf");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        /** @return the file name extension without the dot */
        public String extension() {
            return extension;
        }

        /** @return the format for a file name's extension, or null */
        public static Format forFileName(String fileName) {
            if (fileName == null) {
                return null;
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            for (Format format : values()) {
                if (lower.endsWith("." + format.extension)) {
                    return format;
                }
            }
            return null;
        }
    }

    /** Which part of the diagram is exported. */
    public enum Area {
        WHOLE, VISIBLE
    }

    /** The palette of the export. */
    public enum ThemeChoice {
        LIGHT, DARK, AS_SHOWN;

        /** @return the concrete palette, {@link #AS_SHOWN} resolved to the current theme */
        public DesignTokens.Theme resolve() {
            return switch (this) {
                case LIGHT -> DesignTokens.Theme.LIGHT;
                case DARK -> DesignTokens.Theme.DARK;
                case AS_SHOWN -> DesignTokens.currentTheme();
            };
        }
    }

    /** The page behind the content. */
    public enum Background {
        /** The diagram's own page colour in the chosen palette. */
        THEME,
        WHITE,
        TRANSPARENT
    }

    /** PDF page sizes; the orientation follows the diagram's aspect ratio. */
    public enum PageSize {
        /** One page exactly as large as the diagram. */
        FIT(0, 0),
        A4(210, 297),
        A3(297, 420),
        LETTER(215.9, 279.4);

        private final double widthMm;
        private final double heightMm;

        PageSize(double widthMm, double heightMm) {
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }

        /** @return the short side in millimetres (0 for {@link #FIT}) */
        public double shortSideMm() {
            return widthMm;
        }

        /** @return the long side in millimetres (0 for {@link #FIT}) */
        public double longSideMm() {
            return heightMm;
        }
    }

    /** Validates and normalises the values. */
    public DiagramExportOptions {
        Objects.requireNonNull(format, "format");
        area = area == null ? Area.WHOLE : area;
        scale = Double.isFinite(scale) ? Math.max(0.25, Math.min(MAX_SCALE, scale)) : 1;
        theme = theme == null ? ThemeChoice.LIGHT : theme;
        background = background == null ? Background.THEME : background;
        padding = Double.isFinite(padding) ? Math.max(0, Math.min(500, padding)) : 0;
        pageSize = pageSize == null ? PageSize.FIT : pageSize;
        title = title == null ? "" : title;
    }

    /**
     * @param format the output format
     * @return the defaults: whole diagram, scale 2, light palette, theme background, 16 px padding
     */
    public static DiagramExportOptions defaults(Format format) {
        return new DiagramExportOptions(format, Area.WHOLE, 2, ThemeChoice.LIGHT, Background.THEME, 16,
                false, PageSize.FIT, "");
    }

    /** @return a copy with another format */
    public DiagramExportOptions withFormat(Format newFormat) {
        return new DiagramExportOptions(newFormat, area, scale, theme, background, padding, embedFonts, pageSize, title);
    }

    /** @return a copy with another area */
    public DiagramExportOptions withArea(Area newArea) {
        return new DiagramExportOptions(format, newArea, scale, theme, background, padding, embedFonts, pageSize, title);
    }

    /** @return a copy with another title */
    public DiagramExportOptions withTitle(String newTitle) {
        return new DiagramExportOptions(format, area, scale, theme, background, padding, embedFonts, pageSize, newTitle);
    }
}
