package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.*;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;

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
 * Exports XSD quality check results to various formats (CSV, JSON, PDF, HTML, Excel).
 *
 * @since 2.0
 */
public class XsdQualityExporter {

    private static final Logger logger = LogManager.getLogger(XsdQualityExporter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Gets the priority of a severity for sorting (lower = more important).
     */
    private int getSeverityPriority(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
            case SUGGESTION -> 3;
        };
    }

    /**
     * Sorts issues by severity (most important first).
     */
    private List<QualityIssue> sortBySeverity(List<QualityIssue> issues) {
        return issues.stream()
                .sorted(Comparator.comparingInt(i -> getSeverityPriority(i.severity())))
                .toList();
    }

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

            // Issues section (sorted by severity)
            writer.write("Category,Severity,Message,XPath,Suggestion,Affected Elements\n");

            List<QualityIssue> sortedIssues = sortBySeverity(result.issues());
            for (QualityIssue issue : sortedIssues) {
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

        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

        // Create exportable structure (without XsdNode references which aren't serializable)
        Map<String, Object> exportData = new LinkedHashMap<>();

        // Add metadata at the beginning
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("generator", metadataService.getAppName());
        metadata.put("version", metadataService.getAppVersion());
        String userName = metadataService.getUserName();
        if (userName != null) {
            metadata.put("author", userName);
        }
        String company = metadataService.getUserCompany();
        if (company != null) {
            metadata.put("company", company);
        }
        metadata.put("generatedAt", metadataService.getTimestamp() + "Z");
        exportData.put("_metadata", metadata);

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

        // Issues (sorted by severity)
        List<Map<String, Object>> issuesList = new ArrayList<>();
        for (QualityIssue issue : sortBySeverity(result.issues())) {
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

        // Set PDF metadata from ExportMetadataService
        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
        metadataService.setPdfMetadata(foUserAgent, "XSD Quality Analysis Report");

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

            // Issues sorted by severity
            for (QualityIssue issue : sortBySeverity(result.issues())) {
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
            case INCONSISTENT_DEFINITION -> "Inconsistent Definition";
            case DUPLICATE_DEFINITION -> "Duplicate Definition";
            case DUPLICATE_ELEMENT_IN_CONTAINER -> "Duplicate Element in Container";
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

    /**
     * Exports quality results to HTML format.
     *
     * @param result     the quality result to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToHtml(QualityResult result, Path outputPath) throws IOException {
        logger.info("Exporting quality results to HTML: {}", outputPath);

        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html lang=\"en\">\n");
            writer.write("<head>\n");
            writer.write("  <meta charset=\"UTF-8\">\n");
            writer.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            writer.write(metadataService.generateHtmlMetaTags());
            writer.write("  <title>XSD Quality Analysis Report</title>\n");
            writer.write("  <style>\n");
            writer.write(getHtmlStyles());
            writer.write("  </style>\n");
            writer.write("</head>\n");
            writer.write("<body>\n");

            // Header
            writer.write("  <div class=\"container\">\n");
            writer.write("    <h1>XSD Quality Analysis Report</h1>\n");
            writer.write("    <p class=\"timestamp\">Generated: " + DATE_FORMATTER.format(LocalDateTime.now()) + "</p>\n");

            // Score Summary
            String scoreColorClass = getScoreColorClass(result.score());
            writer.write("    <div class=\"score-card\">\n");
            writer.write("      <div class=\"score-value " + scoreColorClass + "\">" + result.score() + "/100</div>\n");
            writer.write("      <div class=\"score-description\">" + result.getScoreDescription() + "</div>\n");
            writer.write("      <div class=\"score-stats\">Total Checks: " + result.totalChecks() + " | Passed: " + result.passedChecks() + " | Issues: " + result.issues().size() + "</div>\n");
            writer.write("    </div>\n");

            // Naming Distribution
            writer.write("    <div class=\"section\">\n");
            writer.write("      <h2>Naming Convention Distribution</h2>\n");
            writer.write("      <table>\n");
            writer.write("        <tr><th>Convention</th><th>Count</th></tr>\n");
            for (Map.Entry<NamingConvention, Integer> entry : result.namingDistribution().entrySet()) {
                if (entry.getValue() > 0) {
                    String highlight = entry.getKey() == result.dominantNamingConvention() ? " class=\"dominant\"" : "";
                    writer.write("        <tr" + highlight + "><td>" + escapeHtml(entry.getKey().getDisplayName()) + "</td><td>" + entry.getValue() + "</td></tr>\n");
                }
            }
            writer.write("      </table>\n");
            writer.write("    </div>\n");

            // Issues (sorted by severity)
            if (!result.issues().isEmpty()) {
                writer.write("    <div class=\"section\">\n");
                writer.write("      <h2>Quality Issues (" + result.issues().size() + ")</h2>\n");

                for (QualityIssue issue : sortBySeverity(result.issues())) {
                    String severityClass = getSeverityClass(issue.severity());
                    writer.write("      <div class=\"issue-card " + severityClass + "\">\n");
                    writer.write("        <div class=\"issue-header\">\n");
                    writer.write("          <span class=\"severity-badge\">" + getSeverityText(issue.severity()) + "</span>\n");
                    writer.write("          <span class=\"category-badge\">" + getCategoryText(issue.category()) + "</span>\n");
                    writer.write("        </div>\n");
                    writer.write("        <div class=\"issue-message\">" + escapeHtml(issue.message()) + "</div>\n");

                    if (issue.xpath() != null && !issue.xpath().isBlank()) {
                        writer.write("        <div class=\"issue-xpath\">Location: <code>" + escapeHtml(issue.xpath()) + "</code></div>\n");
                    }

                    if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                        writer.write("        <div class=\"issue-suggestion\">Suggestion: " + escapeHtml(issue.suggestion()) + "</div>\n");
                    }

                    if (!issue.affectedElements().isEmpty()) {
                        writer.write("        <div class=\"issue-affected\">\n");
                        writer.write("          <strong>Affected Elements (" + issue.affectedElements().size() + "):</strong>\n");
                        writer.write("          <ul>\n");
                        for (String elem : issue.affectedElements()) {
                            writer.write("            <li>" + escapeHtml(elem) + "</li>\n");
                        }
                        writer.write("          </ul>\n");
                        writer.write("        </div>\n");
                    }

                    writer.write("      </div>\n");
                }

                writer.write("    </div>\n");
            } else {
                writer.write("    <div class=\"section success-message\">\n");
                writer.write("      <p>No quality issues found. Excellent!</p>\n");
                writer.write("    </div>\n");
            }

            // Footer
            writer.write("    <div class=\"footer\">\n");
            writer.write("      <p>Generated by FreeXmlToolkit</p>\n");
            writer.write("    </div>\n");
            writer.write("  </div>\n");
            writer.write("</body>\n");
            writer.write("</html>\n");
        }

        logger.info("HTML export completed: {}", outputPath);
    }

    private String getHtmlStyles() {
        return """
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
              line-height: 1.6;
              color: #333;
              background-color: #f5f5f5;
              margin: 0;
              padding: 20px;
            }
            .container {
              max-width: 1000px;
              margin: 0 auto;
              background: white;
              padding: 30px;
              border-radius: 8px;
              box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            h1 {
              color: #2c3e50;
              border-bottom: 3px solid #3498db;
              padding-bottom: 10px;
              margin-bottom: 5px;
            }
            h2 {
              color: #34495e;
              margin-top: 25px;
              border-bottom: 1px solid #bdc3c7;
              padding-bottom: 5px;
            }
            .timestamp { color: #7f8c8d; font-size: 0.9em; margin-top: 0; }
            .score-card {
              background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
              color: white;
              padding: 25px;
              border-radius: 10px;
              text-align: center;
              margin: 20px 0;
            }
            .score-value { font-size: 48px; font-weight: bold; margin-bottom: 5px; }
            .score-value.excellent { color: #2ecc71; }
            .score-value.good { color: #3498db; }
            .score-value.average { color: #f1c40f; }
            .score-value.poor { color: #e67e22; }
            .score-value.critical { color: #e74c3c; }
            .score-description { font-size: 18px; margin-bottom: 10px; }
            .score-stats { font-size: 14px; opacity: 0.9; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th, td { padding: 10px 15px; text-align: left; border-bottom: 1px solid #ecf0f1; }
            th { background-color: #3498db; color: white; font-weight: 600; }
            tr:hover { background-color: #f8f9fa; }
            tr.dominant { background-color: #e8f4fd; font-weight: bold; }
            .issue-card {
              border-left: 4px solid #ccc;
              padding: 15px;
              margin: 15px 0;
              background: #fafafa;
              border-radius: 0 5px 5px 0;
            }
            .issue-card.error { border-left-color: #e74c3c; background: #fdf2f2; }
            .issue-card.warning { border-left-color: #f39c12; background: #fef9e7; }
            .issue-card.info { border-left-color: #3498db; background: #ebf5fb; }
            .issue-card.suggestion { border-left-color: #9b59b6; background: #f5eef8; }
            .issue-header { margin-bottom: 10px; }
            .severity-badge, .category-badge {
              display: inline-block;
              padding: 3px 10px;
              border-radius: 3px;
              font-size: 12px;
              font-weight: bold;
              margin-right: 8px;
            }
            .severity-badge { background: #e74c3c; color: white; }
            .issue-card.warning .severity-badge { background: #f39c12; }
            .issue-card.info .severity-badge { background: #3498db; }
            .issue-card.suggestion .severity-badge { background: #9b59b6; }
            .category-badge { background: #34495e; color: white; }
            .issue-message { font-size: 15px; font-weight: 500; margin-bottom: 8px; }
            .issue-xpath { font-size: 13px; color: #666; margin-bottom: 5px; }
            .issue-xpath code { background: #ecf0f1; padding: 2px 6px; border-radius: 3px; }
            .issue-suggestion { font-style: italic; color: #27ae60; margin-bottom: 8px; }
            .issue-affected { font-size: 13px; }
            .issue-affected ul { margin: 5px 0; padding-left: 20px; }
            .issue-affected li { margin: 2px 0; }
            .success-message { background: #d4edda; color: #155724; padding: 20px; border-radius: 5px; text-align: center; }
            .footer { margin-top: 30px; padding-top: 15px; border-top: 1px solid #ecf0f1; text-align: center; color: #95a5a6; font-size: 0.85em; }
            """;
    }

    private String getScoreColorClass(int score) {
        if (score >= 90) return "excellent";
        if (score >= 75) return "good";
        if (score >= 60) return "average";
        if (score >= 40) return "poor";
        return "critical";
    }

    private String getSeverityClass(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "info";
            case SUGGESTION -> "suggestion";
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Exports quality results to Excel format (.xlsx).
     *
     * @param result     the quality result to export
     * @param outputPath the output file path
     * @throws IOException if writing fails
     */
    public void exportToExcel(QualityResult result, Path outputPath) throws IOException {
        logger.info("Exporting quality results to Excel: {}", outputPath);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Set document metadata
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            metadataService.setExcelMetadata(workbook, "XSD Quality Analysis Report");

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle errorStyle = createSeverityStyle(workbook, IndexedColors.RED);
            CellStyle warningStyle = createSeverityStyle(workbook, IndexedColors.ORANGE);
            CellStyle infoStyle = createSeverityStyle(workbook, IndexedColors.LIGHT_BLUE);

            // Create Summary sheet
            Sheet summarySheet = workbook.createSheet("Summary");
            int rowNum = 0;

            // Title
            Row titleRow = summarySheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("XSD Quality Analysis Report");
            titleCell.setCellStyle(headerStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            // Timestamp
            Row timestampRow = summarySheet.createRow(rowNum++);
            timestampRow.createCell(0).setCellValue("Generated: " + DATE_FORMATTER.format(LocalDateTime.now()));
            rowNum++; // Empty row

            // Score section
            rowNum = writeExcelSection(summarySheet, rowNum, "Quality Score", sectionStyle);
            rowNum = writeExcelRow(summarySheet, rowNum, "Score", result.score() + "/100");
            rowNum = writeExcelRow(summarySheet, rowNum, "Description", result.getScoreDescription());
            rowNum = writeExcelRow(summarySheet, rowNum, "Total Checks", String.valueOf(result.totalChecks()));
            rowNum = writeExcelRow(summarySheet, rowNum, "Passed Checks", String.valueOf(result.passedChecks()));
            rowNum = writeExcelRow(summarySheet, rowNum, "Issues Found", String.valueOf(result.issues().size()));
            rowNum++; // Empty row

            // Naming Distribution section
            rowNum = writeExcelSection(summarySheet, rowNum, "Naming Convention Distribution", sectionStyle);
            rowNum = writeExcelRow(summarySheet, rowNum, "Dominant Convention", result.dominantNamingConvention().getDisplayName());
            for (Map.Entry<NamingConvention, Integer> entry : result.namingDistribution().entrySet()) {
                if (entry.getValue() > 0) {
                    rowNum = writeExcelRow(summarySheet, rowNum, entry.getKey().getDisplayName(), String.valueOf(entry.getValue()));
                }
            }

            // Auto-size columns
            summarySheet.setColumnWidth(0, 10000);
            summarySheet.setColumnWidth(1, 15000);

            // Create Issues sheet
            if (!result.issues().isEmpty()) {
                Sheet issuesSheet = workbook.createSheet("Issues");
                int issueRowNum = 0;

                // Header row
                Row issueHeaderRow = issuesSheet.createRow(issueRowNum++);
                String[] headers = {"Severity", "Category", "Message", "XPath", "Suggestion", "Affected Count"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = issueHeaderRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Issue rows (sorted by severity)
                for (QualityIssue issue : sortBySeverity(result.issues())) {
                    Row row = issuesSheet.createRow(issueRowNum++);

                    Cell severityCell = row.createCell(0);
                    severityCell.setCellValue(getSeverityText(issue.severity()));
                    severityCell.setCellStyle(switch (issue.severity()) {
                        case ERROR -> errorStyle;
                        case WARNING -> warningStyle;
                        default -> infoStyle;
                    });

                    row.createCell(1).setCellValue(getCategoryText(issue.category()));
                    row.createCell(2).setCellValue(issue.message());
                    row.createCell(3).setCellValue(issue.xpath() != null ? issue.xpath() : "");
                    row.createCell(4).setCellValue(issue.suggestion() != null ? issue.suggestion() : "");
                    row.createCell(5).setCellValue(issue.affectedElements().size());
                }

                // Auto-size columns
                issuesSheet.setColumnWidth(0, 4000);
                issuesSheet.setColumnWidth(1, 5000);
                issuesSheet.setColumnWidth(2, 15000);
                issuesSheet.setColumnWidth(3, 15000);
                issuesSheet.setColumnWidth(4, 12000);
                issuesSheet.setColumnWidth(5, 4000);

                // Create Affected Elements sheet (detailed)
                Sheet affectedSheet = workbook.createSheet("Affected Elements");
                int affectedRowNum = 0;

                Row affectedHeaderRow = affectedSheet.createRow(affectedRowNum++);
                Cell issueNumHeader = affectedHeaderRow.createCell(0);
                issueNumHeader.setCellValue("Issue #");
                issueNumHeader.setCellStyle(headerStyle);
                Cell categoryHeader = affectedHeaderRow.createCell(1);
                categoryHeader.setCellValue("Category");
                categoryHeader.setCellStyle(headerStyle);
                Cell elementHeader = affectedHeaderRow.createCell(2);
                elementHeader.setCellValue("Affected Element");
                elementHeader.setCellStyle(headerStyle);

                int issueNum = 1;
                // Sorted by severity
                for (QualityIssue issue : sortBySeverity(result.issues())) {
                    for (String element : issue.affectedElements()) {
                        Row row = affectedSheet.createRow(affectedRowNum++);
                        row.createCell(0).setCellValue(issueNum);
                        row.createCell(1).setCellValue(getCategoryText(issue.category()));
                        row.createCell(2).setCellValue(element);
                    }
                    issueNum++;
                }

                affectedSheet.setColumnWidth(0, 3000);
                affectedSheet.setColumnWidth(1, 5000);
                affectedSheet.setColumnWidth(2, 20000);
            }

            // Write to file
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }

        logger.info("Excel export completed: {}", outputPath);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createSeverityStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(color.getIndex());
        style.setFont(font);
        return style;
    }

    private int writeExcelSection(Sheet sheet, int rowNum, String title, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 1));
        return rowNum + 1;
    }

    private int writeExcelRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowNum + 1;
    }
}
