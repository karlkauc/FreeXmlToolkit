package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.controls.shell.schema.XsdSpreadsheetTypeResolver;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.SpreadsheetTypeResolver;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowLimitExceededException;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * UI-free XML &harr; spreadsheet conversion for the shell, reusing
 * {@link XmlSpreadsheetConverterService}. Every call returns a {@link Result}: exports carry
 * {@code "OK: <path>"}, imports the resulting XML string; failures carry {@code "ERROR: …"}
 * plus the exception, so the error dialog (and the anonymous error report) gets its class
 * and stack instead of the bare message.
 */
public final class SpreadsheetActionRunner {

    /**
     * Outcome of a conversion.
     *
     * @param text   {@code "OK: <path>"} (export), the XML (import) or {@code "ERROR: …"}
     * @param error  the failure, or null on success
     * @param schema the XSD an import read its values with, or null
     */
    public record Result(String text, Exception error, File schema) {

        /** A result without a schema. */
        public Result(String text, Exception error) {
            this(text, error, null);
        }

        static Result ok(String text) {
            return new Result(text, null);
        }

        static Result failed(Exception e) {
            return new Result("ERROR: " + e.getMessage(), e);
        }

        /** @return whether the conversion failed */
        public boolean isError() {
            return error != null;
        }

        /**
         * @return the telemetry status: a malformed document is the user's input, a document
         *         beyond the format's row limit or the heap is unsupported, anything else an error
         */
        public TelemetryEvent.Status telemetryStatus() {
            if (error == null) {
                return TelemetryEvent.Status.OK;
            }
            if (error instanceof SAXParseException) {
                return TelemetryEvent.Status.INVALID_INPUT;
            }
            if (error instanceof RowLimitExceededException || error instanceof InsufficientMemoryException) {
                return TelemetryEvent.Status.UNSUPPORTED;
            }
            return TelemetryEvent.Status.ERROR;
        }
    }

    /**
     * The document is too large to convert with the available heap. Raised in place of the
     * {@link OutOfMemoryError} so the user gets an explanation instead of an error report.
     */
    public static final class InsufficientMemoryException extends Exception {
        private final long maxHeap;

        InsufficientMemoryException(long maxHeap, OutOfMemoryError cause) {
            super("Not enough memory to convert the document (maximum heap "
                    + LargeFileGuard.formatSize(maxHeap) + ")", cause);
            this.maxHeap = maxHeap;
        }

        /** @return the maximum heap in bytes */
        public long getMaxHeap() {
            return maxHeap;
        }
    }

    private SpreadsheetActionRunner() {
    }

    // ----- XML -> spreadsheet ---------------------------------------------

    /** Converts the XML to a comma-separated CSV file (default options). */
    public static Result exportToCsv(String xmlContent, File output) {
        return exportToCsv(xmlContent, output, CsvHandler.CsvConfig.comma(), new ConversionConfig());
    }

    /** Converts the XML to a CSV file with the given delimiter/conversion options. */
    public static Result exportToCsv(String xmlContent, File output,
                                     CsvHandler.CsvConfig csvConfig, ConversionConfig config) {
        return exportToCsv(xmlContent, output, csvConfig, config, SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts the XML to a CSV file; with a schema-based {@code types} it gets a "Data Type" column,
     * typed values written bare and text quoted.
     */
    public static Result exportToCsv(String xmlContent, File output, CsvHandler.CsvConfig csvConfig,
                                     ConversionConfig config, SpreadsheetTypeResolver types) {
        try {
            Document doc = parseXml(xmlContent);
            new XmlSpreadsheetConverterService().convertXmlToCsv(doc, output, csvConfig, config, types);
            return Result.ok("OK: " + output.getAbsolutePath());
        } catch (Exception e) {
            return Result.failed(e);
        } catch (OutOfMemoryError e) {
            return outOfMemory(e);
        }
    }

    /** Converts the XML to an Excel (.xlsx) workbook (default options). */
    public static Result exportToExcel(String xmlContent, File output) {
        return exportToExcel(xmlContent, output, new ConversionConfig());
    }

    /** Converts the XML to an Excel (.xlsx) workbook with the given conversion options. */
    public static Result exportToExcel(String xmlContent, File output, ConversionConfig config) {
        return exportToExcel(xmlContent, output, config, SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts the XML to an Excel (.xlsx) workbook, typing the value cells as {@code types} says
     * (from the bound XSD; {@code null} or {@link SpreadsheetTypeResolver#NONE}: all text).
     */
    public static Result exportToExcel(String xmlContent, File output, ConversionConfig config,
                                       SpreadsheetTypeResolver types) {
        try {
            Document doc = parseXml(xmlContent);
            new XmlSpreadsheetConverterService().convertXmlToExcel(doc, output, config, types);
            return Result.ok("OK: " + output.getAbsolutePath());
        } catch (Exception e) {
            return Result.failed(e);
        } catch (OutOfMemoryError e) {
            return outOfMemory(e);
        }
    }

    // ----- spreadsheet -> XML ---------------------------------------------

    /** Imports an Excel (.xlsx) workbook back to XML (default options). @return the XML, or the failure. */
    public static Result excelToXml(File excelFile) {
        return excelToXml(excelFile, new ConversionConfig());
    }

    /** Imports an Excel (.xlsx) workbook back to XML, reading the values untyped. @return the XML, or the failure. */
    public static Result excelToXml(File excelFile, ConversionConfig config) {
        try {
            XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
            return Result.ok(service.documentToString(service.convertExcelToXml(excelFile, config), config));
        } catch (Exception e) {
            return Result.failed(e);
        }
    }

    /**
     * Imports an Excel (.xlsx) workbook back to XML, reading each value as its XSD type says (a date
     * cell under {@code xs:dateTime} gets a time, {@code 1} under {@code xs:boolean} becomes
     * {@code true}, …).
     *
     * @param schema the XSD to use; {@code null} to take the one the sheet's document element declares
     *               ({@code xsi:schemaLocation}, relative to the workbook's folder) or the Schema Library
     *               maps it to - without one the values are read untyped
     * @return the XML and the XSD used ({@link Result#schema()}), or the failure; an explicit
     *         schema that cannot be parsed fails the import
     */
    public static Result excelToXml(File excelFile, ConversionConfig config, File schema) {
        return importWithSchema(excelFile, config, schema,
                (service, typesFor) -> service.convertExcelToXml(excelFile, config, typesFor));
    }

    /**
     * Imports a CSV file back to XML, repairing each value for its XSD type ({@code 31.01.2024} under
     * {@code xs:date}, {@code 10,50} under {@code xs:decimal}, …); values already valid stay as written.
     *
     * @param schema the XSD to use; {@code null} to take the one the file's document element declares or
     *               the Schema Library maps it to - without one the values are imported as written
     * @return the XML and the XSD used, or the failure; an explicit schema that cannot be parsed fails it
     */
    public static Result csvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config, File schema) {
        return importWithSchema(csvFile, config, schema,
                (service, typesFor) -> service.convertCsvToXml(csvFile, csvConfig, config, typesFor));
    }

    /** One spreadsheet import, given the value types for the document element it reads. */
    @FunctionalInterface
    private interface SchemaImport {
        Document run(XmlSpreadsheetConverterService service,
                     java.util.function.Function<String, SpreadsheetTypeResolver> typesFor) throws Exception;
    }

    /** Runs an import with the chosen or declared XSD; reports which one was used. */
    private static Result importWithSchema(File source, ConversionConfig config, File schema, SchemaImport run) {
        File[] used = new File[1];
        try {
            XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
            Document doc = run.run(service, rootElement -> {
                File xsd = schema != null ? schema
                        : EditorSchemaBinding.declaredSchemaFile(rootElement, source.getAbsoluteFile().getParentFile())
                                .orElse(null);
                if (xsd == null) {
                    return SpreadsheetTypeResolver.NONE;
                }
                try {
                    var types = new XsdSpreadsheetTypeResolver(new XsdNodeFactory().fromFile(xsd.toPath()));
                    used[0] = xsd;
                    return types;
                } catch (Exception e) {
                    if (schema != null) {
                        throw new SchemaNotReadableException(xsd, e);
                    }
                    return SpreadsheetTypeResolver.NONE; // a detected schema is a convenience only
                }
            });
            return new Result(service.documentToString(doc, config), null, used[0]);
        } catch (SchemaNotReadableException e) {
            return Result.failed(new java.io.IOException(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            return Result.failed(e);
        }
    }

    /** The XSD chosen for an import could not be parsed. */
    private static final class SchemaNotReadableException extends RuntimeException {
        SchemaNotReadableException(File xsd, Exception cause) {
            super("Cannot read the schema " + xsd.getName() + ": " + cause.getMessage(), cause);
        }
    }

    /** Imports a comma-separated CSV file back to XML (default options). @return the XML, or the failure. */
    public static Result csvToXml(File csvFile) {
        return csvToXml(csvFile, CsvHandler.CsvConfig.comma(), new ConversionConfig());
    }

    /** Imports a CSV file back to XML with the given delimiter/options. @return the XML, or the failure. */
    public static Result csvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) {
        try {
            XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
            return Result.ok(service.documentToString(service.convertCsvToXml(csvFile, csvConfig, config), config));
        } catch (Exception e) {
            return Result.failed(e);
        }
    }

    // ----- previews (Spreadsheet Converter dialog) ---------------------------

    /** Documents larger than this get no export preview (it parses the whole document). */
    public static final int PREVIEW_MAX_CHARS = 5 * 1024 * 1024;
    /** Files larger than this get no import preview. */
    public static final long PREVIEW_MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * What an export would write, for the dialog's preview.
     *
     * @param candidates the repeating elements that can be the record (table layout), default first
     * @param recordPath the record used, or {@code null} (XPath list)
     * @param headers    the header row
     * @param rows       the first rows
     * @param totalRows  all data rows
     * @param otherData  whether values outside the records exist (only in the Excel workbook)
     * @param error      why there is no preview, or {@code null}
     */
    public record ExportPreview(java.util.List<org.fxt.freexmltoolkit.service.spreadsheet.RecordTableLayout.Candidate> candidates,
                                String recordPath, java.util.List<String> headers,
                                java.util.List<java.util.List<String>> rows, int totalRows, boolean otherData,
                                String error) {

        static ExportPreview failed(String error) {
            return new ExportPreview(java.util.List.of(), null, java.util.List.of(), java.util.List.of(), 0, false, error);
        }
    }

    /**
     * Builds the preview of an export of {@code xml} with {@code config} (layout, record, type column).
     * Never throws: a failure is the preview's {@code error}.
     */
    public static ExportPreview exportPreview(String xml, ConversionConfig config, int maxRows) {
        if (xml == null || xml.isBlank()) {
            return ExportPreview.failed(org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-no-document"));
        }
        if (xml.length() > PREVIEW_MAX_CHARS) {
            return ExportPreview.failed(org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-too-large"));
        }
        try {
            var service = new XmlSpreadsheetConverterService();
            var rows = service.extractRowsFromXml(parseXml(xml), config);
            if (config.getLayout() != XmlSpreadsheetConverterService.Layout.TABLE) {
                java.util.List<String> headers = config.isIncludeTypeColumn()
                        ? java.util.List.of("XPath", "Value", "Type") : java.util.List.of("XPath", "Value");
                java.util.List<java.util.List<String>> values = new java.util.ArrayList<>();
                for (var row : rows.subList(0, Math.min(maxRows, rows.size()))) {
                    values.add(config.isIncludeTypeColumn()
                            ? java.util.List.of(row.getXpath(), row.getValue(), row.getNodeType())
                            : java.util.List.of(row.getXpath(), row.getValue()));
                }
                return new ExportPreview(java.util.List.of(), null, headers, values, rows.size(), false, null);
            }
            var candidates = org.fxt.freexmltoolkit.service.spreadsheet.RecordTableLayout.candidates(rows);
            var table = service.toTable(rows, config);
            java.util.List<java.util.List<String>> values = new java.util.ArrayList<>();
            for (var record : table.records().subList(0, Math.min(maxRows, table.records().size()))) {
                java.util.List<String> line = new java.util.ArrayList<>();
                for (var cell : table.cellsOf(record)) {
                    line.add(cell == null ? "" : cell.getValue());
                }
                values.add(line);
            }
            return new ExportPreview(candidates, table.recordPath(), table.headerRow(), values,
                    table.records().size(), table.hasOtherData(), null);
        } catch (org.xml.sax.SAXParseException e) {
            return ExportPreview.failed(org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-not-well-formed",
                    e.getLineNumber(), e.getMessage()));
        } catch (Exception | OutOfMemoryError e) {
            return ExportPreview.failed(org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-failed", String.valueOf(e.getMessage())));
        }
    }

    /**
     * The first {@code lines} lines of the XML an import of {@code input} would give (without a schema).
     * Never throws: a failure is returned as the text.
     */
    public static String importPreview(File input, CsvHandler.CsvConfig csvConfig, ConversionConfig config, int lines) {
        if (input == null || !input.isFile()) {
            return org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-choose-input");
        }
        if (input.length() > PREVIEW_MAX_FILE_BYTES) {
            return org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-file-too-large");
        }
        String name = input.getName().toLowerCase(java.util.Locale.ROOT);
        Result result = name.endsWith(".xlsx") || name.endsWith(".xls")
                ? excelToXml(input, config) : csvToXml(input, csvConfig, config);
        if (result.isError()) {
            return org.fxt.freexmltoolkit.i18n.I18n.t("SpreadsheetActionRunner.preview-unreadable", String.valueOf(result.error().getMessage()));
        }
        return result.text().lines().limit(lines).collect(java.util.stream.Collectors.joining("\n"));
    }

    // ----- helpers ---------------------------------------------------------

    /**
     * The DOM and the rows were local to the failed call and are unreachable by now, so the
     * heap is free again; a half-written output file is removed by the caller's next attempt.
     */
    private static Result outOfMemory(OutOfMemoryError e) {
        return Result.failed(new InsufficientMemoryException(Runtime.getRuntime().maxMemory(), e));
    }

    /** Parses XML with an XXE-hardened factory (no DOCTYPE / external entities / external DTD / XInclude). */
    private static Document parseXml(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));
    }
}
