package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;

/** Resolves the {@link ExportMetadataService}, falling back to a property-less instance in tests. */
final class ReportMetadata {

    private ReportMetadata() {
    }

    static ExportMetadataService service() {
        try {
            return ServiceRegistry.get(ExportMetadataService.class);
        } catch (RuntimeException e) {
            // Partially configured registry (unit tests): no user name / company, but the rest works.
            return new ExportMetadataService((org.fxt.freexmltoolkit.service.PropertiesService) null);
        }
    }
}
