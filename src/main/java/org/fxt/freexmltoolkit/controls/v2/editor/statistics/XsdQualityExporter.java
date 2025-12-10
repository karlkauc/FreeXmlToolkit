package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.*;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports XSD quality check results to various formats (CSV, JSON, PDF).
 *
 * @since 2.0
 */
public class XsdQualityExporter {

    private static final Logger logger = LogManager.getLogger(XsdQualityExporter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Exports quality results to CSV format.
     *
     * @param result     the quality result to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToCsv(QualityResult result, Path outputPath) throws IOException {
        logger.info("Exporting quality results to CSV: {}", outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            // Summary header
            writer.write("# Quality Analysis Report\n");
            writer.write("# Generated: " + DATE_FORMATTER.format(LocalDateTime.now()) + "\n");
            writer.write("# Score: " + result.score() + "/100 (" + result.getScoreDescription() + ")\n");
            writer.write("# Dominant Naming Convention: " + result.dominantNamingConvention().getDisplayName() + "\n");
            writer.write("\n");

            // Issues section
            writer.write("Category,Severity,Message,XPath,Suggestion,Affected Elements\n");

            for (QualityIssue issue : result.issues()) {
                writeCsvLine(writer, issue);
            }

            // Naming Distribution section
            writer.write("\n# Naming Convention Distribution\n");
            writer.write("Convention,Count\n");
            for (Map.Entry<NamingConvention, Integer> entry : result.namingDistribution().entrySet()) {
                if (entry.getValue() > 0) {
                    writer.write(escapeCsv(entry.getKey().getDisplayName()));
                    writer.write(",");
                    writer.write(String.valueOf(entry.getValue()));
                    writer.write("\n");
                }
            }
        }

        logger.info("CSV export completed: {}", outputPath);
    }

    /**
     * Writes a single quality issue as a CSV line.
     */
    private void writeCsvLine(BufferedWriter writer, QualityIssue issue) throws IOException {
        writer.write(escapeCsv(getCategoryText(issue.category())));
        writer.write(",");
        writer.write(escapeCsv(getSeverityText(issue.severity())));
        writer.write(",");
        writer.write(escapeCsv(issue.message()));
        writer.write(",");
        writer.write(escapeCsv(issue.xpath() != null ? issue.xpath() : ""));
        writer.write(",");
        writer.write(escapeCsv(issue.suggestion() != null ? issue.suggestion() : ""));
        writer.write(",");
        writer.write(escapeCsv(String.join("; ", issue.affectedElements())));
        writer.write("\n");
    }

    /**
     * Escapes a value for CSV.
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Exports quality results to JSON format.
     *
     * @param result     the quality result to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToJson(QualityResult result, Path outputPath) throws IOException {
        logger.info("Exporting quality results to JSON: {}", outputPath);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

        // Create exportable structure (without XsdNode references which aren't serializable)
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("generatedAt", DATE_FORMATTER.format(LocalDateTime.now()));
        exportData.put("score", result.score());
        exportData.put("scoreDescription", result.getScoreDescription());
        exportData.put("totalChecks", result.totalChecks());
        exportData.put("passedChecks", result.passedChecks());
        exportData.put("dominantNamingConvention", result.dominantNamingConvention().getDisplayName());

        // Naming distribution
        Map<String, Integer> namingDist = new LinkedHashMap<>();
        for (Map.Entry<NamingConvention, Integer> entry : result.namingDistribution().entrySet()) {
            namingDist.put(entry.getKey().getDisplayName(), entry.getValue());
        }
        exportData.put("namingDistribution", namingDist);

        // Issues
        List<Map<String, Object>> issuesList = new ArrayList<>();
        for (QualityIssue issue : result.issues()) {
            Map<String, Object> issueMap = new LinkedHashMap<>();
            issueMap.put("category", getCategoryText(issue.category()));
            issueMap.put("severity", getSeverityText(issue.severity()));
            issueMap.put("message", issue.message());
            issueMap.put("xpath", issue.xpath());
            issueMap.put("suggestion", issue.suggestion());
            issueMap.put("affectedElements", issue.affectedElements());
            issuesList.add(issueMap);
        }
        exportData.put("issues", issuesList);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(exportData, writer);
        }

        logger.info("JSON export completed: {}", outputPath);
    }

    /**
     * Exports quality results to PDF format using Apache FOP.
     *
     * @param result     the quality result to export
     * @param outputPath the output file path
     * @throws Exception if generation fails
     */
    public void exportToPdf(QualityResult result, Path outputPath) throws Exception {
        logger.info("Exporting quality results to PDF: {}", outputPath);

        // Generate XSL-FO content
        String foContent = generateFoContent(result);

        // Create FOP factory and user agent
        FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
        foUserAgent.setTitle("XSD Quality Analysis Report");
        foUserAgent.setCreator("FreeXmlToolkit");

        // Create output stream for PDF
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);

            // Setup XSLT transformer
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();

            // Setup source and result
            Source src = new StreamSource(new StringReader(foContent));
            Result res = new SAXResult(fop.getDefaultHandler());

            // Transform
            transformer.transform(src, res);
        }

        logger.info("PDF export completed: {}", outputPath);
    }

    /**
     * Generates XSL-FO content for PDF generation.
     */
    private String generateFoContent(QualityResult result) {
        StringBuilder fo = new StringBuilder();

        fo.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        fo.append("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">\n");
        fo.append("  <fo:layout-master-set>\n");
        fo.append("    <fo:simple-page-master master-name=\"page\" page-width=\"210mm\" page-height=\"297mm\" margin=\"20mm\">\n");
        fo.append("      <fo:region-body margin-top=\"15mm\" margin-bottom=\"15mm\"/>\n");
        fo.append("      <fo:region-before extent=\"10mm\"/>\n");
        fo.append("      <fo:region-after extent=\"10mm\"/>\n");
        fo.append("    </fo:simple-page-master>\n");
        fo.append("  </fo:layout-master-set>\n");

        fo.append("  <fo:page-sequence master-reference=\"page\">\n");

        // Header
        fo.append("    <fo:static-content flow-name=\"xsl-region-before\">\n");
        fo.append("      <fo:block font-size=\"8pt\" text-align=\"right\" color=\"#666666\">");
        fo.append("XSD Quality Analysis Report - Generated: ").append(DATE_FORMATTER.format(LocalDateTime.now()));
        fo.append("</fo:block>\n");
        fo.append("    </fo:static-content>\n");

        // Footer
        fo.append("    <fo:static-content flow-name=\"xsl-region-after\">\n");
        fo.append("      <fo:block font-size=\"8pt\" text-align=\"center\" color=\"#666666\">");
        fo.append("Page <fo:page-number/> - FreeXmlToolkit");
        fo.append("</fo:block>\n");
        fo.append("    </fo:static-content>\n");

        // Body
        fo.append("    <fo:flow flow-name=\"xsl-region-body\">\n");

        // Title
        fo.append("      <fo:block font-size=\"18pt\" font-weight=\"bold\" space-after=\"10mm\" color=\"#333333\">\n");
        fo.append("        XSD Quality Analysis Report\n");
        fo.append("      </fo:block>\n");

        // Score summary
        String scoreColor = getScoreColor(result.score());
        fo.append("      <fo:block font-size=\"14pt\" font-weight=\"bold\" space-after=\"5mm\">\n");
        fo.append("        Quality Score: <fo:inline color=\"").append(scoreColor).append("\">")
          .append(result.score()).append("/100</fo:inline> (").append(result.getScoreDescription()).append(")\n");
        fo.append("      </fo:block>\n");

        fo.append("      <fo:block space-after=\"3mm\">\n");
        fo.append("        Total Checks: ").append(result.totalChecks()).append(" | ");
        fo.append("Passed: ").append(result.passedChecks()).append(" | ");
        fo.append("Issues: ").append(result.issues().size()).append("\n");
        fo.append("      </fo:block>\n");

        fo.append("      <fo:block space-after=\"5mm\">\n");
        fo.append("        Dominant Naming Convention: ").append(result.dominantNamingConvention().getDisplayName()).append("\n");
        fo.append("      </fo:block>\n");

        // Naming Distribution
        fo.append("      <fo:block font-size=\"12pt\" font-weight=\"bold\" space-before=\"8mm\" space-after=\"3mm\" border-bottom=\"1pt solid #cccccc\" padding-bottom=\"2mm\">\n");
        fo.append("        Naming Convention Distribution\n");
        fo.append("      </fo:block>\n");

        fo.append("      <fo:table table-layout=\"fixed\" width=\"100%\" space-after=\"8mm\">\n");
        fo.append("        <fo:table-column column-width=\"60%\"/>\n");
        fo.append("        <fo:table-column column-width=\"40%\"/>\n");
        fo.append("        <fo:table-body>\n");

        for (Map.Entry<NamingConvention, Integer> entry : result.namingDistribution().entrySet()) {
            if (entry.getValue() > 0) {
                fo.append("          <fo:table-row>\n");
                fo.append("            <fo:table-cell padding=\"2mm\"><fo:block>").append(escapeXml(entry.getKey().getDisplayName())).append("</fo:block></fo:table-cell>\n");
                fo.append("            <fo:table-cell padding=\"2mm\"><fo:block>").append(entry.getValue()).append("</fo:block></fo:table-cell>\n");
                fo.append("          </fo:table-row>\n");
            }
        }

        fo.append("        </fo:table-body>\n");
        fo.append("      </fo:table>\n");

        // Issues section
        if (!result.issues().isEmpty()) {
            fo.append("      <fo:block font-size=\"12pt\" font-weight=\"bold\" space-before=\"8mm\" space-after=\"3mm\" border-bottom=\"1pt solid #cccccc\" padding-bottom=\"2mm\">\n");
            fo.append("        Quality Issues (").append(result.issues().size()).append(")\n");
            fo.append("      </fo:block>\n");

            for (QualityIssue issue : result.issues()) {
                String severityColor = getSeverityColor(issue.severity());

                fo.append("      <fo:block space-before=\"4mm\" space-after=\"2mm\" border=\"0.5pt solid #dddddd\" padding=\"3mm\" background-color=\"#fafafa\">\n");

                // Category and Severity
                fo.append("        <fo:block font-weight=\"bold\">\n");
                fo.append("          <fo:inline color=\"").append(severityColor).append("\">").append(getSeverityText(issue.severity())).append("</fo:inline>");
                fo.append(" - ").append(escapeXml(getCategoryText(issue.category()))).append("\n");
                fo.append("        </fo:block>\n");

                // Message
                fo.append("        <fo:block space-before=\"2mm\">").append(escapeXml(issue.message())).append("</fo:block>\n");

                // XPath
                if (issue.xpath() != null && !issue.xpath().isBlank()) {
                    fo.append("        <fo:block space-before=\"2mm\" font-size=\"9pt\" color=\"#666666\">\n");
                    fo.append("          Location: ").append(escapeXml(issue.xpath())).append("\n");
                    fo.append("        </fo:block>\n");
                }

                // Suggestion
                if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                    fo.append("        <fo:block space-before=\"2mm\" font-style=\"italic\" color=\"#006600\">\n");
                    fo.append("          Suggestion: ").append(escapeXml(issue.suggestion())).append("\n");
                    fo.append("        </fo:block>\n");
                }

                // Affected elements
                if (!issue.affectedElements().isEmpty()) {
                    fo.append("        <fo:block space-before=\"2mm\" font-size=\"9pt\">\n");
                    fo.append("          Affected (").append(issue.affectedElements().size()).append("): ");
                    int count = 0;
                    for (String elem : issue.affectedElements()) {
                        if (count > 0) fo.append(", ");
                        if (count >= 5) {
                            fo.append("... and ").append(issue.affectedElements().size() - 5).append(" more");
                            break;
                        }
                        fo.append(escapeXml(elem));
                        count++;
                    }
                    fo.append("\n");
                    fo.append("        </fo:block>\n");
                }

                fo.append("      </fo:block>\n");
            }
        } else {
            fo.append("      <fo:block space-before=\"8mm\" color=\"#006600\" font-style=\"italic\">\n");
            fo.append("        No quality issues found. Excellent!\n");
            fo.append("      </fo:block>\n");
        }

        fo.append("    </fo:flow>\n");
        fo.append("  </fo:page-sequence>\n");
        fo.append("</fo:root>\n");

        return fo.toString();
    }

    /**
     * Escapes XML special characters.
     */
    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Gets color for score value.
     */
    private String getScoreColor(int score) {
        if (score >= 90) return "#28a745";
        if (score >= 75) return "#17a2b8";
        if (score >= 60) return "#ffc107";
        if (score >= 40) return "#fd7e14";
        return "#dc3545";
    }

    /**
     * Gets color for severity.
     */
    private String getSeverityColor(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> "#dc3545";
            case WARNING -> "#fd7e14";
            case INFO -> "#17a2b8";
            case SUGGESTION -> "#ffc107";
        };
    }

    /**
     * Gets display text for category.
     */
    private String getCategoryText(IssueCategory category) {
        return switch (category) {
            case NAMING_CONVENTION -> "Naming Convention";
            case BEST_PRACTICE -> "Best Practice";
            case DEPRECATED -> "Deprecated";
            case CONSTRAINT_CONFLICT -> "Constraint Conflict";
        };
    }

    /**
     * Gets display text for severity.
     */
    private String getSeverityText(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case INFO -> "Info";
            case SUGGESTION -> "Suggestion";
        };
    }
}
