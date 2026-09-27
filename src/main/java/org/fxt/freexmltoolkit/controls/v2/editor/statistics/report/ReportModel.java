package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A format-independent report: title, subject line, generation time and ordered sections.
 * Every {@link ReportWriter} renders this model, so a fact added here appears in every format.
 *
 * @param title       report title ("Schema Analysis Report")
 * @param subject     what was analyzed (document name and path)
 * @param generatedAt generation time
 * @param sections    the chapters in display order
 */
public record ReportModel(String title, String subject, LocalDateTime generatedAt, List<ReportSection> sections) {

    public ReportModel {
        title = title == null ? "" : title;
        subject = subject == null ? "" : subject;
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** @return a copy holding only the sections whose id is in {@code ids} (keeps this order). */
    public ReportModel only(String... ids) {
        List<String> wanted = List.of(ids);
        return new ReportModel(title, subject, generatedAt,
                sections.stream().filter(s -> wanted.contains(s.id())).toList());
    }
}
