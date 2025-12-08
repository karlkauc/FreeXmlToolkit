package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports XSD statistics to various formats (CSV, JSON, PDF).
 *
 * @since 2.0
 */
public class XsdStatisticsExporter {

    private static final Logger logger = LogManager.getLogger(XsdStatisticsExporter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Exports statistics to CSV format.
     *
     * @param statistics the statistics to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToCsv(XsdStatistics statistics, Path outputPath) throws IOException {
        logger.info("Exporting statistics to CSV: {}", outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            // Header
            writer.write("Category,Property,Value\n");

            // Schema Information
            writeCsvLine(writer, "Schema Info", "XSD Version", statistics.xsdVersion());
            writeCsvLine(writer, "Schema Info", "Target Namespace", statistics.targetNamespace());
            writeCsvLine(writer, "Schema Info", "Element Form Default", statistics.elementFormDefault());
            writeCsvLine(writer, "Schema Info", "Attribute Form Default", statistics.attributeFormDefault());
            writeCsvLine(writer, "Schema Info", "Namespace Count", String.valueOf(statistics.namespaceCount()));
            writeCsvLine(writer, "Schema Info", "File Count", String.valueOf(statistics.fileCount()));
            if (statistics.mainSchemaPath() != null) {
                writeCsvLine(writer, "Schema Info", "Main Schema Path", statistics.mainSchemaPath().toString());
            }

            // Node Counts
            writeCsvLine(writer, "Node Counts", "Total Nodes", String.valueOf(statistics.totalNodeCount()));
            for (XsdNodeType type : XsdNodeType.values()) {
                int count = statistics.getNodeCount(type);
                if (count > 0) {
                    writeCsvLine(writer, "Node Counts", type.name(), String.valueOf(count));
                }
            }

            // Documentation Statistics
            writeCsvLine(writer, "Documentation", "Nodes with Documentation", String.valueOf(statistics.nodesWithDocumentation()));
            writeCsvLine(writer, "Documentation", "Nodes with AppInfo", String.valueOf(statistics.nodesWithAppInfo()));
            writeCsvLine(writer, "Documentation", "Coverage (%)", String.format("%.1f", statistics.documentationCoveragePercent()));

            // AppInfo Tags
            for (Map.Entry<String, Integer> entry : statistics.appInfoTagCounts().entrySet()) {
                writeCsvLine(writer, "AppInfo Tags", entry.getKey(), String.valueOf(entry.getValue()));
            }

            // Documentation Languages
            for (String lang : statistics.documentationLanguages()) {
                writeCsvLine(writer, "Documentation", "Language", lang);
            }

            // Type Usage
            for (XsdStatistics.TypeUsageEntry entry : statistics.topUsedTypes()) {
                writeCsvLine(writer, "Type Usage (Top 10)", entry.typeName(), String.valueOf(entry.usageCount()));
            }

            // Unused Types
            for (String unusedType : statistics.unusedTypes()) {
                writeCsvLine(writer, "Unused Types", unusedType, "0");
            }

            // Cardinality
            writeCsvLine(writer, "Cardinality", "Optional Elements", String.valueOf(statistics.optionalElements()));
            writeCsvLine(writer, "Cardinality", "Required Elements", String.valueOf(statistics.requiredElements()));
            writeCsvLine(writer, "Cardinality", "Unbounded Elements", String.valueOf(statistics.unboundedElements()));

            // Metadata
            writeCsvLine(writer, "Metadata", "Collected At", statistics.collectedAt().format(DATE_FORMATTER));
        }

        logger.info("CSV export completed: {}", outputPath);
    }

    private void writeCsvLine(BufferedWriter writer, String category, String property, String value) throws IOException {
        // Escape CSV values
        writer.write(escapeCsv(category));
        writer.write(",");
        writer.write(escapeCsv(property));
        writer.write(",");
        writer.write(escapeCsv(value));
        writer.write("\n");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Exports statistics to JSON format.
     *
     * @param statistics the statistics to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToJson(XsdStatistics statistics, Path outputPath) throws IOException {
        logger.info("Exporting statistics to JSON: {}", outputPath);

        // Build a structured map for JSON
        Map<String, Object> jsonData = new LinkedHashMap<>();

        // Schema Information
        Map<String, Object> schemaInfo = new LinkedHashMap<>();
        schemaInfo.put("xsdVersion", statistics.xsdVersion());
        schemaInfo.put("targetNamespace", statistics.targetNamespace());
        schemaInfo.put("elementFormDefault", statistics.elementFormDefault());
        schemaInfo.put("attributeFormDefault", statistics.attributeFormDefault());
        schemaInfo.put("namespaceCount", statistics.namespaceCount());
        schemaInfo.put("fileCount", statistics.fileCount());
        if (statistics.mainSchemaPath() != null) {
            schemaInfo.put("mainSchemaPath", statistics.mainSchemaPath().toString());
        }
        List<String> includedFilesList = new ArrayList<>();
        for (Path p : statistics.includedFiles()) {
            includedFilesList.add(p.toString());
        }
        schemaInfo.put("includedFiles", includedFilesList);
        jsonData.put("schemaInfo", schemaInfo);

        // Node Counts
        Map<String, Integer> nodeCounts = new LinkedHashMap<>();
        nodeCounts.put("total", statistics.totalNodeCount());
        for (XsdNodeType type : XsdNodeType.values()) {
            int count = statistics.getNodeCount(type);
            if (count > 0) {
                nodeCounts.put(type.name(), count);
            }
        }
        jsonData.put("nodeCounts", nodeCounts);

        // Documentation Statistics
        Map<String, Object> docStats = new LinkedHashMap<>();
        docStats.put("nodesWithDocumentation", statistics.nodesWithDocumentation());
        docStats.put("nodesWithAppInfo", statistics.nodesWithAppInfo());
        docStats.put("coveragePercent", statistics.documentationCoveragePercent());
        docStats.put("appInfoTags", statistics.appInfoTagCounts());
        docStats.put("languages", new ArrayList<>(statistics.documentationLanguages()));
        jsonData.put("documentation", docStats);

        // Type Usage
        Map<String, Object> typeUsage = new LinkedHashMap<>();
        List<Map<String, Object>> topTypes = new ArrayList<>();
        for (XsdStatistics.TypeUsageEntry entry : statistics.topUsedTypes()) {
            Map<String, Object> typeEntry = new LinkedHashMap<>();
            typeEntry.put("name", entry.typeName());
            typeEntry.put("usageCount", entry.usageCount());
            topTypes.add(typeEntry);
        }
        typeUsage.put("topUsedTypes", topTypes);
        typeUsage.put("unusedTypes", new ArrayList<>(statistics.unusedTypes()));
        typeUsage.put("allTypeCounts", statistics.typeUsageCounts());
        jsonData.put("typeUsage", typeUsage);

        // Cardinality
        Map<String, Integer> cardinality = new LinkedHashMap<>();
        cardinality.put("optionalElements", statistics.optionalElements());
        cardinality.put("requiredElements", statistics.requiredElements());
        cardinality.put("unboundedElements", statistics.unboundedElements());
        jsonData.put("cardinality", cardinality);

        // Metadata
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("collectedAt", statistics.collectedAt().format(DATE_FORMATTER));
        jsonData.put("metadata", metadata);

        // Write JSON
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(jsonData, writer);
        }

        logger.info("JSON export completed: {}", outputPath);
    }

    /**
     * Exports statistics to PDF format using Apache FOP.
     *
     * @param statistics the statistics to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToPdf(XsdStatistics statistics, Path outputPath) throws IOException {
        logger.info("Exporting statistics to PDF: {}", outputPath);

        try {
            // Generate XSL-FO content
            String foContent = generateXslFo(statistics);

            // Create FOP factory
            FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
            FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
            foUserAgent.setTitle("XSD Schema Statistics");
            foUserAgent.setAuthor(System.getProperty("user.name"));
            foUserAgent.setCreator("FreeXmlToolkit");

            // Ensure parent directory exists
            Files.createDirectories(outputPath.getParent());

            // Generate PDF
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
                Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);

                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();

                Source src = new StreamSource(new StringReader(foContent));
                Result res = new SAXResult(fop.getDefaultHandler());
                transformer.transform(src, res);
            }

            logger.info("PDF export completed: {}", outputPath);

        } catch (Exception e) {
            logger.error("Failed to export PDF", e);
            throw new IOException("Failed to export statistics to PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generates XSL-FO content for PDF rendering.
     */
    private String generateXslFo(XsdStatistics statistics) {
        StringBuilder fo = new StringBuilder();

        // XSL-FO header
        fo.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        fo.append("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">\n");
        fo.append("  <fo:layout-master-set>\n");
        fo.append("    <fo:simple-page-master master-name=\"A4\" page-height=\"297mm\" page-width=\"210mm\" ");
        fo.append("margin-top=\"20mm\" margin-bottom=\"20mm\" margin-left=\"20mm\" margin-right=\"20mm\">\n");
        fo.append("      <fo:region-body margin-top=\"15mm\" margin-bottom=\"15mm\"/>\n");
        fo.append("      <fo:region-before extent=\"15mm\"/>\n");
        fo.append("      <fo:region-after extent=\"15mm\"/>\n");
        fo.append("    </fo:simple-page-master>\n");
        fo.append("  </fo:layout-master-set>\n");

        fo.append("  <fo:page-sequence master-reference=\"A4\">\n");

        // Header
        fo.append("    <fo:static-content flow-name=\"xsl-region-before\">\n");
        fo.append("      <fo:block font-size=\"10pt\" text-align=\"center\" color=\"#666666\">\n");
        fo.append("        XSD Schema Statistics - Generated by FreeXmlToolkit\n");
        fo.append("      </fo:block>\n");
        fo.append("    </fo:static-content>\n");

        // Footer
        fo.append("    <fo:static-content flow-name=\"xsl-region-after\">\n");
        fo.append("      <fo:block font-size=\"9pt\" text-align=\"center\" color=\"#666666\">\n");
        fo.append("        Page <fo:page-number/> - Generated: ").append(statistics.collectedAt().format(DATE_FORMATTER)).append("\n");
        fo.append("      </fo:block>\n");
        fo.append("    </fo:static-content>\n");

        // Body
        fo.append("    <fo:flow flow-name=\"xsl-region-body\">\n");

        // Title
        fo.append("      <fo:block font-size=\"18pt\" font-weight=\"bold\" space-after=\"12pt\" text-align=\"center\">\n");
        fo.append("        XSD Schema Statistics\n");
        fo.append("      </fo:block>\n");

        // Schema Information Section
        appendSection(fo, "Schema Information");
        appendTableRow(fo, "XSD Version", statistics.xsdVersion());
        appendTableRow(fo, "Target Namespace", statistics.targetNamespace().isEmpty() ? "(none)" : statistics.targetNamespace());
        appendTableRow(fo, "Element Form Default", statistics.elementFormDefault());
        appendTableRow(fo, "Attribute Form Default", statistics.attributeFormDefault());
        appendTableRow(fo, "Namespace Count", String.valueOf(statistics.namespaceCount()));
        appendTableRow(fo, "File Count", String.valueOf(statistics.fileCount()));
        fo.append("      </fo:table-body></fo:table>\n");

        // Node Counts Section
        appendSection(fo, "Node Counts");
        appendTableRow(fo, "Total Nodes", String.valueOf(statistics.totalNodeCount()));
        appendTableRow(fo, "Elements", String.valueOf(statistics.getElementCount()));
        appendTableRow(fo, "Attributes", String.valueOf(statistics.getAttributeCount()));
        appendTableRow(fo, "Complex Types", String.valueOf(statistics.getComplexTypeCount()));
        appendTableRow(fo, "Simple Types", String.valueOf(statistics.getSimpleTypeCount()));
        appendTableRow(fo, "Groups", String.valueOf(statistics.getGroupCount()));
        appendTableRow(fo, "Attribute Groups", String.valueOf(statistics.getAttributeGroupCount()));
        fo.append("      </fo:table-body></fo:table>\n");

        // Documentation Section
        appendSection(fo, "Documentation Statistics");
        appendTableRow(fo, "Nodes with Documentation", String.valueOf(statistics.nodesWithDocumentation()));
        appendTableRow(fo, "Nodes with AppInfo", String.valueOf(statistics.nodesWithAppInfo()));
        appendTableRow(fo, "Documentation Coverage", String.format("%.1f%%", statistics.documentationCoveragePercent()));
        for (Map.Entry<String, Integer> entry : statistics.appInfoTagCounts().entrySet()) {
            appendTableRow(fo, entry.getKey() + " tags", String.valueOf(entry.getValue()));
        }
        fo.append("      </fo:table-body></fo:table>\n");

        // Type Usage Section
        if (!statistics.topUsedTypes().isEmpty()) {
            appendSection(fo, "Top Used Types");
            for (XsdStatistics.TypeUsageEntry entry : statistics.topUsedTypes()) {
                appendTableRow(fo, entry.typeName(), entry.usageCount() + " usages");
            }
            fo.append("      </fo:table-body></fo:table>\n");
        }

        // Cardinality Section
        appendSection(fo, "Cardinality Statistics");
        appendTableRow(fo, "Optional Elements (minOccurs=0)", String.valueOf(statistics.optionalElements()));
        appendTableRow(fo, "Required Elements (minOccurs≥1)", String.valueOf(statistics.requiredElements()));
        appendTableRow(fo, "Unbounded Elements", String.valueOf(statistics.unboundedElements()));
        fo.append("      </fo:table-body></fo:table>\n");

        // Close body and document
        fo.append("    </fo:flow>\n");
        fo.append("  </fo:page-sequence>\n");
        fo.append("</fo:root>\n");

        return fo.toString();
    }

    private void appendSection(StringBuilder fo, String title) {
        fo.append("      <fo:block font-size=\"14pt\" font-weight=\"bold\" space-before=\"12pt\" space-after=\"6pt\" ");
        fo.append("border-bottom=\"1pt solid #333333\" padding-bottom=\"3pt\">\n");
        fo.append("        ").append(escapeXml(title)).append("\n");
        fo.append("      </fo:block>\n");
        fo.append("      <fo:table width=\"100%\" table-layout=\"fixed\">\n");
        fo.append("        <fo:table-column column-width=\"50%\"/>\n");
        fo.append("        <fo:table-column column-width=\"50%\"/>\n");
        fo.append("        <fo:table-body>\n");
    }

    private void appendTableRow(StringBuilder fo, String label, String value) {
        fo.append("          <fo:table-row>\n");
        fo.append("            <fo:table-cell padding=\"2pt\">\n");
        fo.append("              <fo:block font-size=\"10pt\">").append(escapeXml(label)).append("</fo:block>\n");
        fo.append("            </fo:table-cell>\n");
        fo.append("            <fo:table-cell padding=\"2pt\">\n");
        fo.append("              <fo:block font-size=\"10pt\" font-weight=\"bold\">").append(escapeXml(value)).append("</fo:block>\n");
        fo.append("            </fo:table-cell>\n");
        fo.append("          </fo:table-row>\n");
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
