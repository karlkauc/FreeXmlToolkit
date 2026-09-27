package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Facade: picks the {@link ReportWriter} for a {@link ReportFormat}. */
public final class ReportWriters {

    /** Timestamp format shared by all writers. */
    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportWriters() {
    }

    /** @return the writer for {@code format}. */
    public static ReportWriter forFormat(ReportFormat format) {
        return switch (format) {
            case CSV -> new CsvReportWriter();
            case JSON -> new JsonReportWriter();
            case HTML -> new HtmlReportWriter();
            case PDF -> new PdfReportWriter();
            case XLSX -> new ExcelReportWriter();
        };
    }

    /** Writes {@code model} as {@code format} to {@code target}. */
    public static void write(ReportModel model, ReportFormat format, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        forFormat(format).write(model, target);
    }
}
