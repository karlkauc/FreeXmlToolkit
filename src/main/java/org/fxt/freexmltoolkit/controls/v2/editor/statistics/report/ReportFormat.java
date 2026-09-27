package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

/** The file formats a {@link ReportModel} can be written to. */
public enum ReportFormat {
    CSV("csv", "CSV", "bi-filetype-csv"),
    JSON("json", "JSON", "bi-filetype-json"),
    HTML("html", "HTML", "bi-filetype-html"),
    PDF("pdf", "PDF", "bi-filetype-pdf"),
    XLSX("xlsx", "Excel", "bi-file-earmark-excel");

    private final String extension;
    private final String label;
    private final String icon;

    ReportFormat(String extension, String label, String icon) {
        this.extension = extension;
        this.label = label;
        this.icon = icon;
    }

    public String extension() {
        return extension;
    }

    public String label() {
        return label;
    }

    /** @return the Bootstrap icon literal used in export menus. */
    public String icon() {
        return icon;
    }
}
